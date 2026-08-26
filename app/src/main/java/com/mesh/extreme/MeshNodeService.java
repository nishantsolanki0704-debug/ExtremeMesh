package com.mesh.extreme;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("MissingPermission")
public class MeshNodeService extends Service {

    private static final String TAG = "MeshNodeService";
    private static final int MESH_MANUFACTURER_ID = 0xFFFF;

    private PowerManager.WakeLock wakeLock;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothLeScanner scanner;
    private boolean isCodedPhySupported = false;

    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private MeshDatabase db;
    private BloomFilter64 bloomFilter;

    private WifiBurstManager wifiBurstManager;
    private WifiClientManager wifiClientManager;
    private long lastHotspotTriggerTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            db = MeshDatabase.getDatabase(this);
            bloomFilter = new BloomFilter64();
            wifiBurstManager = new WifiBurstManager(this, bloomFilter);
            wifiClientManager = new WifiClientManager(this);

            acquireWakeLock();
            startForegroundServiceStrict();
            initBluetooth();
        } catch (Exception e) {
            Log.e(TAG, "Fatal error in Service onCreate", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent != null) {
                byte[] payloadToBroadcast = intent.getByteArrayExtra("PAYLOAD");
                if (payloadToBroadcast != null) {
                    startExtendedAdvertising(payloadToBroadcast);
                } else {
                    startScanning();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartCommand", e);
        }
        return START_STICKY;
    }

    private void startForegroundServiceStrict() {
        try {
            String channelId = "extreme_mesh_channel";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        channelId, "ReliefMesh Background Node", NotificationManager.IMPORTANCE_LOW);
                getSystemService(NotificationManager.class).createNotificationChannel(channel);
            }

            Notification notification = new NotificationCompat.Builder(this, channelId)
                    .setContentTitle("ReliefMesh Node Active")
                    .setContentText("Maintaining background radio link...")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();

            // Simple foreground start to bypass Android 14 type security crashes
            startForeground(1, notification);
        } catch (Exception e) {
            Log.e(TAG, "Foreground Start failed.", e);
        }
    }

    private void acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ReliefMesh::CpuWakeLock");
            wakeLock.acquire();
        } catch (Exception e) {
            Log.e(TAG, "Failed to acquire WakeLock", e);
        }
    }

    private void initBluetooth() {
        try {
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();

            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
                scanner = bluetoothAdapter.getBluetoothLeScanner();
                isCodedPhySupported = bluetoothAdapter.isLeCodedPhySupported();
            } else {
                Log.w(TAG, "Bluetooth is disabled or missing.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Bluetooth", e);
        }
    }

    private void startExtendedAdvertising(byte[] payload) {
        if (advertiser == null) return;
        try {
            int phyType = isCodedPhySupported ? BluetoothDevice.PHY_LE_CODED : BluetoothDevice.PHY_LE_1M;
            AdvertisingSetParameters parameters = new AdvertisingSetParameters.Builder()
                    .setLegacyMode(false)
                    .setConnectable(false)
                    .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
                    .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MAX)
                    .setPrimaryPhy(phyType)
                    .setSecondaryPhy(phyType)
                    .build();

            AdvertiseData data = new AdvertiseData.Builder().addManufacturerData(MESH_MANUFACTURER_ID, payload).build();
            advertiser.startAdvertisingSet(parameters, data, null, null, null, new AdvertisingSetCallback() {});
        } catch (Exception e) {
            Log.e(TAG, "Advertising crash", e);
        }
    }

    private void startScanning() {
        if (scanner == null) return;
        try {
            ScanSettings settings = new ScanSettings.Builder()
                    .setLegacy(false)
                    .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            scanner.startScan(Collections.emptyList(), settings, scanCallback);
        } catch (Exception e) {
            Log.e(TAG, "Scanning crash", e);
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result.getScanRecord() != null) {
                byte[] payload = result.getScanRecord().getManufacturerSpecificData(MESH_MANUFACTURER_ID);
                if (payload != null && payload.length == 35) {
                    processIncomingPacket(payload, result.getRssi());
                }
            }
        }
    };

    private void processIncomingPacket(byte[] payload, int rssi) {
        try {
            int packetHash = Arrays.hashCode(payload);
            if (bloomFilter.mightContain(packetHash)) return;

            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte flags = buffer.get();
            int peopleCount = buffer.get() & 0xFF;
            float lat = buffer.getFloat();
            float lon = buffer.getFloat();
            int timestamp = buffer.getInt();

            byte msgLen = buffer.get();
            byte[] msgBytes = new byte[msgLen];
            buffer.get(msgBytes);
            String message = new String(msgBytes, StandardCharsets.UTF_8);

            MeshPacket newPacket = new MeshPacket(packetHash, flags, peopleCount, lat, lon, timestamp, System.currentTimeMillis(), message);

            dbExecutor.execute(() -> {
                if (db.meshDao().checkExists(packetHash) == 0) {
                    db.meshDao().insertPacket(newPacket);
                    bloomFilter.add(packetHash);
                    startExtendedAdvertising(payload);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Incoming packet parse error", e);
        }
    }

    @Override
    public void onDestroy() {
        try { if (scanner != null) scanner.stopScan(scanCallback); } catch (Exception e) {}
        try { if (wifiBurstManager != null) wifiBurstManager.teardownHotspot(); } catch (Exception e) {}
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception e) {}
        dbExecutor.shutdown();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}