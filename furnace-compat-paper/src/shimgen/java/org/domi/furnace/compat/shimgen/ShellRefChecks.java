package org.domi.furnace.compat.shimgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 껍데기 참조 틀 검사(설계 docs/roadmap/shell-refs-and-field-redirect-2026-11-27.md 2.4). 전체 껍데기를 만든 뒤, 실행 때 로더가 보게 될 바이트
 * (껍데기 jar 의 껍데기·합친 사본 → 호환 클래스)로 본다:
 * <ol>
 *   <li>틀 = 껍데기 모양: 틀의 명세 멤버가 껍데기(상위 포함)에 같은 이름·서술자·정적 여부·공개 범위로 있고, 상위·인터페이스가 같다.
 *       안쪽 틀이면 바깥(생성 shim 또는 합친 사본)의 InnerClasses·NestMembers 와 껍데기 안쪽의 InnerClasses·NestHost 가 서로 맞다.</li>
 *   <li>쓰는 멤버는 살아 있다: 호환 클래스 바이트코드가 틀 클래스를 소유로 부르는 멤버(명령·핸들 상수·부트스트랩 인자)가 껍데기에서 던지지 않는다.</li>
 *   <li>만들 수 없는 껍데기 금지: 틀 클래스의 껍데기가 만들어졌고 클래스 초기화 덫이 아니다.</li>
 * </ol>
 */
final class ShellRefChecks {
    private static final String SHELL_MEMBER = FullShells.SHELL_MEMBER;
    private static final int VISIBILITY = Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE;

    private ShellRefChecks() {}

    /** 멤버 하나. dead = 껍데기 표지(던지는 몸통·값 없는 필드). */
    record Mem(char kind, String name, String desc, int access, boolean dead) {}

    /** InnerClasses 줄 하나(바깥 이름은 익명·지역이면 null). */
    record Inner(String outer, String simpleName, int access) {}

    /** 클래스 파일 머리와 멤버(몸통은 읽지 않는다). */
    record Shape(String name, int access, String superName, List<String> interfaces, Map<String, Mem> members, Map<String, Inner> inners,
            String nestHost, Set<String> nestMembers, boolean shellMark, boolean initTrap) {
        Mem member(char kind, String name, String desc) { return members.get(kind + name + desc); }
    }

