package com.sashawater;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

final class HydrationPrefs {
    static final String THEME_SYSTEM = "system";
    static final String THEME_LIGHT = "light";
    static final String THEME_DARK = "dark";
    static final int DEFAULT_TARGET_ML = 3000;
    static final int DEFAULT_REMINDER_COUNT = 3;
    static final int MAX_REMINDER_COUNT = 10;
    private static final String PREFS = "sasha_water";
    private static final String TIMES = "reminder_times";
    private static final String TONE = "alarm_tone";

    static final class Sip {
        final int amountMl;
        final long timestamp;

        Sip(int amountMl, long timestamp) {
            this.amountMl = amountMl;
            this.timestamp = timestamp;
        }
    }

    private HydrationPrefs() {}

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String getName(Context context) {
        return preferences(context).getString("name", "friend");
    }

    static boolean hasSavedName(Context context) {
        String name = preferences(context).getString("name", "");
        return name != null && !name.trim().isEmpty();
    }

    static void setName(Context context, String name) {
        preferences(context).edit().putString("name", name.trim()).apply();
    }

    static int getDailyGoal(Context context) {
        return preferences(context).getInt("daily_goal", DEFAULT_TARGET_ML);
    }

    static void setDailyGoal(Context context, int amount) {
        if (amount < 250 || amount > 10000) {
            throw new IllegalArgumentException("Daily goal must be between 250 and 10,000 ml");
        }
        preferences(context).edit().putInt("daily_goal", amount).apply();
    }

    static boolean areRemindersEnabled(Context context) {
        return preferences(context).getBoolean("reminders_enabled", true);
    }

