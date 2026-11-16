package com.xitomods.panel;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.VpnService;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Actividad Principal de XITO MODS con todas las funciones originales de HEX_ESP integradas.
 */
public class MainActivity extends Activity {
    private static final int REQ_OVERLAY_PERMISSION = 2001;
    private static final int REQ_VPN_PERMISSION = 2002;
    private static final int REQ_NOTIF_PERMISSION = 2003;
    private static final int REQ_STORAGE_PERMISSION = 2004;
    private static final String CHANNEL_URL = "https://youtube.com/@XitoMods";

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private TextView keyCountdownTv;
    private View appStatusDot;
    private TextView selectedAppTv;

    private View vpnPermDot;
    private TextView vpnPermBadge;
    private View overlayPermDot;
    private TextView overlayPermBadge;
    private View notifPermDot;
    private TextView notifPermBadge;
    private View storagePermDot;
    private TextView storagePermBadge;

    private View engineStatusDot;
    private TextView engineStatusTv;
    private TextView engineSubStatusTv;
    private Button startStopButton;

    private TextView hideCodeSubTv;
    private TextView hideCodeBadge;
    private TextView tpTargetBadge;
    private TextView tpTargetSubTv;
    private TextView tpInfiniteBadge;
    private TextView tpInfiniteSubTv;
    private TextView freezeBadge;
    private TextView freezeSubTv;
    private View crossColorPreviewBar;
    private final List<TextView> crossStyleButtons = new ArrayList<>();

    private boolean pendingStartAfterVpnGrant = false;

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
            refreshStatusBadges();
            uiHandler.removeCallbacks(tickRunnable);
            uiHandler.post(tickRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        uiHandler.removeCallbacks(tickRunnable);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN_PERMISSION) {
            if (resultCode == RESULT_OK) {
                getSharedPreferences(FloatingPanelService.UI_PREFS, MODE_PRIVATE)
                        .edit().putBoolean("vpn_perm_granted", true).apply();
                if (pendingStartAfterVpnGrant) {
                    pendingStartAfterVpnGrant = false;
                    startProxyAndOverlayServices();
                }
            } else {
                pendingStartAfterVpnGrant = false;
                Toast.makeText(this, "Permiso de VPN no concedido", Toast.LENGTH_SHORT).show();
            }
        }
        refreshStatusBadges();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // =========================================================================
    // PANTALLA DE LOGIN NEÓN
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
        subtitle.setText("ACCESO CON CLAVE • EDICIÓN HEX NEÓN V4");
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
        loginStatus.setText("Introduce tu clave de acceso para desbloquear el proxy y panel flotante.");
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
    // DASHBOARD PRINCIPAL CON LAS 6 SECCIONES DE HEX_ESP
    // =========================================================================
    private void buildDashboardUi() {
        final SharedPreferences prefs = getSharedPreferences(FloatingPanelService.UI_PREFS, MODE_PRIVATE);
        crossStyleButtons.clear();

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
        headerLp.bottomMargin = dp(12);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_xito_logo);
        header.addView(logo, new LinearLayout.LayoutParams(dp(64), dp(64)));

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

        TextView tagline = new TextView(this);
        tagline.setText("Tu panel. Tu control.");
        tagline.setTextSize(13f);
        tagline.setTextColor(Color.parseColor("#9BA6C4"));
        titleCol.addView(tagline);

        keyCountdownTv = new TextView(this);
        long exp = KeyAuthManager.getExpirationMs(this);
        keyCountdownTv.setText("Validez de la clave: " + KeyAuthManager.formatRemainingTime(exp));
        keyCountdownTv.setTextSize(11f);
        keyCountdownTv.setTextColor(Color.parseColor("#BC13FE"));
        keyCountdownTv.setPadding(0, dp(2), 0, 0);
        titleCol.addView(keyCountdownTv);

        Button logoutBtn = new Button(this);
        logoutBtn.setText("SALIR");
        logoutBtn.setTextSize(11f);
        logoutBtn.setTextColor(Color.parseColor("#FF2A85"));
        logoutBtn.setBackgroundResource(R.drawable.btn_neon_secondary);
        logoutBtn.setOnClickListener(v -> {
            stopProxyAndOverlayServices();
            KeyAuthManager.logout(MainActivity.this);
            buildLoginUi();
        });

        header.addView(titleCol);
        header.addView(logoutBtn, new LinearLayout.LayoutParams(dp(74), dp(38)));
        root.addView(header, headerLp);

        TextView heroTitle = new TextView(this);
        heroTitle.setText("Prepara. Conecta. Controla.");
        heroTitle.setTextSize(22f);
        heroTitle.setTypeface(Typeface.DEFAULT_BOLD);
        heroTitle.setTextColor(Color.parseColor("#F4F7FF"));
        root.addView(heroTitle);

        TextView heroSub = new TextView(this);
        heroSub.setText("Configura el acceso abajo. Después, usa el menú flotante dentro de la aplicación.");
        heroSub.setTextSize(13f);
        heroSub.setTextColor(Color.parseColor("#9BA6C4"));
        heroSub.setPadding(0, dp(4), 0, dp(18));
        root.addView(heroSub);

