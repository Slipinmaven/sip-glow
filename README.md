# SipGlow

SipGlow is an offline-first Android hydration reminder app. It helps you build
a water-drinking routine with scheduled reminders, sip tracking, daily goals,
and animated streak rewards.

Your name, reminders, goals, theme preference, alarm tone, and sip history are
stored locally on your device. SipGlow has no account, cloud sync, or internet
permission.

## Features

### Reminders

- Three daily reminders are included by default: 9:00 AM, 2:00 PM, and 7:00 PM.
- Add up to seven more reminders and customize their time, repeat days, and
  suggested sip amount.
- Choose a local alarm tone or use the device default. Turn the alarm off for
  an individual reminder to receive a notification instead.
- Open a dedicated reminder screen to log the suggested sip, snooze for
  10 minutes, or dismiss the reminder.
- Three fixed streak-care notifications run at 9:00 PM, 10:00 PM, and 11:00 PM
  when a previous streak needs a sip check-in. These check-ins are not editable.
- Reminders are scheduled by Android and restored after a device restart or a
  time or time-zone change. They do not require the app to remain open.

### Hydration tracking

- Log quick or custom sip amounts. Each entry records its amount and time.
- View history for today, this week, all time, or a custom date range; undo the
  latest sip or clear the history.
- Start with a 3,000 ml daily goal and customize it in Settings.
- Earn one star for every 100 ml logged.

### Goals and streaks

- See progress toward the daily goal and receive messages based on time of day,
  logged intake, goal progress, and streak status.
- Build a daily streak by logging at least one sip. Animated flame badges
  change at 1-, 3-, 7-, 14-, and 30-day milestones.
- Preview the next streak badge and celebrate goal completion, goal milestones,
  and earned stars with animations.
- Late-night prompts avoid encouraging forced catch-up drinking. Hydration
  goals are personal prompts, not medical requirements.

### Personalization

- Enter your name during setup and change it later in Settings.
- Follow the device's system light or dark theme by default, or choose Light or
  Dark explicitly.
- Customize your daily goal, reminder schedule, reminder sip sizes, and alarm
  tone.

## Requirements

- Android 8.0 (API 26) or later
- Android Studio with the Android SDK and Java 17

## Build

1. Open the project folder in Android Studio and allow Gradle sync to finish.
2. Connect an Android device running API 26 or later, or start an emulator.
3. Select the `app` run configuration and press **Run**.

To build a debug APK from PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Reminder permissions and reliability

Allow notifications so reminders can be shown. On Android 12 and later, allow
exact alarms for the most reliable reminder timing. On Android 14 and later,
allow full-screen alarms if you want the dedicated reminder screen to open over
the lock screen.

If exact-alarm access is unavailable, Android schedules inexact reminders.
Alarm volume, Do Not Disturb, notification settings, battery management, and
manufacturer-specific background restrictions can affect reminder timing or
sound. Force-stopping the app in Android Settings can prevent alarms from
running until the app is opened again.

## Privacy and storage

SipGlow keeps its app data in private on-device storage and excludes it from
Android cloud backups. Data is not sent to a server. Uninstalling the app or
clearing its storage removes saved history and preferences.
