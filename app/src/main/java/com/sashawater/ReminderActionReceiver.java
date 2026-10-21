package com.sashawater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderActionReceiver extends BroadcastReceiver {
    static final String ACTION_DRANK = "com.sashawater.action.DRANK";
    static final String ACTION_SNOOZE = "com.sashawater.action.SNOOZE";
    static final String EXTRA_AMOUNT_ML = "amount_ml";
    static final int SNOOZE_MINUTES = 10;

    @Override
    public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra(ReminderActivity.EXTRA_REMINDER_ID, -1);
        if (id < 0 || id >= HydrationPrefs.getReminderTimes(context).size()) {
            return;
        }
        if (ACTION_DRANK.equals(intent.getAction())) {
            int amount = intent.getIntExtra(EXTRA_AMOUNT_ML,
                    HydrationPrefs.getReminderAmount(context, id));
            HydrationPrefs.addIntake(context, amount);
            stopAlarm(context, id);
        } else if (ACTION_SNOOZE.equals(intent.getAction())) {
            scheduleSnooze(context, id);
            stopAlarm(context, id);
        }
    }

    static void scheduleSnooze(Context context, int id) {
        android.app.AlarmManager alarms = context.getSystemService(android.app.AlarmManager.class);
        if (alarms == null) {
            throw new IllegalStateException("Alarm service is unavailable");
        }
        Intent snooze = new Intent(context, ReminderReceiver.class)
                .putExtra(ReminderActivity.EXTRA_REMINDER_ID, id)
                .putExtra(ReminderReceiver.EXTRA_SNOOZE, true);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(
                context, 900 + id, snooze,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        long when = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                && !alarms.canScheduleExactAlarms()) {
            alarms.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, when, pendingIntent);
        } else {
            alarms.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, when, pendingIntent);
        }
    }

    static void cancelSnooze(Context context, int id) {
        android.app.AlarmManager alarms = context.getSystemService(android.app.AlarmManager.class);
        if (alarms == null) {
            throw new IllegalStateException("Alarm service is unavailable");
        }
        Intent snooze = new Intent(context, ReminderReceiver.class)
                .putExtra(ReminderActivity.EXTRA_REMINDER_ID, id)
                .putExtra(ReminderReceiver.EXTRA_SNOOZE, true);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(
                context, 900 + id, snooze,
                android.app.PendingIntent.FLAG_NO_CREATE | android.app.PendingIntent.FLAG_IMMUTABLE);
        if (pendingIntent != null) {
            alarms.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    static void stopAlarm(Context context, int id) {
        Intent stop = new Intent(context, ReminderAlarmService.class);
        context.stopService(stop);
        ReminderNotifications.clearReminder(context, id);
    }
}
