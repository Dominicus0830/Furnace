// S→C 레시피 목록(0x77, 바닐라 ClientboundUpdateRecipesPacket). 본문은 RecipeManager 가 미리 만들어 둔 바이트.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket")
public record UpdateRecipes(byte[] body) implements Packet {

    public static final int ID = PacketIds.PLAY_CLIENTBOUND_UPDATE_RECIPES;

    @Override public int id() { return ID; }

    @Override public void write(ByteBuf buf) { buf.writeBytes(body); }
}
