package com.example.aud;

/**
 * AlertReceiver - BroadcastReceiver xử lý các alarm và hành động liên quan
 * 
 * AlertReceiver là "trung tâm điều hành" xử lý mọi thông báo từ hệ thống:
 * 
 * 1. ACTION_REMINDER - Nhắc nhở điểm danh hàng ngày
 *    - Kích hoạt bởi AlarmScheduler mỗi ngày vào giờ đã cài đặt
 *    - Gửi notification nhắc người dùng điểm danh
 *    - Có nút "Điểm danh" trong notification để điểm danh nhanh
 *    - Reschedule reminder alarm cho ngày tiếp theo
 * 
 * 2. ACTION_EMERGENCY - Báo động khẩn cấp
 *    - Kích hoạt khi người dùng quên điểm danh quá N ngày
 *    - Gửi SMS khẩn cấp cho tất cả liên hệ
 *    - Nếu SMS thành công: reschedule alarm (thử lại sau 1 ngày)
 *    - Nếu đã gửi đủ 3 lần: hủy alarm và thông báo cho user
 * 
 * 3. ACTION_CHECK_IN_FROM_NOTIFICATION - Điểm danh nhanh từ notification
 *    - Thực hiện điểm danh mà không cần mở app
 *    - Cập nhật streak và SharedPreferences
 *    - Reschedule alarm
 * 
 * Cơ chế hoạt động:
 * - BroadcastReceiver nhận broadcast từ hệ thống khi alarm kích hoạt
 * - Kiểm tra action và xử lý tương ứng
 * - Dùng SmsManager để gửi SMS khẩn cấp
 * - Dùng NotificationManager để hiện notification
 */

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * BroadcastReceiver - nhận và xử lý system broadcasts
 * 
 * Được đăng ký trong AndroidManifest.xml để nhận:
 * - Alarm broadcasts từ AlarmManager
 * - Custom actions từ notification buttons
 */
public class AlertReceiver extends BroadcastReceiver {
    
    /** Channel ID cho notification (Android 8+) */
    public static final String CHANNEL_ID = "AUD_CHANNEL";
    
    /** Action cho reminder alarm (nhắc nhở hàng ngày) */
    public static final String ACTION_REMINDER = "com.example.aud.ACTION_REMINDER";
    
    /** Action cho emergency alarm (không điểm danh quá lâu) */
    public static final String ACTION_EMERGENCY = "com.example.aud.ACTION_EMERGENCY";
    
    /** Action cho điểm danh nhanh từ notification */
    public static final String ACTION_CHECK_IN_FROM_NOTIFICATION = "com.example.aud.ACTION_CHECK_IN_FROM_NOTIFICATION";

