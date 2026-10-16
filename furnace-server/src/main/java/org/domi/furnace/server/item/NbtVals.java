// NBT 값 도구 — NbtOps 의 리스트 만들기 규칙, 네트워크 NBT(이름 없는 루트) 읽기·쓰기(CompoundTag 는 HashMap 순서).
package org.domi.furnace.server.item;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

public final class NbtVals {
    private NbtVals() {}

    public static byte typeOf(Object v) {
        if (v instanceof Byte) return NbtIo.TAG_BYTE;
        if (v instanceof Short) return NbtIo.TAG_SHORT;
        if (v instanceof Integer) return NbtIo.TAG_INT;
        if (v instanceof Long) return NbtIo.TAG_LONG;
        if (v instanceof Float) return NbtIo.TAG_FLOAT;
        if (v instanceof Double) return NbtIo.TAG_DOUBLE;
        if (v instanceof byte[]) return NbtIo.TAG_BYTE_ARRAY;
        if (v instanceof String) return NbtIo.TAG_STRING;
        if (v instanceof NbtList) return NbtIo.TAG_LIST;
        if (v instanceof NbtCompound) return NbtIo.TAG_COMPOUND;
        if (v instanceof int[]) return NbtIo.TAG_INT_ARRAY;
        if (v instanceof long[]) return NbtIo.TAG_LONG_ARRAY;
        throw new IllegalArgumentException("NBT 값이 아님: " + (v == null ? "null" : v.getClass()));
    }

    /**
     * NbtOps 리스트 수집 규칙: 첫 원소가 byte·int·long 이면 같은 종류가 이어지는 동안 배열 태그,
     * compound 로 시작하거나 종류가 섞이면 compound 리스트(compound 가 아니거나 {"":…} 꼴이면 {"": 값} 으로 감쌈),
     * 그 밖에는 같은 종류의 리스트.
     */
    public static Object list(List<Object> tags) {
        if (tags.isEmpty()) return new NbtList(NbtIo.TAG_END);
        Object first = tags.get(0);
        byte t = typeOf(first);
        boolean homogeneous = true;
        for (Object o : tags) if (typeOf(o) != t) { homogeneous = false; break; }
        if (t != NbtIo.TAG_COMPOUND && homogeneous) {
            switch (t) {
                case NbtIo.TAG_BYTE -> { byte[] a = new byte[tags.size()]; for (int i = 0; i < a.length; i++) a[i] = (Byte) tags.get(i); return a; }
                case NbtIo.TAG_INT -> { int[] a = new int[tags.size()]; for (int i = 0; i < a.length; i++) a[i] = (Integer) tags.get(i); return a; }
                case NbtIo.TAG_LONG -> { long[] a = new long[tags.size()]; for (int i = 0; i < a.length; i++) a[i] = (Long) tags.get(i); return a; }
                default -> {
                    NbtList l = new NbtList(t);
                    for (Object o : tags) l.add(o);
                    return l;
                }
            }
        }
        NbtList l = new NbtList(NbtIo.TAG_COMPOUND);
        for (Object o : tags) {
            if (o instanceof NbtCompound c && !isWrapper(c)) l.add(c);
            else {
                NbtCompound w = new NbtCompound();
                w.put("", o);
                l.add(w);
            }
        }
        return l;
    }

    private static boolean isWrapper(NbtCompound c) { return c.tags().size() == 1 && c.tags().containsKey(""); }

    /** {"": 값} 감싸기를 벗긴 원소 목록(NbtOps.getStream 대응). */
    public static List<Object> unwrapList(List<Object> items) {
        List<Object> out = null;
        for (int i = 0; i < items.size(); i++) {
            Object o = items.get(i);
            if (o instanceof NbtCompound c && isWrapper(c)) {
                if (out == null) out = new ArrayList<>(items.subList(0, i));
                out.add(c.tags().get(""));
            } else if (out != null) out.add(o);
        }
        return out == null ? items : out;
    }

