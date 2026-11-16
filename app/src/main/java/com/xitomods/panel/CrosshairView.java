package com.xitomods.panel;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/**
 * Vista personalizada en Canvas que dibuja los 8 estilos de mira neón sobre la pantalla
 * (idéntico al CrosshairView de A2 en el APK original).
 */
public class CrosshairView extends View {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int style = 0;
    private int color = Color.parseColor("#00F5D4");
    private int sizeDp = 42;
    private int alphaPercent = 100;

    public CrosshairView(Context context) {
        super(context);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        updatePaints();
    }

    public void setConfig(int style, int color, int sizeDp, int alphaPercent) {
        this.style = style;
        this.color = color;
        this.sizeDp = sizeDp;
        this.alphaPercent = alphaPercent;
        updatePaints();
        invalidate();
    }

    private void updatePaints() {
        int alpha = Math.max(25, Math.min(255, (alphaPercent * 255) / 100));
        int c = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        linePaint.setColor(c);
        linePaint.setStrokeWidth(dp(2.2f));
        linePaint.setStyle(Paint.Style.STROKE);

        glowPaint.setColor(c);
        glowPaint.setStrokeWidth(dp(5.5f));
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setMaskFilter(new BlurMaskFilter(dp(4f), BlurMaskFilter.Blur.NORMAL));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = dp(sizeDp) * 0.35f;

        switch (style) {
            case 0: // Punto
                linePaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, dp(4f), glowPaint);
                canvas.drawCircle(cx, cy, dp(3.5f), linePaint);
                linePaint.setStyle(Paint.Style.STROKE);
                break;
            case 1: // Cruz
                drawCross(canvas, cx, cy, r, dp(4f));
                break;
            case 2: // Círculo
                canvas.drawCircle(cx, cy, r, glowPaint);
                canvas.drawCircle(cx, cy, r, linePaint);
                break;
            case 3: // Punto y anillo
                canvas.drawCircle(cx, cy, r, glowPaint);
                canvas.drawCircle(cx, cy, r, linePaint);
                linePaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, dp(3f), linePaint);
                linePaint.setStyle(Paint.Style.STROKE);
                break;
            case 4: // Precisión
                canvas.drawCircle(cx, cy, r * 0.75f, linePaint);
                drawCross(canvas, cx, cy, r * 1.15f, r * 0.35f);
                break;
            case 5: // Dinámica
                drawCross(canvas, cx, cy, r, r * 0.45f);
                canvas.drawCircle(cx, cy, dp(2.5f), linePaint);
                break;
            case 6: // Mínima (Esquinas)
                drawCorners(canvas, cx, cy, r * 0.8f);
                break;
            default: // Personalizada XITO Neón
                canvas.drawCircle(cx, cy, r, glowPaint);
                canvas.drawCircle(cx, cy, r, linePaint);
                drawCross(canvas, cx, cy, r * 1.25f, r * 0.5f);
                linePaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, dp(2.5f), linePaint);
                linePaint.setStyle(Paint.Style.STROKE);
                break;
        }
    }

    private void drawCross(Canvas c, float cx, float cy, float len, float gap) {
        c.drawLine(cx - len, cy, cx - gap, cy, glowPaint);
        c.drawLine(cx + gap, cy, cx + len, cy, glowPaint);
        c.drawLine(cx, cy - len, cx, cy - gap, glowPaint);
        c.drawLine(cx, cy + gap, cx, cy + len, glowPaint);

        c.drawLine(cx - len, cy, cx - gap, cy, linePaint);
        c.drawLine(cx + gap, cy, cx + len, cy, linePaint);
        c.drawLine(cx, cy - len, cx, cy - gap, linePaint);
        c.drawLine(cx, cy + gap, cx, cy + len, linePaint);
    }

    private void drawCorners(Canvas c, float cx, float cy, float s) {
        float seg = s * 0.45f;
        // Top-Left
        c.drawLine(cx - s, cy - s, cx - s + seg, cy - s, linePaint);
        c.drawLine(cx - s, cy - s, cx - s, cy - s + seg, linePaint);
        // Top-Right
        c.drawLine(cx + s, cy - s, cx + s - seg, cy - s, linePaint);
        c.drawLine(cx + s, cy - s, cx + s, cy - s + seg, linePaint);
        // Bottom-Left
        c.drawLine(cx - s, cy + s, cx - s + seg, cy + s, linePaint);
        c.drawLine(cx - s, cy + s, cx - s, cy + s - seg, linePaint);
        // Bottom-Right
        c.drawLine(cx + s, cy + s, cx + s - seg, cy + s, linePaint);
        c.drawLine(cx + s, cy + s, cx + s, cy + s - seg, linePaint);
    }
}
