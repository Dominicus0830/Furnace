package org.domi.furnace.compat.shimgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.domi.furnace.compat.nms.ShimCtor;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * shim 생성기(T-229)·연결 명세(T-232) 시험: 명세 + 잠금 + 손 조각 → 소스 → javac → 실제로 불러 본다.
 * 위임·소유 검사(다른 스레드의 쓰기는 던짐)·별칭·필드 값·열거형 상수 순서·안쪽 클래스·생성기 전용 생성자·구현 없는 추상 메서드 색인·조각 합치기.
 */
class ShimGeneratorTest {

    @TempDir
    Path tmp;

    static final String FIX = "org.domi.furnace.compat.shimgen.ShimGenFixtures";

    static final String SPEC = """
            import org.domi.furnace.compat.shimgen.ShimGenFixtures
            # 시험용 클래스
            class net.minecraft.zz.Thing  owner=org.domi.furnace.compat.shimgen.ShimGenFixtures$Owner
              getX -> ShimGenFixtures.x read=any
              setX -> ShimGenFixtures.setX write          # 쓰기는 소유 검사 뒤 위임
              peek -> ShimGenFixtures.peek read=owner
              alias -> this.getX read=any
              twice -> ShimGenFixtures.twice pure
              size -> @hand read=any
              reset -> @empty write                       # 빈 몸통도 쓰기면 소유 검사만 있는 몸통
              LIMIT = 7
            class net.minecraft.zz.Thing$Inner
            class net.minecraft.zz.Kind
              B
              A
            class net.minecraft.zz.Leaf
            """;

    static final String LOCK = String.join("\n",
            "#furnace-shim-lock\t1\ttest",
            "C\tnet/minecraft/zz/Kind\t16433\tjava/lang/Enum\t-\tLjava/lang/Enum<Lnet/minecraft/zz/Kind;>;\t-\tt",
            "E\tnet/minecraft/zz/Kind\t2",
            "F\tnet/minecraft/zz/Kind\tA\tLnet/minecraft/zz/Kind;\t16409\t-",
            "F\tnet/minecraft/zz/Kind\tB\tLnet/minecraft/zz/Kind;\t16409\t-",
            "C\tnet/minecraft/zz/Leaf\t33\tnet/minecraft/zz/Base\t-\t-\t-\tt",
            "C\tnet/minecraft/zz/Base\t1057\tjava/lang/Object\t-\t-\t-\tt",
            "C\tnet/minecraft/zz/Named\t1537\tjava/lang/Object\t-\t-\t-\tt",
            "C\tnet/minecraft/zz/Thing\t33\tjava/lang/Object\tnet/minecraft/zz/Named\t-\t-\tt",
            "F\tnet/minecraft/zz/Thing\tLIMIT\tI\t25\t-",
            "M\tnet/minecraft/zz/Thing\talias\t()D\t1\t-",
            "M\tnet/minecraft/zz/Thing\tgetX\t()D\t1\t-",
            "M\tnet/minecraft/zz/Thing\tpeek\t()I\t1\t-",
            "M\tnet/minecraft/zz/Thing\treset\t()V\t1\t-",
            "M\tnet/minecraft/zz/Thing\tsetX\t(D)V\t1\t-",
            "M\tnet/minecraft/zz/Thing\tsize\t()I\t1\t-",
            "M\tnet/minecraft/zz/Thing\ttwice\t(I)I\t9\t-",
            "C\tnet/minecraft/zz/Thing$Inner\t33\tjava/lang/Object\t-\t-\t9\tt",
            "") ;

    static final String FRAGMENT = """
            package net.minecraft.zz;

            import java.util.List;

            /** 손 조각: 머리는 생성기가 붙인다. */
            @SuppressWarnings("unused")
            class Thing {
                private static final List<String> HAND = List.of("hand");

                public Thing() {}

                public int size() { return HAND.size() + 2; }
            }
            """;

