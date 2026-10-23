// S→C 플레이 단계 태그 동기화(0x78) — /reload 뒤 바닐라 PlayerList.reloadResources 가 보낸다. 본문은 구성 단계 UpdateTags 와 같다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.configuration.UpdateTags;
import org.domi.furnace.server.net.registry.TagSet;

public record UpdateTagsPlay(TagSet tags) implements Packet {

    public static final int ID = 0x78;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) { new UpdateTags(tags).write(buf); }
}
