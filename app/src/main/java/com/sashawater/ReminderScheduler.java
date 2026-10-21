package com.sashawater;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.List;

final class ReminderScheduler {
    static final int STREAK_REMINDER_BASE = 100;
    static final int STREAK_REMINDER_COUNT = 3;
    private static final String[] STREAK_TIMES = {"21:00", "22:00", "23:00"};

    private ReminderScheduler() {}

    static void scheduleAll(Context context) {
        if (!HydrationPrefs.areRemindersEnabled(context)) {
            cancelAll(context);
            scheduleStreakProtection(context);
            return;
        }
        List<String> times = HydrationPrefs.getReminderTimes(context);
        for (int i = 0; i < HydrationPrefs.MAX_REMINDER_COUNT; i++) {
            if (i < times.size() && HydrationPrefs.isReminderEnabled(context, i)) {
                schedule(context, i, times.get(i));
            } else {
                cancelReminder(context, i);
            }
        }
        scheduleStreakProtection(context);
    }

    static void scheduleNext(Context context, int id) {
        if (!HydrationPrefs.areRemindersEnabled(context)) {
            return;
        }
        List<String> times = HydrationPrefs.getReminderTimes(context);
        if (id >= 0 && id < times.size() && HydrationPrefs.isReminderEnabled(context, id)) {
            schedule(context, id, times.get(id));
        }
    }

    static void scheduleNextStreakProtection(Context context, int id) {
        int index = id - STREAK_REMINDER_BASE;
        if (index < 0 || index >= STREAK_REMINDER_COUNT) {
            return;
        }
        schedule(context, id, STREAK_TIMES[index], 0b1111111);
    }

    private static void scheduleStreakProtection(Context context) {
        for (int i = 0; i < STREAK_REMINDER_COUNT; i++) {
            scheduleNextStreakProtection(context, STREAK_REMINDER_BASE + i);
        }
    }

    static void cancelReminder(Context context, int id) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            throw new IllegalStateException("Alarm service is unavailable");
        }
        PendingIntent pendingIntent = pendingIntent(context, id);
        manager.cancel(pendingIntent);
        pendingIntent.cancel();
        ReminderActionReceiver.cancelSnooze(context, id);
    }

    private static void schedule(Context context, int id, String time) {
        schedule(context, id, time, HydrationPrefs.getReminderDayMask(context, id));
    }

    private static void schedule(Context context, int id, String time, int repeatDays) {
        String[] parts = time.split(":");
        Calendar trigger = Calendar.getInstance();
        trigger.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
        trigger.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
        trigger.set(Calendar.SECOND, 0);
        trigger.set(Calendar.MILLISECOND, 0);
        if (trigger.getTimeInMillis() <= System.currentTimeMillis()) {
            trigger.add(Calendar.DAY_OF_YEAR, 1);
        }
        while ((repeatDays & (1 << (trigger.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY))) == 0) {
            trigger.add(Calendar.DAY_OF_YEAR, 1);
        }

        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            throw new IllegalStateException("Alarm service is unavailable");
        }
        PendingIntent pendingIntent = pendingIntent(context, id);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    trigger.getTimeInMillis(), pendingIntent);
        } else {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    trigger.getTimeInMillis(), pendingIntent);
        }
    }

    static void cancelAll(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            throw new IllegalStateException("Alarm service is unavailable");
        }
        for (int i = 0; i < HydrationPrefs.MAX_REMINDER_COUNT; i++) {
            cancelReminder(context, i);
        }
        for (int i = 0; i < STREAK_REMINDER_COUNT; i++) {
            cancelReminder(context, STREAK_REMINDER_BASE + i);
        }
    }

    private static PendingIntent pendingIntent(Context context, int id) {
        Intent intent = new Intent(context, ReminderReceiver.class)
                .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, id);
        return PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
