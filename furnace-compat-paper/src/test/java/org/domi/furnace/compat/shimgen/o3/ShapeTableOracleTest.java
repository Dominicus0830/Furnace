package org.domi.furnace.compat.shimgen.o3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

/**
 * T-263 S5 모양 정적 표 정답기. 바닐라 jar 의 모양 정적 필드(서술자·제네릭 서명에 VoxelShape — 블록 클래스의 모양·모양 배열·모양 지도)
 * <b>전부</b>를, 바닐라(부트스트랩)와 shim(실행 때와 같은 로더 + 시험 서버)에서 클래스를 초기화해 읽고 견준다. 생성기·추출기 코드를 쓰지 않고
 * 따로 읽는다: 바닐라 모양은 {@code toAabbs()} 상자, shim 모양은 상자 목록, 배열은 칸마다, 지도는 구체 클래스 이름·돌기 순서·열쇠까지.
 * 객체 같음도 견준다 — 빈 모양·꽉 찬 블록 객체인지, 그리고 견주는 모든 값에 걸쳐 "같은 객체" 관계(필드·배열 칸·클래스 사이 별칭)가 같아야 한다.
 *
 * <p>shim 에 값이 있는 필드는 모두 같아야 하고(다르면 실패), 값이 없는 필드는 이유를 센다. 카나리: 채운 필드마다 바닐라의 다음 다른 값과
 * 견줘 "다름"으로 나와야 한다(비교가 무뎌지지 않았는지). CI 로그에는 숫자만, 이름은 빌드 보고서에만.
 */
@Tag("full-shells")
class ShapeTableOracleTest {
    static final String VS = "net/minecraft/world/phys/shapes/VoxelShape";

    record Fld(String owner, String name, String desc) {}

