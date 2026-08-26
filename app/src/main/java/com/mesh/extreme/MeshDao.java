package com.mesh.extreme;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MeshDao {

    // Silently ignore duplicates
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertPacket(MeshPacket packet);

    // Bulk insert for Wi-Fi burst payloads
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    List<Long> insertPackets(List<MeshPacket> packets);

    // Synchronous fetch for background serialization
    @Query("SELECT * FROM mesh_packets ORDER BY localReceivedTimeMs DESC")
    List<MeshPacket> getAllPackets();

    // Reactive stream for UI observation
    @Query("SELECT * FROM mesh_packets ORDER BY localReceivedTimeMs DESC")
    LiveData<List<MeshPacket>> getAllPacketsLiveData();

    @Query("SELECT COUNT(*) FROM mesh_packets WHERE packetHash = :hash")
    int checkExists(int hash);

    // Deletes packets older than 24h
    @Query("DELETE FROM mesh_packets WHERE localReceivedTimeMs < :cutoffTimeMs")
    int pruneOldPackets(long cutoffTimeMs);
}