// 송신 바이트 암호화(AES/CFB8). 파이프라인 맨 앞(송신 방향으로는 마지막) — 프레임·압축 뒤의 바이트를 감싼다.
package org.domi.furnace.server.net.auth;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import javax.crypto.Cipher;

public final class CipherEncoder extends MessageToByteEncoder<ByteBuf> {

    private final Cipher cipher;

    public CipherEncoder(Cipher cipher) {
        this.cipher = cipher;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
        int n = in.readableBytes();
        byte[] src = new byte[n];
        in.readBytes(src);
        int size = cipher.getOutputSize(n);
        out.ensureWritable(size);
        byte[] dst = new byte[size];
        int written = cipher.update(src, 0, n, dst, 0);
        out.writeBytes(dst, 0, written);
    }
}
