package org.domi.furnace.compat.shimgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;
import org.domi.furnace.compat.nms.link.LinkConvert;
import org.domi.furnace.compat.nms.link.LinkIdiom;
import org.domi.furnace.compat.nms.link.LinkTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * T-263 S1: 연결 파일 → 연결 몸통. 작은 가짜 서명표·ASM 손 shim·이 시험의 다리(@LinkTarget·@LinkIdiom·@LinkConvert)로
 * 전체 껍데기를 만들어 실제 JVM 에 올리고, 이은 멤버가 다리를 부르는지(소유 검사·값·변환 포함), 어긴 줄이 멈추는지 본다.
 */
class LinksTest {

    @TempDir
    Path tmp;

    static final String Z = "net/minecraft/zz/";
    static final String BRIDGE = "org.domi.furnace.compat.shimgen.LinksTest$Bridge";

    static final String PAPER = String.join("\n",
            "#furnace-sig-table\t2\tpaper-server-test",
            "C\t33\t" + Z + "HandBase\tjava/lang/Object\t-\t-\t-",
            "M\t1\t" + Z + "HandBase\t<init>\t()V\t-",
            "M\t1\t" + Z + "HandBase\tbase\t()I\t-",
            "M\t1\t" + Z + "HandBase\textra\t()I\t-",
            "F\t8\t" + Z + "HandBase\tSLOT\tI\t-",
            "F\t10\t" + Z + "HandBase\tSECRET\tI\t-",
            "C\t33\t" + Z + "Mob\t" + Z + "HandBase\t-\t-\t-",
            "F\t26\t" + Z + "Mob\tDATA_BABY\tI\t-",
            "F\t10\t" + Z + "Mob\tSLOT\tI\t-",
            "M\t1\t" + Z + "Mob\t<init>\t()V\t-",
            "M\t1\t" + Z + "Mob\thealth\t()F\t-",
            "M\t1\t" + Z + "Mob\tsetHealth\t(F)V\t-",
            "M\t1\t" + Z + "Mob\tisBaby\t()Z\t-",
            "M\t1\t" + Z + "Mob\tlevel\t()I\t-",
            "M\t9\t" + Z + "Mob\tsize\t(Ljava/lang/String;)I\t-",
            "M\t1\t" + Z + "Mob\tlabel\t()Ljava/lang/String;\t-",
            "M\t2\t" + Z + "Mob\tsecret\t()I\t-",
            "M\t1\t" + Z + "Mob\tplain\t()I\t-",
            "M\t1\t" + Z + "Mob\tgFlag\t()Z\t-",
            "M\t1\t" + Z + "Mob\tgInt\t()I\t-",
            "M\t1\t" + Z + "Mob\tgName\t()Ljava/lang/String;\t-",
            "M\t1\t" + Z + "Mob\tgSet\t(F)V\t-",
            "M\t1\t" + Z + "Mob\tb1\t()Z\t-",
            "M\t1\t" + Z + "Mob\tb4\t()I\t-",
            "M\t1\t" + Z + "Mob\tb5\t()I\t-",
            "M\t1\t" + Z + "Mob\tb2\t()Z\t-",
            "M\t1\t" + Z + "Mob\tb3\t()Z\t-",
            "M\t1\t" + Z + "Mob\toInst\t(Ljava/lang/String;)I\t-",
            "M\t9\t" + Z + "Mob\toStat\t(Ljava/lang/String;)I\t-",
            "M\t1\t" + Z + "Mob\toPair\t(Ljava/lang/String;I)I\t-",
            "M\t1\t" + Z + "Mob\toAmb\t(Ljava/lang/String;)I\t-",
            "M\t1\t" + Z + "Mob\toNone\t()I\t-",
            "M\t1\t" + Z + "Mob\toBadValue\t(Ljava/lang/String;)I\t-",
            "K\t" + Z + "Mob\tDATA_BABY\tI\t12",
            "C\t33\t" + Z + "Slots\tjava/lang/Object\t-\t-\t-",
            "F\t26\t" + Z + "Slots\tDATA_A\tLnet/minecraft/network/syncher/EntityDataAccessor;\t-",
            "F\t26\t" + Z + "Slots\tDATA_V\tLnet/minecraft/network/syncher/EntityDataAccessor;\t-",
            "M\t1\t" + Z + "Slots\ta\t()Z\t-",
            "M\t1\t" + Z + "Slots\tv\t()Z\t-",
            "");

