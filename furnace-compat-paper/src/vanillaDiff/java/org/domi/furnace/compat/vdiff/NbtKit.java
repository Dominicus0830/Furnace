package org.domi.furnace.compat.vdiff;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;

/**
 * NBT 형식 꾸러미(T-235). 태그 나무 씨앗을 고정 시드로 만들고(깊이 3, 칸 4개까지, 모든 태그 종류), 양쪽 로더에서 같은 나무로 올리고,
 * 결과 태그는 종류 번호·내용으로 내린다. 스트림 인자는 시험 쪽 클래스(양쪽 로더가 같은 JDK 형식으로 본다)로 바이트를 받아 견준다.
 * 읽기 입력 바이트는 바닐라 NbtIo 가 쓴 것(+ 일부는 한 바이트 깨기·자르기)이라 정상·오류 경로를 둘 다 지난다.
 */
final class NbtKit {
    private NbtKit() {}

    static final String P = "net.minecraft.nbt.";
    private static final String[] KEYS = {"", "a", "b", "id", "Count", "x y", "é", "tag"};
    static final byte END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6, BYTE_ARRAY = 7, STRING = 8,
            LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

    /** 태그 씨앗: 종류 번호 + 내용(숫자·글·원시 배열·목록·이름표). */
    record TagSeed(byte kind, Object payload) {
        @Override
        public String toString() {
            if (payload instanceof byte[] b) return "B" + java.util.Arrays.toString(b);
            if (payload instanceof int[] b) return "I" + java.util.Arrays.toString(b);
            if (payload instanceof long[] b) return "L" + java.util.Arrays.toString(b);
            return kind == STRING ? "\"" + payload + "\"" : String.valueOf(payload);
        }
    }

    private static final java.util.concurrent.atomic.AtomicLong TEMP_COUNTER = new java.util.concurrent.atomic.AtomicLong();
    private static java.nio.file.Path temp;

    /** 경로 인자용 임시 폴더 하나(시험 끝에 지운다). */
    private static synchronized java.nio.file.Path tempDir() throws IOException {
        if (temp == null) temp = java.nio.file.Files.createTempDirectory("vanilla-diff");
        return temp;
    }