    // ------------------------------------------------------------------ 네트워크 NBT

    /** 네트워크 NBT 크기 상한(바닐라 ByteBufCodecs.COMPOUND_TAG 의 NbtAccounter 2 MiB) — 깊이 512. */
    public static final long NETWORK_QUOTA = 2_097_152L;
    private static final int MAX_DEPTH = 512;

    /** 이름 없는 루트 태그 읽기(종류 바이트 + 내용). TAG_END 면 null. */
    public static Object readNetwork(ByteBuf buf) {
        byte type = buf.readByte();
        if (type == NbtIo.TAG_END) return null;
        long[] quota = {NETWORK_QUOTA - 8};
        return readPayload(buf, type, quota, 0);
    }

    private static void account(long[] q, long bytes) {
        q[0] -= bytes;
        if (q[0] < 0) throw new CodecException("Tried to read NBT tag that was too big; tried to allocate: " + bytes + " bytes");
    }

    private static Object readPayload(ByteBuf in, byte type, long[] q, int depth) {
        if (depth > MAX_DEPTH) throw new CodecException("Tried to read NBT tag with too high complexity, depth > 512");
        switch (type) {
            case NbtIo.TAG_BYTE -> { account(q, 9); return in.readByte(); }
            case NbtIo.TAG_SHORT -> { account(q, 10); return in.readShort(); }
            case NbtIo.TAG_INT -> { account(q, 12); return in.readInt(); }
            case NbtIo.TAG_LONG -> { account(q, 16); return in.readLong(); }
            case NbtIo.TAG_FLOAT -> { account(q, 12); return in.readFloat(); }
            case NbtIo.TAG_DOUBLE -> { account(q, 16); return in.readDouble(); }
            case NbtIo.TAG_BYTE_ARRAY -> {
                account(q, 24);
                int n = in.readInt();
                account(q, n);
                byte[] a = new byte[n];
                in.readBytes(a);
                return a;
            }
            case NbtIo.TAG_STRING -> { account(q, 36); String s = readUtf(in); account(q, 2L * s.length()); return s; }
            case NbtIo.TAG_LIST -> {
                account(q, 37);
                byte et = in.readByte();
                int n = in.readInt();
                if (et == NbtIo.TAG_END && n > 0) throw new CodecException("Missing type on ListTag");
                account(q, 4L * n);
                NbtList l = new NbtList(et);
                for (int i = 0; i < n; i++) l.add(readPayload(in, et, q, depth + 1));
                return l;
            }
            case NbtIo.TAG_COMPOUND -> {
                account(q, 48);
                NbtCompound c = new NbtCompound();
                while (true) {
                    byte t = in.readByte();
                    if (t == NbtIo.TAG_END) break;
                    String name = readUtf(in);
                    account(q, 28 + 2L * name.length());
                    Object v = readPayload(in, t, q, depth + 1);
                    if (c.tags().put(name, v) == null) account(q, 36);
                }
                return c;
            }
            case NbtIo.TAG_INT_ARRAY -> {
                account(q, 24);
                int n = in.readInt();
                account(q, 4L * n);
                int[] a = new int[n];
                for (int i = 0; i < n; i++) a[i] = in.readInt();
                return a;
            }
            case NbtIo.TAG_LONG_ARRAY -> {
                account(q, 24);
                int n = in.readInt();
                account(q, 8L * n);
                long[] a = new long[n];
                for (int i = 0; i < n; i++) a[i] = in.readLong();
                return a;
            }
            default -> throw new CodecException("Invalid tag id: " + type);
        }
    }

    /** 이름 없는 루트 태그 쓰기. null 이면 TAG_END. */
    public static void writeNetwork(ByteBuf buf, Object tag) {
        if (tag == null) { buf.writeByte(NbtIo.TAG_END); return; }
        byte t = typeOf(tag);
        buf.writeByte(t);
        writePayload(buf, t, tag);
    }

