package com.mesh.extreme;

public class BloomFilter64 {

    
    private long filterMask = 0L;

  
    public void add(int packetHash) {
        int[] indices = getBitIndices(packetHash);
        for (int index : indices) {
            filterMask |= (1L << index);
        }
    }

  
    public boolean mightContain(int packetHash) {
        int[] indices = getBitIndices(packetHash);
        for (int index : indices) {
            if ((filterMask & (1L << index)) == 0L) {
                return false; 
            }
        }
        return true; // Probably seen it
    }

    
    public void merge(long remoteFilterMask) {
        this.filterMask |= remoteFilterMask;
    }

    public long getFilterMask() {
        return filterMask;
    }

   
    private int[] getBitIndices(int hash) {
        // Mix the hash to avoid clustering
        int h1 = (hash ^ (hash >>> 16)) & 0x3F; // 0x3F is 63
        int h2 = ((hash * 31) ^ (hash >>> 8)) & 0x3F;
        int h3 = ((hash * 17) ^ (hash >>> 4)) & 0x3F;
        return new int[]{h1, h2, h3};
    }
}