// Anvil 청크 압축(gzip=type 1 / zlib=deflate=type 2 / none=type 3 / lz4=type 4 / zstd=type 5, 바닐라 아님) 헬퍼. 쓰기 방식은 server.properties region-file-compression 이
// 고른다(바닐라 RegionFileVersion.configure 와 같이 프로세스 전역 하나). lz4 는 lz4-java 블록 스트림 형식을 직접 구현한 Lz4Block 을 쓴다.
package org.domi.furnace.server.world.anvil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

public final class Compression {

    private Compression() {}

    public static final int GZIP = 1, DEFLATE = 2, NONE = 3, LZ4 = 4,
            /** 바닐라에 없는 종류(Furnace 전용). 이걸로 쓴 region 파일은 바닐라·Paper 가 못 읽는다 — {@link RegionConvert} 로 되돌린다. */
            ZSTD = 5;
    private static volatile int writeType = DEFLATE;

    /** 새로 쓰는 청크의 압축 종류. */
    public static int writeType() { return writeType; }

    /** region-file-compression 값을 적용한다. 모르는 값이면 거짓 — 이전 설정을 유지한다. */
    public static boolean configure(String name) {
        Integer t = switch (name) {
            case "deflate" -> DEFLATE;
            case "gzip" -> GZIP;
            case "none" -> NONE;
            case "lz4" -> LZ4;
            case "zstd" -> ZSTD;
            default -> null;
        };
        if (t == null) return false;
        writeType = t;
        return true;
    }

    /** 시험·초기화용. */
    public static void resetWriteType() { writeType = DEFLATE; }

    /** 지금 설정한 종류로 압축. */
    public static byte[] compress(int type, byte[] data) throws IOException {
        return switch (type) {
            case GZIP -> {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (java.util.zip.GZIPOutputStream g = new java.util.zip.GZIPOutputStream(bos)) {
                    g.write(data);
                }
                yield bos.toByteArray();
            }
            case NONE -> data.clone();
            case LZ4 -> Lz4Block.compress(data);
            case ZSTD -> zstd(data);
            default -> deflate(data);
        };
    }

    /** region 파일 zlib 레벨(-Dfurnace.region.compressionLevel, 기본 = zlib 기본 6, 바닐라와 같음). */
    public static final int LEVEL = level(Integer.getInteger("furnace.region.compressionLevel", java.util.zip.Deflater.DEFAULT_COMPRESSION));

    private static int level(int v) {
        if (v < -1 || v > 9) throw new IllegalArgumentException("furnace.region.compressionLevel 범위 밖: " + v);
        return v;
    }

    public static byte[] deflate(byte[] data) throws IOException {
        // DeflaterOutputStream 과 같은 압축 스트림(같은 레벨·한 번에 setInput·finish)을 512B 버퍼 대신 큰 버퍼로 바로 뽑는다.
        java.util.zip.Deflater def = new java.util.zip.Deflater(LEVEL);
        try {
            // 호출 순서도 DeflaterOutputStream 과 같다: 입력을 다 먹을 때까지 NO_FLUSH, 그다음 finish.
            def.setInput(data);
            byte[] out = new byte[Math.max(64, data.length / 4)];
            int n = 0;
            while (!def.needsInput()) {
                if (n == out.length) out = java.util.Arrays.copyOf(out, out.length * 2);
                n += def.deflate(out, n, out.length - n);
            }
            def.finish();
            while (!def.finished()) {
                if (n == out.length) out = java.util.Arrays.copyOf(out, out.length * 2);
                n += def.deflate(out, n, out.length - n);
            }
            return n == out.length ? out : java.util.Arrays.copyOf(out, n);
        } finally {
            def.end();
        }
    }

    public static byte[] decompress(int type, byte[] data) throws IOException {
        if (type == LZ4) return Lz4Block.decompress(data);
        if (type == ZSTD) return unzstd(data);
        if (type == DEFLATE) return inflate(data);
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        java.io.InputStream dec = switch (type) {
            case 1 -> new GZIPInputStream(in);
            case 3 -> in;
            default -> throw new IOException("미지원 압축타입: " + type);
        };
        dec.transferTo(out);
        dec.close();
        return out.toByteArray();
    }

    /**
     * zlib 본문 풀기. InflaterInputStream 처럼 첫 zlib 스트림 끝(또는 사전 요구)에서 멈추고(뒤 쓰레기는 무시), 본문이 모자라면 EOFException,
     * 깨졌으면 ZipException. 512B 입력 버퍼·ByteArrayOutputStream 키우기 대신 입력 전체를 한 번에 넣는다.
     */
    static byte[] inflate(byte[] data) throws IOException {
        java.util.zip.Inflater inf = new java.util.zip.Inflater();
        try {
            inf.setInput(data);
            byte[] out = new byte[Math.max(256, Math.min(data.length * 8, 1 << 24))];
            int n = 0;
            while (!inf.finished()) {
                if (n == out.length) out = java.util.Arrays.copyOf(out, out.length * 2);
                int got;
                try {
                    got = inf.inflate(out, n, out.length - n);
                } catch (java.util.zip.DataFormatException e) {
                    String m = e.getMessage();
                    throw new java.util.zip.ZipException(m != null ? m : "Invalid ZLIB data format");
                }
                n += got;
                if (got == 0 && !inf.finished()) {
                    if (inf.needsDictionary()) break;   // InflaterInputStream 처럼 거기서 끝(받은 데까지)
                    if (inf.needsInput()) throw new java.io.EOFException("Unexpected end of ZLIB input stream");
                }
            }
            return n == out.length ? out : java.util.Arrays.copyOf(out, n);
        } finally {
            inf.end();
        }
    }

    /** zstd 프레임(내용 크기를 헤더에 담는다). 순수 자바(aircompressor). */
    static byte[] zstd(byte[] data) {
        io.airlift.compress.zstd.ZstdCompressor c = new io.airlift.compress.zstd.ZstdCompressor();
        byte[] out = new byte[c.maxCompressedLength(data.length)];
        int n = c.compress(data, 0, data.length, out, 0, out.length);
        return java.util.Arrays.copyOf(out, n);
    }

    static byte[] unzstd(byte[] data) throws IOException {
        io.airlift.compress.zstd.ZstdDecompressor d = new io.airlift.compress.zstd.ZstdDecompressor();
        try {
            long size = io.airlift.compress.zstd.ZstdDecompressor.getDecompressedSize(data, 0, data.length);
            if (size < 0 || size > (1 << 28)) throw new IOException("zstd: 해제 크기 이상: " + size);
            byte[] out = new byte[(int) size];
            int n = d.decompress(data, 0, data.length, out, 0, out.length);
            if (n != out.length) throw new IOException("zstd: 해제 길이 불일치");
            return out;
        } catch (io.airlift.compress.MalformedInputException | IllegalArgumentException | IndexOutOfBoundsException e) {
            throw new IOException("zstd: 본문이 깨졌다", e);
        }
    }
}