    static void setRemindersEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("reminders_enabled", enabled).apply();
    }

    static List<String> getReminderTimes(Context context) {
        String[] defaults = {"09:00", "14:00", "19:00"};
        String[] values = preferences(context).getString(TIMES, String.join(",", defaults)).split(",");
        int count = Math.max(DEFAULT_REMINDER_COUNT,
                Math.min(MAX_REMINDER_COUNT, values.length));
        List<String> times = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String fallback = i < defaults.length ? defaults[i] : "17:00";
            times.add(i < values.length && values[i].matches("\\d{2}:\\d{2}")
                    ? values[i] : fallback);
        }
        return times;
    }

    static int addReminder(Context context, String time) {
        List<String> times = getReminderTimes(context);
        if (times.size() >= MAX_REMINDER_COUNT) {
            throw new IllegalStateException("You can have up to " + MAX_REMINDER_COUNT
                    + " daily reminders.");
        }
        int index = times.size();
        times.add(time);
        preferences(context).edit()
                .putString(TIMES, String.join(",", times))
                .putBoolean("reminder_enabled_" + index, true)
                .putBoolean("reminder_alarm_enabled_" + index, true)
                .putInt("reminder_days_" + index, 0b1111111)
                .putInt("reminder_amount_" + index, 250)
                .apply();
        return index;
    }

    static void removeCustomReminder(Context context, int index) {
        List<String> times = getReminderTimes(context);
        if (times.size() <= DEFAULT_REMINDER_COUNT
                || index < DEFAULT_REMINDER_COUNT || index >= times.size()) {
            return;
        }
        SharedPreferences prefs = preferences(context);
        List<Boolean> enabled = new ArrayList<>();
        List<Boolean> alarmEnabled = new ArrayList<>();
        List<Integer> dayMasks = new ArrayList<>();
        List<Integer> amounts = new ArrayList<>();
        for (int i = DEFAULT_REMINDER_COUNT; i < times.size(); i++) {
            if (i != index) {
                enabled.add(isReminderEnabled(context, i));
                alarmEnabled.add(isReminderAlarmEnabled(context, i));
                dayMasks.add(getReminderDayMask(context, i));
                amounts.add(getReminderAmount(context, i));
            }
        }
        times.remove(index);
        SharedPreferences.Editor editor = prefs.edit().putString(TIMES, String.join(",", times));
        for (int i = DEFAULT_REMINDER_COUNT; i < MAX_REMINDER_COUNT; i++) {
            editor.remove("reminder_enabled_" + i)
                    .remove("reminder_alarm_enabled_" + i)
                    .remove("reminder_days_" + i)
                    .remove("reminder_amount_" + i);
        }
        for (int i = 0; i < enabled.size(); i++) {
            int target = DEFAULT_REMINDER_COUNT + i;
            editor.putBoolean("reminder_enabled_" + target, enabled.get(i))
                    .putBoolean("reminder_alarm_enabled_" + target, alarmEnabled.get(i))
                    .putInt("reminder_days_" + target, dayMasks.get(i))
                    .putInt("reminder_amount_" + target, amounts.get(i));
        }
        editor.apply();
    }

    static boolean isDarkTheme(Context context) {
        String mode = getThemeMode(context);
        if (THEME_DARK.equals(mode)) {
            return true;
        }
        if (THEME_LIGHT.equals(mode)) {
            return false;
        }
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    static String getThemeMode(Context context) {
        return preferences(context).getString("theme_mode", THEME_SYSTEM);
    }

    static void setThemeMode(Context context, String mode) {
        if (!THEME_SYSTEM.equals(mode) && !THEME_LIGHT.equals(mode)
                && !THEME_DARK.equals(mode)) {
            throw new IllegalArgumentException("Unknown theme mode: " + mode);
        }
        preferences(context).edit().putString("theme_mode", mode).apply();
    }

    static boolean isReminderEnabled(Context context, int index) {
        return preferences(context).getBoolean("reminder_enabled_" + index, true);
    }

    static void setReminderEnabled(Context context, int index, boolean enabled) {
        preferences(context).edit().putBoolean("reminder_enabled_" + index, enabled).apply();
    }

    static boolean isReminderAlarmEnabled(Context context, int index) {
        return preferences(context).getBoolean("reminder_alarm_enabled_" + index, true);
    }

    static void setReminderAlarmEnabled(Context context, int index, boolean enabled) {
        preferences(context).edit()
                .putBoolean("reminder_alarm_enabled_" + index, enabled).apply();
    }

    static int getReminderDayMask(Context context, int index) {
        return preferences(context).getInt("reminder_days_" + index, 0b1111111);
    }

    static void setReminderDayMask(Context context, int index, int dayMask) {
        if (dayMask < 1 || dayMask > 0b1111111) {
            throw new IllegalArgumentException("Choose at least one reminder day");
        }
        preferences(context).edit().putInt("reminder_days_" + index, dayMask).apply();
    }

    static void resetReminderDefaults(Context context) {
        SharedPreferences prefs = preferences(context);
        SharedPreferences.Editor editor = prefs.edit().remove(TIMES).remove("reminders_enabled");
        for (int i = 0; i < MAX_REMINDER_COUNT; i++) {
            editor.remove("reminder_enabled_" + i)
                    .remove("reminder_alarm_enabled_" + i)
                    .remove("reminder_days_" + i)
                    .remove("reminder_amount_" + i);
        }
        editor.apply();
    }

    static int getReminderAmount(Context context, int index) {
        return preferences(context).getInt("reminder_amount_" + index, 250);
    }

    static void setReminderAmount(Context context, int index, int amount) {
        if (amount < 1 || amount > 5000) {
            throw new IllegalArgumentException("Reminder amount must be between 1 and 5,000 ml");
        }
        preferences(context).edit().putInt("reminder_amount_" + index, amount).apply();
    }

    static void setReminderTime(Context context, int index, String time) {
        List<String> times = getReminderTimes(context);
        times.set(index, time);
        preferences(context).edit().putString(TIMES, String.join(",", times)).apply();
    }

    static Uri getAlarmTone(Context context) {
        String value = preferences(context).getString(TONE, null);
        return value == null ? null : Uri.parse(value);
    }

    static void setAlarmTone(Context context, Uri tone) {
        SharedPreferences.Editor editor = preferences(context).edit();
        if (tone == null) {
            editor.remove(TONE);
        } else {
            editor.putString(TONE, tone.toString());
        }
        editor.apply();
    }

    static String getTodayKey() {
        return dayKey(Calendar.getInstance());
    }

    static String getDayKey(int daysAgo) {
        Calendar day = Calendar.getInstance();
        day.add(Calendar.DAY_OF_YEAR, -daysAgo);
        return dayKey(day);
    }

    static String getDayLabel(String key) {
        if (key.equals(getTodayKey())) {
            return "Today";
        }
        if (key.equals(getDayKey(1))) {
            return "Yesterday";
        }
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(key);
            return date == null ? key : new SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())
                    .format(date);
        } catch (java.text.ParseException exception) {
            return key;
        }
    }

    static List<String> getRecordedDayKeys(Context context) {
        String today = getTodayKey();
        getEntries(context, today);
        Set<String> days = new TreeSet<>(Collections.reverseOrder());
        for (String key : preferences(context).getAll().keySet()) {
            if (key.startsWith("sip_entries_")) {
                String dayKey = key.substring("sip_entries_".length());
                if (!getEntries(context, dayKey).isEmpty()) {
                    days.add(dayKey);
                }
            }
        }
        return new ArrayList<>(days);
    }

    static void clearSipHistory(Context context) {
        SharedPreferences prefs = preferences(context);
        SharedPreferences.Editor editor = prefs.edit()
                .remove("intake_date")
                .remove("intake_total")
                .remove("intake_entries");
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("sip_entries_")) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    static long getLifetimeIntake(Context context) {
        long total = 0;
        for (String dayKey : getRecordedDayKeys(context)) {
            total += getIntake(context, dayKey);
        }
        return total;
    }

    static int getStars(Context context) {
        return (int) Math.min(Integer.MAX_VALUE, getLifetimeIntake(context) / 100);
    }

    static int getCurrentStreak(Context context) {
        int streak = 0;
        int firstDay = isTodayStreakActive(context) ? 0 : 1;
        for (int daysAgo = firstDay; ; daysAgo++) {
            if (getIntake(context, getDayKey(daysAgo)) == 0) {
                return streak;
            }
            streak++;
        }
    }

    static boolean isTodayStreakActive(Context context) {
        return getTodayIntake(context) > 0;
    }

    static int getStreakBeforeToday(Context context) {
        int streak = 0;
        for (int daysAgo = 1; ; daysAgo++) {
            if (getIntake(context, getDayKey(daysAgo)) == 0) {
                return streak;
            }
            streak++;
        }
    }

    static List<Sip> getEntries(Context context, String dayKey) {
        SharedPreferences prefs = preferences(context);
        String stored = prefs.getString(entriesKey(dayKey), null);
        if (stored == null && dayKey.equals(prefs.getString("intake_date", ""))) {
            List<Sip> migrated = new ArrayList<>();
            String legacyEntries = prefs.getString("intake_entries", "");
            if (!legacyEntries.isEmpty()) {
                long timestamp = System.currentTimeMillis();
                for (String entry : legacyEntries.split(",")) {
                    try {
                        int amount = Integer.parseInt(entry);
                        if (amount > 0) {
                            migrated.add(new Sip(amount, timestamp++));
                        }
                    } catch (NumberFormatException ignored) {
                        // Keep valid legacy entries when an older stored entry is malformed.
                    }
                }
            }
            int legacyTotal = Math.max(0, prefs.getInt("intake_total", 0));
            int migratedTotal = 0;
            for (Sip sip : migrated) {
                migratedTotal += sip.amountMl;
            }
            if (legacyTotal > migratedTotal) {
                migrated.add(new Sip(legacyTotal - migratedTotal, System.currentTimeMillis()));
            }
            storeEntries(prefs, dayKey, migrated);
            return migrated;
        }
        List<Sip> entries = new ArrayList<>();
        if (stored == null || stored.isEmpty()) {
            return entries;
        }
        try {
            JSONArray array = new JSONArray(stored);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    int amount = item.optInt("amount");
                    long timestamp = item.optLong("time");
                    if (amount > 0 && timestamp > 0) {
                        entries.add(new Sip(amount, timestamp));
                    }
                }
            }
        } catch (JSONException exception) {
            throw new IllegalStateException("Could not read saved sip history for " + dayKey, exception);
        }
        return entries;
    }

    static int getIntake(Context context, String dayKey) {
        int total = 0;
        for (Sip sip : getEntries(context, dayKey)) {
            total += sip.amountMl;
        }
        return total;
    }

    static int getTodayIntake(Context context) {
        return getIntake(context, getTodayKey());
    }

    static List<Sip> getTodayEntries(Context context) {
        return getEntries(context, getTodayKey());
    }

    static void addIntake(Context context, int amount) {
        if (amount < 1 || amount > 5000) {
            throw new IllegalArgumentException("Sip amount must be between 1 and 5,000 ml");
        }
        String today = getTodayKey();
        List<Sip> entries = getEntries(context, today);
        entries.add(new Sip(amount, System.currentTimeMillis()));
        storeEntries(preferences(context), today, entries);
    }

    static int undoLastIntake(Context context) {
        String today = getTodayKey();
        List<Sip> entries = getEntries(context, today);
        if (entries.isEmpty()) {
            return 0;
        }
        int removed = entries.remove(entries.size() - 1).amountMl;
        storeEntries(preferences(context), today, entries);
        return removed;
    }

    private static void storeEntries(SharedPreferences prefs, String dayKey, List<Sip> entries) {
        JSONArray array = new JSONArray();
        for (Sip sip : entries) {
            JSONObject item = new JSONObject();
            try {
                item.put("amount", sip.amountMl);
                item.put("time", sip.timestamp);
                array.put(item);
            } catch (JSONException exception) {
                throw new IllegalStateException("Could not save sip history", exception);
            }
        }
        prefs.edit().putString(entriesKey(dayKey), array.toString()).apply();
    }

    private static String entriesKey(String dayKey) {
        return "sip_entries_" + dayKey;
    }

    private static String dayKey(Calendar calendar) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }
}
