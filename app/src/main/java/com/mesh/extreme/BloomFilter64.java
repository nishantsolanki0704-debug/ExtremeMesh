package com.mesh.extreme;

public class BloomFilter64 {

    // The entire Bloom filter state is stored in this single 64-bit primitive
    private long filterMask = 0L;

    /**
     * Adds a packet hash to the Bloom Filter.
     */
    public void add(int packetHash) {
        int[] indices = getBitIndices(packetHash);
        for (int index : indices) {
            filterMask |= (1L << index);
        }
    }

    /**
     * Checks if the packet MIGHT be in the database.
     * Returns TRUE if probably present, FALSE if definitely NOT present.
     */
    public boolean mightContain(int packetHash) {
        int[] indices = getBitIndices(packetHash);
        for (int index : indices) {
            if ((filterMask & (1L << index)) == 0L) {
                return false; // Definitely not seen this packet
            }
        }
        return true; // Probably seen it
    }

    /**
     * Merges another node's Bloom filter into ours via bitwise OR.
     */
    public void merge(long remoteFilterMask) {
        this.filterMask |= remoteFilterMask;
    }

    public long getFilterMask() {
        return filterMask;
    }

    /**
     * Deterministically derives 3 indices (0-63) from the packet hash.
     */
    private int[] getBitIndices(int hash) {
        // Mix the hash to avoid clustering
        int h1 = (hash ^ (hash >>> 16)) & 0x3F; // 0x3F is 63
        int h2 = ((hash * 31) ^ (hash >>> 8)) & 0x3F;
        int h3 = ((hash * 17) ^ (hash >>> 4)) & 0x3F;
        return new int[]{h1, h2, h3};
    }
}