package org.domi.furnace.compat.shimgen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;
import org.domi.furnace.tools.extract.SigTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * T-261: 이미 있는 손 shim 에 빠진 멤버를 껍데기 멤버로 합치기. 작은 가짜 서명표와 ASM 으로 만든 손 shim → 합친 바이트 → 실제 JVM 에 올려 본다.
 * 원래 멤버는 그대로 돌고, 빠진 메서드·생성자·정적 필드는 표지 달린 던지는 몸통·선언, 상수는 값, 인스턴스 필드는 색인만,
 * 상위에서 이미 찾아지는 것·손 하위가 다른 인터페이스 default 로 받는 것은 넣지 않는다(지금 잘 도는 상속을 가리지 않는다).
 */
class ShellMergeTest {

    @TempDir
    Path tmp;

    static final String PAPER = String.join("\n",
            "#furnace-sig-table\t2\tpaper-server-test",
            "C\t33\tnet/minecraft/zz/Base\tjava/lang/Object\t-\t-\t-",
            "M\t1\tnet/minecraft/zz/Base\tinh\t()I\t-",
            "C\t1057\tnet/minecraft/zz/Hand\tnet/minecraft/zz/Base\t-\t-\t-",
            "F\t25\tnet/minecraft/zz/Hand\tLIMIT\tI\t-",
            "F\t25\tnet/minecraft/zz/Hand\tINSTANCE\tLnet/minecraft/zz/Hand;\t-",
            "F\t2\tnet/minecraft/zz/Hand\tcount\tI\t-",
            "M\t1\tnet/minecraft/zz/Hand\t<init>\t()V\t-",
            "M\t1\tnet/minecraft/zz/Hand\t<init>\t(IJ)V\t-",
            "M\t1\tnet/minecraft/zz/Hand\tkept\t()I\t-",
            "M\t1\tnet/minecraft/zz/Hand\tadded\t(Ljava/lang/String;J)I\t-",
            "M\t1025\tnet/minecraft/zz/Hand\tabs\t()V\t-",
            "M\t9\tnet/minecraft/zz/Hand\tmake\t(D)Lnet/minecraft/zz/Hand;\t-",
            "M\t17\tnet/minecraft/zz/Hand\tfin\t()I\t-",
            "M\t1\tnet/minecraft/zz/Hand\tinh\t()I\t-",
            "M\t1\tnet/minecraft/zz/Hand\tdm\t()I\t-",
            "M\t4097\tnet/minecraft/zz/Hand\tlambda$x$0\t()V\t-",
            "K\tnet/minecraft/zz/Hand\tLIMIT\tI\t7",
            "C\t1537\tnet/minecraft/zz/HItf\tjava/lang/Object\t-\t-\t-",
            "M\t1025\tnet/minecraft/zz/HItf\tneed\t()I\t-",
            "M\t9\tnet/minecraft/zz/HItf\tof\t()Lnet/minecraft/zz/HItf;\t-",
            "C\t33\tnet/minecraft/zz/Complete\tjava/lang/Object\t-\t-\t-",
            "M\t1\tnet/minecraft/zz/Complete\t<init>\t()V\t-",
            "");

    static final String EMPTY = "#furnace-sig-table\t2\tempty\n";

