package com.example.aud;

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

public class AlertReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "AUD_CHANNEL";
    public static final String ACTION_REMINDER = "com.example.aud.ACTION_REMINDER";
    public static final String ACTION_EMERGENCY = "com.example.aud.ACTION_EMERGENCY";
    public static final String ACTION_CHECK_IN_FROM_NOTIFICATION = "com.example.aud.ACTION_CHECK_IN_FROM_NOTIFICATION";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            Log.e("AUD_DEBUG", "AlertReceiver: intent is NULL!");
            return;
        }
        String action = intent.getAction();
        Log.d("AUD_DEBUG", "AlertReceiver: Received action=" + action + " at " + new java.util.Date());

        if (ACTION_REMINDER.equals(action)) {
            Log.d("AUD_DEBUG", "AlertReceiver: Processing REMINDER");
            sendNotification(context, "Nhắc nhở an toàn", "Đã đến giờ điểm danh hằng ngày.", true);
            AlarmScheduler.scheduleFromPrefs(context);

        } else if (ACTION_CHECK_IN_FROM_NOTIFICATION.equals(action)) {
            Log.d("AUD_DEBUG", "AlertReceiver: Processing QUICK_CHECK_IN");
            performQuickCheckIn(context);

        } else if (ACTION_EMERGENCY.equals(action)) {
            Log.d("AUD_DEBUG", "AlertReceiver: Processing EMERGENCY_SMS");

            // Đếm số lần emergency đã kích hoạt
            SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
            int emergencyCount = prefs.getInt(MainActivity.KEY_EMERGENCY_COUNT, 0) + 1;
            prefs.edit().putInt(MainActivity.KEY_EMERGENCY_COUNT, emergencyCount).apply();

            boolean smsSent = sendEmergencySms(context);
            if (smsSent) {
                sendNotification(context, "Cảnh báo khẩn cấp",
                        "Không nhận được điểm danh. Đã gửi SMS khẩn cấp lần " + emergencyCount + ".", false);
            } else {
                sendNotification(context, "Cảnh báo khẩn cấp",
                        "Không nhận được điểm danh. Gửi SMS thất bại - kiểm tra quyền SMS.", false);
            }

            // Chỉ reschedule nếu chưa đến 3 lần
            if (emergencyCount < 3) {
                Log.d("AUD_DEBUG", "Emergency count=" + emergencyCount + ", rescheduling for next attempt");
                AlarmScheduler.scheduleFromPrefs(context);
            } else {
                Log.d("AUD_DEBUG", "Emergency count=" + emergencyCount + ", max reached, canceling alarm");
                AlarmScheduler.cancelEmergencyAlarm(context);
                sendNotification(context, "Hết lượt gửi SMS",
                        "Đã gửi tối đa 3 lần SMS khẩn cấp. Vui lòng điểm danh thủ công.", false);
            }

        } else {
            Log.w("AUD_DEBUG", "AlertReceiver: Unknown action=" + action);
        }
    }

    private void sendNotification(Context context, String title, String message, boolean includeCheckInAction) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            Log.w("AUD_DEBUG", "POST_NOTIFICATIONS permission denied, cannot show notification.");
            return;
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Thông báo an toàn", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                context, 2001, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openAppPendingIntent)
                .setAutoCancel(true);

        if (includeCheckInAction) {
            Intent quickCheckInIntent = new Intent(context, AlertReceiver.class)
                    .setAction(ACTION_CHECK_IN_FROM_NOTIFICATION);
            PendingIntent quickCheckInPendingIntent = PendingIntent.getBroadcast(
                    context, 2002, quickCheckInIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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
        // FIX 2: Dùng isYesterday() thay vì so sánh ms thô để tránh streak sai
        // khi điểm danh vào cuối/đầu ngày (ví dụ 23:59 hôm trước → 00:01 hôm nay).
        if (lastCheckIn > 0L) {
            if (isYesterday(lastCheckIn, now)) {
                streak++;
            } else {
                streak = 1;
            }
        } else {
            streak = 1;
        }

        prefs.edit()
                .putLong(MainActivity.KEY_LAST_CHECK_IN, now)
                .putInt(MainActivity.KEY_STREAK_COUNT, streak)
                .putInt(MainActivity.KEY_EMERGENCY_COUNT, 0)
                .apply();

        AlarmScheduler.scheduleFromPrefs(context);
        sendNotification(context, "Đã điểm danh", "Điểm danh nhanh thành công từ thông báo.", false);
    }

    private boolean sendEmergencySms(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String userName = prefs.getString(MainActivity.KEY_USER_NAME, "Người dùng");
        int days = prefs.getInt(MainActivity.KEY_DAYS_BEFORE_ALERT, 2);
        List<String> phones = getEmergencyPhones(prefs);

        Log.d("AUD_DEBUG", "=== sendEmergencySms ===");
        Log.d("AUD_DEBUG", "userName=" + userName + ", days=" + days + ", phones=" + phones);

        if (phones.isEmpty()) {
            Log.e("AUD_DEBUG", "ERROR: No phone numbers configured!");
            return false;
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e("AUD_DEBUG", "ERROR: SEND_SMS permission not granted!");
            return false;
        }

        String message = "KHẨN CẤP: " + userName + " không điểm danh " + days
                + " ngày. Kiểm tra ngay!";
        Log.d("AUD_DEBUG", "SMS message: " + message);

        SmsManager smsManager = getSmsManager(context);
        boolean allSucceeded = true;

        for (int i = 0; i < phones.size(); i++) {
            String phone = phones.get(i);
            try {
                Log.d("AUD_DEBUG", "Sending SMS to: " + phone);

                // FIX 3: Dùng explicit intent thay vì implicit để đảm bảo
                // SmsSentReceiver nhận được broadcast trên Android 8.0+.
                Intent sentIntent = new Intent(context, SmsSentReceiver.class);
                sentIntent.setAction(SmsSentReceiver.ACTION_SMS_SENT);
                sentIntent.putExtra("phone", phone);

                // FIX 4: Dùng index i làm requestCode thay vì System.currentTimeMillis()
                // để tránh trùng requestCode khi gửi nhiều số trong cùng một vòng lặp.
                PendingIntent sentPI = PendingIntent.getBroadcast(
                        context,
                        i,  // unique per phone
                        sentIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                ArrayList<String> parts = smsManager.divideMessage(message);
                if (parts.size() == 1) {
                    smsManager.sendTextMessage(phone, null, message, sentPI, null);
                    Log.d("AUD_DEBUG", "sendTextMessage() called for: " + phone);
                } else {
                    // FIX 5: Tạo danh sách sentPI cho từng phần của multipart SMS
                    // thay vì truyền null — đảm bảo tracking đầy đủ cho mọi phần.
                    ArrayList<PendingIntent> sentPIList = new ArrayList<>();
                    for (int part = 0; part < parts.size(); part++) {
                        Intent partIntent = new Intent(context, SmsSentReceiver.class);
                        partIntent.setAction(SmsSentReceiver.ACTION_SMS_SENT);
                        partIntent.putExtra("phone", phone);
                        partIntent.putExtra("part", part);

                        // requestCode = i * 100 + part để unique cho mỗi phần của mỗi số
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
     * Lấy SmsManager phù hợp theo API level.
     * Trên Android 6+, createForDefaultSmsApp() ít bị chặn hơn getDefault()
     * trên một số thiết bị (Samsung, Xiaomi).
     */
    private SmsManager getSmsManager(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.getSystemService(SmsManager.class);
        }
        return SmsManager.getDefault();
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

        // Fallback sang KEY_PHONE cũ nếu chưa migrate
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

    /**
     * Kiểm tra time1 có phải là ngày hôm qua so với time2 không.
     * Dùng Calendar để so sánh ngày thực, tránh sai sót khi điểm danh
     * vào đầu hoặc cuối ngày (ví dụ: 23:59 → 00:01 cách nhau chỉ 2 phút
     * nhưng khác ngày, còn 00:01 → 23:59 cách nhau ~24h nhưng cùng "khoảng 1 ngày").
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