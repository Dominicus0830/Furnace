// 아웃바운드 프레임 payload를 임계값 이상이면 zlib 압축, 미만이면 원본+길이0으로 감싸는 인코더
package org.domi.furnace.server.net.io;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.MessageToByteEncoder;
import java.nio.ByteBuffer;
import java.util.zip.Deflater;

public final class CompressionEncoder extends MessageToByteEncoder<ByteBuf> {

    /** 네트워크 zlib 레벨(-Dfurnace.network.compressionLevel, 기본 = zlib 기본 6, 바닐라와 같음). 0~9 또는 -1. */
    public static final int LEVEL = level(Integer.getInteger("furnace.network.compressionLevel", Deflater.DEFAULT_COMPRESSION));

    static int level(int v) {
        if (v < -1 || v > 9) throw new IllegalArgumentException("furnace.network.compressionLevel 범위 밖: " + v);
        return v;
    }

    /** 이미 이 인코더 형식([VarInt dataLen][zlib 또는 원본])으로 만든 payload. 다시 압축하지 않고 넘긴다. */
    public record Precompressed(ByteBuf payload) {}

    private final int threshold;
    private final int level;
    private final Deflater deflater;
    private byte[] inputScratch = new byte[0]; // 연속 nio 버퍼가 아닐 때만 쓰는 채널당 재사용 배열

    public CompressionEncoder(int threshold) {
        this(threshold, LEVEL);
    }

    public CompressionEncoder(int threshold, int level) {
        this.threshold = threshold;
        this.level = level(level);
        this.deflater = new Deflater(level);
    }

    public int threshold() { return threshold; }
    public int level() { return level; }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof Precompressed p) {
            ctx.write(p.payload(), promise);
            return;
        }
        super.write(ctx, msg, promise);
    }

    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, ByteBuf msg, boolean preferDirect) {
        int len = msg.readableBytes();
        int hint = len < threshold ? len + 5 : (len >>> 2) + 64;   // 크기 힌트일 뿐, 모자라면 늘린다
        return preferDirect ? ctx.alloc().ioBuffer(hint) : ctx.alloc().heapBuffer(hint);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
        int dataLen = msg.readableBytes();
        if (dataLen < threshold) {
            Types.writeVarInt(out, 0); // 비압축 표시
            out.writeBytes(msg);
            return;
        }
        Types.writeVarInt(out, dataLen); // 압축 해제 후 크기
        if (msg.nioBufferCount() == 1) {
            deflater.setInput(msg.nioBuffer(msg.readerIndex(), dataLen));
        } else {
            if (inputScratch.length < dataLen) inputScratch = new byte[dataLen];
            msg.getBytes(msg.readerIndex(), inputScratch, 0, dataLen);
            deflater.setInput(inputScratch, 0, dataLen);
        }
        msg.skipBytes(dataLen);
        deflateTo(out);
    }

    private void deflateTo(ByteBuf out) {
        deflater.finish();
        try {
            while (!deflater.finished()) {
                out.ensureWritable(8192);
                ByteBuffer dst = out.internalNioBuffer(out.writerIndex(), out.writableBytes());
                int n = deflater.deflate(dst);
                out.writerIndex(out.writerIndex() + n);
            }
        } finally {
            deflater.reset();
        }
    }

    /**
     * encode() 가 같은 패킷에 대해 내보낼 바이트와 똑같은 payload 를 만든다(공유 청크 프레임용).
     * 이 인코더의 Deflater 를 쓰므로 채널 이벤트 루프 스레드에서만 부른다.
     */
    public byte[] encodeToBytes(byte[] packet) {
        ByteBuf out = io.netty.buffer.Unpooled.buffer(packet.length < threshold ? packet.length + 5 : (packet.length >>> 2) + 64);
        try {
            if (packet.length < threshold) {
                Types.writeVarInt(out, 0);
                out.writeBytes(packet);
            } else {
                Types.writeVarInt(out, packet.length);
                deflater.setInput(packet);
                deflateTo(out);
            }
            byte[] r = new byte[out.readableBytes()];
            out.getBytes(out.readerIndex(), r);
            return r;
        } finally {
            out.release();
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        try {
            super.handlerRemoved(ctx);
        } finally {
            deflater.end();   // 네이티브 zlib 상태를 GC 를 기다리지 않고 바로 돌려준다
        }
    }
}
