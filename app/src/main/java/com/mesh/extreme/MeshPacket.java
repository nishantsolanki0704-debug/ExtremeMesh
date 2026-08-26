package com.mesh.extreme;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "mesh_packets")
public class MeshPacket {

    @PrimaryKey
    public int packetHash;

    public byte flags;
    public int peopleCount;
    public float latitude;
    public float longitude;
    public int senderTimestamp;
    public long localReceivedTimeMs;

    // NEW: 20-character custom message
    public String message;

    public MeshPacket(int packetHash, byte flags, int peopleCount, float latitude, float longitude, int senderTimestamp, long localReceivedTimeMs, String message) {
        this.packetHash = packetHash;
        this.flags = flags;
        this.peopleCount = peopleCount;
        this.latitude = latitude;
        this.longitude = longitude;
        this.senderTimestamp = senderTimestamp;
        this.localReceivedTimeMs = localReceivedTimeMs;
        this.message = message;
    }
}