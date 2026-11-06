// S→C 점수(0x61): String 주인, String 목표, VarInt 점수, Optional<텍스트 NBT> 표시 이름, Optional<숫자 형식>.
// 바닐라 ClientboundSetScorePacket(1.21.1) 의 StreamCodec 순서.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.scores.NumberFormat;

public record SetScore(String owner, String objectiveName, int score, Text display, NumberFormat numberFormat) implements Packet {

    public static final int ID = 0x61;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeString(buf, owner);
        Types.writeString(buf, objectiveName);
        Types.writeVarInt(buf, score);
        buf.writeBoolean(display != null);
        if (display != null) buf.writeBytes(display.networkNbt());
        NumberFormat.writeOptional(buf, numberFormat);
    }
}
