// S→C 명령 자동완성 응답(0x10) — 요청 id, 바꿀 범위(start, length), 후보(text + 선택적 툴팁).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.command.brigadier.Suggestion;
import org.domi.furnace.server.command.brigadier.Suggestions;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtWriter;
import org.domi.furnace.server.net.protocol.Packet;

public record CommandSuggestionsResponse(int transactionId, Suggestions suggestions) implements Packet {

    public static final int ID = 0x10;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, transactionId);
        Types.writeVarInt(buf, suggestions.getRange().getStart());
        Types.writeVarInt(buf, suggestions.getRange().getLength());
        Types.writeVarInt(buf, suggestions.getList().size());
        for (Suggestion s : suggestions.getList()) {
            Types.writeString(buf, s.getText());
            buf.writeBoolean(s.getTooltip() != null);
            if (s.getTooltip() != null) new NbtWriter(buf).writeCompound(s.getTooltip().toNbt());
        }
    }
}
