package com.sashawater;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

final class WaterIconView extends View {
    static final String FLOWER = "flower";
    static final String DROP = "drop";
    static final String STAR = "star";
    static final String STREAK = "streak";
    static final String CLOCK = "clock";
    static final String HOME = "home";
    static final String HISTORY = "history";
    static final String SETTINGS = "settings";
    static final String SOUND = "sound";
    static final String ALARM = "alarm";

    private final String shape;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int tint;
    private boolean glowing;
    private boolean flameActive;
    private float glowStrength = 1f;
    private float flamePhase;
    private ValueAnimator flameAnimator;

    WaterIconView(Context context, String shape, int tint) {
        super(context);
        this.shape = shape;
        this.tint = tint;
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(tint);
    }

    void setTint(int color) {
        tint = color;
        paint.setColor(color);
        invalidate();
    }

    void setGlowing(boolean enabled) {
        glowing = enabled;
        setLayerType(enabled && !shape.equals(STREAK)
                ? View.LAYER_TYPE_SOFTWARE : View.LAYER_TYPE_NONE, null);
        if (shape.equals(STREAK)) {
            if (enabled && isAttachedToWindow()) {
                startFlameAnimation();
            } else {
                stopFlameAnimation();
            }
        }
        invalidate();
    }

    void setFlameActive(boolean active) {
        flameActive = active;
        invalidate();
    }

    void setGlowStrength(float strength) {
        glowStrength = Math.max(0f, Math.min(5f, strength));
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (shape.equals(STREAK) && glowing) {
            startFlameAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopFlameAnimation();
        super.onDetachedFromWindow();
    }

    private void startFlameAnimation() {
        if (flameAnimator != null || !glowing) {
            return;
        }
        flameAnimator = ValueAnimator.ofFloat(0f, 1f);
        flameAnimator.setDuration(920);
        flameAnimator.setRepeatCount(ValueAnimator.INFINITE);
        flameAnimator.setRepeatMode(ValueAnimator.REVERSE);
        flameAnimator.addUpdateListener(animation -> {
            flamePhase = (float) animation.getAnimatedValue();
            invalidate();
        });
        flameAnimator.start();
    }

    private void stopFlameAnimation() {
        if (flameAnimator != null) {
            flameAnimator.cancel();
            flameAnimator = null;
        }
        flamePhase = 0f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) {
            return;
        }
        canvas.save();
        canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
        canvas.scale(size / 100f, size / 100f);
        paint.setColor(tint);
        paint.setStrokeWidth(6f);
        paint.setStyle(Paint.Style.STROKE);
        if (glowing && !shape.equals(STREAK)) {
            paint.setShadowLayer(10f, 0f, 0f, tint);
        }
        switch (shape) {
            case FLOWER:
                drawFlower(canvas);
                break;
            case DROP:
                drawDrop(canvas);
                break;
            case STAR:
                drawStar(canvas);
                break;
            case STREAK:
                drawStreak(canvas, flameActive);
                break;
            case CLOCK:
                drawClock(canvas);
                break;
            case HOME:
                drawHome(canvas);
                break;
            case HISTORY:
                drawHistory(canvas);
                break;
            case SETTINGS:
                drawSettings(canvas);
                break;
            case SOUND:
                drawSound(canvas);
                break;
            case ALARM:
                drawAlarm(canvas);
                break;
            default:
                drawDrop(canvas);
        }
        paint.clearShadowLayer();
        canvas.restore();
    }

