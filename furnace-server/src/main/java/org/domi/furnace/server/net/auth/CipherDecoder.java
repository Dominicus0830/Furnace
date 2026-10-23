// 수신 바이트 복호화(AES/CFB8). 파이프라인 맨 앞 — 프레임 분리 전에 푼다.
package org.domi.furnace.server.net.auth;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;
import javax.crypto.Cipher;

public final class CipherDecoder extends MessageToMessageDecoder<ByteBuf> {

    private final Cipher cipher;

    public CipherDecoder(Cipher cipher) {
        this.cipher = cipher;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int n = in.readableBytes();
        byte[] src = new byte[n];
        in.readBytes(src);
        ByteBuf dst = ctx.alloc().heapBuffer(cipher.getOutputSize(n));
        int written = cipher.update(src, 0, n, dst.array(), dst.arrayOffset());
        dst.writerIndex(written);
        out.add(dst);
    }
}
