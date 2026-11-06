// 인바운드 바이트스트림을 VarInt 길이 프리픽스 단위 프레임으로 분리하는 디코더
package org.domi.furnace.server.net.io;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import org.domi.furnace.server.net.protocol.ProtocolException;

public final class FrameDecoder extends ByteToMessageDecoder {

    private static final int MAX_PACKET = 2 * 1024 * 1024; // 2 MiB 안전 상한(바닐라 Varint21FrameDecoder 의 21비트 길이와 같은 규모)

    // 단계별 프레임 상한(채널 이벤트루프 전용). 로그인 전 단계에서 2 MiB 까지 버퍼링되지 않게 ConnectionHandler 가 줄인다.
    private int maxLength = MAX_PACKET;

    /** 지금 단계에서 받을 프레임 최대 길이(바이트). 2 MiB 를 넘길 수 없다. */
    public void maxLength(int max) { this.maxLength = Math.min(max, MAX_PACKET); }

    public int maxLength() { return maxLength; }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        in.markReaderIndex();
        int length = 0;
        int position = 0;
        while (true) {
            if (!in.isReadable()) {
                in.resetReaderIndex();
                return;
            }
            byte b = in.readByte();
            length |= (b & 0x7F) << position;
            position += 7;
            if (position > 35) {
                throw new ProtocolException("Frame length VarInt too long");
            }
            if ((b & 0x80) == 0) break;
        }
        if (length < 0 || length > maxLength) {
            throw new ProtocolException("Frame length out of bounds: " + length);
        }
        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }
        out.add(in.readRetainedSlice(length));
    }
}
