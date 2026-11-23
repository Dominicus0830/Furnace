package org.domi.furnace.compat.shimgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * T-262: 채운 껍데기 멤버 ↔ 바닐라 대조 시험(공통 틀). 채워진 멤버(표지 {@code @NmsShellMember} 가 없는 껍데기 멤버)마다 이름 푼 바닐라 클래스와
 * 같은 입력으로 같은 값이 나오는지 본다. 영역 스레드가 채우는 멤버 종류를 늘릴 때는 {@link #checkClass} 에 종류 하나를 더한다.
 * 다르면 실패 — 기준선에 예외로 적어 초록을 만들지 않는다(AGENTS.md 규칙 5). 비교할 바닐라가 만들어지지 않는 입력은 "기준 불가"로 세어 숫자만 보고한다.
 *
 * <p>종류: 열거형 상수(이름·서수·원시/문자열 필드 값), 정적 리터럴 필드, 단순 생성자 + 단순 읽기(같은 입력으로 만든 두 객체의 읽기 결과),
 * 레코드 기본 메서드(hashCode·toString·equals). 바닐라 jar 는 캐시에서 읽기만 한다(T-235 {@code furnace.vdiff.classpath} 와 같은 목록).
 */
@Tag("full-shells")
class FillParityTest {
    static final String MARKER = "Lorg/domi/furnace/compat/nms/NmsShellMember;";

    final List<String> failures = new ArrayList<>();
    int refGetters, classes, enumConsts, statics, instances, getters, recordChecks, referenceUnusable, noVanilla;

    @Test
    void filledMembersMatchVanilla() throws Exception {
        Path jar = Path.of(System.getProperty("furnace.fullShells.jar"));
        List<URL> shellUrls = new ArrayList<>();
        for (String p : System.getProperty("furnace.fullShells.classpath").split(File.pathSeparator)) if (!p.isBlank()) shellUrls.add(Path.of(p).toUri().toURL());
        List<URL> vanUrls = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(System.getProperty("furnace.vdiff.classpath")), StandardCharsets.UTF_8)) {
            if (!line.isBlank()) vanUrls.add(Path.of(line.trim()).toUri().toURL());
        }
        List<String> names = new ArrayList<>();
        try (JarFile jf = new JarFile(jar.toFile())) {
            for (JarEntry e : java.util.Collections.list(jf.entries())) {
                String n = e.getName();
                if (n.endsWith(".class") && n.startsWith("net/minecraft/")) names.add(n.substring(0, n.length() - 6));
            }
            names.sort(null);
            try (URLClassLoader shell = new URLClassLoader("shells", shellUrls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
                 URLClassLoader van = new URLClassLoader("vanilla", vanUrls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
                bootstrap(van);
                for (String n : names) {
                    ClassNode cn = new ClassNode();
                    try (var in = jf.getInputStream(jf.getEntry(n + ".class"))) {
                        new ClassReader(in).accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    }
                    if (!hasFill(cn)) continue;
                    classes++;
                    Class<?> sc;
                    Class<?> vc;
                    try {
                        sc = Class.forName(n.replace('/', '.'), true, shell);
                    } catch (Throwable t) {
                        failures.add(n + ": shell class does not initialize: " + t);
                        continue;
                    }
                    try {
                        vc = Class.forName(n.replace('/', '.'), true, van);
                    } catch (Throwable t) {
                        noVanilla++;
                        continue;
                    }
                    try {
                        checkClass(cn, sc, vc);
                    } catch (Throwable t) {
                        failures.add(n + ": " + t);
                    }
                }
            }
        }
        // CI 로그에는 숫자만 — 이름은 실패 메시지에만(실패해야 나온다)
        System.out.println("[fill-parity] classes=" + classes + " enumConstants=" + enumConsts + " statics=" + statics + " instances=" + instances + " getters=" + getters
                + " refGetters=" + refGetters + " recordChecks=" + recordChecks + " referenceUnusable=" + referenceUnusable + " noVanillaClass=" + noVanilla + " failures=" + failures.size());
        // 대조 수가 줄어도 초록이 되지 않게 종류별 하한(2026-11-23 측정의 절반 안팎)과 바닐라 클래스 없음 상한
        assertTrue(classes > 900, "filled classes: " + classes);
        assertTrue(enumConsts > 250, "enum constants compared: " + enumConsts);
        assertTrue(instances > 800, "instances compared: " + instances);
        assertTrue(getters > 800, "getters compared: " + getters);
        assertTrue(refGetters > 100, "reference getters compared by identity: " + refGetters);
        assertTrue(recordChecks > 250, "record checks: " + recordChecks);
        assertTrue(noVanilla < 20, "classes without a vanilla counterpart: " + noVanilla);
        assertEquals(List.of(), failures.stream().limit(30).toList(), "filled members must match the vanilla values");
    }

    /** 표지 없는 멤버가 하나라도 있나(채운 클래스). */
    static boolean hasFill(ClassNode cn) {
        for (MethodNode m : cn.methods) if (isFilledMethod(cn, m)) return true;
        for (FieldNode f : cn.fields) if (isFilledStatic(cn, f)) return true;
        return false;
    }

    static boolean marked(List<AnnotationNode> l) {
        if (l == null) return false;
        for (AnnotationNode a : l) if (a.desc.equals(MARKER)) return true;
        return false;
    }

    static boolean isFilledMethod(ClassNode cn, MethodNode m) {
        if ((m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0) return false;
        if (m.name.equals("<clinit>") || marked(m.invisibleAnnotations)) return false;
        boolean en = (cn.access & Opcodes.ACC_ENUM) != 0;
        if (en && (m.name.equals("values") || m.name.equals("valueOf")) && (m.access & Opcodes.ACC_STATIC) != 0) return false;
        return true;
    }

    static boolean isFilledStatic(ClassNode cn, FieldNode f) {
        return (f.access & Opcodes.ACC_STATIC) != 0 && f.value == null && (f.access & (Opcodes.ACC_ENUM | Opcodes.ACC_SYNTHETIC)) == 0 && !marked(f.invisibleAnnotations);
    }

    void checkClass(ClassNode cn, Class<?> sc, Class<?> vc) throws Exception {
        // 정적 리터럴
        for (FieldNode f : cn.fields) {
            if (!isFilledStatic(cn, f)) continue;
            Field sf = sc.getDeclaredField(f.name);
            Field vf = vc.getDeclaredField(f.name);
            sf.setAccessible(true);
            vf.setAccessible(true);
            statics++;
            same(cn.name + "." + f.name, vf.get(null), sf.get(null));
        }
        // 열거형 상수
        if (sc.isEnum() && vc.isEnum()) {
            boolean filled = false;
            for (MethodNode m : cn.methods) if (m.name.equals("<init>") && isFilledMethod(cn, m)) filled = true;
            if (filled) {
                Object[] sv = sc.getEnumConstants();
                Object[] vv = vc.getEnumConstants();
                assertEquals(vv.length, sv.length, "enum constant count");
                for (int i = 0; i < vv.length; i++) {
                    enumConsts++;
                    assertEquals(((Enum<?>) vv[i]).name(), ((Enum<?>) sv[i]).name());
                    for (Field f : vc.getDeclaredFields()) {
                        if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic() || !simple(f.getType())) continue;
                        Field s = sc.getDeclaredField(f.getName());
                        f.setAccessible(true);
                        s.setAccessible(true);
                        same(cn.name + "#" + ((Enum<?>) vv[i]).name() + "." + f.getName(), f.get(vv[i]), s.get(sv[i]));
                    }
                }
            }
            return;
        }
        // 단순 생성자 + 읽기: 같은 입력으로 만든 두 객체
        for (MethodNode cm : cn.methods) {
            if (!cm.name.equals("<init>") || !isFilledMethod(cn, cm) || (sc.getModifiers() & Modifier.ABSTRACT) != 0) continue;
            Constructor<?> sk = findCtor(sc, cm.desc);
            Constructor<?> vk = findCtor(vc, cm.desc);
            if (sk == null || vk == null) continue;
            Object[] args = sample(sk.getParameterTypes());
            Object vo;
            try {
                sk.setAccessible(true);
                vk.setAccessible(true);
                vo = vk.newInstance(args);
            } catch (Throwable t) {
                referenceUnusable++;
                continue;
            }
            Object so = sk.newInstance(args);
            instances++;
            for (MethodNode gm : cn.methods) {
                if (gm.name.equals("<init>") || (gm.access & Opcodes.ACC_STATIC) != 0 || !isFilledMethod(cn, gm) || !gm.desc.startsWith("()")) continue;
                if (gm.name.equals("hashCode") || gm.name.equals("toString")) continue;
                Method s = sc.getDeclaredMethod(gm.name);
                Method v = vc.getDeclaredMethod(gm.name);
                s.setAccessible(true);
                v.setAccessible(true);
                Object vr = v.invoke(vo);
                Object sr = s.invoke(so);
                getters++;
                if (simple(v.getReturnType())) same(cn.name + "#" + gm.name, vr, sr);
                else if (vr != sr && !(vr instanceof Enum)) failures.add(cn.name + "#" + gm.name + ": returned reference differs (vanilla=" + vr + " shell=" + sr + ")");
                if (vr != null && !simple(v.getReturnType())) refGetters++;
            }
            if (sc.isRecord()) {
                recordChecks++;
                for (MethodNode om : cn.methods) {
                    if (om.name.equals("hashCode") && isFilledMethod(cn, om)) same(cn.name + "#hashCode", vo.hashCode(), so.hashCode());
                    if (om.name.equals("toString") && isFilledMethod(cn, om)) same(cn.name + "#toString", vo.toString(), so.toString());
                    if (om.name.equals("equals") && isFilledMethod(cn, om)) {
                        Object so2 = sk.newInstance(args);
                        Object vo2 = vk.newInstance(args);
                        same(cn.name + "#equals(same)", vo.equals(vo2), so.equals(so2));
                        Object[] other = sample(sk.getParameterTypes());   // 마커 객체가 다르다(참조 인자가 있으면 equals 가 갈린다)
                        Object so3 = sk.newInstance(other);
                        Object vo3 = vk.newInstance(other);
                        same(cn.name + "#equals(other)", vo.equals(vo3), so.equals(so3));
                    }
                }
            }
        }
    }

    static Constructor<?> findCtor(Class<?> c, String desc) {
        for (Constructor<?> k : c.getDeclaredConstructors()) if (org.objectweb.asm.Type.getConstructorDescriptor(k).equals(desc)) return k;
        return null;
    }

    static boolean simple(Class<?> t) { return t.isPrimitive() || t == String.class; }

    static Object[] sample(Class<?>[] ps) {
        Object[] a = new Object[ps.length];
        for (int i = 0; i < ps.length; i++) {
            Class<?> t = ps[i];
            a[i] = t == boolean.class ? Boolean.TRUE : t == int.class ? (Object) (3 + i) : t == long.class ? (Object) (4L + i) : t == float.class ? (Object) (1.5F + i)
                    : t == double.class ? (Object) (2.5D + i) : t == char.class ? (Object) 'c' : t == byte.class ? (Object) (byte) 1 : t == short.class ? (Object) (short) 2
                    : t == String.class ? "s" + i : marker(t, i);
        }
        return a;
    }

    /** JDK 형식 인자에는 자리마다 다른 객체를 넣는다 — 읽기가 같은 객체를 돌려주는지 동일성으로 본다(필드를 뒤바꿔 채우면 걸린다). 그 밖의 형식은 null. */
    static Object marker(Class<?> t, int i) {
        if (t == Object.class) return new Object();
        if (t == Integer.class) return Integer.valueOf(1000 + i);
        if (t == Long.class) return Long.valueOf(2000L + i);
        if (t == Double.class) return Double.valueOf(3000.5 + i);
        if (t == Float.class) return Float.valueOf(4000.5F + i);
        if (t == Boolean.class) return Boolean.TRUE;
        if (t == java.util.UUID.class) return java.util.UUID.randomUUID();
        if (t == CharSequence.class) return new StringBuilder("c" + i);
        if (t == java.util.List.class || t == java.util.Collection.class) return new ArrayList<>(List.of("l" + i));
        if (t == java.util.Map.class) return new java.util.HashMap<>(java.util.Map.of("k", "v" + i));
        if (t == java.util.Set.class) return new java.util.HashSet<>(List.of("s" + i));
        if (t == java.util.Optional.class) return java.util.Optional.of("o" + i);
        return null;
    }

    void same(String what, Object vanilla, Object shell) {
        if (!Objects.equals(vanilla, shell)) failures.add(what + ": vanilla=" + vanilla + " shell=" + shell);
    }

    /** 바닐라 레지스트리를 채운다(정적 초기화가 레지스트리를 보는 클래스용). 안 되면 그 클래스만 "바닐라 없음"으로 센다. */
    static void bootstrap(ClassLoader van) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        java.io.PrintStream out = System.out;
        java.io.PrintStream err = System.err;
        Thread.currentThread().setContextClassLoader(van);
        try {
            Class.forName("net.minecraft.SharedConstants", true, van).getMethod("tryDetectVersion").invoke(null);
            Class.forName("net.minecraft.server.Bootstrap", true, van).getMethod("bootStrap").invoke(null);
        } catch (Throwable t) {
            System.out.println("[fill-parity] vanilla bootstrap failed: " + t);
        } finally {
            System.setOut(out);   // 바닐라 Bootstrap 이 표준 출력을 자기 로거로 바꾼다 — 시험 출력은 되돌린다
            System.setErr(err);
            Thread.currentThread().setContextClassLoader(old);
        }
    }
}