    @BeforeEach
    void reset() {
        ShimGenFixtures.calls.clear();
        ShimGenFixtures.owner = null;
        ShimGenFixtures.x = 1.5;
    }

    /** 명세에 Named(getName 추상) 을 더한 판 — Thing 이 getName 을 안 이어서 던지는 몸통 + 색인. */
    static String specWithNamed() {
        return SPEC + "class net.minecraft.zz.Named\n  getName\n";
    }

    static String lockWithNamed() {
        return LOCK + "M\tnet/minecraft/zz/Named\tgetName\t()Ljava/lang/String;\t1025\t-\n";
    }

    record Built(ShimGenerator.Result result, URLClassLoader loader, Path classes) {}

    Built build(String specText, String lockText, Map<String, String> fragments) throws Exception {
        Path specDir = Files.createDirectories(tmp.resolve("spec"));
        Files.writeString(specDir.resolve("t.spec"), specText, StandardCharsets.UTF_8);
        Path frag = Files.createDirectories(tmp.resolve("frag"));
        for (Map.Entry<String, String> e : fragments.entrySet()) {
            Path p = frag.resolve(e.getKey() + ".java");
            Files.createDirectories(p.getParent());
            Files.writeString(p, e.getValue(), StandardCharsets.UTF_8);
        }
        Path hand = Files.createDirectories(tmp.resolve("hand"));
        // 손 shim(src/main/java 대역): Base 는 손으로 쓴 상위 — 생성 하위가 부를 C(ShimCtor) 를 가진다
        Path base = hand.resolve("net/minecraft/zz/Base.java");
        Files.createDirectories(base.getParent());
        Files.writeString(base, """
                package net.minecraft.zz;
                public abstract class Base {
                    protected Base(org.domi.furnace.compat.nms.ShimCtor tag) {}
                }
                """, StandardCharsets.UTF_8);
        ShimSpec spec = new ShimSpec();
        spec.parse("t.spec", specText);
        ShimLock lock = ShimLock.parse(lockText);
        ShimGenerator g = new ShimGenerator(spec, lock, frag, List.of(hand), List.of());
        ShimGenerator.Result r = g.generate();
        Path src = Files.createDirectories(tmp.resolve("gen"));
        List<String> files = new ArrayList<>();
        for (Map.Entry<String, String> e : r.files().entrySet()) {
            Path p = src.resolve(e.getKey() + ".java");
            Files.createDirectories(p.getParent());
            Files.writeString(p, e.getValue(), StandardCharsets.UTF_8);
            files.add(p.toString());
        }
        files.add(base.toString());
        Path out = Files.createDirectories(tmp.resolve("classes"));
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        List<String> args = new ArrayList<>(List.of("-d", out.toString(), "-cp", System.getProperty("java.class.path"), "-encoding", "UTF-8"));
        args.addAll(files);
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        int rc = javac.run(null, null, err, args.toArray(String[]::new));
        assertEquals(0, rc, () -> "생성 소스가 컴파일되지 않는다:\n" + err + "\n" + r.files());
        URLClassLoader l = new URLClassLoader(new URL[] {out.toUri().toURL()}, getClass().getClassLoader());
        return new Built(r, l, out);
    }

