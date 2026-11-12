package net.minecraft.network;

import io.netty.channel.ChannelInboundHandlerAdapter;

/** 패킷 디코더 shim(Mojang 이름, ProtocolLib 다리 2b). {@link PacketEncoder} 와 같다 — 파이프라인의 {@code "decoder"} 자리, 단계를 알리는 용도뿐이다. */
public class PacketDecoder<T extends PacketListener> extends ChannelInboundHandlerAdapter {

    private final ProtocolInfo<T> protocolInfo;

    public PacketDecoder(ProtocolInfo<T> protocolInfo) { this.protocolInfo = protocolInfo; }

    /** 호환 모듈 전용. */
    public ProtocolInfo<T> protocolInfoForCompat() { return protocolInfo; }
}
