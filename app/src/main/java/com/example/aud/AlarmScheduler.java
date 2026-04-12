package com.example.aud;

/**
 * 
 * Có 2 loại alarm được quản lý:
 * 
 * 1. REMINDER ALARM - Alarm nhắc nhở hàng ngày
 *    - Kích hoạt mỗi ngày vào giờ đã cài đặt (mặc định 09:00)
 *    - Gửi notification nhắc người dùng điểm danh
 *    - Có thể điểm danh nhanh từ notification
 * 
 * 2. EMERGENCY ALARM - Alarm khẩn cấp
 *    - Kích hoạt sau N ngày kể từ lần điểm danh cuối (mặc định 2 ngày)
 *    - Gửi SMS khẩn cấp cho tất cả liên hệ khẩn cấp
 *    - Reschedule tối đa 3 lần nếu người dùng vẫn chưa điểm danh
 * 
 * Cơ chế hoạt động:
 * - Dùng AlarmManager.setExactAndAllowWhileIdle() để đảm bảo alarm chạy ngay cả khi Doze mode
 * - Trên Android 12+, kiểm tra quyền SCHEDULE_EXACT_ALARM trước khi đặt exact alarm
 * - PendingIntent được dùng để gửi broadcast đến AlertReceiver khi alarm kích hoạt
 */

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

/**
 * Utility class - không bao giờ được khởi tạo
 * Tất cả method đều là static
 */
public final class AlarmScheduler {
    
    /** Mã request cho emergency alarm (dùng trong PendingIntent) */
    private static final int REQ_CODE_EMERGENCY = 1;
    
    /** Mã request cho reminder alarm */
    private static final int REQ_CODE_REMINDER = 2;
    
    /** Số milliseconds trong 1 ngày (24 * 60 * 60 * 1000) */
    private static final long ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L;

    /** Private constructor - ngăn khởi tạo */
    private AlarmScheduler() {
    }

    /**
     * Đặt tất cả alarm dựa trên dữ liệu trong SharedPreferences
     * 
     * Called by:
     * - MainActivity.onCreate() - Đặt alarm khi khởi động app
     * - MainActivity.performCheckIn() - Reschedule sau khi điểm danh
     * - SettingsActivity - Reschedule khi thay đổi settings
     * - BootReceiver - Khôi phục alarm sau khi khởi động lại
     * - AlertReceiver - Reschedule sau khi reminder/emergency kích hoạt
     * 
     * @param context Context của app/activity
     */
    public static void scheduleFromPrefs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheckIn = prefs.getLong(MainActivity.KEY_LAST_CHECK_IN, 0L);
        int daysBeforeAlert = Math.max(1, prefs.getInt(MainActivity.KEY_DAYS_BEFORE_ALERT, 2));
        String reminderTime = prefs.getString(MainActivity.KEY_REMINDER_TIME, "09:00");

        // Luôn đặt reminder alarm (chạy hàng ngày)
        scheduleReminderAlarm(context, reminderTime);

