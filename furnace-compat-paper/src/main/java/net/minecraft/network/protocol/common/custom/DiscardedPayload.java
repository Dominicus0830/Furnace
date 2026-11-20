package net.minecraft.network.protocol.common.custom;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 채널 이름과 날 바이트만 가진 플러그인 메시지 본문(Mojang 이름, 바닐라 1.21.1 모양). Paper 의 {@code CraftPlayer#sendPluginMessage} 와
 * Axiom·VeinMiner 같은 플러그인이 {@code new ClientboundCustomPayloadPacket(new DiscardedPayload(id, buf))} 로 보낸다.
 * Furnace 는 이 패킷을 공개 API 의 플러그인 메시지 패킷으로 바꿔 보낸다({@code TypedPacketBridge}).
 */
public record DiscardedPayload(ResourceLocation id, ByteBuf data) implements CustomPacketPayload {

    /** 바닐라와 같다: 읽을 때는 남은 바이트 전부(상한 초과면 IllegalArgumentException), 쓸 때는 data 를 그대로 붙인다. */
    public static <T extends FriendlyByteBuf> StreamCodec<T, DiscardedPayload> codec(ResourceLocation id, int maxSize) {
        return CustomPacketPayload.codec((DiscardedPayload payload, T buf) -> buf.writeBytes(payload.data), (T buf) -> {
            int n = buf.readableBytes();
            if (n >= 0 && n <= maxSize) return new DiscardedPayload(id, buf.readBytes(n));
            throw new IllegalArgumentException("Payload may not be larger than " + maxSize + " bytes");
        });
    }

    @Override
    public Type<DiscardedPayload> type() { return new Type<>(id); }
}
