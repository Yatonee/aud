package com.example.aud;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class AlertReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "AUD_CHANNEL";
    public static final String ACTION_REMINDER = "com.example.aud.ACTION_REMINDER";
    public static final String ACTION_EMERGENCY = "com.example.aud.ACTION_EMERGENCY";
    public static final String ACTION_CHECK_IN_FROM_NOTIFICATION = "com.example.aud.ACTION_CHECK_IN_FROM_NOTIFICATION";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Log.d("AUD_DEBUG", "Received action: " + action);
        
        if (ACTION_REMINDER.equals(action)) {
            sendNotification(context, "Nhắc nhở an toàn", "Đã đến giờ điểm danh hằng ngày.", true);
            // Nhắc hằng ngày dùng dạng one-shot exact, nên cần tự lên lịch lại sau mỗi lần chạy.
            AlarmScheduler.scheduleFromPrefs(context);
        } else if (ACTION_CHECK_IN_FROM_NOTIFICATION.equals(action)) {
            performQuickCheckIn(context);
        } else if (ACTION_EMERGENCY.equals(action)) {
            sendEmergencySms(context);
            sendNotification(context, "Cảnh báo khẩn cấp", "Không nhận được điểm danh. Đã gửi SMS khẩn cấp.", false);
        }
    }

    private void sendNotification(Context context, String title, String message, boolean includeCheckInAction) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w("AUD_DEBUG", "POST_NOTIFICATIONS permission denied, cannot show notification.");
            return;
        }

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Thông báo an toàn", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                context,
                2001,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openAppPendingIntent)
                .setAutoCancel(true);

        if (includeCheckInAction) {
            Intent quickCheckInIntent = new Intent(context, AlertReceiver.class).setAction(ACTION_CHECK_IN_FROM_NOTIFICATION);
            PendingIntent quickCheckInPendingIntent = PendingIntent.getBroadcast(
                    context,
                    2002,
                    quickCheckInIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.addAction(0, "Điểm danh", quickCheckInPendingIntent);
        }

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void performQuickCheckIn(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long lastCheckIn = prefs.getLong(MainActivity.KEY_LAST_CHECK_IN, 0L);

        if (isSameDay(lastCheckIn, now)) {
            sendNotification(context, "Đã điểm danh", "Hôm nay bạn đã điểm danh rồi.", false);
            return;
        }

        int streak = prefs.getInt(MainActivity.KEY_STREAK_COUNT, 0);
        long oneDayMillis = 24L * 60L * 60L * 1000L;
        if (lastCheckIn > 0L) {
            if (now - lastCheckIn < oneDayMillis * 2L) streak++;
            else streak = 1;
        } else {
            streak = 1;
        }

        prefs.edit()
                .putLong(MainActivity.KEY_LAST_CHECK_IN, now)
                .putInt(MainActivity.KEY_STREAK_COUNT, streak)
                .apply();

        AlarmScheduler.scheduleFromPrefs(context);
        sendNotification(context, "Đã điểm danh", "Điểm danh nhanh thành công từ thông báo.", false);
    }

    private void sendEmergencySms(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String userName = prefs.getString(MainActivity.KEY_USER_NAME, "Người dùng");
        int days = prefs.getInt(MainActivity.KEY_DAYS_BEFORE_ALERT, 2);
        List<String> phones = getEmergencyPhones(prefs);
        
        String message = "KHẨN CẤP: " + userName + " đã không điểm danh trên Bạn ổn không trong " + days + " ngày. Vui lòng kiểm tra ngay.";

        if (!phones.isEmpty()) {
            try {
                SmsManager smsManager;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    smsManager = context.getSystemService(SmsManager.class);
                } else {
                    smsManager = SmsManager.getDefault();
                }
                for (String phone : phones) {
                    smsManager.sendTextMessage(phone, null, message, null, null);
                    Log.d("AUD_DEBUG", "SMS Sent to: " + phone);
                }
            } catch (Exception e) {
                Log.e("AUD_DEBUG", "SMS Failed: " + e.getMessage());
            }
        } else {
            Log.d("AUD_DEBUG", "Phone number is empty, cannot send SMS");
        }
    }

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

        if (phones.isEmpty()) {
            String fallback = prefs.getString(MainActivity.KEY_PHONE, "").trim();
            if (!fallback.isEmpty()) phones.add(fallback);
        }
        return phones;
    }

    private boolean isSameDay(long time1, long time2) {
        if (time1 <= 0L || time2 <= 0L) return false;
        Calendar cal1 = Calendar.getInstance();
        cal1.setTimeInMillis(time1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTimeInMillis(time2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

}