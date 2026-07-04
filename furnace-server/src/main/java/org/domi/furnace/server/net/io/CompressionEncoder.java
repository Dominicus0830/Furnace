// 아웃바운드 프레임 payload를 임계값 이상이면 zlib 압축, 미만이면 원본+길이0으로 감싸는 인코더
package org.domi.furnace.server.net.io;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.zip.Deflater;

public final class CompressionEncoder extends MessageToByteEncoder<ByteBuf> {

    private final int threshold;
    private final Deflater deflater = new Deflater();
    private byte[] inputScratch = new byte[0]; // 채널당 재사용, 필요 시 성장(Netty encoder는 이벤트루프 단일스레드 순차 처리라 채널 간 공유 없이 안전)

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
        if (inputScratch.length < dataLen) {
            inputScratch = new byte[dataLen];
        }
        byte[] input = inputScratch;
        msg.readBytes(input, 0, dataLen);
        Types.writeVarInt(out, dataLen); // 압축 해제 후 크기
        deflater.setInput(input, 0, dataLen);
        deflater.finish();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int n = deflater.deflate(buffer);
            out.writeBytes(buffer, 0, n);
        }
        deflater.reset();
    }
}
