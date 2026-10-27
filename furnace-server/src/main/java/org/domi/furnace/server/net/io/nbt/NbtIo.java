// NBT 파일 read/write(DataInput/Output) + gzip. 표준 named-root 포맷.
package org.domi.furnace.server.net.io.nbt;

public final class NbtIo {

    public static final byte TAG_END = 0;
    public static final byte TAG_BYTE = 1;
    public static final byte TAG_SHORT = 2;
    public static final byte TAG_INT = 3;
    public static final byte TAG_LONG = 4;
    public static final byte TAG_FLOAT = 5;
    public static final byte TAG_DOUBLE = 6;
    public static final byte TAG_BYTE_ARRAY = 7;
    public static final byte TAG_STRING = 8;
    public static final byte TAG_LIST = 9;
    public static final byte TAG_COMPOUND = 10;
    public static final byte TAG_INT_ARRAY = 11;
    public static final byte TAG_LONG_ARRAY = 12;

    // ---- write ----

    /** 명명 루트 compound write(표준 NBT: [type][name][payload]). */
    public static void write(java.io.DataOutput out, NbtCompound root, String rootName) throws java.io.IOException {
        out.writeByte(TAG_COMPOUND);
        out.writeUTF(rootName);
        writeCompoundPayload(out, root);
    }

    public static void writeGzip(java.io.OutputStream os, NbtCompound root, String rootName) throws java.io.IOException {
        try (java.io.DataOutputStream d = new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(os))) {
            write(d, root, rootName);
        }
    }

    private static void writeCompoundPayload(java.io.DataOutput out, NbtCompound c) throws java.io.IOException {
        for (var e : c.tags().entrySet()) {
            byte type = typeOf(e.getValue());
            out.writeByte(type);
            out.writeUTF(e.getKey());
            writePayload(out, type, e.getValue());
        }
        out.writeByte(TAG_END);
    }

    private static void writePayload(java.io.DataOutput out, byte type, Object v) throws java.io.IOException {
        switch (type) {
            case TAG_BYTE -> out.writeByte((Byte) v);
            case TAG_SHORT -> out.writeShort((Short) v);
            case TAG_INT -> out.writeInt((Integer) v);
            case TAG_LONG -> out.writeLong((Long) v);
            case TAG_FLOAT -> out.writeFloat((Float) v);
            case TAG_DOUBLE -> out.writeDouble((Double) v);
            case TAG_BYTE_ARRAY -> { byte[] a = (byte[]) v; out.writeInt(a.length); out.write(a); }
            case TAG_STRING -> out.writeUTF((String) v);
            case TAG_INT_ARRAY -> { int[] a = (int[]) v; out.writeInt(a.length); for (int x : a) out.writeInt(x); }
            case TAG_LONG_ARRAY -> { long[] a = (long[]) v; out.writeInt(a.length); for (long x : a) out.writeLong(x); }
            case TAG_LIST -> {
                NbtList l = (NbtList) v;
                out.writeByte(l.size() == 0 ? TAG_END : l.elementType());
                out.writeInt(l.size());
                for (Object item : l.items()) writePayload(out, l.elementType(), item);
            }
            case TAG_COMPOUND -> writeCompoundPayload(out, (NbtCompound) v);
            default -> throw new IllegalArgumentException("미지원 태그: " + type);
        }
    }

    private static byte typeOf(Object v) {
        if (v instanceof Byte) return TAG_BYTE;
        if (v instanceof Short) return TAG_SHORT;
        if (v instanceof Integer) return TAG_INT;
        if (v instanceof Long) return TAG_LONG;
        if (v instanceof Float) return TAG_FLOAT;
        if (v instanceof Double) return TAG_DOUBLE;
        if (v instanceof byte[]) return TAG_BYTE_ARRAY;
        if (v instanceof String) return TAG_STRING;
        if (v instanceof int[]) return TAG_INT_ARRAY;
        if (v instanceof long[]) return TAG_LONG_ARRAY;
        if (v instanceof NbtList) return TAG_LIST;
        if (v instanceof NbtCompound) return TAG_COMPOUND;
        throw new IllegalArgumentException("미지원 값: " + v.getClass());
    }

    // ---- read ----

    /** 명명 루트 compound read. 루트 이름은 버린다. */
    public static NbtCompound read(java.io.DataInput in) throws java.io.IOException {
        byte type = in.readByte();
        if (type != TAG_COMPOUND) throw new java.io.IOException("루트가 compound 아님: " + type);
        in.readUTF(); // 루트 이름 버림
        return readCompoundPayload(in);
    }

    /** 이름 없는 루트(플레이 단계 네트워크 NBT: [type][payload]) 하나 — 어떤 태그든 값 객체로. */
    public static Object readUnnamed(byte[] bytes) {
        try {
            java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes));
            return readPayload(in, in.readByte());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public static NbtCompound readGzip(java.io.InputStream is) throws java.io.IOException {
        try (java.io.DataInputStream d = new java.io.DataInputStream(new java.util.zip.GZIPInputStream(is))) {
            return read(d);
        }
    }

    private static NbtCompound readCompoundPayload(java.io.DataInput in) throws java.io.IOException {
        NbtCompound c = new NbtCompound();
        while (true) {
            byte type = in.readByte();
            if (type == TAG_END) break;
            String name = in.readUTF();
            c.put(name, readPayload(in, type));
        }
        return c;
    }

    private static Object readPayload(java.io.DataInput in, byte type) throws java.io.IOException {
        return switch (type) {
            case TAG_BYTE -> in.readByte();
            case TAG_SHORT -> in.readShort();
            case TAG_INT -> in.readInt();
            case TAG_LONG -> in.readLong();
            case TAG_FLOAT -> in.readFloat();
            case TAG_DOUBLE -> in.readDouble();
            case TAG_BYTE_ARRAY -> { int n = in.readInt(); byte[] a = new byte[n]; in.readFully(a); yield a; }
            case TAG_STRING -> in.readUTF();
            case TAG_INT_ARRAY -> { int n = in.readInt(); int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = in.readInt(); yield a; }
            case TAG_LONG_ARRAY -> { int n = in.readInt(); long[] a = new long[n]; for (int i = 0; i < n; i++) a[i] = in.readLong(); yield a; }
            case TAG_LIST -> {
                byte et = in.readByte();
                int n = in.readInt();
                NbtList l = new NbtList(et);
                for (int i = 0; i < n; i++) l.add(readPayload(in, et));
                yield l;
            }
            case TAG_COMPOUND -> readCompoundPayload(in);
            default -> throw new java.io.IOException("미지원 태그: " + type);
        };
    }

    private NbtIo() {}
}
