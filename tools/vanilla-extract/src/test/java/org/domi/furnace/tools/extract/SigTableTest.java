package org.domi.furnace.tools.extract;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class SigTableTest {
    /** 선언 머리가 다양한 클래스 하나. */
    private static byte[] sample(String name) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, name, "<T:Ljava/lang/Object;>Ljava/lang/Object;", "java/lang/Object", new String[]{"java/lang/Runnable", "java/lang/Cloneable"});
        cw.visitInnerClass(name, "a/Outer", "Inner", Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC);
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "COUNT", "I", null, 1).visitEnd();
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC, "this$0", "La/Outer;", null, null).visitEnd();
        MethodVisitor run = cw.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        run.visitCode();
        run.visitInsn(Opcodes.RETURN);
        run.visitMaxs(0, 1);
        run.visitEnd();
        cw.visitMethod(Opcodes.ACC_PUBLIC, "get", "()Ljava/lang/Object;", "()TT;", null).visitEnd();
        cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "lambda$run$0", "()V", null, null).visitEnd();
        cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null).visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    void classHeaderAndMembersAreCollected() {
        SigTable t = SigTable.empty("x");
        t.add(sample("a/Outer$Inner"));
        SigTable.Cls c = t.classes().get("a/Outer$Inner");
        assertEquals("java/lang/Object", c.superName());
        assertEquals(List.of("java/lang/Runnable", "java/lang/Cloneable"), c.interfaces());
        assertEquals("<T:Ljava/lang/Object;>Ljava/lang/Object;", c.signature());
        assertEquals(Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC, c.innerAccess());
        assertEquals(c.innerAccess(), c.effectiveAccess());
        // 합성 멤버와 <clinit> 는 뺀다. 필드가 메서드보다 앞, 이름순.
        List<String> names = t.members("a/Outer$Inner").stream().map(m -> m.kind() + ":" + m.name()).toList();
        assertEquals(List.of("F:COUNT", "M:get", "M:run"), names);
        assertEquals("()TT;", t.members("a/Outer$Inner").get(1).signature());
    }

    @Test
    void methodBodiesAreNeverRead() {
        // 몸통에 잘못된 명령어(0xFF)가 있는 클래스: 통째로 읽으면 실패하지만 서명표는 몸통을 건너뛰어 성공해야 한다(법적 경계).
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "a/Bad", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "m", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(0xFF);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();
        byte[] bad = cw.toByteArray();
        assertThrows(RuntimeException.class, () -> new ClassReader(bad).accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {};
            }
        }, 0));
        SigTable t = SigTable.empty("x");
        t.add(bad);
        assertEquals("m", t.members("a/Bad").get(0).name());
    }

    @Test
    void writeReadRoundTripAndDeterministic(@TempDir Path dir) throws IOException {
        SigTable t = SigTable.empty("vanilla-test");
        t.add(sample("a/Outer$Inner"));
        t.add(sample("a/B"));
        Path f1 = dir.resolve("1.tsv.gz"), f2 = dir.resolve("2.tsv.gz");
        t.write(f1);
        SigTable back = SigTable.read(f1);
        assertEquals("vanilla-test", back.label());
        assertEquals(t.classes(), back.classes());
        assertEquals(t.members("a/B"), back.members("a/B"));
        assertEquals(t.memberCount(), back.memberCount());
        back.write(f2);
        assertArrayEquals(Files.readAllBytes(f1), Files.readAllBytes(f2), "같은 표는 같은 바이트");
    }

    @Test
    void jarPrefixFilterSkipsMultiRelease(@TempDir Path dir) throws IOException {
        Path jar = dir.resolve("x.jar");
        try (JarOutputStream jo = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String n : new String[]{"net/minecraft/A", "com/other/B", "META-INF/versions/21/net/minecraft/C"}) {
                jo.putNextEntry(new JarEntry(n + ".class"));
                jo.write(sample(n));
                jo.closeEntry();
            }
        }
        assertEquals(List.of("net/minecraft/A"), List.copyOf(SigTable.fromJar(jar, List.of("net/minecraft/"), "x").classes().keySet()));
        assertEquals(2, SigTable.fromJar(jar, List.of(), "x").classes().size());
    }

    @Test
    void badHeaderIsRejected() {
        assertThrows(IOException.class, () -> SigTable.read(new BufferedReader(new StringReader("hello\n"))));
        assertThrows(IOException.class, () -> SigTable.read(new BufferedReader(new StringReader("#furnace-sig-table\t9\tx\n"))));
        assertFalse(SigTableCommand.snapshotJarValue("<metadata/>").isPresent());
    }

    @Test
    void snapshotJarValueSkipsClassifiers() {
        String xml = """
                <snapshotVersions>
                  <snapshotVersion><classifier>sources</classifier><extension>jar</extension><value>1.0-S-9</value></snapshotVersion>
                  <snapshotVersion><extension>pom</extension><value>1.0-S-10</value></snapshotVersion>
                  <snapshotVersion><extension>jar</extension><value>1.0-S-8</value></snapshotVersion>
                </snapshotVersions>""";
        assertEquals("1.0-S-8", SigTableCommand.snapshotJarValue(xml).orElseThrow());
        assertTrue(SigTable.MAGIC.startsWith("#"));
    }
}
