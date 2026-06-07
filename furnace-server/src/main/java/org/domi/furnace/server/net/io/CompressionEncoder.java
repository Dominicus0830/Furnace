// 아웃바운드 프레임 payload를 임계값 이상이면 zlib 압축, 미만이면 원본+길이0으로 감싸는 인코더
package org.domi.furnace.server.net.io;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.zip.Deflater;

public final class CompressionEncoder extends MessageToByteEncoder<ByteBuf> {

    private final int threshold;
    private final Deflater deflater = new Deflater();

    public CompressionEncoder(int threshold) {
        this.threshold = threshold;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
        int dataLen = msg.readableBytes();
        if (dataLen < threshold) {
            Types.writeVarInt(out, 0); // 비압축 표시
            out.writeBytes(msg);
            return;
        }
        byte[] input = new byte[dataLen];
        msg.readBytes(input);
        Types.writeVarInt(out, dataLen); // 압축 해제 후 크기
        deflater.setInput(input);
        deflater.finish();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int n = deflater.deflate(buffer);
            out.writeBytes(buffer, 0, n);
        }
        deflater.reset();
    }
}
