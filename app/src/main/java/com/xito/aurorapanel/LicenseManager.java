package com.xito.aurorapanel;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class LicenseManager {

    private static final String PREFS_NAME = "xito_rgb_license_prefs";

    // Las 3 Keys configuradas
    public static final String KEY_OWNER = "XITOMODS-OWNER";
    public static final String KEY_TESTER_1 = "XITO-MODS-TESTER1";
    public static final String KEY_TESTER_2 = "XITO-MODS-TESTER2";

    // 24 horas en milisegundos
    public static final long TESTER_DURATION_MS = 24L * 60L * 60L * 1000L;

    private static final String PREF_ACTIVE_KEY = "active_key";
    private static final String PREF_FIRST_ACT_PREFIX = "first_act_time_";
    private static final String PREF_BOUND_DEV_PREFIX = "bound_device_";

    public static class ValidationResult {
        public final boolean valid;
        public final String message;
        public final String keyName;
        public final boolean isPermanent;
        public final long remainingMs;

        public ValidationResult(boolean valid, String message, String keyName, boolean isPermanent, long remainingMs) {
            this.valid = valid;
            this.message = message;
            this.keyName = keyName;
            this.isPermanent = isPermanent;
            this.remainingMs = remainingMs;
        }
    }

    public static String getDeviceId(Context context) {
        try {
            String id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (id != null && !id.trim().isEmpty()) {
                return id.trim().toUpperCase(Locale.US);
            }
        } catch (Throwable ignored) {
        }
        return "UNKNOWN-DEVICE";
    }

    public static ValidationResult validateAndActivateKey(Context context, String rawInputKey) {
        if (rawInputKey == null) {
            return new ValidationResult(false, "Por favor, coloca tu key aquí.", "", false, 0);
        }
        String key = rawInputKey.trim();
        if (key.isEmpty()) {
            return new ValidationResult(false, "Por favor, coloca tu key aquí.", "", false, 0);
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String deviceId = getDeviceId(context);

        // 1. Key Owner: Permanente y Multidispositivo
        if (KEY_OWNER.equals(key)) {
            prefs.edit().putString(PREF_ACTIVE_KEY, KEY_OWNER).apply();
            return new ValidationResult(
                    true,
                    "Acceso OWNER concedido (Permanente • Multidispositivo)",
                    KEY_OWNER,
                    true,
                    Long.MAX_VALUE
            );
        }

        // 2. Keys Tester 1 y Tester 2: 24 Horas y 1 solo dispositivo
        if (KEY_TESTER_1.equals(key) || KEY_TESTER_2.equals(key)) {
            String boundDeviceKey = PREF_BOUND_DEV_PREFIX + key;
            String firstActKey = PREF_FIRST_ACT_PREFIX + key;

            String boundDevice = prefs.getString(boundDeviceKey, null);
            long firstActivationTime = prefs.getLong(firstActKey, 0L);
            long now = System.currentTimeMillis();

            // También verificamos respaldo en Shizuku (/data/local/tmp) si está activo para evitar reinicio de 24h al borrar datos
            long shizukuFirstAct = readShizukuTimestamp(key);
            String shizukuBoundDev = readShizukuBoundDevice(key);

            if (boundDevice == null && shizukuBoundDev != null) {
                boundDevice = shizukuBoundDev;
            }
            if (firstActivationTime == 0L && shizukuFirstAct > 0L) {
                firstActivationTime = shizukuFirstAct;
            }

            if (boundDevice != null && !boundDevice.equals(deviceId)) {
                return new ValidationResult(
                        false,
                        "Esta key (" + key + ") ya está vinculada a otro dispositivo.",
                        key,
                        false,
                        0
                );
            }

            if (firstActivationTime == 0L) {
                firstActivationTime = now;
                boundDevice = deviceId;
                prefs.edit()
                        .putLong(firstActKey, firstActivationTime)
                        .putString(boundDeviceKey, boundDevice)
                        .apply();
                writeShizukuActivation(key, firstActivationTime, boundDevice);
            } else {
                // Asegurar que quede guardado localmente
                prefs.edit()
                        .putLong(firstActKey, firstActivationTime)
                        .putString(boundDeviceKey, boundDevice)
                        .apply();
            }

            long elapsed = now - firstActivationTime;
            if (elapsed >= TESTER_DURATION_MS) {
                return new ValidationResult(
                        false,
                        "La key " + key + " ha vencido (límite de 24 horas alcanzado).",
                        key,
                        false,
                        0
                );
            }

            long remaining = TESTER_DURATION_MS - elapsed;
            prefs.edit().putString(PREF_ACTIVE_KEY, key).apply();
            return new ValidationResult(
                    true,
                    "Acceso TESTER activo (" + formatRemainingTime(remaining) + " restantes • 1 Dispositivo)",
                    key,
                    false,
                    remaining
            );
        }

        return new ValidationResult(
                false,
                "Key inválida. Verifica tu key e inténtalo de nuevo.",
                key,
                false,
                0
        );
    }

    public static ValidationResult checkCurrentSession(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedKey = prefs.getString(PREF_ACTIVE_KEY, null);
        if (savedKey == null || savedKey.isEmpty()) {
            return new ValidationResult(false, "Sin sesión activa", "", false, 0);
        }
        return validateAndActivateKey(context, savedKey);
    }

    public static void clearSession(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(PREF_ACTIVE_KEY).apply();
    }

    public static String formatRemainingTime(long remainingMs) {
        if (remainingMs <= 0) return "0h 00m";
        long hours = TimeUnit.MILLISECONDS.toHours(remainingMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs) % 60;
        return String.format(Locale.US, "%dh %02dm", hours, minutes);
    }

    private static String getSafeKeyFileTag(String key) {
        return key.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static long readShizukuTimestamp(String key) {
        try {
            String tag = getSafeKeyFileTag(key);
            ShizukuShellHelper.ShellResult res = ShizukuShellHelper.execShizukuCommand(
                    "if [ -f /data/local/tmp/.xito_lic_" + tag + " ]; then head -n 1 /data/local/tmp/.xito_lic_" + tag + "; fi"
            );
            if (res.exitCode == 0 && res.output != null && !res.output.isEmpty()) {
                return Long.parseLong(res.output.split("\\s+")[0].trim());
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    private static String readShizukuBoundDevice(String key) {
        try {
            String tag = getSafeKeyFileTag(key);
            ShizukuShellHelper.ShellResult res = ShizukuShellHelper.execShizukuCommand(
                    "if [ -f /data/local/tmp/.xito_lic_" + tag + " ]; then tail -n 1 /data/local/tmp/.xito_lic_" + tag + "; fi"
            );
            if (res.exitCode == 0 && res.output != null && !res.output.isEmpty()) {
                return res.output.trim();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void writeShizukuActivation(String key, long timestamp, String deviceId) {
        try {
            String tag = getSafeKeyFileTag(key);
            ShizukuShellHelper.execShizukuCommand(
                    "printf '%d\n%s\n' " + timestamp + " '" + deviceId + "' > /data/local/tmp/.xito_lic_" + tag
            );
        } catch (Throwable ignored) {
        }
    }
}
