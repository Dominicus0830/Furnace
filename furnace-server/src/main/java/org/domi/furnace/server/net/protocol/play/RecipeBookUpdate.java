// S→C recipe(0x41, 바닐라 ClientboundRecipePacket) — 상태(INIT·ADD·REMOVE), 책 설정 8개, 레시피 id, INIT 이면 강조 id.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.List;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record RecipeBookUpdate(int state, boolean[] settings, List<ResourceId> recipes, List<ResourceId> highlight) implements Packet {

    public static final int ID = 0x41;
    public static final int INIT = 0, ADD = 1, REMOVE = 2;

    @Override public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, state);
        for (boolean b : settings) buf.writeBoolean(b);
        writeIds(buf, recipes);
        if (state == INIT) writeIds(buf, highlight);
    }

    private static void writeIds(ByteBuf buf, List<ResourceId> ids) {
        Types.writeVarInt(buf, ids.size());
        for (ResourceId id : ids) Types.writeString(buf, id.toString());
    }
}
