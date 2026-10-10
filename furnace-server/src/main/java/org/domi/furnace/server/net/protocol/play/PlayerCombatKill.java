// S→C 플레이어 사망(0x3C) — 사망 화면과 그 메시지(showDeathMessages 가 꺼지면 빈 문자열).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtWriter;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.Text;

public record PlayerCombatKill(int playerId, Text message) implements Packet {

    public static final int ID = 0x3C;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, playerId);
        new NbtWriter(buf).writeCompound(message.toNbt());
    }
}
