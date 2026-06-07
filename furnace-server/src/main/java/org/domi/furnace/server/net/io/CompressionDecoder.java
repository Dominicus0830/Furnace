// 인바운드 프레임 payload를 VarInt 길이로 판별해 zlib 해제(0이면 비압축)하는 디코더
package org.domi.furnace.server.net.io;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import java.util.zip.Inflater;
import org.domi.furnace.server.net.protocol.ProtocolException;

public final class CompressionDecoder extends ByteToMessageDecoder {

    private static final int MAX = 2 * 1024 * 1024;
    private final Inflater inflater = new Inflater();
    @SuppressWarnings("unused")
    private final int threshold;

    public CompressionDecoder(int threshold) {
        this.threshold = threshold;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (!in.isReadable()) return;
        int dataLength = Types.readVarInt(in);
        if (dataLength == 0) {
            out.add(in.readRetainedSlice(in.readableBytes())); // 비압축
            return;
        }
        if (dataLength > MAX) {
            throw new ProtocolException("Decompressed size too large: " + dataLength);
        }
        byte[] compressed = new byte[in.readableBytes()];
        in.readBytes(compressed);
        inflater.setInput(compressed);
        byte[] result = new byte[dataLength];
        try {
            int n = inflater.inflate(result);
            if (n != dataLength) {
                throw new ProtocolException("Decompressed size mismatch: " + n + " != " + dataLength);
            }
        } catch (java.util.zip.DataFormatException e) {
            throw new ProtocolException("Bad zlib data: " + e.getMessage());
        } finally {
            inflater.reset();
        }
        out.add(ctx.alloc().buffer(dataLength).writeBytes(result));
    }
}