    /**
     * Callback chính - được gọi khi nhận được broadcast
     *
     * @param context Context của app/activity đã gửi broadcast
     * @param intent Intent chứa action và data
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // Áp dụng ngôn ngữ đã lưu
        context = LocaleHelper.applyLanguage(context);
        if (intent == null) {
            Log.e("AUD_DEBUG", "AlertReceiver: intent is NULL!");
            return;
        }
        String action = intent.getAction();
        Log.d("AUD_DEBUG", "AlertReceiver: Received action=" + action + " at " + new java.util.Date());

        // Xử lý theo action
        if (ACTION_REMINDER.equals(action)) {
            // ===== NHẮC NHỞ HÀNG NGÀY =====
            Log.d("AUD_DEBUG", "AlertReceiver: Processing REMINDER");
            sendNotification(context, context.getString(R.string.safety_reminder_title), context.getString(R.string.daily_check_in_reminder), true);
            AlarmScheduler.scheduleFromPrefs(context);

        } else if (ACTION_CHECK_IN_FROM_NOTIFICATION.equals(action)) {
            // ===== ĐIỂM DANH NHANH TỪ NOTIFICATION =====
            Log.d("AUD_DEBUG", "AlertReceiver: Processing QUICK_CHECK_IN");
            performQuickCheckIn(context);

        } else if (ACTION_EMERGENCY.equals(action)) {
            // ===== BÁO ĐỘNG KHẨN CẤP =====
            Log.d("AUD_DEBUG", "AlertReceiver: Processing EMERGENCY_SMS");

            // Tăng số lần emergency đã kích hoạt
            SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
            int emergencyCount = prefs.getInt(MainActivity.KEY_EMERGENCY_COUNT, 0) + 1;
            prefs.edit().putInt(MainActivity.KEY_EMERGENCY_COUNT, emergencyCount).apply();

            // Gửi SMS khẩn cấp cho tất cả liên hệ
            boolean smsSent = sendEmergencySms(context);
            if (smsSent) {
                sendNotification(context, context.getString(R.string.emergency_alert_title),
                        context.getString(R.string.emergency_sms_sent, emergencyCount), false);
            } else {
                sendNotification(context, context.getString(R.string.emergency_alert_title),
                        context.getString(R.string.emergency_sms_failed), false);
            }

            // Reschedule nếu chưa đến 3 lần, ngược lại hủy alarm
            if (emergencyCount < 3) {
                Log.d("AUD_DEBUG", "Emergency count=" + emergencyCount + ", rescheduling for next attempt");
                AlarmScheduler.scheduleFromPrefs(context);
            } else {
                Log.d("AUD_DEBUG", "Emergency count=" + emergencyCount + ", max reached, canceling alarm");
                AlarmScheduler.cancelEmergencyAlarm(context);
                sendNotification(context, context.getString(R.string.sms_limit_reached_title),
                        context.getString(R.string.sms_limit_reached), false);
            }

        } else {
            Log.w("AUD_DEBUG", "AlertReceiver: Unknown action=" + action);
        }
    }

    /**
     * Gửi notification cho người dùng
     * 
     * @param context              Context của app
     * @param title                Tiêu đề notification
     * @param message             Nội dung notification
     * @param includeCheckInAction Có thêm nút "Điểm danh" không
     */
    private void sendNotification(Context context, String title, String message, boolean includeCheckInAction) {
        // Android 13+: Kiểm tra quyền POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            Log.w("AUD_DEBUG", "POST_NOTIFICATIONS permission denied, cannot show notification.");
            return;
        }