    static synchronized void cleanTemp() throws IOException {
        if (temp == null) return;
        try (var s = java.nio.file.Files.walk(temp)) {
            for (java.nio.file.Path p : s.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.deleteIfExists(p);
        }
        temp = null;
    }

    /** 바이트를 받는 출력 인자. */
    record OutSeed(boolean dataOutput) {}

    /** 바이트를 내주는 입력 인자(바닐라가 쓴 태그 바이트, gzip 여부, 깨기). */
    record InSeed(TagSeed tag, boolean compressed, boolean dataInput, int corrupt, boolean named) {}

    /** 파일 경로 인자: 내용이 있으면 바닐라가 쓴 태그 바이트가 든 새 임시 파일, 없으면 아직 없는 경로(쓰기용). */
    record PathSeed(InSeed content) {}

    /** 형식 이름이 NBT 꾸러미 몫이면 씨앗, 아니면 null. */
    static Object seed(String type, SplittableRandom r) {
        return switch (type) {
            case P + "Tag" -> tag(r, (byte) (1 + r.nextInt(12)), 0);
            case P + "CompoundTag" -> tag(r, COMPOUND, 0);
            case P + "ListTag" -> tag(r, LIST, 0);
            case P + "CollectionTag" -> tag(r, new byte[]{LIST, BYTE_ARRAY, INT_ARRAY, LONG_ARRAY}[r.nextInt(4)], 0);
            case P + "NumericTag" -> tag(r, (byte) (1 + r.nextInt(6)), 0);
            case P + "ByteTag" -> tag(r, BYTE, 0);
            case P + "ShortTag" -> tag(r, SHORT, 0);
            case P + "IntTag" -> tag(r, INT, 0);
            case P + "LongTag" -> tag(r, LONG, 0);
            case P + "FloatTag" -> tag(r, FLOAT, 0);
            case P + "DoubleTag" -> tag(r, DOUBLE, 0);
            case P + "StringTag" -> tag(r, STRING, 0);
            case P + "ByteArrayTag" -> tag(r, BYTE_ARRAY, 0);
            case P + "IntArrayTag" -> tag(r, INT_ARRAY, 0);
            case P + "LongArrayTag" -> tag(r, LONG_ARRAY, 0);
            case P + "EndTag" -> new TagSeed(END, null);
            case "byte[]", "[B" -> bytes(r);
            case "int[]", "[I" -> ints(r);
            case "long[]", "[J" -> longs(r);
            case "java.io.DataOutput" -> new OutSeed(true);
            case "java.io.OutputStream" -> new OutSeed(false);
            case "java.io.DataInput" -> in(r, false, true);
            case "java.io.InputStream" -> in(r, true, false);
            case "java.nio.file.Path" -> new PathSeed(r.nextBoolean() ? in(r, r.nextBoolean(), false) : null);
            default -> null;
        };
    }

    private static InSeed in(SplittableRandom r, boolean compressed, boolean dataInput) {
        int corrupt = r.nextInt(4) == 0 ? r.nextInt(1, 1 << 20) : 0;
        return new InSeed(tag(r, COMPOUND, 0), compressed, dataInput, corrupt, r.nextInt(3) != 0);
    }

    static TagSeed tag(SplittableRandom r, byte kind, int depth) {
        Object p = switch (kind) {
            case BYTE -> (byte) TypeKit.INT_EDGES[r.nextInt(TypeKit.INT_EDGES.length)];
            case SHORT -> (short) (r.nextBoolean() ? r.nextInt() : TypeKit.INT_EDGES[r.nextInt(TypeKit.INT_EDGES.length)]);
            case INT -> r.nextBoolean() ? r.nextInt() : TypeKit.INT_EDGES[r.nextInt(TypeKit.INT_EDGES.length)];
            case LONG -> r.nextBoolean() ? r.nextLong() : TypeKit.LONG_EDGES[r.nextInt(TypeKit.LONG_EDGES.length)];
            case FLOAT -> TypeKit.FLOAT_EDGES[r.nextInt(TypeKit.FLOAT_EDGES.length)];
            case DOUBLE -> TypeKit.DOUBLE_EDGES[r.nextInt(TypeKit.DOUBLE_EDGES.length)];
            case STRING -> KEYS[r.nextInt(KEYS.length)] + (r.nextBoolean() ? "" : "\"q'\\");
            case BYTE_ARRAY -> bytes(r);
            case INT_ARRAY -> ints(r);
            case LONG_ARRAY -> longs(r);
            case LIST -> {
                int n = depth >= 3 ? 0 : r.nextInt(5);
                byte el = (byte) (1 + r.nextInt(12));
                if (depth >= 2 && (el == LIST || el == COMPOUND)) el = INT;
                List<TagSeed> l = new ArrayList<>();
                for (int i = 0; i < n; i++) l.add(tag(r, el, depth + 1));
                yield l;
            }
            case COMPOUND -> {
                int n = depth >= 3 ? 0 : r.nextInt(5);
                Map<String, TagSeed> m = new LinkedHashMap<>();
                for (int i = 0; i < n; i++) {
                    byte k = (byte) (1 + r.nextInt(12));
                    if (depth >= 2 && (k == LIST || k == COMPOUND)) k = STRING;
                    m.put(KEYS[r.nextInt(KEYS.length)], tag(r, k, depth + 1));
                }
                yield m;
            }
            default -> null;
        };
        return new TagSeed(kind, p);
    }

    private static byte[] bytes(SplittableRandom r) {
        byte[] b = new byte[r.nextInt(6)];
        r.nextBytes(b);
        return b;
    }

    private static int[] ints(SplittableRandom r) {
        int[] a = new int[r.nextInt(6)];
        for (int i = 0; i < a.length; i++) a[i] = r.nextInt();
        return a;
    }

    private static long[] longs(SplittableRandom r) {
        long[] a = new long[r.nextInt(6)];
        for (int i = 0; i < a.length; i++) a[i] = r.nextLong();
        return a;
    }

    // ---- 올리기 ----

    static Object raise(Object seed, ClassLoader cl) throws ReflectiveOperationException, IOException {
        if (seed instanceof byte[] b) return b.clone();
        if (seed instanceof int[] b) return b.clone();
        if (seed instanceof long[] b) return b.clone();
        if (seed instanceof OutSeed o) return o.dataOutput ? new CaptureData() : new CaptureStream();
        if (seed instanceof PathSeed ps) {
            java.nio.file.Path f = tempDir().resolve("f" + TEMP_COUNTER.incrementAndGet() + ".nbt");
            if (ps.content != null) java.nio.file.Files.write(f, inputBytes(ps.content));
            return f;
        }
        if (seed instanceof InSeed in) {
            byte[] b = inputBytes(in);
            return in.dataInput ? new DataInputStream(new ByteArrayInputStream(b)) : new ByteArrayInputStream(b);
        }
        return raiseTag((TagSeed) seed, cl);
    }

    @SuppressWarnings("unchecked")
    static Object raiseTag(TagSeed s, ClassLoader cl) throws ReflectiveOperationException {
        return switch (s.kind) {
            case END -> cls(cl, "EndTag").getField("INSTANCE").get(null);
            case BYTE -> cls(cl, "ByteTag").getMethod("valueOf", byte.class).invoke(null, s.payload);
            case SHORT -> cls(cl, "ShortTag").getMethod("valueOf", short.class).invoke(null, s.payload);
            case INT -> cls(cl, "IntTag").getMethod("valueOf", int.class).invoke(null, s.payload);
            case LONG -> cls(cl, "LongTag").getMethod("valueOf", long.class).invoke(null, s.payload);
            case FLOAT -> cls(cl, "FloatTag").getMethod("valueOf", float.class).invoke(null, s.payload);
            case DOUBLE -> cls(cl, "DoubleTag").getMethod("valueOf", double.class).invoke(null, s.payload);
            case STRING -> cls(cl, "StringTag").getMethod("valueOf", String.class).invoke(null, s.payload);
            case BYTE_ARRAY -> cls(cl, "ByteArrayTag").getConstructor(byte[].class).newInstance((Object) ((byte[]) s.payload).clone());
            case INT_ARRAY -> cls(cl, "IntArrayTag").getConstructor(int[].class).newInstance((Object) ((int[]) s.payload).clone());
            case LONG_ARRAY -> cls(cl, "LongArrayTag").getConstructor(long[].class).newInstance((Object) ((long[]) s.payload).clone());
            case LIST -> {
                Object list = cls(cl, "ListTag").getConstructor().newInstance();
                Method add = List.class.getMethod("add", Object.class);
                for (TagSeed e : (List<TagSeed>) s.payload) add.invoke(list, raiseTag(e, cl));
                yield list;
            }
            case COMPOUND -> {
                Class<?> c = cls(cl, "CompoundTag");
                Object tag = c.getConstructor().newInstance();
                Method put = c.getMethod("put", String.class, cls(cl, "Tag"));
                for (Map.Entry<String, TagSeed> e : ((Map<String, TagSeed>) s.payload).entrySet()) put.invoke(tag, e.getKey(), raiseTag(e.getValue(), cl));
                yield tag;
            }
            default -> throw new IllegalArgumentException("kind " + s.kind);
        };
    }

    private static Class<?> cls(ClassLoader cl, String simple) throws ClassNotFoundException {
        return Class.forName(P + simple, true, cl);
    }

    /** 입력 바이트는 바닐라가 쓴다(정답 쪽 직렬화) — 양쪽이 같은 바이트를 읽는다. */
    private static byte[] inputBytes(InSeed in) throws ReflectiveOperationException, IOException {
        ClassLoader v = VanillaRuntime.get().loader();
        Object tag = raiseTag(in.tag, v);
        Class<?> io = cls(v, "NbtIo");
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bo)) {
            if (in.named) io.getMethod("write", cls(v, "CompoundTag"), java.io.DataOutput.class).invoke(null, tag, out);
            else io.getMethod("writeAnyTag", cls(v, "Tag"), java.io.DataOutput.class).invoke(null, tag, out);
        } catch (InvocationTargetException e) {
            throw new IOException(e.getCause());
        }
        byte[] b = bo.toByteArray();
        if (in.corrupt != 0 && b.length > 0) {
            int at = in.corrupt % b.length;
            if ((in.corrupt & 1) == 0) b = java.util.Arrays.copyOf(b, at);
            else b[at] ^= (byte) (in.corrupt >>> 8 | 1);
        }
        if (!in.compressed) return b;
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream z = new GZIPOutputStream(gz)) {
            z.write(b);
        }
        return gz.toByteArray();
    }

    // ---- 내리기 ----

    /** 시험 쪽 바이트 받기 — 양쪽 로더가 같은 이 클래스를 쓴다(JDK 형식 인자). */
    static final class CaptureData extends DataOutputStream {
        CaptureData() {
            super(new ByteArrayOutputStream());
        }

        byte[] bytes() {
            return ((ByteArrayOutputStream) out).toByteArray();
        }
    }

    static final class CaptureStream extends ByteArrayOutputStream {}

    static boolean isTag(Class<?> c) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Class<?> i : k.getInterfaces()) if (i.getName().equals(P + "Tag")) return true;
        }
        return false;
    }

    /** 태그 → [클래스, 종류 번호, 내용]. 이름표 칸은 이름 순(HashMap 순서에 기대지 않는다). */
    static Object lowerTag(Object t, int depth) throws TypeKit.Unsupported {
        if (depth > 16) throw new TypeKit.Unsupported("nbt too deep");
        try {
            Class<?> c = t.getClass();
            List<Object> out = new ArrayList<>();
            out.add(c.getName());
            byte id = (byte) c.getMethod("getId").invoke(t);
            out.add(id);
            switch (id) {
                case COMPOUND -> {
                    Map<String, Object> m = new TreeMap<>();
                    Method get = c.getMethod("get", String.class);
                    for (Object k : (Set<?>) c.getMethod("getAllKeys").invoke(t)) m.put((String) k, lowerTag(get.invoke(t, k), depth + 1));
                    out.add(m);
                }
                case LIST -> {
                    out.add(c.getMethod("getElementType").invoke(t));
                    for (Object e : (List<?>) t) out.add(lowerTag(e, depth + 1));
                }
                case BYTE_ARRAY -> out.add(TypeKit.lower(c.getMethod("getAsByteArray").invoke(t)));
                case INT_ARRAY -> out.add(TypeKit.lower(c.getMethod("getAsIntArray").invoke(t)));
                case LONG_ARRAY -> out.add(TypeKit.lower(c.getMethod("getAsLongArray").invoke(t)));
                case STRING -> out.add(c.getMethod("getAsString").invoke(t));
                case END -> { }
                default -> out.add(c.getMethod("getAsNumber").invoke(t));
            }
            return out;
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new TypeKit.Unsupported("lower nbt " + t.getClass().getName() + ": " + e);
        }
    }

    static Object lowerCapture(Object v) {
        if (v instanceof CaptureData d) return List.of("bytes", java.util.HexFormat.of().formatHex(d.bytes()));
        if (v instanceof CaptureStream s) return List.of("bytes", java.util.HexFormat.of().formatHex(s.toByteArray()));
        if (v instanceof java.nio.file.Path p) {
            try {
                return java.nio.file.Files.isRegularFile(p)
                        ? List.of("file", java.util.HexFormat.of().formatHex(java.nio.file.Files.readAllBytes(p))) : List.of("nofile");
            } catch (IOException e) {
                return List.of("file", "?");
            }
        }
        if (v instanceof DataInputStream d) {
            // 읽은 뒤 남은 바이트 수(DataInput 은 얼마나 읽었나가 보인다)
            try {
                return List.of("remaining", d.available());
            } catch (IOException e) {
                return List.of("remaining", "?");
            }
        }
        // gzip 입력은 안쪽 버퍼 크기에 따라 미리 읽는 양이 달라 남은 양을 견주지 않는다
        if (v instanceof ByteArrayInputStream) return List.of("stream");
        return null;
    }
}
