// S→C 태그 질의 응답(0x6E): VarInt 처리 번호, NBT(없으면 TAG_END 1바이트, 있으면 무명 루트 compound).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtWriter;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

/** @param tag 질의 결과(없으면 null) */
@VanillaPort("net.minecraft.network.protocol.game.ClientboundTagQueryPacket")
public record TagQuery(int transactionId, NbtCompound tag) implements Packet {

    public static final int ID = PacketIds.PLAY_CLIENTBOUND_TAG_QUERY;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, transactionId);
        if (tag == null) buf.writeByte(0);
        else new NbtWriter(buf).writeCompound(tag);
    }
}
