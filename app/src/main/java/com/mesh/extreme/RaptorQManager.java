package com.mesh.extreme;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class RaptorQManager {

    private static final String TAG = "RaptorQManager";

    // =========================================================
    // JNI Native Bridges (For C++ Coded PHY Galois Field Math)
    // =========================================================
    // static { System.loadLibrary("raptorq-native"); }
    // public static native byte[] generateRepairSymbolNative(byte[] payload, int esi);
    // public static native byte[] decodeSymbolsNative(byte[][] receivedSymbols);

    /**
     * Java Fallback: Chunks the compressed data into Systematic Fountain Symbols.
     * This produces the exact packets that will be sent over Wi-Fi and BLE.
     */
    public static List<FountainSymbol> encodeSystematic(byte[] compressedPayload, int payloadId) {
        List<FountainSymbol> symbols = new ArrayList<>();

        int totalSymbols = (int) Math.ceil((double) compressedPayload.length / FountainSymbol.MAX_DATA_SIZE);
        Log.d(TAG, "Chunking " + compressedPayload.length + " bytes into " + totalSymbols + " symbols.");

        for (short i = 0; i < totalSymbols; i++) {
            int offset = i * FountainSymbol.MAX_DATA_SIZE;
            int length = Math.min(FountainSymbol.MAX_DATA_SIZE, compressedPayload.length - offset);

            byte[] chunk = new byte[length];
            System.arraycopy(compressedPayload, offset, chunk, 0, length);

            symbols.add(new FountainSymbol(payloadId, (short) totalSymbols, i, chunk));
        }

        return symbols;
    }

    /**
     * Java Fallback: Reconstructs the compressed payload from received systematic symbols.
     */
    public static byte[] decodeSystematic(List<FountainSymbol> receivedSymbols, int expectedTotal) {
        if (receivedSymbols.size() < expectedTotal) {
            Log.e(TAG, "Not enough symbols to decode. Have " + receivedSymbols.size() + "/" + expectedTotal);
            return null;
        }

        // Sort symbols by their ID to ensure perfect reconstruction order
        receivedSymbols.sort((a, b) -> Short.compare(a.symbolId, b.symbolId));

        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        try {
            for (FountainSymbol symbol : receivedSymbols) {
                // Strip padding from the final block if necessary by checking original Zstd header later
                outputStream.write(symbol.data);
            }
            return outputStream.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Failed to reconstruct payload: " + e.getMessage());
            return null;
        }
    }
}