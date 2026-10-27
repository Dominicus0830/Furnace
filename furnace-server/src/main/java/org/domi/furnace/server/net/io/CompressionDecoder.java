// 인바운드 프레임 payload를 VarInt 길이로 판별해 zlib 해제(0이면 비압축)하는 디코더
package org.domi.furnace.server.net.io;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import java.util.zip.Inflater;
import org.domi.furnace.server.net.protocol.ProtocolException;

public final class CompressionDecoder extends ByteToMessageDecoder {

    // 바닐라 상한은 8 MiB(CompressionDecoder.MAXIMUM_UNCOMPRESSED_LENGTH)지만 C→S 패킷 중 가장 큰 것도 1 MiB(로그인 커스텀 응답)라
    // 더 좁은 2 MiB 를 유지한다 — 압축 해제 증폭으로 채널당 잡는 버퍼를 줄인다.
    private static final int MAX = 2 * 1024 * 1024;
    private final Inflater inflater = new Inflater();
    private byte[] inputScratch = new byte[0]; // 연속 nio 버퍼가 아닐 때만 쓰는 채널당 재사용 배열
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
        // 바닐라 CompressionDecoder(서버는 validateDecompressed=true): 임계값 미만인데 압축한 패킷은 거부한다
        if (dataLength < threshold) {
            throw new ProtocolException("Badly compressed packet - size of " + dataLength + " is below server threshold of " + threshold);
        }
        if (dataLength > MAX) {
            throw new ProtocolException("Decompressed size too large: " + dataLength);
        }
        int len = in.readableBytes();
        if (in.nioBufferCount() == 1) {
            inflater.setInput(in.nioBuffer(in.readerIndex(), len));
        } else {
            if (inputScratch.length < len) inputScratch = new byte[len];
            in.getBytes(in.readerIndex(), inputScratch, 0, len);
            inflater.setInput(inputScratch, 0, len);
        }
        in.skipBytes(len);
        ByteBuf result = ctx.alloc().buffer(dataLength, dataLength);
        boolean ok = false;
        try {
            int n = 0;
            while (n < dataLength) {
                int r = inflater.inflate(result.internalNioBuffer(n, dataLength - n));
                if (r == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) break;
                n += r;
            }
            if (n != dataLength) {
                throw new ProtocolException("Decompressed size mismatch: " + n + " != " + dataLength);
            }
            result.writerIndex(dataLength);
            ok = true;
        } catch (java.util.zip.DataFormatException e) {
            throw new ProtocolException("Bad zlib data: " + e.getMessage());
        } finally {
            inflater.reset();
            if (!ok) result.release();
        }
        out.add(result);
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) {
        inflater.end();   // 네이티브 zlib 상태를 GC 를 기다리지 않고 바로 돌려준다
    }
}
