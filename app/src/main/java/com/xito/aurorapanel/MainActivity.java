package com.xito.aurorapanel;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {

    private static final int SHIZUKU_PERMISSION_REQUEST_CODE = 1001;

    public static final String PKG_FF_NORMAL = "com.dts.freefireth";
    public static final String PKG_FF_MAX = "com.dts.freefiremax";

    // Archivos exclusivos para Free Fire Normal (com.dts.freefireth)
    public static final String ASSET_ORI_NORMAL = "ORIFILEINFO_NORMAL";
    public static final String ASSET_BYPASS_NORMAL = "BYPASS_NORMAL";
    public static final String ASSET_RGB_NORMAL = "RGB_NORMAL";

    // Archivos exclusivos para Free Fire MAX (com.dts.freefiremax)
    public static final String ASSET_ORI_MAX = "ORIFILEINFO_MAX";
    public static final String ASSET_BYPASS_MAX = "BYPASS_MAX";
    public static final String ASSET_RGB_MAX = "RGB_MAX";

    private TextView tvLicenseBadge;
    private TextView tvShizukuStatus;
    private TextView tvActiveFilesInfo;
    private TextView tvConsoleLog;
    private RadioGroup rgGameTarget;
    private RadioButton rbFreeFireNormal;
    private RadioButton rbFreeFireMax;
    private CheckBox cbAutoReplaceInstalledHash;

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            (requestCode, grantResult) -> {
                if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                    runOnUiThread(this::refreshShizukuStatus);
                }
            };

    private final Shizuku.OnBinderReceivedListener binderReceivedListener =
            () -> runOnUiThread(this::refreshShizukuStatus);

    private final Shizuku.OnBinderDeadListener binderDeadListener =
            () -> runOnUiThread(this::refreshShizukuStatus);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvLicenseBadge = findViewById(R.id.tvLicenseBadge);
        tvShizukuStatus = findViewById(R.id.tvShizukuStatus);
        tvActiveFilesInfo = findViewById(R.id.tvActiveFilesInfo);
        tvConsoleLog = findViewById(R.id.tvConsoleLog);
        rgGameTarget = findViewById(R.id.rgGameTarget);
        rbFreeFireNormal = findViewById(R.id.rbFreeFireNormal);
        rbFreeFireMax = findViewById(R.id.rbFreeFireMax);
        cbAutoReplaceInstalledHash = findViewById(R.id.cbAutoReplaceInstalledHash);

        Button btnRequestShizuku = findViewById(R.id.btnRequestShizuku);
        Button btnLogoutKey = findViewById(R.id.btnLogoutKey);
        Button btnStep1Prepare = findViewById(R.id.btnStep1Prepare);
        Button btnStep2ActivateLobby = findViewById(R.id.btnStep2ActivateLobby);
        Button btnDirectInjectOnly = findViewById(R.id.btnDirectInjectOnly);
        Button btnRestoreClean = findViewById(R.id.btnRestoreClean);

        String savedKey = LicenseManager.getSavedKey(this);
        if (savedKey == null || savedKey.isEmpty()) {
            redirectToLogin();
            return;
        }
        tvLicenseBadge.setText("Key Activa: " + savedKey);

        Shizuku.addRequestPermissionResultListener(permissionResultListener);
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);

        rgGameTarget.setOnCheckedChangeListener((group, checkedId) -> {
            updateActiveFilesBanner();
            if (checkedId == R.id.rbFreeFireMax) {
                appendLog("Cambiado a Free Fire MAX: usando ORIFILEINFO_MAX + BYPASS_MAX + RGB_MAX.");
            } else {
                appendLog("Cambiado a Free Fire Normal: usando ORIFILEINFO_NORMAL + BYPASS_NORMAL + RGB_NORMAL.");
            }
        });

        btnRequestShizuku.setOnClickListener(v -> checkOrRequestShizukuPermission());

        btnLogoutKey.setOnClickListener(v -> {
            LicenseManager.clearSession(this);
            redirectToLogin();
        });

        btnStep1Prepare.setOnClickListener(v -> runStep1PrepareAndLaunch());
        btnStep2ActivateLobby.setOnClickListener(v -> runStep2ActivateBypassAndRgb());
        btnDirectInjectOnly.setOnClickListener(v -> runDirectShaderReplacement());
        btnRestoreClean.setOnClickListener(v -> runRestoreCleanState());

        updateActiveFilesBanner();
        refreshShizukuStatus();
        revalidateKeyInBackground();
    }

    @Override
    protected void onResume() {
        super.onResume();
        revalidateKeyInBackground();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
    }

    private void revalidateKeyInBackground() {
        final String savedKey = LicenseManager.getSavedKey(this);
        if (savedKey == null || savedKey.isEmpty()) {
            redirectToLogin();
            return;
        }
        new Thread(() -> {
            LicenseManager.ValidationResult res = LicenseManager.validateAndActivateKeySync(MainActivity.this, savedKey);
            runOnUiThread(() -> {
                if (!res.valid) {
                    Toast.makeText(MainActivity.this, res.message, Toast.LENGTH_LONG).show();
                    LicenseManager.clearSession(MainActivity.this);
                    redirectToLogin();
                } else {
                    if (res.isPermanent) {
                        tvLicenseBadge.setText("Key: " + res.keyName + " (Permanente)");
                    } else {
                        tvLicenseBadge.setText("Key: " + res.keyName + " (Expira en " + LicenseManager.formatRemainingTime(res.remainingMs) + ")");
                    }
                }
            });
        }).start();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    private boolean isFreeFireMaxSelected() {
        return rgGameTarget.getCheckedRadioButtonId() == R.id.rbFreeFireMax;
    }

    private String getSelectedGamePackage() {
        return isFreeFireMaxSelected() ? PKG_FF_MAX : PKG_FF_NORMAL;
    }

    private String getSelectedOriAsset() {
        return isFreeFireMaxSelected() ? ASSET_ORI_MAX : ASSET_ORI_NORMAL;
    }

    private String getSelectedBypassAsset() {
        return isFreeFireMaxSelected() ? ASSET_BYPASS_MAX : ASSET_BYPASS_NORMAL;
    }

    private String getSelectedRgbAsset() {
        return isFreeFireMaxSelected() ? ASSET_RGB_MAX : ASSET_RGB_NORMAL;
    }

    private void updateActiveFilesBanner() {
        if (isFreeFireMaxSelected()) {
            tvActiveFilesInfo.setText(
                    "Modo actual: FREE FIRE MAX (com.dts.freefiremax)\n" +
                    "• ORIFILEINFO_MAX (132 KB)\n" +
                    "• BYPASS_MAX (132 KB)\n" +
                    "• RGB_MAX / shaders.XITOMODS~FREE~3D (1.03 MB)"
            );
            tvActiveFilesInfo.setTextColor(0xFF00E676);
        } else {
            tvActiveFilesInfo.setText(
                    "Modo actual: FREE FIRE NORMAL (com.dts.freefireth)\n" +
                    "• ORIFILEINFO_NORMAL (134 KB)\n" +
                    "• BYPASS_NORMAL (134 KB)\n" +
                    "• RGB_NORMAL / shaders.XITOMODS~FREE~3D (911 KB)"
            );
            tvActiveFilesInfo.setTextColor(0xFF69F0AE);
        }
    }

    private boolean isShizukuReady() {
        try {
            if (!Shizuku.pingBinder()) {
                return false;
            }
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    private void refreshShizukuStatus() {
        try {
            if (!Shizuku.pingBinder()) {
                tvShizukuStatus.setText("Estado de Shizuku: NO ACTIVO (Abre la app Shizuku e inicia el servicio)");
                tvShizukuStatus.setTextColor(0xFFFF595E);
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                tvShizukuStatus.setText("Estado de Shizuku: CONECTADO Y AUTORIZADO (UID " + Shizuku.getUid() + ")");
                tvShizukuStatus.setTextColor(0xFF00E676);
            } else {
                tvShizukuStatus.setText("Estado de Shizuku: Falta conceder permiso (Toca el botón de abajo)");
                tvShizukuStatus.setTextColor(0xFFFFD166);
            }
        } catch (Throwable t) {
            tvShizukuStatus.setText("Estado de Shizuku: Error (" + t.getMessage() + ")");
            tvShizukuStatus.setTextColor(0xFFFF595E);
        }
    }

    private void checkOrRequestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                Intent launchShizuku = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                if (launchShizuku != null) {
                    startActivity(launchShizuku);
                } else {
                    Toast.makeText(this, "Instala e inicia la app Shizuku primero.", Toast.LENGTH_LONG).show();
                }
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
            } else {
                refreshShizukuStatus();
                appendLog("Shizuku ya está conectado y autorizado.");
            }
        } catch (Throwable t) {
            appendLog("Error solicitando permiso Shizuku: " + t.getMessage());
        }
    }

    private File extractAssetToSharedStage(String assetName) throws Exception {
        File stageDir = new File(getExternalCacheDir(), "stage_xito");
        if (!stageDir.exists()) {
            stageDir.mkdirs();
        }
        File outFile = new File(stageDir, assetName);
        try (InputStream is = getAssets().open(assetName);
             FileOutputStream fos = new FileOutputStream(outFile)) {
            byte[] buf = new byte[65536];
            int r;
            while ((r = is.read(buf)) != -1) {
                fos.write(buf, 0, r);
            }
            fos.flush();
        }
        return outFile;
    }

    private String getOptionalAndroidDir(String pkg) {
        return "/storage/emulated/0/Android/data/" + pkg + "/files/contentcache/Optional/android";
    }

    private String getBundlesDir(String pkg) {
        return getOptionalAndroidDir(pkg) + "/gameassetbundles";
    }

    private void runStep1PrepareAndLaunch() {
        if (!isShizukuReady()) {
            checkOrRequestShizukuPermission();
            return;
        }
        final String pkg = getSelectedGamePackage();
        final String oriAsset = getSelectedOriAsset();
        final String rgbAsset = getSelectedRgbAsset();
        appendLog("Preparando PASO 1 para " + pkg + " usando " + oriAsset + " + " + rgbAsset + "...");

        new Thread(() -> {
            try {
                File oriFile = extractAssetToSharedStage(oriAsset);
                File shaderFile = extractAssetToSharedStage(rgbAsset);

                String optDir = getOptionalAndroidDir(pkg);
                String bundlesDir = getBundlesDir(pkg);

                String script =
                        "mkdir -p '" + bundlesDir + "'\n" +
                        "if [ -f '" + optDir + "/fileinfo' ] && [ ! -f '" + optDir + "/fileinfo.orig.bak' ]; then\n" +
                        "  cp -f '" + optDir + "/fileinfo' '" + optDir + "/fileinfo.orig.bak'\n" +
                        "fi\n" +
                        "cp -f '" + oriFile.getAbsolutePath() + "' '" + optDir + "/fileinfo'\n" +
                        "cp -f '" + shaderFile.getAbsolutePath() + "' '" + bundlesDir + "/shaders.Jtvvip~j='\n" +
                        "cp -f '" + shaderFile.getAbsolutePath() + "' '" + bundlesDir + "/shaders.XITOMODS~FREE~3D'\n" +
                        "echo 'PASO 1 completado en " + pkg + " (" + oriAsset + " + " + rgbAsset + ")'";

                ShizukuShellHelper.ShellResult res = ShizukuShellHelper.execShizukuCommand(script);
                runOnUiThread(() -> {
                    appendLog(res.output);
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                    } else {
                        Toast.makeText(this, "No se encontró instalado " + pkg, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> appendLog("Error en PASO 1: " + e.getMessage()));
            }
        }).start();
    }

    private void runStep2ActivateBypassAndRgb() {
        if (!isShizukuReady()) {
            checkOrRequestShizukuPermission();
            return;
        }
        final String pkg = getSelectedGamePackage();
        final String bypassAsset = getSelectedBypassAsset();
        final String rgbAsset = getSelectedRgbAsset();
        final boolean replaceHash = cbAutoReplaceInstalledHash.isChecked();
        appendLog("Activando PASO 2 (" + bypassAsset + " + " + rgbAsset + ") en " + pkg + "...");

        new Thread(() -> {
            try {
                File bypassFile = extractAssetToSharedStage(bypassAsset);
                File shaderFile = extractAssetToSharedStage(rgbAsset);

                String optDir = getOptionalAndroidDir(pkg);
                String bundlesDir = getBundlesDir(pkg);

                StringBuilder sb = new StringBuilder();
                sb.append("mkdir -p '").append(bundlesDir).append("'\n");
                sb.append("cp -f '").append(bypassFile.getAbsolutePath()).append("' '").append(optDir).append("/fileinfo'\n");
                sb.append("cp -f '").append(shaderFile.getAbsolutePath()).append("' '").append(bundlesDir).append("/shaders.Jtvvip~j='\n");
                sb.append("cp -f '").append(shaderFile.getAbsolutePath()).append("' '").append(bundlesDir).append("/shaders.XITOMODS~FREE~3D'\n");

                if (replaceHash) {
                    sb.append("for f in '").append(bundlesDir).append("'/shaders.*; do\n");
                    sb.append("  if [ -f \"$f\" ] && [ \"$f\" != '").append(bundlesDir).append("/shaders.Jtvvip~j=' ] && [ \"$f\" != '").append(bundlesDir).append("/shaders.XITOMODS~FREE~3D' ]; then\n");
                    sb.append("    case \"$f\" in *.bak) continue ;; esac\n");
                    sb.append("    if [ ! -f \"$f.bak\" ]; then cp -f \"$f\" \"$f.bak\"; fi\n");
                    sb.append("    cp -f '").append(shaderFile.getAbsolutePath()).append("' \"$f\"\n");
                    sb.append("    echo \"Reemplazado con ").append(rgbAsset).append(": $(basename \"$f\")\"\n");
                    sb.append("  fi\n");
                    sb.append("done\n");
                }
                sb.append("echo '").append(bypassAsset).append(" + ").append(rgbAsset).append(" ACTIVADO con éxito en ").append(pkg).append("'");

                ShizukuShellHelper.ShellResult res = ShizukuShellHelper.execShizukuCommand(sb.toString());
                runOnUiThread(() -> {
                    appendLog(res.output);
                    Toast.makeText(this, "¡" + rgbAsset + " + " + bypassAsset + " Activado!", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> appendLog("Error en PASO 2: " + e.getMessage()));
            }
        }).start();
    }

    private void runDirectShaderReplacement() {
        if (!isShizukuReady()) {
            checkOrRequestShizukuPermission();
            return;
        }
        final String pkg = getSelectedGamePackage();
        final String rgbAsset = getSelectedRgbAsset();
        appendLog("Inyectando directamente " + rgbAsset + " en " + pkg + "...");

        new Thread(() -> {
            try {
                File shaderFile = extractAssetToSharedStage(rgbAsset);
                String bundlesDir = getBundlesDir(pkg);

                String script =
                        "mkdir -p '" + bundlesDir + "'\n" +
                        "count=0\n" +
                        "for f in '" + bundlesDir + "'/shaders.*; do\n" +
                        "  if [ -f \"$f\" ]; then\n" +
                        "    case \"$f\" in *.bak) continue ;; esac\n" +
                        "    if [ ! -f \"$f.bak\" ]; then cp -f \"$f\" \"$f.bak\"; fi\n" +
                        "    cp -f '" + shaderFile.getAbsolutePath() + "' \"$f\"\n" +
                        "    count=$((count + 1))\n" +
                        "    echo \"Shader " + rgbAsset + " aplicado en: $(basename \"$f\")\"\n" +
                        "  fi\n" +
                        "done\n" +
                        "if [ $count -eq 0 ]; then\n" +
                        "  cp -f '" + shaderFile.getAbsolutePath() + "' '" + bundlesDir + "/shaders.XITOMODS~FREE~3D'\n" +
                        "  echo 'No había shaders previos; copiado como shaders.XITOMODS~FREE~3D'\n" +
                        "fi";

                ShizukuShellHelper.ShellResult res = ShizukuShellHelper.execShizukuCommand(script);
                runOnUiThread(() -> {
                    appendLog(res.output);
                    Toast.makeText(this, rgbAsset + " inyectado en " + pkg, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> appendLog("Error en Inyección Directa: " + e.getMessage()));
            }
        }).start();
    }

    private void runRestoreCleanState() {
        if (!isShizukuReady()) {
            checkOrRequestShizukuPermission();
            return;
        }
        final String pkg = getSelectedGamePackage();
        final String oriAsset = getSelectedOriAsset();
        appendLog("Restaurando archivos originales en " + pkg + "...");

        new Thread(() -> {
            try {
                File oriFile = extractAssetToSharedStage(oriAsset);
                String optDir = getOptionalAndroidDir(pkg);
                String bundlesDir = getBundlesDir(pkg);

                String script =
                        "if [ -f '" + optDir + "/fileinfo.orig.bak' ]; then\n" +
                        "  cp -f '" + optDir + "/fileinfo.orig.bak' '" + optDir + "/fileinfo'\n" +
                        "else\n" +
                        "  cp -f '" + oriFile.getAbsolutePath() + "' '" + optDir + "/fileinfo'\n" +
                        "fi\n" +
                        "rm -f '" + bundlesDir + "/shaders.Jtvvip~j='\n" +
                        "rm -f '" + bundlesDir + "/shaders.XITOMODS~FREE~3D'\n" +
                        "for bak in '" + bundlesDir + "'/*.bak; do\n" +
                        "  if [ -f \"$bak\" ]; then\n" +
                        "    orig=\"${bak%.bak}\"\n" +
                        "    mv -f \"$bak\" \"$orig\"\n" +
                        "    echo \"Restaurado: $(basename \"$orig\")\"\n" +
                        "  fi\n" +
                        "done\n" +
                        "echo 'Restauración completada con éxito en " + pkg + "'";

                ShizukuShellHelper.ShellResult res = ShizukuShellHelper.execShizukuCommand(script);
                runOnUiThread(() -> {
                    appendLog(res.output);
                    Toast.makeText(this, "Archivos originales restaurados en " + pkg, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> appendLog("Error restaurando: " + e.getMessage()));
            }
        }).start();
    }

    private void appendLog(String msg) {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        tvConsoleLog.setText("[" + ts + "] " + msg + "\n" + tvConsoleLog.getText());
    }
}
