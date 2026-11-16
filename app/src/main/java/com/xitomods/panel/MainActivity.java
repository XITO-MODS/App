package com.xitomods.panel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Actividad Principal de XITO MODS (equivalente a A0 del APK original).
 * Incluye:
 * 1. Pantalla de Login con Clave Neón (buildLoginUi) y validación en KeyAuthManager.
 * 2. Diálogo de bienvenida (showJoinPopup).
 * 3. Panel Principal (Dashboard) con selector de app, estado de permisos, activación del botón flotante,
 *    personalización de tamaño/opacidad y configurador completo de Mira Neón.
 */
public class MainActivity extends Activity {
    private static final int REQ_OVERLAY_PERMISSION = 2001;
    private static final String CHANNEL_URL = "https://youtube.com/@XitoMods";

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private TextView keyCountdownTv;
    private TextView selectedAppTv;
    private TextView overlayPermBadge;
    private TextView engineStatusTv;
    private Button startStopButton;

    private final int[] NEON_COLORS = {
        Color.parseColor("#00F5D4"), // Cian Neón
        Color.parseColor("#BC13FE"), // Púrpura Neón
        Color.parseColor("#FF2A85"), // Rosa Neón
        Color.parseColor("#00FF87"), // Verde Neón
        Color.parseColor("#FFE600"), // Amarillo Cyber
        Color.parseColor("#00B4D8"), // Azul Eléctrico
        Color.parseColor("#FF5400"), // Naranja Plasma
        Color.parseColor("#FF0055"), // Carmesí
        Color.parseColor("#FFFFFF")  // Blanco Puro
    };

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!KeyAuthManager.hasValidSession(MainActivity.this)) {
                buildLoginUi();
                return;
            }
            if (keyCountdownTv != null) {
                long exp = KeyAuthManager.getExpirationMs(MainActivity.this);
                keyCountdownTv.setText("Validez de la clave: " + KeyAuthManager.formatRemainingTime(exp));
            }
            refreshStatusBadges();
            uiHandler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (KeyAuthManager.hasValidSession(this)) {
            buildDashboardUi();
        } else {
            buildLoginUi();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (KeyAuthManager.hasValidSession(this)) {
            uiHandler.post(tickRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        uiHandler.removeCallbacks(tickRunnable);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // =========================================================================
    // PANTALLA DE LOGIN NEÓN (Equivalente a buildLoginUi en A0)
    // =========================================================================
    private void buildLoginUi() {
        uiHandler.removeCallbacks(tickRunnable);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor("#080A10"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackgroundResource(R.drawable.neon_panel);
        int cardPad = dp(24);
        card.setPadding(cardPad, cardPad, cardPad, cardPad);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_xito_logo);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(92), dp(92));
        logoLp.bottomMargin = dp(14);
        card.addView(logo, logoLp);

        TextView title = new TextView(this);
        title.setText("XITO MODS");
        title.setTextSize(26f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.parseColor("#00F5D4"));
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(0.08f);
        card.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("ACCESO CON CLAVE • EDICIÓN NEÓN V4");
        subtitle.setTextSize(12f);
        subtitle.setTextColor(Color.parseColor("#BC13FE"));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(4), 0, dp(20));
        card.addView(subtitle);

        final EditText keyInput = new EditText(this);
        keyInput.setHint("Escribe tu clave (ej: XITOMODS-VIP)");
        keyInput.setHintTextColor(Color.parseColor("#6E7894"));
        keyInput.setTextColor(Color.parseColor("#F4F7FF"));
        keyInput.setTextSize(14f);
        keyInput.setSingleLine(true);
        keyInput.setBackgroundResource(R.drawable.bg_neon_input);
        keyInput.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputLp.bottomMargin = dp(12);
        card.addView(keyInput, inputLp);

        final TextView loginStatus = new TextView(this);
        loginStatus.setText("Introduce tu clave de acceso para desbloquear el panel flotante.");
        loginStatus.setTextColor(Color.parseColor("#9BA6C4"));
        loginStatus.setTextSize(12f);
        loginStatus.setGravity(Gravity.CENTER);
        loginStatus.setPadding(0, 0, 0, dp(16));
        card.addView(loginStatus);

        Button loginBtn = new Button(this);
        loginBtn.setText("ACCEDER");
        loginBtn.setTextColor(Color.parseColor("#080A10"));
        loginBtn.setTypeface(Typeface.DEFAULT_BOLD);
        loginBtn.setTextSize(14f);
        loginBtn.setAllCaps(false);
        loginBtn.setBackgroundResource(R.drawable.btn_neon_primary);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        btnLp.bottomMargin = dp(10);
        loginBtn.setOnClickListener(v -> {
            String entered = keyInput.getText().toString();
            loginStatus.setText("Verificando la clave...");
            loginStatus.setTextColor(Color.parseColor("#00F5D4"));
            KeyAuthManager.AuthResult res = KeyAuthManager.verifyKey(MainActivity.this, entered);
            if (res.valid) {
                Toast.makeText(MainActivity.this, "Bienvenido a XITO MODS V4", Toast.LENGTH_SHORT).show();
                buildDashboardUi();
                showJoinPopup();
            } else {
                loginStatus.setText(res.message);
                loginStatus.setTextColor(Color.parseColor("#FF2A85"));
            }
        });
        card.addView(loginBtn, btnLp);

        Button getKeyBtn = new Button(this);
        getKeyBtn.setText("OBTENER CLAVE / CANAL OFICIAL");
        getKeyBtn.setTextColor(Color.parseColor("#00F5D4"));
        getKeyBtn.setTextSize(13f);
        getKeyBtn.setAllCaps(false);
        getKeyBtn.setBackgroundResource(R.drawable.btn_neon_secondary);
        getKeyBtn.setOnClickListener(v -> openLink(CHANNEL_URL));
        card.addView(getKeyBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        root.addView(card);
        scroll.addView(root);
        setContentView(scroll);
    }

    // =========================================================================
    // DASHBOARD PRINCIPAL NEÓN (Equivalente a l000.xml + buildMainPages en A0)
    // =========================================================================
    private void buildDashboardUi() {
        final SharedPreferences prefs = getSharedPreferences(FloatingPanelService.UI_PREFS, MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor("#080A10"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);

        // Encabezado XITO MODS
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headerLp.bottomMargin = dp(18);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_xito_logo);
        header.addView(logo, new LinearLayout.LayoutParams(dp(62), dp(62)));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tcLp.leftMargin = dp(14);
        titleCol.setLayoutParams(tcLp);

        TextView appTitle = new TextView(this);
        appTitle.setText("XITO MODS");
        appTitle.setTextSize(22f);
        appTitle.setTypeface(Typeface.DEFAULT_BOLD);
        appTitle.setTextColor(Color.parseColor("#00F5D4"));
        appTitle.setLetterSpacing(0.06f);
        titleCol.addView(appTitle);

        keyCountdownTv = new TextView(this);
        long exp = KeyAuthManager.getExpirationMs(this);
        keyCountdownTv.setText("Validez de la clave: " + KeyAuthManager.formatRemainingTime(exp));
        keyCountdownTv.setTextSize(12f);
        keyCountdownTv.setTextColor(Color.parseColor("#BC13FE"));
        titleCol.addView(keyCountdownTv);

        Button logoutBtn = new Button(this);
        logoutBtn.setText("SALIR");
        logoutBtn.setTextSize(11f);
        logoutBtn.setTextColor(Color.parseColor("#FF2A85"));
        logoutBtn.setBackgroundResource(R.drawable.btn_neon_secondary);
        logoutBtn.setOnClickListener(v -> {
            stopService(new Intent(MainActivity.this, FloatingPanelService.class));
            KeyAuthManager.logout(MainActivity.this);
            buildLoginUi();
        });

        header.addView(titleCol);
        header.addView(logoutBtn, new LinearLayout.LayoutParams(dp(74), dp(38)));
        root.addView(header, headerLp);

        // SECCIÓN 01: ELIGE LA APLICACIÓN
        LinearLayout card1 = createSectionCard("01  /  ELIGE LA APLICACIÓN", "Selecciona el perfil donde usarás el panel flotante XITO MODS.");
        selectedAppTv = new TextView(this);
        String savedPkg = prefs.getString("selected_app", "FF MAX (com.dts.freefiremax)");
        selectedAppTv.setText("Aplicación activa: " + savedPkg);
        selectedAppTv.setTextColor(Color.parseColor("#00F5D4"));
        selectedAppTv.setTextSize(13f);
        selectedAppTv.setPadding(0, 0, 0, dp(10));
        card1.addView(selectedAppTv);

        LinearLayout rowApps = new LinearLayout(this);
        rowApps.setOrientation(LinearLayout.HORIZONTAL);
        Button btnFfMax = createNeonButton("FF MAX", false, v -> selectAppProfile("FF MAX (com.dts.freefiremax)"));
        Button btnFfNorm = createNeonButton("FF NORMAL", false, v -> selectAppProfile("FF NORMAL (com.dts.freefireth)"));
        LinearLayout.LayoutParams halfLp1 = new LinearLayout.LayoutParams(0, dp(48), 1f);
        halfLp1.rightMargin = dp(6);
        LinearLayout.LayoutParams halfLp2 = new LinearLayout.LayoutParams(0, dp(48), 1f);
        halfLp2.leftMargin = dp(6);
        rowApps.addView(btnFfMax, halfLp1);
        rowApps.addView(btnFfNorm, halfLp2);
        card1.addView(rowApps);
        root.addView(card1);

        // SECCIÓN 02: CONCEDE EL ACCESO
        LinearLayout card2 = createSectionCard("02  /  CONCEDE EL ACCESO", "Habilita el permiso de superposición para mostrar el panel neón.");
        LinearLayout permRow = new LinearLayout(this);
        permRow.setOrientation(LinearLayout.HORIZONTAL);
        permRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView permLabel = new TextView(this);
        permLabel.setText("Mostrar sobre otras aplicaciones");
        permLabel.setTextColor(Color.parseColor("#F4F7FF"));
        permLabel.setTextSize(13f);
        permLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        overlayPermBadge = new TextView(this);
        overlayPermBadge.setOnClickListener(v -> requestOverlayPermission());
        permRow.addView(permLabel);
        permRow.addView(overlayPermBadge);
        card2.addView(permRow);
        root.addView(card2);

        // SECCIÓN 03: INICIA EL BOTÓN FLOTANTE
        LinearLayout card3 = createSectionCard("03  /  INICIA EL BOTÓN FLOTANTE", "Después de iniciar, toca la burbuja XITO MODS para abrir el menú neón.");
        engineStatusTv = new TextView(this);
        engineStatusTv.setTextSize(14f);
        engineStatusTv.setTypeface(Typeface.DEFAULT_BOLD);
        engineStatusTv.setPadding(0, 0, 0, dp(10));
        card3.addView(engineStatusTv);

        startStopButton = createNeonButton("INICIAR BOTÓN Y PANEL FLOTANTE", true, v -> toggleFloatingService());
        card3.addView(startStopButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));
        root.addView(card3);

        // SECCIÓN 04: PERSONALIZA EL PANEL
        LinearLayout card4 = createSectionCard("04  /  PERSONALIZA", "Tamaño y transparencia del botón y panel flotante en tiempo real.");
        addDashboardSlider(card4, "TAMAÑO DEL BOTÓN FLOTANTE", "%", "bubble_size", 70, 160, 100);
        addDashboardSlider(card4, "OPACIDAD DEL PANEL FLOTANTE", "%", "menu_alpha", 50, 100, 94);
        root.addView(card4);

        // SECCIÓN 05: MIRA PERSONALIZADA NEÓN
        LinearLayout card5 = createSectionCard("05  /  MIRA PERSONALIZADA NEÓN", "Elige entre los 8 estilos y 9 colores neón en tiempo real.");
        String[] styleNames = {
            "•
Punto", "＋
Cruz", "○
Círculo", "⊙
Anillo",
            "⌖
Precisión", "◌
Dinámica", "⌜⌟
Mínima", "✣
XITO Neón"
        };
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setRowCount(2);
        for (int i = 0; i < styleNames.length; i++) {
            final int styleIdx = i;
            TextView styleBtn = new TextView(this);
            styleBtn.setText(styleNames[i]);
            styleBtn.setGravity(Gravity.CENTER);
            styleBtn.setTextSize(11f);
            styleBtn.setTextColor(Color.parseColor("#F4F7FF"));
            styleBtn.setBackgroundResource(R.drawable.bg_neon_tab);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0;
            glp.height = dp(54);
            glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            glp.setMargins(dp(3), dp(3), dp(3), dp(3));
            styleBtn.setLayoutParams(glp);
            styleBtn.setOnClickListener(v -> {
                prefs.edit().putInt("cross_style", styleIdx).apply();
                notifyServiceRefresh();
                Toast.makeText(MainActivity.this, "Estilo de mira actualizado", Toast.LENGTH_SHORT).show();
            });
            grid.addView(styleBtn);
        }
        card5.addView(grid);

        // Selector de 9 colores neón
        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        colorRow.setPadding(0, dp(12), 0, dp(8));
        for (final int c : NEON_COLORS) {
            View swatch = new View(this);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(c);
            gd.setStroke(dp(1.5f), Color.WHITE);
            swatch.setBackground(gd);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(26), dp(26));
            slp.rightMargin = dp(8);
            swatch.setOnClickListener(v -> {
                prefs.edit().putInt("cross_color", c).apply();
                notifyServiceRefresh();
            });
            colorRow.addView(swatch, slp);
        }
        card5.addView(colorRow);

        addDashboardSlider(card5, "TAMAÑO DE LA MIRA", "dp", "cross_size", 16, 80, 42);
        addDashboardSlider(card5, "OPACIDAD DE LA MIRA", "%", "cross_alpha", 25, 100, 100);
        root.addView(card5);

        setContentView(scroll);
        refreshStatusBadges();
        uiHandler.post(tickRunnable);
    }

    private LinearLayout createSectionCard(String title, String subtitle) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.neon_card);
        int p = dp(16);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);

        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextSize(15f);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        t1.setTextColor(Color.parseColor("#00F5D4"));
        card.addView(t1);

        TextView t2 = new TextView(this);
        t2.setText(subtitle);
        t2.setTextSize(12f);
        t2.setTextColor(Color.parseColor("#9BA6C4"));
        t2.setPadding(0, dp(4), 0, dp(12));
        card.addView(t2);
        return card;
    }

    private Button createNeonButton(String text, boolean primary, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.parseColor(primary ? "#080A10" : "#F4F7FF"));
        b.setBackgroundResource(primary ? R.drawable.btn_neon_primary : R.drawable.btn_neon_secondary);
        b.setOnClickListener(listener);
        return b;
    }

    private void addDashboardSlider(LinearLayout parent, String label, final String unit, final String key, final int min, int max, int defVal) {
        final SharedPreferences prefs = getSharedPreferences(FloatingPanelService.UI_PREFS, MODE_PRIVATE);
        int cur = prefs.getInt(key, defVal);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, 0);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.parseColor("#F4F7FF"));
        tvLabel.setTextSize(12f);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final TextView tvVal = new TextView(this);
        tvVal.setText(cur + unit);
        tvVal.setTextColor(Color.parseColor("#00F5D4"));
        tvVal.setTextSize(12f);

        row.addView(tvLabel);
        row.addView(tvVal);
        parent.addView(row);

        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, cur - min));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = min + progress;
                tvVal.setText(val + unit);
                prefs.edit().putInt(key, val).apply();
                notifyServiceRefresh();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        parent.addView(sb);
    }

    private void selectAppProfile(String profileName) {
        getSharedPreferences(FloatingPanelService.UI_PREFS, MODE_PRIVATE)
                .edit().putString("selected_app", profileName).apply();
        if (selectedAppTv != null) {
            selectedAppTv.setText("Aplicación activa: " + profileName);
        }
        Toast.makeText(this, "Seleccionado: " + profileName, Toast.LENGTH_SHORT).show();
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void requestOverlayPermission() {
        if (!canDrawOverlays() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_OVERLAY_PERMISSION);
        } else {
            Toast.makeText(this, "Permiso de superposición concedido", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleFloatingService() {
        if (!canDrawOverlays()) {
            requestOverlayPermission();
            return;
        }
        Intent svc = new Intent(this, FloatingPanelService.class);
        if (FloatingPanelService.isRunning) {
            stopService(svc);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
        }
        uiHandler.postDelayed(this::refreshStatusBadges, 250L);
    }

    private void notifyServiceRefresh() {
        if (FloatingPanelService.isRunning) {
            Intent i = new Intent(this, FloatingPanelService.class);
            i.setAction(FloatingPanelService.ACTION_REFRESH);
            startService(i);
        }
    }

    private void refreshStatusBadges() {
        if (overlayPermBadge != null) {
            boolean ok = canDrawOverlays();
            overlayPermBadge.setText(ok ? "PERMITIDO" : "CONCEDER");
            overlayPermBadge.setTextColor(Color.parseColor(ok ? "#00F5D4" : "#FF2A85"));
            overlayPermBadge.setBackgroundResource(R.drawable.bg_neon_badge);
            overlayPermBadge.setPadding(dp(12), dp(6), dp(12), dp(6));
        }
        if (engineStatusTv != null && startStopButton != null) {
            boolean active = FloatingPanelService.isRunning;
            engineStatusTv.setText(active ? "● PANEL FLOTANTE ACTIVO" : "● DESCONECTADO");
            engineStatusTv.setTextColor(Color.parseColor(active ? "#00FF87" : "#FF2A85"));
            startStopButton.setText(active ? "DETENER PANEL FLOTANTE" : "INICIAR BOTÓN Y PANEL FLOTANTE");
        }
    }

    private void showJoinPopup() {
        View popupView = LayoutInflater.from(this).inflate(R.layout.join_popup, null);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(popupView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        popupView.findViewById(R.id.btn_popup_dismiss).setOnClickListener(v -> dialog.dismiss());
        popupView.findViewById(R.id.btn_popup_join).setOnClickListener(v -> {
            dialog.dismiss();
            openLink(CHANNEL_URL);
        });
        dialog.show();
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Instala un navegador para abrir el enlace.", Toast.LENGTH_SHORT).show();
        }
    }
}
