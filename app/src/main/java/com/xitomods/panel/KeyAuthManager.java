package com.xitomods.panel;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Gestor de autenticación por clave para XITO MODS.
 * Soporta claves maestras locales y validación por API remota JSON.
 */
public class KeyAuthManager {
    private static final String PREFS_NAME = "xito_mods_auth_prefs";
    private static final String KEY_SAVED = "saved_key";
    private static final String KEY_EXPIRES = "session_expires_ms";

    // Claves maestras incluidas por defecto para pruebas inmediatas
    private static final String[] MASTER_KEYS = {
        "XITOMODS-VIP",
        "XITO-NEON-V4",
        "XITO-INF71",
        "PATOHEX-INF71"
    };

    // Endpoint opcional por si deseas conectarlo a tu propio servidor o JSON en GitHub Pages
    public static final String REMOTE_API_BASE = "https://xitomods.github.io/api/keys.json";

    public static class AuthResult {
        public final boolean valid;
        public final String message;
        public final long expiresAtMs;

        public AuthResult(boolean valid, String message, long expiresAtMs) {
            this.valid = valid;
            this.message = message;
            this.expiresAtMs = expiresAtMs;
        }
    }

    public static AuthResult verifyKey(Context context, String inputKey) {
        if (inputKey == null || inputKey.trim().isEmpty()) {
            return new AuthResult(false, "Escribe la clave para continuar.", 0L);
        }
        String cleanKey = inputKey.trim();

        // 1. Verificar contra claves maestras locales (30 días de validez)
        for (String master : MASTER_KEYS) {
            if (master.equalsIgnoreCase(cleanKey)) {
                long expires = System.currentTimeMillis() + (30L * 24L * 60L * 60L * 1000L);
                saveSession(context, cleanKey, expires);
                return new AuthResult(true, "Clave XITO MODS verificada correctamente", expires);
            }
        }

        // 2. Intentar verificación remota opcional si no es una clave maestra local
        try {
            URL url = new URL(REMOTE_API_BASE + "?key=" + cleanKey);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONObject json = new JSONObject(sb.toString());
                if (json.optBoolean("valid", false)) {
                    long exp = json.optLong("expiration_ms", System.currentTimeMillis() + 86400000L * 7L);
                    saveSession(context, cleanKey, exp);
                    return new AuthResult(true, "Clave validada en el servidor", exp);
                }
            }
        } catch (Exception ignored) {
            // Si no hay conexión al servidor opcional, se informa clave inválida
        }

        return new AuthResult(false, "Clave inválida. Prueba con: XITOMODS-VIP", 0L);
    }

    public static void saveSession(Context context, String key, long expiresAtMs) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putString(KEY_SAVED, key)
            .putLong(KEY_EXPIRES, expiresAtMs)
            .apply();
    }

    public static boolean hasValidSession(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String key = prefs.getString(KEY_SAVED, "");
        long exp = prefs.getLong(KEY_EXPIRES, 0L);
        return !key.isEmpty() && exp > System.currentTimeMillis();
    }

    public static String getSavedKey(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_SAVED, "");
    }

    public static long getExpirationMs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_EXPIRES, 0L);
    }

    public static void logout(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply();
    }

    public static String formatRemainingTime(long expiresAtMs) {
        long diff = Math.max(0L, expiresAtMs - System.currentTimeMillis());
        long totalSec = diff / 1000L;
        long days = totalSec / 86400L;
        long hours = (totalSec % 86400L) / 3600L;
        long mins = (totalSec % 3600L) / 60L;
        long secs = totalSec % 60L;
        return String.format("%dd %02dh %02dm %02ds", days, hours, mins, secs);
    }
}
