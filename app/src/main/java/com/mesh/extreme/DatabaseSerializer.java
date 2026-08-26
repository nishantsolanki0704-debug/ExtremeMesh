package com.mesh.extreme;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import android.util.Log;

public class DatabaseSerializer {

    private static final String TAG = "DatabaseSerializer";
    private static final int PACKET_SIZE = 35; // UPDATED: 15 bytes core + 20 bytes message

    /**
     * Transforms a list of MeshPackets into a dense, flat byte array.
     */
    public static byte[] serialize(List<MeshPacket> packets) {
        if (packets == null || packets.isEmpty()) {
            Log.d(TAG, "Database is empty. Returning 0-byte payload.");
            return new byte[0];
        }

        ByteBuffer buffer = ByteBuffer.allocate(packets.size() * PACKET_SIZE);

        for (MeshPacket packet : packets) {
            buffer.put(packet.flags);
            buffer.put((byte) (packet.peopleCount & 0xFF));
            buffer.putFloat(packet.latitude);
            buffer.putFloat(packet.longitude);
            buffer.putInt(packet.senderTimestamp);

            // Encode the 20-character message
            byte[] msgBytes = (packet.message != null) ? packet.message.getBytes(StandardCharsets.UTF_8) : new byte[0];
            byte msgLen = (byte) Math.min(msgBytes.length, 20);

            buffer.put(msgLen);
            buffer.put(msgBytes, 0, msgLen);

            // Pad the remaining bytes with zeros to strictly enforce the 35-byte block
            if (msgLen < 20) {
                buffer.put(new byte[20 - msgLen]);
            }
        }

        Log.d(TAG, "Serialized " + packets.size() + " packets into a " + buffer.capacity() + "-byte payload.");
        return buffer.array();
    }
}