    private void drawFlower(Canvas canvas) {
        canvas.save();
        canvas.translate(50, 50);
        paint.setStyle(Paint.Style.FILL);
        for (int layer = 0; layer < 2; layer++) {
            int petals = layer == 0 ? 8 : 7;
            float reach = layer == 0 ? 34f : 25f;
            float width = layer == 0 ? 19f : 15f;
            float angleOffset = layer == 0 ? 0f : 25.7f;
            int petalColor = layer == 0 ? tint : blend(tint, 0xFFFFFFFF, 0.2f);
            paint.setShader(new android.graphics.LinearGradient(0, -reach - width,
                    0, -reach + width, new int[]{blend(petalColor, 0xFFFFFFFF, 0.52f),
                    petalColor, blend(petalColor, 0xFF000000, 0.12f)},
                    null, Shader.TileMode.CLAMP));
            for (int i = 0; i < petals; i++) {
                canvas.save();
                canvas.rotate(angleOffset + i * 360f / petals);
                Path petal = new Path();
                petal.moveTo(0, -10);
                petal.cubicTo(-width * 0.75f, -reach * 0.48f,
                        -width * 0.62f, -reach - width * 0.35f, 0, -reach - width * 0.38f);
                petal.cubicTo(width * 0.62f, -reach - width * 0.35f,
                        width * 0.75f, -reach * 0.48f, 0, -10);
                petal.close();
                if (glowing) {
                    paint.setShadowLayer(8f, 0f, 1f, tint);
                }
                canvas.drawPath(petal, paint);
                paint.clearShadowLayer();
                canvas.restore();
            }
            paint.setShader(null);
        }
        paint.setShader(new android.graphics.RadialGradient(-3, -4, 16,
                new int[]{0xFFFFF8D7, 0xFFFFC85E, 0xFFDF7B43},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(0, 0, 13, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0xFFFFF0B8);
        for (int i = 0; i < 8; i++) {
            canvas.save();
            canvas.rotate(i * 45f);
            canvas.drawLine(0, -4, 0, -10, paint);
            canvas.restore();
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFF5D1);
        canvas.drawCircle(-4, -5, 2.4f, paint);
        canvas.restore();
    }

    private void drawDrop(Canvas canvas) {
        Path path = new Path();
        path.moveTo(50, 10);
        path.cubicTo(42, 25, 23, 47, 23, 63);
        path.cubicTo(23, 80, 35, 91, 50, 91);
        path.cubicTo(65, 91, 77, 80, 77, 63);
        path.cubicTo(77, 47, 58, 25, 50, 10);
        canvas.drawPath(path, paint);
        canvas.drawArc(37, 59, 63, 78, 10, 145, false, paint);
    }

    private void drawStar(Canvas canvas) {
        Path path = new Path();
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            float radius = i % 2 == 0 ? 42 : 19;
            float x = 50 + (float) Math.cos(angle) * radius;
            float y = 50 + (float) Math.sin(angle) * radius;
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new android.graphics.LinearGradient(28, 16, 73, 91,
                new int[]{blend(tint, 0xFFFFFFFF, 0.48f), tint,
                        blend(tint, 0xFFB95A20, 0.18f)},
                null, Shader.TileMode.CLAMP));
        if (glowing) {
            paint.setShadowLayer(12f, 0f, 2f, tint);
        }
        canvas.drawPath(path, paint);
        paint.clearShadowLayer();
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(blend(tint, 0xFF8A461D, 0.3f));
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFF7D4);
        canvas.drawCircle(47, 31, 3.2f, paint);
        canvas.drawCircle(39, 39, 1.7f, paint);
    }

    private void drawStreak(Canvas canvas, boolean active) {
        float pulse = glowing ? (float) Math.sin(flamePhase * Math.PI) : 0f;
        float stage = Math.max(1f, Math.min(5f, glowStrength));
        float stageIntensity = 1f + stage * 0.2f;
        if (active && glowing) {
            drawFlameHalo(canvas, stage, pulse);
        }
        canvas.save();
        canvas.translate(50, 55);
        canvas.scale(1f + pulse * 0.045f * stageIntensity,
                1f - pulse * 0.035f * stageIntensity);
        canvas.translate(-50, -55);

        Path outer = new Path();
        outer.moveTo(51, 7);
        outer.cubicTo(55, 28, 84, 39, 82, 64);
        outer.cubicTo(80, 84, 66, 96, 49, 96);
        outer.cubicTo(29, 96, 16, 81, 17, 62);
        outer.cubicTo(18, 45, 32, 31, 40, 19);
        outer.cubicTo(42, 34, 48, 42, 51, 7);
        outer.close();

        paint.setStyle(Paint.Style.FILL);
        if (active) {
            int[][] palettes = {
                    {0xFFB51255, 0xFFFF286F, 0xFFFF704F, 0xFFFFC34E, 0xFFFFF0AE},
                    {0xFF5716C5, 0xFF9B29F4, 0xFFEF39BC, 0xFF5BD7FF, 0xFFF0BFFF},
                    {0xFF1738C9, 0xFF176BFF, 0xFF13C8F2, 0xFF8A59FF, 0xFFFF83CD},
                    {0xFF00634F, 0xFF00A981, 0xFF00E2B0, 0xFF9BF34C, 0xFF75CFFF},
                    {0xFFB52D21, 0xFFFF7225, 0xFFFFC12E, 0xFFFF62A5, 0xFFC24DFF}
            };
            int paletteIndex = Math.min(palettes.length - 1, Math.round(stage) - 1);
            int[] flameColors = palettes[paletteIndex];
            paint.setShader(new android.graphics.LinearGradient(32, 90, 70, 12,
                    flameColors, new float[]{0f, 0.23f, 0.49f, 0.75f, 1f},
                    Shader.TileMode.CLAMP));
        } else {
            paint.setShader(null);
            paint.setColor(0xFFB9B2BD);
        }
        canvas.drawPath(outer, paint);
        paint.setShader(null);

        Path inner = new Path();
        inner.moveTo(52, 47);
        inner.cubicTo(56, 57, 70, 64, 68, 76);
        inner.cubicTo(66, 87, 58, 91, 49, 91);
        inner.cubicTo(39, 91, 31, 84, 32, 74);
        inner.cubicTo(33, 65, 43, 57, 47, 48);
        inner.cubicTo(48, 57, 51, 61, 52, 47);
        inner.close();
        if (active) {
            paint.setShader(new android.graphics.LinearGradient(41, 88, 59, 49,
                    new int[]{blend(tint, 0xFFFFFFFF, 0.76f), 0xFFFFFFFF},
                    null, Shader.TileMode.CLAMP));
            canvas.drawPath(inner, paint);
            paint.setShader(null);
        } else {
            paint.setColor(0xFFD8D3DC);
            canvas.drawPath(inner, paint);
        }
        canvas.restore();
    }

