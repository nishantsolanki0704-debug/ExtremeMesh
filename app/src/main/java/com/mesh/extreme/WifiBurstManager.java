package com.mesh.extreme;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WifiBurstManager {

    private static final String TAG = "WifiBurstManager";
    private static final int BURST_PORT = 8888;

    private final Context context;
    private final WifiManager wifiManager;
    private WifiManager.LocalOnlyHotspotReservation hotspotReservation;
    private final ExecutorService socketExecutor = Executors.newSingleThreadExecutor();

    private final MeshDatabase db;
    private final BloomFilter64 bloomFilter;

    public boolean isHotspotActive = false;

    public WifiBurstManager(Context context, BloomFilter64 bloomFilter) {
        this.context = context;
        this.wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        this.db = MeshDatabase.getDatabase(context);
        this.bloomFilter = bloomFilter;
    }

    @SuppressLint("MissingPermission") // Enforced in MainActivity
    public void triggerHotspotBurst(HotspotCredentialsCallback callback) {
        if (isHotspotActive) return;
        isHotspotActive = true;

        wifiManager.startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback() {
            @Override
            public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
                super.onStarted(reservation);
                hotspotReservation = reservation;
                Log.d(TAG, "LocalOnlyHotspot successfully spun up.");

                String ssid;
                String password;

                // API 30+ uses SoftApConfiguration
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    SoftApConfiguration config = reservation.getSoftApConfiguration();
                    ssid = config.getSsid();
                    password = config.getPassphrase();
                } else {
                    WifiConfiguration config = reservation.getWifiConfiguration();
                    ssid = config.SSID;
                    password = config.preSharedKey;
                }

                // 1. Pass credentials back to BLE Advertiser to transmit to the client node
                callback.onCredentialsGenerated(ssid, password);

                // 2. Open the TCP ServerSocket to await the massive payload burst
                startServerSocket();
            }

            @Override
            public void onStopped() {
                super.onStopped();
                isHotspotActive = false;
                Log.d(TAG, "Hotspot destroyed. Returned to BLE Fringe Range Mode.");
            }

            @Override
            public void onFailed(int reason) {
                super.onFailed(reason);
                isHotspotActive = false;
                Log.e(TAG, "Hotspot creation failed. Reason code: " + reason);
            }
        }, new Handler(Looper.getMainLooper()));
    }

    private void startServerSocket() {
        socketExecutor.execute(() -> {
            try (ServerSocket serverSocket = new ServerSocket(BURST_PORT)) {
                serverSocket.setSoTimeout(15000); // 15-second timeout window for client to connect
                Log.d(TAG, "ServerSocket listening on port " + BURST_PORT);

                // Block until client connects
                try (Socket clientSocket = serverSocket.accept();
                     DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                     OutputStream out = clientSocket.getOutputStream()) {

                    Log.d(TAG, "Client connected! Initiating rateless payload transfer...");

                    List<FountainSymbol> receivedSymbols = new ArrayList<>();
                    byte[] symbolBuffer = new byte[FountainSymbol.SYMBOL_SIZE];
                    int expectedTotal = -1;

                    try {
                        // Read the stream in exact 234-byte chunks
                        while (expectedTotal == -1 || receivedSymbols.size() < expectedTotal) {
                            in.readFully(symbolBuffer);

                            FountainSymbol symbol = FountainSymbol.fromBytes(symbolBuffer);
                            if (symbol != null) {
                                receivedSymbols.add(symbol);
                                if (expectedTotal == -1) {
                                    expectedTotal = symbol.totalSymbols;
                                }
                            }
                        }
                        Log.d(TAG, "All symbols received (" + expectedTotal + "). Decoding...");
                    } catch (java.io.EOFException e) {
                        Log.d(TAG, "Socket stream ended early. Received " + receivedSymbols.size() + "/" + expectedTotal + " symbols.");
                    }

                    // 1. Decode the RaptorQ Symbols back into the compressed payload
                    byte[] compressedPayload = RaptorQManager.decodeSystematic(receivedSymbols, expectedTotal);

                    if (compressedPayload != null) {
                        // 2. Decompress the Zstandard payload back to original byte array
                        byte[] decompressedPayload = ZstdManager.decompressWithHeader(compressedPayload);
                        if (decompressedPayload != null) {
                            Log.d(TAG, "SUCCESS! Reconstructed and decompressed " + decompressedPayload.length + " bytes.");

                            // 3. Parse and ingest the data into the Room Database
                            ingestBulkPayload(decompressedPayload);
                        } else {
                            Log.e(TAG, "Zstandard Decompression failed.");
                        }
                    } else {
                        Log.e(TAG, "RaptorQ Decoding failed. Missing symbols.");
                    }

                    // 4. Send ACK back to client
                    out.write("ACK_BURST_COMPLETE".getBytes());
                    out.flush();

                } catch (Exception e) {
                    Log.e(TAG, "Socket transfer interrupted: " + e.getMessage());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to open ServerSocket: " + e.getMessage());
            } finally {
                // CRITICAL: Aggressively tear down the hotspot to save battery
                teardownHotspot();
            }
        });
    }

    private void ingestBulkPayload(byte[] decompressedPayload) {
        if (decompressedPayload.length % 35 != 0) { // UPDATED TO 35
            Log.w(TAG, "Warning: Payload length is not a multiple of 35 bytes. Trailing bytes ignored.");
        }

        List<MeshPacket> newPackets = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.wrap(decompressedPayload);
        byte[] chunk = new byte[35]; // UPDATED TO 35

        while (buffer.remaining() >= 35) { // UPDATED TO 35
            buffer.get(chunk);

            int packetHash = Arrays.hashCode(chunk);

            if (bloomFilter.mightContain(packetHash)) continue;

            ByteBuffer pBuf = ByteBuffer.wrap(chunk);
            byte flags = pBuf.get();
            int peopleCount = pBuf.get() & 0xFF;
            float lat = pBuf.getFloat();
            float lon = pBuf.getFloat();
            int timestamp = pBuf.getInt();

            // Extract the String message
            byte msgLen = pBuf.get();
            byte[] msgBytes = new byte[msgLen];
            pBuf.get(msgBytes);
            String message = new String(msgBytes, StandardCharsets.UTF_8);

            // Skip the padding zeroes to align the buffer for the next 35-byte packet
            int padding = 20 - msgLen;
            if (padding > 0) pBuf.position(pBuf.position() + padding);

            MeshPacket packet = new MeshPacket(packetHash, flags, peopleCount, lat, lon, timestamp, System.currentTimeMillis(), message);
            newPackets.add(packet);
            bloomFilter.add(packetHash);
        }

        if (!newPackets.isEmpty()) {
            List<Long> rowIds = db.meshDao().insertPackets(newPackets);
            Log.d(TAG, "Successfully ingested " + rowIds.size() + " new packets into the DB.");
        }
    }

    public void teardownHotspot() {
        if (hotspotReservation != null) {
            hotspotReservation.close();
            hotspotReservation = null;
            isHotspotActive = false;
            Log.d(TAG, "Teardown complete. Battery saved.");
        }
    }

    public interface HotspotCredentialsCallback {
        void onCredentialsGenerated(String ssid, String password);
    }
}