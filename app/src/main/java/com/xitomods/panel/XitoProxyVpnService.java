package com.xitomods.panel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Servicio de Proxy VPN de XITO MODS (equivalente funcional a A1 de HEX_ESP).
 */
public class XitoProxyVpnService extends VpnService {
    public static final String ACTION_START = "com.xitomods.panel.START_VPN_PROXY";
    public static final String ACTION_STOP = "com.xitomods.panel.STOP_VPN_PROXY";
    public static final String ACTION_SYNC = "com.xitomods.panel.SYNC_VPN_PROXY";

    public static volatile boolean isConnected = false;
    public static volatile int currentSimulatedPingMs = 18;
    public static volatile long processedBursts = 0L;
    public static volatile int bufferedPacketsCount = 0;

    private ParcelFileDescriptor vpnInterface;
    private Thread engineThread;
    private volatile boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        setupForegroundNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopVpnProxy();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            startVpnProxy();
        } else {
            applyAutoStopTeleportRule();
        }
        return START_STICKY;
    }

    private void setupForegroundNotification() {
        String channelId = "xito_mods_vpn_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "XITO MODS Proxy VPN", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
            Notification notification = new Notification.Builder(this, channelId)
                    .setContentTitle("XITO MODS PROXY V4 • CONECTADO")
                    .setContentText("Módulos de proxy y panel flotante en ejecución.")
                    .setSmallIcon(R.drawable.ic_xito_bubble)
                    .build();
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(1002, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                } else {
                    startForeground(1002, notification);
                }
            } catch (Exception e) {
                try {
                    startForeground(1002, notification);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void startVpnProxy() {
        try {
            Builder builder = new Builder();
            builder.setSession("XITO MODS PROXY V4");
            builder.setMtu(1400);
            builder.addAddress("10.8.0.2", 32);
            builder.addRoute("10.8.0.0", 24);
            builder.addDnsServer("8.8.8.8");
            builder.addDnsServer("1.1.1.1");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    builder.addDisallowedApplication(getPackageName());
                } catch (Exception ignored) {
                }
            }

            vpnInterface = builder.establish();
            running = true;
            isConnected = true;

            applyAutoStopTeleportRule();

            engineThread = new Thread(this::runProxyLoop, "XitoProxyEngineThread");
            engineThread.setDaemon(true);
            engineThread.start();
        } catch (Exception e) {
            running = true;
            isConnected = true;
            engineThread = new Thread(this::runProxyLoop, "XitoProxyEngineThread");
            engineThread.setDaemon(true);
            engineThread.start();
        }
    }

    private void applyAutoStopTeleportRule() {
        SharedPreferences prefs = getSharedPreferences(FloatingPanelService.UI_PREFS, Context.MODE_PRIVATE);
        boolean freezeOn = prefs.getBoolean("hex_freeze", false);
        boolean autoStopTp = prefs.getBoolean("hex_auto_stop_tp", true);
        if (freezeOn && autoStopTp) {
            boolean tpTarget = prefs.getBoolean("hex_tp_target", false);
            boolean tpInf = prefs.getBoolean("hex_tp_infinite", false);
            if (tpTarget || tpInf) {
                prefs.edit()
                        .putBoolean("hex_tp_target", false)
                        .putBoolean("hex_tp_infinite", false)
                        .apply();
            }
        }
    }

    private void runProxyLoop() {
        Queue<byte[]> recoveryQueue = new LinkedList<>();
        ByteBuffer packetBuffer = ByteBuffer.allocate(2048);

        FileInputStream in = null;
        FileOutputStream out = null;
        if (vpnInterface != null) {
            in = new FileInputStream(vpnInterface.getFileDescriptor());
            out = new FileOutputStream(vpnInterface.getFileDescriptor());
        }

        while (running) {
            try {
                SharedPreferences prefs = getSharedPreferences(FloatingPanelService.UI_PREFS, Context.MODE_PRIVATE);
                boolean ghostV2 = prefs.getBoolean("hex_ghost_v2", false);
                boolean pingLatency = prefs.getBoolean("hex_ping_latency", false);
                boolean invisibleV2 = prefs.getBoolean("hex_invisible_v2", false);
                int repeatDelayMs = prefs.getInt("hex_repeat_delay_ms", 1);
                boolean autoStopTp = prefs.getBoolean("hex_auto_stop_tp", true);
                boolean freeze = prefs.getBoolean("hex_freeze", false);
                boolean tpTarget = prefs.getBoolean("hex_tp_target", false);
                boolean tpInfinite = prefs.getBoolean("hex_tp_infinite", false);
                int burstPackets = prefs.getInt("hex_burst_packets", 1);
                int burstDelayMs = prefs.getInt("hex_burst_delay_ms", 5);
                boolean recoveryBuffer = prefs.getBoolean("hex_recovery_buffer", false);

                if (freeze && autoStopTp && (tpTarget || tpInfinite)) {
                    prefs.edit()
                            .putBoolean("hex_tp_target", false)
                            .putBoolean("hex_tp_infinite", false)
                            .apply();
                    tpTarget = false;
                    tpInfinite = false;
                }

                int basePing = 17;
                if (pingLatency) basePing += 145;
                if (ghostV2) basePing += 65;
                if (invisibleV2) basePing += (repeatDelayMs * 3);
                if (freeze) basePing = 999;
                else if (tpInfinite) basePing += (burstPackets * burstDelayMs);
                else if (tpTarget) basePing += 42;

                currentSimulatedPingMs = Math.min(999, basePing);

                if (in != null && in.available() > 0) {
                    int len = in.read(packetBuffer.array());
                    if (len > 0) {
                        byte[] pkt = new byte[len];
                        System.arraycopy(packetBuffer.array(), 0, pkt, 0, len);
                        if (freeze || (recoveryBuffer && (ghostV2 || tpInfinite))) {
                            if (recoveryQueue.size() < 256) {
                                recoveryQueue.offer(pkt);
                            }
                        } else if (out != null) {
                            int loops = tpInfinite ? Math.max(1, burstPackets) : 1;
                            for (int i = 0; i < loops; i++) {
                                out.write(pkt, 0, len);
                            }
                        }
                    }
                } else if (!freeze && !recoveryQueue.isEmpty() && out != null) {
                    int flushCount = Math.min(recoveryQueue.size(), Math.max(1, burstPackets));
                    for (int i = 0; i < flushCount; i++) {
                        byte[] cached = recoveryQueue.poll();
                        if (cached != null) {
                            out.write(cached);
                        }
                    }
                }

                bufferedPacketsCount = recoveryQueue.size();
                if (ghostV2 || pingLatency || invisibleV2 || freeze || tpTarget || tpInfinite) {
                    processedBursts++;
                }

                long sleepMs = 40L;
                if (invisibleV2) {
                    sleepMs = Math.max(5L, repeatDelayMs);
                } else if (tpInfinite) {
                    sleepMs = Math.max(5L, burstDelayMs);
                }
                Thread.sleep(sleepMs);
            } catch (InterruptedException ie) {
                break;
            } catch (Exception ignored) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private void stopVpnProxy() {
        running = false;
        isConnected = false;
        bufferedPacketsCount = 0;
        currentSimulatedPingMs = 18;
        if (engineThread != null) {
            engineThread.interrupt();
            engineThread = null;
        }
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (Exception ignored) {
            }
            vpnInterface = null;
        }
    }

    @Override
    public void onDestroy() {
        stopVpnProxy();
        super.onDestroy();
    }
}
