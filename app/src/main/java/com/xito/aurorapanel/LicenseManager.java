package com.xito.aurorapanel;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class LicenseManager {

    private static final String PREFS_NAME = "xito_rgb_license_prefs";

    // URL fija de tu servidor Firebase Realtime Database
    public static final String FIREBASE_DB_URL = "https://xito-mods-default-rtdb.firebaseio.com";

    // Keys base integradas
    public static final String KEY_OWNER = "XITOMODS-OWNER";
    public static final String KEY_TESTER_1 = "XITO-MODS-TESTER1";
    public static final String KEY_TESTER_2 = "XITO-MODS-TESTER2";

    public static final long ONE_HOUR_MS = 60L * 60L * 1000L;
    public static final long TESTER_DURATION_MS = 24L * ONE_HOUR_MS;

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

    /**
     * Valida la key ingresada contra https://xito-mods-default-rtdb.firebaseio.com/keys/<KEY>.json
     * También mantiene soporte para XITOMODS-OWNER, XITO-MODS-TESTER1 y XITO-MODS-TESTER2.
     */
    public static ValidationResult validateAndActivateKeySync(Context context, String rawInputKey) {
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
                    "Acceso OWNER activo (Permanente • Multidispositivo)",
                    KEY_OWNER,
                    true,
                    Long.MAX_VALUE
            );
        }

        // 2. Validar en línea en https://xito-mods-default-rtdb.firebaseio.com/keys/<KEY>.json
        try {
            ValidationResult onlineRes = checkAndBindFirebaseKey(key, deviceId);
            if (onlineRes != null) {
                if (onlineRes.valid) {
                    prefs.edit().putString(PREF_ACTIVE_KEY, key).apply();
                }
                return onlineRes;
            }
        } catch (Throwable ignored) {
        }

        // 3. Respaldo para XITO-MODS-TESTER1 y XITO-MODS-TESTER2 (24 horas, 1 dispositivo)
        if (KEY_TESTER_1.equals(key) || KEY_TESTER_2.equals(key)) {
            return validateLocalTesterKey(context, key, deviceId);
        }

        return new ValidationResult(
                false,
                "Key inválida o no registrada en el servidor.",
                key,
                false,
                0
        );
    }

    private static ValidationResult checkAndBindFirebaseKey(String key, String deviceId) throws Exception {
        String safeKeyPath = URLEncoder.encode(key, "UTF-8").replace("+", "%20");
        String endpoint = FIREBASE_DB_URL + "/keys/" + safeKeyPath + ".json";

        String jsonResponse = httpRequest("GET", endpoint, null);
        if (jsonResponse == null || "null".equals(jsonResponse.trim())) {
            return null;
        }

        JSONObject obj = new JSONObject(jsonResponse);
        if (obj.has("error")) {
            return null;
        }

        boolean active = obj.optBoolean("active", true);
        if (!active) {
            return new ValidationResult(false, "Esta key ha sido pausada o desactivada.", key, false, 0);
        }

        boolean isPermanent = obj.optBoolean("permanent", false);
        int maxDevices = obj.optInt("maxDevices", 1);
        long durationHours = obj.optLong("durationHours", 24L);
        String boundDevice = obj.optString("boundDevice", "");
        long firstLoginAt = obj.optLong("firstLoginAt", 0L);
        long now = System.currentTimeMillis();

        // Bloqueo de 1 solo dispositivo
        if (maxDevices == 1 && !boundDevice.isEmpty() && !boundDevice.equals(deviceId)) {
            return new ValidationResult(
                    false,
                    "Esta key ya está vinculada a otro dispositivo.",
                    key,
                    false,
                    0
            );
        }

        boolean needsUpdate = false;
        if (firstLoginAt <= 0L) {
            firstLoginAt = now;
            obj.put("firstLoginAt", firstLoginAt);
            needsUpdate = true;
        }
        if (maxDevices == 1 && boundDevice.isEmpty()) {
            boundDevice = deviceId;
            obj.put("boundDevice", boundDevice);
            needsUpdate = true;
        }

        if (needsUpdate) {
            httpRequest("PUT", endpoint, obj.toString());
        }

        if (isPermanent) {
            return new ValidationResult(
                    true,
                    "Key activa (Permanente)",
                    key,
                    true,
                    Long.MAX_VALUE
            );
        }

        long totalDurationMs = durationHours * ONE_HOUR_MS;
        long elapsed = now - firstLoginAt;
        if (elapsed >= totalDurationMs) {
            return new ValidationResult(
                    false,
                    "Esta key ha vencido (" + durationHours + "h completadas).",
                    key,
                    false,
                    0
            );
        }

        long remaining = totalDurationMs - elapsed;
        return new ValidationResult(
                true,
                "Key activa (" + formatRemainingTime(remaining) + " restantes)",
                key,
                false,
                remaining
        );
    }

    private static String httpRequest(String method, String urlStr, String jsonBody) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);
            conn.setRequestProperty("Accept", "application/json");

            if (jsonBody != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                    os.flush();
                }
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return null;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            return sb.toString();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static ValidationResult validateLocalTesterKey(Context context, String key, String deviceId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String boundDeviceKey = PREF_BOUND_DEV_PREFIX + key;
        String firstActKey = PREF_FIRST_ACT_PREFIX + key;

        String boundDevice = prefs.getString(boundDeviceKey, null);
        long firstActivationTime = prefs.getLong(firstActKey, 0L);
        long now = System.currentTimeMillis();

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

    public static String getSavedKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_ACTIVE_KEY, "");
    }

    public static void clearSession(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(PREF_ACTIVE_KEY).apply();
    }

    public static String formatRemainingTime(long remainingMs) {
        if (remainingMs <= 0) return "0h 00m";
        long days = TimeUnit.MILLISECONDS.toDays(remainingMs);
        long hours = TimeUnit.MILLISECONDS.toHours(remainingMs) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs) % 60;
        if (days > 0) {
            return String.format(Locale.US, "%dd %dh %02dm", days, hours, minutes);
        }
        return String.format(Locale.US, "%dh %02dm", hours, minutes);
    }
}