    static final String EMPTY = "#furnace-sig-table\t2\tempty\n";

    // ───────── 이 시험의 다리 ─────────

    public static final class Rule {
        public static final List<String> CALLS = new ArrayList<>();

        public static void read(Object self, String what) { CALLS.add("read " + what); }

        public static void write(Object self, String what) { CALLS.add("write " + what); }
    }

    public static final class Bridge {
        public static float lastHealth;

        @LinkTarget(check = "read=any")
        public static float health(Object self) { return 20f; }

        @LinkTarget(check = "write", owner = Rule.class)
        public static void setHealth(Object self, float v) { lastHealth = v; }

        @LinkIdiom(value = "test-flag", check = "read=any")
        public static boolean flag(Object self, int slot) { return slot == 12; }

        @LinkTarget(check = "pure")
        public static int size(String s) { return s.length(); }

        public static Object lastSet;

        /** 제네릭 관용구(엔티티 데이터 칸처럼 형식이 칸마다 다름): 12 → true, 7 → "seven", 나머지 → 2배 정수. */
        @LinkIdiom(value = "test-get", check = "read=any")
        public static Object get(Object self, int slot) { return slot == 12 ? Boolean.TRUE : slot == 7 ? "seven" : Integer.valueOf(slot * 2); }

        @LinkIdiom(value = "test-set", check = "write", owner = Rule.class)
        public static void set(Object self, int slot, Object value) { lastSet = slot + ":" + value + ":" + value.getClass().getSimpleName(); }

        /** 칸 필드를 값으로 받는 관용구(엔티티 데이터 칸처럼 객체 값). */
        @LinkIdiom(value = "test-slot", check = "read=any")
        public static Object slot(Object self, Object slot) { return slot != null; }

        @LinkTarget(check = "pure")
        public static StringBuilder label(Object self) { return new StringBuilder("mob"); }

        @LinkConvert
        public static String str(StringBuilder b) { return b.toString(); }

        @LinkTarget(check = "pure")
        public static int secret(Object self) { return 99; }

        /** 겹친 관용구(S4 블록 상태처럼 멤버 인자 꼴마다 하나): 인스턴스 (글) · 정적 (글) · 인스턴스 (글, 정수). */
        @LinkIdiom(value = "test-over", check = "pure")
        public static int over(Object self, int base, String s) { return base + s.length(); }

        @LinkIdiom(value = "test-over", check = "pure")
        public static int over(int base, String s) { return -(base + s.length()); }

        @LinkIdiom(value = "test-over", check = "pure")
        public static int over(Object self, int base, String s, int k) { return base * k; }

        /** 둘 다 맞는 겹침 — 줄은 문제로 멈춰야 한다. */
        @LinkIdiom(value = "test-amb", check = "pure")
        public static int amb(Object self, Object a) { return 1; }

        @LinkIdiom(value = "test-amb", check = "pure")
        public static int amb(Object self, String a) { return 2; }
    }

    // ───────── 시험 ─────────

