package com.mesh.extreme;

import java.nio.ByteBuffer;

public class FountainSymbol {
    public static final int SYMBOL_SIZE = 234;
    public static final int MAX_DATA_SIZE = 226; // 234 - 8 bytes of headers

    public int payloadId;      // 4 bytes: Unique ID for this specific emergency broadcast
    public short totalSymbols; // 2 bytes: Total symbols needed to reconstruct
    public short symbolId;     // 2 bytes: ESI (Encoding Symbol ID)
    public byte[] data;        // Up to 226 bytes of compressed data

    public FountainSymbol(int payloadId, short totalSymbols, short symbolId, byte[] data) {
        this.payloadId = payloadId;
        this.totalSymbols = totalSymbols;
        this.symbolId = symbolId;
        this.data = data;
    }

    /**
     * Serializes the object into a strict 234-byte array for radio transmission.
     */
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(SYMBOL_SIZE);
        buffer.putInt(payloadId);
        buffer.putShort(totalSymbols);
        buffer.putShort(symbolId);
        buffer.put(data);

        // Pad the rest with zeros if data is less than 226 bytes
        if (data.length < MAX_DATA_SIZE) {
            buffer.put(new byte[MAX_DATA_SIZE - data.length]);
        }
        return buffer.array();
    }

    /**
     * Deserializes a 234-byte array back into a FountainSymbol.
     */
    public static FountainSymbol fromBytes(byte[] rawBytes) {
        if (rawBytes.length != SYMBOL_SIZE) return null;

        ByteBuffer buffer = ByteBuffer.wrap(rawBytes);
        int pId = buffer.getInt();
        short tSymbols = buffer.getShort();
        short sId = buffer.getShort();

        byte[] sData = new byte[MAX_DATA_SIZE];
        buffer.get(sData);

        return new FountainSymbol(pId, tSymbols, sId, sData);
    }
}