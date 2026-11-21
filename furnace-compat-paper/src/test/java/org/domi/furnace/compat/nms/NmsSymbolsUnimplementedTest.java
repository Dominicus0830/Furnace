package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * T-229: shim 생성기가 "구현 없는 추상 메서드"에 붙인 던지는 몸통은 NmsSymbols 가 없음으로 본다(설계 4.3) — 그 클래스·하위로 부르는 플러그인
 * 호출에는 링크 때 덫이 걸린다. 인터페이스로 부르는 호출은 바닐라처럼 해석된다(다른 구현이 있을 수 있다).
 */
class NmsSymbolsUnimplementedTest {

    @TempDir
    Path tmp;

    static byte[] cls(int access, String name, String sup, String[] itfs, String... methods) {
        ClassWriter w = new ClassWriter(0);
        w.visit(Opcodes.V21, access, name, null, sup, itfs);
        for (String m : methods) {
            String[] p = m.split(" ");
            int acc = (access & Opcodes.ACC_INTERFACE) != 0 ? Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT : Opcodes.ACC_PUBLIC;
            w.visitMethod(acc, p[0], p[1], null, null).visitEnd();
        }
        w.visitEnd();
        return w.toByteArray();
    }

    void put(String name, byte[] b) throws Exception {
        Path p = tmp.resolve(name + ".class");
        Files.createDirectories(p.getParent());
        Files.write(p, b);
    }

    @Test
    void throwingBodiesFromTheIndexCountAsMissing() throws Exception {
        put("net/minecraft/u/Named", cls(Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, "net/minecraft/u/Named", "java/lang/Object", null,
                "getName ()Ljava/lang/String;"));
        put("net/minecraft/u/Thing", cls(Opcodes.ACC_PUBLIC, "net/minecraft/u/Thing", "java/lang/Object", new String[] {"net/minecraft/u/Named"},
                "getName ()Ljava/lang/String;", "getX ()D"));
        put("net/minecraft/u/Sub", cls(Opcodes.ACC_PUBLIC, "net/minecraft/u/Sub", "net/minecraft/u/Thing", null));
        Path idx = tmp.resolve(NmsSymbols.UNIMPLEMENTED_INDEX);
        Files.createDirectories(idx.getParent());
        Files.writeString(idx, "net/minecraft/u/Thing getName ()Ljava/lang/String;\n", StandardCharsets.UTF_8);
        try (URLClassLoader l = new URLClassLoader(new URL[] {tmp.toUri().toURL()}, null)) {
            NmsSymbols s = new NmsSymbols(l);
            assertEquals(Set.of("net/minecraft/u/Thing getName ()Ljava/lang/String;"), s.unimplemented());
            assertFalse(s.hasMethod("net/minecraft/u/Thing", "getName", "()Ljava/lang/String;"), "생성기의 던지는 몸통 = 없음");
            assertFalse(s.hasMethod("net/minecraft/u/Sub", "getName", "()Ljava/lang/String;"), "하위 클래스로 불러도 같은 몸통에 닿는다");
            assertTrue(s.hasMethod("net/minecraft/u/Named", "getName", "()Ljava/lang/String;"), "인터페이스로 부르면 해석은 된다");
            assertTrue(s.hasMethod("net/minecraft/u/Thing", "getX", "()D"));
        }
    }

    @Test
    void theRuntimeIndexIsTheBuildOutput() {
        // 실제 빌드: 생성기가 색인을 늘 쓴다(비어 있어도). 색인의 줄은 모두 실제로 그 클래스가 선언한 메서드다
        assertTrue(NmsSymbols.class.getClassLoader().getResource(NmsSymbols.UNIMPLEMENTED_INDEX) != null, "생성기 색인이 자원에 있어야 한다");
        for (String line : NmsSymbols.RUNTIME.unimplemented()) {
            String[] p = line.split(" ");
            assertTrue(NmsSymbols.RUNTIME.declaresMethod(p[0], p[1], p[2]), line);
            assertFalse(NmsSymbols.RUNTIME.hasMethod(p[0], p[1], p[2]), line);
        }
    }
}
