package org.domi.furnace.compat.shimgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * T-263 S1b: 추론 도구의 모양 맞추기. 가짜 서명표와 ASM 으로 만든 "바닐라" 몸통(javac 가 내는 꼴 그대로)으로
 * 엔티티 데이터 읽기·쓰기·깃발 읽기·깃발 쓰기와 별칭을 알아보는지, 모양이 조금이라도 다르면 후보를 내지 않는지 본다.
 */
class LinkInferTest {

    @TempDir
    Path tmp;

    static final String Z = "net/minecraft/zz/";
    static final String MOB = Z + "Mob";
    static final String SED = "net/minecraft/network/syncher/SynchedEntityData";
    static final String ACC = "Lnet/minecraft/network/syncher/EntityDataAccessor;";
    static final String SER = "net/minecraft/network/syncher/EntityDataSerializers";
    static final String SER_DESC = "Lnet/minecraft/network/syncher/EntityDataSerializer;";

    static final String PAPER = String.join("\n",
            "#furnace-sig-table\t2\tpaper-server-test",
            "C\t33\t" + Z + "Base\tjava/lang/Object\t-\t-\t-",
            "F\t4\t" + Z + "Base\tentityData\tL" + SED + ";\t-",
            "F\t28\t" + Z + "Base\tDATA_SHARED\t" + ACC + "\t-",
            "M\t1\t" + Z + "Base\tbase\t()I\t-",
            "C\t33\t" + MOB + "\t" + Z + "Base\t-\t-\t-",
            "F\t26\t" + MOB + "\tDATA_BABY\t" + ACC + "\t-",
            "F\t26\t" + MOB + "\tDATA_FLAGS\t" + ACC + "\t-",
            "F\t26\t" + MOB + "\tDATA_NAME\t" + ACC + "\t-",
            "M\t1\t" + MOB + "\tisBaby\t()Z\t-",
            "M\t1\t" + MOB + "\tsetBaby\t(Z)V\t-",
            "M\t1\t" + MOB + "\tgetName2\t()Ljava/util/Optional;\t()Ljava/util/Optional<Lnet/minecraft/zz/Text;>;",
            "M\t1\t" + MOB + "\tgetOwner2\t()Ljava/util/Optional;\t()Ljava/util/Optional<Ljava/util/UUID;>;",
            "M\t1\t" + MOB + "\tshared\t()I\t-",
            "M\t1\t" + MOB + "\tisAggro\t()Z\t-",
            "M\t1\t" + MOB + "\tsetAggro\t(Z)V\t-",
            "M\t1\t" + MOB + "\tsetAggroWrong\t(Z)V\t-",
            "M\t1\t" + MOB + "\tlevel\t()I\t-",
            "M\t2\t" + MOB + "\thidden\t()I\t-",
            "M\t1\t" + MOB + "\tviaHidden\t()I\t-",
            "M\t1\t" + MOB + "\tdoubled\t()I\t-",
            "M\t1\t" + MOB + "\tsharedAsFlag\t()Z\t-",
            "C\t33\t" + SER + "\tjava/lang/Object\t-\t-\t-",
            serializer("BOOLEAN", "Ljava/lang/Boolean;"),
            serializer("BYTE", "Ljava/lang/Byte;"),
            serializer("INT", "Ljava/lang/Integer;"),
            serializer("OPTIONAL_COMPONENT", "Ljava/util/Optional<Lnet/minecraft/zz/Text;>;"),
            "");

    static String serializer(String kind, String value) {
        return "F\t25\t" + SER + "\t" + kind + "\t" + SER_DESC + "\t" + SER_DESC.substring(0, SER_DESC.length() - 1) + "<" + value + ">;";
    }

