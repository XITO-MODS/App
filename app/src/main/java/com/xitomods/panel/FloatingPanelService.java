package com.xitomods.panel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Servicio de superposición flotante XITO MODS (equivalente a A2 del APK original).
 * Incluye:
 * - Burbuja flotante arrastrable (BubbleTouch).
 * - Panel flotante neón arrastrable desde el título (MenuDragTouch) y redimensionable desde la esquina inferior derecha (MenuResizeTouch).
 * - 4 Pestañas interactivas: MIRA, VISUALES, AJUSTES, INFO.
 * - Superposición de Mira Personalizada en tiempo real (CrosshairView).
 */
public class FloatingPanelService extends Service {
    public static final String ACTION_REFRESH = "com.xitomods.panel.REFRESH_OVERLAY";
    public static final String ACTION_STOP = "com.xitomods.panel.STOP_OVERLAY";
    public static final String UI_PREFS = "xito_mods_ui_prefs";

    public static boolean isRunning = false;

    private WindowManager wm;
    private ImageView bubbleView;
    private WindowManager.LayoutParams bubbleLp;

    private View menuView;
    private WindowManager.LayoutParams menuLp;
    private LinearLayout contentContainer;

    private CrosshairView crosshairView;
    private WindowManager.LayoutParams crossLp;

    private int currentTab = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView infoCountdownText;

    private final Runnable countdownTicker = new Runnable() {
        @Override
        public void run() {
            if (infoCountdownText != null) {
                long exp = KeyAuthManager.getExpirationMs(FloatingPanelService.this);
                infoCountdownText.setText("Validez de la clave: " + KeyAuthManager.formatRemainingTime(exp));
            }
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        setupForegroundNotification();
        createBubbleOverlay();
        createMenuOverlay();
        createCrosshairOverlay();
        syncOverlayFromPrefs();
        handler.post(countdownTicker);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        syncOverlayFromPrefs();
        return START_STICKY;
    }

    private int getOverlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void setupForegroundNotification() {
        String channelId = "xito_mods_overlay_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "XITO MODS Panel Flotante", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
            Notification notification = new Notification.Builder(this, channelId)
                    .setContentTitle("XITO MODS NEÓN ACTIVO")
                    .setContentText("Toca la burbuja flotante para abrir el panel.")
                    .setSmallIcon(R.drawable.ic_xito_bubble)
                    .build();
            startForeground(1001, notification);
        }
    }

