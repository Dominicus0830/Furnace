package net.minecraft.network.protocol;

import net.minecraft.network.PacketListener;

/**
 * 패킷 shim(Mojang 이름, H7c). 이 모듈의 clientbound 패킷 shim 은 Furnace 패킷으로 바뀌어 나간다
 * (손으로 쓴 것은 {@link org.domi.furnace.compat.nms.NmsPacket}, 스키마로 생성한 것은 {@link org.domi.furnace.compat.nms.SchemaPacketBridge}).
 * 플러그인이 이 인터페이스를 직접 구현한 패킷은 보낼 수 없다(UnsupportedNmsCall).
 */
public interface Packet<T extends PacketListener> {

    /** 바닐라: 처리 중 예외가 나도 연결을 끊지 않아도 되는가. */
    default boolean isSkippable() { return false; }

    /** 바닐라: 이 패킷 뒤로 프로토콜이 끝나는가(연결 끊기 패킷). */
    default boolean isTerminal() { return false; }
}
