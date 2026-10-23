// S→C play 단계 연결 종료(0x1D). 사유 텍스트를 NBT 텍스트 컴포넌트로 보낸다(1.20.3+).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.nbt.NbtWriter;
import org.domi.furnace.server.net.protocol.Packet;

public record Disconnect(org.domi.furnace.api.text.Text reason) implements Packet {

    public Disconnect(String literal) {
        this(org.domi.furnace.api.text.Text.text(literal));
    }

    public static final int ID = 0x1D;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeBytes(reason.networkNbt());
    }
}
