package com.mesh.extreme;

import android.util.Log;
import com.github.luben.zstd.Zstd;
import java.nio.ByteBuffer;

public class ZstdManager {

    private static final String TAG = "ZstdManager";

    /**
     * Compresses data and prepends a 4-byte header indicating the original size.
     */
    public static byte[] compressWithHeader(byte[] originalData) {
        try {
            // 1. Compress the data (Compression Level 3 is optimal for speed vs ratio on mobile)
            byte[] compressedData = Zstd.compress(originalData, 3);

            Log.d(TAG, "Compression ratio: " + originalData.length + " bytes -> " + compressedData.length + " bytes");

            // 2. Prepend the original size (4 bytes) so the receiver knows how much to allocate
            ByteBuffer buffer = ByteBuffer.allocate(4 + compressedData.length);
            buffer.putInt(originalData.length);
            buffer.put(compressedData);

            return buffer.array();
        } catch (Exception e) {
            Log.e(TAG, "Compression failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Reads the 4-byte header and decompresses the payload.
     */
    public static byte[] decompressWithHeader(byte[] compressedDataWithHeader) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(compressedDataWithHeader);

            // 1. Extract the 4-byte original size header
            int originalSize = buffer.getInt();

            // 2. Extract the actual compressed payload
            byte[] compressedData = new byte[compressedDataWithHeader.length - 4];
            buffer.get(compressedData);

            // 3. Decompress via JNI
            byte[] decompressedData = new byte[originalSize];
            long decompressedSize = Zstd.decompress(decompressedData, compressedData);

            if (decompressedSize != originalSize) {
                Log.e(TAG, "Size mismatch! Expected: " + originalSize + ", Got: " + decompressedSize);
            }

            return decompressedData;
        } catch (Exception e) {
            Log.e(TAG, "Decompression failed: " + e.getMessage());
            return null;
        }
    }
}