    @Test
    void linkedMembersCallTheirBridges() throws Exception {
        String links = String.join("\n",
                "#furnace-links 1 1.21.1 area=9",
                "# 주석 줄",
                "class net.minecraft.zz.Mob",
                "  health()F -> " + BRIDGE + ".health read=any   # 끝 주석",
                "  setHealth(F)V -> " + BRIDGE + ".setHealth write",
                "  isBaby()Z -> @idiom(test-flag, " + Links.fingerprint(Z + "Mob", "DATA_BABY") + ") read=any",
                "  level()I -> this.base pure",
                "  size(Ljava/lang/String;)I -> " + BRIDGE + ".size pure",
                "  label()Ljava/lang/String; -> " + BRIDGE + ".label pure",
                "  " + Links.fingerprint(Z + "Mob", "secret") + "()I -> " + BRIDGE + ".secret pure",
                "  gFlag()Z -> @idiom(test-get, " + Links.fingerprint(Z + "Mob", "DATA_BABY") + ") read=any",
                "  gInt()I -> @idiom(test-get, 5) read=any",
                "  gName()Ljava/lang/String; -> @idiom(test-get, 7) read=any",
                "  gSet(F)V -> @idiom(test-set, 3) write",
                "  oInst(Ljava/lang/String;)I -> @idiom(test-over, 10) pure",
                "  oStat(Ljava/lang/String;)I -> @idiom(test-over, 10) pure",
                "  oPair(Ljava/lang/String;I)I -> @idiom(test-over, 10) pure",
                "");
        Gen r = gen(links);
        r.g().checkLinks(null);
        assertEquals(14, r.g().links.linkedMembers);
        assertEquals(List.of(), shellMarked(r.classes().get(Z + "Mob"), "health()F", "setHealth(F)V", "isBaby()Z", "level()I", "size(Ljava/lang/String;)I",
                "label()Ljava/lang/String;", "secret()I"), "이은 멤버엔 껍데기 표지가 없다");
        assertEquals(List.of("plain()I"), shellMarked(r.classes().get(Z + "Mob"), "plain()I"), "줄 없는 멤버는 그대로 껍데기");

        try (URLClassLoader cl = r.load()) {
            Class<?> mob = Class.forName("net.minecraft.zz.Mob", true, cl);
            Object m = allocate(mob);
            assertEquals(20f, mob.getMethod("health").invoke(m));
            Rule.CALLS.clear();
            mob.getMethod("setHealth", float.class).invoke(m, 7.5f);
            assertEquals(7.5f, Bridge.lastHealth);
            assertEquals(List.of("write Mob#setHealth"), Rule.CALLS, "write 는 소유 검사를 먼저 부른다");
            assertEquals(true, mob.getMethod("isBaby").invoke(m), "관용구는 지문 필드의 값(상수 12)을 받는다");
            assertEquals(11, mob.getMethod("level").invoke(m), "별칭은 손 상위의 살아 있는 멤버를 부른다");
            assertEquals(5, mob.getMethod("size", String.class).invoke(null, "abcde"));
            assertEquals("mob", mob.getMethod("label").invoke(m), "@LinkConvert 로 반환을 바꾼다");
            var secret = mob.getDeclaredMethod("secret");
            secret.setAccessible(true);
            assertEquals(99, secret.invoke(m));
            // 제네릭 관용구: 반환 Object → 멤버 반환형(원시는 박스형 CHECKCAST + xxxValue), 원시 인자 → 박싱
            assertEquals(true, mob.getMethod("gFlag").invoke(m));
            assertEquals(10, mob.getMethod("gInt").invoke(m));
            assertEquals("seven", mob.getMethod("gName").invoke(m));
            Rule.CALLS.clear();
            mob.getMethod("gSet", float.class).invoke(m, 2.5f);
            assertEquals("3:2.5:Float", Bridge.lastSet);
            assertEquals(List.of("write Mob#gSet"), Rule.CALLS);
            // 겹친 관용구: 멤버 꼴에 맞는 하나를 부른다
            assertEquals(13, mob.getMethod("oInst", String.class).invoke(m, "abc"));
            assertEquals(-13, mob.getMethod("oStat", String.class).invoke(null, "abc"));
            assertEquals(40, mob.getMethod("oPair", String.class, int.class).invoke(m, "abc", 4));
            InvocationTargetException e = assertThrows(InvocationTargetException.class, () -> mob.getMethod("plain").invoke(m));
            assertInstanceOf(UnsupportedNmsCall.class, e.getCause());
        }
    }

