// S→C 레시피 목록(0x77, 바닐라 ClientboundUpdateRecipesPacket). 본문은 RecipeManager 가 미리 만들어 둔 바이트.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record UpdateRecipes(byte[] body) implements Packet {

    public static final int ID = 0x77;

    @Override public int id() { return ID; }

    @Override public void write(ByteBuf buf) { buf.writeBytes(body); }
}