    /** 손 shim 들: Base(inh), Hand extends Base(kept), Itf(default dm), Sub extends Hand implements Itf(fin 을 덮음), HItf(빈 인터페이스), Complete. */
    private Path hand() throws Exception {
        Path dir = tmp.resolve("compat");
        write(dir, "net/minecraft/zz/Base", Opcodes.ACC_PUBLIC, "java/lang/Object", null, cw -> {
            ctor(cw, "java/lang/Object");
            ret(cw, Opcodes.ACC_PUBLIC, "inh", 11);
        });
        write(dir, "net/minecraft/zz/Hand", Opcodes.ACC_PUBLIC, "net/minecraft/zz/Base", null, cw -> {
            ctor(cw, "net/minecraft/zz/Base");
            ret(cw, Opcodes.ACC_PUBLIC, "kept", 5);
        });
        write(dir, "net/minecraft/zz/Itf", Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, "java/lang/Object", null,
                cw -> ret(cw, Opcodes.ACC_PUBLIC, "dm", 42));
        write(dir, "net/minecraft/zz/Sub", Opcodes.ACC_PUBLIC, "net/minecraft/zz/Hand", new String[]{"net/minecraft/zz/Itf"}, cw -> {
            ctor(cw, "net/minecraft/zz/Hand");
            ret(cw, Opcodes.ACC_PUBLIC, "fin", 9);
        });
        write(dir, "net/minecraft/zz/HItf", Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, "java/lang/Object", null, cw -> {});
        write(dir, "net/minecraft/zz/Complete", Opcodes.ACC_PUBLIC, "java/lang/Object", null, cw -> ctor(cw, "java/lang/Object"));
        return dir;
    }

    private record Run(FullShells g, ShellMerge merge, Map<String, byte[]> merged, Path compat) {}

    private Run run() throws Exception {
        Path compat = hand();
        FullShells g = new FullShells(table(PAPER), table(EMPTY), table(EMPTY), new FullShells.Existing(List.of(compat)));
        g.generate();
        ShellMerge m = new ShellMerge(g, FullShells.Existing.classesIn(compat));
        return new Run(g, m, m.merge(), compat);
    }

    @Test
    void mergesMissingMembersAndKeepsTheRest() throws Exception {
        Run r = run();
        assertEquals(Set.of("net/minecraft/zz/Hand", "net/minecraft/zz/HItf"), r.merged().keySet(), "Base·Complete 는 빠진 것이 없고 Itf·Sub 는 표에 없다");
        assertEquals(2, r.merge().completeClasses);
        // 상위에서 찾아지는 inh, 손 하위 Sub 가 Itf 의 default 로 받는 dm, 합성 람다는 넣지 않는다
        assertTrue(r.merge().notAddedList.get("method already resolves through a supertype").contains("net/minecraft/zz/Hand#inh()I"));
        assertTrue(r.merge().notAddedList.get("a hand subtype inherits it as a default from another interface").contains("net/minecraft/zz/Hand#dm()I"));
        assertTrue(r.merge().notAddedList.get("synthetic (compiler-made: lambdas, bridges)").contains("net/minecraft/zz/Hand#lambda$x$0()V"));
        // 인스턴스 필드는 색인만
        assertEquals(List.of("net/minecraft/zz/Hand\tcount\tI\t2"), r.merge().instanceFields);

        // 원래 메서드 바이트는 그대로(복사) — 원래 클래스의 메서드가 합친 클래스에 같은 순서로 먼저 있다
        byte[] original = Files.readAllBytes(r.compat().resolve("net/minecraft/zz/Hand.class"));
        assertEquals(methods(original), methods(r.merged().get("net/minecraft/zz/Hand")).subList(0, methods(original).size()));

        // 같은 입력이면 같은 바이트
        Run again = run();
        for (String k : r.merged().keySet()) assertArrayEquals(r.merged().get(k), again.merged().get(k), k);
    }

