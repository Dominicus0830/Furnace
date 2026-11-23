package org.domi.furnace.tools.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class BodyTableTest {
    /** 메서드 m 이 callee 를 부르고(extraConst 가 켜지면 상수 하나를 더 놓는) 클래스. 람다 합성 메서드 둘은 번호를 바꿔 넣을 수 있다. */
    private static byte[] cls(String callee, boolean extraConst, String lambdaA, String lambdaB) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "a/C", null, "java/lang/Object", null);
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "K", "I", null, 7).visitEnd();
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "m", "()V", null, null);
        mv.visitCode();
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "a/X", callee, "()V", false);
        if (extraConst) {
            mv.visitInsn(Opcodes.ICONST_3);
            mv.visitInsn(Opcodes.POP);
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 0);
        mv.visitEnd();
        for (String[] l : new String[][] {{lambdaA, "x"}, {lambdaB, "y"}}) {
            MethodVisitor lm = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, l[0], "()V", null, null);
            lm.visitCode();
            lm.visitLdcInsn(l[1]);
            lm.visitInsn(Opcodes.POP);
            lm.visitInsn(Opcodes.RETURN);
            lm.visitMaxs(1, 0);
            lm.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static Map<String, String> rows(byte[] bytes, Path dir) throws IOException {
        BodyTable t = BodyTable.fromJar(jarOf(bytes, dir), java.util.List.of(), "x");
        Path out = dir.resolve("b.tsv.gz");
        t.write(out);
        Map<String, String> m = new HashMap<>();
        try (var in = new GZIPInputStream(Files.newInputStream(out))) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.startsWith("#")) continue;
                String[] c = line.split("\t");
                boolean member = c[0].equals("M") || c[0].equals("K");
                int from = member ? 4 : 2;
                m.put(c[0] + " " + c[1] + (member ? " " + c[2] : ""), String.join("\t", java.util.Arrays.copyOfRange(c, from, c.length)));
            }
        }
        return m;
    }

    private static Path jarOf(byte[] cls, Path dir) throws IOException {
        Path jar = Files.createTempFile(dir, "t", ".jar");
        try (var os = new java.util.jar.JarOutputStream(Files.newOutputStream(jar))) {
            os.putNextEntry(new java.util.jar.JarEntry("a/C.class"));
            os.write(cls);
            os.closeEntry();
        }
        return jar;
    }

    @Test
    void referenceOnlyChangeKeepsShapeButFlowChangeDoesNot(@TempDir Path dir) throws IOException {
        Map<String, String> base = rows(cls("foo", false, "lambda$m$0", "lambda$m$1"), dir);
        Map<String, String> renamed = rows(cls("bar", false, "lambda$m$0", "lambda$m$1"), dir);
        Map<String, String> flow = rows(cls("foo", true, "lambda$m$0", "lambda$m$1"), dir);
        String[] b = base.get("M a/C m").split("\t");
        String[] r = renamed.get("M a/C m").split("\t");
        String[] f = flow.get("M a/C m").split("\t");
        assertEquals(b[0], r[0], "이름만 바뀌면 모양 해시는 같다");
        assertNotEquals(b[1], r[1], "이름이 바뀌면 참조 해시는 다르다");
        assertNotEquals(b[0], f[0], "명령이 늘면 모양 해시가 달라진다");
        assertTrue(base.containsKey("K a/C K"), "static final 상수는 값으로 남는다");
    }

    @Test
    void lambdaBagIgnoresCompilerNumbering(@TempDir Path dir) throws IOException {
        Map<String, String> a = rows(cls("foo", false, "lambda$m$0", "lambda$m$1"), dir);
        Map<String, String> b = rows(cls("foo", false, "lambda$m$7", "lambda$m$3"), dir);
        assertEquals(a.get("L a/C"), b.get("L a/C"), "람다 번호가 달라도 묶음은 같다");
        assertTrue(a.get("L a/C").startsWith("2\t"), "람다 개수");
    }

    @Test
    void sameInputSameBytes(@TempDir Path dir) throws IOException {
        byte[] c = cls("foo", false, "lambda$m$0", "lambda$m$1");
        BodyTable.fromJar(jarOf(c, dir), java.util.List.of(), "x").write(dir.resolve("1.gz"));
        BodyTable.fromJar(jarOf(c, dir), java.util.List.of(), "x").write(dir.resolve("2.gz"));
        assertEquals(Files.mismatch(dir.resolve("1.gz"), dir.resolve("2.gz")), -1L);
    }
}
