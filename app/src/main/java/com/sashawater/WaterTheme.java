package com.sashawater;

import android.app.Activity;
import android.graphics.Color;

final class WaterTheme {
    private WaterTheme() {}

    static boolean isDark(android.content.Context context) {
        return HydrationPrefs.isDarkTheme(context);
    }

    static void applyMain(Activity activity) {
        activity.setTheme(isDark(activity) ? R.style.AppThemeDark : R.style.AppTheme);
    }

    static void applyReminder(Activity activity) {
        activity.setTheme(isDark(activity) ? R.style.ReminderThemeDark : R.style.ReminderTheme);
    }

    static int textColor(android.content.Context context, int color) {
        if (!isDark(context)) {
            return color;
        }
        if (color == 0xFF352938) {
            return 0xFFF7EEF8;
        }
        if (color == 0xFF8E8290) {
            return 0xFFC3B6CA;
        }
        if (color == 0xFFBA6686 || color == 0xFFC96F91) {
            return 0xFFF0A0C0;
        }
        if (color == 0xFFFFFFFF) {
            return 0xFFFCF5FF;
        }
        if (color == 0xFFFFF8F5) {
            return 0xFF19151F;
        }
        if (color == 0xFFFFF0F3 || color == 0xFFFFF0F4
                || color == 0xFFFFF1F4 || color == 0xFFFFF2F5
                || color == 0xFFFFEAF0) {
            return 0xFF382936;
        }
        if (color == 0xFFF1EAEE || color == 0xFFF7F1F4) {
            return 0xFF302B36;
        }
        if (luminance(color) > 0.82f) {
            return 0xFFE9DCEB;
        }
        return color;
    }

    static int surfaceColor(android.content.Context context, int color) {
        if (!isDark(context)) {
            return color;
        }
        if (color == 0xFFFFF8F5) {
            return 0xFF19151F;
        }
        if (color == 0xFF493047) {
            return 0xFF35263B;
        }
        if (color == 0xFF352938) {
            return 0xFF29232F;
        }
        if (color == 0xFFFFFFFF) {
            return 0xFF302837;
        }
        if (color == 0xFFFFF0F3 || color == 0xFFFFF0F4
                || color == 0xFFFFF1F4 || color == 0xFFFFF2F5
                || color == 0xFFFFEAF0) {
            return 0xFF392B3A;
        }
        float light = luminance(color);
        if (light > 0.92f) {
            return 0xFF26212D;
        }
        if (light > 0.76f) {
            return 0xFF312736;
        }
        return color;
    }

    private static float luminance(int color) {
        return (0.2126f * Color.red(color) + 0.7152f * Color.green(color)
                + 0.0722f * Color.blue(color)) / 255f;
    }
}
