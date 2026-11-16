package net.minecraft.network.chat;

import java.util.UUID;
import net.minecraft.world.entity.player.ProfilePublicKey;

/**
 * 플레이어 채팅 세션 shim(Mojang 이름, ProtocolLib 다리 2b-3). 바닐라 1.21.1 과 같은 모양 — 세션 번호와 공개 키.
 * 받은 세션 갱신 패킷(ServerboundChatSessionUpdatePacket)과 탭 목록 패킷 항목에는 {@link Data} 가 들어 있다.
 */
public record RemoteChatSession(UUID sessionId, ProfilePublicKey profilePublicKey) {

    public Data asData() { return new Data(sessionId, profilePublicKey.data()); }

    public boolean hasExpired() { return profilePublicKey.data().hasExpired(); }

    /** 바닐라: 패킷에 실리는 값(세션 번호, 키 값). */
    public record Data(UUID sessionId, ProfilePublicKey.Data profilePublicKey) {}
}
