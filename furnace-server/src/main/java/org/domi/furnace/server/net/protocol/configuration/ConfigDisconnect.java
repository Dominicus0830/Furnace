// S→C 구성 단계 연결 종료(0x02). 사유는 NBT 텍스트 컴포넌트.
package org.domi.furnace.server.net.protocol.configuration;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.nbt.NbtWriter;
import org.domi.furnace.server.net.protocol.Packet;

public record ConfigDisconnect(org.domi.furnace.server.net.protocol.Text reason) implements Packet {

    public ConfigDisconnect(String literal) {
        this(org.domi.furnace.server.net.protocol.Text.literal(literal));
    }

    public static final int ID = 0x02;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        new NbtWriter(buf).writeCompound(reason.toNbt());
    }
}
