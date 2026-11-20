package org.spigotmc;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.nbt.NbtAccounter;

/** Spigot: 읽으려는 바이트 수를 먼저 {@link NbtAccounter} 에 센다(Paper 는 네트워크 버퍼 스트림에서 NBT 를 읽을 때 쓴다). */
public class LimitStream extends FilterInputStream {

    private final NbtAccounter limit;

    public LimitStream(InputStream is, NbtAccounter limit) {
        super(is);
        this.limit = limit;
    }

    @Override
    public int read() throws IOException {
        limit.accountBytes(1);
        return super.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        limit.accountBytes(b.length);
        return super.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        limit.accountBytes(len);
        return super.read(b, off, len);
    }
}
