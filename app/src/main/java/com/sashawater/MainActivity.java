package com.sashawater;

import android.animation.ValueAnimator;
import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.DatePicker;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String TAG = "SashaWater";
    private static final int REQUEST_NOTIFICATIONS = 41;
    private static final int REQUEST_ALARM_TONE = 42;
    private static final int REQUEST_EXACT_ALARMS = 43;
    private static final int TAB_TODAY = 0;
    private static final int TAB_HISTORY = 1;
    private static final int TAB_SETTINGS = 2;
    private static final int HISTORY_TODAY = 0;
    private static final int HISTORY_WEEK = 1;
    private static final int HISTORY_ALL = 2;
    private static final int HISTORY_CUSTOM = 3;

    private static final int INK = 0xFF352938;
    private static final int MUTED = 0xFF8E8290;
    private static final int PINK = 0xFFBA6686;
    private static final int CREAM = 0xFFFFF8F5;
    private static final int PALE_PINK = 0xFFFFEAF0;
    private static final int DEEP_PLUM = 0xFF493047;
    private static final int WHITE = 0xFFFFFFFF;

    private int currentTab = TAB_TODAY;
    private LinearLayout root;
    private LinearLayout content;
    private FrameLayout screenFrame;
    private TextView reminderStatus;
    private Integer progressBeforeUpdate;
    private WaterIconView todayStreakFlame;
    private WaterIconView streakBadgeIcon;
    private TextView starsScore;
    private int historyFilter = HISTORY_WEEK;
    private String historyFrom;
    private String historyTo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WaterTheme.applyMain(this);
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        boolean dark = WaterTheme.isDark(this);
        window.setStatusBarColor(WaterTheme.surfaceColor(this, CREAM));
        window.setNavigationBarColor(WaterTheme.surfaceColor(this, CREAM));
        int systemUiFlags = dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (!dark && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            systemUiFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(systemUiFlags);
        if (savedInstanceState != null) {
            currentTab = savedInstanceState.getInt("selected_tab", TAB_TODAY);
        }
        ReminderNotifications.createChannel(this);
        ReminderScheduler.scheduleAll(this);
        renderScreen();
        if (!HydrationPrefs.hasSavedName(this)) {
            screenFrame.post(this::showFirstRunNameDialog);
        }
        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("selected_tab", currentTab);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ReminderScheduler.scheduleAll(this);
        if (reminderStatus != null) {
            updateReminderStatus();
        }
    }

    private void renderScreen() {
        root = column();
        root.setBackgroundColor(WaterTheme.surfaceColor(this, CREAM));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(WaterTheme.surfaceColor(this, CREAM));
        content = column();
        content.setPadding(dp(22), dp(18), dp(22), dp(26));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                content.setPadding(dp(22), dp(16) + bars.top, dp(22), dp(24));
                return insets;
            });
        }
        boolean needsName = !HydrationPrefs.hasSavedName(this);
        if (needsName) {
            addTopBar();
            renderWelcomePreview();
        }
        finishRenderingScreen();
    }

    private void renderWelcomePreview() {
        content.addView(text("Hydration, in your own rhythm.", 29, INK, true),
                margin(MATCH, WRAP, 0, dp(25), 0, dp(9)));
        content.addView(text("A thoughtful little companion for your everyday water habit.",
                        14, MUTED, false),
                margin(MATCH, WRAP, 0, 0, 0, dp(20)));
        LinearLayout welcomeCard = card();
        welcomeCard.addView(text("YOUR WATER ROUTINE", 10, PINK, true));
        welcomeCard.addView(text("Daily reminders, sip history, and a streak badge—kept private on this phone.",
                        13, MUTED, false),
                margin(MATCH, WRAP, 0, dp(8), 0, 0));
        content.addView(welcomeCard, margin(MATCH, WRAP, 0, 0, 0, 0));
    }

    private interface DialogAction {
        boolean run();
    }

    private Dialog showStyledDialog(String title, String subtitle, View body,
                                String negativeLabel, String positiveLabel,
                                DialogAction positiveAction) {
            Dialog dialog = new Dialog(this);
            LinearLayout panel = column();
            panel.setPadding(dp(22), dp(22), dp(22), dp(17));
            panel.setBackground(background(WHITE, 26));
            panel.setElevation(dp(10));
            panel.addView(text(title, 20, INK, true));
            if (subtitle != null && !subtitle.isEmpty()) {
                panel.addView(text(subtitle, 12, MUTED, false),
                margin(MATCH, WRAP, 0, dp(6), 0, body == null ? dp(8) : dp(13)));
            } else if (body != null) {
                panel.addView(new View(this), margin(MATCH, dp(5), 0, 0, 0, 0));
            }
            if (body != null) {
                panel.addView(body, margin(MATCH, WRAP, 0, 0, 0, dp(15)));
            }

            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            if (negativeLabel != null) {
                TextView cancel = text(negativeLabel, 12, MUTED, true);
                cancel.setGravity(Gravity.CENTER);
                cancel.setBackground(background(WaterTheme.isDark(this)
                ? 0xFF372B3B : 0xFFF5EEF1, 15));
                cancel.setOnClickListener(view -> dialog.dismiss());
                actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(46), 1));
            }
            if (positiveLabel != null) {
                TextView confirm = text(positiveLabel, 12, WHITE, true);
                confirm.setGravity(Gravity.CENTER);
                confirm.setBackground(gradientBackground(PINK, 0xFFD58BA4, 15));
                confirm.setOnClickListener(view -> {
            if (positiveAction == null || positiveAction.run()) {
                dialog.dismiss();
            }
                });
                LinearLayout.LayoutParams confirmParams =
                new LinearLayout.LayoutParams(0, dp(46), 1);
                if (negativeLabel != null) {
            confirmParams.leftMargin = dp(9);
                }
                actions.addView(confirm, confirmParams);
            }
            panel.addView(actions);
            dialog.setContentView(panel);
            dialog.setCancelable(true);
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                window.setLayout(Math.min(screenWidth - dp(36), dp(430)),
                WindowManager.LayoutParams.WRAP_CONTENT);
                WindowManager.LayoutParams attributes = window.getAttributes();
                attributes.dimAmount = 0.48f;
                window.setAttributes(attributes);
            }
            return dialog;
        }

    private void showChoiceDialog(String title, String subtitle, String[] choices,
                              int selected, java.util.function.IntConsumer onSelected) {
            LinearLayout options = column();
            options.setPadding(0, dp(3), 0, 0);
            Dialog[] dialog = new Dialog[1];
            for (int i = 0; i < choices.length; i++) {
                final int choice = i;
                boolean isSelected = i == selected;
                TextView option = text((isSelected ? "●  " : "○  ") + choices[i],
                13, isSelected ? PINK : INK, true);
                option.setGravity(Gravity.CENTER_VERTICAL);
                option.setPadding(dp(14), 0, dp(12), 0);
                option.setBackground(background(isSelected ? 0xFFFFEAF0 : WHITE, 14));
                option.setOnClickListener(view -> {
            dialog[0].dismiss();
            onSelected.accept(choice);
                });
                options.addView(option, margin(MATCH, dp(48), 0,
                i == 0 ? 0 : dp(7), 0, 0));
            }
            dialog[0] = showStyledDialog(title, subtitle, options, "Cancel", null, null);
        }

    private void updateDayChoice(TextView row, String label, boolean selected) {
            row.setText((selected ? "✓   " : "○   ") + label);
            row.setTextColor(WaterTheme.textColor(this, selected ? PINK : INK));
        }

    private void showTimePickerDialog(String title, String time,
                                  java.util.function.BiConsumer<Integer, Integer> onTime) {
            String[] parts = time.split(":");
            int initialHour = Integer.parseInt(parts[0]);
            int initialMinute = Integer.parseInt(parts[1]);
            NumberPicker hour = new NumberPicker(this);
            hour.setMinValue(1);
            hour.setMaxValue(12);
            hour.setValue(initialHour % 12 == 0 ? 12 : initialHour % 12);
            hour.setWrapSelectorWheel(true);
            NumberPicker minute = new NumberPicker(this);
            minute.setMinValue(0);
            minute.setMaxValue(59);
            String[] minuteLabels = new String[60];
            for (int i = 0; i < minuteLabels.length; i++) {
                minuteLabels[i] = String.format(Locale.US, "%02d", i);
            }
            minute.setDisplayedValues(minuteLabels);
            minute.setValue(initialMinute);
            minute.setWrapSelectorWheel(true);
            NumberPicker meridiem = new NumberPicker(this);
            meridiem.setMinValue(0);
            meridiem.setMaxValue(1);
            meridiem.setDisplayedValues(new String[]{"AM", "PM"});
            meridiem.setValue(initialHour < 12 ? 0 : 1);
            meridiem.setWrapSelectorWheel(false);

            LinearLayout pickers = new LinearLayout(this);
            pickers.setGravity(Gravity.CENTER);
            pickers.setOrientation(LinearLayout.HORIZONTAL);
            pickers.setPadding(0, dp(4), 0, dp(4));
            pickers.addView(hour, new LinearLayout.LayoutParams(0, dp(132), 1));
            pickers.addView(minute, new LinearLayout.LayoutParams(0, dp(132), 1));
            pickers.addView(meridiem, new LinearLayout.LayoutParams(0, dp(132), 1));
            showStyledDialog(title, "Choose the hour, minute, and time of day.",
            pickers, "Cancel", "Save time", () -> {
                int hour24 = hour.getValue() % 12 + meridiem.getValue() * 12;
                onTime.accept(hour24, minute.getValue());
                return true;
            });
        }

        private void showFirstRunNameDialog() {
            if (HydrationPrefs.hasSavedName(this)) {
                return;
            }
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setHint("Your name");
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            LinearLayout container = new LinearLayout(this);
            container.setPadding(0, dp(6), 0, 0);
            styleInput(input);
            container.addView(input, new LinearLayout.LayoutParams(MATCH, dp(56)));
            Dialog dialog = showStyledDialog("Welcome! What should I call you?",
            "Your name personalizes greetings and hydration reminders. You can change it later in Settings.",
            container, null, "Let’s go", () -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) {
                    input.setError("Enter your name to get started");
                    return false;
                }
                HydrationPrefs.setName(this, name);
                screenFrame.post(this::renderScreen);
                return true;
            });
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            input.requestFocus();
            Window dialogWindow = dialog.getWindow();
            if (dialogWindow != null) {
                dialogWindow.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        }

    private EditText amountInput(int amount) {
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            input.setText(String.valueOf(amount));
            input.setTextSize(24);
            input.setTypeface(Typeface.create("sans-serif-rounded", Typeface.BOLD));
            input.setTextColor(WaterTheme.textColor(this, INK));
            input.setHintTextColor(WaterTheme.textColor(this, MUTED));
            input.setHint("ml");
            input.setSelectAllOnFocus(true);
            input.setGravity(Gravity.CENTER);
            input.setPadding(dp(12), 0, dp(12), 0);
            input.setBackground(background(0xFFFFF0F4, 16));
            return input;
        }

    private void styleInput(EditText input) {
            input.setTextSize(16);
            input.setTextColor(WaterTheme.textColor(this, INK));
            input.setHintTextColor(WaterTheme.textColor(this, MUTED));
            input.setTypeface(Typeface.create("sans-serif-rounded", Typeface.NORMAL));
            input.setPadding(dp(14), 0, dp(14), 0);
            input.setBackground(background(0xFFFFF0F4, 15));
        }

    private void addAmountPresets(LinearLayout parent, EditText input, int[] values) {
            LinearLayout presets = new LinearLayout(this);
            presets.setGravity(Gravity.CENTER);
            for (int value : values) {
                TextView preset = text(String.format(Locale.getDefault(), "%,d ml", value),
                values.length > 4 ? 8 : 10, PINK, true);
                preset.setGravity(Gravity.CENTER);
                preset.setBackground(background(0xFFFFEAF0, 12));
                preset.setOnClickListener(view -> input.setText(String.valueOf(value)));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(39), 1);
                if (presets.getChildCount() > 0) {
            params.leftMargin = dp(6);
                }
                presets.addView(preset, params);
            }
            parent.addView(presets);
        }

    private int parseAmount(EditText input) {
            try {
                return Integer.parseInt(input.getText().toString().trim());
            } catch (NumberFormatException exception) {
                return 0;
            }
        }

        private void showFeedback(String message) {
            if (screenFrame == null) {
                return;
            }
            TextView feedback = text(message, 12, WHITE, true);
            feedback.setGravity(Gravity.CENTER);
            feedback.setPadding(dp(18), dp(13), dp(18), dp(13));
            feedback.setBackground(gradientBackground(DEEP_PLUM, 0xFF74516D, 17));
            feedback.setElevation(dp(8));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    WRAP, WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            params.setMargins(dp(20), 0, dp(20), dp(88));
            screenFrame.addView(feedback, params);
            feedback.setAlpha(0f);
            feedback.setTranslationY(dp(18));
            feedback.animate().alpha(1f).translationY(0f).setDuration(180).start();
            feedback.postDelayed(() -> feedback.animate().alpha(0f).translationY(dp(10))
                    .setDuration(220).withEndAction(() -> {
                        if (feedback.getParent() == screenFrame) {
                            screenFrame.removeView(feedback);
                        }
                    }).start(), 1850);
        }

        private void finishRenderingScreen() {
        if (!HydrationPrefs.hasSavedName(this)) {
            root.addView(buildBottomNavigation(),
                    new LinearLayout.LayoutParams(MATCH, dp(72)));
            screenFrame = new FrameLayout(this);
            screenFrame.addView(root, new FrameLayout.LayoutParams(MATCH, MATCH));
            setContentView(screenFrame);
            return;
        }
        addTopBar();
        if (currentTab == TAB_TODAY) {
            renderToday();
        } else if (currentTab == TAB_HISTORY) {
            renderHistory();
        } else {
            renderSettings();
        }
        root.addView(buildBottomNavigation(), new LinearLayout.LayoutParams(MATCH, dp(72)));
        screenFrame = new FrameLayout(this);
        screenFrame.addView(root, new FrameLayout.LayoutParams(MATCH, MATCH));
        setContentView(screenFrame);
    }

    private void addTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        WaterIconView mark = new WaterIconView(this, WaterIconView.FLOWER, PINK);
        bar.addView(mark, margin(dp(34), dp(40), 0, 0, dp(8), 0));
        LinearLayout labels = column();
        labels.addView(text("SIPGLOW", 12, INK, true));
        TextView subtitle = text(
                currentTab == TAB_TODAY ? "YOUR LITTLE MOMENT OF CARE"
                        : currentTab == TAB_HISTORY ? "YOUR HYDRATION JOURNAL" : "MADE JUST FOR YOU",
                9, MUTED, true);
        subtitle.setLetterSpacing(0.12f);
        labels.addView(subtitle, margin(WRAP, WRAP, 0, dp(3), 0, 0));
        bar.addView(labels, new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView avatar = text("S", 16, PINK, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(background(0xFFF4E1E9, 50));
        bar.addView(avatar, margin(dp(38), dp(38), 0, 0, 0, 0));
        content.addView(bar, margin(MATCH, WRAP, 0, 0, 0, dp(22)));
    }

    private void renderToday() {
        String name = HydrationPrefs.getName(this);
        int total = HydrationPrefs.getTodayIntake(this);
        int goal = HydrationPrefs.getDailyGoal(this);
        int streak = HydrationPrefs.getCurrentStreak(this);
        int streakBeforeToday = HydrationPrefs.getStreakBeforeToday(this);
        TextView date = text(new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
                .format(new Date()).toUpperCase(Locale.getDefault()), 10, PINK, true);
        date.setLetterSpacing(0.1f);
        content.addView(date, margin(MATCH, WRAP, 0, 0, 0, dp(9)));
        content.addView(text(getTimeGreeting(name), 29, INK, true),
                margin(MATCH, WRAP, 0, 0, 0, dp(7)));
        content.addView(text(getHydrationNudge(total, goal, streak, streakBeforeToday),
                        13, MUTED, false),
                margin(MATCH, WRAP, 0, 0, 0, dp(19)));
        boolean goalReached = total >= goal;
        FrameLayout heroFrame = new FrameLayout(this);
        heroFrame.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(26));
            }
        });
        heroFrame.setClipToOutline(true);
        if (goalReached) {
            heroFrame.addView(new GoalPremiumBackground(),
                    new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        LinearLayout hero = column();
        hero.setPadding(dp(21), dp(19), dp(21), dp(20));
        if (!goalReached) {
            hero.setBackground(gradientBackground(DEEP_PLUM, 0xFF74516D, 26));
        }
        LinearLayout heroHeader = new LinearLayout(this);
        heroHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text(goalReached ? "TODAY’S GOAL · COMPLETE" : "TODAY’S WATER PROGRESS",
                10, goalReached ? 0xFFFFE7AA : 0xFFF2CFD9, true);
        heading.setLetterSpacing(0.12f);
        if (goalReached) {
            GradientDrawable statusPill = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0x44F7C663, 0x1EF7C663});
            statusPill.setCornerRadius(dp(18));
            statusPill.setStroke(dp(1), 0x77FFD987);
            heading.setPadding(dp(10), dp(6), dp(10), dp(6));
            heading.setBackground(statusPill);
            heroHeader.addView(heading, new LinearLayout.LayoutParams(WRAP, WRAP));
            heroHeader.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
            WaterIconView achieved = new WaterIconView(this, WaterIconView.STAR, 0xFFFFD36B);
            achieved.setGlowing(true);
            achieved.setContentDescription("Daily water goal reached");
            heroHeader.addView(achieved, new LinearLayout.LayoutParams(dp(27), dp(27)));
            achieved.animate().rotation(360f).scaleX(1.18f).scaleY(1.18f).setDuration(1150)
                    .withEndAction(() -> achieved.animate().rotation(0f).scaleX(1f).scaleY(1f)
                            .setDuration(800).start()).start();
        } else {
            heroHeader.addView(heading, new LinearLayout.LayoutParams(0, WRAP, 1));
        }
        hero.addView(heroHeader);

        LinearLayout meterRow = new LinearLayout(this);
        meterRow.setGravity(Gravity.CENTER_VERTICAL);
        ProgressRing progressRing = new ProgressRing(this, goalReached);
        if (progressBeforeUpdate != null) {
            progressRing.animateProgress(progressBeforeUpdate / (float) goal, total / (float) goal);
            progressBeforeUpdate = null;
        } else {
            progressRing.setProgress(total / (float) goal);
        }
        meterRow.addView(progressRing, margin(dp(112), dp(112), 0, dp(15), dp(17), 0));
        LinearLayout meterCopy = column();
        TextView number = text(String.format(Locale.getDefault(), "%,d", total), 35, WHITE, true);
        meterCopy.addView(number);
        meterCopy.addView(text("ml so far", 13, 0xFFE9DCE8, false),
                margin(MATCH, WRAP, 0, dp(1), 0, dp(9)));
        View divider = new View(this);
        divider.setBackgroundColor(0x44FFFFFF);
        meterCopy.addView(divider, margin(dp(34), dp(1), 0, 0, 0, dp(9)));
        TextView goalText = text(String.format(Locale.getDefault(), "%,d ml goal", goal),
                12, 0xFFE9DCE8, false);
        meterCopy.addView(goalText);
        meterRow.addView(meterCopy, new LinearLayout.LayoutParams(0, WRAP, 1));
        hero.addView(meterRow);

        LinearLayout dayProgress = new LinearLayout(this);
        dayProgress.setGravity(Gravity.CENTER_VERTICAL);
        dayProgress.setPadding(dp(12), dp(10), dp(12), dp(10));
        dayProgress.setBackground(background(goalReached ? 0x33FFE9A4 : 0x22FFFFFF, 15));
        dayProgress.addView(text(goalReached ? "✦" : "✓", 14,
                goalReached ? 0xFFFFE49D : 0xFFFFD7B5, true),
                margin(WRAP, WRAP, 0, 0, dp(8), 0));
        String message = getProgressCardMessage(total, goal, streak, streakBeforeToday);
        dayProgress.addView(text(message, 12, WHITE, false));
        hero.addView(dayProgress, margin(MATCH, WRAP, 0, dp(14), 0, 0));
        heroFrame.addView(hero, new FrameLayout.LayoutParams(MATCH, WRAP));
        heroFrame.setElevation(dp(goalReached ? 9 : 5));
        content.addView(heroFrame, margin(MATCH, WRAP, 0, 0, 0, dp(21)));
        addRewardsCard();

        LinearLayout sipHeading = new LinearLayout(this);
        sipHeading.setGravity(Gravity.CENTER_VERTICAL);
        sipHeading.addView(text("A sip, your way", 18, INK, true),
                new LinearLayout.LayoutParams(0, WRAP, 1));
        sipHeading.addView(text("Every sip is saved", 10, MUTED, false));
        content.addView(sipHeading, margin(MATCH, WRAP, 0, 0, 0, dp(11)));

        LinearLayout quickButtons = new LinearLayout(this);
        quickButtons.setOrientation(LinearLayout.HORIZONTAL);
        addSipButton(quickButtons, 200, false);
        addSipButton(quickButtons, 350, false);
        addSipButton(quickButtons, 500, true);
        content.addView(quickButtons, margin(MATCH, dp(58), 0, 0, 0, dp(10)));
        TextView custom = textButton("＋  Add a custom amount", PINK, 12);
        custom.setGravity(Gravity.CENTER);
        custom.setBackground(background(0xFFFFF0F4, 16));
        custom.setOnClickListener(view -> showCustomSipDialog());
        content.addView(custom, margin(MATCH, dp(46), 0, 0, 0, dp(19)));

        addRecentSips();
        addTodayReminderPreview();
    }

    private String getTimeGreeting(String name) {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        String greeting = hour < 12 ? "Good morning"
                : hour < 17 ? "Good afternoon"
                : hour < 21 ? "Good evening" : "A little late-night care";
        return greeting + ", " + name + ".";
    }

    private String getTimeOfDay() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return "this morning";
        if (hour < 17) return "this afternoon";
        if (hour < 21) return "this evening";
        return "tonight";
    }

    private String getHydrationNudge(int total, int goal, int streak, int streakBeforeToday) {
        String name = HydrationPrefs.getName(this);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (total >= goal) {
            if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 12) {
                return "Goal met before noon, " + name + ". Take a moment to enjoy that win.";
            }
            if (streak >= 7) {
                return "You met your goal " + getTimeOfDay() + " with a " + streak
                        + "-day streak glowing behind you.";
            }
            return "Your goal is complete " + getTimeOfDay()
                    + ". You showed up for yourself today.";
        }
        String eveningMessage = getEveningHydrationNudge(total, goal, streak,
                streakBeforeToday, hour);
        if (eveningMessage != null) {
            return eveningMessage;
        }
        if (total == 0 && streakBeforeToday > 0) {
            int nextStage = nextStreakStage(streakBeforeToday);
            if (streakBeforeToday + 1 == nextStage) {
                return "Your " + streakBeforeToday + "-day streak is one sip from "
                        + getStreakStageName(nextStage).substring(0,
                        getStreakStageName(nextStage).indexOf(" ·")) + ".";
            }
            return "You’ve carried your " + streakBeforeToday + "-day streak into today. "
                    + "One sip keeps it alive.";
        }
        if (total == 0) {
            return hour < 12
                    ? "A fresh day, " + name + ". A first sip starts today’s streak."
                    : "Your streak is still waiting to begin, " + name
                    + ". Start with a sip that feels comfortable.";
        }
        int remaining = goal - total;
        int percent = (int) (total * 100f / goal);
        if (remaining <= Math.max(100, goal / 10)) {
            return String.format(Locale.getDefault(),
                    "You’re at %d%%, just %,d ml from your goal. Your streak is already safe today.",
                    percent, remaining);
        }
        if (streak > 0) {
            int nextStage = nextStreakStage(streak);
            String streakLine = streak + " days strong";
            if (nextStage > streak && nextStage - streak <= 2) {
                streakLine += ", with " + (nextStage - streak) + " "
                        + (nextStage - streak == 1 ? "day" : "days") + " to "
                        + getStreakStageName(nextStage).substring(0,
                        getStreakStageName(nextStage).indexOf(" ·"));
            }
            return String.format(Locale.getDefault(),
                    "%s. You’re at %d%% of your goal; %,d ml remain.",
                    streakLine, percent, remaining);
        }
        if (percent >= 60) {
            return String.format(Locale.getDefault(),
                    "You’re past halfway at %d%%. Just %,d ml remains.",
                    percent, remaining);
        }
        return String.format(Locale.getDefault(),
                "%,d ml logged %s. Today’s streak is active; keep your goal in view.",
                total, getTimeOfDay());
    }

    private String getEveningHydrationNudge(int total, int goal, int streak,
                                            int streakBeforeToday, int hour) {
        if (hour < 17) {
            return null;
        }
        int remaining = Math.max(0, goal - total);
        int closeThreshold = Math.max(500, goal / 5);
        boolean closeToGoal = total > 0 && remaining <= closeThreshold;
        boolean afterNine = hour >= 21;
        boolean veryLate = hour >= 23;
        String clockTime = hour == 21 ? "9 p.m." : hour == 22 ? "10 p.m." : "11 p.m.";

        if (veryLate) {
            if (total == 0 && streakBeforeToday > 0) {
                int nextStage = nextStreakStage(streakBeforeToday);
                String milestone = streakBeforeToday + 1 == nextStage
                        ? " One sip also unlocks " + getStreakStageName(nextStage)
                        .substring(0, getStreakStageName(nextStage).indexOf(" ·")) + "."
                        : "";
                return "It’s 11 p.m. and your " + streakBeforeToday
                        + "-day streak is waiting for today’s check-in."
                        + milestone + " If you’re thirsty, have a small sip; don’t force a late catch-up.";
            }
            if (total == 0) {
                return "It’s 11 p.m. and today’s streak hasn’t started. Your wellbeing comes first—"
                        + "don’t force water or lose sleep chasing a number. Begin fresh tomorrow.";
            }
            if (closeToGoal) {
                return String.format(Locale.getDefault(),
                        "It’s 11 p.m.—only %,d ml remain, and your streak is safe. You’re close, "
                                + "but don’t force a late catch-up; listen to your body.",
                        remaining);
            }
            return String.format(Locale.getDefault(),
                    "It’s 11 p.m. Your streak is safe today; %,d ml remain. No need to rush or "
                            + "drink beyond comfort—rest matters too.", remaining);
        }

        if (afterNine) {
            if (total == 0 && streakBeforeToday > 0) {
                int nextStage = nextStreakStage(streakBeforeToday);
                String milestone = streakBeforeToday + 1 == nextStage
                        ? " You’re also one day from " + getStreakStageName(nextStage)
                        .substring(0, getStreakStageName(nextStage).indexOf(" ·")) + "."
                        : "";
                return "It’s " + clockTime + "—your " + streakBeforeToday
                        + "-day streak isn’t checked in yet."
                        + milestone + " One comfortable sip tonight keeps it burning.";
            }
            if (total == 0) {
                return "It’s " + clockTime + " and today’s streak is still inactive. "
                        + "If you’re thirsty, a small sip can start it—no need to overdo it.";
            }
            if (closeToGoal) {
                return String.format(Locale.getDefault(),
                        "It’s %s and you’re only %,d ml from your goal. Your streak is safe—"
                                + "you’re so close; finish only if it feels comfortable.",
                        clockTime, remaining);
            }
            return String.format(Locale.getDefault(),
                    "It’s %s; today’s streak is safe, with %,d ml left toward your goal. "
                            + "Take it at a comfortable pace.", clockTime, remaining);
        }

        if (total == 0 && streakBeforeToday > 0) {
            return "Evening check-in: your " + streakBeforeToday
                    + "-day streak is waiting for today’s sip. Keep that flame alive.";
        }
        if (total == 0) {
            return "Evening check-in: today’s streak hasn’t started yet. One sip can light it.";
        }
        if (closeToGoal) {
            return String.format(Locale.getDefault(),
                    "You’re so close this evening—only %,d ml to your goal. Your streak is active.",
                    remaining);
        }
        return String.format(Locale.getDefault(),
                "Your streak is active. You have %,d ml left; keep sipping at your own pace.",
                remaining);
    }

    private int nextStreakStage(int streak) {
        if (streak < 1) return 1;
        if (streak < 3) return 3;
        if (streak < 7) return 7;
        if (streak < 14) return 14;
        return 30;
    }

    private String getProgressCardMessage(int total, int goal, int streak, int streakBeforeToday) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (total >= goal) {
            if (streak >= 3) {
                return "Goal complete " + getTimeOfDay() + " · day " + streak
                        + " of your streak. That’s a beautiful kind of consistency.";
            }
            return "Goal complete " + getTimeOfDay()
                    + ". Pause, celebrate, and let your body guide you from here.";
        }
        String eveningMessage = getEveningProgressMessage(total, goal, streak,
                streakBeforeToday, hour);
        if (eveningMessage != null) {
            return eveningMessage;
        }
        if (total == 0 && streakBeforeToday > 0) {
            int nextStage = nextStreakStage(streakBeforeToday);
            if (streakBeforeToday + 1 == nextStage) {
                return "One sip today lights day " + nextStage + " and unlocks "
                        + getStreakStageName(nextStage).substring(0,
                        getStreakStageName(nextStage).indexOf(" ·")) + ".";
            }
            return "Your " + streakBeforeToday
                    + "-day flame is waiting for today’s first sip.";
        }
        int remaining = goal - total;
        if (total == 0) {
            return Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 12
                    ? "A new page. Begin with a sip when you’re ready."
                    : "Your day is still unfolding. Begin whenever it feels right.";
        }
        if (remaining <= Math.max(100, goal / 10)) {
            return String.format(Locale.getDefault(), "The home stretch: %,d ml to your goal.",
                    remaining);
        }
        if (streak > 0) {
            int nextStage = nextStreakStage(streak);
            if (nextStage - streak <= 2) {
                return "Day " + streak + " is burning bright. "
                        + (nextStage - streak) + " more to unlock "
                        + getStreakStageName(nextStage).substring(0,
                        getStreakStageName(nextStage).indexOf(" ·")) + ".";
            }
        }
        if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 17) {
            return String.format(Locale.getDefault(),
                    "%,d ml still to go %s. Be gentle with yourself.",
                    remaining, getTimeOfDay());
        }
        return String.format(Locale.getDefault(), "%,d ml to go. Every little sip adds up.",
                remaining);
    }

    private String getEveningProgressMessage(int total, int goal, int streak,
                                             int streakBeforeToday, int hour) {
        if (hour < 17) {
            return null;
        }
        int remaining = Math.max(0, goal - total);
        boolean close = total > 0 && remaining <= Math.max(500, goal / 5);
        if (hour >= 23) {
            if (total == 0 && streakBeforeToday > 0) {
                int nextStage = nextStreakStage(streakBeforeToday);
                return streakBeforeToday + 1 == nextStage
                        ? "11 p.m. · One small sip keeps your streak and unlocks "
                        + getStreakStageName(nextStage).substring(0,
                        getStreakStageName(nextStage).indexOf(" ·")) + "."
                        : "11 p.m. · Your " + streakBeforeToday
                        + "-day streak needs today’s check-in. Don’t force it or lose sleep.";
            }
            if (total == 0) {
                return "11 p.m. · No sip logged today. Rest is more important than forcing a goal.";
            }
            return close
                    ? String.format(Locale.getDefault(),
                    "11 p.m. · Only %,d ml remain. Your streak is safe; don’t force a late catch-up.",
                    remaining)
                    : String.format(Locale.getDefault(),
                    "11 p.m. · Your streak is safe. %,d ml remain; listen to your body and rest.",
                    remaining);
        }
        if (hour >= 21) {
            String time = hour == 21 ? "9 p.m." : "10 p.m.";
            if (total == 0 && streakBeforeToday > 0) {
                int nextStage = nextStreakStage(streakBeforeToday);
                return streakBeforeToday + 1 == nextStage
                        ? time + " · One sip keeps your " + streakBeforeToday
                        + "-day streak and unlocks " + getStreakStageName(nextStage)
                        .substring(0, getStreakStageName(nextStage).indexOf(" ·")) + "."
                        : time + " · Your " + streakBeforeToday
                        + "-day streak is waiting for today’s sip.";
            }
            if (total == 0) {
                return time + " · Your streak hasn’t started today. A comfortable sip can light it.";
            }
            return close
                    ? String.format(Locale.getDefault(),
                    "%s · You’re so close—%,d ml to go. Your streak is safe.", time, remaining)
                    : String.format(Locale.getDefault(),
                    "%s · Streak safe. %,d ml remain; keep a comfortable pace.", time, remaining);
        }
        if (total == 0 && streakBeforeToday > 0) {
            return "Evening · Keep your " + streakBeforeToday
                    + "-day streak alive with today’s first sip.";
        }
        if (total == 0) {
            return "Evening · Your streak is waiting to be lit today.";
        }
        if (close) {
            return String.format(Locale.getDefault(),
                    "Evening · Just %,d ml from your goal; today’s streak is active.", remaining);
        }
        return String.format(Locale.getDefault(),
                "Evening · Streak active; %,d ml remain toward your goal.", remaining);
    }

    private void addRewardsCard() {
        LinearLayout rewards = column();
        rewards.setPadding(dp(15), dp(12), dp(15), dp(12));
        rewards.setBackground(background(0xFFFFF0F3, 19));

        boolean activeToday = HydrationPrefs.isTodayStreakActive(this);
        int streak = HydrationPrefs.getCurrentStreak(this);
        int streakBeforeToday = HydrationPrefs.getStreakBeforeToday(this);
        int badgeColor = getStreakStageColor(streak);
        LinearLayout rewardTop = new LinearLayout(this);
        rewardTop.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout badge = new LinearLayout(this);
        badge.setGravity(Gravity.CENTER_VERTICAL);
        streakBadgeIcon = new WaterIconView(this, WaterIconView.STREAK, badgeColor);
        streakBadgeIcon.setFlameActive(activeToday);
        streakBadgeIcon.setGlowing(activeToday);
        streakBadgeIcon.setGlowStrength(getStreakGlowStrength(streak));
        streakBadgeIcon.setBackground(background(activeToday ? 0xFFFFE8D8 : 0xFFE6E1E8, 36));
        badge.addView(streakBadgeIcon, margin(dp(42), dp(42), 0, 0, dp(9), 0));
        LinearLayout badgeCopy = column();
        badgeCopy.addView(text(getStreakStageName(streak), 12, INK, true));
        badgeCopy.addView(text(activeToday ? "Today’s streak is active"
                        : streak > 0 ? "Not started today · streak at risk"
                        : "Today’s streak is not started",
                9, MUTED, false), margin(MATCH, WRAP, 0, dp(2), 0, 0));
        badge.addView(badgeCopy);
        rewardTop.addView(badge, new LinearLayout.LayoutParams(0, WRAP, 1));

        View divider = new View(this);
        divider.setBackgroundColor(WaterTheme.surfaceColor(this, 0xFFEAD9E0));
        rewardTop.addView(divider, margin(dp(1), dp(35), dp(5), 0, dp(12), 0));

        LinearLayout scoreCopy = column();
        scoreCopy.setGravity(Gravity.END);
        LinearLayout starsRow = new LinearLayout(this);
        starsRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        WaterIconView rewardStar = new WaterIconView(this, WaterIconView.STAR, 0xFFE9A83E);
        starsRow.addView(rewardStar, margin(dp(16), dp(16), 0, 0, dp(4), 0));
        starsScore = text(HydrationPrefs.getStars(this) + " stars", 12, 0xFFDEA83A, true);
        starsRow.addView(starsScore);
        scoreCopy.addView(starsRow);
        scoreCopy.addView(text(streak + (streak == 1 ? " day" : " days"), 12,
                badgeColor, true));
        scoreCopy.addView(text(activeToday ? "active today"
                        : streak > 0 ? "streak at risk" : "today’s streak",
                9, MUTED, false),
                margin(WRAP, WRAP, 0, dp(3), 0, 0));
        rewardTop.addView(scoreCopy);
        rewards.addView(rewardTop);

        TextView weekLabel = text("YOUR LAST 7 DAYS", 8, MUTED, true);
        weekLabel.setLetterSpacing(0.12f);
        rewards.addView(weekLabel, margin(MATCH, WRAP, 0, dp(12), 0, dp(7)));
        LinearLayout weekStrip = new LinearLayout(this);
        weekStrip.setGravity(Gravity.CENTER);
        todayStreakFlame = null;
        int consecutiveDays = 0;
        for (int daysAgo = 6; daysAgo >= 0; daysAgo--) {
            String key = HydrationPrefs.getDayKey(daysAgo);
            boolean dayActive = HydrationPrefs.getIntake(this, key) > 0;
            consecutiveDays = dayActive ? consecutiveDays + 1 : 0;
            boolean isToday = daysAgo == 0;
            int dayTint = dayActive ? getStreakStageColor(consecutiveDays) : 0xFFCFC4CD;
            LinearLayout day = column();
            day.setGravity(Gravity.CENTER);
            WaterIconView star = new WaterIconView(this, WaterIconView.STREAK, dayTint);
            star.setFlameActive(dayActive);
            star.setGlowing(dayActive);
            star.setGlowStrength(getStreakGlowStrength(consecutiveDays));
            day.addView(star, new LinearLayout.LayoutParams(dp(24), dp(28)));
            if (isToday) {
                todayStreakFlame = star;
            }
            Calendar dayDate = Calendar.getInstance();
            dayDate.add(Calendar.DAY_OF_YEAR, -daysAgo);
            String weekday = new SimpleDateFormat("EE", Locale.getDefault())
                    .format(dayDate.getTime());
            day.addView(text(isToday ? "TODAY" : weekday.toUpperCase(Locale.getDefault()),
                    7, isToday ? badgeColor : MUTED, isToday || dayActive),
                    margin(WRAP, WRAP, 0, dp(4), 0, 0));
            day.setContentDescription((isToday ? "Today" : weekday) +
                    (dayActive ? ", sip streak active" : ", no sip logged"));
            weekStrip.addView(day, new LinearLayout.LayoutParams(0, WRAP, 1));
        }
        rewards.addView(weekStrip);
        int nextStage = nextStreakStage(activeToday ? streak : streakBeforeToday);
        int projectedStreak = activeToday ? streak : streakBeforeToday + 1;
        int daysUntilBadge = Math.max(0, nextStage - projectedStreak);
        int nextColor = getStreakStageColor(nextStage);
        LinearLayout upcoming = new LinearLayout(this);
        upcoming.setGravity(Gravity.CENTER_VERTICAL);
        upcoming.setPadding(dp(12), dp(9), dp(12), dp(9));
        GradientDrawable upcomingBackground = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{withAlpha(nextColor, 42), 0x18221A2B});
        upcomingBackground.setCornerRadius(dp(16));
        upcomingBackground.setStroke(dp(1), withAlpha(nextColor, 118));
        upcoming.setBackground(upcomingBackground);
        upcoming.setElevation(dp(2));
        WaterIconView previewFlame = new WaterIconView(this, WaterIconView.STREAK, nextColor);
        previewFlame.setFlameActive(true);
        previewFlame.setGlowing(true);
        previewFlame.setGlowStrength(getStreakGlowStrength(nextStage));
        upcoming.addView(previewFlame, margin(dp(34), dp(38), 0, 0, dp(10), 0));
        LinearLayout previewCopy = column();
        String nextName = getStreakStageName(nextStage);
        String nextTitle = nextName.substring(0, nextName.indexOf(" ·")).toUpperCase(Locale.getDefault());
        previewCopy.addView(text("NEXT FLAME  ·  " + nextTitle, 9, nextColor, true));
        String previewMessage = daysUntilBadge == 0
                ? activeToday ? "One sip unlocked it—your new badge is ready!"
                : "Your next sip unlocks this badge."
                : daysUntilBadge + (daysUntilBadge == 1 ? " day" : " days")
                + " until your next badge";
        previewCopy.addView(text(previewMessage, 10, MUTED, false),
                margin(MATCH, WRAP, 0, dp(3), 0, 0));
        upcoming.addView(previewCopy, new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView milestone = text(String.valueOf(nextStage), 18, nextColor, true);
        upcoming.addView(milestone);
        rewards.addView(upcoming, margin(MATCH, WRAP, 0, dp(11), 0, 0));
        content.addView(rewards, margin(MATCH, WRAP, 0, 0, 0, dp(18)));
    }

    private int getStreakStageColor(int streak) {
        if (streak >= 30) return 0xFFFFC12E;
        if (streak >= 14) return 0xFF00D5A2;
        if (streak >= 7) return 0xFF16BFFF;
        if (streak >= 3) return 0xFFB044FF;
        if (streak >= 1) return 0xFFFF3F88;
        return MUTED;
    }

    private float getStreakGlowStrength(int streak) {
        if (streak >= 30) return 5f;
        if (streak >= 14) return 4f;
        if (streak >= 7) return 3f;
        if (streak >= 3) return 2f;
        return streak >= 1 ? 1f : 0f;
    }

    private String getStreakStageName(int streak) {
        if (streak >= 30) return "Radiant · Gold flame";
        if (streak >= 14) return "Blooming · Emerald flame";
        if (streak >= 7) return "Sapphire · Blue flame";
        if (streak >= 3) return "Amethyst · Violet flame";
        if (streak >= 1) return "First light · Rose flame";
        return "Your sip streak";
    }

    private void addSipButton(LinearLayout row, int amount, boolean featured) {
        TextView button = text("+" + amount + " ml", 13, featured ? WHITE : PINK, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(featured
                ? gradientBackground(PINK, 0xFFDA8BA7, 18)
                : background(WHITE, 18));
        button.setElevation(dp(featured ? 4 : 2));
        button.setOnClickListener(view -> recordWater(amount, button));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, MATCH, 1);
        if (row.getChildCount() > 0) {
            params.leftMargin = dp(11);
        }
        row.addView(button, params);
    }

    private void addRecentSips() {
        List<HydrationPrefs.Sip> entries = HydrationPrefs.getTodayEntries(this);
        LinearLayout card = card();
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("Today’s sips", 17, INK, true),
                new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView count = text(entries.size() + " LOGGED", 9, PINK, true);
        count.setLetterSpacing(0.1f);
        header.addView(count);
        card.addView(header);

        if (entries.isEmpty()) {
            card.addView(text("Your drinks will show up here as you log them.", 12, MUTED, false),
                    margin(MATCH, WRAP, 0, dp(12), 0, 0));
        } else {
            int start = Math.max(0, entries.size() - 4);
            for (int i = entries.size() - 1; i >= start; i--) {
                addSipRow(card, entries.get(i));
            }
            if (entries.size() > 0) {
                TextView undo = textButton("Undo last sip", PINK, 11);
                undo.setOnClickListener(view -> undoWater());
                card.addView(undo, margin(WRAP, dp(32), 0, dp(5), 0, 0));
            }
        }
        content.addView(card, margin(MATCH, WRAP, 0, 0, 0, dp(16)));
    }

    private void addSipRow(LinearLayout parent, HydrationPrefs.Sip sip) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        WaterIconView drop = new WaterIconView(this, WaterIconView.DROP, PINK);
        drop.setBackground(background(PALE_PINK, 30));
        row.addView(drop, margin(dp(32), dp(32), 0, 0, dp(10), 0));
        row.addView(text(String.format(Locale.getDefault(), "%,d ml", sip.amountMl),
                        13, INK, true),
                new LinearLayout.LayoutParams(0, WRAP, 1));
        row.addView(text(formatSipTime(sip.timestamp), 11, MUTED, false));
        parent.addView(row, margin(MATCH, WRAP, 0, dp(13), 0, 0));
    }

    private void addTodayReminderPreview() {
        LinearLayout card = card();
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("A gentle nudge", 16, INK, true),
                new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView settings = textButton("SETTINGS  ›", PINK, 9);
        settings.setOnClickListener(view -> selectTab(TAB_SETTINGS));
        header.addView(settings);
        card.addView(header);
        card.addView(text("Your little reminders, right when you need them.",
                11, MUTED, false), margin(MATCH, WRAP, 0, dp(4), 0, dp(12)));

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        List<String> reminderTimes = HydrationPrefs.getReminderTimes(this);
        for (String time : reminderTimes) {
            TextView chip = text(formatTime(time), 11, PINK, true);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(background(0xFFFFF1F3, 14));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1);
            if (times.getChildCount() > 0) {
                params.leftMargin = dp(7);
            }
            times.addView(chip, params);
        }
        card.addView(times);
        content.addView(card);
    }

    private void renderHistory() {
        content.addView(text("Your small sips,\nadding up.", 28, INK, true),
                margin(MATCH, WRAP, 0, 0, 0, dp(7)));
        content.addView(text("A little look at how you’ve been caring for yourself.", 13, MUTED, false),
                margin(MATCH, WRAP, 0, 0, 0, dp(20)));

        addHistoryFilters();
        List<String> visibleDays = getFilteredHistoryDays();
        int weeklyTotal = 0;
        for (String dayKey : visibleDays) {
            weeklyTotal += HydrationPrefs.getIntake(this, dayKey);
        }
        LinearLayout summary = new LinearLayout(this);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.setPadding(dp(17), dp(17), dp(17), dp(17));
        summary.setBackground(gradientBackground(DEEP_PLUM, 0xFF79556F, 21));
        LinearLayout copy = column();
        copy.addView(text(historyRangeTitle().toUpperCase(Locale.getDefault()),
                9, 0xFFE8CDD9, true));
        copy.addView(text(String.format(Locale.getDefault(), "%,d ml", weeklyTotal),
                26, WHITE, true), margin(MATCH, WRAP, 0, dp(6), 0, dp(3)));
        copy.addView(text(visibleDays.size() + (visibleDays.size() == 1
                ? " day with sips" : " days with sips"), 11, 0xFFE8DDE7, false));
        summary.addView(copy, new LinearLayout.LayoutParams(0, WRAP, 1));
        WaterIconView flower = new WaterIconView(this, WaterIconView.FLOWER, 0xFFFFD7B5);
        summary.addView(flower, new LinearLayout.LayoutParams(dp(36), dp(36)));
        content.addView(summary, margin(MATCH, WRAP, 0, 0, 0, dp(18)));

        content.addView(text("YOUR SIP HISTORY", 10, PINK, true),
                margin(MATCH, WRAP, 0, 0, 0, dp(10)));
        if (visibleDays.isEmpty()) {
            LinearLayout empty = card();
            empty.setGravity(Gravity.CENTER);
            WaterIconView drop = new WaterIconView(this, WaterIconView.DROP, PINK);
            empty.addView(drop, margin(dp(42), dp(42), 0, 0, 0, dp(10)));
            empty.addView(text("Your first sip will start your story.", 13, INK, true));
            empty.addView(text("There are no saved sips in this date range.",
                    11, MUTED, false), margin(MATCH, WRAP, 0, dp(5), 0, 0));
            content.addView(empty, margin(MATCH, WRAP, 0, 0, 0, dp(14)));
        } else {
            for (String dayKey : visibleDays) {
                addHistoryDay(dayKey);
            }
        }
        TextView clearHistory = textButton("Clear sip history", 0xFF9E526C, 11);
        clearHistory.setGravity(Gravity.CENTER);
        clearHistory.setOnClickListener(view -> confirmClearSipHistory());
        content.addView(clearHistory, margin(MATCH, dp(42), 0, dp(5), 0, dp(10)));
        content.addView(text("Your sip history stays on this phone. It isn’t sent anywhere.",
                        11, MUTED, false),
                margin(MATCH, WRAP, 0, dp(2), 0, 0));
    }

    private void addHistoryFilters() {
        LinearLayout filters = new LinearLayout(this);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"Today", "This week", "All time", "Custom"};
        int[] values = {HISTORY_TODAY, HISTORY_WEEK, HISTORY_ALL, HISTORY_CUSTOM};
        for (int i = 0; i < labels.length; i++) {
            final int filter = values[i];
            TextView chip = text(labels[i], 10,
                    historyFilter == filter ? WHITE : PINK, true);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setPadding(dp(9), 0, dp(9), 0);
            chip.setBackground(background(historyFilter == filter
                    ? PINK : 0xFFFFF0F4, 14));
            chip.setOnClickListener(view -> {
                if (filter == HISTORY_CUSTOM) {
                    showHistoryDatePicker(true);
                } else {
                    historyFilter = filter;
                    renderScreen();
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1);
            if (i > 0) {
                params.leftMargin = dp(6);
            }
            filters.addView(chip, params);
        }
        content.addView(filters, margin(MATCH, WRAP, 0, 0, 0, dp(13)));
        if (historyFilter == HISTORY_CUSTOM && historyFrom != null && historyTo != null) {
            TextView range = text(historyFrom + "  →  " + historyTo, 10, PINK, true);
            range.setGravity(Gravity.CENTER);
            range.setOnClickListener(view -> showHistoryDatePicker(true));
            content.addView(range, margin(MATCH, dp(30), 0, -dp(8), 0, dp(7)));
        }
    }

    private List<String> getFilteredHistoryDays() {
        List<String> days = HydrationPrefs.getRecordedDayKeys(this);
        List<String> filtered = new ArrayList<>();
        Calendar today = Calendar.getInstance();
        Calendar firstDay = (Calendar) today.clone();
        if (historyFilter == HISTORY_TODAY) {
            String todayKey = HydrationPrefs.getTodayKey();
            if (days.contains(todayKey)) {
                filtered.add(todayKey);
            }
            return filtered;
        }
        if (historyFilter == HISTORY_WEEK) {
            firstDay.add(Calendar.DAY_OF_YEAR, -6);
            String lowerBound = historyDateKey(firstDay);
            for (String day : days) {
                if (day.compareTo(lowerBound) >= 0) {
                    filtered.add(day);
                }
            }
            return filtered;
        }
        if (historyFilter == HISTORY_CUSTOM && historyFrom != null && historyTo != null) {
            for (String day : days) {
                if (day.compareTo(historyFrom) >= 0 && day.compareTo(historyTo) <= 0) {
                    filtered.add(day);
                }
            }
            return filtered;
        }
        return days;
    }

    private String historyRangeTitle() {
        switch (historyFilter) {
            case HISTORY_TODAY:
                return "Today";
            case HISTORY_WEEK:
                return "This week";
            case HISTORY_CUSTOM:
                return historyFrom == null || historyTo == null
                        ? "Custom date range" : historyFrom + " — " + historyTo;
            default:
                return "All time";
        }
    }

    private void showHistoryDatePicker(boolean chooseStart) {
        Calendar initial = Calendar.getInstance();
        String existing = chooseStart ? historyFrom : historyTo;
        if (existing != null) {
            initial = parseHistoryDate(existing);
        } else if (chooseStart && historyTo != null) {
            initial = parseHistoryDate(historyTo);
            initial.add(Calendar.DAY_OF_MONTH, -6);
        } else if (!chooseStart && historyFrom != null) {
            initial = parseHistoryDate(historyFrom);
        } else if (chooseStart) {
            initial.add(Calendar.DAY_OF_MONTH, -6);
        }
        DatePicker picker = new DatePicker(this);
        picker.setCalendarViewShown(false);
        picker.updateDate(initial.get(Calendar.YEAR), initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH));
        LinearLayout body = column();
        body.setGravity(Gravity.CENTER);
        body.addView(picker);
        showStyledDialog(chooseStart ? "Start of your range" : "End of your range",
                "Choose a date for your sip history.", body, "Cancel", "Choose date", () -> {
                    String chosen = String.format(Locale.US, "%04d-%02d-%02d",
                            picker.getYear(), picker.getMonth() + 1, picker.getDayOfMonth());
                    if (chooseStart) {
                        historyFrom = chosen;
                        if (historyTo != null && historyFrom.compareTo(historyTo) > 0) {
                            historyTo = null;
                        }
                        screenFrame.post(() -> showHistoryDatePicker(false));
                    } else {
                        if (historyFrom == null || chosen.compareTo(historyFrom) < 0) {
                            showFeedback("Choose an end date on or after the start date.");
                            return false;
                        }
                        historyTo = chosen;
                        historyFilter = HISTORY_CUSTOM;
                        screenFrame.post(this::renderScreen);
                    }
                    return true;
                });
    }

    private Calendar parseHistoryDate(String value) {
        Calendar result = Calendar.getInstance();
        try {
            String[] parts = value.split("-");
            result.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1,
                    Integer.parseInt(parts[2]), 0, 0, 0);
            result.set(Calendar.MILLISECOND, 0);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException exception) {
            result = Calendar.getInstance();
        }
        return result;
    }

    private String historyDateKey(Calendar date) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                date.get(Calendar.YEAR), date.get(Calendar.MONTH) + 1,
                date.get(Calendar.DAY_OF_MONTH));
    }

    private void confirmClearSipHistory() {
        showStyledDialog("A fresh start?",
                "Clear every saved sip. Your streak and star balance will restart at zero; your name, goal, and reminders stay just as they are.",
                null, "Keep my history", "Clear history", () -> {
                    HydrationPrefs.clearSipHistory(this);
                    currentTab = TAB_HISTORY;
                    screenFrame.post(() -> {
                        renderScreen();
                        showFeedback("Your sip history is cleared. Today is a fresh start.");
                    });
                    return true;
                });
    }

    private void addHistoryDay(String key) {
        List<HydrationPrefs.Sip> entries = HydrationPrefs.getEntries(this, key);
        int total = HydrationPrefs.getIntake(this, key);
        int goal = HydrationPrefs.getDailyGoal(this);
        String label = HydrationPrefs.getDayLabel(key);
        LinearLayout card = card();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        boolean recent = label.equals("Today") || label.equals("Yesterday");
        LinearLayout dateMark = new LinearLayout(this);
        dateMark.setGravity(Gravity.CENTER);
        dateMark.setBackground(background(PALE_PINK, 30));
        if (recent) {
            dateMark.addView(text(label.toUpperCase(Locale.getDefault()), 7, PINK, true));
        } else {
            dateMark.addView(new WaterIconView(this, WaterIconView.FLOWER, PINK),
                    new LinearLayout.LayoutParams(dp(19), dp(19)));
        }
        row.addView(dateMark, margin(dp(40), dp(40), 0, 0, dp(11), 0));
        LinearLayout dateCopy = column();
        dateCopy.addView(text(label, 13, INK, true));
        dateCopy.addView(text(entries.size() + (entries.size() == 1 ? " sip" : " sips"),
                10, MUTED, false), margin(MATCH, WRAP, 0, dp(3), 0, 0));
        row.addView(dateCopy, new LinearLayout.LayoutParams(0, WRAP, 1));
        LinearLayout totalCopy = column();
        totalCopy.setGravity(Gravity.END);
        totalCopy.addView(text(String.format(Locale.getDefault(), "%,d ml", total),
                14, INK, true));
        TextView progress = text(total >= goal ? "GOAL MET" : goal - total + " ml to goal",
                9, total >= goal ? PINK : MUTED, false);
        totalCopy.addView(progress, margin(WRAP, WRAP, 0, dp(3), 0, 0));
        row.addView(totalCopy);
        card.addView(row);

        if (!entries.isEmpty()) {
            View divider = new View(this);
            divider.setBackgroundColor(WaterTheme.surfaceColor(this, 0xFFF3EBEE));
            card.addView(divider, margin(MATCH, dp(1), 0, dp(13), 0, dp(1)));
            for (int i = entries.size() - 1; i >= 0; i--) {
                HydrationPrefs.Sip sip = entries.get(i);
                LinearLayout sipLine = new LinearLayout(this);
                sipLine.setGravity(Gravity.CENTER_VERTICAL);
                sipLine.addView(text("•", 13, PINK, true), margin(WRAP, WRAP, 0, 0, dp(8), 0));
                sipLine.addView(text(String.format(Locale.getDefault(), "%,d ml", sip.amountMl),
                        11, INK, false), new LinearLayout.LayoutParams(0, WRAP, 1));
                sipLine.addView(text(formatSipTime(sip.timestamp), 10, MUTED, false));
                card.addView(sipLine, margin(MATCH, WRAP, 0, dp(7), 0, 0));
            }
        } else {
            card.addView(text("A new day is a fresh start.", 10, MUTED, false),
                    margin(MATCH, WRAP, 0, dp(9), 0, 0));
        }
        content.addView(card, margin(MATCH, WRAP, 0, 0, 0, dp(10)));
    }

    private void renderSettings() {
        content.addView(text("Your routine,\nyour rules.", 29, INK, true),
                margin(MATCH, WRAP, 0, 0, 0, dp(7)));
        content.addView(text("Make your hydration habit feel like yours.", 13, MUTED, false),
                margin(MATCH, WRAP, 0, 0, 0, dp(21)));

        content.addView(text("YOUR LOOK", 10, PINK, true),
                margin(MATCH, WRAP, 0, 0, 0, dp(9)));
        LinearLayout appearance = card();
        addSettingsRow(appearance, "Appearance",
                getThemeLabel(),
                WaterIconView.STAR, this::chooseTheme, false);
        content.addView(appearance, margin(MATCH, WRAP, 0, 0, 0, dp(19)));

        content.addView(text("A LITTLE ABOUT YOU", 10, PINK, true),
                margin(MATCH, WRAP, 0, 0, 0, dp(9)));
        LinearLayout profile = card();
        addSettingsRow(profile, "Your name", HydrationPrefs.getName(this), WaterIconView.FLOWER,
                this::showNameDialog, true);
        content.addView(profile, margin(MATCH, WRAP, 0, 0, 0, dp(19)));

        content.addView(text("YOUR DAILY GOAL", 10, PINK, true),
                margin(MATCH, WRAP, 0, 0, 0, dp(9)));
        LinearLayout goalCard = card();
        addSettingsRow(goalCard, "Water goal",
                formatAmount(HydrationPrefs.getDailyGoal(this)) + " / day",
                WaterIconView.DROP, this::showGoalDialog, true);
        goalCard.addView(text("A personal starting point, not a medical rule. Adjust it to what feels right for you.",
                        10, MUTED, false),
                margin(MATCH, WRAP, 0, dp(9), 0, 0));
        content.addView(goalCard, margin(MATCH, WRAP, 0, 0, 0, dp(19)));

        content.addView(text("YOUR DAILY REMINDERS", 10, PINK, true),
                margin(MATCH, WRAP, 0, 0, 0, dp(9)));
        List<String> times = HydrationPrefs.getReminderTimes(this);
        for (int i = 0; i < times.size(); i++) {
            addReminderSettingsCard(i, getTimeOfDayLabel(times.get(i)));
        }
        TextView addReminder = textButton(
                times.size() < HydrationPrefs.MAX_REMINDER_COUNT
                        ? "＋  Add another daily reminder" : "You’ve reached 10 daily reminders",
                PINK, 12);
        addReminder.setGravity(Gravity.CENTER);
        addReminder.setEnabled(times.size() < HydrationPrefs.MAX_REMINDER_COUNT);
        addReminder.setOnClickListener(view -> addCustomReminder());
        content.addView(addReminder, margin(MATCH, dp(46), 0, dp(3), 0, dp(7)));
        LinearLayout reminderCard = card();
        reminderCard.addView(text("Streak care is on in the background: gentle notification-only check-ins at 9 PM, 10 PM, and 11 PM when you haven’t logged a sip that day. These aren’t editable reminders.",
                        10, MUTED, false),
                margin(MATCH, WRAP, 0, dp(1), 0, dp(8)));
        reminderStatus = text("", 10, MUTED, false);
        reminderCard.addView(reminderStatus, margin(MATCH, WRAP, 0, dp(9), 0, dp(3)));
        TextView permission = textButton("Notification & alarm permissions  ›", PINK, 11);
        permission.setGravity(Gravity.CENTER);
        permission.setOnClickListener(view -> openReminderSettings());
        reminderCard.addView(permission, margin(MATCH, dp(40), 0, dp(5), 0, 0));
        TextView restoreReminders = textButton("Restore 9 AM · 2 PM · 7 PM defaults",
                MUTED, 10);
        restoreReminders.setGravity(Gravity.CENTER);
        restoreReminders.setOnClickListener(view -> confirmReminderReset());
        reminderCard.addView(restoreReminders, margin(MATCH, dp(38), 0, dp(4), 0, 0));
        content.addView(reminderCard, margin(MATCH, WRAP, 0, 0, 0, dp(19)));

        content.addView(text("MAKE IT YOURS", 10, PINK, true),
                margin(MATCH, WRAP, 0, 0, 0, dp(9)));
        LinearLayout soundCard = card();
        addSettingsRow(soundCard, "Reminder sound", getToneTitle(), WaterIconView.SOUND,
                this::chooseAlarmTone, false);
        content.addView(soundCard, margin(MATCH, WRAP, 0, 0, 0, dp(16)));
        content.addView(text("Made with care, for " + HydrationPrefs.getName(this) + ".\nYour data stays in this app on this phone.",
                        10, MUTED, false),
                margin(MATCH, WRAP, 0, dp(3), 0, dp(8)));
        updateReminderStatus();
    }

    private String getTimeOfDayLabel(String time) {
        try {
            int hour = Integer.parseInt(time.split(":")[0]);
            return hour < 12 ? "Morning" : hour < 17 ? "Afternoon" : "Evening";
        } catch (NumberFormatException exception) {
            return "Daily";
        }
    }

    private String getThemeLabel() {
        String mode = HydrationPrefs.getThemeMode(this);
        if (HydrationPrefs.THEME_LIGHT.equals(mode)) {
            return "Light · rose cream";
        }
        if (HydrationPrefs.THEME_DARK.equals(mode)) {
            return "Dark · plum velvet";
        }
        return "System · follow device";
    }

    private void addCustomReminder() {
        List<String> times = HydrationPrefs.getReminderTimes(this);
        if (times.size() >= HydrationPrefs.MAX_REMINDER_COUNT) {
            return;
        }
        showTimePickerDialog("Choose a reminder time", "17:00", (hour, minute) -> {
            String time = String.format(Locale.US, "%02d:%02d", hour, minute);
            HydrationPrefs.addReminder(this, time);
            ReminderScheduler.scheduleAll(this);
            screenFrame.post(() -> {
                renderScreen();
                showFeedback(getTimeOfDayLabel(time) + " reminder added.");
            });
        });
    }

    private void chooseTheme() {
        String[] choices = {"System · follow device", "Light · rose cream", "Dark · plum velvet"};
        String themeMode = HydrationPrefs.getThemeMode(this);
        int selected = HydrationPrefs.THEME_LIGHT.equals(themeMode) ? 1
                : HydrationPrefs.THEME_DARK.equals(themeMode) ? 2 : 0;
        showChoiceDialog("Choose your look", "System follows your phone’s light or dark setting.",
                choices,
                selected, which -> {
                    String mode = which == 1 ? HydrationPrefs.THEME_LIGHT
                            : which == 2 ? HydrationPrefs.THEME_DARK
                            : HydrationPrefs.THEME_SYSTEM;
                    HydrationPrefs.setThemeMode(this, mode);
                    recreate();
                });
    }

    private void confirmReminderReset() {
        showStyledDialog("Restore your daily routine?",
                "Return to 9 AM, 2 PM, and 7 PM with alarms on and 250 ml per reminder. Extra reminders will be removed. Your sip history and streak-care notifications stay.",
                null, "Keep my settings", "Restore defaults", () -> {
                    HydrationPrefs.resetReminderDefaults(this);
                    ReminderScheduler.scheduleAll(this);
                    screenFrame.post(() -> {
                        renderScreen();
                        showFeedback("Your daily reminders are ready.");
                    });
                    return true;
                });
    }

    private void addReminderSettingsCard(int index, String label) {
        LinearLayout card = card();
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout headingCopy = column();
        headingCopy.addView(text(label, 15, INK, true));
        boolean reminderEnabled = HydrationPrefs.areRemindersEnabled(this)
                && HydrationPrefs.isReminderEnabled(this, index);
        boolean alarmEnabled = HydrationPrefs.isReminderAlarmEnabled(this, index);
        TextView daily = text(reminderEnabled ? getRepeatLabel(
                HydrationPrefs.getReminderDayMask(this, index)).toUpperCase(Locale.getDefault())
                + (alarmEnabled ? "  ·  ALARM ON" : "  ·  NOTIFICATION ONLY")
                : "PAUSED", 8, reminderEnabled ? PINK : MUTED, true);
        daily.setLetterSpacing(0.1f);
        headingCopy.addView(daily, margin(MATCH, WRAP, 0, dp(4), 0, 0));
        heading.addView(headingCopy, new LinearLayout.LayoutParams(0, WRAP, 1));
        Switch toggle = new Switch(this);
        toggle.setChecked(HydrationPrefs.areRemindersEnabled(this)
                && HydrationPrefs.isReminderEnabled(this, index));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            HydrationPrefs.setReminderEnabled(this, index, checked);
            if (!checked) {
                ReminderActionReceiver.stopAlarm(this, index);
            }
            ReminderScheduler.scheduleAll(this);
            renderScreen();
        });
        heading.addView(toggle);
        card.addView(heading);

        LinearLayout repeatRow = new LinearLayout(this);
        repeatRow.setGravity(Gravity.CENTER_VERTICAL);
        WaterIconView clock = new WaterIconView(this, WaterIconView.CLOCK, PINK);
        repeatRow.addView(clock, margin(dp(17), dp(17), 0, 0, dp(6), 0));
        TextView repeat = text(getRepeatLabel(
                HydrationPrefs.getReminderDayMask(this, index)) + "  ·  Change days",
                10, PINK, true);
        repeat.setGravity(Gravity.CENTER_VERTICAL);
        repeat.setOnClickListener(view -> chooseReminderDays(index));
        repeatRow.addView(repeat);
        card.addView(repeatRow, margin(MATCH, dp(35), 0, dp(7), 0, 0));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        TextView time = text(formatTime(HydrationPrefs.getReminderTimes(this).get(index)),
                17, INK, true);
        time.setGravity(Gravity.CENTER);
        time.setBackground(background(0xFFFFF0F4, 15));
        time.setOnClickListener(view -> editReminderTime(index));
        controls.addView(time, new LinearLayout.LayoutParams(0, dp(49), 1));

        TextView amount = text("✧  " + formatAmount(
                HydrationPrefs.getReminderAmount(this, index)), 12, PINK, true);
        amount.setGravity(Gravity.CENTER);
        amount.setBackground(background(0xFFFFF7F0, 15));
        amount.setOnClickListener(view -> showReminderAmountPicker(index));
        LinearLayout.LayoutParams amountParams = new LinearLayout.LayoutParams(0, dp(49), 1);
        amountParams.leftMargin = dp(9);
        controls.addView(amount, amountParams);
        card.addView(controls, margin(MATCH, WRAP, 0, dp(12), 0, dp(5)));
        LinearLayout alarmRow = new LinearLayout(this);
        alarmRow.setGravity(Gravity.CENTER_VERTICAL);
        WaterIconView alarmIcon = new WaterIconView(this, WaterIconView.ALARM,
                alarmEnabled ? PINK : MUTED);
        alarmRow.addView(alarmIcon, margin(dp(20), dp(20), 0, 0, dp(8), 0));
        LinearLayout alarmCopy = column();
        alarmCopy.addView(text("Alarm sound", 11, INK, true));
        alarmCopy.addView(text(alarmEnabled ? "Rings with your selected tone"
                : "A notification will still appear", 9, MUTED, false),
                margin(MATCH, WRAP, 0, dp(2), 0, 0));
        alarmRow.addView(alarmCopy, new LinearLayout.LayoutParams(0, WRAP, 1));
        Switch alarmToggle = new Switch(this);
        alarmToggle.setContentDescription(label + " alarm sound");
        alarmToggle.setChecked(alarmEnabled);
        alarmToggle.setOnCheckedChangeListener((button, checked) -> {
            HydrationPrefs.setReminderAlarmEnabled(this, index, checked);
            ReminderActionReceiver.stopAlarm(this, index);
            ReminderScheduler.scheduleAll(this);
            renderScreen();
        });
        alarmRow.addView(alarmToggle);
        card.addView(alarmRow, margin(MATCH, dp(42), 0, dp(4), 0, 0));
        if (index >= HydrationPrefs.DEFAULT_REMINDER_COUNT) {
            TextView remove = textButton("Remove this extra reminder", MUTED, 10);
            remove.setOnClickListener(view -> showStyledDialog("Remove this reminder?",
                    "Only this extra reminder will be removed. Your sip history and three original reminders won’t change.",
                    null, "Keep it", "Remove", () -> {
                        int count = HydrationPrefs.getReminderTimes(this).size();
                        for (int reminder = HydrationPrefs.DEFAULT_REMINDER_COUNT;
                             reminder < count; reminder++) {
                            ReminderActionReceiver.stopAlarm(this, reminder);
                            ReminderScheduler.cancelReminder(this, reminder);
                        }
                        HydrationPrefs.removeCustomReminder(this, index);
                        ReminderScheduler.scheduleAll(this);
                        screenFrame.post(this::renderScreen);
                        return true;
                    }));
            card.addView(remove, margin(WRAP, dp(34), 0, dp(4), 0, 0));
        }
        content.addView(card, margin(MATCH, WRAP, 0, 0, 0, dp(10)));
    }

    private String getRepeatLabel(int dayMask) {
        if (dayMask == 0b1111111) {
            return "Every day";
        }
        if (dayMask == ((1 << 1) | (1 << 2) | (1 << 3) | (1 << 4) | (1 << 5))) {
            return "Weekdays";
        }
        if (dayMask == ((1 << 0) | (1 << 6))) {
            return "Weekends";
        }
        String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        StringBuilder result = new StringBuilder();
        for (int day = 0; day < dayNames.length; day++) {
            if ((dayMask & (1 << day)) != 0) {
                if (result.length() > 0) {
                    result.append(" · ");
                }
                result.append(dayNames[day]);
            }
        }
        return result.toString();
    }

    private void chooseReminderDays(int index) {
        String[] choices = {"Every day", "Weekdays (Mon–Fri)",
                "Weekends (Sat–Sun)", "Choose individual days…"};
        int current = HydrationPrefs.getReminderDayMask(this, index);
        int weekdays = (1 << 1) | (1 << 2) | (1 << 3) | (1 << 4) | (1 << 5);
        int weekends = (1 << 0) | (1 << 6);
        int selection = current == 0b1111111 ? 0
                : current == weekdays ? 1 : current == weekends ? 2 : 3;
        showChoiceDialog("When should this repeat?", "Choose the days for this reminder.",
                choices, selection, which -> {
                    if (which == 0) {
                        saveReminderDays(index, 0b1111111);
                    } else if (which == 1) {
                        saveReminderDays(index, weekdays);
                    } else if (which == 2) {
                        saveReminderDays(index, weekends);
                    } else {
                        showCustomReminderDays(index);
                    }
                });
    }

    private void showCustomReminderDays(int index) {
        String[] labels = {"Sunday", "Monday", "Tuesday", "Wednesday",
                "Thursday", "Friday", "Saturday"};
        boolean[] checked = new boolean[labels.length];
        int currentMask = HydrationPrefs.getReminderDayMask(this, index);
        for (int day = 0; day < checked.length; day++) {
            checked[day] = (currentMask & (1 << day)) != 0;
        }
        LinearLayout choices = column();
        choices.setPadding(0, dp(4), 0, dp(4));
        TextView[] dayRows = new TextView[labels.length];
        for (int day = 0; day < labels.length; day++) {
            final int selectedDay = day;
            dayRows[day] = text("", 13, INK, true);
            dayRows[day].setGravity(Gravity.CENTER_VERTICAL);
            dayRows[day].setPadding(dp(14), 0, dp(14), 0);
            dayRows[day].setBackground(background(checked[day]
                    ? 0xFFFFEAF0 : WHITE, 13));
            updateDayChoice(dayRows[day], labels[day], checked[day]);
            dayRows[day].setOnClickListener(view -> {
                checked[selectedDay] = !checked[selectedDay];
                dayRows[selectedDay].setBackground(background(checked[selectedDay]
                        ? 0xFFFFEAF0 : WHITE, 13));
                updateDayChoice(dayRows[selectedDay], labels[selectedDay],
                        checked[selectedDay]);
            });
            choices.addView(dayRows[day], margin(MATCH, dp(43), 0,
                    day == 0 ? 0 : dp(6), 0, 0));
        }
        showStyledDialog("Choose the days", "Pick one or more days.", choices,
                "Cancel", "Save days", () -> {
                    int mask = 0;
                    for (int day = 0; day < checked.length; day++) {
                        if (checked[day]) {
                            mask |= 1 << day;
                        }
                    }
                    if (mask == 0) {
                        showFeedback("Choose at least one day.");
                        return false;
                    } else {
                        saveReminderDays(index, mask);
                    }
                    return true;
                });
    }

    private void saveReminderDays(int index, int mask) {
        HydrationPrefs.setReminderDayMask(this, index, mask);
        ReminderScheduler.scheduleAll(this);
        screenFrame.post(this::renderScreen);
    }

    private void showReminderAmountPicker(int index) {
        String[] options = {"250 ml", "500 ml", "750 ml", "1,000 ml", "Custom amount…"};
        int[] amounts = {250, 500, 750, 1000};
        int current = HydrationPrefs.getReminderAmount(this, index);
        int selected = -1;
        for (int i = 0; i < amounts.length; i++) {
            if (amounts[i] == current) {
                selected = i;
                break;
            }
        }
        showChoiceDialog("How much for this reminder?", "Choose a sip size.", options,
                selected, which -> {
                    if (which == 4) {
                        showCustomReminderAmount(index);
                    } else {
                        HydrationPrefs.setReminderAmount(this, index, amounts[which]);
                        renderScreen();
                    }
                });
    }

    private void showCustomReminderAmount(int index) {
        EditText amount = amountInput(HydrationPrefs.getReminderAmount(this, index));
        LinearLayout content = column();
        content.setPadding(0, dp(8), 0, 0);
        content.addView(amount, margin(MATCH, dp(60), 0, 0, 0, dp(8)));
        addAmountPresets(content, amount, new int[]{250, 500, 750, 1000});
        showStyledDialog("Custom reminder sip", "Enter an amount in millilitres.",
                content, "Cancel", "Save amount", () -> {
                    int value = parseAmount(amount);
                    if (value < 1 || value > 5000) {
                        amount.setError("Enter a whole number from 1 to 5,000 ml");
                        return false;
                    }
                    HydrationPrefs.setReminderAmount(this, index, value);
                    screenFrame.post(this::renderScreen);
                    return true;
                });
    }

    private void addSettingsRow(LinearLayout parent, String title, String value, String icon,
                                Runnable action, boolean dividerBelow) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        WaterIconView symbol = new WaterIconView(this, icon, PINK);
        symbol.setBackground(background(PALE_PINK, 13));
        row.addView(symbol, margin(dp(34), dp(34), 0, 0, dp(11), 0));
        LinearLayout copy = column();
        copy.addView(text(title, 13, INK, true));
        if (value != null && !value.isEmpty()) {
            copy.addView(text(value, 10, MUTED, false), margin(MATCH, WRAP, 0, dp(3), 0, 0));
        }
        row.addView(copy, new LinearLayout.LayoutParams(0, WRAP, 1));
        row.addView(text("›", 20, MUTED, false));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(view -> action.run());
        parent.addView(row, margin(MATCH, dp(51), 0, dp(2), 0, 0));
        if (dividerBelow) {
            View divider = new View(this);
            divider.setBackgroundColor(WaterTheme.surfaceColor(this, 0xFFF3EBEE));
            parent.addView(divider, margin(MATCH, dp(1), dp(45), dp(3), 0, dp(3)));
        }
    }

    private LinearLayout buildBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(WaterTheme.surfaceColor(this, WHITE));
        nav.setElevation(dp(12));
        String[] labels = {"Today", "History", "Settings"};
        String[] icons = {WaterIconView.HOME, WaterIconView.HISTORY, WaterIconView.SETTINGS};
        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            boolean selected = currentTab == i;
            LinearLayout item = column();
            item.setGravity(Gravity.CENTER);
            WaterIconView icon = new WaterIconView(this, icons[i], selected ? PINK : MUTED);
            item.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));
            TextView label = text(labels[i], 9, selected ? PINK : MUTED, selected);
            item.addView(label, margin(WRAP, WRAP, 0, dp(3), 0, 0));
            item.setClickable(true);
            item.setFocusable(true);
            item.setOnClickListener(view -> selectTab(tab));
            item.setContentDescription(labels[i]);
            nav.addView(item, new LinearLayout.LayoutParams(0, MATCH, 1));
        }
        return nav;
    }

    private void selectTab(int tab) {
        currentTab = tab;
        renderScreen();
    }

    private void recordWater(int amount) {
        recordWater(amount, null);
    }

    private void recordWater(int amount, View source) {
        if (source == null) {
            logWaterAndCelebrate(amount, null);
            return;
        }
        source.animate().cancel();
        source.animate().rotation(-7f).setDuration(55).withEndAction(() ->
                source.animate().rotation(7f).setDuration(60).withEndAction(() ->
                        source.animate().rotation(-4f).setDuration(50).withEndAction(() ->
                                source.animate().rotation(0f).setDuration(55).withEndAction(() ->
                                        logWaterAndCelebrate(amount, source)).start()).start())
                        .start()).start();
    }

    private void logWaterAndCelebrate(int amount, View source) {
        PointF sourcePoint = null;
        if (source != null && screenFrame != null) {
            int[] sourceLocation = new int[2];
            int[] frameLocation = new int[2];
            source.getLocationOnScreen(sourceLocation);
            screenFrame.getLocationOnScreen(frameLocation);
            sourcePoint = new PointF(sourceLocation[0] - frameLocation[0]
                    + source.getWidth() / 2f, sourceLocation[1] - frameLocation[1]
                    + source.getHeight() / 2f);
        }
        int previousTotal = HydrationPrefs.getTodayIntake(this);
        long previousLifetimeIntake = HydrationPrefs.getLifetimeIntake(this);
        int previousStars = HydrationPrefs.getStars(this);
        int goal = HydrationPrefs.getDailyGoal(this);
        boolean streakStartedToday = !HydrationPrefs.isTodayStreakActive(this);
        int previousStreak = HydrationPrefs.getCurrentStreak(this);
        HydrationPrefs.addIntake(this, amount);
        int updatedTotal = previousTotal + amount;
        long updatedLifetimeIntake = HydrationPrefs.getLifetimeIntake(this);
        int earnedStars = (int) (updatedLifetimeIntake / 100 - previousLifetimeIntake / 100);
        int updatedStars = HydrationPrefs.getStars(this);
        int updatedStreak = HydrationPrefs.getCurrentStreak(this);
        progressBeforeUpdate = previousTotal;
        renderScreen();
        boolean goalJustReached = previousTotal < goal && updatedTotal >= goal;
        animateSipRewards(earnedStars, previousStars, updatedStars, previousStreak,
                updatedStreak, streakStartedToday, goalJustReached,
                updatedTotal > goal ? updatedTotal - goal : 0, sourcePoint);
    }

    private void animateSipRewards(int earnedStars, int previousStars, int updatedStars,
                                   int previousStreak, int updatedStreak,
                                   boolean streakStartedToday, boolean goalJustReached,
                                   int overGoalMl, PointF sourcePoint) {
        int newStageColor = getStreakStageColor(updatedStreak);
        int oldStageColor = getStreakStageColor(previousStreak);
        int newlyUnlockedStage = newStageColor != oldStageColor
                ? getUnlockedStage(updatedStreak)
                : previousStreak == 0 && streakStartedToday ? 1 : 0;
        if (streakBadgeIcon != null) {
            streakBadgeIcon.setTint(oldStageColor);
            ValueAnimator tintAnimator = ValueAnimator.ofArgb(oldStageColor, newStageColor);
            tintAnimator.setDuration(newlyUnlockedStage > 0 ? 1450 : 850);
            tintAnimator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            tintAnimator.addUpdateListener(animation -> {
                if (streakBadgeIcon != null) {
                    streakBadgeIcon.setTint((int) animation.getAnimatedValue());
                    streakBadgeIcon.setGlowStrength(getStreakGlowStrength(updatedStreak));
                }
            });
            tintAnimator.start();
        }
        if (streakBadgeIcon != null && streakStartedToday) {
            streakBadgeIcon.setFlameActive(true);
            streakBadgeIcon.setGlowing(true);
            animateFlameIgnition(streakBadgeIcon);
        } else if (streakBadgeIcon != null && newStageColor != oldStageColor) {
            animateFlameUpgrade(streakBadgeIcon);
        }
        Runnable afterStarFlight = () -> {
            if (overGoalMl > 0) {
                showSurpassedGoalCelebration(overGoalMl, newlyUnlockedStage);
            } else if (goalJustReached) {
                showGoalCelebration(newlyUnlockedStage);
            } else if (newlyUnlockedStage > 0) {
                showStreakCelebration(newlyUnlockedStage, newStageColor,
                        false, false);
            }
        };
        if (earnedStars > 0 && starsScore != null && screenFrame != null) {
            starsScore.setText(previousStars + " stars");
            screenFrame.post(() -> animateEarnedStars(earnedStars, previousStars, updatedStars,
                    sourcePoint, afterStarFlight));
        } else {
            afterStarFlight.run();
        }
    }

    private void animateEarnedStars(int earnedStars, int previousStars, int updatedStars,
                                    PointF sourcePoint, Runnable onComplete) {
        if (starsScore == null || screenFrame == null) {
            return;
        }
        int[] frameLocation = new int[2];
        screenFrame.getLocationOnScreen(frameLocation);
        PointF start = sourcePoint == null
                ? new PointF(screenFrame.getWidth() / 2f, screenFrame.getHeight() / 2f)
                : sourcePoint;
        int[] targetLocation = new int[2];
        starsScore.getLocationOnScreen(targetLocation);
        PointF end = new PointF(targetLocation[0] - frameLocation[0]
                + starsScore.getWidth() / 2f,
                targetLocation[1] - frameLocation[1] + starsScore.getHeight() / 2f);
        StarRewardFlightView flight = new StarRewardFlightView(this, earnedStars);
        screenFrame.addView(flight, new FrameLayout.LayoutParams(MATCH, MATCH));
        flight.play(start, end, previousStars, updatedStars, () -> {
            screenFrame.removeView(flight);
            if (starsScore != null) {
                starsScore.animate().scaleX(1.2f).scaleY(1.2f).setDuration(130)
                        .withEndAction(() -> starsScore.animate().scaleX(1f).scaleY(1f)
                                .setDuration(220).start()).start();
            }
            onComplete.run();
        });
    }

    private int getUnlockedStage(int streak) {
        if (streak >= 30) return 30;
        if (streak >= 14) return 14;
        if (streak >= 7) return 7;
        if (streak >= 3) return 3;
        return streak >= 1 ? 1 : 0;
    }

    private void animateFlameIgnition(WaterIconView icon) {
        icon.setScaleX(0.28f);
        icon.setScaleY(0.28f);
        icon.setAlpha(0.1f);
        icon.setRotation(-24f);
        icon.animate().scaleX(1.35f).scaleY(1.35f).alpha(1f).rotation(8f)
                .setDuration(560)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.7f))
                .withEndAction(() -> icon.animate().scaleX(1f).scaleY(1f).rotation(0f)
                        .setDuration(360).start()).start();
    }

    private void animateFlameUpgrade(WaterIconView icon) {
        icon.animate().scaleX(1.55f).scaleY(1.55f).rotation(-12f)
                .setDuration(180).withEndAction(() -> icon.animate()
                        .scaleX(0.72f).scaleY(0.72f).rotation(10f).setDuration(140)
                        .withEndAction(() -> icon.animate().scaleX(1.18f).scaleY(1.18f)
                                .rotation(0f).setDuration(470)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f))
                                .withEndAction(() -> icon.animate().scaleX(1f).scaleY(1f)
                                        .setDuration(180).start()).start()).start()).start();
    }

    private void showStreakCelebration(int stage, int color, boolean goalReached,
                                      boolean surpassedGoal) {
        String stageName = getStreakStageName(stage);
        String tier = stageName.substring(0, stageName.indexOf(" ·")).toUpperCase(Locale.getDefault());
        String title;
        String subtitle;
        if (stage == 1) {
            title = "ONE SIP.\nA SPARK FORMED.";
            subtitle = "Every beautiful ritual starts somewhere.\nToday, it starts with you.";
        } else if (stage == 3) {
            title = "THREE DAYS.\nA NEW FLAME.";
            subtitle = "You came back for yourself. Your streak just evolved.";
        } else if (stage == 7) {
            title = "ONE WHOLE WEEK.\nSTILL SHOWING UP.";
            subtitle = "Seven little promises kept. Look how bright you burn.";
        } else if (stage == 14) {
            title = "TWO WEEKS OF YOU.";
            subtitle = "Fourteen days of care, now glowing in a new color.";
        } else {
            title = "THIRTY DAYS.\nABSOLUTELY RADIANT.";
            subtitle = "A whole month of showing up. This flame is yours.";
        }
        if (goalReached) {
            subtitle += surpassedGoal
                    ? "\nAnd you passed your daily goal—celebrate, and listen to your body."
                    : "\nYou reached your daily goal too. A lovely double win.";
        }
        subtitle += "\n" + getStreakMomentContext(stage, goalReached, surpassedGoal);

        Dialog dialog = new Dialog(this);
        FrameLayout scene = new FrameLayout(this);
        scene.setBackgroundColor(0xFF170E2B);
        CelebrationBackdrop backdrop = new CelebrationBackdrop(color);
        scene.addView(backdrop, new FrameLayout.LayoutParams(MATCH, MATCH));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout layout = column();
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(dp(28), dp(26), dp(28), dp(120));

        LinearLayout topLabel = new LinearLayout(this);
        topLabel.setGravity(Gravity.CENTER);
        WaterIconView miniFlame = new WaterIconView(this, WaterIconView.STREAK, color);
        miniFlame.setFlameActive(true);
        topLabel.addView(miniFlame, margin(dp(18), dp(22), 0, 0, dp(7), 0));
        TextView label = text(stage == 1 ? "YOUR FIRST STREAK DAY" : "STREAK LEVEL UP",
                10, 0xFFFFD99B, true);
        label.setLetterSpacing(0.2f);
        topLabel.addView(label);
        layout.addView(topLabel, margin(MATCH, WRAP, 0, dp(16), 0, dp(19)));

        TextView headline = text(title, stage >= 7 ? 29 : 31, WHITE, true);
        headline.setGravity(Gravity.CENTER);
        headline.setLetterSpacing(0.025f);
        headline.setLineSpacing(dp(2), 1f);
        layout.addView(headline, margin(MATCH, WRAP, 0, 0, 0, dp(10)));

        TextView copy = text(subtitle, 14, 0xFFE8DDF4, false);
        copy.setGravity(Gravity.CENTER);
        copy.setLineSpacing(dp(4), 1f);
        layout.addView(copy, margin(MATCH, WRAP, 0, 0, 0, dp(23)));

        FrameLayout crest = new FrameLayout(this);
        int crestSize = dp(204);
        View outerRing = new View(this);
        outerRing.setBackground(celebrationRing(color, 2, 100));
        FrameLayout.LayoutParams outerParams = new FrameLayout.LayoutParams(dp(194), dp(194),
                Gravity.CENTER);
        crest.addView(outerRing, outerParams);
        View innerRing = new View(this);
        innerRing.setBackground(celebrationRing(0x99FFFFFF, 1, 80));
        FrameLayout.LayoutParams innerParams = new FrameLayout.LayoutParams(dp(160), dp(160),
                Gravity.CENTER);
        crest.addView(innerRing, innerParams);

        View halo = new View(this);
        GradientDrawable haloDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{withAlpha(color, 120), withAlpha(color, 45), 0x113B164D});
        haloDrawable.setShape(GradientDrawable.OVAL);
        halo.setBackground(haloDrawable);
        FrameLayout.LayoutParams haloParams = new FrameLayout.LayoutParams(dp(130), dp(130),
                Gravity.CENTER);
        crest.addView(halo, haloParams);

        WaterIconView flame = new WaterIconView(this, WaterIconView.STREAK, color);
        flame.setFlameActive(true);
        flame.setGlowing(true);
        FrameLayout.LayoutParams flameParams = new FrameLayout.LayoutParams(dp(94), dp(112),
                Gravity.CENTER);
        crest.addView(flame, flameParams);
        layout.addView(crest, margin(crestSize, crestSize, 0, 0, 0, dp(9)));

        TextView dayCount = text(String.format(Locale.getDefault(), "%02d", stage),
                58, WHITE, true);
        dayCount.setGravity(Gravity.CENTER);
        dayCount.setShadowLayer(dp(18), 0, 0, withAlpha(color, 210));
        layout.addView(dayCount, margin(MATCH, dp(68), 0, dp(3), 0, 0));
        TextView daysLabel = text(stage == 1 ? "DAY STREAK" : "DAYS IN A ROW",
                10, 0xFFFFD99B, true);
        daysLabel.setLetterSpacing(0.22f);
        daysLabel.setGravity(Gravity.CENTER);
        layout.addView(daysLabel, margin(MATCH, WRAP, 0, 0, 0, dp(17)));

        TextView tierLabel = text(tier + "  ·  FLAME UNLOCKED", 11, color, true);
        tierLabel.setGravity(Gravity.CENTER);
        tierLabel.setPadding(dp(18), dp(10), dp(18), dp(10));
        tierLabel.setBackground(celebrationRing(withAlpha(color, 115), 1, 24));
        layout.addView(tierLabel, margin(WRAP, WRAP, 0, 0, 0, dp(9)));
        int nextStage = stage < 3 ? 3 : stage < 7 ? 7 : stage < 14 ? 14 : stage < 30 ? 30 : 0;
        TextView nextStep = text(nextStage == 0 ? "A month of care. Keep glowing."
                        : "NEXT FLAME  ·  " + nextStage + " DAYS",
                10, 0xFFD4C7E0, true);
        nextStep.setLetterSpacing(0.08f);
        layout.addView(nextStep, margin(WRAP, WRAP, 0, 0, 0, dp(12)));

        scroll.addView(layout, new ScrollView.LayoutParams(MATCH, WRAP));
        scene.addView(scroll, new FrameLayout.LayoutParams(MATCH, MATCH));

        TextView close = text("✕", 18, 0xFFE8DDF4, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(celebrationRing(0x66FFFFFF, 1, 24));
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(42), dp(42),
                Gravity.TOP | Gravity.END);
        closeParams.setMargins(0, dp(18), dp(18), 0);
        scene.addView(close, closeParams);
        close.setOnClickListener(view -> dialog.dismiss());

        TextView continueButton = text("KEEP THIS FIRE GOING   →", 13, 0xFF281735, true);
        continueButton.setGravity(Gravity.CENTER);
        continueButton.setLetterSpacing(0.045f);
        continueButton.setBackground(gradientBackground(0xFFFFE19B, 0xFFFFB85C, 20));
        continueButton.setElevation(dp(8));
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(MATCH, dp(56),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        buttonParams.setMargins(dp(26), 0, dp(26), dp(26));
        scene.addView(continueButton, buttonParams);
        continueButton.setOnClickListener(view -> dialog.dismiss());

        dialog.setContentView(scene);
        dialog.setCancelable(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            window.setStatusBarColor(0xFF170E2B);
            window.setNavigationBarColor(0xFF170E2B);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.62f;
            window.setAttributes(attributes);
        }

        crest.setScaleX(0.62f);
        crest.setScaleY(0.62f);
        crest.setAlpha(0.25f);
        crest.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(760)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.35f)).start();
        dayCount.setScaleX(0.7f);
        dayCount.setScaleY(0.7f);
        dayCount.animate().scaleX(1f).scaleY(1f).setStartDelay(230).setDuration(620)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f)).start();
        pulseCelebrationRings(outerRing, innerRing);
    }

    private String getStreakMomentContext(int stage, boolean goalReached,
                                          boolean surpassedGoal) {
        int todayTotal = HydrationPrefs.getTodayIntake(this);
        int goal = HydrationPrefs.getDailyGoal(this);
        if (goalReached) {
            return surpassedGoal
                    ? "It’s " + getTimeOfDay() + " and you’ve gone beyond your goal. "
                    + "Your streak and your progress are both showing up."
                    : "You reached your goal " + getTimeOfDay() + " and unlocked "
                    + stage + " days. That’s a lovely double milestone.";
        }
        if (todayTotal == 0) {
            return "A first sip today made day " + stage
                    + " possible. Your flame is ready for tomorrow.";
        }
        int remaining = Math.max(0, goal - todayTotal);
        int progress = Math.min(100, todayTotal * 100 / Math.max(1, goal));
        if (remaining <= Math.max(100, goal / 10)) {
            return String.format(Locale.getDefault(),
                    "It’s %s; you’re at %d%% of today’s goal, just %,d ml away.",
                    getTimeOfDay(), progress, remaining);
        }
        return String.format(Locale.getDefault(),
                "It’s %s and you’re %d%% toward your goal. Your %d-day flame just changed color.",
                getTimeOfDay(), progress, stage);
    }

    private void pulseCelebrationRings(View outer, View inner) {
        ValueAnimator outerPulse = ValueAnimator.ofFloat(0.92f, 1.12f);
        outerPulse.setDuration(1450);
        outerPulse.setRepeatCount(ValueAnimator.INFINITE);
        outerPulse.setRepeatMode(ValueAnimator.REVERSE);
        outerPulse.addUpdateListener(animation -> {
            float scale = (float) animation.getAnimatedValue();
            outer.setScaleX(scale);
            outer.setScaleY(scale);
            outer.setAlpha(1.05f - scale * 0.45f);
        });
        outerPulse.start();
        ValueAnimator innerPulse = ValueAnimator.ofFloat(1.08f, 0.94f);
        innerPulse.setDuration(1450);
        innerPulse.setStartDelay(180);
        innerPulse.setRepeatCount(ValueAnimator.INFINITE);
        innerPulse.setRepeatMode(ValueAnimator.REVERSE);
        innerPulse.addUpdateListener(animation -> {
            float scale = (float) animation.getAnimatedValue();
            inner.setScaleX(scale);
            inner.setScaleY(scale);
        });
        innerPulse.start();
    }

    private GradientDrawable celebrationRing(int color, int strokeDp, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(0x00000000);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(strokeDp), color);
        return drawable;
    }

    private int withAlpha(int color, int alpha) {
        return color & 0x00FFFFFF | Math.max(0, Math.min(255, alpha)) << 24;
    }

    private final class CelebrationBackdrop extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int accent;
        private Shader backgroundShader;
        private Shader glowShader;
        private ValueAnimator animator;
        private float phase;

        CelebrationBackdrop(int accent) {
            super(MainActivity.this);
            this.accent = accent;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            backgroundShader = new LinearGradient(0, 0, width, height,
                    new int[]{0xFF160D2A, blendCelebration(accent, 0xFF27143E, 0.7f),
                            0xFF111327},
                    null, Shader.TileMode.CLAMP);
            glowShader = new RadialGradient(width * 0.5f, height * 0.42f,
                    Math.max(width, height) * 0.76f,
                    new int[]{withAlpha(accent, 100), withAlpha(accent, 34), 0x00170E2B},
                    new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(2800);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.addUpdateListener(animation -> {
                phase = (float) animation.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (backgroundShader == null || glowShader == null) {
                return;
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(backgroundShader);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(glowShader);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setShader(null);

            float centerX = getWidth() * 0.5f;
            float centerY = getHeight() * 0.42f;
            canvas.save();
            canvas.rotate((phase - 0.5f) * 18f, centerX, centerY);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            for (int ray = 0; ray < 28; ray++) {
                double angle = Math.PI * 2 * ray / 28;
                float inner = dp(126);
                float outer = dp(225 + ray % 4 * 13);
                paint.setColor(withAlpha(0xFFFFE6B0, ray % 2 == 0 ? 24 : 12));
                canvas.drawLine(centerX + (float) Math.cos(angle) * inner,
                        centerY + (float) Math.sin(angle) * inner,
                        centerX + (float) Math.cos(angle) * outer,
                        centerY + (float) Math.sin(angle) * outer, paint);
            }
            canvas.restore();

            paint.setStyle(Paint.Style.FILL);
            paint.setShadowLayer(dp(8), 0, 0, accent);
            for (int i = 0; i < 34; i++) {
                float travel = (phase + i * 0.173f) % 1f;
                float seedX = (i * 47 % 101) / 100f;
                float drift = (float) Math.sin((phase * Math.PI * 2) + i * 1.7f) * dp(19);
                float x = getWidth() * seedX + drift;
                float y = getHeight() + dp(18) - travel * (getHeight() + dp(36));
                float visibility = Math.min(1f, Math.min(travel * 4f, (1f - travel) * 4f));
                paint.setColor(withAlpha(i % 3 == 0 ? 0xFFFFE2A0 : 0xFFFFFFFF,
                        (int) (visibility * (i % 4 == 0 ? 210 : 135))));
                float radius = dp(i % 5 == 0 ? 2 : 1);
                canvas.drawCircle(x, y, radius, paint);
                if (i % 4 == 0) {
                    canvas.drawCircle(x, y, radius * 2.4f, paint);
                }
            }
            paint.clearShadowLayer();
        }

        private int blendCelebration(int first, int second, float amount) {
            int red = (int) (((first >> 16) & 0xFF) * (1 - amount)
                    + ((second >> 16) & 0xFF) * amount);
            int green = (int) (((first >> 8) & 0xFF) * (1 - amount)
                    + ((second >> 8) & 0xFF) * amount);
            int blue = (int) ((first & 0xFF) * (1 - amount) + (second & 0xFF) * amount);
            return 0xFF000000 | red << 16 | green << 8 | blue;
        }
    }

    private final class GoalPremiumBackground extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private Shader baseShader;
        private Shader cornerGlow;

        GoalPremiumBackground() {
            super(MainActivity.this);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            baseShader = new LinearGradient(0, 0, width * 0.82f, height,
                    new int[]{0xFF513652, 0xFF38283F, 0xFF251C31},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
            cornerGlow = new RadialGradient(width * 0.86f, height * 0.03f,
                    Math.max(width, height) * 0.82f,
                    new int[]{0x55FFCF73, 0x20E991B6, 0x00251C31},
                    new float[]{0f, 0.34f, 1f}, Shader.TileMode.CLAMP);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            bounds.set(0, 0, getWidth(), getHeight());
            float radius = dp(26);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(baseShader);
            canvas.drawRoundRect(bounds, radius, radius, paint);

            Path clip = new Path();
            clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            paint.setShader(cornerGlow);
            canvas.drawRoundRect(bounds, radius, radius, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(0x66FFFFFF);
            RectF inset = new RectF(dp(1), dp(1), getWidth() - dp(1), getHeight() - dp(1));
            canvas.drawRoundRect(inset, radius - dp(1), radius - dp(1), paint);
            paint.setStrokeWidth(dp(2));
            paint.setColor(0xA6F1C879);
            canvas.drawRoundRect(bounds, radius, radius, paint);

            paint.setStyle(Paint.Style.FILL);
            float glintX = getWidth() - dp(28);
            paint.setColor(0xFFFFE9A6);
            canvas.drawCircle(glintX, dp(20), dp(2), paint);
            paint.setStrokeWidth(dp(1));
            paint.setColor(0xBBFFF3CD);
            canvas.drawLine(glintX - dp(6), dp(20), glintX + dp(6), dp(20), paint);
            canvas.drawLine(glintX, dp(14), glintX, dp(26), paint);
            canvas.restore();
            paint.setShader(null);
        }
    }

    private void showGoalCelebration(int newlyUnlockedStage) {
        final int aqua = 0xFF42E4D0;
        final int todayTotal = HydrationPrefs.getTodayIntake(this);
        final int dailyGoal = HydrationPrefs.getDailyGoal(this);
        Dialog dialog = new Dialog(this);
        FrameLayout scene = new FrameLayout(this);
        scene.setBackgroundColor(0xFF101B32);
        scene.addView(new CelebrationBackdrop(aqua),
                new FrameLayout.LayoutParams(MATCH, MATCH));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout celebration = column();
        celebration.setGravity(Gravity.CENTER_HORIZONTAL);
        celebration.setPadding(dp(27), dp(46), dp(27), dp(118));

        TextView eyebrow = text("A PROMISE KEPT TO YOURSELF", 10, 0xFFB3FFF1, true);
        eyebrow.setLetterSpacing(0.17f);
        eyebrow.setGravity(Gravity.CENTER);
        celebration.addView(eyebrow, margin(MATCH, WRAP, 0, dp(8), 0, dp(19)));

        TextView headline = text("YOU DID IT.", 38, WHITE, true);
        headline.setGravity(Gravity.CENTER);
        headline.setLetterSpacing(0.08f);
        celebration.addView(headline, margin(MATCH, WRAP, 0, 0, 0, dp(4)));
        String name = HydrationPrefs.getName(this);
        TextView subheading = text("Goal complete " + getTimeOfDay() + ", " + name + ".",
                15, 0xFFD7F8F3, false);
        subheading.setGravity(Gravity.CENTER);
        celebration.addView(subheading, margin(MATCH, WRAP, 0, 0, 0, dp(10)));

        FrameLayout emblem = new FrameLayout(this);
        View glow = new View(this);
        GradientDrawable glowBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xBB48FFE0, 0x6656CFFF, 0x004CB8FF});
        glowBackground.setShape(GradientDrawable.OVAL);
        glow.setBackground(glowBackground);
        emblem.addView(glow, new FrameLayout.LayoutParams(dp(206), dp(206), Gravity.CENTER));
        View orbit = new View(this);
        orbit.setBackground(celebrationRing(0xAA8FFFF4, 2, 100));
        emblem.addView(orbit, new FrameLayout.LayoutParams(dp(180), dp(180), Gravity.CENTER));
        View orbitInner = new View(this);
        orbitInner.setBackground(celebrationRing(0x55FFFFFF, 1, 100));
        emblem.addView(orbitInner, new FrameLayout.LayoutParams(dp(142), dp(142), Gravity.CENTER));

        WaterIconView goalDrop = new WaterIconView(this, WaterIconView.DROP, aqua);
        goalDrop.setGlowing(true);
        emblem.addView(goalDrop,
                new FrameLayout.LayoutParams(dp(92), dp(108), Gravity.CENTER));
        WaterIconView bloom = new WaterIconView(this, WaterIconView.FLOWER, 0xFFFF94C8);
        bloom.setGlowing(true);
        FrameLayout.LayoutParams bloomParams = new FrameLayout.LayoutParams(dp(45), dp(45),
                Gravity.TOP | Gravity.END);
        bloomParams.setMargins(0, dp(34), dp(37), 0);
        emblem.addView(bloom, bloomParams);
        WaterIconView star = new WaterIconView(this, WaterIconView.STAR, 0xFFFFD46B);
        star.setGlowing(true);
        FrameLayout.LayoutParams starParams = new FrameLayout.LayoutParams(dp(37), dp(37),
                Gravity.BOTTOM | Gravity.START);
        starParams.setMargins(dp(40), 0, 0, dp(39));
        emblem.addView(star, starParams);
        celebration.addView(emblem, margin(dp(244), dp(238), 0, dp(4), 0, dp(5)));

        TextView completion = text("100%", 57, 0xFFB9FFF2, true);
        completion.setGravity(Gravity.CENTER);
        completion.setShadowLayer(dp(18), 0, 0, 0xAA42E4D0);
        celebration.addView(completion, margin(MATCH, dp(68), 0, 0, 0, 0));
        TextView mark = text("GOAL MET  ·  BEAUTIFULLY DONE", 10, 0xFFFFE49D, true);
        mark.setGravity(Gravity.CENTER);
        mark.setLetterSpacing(0.14f);
        celebration.addView(mark, margin(MATCH, WRAP, 0, 0, 0, dp(19)));

        LinearLayout totalCard = column();
        totalCard.setGravity(Gravity.CENTER);
        totalCard.setPadding(dp(19), dp(15), dp(19), dp(15));
        totalCard.setBackground(celebrationRing(0x66FFFFFF, 1, 20));
        totalCard.addView(text(String.format(Locale.getDefault(), "%,d ml", todayTotal),
                23, WHITE, true));
        totalCard.addView(text("of your " + String.format(Locale.getDefault(), "%,d ml", dailyGoal)
                        + " daily goal", 12, 0xFFD7F8F3, false),
                margin(MATCH, WRAP, 0, dp(4), 0, 0));
        String praiseCopy = HydrationPrefs.getCurrentStreak(this) >= 3
                ? "Goal met, streak at " + HydrationPrefs.getCurrentStreak(this)
                + " days. That’s two wins in one."
                : "Pause for a second and take that in.\nYou showed up for your wellbeing today.";
        TextView praise = text(praiseCopy,
                13, 0xFFE7E2F2, false);
        praise.setGravity(Gravity.CENTER);
        praise.setLineSpacing(dp(4), 1f);
        totalCard.addView(praise, margin(MATCH, WRAP, 0, dp(11), 0, 0));
        celebration.addView(totalCard, margin(MATCH, WRAP, 0, 0, 0, dp(17)));

        if (newlyUnlockedStage > 0) {
            TextView streakBonus = text("STREAK UPGRADE UNLOCKED  ·  "
                            + getStreakStageName(newlyUnlockedStage)
                            .toUpperCase(Locale.getDefault()),
                    10, getStreakStageColor(newlyUnlockedStage), true);
            streakBonus.setGravity(Gravity.CENTER);
            streakBonus.setPadding(dp(14), dp(11), dp(14), dp(11));
            streakBonus.setBackground(celebrationRing(
                    withAlpha(getStreakStageColor(newlyUnlockedStage), 160), 1, 24));
            celebration.addView(streakBonus, margin(WRAP, WRAP, 0, 0, 0, dp(12)));
        }

        scroll.addView(celebration, new ScrollView.LayoutParams(MATCH, WRAP));
        scene.addView(scroll, new FrameLayout.LayoutParams(MATCH, MATCH));
        TextView close = text("✕", 18, 0xFFE8F5F5, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(celebrationRing(0x66FFFFFF, 1, 24));
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(42), dp(42),
                Gravity.TOP | Gravity.END);
        closeParams.setMargins(0, dp(18), dp(18), 0);
        scene.addView(close, closeParams);
        close.setOnClickListener(view -> dialog.dismiss());

        TextView action = text("CELEBRATE THIS WIN   ✦", 13, 0xFF102B36, true);
        action.setGravity(Gravity.CENTER);
        action.setLetterSpacing(0.05f);
        action.setBackground(gradientBackground(0xFFB9FFF2, 0xFF63D9D7, 20));
        action.setElevation(dp(8));
        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(MATCH, dp(55),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        actionParams.setMargins(dp(25), 0, dp(25), dp(24));
        scene.addView(action, actionParams);
        action.setOnClickListener(view -> dialog.dismiss());

        dialog.setContentView(scene);
        dialog.setCancelable(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            window.setStatusBarColor(0xFF101B32);
            window.setNavigationBarColor(0xFF101B32);
        }

        emblem.setScaleX(0.5f);
        emblem.setScaleY(0.5f);
        emblem.setAlpha(0.2f);
        emblem.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(760)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.65f)).start();
        ValueAnimator pulse = ValueAnimator.ofFloat(0.94f, 1.08f);
        pulse.setDuration(1160);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.addUpdateListener(animation -> {
            float scale = (float) animation.getAnimatedValue();
            glow.setScaleX(scale);
            glow.setScaleY(scale);
            orbit.setRotation(scale * 20f);
            orbitInner.setScaleX(2f - scale);
            orbitInner.setScaleY(2f - scale);
        });
        pulse.start();
        bloom.setScaleX(0.1f);
        bloom.setScaleY(0.1f);
        bloom.animate().scaleX(1f).scaleY(1f).rotation(36f).setStartDelay(280)
                .setDuration(570)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.7f)).start();
        star.setScaleX(0.1f);
        star.setScaleY(0.1f);
        star.animate().scaleX(1f).scaleY(1f).rotation(30f).setStartDelay(410)
                .setDuration(570)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.7f)).start();
    }

    private void showSurpassedGoalCelebration(int overGoalMl, int newlyUnlockedStage) {
        final int gold = 0xFFFFC442;
        final int todayTotal = HydrationPrefs.getTodayIntake(this);
        final int dailyGoal = HydrationPrefs.getDailyGoal(this);
        Dialog dialog = new Dialog(this);
        FrameLayout scene = new FrameLayout(this);
        scene.setBackgroundColor(0xFF171027);
        scene.addView(new CelebrationBackdrop(gold),
                new FrameLayout.LayoutParams(MATCH, MATCH));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout contentColumn = column();
        contentColumn.setGravity(Gravity.CENTER_HORIZONTAL);
        contentColumn.setPadding(dp(27), dp(44), dp(27), dp(115));

        TextView ribbon = text("A GOLDEN MOMENT", 10, 0xFFFFE6A0, true);
        ribbon.setLetterSpacing(0.24f);
        ribbon.setGravity(Gravity.CENTER);
        contentColumn.addView(ribbon, margin(MATCH, WRAP, 0, dp(7), 0, dp(19)));

        TextView heading = text("YOU WENT\nBEYOND YOUR GOAL.", 29, WHITE, true);
        heading.setGravity(Gravity.CENTER);
        heading.setLetterSpacing(0.025f);
        heading.setLineSpacing(dp(3), 1f);
        contentColumn.addView(heading, margin(MATCH, WRAP, 0, 0, 0, dp(10)));

        TextView subheading = text("Look at that progress.", 14, 0xFFE8DDF4, false);
        subheading.setGravity(Gravity.CENTER);
        contentColumn.addView(subheading, margin(MATCH, WRAP, 0, 0, 0, dp(8)));

        FrameLayout burst = new FrameLayout(this);
        FrameLayout.LayoutParams burstParams = new FrameLayout.LayoutParams(dp(250), dp(250),
                Gravity.CENTER);
        View halo = new View(this);
        GradientDrawable haloDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xAAFFD866, 0x55FF982F, 0x00FF982F});
        haloDrawable.setShape(GradientDrawable.OVAL);
        halo.setBackground(haloDrawable);
        burst.addView(halo, new FrameLayout.LayoutParams(dp(210), dp(210), Gravity.CENTER));

        View orbitOne = new View(this);
        orbitOne.setBackground(celebrationRing(0x88FFE89C, 1, 100));
        burst.addView(orbitOne, new FrameLayout.LayoutParams(dp(190), dp(190), Gravity.CENTER));
        View orbitTwo = new View(this);
        orbitTwo.setBackground(celebrationRing(0x55FFFFFF, 1, 100));
        burst.addView(orbitTwo, new FrameLayout.LayoutParams(dp(155), dp(155), Gravity.CENTER));

        WaterIconView heroStar = new WaterIconView(this, WaterIconView.STAR, gold);
        heroStar.setGlowing(true);
        FrameLayout.LayoutParams heroParams = new FrameLayout.LayoutParams(dp(112), dp(112),
                Gravity.CENTER);
        burst.addView(heroStar, heroParams);

        WaterIconView blossomA = new WaterIconView(this, WaterIconView.FLOWER, 0xFFFF88AE);
        blossomA.setGlowing(true);
        FrameLayout.LayoutParams blossomAParams = new FrameLayout.LayoutParams(dp(43), dp(43),
                Gravity.TOP | Gravity.END);
        blossomAParams.setMargins(0, dp(37), dp(31), 0);
        burst.addView(blossomA, blossomAParams);
        WaterIconView blossomB = new WaterIconView(this, WaterIconView.FLOWER, 0xFF91E8D0);
        blossomB.setGlowing(true);
        FrameLayout.LayoutParams blossomBParams = new FrameLayout.LayoutParams(dp(35), dp(35),
                Gravity.BOTTOM | Gravity.START);
        blossomBParams.setMargins(dp(35), 0, 0, dp(37));
        burst.addView(blossomB, blossomBParams);
        contentColumn.addView(burst, margin(dp(250), dp(250), 0, dp(8), 0, dp(4)));

        TextView overage = text(String.format(Locale.getDefault(), "+%,d ml", overGoalMl),
                38, 0xFFFFD76B, true);
        overage.setGravity(Gravity.CENTER);
        overage.setShadowLayer(dp(16), 0, 0, 0xAAFF9D2E);
        contentColumn.addView(overage, margin(MATCH, dp(54), 0, dp(1), 0, 0));
        TextView beyondLabel = text("PAST YOUR DAILY GOAL", 10, 0xFFFFE7AA, true);
        beyondLabel.setGravity(Gravity.CENTER);
        beyondLabel.setLetterSpacing(0.18f);
        contentColumn.addView(beyondLabel, margin(MATCH, WRAP, 0, 0, 0, dp(17)));

        LinearLayout totalsCard = column();
        totalsCard.setGravity(Gravity.CENTER);
        totalsCard.setPadding(dp(18), dp(15), dp(18), dp(15));
        totalsCard.setBackground(celebrationRing(0x66FFFFFF, 1, 20));
        totalsCard.addView(text(String.format(Locale.getDefault(), "%,d ml  /  %,d ml",
                todayTotal, dailyGoal), 16, WHITE, true));
        TextView body = text("You’ve done what you set out to do—and then some. Take a pause, enjoy the win, and listen to your body’s thirst cues.",
                12, 0xFFE1D7ED, false);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(dp(4), 1f);
        totalsCard.addView(body, margin(MATCH, WRAP, dp(3), dp(8), dp(3), 0));
        contentColumn.addView(totalsCard, margin(MATCH, WRAP, 0, dp(2), 0, dp(16)));
        if (newlyUnlockedStage > 0) {
            TextView streakBonus = text("BONUS UNLOCK  ·  "
                            + getStreakStageName(newlyUnlockedStage).toUpperCase(Locale.getDefault()),
                    10, getStreakStageColor(newlyUnlockedStage), true);
            streakBonus.setGravity(Gravity.CENTER);
            streakBonus.setPadding(dp(15), dp(10), dp(15), dp(10));
            streakBonus.setBackground(celebrationRing(
                    withAlpha(getStreakStageColor(newlyUnlockedStage), 145), 1, 24));
            contentColumn.addView(streakBonus, margin(WRAP, WRAP, 0, 0, 0, dp(10)));
        }

        scroll.addView(contentColumn, new ScrollView.LayoutParams(MATCH, WRAP));
        scene.addView(scroll, new FrameLayout.LayoutParams(MATCH, MATCH));

        TextView close = text("✕", 18, 0xFFE8DDF4, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(celebrationRing(0x66FFFFFF, 1, 24));
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(42), dp(42),
                Gravity.TOP | Gravity.END);
        closeParams.setMargins(0, dp(18), dp(18), 0);
        scene.addView(close, closeParams);
        close.setOnClickListener(view -> dialog.dismiss());

        TextView action = text("SAVOR THE WIN   ✦", 13, 0xFF30200C, true);
        action.setGravity(Gravity.CENTER);
        action.setLetterSpacing(0.06f);
        action.setBackground(gradientBackground(0xFFFFE69A, 0xFFFFB842, 20));
        action.setElevation(dp(8));
        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(MATCH, dp(55),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        actionParams.setMargins(dp(25), 0, dp(25), dp(24));
        scene.addView(action, actionParams);
        action.setOnClickListener(view -> dialog.dismiss());

        dialog.setContentView(scene);
        dialog.setCancelable(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            window.setStatusBarColor(0xFF171027);
            window.setNavigationBarColor(0xFF171027);
        }

        heroStar.setScaleX(0.25f);
        heroStar.setScaleY(0.25f);
        heroStar.setRotation(-28f);
        heroStar.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(720)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f)).start();
        ValueAnimator pulse = ValueAnimator.ofFloat(0.94f, 1.07f);
        pulse.setDuration(1050);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.addUpdateListener(animation -> {
            float scale = (float) animation.getAnimatedValue();
            halo.setScaleX(scale);
            halo.setScaleY(scale);
            halo.setAlpha(1.1f - scale * 0.35f);
            orbitOne.setRotation((float) animation.getAnimatedValue() * 16f);
            orbitTwo.setRotation(-(float) animation.getAnimatedValue() * 22f);
        });
        pulse.start();
        blossomA.setScaleX(0.1f);
        blossomA.setScaleY(0.1f);
        blossomA.animate().scaleX(1f).scaleY(1f).rotation(28f).setStartDelay(280)
                .setDuration(560)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.7f)).start();
        blossomB.setScaleX(0.1f);
        blossomB.setScaleY(0.1f);
        blossomB.animate().scaleX(1f).scaleY(1f).rotation(-25f).setStartDelay(400)
                .setDuration(560)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.7f)).start();
    }

    private void showRewardMoment(String eyebrow, String title, String message, int color,
                                  boolean goldenStar) {
        Dialog dialog = new Dialog(this);
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(WaterTheme.isDark(this) ? 0xEE15111B : 0xEDEDE1E8);
        LinearLayout panel = column();
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(25), dp(30), dp(25), dp(24));
        panel.setBackground(gradientBackground(DEEP_PLUM, 0xFF77506D, 30));
        panel.setElevation(dp(18));

        FrameLayout emblem = new FrameLayout(this);
        emblem.setBackground(background(0x22FFFFFF, 70));
        WaterIconView rewardIcon = new WaterIconView(this,
                goldenStar ? WaterIconView.STAR : WaterIconView.STREAK, color);
        rewardIcon.setFlameActive(!goldenStar);
        rewardIcon.setGlowing(true);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(92), dp(92),
                Gravity.CENTER);
        emblem.addView(rewardIcon, iconParams);
        panel.addView(emblem, margin(dp(110), dp(110), 0, 0, 0, dp(20)));

        TextView eyebrowText = text(eyebrow, 9, 0xFFFFDCA7, true);
        eyebrowText.setLetterSpacing(0.18f);
        panel.addView(eyebrowText, margin(WRAP, WRAP, 0, 0, 0, dp(10)));
        TextView heading = text(title, 25, WHITE, true);
        heading.setGravity(Gravity.CENTER);
        panel.addView(heading, margin(MATCH, WRAP, 0, 0, 0, dp(10)));
        TextView details = text(message, 13, 0xFFF4E6F1, false);
        details.setGravity(Gravity.CENTER);
        panel.addView(details, margin(MATCH, WRAP, 0, 0, 0, dp(21)));

        TextView close = text("Keep it going", 13, DEEP_PLUM, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(gradientBackground(0xFFFFF7E7, 0xFFFFDDA7, 17));
        panel.addView(close, margin(MATCH, dp(51), 0, 0, 0, 0));
        close.setOnClickListener(view -> dialog.dismiss());

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                MATCH, WRAP, Gravity.CENTER);
        panelParams.setMargins(dp(24), dp(28), dp(24), dp(28));
        overlay.addView(panel, panelParams);
        dialog.setContentView(overlay);
        dialog.setCancelable(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(getResources().getDisplayMetrics().widthPixels,
                    WindowManager.LayoutParams.MATCH_PARENT);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.55f;
            window.setAttributes(attributes);
        }
        panel.setScaleX(0.55f);
        panel.setScaleY(0.55f);
        panel.setAlpha(0.15f);
        panel.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(680)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.25f)).start();
        rewardIcon.setScaleX(0.2f);
        rewardIcon.setScaleY(0.2f);
        rewardIcon.animate().scaleX(1.12f).scaleY(1.12f).setDuration(510)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2f))
                .withEndAction(() -> rewardIcon.animate().scaleX(1f).scaleY(1f)
                        .setDuration(260).start()).start();
        for (int i = 0; i < 6; i++) {
            WaterIconView sparkle = new WaterIconView(this, WaterIconView.STAR,
                    goldenStar ? 0xFFFFD36B : (i % 2 == 0 ? color : 0xFFFFD59A));
            FrameLayout.LayoutParams sparkleParams = new FrameLayout.LayoutParams(
                    dp(15), dp(15), Gravity.CENTER);
            overlay.addView(sparkle, sparkleParams);
            double angle = Math.PI * 2 * i / 6;
            sparkle.animate().translationX((float) Math.cos(angle) * dp(132))
                    .translationY((float) Math.sin(angle) * dp(155))
                    .rotation(200f).scaleX(0.45f).scaleY(0.45f).alpha(0f)
                    .setDuration(780).setStartDelay(90L + i * 45L).start();
        }
    }

    private void undoWater() {
        int removed = HydrationPrefs.undoLastIntake(this);
        if (removed > 0) {
            renderScreen();
            showFeedback(getString(R.string.water_removed, removed));
        }
    }

    private void showCustomSipDialog() {
        LinearLayout container = column();
        container.setPadding(0, dp(6), 0, 0);
        EditText amountInput = amountInput(250);
        container.addView(amountInput, margin(MATCH, dp(62), 0, 0, 0, dp(9)));
        container.addView(text("Your amount, in millilitres", 10, MUTED, false),
                margin(MATCH, WRAP, 0, 0, 0, dp(12)));
        addAmountPresets(container, amountInput, new int[]{150, 250, 350, 500});
        showStyledDialog("Add a custom sip", "Tap a size or enter the exact amount.",
                container, "Cancel", "Log sip", () -> {
                    int amount = parseAmount(amountInput);
                    if (amount < 1 || amount > 5000) {
                        amountInput.setError("Enter a whole number from 1 to 5,000 ml");
                        return false;
                    }
                    screenFrame.post(() -> recordWater(amount));
                    return true;
                });
    }

    private void showGoalDialog() {
        LinearLayout container = column();
        container.setPadding(0, dp(6), 0, 0);
        EditText goalInput = amountInput(HydrationPrefs.getDailyGoal(this));
        container.addView(goalInput, margin(MATCH, dp(62), 0, 0, 0, dp(9)));
        container.addView(text("Set the goal directly in millilitres (250–10,000 ml).",
                        10, MUTED, false),
                margin(MATCH, WRAP, 0, 0, 0, dp(12)));
        addAmountPresets(container, goalInput, new int[]{2000, 2500, 3000, 3500, 4000});
        showStyledDialog("Your daily water goal", "Choose a target that feels right for you.",
                container, "Cancel", "Save goal", () -> {
                    int goal = parseAmount(goalInput);
                    if (goal < 250 || goal > 10000) {
                        goalInput.setError("Enter a whole number from 250 to 10,000 ml");
                        return false;
                    }
                    HydrationPrefs.setDailyGoal(this, goal);
                    screenFrame.post(this::renderScreen);
                    return true;
                });
    }

    private void showNameDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(HydrationPrefs.getName(this));
        input.setSelectAllOnFocus(true);
        LinearLayout container = new LinearLayout(this);
        container.setPadding(0, dp(6), 0, 0);
        styleInput(input);
        container.addView(input, new LinearLayout.LayoutParams(MATCH, dp(56)));
        showStyledDialog("What should I call you?", "Your name personalizes your reminders.",
                container, "Cancel", "Save", () -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        input.setError("Please enter a name");
                        return false;
                    }
                    HydrationPrefs.setName(this, name);
                    screenFrame.post(this::renderScreen);
                    return true;
                });
    }

    private void editReminderTime(int index) {
        if (!HydrationPrefs.areRemindersEnabled(this)
                || !HydrationPrefs.isReminderEnabled(this, index)) {
            showFeedback("Turn on reminders to edit their times.");
            return;
        }
        List<String> times = HydrationPrefs.getReminderTimes(this);
        showTimePickerDialog("Edit reminder time", times.get(index), (hour, minute) -> {
            HydrationPrefs.setReminderTime(this, index,
                    String.format(Locale.US, "%02d:%02d", hour, minute));
            ReminderScheduler.scheduleAll(this);
            screenFrame.post(() -> {
                renderScreen();
                showFeedback(getString(R.string.time_saved));
            });
        });
    }

    private void chooseAlarmTone() {
        Uri current = HydrationPrefs.getAlarmTone(this);
        showChoiceDialog("Reminder sound", "Choose an audio file stored on this device.",
                new String[]{"Use the device default", "Choose an audio file"},
                current == null ? 0 : 1, choice -> {
                    if (choice == 0) {
                        saveAlarmTone(null);
                    } else {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("audio/*");
                        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                        startActivityForResult(intent, REQUEST_ALARM_TONE);
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ALARM_TONE && resultCode == RESULT_OK && data != null) {
            Uri tone = data.getData();
            int grantedFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            int persistableFlag = data.getFlags() & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
            if (tone == null || !"content".equals(tone.getScheme())
                    || grantedFlags == 0 || persistableFlag == 0) {
                showFeedback("That file can’t be kept for reminders. Choose a local audio file.");
                return;
            }
            try {
                getContentResolver().takePersistableUriPermission(tone,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException | IllegalArgumentException exception) {
                Log.w(TAG, "Could not keep access to the selected alarm file.", exception);
                showFeedback("That file can’t be kept for reminders. Choose another audio file.");
                return;
            }
            saveAlarmTone(tone);
        } else if (requestCode == REQUEST_EXACT_ALARMS) {
            ReminderScheduler.scheduleAll(this);
            renderScreen();
        }
    }

    private void saveAlarmTone(Uri tone) {
        Uri oldTone = HydrationPrefs.getAlarmTone(this);
        HydrationPrefs.setAlarmTone(this, tone);
        if (oldTone != null && !oldTone.equals(tone)
                && "content".equals(oldTone.getScheme())) {
            try {
                getContentResolver().releasePersistableUriPermission(oldTone,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException exception) {
                Log.w(TAG, "Could not release access to the previous alarm file.", exception);
            }
        }
        if (!oldToneEquals(oldTone, tone)) {
            android.app.NotificationManager manager =
                    getSystemService(android.app.NotificationManager.class);
            if (manager != null) {
                manager.deleteNotificationChannel(ReminderNotifications.channelId());
            }
        }
        ReminderNotifications.createChannel(this);
        renderScreen();
        showFeedback(getString(R.string.tone_saved));
    }

    private boolean oldToneEquals(Uri first, Uri second) {
        return first == null ? second == null : first.equals(second);
    }

    private String getToneTitle() {
        Uri tone = HydrationPrefs.getAlarmTone(this);
        if (tone == null) {
            return "Default alarm tone";
        }
        try (Cursor cursor = getContentResolver().query(tone,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameColumn >= 0) {
                    return cursor.getString(nameColumn);
                }
            }
        } catch (SecurityException exception) {
            Log.w(TAG, "Could not read the selected alarm file name.", exception);
            return "Audio file access expired";
        }
        Ringtone ringtone = RingtoneManager.getRingtone(this, tone);
        return ringtone == null ? "Selected audio file" : ringtone.getTitle(this);
    }

    private void updateReminderStatus() {
        if (reminderStatus == null) {
            return;
        }
        if (!HydrationPrefs.areRemindersEnabled(this)) {
            reminderStatus.setText("Reminders are paused. Turn them on whenever you like.");
            return;
        }
        boolean notifications = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        boolean exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || getSystemService(android.app.AlarmManager.class).canScheduleExactAlarms();
        if (!notifications) {
            reminderStatus.setText(R.string.status_notifications);
        } else if (Build.VERSION.SDK_INT >= 34
                && !getSystemService(android.app.NotificationManager.class).canUseFullScreenIntent()) {
            reminderStatus.setText("Allow full-screen alarms to see the check-in straight away.");
        } else if (!exact) {
            reminderStatus.setText(R.string.status_exact_alarms);
        } else {
            reminderStatus.setText(R.string.status_ready);
        }
    }

    private void openReminderSettings() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
            return;
        }
        if (Build.VERSION.SDK_INT >= 34
                && !getSystemService(android.app.NotificationManager.class).canUseFullScreenIntent()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !getSystemService(android.app.AlarmManager.class).canScheduleExactAlarms()) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_EXACT_ALARMS);
            return;
        }
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            updateReminderStatus();
        }
    }

    private LinearLayout card() {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(background(WHITE, 21));
        card.setElevation(dp(2));
        return card;
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

    private String formatTime(String time) {
        String[] parts = time.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        return String.format(Locale.getDefault(), "%d:%02d %s",
                hour % 12 == 0 ? 12 : hour % 12, minute, hour < 12 ? "AM" : "PM");
    }

    private String formatSipTime(long timestamp) {
        return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(timestamp));
    }

    private String formatAmount(int amount) {
        return String.format(Locale.getDefault(), "%,d ml", amount);
    }

    private final class StarRewardFlightView extends View {
        private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int count;
        private PointF start = new PointF();
        private PointF end = new PointF();
        private float progress;
        private int visibleStars;

        StarRewardFlightView(Activity context, int count) {
            super(context);
            this.count = Math.min(50, count);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void play(PointF start, PointF end, int previousStars, int updatedStars,
                  Runnable onFinished) {
            this.start = start;
            this.end = end;
            visibleStars = previousStars;
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1350);
            animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
            animator.addUpdateListener(animation -> {
                progress = (float) animation.getAnimatedValue();
                int arrived = 0;
                for (int i = 0; i < count; i++) {
                    float arrival = 0.4f + 0.6f * (i + 1f) / count;
                    if (progress >= arrival) {
                        arrived++;
                    }
                }
                int nextVisibleStars = Math.min(updatedStars, previousStars + arrived);
                if (starsScore != null && nextVisibleStars != visibleStars) {
                    visibleStars = nextVisibleStars;
                    starsScore.setText(visibleStars + " stars");
                }
                invalidate();
            });
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (starsScore != null) {
                        starsScore.setText(updatedStars + " stars");
                    }
                    onFinished.run();
                }
            });
            animator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            for (int i = 0; i < count; i++) {
                float launch = 0.08f * i / Math.max(1f, count - 1f);
                float arrival = 0.4f + 0.6f * (i + 1f) / count;
                float travel = Math.max(0f,
                        Math.min(1f, (progress - launch) / (arrival - launch)));
                if (travel <= 0f) {
                    continue;
                }
                float fan = (i - (count - 1) / 2f) * dp(3);
                float x = start.x + (end.x - start.x) * travel
                        + (float) Math.sin(travel * Math.PI) * fan;
                float y = start.y + (end.y - start.y) * travel
                        - (float) Math.sin(Math.PI * travel) * dp(72 + i % 4 * 8);
                float size = dp(6) * (1.25f - travel * 0.3f);
                canvas.save();
                canvas.translate(x, y);
                canvas.rotate(travel * 210f + i * 24f);
                starPaint.setStyle(Paint.Style.FILL);
                starPaint.setColor(i % 3 == 0 ? 0xFFFFD76D : 0xFFFFF0A6);
                starPaint.setShadowLayer(dp(4), 0f, 0f, 0xFFFFC65A);
                Path star = new Path();
                for (int point = 0; point < 10; point++) {
                    double angle = -Math.PI / 2 + point * Math.PI / 5;
                    float radius = size * (point % 2 == 0 ? 1f : 0.43f);
                    float px = (float) Math.cos(angle) * radius;
                    float py = (float) Math.sin(angle) * radius;
                    if (point == 0) {
                        star.moveTo(px, py);
                    } else {
                        star.lineTo(px, py);
                    }
                }
                star.close();
                canvas.drawPath(star, starPaint);
                starPaint.clearShadowLayer();
                canvas.restore();
            }
        }
    }

    private final class ProgressRing extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private final Typeface percentTypeface =
                Typeface.create("sans-serif-medium", Typeface.NORMAL);
        private final boolean goalComplete;
        private Shader progressShader;
        private String percentLabel = "0%";
        private float progress;

        ProgressRing(Activity context, boolean goalComplete) {
            super(context);
            this.goalComplete = goalComplete;
        }

        void setProgress(float value) {
            progress = Math.min(1f, Math.max(0f, value));
            percentLabel = Math.round(progress * 100) + "%";
            invalidate();
        }

        void animateProgress(float from, float to) {
            float start = Math.min(1f, Math.max(0f, from));
            float end = Math.min(1f, Math.max(0f, to));
            setProgress(start);
            ValueAnimator animator = ValueAnimator.ofFloat(start, end);
            animator.setDuration(850);
            animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
            animator.addUpdateListener(animation -> setProgress((float) animation.getAnimatedValue()));
            animator.start();
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            progressShader = new LinearGradient(0, 0, width, height,
                    goalComplete ? 0xFFFFF4BD : 0xFFFFD7B5,
                    goalComplete ? 0xFFFFB52E : 0xFFF4AFC6, Shader.TileMode.CLAMP);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float stroke = dp(8);
            float inset = stroke / 2;
            bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(goalComplete ? 0x66FFE5A3 : 0x55FFFFFF);
            canvas.drawArc(bounds, -90, 360, false, paint);
            paint.setShader(progressShader);
            canvas.drawArc(bounds, -90, progress * 360, false, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(WHITE);
            paint.setTypeface(percentTypeface);
            paint.setTextSize(dp(17));
            canvas.drawText(percentLabel, getWidth() / 2f,
                    getHeight() / 2f + dp(6), paint);
        }
    }
}
