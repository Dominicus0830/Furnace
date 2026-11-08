package net.minecraft.network;

/** 패킷 처리자 shim(Mojang 이름, H7c). 이 서버에서 플러그인이 처리자를 만들거나 끼우는 것은 지원하지 않는다(Netty 파이프라인은 등급 C). */
public interface PacketListener {

    /** 바닐라: 연결이 아직 메시지를 받는가. */
    default boolean isAcceptingMessages() { return true; }
}