    @Test
    void delegatesWithOwnerChecksAndKeepsVanillaShape() throws Exception {
        Built b = build(specWithNamed(), lockWithNamed(), Map.of("net/minecraft/zz/Thing", FRAGMENT));
        Class<?> thing = b.loader().loadClass("net.minecraft.zz.Thing");
        Object t = thing.getConstructor().newInstance();
        assertEquals(1.5, (double) thing.getMethod("getX").invoke(t));
        assertEquals(List.of("x"), ShimGenFixtures.calls, "read=any 는 소유 검사가 없다");
        ShimGenFixtures.calls.clear();
        thing.getMethod("setX", double.class).invoke(t, 4.0);
        assertEquals(List.of("write Thing#setX", "setX"), ShimGenFixtures.calls, "쓰기는 소유 검사가 먼저");
        ShimGenFixtures.calls.clear();
        assertEquals(42, (int) thing.getMethod("peek").invoke(t));
        assertEquals(List.of("read Thing#peek", "peek"), ShimGenFixtures.calls);
        assertEquals(4.0, (double) thing.getMethod("alias").invoke(t), "별칭은 같은 클래스의 메서드로");
        assertEquals(14, (int) thing.getMethod("twice", int.class).invoke(null, 7), "정적 위임은 this 없이");
        assertEquals(3, (int) thing.getMethod("size").invoke(t), "손 조각 몸통이 그대로 들어간다");
        assertEquals(7, thing.getField("LIMIT").getInt(null));
        assertTrue(Modifier.isFinal(thing.getField("LIMIT").getModifiers()) && Modifier.isStatic(thing.getField("LIMIT").getModifiers()));
        // 상속: 잠금 파일의 인터페이스
        assertEquals(List.of(b.loader().loadClass("net.minecraft.zz.Named")), Arrays.asList(thing.getInterfaces()));
        assertTrue(Modifier.isPublic(thing.getModifiers()));
    }

    @Test
    void writeFromAnotherThreadThrowsBeforeTouchingState() throws Exception {
        Built b = build(SPEC, LOCK, Map.of("net/minecraft/zz/Thing", FRAGMENT));
        Class<?> thing = b.loader().loadClass("net.minecraft.zz.Thing");
        Object t = thing.getConstructor().newInstance();
        Method setX = thing.getMethod("setX", double.class);
        Method getX = thing.getMethod("getX");
        Thread other = new Thread(() -> {});
        ShimGenFixtures.owner = other;   // 지금 스레드는 주인이 아니다
        InvocationTargetException e = assertThrows(InvocationTargetException.class, () -> setX.invoke(t, 9.0));
        assertTrue(e.getCause() instanceof IllegalStateException && e.getCause().getMessage().contains("write Thing#setX"), String.valueOf(e.getCause()));
        assertEquals(1.5, ShimGenFixtures.x, "검사가 위임보다 먼저 — 값이 안 바뀐다");
        assertFalse(ShimGenFixtures.calls.contains("setX"));
        assertEquals(1.5, (double) getX.invoke(t), "read=any 는 다른 스레드에서도 된다");
        InvocationTargetException e2 = assertThrows(InvocationTargetException.class, () -> thing.getMethod("reset").invoke(t));
        assertTrue(e2.getCause().getMessage().contains("write Thing#reset"), "@empty write 도 소유 검사를 한다: " + e2.getCause());
        // 주인 스레드에서는 된다
        AtomicReference<Throwable> fail = new AtomicReference<>();
        Thread owner = new Thread(() -> {
            try { setX.invoke(t, 9.0); } catch (Throwable x) { fail.set(x); }
        });
        ShimGenFixtures.owner = owner;
        owner.start();
        owner.join();
        assertEquals(null, fail.get());
        assertEquals(9.0, ShimGenFixtures.x);
    }

    @Test
    void enumConstantsFollowSpecOrderAndNestedClassesGetGeneratorConstructors() throws Exception {
        Built b = build(SPEC, LOCK, Map.of("net/minecraft/zz/Thing", FRAGMENT));
        Class<?> kind = b.loader().loadClass("net.minecraft.zz.Kind");
        assertTrue(kind.isEnum());
        assertEquals("[B, A]", Arrays.toString(kind.getEnumConstants()), "명세 순서가 서수 순서(서명표는 이름 순이라 명세가 정한다)");
        Class<?> inner = b.loader().loadClass("net.minecraft.zz.Thing$Inner");
        assertTrue(Modifier.isStatic(inner.getModifiers()) && Modifier.isPublic(inner.getModifiers()));
        Constructor<?>[] cs = inner.getDeclaredConstructors();
        assertEquals(1, cs.length, "기본 생성자(바닐라에 없는 public Inner())가 생기지 않는다");
        assertEquals(List.of(ShimCtor.class), List.of(cs[0].getParameterTypes()));
        assertTrue(Modifier.isProtected(cs[0].getModifiers()));
        // 손 shim 상위(Base) 를 둔 생성 클래스는 그 C(ShimCtor) 를 부른다
        Class<?> leaf = b.loader().loadClass("net.minecraft.zz.Leaf");
        assertEquals("net.minecraft.zz.Base", leaf.getSuperclass().getName());
        assertEquals(1, leaf.getDeclaredConstructors().length);
        assertFalse(b.result().files().containsKey("net/minecraft/zz/Base"), "손 shim 은 만들지 않는다");
        assertTrue(b.result().files().containsKey("net/minecraft/zz/Named"), "명세에 없어도 상위 사슬(인터페이스)은 선언만 만든다");
    }

