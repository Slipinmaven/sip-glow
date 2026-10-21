# SipGlow

A small, offline-first Android water reminder app. Sip-by-sip history, your name,
daily goal, reminder times, and selected alarm tone are stored privately on the
phone. The app has no internet permission, account, or cloud sync.

## Features

- On first launch, asks what to call you and uses that name in greetings and
  reminders. Change it any time in Settings.
- Follows the phone's system light or dark appearance by default. Choose
  System, Light, or Dark in Settings; an explicit choice is saved on the phone.
- Starts with three daily reminders at 9:00 AM, 2:00 PM, and 7:00 PM.
- Add up to seven extra daily reminders. Each reminder has its own
  daily/weekday/weekend/custom repeat days, enable
  switch, time, and sip size. Choose 250 ml, 500 ml, 750 ml, 1,000 ml, or a
  custom amount. Its alarm sound can be switched off independently; the
  reminder still arrives as a notification.
- Three fixed, notification-only streak-care check-ins run at 9 PM, 10 PM, and
  11 PM if the user has an existing sip streak and has not logged a sip today.
  These background reminders are not editable.
- A dedicated, interactive reminder page offers to log the configured sip,
  snooze for 10 minutes, or dismiss. Reminders play a looping alarm sound until
  the user responds; choose a local audio file or use the device default tone.
  Selected files keep their read permission so custom tones remain available
  after the app closes or the device restarts.
- The home screen tracks progress toward a 3,000 ml daily goal and celebrates
  completed goals, daily sip streaks, and water-earned stars. A streak day
  activates with any logged sip and is shown as a living, animated flame; missed
  days are muted. Flame colors change at 1, 3, 7, 14, and 30-day milestones.
  Every 100 ml logged earns one star, so a 500 ml drink earns five stars. The
  daily goal is customizable in millilitres. Progress rings and completed-goal
  cards use rounded clipping and avoid clipped square-edged glow shadows.
- Home-screen greetings and progress nudges adapt to the time of day, logged
  amount, distance from the daily goal, and active streak. Completing the goal
  gives the progress card a layered plum-and-gold finish with a subtle corner
  glow and matching luminous gold meter, without a moving stripe.
- Evening reminders are context-aware: they distinguish an inactive streak,
  a streak that needs today’s check-in, a goal that is close, and a goal that
  is still far away. Late-night copy avoids urging large catch-up drinks or
  sacrificing sleep; hydration goals are prompts, not medical requirements.
- The next streak badge is previewed with its own brighter stage color, and
  active flames become more vivid as a streak advances, blending stage-specific
  rose, violet, sapphire, emerald, and radiant multicolor gradients. The flame
  halo uses a soft radial fade rather than a clipped shadow that can leave
  square edges around the icon.
- Logging a sip keeps the screen visible: the quick-add button wiggles, earned
  tiny, gold-filled stars fly from the sip button into the saved star balance,
  and the counter increases as each star lands. Passing a daily goal opens a
  dedicated gold-themed celebration that shows the overage without urging the
  user to drink more. The flame ignites on the first sip of a new streak day,
  with milestone celebrations and changing flame colors at 1, 3, 7, 14, and
  30 days.
- Log a quick sip or type any custom amount directly in millilitres, with the
  time and amount saved for every entry. Filter saved history by today, this
  week, all time, or a custom date range; empty days are not shown. Undo your
  latest entry.
- Sip logging animates the progress ring and a celebratory sip card. Reaching
  the goal and going beyond it have distinct celebrations; the beyond-goal
  message celebrates progress without encouraging the user to drink more.
- Personalize your name, daily water goal, reminders, and alarm tone in Settings.
- Switch between the light rose-cream theme and a dark plum theme in Settings.
  The selected theme is saved on the phone and applied whenever the app opens.
- Restore the original three daily reminder times and 250 ml sip amounts without
  deleting your hydration history.
- Clear all sip history from the History tab when you want to reset test data;
  this also resets the streak and earned-star balance.
- Consistent drawn icons replace device-dependent text symbols in navigation,
  settings, and reminder check-ins. Decorative stars are richly filled, and
  flower marks use layered petals and a luminous center.
- Your sip history remains available after closing and reopening the app.
- Restores reminders after a device reboot and reschedules them after a time
  or time-zone change.

App data is stored in private on-device storage and is not included in Android
cloud backups. Uninstalling the app or clearing its storage removes the saved
history.

## Build

Open this project in Android Studio and run the `app` configuration on a device
running Android 8.0 (API 26) or later.

Notifications must be allowed for reminders to appear. On Android 12 and later,
allow exact alarms in the app's reminder settings for the most reliable timing.
On Android 14 and later, allow full-screen alarms for the dedicated check-in to
open over the lock screen. The phone's alarm volume and Do Not Disturb settings
can affect audible playback. If exact-alarm access is unavailable, the app
schedules inexact reminders instead. Android and some manufacturers may apply
device-specific background restrictions to alarms.
