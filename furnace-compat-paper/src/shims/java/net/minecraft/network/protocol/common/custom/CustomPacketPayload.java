package net.minecraft.network.protocol.common.custom;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;
import net.minecraft.resources.ResourceLocation;

/**
 * 플러그인 메시지(custom_payload 패킷) 본문(Mojang 이름, 바닐라 1.21.1 모양). Furnace 가 보낼 수 있는 본문은 {@link DiscardedPayload}
 * (채널 이름 + 날 바이트)뿐이다 — Paper 도 플러그인 메시지를 이것으로 보낸다. 바닐라 디버그·브랜드 페이로드 목록으로 고르는
 * {@code codec(FallbackProvider, List<TypeAndCodec>)} 는 아직 없다(플러그인이 쓰는 것이 확인되면 더한다).
 */
interface CustomPacketPayload {

    Type<? extends CustomPacketPayload> type();

    static <B extends ByteBuf, T extends CustomPacketPayload> StreamCodec<B, T> codec(StreamMemberEncoder<B, T> encoder, StreamDecoder<B, T> decoder) {
        return StreamCodec.ofMember(encoder, decoder);
    }

    static <T extends CustomPacketPayload> Type<T> createType(String id) {
        return new Type<>(ResourceLocation.withDefaultNamespace(id));
    }

    /** 페이로드 종류(채널 이름). */
    record Type<T extends CustomPacketPayload>(ResourceLocation id) {}
}