    private void drawFlameHalo(Canvas canvas, float stage, float pulse) {
        int[][] palettes = {
                {0xFFFF286F, 0xFFFFC34E},
                {0xFF9B29F4, 0xFF5BD7FF},
                {0xFF176BFF, 0xFFFF83CD},
                {0xFF00E2B0, 0xFF9BF34C},
                {0xFFFFC12E, 0xFFFF62A5}
        };
        int paletteIndex = Math.min(palettes.length - 1, Math.round(stage) - 1);
        int[] colors = palettes[paletteIndex];
        int strengthAlpha = Math.round(22f + stage * 4f + pulse * (4f + stage));
        int innerColor = Color.argb(Math.min(52, strengthAlpha), Color.red(colors[0]),
                Color.green(colors[0]), Color.blue(colors[0]));
        int middleColor = Color.argb(Math.min(28, strengthAlpha / 2),
                Color.red(colors[1]), Color.green(colors[1]), Color.blue(colors[1]));
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new android.graphics.RadialGradient(50, 54, 45,
                new int[]{innerColor, middleColor, Color.TRANSPARENT},
                new float[]{0f, 0.58f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(50, 54, 45, paint);
        paint.setShader(null);
    }

    private int blend(int color, int target, float amount) {
        int alpha = (int) (((color >>> 24) & 0xFF) * (1 - amount)
                + ((target >>> 24) & 0xFF) * amount);
        int red = (int) (((color >>> 16) & 0xFF) * (1 - amount)
                + ((target >>> 16) & 0xFF) * amount);
        int green = (int) (((color >>> 8) & 0xFF) * (1 - amount)
                + ((target >>> 8) & 0xFF) * amount);
        int blue = (int) ((color & 0xFF) * (1 - amount) + (target & 0xFF) * amount);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private void drawClock(Canvas canvas) {
        canvas.drawCircle(50, 50, 37, paint);
        canvas.drawLine(50, 27, 50, 51, paint);
        canvas.drawLine(50, 51, 68, 61, paint);
    }

    private void drawHome(Canvas canvas) {
        Path roof = new Path();
        roof.moveTo(15, 48);
        roof.lineTo(50, 20);
        roof.lineTo(85, 48);
        canvas.drawPath(roof, paint);
        canvas.drawRoundRect(23, 45, 77, 86, 4, 4, paint);
        canvas.drawRoundRect(43, 62, 57, 86, 3, 3, paint);
    }

    private void drawHistory(Canvas canvas) {
        canvas.drawArc(16, 17, 84, 85, 215, 305, false, paint);
        Path arrow = new Path();
        arrow.moveTo(15, 23);
        arrow.lineTo(15, 42);
        arrow.lineTo(34, 40);
        canvas.drawPath(arrow, paint);
        canvas.drawLine(50, 35, 50, 52, paint);
        canvas.drawLine(50, 52, 66, 61, paint);
    }

    private void drawSettings(Canvas canvas) {
        Path gear = new Path();
        for (int i = 0; i < 24; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 12;
            float radius = i % 3 == 0 ? 42 : 34;
            float x = 50 + (float) Math.cos(angle) * radius;
            float y = 50 + (float) Math.sin(angle) * radius;
            if (i == 0) {
                gear.moveTo(x, y);
            } else {
                gear.lineTo(x, y);
            }
        }
        gear.close();
        canvas.drawPath(gear, paint);
        canvas.drawCircle(50, 50, 13, paint);
    }

    private void drawSound(Canvas canvas) {
        Path speaker = new Path();
        speaker.moveTo(18, 41);
        speaker.lineTo(35, 41);
        speaker.lineTo(55, 25);
        speaker.lineTo(55, 75);
        speaker.lineTo(35, 59);
        speaker.lineTo(18, 59);
        speaker.close();
        canvas.drawPath(speaker, paint);
        canvas.drawArc(39, 30, 78, 70, -58, 116, false, paint);
        canvas.drawArc(37, 20, 92, 80, -57, 114, false, paint);
    }

    private void drawAlarm(Canvas canvas) {
        Path bell = new Path();
        bell.moveTo(25, 71);
        bell.cubicTo(34, 62, 30, 40, 39, 31);
        bell.cubicTo(47, 23, 58, 24, 66, 31);
        bell.cubicTo(75, 40, 71, 62, 80, 71);
        bell.close();
        canvas.drawPath(bell, paint);
        canvas.drawLine(19, 71, 86, 71, paint);
        canvas.drawArc(42, 68, 58, 84, 5, 170, false, paint);
        canvas.drawLine(29, 22, 20, 14, paint);
        canvas.drawLine(71, 22, 80, 14, paint);
    }
}