    private void createBubbleOverlay() {
        bubbleView = new ImageView(this);
        bubbleView.setImageResource(R.drawable.ic_xito_bubble);
        int size = dp(58);
        bubbleLp = new WindowManager.LayoutParams(
                size, size, getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.START;
        bubbleLp.x = dp(24);
        bubbleLp.y = dp(160);

        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float touchX, touchY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = bubbleLp.x;
                        startY = bubbleLp.y;
                        touchX = e.getRawX();
                        touchY = e.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = Math.round(e.getRawX() - touchX);
                        int dy = Math.round(e.getRawY() - touchY);
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true;
                        bubbleLp.x = startX + dx;
                        bubbleLp.y = startY + dy;
                        wm.updateViewLayout(bubbleView, bubbleLp);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) openMenu();
                        return true;
                }
                return false;
            }
        });

        wm.addView(bubbleView, bubbleLp);
    }

    private void createMenuOverlay() {
        LayoutInflater inflater = LayoutInflater.from(this);
        menuView = inflater.inflate(R.layout.xito_floating_panel, null);
        contentContainer = menuView.findViewById(R.id.floating_content_container);

        int w = dp(310);
        int h = dp(390);
        menuLp = new WindowManager.LayoutParams(
                w, h, getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        menuLp.gravity = Gravity.TOP | Gravity.START;
        menuLp.x = dp(40);
        menuLp.y = dp(140);
        menuView.setVisibility(View.GONE);

        // Arrastrar desde el título (MenuDragTouch)
        View titleDrag = menuView.findViewById(R.id.floating_title_drag);
        titleDrag.setOnTouchListener(new View.OnTouchListener() {
            private int sx, sy;
            private float rx, ry;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = menuLp.x; sy = menuLp.y;
                        rx = e.getRawX(); ry = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        menuLp.x = sx + Math.round(e.getRawX() - rx);
                        menuLp.y = sy + Math.round(e.getRawY() - ry);
                        wm.updateViewLayout(menuView, menuLp);
                        return true;
                }
                return false;
            }
        });

        // Redimensionar desde la esquina inferior derecha (MenuResizeTouch)
        View resizeHandle = menuView.findViewById(R.id.floating_resize_handle);
        resizeHandle.setOnTouchListener(new View.OnTouchListener() {
            private int sw, sh;
            private float rx, ry;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sw = menuLp.width; sh = menuLp.height;
                        rx = e.getRawX(); ry = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        menuLp.width = Math.max(dp(250), Math.min(dp(440), sw + Math.round(e.getRawX() - rx)));
                        menuLp.height = Math.max(dp(280), Math.min(dp(600), sh + Math.round(e.getRawY() - ry)));
                        wm.updateViewLayout(menuView, menuLp);
                        return true;
                }
                return false;
            }
        });

        // Botón minimizar (−)
        menuView.findViewById(R.id.floating_btn_minimize).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeMenu();
            }
        });

        // Eventos de las 4 pestañas
        menuView.findViewById(R.id.tab_container_mira).setOnClickListener(v -> showTab(0));
        menuView.findViewById(R.id.tab_container_visuales).setOnClickListener(v -> showTab(1));
        menuView.findViewById(R.id.tab_container_ajustes).setOnClickListener(v -> showTab(2));
        menuView.findViewById(R.id.tab_container_info).setOnClickListener(v -> showTab(3));

        wm.addView(menuView, menuLp);
        showTab(0);
    }

    private void createCrosshairOverlay() {
        crosshairView = new CrosshairView(this);
        int s = dp(120);
        crossLp = new WindowManager.LayoutParams(
                s, s, getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        crossLp.gravity = Gravity.CENTER;
        wm.addView(crosshairView, crossLp);
    }

    private void openMenu() {
        bubbleView.setVisibility(View.GONE);
        menuView.setVisibility(View.VISIBLE);
    }

    private void closeMenu() {
        menuView.setVisibility(View.GONE);
        bubbleView.setVisibility(View.VISIBLE);
    }

    private void showTab(int tabIdx) {
        currentTab = tabIdx;
        int[] containers = {
            R.id.tab_container_mira, R.id.tab_container_visuales,
            R.id.tab_container_ajustes, R.id.tab_container_info
        };
        int[] labels = {
            R.id.tab_text_mira, R.id.tab_text_visuales,
            R.id.tab_text_ajustes, R.id.tab_text_info
        };
        for (int i = 0; i < 4; i++) {
            View c = menuView.findViewById(containers[i]);
            TextView t = menuView.findViewById(labels[i]);
            c.setBackgroundResource(i == tabIdx ? R.drawable.bg_neon_tab_selected : R.drawable.bg_neon_tab);
            t.setTextColor(Color.parseColor(i == tabIdx ? "#00F5D4" : "#9BA6C4"));
        }

        contentContainer.removeAllViews();
        SharedPreferences prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);

        if (tabIdx == 0) {
            addSectionHeader("CONFIGURACIÓN DE MIRA NEÓN");
            addToggleOption("Mostrar Mira en Pantalla", " Superposición central activa", "cross_enabled", true);
            addToggleOption("Estabilizador Visual HUD", "Suavizado de retícula neón", "hud_smooth", true);
            addSliderOption("Tamaño de la Mira", "dp", "cross_size", 16, 80, 42);
            addSliderOption("Opacidad de la Mira", "%", "cross_alpha", 25, 100, 100);
        } else if (tabIdx == 1) {
            addSectionHeader("EFECTOS VISUALES Y HUD");
            addToggleOption("Borde Holograma RGB", "Resaltado neón en tarjetas", "vis_rgb_border", true);
            addToggleOption("Contador de FPS en Pantalla", "Muestra rendimiento en tiempo real", "vis_fps", false);
            addToggleOption("Modo Alto Contraste Cian", "Intensifica el brillo del panel", "vis_high_contrast", true);
            addSliderOption("Intensidad de Brillo Neón", "%", "vis_glow", 10, 100, 85);
        } else if (tabIdx == 2) {
            addSectionHeader("PERSONALIZACIÓN DEL PANEL");
            addSliderOption("Tamaño de la Burbuja", "%", "bubble_size", 70, 160, 100);
            addSliderOption("Transparencia del Panel", "%", "menu_alpha", 50, 100, 94);
            addToggleOption("Bloquear Posición", "Evita movimientos accidentales", "lock_pos", false);
        } else {
            addSectionHeader("INFORMACIÓN DE SESIÓN");
            LinearLayout card = createNeonCard();
            TextView brand = new TextView(this);
            brand.setText("XITO MODS • EDICIÓN NEÓN V4");
            brand.setTextColor(Color.parseColor("#00F5D4"));
            brand.setTextSize(14f);
            brand.setPadding(0, 0, 0, dp(6));
            card.addView(brand);

            TextView keyTv = new TextView(this);
            keyTv.setText("Clave activa: " + KeyAuthManager.getSavedKey(this));
            keyTv.setTextColor(Color.parseColor("#F4F7FF"));
            keyTv.setTextSize(12f);
            card.addView(keyTv);

            infoCountdownText = new TextView(this);
            long exp = KeyAuthManager.getExpirationMs(this);
            infoCountdownText.setText("Validez de la clave: " + KeyAuthManager.formatRemainingTime(exp));
            infoCountdownText.setTextColor(Color.parseColor("#BC13FE"));
            infoCountdownText.setTextSize(12f);
            infoCountdownText.setPadding(0, dp(6), 0, dp(10));
            card.addView(infoCountdownText);

            TextView closeBtn = new TextView(this);
            closeBtn.setText("CERRAR PANEL FLOTANTE");
            closeBtn.setGravity(Gravity.CENTER);
            closeBtn.setTextColor(Color.parseColor("#080A10"));
            closeBtn.setTextSize(12f);
            closeBtn.setBackgroundResource(R.drawable.btn_neon_primary);
            closeBtn.setPadding(dp(12), dp(10), dp(12), dp(10));
            closeBtn.setOnClickListener(v -> stopSelf());
            card.addView(closeBtn);

            contentContainer.addView(card);
        }
    }

    private void addSectionHeader(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(Color.parseColor("#00F5D4"));
        tv.setTextSize(11f);
        tv.setPadding(dp(4), dp(6), dp(4), dp(6));
        contentContainer.addView(tv);
    }

    private LinearLayout createNeonCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.neon_card);
        int p = dp(12);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        return card;
    }

    private void addToggleOption(String title, String subtitle, final String prefKey, boolean defVal) {
        final SharedPreferences prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
        boolean current = prefs.getBoolean(prefKey, defVal);

        LinearLayout card = createNeonCard();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextColor(Color.parseColor("#F4F7FF"));
        t1.setTextSize(13f);
        texts.addView(t1);

        TextView t2 = new TextView(this);
        t2.setText(subtitle);
        t2.setTextColor(Color.parseColor("#9BA6C4"));
        t2.setTextSize(11f);
        texts.addView(t2);

        final TextView badge = new TextView(this);
        styleToggleBadge(badge, current);
        badge.setOnClickListener(v -> {
            boolean next = !prefs.getBoolean(prefKey, defVal);
            prefs.edit().putBoolean(prefKey, next).apply();
            styleToggleBadge(badge, next);
            syncOverlayFromPrefs();
        });

        row.addView(texts);
        row.addView(badge);
        card.addView(row);
        contentContainer.addView(card);
    }

    private void styleToggleBadge(TextView badge, boolean active) {
        badge.setText(active ? "ACTIVO" : "OFF");
        badge.setTextSize(11f);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(14), dp(6), dp(14), dp(6));
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(999));
        if (active) {
            gd.setColor(Color.parseColor("#2600F5D4"));
            gd.setStroke(dp(1.2f), Color.parseColor("#00F5D4"));
            badge.setTextColor(Color.parseColor("#00F5D4"));
        } else {
            gd.setColor(Color.parseColor("#1A9BA6C4"));
            gd.setStroke(dp(1f), Color.parseColor("#409BA6C4"));
            badge.setTextColor(Color.parseColor("#9BA6C4"));
        }
        badge.setBackground(gd);
    }

    private void addSliderOption(String title, final String unit, final String prefKey, final int min, int max, int defVal) {
        final SharedPreferences prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
        int current = prefs.getInt(prefKey, defVal);

        LinearLayout card = createNeonCard();
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextColor(Color.parseColor("#F4F7FF"));
        t1.setTextSize(12f);
        t1.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final TextView valTv = new TextView(this);
        valTv.setText(current + unit);
        valTv.setTextColor(Color.parseColor("#00F5D4"));
        valTv.setTextSize(12f);

        header.addView(t1);
        header.addView(valTv);
        card.addView(header);

        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, current - min));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = min + progress;
                valTv.setText(v + unit);
                prefs.edit().putInt(prefKey, v).apply();
                syncOverlayFromPrefs();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        card.addView(sb);
        contentContainer.addView(card);
    }

    public void syncOverlayFromPrefs() {
        SharedPreferences prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
        boolean crossOn = prefs.getBoolean("cross_enabled", true);
        int style = prefs.getInt("cross_style", 0);
        int color = prefs.getInt("cross_color", Color.parseColor("#00F5D4"));
        int size = prefs.getInt("cross_size", 42);
        int alpha = prefs.getInt("cross_alpha", 100);

        if (crosshairView != null) {
            crosshairView.setVisibility(crossOn ? View.VISIBLE : View.GONE);
            crosshairView.setConfig(style, color, size, alpha);
        }

        int bubbleScale = prefs.getInt("bubble_size", 100);
        if (bubbleView != null && bubbleLp != null) {
            int px = dp(56 * (bubbleScale / 100f));
            bubbleLp.width = px;
            bubbleLp.height = px;
            wm.updateViewLayout(bubbleView, bubbleLp);
        }

        int menuAlpha = prefs.getInt("menu_alpha", 94);
        if (menuView != null) {
            menuView.setAlpha(Math.max(0.4f, menuAlpha / 100f));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        handler.removeCallbacks(countdownTicker);
        if (wm != null) {
            if (bubbleView != null) wm.removeView(bubbleView);
            if (menuView != null) wm.removeView(menuView);
            if (crosshairView != null) wm.removeView(crosshairView);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
