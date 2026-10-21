package com.sashawater;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

final class ReminderNotifications {
    private static final String CHANNEL_ID = "water_reminders_v2";
    private static final String SERVICE_CHANNEL_ID = "water_alarm_playback";
    static final int SERVICE_NOTIFICATION_BASE = 7100;
    static final int REMINDER_NOTIFICATION_BASE = 7200;

    private ReminderNotifications() {}

    static String channelId() {
        return CHANNEL_ID;
    }

    static void createChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            throw new IllegalStateException("Notification service is unavailable");
        }
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Daily water alarms", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Hydration reminders and drink check-ins");
            channel.setSound(null, null);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(channel);
        }
        if (manager.getNotificationChannel(SERVICE_CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(
                    SERVICE_CHANNEL_ID, "Active water alarm", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shown while your selected alarm tone is playing");
            channel.setSound(null, null);
            manager.createNotificationChannel(channel);
        }
    }

    static android.app.Notification buildServiceNotification(Context context, int id) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            throw new IllegalStateException("Notification service is unavailable");
        }
        Intent openReminder = new Intent(context, ReminderActivity.class)
                .putExtra(ReminderActivity.EXTRA_REMINDER_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 810 + id, openReminder,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String name = HydrationPrefs.hasSavedName(context)
                ? HydrationPrefs.getName(context) : "you";
        int amount = HydrationPrefs.getReminderAmount(context, id);
        android.app.Notification.Builder builder =
                new android.app.Notification.Builder(context, SERVICE_CHANNEL_ID);
        return builder
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("SipGlow is ringing")
                .setContentText("A " + amount + " ml sip is ready for " + name + ".")
                .setContentIntent(contentIntent)
                .setCategory(android.app.Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setLocalOnly(true)
                .build();
    }

    static void showReminder(Context context, int id) {
        showReminder(context, id, HydrationPrefs.isReminderAlarmEnabled(context, id));
    }

    static void showReminder(Context context, int id, boolean soundAlarm) {
        createChannel(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            throw new IllegalStateException("Notification service is unavailable");
        }
        int amount = HydrationPrefs.getReminderAmount(context, id);
        String title = ReminderActivity.getTitle(context, id);
        String message = ReminderActivity.getMessage(context, id);

        Intent openReminder = new Intent(context, ReminderActivity.class)
                .putExtra(ReminderActivity.EXTRA_REMINDER_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 830 + id, openReminder,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent drank = new Intent(context, ReminderActionReceiver.class)
                .setAction(ReminderActionReceiver.ACTION_DRANK)
                .putExtra(ReminderActivity.EXTRA_REMINDER_ID, id)
                .putExtra(ReminderActionReceiver.EXTRA_AMOUNT_ML, amount);
        PendingIntent drankIntent = PendingIntent.getBroadcast(context, 840 + id, drank,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent snooze = new Intent(context, ReminderActionReceiver.class)
                .setAction(ReminderActionReceiver.ACTION_SNOOZE)
                .putExtra(ReminderActivity.EXTRA_REMINDER_ID, id)
                .putExtra(ReminderActionReceiver.EXTRA_AMOUNT_ML, amount);
        PendingIntent snoozeIntent = PendingIntent.getBroadcast(context, 850 + id, snooze,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification.Builder builder =
                new android.app.Notification.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_stat_water_alarm)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setContentIntent(contentIntent)
                        .setCategory(soundAlarm ? android.app.Notification.CATEGORY_ALARM
                                : android.app.Notification.CATEGORY_REMINDER)
                        .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
                        .setPriority(soundAlarm ? android.app.Notification.PRIORITY_MAX
                                : android.app.Notification.PRIORITY_HIGH)
                        .addAction(R.drawable.ic_stat_water_alarm, "I drank it", drankIntent)
                        .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze 10 min", snoozeIntent);
        if (soundAlarm) {
            PendingIntent fullScreenIntent = PendingIntent.getActivity(context, 860 + id, openReminder,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.setFullScreenIntent(fullScreenIntent, true).setOngoing(true);
        }
        android.app.Notification notification = builder.build();
        manager.notify(REMINDER_NOTIFICATION_BASE + id, notification);
    }

    static void showStreakProtection(Context context, int id) {
        createChannel(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            throw new IllegalStateException("Notification service is unavailable");
        }
        Intent openApp = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 950 + id, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        int stage = HydrationPrefs.getStreakBeforeToday(context);
        android.app.Notification notification = new android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_water_alarm)
                .setContentTitle("Your " + stage + "-day sip streak is waiting")
                .setContentText("One sip today keeps your caring-for-yourself streak going. No pressure.")
                .setContentIntent(contentIntent)
                .setCategory(android.app.Notification.CATEGORY_REMINDER)
                .setVisibility(android.app.Notification.VISIBILITY_PRIVATE)
                .setPriority(android.app.Notification.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build();
        manager.notify(7600 + id, notification);
    }

    static void clearReminder(Context context, int id) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            throw new IllegalStateException("Notification service is unavailable");
        }
        manager.cancel(REMINDER_NOTIFICATION_BASE + id);
        manager.cancel(SERVICE_NOTIFICATION_BASE + id);
    }

    static void clearPlaybackNotification(Context context, int id) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            throw new IllegalStateException("Notification service is unavailable");
        }
        manager.cancel(SERVICE_NOTIFICATION_BASE + id);
    }
}
