// S→C 리소스 팩 내려받기(구성 0x09 / play 0x46): UUID, 문자열 URL, 문자열 해시(최대 40자), boolean 필수, Optional<텍스트 NBT> 안내 문구.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

/** @param prompt 안내 문구(없으면 null)  @param inPlay play 단계(0x46)면 true, 구성 단계(0x09)면 false */
public record ResourcePackPush(UUID packId, String url, String hash, boolean required, Text prompt, boolean inPlay) implements Packet {

    public static final int ID_PLAY = 0x46;
    public static final int ID_CONFIG = 0x09;
    public static final int MAX_HASH = 40;

    public ResourcePackPush {
        if (hash.length() > MAX_HASH) throw new IllegalArgumentException("Hash is too long (max 40, was " + hash.length() + ")");
    }

    @Override
    public int id() { return inPlay ? ID_PLAY : ID_CONFIG; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeUuid(buf, packId);
        Types.writeString(buf, url);
        Types.writeString(buf, hash);
        buf.writeBoolean(required);
        buf.writeBoolean(prompt != null);
        if (prompt != null) buf.writeBytes(prompt.networkNbt());
    }
}
