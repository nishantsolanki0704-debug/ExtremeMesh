package com.mesh.extreme;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class SosRequestsActivity extends AppCompatActivity {

    private RecyclerView rvSosRequests;
    private TextView tvRequestCount, tvEmptyState;
    private SosRequestsAdapter adapter;
    private MeshDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sos_requests);

        db = MeshDatabase.getDatabase(this);

        tvRequestCount = findViewById(R.id.tvRequestCount);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        rvSosRequests = findViewById(R.id.rvSosRequests);

        rvSosRequests.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SosRequestsAdapter(this::sendHelpEnRouteAck);
        rvSosRequests.setAdapter(adapter);

        observeDatabase();
    }

    private void observeDatabase() {
        db.meshDao().getAllPacketsLiveData().observe(this, packets -> {
            if (packets == null || packets.isEmpty()) {
                tvEmptyState.setVisibility(View.VISIBLE);
                rvSosRequests.setVisibility(View.GONE);
                tvRequestCount.setText("0 Signals");
            } else {
                tvEmptyState.setVisibility(View.GONE);
                rvSosRequests.setVisibility(View.VISIBLE);
                tvRequestCount.setText(packets.size() + " Signals");
                adapter.setPackets(packets);
            }
        });
    }

    private void sendHelpEnRouteAck(MeshPacket targetPacket) {
        // Pack a 35-byte ACK payload targeting this emergency node
        byte flags = (1 << 2); // Bit 2 = Help is coming ACK
        float lat = targetPacket.latitude;
        float lon = targetPacket.longitude;
        int timestamp = (int) (System.currentTimeMillis() / 1000);
        String ackMsg = "HELP EN ROUTE";

        byte[] msgBytes = ackMsg.getBytes(StandardCharsets.UTF_8);
        byte msgLen = (byte) Math.min(msgBytes.length, 20);

        ByteBuffer buffer = ByteBuffer.allocate(35);
        buffer.put(flags);
        buffer.put((byte) 0); // 0 people for ACK
        buffer.putFloat(lat);
        buffer.putFloat(lon);
        buffer.putInt(timestamp);
        buffer.put(msgLen);
        buffer.put(msgBytes, 0, msgLen);
        if (msgLen < 20) {
            buffer.put(new byte[20 - msgLen]);
        }

        byte[] ackPacket = buffer.array();

        // Dispatch immediately to MeshNodeService to advertise over BLE Coded PHY
        Intent serviceIntent = new Intent(this, MeshNodeService.class);
        serviceIntent.putExtra("PAYLOAD", ackPacket);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Toast.makeText(this, "ACK broadcasted for Node #" + Integer.toHexString(targetPacket.packetHash), Toast.LENGTH_SHORT).show();
    }
}