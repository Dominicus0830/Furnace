// NbtIo.toBytes / read(byte[]) 가 DataOutputStream·DataInputStream 경로와 같은 바이트·같은 결과·같은 예외 종류를 내는지.
package org.domi.furnace.server.net.io.nbt;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class NbtByteArrayParityTest {

    private static byte[] legacyBytes(NbtCompound c, String name) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        NbtIo.write(new DataOutputStream(bos), c, name);
        return bos.toByteArray();
    }

    private static NbtCompound legacyRead(byte[] b) throws Exception {
        return NbtIo.read(new DataInputStream(new ByteArrayInputStream(b)));
    }

    private static String randomString(SplittableRandom r) {
        int len = r.nextInt(4) == 0 ? 0 : r.nextInt(40);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            int kind = r.nextInt(10);
            char c = kind < 6 ? (char) (0x20 + r.nextInt(0x5F))   // ASCII
                    : kind == 6 ? '\0'                             // NUL 은 2바이트
                    : kind == 7 ? (char) (0x80 + r.nextInt(0x780)) // 2바이트
                    : kind == 8 ? (char) (0x800 + r.nextInt(0xD000)) // 3바이트(한글 포함)
                    : (char) (0xD800 + r.nextInt(0x800));          // 짝 없는 대리 문자
            sb.append(c);
        }
        return sb.toString();
    }

    private static Object randomValue(SplittableRandom r, byte type, int depth) {
        return switch (type) {
            case NbtIo.TAG_BYTE -> (byte) r.nextInt();
            case NbtIo.TAG_SHORT -> (short) r.nextInt();
            case NbtIo.TAG_INT -> r.nextInt();
            case NbtIo.TAG_LONG -> r.nextLong();
            case NbtIo.TAG_FLOAT -> r.nextInt(8) == 0 ? Float.NaN : Float.intBitsToFloat(r.nextInt());
            case NbtIo.TAG_DOUBLE -> r.nextInt(8) == 0 ? -0.0 : Double.longBitsToDouble(r.nextLong());
            case NbtIo.TAG_BYTE_ARRAY -> { byte[] a = new byte[r.nextInt(3000)]; for (int i = 0; i < a.length; i++) a[i] = (byte) r.nextInt(); yield a; }
            case NbtIo.TAG_STRING -> randomString(r);
            case NbtIo.TAG_INT_ARRAY -> r.ints(r.nextInt(300)).toArray();
            case NbtIo.TAG_LONG_ARRAY -> r.longs(r.nextInt(1200)).toArray();
            case NbtIo.TAG_LIST -> {
                byte et = depth > 3 ? NbtIo.TAG_INT : (byte) (1 + r.nextInt(12));
                NbtList l = new NbtList(et);
                int n = r.nextInt(4) == 0 ? 0 : r.nextInt(6);
                for (int i = 0; i < n; i++) l.add(randomValue(r, et, depth + 1));
                yield l;
            }
            default -> randomCompound(r, depth + 1);
        };
    }

    private static NbtCompound randomCompound(SplittableRandom r, int depth) {
        NbtCompound c = new NbtCompound();
        int n = depth > 4 ? 0 : r.nextInt(8);
        for (int i = 0; i < n; i++) {
            byte t = (byte) (1 + r.nextInt(12));
            c.put(randomString(r), randomValue(r, t, depth));
        }
        return c;
    }

    @Test
    void randomTrees_sameBytesAndSameReadBack() throws Exception {
        SplittableRandom r = new SplittableRandom(0x4E87);
        for (int iter = 0; iter < 3000; iter++) {
            NbtCompound c = randomCompound(r, 0);
            String name = iter % 5 == 0 ? randomString(r) : "";
            byte[] legacy = legacyBytes(c, name);
            byte[] fast = NbtIo.toBytes(c, name);
            assertArrayEquals(legacy, fast, "iter=" + iter);
            // 읽기: 두 경로가 같은 트리 → 다시 쓰면 같은 바이트
            assertArrayEquals(legacyBytes(legacyRead(legacy), ""), legacyBytes(NbtIo.read(legacy), ""), "read iter=" + iter);
        }
    }

    @Test
    void truncatedOrCorrupt_sameExceptionKind() throws Exception {
        SplittableRandom r = new SplittableRandom(0xC0DE);
        for (int iter = 0; iter < 800; iter++) {
            byte[] full = legacyBytes(randomCompound(r, 0), "");
            byte[] b = java.util.Arrays.copyOf(full, r.nextInt(full.length + 1));
            if (r.nextBoolean() && b.length > 0) b[r.nextInt(b.length)] = (byte) r.nextInt();
            Class<?> legacyErr = null, fastErr = null;
            byte[] legacyOut = null, fastOut = null;
            try { legacyOut = legacyBytes(legacyRead(b), ""); } catch (Throwable t) { legacyErr = kind(t); }
            try { fastOut = legacyBytes(NbtIo.read(b), ""); } catch (Throwable t) { fastErr = kind(t); }
            if (legacyErr == OutOfMemoryError.class) continue;   // 예전 경로만 큰 길이로 먼저 할당했다(빠른 경로는 EOF)
            assertEquals(legacyErr, fastErr, "iter=" + iter);
            if (legacyErr == null) assertArrayEquals(legacyOut, fastOut, "iter=" + iter);
        }
    }

    private static Class<?> kind(Throwable t) {
        // 정확한 클래스보다 바깥 처리에 쓰이는 종류로 비교(IOException 계열은 하나로)
        if (t instanceof java.io.IOException) return java.io.IOException.class;
        return t.getClass();
    }

    @Test
    void tooLongString_rejectedLikeWriteUtf() {
        NbtCompound c = new NbtCompound();
        c.putString("k", "가".repeat(30000));   // 90000 바이트
        assertThrows(java.io.UTFDataFormatException.class, () -> legacyBytes(c, ""));
        assertThrows(java.io.UTFDataFormatException.class, () -> NbtIo.toBytes(c, ""));
    }
}
