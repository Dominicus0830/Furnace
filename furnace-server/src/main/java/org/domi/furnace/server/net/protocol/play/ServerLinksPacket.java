// S→C 서버 링크(play 0x7B / 구성 0x10): VarInt 개수, 항목마다 [boolean 알려진 종류, 알려진 종류면 VarInt 종류 아니면 NBT 텍스트], 문자열 URL.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

/** @param inPlay play 단계(0x7B)면 true, 구성 단계(0x10)면 false */
@VanillaPort("net.minecraft.network.protocol.common.ClientboundServerLinksPacket")
public record ServerLinksPacket(List<Entry> links, boolean inPlay) implements Packet {

    public static final int ID_PLAY = PacketIds.PLAY_CLIENTBOUND_SERVER_LINKS;
    public static final int ID_CONFIG = PacketIds.CONFIGURATION_CLIENTBOUND_SERVER_LINKS;

    /** 바닐라 ServerLinks.KnownLinkType 순서: 0 report_bug … 9 announcements. */
    public static final int KNOWN_REPORT_BUG = 0;

    /** @param knownType 알려진 종류 번호(없으면 -1)  @param label 사용자 정의 이름(knownType 이 -1 일 때) */
    public record Entry(int knownType, Text label, String url) { }

    @Override
    public int id() { return inPlay ? ID_PLAY : ID_CONFIG; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, links.size());
        for (Entry e : links) {
            if (e.knownType() >= 0) {
                buf.writeBoolean(true);
                Types.writeVarInt(buf, e.knownType());
            } else {
                buf.writeBoolean(false);
                buf.writeBytes(e.label().networkNbt());
            }
            Types.writeString(buf, e.url());
        }
    }
}
