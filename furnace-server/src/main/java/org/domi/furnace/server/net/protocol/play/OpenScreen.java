// 체스트 등 컨테이너 화면 열기(clientbound). title은 텍스트 컴포넌트(NBT, 1.20.3+ 규약).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtWriter;
import org.domi.furnace.server.net.protocol.Packet;

public record OpenScreen(int containerId, int menuTypeId, String title) implements Packet {

    public static final int ID = 0x33;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, containerId);
        Types.writeVarInt(buf, menuTypeId);
        NbtCompound titleTag = new NbtCompound();
        titleTag.putString("text", title);
        new NbtWriter(buf).writeCompound(titleTag);
    }
}
