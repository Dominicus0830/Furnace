package org.domi.furnace.tools.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.objectweb.asm.Opcodes.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.lang.invoke.LambdaMetafactory;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * 난독화 이름으로 ASM 이 직접 만든 작은 jar 를 대응표로 풀어, 풀린 클래스가 실제로 로드·실행되는지 본다.
 * 보는 것: 클래스·필드·메서드 이름, 하위 클래스 이름으로 부른 부모 멤버, 인터페이스 람다(invokedynamic), 서명 파일 제거, 데이터 파일 유지.
 */
class JarRemapperTest {
    static final String MAPPINGS = """
            game.Counter -> a:
                int count -> b
                int next() -> c
            game.SubCounter -> d:
                int twice() -> e
            game.Source -> f:
                int get() -> g
            game.Main -> h:
                int run() -> i
            """;

    /** a: 필드 b, 메서드 c(count 를 하나 올려 돌려줌). d extends a: e() 는 this.c() 를 두 번(owner = d) 부른다. f: 인터페이스 g()I. h: i() 는 람다 Source 와 SubCounter 를 쓴다. */
    static byte[][] classes() {
        ClassWriter a = new ClassWriter(0);
        a.visit(V21, ACC_PUBLIC, "a", null, "java/lang/Object", null);
        a.visitField(ACC_PUBLIC, "b", "I", null, null).visitEnd();
        MethodVisitor init = a.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(ALOAD, 0);
        init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();
        MethodVisitor c = a.visitMethod(ACC_PUBLIC, "c", "()I", null, null);
        c.visitCode();
        c.visitVarInsn(ALOAD, 0);
        c.visitVarInsn(ALOAD, 0);
        c.visitFieldInsn(GETFIELD, "a", "b", "I");
        c.visitInsn(ICONST_1);
        c.visitInsn(IADD);
        c.visitFieldInsn(PUTFIELD, "a", "b", "I");
        c.visitVarInsn(ALOAD, 0);
        c.visitFieldInsn(GETFIELD, "a", "b", "I");
        c.visitInsn(IRETURN);
        c.visitMaxs(3, 1);
        c.visitEnd();
        a.visitEnd();

        ClassWriter d = new ClassWriter(0);
        d.visit(V21, ACC_PUBLIC, "d", null, "a", null);
        MethodVisitor dInit = d.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        dInit.visitCode();
        dInit.visitVarInsn(ALOAD, 0);
        dInit.visitMethodInsn(INVOKESPECIAL, "a", "<init>", "()V", false);
        dInit.visitInsn(RETURN);
        dInit.visitMaxs(1, 1);
        dInit.visitEnd();
        MethodVisitor e = d.visitMethod(ACC_PUBLIC, "e", "()I", null, null);
        e.visitCode();
        e.visitVarInsn(ALOAD, 0);
        e.visitMethodInsn(INVOKEVIRTUAL, "d", "c", "()I", false);   // owner 가 d — 대응표에는 a 아래에만 있다
        e.visitInsn(POP);
        e.visitVarInsn(ALOAD, 0);
        e.visitFieldInsn(GETFIELD, "d", "b", "I");                  // 필드도 하위 클래스 이름으로 읽는다
        e.visitVarInsn(ALOAD, 0);
        e.visitMethodInsn(INVOKEVIRTUAL, "d", "c", "()I", false);
        e.visitInsn(IADD);
        e.visitInsn(IRETURN);
        e.visitMaxs(3, 1);
        e.visitEnd();
        d.visitEnd();

        ClassWriter f = new ClassWriter(0);
        f.visit(V21, ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT, "f", null, "java/lang/Object", null);
        f.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, "g", "()I", null, null).visitEnd();
        f.visitEnd();

        ClassWriter h = new ClassWriter(0);
        h.visit(V21, ACC_PUBLIC, "h", null, "java/lang/Object", null);
        MethodVisitor lam = h.visitMethod(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, "lambda$i$0", "()I", null, null);
        lam.visitCode();
        lam.visitIntInsn(BIPUSH, 40);
        lam.visitInsn(IRETURN);
        lam.visitMaxs(1, 0);
        lam.visitEnd();
        MethodVisitor i = h.visitMethod(ACC_PUBLIC | ACC_STATIC, "i", "()I", null, null);
        i.visitCode();
        Handle bsm = new Handle(H_INVOKESTATIC, Type.getInternalName(LambdaMetafactory.class), "metafactory",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
                        + "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
        i.visitInvokeDynamicInsn("g", "()Lf;", bsm, Type.getMethodType("()I"), new Handle(H_INVOKESTATIC, "h", "lambda$i$0", "()I", false),
                Type.getMethodType("()I"));
        i.visitMethodInsn(INVOKEINTERFACE, "f", "g", "()I", true);
        i.visitTypeInsn(NEW, "d");
        i.visitInsn(DUP);
        i.visitMethodInsn(INVOKESPECIAL, "d", "<init>", "()V", false);
        i.visitMethodInsn(INVOKEVIRTUAL, "d", "e", "()I", false);
        i.visitInsn(IADD);
        i.visitInsn(IRETURN);
        i.visitMaxs(3, 0);
        i.visitEnd();
        h.visitEnd();
        return new byte[][] {a.toByteArray(), d.toByteArray(), f.toByteArray(), h.toByteArray()};
    }

