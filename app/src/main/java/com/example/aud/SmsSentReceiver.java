package com.example.aud;

/**
 * SmsSentReceiver - BroadcastReceiver nhận kết quả gửi SMS
 * 
 * SmsSentReceiver lắng nghe kết quả của việc gửi SMS:
 * - SMS gửi thành công (RESULT_OK)
 * - Lỗi tổng quát (GENERIC_FAILURE)
 * - Không có sóng (NO_SERVICE)
 * - Radio tắt (RADIO_OFF)
 * - Lỗi PDU (NULL_PDU)
 * 
 * Hiện tại chỉ logging kết quả để debug.
 * Có thể mở rộng để:
 * - Thông báo cho người dùng khi gửi thất bại
 * - Thử gửi lại SMS
 * - Báo cáo lỗi cho backend
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.app.Activity;
import android.os.Build;
import android.telephony.SmsManager;

/**
 * BroadcastReceiver nhận SMS_SENT broadcast
 * 
 * Được đăng ký trong AndroidManifest.xml:
 * - nhận kết quả từ SmsManager.sendTextMessage()
 * - nhận kết quả từ SmsManager.sendMultipartTextMessage()
 */
public class SmsSentReceiver extends BroadcastReceiver {
    
    /** Action định danh cho SMS sent broadcast */
    public static final String ACTION_SMS_SENT = "com.example.aud.SMS_SENT";
    
    /**
     * Callback được gọi khi nhận được SMS_SENT broadcast
     * 
     * @param context Context của app
     * @param intent Intent chứa phone number và result code
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_SMS_SENT.equals(intent.getAction())) {
            // Lấy số điện thoại từ extra
            String phone = intent.getStringExtra("phone");
            
            // Lấy result code từ hệ thống
            // Result code cho biết SMS đã được gửi thành công hay thất bại
            int result = getResultCode();
            
            // Log kết quả để debug
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
