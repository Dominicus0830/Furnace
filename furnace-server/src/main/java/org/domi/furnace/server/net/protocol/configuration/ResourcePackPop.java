// S→C 리소스 팩 빼기(구성 0x08 / play 0x45): Optional<UUID>(없으면 서버가 보낸 팩 전부).
package org.domi.furnace.server.net.protocol.configuration;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

/** @param packId 뺄 팩(null 이면 전부)  @param inPlay play 단계(0x45)면 true, 구성 단계(0x08)면 false */
public record ResourcePackPop(UUID packId, boolean inPlay) implements Packet {

    public static final int ID_CONFIG = 0x08;
    public static final int ID_PLAY = 0x45;

    @Override
    public int id() { return inPlay ? ID_PLAY : ID_CONFIG; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeBoolean(packId != null);
        if (packId != null) Types.writeUuid(buf, packId);
    }
}
