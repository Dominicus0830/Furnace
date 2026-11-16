package org.domi.furnace.tools.extract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * 이전 도구(난독화 이름)를 새 도구(Mojang 이름)로 옮긴 것이 "같은 일을 하는지" 바이트코드로 견준다.
 * 이전 도구의 클래스 파일을 공식 대응표로 이름만 풀면(JarRemapper) 이전 코드가 부르던 바닐라 메서드·필드가 Mojang 이름으로 나오는데,
 * 새 도구가 메서드마다 같은 호출 순서를 하면 옮김이 충실한 것이다. 다른 곳은 목록으로 보여 사람이 본다(이름 추측이 틀렸거나 일부러 바꾼 곳).
 * 견주는 것: 메서드 호출(소유 클래스·이름·서술자), 필드 접근, new/checkcast/instanceof, 상수(문자열·숫자), invokedynamic.
 * 새 도구가 공용 도우미(Vanilla.boot)를 거치는 것은 알려진 차이라 따로 표시한다.
 */
public final class PortCheck {
    /** 클래스 한 개의 비교 결과. */
    public record ClassReport(String className, int methodsCompared, List<String> differences) {
        public boolean same() {
            return differences.isEmpty();
        }
    }

    private PortCheck() {}

    /** 이전 클래스 폴더(이름을 풀기 전)와 새 클래스 폴더를 견준다. 새 폴더에 없는 이전 클래스는 "이관 안 됨"으로 보고한다. */
    public static List<ClassReport> compare(Path legacyClasses, Path newClasses, JarRemapper remapper) throws IOException {
        Map<String, Map<String, List<String>>> legacy = scan(legacyClasses, remapper);
        Map<String, Map<String, List<String>>> fresh = scan(newClasses, null);
        List<ClassReport> res = new ArrayList<>();
        for (var e : legacy.entrySet()) {
            String cls = e.getKey();
            Map<String, List<String>> nm = fresh.get(cls);
            List<String> diffs = new ArrayList<>();
            if (nm == null) {
                res.add(new ClassReport(cls, 0, List.of("새 도구에 이 클래스가 없다")));
                continue;
            }
            int compared = 0;
            for (var m : e.getValue().entrySet()) {
                List<String> other = nm.get(m.getKey());
                if (other == null) {
                    diffs.add("메서드 " + m.getKey() + " 가 새 도구에 없다");
                    continue;
                }
                compared++;
                diffs.addAll(diff(m.getKey(), m.getValue(), other));
            }
            for (String k : nm.keySet()) if (!e.getValue().containsKey(k)) diffs.add("메서드 " + k + " 는 새 도구에만 있다");
            res.add(new ClassReport(cls, compared, diffs));
        }
        return res;
    }

    /** 호출 순서 비교: 앞에서부터 같은 만큼 건너뛰고, 처음 어긋난 곳부터 양쪽 나머지에서 서로 상대에 없는 항목을 적는다. */
    static List<String> diff(String method, List<String> a, List<String> b) {
        if (a.equals(b)) return List.of();
        List<String> res = new ArrayList<>();
        int i = 0;
        while (i < a.size() && i < b.size() && a.get(i).equals(b.get(i))) i++;
        List<String> restA = new ArrayList<>(a.subList(i, a.size()));
        List<String> restB = new ArrayList<>(b.subList(i, b.size()));
        List<String> onlyA = new ArrayList<>(restA);
        for (String x : restB) onlyA.remove(x);
        List<String> onlyB = new ArrayList<>(restB);
        for (String x : restA) onlyB.remove(x);
        if (onlyA.isEmpty() && onlyB.isEmpty()) {
            res.add(method + ": 호출은 같고 순서만 다름(처음 다른 곳 #" + i + ")");
            return res;
        }
        for (String x : onlyA) res.add(method + ": - 이전에만  " + x);
        for (String x : onlyB) res.add(method + ": + 새것에만  " + x);
        return res;
    }

    private static Map<String, Map<String, List<String>>> scan(Path dir, JarRemapper remapper) throws IOException {
        Map<String, Map<String, List<String>>> res = new LinkedHashMap<>();
        List<Path> files;
        try (Stream<Path> s = Files.walk(dir)) {
            files = s.filter(p -> p.toString().endsWith(".class")).sorted().toList();
        }
        for (Path f : files) {
            byte[] data = Files.readAllBytes(f);
            if (remapper != null) data = remapper.remapClass(data);
            ClassReader r = new ClassReader(data);
            String name = normalize(r.getClassName());
            Map<String, List<String>> methods = new LinkedHashMap<>();
            r.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String mname, String desc, String sig, String[] ex) {
                    List<String> ops = new ArrayList<>();
                    methods.put(mname + normalizeDesc(desc), ops);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int op, String owner, String n, String d, boolean itf) {
                            ops.add("call " + normalize(owner) + "." + n + normalizeDesc(d));
                        }

                        @Override
                        public void visitFieldInsn(int op, String owner, String n, String d) {
                            ops.add("field " + normalize(owner) + "." + n + ":" + normalizeDesc(d));
                        }

                        @Override
                        public void visitTypeInsn(int op, String type) {
                            ops.add("type " + op + " " + normalize(type));
                        }

                        @Override
                        public void visitLdcInsn(Object v) {
                            ops.add("ldc " + (v instanceof Type t ? "class " + normalizeDesc(t.getDescriptor()) : v instanceof String ? "\"" + v + "\"" : String.valueOf(v)));
                        }

                        @Override
                        public void visitInvokeDynamicInsn(String n, String d, Handle bsm, Object... args) {
                            StringBuilder sb = new StringBuilder("indy " + n + normalizeDesc(d));
                            for (Object a : args) {
                                if (a instanceof Handle h) sb.append(" handle ").append(normalize(h.getOwner())).append('.').append(h.getName());
                            }
                            ops.add(sb.toString());
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            res.put(name, methods);
        }
        return res;
    }

    /** 이전 도구는 기본 패키지, 새 도구는 furnacetools 패키지 — 비교할 때 패키지를 같게 본다. */
    static String normalize(String internal) {
        return internal.replace("furnacetools/", "");
    }

    static String normalizeDesc(String desc) {
        return desc.replace("furnacetools/", "");
    }
}