    @Test
    void mergedClassesLoadAndBehave() throws Exception {
        Run r = run();
        Path out = tmp.resolve("shells");
        for (Map.Entry<String, byte[]> e : r.merged().entrySet()) {
            Path p = out.resolve(e.getKey() + ".class");
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
        // 껍데기 폴더가 앞 — 합친 클래스가 원래 클래스를 이긴다(서버의 jar 순서와 같다)
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL(), r.compat().toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> hand = Class.forName("net.minecraft.zz.Hand", true, cl);
            Object h = hand.getConstructor().newInstance();
            assertEquals(5, hand.getMethod("kept").invoke(h), "원래 멤버는 그대로 돈다");
            assertEquals(11, hand.getMethod("inh").invoke(h), "상위의 inh 를 가리지 않는다");
            assertEquals(7, hand.getField("LIMIT").getInt(null), "상수는 값 그대로");
            assertFalse(hasField(hand, "count"), "인스턴스 필드는 넣지 않는다(색인만)");

            InvocationTargetException ite = assertThrows(InvocationTargetException.class, () -> hand.getMethod("added", String.class, long.class).invoke(h, "a", 1L));
            assertInstanceOf(UnsupportedNmsCall.class, ite.getCause());
            assertTrue(ite.getCause().getMessage().contains("net.minecraft.zz.Hand#added(Ljava/lang/String;J)I"), ite.getCause().getMessage());
            ite = assertThrows(InvocationTargetException.class, () -> hand.getMethod("make", double.class).invoke(null, 1.0));
            assertInstanceOf(UnsupportedNmsCall.class, ite.getCause());
            ite = assertThrows(InvocationTargetException.class, () -> hand.getConstructor(int.class, long.class).newInstance(1, 2L));
            assertInstanceOf(UnsupportedNmsCall.class, ite.getCause());
            // 추상은 떼어 던지는 몸통(손 하위가 구현하지 않았을 수 있다)
            assertFalse(Modifier.isAbstract(hand.getMethod("abs").getModifiers()));
            ite = assertThrows(InvocationTargetException.class, () -> hand.getMethod("abs").invoke(h));
            assertInstanceOf(UnsupportedNmsCall.class, ite.getCause());

            // 손 하위 Sub 는 그대로 올라가고(final 이 아니게 넣었으므로 fin 덮기가 된다) Itf 의 default dm 을 계속 받는다
            Class<?> sub = Class.forName("net.minecraft.zz.Sub", true, cl);
            assertFalse(Modifier.isFinal(hand.getMethod("fin").getModifiers()));
            Object s = sub.getConstructor().newInstance();
            assertEquals(9, sub.getMethod("fin").invoke(s));
            assertEquals(42, sub.getMethod("dm").invoke(s));
            assertTrue(r.g().deviations.get("merged method made non-final: a hand subtype overrides it").contains("net/minecraft/zz/Hand#fin()I"));

            // 인터페이스: 빠진 추상 메서드는 default 던지는 몸통, static 도 던진다
            Class<?> hitf = Class.forName("net.minecraft.zz.HItf", true, cl);
            assertTrue(hitf.getMethod("need").isDefault());
            ite = assertThrows(InvocationTargetException.class, () -> hitf.getMethod("of").invoke(null));
            assertInstanceOf(UnsupportedNmsCall.class, ite.getCause());
        }
    }