    @Test
    void unimplementedAbstractMethodGetsThrowingBodyAndIndexLine() throws Exception {
        Built b = build(specWithNamed(), lockWithNamed(), Map.of("net/minecraft/zz/Thing", FRAGMENT));
        assertEquals(List.of("net/minecraft/zz/Thing getName ()Ljava/lang/String;"), b.result().unimplemented());
        Class<?> thing = b.loader().loadClass("net.minecraft.zz.Thing");
        Object t = thing.getConstructor().newInstance();
        Class<?> named = b.loader().loadClass("net.minecraft.zz.Named");
        InvocationTargetException e = assertThrows(InvocationTargetException.class, () -> named.getMethod("getName").invoke(t));
        assertTrue(e.getCause() instanceof UnsupportedNmsCall, String.valueOf(e.getCause()));
        assertTrue(b.result().generated().contains("F net/minecraft/zz/Thing"));
        assertTrue(b.result().generated().contains("G net/minecraft/zz/Named"));
    }

    @Test
    void generatedBodiesUseNoReflectionOrInvokedynamic() throws Exception {
        Built b = build(specWithNamed(), lockWithNamed(), Map.of("net/minecraft/zz/Thing", FRAGMENT));
        List<String> bad = new ArrayList<>();
        try (Stream<Path> s = Files.walk(b.classes())) {
            for (Path p : s.filter(x -> x.toString().endsWith(".class")).toList()) {
                String cls = b.classes().relativize(p).toString().replace(File.separatorChar, '/');
                new ClassReader(Files.readAllBytes(p)).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                        boolean generated = !(cls.equals("net/minecraft/zz/Thing.class") && (name.equals("size") || name.equals("<clinit>") || name.equals("<init>")));
                        if (!generated) return null;
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitInvokeDynamicInsn(String n, String d, Handle bsm, Object... args) { bad.add(cls + "#" + name + " invokedynamic"); }

                            @Override
                            public void visitMethodInsn(int op, String owner, String n, String d, boolean itf) {
                                if (owner.startsWith("java/lang/reflect/") || owner.equals("java/lang/invoke/MethodHandles") || owner.equals("java/lang/Class") && n.equals("forName")) {
                                    bad.add(cls + "#" + name + " " + owner + "." + n);
                                }
                            }
                        };
                    }
                }, 0);
            }
        }
        assertEquals(List.of(), bad);
    }

    @Test
    void specMistakesAreReportedWithFileAndLine() {
        ShimSpec s = new ShimSpec();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> s.parse("a.spec", "class net.minecraft.A\n  getX -> org.example.Foo.x\n"));
        assertTrue(e.getMessage().startsWith("a.spec:2: "), e.getMessage());
        assertTrue(e.getMessage().contains("check kind"), e.getMessage());
        IllegalArgumentException imp = assertThrows(IllegalArgumentException.class, () -> new ShimSpec().parse("f.spec", "class net.minecraft.A\n\n  getX -> Foo.x pure\n"));
        assertTrue(imp.getMessage().startsWith("f.spec:3: name not imported: Foo"), imp.getMessage());
        new ShimSpec().parse("b.spec", "class net.minecraft.A\n  getX -> @hand write\n  tick -> @empty pure\n  <init> -> @hand\n");
        IllegalArgumentException hand = assertThrows(IllegalArgumentException.class, () -> new ShimSpec().parse("b.spec", "class net.minecraft.A\n  getX -> @hand\n"));
        assertTrue(hand.getMessage().contains("hand bodies too"), "손 몸통도 검사 종류를 적는다: " + hand.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new ShimSpec().parse("b.spec", "class net.minecraft.A\n  tick -> @empty\n"));
        assertThrows(IllegalArgumentException.class, () -> new ShimSpec().parse("b.spec", "class net.minecraft.A\n  <init> -> @empty write\n"));
        assertThrows(IllegalArgumentException.class, () -> new ShimSpec().parse("c.spec", "class org.example.A\n"));
        assertThrows(IllegalArgumentException.class, () -> new ShimSpec().parse("d.spec", "class net.minecraft.A\n  x\n  x\n"));
        assertThrows(IllegalArgumentException.class, () -> new ShimSpec().parse("e.spec", "  x\n"));
    }

    @Test
    void writeLinkWithoutOwnerRuleAndStaleLockFailTheBuild() {
        ShimSpec s = new ShimSpec();
        s.parse("t.spec", SPEC.replace("  owner=org.domi.furnace.compat.shimgen.ShimGenFixtures$Owner", ""));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ShimGenerator(s, ShimLock.parse(LOCK), tmp, List.of(), List.of()).generate());
        assertTrue(e.getMessage().contains("owner=<owner rule class>"), e.getMessage());
        // 명세에서 멤버를 빼면 잠금이 낡았다고 실패한다
        ShimSpec s2 = new ShimSpec();
        s2.parse("t.spec", SPEC.replace("  LIMIT = 7\n", ""));
        IllegalStateException e2 = assertThrows(IllegalStateException.class,
                () -> new ShimGenerator(s2, ShimLock.parse(LOCK), tmp, List.of(), List.of()).generate());
        assertTrue(e2.getMessage().contains("stale shim lock (member not in the spec): net/minecraft/zz/Thing#LIMITI"), e2.getMessage());
    }

    @Test
    void handBodyWithAnOwnerCheckKindMustCallTheCheckFirst() throws Exception {
        // 실제 빌드의 ShimSpecBuildTest 와 같은 함수로: @hand write 인데 몸통이 검사를 안 부르면 문제, 첫 줄에 부르면 통과
        String spec = SPEC.replace("size -> @hand read=any", "size -> @hand write");
        ShimSpec parsed = new ShimSpec();
        parsed.parse("t.spec", spec);
        ShimSpec.Cls sc = parsed.classes.get("net.minecraft.zz.Thing");
        ShimSpec.Member sm = sc.members().stream().filter(m -> m.name().equals("size")).findFirst().orElseThrow();
        Built bad = build(spec, LOCK, Map.of("net/minecraft/zz/Thing", FRAGMENT));
        ShimSpecBuildTest.Compiled got = ShimSpecBuildTest.read("net/minecraft/zz/Thing", Files.readAllBytes(bad.classes().resolve("net/minecraft/zz/Thing.class")));
        String p = ShimSpecBuildTest.ownerCheckProblem(got, "net/minecraft/zz/Thing", "M size ()I", sc, sm);
        assertTrue(p != null && p.contains("ShimGenFixtures$Owner.write"), String.valueOf(p));
        String checked = FRAGMENT.replace("public int size() { return HAND.size() + 2; }",
                "public int size() { org.domi.furnace.compat.shimgen.ShimGenFixtures.Owner.write(this, \"Thing#size\"); return HAND.size() + 2; }");
        Built good = build(spec, LOCK, Map.of("net/minecraft/zz/Thing", checked));
        ShimSpecBuildTest.Compiled ok = ShimSpecBuildTest.read("net/minecraft/zz/Thing", Files.readAllBytes(good.classes().resolve("net/minecraft/zz/Thing.class")));
        assertEquals(null, ShimSpecBuildTest.ownerCheckProblem(ok, "net/minecraft/zz/Thing", "M size ()I", sc, sm));
    }

    @Test
    void ownerCheckOnAStaticMethodIsRefused() {
        ShimSpec s = new ShimSpec();
        s.parse("t.spec", SPEC.replace("twice -> ShimGenFixtures.twice pure", "twice -> ShimGenFixtures.twice write"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ShimGenerator(s, ShimLock.parse(LOCK), tmp, List.of(), List.of()).generate());
        assertTrue(e.getMessage().contains("t.spec:8: no owner check is generated for static methods"), e.getMessage());
    }

    @Test
    void enumWithMissingConstantsIsRefused() {
        ShimSpec s = new ShimSpec();
        s.parse("t.spec", SPEC.replace("  B\n  A\n", "  B\n"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ShimGenerator(s, ShimLock.parse(LOCK.replace("F\tnet/minecraft/zz/Kind\tA\tLnet/minecraft/zz/Kind;\t16409\t-\n", "")), tmp, List.of(), List.of()).generate());
        assertTrue(e.getMessage().contains("enum net/minecraft/zz/Kind lists 1 of its 2 vanilla constants"), e.getMessage());
        // 잠금에 상수 개수가 없으면(옛 잠금) 다시 쓰라고 한다
        ShimSpec s2 = new ShimSpec();
        s2.parse("t.spec", SPEC);
        IllegalStateException e2 = assertThrows(IllegalStateException.class,
                () -> new ShimGenerator(s2, ShimLock.parse(LOCK.replace("E\tnet/minecraft/zz/Kind\t2\n", "")), tmp, List.of(), List.of()).generate());
        assertTrue(e2.getMessage().contains("no enum constant count for net/minecraft/zz/Kind"), e2.getMessage());
    }

    @Test
    void enumFragmentGetsItsConstantsFirst() throws Exception {
        String spec = "class net.minecraft.zz.Mode\n  Y\n  X\n  label -> @hand pure\n";
        String lock = String.join("\n", "#furnace-shim-lock\t1\ttest",
                "C\tnet/minecraft/zz/Mode\t16433\tjava/lang/Enum\t-\tLjava/lang/Enum<Lnet/minecraft/zz/Mode;>;\t-\tt",
                "E\tnet/minecraft/zz/Mode\t2",
                "F\tnet/minecraft/zz/Mode\tX\tLnet/minecraft/zz/Mode;\t16409\t-",
                "F\tnet/minecraft/zz/Mode\tY\tLnet/minecraft/zz/Mode;\t16409\t-",
                "M\tnet/minecraft/zz/Mode\tlabel\t()Ljava/lang/String;\t1\t-", "");
        Built b = build(spec, lock, Map.of("net/minecraft/zz/Mode", "package net.minecraft.zz;\n\nenum Mode {\n    public String label() { return name().toLowerCase(); }\n}\n"));
        Class<?> mode = b.loader().loadClass("net.minecraft.zz.Mode");
        assertEquals("[Y, X]", Arrays.toString(mode.getEnumConstants()));
        assertEquals("y", mode.getMethod("label").invoke(mode.getEnumConstants()[0]));
    }

    @Test
    void abstractMethodsCannotGetABody() {
        // 인터페이스의 추상 메서드에 위임을 적으면 default 없는 몸통(문법 오류)이 아니라 이름 있는 생성기 오류
        ShimSpec s = new ShimSpec();
        s.parse("t.spec", SPEC + "class net.minecraft.zz.Named\n  getName -> ShimGenFixtures.peek read=any\n");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ShimGenerator(s, ShimLock.parse(lockWithNamed()), tmp, List.of(), List.of()).generate());
        assertTrue(e.getMessage().contains("net/minecraft/zz/Named#getName is abstract in vanilla (an interface method without a default body)"), e.getMessage());
    }

    @Test
    void handBodyNeedsAFragmentAndAGenericStubIsRefused() {
        ShimSpec s = new ShimSpec();
        s.parse("t.spec", SPEC);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ShimGenerator(s, ShimLock.parse(LOCK), tmp.resolve("nofrag"), List.of(), List.of()).generate());
        assertTrue(e.getMessage().contains("@hand/@fast bodies go in the fragment"), e.getMessage());
        // 형식 변수를 쓰는 상위 추상 메서드는 던지는 몸통을 만들 수 없다 — 명세에 이으라고 실패한다
        ShimSpec g = new ShimSpec();
        g.parse("g.spec", "class net.minecraft.zz.Box\n  get\nclass net.minecraft.zz.IntBox\n");
        String lock = String.join("\n", "#furnace-shim-lock\t1\ttest",
                "C\tnet/minecraft/zz/Box\t1057\tjava/lang/Object\t-\t<T:Ljava/lang/Object;>Ljava/lang/Object;\t-\tt",
                "M\tnet/minecraft/zz/Box\tget\t()Ljava/lang/Object;\t1025\t()TT;",
                "C\tnet/minecraft/zz/IntBox\t33\tnet/minecraft/zz/Box\t-\tLnet/minecraft/zz/Box<Ljava/lang/Integer;>;\t-\tt", "");
        IllegalStateException e2 = assertThrows(IllegalStateException.class,
                () -> new ShimGenerator(g, ShimLock.parse(lock), tmp, List.of(), List.of()).generate());
        assertTrue(e2.getMessage().contains("uses type variables"), e2.getMessage());
    }

    @Test
    void genericHeadersAreWrittenFromSignatures() throws Exception {
        ShimSpec g = new ShimSpec();
        g.parse("g.spec", "class net.minecraft.zz.Box\n  get\nclass net.minecraft.zz.IntBox\n  get -> ShimGenFixtures.boxed pure\n"
                .replace("ShimGenFixtures", FIX));
        String lock = String.join("\n", "#furnace-shim-lock\t1\ttest",
                "C\tnet/minecraft/zz/Box\t1057\tjava/lang/Object\t-\t<T:Ljava/lang/Object;>Ljava/lang/Object;\t-\tt",
                "M\tnet/minecraft/zz/Box\tget\t()Ljava/lang/Object;\t1025\t()TT;",
                "C\tnet/minecraft/zz/IntBox\t33\tnet/minecraft/zz/Box\t-\tLnet/minecraft/zz/Box<Ljava/lang/Integer;>;\t-\tt",
                "M\tnet/minecraft/zz/IntBox\tget\t()Ljava/lang/Integer;\t1\t-", "");
        String box = new ShimGenerator(g, ShimLock.parse(lock), tmp, List.of(), List.of()).generate().files().get("net/minecraft/zz/Box");
        assertTrue(box.contains("public abstract class Box<T> {"), box);
        assertTrue(box.contains("public abstract T get();"), box);
        assertTrue(box.contains("protected Box(org.domi.furnace.compat.nms.ShimCtor tag) { }"), box);
    }

    /** T-231: @each — 인터페이스의 추상 메서드는 추상 그대로, 그것을 물려받는 구체 생성 클래스마다(손 상위 아래여도) 위임 몸통. 추상 중간 클래스는 몸통 없음. */
    @Test
    void eachLinksAnAbstractMethodIntoEveryConcreteSubclass() throws Exception {
        String spec = "import " + FIX + "\n"
                + "class net.minecraft.zz.Named\n  getName -> @each(ShimGenFixtures.name) read=any\n"
                + "class net.minecraft.zz.Low\nclass net.minecraft.zz.Tag\n";
        String lock = String.join("\n", "#furnace-shim-lock\t1\ttest",
                "C\tnet/minecraft/zz/Base\t1057\tjava/lang/Object\t-\t-\t-\tt",
                "C\tnet/minecraft/zz/Named\t1537\tjava/lang/Object\t-\t-\t-\tt",
                "M\tnet/minecraft/zz/Named\tgetName\t()Ljava/lang/String;\t1025\t-",
                "C\tnet/minecraft/zz/Mid\t1057\tnet/minecraft/zz/Base\tnet/minecraft/zz/Named\t-\t-\tt",
                "C\tnet/minecraft/zz/Low\t33\tnet/minecraft/zz/Mid\t-\t-\t-\tt",
                "C\tnet/minecraft/zz/Tag\t33\tnet/minecraft/zz/Base\tnet/minecraft/zz/Named\t-\t-\tt", "");
        Built b = build(spec, lock, Map.of());
        assertEquals(List.of(), b.result().unimplemented(), "@each 로 이은 메서드는 '없음' 색인에 들지 않는다");
        String mid = b.result().files().get("net/minecraft/zz/Mid");
        assertFalse(mid.contains("getName"), "추상 중간 클래스는 몸통을 받지 않는다:\n" + mid);
        Class<?> named = b.loader().loadClass("net.minecraft.zz.Named");
        assertTrue(Modifier.isAbstract(named.getMethod("getName").getModifiers()), "인터페이스 메서드는 추상 그대로");
        for (String c : List.of("Low", "Tag")) {
            Class<?> k = b.loader().loadClass("net.minecraft.zz." + c);
            Constructor<?> ctor = k.getDeclaredConstructor(ShimCtor.class);
            ctor.setAccessible(true);
            Object o = ctor.newInstance(ShimCtor.TAG);
            assertEquals("named:" + c, named.getMethod("getName").invoke(o));
            assertEquals(k, k.getDeclaredMethod("getName").getDeclaringClass(), c + " 가 몸통을 가진다");
        }
    }

    @Test
    void eachIsOnlyForAbstractMethodsAndKindTablesAddDeclarations() throws Exception {
        ShimSpec s = new ShimSpec();
        s.parse("t.spec", SPEC.replace("twice -> ShimGenFixtures.twice pure", "twice -> @each(ShimGenFixtures.twice) pure"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ShimGenerator(s, ShimLock.parse(LOCK), tmp, List.of(), List.of()).generate());
        assertTrue(e.getMessage().contains("@each is for methods that are abstract in vanilla"), e.getMessage());
        // 종류 표: 명세에 없는 클래스는 멤버 없는 class 줄이 되고, 이미 있는 클래스는 명세 줄을 그대로 둔다. 표는 판마다 하나.
        Path dir = Files.createDirectories(tmp.resolve("kinds"));
        Files.writeString(dir.resolve("a.spec"), "class net.minecraft.zz.Leaf\n  <init> -> @empty\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("kind-classes-test.txt"), "# c\nB minecraft:a net.minecraft.zz.Leaf\nI minecraft:b net.minecraft.zz.Other\n", StandardCharsets.UTF_8);
        ShimSpec k = ShimSpec.readDir(dir);
        assertEquals(List.of("net.minecraft.zz.Leaf", "net.minecraft.zz.Other"), List.copyOf(k.classes.keySet()));
        assertEquals(1, k.classes.get("net.minecraft.zz.Leaf").members().size(), "명세 줄이 이긴다");
        assertEquals(2, k.kinds.size());
        Files.writeString(dir.resolve("kind-classes-old.txt"), "B minecraft:a net.minecraft.zz.Leaf\n", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> ShimSpec.readDir(dir));
        Files.delete(dir.resolve("kind-classes-old.txt"));
        Files.writeString(dir.resolve("kind-classes-test.txt"), "X minecraft:a net.minecraft.zz.Leaf\n", StandardCharsets.UTF_8);
        IllegalArgumentException bad = assertThrows(IllegalArgumentException.class, () -> ShimSpec.readDir(dir));
        assertTrue(bad.getMessage().contains("kind-classes-test.txt:1"), bad.getMessage());
    }
}
