// block_entity_data(clientbound 0x07): 칸 위치 + 블록 엔티티 종류 id + 갱신 태그(이름 없는 네트워크 NBT).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtWriter;
import org.domi.furnace.server.net.protocol.Packet;

public record BlockEntityDataPacket(int x, int y, int z, int typeId, NbtCompound tag) implements Packet {

    public static final int ID = 0x07;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeBlockPos(buf, x, y, z);
        Types.writeVarInt(buf, typeId);
        new NbtWriter(buf).writeCompound(tag == null ? new NbtCompound() : tag);
    }
}
