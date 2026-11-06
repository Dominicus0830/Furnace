// 컨테이너 화면 열기(clientbound). title 은 텍스트 컴포넌트(NBT, 1.20.3+ 규약) — 바닐라처럼 번역 키(container.chest 등)를 보낸다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record OpenScreen(int containerId, int menuTypeId, Text title) implements Packet {

    public static final int ID = 0x33;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, containerId);
        Types.writeVarInt(buf, menuTypeId);
        buf.writeBytes(title.networkNbt());
    }
}
