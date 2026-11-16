// 아웃바운드 프레임 payload 앞에 VarInt 길이 프리픽스를 붙이는 인코더
package org.domi.furnace.server.net.io;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.domi.furnace.server.VanillaPort;

@VanillaPort("net.minecraft.network.Varint21LengthFieldPrepender")
public final class FrameEncoder extends MessageToByteEncoder<ByteBuf> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
        Types.writeVarInt(out, msg.readableBytes());
        out.writeBytes(msg);
    }
}
