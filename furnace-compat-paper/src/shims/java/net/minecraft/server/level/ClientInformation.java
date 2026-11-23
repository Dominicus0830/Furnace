package net.minecraft.server.level;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;

/**
 * 클라이언트 설정 shim(Mojang 이름, ProtocolLib 다리 2b-3). 바닐라 1.21.1 과 같은 모양 — 클라이언트 설정 패킷(ServerboundClientInformationPacket)에 들어 있다.
 * modelCustomisation 은 스킨 부위 비트, viewDistance 는 클라이언트 렌더 거리(청크).
 */
record ClientInformation {

    /** 바닐라: 언어 이름 최대 길이. */
    public static final int MAX_LANGUAGE_LENGTH = 16;

    /** 바닐라: 접속 직후(설정 패킷 전) 기본값. */
    public static ClientInformation createDefault() {
        return new ClientInformation("en_us", 2, ChatVisiblity.FULL, true, 0, HumanoidArm.RIGHT, false, false);
    }
}
