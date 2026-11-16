package org.domi.furnace.tools.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.objectweb.asm.Opcodes.*;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

class PortCheckTest {
    /** 클래스 Tool 의 main: owner.name()I 를 한 번 부르고 상수 문자열을 하나 쓴다. */
    static byte[] tool(String pkgPrefix, String owner, String method, String text) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(V21, ACC_PUBLIC, pkgPrefix + "Tool", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "run", "(L" + owner + ";)I", null, null);
        mv.visitCode();
        mv.visitLdcInsn(text);
        mv.visitInsn(POP);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKEVIRTUAL, owner, method, "()I", false);
        mv.visitInsn(IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private JarRemapper remapper() throws IOException {
        MojangMappings maps = MojangMappings.parse(new StringReader(JarRemapperTest.MAPPINGS));
        return new JarRemapper(maps, ClassHierarchy.of(Map.of("d", new String[] {"a"}, "a", new String[] {"java/lang/Object"})));
    }

    @Test
    void faithfulPortIsReportedSame(@TempDir Path dir) throws IOException {
        Path legacy = Files.createDirectories(dir.resolve("legacy"));
        Path fresh = Files.createDirectories(dir.resolve("fresh/furnacetools"));
        Files.write(legacy.resolve("Tool.class"), tool("", "d", "c", "x"));          // 이전: 난독화 이름 d.c()
        Files.write(fresh.resolve("Tool.class"), tool("furnacetools/", "game/SubCounter", "next", "x"));
        List<PortCheck.ClassReport> r = PortCheck.compare(legacy, dir.resolve("fresh"), remapper());
        assertEquals(1, r.size());
        assertTrue(r.get(0).same(), r.get(0).differences().toString());
        assertEquals(1, r.get(0).methodsCompared());
    }

    @Test
    void wrongGuessedNameAndChangedConstantAreListed(@TempDir Path dir) throws IOException {
        Path legacy = Files.createDirectories(dir.resolve("legacy"));
        Path fresh = Files.createDirectories(dir.resolve("fresh/furnacetools"));
        Files.write(legacy.resolve("Tool.class"), tool("", "d", "c", "x"));
        Files.write(fresh.resolve("Tool.class"), tool("furnacetools/", "game/SubCounter", "twice", "y"));   // 이름을 잘못 짚음 + 상수 다름
        List<PortCheck.ClassReport> r = PortCheck.compare(legacy, dir.resolve("fresh"), remapper());
        assertFalse(r.get(0).same());
        String all = String.join("\n", r.get(0).differences());
        assertTrue(all.contains("- 이전에만") && all.contains("game/SubCounter.next"), all);
        assertTrue(all.contains("+ 새것에만") && all.contains("twice"), all);
        assertTrue(all.contains("ldc \"x\"") && all.contains("ldc \"y\""), all);
    }

    @Test
    void missingClassIsReported(@TempDir Path dir) throws IOException {
        Path legacy = Files.createDirectories(dir.resolve("legacy"));
        Files.createDirectories(dir.resolve("fresh"));
        Files.write(legacy.resolve("Tool.class"), tool("", "d", "c", "x"));
        List<PortCheck.ClassReport> r = PortCheck.compare(legacy, dir.resolve("fresh"), remapper());
        assertEquals("새 도구에 이 클래스가 없다", r.get(0).differences().get(0));
    }

    @Test
    void orderOnlyDifferenceIsDistinguished() {
        List<String> d = PortCheck.diff("m", List.of("a", "b", "c"), List.of("a", "c", "b"));
        assertEquals(1, d.size());
        assertTrue(d.get(0).contains("순서만"));
        assertTrue(PortCheck.diff("m", List.of("a"), List.of("a")).isEmpty());
    }

    @Test
    void soundTypesAreComparedBySoundNotByIndex() {
        String a = "[types]\n0 1.0 1.0 s1 s2 s3 s4 s5\n1 1.0 1.5 t1 t2 t3 t4 t5\n[blocks]\nstone 0\ngold 1\n[state-overrides]\npot cracked=true 1\n";
        String b = "[types]\n0 1.0 1.5 t1 t2 t3 t4 t5\n1 1.0 1.0 s1 s2 s3 s4 s5\n[blocks]\nstone 1\ngold 0\n[state-overrides]\npot cracked=true 0\n";
        assertEquals(null, Verifier.soundTypesDifference(a, b));
        String c = b.replace("gold 0", "gold 1");
        assertTrue(Verifier.soundTypesDifference(a, c).contains("gold"));
    }
}
