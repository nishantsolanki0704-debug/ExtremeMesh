package com.mesh.extreme;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

// INCREMENTED VERSION TO 2
@Database(entities = {MeshPacket.class}, version = 2, exportSchema = false)
public abstract class MeshDatabase extends RoomDatabase {

    public abstract MeshDao meshDao();
    private static volatile MeshDatabase INSTANCE;

    public static MeshDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (MeshDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    MeshDatabase.class, "mesh_extreme_db")
                            // CRITICAL FIX: Automatically rebuilds the DB if we change columns, preventing instant crashes.
                            .fallbackToDestructiveMigration()
                            .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                            .addCallback(new RoomDatabase.Callback() {
                                @Override
                                public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                    super.onOpen(db);
                                    db.execSQL("PRAGMA synchronous = NORMAL;");
                                }
                            })
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}