    @Test
    void addedMembersCarryTheMarkerOriginalsDoNot() throws Exception {
        Run r = run();
        java.util.List<String> marked = new java.util.ArrayList<>();
        java.util.List<String> unmarked = new java.util.ArrayList<>();
        new ClassReader(r.merged().get("net/minecraft/zz/Hand")).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                String k = name;
                unmarked.add(k);
                return new org.objectweb.asm.FieldVisitor(Opcodes.ASM9) {
                    @Override
                    public org.objectweb.asm.AnnotationVisitor visitAnnotation(String d, boolean visible) {
                        if (d.equals(FullShells.SHELL_MEMBER)) {
                            unmarked.remove(k);
                            marked.add(k);
                        }
                        return null;
                    }
                };
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                String k = name + descriptor;
                unmarked.add(k);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public org.objectweb.asm.AnnotationVisitor visitAnnotation(String d, boolean visible) {
                        if (d.equals(FullShells.SHELL_MEMBER)) {
                            unmarked.remove(k);
                            marked.add(k);
                        }
                        return null;
                    }
                };
            }
        }, 0);
        assertEquals(Set.of("INSTANCE", "<init>(IJ)V", "added(Ljava/lang/String;J)I", "abs()V", "make(D)Lnet/minecraft/zz/Hand;", "fin()I"), Set.copyOf(marked));
        assertEquals(Set.of("LIMIT", "<init>()V", "kept()I"), Set.copyOf(unmarked), "원래 멤버와 값 있는 상수에는 표지가 없다");
    }

    /**
     * T-264 P3 핸들 다리: CraftBukkit 클래스의 "상위 접근자 + 반환형 좁힘"은 던지는 몸통 대신 다리(@CraftBridge)가 되고, Paper 상위 형식의 넓은
     * 서술자는 javac 처럼 컴파일러 다리가 된다. 실제 JVM 에 올려 부른다(검증기·스택 프레임까지 본다).
     */
    @Test
    void craftNarrowingAccessorsBecomeBridges() throws Exception {
        String paper = String.join("\n",
                "#furnace-sig-table\t2\tpaper-server-test",
                "C\t33\tnet/minecraft/zz/Ent\tjava/lang/Object\t-\t-\t-",
                "C\t1537\tnet/minecraft/zz/Enemy\tjava/lang/Object\t-\t-\t-",
                "C\t33\tnet/minecraft/zz/Mob\tnet/minecraft/zz/Ent\tnet/minecraft/zz/Enemy\t-\t-",
                "C\t33\tnet/minecraft/zz/Zom\tnet/minecraft/zz/Mob\t-\t-\t-",
                "C\t1537\torg/bukkit/craftbukkit/zz/CraftEnemy\tjava/lang/Object\t-\t-\t-",
                "M\t1025\torg/bukkit/craftbukkit/zz/CraftEnemy\tgetHandle\t()Lnet/minecraft/zz/Enemy;\t-",
                "C\t33\torg/bukkit/craftbukkit/zz/CraftRoot\tjava/lang/Object\t-\t-\t-",
                "M\t1\torg/bukkit/craftbukkit/zz/CraftRoot\tgetHandle\t()Lnet/minecraft/zz/Ent;\t-",
                "C\t33\torg/bukkit/craftbukkit/zz/CraftMid\torg/bukkit/craftbukkit/zz/CraftRoot\torg/bukkit/craftbukkit/zz/CraftEnemy\t-\t-",
                "M\t1\torg/bukkit/craftbukkit/zz/CraftMid\tgetHandle\t()Lnet/minecraft/zz/Mob;\t-",
                "C\t33\torg/bukkit/craftbukkit/zz/CraftLeaf\torg/bukkit/craftbukkit/zz/CraftMid\t-\t-\t-",
                "M\t1\torg/bukkit/craftbukkit/zz/CraftLeaf\tgetHandle\t()Lnet/minecraft/zz/Zom;\t-",
                "M\t1\torg/bukkit/craftbukkit/zz/CraftLeaf\tother\t(I)Lnet/minecraft/zz/Zom;\t-",
                "");
        Path compat = tmp.resolve("craft");
        write(compat, "net/minecraft/zz/Ent", Opcodes.ACC_PUBLIC, "java/lang/Object", null, cw -> ctor(cw, "java/lang/Object"));
        write(compat, "net/minecraft/zz/Enemy", Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, "java/lang/Object", null, cw -> {});
        write(compat, "net/minecraft/zz/Mob", Opcodes.ACC_PUBLIC, "net/minecraft/zz/Ent", new String[]{"net/minecraft/zz/Enemy"}, cw -> ctor(cw, "net/minecraft/zz/Ent"));
        write(compat, "net/minecraft/zz/Zom", Opcodes.ACC_PUBLIC, "net/minecraft/zz/Mob", null, cw -> ctor(cw, "net/minecraft/zz/Mob"));
        write(compat, "org/bukkit/craftbukkit/zz/CraftRoot", Opcodes.ACC_PUBLIC, "java/lang/Object", null, cw -> {
            ctor(cw, "java/lang/Object");
            cw.visitField(Opcodes.ACC_PUBLIC, "h", "Ljava/lang/Object;", null, null).visitEnd();
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getHandle", "()Lnet/minecraft/zz/Ent;", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, "org/bukkit/craftbukkit/zz/CraftRoot", "h", "Ljava/lang/Object;");
            mv.visitTypeInsn(Opcodes.CHECKCAST, "net/minecraft/zz/Ent");
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        });
        write(compat, "org/bukkit/craftbukkit/zz/CraftMid", Opcodes.ACC_PUBLIC, "org/bukkit/craftbukkit/zz/CraftRoot", null, cw -> ctor(cw, "org/bukkit/craftbukkit/zz/CraftRoot"));
        write(compat, "org/bukkit/craftbukkit/zz/CraftLeaf", Opcodes.ACC_PUBLIC, "org/bukkit/craftbukkit/zz/CraftMid", null, cw -> ctor(cw, "org/bukkit/craftbukkit/zz/CraftMid"));

        FullShells g = new FullShells(table(paper), table(EMPTY), table(EMPTY), new FullShells.Existing(List.of(compat)));
        g.generate();
        ShellMerge m = new ShellMerge(g, FullShells.Existing.classesIn(compat));
        Map<String, byte[]> merged = m.merge();
        assertEquals(Set.of("org/bukkit/craftbukkit/zz/CraftMid#getHandle()Lnet/minecraft/zz/Mob;",
                "org/bukkit/craftbukkit/zz/CraftLeaf#getHandle()Lnet/minecraft/zz/Zom;"), m.bridges, "인자가 다른 other(I) 는 좁힘이 아니라 껍데기");
        // javac 와 같다: CraftMid 는 Paper 인터페이스 CraftEnemy 의 넓은 서술자(손 CraftEnemy 가 없어도 Paper 계층으로 계산), CraftLeaf 는 CraftMid 의 두 서술자.
        // ()Ent 는 손 CraftRoot 의 진짜 메서드가 이미 받으므로 넣지 않는다
        assertEquals(Set.of("org/bukkit/craftbukkit/zz/CraftMid#getHandle()Lnet/minecraft/zz/Enemy;",
                "org/bukkit/craftbukkit/zz/CraftLeaf#getHandle()Lnet/minecraft/zz/Enemy;",
                "org/bukkit/craftbukkit/zz/CraftLeaf#getHandle()Lnet/minecraft/zz/Mob;"), m.compilerBridges);
        String bridge = "Lorg/domi/furnace/compat/nms/CraftBridge;";
        assertTrue(annotated(merged.get("org/bukkit/craftbukkit/zz/CraftLeaf"), "getHandle()Lnet/minecraft/zz/Zom;", bridge));
        assertFalse(annotated(merged.get("org/bukkit/craftbukkit/zz/CraftLeaf"), "getHandle()Lnet/minecraft/zz/Zom;", FullShells.SHELL_MEMBER), "다리는 껍데기 표지가 없다");
        assertTrue(annotated(merged.get("org/bukkit/craftbukkit/zz/CraftLeaf"), "other(I)Lnet/minecraft/zz/Zom;", FullShells.SHELL_MEMBER));

        CraftRoles roles = new CraftRoles(g);
        String tsv = roles.build(m.bridges, g.conversions.converted);
        assertTrue(tsv.contains("org/bukkit/craftbukkit/zz/CraftLeaf\tgetHandle\t()Lnet/minecraft/zz/Zom;\tb\n"), tsv);
        assertTrue(tsv.contains("org/bukkit/craftbukkit/zz/CraftLeaf\tother\t(I)Lnet/minecraft/zz/Zom;\td\n"), tsv);

        Path out = tmp.resolve("craft-shells");
        for (Map.Entry<String, byte[]> e : merged.entrySet()) {
            Path p = out.resolve(e.getKey() + ".class");
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL(), compat.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> leaf = Class.forName("org.bukkit.craftbukkit.zz.CraftLeaf", true, cl);
            Class<?> mid = Class.forName("org.bukkit.craftbukkit.zz.CraftMid", true, cl);
            Class<?> root = Class.forName("org.bukkit.craftbukkit.zz.CraftRoot", true, cl);
            Class<?> zom = Class.forName("net.minecraft.zz.Zom", true, cl);
            Class<?> mob = Class.forName("net.minecraft.zz.Mob", true, cl);
            Object l = leaf.getConstructor().newInstance();
            Object z = zom.getConstructor().newInstance();
            root.getField("h").set(l, z);
            java.lang.reflect.Method narrow = method(leaf, "getHandle", zom);
            assertTrue(narrow.invoke(l) == z, "좁힌 접근자가 같은 객체를 준다");
            assertTrue(method(mid, "getHandle", mob).invoke(l) == z);
            assertTrue(method(mid, "getHandle", Class.forName("net.minecraft.zz.Enemy", true, cl)).invoke(l) == z, "컴파일러 다리");
            root.getField("h").set(l, null);
            assertEquals(null, narrow.invoke(l), "null 은 null");
            root.getField("h").set(l, mob.getConstructor().newInstance());
            var ex = assertThrows(InvocationTargetException.class, () -> narrow.invoke(l));
            assertInstanceOf(UnsupportedNmsCall.class, ex.getCause(), "형이 안 맞으면 ClassCastException 이 아니라 껍데기와 같은 오류");
        }
    }

    /**
     * T-264 P4 변환 가족: CraftBukkit 클래스의 레지스트리 값 변환 정적 메서드는 손 클래스에 합칠 때(CraftEff)도 통째 껍데기(CraftEff2)도 진짜 몸통
     * (CraftConvert, @CraftBridge)이 되고, 키·위치 변환이나 Registries 필드가 없는 형은 껍데기로 남는다. 만든 클래스를 JVM 에 올려 부른다.
     */
    @Test
    void craftRegistryConversionsGetRealBodies() throws Exception {
        String reg = "\tLnet/minecraft/resources/ResourceKey;\tLnet/minecraft/resources/ResourceKey<Lnet/minecraft/core/Registry<";
        String paper = String.join("\n",
                "#furnace-sig-table\t2\tpaper-server-test",
                "F\t25\tnet/minecraft/core/registries/Registries\tMOB_EFFECT" + reg + "Lnet/minecraft/world/effect/MobEffect;>;>;",
                "F\t25\tnet/minecraft/core/registries/Registries\tPOTION" + reg + "Lnet/minecraft/world/item/alchemy/Potion;>;>;",
                "C\t33\torg/bukkit/craftbukkit/zz/CraftEff\tjava/lang/Object\t-\t-\t-",
                "M\t9\torg/bukkit/craftbukkit/zz/CraftEff\tminecraftToBukkit\t(Lnet/minecraft/world/effect/MobEffect;)Lorg/bukkit/potion/PotionEffectType;\t-",
                "M\t9\torg/bukkit/craftbukkit/zz/CraftEff\tbukkitToMinecraftHolder\t(Lorg/bukkit/potion/PotionEffectType;)Lnet/minecraft/core/Holder;"
                        + "\t(Lorg/bukkit/potion/PotionEffectType;)Lnet/minecraft/core/Holder<Lnet/minecraft/world/effect/MobEffect;>;",
                "C\t33\torg/bukkit/craftbukkit/zz/CraftEff2\tjava/lang/Object\t-\t-\t-",
                "M\t1\torg/bukkit/craftbukkit/zz/CraftEff2\t<init>\t()V\t-",
                "M\t9\torg/bukkit/craftbukkit/zz/CraftEff2\tbukkitToMinecraft\t(Lorg/bukkit/potion/PotionEffectType;)Lnet/minecraft/world/effect/MobEffect;\t-",
                "M\t9\torg/bukkit/craftbukkit/zz/CraftEff2\tminecraftHolderToBukkit\t(Lnet/minecraft/core/Holder;)Lorg/bukkit/potion/PotionEffectType;"
                        + "\t(Lnet/minecraft/core/Holder<Lnet/minecraft/world/effect/MobEffect;>;)Lorg/bukkit/potion/PotionEffectType;",
                "M\t9\torg/bukkit/craftbukkit/zz/CraftEff2\tbukkitToMinecraftHolder\t(Lorg/bukkit/potion/PotionType;)Lnet/minecraft/core/Holder;"
                        + "\t(Lorg/bukkit/potion/PotionType;)Lnet/minecraft/core/Holder<Lnet/minecraft/world/item/alchemy/Potion;>;",
                "M\t9\torg/bukkit/craftbukkit/zz/CraftEff2\tminecraftToBukkit\t(Lnet/minecraft/resources/ResourceLocation;)Lorg/bukkit/NamespacedKey;\t-",
                "M\t9\torg/bukkit/craftbukkit/zz/CraftEff2\tminecraftToBukkit\t(Lnet/minecraft/core/BlockPos;)Lorg/bukkit/Art;\t-",
                "");
        Path compat = tmp.resolve("conv");
        write(compat, "org/bukkit/craftbukkit/zz/CraftEff", Opcodes.ACC_PUBLIC, "java/lang/Object", null, cw -> ctor(cw, "java/lang/Object"));
        FullShells g = new FullShells(table(paper), table(EMPTY), table(EMPTY), new FullShells.Existing(List.of(compat)));
        Map<String, byte[]> shells = g.generate();
        ShellMerge m = new ShellMerge(g, FullShells.Existing.classesIn(compat));
        Map<String, byte[]> merged = m.merge();
        assertEquals(Set.of("org/bukkit/craftbukkit/zz/CraftEff#minecraftToBukkit(Lnet/minecraft/world/effect/MobEffect;)Lorg/bukkit/potion/PotionEffectType;",
                "org/bukkit/craftbukkit/zz/CraftEff#bukkitToMinecraftHolder(Lorg/bukkit/potion/PotionEffectType;)Lnet/minecraft/core/Holder;",
                "org/bukkit/craftbukkit/zz/CraftEff2#bukkitToMinecraft(Lorg/bukkit/potion/PotionEffectType;)Lnet/minecraft/world/effect/MobEffect;",
                "org/bukkit/craftbukkit/zz/CraftEff2#minecraftHolderToBukkit(Lnet/minecraft/core/Holder;)Lorg/bukkit/potion/PotionEffectType;",
                "org/bukkit/craftbukkit/zz/CraftEff2#bukkitToMinecraftHolder(Lorg/bukkit/potion/PotionType;)Lnet/minecraft/core/Holder;"), g.conversions.converted);
        String bridge = "Lorg/domi/furnace/compat/nms/CraftBridge;";
        byte[] eff2 = shells.get("org/bukkit/craftbukkit/zz/CraftEff2");
        assertTrue(annotated(eff2, "bukkitToMinecraft(Lorg/bukkit/potion/PotionEffectType;)Lnet/minecraft/world/effect/MobEffect;", bridge));
        assertFalse(annotated(eff2, "bukkitToMinecraft(Lorg/bukkit/potion/PotionEffectType;)Lnet/minecraft/world/effect/MobEffect;", FullShells.SHELL_MEMBER));
        assertTrue(annotated(eff2, "minecraftToBukkit(Lnet/minecraft/resources/ResourceLocation;)Lorg/bukkit/NamespacedKey;", FullShells.SHELL_MEMBER), "위치 변환은 레지스트리 값 변환이 아니다");
        assertTrue(annotated(eff2, "minecraftToBukkit(Lnet/minecraft/core/BlockPos;)Lorg/bukkit/Art;", FullShells.SHELL_MEMBER), "Registries 필드가 없는 형");
        assertTrue(annotated(merged.get("org/bukkit/craftbukkit/zz/CraftEff"), "minecraftToBukkit(Lnet/minecraft/world/effect/MobEffect;)Lorg/bukkit/potion/PotionEffectType;", bridge));
        String tsv = new CraftRoles(g).build(m.bridges, g.conversions.converted);
        assertTrue(tsv.contains("org/bukkit/craftbukkit/zz/CraftEff\tminecraftToBukkit\t(Lnet/minecraft/world/effect/MobEffect;)Lorg/bukkit/potion/PotionEffectType;\tc\n"), tsv);
        assertTrue(tsv.contains("org/bukkit/craftbukkit/zz/CraftEff2\tminecraftToBukkit\t(Lnet/minecraft/core/BlockPos;)Lorg/bukkit/Art;\td\n"), tsv);

        Path out = tmp.resolve("conv-shells");
        for (Map.Entry<String, byte[]> e : Map.of("org/bukkit/craftbukkit/zz/CraftEff", merged.get("org/bukkit/craftbukkit/zz/CraftEff"),
                "org/bukkit/craftbukkit/zz/CraftEff2", eff2).entrySet()) {
            Path p = out.resolve(e.getKey() + ".class");
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL(), compat.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> c1 = Class.forName("org.bukkit.craftbukkit.zz.CraftEff", true, cl);
            Class<?> c2 = Class.forName("org.bukkit.craftbukkit.zz.CraftEff2", true, cl);
            // 실제 레지스트리 왕복은 서버가 있어야 해서 CraftConvertTest 가 본다 — 여기서는 몸통이 검증기를 지나고 도우미로 가는지만
            Class<?> mobEffect = net.minecraft.world.effect.MobEffect.class;
            var nul = assertThrows(InvocationTargetException.class, () -> c1.getMethod("minecraftToBukkit", mobEffect).invoke(null, (Object) null));
            assertInstanceOf(IllegalArgumentException.class, nul.getCause());
            var missing = assertThrows(InvocationTargetException.class,
                    () -> c2.getMethod("bukkitToMinecraftHolder", org.bukkit.potion.PotionType.class).invoke(null, org.bukkit.potion.PotionType.WATER));
            assertInstanceOf(UnsupportedNmsCall.class, missing.getCause(), "Furnace 가 아직 주지 않는 레지스트리(물약)는 껍데기와 같은 오류");
        }
    }

    private static java.lang.reflect.Method method(Class<?> c, String name, Class<?> ret) {
        for (java.lang.reflect.Method x : c.getDeclaredMethods()) if (x.getName().equals(name) && x.getReturnType() == ret && x.getParameterCount() == 0) return x;
        throw new AssertionError(c + "#" + name + " -> " + ret);
    }

    private static boolean annotated(byte[] cls, String method, String annotation) {
        boolean[] found = {false};
        new ClassReader(cls).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!(name + descriptor).equals(method)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public org.objectweb.asm.AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        if (desc.equals(annotation)) found[0] = true;
                        return null;
                    }
                };
            }
        }, 0);
        return found[0];
    }

    // ───────────────────────── 도우미 ─────────────────────────

    static SigTable table(String text) throws Exception {
        return SigTable.read(new BufferedReader(new StringReader(text)));
    }

    private static boolean hasField(Class<?> c, String name) {
        for (var f : c.getDeclaredFields()) if (f.getName().equals(name)) return true;
        return false;
    }

    private static List<String> methods(byte[] b) {
        List<String> out = new java.util.ArrayList<>();
        new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                out.add(access + " " + name + descriptor);
                return null;
            }
        }, ClassReader.SKIP_CODE);
        return out;
    }

    private static void ctor(ClassWriter cw, String sup) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, sup, "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    private static void ret(ClassWriter cw, int access, String name, int value) {
        MethodVisitor mv = cw.visitMethod(access, name, "()I", null, null);
        mv.visitCode();
        mv.visitIntInsn(Opcodes.BIPUSH, value);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    private static void write(Path dir, String name, int access, String sup, String[] itfs, Consumer<ClassWriter> body) throws Exception {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, access | ((access & Opcodes.ACC_INTERFACE) == 0 ? Opcodes.ACC_SUPER : 0), name, null, sup, itfs);
        body.accept(cw);
        cw.visitEnd();
        Path p = dir.resolve(name + ".class");
        Files.createDirectories(p.getParent());
        Files.write(p, cw.toByteArray());
    }
}