    static Shape read(byte[] b) {
        Object[] head = new Object[4];
        Map<String, Mem> members = new LinkedHashMap<>();
        Map<String, Inner> inners = new HashMap<>();
        Set<String> nestMembers = new TreeSet<>();
        String[] nestHost = {null};
        boolean[] mark = {false, false};
        new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                head[0] = name;
                head[1] = access;
                head[2] = superName;
                head[3] = interfaces == null ? List.of() : List.of(interfaces);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!descriptor.equals(SHELL_MEMBER)) return null;
                mark[0] = true;
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String name, Object value) {
                        if (name.equals("initTrap") && Boolean.TRUE.equals(value)) mark[1] = true;
                    }
                };
            }

            @Override
            public void visitInnerClass(String name, String outerName, String innerName, int access) {
                inners.put(name, new Inner(outerName, innerName, access));
            }

            @Override
            public void visitNestHost(String host) { nestHost[0] = host; }

            @Override
            public void visitNestMember(String member) { nestMembers.add(member); }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                boolean[] dead = {false};
                return new FieldVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String d, boolean visible) {
                        if (d.equals(SHELL_MEMBER)) dead[0] = true;
                        return null;
                    }

                    @Override
                    public void visitEnd() { members.put('F' + name + descriptor, new Mem('F', name, descriptor, access, dead[0])); }
                };
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                boolean[] dead = {false};
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String d, boolean visible) {
                        if (d.equals(SHELL_MEMBER)) dead[0] = true;
                        return null;
                    }

                    @Override
                    public void visitEnd() { members.put('M' + name + descriptor, new Mem('M', name, descriptor, access, dead[0])); }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        @SuppressWarnings("unchecked")
        List<String> itfs = (List<String>) head[3];
        return new Shape((String) head[0], (Integer) head[1], (String) head[2], itfs, members, inners, nestHost[0], nestMembers, mark[0], mark[1]);
    }

    /** 실행 때 보이는 클래스: 껍데기 jar(만든 껍데기·합친 사본)가 먼저, 다음 호환 클래스·라이브러리·JDK. 틀은 Existing 이 숨긴다. */
    static final class World {
        private final Map<String, byte[]> shells;
        private final FullShells.Existing existing;
        private final Map<String, Shape> cache = new HashMap<>();

        World(Map<String, byte[]> shells, FullShells.Existing existing) {
            this.shells = shells;
            this.existing = existing;
        }

        Shape shape(String n) {
            if (cache.containsKey(n)) return cache.get(n);
            byte[] b = shells.get(n);
            if (b == null) b = existing.bytesOf(n);
            Shape s = b == null ? null : read(b);
            cache.put(n, s);
            return s;
        }

        /** JVM 해석 순서로 멤버를 찾는다(메서드: 클래스 사슬 → 인터페이스, 필드: 자기 → 인터페이스 → 상위). 못 찾으면 null. */
        Mem resolve(char kind, String owner, String name, String desc) {
            Shape s = shape(owner);
            if (s == null) return null;
            Mem m = s.member(kind, name, desc);
            if (m != null || name.equals("<init>") || name.equals("<clinit>")) return m;
            if (kind == 'F') {
                for (String i : s.interfaces()) if ((m = resolve('F', i, name, desc)) != null) return m;
                return s.superName() == null ? null : resolve('F', s.superName(), name, desc);
            }
            for (String c = s.superName(); c != null; ) {
                Shape cs = shape(c);
                if (cs == null) break;
                if ((m = cs.member('M', name, desc)) != null) return m;
                c = cs.superName();
            }
            return interfaceMethod(owner, name, desc, new HashSet<>());
        }

        private Mem interfaceMethod(String n, String name, String desc, Set<String> seen) {
            for (String c = n; c != null && seen.add(c); ) {
                Shape s = shape(c);
                if (s == null) return null;
                for (String i : s.interfaces()) {
                    Shape is = shape(i);
                    if (is == null) continue;
                    Mem m = is.member('M', name, desc);
                    if (m != null && (m.access() & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0) return m;
                    if ((m = interfaceMethod(i, name, desc, seen)) != null) return m;
                }
                c = s.superName();
            }
            return null;
        }
    }

    /** 호환 클래스가 틀 클래스를 소유로 부르는 멤버 하나. */
    record Use(char kind, String owner, String name, String desc, String from) {}

    /** 검사 1~3. 어긴 것들(없으면 빈 목록). */
    static List<String> check(FullShells g, Map<String, byte[]> classes) {
        List<String> problems = new ArrayList<>();
        Set<String> refs = g.existing.refs();
        if (refs.isEmpty()) return problems;
        World w = new World(classes, g.existing);
        for (String r : refs) {
            // 3) 만들 수 없는 껍데기
            if (!g.shells.contains(r)) {
                String why = g.skipped.get(r);
                problems.add("ref " + r + ": the full shell is not built (" + (why != null ? why : "not in the signature tables or out of scope") + ")");
                continue;
            }
            Shape shell = w.shape(r);
            if (shell.initTrap()) problems.add("ref " + r + ": the full shell is a class-initialization trap (value-less fields outside net.minecraft)");
            // 1) 틀 = 껍데기 모양
            Shape tpl = read(g.existing.refBytes(r));
            if (!java.util.Objects.equals(tpl.superName(), shell.superName()) || !new TreeSet<>(tpl.interfaces()).equals(new TreeSet<>(shell.interfaces()))) {
                problems.add("ref " + r + ": template supertypes " + tpl.superName() + " " + tpl.interfaces() + " differ from the full shell " + shell.superName() + " "
                        + shell.interfaces() + " - stale shim lock, rerun :furnace-compat-paper:shimLock");
            }
            int kindBits = Opcodes.ACC_INTERFACE | Opcodes.ACC_ENUM | Opcodes.ACC_ANNOTATION;
            if ((tpl.access() & kindBits) != (shell.access() & kindBits)) problems.add("ref " + r + ": template class kind differs from the full shell (stale shim lock)");
            for (Mem m : tpl.members().values()) {
                if (!m.dead()) continue;   // 명세 멤버만(틀의 명세 멤버는 전부 표지가 있다 — javac 가 만든 values()·생성기 전용 생성자는 없다)
                Mem s = w.resolve(m.kind(), r, m.name(), m.desc());
                String id = r + "#" + m.name() + (m.kind() == 'F' ? ":" : "") + m.desc();
                if (s == null) problems.add("ref " + id + ": the template member is not in the full shell (stale shim lock)");
                else if ((m.access() & Opcodes.ACC_STATIC) != (s.access() & Opcodes.ACC_STATIC) || (m.access() & VISIBILITY) != (s.access() & VISIBILITY)) {
                    problems.add("ref " + id + ": template access " + m.access() + " differs from the full shell " + s.access() + " (static or visibility, stale shim lock)");
                }
            }
            nested(w, r, shell, problems);
        }
        // 2) 쓰는 멤버는 살아 있다
        List<Use> uses = uses(g.existing, refs);
        for (Use u : uses) {
            Mem m = w.resolve(u.kind(), u.owner(), u.name(), u.desc());
            String id = u.owner() + "#" + u.name() + (u.kind() == 'F' ? ":" : "") + u.desc();
            if (m == null) problems.add("ref member used by " + u.from() + " is not in the full shell: " + id);
            else if (m.dead()) problems.add("ref member used by " + u.from() + " throws in the full shell (fill it or add a link line first): " + id);
        }
        System.out.println("[full-shells] shell refs: " + refs.size() + " checked, " + uses.size() + " member uses from compat classes");
        return problems;
    }

    /** 안쪽 틀: 실행 때 바깥과 껍데기 안쪽의 중첩 속성이 서로 맞는가. */
    private static void nested(World w, String r, Shape shell, List<String> problems) {
        int d = r.lastIndexOf('$');
        if (d <= 0) return;
        String outer = r.substring(0, d);
        Shape o = w.shape(outer);
        if (o == null) {
            problems.add("ref " + r + ": outer class " + outer + " is missing at run time");
            return;
        }
        Inner oe = o.inners().get(r);
        Inner ie = shell.inners().get(r);
        if (oe == null || !oe.equals(ie)) {
            problems.add("ref " + r + ": InnerClasses differ between the outer class " + outer + " (" + oe + ") and the full shell (" + ie + ")");
        }
        boolean member = o.nestMembers().contains(r);
        boolean hosted = outer.equals(shell.nestHost());
        if (member != hosted || shell.nestHost() != null && !hosted) {
            problems.add("ref " + r + ": nest attributes differ - outer NestMembers lists it: " + member + ", full shell NestHost: " + shell.nestHost());
        }
    }

    /** 호환 클래스(틀 빼고)의 바이트코드에서 틀 클래스를 소유로 부르는 멤버를 모은다. 메서드 명령·필드 명령·핸들 상수·부트스트랩 핸들과 인자. */
    static List<Use> uses(FullShells.Existing existing, Set<String> refs) {
        Set<Use> out = new java.util.LinkedHashSet<>();
        for (String n : new TreeSet<>(existing.dirClasses())) {
            if (refs.contains(n)) continue;
            byte[] b = existing.bytesOf(n);
            if (b == null) continue;
            new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int op, String owner, String mn, String md, boolean itf) {
                            if (refs.contains(owner)) out.add(new Use('M', owner, mn, md, n));
                        }

                        @Override
                        public void visitFieldInsn(int op, String owner, String fn, String fd) {
                            if (refs.contains(owner)) out.add(new Use('F', owner, fn, fd, n));
                        }

                        @Override
                        public void visitLdcInsn(Object value) { constant(value); }

                        @Override
                        public void visitInvokeDynamicInsn(String in, String id, Handle bsm, Object... args) {
                            constant(bsm);
                            for (Object a : args) constant(a);
                        }

                        private void constant(Object v) {
                            if (v instanceof Handle h && refs.contains(h.getOwner())) {
                                boolean field = h.getTag() <= Opcodes.H_PUTSTATIC;
                                out.add(new Use(field ? 'F' : 'M', h.getOwner(), h.getName(), h.getDesc(), n));
                            } else if (v instanceof org.objectweb.asm.ConstantDynamic cd) {
                                constant(cd.getBootstrapMethod());
                                for (int i = 0; i < cd.getBootstrapMethodArgumentCount(); i++) constant(cd.getBootstrapMethodArgument(i));
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return new ArrayList<>(out);
    }

    /** FullShells.shell 용: 이미 있는 바깥 클래스가 이 안쪽 클래스를 InnerClasses·NestMembers 에 적었는가(틀이 생성 shim 바깥 소스 안에 있을 때). */
    static boolean[] outerLists(FullShells.Existing existing, String inner) {
        int d = inner.lastIndexOf('$');
        byte[] b = d > 0 ? existing.bytesOf(inner.substring(0, d)) : null;
        if (b == null) return new boolean[]{false, false};
        Shape o = read(b);
        return new boolean[]{o.inners().containsKey(inner), o.nestMembers().contains(inner)};
    }
}