        // Tạo NotificationManager
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Tạo notification channel cho Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        // Intent mở app khi tap vào notification
        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                context, 2001, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Xây dựng notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openAppPendingIntent)
                .setAutoCancel(true);  // Tự đóng khi tap

        // Thêm nút điểm danh nếu được yêu cầu
        if (includeCheckInAction) {
            Intent quickCheckInIntent = new Intent(context, AlertReceiver.class)
                    .setAction(ACTION_CHECK_IN_FROM_NOTIFICATION);
            PendingIntent quickCheckInPendingIntent = PendingIntent.getBroadcast(
                    context, 2002, quickCheckInIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(0, context.getString(R.string.check_in), quickCheckInPendingIntent);
        }

        // Hiện notification với unique ID (timestamp để tránh trùng)
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    /**
     * Thực hiện điểm danh nhanh từ notification
     * 
     * Algorithm tương tự performCheckIn() trong MainActivity:
     * 1. Kiểm tra đã điểm danh hôm nay chưa
     * 2. Tính streak dựa trên lần điểm danh cuối
     * 3. Lưu dữ liệu và reschedule alarm
     * 
     * @param context Context của app
     */
    private void performQuickCheckIn(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long lastCheckIn = prefs.getLong(MainActivity.KEY_LAST_CHECK_IN, 0L);

        // Kiểm tra đã điểm danh hôm nay chưa
        if (isSameDay(lastCheckIn, now)) {
            sendNotification(context, context.getString(R.string.quick_check_in_title), context.getString(R.string.already_checked_in), false);
            return;
        }

        int streak = prefs.getInt(MainActivity.KEY_STREAK_COUNT, 0);
        
        // Tính streak: dùng isYesterday() thay vì so sánh ms thô
        // để tránh streak sai khi điểm danh vào cuối/đầu ngày
        // Ví dụ: 23:59 hôm trước → 00:01 hôm nay (chỉ 2 phút nhưng khác ngày)
        if (lastCheckIn > 0L) {
            if (isYesterday(lastCheckIn, now)) {
                streak++;     // Tiếp tục chuỗi
            } else {
                streak = 1;    // Bắt đầu chuỗi mới
            }
        } else {
            streak = 1;        // Lần đầu tiên
        }

        // Lưu dữ liệu và reset số lần emergency
        prefs.edit()
                .putLong(MainActivity.KEY_LAST_CHECK_IN, now)
                .putInt(MainActivity.KEY_STREAK_COUNT, streak)
                .putInt(MainActivity.KEY_EMERGENCY_COUNT, 0)
                .apply();

        // Reschedule alarm với thời điểm alert mới
        AlarmScheduler.scheduleFromPrefs(context);
        sendNotification(context, context.getString(R.string.quick_check_in_title), context.getString(R.string.quick_check_in_success), false);
    }

    /**
     * Gửi SMS khẩn cấp cho tất cả liên hệ khẩn cấp
     * 
     * Algorithm:
     * 1. Lấy danh sách số điện thoại từ SharedPreferences
     * 2. Kiểm tra quyền SEND_SMS
     * 3. Tạo message với tên user và số ngày quên
     * 4. Gửi SMS cho từng số điện thoại
     * 5. Xử lý multipart SMS (tin nhắn dài > 160 ký tự)
     * 6. Tracking kết quả qua SmsSentReceiver
     * 
     * @param context Context của app
     * @return true nếu tất cả SMS gửi thành công
     */
    private boolean sendEmergencySms(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String userName = prefs.getString(MainActivity.KEY_USER_NAME, context.getString(R.string.app_name));
        int days = prefs.getInt(MainActivity.KEY_DAYS_BEFORE_ALERT, 2);
        List<String> phones = getEmergencyPhones(prefs);

        Log.d("AUD_DEBUG", "=== sendEmergencySms ===");
        Log.d("AUD_DEBUG", "userName=" + userName + ", days=" + days + ", phones=" + phones);

        // Kiểm tra không có số điện thoại
        if (phones.isEmpty()) {
            Log.e("AUD_DEBUG", "ERROR: No phone numbers configured!");
            return false;
        }

        // Kiểm tra quyền gửi SMS
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e("AUD_DEBUG", "ERROR: SEND_SMS permission not granted!");
            return false;
        }

        // Tạo nội dung SMS
        String message = context.getString(R.string.sms_body, userName, days);
        Log.d("AUD_DEBUG", "SMS message: " + message);

        // Lấy SmsManager phù hợp theo Android version
        SmsManager smsManager = getSmsManager(context);
        boolean allSucceeded = true;

        // Gửi SMS cho từng số điện thoại
        for (int i = 0; i < phones.size(); i++) {
            String phone = phones.get(i);
            try {
                Log.d("AUD_DEBUG", "Sending SMS to: " + phone);

                // Tạo Intent để tracking kết quả gửi SMS
                Intent sentIntent = new Intent(context, SmsSentReceiver.class);
                sentIntent.setAction(SmsSentReceiver.ACTION_SMS_SENT);
                sentIntent.putExtra("phone", phone);

                // Tạo PendingIntent với unique requestCode (= i)
                // Dùng i làm requestCode thay vì System.currentTimeMillis()
                // để tránh trùng requestCode khi gửi nhiều số trong vòng lặp
                PendingIntent sentPI = PendingIntent.getBroadcast(
                        context,
                        i,  // unique per phone
                        sentIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                // Kiểm tra tin nhắn có dài hơn giới hạn SMS đơn không
                ArrayList<String> parts = smsManager.divideMessage(message);
                if (parts.size() == 1) {
                    // Tin nhắn ngắn: gửi bình thường
                    smsManager.sendTextMessage(phone, null, message, sentPI, null);
                    Log.d("AUD_DEBUG", "sendTextMessage() called for: " + phone);
                } else {
                    // Tin nhắn dài: gửi multipart SMS
                    // Tạo danh sách PendingIntent cho từng phần
                    // requestCode = i * 100 + part để unique cho mỗi phần
                    ArrayList<PendingIntent> sentPIList = new ArrayList<>();
                    for (int part = 0; part < parts.size(); part++) {
                        Intent partIntent = new Intent(context, SmsSentReceiver.class);
                        partIntent.setAction(SmsSentReceiver.ACTION_SMS_SENT);
                        partIntent.putExtra("phone", phone);
                        partIntent.putExtra("part", part);

                        PendingIntent partPI = PendingIntent.getBroadcast(
                                context,
                                i * 100 + part,
                                partIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        );
                        sentPIList.add(partPI);
                    }
                    smsManager.sendMultipartTextMessage(phone, null, parts, sentPIList, null);
                    Log.d("AUD_DEBUG", "sendMultipartTextMessage() called for: " + phone
                            + " (" + parts.size() + " parts)");
                }

            } catch (Exception e) {
                Log.e("AUD_DEBUG", "Failed to send SMS to " + phone + ": "
                        + e.getClass().getSimpleName() + " - " + e.getMessage());
                allSucceeded = false;
            }
        }

        return allSucceeded;
    }

    /**
     * Lấy SmsManager phù hợp theo Android version
     * 
     * Trên Android 6+, createForDefaultSmsApp() ít bị chặn hơn getDefault()
     * trên một số thiết bị (Samsung, Xiaomi).
     * 
     * @param context Context của app
     * @return SmsManager instance
     */
    private SmsManager getSmsManager(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.getSystemService(SmsManager.class);
        }
        return SmsManager.getDefault();
    }

    /**
     * Lấy danh sách số điện thoại khẩn cấp từ SharedPreferences
     * 
     * @param prefs SharedPreferences instance
     * @return Danh sách số điện thoại
     */
    private List<String> getEmergencyPhones(SharedPreferences prefs) {
        List<String> phones = new ArrayList<>();
        String contactsJson = prefs.getString(MainActivity.KEY_CONTACTS_JSON, "");
        if (contactsJson != null && !contactsJson.trim().isEmpty()) {
            try {
                JSONArray arr = new JSONArray(contactsJson);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj == null) continue;
                    String phone = obj.optString("phone", "").trim();
                    if (!phone.isEmpty()) phones.add(phone);
                }
            } catch (Exception ignored) {
            }
        }

        // Fallback sang KEY_PHONE cũ nếu chưa migrate
        if (phones.isEmpty()) {
            String fallback = prefs.getString(MainActivity.KEY_PHONE, "").trim();
            if (!fallback.isEmpty()) phones.add(fallback);
        }
        return phones;
    }

    /**
     * Kiểm tra hai timestamp có cùng ngày hay không
     */
    private boolean isSameDay(long time1, long time2) {
        if (time1 <= 0L || time2 <= 0L) return false;
        Calendar cal1 = Calendar.getInstance();
        cal1.setTimeInMillis(time1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTimeInMillis(time2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * Kiểm tra time1 có phải là ngày hôm qua so với time2 không
     * 
     * Dùng Calendar để so sánh ngày thực, tránh sai sót khi điểm danh
     * vào đầu hoặc cuối ngày:
     * Ví dụ: 23:59 → 00:01 cách nhau chỉ 2 phút nhưng khác ngày,
     * còn 00:01 → 23:59 cách nhau ~24h nhưng cùng ngày.
     * 
     * @param time1 Timestamp cần kiểm tra
     * @param time2 Timestamp tham chiếu (thường là hiện tại)
     * @return true nếu time1 là ngày hôm qua của time2
     */
    private boolean isYesterday(long time1, long time2) {
        if (time1 <= 0L || time2 <= 0L) return false;
        Calendar cal1 = Calendar.getInstance();
        cal1.setTimeInMillis(time1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTimeInMillis(time2);

        // Lùi cal2 về 1 ngày rồi so sánh
        cal2.add(Calendar.DAY_OF_YEAR, -1);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
}