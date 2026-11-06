// 레시피 북이 재료가 모자라 못 채울 때 고스트 레시피를 보여 주라는 패킷(clientbound place_ghost_recipe 0x37).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record PlaceGhostRecipe(int containerId, String recipe) implements Packet {

    public static final int ID = 0x37;

    @Override public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeByte(containerId);
        Types.writeString(buf, recipe);
    }
}
