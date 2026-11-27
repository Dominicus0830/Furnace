package org.domi.furnace.tools.extract;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

/**
 * 모양 정적 필드 값(T-263 S5). 채움 표의 H 줄이 있는 클래스(모양 정적 필드가 있는 것)를 바닐라 서버 jar 를 따로 된 로더에 올려
 * 부트스트랩한 뒤 초기화하고, 정적 필드 값을 리플렉션으로 읽어 X 줄로 더한다. 바닐라 코드는 복사하지 않는다 — 남는 것은 값(상자 좌표·
 * 객체 같음 관계·지도 열쇠)뿐이다. 바닐라 표에만 쓴다(Paper 는 띄우지 않고 H 줄로 같은 클래스인지만 본다).
 *
 * <p>읽는 값: {@code VoxelShape}(상자 목록 = {@code toAabbs()}), {@code VoxelShape[]}, 열쇠가 열거형·정수이고 값이 모양인 지도
 * (EnumMap·Guava 불변 지도·HashMap). 그 밖(캐시·함수·상태 열쇠 지도)이나 null 은 쓰지 않는다(모르면 채우지 않는다).
 * 객체마다 번호를 하나 주어(전체에서, 클래스 이름·필드 선언 순) 같은 객체는 같은 번호다. 빈 모양·꽉 찬 블록 객체는 E·B.
 */
final class ShapeValues {
    private static final String VS = "net.minecraft.world.phys.shapes.VoxelShape";

    private final Class<?> voxelShape;
    private final Method toAabbs;
    private final Object empty;
    private final Object block;
    private final IdentityHashMap<Object, Integer> ids = new IdentityHashMap<>();
    /** 쓰지 않은 필드 수(이유별). */
    final Map<String, Integer> skipped = new TreeMap<>();
    int written;

    private ShapeValues(ClassLoader cl) throws ReflectiveOperationException {
        voxelShape = Class.forName(VS, false, cl);
        toAabbs = voxelShape.getMethod("toAabbs");
        Class<?> shapes = Class.forName("net.minecraft.world.phys.shapes.Shapes", true, cl);
        empty = shapes.getMethod("empty").invoke(null);
        block = shapes.getMethod("block").invoke(null);
    }

    /** 바닐라 jar 와 라이브러리로 H 줄 클래스의 모양 필드를 읽어 t 에 X 줄로 더한다. 돌려준 것은 요약(숫자만). */
    static String addTo(FillTable t, Path serverJar, List<Path> libraries) throws IOException {
        List<URL> urls = new ArrayList<>();
        urls.add(serverJar.toUri().toURL());
        for (Path l : libraries) urls.add(l.toUri().toURL());
        List<String> owners = new ArrayList<>(t.clinitHashOwners());   // 이름 순(TreeMap)
        // 로더는 닫지 않는다 — 부트스트랩이 띄운 바닐라 스레드·로그 종료 훅이 JVM 이 끝날 때까지 그 클래스를 쓴다
        URLClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
        try (ZipFile zf = new ZipFile(serverJar.toFile())) {
            ClassLoader prev = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(cl);
            try {
                Class.forName("net.minecraft.SharedConstants", true, cl).getMethod("tryDetectVersion").invoke(null);
                Class.forName("net.minecraft.server.Bootstrap", true, cl).getMethod("bootStrap").invoke(null);
                ShapeValues sv = new ShapeValues(cl);
                for (String owner : owners) sv.readClass(t, cl, zf, owner);
                return "shape statics: " + sv.written + " written, skipped " + sv.skipped;
            } finally {
                Thread.currentThread().setContextClassLoader(prev);
            }
        } catch (ReflectiveOperationException e) {
            throw new IOException("could not boot the vanilla jar for shape statics", e);
        }
    }

