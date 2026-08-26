package com.mesh.extreme;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WifiClientManager {

    private static final String TAG = "WifiClientManager";
    private static final int BURST_PORT = 8888;
    // The default IP address for Android LocalOnlyHotspot Group Owners
    private static final String AP_IP_ADDRESS = "192.168.43.1";

    private final Context context;
    private final ConnectivityManager connectivityManager;
    private final ExecutorService socketExecutor = Executors.newSingleThreadExecutor();
    private ConnectivityManager.NetworkCallback networkCallback;

    public WifiClientManager(Context context) {
        this.context = context;
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    public void connectAndBurst(String ssid, String password, byte[] payloadToBurst) {

        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build();

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) // Peer-to-peer only
                .setNetworkSpecifier(specifier)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                Log.d(TAG, "Successfully connected to Mesh AP: " + ssid);

                // Route all socket traffic in this app through this specific Wi-Fi network
                connectivityManager.bindProcessToNetwork(network);

                // Execute the TCP transfer
                executePayloadBurst(network, payloadToBurst);
            }

            @Override
            public void onUnavailable() {
                super.onUnavailable();
                Log.e(TAG, "Failed to connect to Mesh AP. Timeout reached.");
                releaseNetwork();
            }
        };

        Log.d(TAG, "Requesting connection to Mesh AP...");
        connectivityManager.requestNetwork(request, networkCallback);
    }

    private void executePayloadBurst(Network network, byte[] payloadToCompress) {
        socketExecutor.execute(() -> {
            try (Socket socket = new Socket()) {
                // Bind the socket explicitly to the Wi-Fi network interface
                network.bindSocket(socket);

                Log.d(TAG, "Connecting TCP Socket to " + AP_IP_ADDRESS + ":" + BURST_PORT);
                socket.connect(new InetSocketAddress(AP_IP_ADDRESS, BURST_PORT), 10000);

                try (OutputStream out = socket.getOutputStream();
                     InputStream in = socket.getInputStream()) {

                    // 1. Compress the data!
                    Log.d(TAG, "Original payload size: " + payloadToCompress.length + " bytes");
                    byte[] compressedPayload = ZstdManager.compressWithHeader(payloadToCompress);

                    if (compressedPayload != null) {
                        // Create a unique Payload ID based on timestamp
                        int payloadId = (int) (System.currentTimeMillis() / 1000);

                        // Encode into RaptorQ Symbols
                        List<FountainSymbol> symbols = RaptorQManager.encodeSystematic(compressedPayload, payloadId);

                        Log.d(TAG, "Streaming " + symbols.size() + " Fountain Symbols over Wi-Fi...");

                        // Transmit symbols sequentially
                        for (FountainSymbol symbol : symbols) {
                            out.write(symbol.toBytes());
                        }
                        out.flush();
                        Log.d(TAG, "Burst complete!");
                    }

                    // 3. Wait for Server ACK
                    byte[] ackBuffer = new byte[64];
                    int bytesRead = in.read(ackBuffer);
                    if (bytesRead > 0) {
                        String ack = new String(ackBuffer, 0, bytesRead);
                        Log.d(TAG, "Received Server ACK: " + ack);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Socket transfer failed: " + e.getMessage());
            } finally {
                releaseNetwork();
            }
        });
    }

    public void releaseNetwork() {
        if (networkCallback != null) {
            try {
                connectivityManager.bindProcessToNetwork(null); // Clear routing
                connectivityManager.unregisterNetworkCallback(networkCallback);
                Log.d(TAG, "Network released. Returning to BLE Fringe Mode.");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing network: " + e.getMessage());
            }
            networkCallback = null;
        }
    }
}