package org.domi.furnace.compat.nms;

import io.netty.buffer.ByteBuf;
import java.util.function.Function;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.codec.IdDispatchCodec;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.BundlerInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;

/**
 * 생성된 {@code *Protocols} shim 이 쓰는 도우미(N3 1단계). 패킷 종류 목록(번호 순서)에서 바닐라 모양의 {@link ProtocolInfo.Unbound} 와
 * 묶은 {@link ProtocolInfo} 를 만든다. 코덱의 패킷 바이트 변환은 아직 연결되지 않았다(쓰면 {@link UnsupportedNmsCall}) —
 * 지금은 "어느 번호가 어느 패킷 종류인가" 표만 바닐라와 같다.
 */
public final class ShimProtocols {

    private ShimProtocols() {}

    public static <T extends PacketListener> ProtocolInfo.Unbound<T, ByteBuf> unbound(ConnectionProtocol id, PacketFlow flow, PacketType<?>... types) {
        return new UnboundImpl<>(id, flow, types);
    }

    private static final class UnboundImpl<T extends PacketListener> implements ProtocolInfo.Unbound<T, ByteBuf> {
        private final ConnectionProtocol id;
        private final PacketFlow flow;
        private final PacketType<?>[] types;

        UnboundImpl(ConnectionProtocol id, PacketFlow flow, PacketType<?>[] types) {
            this.id = id;
            this.flow = flow;
            this.types = types.clone();
        }

        @Override
        public ProtocolInfo<T> bind(Function<ByteBuf, ByteBuf> bufferFactory) {
            IdDispatchCodec.Builder<ByteBuf, Packet<? super T>, PacketType<? extends Packet<? super T>>> builder = IdDispatchCodec.builder(p -> null);
            for (PacketType<?> t : types) {
                @SuppressWarnings("unchecked")
                PacketType<? extends Packet<? super T>> type = (PacketType<? extends Packet<? super T>>) t;
                builder.add(type, unsupportedSerializer(t));
            }
            @SuppressWarnings("unchecked")
            StreamCodec<ByteBuf, Packet<? super T>> codec = (StreamCodec<ByteBuf, Packet<? super T>>) (StreamCodec<?, ?>) builder.build();
            return new BoundImpl<>(id, flow, codec);
        }

        @Override public ConnectionProtocol id() { return id; }

        @Override public PacketFlow flow() { return flow; }

        @Override
        public void listPackets(PacketVisitor visitor) {
            for (int i = 0; i < types.length; i++) visitor.accept(types[i], i);
        }
    }

    private static final class BoundImpl<T extends PacketListener> implements ProtocolInfo<T> {
        private final ConnectionProtocol id;
        private final PacketFlow flow;
        private final StreamCodec<ByteBuf, Packet<? super T>> codec;

        BoundImpl(ConnectionProtocol id, PacketFlow flow, StreamCodec<ByteBuf, Packet<? super T>> codec) {
            this.id = id;
            this.flow = flow;
            this.codec = codec;
        }

        @Override public ConnectionProtocol id() { return id; }

        @Override public PacketFlow flow() { return flow; }

        @Override public StreamCodec<ByteBuf, Packet<? super T>> codec() { return codec; }

        @Override public BundlerInfo bundlerInfo() { return BundlerInfo.EMPTY; }
    }

    private static <B, V> StreamCodec<B, V> unsupportedSerializer(PacketType<?> type) {
        return StreamCodec.of(
                (out, v) -> { throw new UnsupportedNmsCall("packet codec " + type, "패킷 바이트 변환은 아직 연결되지 않았다(ProtocolLib 다리 3단계)"); },
                in -> { throw new UnsupportedNmsCall("packet codec " + type, "패킷 바이트 변환은 아직 연결되지 않았다(ProtocolLib 다리 3단계)"); });
    }
}