    @Test
    void everyShapeStaticFieldMatchesVanilla() throws Exception {
        List<URL> vanUrls = new ArrayList<>();
        Path vanillaJar = null;
        for (String line : Files.readAllLines(Path.of(System.getProperty("furnace.vdiff.classpath")), StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            Path p = Path.of(line.trim());
            vanUrls.add(p.toUri().toURL());
            if (vanillaJar == null) {
                try (ZipFile z = new ZipFile(p.toFile())) {
                    if (z.getEntry(VS + ".class") != null) vanillaJar = p;
                }
            }
        }
        assertTrue(vanillaJar != null, "no vanilla server jar in the classpath list");
        List<Fld> fields = shapeStatics(vanillaJar);
        List<URL> shimUrls = new ArrayList<>();
        shimUrls.add(Path.of(System.getProperty("furnace.fullShells.jar")).toUri().toURL());
        for (String p : System.getProperty("java.class.path").split(File.pathSeparator)) if (!p.isBlank()) shimUrls.add(Path.of(p).toUri().toURL());

        Map<String, Integer> notFilled = new TreeMap<>();
        List<String> failures = new ArrayList<>();
        int same = 0, canaryCaught = 0, canaryMissed = 0;
        StringBuilder table = new StringBuilder("# T-263 S5 shape table oracle (names - build output only)\n\n| field | result |\n|---|---|\n");
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader van = new URLClassLoader("vanilla", vanUrls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
             URLClassLoader shim = new URLClassLoader("shim", shimUrls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            java.io.PrintStream out = System.out, err = System.err;
            Thread.currentThread().setContextClassLoader(van);
            try {
                Class.forName("net.minecraft.SharedConstants", true, van).getMethod("tryDetectVersion").invoke(null);
                Class.forName("net.minecraft.server.Bootstrap", true, van).getMethod("bootStrap").invoke(null);
            } finally {
                System.setOut(out);   // 바닐라 Bootstrap 이 표준 출력을 자기 로거로 바꾼다
                System.setErr(err);
            }
            Class<?> sp = Class.forName(ShimBlockProbe.class.getName(), true, shim);
            assertTrue(sp.getClassLoader() == shim, "shim probe must come from the isolated loader");
            Thread.currentThread().setContextClassLoader(shim);
            sp.getMethod("init").invoke(null);
            try {
                Side v = new Side(van, "toAabbs");
                Side s = new Side(shim, null);
                // 1) 값 읽기 — shim 에 값이 있는 필드만 견준다
                List<Fld> filled = new ArrayList<>();
                List<Object> vv = new ArrayList<>(), sv = new ArrayList<>();
                for (Fld f : fields) {
                    Thread.currentThread().setContextClassLoader(van);
                    Object a = v.read(f);
                    Thread.currentThread().setContextClassLoader(shim);
                    Object b;
                    try {
                        b = s.read(f);
                    } catch (NoSuchFieldException e) {
                        b = null;
                        notFilled.merge("field missing in the shim class", 1, Integer::sum);
                        table.append("| ").append(f.owner()).append('.').append(f.name()).append(" | missing |\n");
                        continue;
                    }
                    if (b == null) {
                        String why = a == null ? "null in vanilla too" : "no value in the shim";
                        notFilled.merge(why, 1, Integer::sum);
                        table.append("| ").append(f.owner()).append('.').append(f.name()).append(" | ").append(why).append(" |\n");
                        continue;
                    }
                    filled.add(f);
                    vv.add(a);
                    sv.add(b);
                }
                // 2) 정준 글(객체 번호는 견주는 값 전체에서 처음 본 순서) — 같은 순서로 돌므로 별칭 관계가 같으면 번호도 같다
                v.ids.clear();
                s.ids.clear();
                List<String> vc = new ArrayList<>(), sc = new ArrayList<>();
                for (int i = 0; i < filled.size(); i++) {
                    vc.add(v.canon(vv.get(i)));
                    sc.add(s.canon(sv.get(i)));
                }
                for (int i = 0; i < filled.size(); i++) {
                    Fld f = filled.get(i);
                    String label = f.owner() + "." + f.name();
                    if (vc.get(i).equals(sc.get(i))) {
                        same++;
                        table.append("| ").append(label).append(" | same |\n");
                    } else {
                        failures.add(label + ": vanilla=" + cut(vc.get(i)) + " shim=" + cut(sc.get(i)));
                        table.append("| ").append(label).append(" | DIFFERS |\n");
                    }
                    // 카나리: 바닐라의 다음 다른 값과 견주면 달라야 한다
                    for (int j = 1; j < filled.size(); j++) {
                        String other = vc.get((i + j) % filled.size());
                        if (other.equals(vc.get(i))) continue;
                        if (!other.equals(sc.get(i))) canaryCaught++;
                        else {
                            canaryMissed++;
                            failures.add("canary missed: " + label);
                        }
                        break;
                    }
                }
            } finally {
                Thread.currentThread().setContextClassLoader(shim);
                sp.getMethod("close").invoke(null);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
        Path report = Path.of(System.getProperty("furnace.s5.report", "build/reports/shape-table-oracle/report.md"));
        Files.createDirectories(report.getParent());
        Files.writeString(report, table.toString(), StandardCharsets.UTF_8);
        System.out.println("[shape-table-oracle] fields=" + fields.size() + " same=" + same + " notFilled=" + notFilled
                + " canary caught=" + canaryCaught + " missed=" + canaryMissed + " failures=" + failures.size());
        assertEquals(List.of(), failures.stream().limit(20).toList(), "shape static fields filled in the shim must equal vanilla");
        // 2026-11-27 측정: 모양 정적 필드 411 중 같음 404(생성기 채움 402 + 손 shim Shapes 의 빈·꽉 찬 모양 2). 값 없음 7: Paper 가 정적 초기화를
        // 바꾼 클래스(액체·맹그로브 번식체·벽 횃불·Shapes)의 4필드, 바닐라가 실행 중에 채우는 캐시·함수·상태 열쇠 지도 3
        assertTrue(same >= 404, "shape static fields proved: " + same);
        assertEquals(0, canaryMissed);
        assertTrue(canaryCaught >= 400, "canaries told apart: " + canaryCaught);
    }

    private static String cut(String s) { return s.length() > 300 ? s.substring(0, 300) + "…" : s; }

    /** 바닐라 jar 의 모양 정적 필드(클래스 이름 순, 클래스 파일의 선언 순). */
    static List<Fld> shapeStatics(Path jar) throws Exception {
        List<Fld> out = new ArrayList<>();
        try (ZipFile z = new ZipFile(jar.toFile())) {
            List<String> names = new ArrayList<>();
            for (ZipEntry e : java.util.Collections.list(z.entries())) {
                if (e.getName().startsWith("net/minecraft/") && e.getName().endsWith(".class")) names.add(e.getName());
            }
            names.sort(null);
            for (String n : names) {
                ClassNode cn = new ClassNode();
                try (InputStream in = z.getInputStream(z.getEntry(n))) {
                    new ClassReader(in.readAllBytes()).accept(cn, ClassReader.SKIP_CODE);
                }
                for (FieldNode f : cn.fields) {
                    if ((f.access & Opcodes.ACC_STATIC) != 0 && (f.signature != null ? f.signature : f.desc).contains(VS)) {
                        out.add(new Fld(cn.name.replace('/', '.'), f.name, f.desc));
                    }
                }
            }
        }
        return out;
    }

    /** 한쪽(바닐라 또는 shim) 값 읽기·정준 글. toAabbs 가 null 이면 shim 상자 목록(furnaceBoxes). */
    static final class Side {
        final ClassLoader loader;
        final Class<?> voxelShape;
        final Object empty, block;
        final java.lang.invoke.MethodHandle boxes;
        final boolean vanilla;
        final IdentityHashMap<Object, Integer> ids = new IdentityHashMap<>();

        Side(ClassLoader loader, String toAabbs) throws Exception {
            this.loader = loader;
            // 리플렉션 목록(getMethod·getDeclaredField)은 쓰지 않는다 — shim 클래스에는 합친 Paper 멤버가 있어 그 형식(moonrise 등)을 못 찾는다.
            // 메서드 핸들은 그 멤버 하나만 잇는다.
            voxelShape = Class.forName(VS.replace('/', '.'), false, loader);
            Class<?> shapes = Class.forName("net.minecraft.world.phys.shapes.Shapes", true, loader);
            var lk = java.lang.invoke.MethodHandles.publicLookup();
            var vsType = java.lang.invoke.MethodType.methodType(voxelShape);
            try {
                empty = lk.findStatic(shapes, "empty", vsType).invoke();
                block = lk.findStatic(shapes, "block", vsType).invoke();
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
            vanilla = toAabbs != null;
            boxes = vanilla ? lk.findVirtual(voxelShape, toAabbs, java.lang.invoke.MethodType.methodType(List.class))
                    : lk.findVirtual(voxelShape, "furnaceBoxes", java.lang.invoke.MethodType.methodType(double[].class));
        }

        Object read(Fld f) throws Exception {
            Class<?> c = Class.forName(f.owner(), true, loader);
            String d = f.desc();
            Class<?> type = Class.forName(d.startsWith("[") ? d.replace('/', '.') : org.objectweb.asm.Type.getType(d).getClassName(), false, loader);
            try {
                return java.lang.invoke.MethodHandles.privateLookupIn(c, java.lang.invoke.MethodHandles.lookup()).findStaticGetter(c, f.name(), type).invoke();
            } catch (NoSuchFieldException e) {
                throw e;
            } catch (Throwable t) {
                throw new IllegalStateException(f.owner() + "." + f.name(), t);
            }
        }

        private String id(Object o) {
            Integer i = ids.get(o);
            if (i == null) ids.put(o, i = ids.size());
            return "#" + i;
        }

        String canon(Object v) throws Exception {
            if (v == null) return "null";
            if (voxelShape.isInstance(v)) return shape(v);
            if (v.getClass().isArray()) {
                StringBuilder b = new StringBuilder(id(v)).append('[');
                for (int i = 0; i < Array.getLength(v); i++) b.append(i == 0 ? "" : " | ").append(canon(Array.get(v, i)));
                return b.append(']').toString();
            }
            if (v instanceof Map<?, ?> m) {
                StringBuilder b = new StringBuilder(id(v)).append(' ').append(v.getClass().getName()).append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    Object k = e.getKey();
                    String ks = k instanceof Enum<?> en ? en.getDeclaringClass().getName() + "." + en.name() : k.getClass().getName() + ":" + k;
                    b.append(first ? "" : ", ").append(ks).append('=').append(canon(e.getValue()));
                    first = false;
                }
                return b.append('}').toString();
            }
            return "other:" + v.getClass().getName();   // 캐시·함수 — 견주지 않는 꼴(shim 에 값이 있으면 다르게 나와 실패한다)
        }

        private Object invoke(Object s) throws Exception {
            try {
                return boxes.invoke(s);
            } catch (Exception | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }

        private String shape(Object s) throws Exception {
            if (s == empty) return "EMPTY";
            if (s == block) return "BLOCK";
            StringBuilder b = new StringBuilder(id(s)).append('(');
            if (vanilla) {
                boolean first = true;
                for (Object box : (List<?>) invoke(s)) {
                    for (String a : new String[] {"minX", "minY", "minZ", "maxX", "maxY", "maxZ"}) {
                        b.append(first ? "" : ",").append(box.getClass().getField(a).getDouble(box));
                        first = false;
                    }
                }
            } else {
                double[] d = (double[]) invoke(s);
                for (int i = 0; i < d.length; i++) b.append(i == 0 ? "" : ",").append(d[i]);
            }
            return b.append(')').toString();
        }
    }
}