    private static void writePayload(ByteBuf buf, byte type, Object v) {
        switch (type) {
            case NbtIo.TAG_BYTE -> buf.writeByte((Byte) v);
            case NbtIo.TAG_SHORT -> buf.writeShort((Short) v);
            case NbtIo.TAG_INT -> buf.writeInt((Integer) v);
            case NbtIo.TAG_LONG -> buf.writeLong((Long) v);
            case NbtIo.TAG_FLOAT -> buf.writeFloat((Float) v);
            case NbtIo.TAG_DOUBLE -> buf.writeDouble((Double) v);
            case NbtIo.TAG_BYTE_ARRAY -> { byte[] a = (byte[]) v; buf.writeInt(a.length); buf.writeBytes(a); }
            case NbtIo.TAG_STRING -> writeUtf(buf, (String) v);
            case NbtIo.TAG_LIST -> {
                NbtList l = (NbtList) v;
                buf.writeByte(l.size() == 0 ? NbtIo.TAG_END : l.elementType());
                buf.writeInt(l.size());
                for (Object o : l.items()) writePayload(buf, l.elementType(), o);
            }
            case NbtIo.TAG_COMPOUND -> {
                // 바닐라 CompoundTag 는 HashMap — 같은 순서로 넣은 HashMap 의 순회 순서로 쓴다.
                Map<String, Object> hm = new HashMap<>();
                for (var e : ((NbtCompound) v).tags().entrySet()) hm.put(e.getKey(), e.getValue());
                for (var e : hm.entrySet()) {
                    byte t = typeOf(e.getValue());
                    buf.writeByte(t);
                    writeUtf(buf, e.getKey());
                    writePayload(buf, t, e.getValue());
                }
                buf.writeByte(NbtIo.TAG_END);
            }
            case NbtIo.TAG_INT_ARRAY -> { int[] a = (int[]) v; buf.writeInt(a.length); for (int x : a) buf.writeInt(x); }
            case NbtIo.TAG_LONG_ARRAY -> { long[] a = (long[]) v; buf.writeInt(a.length); for (long x : a) buf.writeLong(x); }
            default -> throw new IllegalArgumentException("미지원 태그: " + type);
        }
    }

    /** DataOutput.writeUTF 와 같은 수정 UTF-8. */
    static void writeUtf(ByteBuf buf, String s) {
        int len = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            len += c >= 1 && c <= 0x7f ? 1 : c <= 0x7ff ? 2 : 3;
        }
        if (len > 65535) throw new CodecException("NBT string too long: " + len);
        buf.writeShort(len);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 1 && c <= 0x7f) buf.writeByte(c);
            else if (c <= 0x7ff) { buf.writeByte(0xc0 | (c >> 6)); buf.writeByte(0x80 | (c & 0x3f)); }
            else { buf.writeByte(0xe0 | (c >> 12)); buf.writeByte(0x80 | ((c >> 6) & 0x3f)); buf.writeByte(0x80 | (c & 0x3f)); }
        }
    }

    static String readUtf(ByteBuf buf) {
        int len = buf.readUnsignedShort();
        byte[] b = new byte[len];
        buf.readBytes(b);
        try {
            return new java.io.DataInputStream(new java.io.ByteArrayInputStream(withLength(b))).readUTF();
        } catch (java.io.IOException e) {
            throw new CodecException("Malformed NBT string");
        }
    }

    private static byte[] withLength(byte[] b) {
        byte[] r = new byte[b.length + 2];
        r[0] = (byte) (b.length >> 8);
        r[1] = (byte) b.length;
        System.arraycopy(b, 0, r, 2, b.length);
        return r;
    }

    /** 표준 UTF-8 로 바이트 길이(문자열 상한 검사용). */
    static int utf8Length(String s) { return s.getBytes(StandardCharsets.UTF_8).length; }
}
