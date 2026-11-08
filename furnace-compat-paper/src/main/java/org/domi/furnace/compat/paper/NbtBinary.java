// NBT 이진 형식(gzip 압축 루트 묶음) 읽기·쓰기 — Paper 아이템 직렬화의 "unhandled"·"custom" 값(base64 로 감싼 압축 NBT)과 바이트 단위로 읽히게.
// 값 종류는 furnace-api 의 NbtCompound·NbtList 가 쓰는 자바 형(Byte…long[])이다.
package org.domi.furnace.compat.paper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.nbt.NbtList;
import org.domi.furnace.api.nbt.NbtType;

final class NbtBinary {

    /** 압축을 푼 뒤 크기 상한 — 잘못된(또는 악의적인) 입력이 메모리를 먹지 않게. */
    private static final int MAX_BYTES = 64 << 20;
    private static final int MAX_DEPTH = 512;

    private NbtBinary() {}

    // ------------------------------------------------------------------ 쓰기

    /** 루트 묶음(이름 "")을 gzip 으로 압축한 NBT 바이트. */
    static byte[] writeCompressed(NbtCompound root) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
                out.writeByte(NbtType.COMPOUND.id());
                out.writeUTF("");
                writeCompound(out, root);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e); // 메모리 스트림이라 일어나지 않는다
        }
    }

    private static void writeCompound(DataOutputStream out, NbtCompound c) throws IOException {
        for (String key : c.keys()) {
            Object v = c.get(key);
            out.writeByte(NbtType.of(v).id());
            out.writeUTF(key);
            writePayload(out, v);
        }
        out.writeByte(0);
    }

    private static void writePayload(DataOutputStream out, Object v) throws IOException {
        switch (v) {
            case Byte x -> out.writeByte(x);
            case Short x -> out.writeShort(x);
            case Integer x -> out.writeInt(x);
            case Long x -> out.writeLong(x);
            case Float x -> out.writeFloat(x);
            case Double x -> out.writeDouble(x);
            case String x -> out.writeUTF(x);
            case byte[] a -> {
                out.writeInt(a.length);
                out.write(a);
            }
            case int[] a -> {
                out.writeInt(a.length);
                for (int x : a) out.writeInt(x);
            }
            case long[] a -> {
                out.writeInt(a.length);
                for (long x : a) out.writeLong(x);
            }
            case NbtList l -> {
                out.writeByte(l.isEmpty() ? 0 : l.elementType().id());
                out.writeInt(l.size());
                for (Object o : l.values()) writePayload(out, o);
            }
            case NbtCompound c -> writeCompound(out, c);
            default -> throw new IllegalArgumentException("NBT 값이 아니다: " + v);
        }
    }

    // ------------------------------------------------------------------ 읽기

    /** gzip 으로 압축된 NBT 바이트의 루트 묶음. 형식이 틀리면 IOException. */
    static NbtCompound readCompressed(byte[] gzip) throws IOException {
        byte[] raw;
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gzip))) {
            raw = in.readNBytes(MAX_BYTES + 1);
        }
        if (raw.length > MAX_BYTES) throw new IOException("NBT 가 너무 크다");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
        int type = in.readUnsignedByte();
        if (type != NbtType.COMPOUND.id()) throw new IOException("루트가 묶음이 아니다: 태그 " + type);
        in.readUTF();
        return readCompound(in, 0);
    }

    private static NbtCompound readCompound(DataInputStream in, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT 가 너무 깊다");
        NbtCompound c = new NbtCompound();
        while (true) {
            int type = in.readUnsignedByte();
            if (type == 0) return c;
            String key = in.readUTF();
            c.put(key, readPayload(in, type, depth + 1));
        }
    }

    private static Object readPayload(DataInputStream in, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT 가 너무 깊다");
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: {
                byte[] a = new byte[length(in, 1)];
                in.readFully(a);
                return a;
            }
            case 8: return in.readUTF();
            case 9: {
                int elem = in.readUnsignedByte();
                int n = in.readInt();
                if (n < 0) throw new IOException("음수 길이");
                if (elem == 0 && n > 0) throw new IOException("종류 없는 리스트");
                NbtList l = new NbtList();
                for (int i = 0; i < n; i++) l.add(readPayload(in, elem, depth + 1));
                return l;
            }
            case 10: return readCompound(in, depth);
            case 11: {
                int[] a = new int[length(in, 4)];
                for (int i = 0; i < a.length; i++) a[i] = in.readInt();
                return a;
            }
            case 12: {
                long[] a = new long[length(in, 8)];
                for (int i = 0; i < a.length; i++) a[i] = in.readLong();
                return a;
            }
            default: throw new IOException("알 수 없는 NBT 태그: " + type);
        }
    }

    /** 배열 길이 — 남은 바이트보다 큰 길이는 거절한다(큰 배열부터 잡는 것을 막는다). */
    private static int length(DataInputStream in, int elementBytes) throws IOException {
        int n = in.readInt();
        if (n < 0 || (long) n * elementBytes > in.available()) throw new IOException("잘못된 배열 길이: " + n);
        return n;
    }
}
