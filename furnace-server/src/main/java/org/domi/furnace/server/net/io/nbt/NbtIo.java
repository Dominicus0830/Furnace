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

    /** 스트림에서 이름 없는 루트(네트워크 NBT: [type][payload]) 하나를 읽는다 — 그 태그의 바이트만큼만 읽는다. */
    public static Object readUnnamed(java.io.DataInput in) throws java.io.IOException {
        return readPayload(in, in.readByte());
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


    // ---- byte[] 직행(청크·엔티티·POI 저장 경로) ----
    // DataOutputStream(ByteArrayOutputStream)·DataInputStream(ByteArrayInputStream) 과 바이트가 같다. 값 하나마다
    // 동기화된 스트림 호출(long 배열은 원소마다)을 타던 것을 배열 하나에 바로 쓴다/읽는다.

    /** {@link #write(java.io.DataOutput, NbtCompound, String)} 를 ByteArrayOutputStream 에 쓴 것과 같은 바이트. */
    public static byte[] toBytes(NbtCompound root, String rootName) throws java.io.IOException {
        Out o = new Out();
        o.b(TAG_COMPOUND);
        o.utf(rootName);
        o.compound(root);
        return java.util.Arrays.copyOf(o.buf, o.n);
    }

    /** {@link #read(java.io.DataInput)} 를 그 바이트 위 DataInputStream 으로 읽은 것과 같은 결과. 모자라면 EOFException. */
    public static NbtCompound read(byte[] bytes) throws java.io.IOException {
        In in = new In(bytes);
        byte type = in.b();
        if (type != TAG_COMPOUND) throw new java.io.IOException("루트가 compound 아님: " + type);
        in.utf();
        return in.compound();
    }

    private static final class Out {
        byte[] buf = new byte[8192];
        int n;

        void ensure(int more) {
            if (n + more > buf.length) buf = java.util.Arrays.copyOf(buf, Math.max(buf.length * 2, n + more));
        }
        void b(int v) { ensure(1); buf[n++] = (byte) v; }
        void s(int v) { ensure(2); buf[n++] = (byte) (v >>> 8); buf[n++] = (byte) v; }
        void i(int v) { ensure(4); putInt(v); }
        private void putInt(int v) {
            buf[n++] = (byte) (v >>> 24); buf[n++] = (byte) (v >>> 16); buf[n++] = (byte) (v >>> 8); buf[n++] = (byte) v;
        }
        void l(long v) { ensure(8); putInt((int) (v >>> 32)); putInt((int) v); }

        /** DataOutput.writeUTF 와 같은 수정 UTF-8. 65535 바이트를 넘으면 UTFDataFormatException. */
        void utf(String str) throws java.io.UTFDataFormatException {
            int len = str.length();
            int utf = len;
            for (int k = 0; k < len; k++) {
                char c = str.charAt(k);
                if (c >= 0x80 || c == 0) utf += c >= 0x800 ? 2 : 1;
            }
            if (utf > 65535) throw new java.io.UTFDataFormatException("encoded string too long: " + utf + " bytes");
            ensure(2 + utf);
            buf[n++] = (byte) (utf >>> 8);
            buf[n++] = (byte) utf;
            for (int k = 0; k < len; k++) {
                char c = str.charAt(k);
                if (c < 0x80 && c != 0) {
                    buf[n++] = (byte) c;
                } else if (c >= 0x800) {
                    buf[n++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
                    buf[n++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                    buf[n++] = (byte) (0x80 | (c & 0x3F));
                } else {
                    buf[n++] = (byte) (0xC0 | ((c >> 6) & 0x1F));
                    buf[n++] = (byte) (0x80 | (c & 0x3F));
                }
            }
        }

        void compound(NbtCompound c) throws java.io.IOException {
            for (var e : c.tags().entrySet()) {
                byte type = typeOf(e.getValue());
                b(type);
                utf(e.getKey());
                payload(type, e.getValue());
            }
            b(TAG_END);
        }

        void payload(byte type, Object v) throws java.io.IOException {
            switch (type) {
                case TAG_BYTE -> b((Byte) v);
                case TAG_SHORT -> s((Short) v);
                case TAG_INT -> i((Integer) v);
                case TAG_LONG -> l((Long) v);
                case TAG_FLOAT -> i(Float.floatToIntBits((Float) v));
                case TAG_DOUBLE -> l(Double.doubleToLongBits((Double) v));
                case TAG_BYTE_ARRAY -> { byte[] a = (byte[]) v; i(a.length); ensure(a.length); System.arraycopy(a, 0, buf, n, a.length); n += a.length; }
                case TAG_STRING -> utf((String) v);
                case TAG_INT_ARRAY -> { int[] a = (int[]) v; i(a.length); ensure(4 * a.length); for (int x : a) putInt(x); }
                case TAG_LONG_ARRAY -> {
                    long[] a = (long[]) v;
                    i(a.length);
                    ensure(8 * a.length);
                    for (long x : a) { putInt((int) (x >>> 32)); putInt((int) x); }
                }
                case TAG_LIST -> {
                    NbtList list = (NbtList) v;
                    b(list.size() == 0 ? TAG_END : list.elementType());
                    i(list.size());
                    for (Object item : list.items()) payload(list.elementType(), item);
                }
                case TAG_COMPOUND -> compound((NbtCompound) v);
                default -> throw new IllegalArgumentException("미지원 태그: " + type);
            }
        }
    }

    private static final class In {
        final byte[] a;
        int p;

        In(byte[] a) { this.a = a; }

        private void need(int k) throws java.io.EOFException {
            if (k < 0 || a.length - p < k) throw new java.io.EOFException();
        }
        byte b() throws java.io.EOFException { need(1); return a[p++]; }
        short s() throws java.io.EOFException { need(2); short v = (short) (((a[p] & 0xFF) << 8) | (a[p + 1] & 0xFF)); p += 2; return v; }
        int i() throws java.io.EOFException { need(4); return getInt(); }
        private int getInt() {
            int v = ((a[p] & 0xFF) << 24) | ((a[p + 1] & 0xFF) << 16) | ((a[p + 2] & 0xFF) << 8) | (a[p + 3] & 0xFF);
            p += 4;
            return v;
        }
        long l() throws java.io.EOFException { need(8); return ((long) getInt() << 32) | (getInt() & 0xFFFFFFFFL); }

        /** DataInput.readUTF. 전부 ASCII(흔한 경우)면 바로, 아니면 DataInputStream 에 맡겨 같은 해석·예외. */
        String utf() throws java.io.IOException {
            int len = s() & 0xFFFF;
            need(len);
            int start = p;
            boolean ascii = true;
            for (int k = start; k < start + len; k++) if (a[k] < 1) { ascii = false; break; }   // 0 과 음수(≥0x80)
            if (ascii) {
                p += len;
                return new String(a, start, len, java.nio.charset.StandardCharsets.ISO_8859_1);   // 7비트 바이트 → 같은 문자
            }
            java.io.DataInputStream d = new java.io.DataInputStream(new java.io.ByteArrayInputStream(a, start - 2, len + 2));
            String v = d.readUTF();
            p += len;
            return v;
        }

        NbtCompound compound() throws java.io.IOException {
            NbtCompound c = new NbtCompound();
            while (true) {
                byte type = b();
                if (type == TAG_END) break;
                String name = utf();
                c.put(name, payload(type));
            }
            return c;
        }

        Object payload(byte type) throws java.io.IOException {
            return switch (type) {
                case TAG_BYTE -> b();
                case TAG_SHORT -> s();
                case TAG_INT -> i();
                case TAG_LONG -> l();
                case TAG_FLOAT -> Float.intBitsToFloat(i());
                case TAG_DOUBLE -> Double.longBitsToDouble(l());
                case TAG_BYTE_ARRAY -> {
                    int k = i();
                    if (k < 0) throw new NegativeArraySizeException(String.valueOf(k));   // 예전 new byte[k] 와 같은 예외
                    need(k);   // 큰 길이로 먼저 할당하지 않는다
                    byte[] out = new byte[k];
                    System.arraycopy(a, p, out, 0, k);
                    p += k;
                    yield out;
                }
                case TAG_STRING -> utf();
                case TAG_INT_ARRAY -> {
                    int k = i();
                    if (k < 0) throw new NegativeArraySizeException(String.valueOf(k));
                    if (a.length - p < 4L * k) throw new java.io.EOFException();
                    int[] out = new int[k];
                    for (int j = 0; j < k; j++) out[j] = getInt();
                    yield out;
                }
                case TAG_LONG_ARRAY -> {
                    int k = i();
                    if (k < 0) throw new NegativeArraySizeException(String.valueOf(k));
                    if (a.length - p < 8L * k) throw new java.io.EOFException();
                    long[] out = new long[k];
                    for (int j = 0; j < k; j++) out[j] = ((long) getInt() << 32) | (getInt() & 0xFFFFFFFFL);
                    yield out;
                }
                case TAG_LIST -> {
                    byte et = b();
                    int k = i();
                    NbtList list = new NbtList(et);
                    for (int j = 0; j < k; j++) list.add(payload(et));
                    yield list;
                }
                case TAG_COMPOUND -> compound();
                default -> throw new java.io.IOException("미지원 태그: " + type);
            };
        }
    }

    private NbtIo() {}
}
