package com.example.aud;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

public final class AlarmScheduler {
    private static final int REQ_CODE_EMERGENCY = 1;
    private static final int REQ_CODE_REMINDER = 2;
    private static final long ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private AlarmScheduler() {
    }

    public static void scheduleFromPrefs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheckIn = prefs.getLong(MainActivity.KEY_LAST_CHECK_IN, 0L);
        int daysBeforeAlert = Math.max(1, prefs.getInt(MainActivity.KEY_DAYS_BEFORE_ALERT, 2));
        String reminderTime = prefs.getString(MainActivity.KEY_REMINDER_TIME, "09:00");

        scheduleReminderAlarm(context, reminderTime);

        if (lastCheckIn > 0L) {
            long emergencyAt = lastCheckIn + (daysBeforeAlert * ONE_DAY_MILLIS);
            scheduleEmergencyAlarm(context, emergencyAt);
        } else {
            cancelEmergencyAlarm(context);
        }
    }

    private static void scheduleEmergencyAlarm(Context context, long triggerAtMillis) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e("AUD_DEBUG", "AlarmScheduler: AlarmManager is NULL!");
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheckIn = prefs.getLong(MainActivity.KEY_LAST_CHECK_IN, 0L);
        int daysBeforeAlert = Math.max(1, prefs.getInt(MainActivity.KEY_DAYS_BEFORE_ALERT, 2));
        long calculatedTrigger = lastCheckIn + (daysBeforeAlert * ONE_DAY_MILLIS);
        
        Log.d("AUD_DEBUG", "AlarmScheduler: lastCheckIn=" + lastCheckIn + ", daysBeforeAlert=" + daysBeforeAlert);
        Log.d("AUD_DEBUG", "AlarmScheduler: calculatedTrigger=" + calculatedTrigger + ", triggerAtMillis=" + triggerAtMillis);

        Intent emergencyIntent = new Intent(context, AlertReceiver.class).setAction(AlertReceiver.ACTION_EMERGENCY);
        PendingIntent emergencyPi = PendingIntent.getBroadcast(
                context,
                REQ_CODE_EMERGENCY,
                emergencyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long safeTrigger = Math.max(triggerAtMillis, System.currentTimeMillis() + 5_000L);
        Log.d("AUD_DEBUG", "AlarmScheduler: Setting emergency alarm for " + new java.util.Date(safeTrigger));
        setExactBestEffort(alarmManager, safeTrigger, emergencyPi);
    }

    private static void scheduleReminderAlarm(Context context, String reminderTime) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        int hour = 9;
        int minute = 0;
        if (reminderTime != null && reminderTime.contains(":")) {
            String[] parts = reminderTime.split(":");
            if (parts.length == 2) {
                try {
                    hour = Integer.parseInt(parts[0]);
                    minute = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                    hour = 9;
                    minute = 0;
                }
            }
        }

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, Math.max(0, Math.min(23, hour)));
        calendar.set(Calendar.MINUTE, Math.max(0, Math.min(59, minute)));
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        Intent reminderIntent = new Intent(context, AlertReceiver.class).setAction(AlertReceiver.ACTION_REMINDER);
        PendingIntent reminderPi = PendingIntent.getBroadcast(
                context,
                REQ_CODE_REMINDER,
                reminderIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.cancel(reminderPi);
        setExactBestEffort(alarmManager, calendar.getTimeInMillis(), reminderPi);
    }

    public static void cancelEmergencyAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        Intent emergencyIntent = new Intent(context, AlertReceiver.class).setAction(AlertReceiver.ACTION_EMERGENCY);
        PendingIntent emergencyPi = PendingIntent.getBroadcast(
                context,
                REQ_CODE_EMERGENCY,
                emergencyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(emergencyPi);
    }

    private static void setExactBestEffort(AlarmManager alarmManager, long triggerAtMillis, PendingIntent pi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            }
            return;
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
    }
}
