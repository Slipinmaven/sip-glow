package com.sashawater;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

public class ReminderActivity extends Activity {
    static final String EXTRA_REMINDER_ID = "reminder_id";
    private static final int INK = 0xFF352938;
    private static final int MUTED = 0xFF8E8290;
    private static final int PINK = 0xFFBA6686;
    private static final int CREAM = 0xFFFFF8F5;
    private static final int DEEP_PLUM = 0xFF493047;

    private int reminderId;
    private int amount;
    private int total;
    private int goal;
    private TextView response;
    private TextView progress;
    private LinearLayout page;
    private boolean loggedForThisReminder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WaterTheme.applyReminder(this);
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(WaterTheme.surfaceColor(this, DEEP_PLUM));
        getWindow().setNavigationBarColor(WaterTheme.surfaceColor(this, DEEP_PLUM));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if (savedInstanceState != null) {
            loggedForThisReminder = savedInstanceState.getBoolean("logged_for_reminder", false);
        }
        reminderId = getIntent().getIntExtra(EXTRA_REMINDER_ID, 0);
        if (reminderId < 0 || reminderId >= HydrationPrefs.getReminderTimes(this).size()) {
            reminderId = 0;
        }
        amount = HydrationPrefs.getReminderAmount(this, reminderId);
        total = HydrationPrefs.getTodayIntake(this);
        goal = HydrationPrefs.getDailyGoal(this);
        render();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("logged_for_reminder", loggedForThisReminder);
        super.onSaveInstanceState(outState);
    }

    static String getTitle(android.content.Context context, int reminderId) {
        String timeOfDay = getReminderPeriod(context, reminderId);
        if (reminderId == 0 || HydrationPrefs.getTodayIntake(context) == 0) {
            return "Good " + timeOfDay + ", " + HydrationPrefs.getName(context);
        }
        return "A little water, " + HydrationPrefs.getName(context) + "?";
    }

    static String getMessage(android.content.Context context, int reminderId) {
        String name = HydrationPrefs.getName(context);
        int total = HydrationPrefs.getTodayIntake(context);
        int goal = HydrationPrefs.getDailyGoal(context);
        if (total >= goal) {
            return "You’ve reached today’s goal, " + name
                    + ". Beautiful care — listen to what your body needs.";
        }
        if (total == 0 && reminderId > 0) {
            return "We missed our earlier sip together. No worries — every moment is a fresh start.";
        }
        if (total == 0) {
            String timeOfDay = getReminderPeriod(context, reminderId);
            return "Ready to start your " + timeOfDay + " with a lovely first glass?";
        }
        return name + ", you’re at " + total + " ml. There’s still time for a few gentle sips.";
    }

    private static String getReminderPeriod(android.content.Context context, int reminderId) {
        java.util.List<String> times = HydrationPrefs.getReminderTimes(context);
        if (reminderId < 0 || reminderId >= times.size()) {
            return getTimeOfDay(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        }
        try {
            return getTimeOfDay(Integer.parseInt(times.get(reminderId).split(":")[0]));
        } catch (NumberFormatException exception) {
            return getTimeOfDay(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        }
    }

    private static String getTimeOfDay(int hour) {
        if (hour < 12) {
            return "morning";
        }
        return hour < 17 ? "afternoon" : "evening";
    }

    private void render() {
        String name = HydrationPrefs.getName(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(WaterTheme.surfaceColor(this, CREAM));
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(24), dp(34), dp(24), dp(30));
        scroll.addView(page);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                page.setPadding(dp(24), dp(27) + bars.top, dp(24), dp(26) + bars.bottom);
                return insets;
            });
        }

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER);
        WaterIconView brandMark = new WaterIconView(this, WaterIconView.FLOWER, 0xFFF1D4DF);
        brand.addView(brandMark, margin(dp(19), dp(19), 0, 0, dp(7), 0));
        TextView brandName = text("SIPGLOW", 11, 0xFFF1D4DF, true);
        brandName.setLetterSpacing(0.14f);
        brand.addView(brandName);
        page.addView(brand, margin(WRAP, WRAP, 0, 0, 0, dp(30)));

        LinearLayout card = column();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(22), dp(25), dp(22), dp(23));
        card.setBackground(gradientBackground(DEEP_PLUM, 0xFF76526C, 27));
        card.setElevation(dp(5));

        WaterIconView flower = new WaterIconView(this, WaterIconView.FLOWER, 0xFFFFD8BD);
        flower.setBackground(background(0x26FFFFFF, 50));
        card.addView(flower, margin(dp(82), dp(82), 0, 0, 0, dp(20)));

        String reminderTime = HydrationPrefs.getReminderTimes(this).get(reminderId);
        String period = getReminderPeriod(this, reminderId);
        boolean alarmEnabled = HydrationPrefs.isReminderAlarmEnabled(this, reminderId);
        String checkIn = String.format(Locale.getDefault(), "%s REMINDER  ·  %s  ·  %s",
                period.toUpperCase(Locale.getDefault()), formatReminderTime(reminderTime),
                alarmEnabled ? "ALARM" : "NOTIFICATION");
        TextView eyebrow = text(checkIn, 9, 0xFFF2D6E1, true);
        eyebrow.setLetterSpacing(0.15f);
        card.addView(eyebrow, margin(WRAP, WRAP, 0, 0, 0, dp(12)));

        TextView title = text(getTitle(this, reminderId), 27, 0xFFFFFFFF, true);
        title.setGravity(Gravity.CENTER);
        card.addView(title, margin(MATCH, WRAP, 0, 0, 0, dp(9)));

        TextView message = text(getMessage(this, reminderId), 14, 0xFFF0E5EE, false);
        message.setGravity(Gravity.CENTER);
        card.addView(message, margin(MATCH, WRAP, 0, 0, 0, dp(19)));

        LinearLayout sip = new LinearLayout(this);
        sip.setGravity(Gravity.CENTER);
        sip.setPadding(dp(12), dp(9), dp(12), dp(9));
        sip.setBackground(background(0x29FFFFFF, 16));
        WaterIconView sipIcon = new WaterIconView(this, WaterIconView.DROP, 0xFFFFD8BD);
        sip.addView(sipIcon, margin(dp(18), dp(18), 0, 0, dp(9), 0));
        sip.addView(text("Your reminder sip", 12, 0xFFF0E5EE, false),
                margin(WRAP, WRAP, 0, 0, dp(9), 0));
        sip.addView(text(formatAmount(amount), 15, 0xFFFFFFFF, true));
        card.addView(sip);
        page.addView(card, margin(MATCH, WRAP, 0, 0, 0, dp(18)));

        LinearLayout progressCard = column();
        progressCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        progressCard.setBackground(background(0xFFFFFFFF, 21));
        LinearLayout progressRow = new LinearLayout(this);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        WaterIconView icon = new WaterIconView(this,
                total >= goal ? WaterIconView.STAR : WaterIconView.CLOCK,
                total >= goal ? 0xFFD19A46 : PINK);
        progressRow.addView(icon, margin(dp(36), dp(36), 0, 0, dp(11), 0));
        LinearLayout progressCopy = column();
        progressCopy.addView(text("TODAY’S LITTLE JOURNEY", 9, MUTED, true));
        progress = text("", 16, INK, true);
        progressCopy.addView(progress, margin(MATCH, WRAP, 0, dp(4), 0, 0));
        progressRow.addView(progressCopy, new LinearLayout.LayoutParams(0, WRAP, 1));
        progressCard.addView(progressRow);
        response = text("", 12, MUTED, false);
        progressCard.addView(response, margin(MATCH, WRAP, 0, dp(12), 0, 0));
        page.addView(progressCard, margin(MATCH, WRAP, 0, 0, 0, dp(19)));
        updateJourney();

        TextView drank = text(loggedForThisReminder
                        ? "Your " + formatAmount(amount) + " sip is logged  ✨"
                        : "I drank it  ·  log " + formatAmount(amount),
                14, 0xFFFFFFFF, true);
        drank.setGravity(Gravity.CENTER);
        drank.setBackground(background(loggedForThisReminder ? 0xFF65846C : PINK, 17));
        drank.setElevation(dp(3));
        drank.setOnClickListener(view -> markDrank());
        drank.setEnabled(!loggedForThisReminder);
        page.addView(drank, margin(MATCH, dp(57), 0, 0, 0, dp(9)));

        TextView later = textButton("Remind me in 10 minutes", PINK, 13);
        later.setGravity(Gravity.CENTER);
        later.setBackground(background(0xFFF8EAF0, 16));
        later.setOnClickListener(view -> snooze());
        later.setVisibility(loggedForThisReminder ? View.GONE : View.VISIBLE);
        page.addView(later, margin(MATCH, dp(50), 0, 0, 0, dp(13)));

        TextView close = textButton(loggedForThisReminder ? "Done for now" : "Not right now",
                MUTED, 11);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(view -> dismiss());
        page.addView(close, margin(MATCH, dp(42), 0, 0, 0, dp(16)));

        TextView kindness = text("No pressure, " + name + ". Every sip is a small act of care.",
                11, MUTED, false);
        kindness.setGravity(Gravity.CENTER);
        page.addView(kindness, margin(MATCH, WRAP, 0, 0, 0, 0));
        page.setContentDescription("Water reminder for " + name);
        setContentView(scroll);
    }

    private void updateJourney() {
        if (loggedForThisReminder) {
            progress.setText(formatAmount(total) + " of " + formatAmount(goal));
            response.setText(total >= goal
                    ? "Beautifully done, " + HydrationPrefs.getName(this)
                            + "! Your daily goal is complete."
                    : "Beautifully done! " + formatAmount(Math.max(0, goal - total))
                            + " to go at your own pace.");
            return;
        }
        int afterSip = total + amount;
        progress.setText(formatAmount(total) + " of " + formatAmount(goal));
        if (total >= goal) {
            response.setText("You’ve reached today’s goal. Take a moment to celebrate, and listen to your body.");
        } else if (afterSip >= goal) {
            response.setText("This sip brings you to your goal. Ready to light up your badge?");
        } else if (total == 0 && reminderId > 0) {
            response.setText("We missed an earlier check-in today. Start fresh with this " + amount + " ml sip.");
        } else if (total == 0) {
            response.setText("Your first sip is ready whenever you are.");
        } else if (total < goal * (reminderId + 1)
                / HydrationPrefs.getReminderTimes(this).size()) {
            response.setText("A few gentle sips can help you catch up — no rush.");
        } else {
            response.setText("You’re finding your flow. Keep being kind to yourself.");
        }
    }

    private void markDrank() {
        if (loggedForThisReminder) {
            return;
        }
        HydrationPrefs.addIntake(this, amount);
        total += amount;
        loggedForThisReminder = true;
        ReminderActionReceiver.stopAlarm(this, reminderId);
        setResult(Activity.RESULT_OK, new Intent().putExtra(EXTRA_REMINDER_ID, reminderId));
        render();
    }

    private void snooze() {
        ReminderActionReceiver.scheduleSnooze(this, reminderId);
        ReminderActionReceiver.stopAlarm(this, reminderId);
        finish();
    }

    private void dismiss() {
        ReminderActionReceiver.stopAlarm(this, reminderId);
        finish();
    }

    private TextView textButton(String label, int color, int size) {
        TextView button = text(label, size, color, true);
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(size);
        result.setTextColor(WaterTheme.textColor(this, color));
        result.setTypeface(Typeface.create("sans-serif-rounded",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        return result;
    }

    private LinearLayout column() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(WaterTheme.surfaceColor(this, color));
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private GradientDrawable gradientBackground(int start, int end, int radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{
                WaterTheme.surfaceColor(this, start), WaterTheme.surfaceColor(this, end)});
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private LinearLayout.LayoutParams margin(int width, int height, int left, int top,
                                             int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String formatAmount(int amount) {
        return String.format(Locale.getDefault(), "%,d ml", amount);
    }

    private String formatReminderTime(String time) {
        String[] parts = time.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        return String.format(Locale.getDefault(), "%d:%02d %s",
                hour % 12 == 0 ? 12 : hour % 12, minute, hour < 12 ? "AM" : "PM");
    }

}
