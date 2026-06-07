// S→C 월드 기본 스폰 위치(나침반 기준점)
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record SetDefaultSpawnPosition(int x, int y, int z) implements Packet {

    public static final int ID = 0x54;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        // Position = ((x & 0x3FFFFFF) << 38) | ((z & 0x3FFFFFF) << 12) | (y & 0xFFF)
        long encoded = ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | (y & 0xFFF);
        buf.writeLong(encoded);
        buf.writeFloat(0f); // Angle
    }
}
