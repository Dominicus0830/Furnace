// region 압축 type 4(lz4). 바닐라 RegionFileVersion(dwr)의 lz4 는 lz4-java 의 기본 생성자 new LZ4BlockOutputStream(out)·
// new LZ4BlockInputStream(in) 이다(2026-11-06 1.21.1 바이트코드로 확인 — 블록 64KB·빠른 압축기·XXHash32 씨앗 기본값).
// 형식은 lz4-java 1.8.0 이 만든 바이트로 대조 시험함. 외부 라이브러리 없이 직접 구현한다.
// 블록 = "LZ4Block"(8) + 토큰(방식 0x10 RAW / 0x20 LZ4 | 크기 레벨) + 압축 길이 + 원래 길이 + XXHash32 체크섬(모두 LE int). 길이 0 블록이 끝.
package org.domi.furnace.server.world.anvil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class Lz4Block {

    private Lz4Block() {}

    private static final byte[] MAGIC = {'L', 'Z', '4', 'B', 'l', 'o', 'c', 'k'};
    private static final int HEADER = MAGIC.length + 1 + 12;
    private static final int METHOD_RAW = 0x10, METHOD_LZ4 = 0x20;
    private static final int LEVEL_BASE = 10;
    /** LZ4BlockOutputStream 기본 블록 크기 64KB(레벨 6). */
    private static final int BLOCK_SIZE = 1 << 16;
    /** StreamingXXHash32 기본 씨앗. asChecksum 은 하위 28비트만 쓴다. */
    private static final int SEED = 0x9747b28c;

    // ---------------- 해제 ----------------

    static byte[] decompress(byte[] in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length * 3);
        int p = 0;
        while (true) {
            if (p + HEADER > in.length) throw new IOException("lz4: 블록 헤더가 잘림");
            for (int i = 0; i < MAGIC.length; i++) {
                if (in[p + i] != MAGIC[i]) throw new IOException("lz4: 매직 불일치");
            }
            int token = in[p + 8] & 0xFF;
            int method = token & 0xF0;
            int maxLen = 1 << ((token & 0x0F) + LEVEL_BASE);
            int compLen = le32(in, p + 9), origLen = le32(in, p + 13), check = le32(in, p + 17);
            p += HEADER;
            if (origLen < 0 || origLen > maxLen || compLen < 0 || (method != METHOD_RAW && method != METHOD_LZ4)
                    || (method == METHOD_RAW && compLen != origLen) || (origLen == 0) != (compLen == 0)
                    || compLen > in.length - p) {
                throw new IOException("lz4: 블록 헤더 손상");
            }
            if (origLen == 0) {
                if (check != 0) throw new IOException("lz4: 끝 블록 손상");
                return out.toByteArray();   // stopOnEmptyBlock — 뒤는 읽지 않는다
            }
            byte[] block;
            if (method == METHOD_RAW) {
                block = java.util.Arrays.copyOfRange(in, p, p + compLen);
            } else {
                block = new byte[origLen];
                if (decodeBlock(in, p, compLen, block) != origLen) throw new IOException("lz4: 해제 길이 불일치");
            }
            if (checksum(block, 0, origLen) != check) throw new IOException("lz4: 체크섬 불일치");
            out.write(block, 0, origLen);
            p += compLen;
        }
    }

    /** LZ4 원시 블록 해제. 반환 = 쓴 바이트 수. */
    static int decodeBlock(byte[] src, int sp, int len, byte[] dst) throws IOException {
        int end = sp + len, dp = 0;
        try {
            while (true) {
                int token = src[sp++] & 0xFF;
                int lit = token >>> 4;
                if (lit == 15) {
                    int b;
                    do { b = src[sp++] & 0xFF; lit += b; } while (b == 255);
                }
                if (lit > end - sp || lit > dst.length - dp) throw new IOException("lz4: 리터럴 범위 밖");
                System.arraycopy(src, sp, dst, dp, lit);
                sp += lit;
                dp += lit;
                if (sp == end) return dp;   // 마지막 시퀀스는 리터럴만
                int off = (src[sp] & 0xFF) | (src[sp + 1] & 0xFF) << 8;
                sp += 2;
                int m = token & 0x0F;
                if (m == 15) {
                    int b;
                    do { b = src[sp++] & 0xFF; m += b; } while (b == 255);
                }
                m += 4;
                int from = dp - off;
                if (off == 0 || from < 0 || m > dst.length - dp) throw new IOException("lz4: 일치 범위 밖");
                for (int i = 0; i < m; i++) dst[dp + i] = dst[from + i];   // 겹침 복사는 바이트 단위
                dp += m;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IOException("lz4: 블록이 잘림", e);
        }
    }

    // ---------------- 압축 ----------------

    static byte[] compress(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 2 + 64);
        int level = 32 - Integer.numberOfLeadingZeros(BLOCK_SIZE - 1) - LEVEL_BASE;
        byte[] tmp = new byte[BLOCK_SIZE + BLOCK_SIZE / 255 + 16];
        for (int p = 0; p < data.length; p += BLOCK_SIZE) {
            int n = Math.min(BLOCK_SIZE, data.length - p);
            int c = encodeBlock(data, p, n, tmp);
            int check = checksum(data, p, n);
            if (c >= n) {   // 압축 이득이 없으면 RAW(LZ4BlockOutputStream 과 같음)
                writeHeader(out, METHOD_RAW | level, n, n, check);
                out.write(data, p, n);
            } else {
                writeHeader(out, METHOD_LZ4 | level, c, n, check);
                out.write(tmp, 0, c);
            }
        }
        writeHeader(out, METHOD_RAW | level, 0, 0, 0);   // finish() 의 끝 블록
        return out.toByteArray();
    }

    private static void writeHeader(ByteArrayOutputStream out, int token, int comp, int orig, int check) {
        out.write(MAGIC, 0, MAGIC.length);
        out.write(token);
        byte[] b = new byte[12];
        putLe32(b, 0, comp);
        putLe32(b, 4, orig);
        putLe32(b, 8, check);
        out.write(b, 0, 12);
    }

    private static final int MIN_MATCH = 4, LAST_LITERALS = 5, MF_LIMIT = 12, HASH_LOG = 12;

    /** 탐욕 해시 압축(바이트는 lz4-java 와 달라도 되고, 표준 해제기로 풀리면 된다). 반환 = 압축 길이. */
    static int encodeBlock(byte[] src, int off, int len, byte[] dst) {
        int dp = 0, anchor = off, end = off + len;
        if (len >= MF_LIMIT + 1) {
            int[] table = new int[1 << HASH_LOG];
            java.util.Arrays.fill(table, -1);
            int mfLimit = end - MF_LIMIT, matchLimit = end - LAST_LITERALS;
            int ip = off;
            while (ip < mfLimit) {
                int v = le32(src, ip);
                int h = (v * -1640531535) >>> (32 - HASH_LOG);
                int ref = table[h];
                table[h] = ip;
                if (ref < 0 || ip - ref > 0xFFFF || le32(src, ref) != v) {
                    ip++;
                    continue;
                }
                int m = MIN_MATCH;
                while (ip + m < matchLimit && src[ref + m] == src[ip + m]) m++;
                dp = writeSequence(src, anchor, ip - anchor, dst, dp, ip - ref, m);
                ip += m;
                anchor = ip;
            }
        }
        return writeSequence(src, anchor, end - anchor, dst, dp, 0, 0);
    }

    /** 시퀀스 하나. matchLen 0 이면 마지막(리터럴만). */
    private static int writeSequence(byte[] src, int lp, int lit, byte[] dst, int dp, int offset, int matchLen) {
        int ml = matchLen == 0 ? 0 : matchLen - MIN_MATCH;
        int tokenAt = dp++;
        dst[tokenAt] = (byte) ((Math.min(lit, 15) << 4) | Math.min(ml, 15));
        dp = writeLength(dst, dp, lit);
        System.arraycopy(src, lp, dst, dp, lit);
        dp += lit;
        if (matchLen == 0) return dp;
        dst[dp++] = (byte) offset;
        dst[dp++] = (byte) (offset >>> 8);
        return writeLength(dst, dp, ml);
    }

    private static int writeLength(byte[] dst, int dp, int n) {
        if (n < 15) return dp;
        n -= 15;
        while (n >= 255) { dst[dp++] = (byte) 255; n -= 255; }
        dst[dp++] = (byte) n;
        return dp;
    }

    // ---------------- XXHash32 ----------------

    private static final int P1 = 0x9E3779B1, P2 = 0x85EBCA77, P3 = 0xC2B2AE3D, P4 = 0x27D4EB2F, P5 = 0x165667B1;

    /** lz4-java StreamingXXHash32(SEED).asChecksum().getValue() 와 같은 값(하위 28비트). */
    static int checksum(byte[] b, int off, int len) {
        return xxhash32(b, off, len, SEED) & 0x0FFFFFFF;
    }

    static int xxhash32(byte[] b, int off, int len, int seed) {
        int p = off, end = off + len, h;
        if (len >= 16) {
            int v1 = seed + P1 + P2, v2 = seed + P2, v3 = seed, v4 = seed - P1;
            int limit = end - 16;
            do {
                v1 = Integer.rotateLeft(v1 + le32(b, p) * P2, 13) * P1;
                v2 = Integer.rotateLeft(v2 + le32(b, p + 4) * P2, 13) * P1;
                v3 = Integer.rotateLeft(v3 + le32(b, p + 8) * P2, 13) * P1;
                v4 = Integer.rotateLeft(v4 + le32(b, p + 12) * P2, 13) * P1;
                p += 16;
            } while (p <= limit);
            h = Integer.rotateLeft(v1, 1) + Integer.rotateLeft(v2, 7) + Integer.rotateLeft(v3, 12) + Integer.rotateLeft(v4, 18);
        } else {
            h = seed + P5;
        }
        h += len;
        while (p + 4 <= end) {
            h = Integer.rotateLeft(h + le32(b, p) * P3, 17) * P4;
            p += 4;
        }
        while (p < end) {
            h = Integer.rotateLeft(h + (b[p] & 0xFF) * P5, 11) * P1;
            p++;
        }
        h ^= h >>> 15;
        h *= P2;
        h ^= h >>> 13;
        h *= P3;
        h ^= h >>> 16;
        return h;
    }

    private static int le32(byte[] b, int p) {
        return (b[p] & 0xFF) | (b[p + 1] & 0xFF) << 8 | (b[p + 2] & 0xFF) << 16 | (b[p + 3] & 0xFF) << 24;
    }

    private static void putLe32(byte[] b, int p, int v) {
        b[p] = (byte) v;
        b[p + 1] = (byte) (v >>> 8);
        b[p + 2] = (byte) (v >>> 16);
        b[p + 3] = (byte) (v >>> 24);
    }
}