    @Test
    void recognisesEntityDataIdiomsAndAliases() throws Exception {
        Path jar = tmp.resolve("vanilla.jar");
        try (OutputStream os = Files.newOutputStream(jar); ZipOutputStream z = new ZipOutputStream(os)) {
            z.putNextEntry(new ZipEntry(MOB + ".class"));
            z.write(vanillaMob(false));
            z.closeEntry();
        }
        Path paperJar = tmp.resolve("paper.jar");
        try (OutputStream os = Files.newOutputStream(paperJar); ZipOutputStream z = new ZipOutputStream(os)) {
            z.putNextEntry(new ZipEntry(MOB + ".class"));
            z.write(vanillaMob(true));
            z.closeEntry();
        }
        FullShells g = new FullShells(ShellMergeTest.table(PAPER), ShellMergeTest.table(ShellMergeTest.EMPTY), ShellMergeTest.table(ShellMergeTest.EMPTY),
                new FullShells.Existing(List.of()));
        // 칸 번호표 A 줄: "클래스 지문10" → 값 종류
        g.dataSlots = Map.of(
                "net.minecraft.zz.Mob " + Links.fingerprint(MOB, "DATA_BABY").substring(1), "BOOLEAN",
                "net.minecraft.zz.Mob " + Links.fingerprint(MOB, "DATA_FLAGS").substring(1), "BYTE",
                "net.minecraft.zz.Mob " + Links.fingerprint(MOB, "DATA_NAME").substring(1), "OPTIONAL_COMPONENT",
                "net.minecraft.zz.Base " + Links.fingerprint(Z + "Base", "DATA_SHARED").substring(1), "INT");
        LinkInfer inf = new LinkInfer(g);
        for (String[] m : new String[][] {{"sharedAsFlag", "()Z"}, {"isBaby", "()Z"}, {"setBaby", "(Z)V"}, {"getName2", "()Ljava/util/Optional;"}, {"getOwner2", "()Ljava/util/Optional;"}, {"shared", "()I"}, {"isAggro", "()Z"},
                {"setAggro", "(Z)V"}, {"setAggroWrong", "(Z)V"}, {"level", "()I"}, {"viaHidden", "()I"}, {"doubled", "()I"}}) {
            inf.shells.add(new LinkInfer.Shell(MOB, m[0], m[1], Opcodes.ACC_PUBLIC));
        }
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            inf.readBodies(zf, inf.bodies);
        }
        inf.paperBodies = new java.util.HashMap<>();
        try (ZipFile zf = new ZipFile(paperJar.toFile())) {
            inf.readBodies(zf, inf.paperBodies);
        }
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            inf.readReactions(zf);
        }
        assertEquals(java.util.Set.of(MOB + " DATA_FLAGS"), inf.reactingSlots);
        inf.classify();
        Map<String, String> got = new TreeMap<>();
        for (LinkInfer.Candidate c : inf.candidates) got.put(c.m().name(), c.target() + " " + c.check());
        String baby = Links.fingerprint(MOB, "DATA_BABY"), flags = Links.fingerprint(MOB, "DATA_FLAGS"), name = Links.fingerprint(MOB, "DATA_NAME");
        Map<String, String> want = new TreeMap<>(Map.of(
                "isBaby", "@idiom(entity-data-get, " + baby + ") read=owner",
                "setBaby", "@idiom(entity-data-set, " + baby + ") write",
                "getName2", "@idiom(entity-data-get, " + name + ") read=owner",
                "shared", "@idiom(entity-data-get, net.minecraft.zz.Base.DATA_SHARED) read=owner",   // 보호 칸은 평문, 선언 클래스(상위)로
                "isAggro", "@idiom(entity-data-flag-get, " + flags + ", 4) read=owner",
                "setAggro", "@idiom(entity-data-flag-set, " + flags + ", 4) write",
                "level", "this.base pure",
                "viaHidden", "this." + Links.fingerprint(MOB, "hidden") + " pure"));
        want.put("sharedAsFlag", "@idiom(entity-data-get, net.minecraft.zz.Base.DATA_SHARED) read=owner");
        want.put("getOwner2", "@idiom(entity-data-get, " + name + ") read=owner");
        assertEquals(want, got, "setAggroWrong(마스크가 짝이 아님)·doubled(값을 바꿈)는 후보가 아니다");
        // 모양은 맞지만 INT 칸을 Boolean 으로 읽는 몸통은 칸 값 종류 검사에서 걸러진다
        Map<String, String> typeBad = new TreeMap<>();
        for (Map.Entry<LinkInfer.Candidate, String> e : inf.typeRejected.entrySet()) typeBad.put(e.getKey().m().name(), e.getValue());
        // Paper 가 몸통을 바꾼 setBaby(이벤트를 더한 꼴)·level 은 Paper 대조에서 걸러진다(호환 면은 Paper)
        assertEquals(Map.of("sharedAsFlag", "entity data slot value type differs from the member type",
                "setBaby", "Paper body differs from the vanilla body", "level", "Paper body differs from the vanilla body",
                "getOwner2", "entity data slot generic value type differs from the member type",
                "setAggro", "entity data slot has a reaction (onSyncedDataUpdated) - a write needs a separate check"), typeBad);   // 읽기 isAggro 는 그대로   // Optional<UUID> 멤버가 Optional<글> 칸을 읽음
        assertTrue(LinkInfer.summary(inf, inf.candidates, Map.of(), List.of()).contains("| G1 entity-data-flag-set | 1 | 1 |"));
        assertEquals("private or package member written in plain text - use", LinkInfer.reasonKind("private or package member written in plain text - use #0123456789: net.minecraft.zz.Mob#x()V"));
    }

    // ── S4 블록 상태 관용구 ──

    static final String CROP = Z + "Crop", PROPS = Z + "Props";
    static final String STATE = "net/minecraft/world/level/block/state/BlockState";
    static final String SD = "L" + STATE + ";";
    static final String INT_P = "Lnet/minecraft/world/level/block/state/properties/IntegerProperty;";
    static final String BOOL_P = "Lnet/minecraft/world/level/block/state/properties/BooleanProperty;";
    static final String PROP = "net/minecraft/world/level/block/state/properties/Property";

    static final String BLOCK_PAPER = String.join("\n",
            "#furnace-sig-table\t2\tpaper-server-test",
            "C\t33\t" + PROPS + "\tjava/lang/Object\t-\t-\t-",
            "F\t25\t" + PROPS + "\tAGE_5\t" + INT_P + "\t-",
            "C\t33\t" + CROP + "\tjava/lang/Object\t-\t-\t-",
            "F\t26\t" + CROP + "\tAGE\t" + INT_P + "\t-",
            "F\t25\t" + CROP + "\tLIT\t" + BOOL_P + "\t-",
            "");

    @Test
    void recognisesBlockStateIdioms() throws Exception {
        Path jar = tmp.resolve("vanilla.jar"), paperJar = tmp.resolve("paper.jar");
        for (boolean paper : new boolean[] {false, true}) {
            try (OutputStream os = Files.newOutputStream(paper ? paperJar : jar); ZipOutputStream z = new ZipOutputStream(os)) {
                z.putNextEntry(new ZipEntry(CROP + ".class"));
                z.write(vanillaCrop(paper));
                z.closeEntry();
            }
        }
        FullShells g = new FullShells(ShellMergeTest.table(BLOCK_PAPER), ShellMergeTest.table(ShellMergeTest.EMPTY), ShellMergeTest.table(ShellMergeTest.EMPTY),
                new FullShells.Existing(List.of()));
        LinkInfer inf = new LinkInfer(g);
        String[][] members = {{"isRandomlyTicking", "(" + SD + ")Z"}, {"getAge", "(" + SD + ")I"}, {"signal", "(" + SD + "Ljava/lang/Object;Ljava/lang/Object;)I"},
                {"notLit", "(Ljava/lang/Object;" + SD + ")Z"}, {"grow", "(" + SD + "I)" + SD}, {"maxAge", "(" + SD + ")" + SD}, {"isYoung", "(" + SD + ")Z"},
                {"twoStates", "(" + SD + SD + ")Z"}, {"isLit", "(" + SD + ")Z"}};
        for (String[] m : members) inf.shells.add(new LinkInfer.Shell(CROP, m[0], m[1], Opcodes.ACC_PUBLIC));
        Map<String, String> vanillaAliases = new java.util.HashMap<>(), paperAliases = new java.util.HashMap<>();
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            inf.readBodies(zf, inf.bodies);
            LinkInfer.readPropertyAliases(zf, vanillaAliases);
        }
        inf.paperBodies = new java.util.HashMap<>();
        try (ZipFile zf = new ZipFile(paperJar.toFile())) {
            inf.readBodies(zf, inf.paperBodies);
            LinkInfer.readPropertyAliases(zf, paperAliases);
        }
        assertEquals(Map.of(CROP + " AGE", PROPS + " AGE_5"), vanillaAliases, "LIT 은 다른 필드를 그대로 넣지 않는다(만든다)");
        inf.propertyAliases.putAll(vanillaAliases);
        inf.classify();
        Map<String, String> got = new TreeMap<>();
        for (LinkInfer.Candidate c : inf.candidates) got.put(c.m().name(), c.target() + " " + c.check());
        String age = "net.minecraft.zz.Props.AGE_5", lit = "net.minecraft.zz.Crop.LIT";
        assertEquals(new TreeMap<>(Map.of(
                "isRandomlyTicking", "@idiom(block-state-int-lt, " + age + ", 5) pure",
                "getAge", "@idiom(block-state-get, " + age + ") pure",
                "signal", "@idiom(block-state-pick-int, " + lit + ", 15, 0) pure",
                "notLit", "@idiom(block-state-not, " + lit + ") pure",
                "grow", "@idiom(block-state-set, " + age + ") pure",
                "maxAge", "@idiom(block-state-set-int, " + age + ", 5) pure",
                "isYoung", "@idiom(block-state-int-eq, " + age + ", 0) pure",
                "isLit", "@idiom(block-state-get, " + lit + ") pure")), got, "twoStates(상태 인자 둘)는 후보가 아니다 — 겹침의 상태 자리가 갈리지 않게");
        Map<String, String> bad = new TreeMap<>();
        for (Map.Entry<LinkInfer.Candidate, String> e : inf.typeRejected.entrySet()) bad.put(e.getKey().m().name(), e.getValue());
        assertEquals(Map.of("isLit", "Paper body differs from the vanilla body"), bad);
    }

    /** 바닐라 블록 흉내(javac 가 내는 꼴). paper 면 isLit 만 다르다(뒤집음). */
    private static byte[] vanillaCrop(boolean paper) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, CROP, null, "java/lang/Object", null);
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "AGE", INT_P, null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "LIT", BOOL_P, null, null).visitEnd();
        MethodVisitor ci = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        ci.visitCode();
        ci.visitFieldInsn(Opcodes.GETSTATIC, PROPS, "AGE_5", INT_P);
        ci.visitFieldInsn(Opcodes.PUTSTATIC, CROP, "AGE", INT_P);
        ci.visitLdcInsn("lit");
        ci.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/world/level/block/state/properties/BooleanProperty", "create", "(Ljava/lang/String;)" + BOOL_P, false);
        ci.visitFieldInsn(Opcodes.PUTSTATIC, CROP, "LIT", BOOL_P);
        ci.visitInsn(Opcodes.RETURN);
        ci.visitMaxs(0, 0);
        ci.visitEnd();
        method(cw, "isRandomlyTicking", "(" + SD + ")Z", mv -> {
            getValue(mv, 1, "AGE", INT_P);
            unbox(mv, "java/lang/Integer", "intValue", "()I");
            mv.visitInsn(Opcodes.ICONST_5);
            branch(mv, Opcodes.IF_ICMPGE, 1, 0, Opcodes.IRETURN);
        });
        method(cw, "getAge", "(" + SD + ")I", mv -> {
            getValue(mv, 1, "AGE", INT_P);
            unbox(mv, "java/lang/Integer", "intValue", "()I");
            mv.visitInsn(Opcodes.IRETURN);
        });
        method(cw, "signal", "(" + SD + "Ljava/lang/Object;Ljava/lang/Object;)I", mv -> {
            getValue(mv, 1, "LIT", BOOL_P);
            unbox(mv, "java/lang/Boolean", "booleanValue", "()Z");
            branch(mv, Opcodes.IFEQ, 15, 0, Opcodes.IRETURN);
        });
        method(cw, "notLit", "(Ljava/lang/Object;" + SD + ")Z", mv -> {
            getValue(mv, 2, "LIT", BOOL_P);
            unbox(mv, "java/lang/Boolean", "booleanValue", "()Z");
            branch(mv, Opcodes.IFNE, 1, 0, Opcodes.IRETURN);
        });
        method(cw, "grow", "(" + SD + "I)" + SD, mv -> {
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitFieldInsn(Opcodes.GETSTATIC, CROP, "AGE", INT_P);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            setValue(mv);
        });
        method(cw, "maxAge", "(" + SD + ")" + SD, mv -> {
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitFieldInsn(Opcodes.GETSTATIC, CROP, "AGE", INT_P);
            mv.visitInsn(Opcodes.ICONST_5);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            setValue(mv);
        });
        method(cw, "isYoung", "(" + SD + ")Z", mv -> {
            getValue(mv, 1, "AGE", INT_P);
            unbox(mv, "java/lang/Integer", "intValue", "()I");
            branch(mv, Opcodes.IFNE, 1, 0, Opcodes.IRETURN);
        });
        method(cw, "twoStates", "(" + SD + SD + ")Z", mv -> {
            getValue(mv, 1, "LIT", BOOL_P);
            unbox(mv, "java/lang/Boolean", "booleanValue", "()Z");
            mv.visitInsn(Opcodes.IRETURN);
        });
        method(cw, "isLit", "(" + SD + ")Z", mv -> {
            getValue(mv, 1, "LIT", BOOL_P);
            unbox(mv, "java/lang/Boolean", "booleanValue", "()Z");
            if (paper) {
                branch(mv, Opcodes.IFNE, 1, 0, Opcodes.IRETURN);   // Paper 가 바꾼 꼴(뒤집음)
            } else {
                mv.visitInsn(Opcodes.IRETURN);
            }
        });
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void getValue(MethodVisitor mv, int slot, String field, String desc) {
        mv.visitVarInsn(Opcodes.ALOAD, slot);
        mv.visitFieldInsn(Opcodes.GETSTATIC, CROP, field, desc);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STATE, "getValue", "(L" + PROP + ";)Ljava/lang/Comparable;", false);
    }

    private static void unbox(MethodVisitor mv, String box, String name, String desc) {
        mv.visitTypeInsn(Opcodes.CHECKCAST, box);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, box, name, desc, false);
    }

    private static void setValue(MethodVisitor mv) {
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STATE, "setValue", "(L" + PROP + ";Ljava/lang/Comparable;)Ljava/lang/Object;", false);
        mv.visitTypeInsn(Opcodes.CHECKCAST, STATE);
        mv.visitInsn(Opcodes.ARETURN);
    }

    /** jump L → a → goto E → L: b → E: ret (javac 의 참거짓·삼항 꼴). */
    private static void branch(MethodVisitor mv, int jump, int a, int b, int ret) {
        Label l = new Label(), e = new Label();
        mv.visitJumpInsn(jump, l);
        mv.visitIntInsn(Opcodes.BIPUSH, a);
        mv.visitJumpInsn(Opcodes.GOTO, e);
        mv.visitLabel(l);
        mv.visitIntInsn(Opcodes.BIPUSH, b);
        mv.visitLabel(e);
        mv.visitInsn(ret);
    }

    @Test
    void adoptRewritesOnlyMachineFilesWithShapeProof() throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("links-1.21.1"));
        Files.writeString(dir.resolve("2-entity.links"), "#furnace-links 1 1.21.1 area=2\n");          // 손 줄 파일: 그대로
        Files.writeString(dir.resolve("2-entity.auto.links"), "old\n");                               // 다시 씀
        Files.writeString(dir.resolve("9-util.auto.links"), "old\n");                                 // 줄이 없어졌으니 지움
        LinkInfer.Shell isBaby = new LinkInfer.Shell(MOB, "isBaby", "()Z", Opcodes.ACC_PUBLIC);
        LinkInfer.Shell hidden = new LinkInfer.Shell(MOB, "hidden", "()I", Opcodes.ACC_PRIVATE);
        LinkInfer.Shell level = new LinkInfer.Shell(MOB, "level", "()I", Opcodes.ACC_PUBLIC);
        List<LinkInfer.Candidate> ok = List.of(
                new LinkInfer.Candidate(isBaby, "@idiom(entity-data-get, #0123456789)", "read=owner", "G1", "entity-data-get"),
                new LinkInfer.Candidate(hidden, "@idiom(entity-data-get, #0123456789)", "read=owner", "G1", "entity-data-get"),
                new LinkInfer.Candidate(level, "this.base", "pure", "G3", "alias"));   // 별칭도 모양 증명(바닐라·Paper 꼴 + 대상이 살아 있음 — 빌드 검사)
        List<String[]> areas = List.<String[]>of(new String[] {"2", "entity", "net/minecraft/zz/"}, new String[] {"9", "util", "net/minecraft/util/"});
        assertEquals(3, LinkInfer.adopt(dir, ok, areas));
        assertEquals("#furnace-links 1 1.21.1 area=2\n", Files.readString(dir.resolve("2-entity.links")));
        assertTrue(!Files.exists(dir.resolve("9-util.auto.links")));
        String auto = Files.readString(dir.resolve("2-entity.auto.links"));
        assertTrue(auto.startsWith("#furnace-links 1 1.21.1 area=2\n# T-263 machine-adopted"), auto);
        assertTrue(auto.endsWith("class net.minecraft.zz.Mob\n"
                + "  " + Links.fingerprint(MOB, "hidden") + "()I -> @idiom(entity-data-get, #0123456789) read=owner via=G1 proof=shape\n"
                + "  isBaby()Z -> @idiom(entity-data-get, #0123456789) read=owner via=G1 proof=shape\n"
                + "  level()I -> this.base pure via=G3 proof=shape\n"), auto);
        // 채택 모드의 찾기는 손 줄 파일만 본다
        String[] args = new String[18];
        java.util.Arrays.fill(args, "-");
        args[16] = dir.toString();
        Path hand = Path.of(LinkInfer.withoutAutoLinks(args, tmp.resolve("report"))[16]);
        try (var l = Files.list(hand)) {
            assertEquals(List.of("2-entity.links"), l.map(x -> x.getFileName().toString()).sorted().toList());
        }
    }

    // ───────── javac 가 내는 꼴의 "바닐라" 몸통 ─────────

    /** paper=true 면 Paper 가 바꾼 꼴: setBaby 앞에 다른 호출, level 은 다른 멤버로. 나머지는 바닐라와 같다. */
    private static byte[] vanillaMob(boolean paper) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, MOB, null, Z + "Base", null);
        method(cw, "isBaby", "()Z", mv -> {
            get(mv, MOB, "DATA_BABY");
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
            mv.visitInsn(Opcodes.IRETURN);
        });
        method(cw, "setBaby", "(Z)V", mv -> {
            if (paper) {
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MOB, "base", "()I", false);
                mv.visitInsn(Opcodes.POP);
            }
            data(mv);
            mv.visitFieldInsn(Opcodes.GETSTATIC, MOB, "DATA_BABY", ACC);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SED, "set", "(" + ACC + "Ljava/lang/Object;)V", false);
            mv.visitInsn(Opcodes.RETURN);
        });
        method(cw, "getName2", "()Ljava/util/Optional;", mv -> {
            get(mv, MOB, "DATA_NAME");
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/util/Optional");
            mv.visitInsn(Opcodes.ARETURN);
        });
        method(cw, "shared", "()I", mv -> {
            get(mv, MOB, "DATA_SHARED");   // 바닐라 명령의 소유자는 참조한 클래스(Mob) — 선언은 상위 Base
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
            mv.visitInsn(Opcodes.IRETURN);
        });
        method(cw, "isAggro", "()Z", mv -> {
            get(mv, MOB, "DATA_FLAGS");
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Byte");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
            mv.visitInsn(Opcodes.ICONST_4);
            mv.visitInsn(Opcodes.IAND);
            Label f = new Label(), e = new Label();
            mv.visitJumpInsn(Opcodes.IFEQ, f);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitJumpInsn(Opcodes.GOTO, e);
            mv.visitLabel(f);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitLabel(e);
            mv.visitInsn(Opcodes.IRETURN);
        });
        method(cw, "onSyncedDataUpdated", "(" + ACC + ")V", mv -> {   // 깃발 칸이 바뀌면 반응한다
            mv.visitFieldInsn(Opcodes.GETSTATIC, MOB, "DATA_FLAGS", ACC);
            mv.visitInsn(Opcodes.POP);
            mv.visitInsn(Opcodes.RETURN);
        });
        method(cw, "getOwner2", "()Ljava/util/Optional;", mv -> {
            get(mv, MOB, "DATA_NAME");
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/util/Optional");
            mv.visitInsn(Opcodes.ARETURN);
        });
        method(cw, "sharedAsFlag", "()Z", mv -> {
            get(mv, MOB, "DATA_SHARED");
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
            mv.visitInsn(Opcodes.IRETURN);
        });
        method(cw, "setAggro", "(Z)V", mv -> flagSet(mv, 4, -5));
        method(cw, "setAggroWrong", "(Z)V", mv -> flagSet(mv, 4, -9));
        method(cw, "level", "()I", mv -> {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MOB, paper ? "hidden" : "base", "()I", false);
            mv.visitInsn(Opcodes.IRETURN);
        });
        method(cw, "viaHidden", "()I", mv -> {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MOB, "hidden", "()I", false);
            mv.visitInsn(Opcodes.IRETURN);
        });
        method(cw, "doubled", "()I", mv -> {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MOB, "base", "()I", false);
            mv.visitInsn(Opcodes.ICONST_2);
            mv.visitInsn(Opcodes.IMUL);
            mv.visitInsn(Opcodes.IRETURN);
        });
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void flagSet(MethodVisitor mv, int mask, int clear) {
        get(mv, MOB, "DATA_FLAGS");
        mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Byte");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
        mv.visitVarInsn(Opcodes.ISTORE, 2);
        mv.visitVarInsn(Opcodes.ILOAD, 1);
        Label off = new Label(), end = new Label();
        mv.visitJumpInsn(Opcodes.IFEQ, off);
        setByte(mv, mask, Opcodes.IOR);
        mv.visitJumpInsn(Opcodes.GOTO, end);
        mv.visitLabel(off);
        setByte(mv, clear, Opcodes.IAND);
        mv.visitLabel(end);
        mv.visitInsn(Opcodes.RETURN);
    }

    private static void setByte(MethodVisitor mv, int mask, int op) {
        data(mv);
        mv.visitFieldInsn(Opcodes.GETSTATIC, MOB, "DATA_FLAGS", ACC);
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitIntInsn(Opcodes.BIPUSH, mask);
        mv.visitInsn(op);
        mv.visitInsn(Opcodes.I2B);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SED, "set", "(" + ACC + "Ljava/lang/Object;)V", false);
    }

    private static void get(MethodVisitor mv, String owner, String field) {
        data(mv);
        mv.visitFieldInsn(Opcodes.GETSTATIC, owner, field, ACC);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SED, "get", "(" + ACC + ")Ljava/lang/Object;", false);
    }

    private static void data(MethodVisitor mv) {
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, MOB, "entityData", "L" + SED + ";");
    }

    private static void method(ClassWriter cw, String name, String desc, Consumer<MethodVisitor> body) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, desc, null, null);
        mv.visitCode();
        body.accept(mv);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}
