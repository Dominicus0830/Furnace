package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import java.util.function.Function;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.BundlerInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;

/**
 * 프로토콜 정보 shim(Mojang 이름, N3 1단계). 단계(id)·방향(flow)·패킷 번호 코덱(codec)을 가진다.
 * {@link Unbound} 는 바이트 버퍼 만드는 법을 아직 안 받은 "틀"이고, bind 로 {@link ProtocolInfo} 가 된다.
 */
public interface ProtocolInfo<T extends PacketListener> {

    ConnectionProtocol id();

    PacketFlow flow();

    StreamCodec<ByteBuf, Packet<? super T>> codec();

    BundlerInfo bundlerInfo();

    interface Unbound<T extends PacketListener, B extends ByteBuf> {

        ProtocolInfo<T> bind(Function<ByteBuf, B> bufferFactory);

        ConnectionProtocol id();

        PacketFlow flow();

        void listPackets(PacketVisitor visitor);

        @FunctionalInterface
        interface PacketVisitor {
            void accept(PacketType<?> type, int id);
        }
    }
}
