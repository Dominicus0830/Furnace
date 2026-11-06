// 클라이언트 설정(언어·렌더 거리·채팅 표시·스킨 부위 비트·주 팔·글자 거르기·서버 목록 공개).
package org.domi.furnace.api.packet.serverbound;

import java.util.Objects;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.ServerboundPacket;

public record ServerboundClientInformationPacket(String locale, int viewDistance, ChatVisibility chatVisibility, boolean chatColors,
                                                 int skinParts, MainArm mainArm, boolean textFiltering, boolean allowsListing)
        implements ServerboundPacket {

    public enum ChatVisibility { FULL, SYSTEM, HIDDEN }

    public enum MainArm { LEFT, RIGHT }

    public ServerboundClientInformationPacket {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(chatVisibility, "chatVisibility");
        Objects.requireNonNull(mainArm, "mainArm");
    }

    @Override
    public PacketType<ServerboundClientInformationPacket> type() { return PacketTypes.Serverbound.CLIENT_INFORMATION; }
}
