package org.domi.furnace.compat.realworld;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** NmsShimAudit(NMS shim 점검 도구)가 구멍 종류를 제대로 가르는지 — 손으로 만든 작은 플러그인·Paper jar 로. */
class NmsShimAuditTest {

    @TempDir
    Path tmp;

    private static byte[] pluginClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "audit/Plug", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "(Lnet/minecraft/core/BlockPos;)V", null, null);
        mv.visitCode();
        // 있음: BlockPos.above()
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "above", "()Lnet/minecraft/core/BlockPos;", false);
        mv.visitInsn(Opcodes.POP);
        // 서명 다름: 같은 이름 above 가 shim 에 있으나 (String) 판은 없다
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "above", "(Ljava/lang/String;)Lnet/minecraft/core/BlockPos;", false);
        mv.visitInsn(Opcodes.POP);
        // Paper 에도 없는 이름
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "noSuchAuditMember", "()V", false);
        // 클래스 통째로 없음
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "net/minecraft/NoSuchAuditClass");
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] paperClass(String name, String... methods) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        for (String m : methods) {
            int sp = m.indexOf(' ');
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, m.substring(0, sp), m.substring(sp + 1), null, null);
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    private Path jar(String file, boolean mojangManifest, java.util.Map<String, byte[]> entries) throws IOException {
        Path p = tmp.resolve(file);
        Manifest m = new Manifest();
        m.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (mojangManifest) m.getMainAttributes().putValue("paperweight-mappings-namespace", "mojang");
        try (OutputStream o = Files.newOutputStream(p); JarOutputStream jo = new JarOutputStream(o, m)) {
            for (var e : entries.entrySet()) {
                jo.putNextEntry(new JarEntry(e.getKey()));
                jo.write(e.getValue());
                jo.closeEntry();
            }
        }
        return p;
    }

    @Test
    void classifiesEachKindOfGap() throws IOException {
        Path plugin = jar("plug.jar", true, java.util.Map.of("audit/Plug.class", pluginClass()));
        NmsShimAudit.Result r = NmsShimAudit.audit(List.of(plugin));
        assertEquals(1, r.jars().size());
        assertTrue(r.jars().get(0).symbols() > 0);

        var byName = new java.util.HashMap<String, NmsShimAudit.Row>();
        for (var row : r.rows()) byName.put(row.miss().kind() + ":" + row.miss().name(), row);

        // BlockPos.above() 는 shim 에 있으니 구멍이 아니다 — 같은 이름은 서명 다름 한 건(String 판)만 나온다
        var sig = byName.get("M:above");
        assertNotNull(sig, "같은 이름의 다른 서술자는 서명 다름으로 나온다");
        assertEquals(NmsShimAudit.Category.SIGNATURE, sig.category());
        assertTrue(sig.shimSigs().contains("()Lnet/minecraft/core/BlockPos;"), sig.shimSigs());
        assertEquals(NmsShimAudit.Category.NOT_PAPER, byName.get("M:noSuchAuditMember").category());
        assertEquals(NmsShimAudit.Category.MISSING, byName.get("C:").category());
        assertEquals("net/minecraft/NoSuchAuditClass", byName.get("C:").miss().owner());

        String md = NmsShimAudit.render(r);
        assertTrue(md.contains("서명 다름"));
        assertTrue(md.contains("net.minecraft.NoSuchAuditClass"));
        assertTrue(NmsShimAudit.tsv(r).contains("noSuchAuditMember"));
    }

    @Test
    void spigotNamedJarIsSkipped() throws IOException {
        Path plugin = jar("spigot.jar", false, java.util.Map.of("audit/Plug.class", pluginClass()));
        NmsShimAudit.Result r = NmsShimAudit.audit(List.of(plugin));
        assertTrue(r.jars().get(0).skipped());
        assertTrue(r.rows().isEmpty());
    }

    @Test
    void paperJarComparisonFindsSignatureAndMissingMembers() throws IOException {
        Path plugin = jar("plug.jar", true, java.util.Map.of("audit/Plug.class", pluginClass()));
        // 가짜 Paper: BlockPos 가 above() 는 같게, above(String) 은 다르게(shim 엔 이름만 있음), fooPaperOnly() 는 shim 에 없음 선언. 클래스 하나는 shim 에 없다.
        Path paper = jar("paper.jar", false, java.util.Map.of(
                "net/minecraft/core/BlockPos.class", paperClass("net/minecraft/core/BlockPos",
                        "above ()Lnet/minecraft/core/BlockPos;", "above (Ljava/lang/String;)Lnet/minecraft/core/BlockPos;", "fooPaperOnly ()V"),
                "net/minecraft/NoSuchAuditClass.class", paperClass("net/minecraft/NoSuchAuditClass")));
        NmsShimAudit.Result base = NmsShimAudit.audit(List.of(plugin));
        NmsShimAudit.PaperCompare pc = NmsShimAudit.compareWithPaper(paper, base);
        assertEquals(2, pc.paperClasses());
        assertEquals(1, pc.shimClasses());
        assertEquals(List.of("net/minecraft/NoSuchAuditClass"), pc.classesMissing());
        var gap = pc.gaps().get(0);
        assertEquals("net/minecraft/core/BlockPos", gap.owner());
        assertTrue(gap.missingMethods().stream().anyMatch(s -> s.startsWith("fooPaperOnly")), gap.missingMethods().toString());
        assertTrue(gap.signatureMethods().stream().anyMatch(s -> s.startsWith("above(String)")), gap.signatureMethods().toString());
        // 있는 above() 는 구멍이 아니다
        assertTrue(gap.signatureMethods().stream().noneMatch(s -> s.startsWith("above()")));

        StringBuilder b = new StringBuilder();
        NmsShimAudit.renderPaper(b, new NmsShimAudit.Result(base.jars(), base.rows(), pc));
        assertTrue(b.toString().contains("서명 다름 1건"), b.toString());
    }

    @Test
    void spigotDirLoadsClassesAndRejectsIncomplete() throws IOException {
        // 빈 입력이면 대응표 없음(Spigot 이름 jar 는 건너뜀)
        org.junit.jupiter.api.Assertions.assertNull(NmsShimAudit.loadSpigot(""));
        org.junit.jupiter.api.Assertions.assertNull(NmsShimAudit.loadSpigot(null));
        // server.txt 만 있고 클래스 csrg 가 없으면 오류
        Path dir = Files.createDirectory(tmp.resolve("sp"));
        Files.writeString(dir.resolve("server.txt"), "net.minecraft.core.BlockPos -> a:\n");
        org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> NmsShimAudit.loadSpigot(dir.toString()));
        // 클래스 csrg 가 생기면 멤버 csrg 없이도 읽힌다
        Files.writeString(dir.resolve("bukkit-1.21.1-cl.csrg"), "a net/minecraft/core/BlockPosSpigot\n");
        assertNotNull(NmsShimAudit.loadSpigot(dir.toString()));
    }
}