    private void readClass(FillTable t, ClassLoader cl, ZipFile zf, String owner) throws IOException, ReflectiveOperationException {
        ClassNode cn = new ClassNode();
        try (InputStream in = zf.getInputStream(zf.getEntry(owner + ".class"))) {
            new ClassReader(in.readAllBytes()).accept(cn, ClassReader.SKIP_CODE);
        }
        Class<?> c = Class.forName(owner.replace('/', '.'), true, cl);
        for (FieldNode f : cn.fields) {   // 클래스 파일의 선언 순 — 번호가 늘 같게
            if ((f.access & Opcodes.ACC_STATIC) == 0 || !(f.signature != null ? f.signature : f.desc).contains(FillExtractor.VOXEL_SHAPE)) continue;
            Field rf = c.getDeclaredField(f.name);
            if (!Modifier.isStatic(rf.getModifiers())) continue;
            rf.setAccessible(true);
            String v = encode(rf.get(null));
            if (v == null) continue;
            t.add(new FillTable.Shape(owner, f.name, f.desc, v));
            written++;
        }
    }

    private void skip(String why) { skipped.merge(why, 1, Integer::sum); }

    /** 필드 값 하나 → 모양값. 모르는 꼴이면 null. */
    private String encode(Object v) throws ReflectiveOperationException {
        if (v == null) {
            skip("null");
            return null;
        }
        if (voxelShape.isInstance(v)) return "v " + shape(v);
        if (v.getClass().isArray() && voxelShape.isAssignableFrom(v.getClass().getComponentType())) {
            int n = Array.getLength(v);
            for (int i = 0; i < n; i++) {
                if (Array.get(v, i) == null) {
                    skip("array with a null element");
                    return null;
                }
            }
            StringBuilder b = new StringBuilder("a ").append(id(v)).append(' ').append(n);
            for (int i = 0; i < n; i++) b.append(' ').append(shape(Array.get(v, i)));
            return b.toString();
        }
        if (v instanceof Map<?, ?> m) return map(m);
        skip("other value kind (" + v.getClass().getSimpleName() + ")");
        return null;
    }

    private String map(Map<?, ?> m) throws ReflectiveOperationException {
        String kind = switch (m.getClass().getName()) {
            case "java.util.EnumMap" -> "enum";
            case "com.google.common.collect.ImmutableEnumMap" -> "ienum";
            case "com.google.common.collect.RegularImmutableMap", "com.google.common.collect.SingletonImmutableBiMap" -> "imm";
            case "java.util.HashMap" -> "hash";
            default -> null;
        };
        if (kind == null || m.isEmpty()) {
            skip("map of another kind or empty");
            return null;
        }
        String keyType = null;
        List<String> parts = new ArrayList<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            Object k = e.getKey();
            String kt;
            String ks;
            if (k instanceof Enum<?> en) {
                kt = "e:" + en.getDeclaringClass().getName().replace('.', '/');
                ks = en.name();
            } else if (k instanceof Integer i) {
                kt = "i";
                ks = Integer.toString(i);
            } else {
                skip("map key not an enum or integer");
                return null;
            }
            if (keyType != null && !keyType.equals(kt) || e.getValue() == null || !voxelShape.isInstance(e.getValue())) {
                skip("map with mixed keys or non-shape values");
                return null;
            }
            keyType = kt;
            parts.add(ks + "=" + shape(e.getValue()));
        }
        if (kind.equals("hash") && !keyType.equals("i")) {
            skip("hash map with enum keys (order is not stable)");
            return null;
        }
        StringBuilder b = new StringBuilder("m ").append(id(m)).append(' ').append(kind).append(' ').append(keyType).append(' ').append(parts.size());
        for (String p : parts) b.append(' ').append(p);
        return b.toString();
    }

    private int id(Object o) {
        Integer i = ids.get(o);
        if (i == null) ids.put(o, i = ids.size());
        return i;
    }

    /** 모양 하나: E·B, 또는 번호:상자 좌표(toAabbs 순). */
    private String shape(Object s) throws ReflectiveOperationException {
        if (s == empty) return "E";
        if (s == block) return "B";
        StringBuilder b = new StringBuilder().append(id(s)).append(':');
        boolean first = true;
        for (Object box : (List<?>) toAabbs.invoke(s)) {
            for (String a : new String[] {"minX", "minY", "minZ", "maxX", "maxY", "maxZ"}) {
                if (!first) b.append(',');
                first = false;
                b.append(num(box.getClass().getField(a).getDouble(box)));
            }
        }
        return b.toString();
    }

    /** 수: 정수면 정수꼴, 아니면 Double.toString(되읽으면 같은 비트). */
    static String num(double d) {
        if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15 && !(d == 0 && 1 / d < 0)) return Long.toString((long) d);
        return Double.toString(d);
    }
}
