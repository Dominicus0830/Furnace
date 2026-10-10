package org.domi.furnace.server.chat;

import java.util.UUID;

final class Bytes {

    private Bytes() {}

    static byte[] ofInt(int v) { return new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v}; }

    static byte[] ofLong(long v) {
        byte[] b = new byte[8];
        for (int i = 7; i >= 0; i--) {
            b[i] = (byte) v;
            v >>>= 8;
        }
        return b;
    }

    /** UUIDUtil.uuidToByteArray — 상위·하위 long 빅엔디언. */
    static byte[] ofUuid(UUID id) {
        byte[] b = new byte[16];
        System.arraycopy(ofLong(id.getMostSignificantBits()), 0, b, 0, 8);
        System.arraycopy(ofLong(id.getLeastSignificantBits()), 0, b, 8, 8);
        return b;
    }
}
