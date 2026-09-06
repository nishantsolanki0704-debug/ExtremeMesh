package com.mesh.extreme;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private MapView mapView;
    private TextView tvNodeStatus, tvGpsCoords, tvLogs;
    private CheckBox cbFirstAid, cbFoodWater;
    private EditText etPeopleCount, etCustomMessage;
    private Button btnBroadcastSos, btnViewRequests, btnStressTest;

    private LocationManager locationManager;
    private Location currentLocation;
    private Marker selfMarker;
    private final List<Marker> emergencyMarkers = new ArrayList<>();

    private MeshDatabase db;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            String stackTrace = Log.getStackTraceString(throwable);
            PreferenceManager.getDefaultSharedPreferences(MainActivity.this)
                    .edit().putString("LAST_CRASH", stackTrace).commit();
            System.exit(2);
        });

        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        setContentView(R.layout.activity_main);

        db = MeshDatabase.getDatabase(this);

        initViews();

        String lastCrash = PreferenceManager.getDefaultSharedPreferences(this).getString("LAST_CRASH", null);
        if (lastCrash != null) {
            tvLogs.setText("FATAL CRASH RECOVERED:\n\n" + lastCrash + "\n\n");
            tvLogs.setTextColor(0xFFFF5252);
            PreferenceManager.getDefaultSharedPreferences(this).edit().remove("LAST_CRASH").apply();
        }

        initMap();
        initLocation();
        setupListeners();
        observeMeshDatabase();
    }

    private void initViews() {
        tvNodeStatus = findViewById(R.id.tvNodeStatus);
        tvGpsCoords = findViewById(R.id.tvGpsCoords);
        tvLogs = findViewById(R.id.tvLogs);
        cbFirstAid = findViewById(R.id.cbFirstAid);
        cbFoodWater = findViewById(R.id.cbFoodWater);
        etPeopleCount = findViewById(R.id.etPeopleCount);
        etCustomMessage = findViewById(R.id.etCustomMessage);
        btnBroadcastSos = findViewById(R.id.btnBroadcastSos);
        btnViewRequests = findViewById(R.id.btnViewRequests);
        btnStressTest = findViewById(R.id.btnStressTest);
        mapView = findViewById(R.id.mapView);
    }

    private void initMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(15.0);
        mapView.getController().setCenter(new GeoPoint(26.2183, 78.1828));
    }

    private void initLocation() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (checkPermissions()) {
            startGpsTracking();
        } else {
            requestMeshPermissions();
        }
    }

    private boolean checkPermissions() {
        boolean hasLoc = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return hasLoc && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
        }

        return hasLoc;
    }

    private void requestMeshPermissions() {
        String[] perms;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms = new String[]{
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms = new String[]{
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        } else {
            perms = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }

        requestPermissions(perms, 100);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 100) {
            if (checkPermissions()) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(this::startGpsTracking, 500);
            } else {
                log("Warning: Missing permissions.");
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void startGpsTracking() {
        if (locationManager == null) return;

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                Location lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

                if (lastKnown != null) {
                    onLocationChanged(lastKnown);
                }

                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        2000,
                        1,
                        this
                );
            }
        } catch (Exception e) {
            Log.e("GPS", "Network Provider error", e);
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        2000,
                        1,
                        this
                );
            }
        } catch (Exception e) {
            Log.e("GPS", "GPS Provider error", e);
        }

        dispatchToService(null);
    }

    @Override
    public void onLocationChanged(Location location) {
        this.currentLocation = location;

        tvGpsCoords.setText(String.format(
                Locale.getDefault(),
                "Lat: %.4f Lon: %.4f",
                location.getLatitude(),
                location.getLongitude()
        ));

        GeoPoint geoPoint = new GeoPoint(
                location.getLatitude(),
                location.getLongitude()
        );

        if (selfMarker == null) {
            selfMarker = new Marker(mapView);
            selfMarker.setTitle("You (Current Node)");
            mapView.getOverlays().add(selfMarker);
        }

        selfMarker.setPosition(geoPoint);
        mapView.getController().animateTo(geoPoint);
    }

    private void setupListeners() {
        btnBroadcastSos.setOnClickListener(v -> {
            byte[] packet = packPayload(
                    cbFirstAid.isChecked(),
                    cbFoodWater.isChecked(),
                    false,
                    false,
                    parsePeopleCount(),
                    etCustomMessage.getText().toString().trim()
            );

            dispatchToService(packet);
        });

        btnViewRequests.setOnClickListener(v ->
                startActivity(new Intent(
                        MainActivity.this,
                        SosRequestsActivity.class
                ))
        );

        btnStressTest.setOnClickListener(v -> executeStressTest());
    }

    private byte[] packPayload(
            boolean aid,
            boolean food,
            boolean ackComing,
            boolean ackDelivered,
            int peopleCount,
            String message
    ) {
        byte flags = 0;

        if (aid) flags |= (1 << 0);
        if (food) flags |= (1 << 1);
        if (ackComing) flags |= (1 << 2);
        if (ackDelivered) flags |= (1 << 3);

        float lat = (currentLocation != null)
                ? (float) currentLocation.getLatitude()
                : 26.2183f;

        float lon = (currentLocation != null)
                ? (float) currentLocation.getLongitude()
                : 78.1828f;

        byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
        byte msgLen = (byte) Math.min(msgBytes.length, 20);

        ByteBuffer buffer = ByteBuffer.allocate(35);

        buffer.put(flags)
                .put((byte) (peopleCount & 0xFF))
                .putFloat(lat)
                .putFloat(lon)
                .putInt((int) (System.currentTimeMillis() / 1000));

        buffer.put(msgLen)
                .put(msgBytes, 0, msgLen);

        if (msgLen < 20) {
            buffer.put(new byte[20 - msgLen]);
        }

        return buffer.array();
    }

    private void executeStressTest() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<MeshPacket> mockPackets = new ArrayList<>();

            for (int i = 0; i < 500; i++) {
                float latOff = (float) ((Math.random() - 0.5) * 0.09);
                float lonOff = (float) ((Math.random() - 0.5) * 0.09);

                ByteBuffer buf = ByteBuffer.allocate(35);

                buf.put((byte) 1)
                        .put((byte) 1)
                        .putFloat(26.2183f + latOff)
                        .putFloat(78.1828f + lonOff)
                        .putInt(0);

                buf.put((byte) 4)
                        .put("TEST".getBytes())
                        .put(new byte[16]);

                mockPackets.add(new MeshPacket(
                        Arrays.hashCode(buf.array()),
                        (byte) 1,
                        1,
                        26.2183f + latOff,
                        78.1828f + lonOff,
                        0,
                        System.currentTimeMillis(),
                        "TEST"
                ));
            }

            db.meshDao().insertPackets(mockPackets);
        });
    }

    private void dispatchToService(byte[] packet) {
        try {
            Intent intent = new Intent(this, MeshNodeService.class);

            if (packet != null) {
                intent.putExtra("PAYLOAD", packet);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            log("Service start error: " + e.getMessage());
        }
    }

    private void observeMeshDatabase() {
        db.meshDao().getAllPacketsLiveData().observe(this, packets -> {
            if (packets == null) return;

            for (Marker m : emergencyMarkers) {
                mapView.getOverlays().remove(m);
            }

            emergencyMarkers.clear();

            for (MeshPacket packet : packets) {
                if (packet.latitude == 0.0f && packet.longitude == 0.0f) {
                    continue;
                }

                Marker marker = new Marker(mapView);

                marker.setPosition(new GeoPoint(
                        packet.latitude,
                        packet.longitude
                ));

                marker.setTitle("Node " + packet.packetHash);

                emergencyMarkers.add(marker);
                mapView.getOverlays().add(marker);
            }

            mapView.invalidate();
        });
    }

    private int parsePeopleCount() {
        try {
            return Integer.parseInt(
                    etPeopleCount.getText().toString().trim()
            );
        } catch (Exception e) {
            return 1;
        }
    }

    private void log(String msg) {
        tvLogs.append(
                "[" + timeFormat.format(new Date()) + "] " + msg + "\n"
        );
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mapView != null) {
            mapView.onPause();
        }
    }

    @Override
    public void onStatusChanged(String p, int s, Bundle e) {}

    @Override
    public void onProviderEnabled(String p) {}

    @Override
    public void onProviderDisabled(String p) {}
}