    static Path writeJar(Path dir) throws IOException {
        Path jar = dir.resolve("obf.jar");
        String[] names = {"a", "d", "f", "h"};
        byte[][] data = classes();
        try (OutputStream os = Files.newOutputStream(jar); JarOutputStream jo = new JarOutputStream(os)) {
            for (int k = 0; k < names.length; k++) {
                jo.putNextEntry(new JarEntry(names[k] + ".class"));
                jo.write(data[k]);
                jo.closeEntry();
            }
            jo.putNextEntry(new JarEntry("META-INF/MOJANGCS.SF"));
            jo.write("sig".getBytes());
            jo.closeEntry();
            jo.putNextEntry(new JarEntry("META-INF/MOJANGCS.RSA"));
            jo.write("sig".getBytes());
            jo.closeEntry();
            jo.putNextEntry(new JarEntry("data/minecraft/tags/block/x.json"));
            jo.write("{\"values\":[]}".getBytes());
            jo.closeEntry();
        }
        return jar;
    }

    @Test
    void remappedJarLoadsAndRunsWithMojangNames(@TempDir Path dir) throws Exception {
        Path in = writeJar(dir);
        MojangMappings maps = MojangMappings.parse(new StringReader(MAPPINGS));
        ClassHierarchy h;
        try (ZipFile zf = new ZipFile(in.toFile())) {
            h = ClassHierarchy.read(zf);
        }
        Path out = dir.resolve("mojmap.jar");
        int n = new JarRemapper(maps, h).remapJar(in, out);
        assertEquals(4, n);

        try (JarFile jf = new JarFile(out.toFile())) {
            assertNotNull(jf.getEntry("game/Counter.class"));
            assertNotNull(jf.getEntry("game/Main.class"));
            assertNotNull(jf.getEntry("data/minecraft/tags/block/x.json"), "데이터 파일은 그대로");
            assertFalse(jf.stream().anyMatch(e -> e.getName().endsWith(".SF") || e.getName().endsWith(".RSA")), "서명 파일은 뺀다");
            assertEquals("1.0", jf.getManifest().getMainAttributes().getValue("Manifest-Version"));
        }
        try (URLClassLoader cl = new URLClassLoader(new URL[] {out.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> counter = cl.loadClass("game.Counter");
            assertNotNull(counter.getField("count"));
            assertNotNull(counter.getMethod("next"));
            Class<?> source = cl.loadClass("game.Source");
            assertNotNull(source.getMethod("get"));
            Class<?> sub = cl.loadClass("game.SubCounter");
            assertEquals(counter, sub.getSuperclass());
            // run() = 람다 40 + SubCounter.twice(): next() → 1, count(1) + next() → 2 = 3
            Object result = cl.loadClass("game.Main").getMethod("run").invoke(null);
            assertEquals(43, result);
        }
    }

    @Test
    void unmappedMembersKeepTheirNames(@TempDir Path dir) throws Exception {
        MojangMappings maps = MojangMappings.parse(new StringReader(MAPPINGS));
        ClassHierarchy h = ClassHierarchy.of(java.util.Map.of("d", new String[] {"a"}, "a", new String[] {"java/lang/Object"}));
        JarRemapper r = new JarRemapper(maps, h);
        assertEquals("next", r.remapper().mapMethodName("d", "c", "()I"));
        assertEquals("c", r.remapper().mapMethodName("d", "c", "(I)I"), "서술자가 다르면 다른 메서드");
        assertEquals("count", r.remapper().mapFieldName("d", "b", "I"));
        assertEquals("toString", r.remapper().mapMethodName("d", "toString", "()Ljava/lang/String;"));
        assertEquals("c", r.remapper().mapMethodName("[Ld;", "c", "()I"), "배열 owner 는 건드리지 않는다");
        assertTrue(JarRemapper.isSignature("META-INF/X.SF"));
        assertFalse(JarRemapper.isSignature("META-INF/services/a.b"));
    }
}