        // Chỉ đặt emergency alarm nếu đã từng điểm danh
        if (lastCheckIn > 0L) {
            long emergencyAt = lastCheckIn + (daysBeforeAlert * ONE_DAY_MILLIS);
            scheduleEmergencyAlarm(context, emergencyAt);
        } else {
            // Chưa điểm danh lần nào → hủy emergency alarm
            cancelEmergencyAlarm(context);
        }
    }

    /**
     * Đặt emergency alarm
     * 
     * Alarm này kích hoạt khi người dùng quên điểm danh quá N ngày
     * 
     * @param context        Context của app
     * @param triggerAtMillis Thời điểm kích hoạt (timestamp milliseconds)
     */
    private static void scheduleEmergencyAlarm(Context context, long triggerAtMillis) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e("AUD_DEBUG", "AlarmScheduler: AlarmManager is NULL!");
            return;
        }

        // Lấy lại dữ liệu mới nhất để tính chính xác thời điểm alert
        // (phòng trường hợp preferences đã thay đổi kể từ lúc gọi scheduleFromPrefs)
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheckIn = prefs.getLong(MainActivity.KEY_LAST_CHECK_IN, 0L);
        int daysBeforeAlert = Math.max(1, prefs.getInt(MainActivity.KEY_DAYS_BEFORE_ALERT, 2));
        long calculatedTrigger = lastCheckIn + (daysBeforeAlert * ONE_DAY_MILLIS);
        
        Log.d("AUD_DEBUG", "AlarmScheduler: lastCheckIn=" + lastCheckIn + ", daysBeforeAlert=" + daysBeforeAlert);
        Log.d("AUD_DEBUG", "AlarmScheduler: calculatedTrigger=" + calculatedTrigger + ", triggerAtMillis=" + triggerAtMillis);

        // Tạo Intent để gửi broadcast đến AlertReceiver
        Intent emergencyIntent = new Intent(context, AlertReceiver.class).setAction(AlertReceiver.ACTION_EMERGENCY);
        
        // PendingIntent: định nghĩa "what to do" khi alarm kích hoạt
        // - REQ_CODE_EMERGENCY: mã định danh duy nhất cho PendingIntent này
        // - FLAG_UPDATE_CURRENT: nếu đã tồn tại, cập nhật data mới
        // - FLAG_IMMUTABLE: Intent không thể thay đổi (bảo mật)
        PendingIntent emergencyPi = PendingIntent.getBroadcast(
                context,
                REQ_CODE_EMERGENCY,
                emergencyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Đảm bảo thời điểm kích hoạt không trong quá khứ (ít nhất 5 giây sau)
        long safeTrigger = Math.max(triggerAtMillis, System.currentTimeMillis() + 5_000L);
        Log.d("AUD_DEBUG", "AlarmScheduler: Setting emergency alarm for " + new java.util.Date(safeTrigger));
        
        // Đặt exact alarm
        setExactBestEffort(alarmManager, safeTrigger, emergencyPi);
    }

    /**
     * Đặt reminder alarm - nhắc nhở điểm danh hàng ngày
     * 
     * Alarm này lặp lại mỗi ngày vào giờ đã cài đặt
     * Nếu thời điểm đã qua trong ngày hôm nay → đặt sang ngày mai
     * 
     * @param context      Context của app
     * @param reminderTime Giờ nhắc nhở (định dạng "HH:mm")
     */
    private static void scheduleReminderAlarm(Context context, String reminderTime) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        // Parse giờ và phút từ string "HH:mm"
        int hour = 9;
        int minute = 0;
        if (reminderTime != null && reminderTime.contains(":")) {
            String[] parts = reminderTime.split(":");
            if (parts.length == 2) {
                try {
                    hour = Integer.parseInt(parts[0]);
                    minute = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                    // Dùng giá trị mặc định nếu parse lỗi
                    hour = 9;
                    minute = 0;
                }
            }
        }

        // Tạo Calendar cho thời điểm reminder
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, Math.max(0, Math.min(23, hour)));
        calendar.set(Calendar.MINUTE, Math.max(0, Math.min(59, minute)));
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        
        // Nếu thời điểm đã qua trong ngày hôm nay → đặt sang ngày mai
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        // Tạo Intent và PendingIntent cho reminder
        Intent reminderIntent = new Intent(context, AlertReceiver.class).setAction(AlertReceiver.ACTION_REMINDER);
        PendingIntent reminderPi = PendingIntent.getBroadcast(
                context,
                REQ_CODE_REMINDER,
                reminderIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Hủy alarm cũ trước khi đặt alarm mới
        alarmManager.cancel(reminderPi);
        setExactBestEffort(alarmManager, calendar.getTimeInMillis(), reminderPi);
    }

    /**
     * Hủy emergency alarm
     * 
     * Called khi:
     * - Người dùng chưa từng điểm danh (chưa cần emergency)
     * - Đã gửi đủ 3 lần SMS khẩn cấp ( AlertReceiver.ACTION_EMERGENCY xử lý)
     * 
     * @param context Context của app
     */
    public static void cancelEmergencyAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        // Tạo cùng Intent/PendingIntent như khi đặt alarm để hủy đúng alarm đó
        Intent emergencyIntent = new Intent(context, AlertReceiver.class).setAction(AlertReceiver.ACTION_EMERGENCY);
        PendingIntent emergencyPi = PendingIntent.getBroadcast(
                context,
                REQ_CODE_EMERGENCY,
                emergencyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(emergencyPi);
    }

    /**
     * Đặt exact alarm với fallback phù hợp theo Android version
     * 
     * @param alarmManager    AlarmManager instance
     * @param triggerAtMillis Thời điểm kích hoạt
     * @param pi             PendingIntent để thực thi khi kích hoạt
     */
    private static void setExactBestEffort(AlarmManager alarmManager, long triggerAtMillis, PendingIntent pi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: Kiểm tra quyền trước
            if (alarmManager.canScheduleExactAlarms()) {
                // Đặt exact alarm (chính xác đến milliseconds)
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } else {
                // Fallback: setAndAllowWhileIdle (ít chính xác hơn nhưng không cần quyền)
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            }
            return;
        }
        // Android < 12: Đặt exact alarm trực tiếp
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
    }
}