        // =====================================================================
        // SECCIÓN 01: ELIGE LA APLICACIÓN
        // =====================================================================
        LinearLayout card1 = createSectionCard("01  /  ELIGE LA APLICACIÓN", "Selecciona dónde usarás el proxy.");
        LinearLayout appStatusRow = new LinearLayout(this);
        appStatusRow.setOrientation(LinearLayout.HORIZONTAL);
        appStatusRow.setGravity(Gravity.CENTER_VERTICAL);
        appStatusRow.setPadding(0, 0, 0, dp(12));

        appStatusDot = createStatusDot(true);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(9), dp(9));
        dotLp.rightMargin = dp(10);
        appStatusRow.addView(appStatusDot, dotLp);

        selectedAppTv = new TextView(this);
        String savedApp = prefs.getString("selected_app", "FF MAX (com.dts.freefiremax)");
        selectedAppTv.setText(savedApp);
        selectedAppTv.setTextColor(Color.parseColor("#F4F7FF"));
        selectedAppTv.setTextSize(14f);
        selectedAppTv.setTypeface(Typeface.DEFAULT_BOLD);
        selectedAppTv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        appStatusRow.addView(selectedAppTv);
        card1.addView(appStatusRow);

        Button btnSelectApp = createNeonButton("SELECCIONAR APLICACIÓN", false, v -> openAppPickerDialog());
        LinearLayout.LayoutParams fullBtnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        fullBtnLp.bottomMargin = dp(8);
        card1.addView(btnSelectApp, fullBtnLp);

        LinearLayout rowApps = new LinearLayout(this);
        rowApps.setOrientation(LinearLayout.HORIZONTAL);
        Button btnFfMax = createNeonButton("FF MAX", false,
                v -> selectAppProfile("FF MAX (com.dts.freefiremax)", "com.dts.freefiremax"));
        Button btnFfNorm = createNeonButton("FF NORMAL", false,
                v -> selectAppProfile("FF NORMAL (com.dts.freefireth)", "com.dts.freefireth"));
        LinearLayout.LayoutParams halfLp1 = new LinearLayout.LayoutParams(0, dp(50), 1f);
        halfLp1.rightMargin = dp(5);
        LinearLayout.LayoutParams halfLp2 = new LinearLayout.LayoutParams(0, dp(50), 1f);
        halfLp2.leftMargin = dp(5);
        rowApps.addView(btnFfMax, halfLp1);
        rowApps.addView(btnFfNorm, halfLp2);
        card1.addView(rowApps);
        root.addView(card1);

        // =====================================================================
        // SECCIÓN 02: CONCEDE EL ACCESO (4 Permisos de HEX_ESP)
        // =====================================================================
        LinearLayout card2 = createSectionCard("02  /  CONCEDE EL ACCESO", "Revisa los permisos antes de iniciar.");

        vpnPermDot = createStatusDot(false);
        vpnPermBadge = new TextView(this);
        card2.addView(createPermissionRow("Servicio VPN", vpnPermDot, vpnPermBadge, v -> requestVpnPermission(false)));

        overlayPermDot = createStatusDot(false);
        overlayPermBadge = new TextView(this);
        card2.addView(createPermissionRow("Mostrar sobre otras aplicaciones", overlayPermDot, overlayPermBadge, v -> requestOverlayPermission()));

        notifPermDot = createStatusDot(false);
        notifPermBadge = new TextView(this);
        card2.addView(createPermissionRow("Notificaciones del sistema", notifPermDot, notifPermBadge, v -> requestNotificationPermission()));

        storagePermDot = createStatusDot(false);
        storagePermBadge = new TextView(this);
        card2.addView(createPermissionRow("Almacenamiento del dispositivo", storagePermDot, storagePermBadge, v -> requestStoragePermission()));

        root.addView(card2);

        // =====================================================================
        // SECCIÓN 03: INICIA EL BOTÓN FLOTANTE Y PROXY
        // =====================================================================
        LinearLayout card3 = createSectionCard("03  /  INICIA EL BOTÓN FLOTANTE", "Confirma la solicitud de VPN cuando Android la solicite.");

        LinearLayout connRow = new LinearLayout(this);
        connRow.setOrientation(LinearLayout.HORIZONTAL);
        connRow.setGravity(Gravity.CENTER_VERTICAL);
        connRow.setPadding(0, 0, 0, dp(12));

        engineStatusDot = createStatusDot(false);
        LinearLayout.LayoutParams eDotLp = new LinearLayout.LayoutParams(dp(9), dp(9));
        eDotLp.rightMargin = dp(12);
        connRow.addView(engineStatusDot, eDotLp);

        LinearLayout connTextCol = new LinearLayout(this);
        connTextCol.setOrientation(LinearLayout.VERTICAL);
        connTextCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        engineStatusTv = new TextView(this);
        engineStatusTv.setTextSize(15f);
        engineStatusTv.setTypeface(Typeface.DEFAULT_BOLD);
        connTextCol.addView(engineStatusTv);

        engineSubStatusTv = new TextView(this);
        engineSubStatusTv.setTextSize(11f);
        engineSubStatusTv.setTextColor(Color.parseColor("#9BA6C4"));
        engineSubStatusTv.setText("Revisa los permisos y selecciona una aplicación.");
        connTextCol.addView(engineSubStatusTv);

        connRow.addView(connTextCol);
        card3.addView(connRow);

        startStopButton = createNeonButton("INICIAR PROXY Y BOTÓN FLOTANTE", true, v -> toggleProxyAndFloatingService());
        card3.addView(startStopButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));

        TextView afterStartHint = new TextView(this);
        afterStartHint.setText("Después de iniciar, toca el botón flotante para abrir los controles.");
        afterStartHint.setTextSize(12f);
        afterStartHint.setTextColor(Color.parseColor("#9BA6C4"));
        afterStartHint.setPadding(0, dp(10), 0, 0);
        card3.addView(afterStartHint);
        root.addView(card3);

        // =====================================================================
        // SECCIÓN 04: PERSONALIZA (Tamaño, opacidad y Ocultar Código de la Partida)
        // =====================================================================
        LinearLayout card4 = createSectionCard("04  /  PERSONALIZA", "Tamaño, transparencia y apariencia del botón flotante.");
        addDashboardSlider(card4, "TAMAÑO DEL BOTÓN FLOTANTE", "%", "bubble_size", 70, 250, 100);
        addDivider(card4);
        addDashboardSlider(card4, "OPACIDAD DEL BOTÓN FLOTANTE", "%", "menu_alpha", 35, 100, 92);
        addDivider(card4);

        boolean hideCodeOn = prefs.getBoolean("hide_match_code", false);
        boolean barLocked = prefs.getBoolean("match_bar_locked", false);
        LinearLayout hideCodeRow = new LinearLayout(this);
        hideCodeRow.setOrientation(LinearLayout.HORIZONTAL);
        hideCodeRow.setGravity(Gravity.CENTER_VERTICAL);
        hideCodeRow.setPadding(0, dp(8), 0, dp(8));

        LinearLayout hideCodeTextCol = new LinearLayout(this);
        hideCodeTextCol.setOrientation(LinearLayout.VERTICAL);
        hideCodeTextCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView hideCodeTitle = new TextView(this);
        hideCodeTitle.setText("OCULTAR CÓDIGO DE LA PARTIDA");
        hideCodeTitle.setTextColor(Color.parseColor("#F4F7FF"));
        hideCodeTitle.setTextSize(14f);
        hideCodeTitle.setTypeface(Typeface.DEFAULT_BOLD);
        hideCodeTextCol.addView(hideCodeTitle);

        hideCodeSubTv = new TextView(this);
        hideCodeSubTv.setText(barLocked
                ? "Posición bloqueada (mantén 2s sobre la barra para desbloquear)"
                : "Arrastra libremente; mantén pulsado 2 segundos para bloquear la posición");
        hideCodeSubTv.setTextColor(Color.parseColor("#9BA6C4"));
        hideCodeSubTv.setTextSize(12f);
        hideCodeSubTv.setPadding(0, dp(2), 0, 0);
        hideCodeTextCol.addView(hideCodeSubTv);

        hideCodeBadge = new TextView(this);
        styleSwitchBadge(hideCodeBadge, hideCodeOn, "Visible", "Oculto");
        hideCodeBadge.setOnClickListener(v -> {
            boolean next = !prefs.getBoolean("hide_match_code", false);
            prefs.edit().putBoolean("hide_match_code", next).apply();
            styleSwitchBadge(hideCodeBadge, next, "Visible", "Oculto");
            notifyServiceRefresh();
        });

        hideCodeRow.addView(hideCodeTextCol);
        hideCodeRow.addView(hideCodeBadge);
        card4.addView(hideCodeRow);

        LinearLayout barSubBox = createSubContainer();
        addDashboardSlider(barSubBox, "GROSOR DE LA BARRA", "dp", "bar_thickness_dp", 10, 40, 16);
        addDashboardSlider(barSubBox, "LONGITUD DE LA BARRA", "dp", "bar_length_dp", 80, 300, 220);
        card4.addView(barSubBox);
        root.addView(card4);

        // =====================================================================
        // SECCIÓN 05: AJUSTES AVANZADOS (Módulos completos de HEX_ESP)
        // =====================================================================
        LinearLayout card5 = createSectionCard("05  /  AJUSTES AVANZADOS", "Opciones adicionales del proxy. Ajusta cuando lo necesites.");

        addModuleToggleRow(card5, "FANTASMA V2 (GHOST V2)", "Estado del módulo", "hex_ghost_v2", false, null);
        addDivider(card5);

        addModuleToggleRow(card5, "LATENCIA DE PING", "Estado del módulo", "hex_ping_latency", false, null);
        addDivider(card5);

        addModuleToggleRow(card5, "INVISIBLE V2", "Estado del módulo", "hex_invisible_v2", false, null);
        LinearLayout invSubBox = createSubContainer();
        addDashboardSlider(invSubBox, "Velocidad de repetición (retardo)", "ms", "hex_repeat_delay_ms", 1, 50, 1);
        card5.addView(invSubBox);
        addDivider(card5);

        addModuleToggleRow(card5,
                "Detener el teletransporte automáticamente",
                "Desactivar el teletransporte mientras la congelación esté activa",
                "hex_auto_stop_tp",
                true,
                isEnabled -> {
                    if (isEnabled && prefs.getBoolean("hex_freeze", false)) {
                        prefs.edit()
                                .putBoolean("hex_tp_target", false)
                                .putBoolean("hex_tp_infinite", false)
                                .apply();
                        refreshModuleStatesUI();
                        notifyServiceRefresh();
                    }
                });
        addDivider(card5);

        // CONGELAR
        LinearLayout freezeRow = new LinearLayout(this);
        freezeRow.setOrientation(LinearLayout.HORIZONTAL);
        freezeRow.setGravity(Gravity.CENTER_VERTICAL);
        freezeRow.setPadding(0, dp(8), 0, dp(8));

        LinearLayout freezeCol = new LinearLayout(this);
        freezeCol.setOrientation(LinearLayout.VERTICAL);
        freezeCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView freezeTitle = new TextView(this);
        freezeTitle.setText("CONGELAR");
        freezeTitle.setTextColor(Color.parseColor("#F4F7FF"));
        freezeTitle.setTextSize(14f);
        freezeTitle.setTypeface(Typeface.DEFAULT_BOLD);
        freezeCol.addView(freezeTitle);

        freezeSubTv = new TextView(this);
        freezeSubTv.setTextColor(Color.parseColor("#9BA6C4"));
        freezeSubTv.setTextSize(12f);
        freezeCol.addView(freezeSubTv);

        freezeBadge = new TextView(this);
        freezeBadge.setOnClickListener(v -> {
            boolean next = !prefs.getBoolean("hex_freeze", false);
            SharedPreferences.Editor ed = prefs.edit().putBoolean("hex_freeze", next);
            if (next && prefs.getBoolean("hex_auto_stop_tp", true)) {
                ed.putBoolean("hex_tp_target", false);
                ed.putBoolean("hex_tp_infinite", false);
                Toast.makeText(MainActivity.this, "CONGELAR activo: Teletransporte detenido automáticamente", Toast.LENGTH_SHORT).show();
            }
            ed.apply();
            refreshModuleStatesUI();
            notifyServiceRefresh();
        });
        freezeRow.addView(freezeCol);
        freezeRow.addView(freezeBadge);
        card5.addView(freezeRow);
        addDivider(card5);

        // TELETRANSPORTE AL OBJETIVO
        LinearLayout tpTargetRow = new LinearLayout(this);
        tpTargetRow.setOrientation(LinearLayout.HORIZONTAL);
        tpTargetRow.setGravity(Gravity.CENTER_VERTICAL);
        tpTargetRow.setPadding(0, dp(8), 0, dp(8));

        LinearLayout tpTargetCol = new LinearLayout(this);
        tpTargetCol.setOrientation(LinearLayout.VERTICAL);
        tpTargetCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tpTargetTitle = new TextView(this);
        tpTargetTitle.setText("TELETRANSPORTE AL OBJETIVO");
        tpTargetTitle.setTextColor(Color.parseColor("#F4F7FF"));
        tpTargetTitle.setTextSize(14f);
        tpTargetTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tpTargetCol.addView(tpTargetTitle);

        tpTargetSubTv = new TextView(this);
        tpTargetSubTv.setTextColor(Color.parseColor("#9BA6C4"));
        tpTargetSubTv.setTextSize(12f);
        tpTargetCol.addView(tpTargetSubTv);

        tpTargetBadge = new TextView(this);
        tpTargetBadge.setOnClickListener(v -> {
            if (prefs.getBoolean("hex_freeze", false) && prefs.getBoolean("hex_auto_stop_tp", true)) {
                Toast.makeText(MainActivity.this, "Desactiva CONGELAR o el bloqueo automático primero", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean next = !prefs.getBoolean("hex_tp_target", false);
            prefs.edit().putBoolean("hex_tp_target", next).apply();
            refreshModuleStatesUI();
            notifyServiceRefresh();
        });
        tpTargetRow.addView(tpTargetCol);
        tpTargetRow.addView(tpTargetBadge);
        card5.addView(tpTargetRow);
        addDivider(card5);

        // TELETRANSPORTE INFINITO
        LinearLayout tpInfRow = new LinearLayout(this);
        tpInfRow.setOrientation(LinearLayout.HORIZONTAL);
        tpInfRow.setGravity(Gravity.CENTER_VERTICAL);
        tpInfRow.setPadding(0, dp(8), 0, dp(8));

        LinearLayout tpInfCol = new LinearLayout(this);
        tpInfCol.setOrientation(LinearLayout.VERTICAL);
        tpInfCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tpInfTitle = new TextView(this);
        tpInfTitle.setText("TELETRANSPORTE INFINITO");
        tpInfTitle.setTextColor(Color.parseColor("#F4F7FF"));
        tpInfTitle.setTextSize(14f);
        tpInfTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tpInfCol.addView(tpInfTitle);

        tpInfiniteSubTv = new TextView(this);
        tpInfiniteSubTv.setTextColor(Color.parseColor("#9BA6C4"));
        tpInfiniteSubTv.setTextSize(12f);
        tpInfCol.addView(tpInfiniteSubTv);

        tpInfiniteBadge = new TextView(this);
        tpInfiniteBadge.setOnClickListener(v -> {
            if (prefs.getBoolean("hex_freeze", false) && prefs.getBoolean("hex_auto_stop_tp", true)) {
                Toast.makeText(MainActivity.this, "Desactiva CONGELAR o el bloqueo automático primero", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean next = !prefs.getBoolean("hex_tp_infinite", false);
            prefs.edit().putBoolean("hex_tp_infinite", next).apply();
            refreshModuleStatesUI();
            notifyServiceRefresh();
        });
        tpInfRow.addView(tpInfCol);
        tpInfRow.addView(tpInfiniteBadge);
        card5.addView(tpInfRow);

        LinearLayout tpInfSubBox = createSubContainer();
        addDashboardSlider(tpInfSubBox, "Paquetes por ráfaga", " paq", "hex_burst_packets", 1, 16, 1);
        addDashboardSlider(tpInfSubBox, "Retardo entre ráfagas", "ms", "hex_burst_delay_ms", 5, 25, 5);
        addModuleToggleRow(tpInfSubBox, "Búfer de recuperación", "Modo de retención de movimiento", "hex_recovery_buffer", false, null);
        card5.addView(tpInfSubBox);
        addDivider(card5);

        addModuleToggleRow(card5, "Modo en vivo (desarrollador)", "Solo para desarrolladores", "hex_live_dev_mode", false, null);
        root.addView(card5);

        // =====================================================================
        // SECCIÓN 06: MIRA PERSONALIZADA
        // =====================================================================
        LinearLayout card6 = createSectionCard("06  /  MIRA PERSONALIZADA", "Ajusta el estilo y la visibilidad de tu mira.");

        boolean crossEnabled = prefs.getBoolean("cross_enabled", true);
        LinearLayout showCrossRow = new LinearLayout(this);
        showCrossRow.setOrientation(LinearLayout.HORIZONTAL);
        showCrossRow.setGravity(Gravity.CENTER_VERTICAL);
        showCrossRow.setPadding(0, 0, 0, dp(10));

        LinearLayout showCrossCol = new LinearLayout(this);
        showCrossCol.setOrientation(LinearLayout.VERTICAL);
        showCrossCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView showCrossTitle = new TextView(this);
        showCrossTitle.setText("MOSTRAR MIRA");
        showCrossTitle.setTextColor(Color.parseColor("#F4F7FF"));
        showCrossTitle.setTextSize(14f);
        showCrossTitle.setTypeface(Typeface.DEFAULT_BOLD);
        showCrossCol.addView(showCrossTitle);

        final TextView showCrossSub = new TextView(this);
        showCrossSub.setText(crossEnabled ? "Visible" : "Oculto");
        showCrossSub.setTextColor(Color.parseColor("#9BA6C4"));
        showCrossSub.setTextSize(12f);
        showCrossCol.addView(showCrossSub);

        final TextView showCrossBadge = new TextView(this);
        styleSwitchBadge(showCrossBadge, crossEnabled, "Visible", "Oculto");
        showCrossBadge.setOnClickListener(v -> {
            boolean next = !prefs.getBoolean("cross_enabled", true);
            prefs.edit().putBoolean("cross_enabled", next).apply();
            showCrossSub.setText(next ? "Visible" : "Oculto");
            styleSwitchBadge(showCrossBadge, next, "Visible", "Oculto");
            notifyServiceRefresh();
        });
        showCrossRow.addView(showCrossCol);
        showCrossRow.addView(showCrossBadge);
        card6.addView(showCrossRow);

        String[] styleNames = {
            "•\nPunto", "＋\nCruz", "○\nCírculo", "⊙\nPunto y anillo",
            "⌖\nPrecisión", "◌\nDinámica", "⌜⌟\nMínima", "✣\nPersonalizada"
        };
        int currentStyle = prefs.getInt("cross_style", 0);
        for (int rowIdx = 0; rowIdx < 2; rowIdx++) {
            LinearLayout styleRow = new LinearLayout(this);
            styleRow.setOrientation(LinearLayout.HORIZONTAL);
            for (int colIdx = 0; colIdx < 4; colIdx++) {
                final int styleIdx = rowIdx * 4 + colIdx;
                TextView styleBtn = new TextView(this);
                styleBtn.setText(styleNames[styleIdx]);
                styleBtn.setGravity(Gravity.CENTER);
                styleBtn.setTextSize(11f);
                styleBtn.setTextColor(Color.parseColor(styleIdx == currentStyle ? "#00F5D4" : "#F4F7FF"));
                styleBtn.setBackgroundResource(styleIdx == currentStyle ? R.drawable.bg_neon_tab_selected : R.drawable.bg_neon_tab);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(56), 1f);
                slp.setMargins(dp(3), dp(3), dp(3), dp(3));
                styleBtn.setLayoutParams(slp);
                styleBtn.setOnClickListener(v -> {
                    prefs.edit().putInt("cross_style", styleIdx).apply();
                    updateCrossStyleHighlight(styleIdx);
                    notifyServiceRefresh();
                });
                crossStyleButtons.add(styleBtn);
                styleRow.addView(styleBtn);
            }
            card6.addView(styleRow);
        }

        TextView colorLabel = new TextView(this);
        colorLabel.setText("Color de la mira");
        colorLabel.setTextSize(13f);
        colorLabel.setTypeface(Typeface.DEFAULT_BOLD);
        colorLabel.setTextColor(Color.parseColor("#9BA6C4"));
        colorLabel.setPadding(0, dp(10), 0, dp(4));
        card6.addView(colorLabel);

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        colorRow.setPadding(0, dp(4), 0, dp(8));
        for (final int c : NEON_COLORS) {
            View swatch = new View(this);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setCornerRadius(dp(8));
            gd.setColor(c);
            gd.setStroke(dp(1.2f), Color.parseColor("#80FFFFFF"));
            swatch.setBackground(gd);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(26), dp(26));
            slp.rightMargin = dp(6);
            swatch.setOnClickListener(v -> {
                prefs.edit().putInt("cross_color", c).apply();
                if (crossColorPreviewBar != null) {
                    crossColorPreviewBar.setBackgroundColor(c);
                }
                notifyServiceRefresh();
            });
            colorRow.addView(swatch, slp);
        }
        card6.addView(colorRow);

        crossColorPreviewBar = new View(this);
        int savedColor = prefs.getInt("cross_color", NEON_COLORS[0]);
        crossColorPreviewBar.setBackgroundColor(savedColor);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(5));
        barLp.bottomMargin = dp(10);
        card6.addView(crossColorPreviewBar, barLp);

        addDashboardSlider(card6, "TAMAÑO DE LA MIRA", "dp", "cross_size", 16, 92, 42);
        addDashboardSlider(card6, "OPACIDAD DE LA MIRA", "%", "cross_alpha", 35, 100, 100);
        root.addView(card6);

        // Pie de versión
        TextView footerTv = new TextView(this);
        footerTv.setText("XITO MODS × HEX ESP V4");
        footerTv.setTextSize(11f);
        footerTv.setTextColor(Color.parseColor("#9BA6C4"));
        footerTv.setGravity(Gravity.CENTER);
        footerTv.setPadding(dp(12), dp(10), dp(12), dp(16));
        root.addView(footerTv);

        // ¡IMPORTANTE! Agregar root al ScrollView antes de mostrarlo en pantalla
        scroll.addView(root);
        setContentView(scroll);

        refreshModuleStatesUI();
        refreshStatusBadges();
        uiHandler.removeCallbacks(tickRunnable);
        uiHandler.post(tickRunnable);
    }

    private void updateCrossStyleHighlight(int selectedIdx) {
        for (int i = 0; i < crossStyleButtons.size(); i++) {
            TextView b = crossStyleButtons.get(i);
            b.setBackgroundResource(i == selectedIdx ? R.drawable.bg_neon_tab_selected : R.drawable.bg_neon_tab);
            b.setTextColor(Color.parseColor(i == selectedIdx ? "#00F5D4" : "#F4F7FF"));
        }
    }

    private interface ToggleCallback {
        void onToggled(boolean enabled);
    }

    private void addModuleToggleRow(LinearLayout parent, String title, final String defaultSub,
                                    final String prefKey, final boolean defVal, final ToggleCallback callback) {
        final SharedPreferences prefs = getSharedPreferences(FloatingPanelService.UI_PREFS, MODE_PRIVATE);
        boolean current = prefs.getBoolean(prefKey, defVal);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextColor(Color.parseColor("#F4F7FF"));
        t1.setTextSize(14f);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(t1);

        final TextView t2 = new TextView(this);
        t2.setText("Estado del módulo".equals(defaultSub)
                ? (current ? "Módulo activo en tiempo real" : "Estado del módulo: Inactivo")
                : defaultSub);
        t2.setTextColor(Color.parseColor(current ? "#00F5D4" : "#9BA6C4"));
        t2.setTextSize(12f);
        t2.setPadding(0, dp(2), 0, 0);
        col.addView(t2);

        final TextView badge = new TextView(this);
        styleSwitchBadge(badge, current, "ACTIVO", "OFF");
        badge.setOnClickListener(v -> {
            boolean next = !prefs.getBoolean(prefKey, defVal);
            prefs.edit().putBoolean(prefKey, next).apply();
            styleSwitchBadge(badge, next, "ACTIVO", "OFF");
            if ("Estado del módulo".equals(defaultSub)) {
                t2.setText(next ? "Módulo activo en tiempo real" : "Estado del módulo: Inactivo");
            }
            t2.setTextColor(Color.parseColor(next ? "#00F5D4" : "#9BA6C4"));
            if (callback != null) {
                callback.onToggled(next);
            }
            notifyServiceRefresh();
        });

        row.addView(col);
        row.addView(badge);
        parent.addView(row);
    }

    private void refreshModuleStatesUI() {
        SharedPreferences prefs = getSharedPreferences(FloatingPanelService.UI_PREFS, MODE_PRIVATE);
        boolean freeze = prefs.getBoolean("hex_freeze", false);
        boolean tpTarget = prefs.getBoolean("hex_tp_target", false);
        boolean tpInf = prefs.getBoolean("hex_tp_infinite", false);

        if (freezeBadge != null && freezeSubTv != null) {
            styleSwitchBadge(freezeBadge, freeze, "ACTIVO", "OFF");
            freezeSubTv.setText(freeze ? "Congelación de paquetes activa" : "Estado del módulo: Inactivo");
            freezeSubTv.setTextColor(Color.parseColor(freeze ? "#00F5D4" : "#9BA6C4"));
        }
        if (tpTargetBadge != null && tpTargetSubTv != null) {
            styleSwitchBadge(tpTargetBadge, tpTarget, "ACTIVO", "OFF");
            tpTargetSubTv.setText(tpTarget ? "Teletransporte al objetivo activo" : "Estado del módulo: Inactivo");
            tpTargetSubTv.setTextColor(Color.parseColor(tpTarget ? "#00F5D4" : "#9BA6C4"));
        }
        if (tpInfiniteBadge != null && tpInfiniteSubTv != null) {
            styleSwitchBadge(tpInfiniteBadge, tpInf, "ACTIVO", "OFF");
            tpInfiniteSubTv.setText(tpInf ? "Ráfaga de teletransporte infinito activa" : "Estado del módulo: Inactivo");
            tpInfiniteSubTv.setTextColor(Color.parseColor(tpInf ? "#00F5D4" : "#9BA6C4"));
        }
    }

    private LinearLayout createSubContainer() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor("#12FFFFFF"));
        gd.setCornerRadius(dp(12));
        gd.setStroke(dp(1), Color.parseColor("#2600F5D4"));
        box.setBackground(gd);
        int p = dp(12);
        box.setPadding(p, dp(8), p, dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(6);
        box.setLayoutParams(lp);
        return box;
    }

    private void addDivider(LinearLayout parent) {
        View div = new View(this);
        div.setBackgroundColor(Color.parseColor("#14FFFFFF"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(4);
        parent.addView(div, lp);
    }

    private View createStatusDot(boolean active) {
        View dot = new View(this);
        styleStatusDot(dot, active);
        return dot;
    }

    private void styleStatusDot(View dot, boolean active) {
        if (dot == null) return;
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(Color.parseColor(active ? "#00FF87" : "#FF2A85"));
        dot.setBackground(gd);
    }

    private LinearLayout createPermissionRow(String label, View dotView, TextView badgeView, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));

        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(7), dp(7));
        dotLp.rightMargin = dp(10);
        row.addView(dotView, dotLp);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.parseColor("#F4F7FF"));
        tv.setTextSize(13f);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tv);

        badgeView.setOnClickListener(listener);
        row.addView(badgeView);
        return row;
    }

    private void styleSwitchBadge(TextView badge, boolean active, String onText, String offText) {
        if (badge == null) return;
        badge.setText(active ? onText : offText);
        badge.setTextSize(11f);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(14), dp(7), dp(14), dp(7));
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(999));
        if (active) {
            gd.setColor(Color.parseColor("#2E00F5D4"));
            gd.setStroke(dp(1.2f), Color.parseColor("#00F5D4"));
            badge.setTextColor(Color.parseColor("#00F5D4"));
        } else {
            gd.setColor(Color.parseColor("#1A9BA6C4"));
            gd.setStroke(dp(1f), Color.parseColor("#409BA6C4"));
            badge.setTextColor(Color.parseColor("#9BA6C4"));
        }
        badge.setBackground(gd);
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
        tvVal.setTypeface(Typeface.DEFAULT_BOLD);

        row.addView(tvLabel);
        row.addView(tvVal);
        parent.addView(row);

        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, Math.min(max - min, cur - min)));
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

    private void openAppPickerDialog() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        final List<String> displayNames = new ArrayList<>();
        final List<String> pkgNames = new ArrayList<>();

        displayNames.add("FF MAX (com.dts.freefiremax)");
        pkgNames.add("com.dts.freefiremax");
        displayNames.add("FF NORMAL (com.dts.freefireth)");
        pkgNames.add("com.dts.freefireth");

        List<String[]> userApps = new ArrayList<>();
        for (ApplicationInfo app : apps) {
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                String label = String.valueOf(pm.getApplicationLabel(app));
                if (!"com.dts.freefiremax".equals(app.packageName)
                        && !"com.dts.freefireth".equals(app.packageName)
                        && !getPackageName().equals(app.packageName)) {
                    userApps.add(new String[]{label + " (" + app.packageName + ")", app.packageName});
                }
            }
        }
        Collections.sort(userApps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        for (String[] item : userApps) {
            displayNames.add(item[0]);
            pkgNames.add(item[1]);
        }

        CharSequence[] items = displayNames.toArray(new CharSequence[0]);
        new AlertDialog.Builder(this)
                .setTitle("SELECCIONAR APLICACIÓN")
                .setItems(items, (dialog, which) -> selectAppProfile(displayNames.get(which), pkgNames.get(which)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void selectAppProfile(String profileName, String pkgName) {
        getSharedPreferences(FloatingPanelService.UI_PREFS, MODE_PRIVATE)
                .edit()
                .putString("selected_app", profileName)
                .putString("selected_app_pkg", pkgName)
                .apply();
        if (selectedAppTv != null) {
            selectedAppTv.setText(profileName);
        }
        styleStatusDot(appStatusDot, true);
        notifyServiceRefresh();
        Toast.makeText(this, "Seleccionado: " + profileName, Toast.LENGTH_SHORT).show();
    }

    private boolean isVpnPermissionGranted() {
        try {
            return VpnService.prepare(this) == null;
        } catch (Exception e) {
            return getSharedPreferences(FloatingPanelService.UI_PREFS, MODE_PRIVATE)
                    .getBoolean("vpn_perm_granted", false);
        }
    }

    private void requestVpnPermission(boolean autoStartAfter) {
        try {
            Intent intent = VpnService.prepare(this);
            if (intent != null) {
                pendingStartAfterVpnGrant = autoStartAfter;
                startActivityForResult(intent, REQ_VPN_PERMISSION);
            } else {
                getSharedPreferences(FloatingPanelService.UI_PREFS, MODE_PRIVATE)
                        .edit().putBoolean("vpn_perm_granted", true).apply();
                if (autoStartAfter) {
                    startProxyAndOverlayServices();
                } else {
                    Toast.makeText(this, "Servicio VPN: Permitido", Toast.LENGTH_SHORT).show();
                }
                refreshStatusBadges();
            }
        } catch (Exception e) {
            if (autoStartAfter) {
                startProxyAndOverlayServices();
            }
        }
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
            Toast.makeText(this, "Mostrar sobre otras aplicaciones: Permitido", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isNotificationEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            return nm == null || nm.areNotificationsEnabled();
        }
        return true;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF_PERMISSION);
        } else if (!isNotificationEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        } else {
            Toast.makeText(this, "Notificaciones del sistema: Permitido", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isStorageAllowed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestStoragePermission() {
        if (!isStorageAllowed() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQ_STORAGE_PERMISSION);
        } else {
            Toast.makeText(this, "Almacenamiento del dispositivo: Permitido", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleProxyAndFloatingService() {
        if (!canDrawOverlays()) {
            Toast.makeText(this, "Concede primero el permiso 'Mostrar sobre otras aplicaciones'", Toast.LENGTH_SHORT).show();
            requestOverlayPermission();
            return;
        }
        if (FloatingPanelService.isRunning || XitoProxyVpnService.isConnected) {
            stopProxyAndOverlayServices();
        } else {
            requestVpnPermission(true);
        }
    }

    private void startProxyAndOverlayServices() {
        try {
            Intent vpnIntent = new Intent(this, XitoProxyVpnService.class);
            vpnIntent.setAction(XitoProxyVpnService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(vpnIntent);
            } else {
                startService(vpnIntent);
            }
        } catch (Exception ignored) {
        }

        try {
            Intent floatIntent = new Intent(this, FloatingPanelService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(floatIntent);
            } else {
                startService(floatIntent);
            }
        } catch (Exception ignored) {
        }
        uiHandler.postDelayed(this::refreshStatusBadges, 250L);
    }

    private void stopProxyAndOverlayServices() {
        stopService(new Intent(this, FloatingPanelService.class));
        Intent stopVpn = new Intent(this, XitoProxyVpnService.class);
        stopVpn.setAction(XitoProxyVpnService.ACTION_STOP);
        stopService(stopVpn);
        uiHandler.postDelayed(this::refreshStatusBadges, 250L);
    }

    private void notifyServiceRefresh() {
        if (FloatingPanelService.isRunning) {
            Intent i = new Intent(this, FloatingPanelService.class);
            i.setAction(FloatingPanelService.ACTION_REFRESH);
            startService(i);
        }
        if (XitoProxyVpnService.isConnected) {
            Intent vpnSync = new Intent(this, XitoProxyVpnService.class);
            vpnSync.setAction(XitoProxyVpnService.ACTION_SYNC);
            startService(vpnSync);
        }
    }

    private void refreshStatusBadges() {
        SharedPreferences prefs = getSharedPreferences(FloatingPanelService.UI_PREFS, MODE_PRIVATE);

        boolean vpnOk = isVpnPermissionGranted();
        styleStatusDot(vpnPermDot, vpnOk);
        styleSwitchBadge(vpnPermBadge, vpnOk, "Permitido", "No permitido");

        boolean overlayOk = canDrawOverlays();
        styleStatusDot(overlayPermDot, overlayOk);
        styleSwitchBadge(overlayPermBadge, overlayOk, "Permitido", "No permitido");

        boolean notifOk = isNotificationEnabled();
        styleStatusDot(notifPermDot, notifOk);
        styleSwitchBadge(notifPermBadge, notifOk, "Permitido", "No permitido");

        boolean storageOk = isStorageAllowed();
        styleStatusDot(storagePermDot, storageOk);
        styleSwitchBadge(storagePermBadge, storageOk, "Permitido", "No permitido");

        if (hideCodeSubTv != null && hideCodeBadge != null) {
            boolean hideCodeOn = prefs.getBoolean("hide_match_code", false);
            boolean barLocked = prefs.getBoolean("match_bar_locked", false);
            styleSwitchBadge(hideCodeBadge, hideCodeOn, "Visible", "Oculto");
            hideCodeSubTv.setText(barLocked
                    ? "Posición bloqueada (mantén 2s sobre la barra para desbloquear)"
                    : "Arrastra libremente; mantén pulsado 2 segundos para bloquear la posición");
        }

        refreshModuleStatesUI();

        if (engineStatusTv != null && startStopButton != null) {
            boolean active = FloatingPanelService.isRunning || XitoProxyVpnService.isConnected;
            styleStatusDot(engineStatusDot, active);
            engineStatusTv.setText(active ? "CONECTADO" : "DESCONECTADO");
            engineStatusTv.setTextColor(Color.parseColor(active ? "#00FF87" : "#FF2A85"));
            if (engineSubStatusTv != null) {
                String app = prefs.getString("selected_app", "FF MAX");
                engineSubStatusTv.setText(active
                        ? "Proxy VPN y botón flotante activos en " + app
                        : "Revisa los permisos y selecciona una aplicación.");
            }
            startStopButton.setText(active ? "DETENER PROXY Y BOTÓN FLOTANTE" : "INICIAR PROXY Y BOTÓN FLOTANTE");
        }
    }

    private void showJoinPopup() {
        try {
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
        } catch (Exception ignored) {
        }
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Instala un navegador para abrir el enlace.", Toast.LENGTH_SHORT).show();
        }
    }
}
