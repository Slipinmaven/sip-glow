package com.sashawater;

import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.io.IOException;

public class ReminderAlarmService extends Service {
    private static final String TAG = "SashaWaterAlarm";
    private MediaPlayer player;
    private int reminderId = -1;
    private boolean keepReminderNotification;

    @Override
    public void onCreate() {
        super.onCreate();
        ReminderNotifications.createChannel(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int id = intent == null ? -1
                : intent.getIntExtra(ReminderActivity.EXTRA_REMINDER_ID, -1);
        if (id < 0 || id >= HydrationPrefs.getReminderTimes(this).size()) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        reminderId = id;
        startForeground(ReminderNotifications.SERVICE_NOTIFICATION_BASE + id,
                ReminderNotifications.buildServiceNotification(this, id));
        ReminderNotifications.showReminder(this, id);
        playAlarmSound();
        return START_NOT_STICKY;
    }

    private void playAlarmSound() {
        stopPlayer();
        Uri selected = HydrationPrefs.getAlarmTone(this);
        Uri defaultAlarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (selected != null) {
            try {
                startPlayer(selected);
                return;
            } catch (IOException | IllegalArgumentException | SecurityException exception) {
                Log.w(TAG, "Could not play the selected alarm tone; using the device default.",
                        exception);
                stopPlayer();
            }
        }
        if (defaultAlarm != null) {
            try {
                startPlayer(defaultAlarm);
                return;
            } catch (IOException | IllegalArgumentException | SecurityException exception) {
                Log.e(TAG, "Could not play the device's default alarm tone.", exception);
                stopPlayer();
            }
        }
        Log.e(TAG, "No playable alarm tone is available on this device.");
        fallbackToNotification();
    }

    private void fallbackToNotification() {
        keepReminderNotification = true;
        ReminderNotifications.showReminder(this, reminderId, false);
        stopSelf();
    }

    private void startPlayer(Uri tone) throws IOException {
        MediaPlayer nextPlayer = new MediaPlayer();
        nextPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        nextPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        nextPlayer.setDataSource(this, tone);
        nextPlayer.setLooping(true);
        nextPlayer.setOnErrorListener((failedPlayer, what, extra) -> {
            Log.e(TAG, "Alarm playback failed. what=" + what + ", extra=" + extra);
            fallbackToNotification();
            return true;
        });
        player = nextPlayer;
        nextPlayer.setOnPreparedListener(MediaPlayer::start);
        nextPlayer.prepareAsync();
    }

    void stopPlayer() {
        if (player == null) {
            return;
        }
        player.setOnPreparedListener(null);
        player.setOnErrorListener(null);
        if (player.isPlaying()) {
            player.stop();
        }
        player.release();
        player = null;
    }

    @Override
    public void onDestroy() {
        stopPlayer();
        if (reminderId >= 0) {
            if (keepReminderNotification) {
                ReminderNotifications.clearPlaybackNotification(this, reminderId);
            } else {
                ReminderNotifications.clearReminder(this, reminderId);
            }
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
