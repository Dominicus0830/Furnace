package org.domi.furnace.compat.realworld;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** T-234: 인기 가중치 순위와 Class.forName 감지 대상 목록 — 손으로 만든 작은 플러그인 jar 로. */
class NmsGapRankingTest {

    @TempDir
    Path tmp;

    /** NMS 클래스 하나를 가리키는 플러그인 한 개 + 문자열 상수로 클래스 이름을 찾는 코드. */
    private static byte[] plugin(String missingClass, String detectName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "rank/P", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitTypeInsn(Opcodes.CHECKCAST, missingClass);
        mv.visitInsn(Opcodes.POP);
        mv.visitLdcInsn(detectName);
        mv.visitInsn(Opcodes.POP);
        mv.visitLdcInsn("org.bukkit.craftbukkit.v1_20_R3.CraftServer");   // 다른 판 이름 — 센 것에서 뺀다
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private Path jar(String file, byte[] cls) throws IOException {
        Path p = tmp.resolve(file);
        Manifest m = new Manifest();
        m.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        m.getMainAttributes().putValue("paperweight-mappings-namespace", "mojang");
        try (OutputStream o = Files.newOutputStream(p); JarOutputStream jo = new JarOutputStream(o, m)) {
            jo.putNextEntry(new JarEntry("rank/P.class"));
            jo.write(cls);
            jo.closeEntry();
        }
        return p;
    }

    @Test
    void weightsFollowDownloadsAndDetectionNamesAreCounted() throws IOException {
        Path a = jar("a.jar", plugin("net/minecraft/NoSuchRankClassA", "net.minecraft.world.entity.animal.Pig"));
        Path b = jar("b.jar", plugin("net/minecraft/NoSuchRankClassA", "net.minecraft.world.entity.animal.Pig"));
        Path c = jar("c.jar", plugin("net/minecraft/NoSuchRankClassB", "net.minecraft.world.entity.Entity"));
        Path manifest = tmp.resolve("sample-manifest.tsv");
        Files.writeString(manifest, "slug\ttitle\tdownloads\tjar\tsha1\tversion\n"
                + "a\tA\t990\ta.jar\tx\t1\nb\tB\t90\tb.jar\tx\t1\nc\tC\t9990\tc.jar\tx\t1\n", StandardCharsets.UTF_8);

        Map<String, Long> d = NmsGapRanking.downloads(manifest);
        assertEquals(990L, d.get("a.jar"));
        assertEquals(3.0, NmsGapRanking.weight(d, "a.jar"), 1e-9, "log10(990+10)");
        assertEquals(1.0, NmsGapRanking.weight(d, "nope.jar"), 1e-9, "매니페스트에 없으면 1");
        assertTrue(NmsGapRanking.downloads(tmp.resolve("missing.tsv")).isEmpty());

        List<Path> jars = List.of(a, b, c);
        Map<String, Set<String>> targets = NmsGapRanking.stringTargets(jars);
        assertEquals(Set.of("a.jar", "b.jar"), targets.get("net.minecraft.world.entity.animal.Pig"));
        assertEquals(Set.of("c.jar"), targets.get("net.minecraft.world.entity.Entity"));
        assertTrue(targets.keySet().stream().noneMatch(k -> k.contains("v1_20_R3")), "다른 판 이름은 뺀다: " + targets.keySet());

        String md = NmsGapRanking.render(NmsShimAudit.audit(jars), d, targets, "2026-01-01");
        // 점수: A = 3.0 + 2.0(b: log10(100)) = 5.0, B = log10(10000) = 4.0 → A 가 먼저
        int iA = md.indexOf("net.minecraft.NoSuchRankClassA"), iB = md.indexOf("net.minecraft.NoSuchRankClassB");
        assertTrue(iA > 0 && iB > iA, md);
        assertTrue(md.contains("| 1 | 5.0 | 2 |"), md);
        assertTrue(md.contains("| 2 | `net.minecraft.world.entity.animal.Pig` | 아니오 |") || md.contains("`net.minecraft.world.entity.animal.Pig` | 아니오"), md);
        assertTrue(md.contains("`net.minecraft.world.entity.Entity` | 예"), "Entity 는 shim 에 있다: " + md);
    }
}
