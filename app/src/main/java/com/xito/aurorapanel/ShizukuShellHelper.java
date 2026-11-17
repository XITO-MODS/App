package com.xito.aurorapanel;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import rikka.shizuku.Shizuku;

public class ShizukuShellHelper {

    public static class ShellResult {
        public final int exitCode;
        public final String output;

        public ShellResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    /**
     * Ejecuta un script de comandos shell con privilegios de Shizuku (ADB / UID 2000)
     * usando reflexión sobre Shizuku.newProcess (que en Shizuku API 13.1.5 tiene acceso privado).
     */
    public static ShellResult execShizukuCommand(String shellScript) {
        StringBuilder outBuilder = new StringBuilder();
        Process process = null;
        try {
            Method newProcessMethod = Shizuku.class.getDeclaredMethod(
                    "newProcess",
                    String[].class,
                    String[].class,
                    String.class
            );
            newProcessMethod.setAccessible(true);
            process = (Process) newProcessMethod.invoke(null, new String[]{"sh"}, null, null);

            try (OutputStream os = process.getOutputStream()) {
                os.write((shellScript + "\nexit\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            try (BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = stdout.readLine()) != null) {
                    outBuilder.append(line).append("\n");
                }
                while ((line = stderr.readLine()) != null) {
                    outBuilder.append("[ERR] ").append(line).append("\n");
                }
            }

            int code = process.waitFor();
            process.destroy();
            return new ShellResult(code, outBuilder.toString().trim());
        } catch (Throwable t) {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Throwable ignored) {
                }
            }
            Throwable cause = (t.getCause() != null) ? t.getCause() : t;
            return new ShellResult(-1, "Error ejecutando Shizuku: " + cause.getMessage());
        }
    }
}