    @Test
    void badLinesStopTheBuild() throws Exception {
        String links = String.join("\n",
                "#furnace-links 1 1.21.1 area=9",
                "class net.minecraft.zz.Mob",
                "  secret()I -> " + BRIDGE + ".secret pure",
                "  " + Links.fingerprint(Z + "Mob", "plain") + "()I -> " + BRIDGE + ".secret pure",
                "  level()I -> " + BRIDGE + ".health read=any",
                "  b1()Z -> @idiom(test-flag, " + Links.fingerprint(Z + "HandBase", "SECRET") + ") read=any",
                "  b2()Z -> @idiom(test-flag, " + Links.fingerprint(Z + "HandBase", "SLOT") + ") read=any",
                "  b3()Z -> @idiom(test-flag, 12) write",
                "  health()F -> " + BRIDGE + ".health read=any",
                "  health()F -> " + BRIDGE + ".health read=any",
                "  nothing()V -> " + BRIDGE + ".health read=any",
                "  isBaby()Z -> this.plain pure",
                "  b4()I -> this.secret pure",
                "  b5()I -> this." + Links.fingerprint(Z + "HandBase", "base") + " pure",
                "  oAmb(Ljava/lang/String;)I -> @idiom(test-amb) pure",
                "  oNone()I -> @idiom(test-over, 1) pure",
                "  oBadValue(Ljava/lang/String;)I -> @idiom(test-over, #0123456789) pure",
                "class net.minecraft.zz.HandBase",
                "  base()I -> " + BRIDGE + ".secret pure",
                "class net.minecraft.zz.Nope",
                "  x()V -> " + BRIDGE + ".health pure",
                "");
        Gen r = gen(links);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> r.g().checkLinks(null));
        String msg = e.getMessage();
        for (String want : List.of(
                "private or package member written in plain text - use " + Links.fingerprint(Z + "Mob", "secret"),
                "public/protected member written as a fingerprint",
                "no @LinkTarget overload of health takes these arguments",
                "names a private field of the supertype net.minecraft.zz.HandBase",
                "same name, different declaring class",
                "check kind write differs from the target's read=any",
                "duplicate line for net.minecraft.zz.Mob#health()F",
                "member not in the Paper signature table: net.minecraft.zz.Mob#nothing()V",
                "alias target has no behaviour or does not match: this.plain",
                "private or package alias target written in plain text - use this." + Links.fingerprint(Z + "Mob", "secret"),
                "public/protected alias target written as a fingerprint - write this.base",
                "line not used - the class is a hand shim and merging is off: net.minecraft.zz.HandBase#base()I",
                "class not in the Paper signature table: net.minecraft.zz.Nope",
                "several @LinkIdiom(\"test-amb\") overloads fit this member",
                "no @LinkIdiom(\"test-over\") overload takes this member's receiver, values and arguments",
                "no static field with fingerprint #0123456789 in the class or its supertypes")) {   // 꼴이 맞는 겹침의 값 문제는 구체적으로 남는다
            assertTrue(msg.contains(want), "문제 목록에 없다: " + want + "\n" + msg);
        }
    }

    @Test
    void mergedHandShimMembersAreLinkedToo() throws Exception {
        String links = String.join("\n",
                "#furnace-links 1 1.21.1 area=9",
                "class net.minecraft.zz.HandBase",
                "  extra()I -> " + BRIDGE + ".secret pure",
                "");
        Gen r = gen(links);
        ShellMerge m = new ShellMerge(r.g(), FullShells.Existing.classesIn(r.hand()));
        Map<String, byte[]> merged = m.merge();
        r.g().checkLinks(m);
        assertEquals(1, r.g().links.linkedMembers);
        assertEquals(1, m.added.get("linked (T-263)"));
        assertEquals(List.of(), shellMarked(merged.get(Z + "HandBase"), "extra()I"));
        Path out = tmp.resolve("merged");
        for (Map.Entry<String, byte[]> e : merged.entrySet()) {
            Path p = out.resolve(e.getKey() + ".class");
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL(), r.hand().toUri().toURL()}, LinksTest.class.getClassLoader())) {
            Class<?> hb = Class.forName("net.minecraft.zz.HandBase", true, cl);
            Object h = hb.getConstructor().newInstance();
            assertEquals(11, hb.getMethod("base").invoke(h), "손 멤버는 그대로");
            assertEquals(99, hb.getMethod("extra").invoke(h), "합친 빠진 멤버는 연결 몸통");
        }
    }

    @Test
    void parsesCommentsAndFingerprints() {
        Links l = new Links();
        l.parse("a.links", List.of(
                "#furnace-links 1 1.21.1 area=1",
                "# 머리 주석",
                "class a.b.C",
                "  #0123456789()V -> this.x pure  # 끝 주석",
                "  m(I)I -> @idiom(f, \"a, b\", 3, #abcdef0123) read=owner via=G1",
                "  n()V -> a.b.D.t api",
                "  o()V -> @idiom(g, \"x ) # y\") pure # 끝"));
        assertEquals(List.of(), l.problems);
        assertEquals(4, l.lines.size());
        assertEquals(new Links.Idiom("g", List.of("\"x ) # y\"")), l.lines.get(3).target(), "글 안의 ) 와 # 는 자르지 않는다");
        assertEquals("#0123456789", l.lines.get(0).member());
        assertEquals(new Links.Alias("x"), l.lines.get(0).target());
        assertEquals(new Links.Idiom("f", List.of("\"a, b\"", "3", "#abcdef0123")), l.lines.get(1).target());
        assertEquals(Links.Check.READ_OWNER, l.lines.get(1).check());
        assertEquals(Map.of("via", "G1"), l.lines.get(1).attrs());
        assertEquals(new Links.StaticTarget("a/b/D", "t"), l.lines.get(2).target());

        Links bad = new Links();
        bad.parse("b.links", List.of("#furnace-links 1 1.21.1", "  m()V -> a.b.D.t pure", "class a.b.C", "  m -> a.b.D.t pure", "  m()V -> a.b.D.t sometimes"));
        assertEquals(3, bad.problems.size(), bad.problems.toString());
    }

    @Test
    void fingerprintIsTheEntityIdTableForm() throws Exception {
        // entity_data/ids 와 같은 꼴: SHA-256("a.b.C.이름") 앞 10자리
        byte[] h = MessageDigest.getInstance("SHA-256").digest("net.minecraft.zz.Mob.DATA_BABY".getBytes(StandardCharsets.UTF_8));
        assertEquals("#" + HexFormat.of().formatHex(h).substring(0, 10), Links.fingerprint(Z + "Mob", "DATA_BABY"));
    }

    @Test
    void noLinksChangesNothing() throws Exception {
        Gen with = gen(null);
        with.g().checkLinks(null);
        assertEquals(0, with.g().links.linkedMembers);
        assertFalse(with.classes().isEmpty());
    }

    /**
     * 엔티티 데이터 D3: 칸 번호표에 줄이 있고 shim 에 값 종류 상수가 있는 정적 EntityDataAccessor 필드는 값이 있는 필드가 된다
     * (정적 초기화가 NmsEntityData.accessor(클래스, "이름") 로 채운다) — 그래서 관용구 줄이 그 필드를 값으로 받을 수 있다.
     * 값 종류 상수가 shim 에 없는 칸(시험용 가짜 종류 NO_SUCH_KIND — 바닐라 종류는 이제 다 있다)은 채우지 않아 껍데기 필드로 남고, 그 칸을 쓰는 줄은 멈춘다.
     */
    @Test
    void entityDataSlotFieldsAreFilledAndLinkable() throws Exception {
        String fa = Links.fingerprint(Z + "Slots", "DATA_A");
        String fv = Links.fingerprint(Z + "Slots", "DATA_V");
        Map<String, String> slots = Map.of("net.minecraft.zz.Slots " + fa.substring(1), "BYTE", "net.minecraft.zz.Slots " + fv.substring(1), "NO_SUCH_KIND");
        String ok = String.join("\n", "#furnace-links 1 1.21.1 area=9", "class net.minecraft.zz.Slots", "  a()Z -> @idiom(test-slot, " + fa + ") read=any", "");
        Gen r = gen(ok, slots);
        r.g().checkLinks(null);
        assertEquals(1, r.g().links.linkedMembers);
        assertEquals(1, r.g().dataSlotsFilled);
        byte[] b = r.classes().get(Z + "Slots");
        List<String> marked = new ArrayList<>();
        List<String> clinit = new ArrayList<>();
        new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                return new org.objectweb.asm.FieldVisitor(Opcodes.ASM9) {
                    @Override
                    public org.objectweb.asm.AnnotationVisitor visitAnnotation(String d, boolean visible) {
                        if (d.equals(FullShells.SHELL_MEMBER)) marked.add(name);
                        return null;
                    }
                };
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                if (!name.equals("<clinit>")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object v) { clinit.add(String.valueOf(v)); }

                    @Override
                    public void visitMethodInsn(int op, String owner, String n, String d, boolean itf) { clinit.add(owner + "." + n + d); }

                    @Override
                    public void visitFieldInsn(int op, String owner, String n, String d) { clinit.add(op == Opcodes.PUTSTATIC ? "put " + n : n); }
                };
            }
        }, 0);
        assertEquals(List.of("DATA_V"), marked, "값 종류가 shim 에 없는 칸만 껍데기 필드");
        assertEquals(List.of("L" + Z + "Slots;", "DATA_A", "org/domi/furnace/compat/nms/NmsEntityData.accessor(Ljava/lang/Class;Ljava/lang/String;)Lnet/minecraft/network/syncher/EntityDataAccessor;",
                "put DATA_A"), clinit);

        String bad = String.join("\n", "#furnace-links 1 1.21.1 area=9", "class net.minecraft.zz.Slots", "  v()Z -> @idiom(test-slot, " + fv + ") read=any", "");
        Gen rb = gen(bad, slots);
        assertTrue(rb.g().links.problems.stream().anyMatch(p -> p.contains("static field has no value in the shim yet: " + fv)), String.valueOf(rb.g().links.problems));
    }

    // ───────── 도움 ─────────

    private record Gen(FullShells g, Map<String, byte[]> classes, Path hand, Path out) {
        URLClassLoader load() throws Exception {
            return new URLClassLoader(new URL[]{out.toUri().toURL(), hand.toUri().toURL()}, LinksTest.class.getClassLoader());
        }
    }

    private Gen gen(String links) throws Exception { return gen(links, Map.of()); }

    private Gen gen(String links, Map<String, String> dataSlots) throws Exception {
        Path hand = tmp.resolve("hand");
        writeHandBase(hand);
        Path bridges = tmp.resolve("bridges");
        for (Class<?> c : List.of(Bridge.class, Rule.class)) {
            String n = c.getName().replace('.', '/');
            try (InputStream in = LinksTest.class.getClassLoader().getResourceAsStream(n + ".class")) {
                Path p = bridges.resolve(n + ".class");
                Files.createDirectories(p.getParent());
                Files.write(p, in.readAllBytes());
            }
        }
        // 칸 필드 시험은 shim 의 값 종류 상수(EntityDataSerializers)를 봐야 한다 — 그 클래스 하나만 얹는다
        for (String n : dataSlots.isEmpty() ? List.<String>of() : List.of("net/minecraft/network/syncher/EntityDataSerializers")) {
            try (InputStream in = LinksTest.class.getClassLoader().getResourceAsStream(n + ".class")) {
                Path p = bridges.resolve(n + ".class");
                Files.createDirectories(p.getParent());
                Files.write(p, in.readAllBytes());
            }
        }
        FullShells g = new FullShells(ShellMergeTest.table(PAPER), ShellMergeTest.table(EMPTY), ShellMergeTest.table(EMPTY),
                new FullShells.Existing(List.of(hand, bridges)));
        g.dataSlots = dataSlots;
        if (links != null) {
            Path dir = tmp.resolve("links");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("9.links"), links, StandardCharsets.UTF_8);
            g.links = Links.read(dir);
        }
        Map<String, byte[]> classes = g.generate();
        Path out = tmp.resolve("shells");
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            Path p = out.resolve(e.getKey() + ".class");
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
        return new Gen(g, classes, hand, out);
    }

    /** 손 shim HandBase: 생성자, base() = 11. */
    private static void writeHandBase(Path dir) throws Exception {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, Z + "HandBase", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "base", "()I", null, null);
        mv.visitCode();
        mv.visitIntInsn(Opcodes.BIPUSH, 11);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();
        Path p = dir.resolve(Z + "HandBase.class");
        Files.createDirectories(p.getParent());
        Files.write(p, cw.toByteArray());
    }

    /** 주어진 메서드 가운데 껍데기 표지(@NmsShellMember)가 달린 것. */
    private static List<String> shellMarked(byte[] b, String... keys) {
        List<String> want = List.of(keys);
        List<String> out = new ArrayList<>();
        new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                String key = name + desc;
                if (!want.contains(key)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public org.objectweb.asm.AnnotationVisitor visitAnnotation(String d, boolean visible) {
                        if (d.equals(FullShells.SHELL_MEMBER)) out.add(key);
                        return null;
                    }
                };
            }
        }, 0);
        return out;
    }

    /** 껍데기 생성자는 던지므로 생성자 없이 객체를 만든다(엔티티 기본값 뽑기 ExportEntityDataDefaults 와 같은 방법). */
    private static Object allocate(Class<?> c) throws Exception {
        var f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Object u = f.get(null);
        return u.getClass().getMethod("allocateInstance", Class.class).invoke(u, c);
    }
}
