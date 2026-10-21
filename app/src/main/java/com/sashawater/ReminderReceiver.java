package com.sashawater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {
    static final String EXTRA_REMINDER_ID = "reminder_id";
    static final String EXTRA_SNOOZE = "snooze";

    @Override
    public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra(EXTRA_REMINDER_ID, -1);
        if (id >= ReminderScheduler.STREAK_REMINDER_BASE
                && id < ReminderScheduler.STREAK_REMINDER_BASE
                + ReminderScheduler.STREAK_REMINDER_COUNT) {
            ReminderScheduler.scheduleNextStreakProtection(context, id);
            if (!HydrationPrefs.isTodayStreakActive(context)
                    && HydrationPrefs.getStreakBeforeToday(context) > 0) {
                ReminderNotifications.showStreakProtection(context, id);
            }
            return;
        }
        if (id < 0 || id >= HydrationPrefs.getReminderTimes(context).size()) {
            return;
        }
        boolean snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false);
        if (!HydrationPrefs.areRemindersEnabled(context)
                || !HydrationPrefs.isReminderEnabled(context, id)) {
            return;
        }
        if (!snooze) {
            ReminderScheduler.scheduleNext(context, id);
        }
        if (!HydrationPrefs.isReminderAlarmEnabled(context, id)) {
            ReminderNotifications.showReminder(context, id);
            return;
        }
        Intent alarm = new Intent(context, ReminderAlarmService.class)
                .putExtra(ReminderActivity.EXTRA_REMINDER_ID, id);
        context.startForegroundService(alarm);
    }
}
