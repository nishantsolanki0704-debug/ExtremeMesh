package com.mesh.extreme;

import java.nio.ByteBuffer;

public class FountainSymbol {
    public static final int SYMBOL_SIZE = 234;
    public static final int MAX_DATA_SIZE = 226;

    public int payloadId;
    public short totalSymbols;
    public short symbolId;
    public byte[] data;

    public FountainSymbol(int payloadId, short totalSymbols, short symbolId, byte[] data) {
        this.payloadId = payloadId;
        this.totalSymbols = totalSymbols;
        this.symbolId = symbolId;
        this.data = data;
    }

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(SYMBOL_SIZE);
        buffer.putInt(payloadId);
        buffer.putShort(totalSymbols);
        buffer.putShort(symbolId);
        buffer.put(data);

        if (data.length < MAX_DATA_SIZE) {
            buffer.put(new byte[MAX_DATA_SIZE - data.length]);
        }
        return buffer.array();
    }

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