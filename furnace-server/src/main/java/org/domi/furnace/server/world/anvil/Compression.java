// Anvil 청크 압축(zlib=deflate, type 2 / gzip=type 1) 헬퍼.
package org.domi.furnace.server.world.anvil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public final class Compression {

    private Compression() {}

    /** region 파일 zlib 레벨(-Dfurnace.region.compressionLevel, 기본 = zlib 기본 6, 바닐라와 같음). */
    public static final int LEVEL = level(Integer.getInteger("furnace.region.compressionLevel", java.util.zip.Deflater.DEFAULT_COMPRESSION));

    private static int level(int v) {
        if (v < -1 || v > 9) throw new IllegalArgumentException("furnace.region.compressionLevel 범위 밖: " + v);
        return v;
    }

    public static byte[] deflate(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        java.util.zip.Deflater def = new java.util.zip.Deflater(LEVEL);
        try (DeflaterOutputStream d = new DeflaterOutputStream(bos, def)) {
            d.write(data);
        } finally {
            def.end();   // 직접 만든 Deflater 는 스트림이 닫아 주지 않는다
        }
        return bos.toByteArray();
    }

    public static byte[] decompress(int type, byte[] data) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        java.io.InputStream dec = switch (type) {
            case 1 -> new GZIPInputStream(in);
            case 2 -> new InflaterInputStream(in);
            default -> throw new IOException("미지원 압축타입: " + type);
        };
        dec.transferTo(out);
        dec.close();
        return out.toByteArray();
    }
}
