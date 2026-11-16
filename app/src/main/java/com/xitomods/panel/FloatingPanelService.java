package com.xitomods.panel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
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
import android.widget.Toast;

/**
 * Servicio de superposición flotante XITO MODS con todas las funciones de HEX_ESP integradas.
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

    private TextView matchCodeBarView;
    private WindowManager.LayoutParams matchCodeBarLp;

    private int currentTab = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView infoCountdownText;
    private TextView infoProxyTelemetryText;

    private final int[] NEON_COLORS = {
        Color.parseColor("#00F5D4"),
        Color.parseColor("#BC13FE"),
        Color.parseColor("#FF2A85"),
        Color.parseColor("#00FF87"),
        Color.parseColor("#FFE600"),
        Color.parseColor("#00B4D8"),
        Color.parseColor("#FF5400"),
        Color.parseColor("#FF0055"),
        Color.parseColor("#FFFFFF")
    };

    private final Runnable countdownTicker = new Runnable() {
        @Override
        public void run() {
            if (infoCountdownText != null) {
                long exp = KeyAuthManager.getExpirationMs(FloatingPanelService.this);
                infoCountdownText.setText("Validez de la clave: " + KeyAuthManager.formatRemainingTime(exp));
            }
            if (infoProxyTelemetryText != null) {
                SharedPreferences prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
                boolean liveDev = prefs.getBoolean("hex_live_dev_mode", false);
                String status = XitoProxyVpnService.isConnected ? "CONECTADO" : "ACTIVO (LOCAL)";
                String telemetry = "Estado del Proxy: " + status
                        + "\nLatencia estimada: " + XitoProxyVpnService.currentSimulatedPingMs + " ms";
                if (liveDev) {
                    telemetry += "\nRáfagas procesadas: " + XitoProxyVpnService.processedBursts
                            + " | Búfer: " + XitoProxyVpnService.bufferedPacketsCount + " paq";
                }
                infoProxyTelemetryText.setText(telemetry);
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
        createMatchCodeBarOverlay();
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
                    .setContentTitle("XITO MODS × HEX ESP ACTIVO")
                    .setContentText("Toca la burbuja flotante para abrir el panel.")
                    .setSmallIcon(R.drawable.ic_xito_bubble)
                    .build();
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                } else {
                    startForeground(1001, notification);
                }
            } catch (Exception e) {
                try {
                    startForeground(1001, notification);
                } catch (Exception ignored) {
                }
            }
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
            private boolean longPressTriggered;
            private final Runnable longPressLockRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!moved) {
                        longPressTriggered = true;
                        SharedPreferences prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
                        boolean locked = !prefs.getBoolean("lock_pos", false);
                        prefs.edit().putBoolean("lock_pos", locked).apply();
                        Toast.makeText(FloatingPanelService.this,
                                locked ? "Posición bloqueada" : "Posición desbloqueada",
                                Toast.LENGTH_SHORT).show();
                        if (currentTab == 2 && menuView != null && menuView.getVisibility() == View.VISIBLE) {
                            showTab(2);
                        }
                    }
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                SharedPreferences prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
                boolean isLocked = prefs.getBoolean("lock_pos", false);
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = bubbleLp.x;
                        startY = bubbleLp.y;
                        touchX = e.getRawX();
                        touchY = e.getRawY();
                        moved = false;
                        longPressTriggered = false;
                        handler.postDelayed(longPressLockRunnable, 2000L);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = Math.round(e.getRawX() - touchX);
                        int dy = Math.round(e.getRawY() - touchY);
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) {
                            moved = true;
                            handler.removeCallbacks(longPressLockRunnable);
                        }
                        if (!isLocked && moved) {
                            bubbleLp.x = startX + dx;
                            bubbleLp.y = startY + dy;
                            wm.updateViewLayout(bubbleView, bubbleLp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(longPressLockRunnable);
                        if (!moved && !longPressTriggered) {
                            openMenu();
                        }
                        return true;
                }
                return false;
            }
        });

        wm.addView(bubbleView, bubbleLp);
    }

    private void createMatchCodeBarOverlay() {
        matchCodeBarView = new TextView(this);
        matchCodeBarView.setText("XITO • OCULTO");
        matchCodeBarView.setTextSize(8f);
        matchCodeBarView.setTypeface(Typeface.DEFAULT_BOLD);
        matchCodeBarView.setTextColor(Color.parseColor("#CC00F5D4"));
        matchCodeBarView.setGravity(Gravity.CENTER);
        styleMatchCodeBar(false);

        matchCodeBarLp = new WindowManager.LayoutParams(
                dp(220), dp(16), getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        matchCodeBarLp.gravity = Gravity.TOP | Gravity.START;
        matchCodeBarLp.x = dp(60);
        matchCodeBarLp.y = dp(18);
        matchCodeBarView.setVisibility(View.GONE);

        matchCodeBarView.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float touchX, touchY;
            private boolean moved;
            private final Runnable lockBarRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!moved) {
                        SharedPreferences prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
                        boolean nextLock = !prefs.getBoolean("match_bar_locked", false);
                        prefs.edit().putBoolean("match_bar_locked", nextLock).apply();
                        styleMatchCodeBar(nextLock);
                        Toast.makeText(FloatingPanelService.this,
                                nextLock ? "Posición bloqueada" : "Posición desbloqueada",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                SharedPreferences prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
                boolean locked = prefs.getBoolean("match_bar_locked", false);
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = matchCodeBarLp.x;
                        startY = matchCodeBarLp.y;
                        touchX = e.getRawX();
                        touchY = e.getRawY();
                        moved = false;
                        handler.postDelayed(lockBarRunnable, 2000L);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = Math.round(e.getRawX() - touchX);
                        int dy = Math.round(e.getRawY() - touchY);
                        if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) {
                            moved = true;
                            handler.removeCallbacks(lockBarRunnable);
                        }
                        if (!locked && moved) {
                            matchCodeBarLp.x = startX + dx;
                            matchCodeBarLp.y = startY + dy;
                            wm.updateViewLayout(matchCodeBarView, matchCodeBarLp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(lockBarRunnable);
                        return true;
                }
                return false;
            }
        });

        wm.addView(matchCodeBarView, matchCodeBarLp);
    }

    private void styleMatchCodeBar(boolean locked) {
        if (matchCodeBarView == null) return;
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor("#F5080A10"));
        gd.setCornerRadius(dp(4));
        gd.setStroke(dp(1.2f), Color.parseColor(locked ? "#FF2A85" : "#00F5D4"));
        matchCodeBarView.setBackground(gd);
        matchCodeBarView.setText(locked ? "XITO • BLOQUEADO" : "XITO • CÓDIGO OCULTO");
    }

    private void createMenuOverlay() {
        LayoutInflater inflater = LayoutInflater.from(this);
        menuView = inflater.inflate(R.layout.xito_floating_panel, null);
        contentContainer = menuView.findViewById(R.id.floating_content_container);

        int w = dp(320);
        int h = dp(420);
        menuLp = new WindowManager.LayoutParams(
                w, h, getOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        menuLp.gravity = Gravity.TOP | Gravity.START;
        menuLp.x = dp(36);
        menuLp.y = dp(120);
        menuView.setVisibility(View.GONE);

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
                        menuLp.width = Math.max(dp(260), Math.min(dp(460), sw + Math.round(e.getRawX() - rx)));
                        menuLp.height = Math.max(dp(290), Math.min(dp(640), sh + Math.round(e.getRawY() - ry)));
                        wm.updateViewLayout(menuView, menuLp);
                        return true;
                }
                return false;
            }
        });

        menuView.findViewById(R.id.floating_btn_minimize).setOnClickListener(v -> closeMenu());

        menuView.findViewById(R.id.tab_container_mira).setOnClickListener(v -> showTab(0));
        menuView.findViewById(R.id.tab_container_visuales).setOnClickListener(v -> showTab(1));
        menuView.findViewById(R.id.tab_container_ajustes).setOnClickListener(v -> showTab(2));
        menuView.findViewById(R.id.tab_container_info).setOnClickListener(v -> showTab(3));

        wm.addView(menuView, menuLp);
        showTab(0);
    }

    private void createCrosshairOverlay() {
        crosshairView = new CrosshairView(this);
        int s = dp(130);
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
        showTab(currentTab);
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
        final SharedPreferences prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);

        if (tabIdx == 0) {
            addSectionHeader("MIRA PERSONALIZADA NEÓN");
            addToggleOption("MOSTRAR MIRA", "Visible en el centro de la pantalla", "cross_enabled", true, null);

            LinearLayout styleCard = createNeonCard();
            TextView stTitle = new TextView(this);
            stTitle.setText("Estilo de la Mira (8 diseños)");
            stTitle.setTextColor(Color.parseColor("#F4F7FF"));
            stTitle.setTextSize(12f);
            stTitle.setPadding(0, 0, 0, dp(6));
            styleCard.addView(stTitle);

            String[] styleNames = {
                "•\nPunto", "＋\nCruz", "○\nCírculo", "⊙\nAnillo",
                "⌖\nPrecisión", "◌\nDinámica", "⌜⌟\nMínima", "✣\nPersonalizada"
            };
            int curStyle = prefs.getInt("cross_style", 0);
            for (int r = 0; r < 2; r++) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                for (int c = 0; c < 4; c++) {
                    final int idx = r * 4 + c;
                    TextView btn = new TextView(this);
                    btn.setText(styleNames[idx]);
                    btn.setGravity(Gravity.CENTER);
                    btn.setTextSize(10f);
                    btn.setTextColor(Color.parseColor(idx == curStyle ? "#00F5D4" : "#F4F7FF"));
                    btn.setBackgroundResource(idx == curStyle ? R.drawable.bg_neon_tab_selected : R.drawable.bg_neon_tab);
                    LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(0, dp(44), 1f);
                    glp.setMargins(dp(2), dp(2), dp(2), dp(2));
                    btn.setLayoutParams(glp);
                    btn.setOnClickListener(v -> {
                        prefs.edit().putInt("cross_style", idx).apply();
                        syncOverlayFromPrefs();
                        showTab(0);
                    });
                    row.addView(btn);
                }
                styleCard.addView(row);
            }

            TextView colTitle = new TextView(this);
            colTitle.setText("Color de la Mira");
            colTitle.setTextColor(Color.parseColor("#9BA6C4"));
            colTitle.setTextSize(11f);
            colTitle.setPadding(0, dp(8), 0, dp(4));
            styleCard.addView(colTitle);

            LinearLayout colorRow = new LinearLayout(this);
            colorRow.setOrientation(LinearLayout.HORIZONTAL);
            for (final int c : NEON_COLORS) {
                View sw = new View(this);
                GradientDrawable gd = new GradientDrawable();
                gd.setCornerRadius(dp(6));
                gd.setColor(c);
                gd.setStroke(dp(1), Color.WHITE);
                sw.setBackground(gd);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(22), dp(22));
                slp.rightMargin = dp(5);
                sw.setOnClickListener(v -> {
                    prefs.edit().putInt("cross_color", c).apply();
                    syncOverlayFromPrefs();
                });
                colorRow.addView(sw, slp);
            }
            styleCard.addView(colorRow);
            contentContainer.addView(styleCard);

            addSliderOption("TAMAÑO DE LA MIRA", "dp", "cross_size", 16, 92, 42);
            addSliderOption("OPACIDAD DE LA MIRA", "%", "cross_alpha", 35, 100, 100);

        } else if (tabIdx == 1) {
            addSectionHeader("OCULTAR CÓDIGO DE LA PARTIDA");
            addToggleOption("OCULTAR CÓDIGO DE LA PARTIDA",
                    "Arrastra libremente; mantén 2s para bloquear posición",
                    "hide_match_code", false, null);
            addSliderOption("GROSOR DE LA BARRA", "dp", "bar_thickness_dp", 10, 40, 16);
            addSliderOption("LONGITUD DE LA BARRA", "dp", "bar_length_dp", 80, 300, 220);

            addSectionHeader("EFECTOS VISUALES Y HUD");
            addToggleOption("Borde Holograma RGB", "Resaltado neón en tarjetas", "vis_rgb_border", true, null);
            addToggleOption("Contador de FPS / Ping", "Muestra rendimiento en tiempo real", "vis_fps", false, null);
            addSliderOption("Intensidad de Brillo Neón", "%", "vis_glow", 10, 100, 85);

        } else if (tabIdx == 2) {
            addSectionHeader("MÓDULOS AVANZADOS DEL PROXY (HEX ESP)");
            addToggleOption("FANTASMA V2 (GHOST V2)", "Estado del módulo", "hex_ghost_v2", false, null);
            addToggleOption("LATENCIA DE PING", "Estado del módulo", "hex_ping_latency", false, null);
            addToggleOption("INVISIBLE V2", "Estado del módulo", "hex_invisible_v2", false, null);
            addSliderOption("Velocidad de repetición (retardo)", "ms", "hex_repeat_delay_ms", 1, 50, 1);

            addToggleOption("Detener teletransporte automáticamente",
                    "Desactivar TP mientras CONGELAR esté activo",
                    "hex_auto_stop_tp", true, isEnabled -> {
                        if (isEnabled && prefs.getBoolean("hex_freeze", false)) {
                            prefs.edit()
                                    .putBoolean("hex_tp_target", false)
                                    .putBoolean("hex_tp_infinite", false)
                                    .apply();
                            showTab(2);
                        }
                    });

            addToggleOption("CONGELAR", "Congelación de paquetes en tiempo real", "hex_freeze", false, isEnabled -> {
                if (isEnabled && prefs.getBoolean("hex_auto_stop_tp", true)) {
                    prefs.edit()
                            .putBoolean("hex_tp_target", false)
                            .putBoolean("hex_tp_infinite", false)
                            .apply();
                    Toast.makeText(FloatingPanelService.this, "Teletransporte detenido por Congelación", Toast.LENGTH_SHORT).show();
                    showTab(2);
                }
            });

            addToggleOption("TELETRANSPORTE AL OBJETIVO", "Estado del módulo", "hex_tp_target", false, isEnabled -> {
                if (isEnabled && prefs.getBoolean("hex_freeze", false) && prefs.getBoolean("hex_auto_stop_tp", true)) {
                    prefs.edit().putBoolean("hex_tp_target", false).apply();
                    Toast.makeText(FloatingPanelService.this, "Desactiva CONGELAR primero", Toast.LENGTH_SHORT).show();
                    showTab(2);
                }
            });

            addToggleOption("TELETRANSPORTE INFINITO", "Estado del módulo", "hex_tp_infinite", false, isEnabled -> {
                if (isEnabled && prefs.getBoolean("hex_freeze", false) && prefs.getBoolean("hex_auto_stop_tp", true)) {
                    prefs.edit().putBoolean("hex_tp_infinite", false).apply();
                    Toast.makeText(FloatingPanelService.this, "Desactiva CONGELAR primero", Toast.LENGTH_SHORT).show();
                    showTab(2);
                }
            });

            addSliderOption("Paquetes por ráfaga", " paq", "hex_burst_packets", 1, 16, 1);
            addSliderOption("Retardo entre ráfagas", "ms", "hex_burst_delay_ms", 5, 25, 5);
            addToggleOption("Búfer de recuperación", "Modo de retención de movimiento", "hex_recovery_buffer", false, null);
            addToggleOption("Modo en vivo (desarrollador)", "Solo para desarrolladores", "hex_live_dev_mode", false, null);

            addSectionHeader("PERSONALIZACIÓN DEL BOTÓN FLOTANTE");
            addSliderOption("TAMAÑO DEL BOTÓN FLOTANTE", "%", "bubble_size", 70, 250, 100);
            addSliderOption("OPACIDAD DEL BOTÓN FLOTANTE", "%", "menu_alpha", 35, 100, 92);
            addToggleOption("Bloquear Posición", "Mantén 2s la burbuja para bloquear/desbloquear", "lock_pos", false, null);

        } else {
            addSectionHeader("INFORMACIÓN Y ESTADO DEL PROXY");
            LinearLayout card = createNeonCard();
            TextView brand = new TextView(this);
            brand.setText("XITO MODS × HEX ESP • V4");
            brand.setTextColor(Color.parseColor("#00F5D4"));
            brand.setTypeface(Typeface.DEFAULT_BOLD);
            brand.setTextSize(14f);
            brand.setPadding(0, 0, 0, dp(6));
            card.addView(brand);

            String targetApp = prefs.getString("selected_app", "FF MAX (com.dts.freefiremax)");
            TextView appTv = new TextView(this);
            appTv.setText("Aplicación: " + targetApp);
            appTv.setTextColor(Color.parseColor("#F4F7FF"));
            appTv.setTextSize(12f);
            appTv.setPadding(0, 0, 0, dp(4));
            card.addView(appTv);

            infoProxyTelemetryText = new TextView(this);
            infoProxyTelemetryText.setText("Estado del Proxy: CONECTADO\nLatencia estimada: "
                    + XitoProxyVpnService.currentSimulatedPingMs + " ms");
            infoProxyTelemetryText.setTextColor(Color.parseColor("#00FF87"));
            infoProxyTelemetryText.setTextSize(12f);
            infoProxyTelemetryText.setPadding(0, 0, 0, dp(6));
            card.addView(infoProxyTelemetryText);

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
            infoCountdownText.setPadding(0, dp(6), 0, dp(12));
            card.addView(infoCountdownText);

            TextView closeBtn = new TextView(this);
            closeBtn.setText("DETENER PROXY Y BOTÓN FLOTANTE");
            closeBtn.setGravity(Gravity.CENTER);
            closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
            closeBtn.setTextColor(Color.parseColor("#080A10"));
            closeBtn.setTextSize(12f);
            closeBtn.setBackgroundResource(R.drawable.btn_neon_primary);
            closeBtn.setPadding(dp(12), dp(10), dp(12), dp(10));
            closeBtn.setOnClickListener(v -> {
                Intent stopVpn = new Intent(FloatingPanelService.this, XitoProxyVpnService.class);
                stopVpn.setAction(XitoProxyVpnService.ACTION_STOP);
                stopService(stopVpn);
                stopSelf();
            });
            card.addView(closeBtn);

            contentContainer.addView(card);
        }
    }

    private interface ToggleListener {
        void onChanged(boolean enabled);
    }

    private void addSectionHeader(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(Color.parseColor("#00F5D4"));
        tv.setTypeface(Typeface.DEFAULT_BOLD);
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

    private void addToggleOption(String title, String subtitle, final String prefKey,
                                 final boolean defVal, final ToggleListener listener) {
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
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        t1.setTextSize(12f);
        texts.addView(t1);

        TextView t2 = new TextView(this);
        t2.setText(subtitle);
        t2.setTextColor(Color.parseColor("#9BA6C4"));
        t2.setTextSize(10f);
        texts.addView(t2);

        final TextView badge = new TextView(this);
        styleToggleBadge(badge, current);
        badge.setOnClickListener(v -> {
            boolean next = !prefs.getBoolean(prefKey, defVal);
            prefs.edit().putBoolean(prefKey, next).apply();
            styleToggleBadge(badge, next);
            syncOverlayFromPrefs();
            if (XitoProxyVpnService.isConnected) {
                Intent syncVpn = new Intent(FloatingPanelService.this, XitoProxyVpnService.class);
                syncVpn.setAction(XitoProxyVpnService.ACTION_SYNC);
                startService(syncVpn);
            }
            if (listener != null) {
                listener.onChanged(next);
            }
        });

        row.addView(texts);
        row.addView(badge);
        card.addView(row);
        contentContainer.addView(card);
    }

    private void styleToggleBadge(TextView badge, boolean active) {
        badge.setText(active ? "ACTIVO" : "OFF");
        badge.setTextSize(10f);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(12), dp(6), dp(12), dp(6));
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
        valTv.setTypeface(Typeface.DEFAULT_BOLD);
        valTv.setTextSize(12f);

        header.addView(t1);
        header.addView(valTv);
        card.addView(header);

        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, Math.min(max - min, current - min)));
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

        boolean hideCode = prefs.getBoolean("hide_match_code", false);
        int barThicknessDp = prefs.getInt("bar_thickness_dp", 16);
        int barLengthDp = prefs.getInt("bar_length_dp", 220);
        boolean barLocked = prefs.getBoolean("match_bar_locked", false);
        if (matchCodeBarView != null && matchCodeBarLp != null) {
            matchCodeBarView.setVisibility(hideCode ? View.VISIBLE : View.GONE);
            matchCodeBarLp.width = dp(barLengthDp);
            matchCodeBarLp.height = dp(barThicknessDp);
            styleMatchCodeBar(barLocked);
            wm.updateViewLayout(matchCodeBarView, matchCodeBarLp);
        }

        int bubbleScale = prefs.getInt("bubble_size", 100);
        int menuAlpha = prefs.getInt("menu_alpha", 92);
        if (bubbleView != null && bubbleLp != null) {
            int px = dp(56 * (bubbleScale / 100f));
            bubbleLp.width = px;
            bubbleLp.height = px;
            bubbleView.setAlpha(Math.max(0.35f, menuAlpha / 100f));
            wm.updateViewLayout(bubbleView, bubbleLp);
        }

        if (menuView != null) {
            menuView.setAlpha(Math.max(0.35f, menuAlpha / 100f));
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
            if (matchCodeBarView != null) wm.removeView(matchCodeBarView);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
