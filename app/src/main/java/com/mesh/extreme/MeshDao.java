package com.mesh.extreme;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MeshDao {

    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertPacket(MeshPacket packet);

    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    List<Long> insertPackets(List<MeshPacket> packets);

    
    @Query("SELECT * FROM mesh_packets ORDER BY localReceivedTimeMs DESC")
    List<MeshPacket> getAllPackets();

    
    @Query("SELECT * FROM mesh_packets ORDER BY localReceivedTimeMs DESC")
    LiveData<List<MeshPacket>> getAllPacketsLiveData();

    @Query("SELECT COUNT(*) FROM mesh_packets WHERE packetHash = :hash")
    int checkExists(int hash);

    
    @Query("DELETE FROM mesh_packets WHERE localReceivedTimeMs < :cutoffTimeMs")
    int pruneOldPackets(long cutoffTimeMs);
}