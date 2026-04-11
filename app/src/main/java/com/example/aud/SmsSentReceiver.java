package com.example.aud;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.app.Activity;
import android.os.Build;
import android.telephony.SmsManager;

public class SmsSentReceiver extends BroadcastReceiver {
    public static final String ACTION_SMS_SENT = "com.example.aud.SMS_SENT";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_SMS_SENT.equals(intent.getAction())) {
            String phone = intent.getStringExtra("phone");
            int result = getResultCode();
            
            String status;
            switch (result) {
                case Activity.RESULT_OK:
                    status = "SMS_SENT_OK";
                    Log.d("AUD_DEBUG", "SMS sent successfully to " + phone);
                    break;
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    status = "GENERIC_FAILURE";
                    Log.e("AUD_DEBUG", "SMS FAILED to " + phone + ": GENERIC_FAILURE");
                    break;
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    status = "NO_SERVICE";
                    Log.e("AUD_DEBUG", "SMS FAILED to " + phone + ": NO_SERVICE");
                    break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    status = "RADIO_OFF";
                    Log.e("AUD_DEBUG", "SMS FAILED to " + phone + ": RADIO_OFF");
                    break;
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    status = "NULL_PDU";
                    Log.e("AUD_DEBUG", "SMS FAILED to " + phone + ": NULL_PDU");
                    break;
                default:
                    status = "UNKNOWN (" + result + ")";
                    Log.e("AUD_DEBUG", "SMS FAILED to " + phone + ": " + status);
            }
        }
    }
}
