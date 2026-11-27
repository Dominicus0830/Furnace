package org.domi.furnace.compat.nms.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.domi.furnace.server.port.PortGuards;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * 이식 다리 소유 검사 규칙(설계 5장 check=port): 연결 몸통은 호환 쪽 소유 검사를 넣지 않는 대신, 서버 생성 구현(server.port.gen)의 모든 공개
 * 메서드가 처음 부르는 것이 PortGuards 여야 한다 — 바이트코드로 본다(생성기를 고쳐 검사를 빠뜨리면 여기서 멈춘다).
 */
class PortImplOwnerFirstTest {

    static final String GUARDS = PortGuards.class.getName().replace('.', '/');

    @Test
    void everyPublicBridgeMethodCallsPortGuardsFirst() throws Exception {
        Path root = Path.of(PortGuards.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String prefix = "org/domi/furnace/server/port/gen/";
        List<byte[]> classes = new ArrayList<>();
        if (Files.isDirectory(root)) {
            try (Stream<Path> s = Files.list(root.resolve(prefix))) {
                for (Path f : s.filter(p -> p.toString().endsWith(".class") && !p.getFileName().toString().contains("$")).toList()) classes.add(Files.readAllBytes(f));
            }
        } else {
            try (JarFile jar = new JarFile(root.toFile())) {
                for (JarEntry e : (Iterable<JarEntry>) jar.stream()::iterator) {
                    String n = e.getName();
                    if (!n.startsWith(prefix) || !n.endsWith(".class") || n.contains("$") || n.indexOf('/', prefix.length()) >= 0) continue;
                    try (InputStream in = jar.getInputStream(e)) {
                        classes.add(in.readAllBytes());
                    }
                }
            }
        }
        List<String> bad = new ArrayList<>();
        int methods = 0;
        for (byte[] bytes : classes) {
            ClassNode cn = new ClassNode();
            new ClassReader(bytes).accept(cn, 0);
            for (MethodNode m : cn.methods) {
                if ((m.access & Opcodes.ACC_PUBLIC) == 0 || (m.access & (Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0 || m.name.equals("<init>")) continue;
                methods++;
                AbstractInsnNode first = null;
                for (AbstractInsnNode i : m.instructions) {
                    if (i instanceof MethodInsnNode || i instanceof InvokeDynamicInsnNode) {
                        first = i;
                        break;
                    }
                }
                if (!(first instanceof MethodInsnNode mi) || mi.getOpcode() != Opcodes.INVOKESTATIC || !mi.owner.equals(GUARDS)) bad.add(cn.name + "#" + m.name + m.desc);
            }
        }
        assertTrue(methods >= 4, "too few generated bridge methods found (" + methods + ") under " + root + "!" + prefix);
        assertEquals(List.of(), bad, "generated bridge methods whose first call is not PortGuards");
    }
}
