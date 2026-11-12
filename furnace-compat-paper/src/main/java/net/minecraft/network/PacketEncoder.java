package net.minecraft.network;

import io.netty.channel.ChannelOutboundHandlerAdapter;

/**
 * 패킷 인코더 shim(Mojang 이름, ProtocolLib 다리 2b). 파이프라인의 {@code "encoder"} 자리 — 실제 바이트 변환은 하지 않고(Furnace 가 패킷을 직접 쓴다)
 * 패킷 라이브러리가 "이 연결의 지금 단계"를 찾는 데만 쓰인다(ProtocolLib: 이 클래스의 {@link ProtocolInfo} 필드 → {@code id()}).
 */
public class PacketEncoder<T extends PacketListener> extends ChannelOutboundHandlerAdapter {

    private final ProtocolInfo<T> protocolInfo;

    public PacketEncoder(ProtocolInfo<T> protocolInfo) { this.protocolInfo = protocolInfo; }

    /** 호환 모듈 전용. */
    public ProtocolInfo<T> protocolInfoForCompat() { return protocolInfo; }
}
