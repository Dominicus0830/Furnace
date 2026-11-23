package org.domi.furnace.compat.shimgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * T-264 P3 핸들 다리(설계 3.4·7장). 실행 때와 같은 로더(껍데기 jar → 호환 jar → …)에서:
 * <ul>
 *   <li>역할 표 {@code craft-roles.tsv}(껍데기 jar 안 — 이름 들어감, 커밋 안 함)의 모양과, b 칸 멤버가 모두 실제로 {@code @CraftBridge} 다리인지.</li>
 *   <li>안전 조건: 엔티티 종류마다(관측표 {@code entity.<종류>} 128개) 종류 표가 만드는 바닐라 클래스 shim 이 그 Craft 클래스의 Paper
 *       {@code getHandle()} 반환형에 대입 가능하고, 그 메서드가 던지는 껍데기가 아닌지.</li>
 *   <li>던지는 {@code getHandle()} 이 남은 손 Craft 클래스(상위에 손 접근자가 없는 뿌리 — 영역 10·P4 몫)가 아래 목록에서 늘지 않는지(줄면 목록을 줄인다).</li>
 * </ul>
 */
@Tag("full-shells")
class CraftBridgeTest {
    static final String ROLES = "META-INF/furnace/full-shells/craft-roles.tsv";
    static final String SHELL = "Lorg/domi/furnace/compat/nms/NmsShellMember;";
    static final String BRIDGE = "Lorg/domi/furnace/compat/nms/CraftBridge;";

    /** 던지는 getHandle() 을 스스로 선언한 Craft 클래스(뿌리 접근자 — 손 상위 접근자가 없어 다리가 없다). 줄어들기만 한다. */
    static final Set<String> THROWING_ROOTS = Set.of(
            "org/bukkit/craftbukkit/CraftRegionAccessor", "org/bukkit/craftbukkit/CraftServer", "org/bukkit/craftbukkit/block/CraftBlock",
            "org/bukkit/craftbukkit/block/banner/CraftPatternType", "org/bukkit/craftbukkit/boss/CraftBossBar", "org/bukkit/craftbukkit/entity/CraftEnemy",
            "org/bukkit/craftbukkit/generator/CraftChunkData", "org/bukkit/craftbukkit/generator/CraftLimitedRegion",
            "org/bukkit/craftbukkit/inventory/CraftInventoryView", "org/bukkit/craftbukkit/inventory/trim/CraftTrimMaterial",
            "org/bukkit/craftbukkit/inventory/trim/CraftTrimPattern", "org/bukkit/craftbukkit/potion/CraftPotionEffectType",
            "org/bukkit/craftbukkit/tag/CraftTag");

    private final Map<String, Map<String, Set<String>>> annotations = new HashMap<>();

    @Test
    void bridgesAreLiveAndEntityHandlesFit() throws Exception {
        List<URL> urls = new ArrayList<>();
        for (String p : System.getProperty("furnace.fullShells.classpath").split(File.pathSeparator)) if (!p.isBlank()) urls.add(Path.of(p).toUri().toURL());
        try (URLClassLoader cl = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            // 1) 역할 표
            Map<String, String> handleDesc = new HashMap<>();
            Map<String, Integer> roles = new TreeMap<>();
            List<String[]> bRows = new ArrayList<>();
            try (InputStream in = cl.getResourceAsStream(ROLES)) {
                assertNotNull(in, "역할 표가 껍데기 jar 에 없다: " + ROLES);
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                    if (line.isEmpty()) continue;
                    String[] f = line.split("\t", -1);
                    assertEquals(4, f.length, line);
                    assertTrue(Set.of("a", "b", "c", "d").contains(f[3]), line);
                    roles.merge(f[3], 1, Integer::sum);
                    if (f[3].equals("b")) bRows.add(f);
                    if (f[1].equals("getHandle") && f[2].startsWith("()")) handleDesc.put(f[0], f[2]);
                }
            }
            assertTrue(roles.getOrDefault("a", 0) > 1000 && roles.getOrDefault("d", 0) > 1000 && bRows.size() > 100, "역할 수 " + roles);
            for (String[] f : bRows) {
                Set<String> marks = marks(cl, f[0], f[1] + f[2]);
                assertTrue(marks.contains(BRIDGE) && !marks.contains(SHELL), "b 칸인데 다리가 아니다: " + String.join(" ", f) + " " + marks);
            }

            // 2) 엔티티 종류의 안전 조건
            Map<String, String> vanillaOf = new HashMap<>();
            try (InputStream in = cl.getResourceAsStream("META-INF/furnace/shim-kinds.txt")) {
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                    String[] f = line.split(" ");
                    if (f.length >= 3 && f[0].equals("E")) vanillaOf.put(f[1], f[2]);
                }
            }
            List<String> bad = new ArrayList<>();
            int checked = 0;
            for (String line : Files.readString(Path.of("src/test/resources/craft-identity-paper-1.21.1.tsv"), StandardCharsets.UTF_8).split("\n")) {
                if (!line.startsWith("entity.")) continue;
                String[] f = line.split("\t");
                String kind = f[0].substring("entity.".length());
                if (kind.contains(".")) continue;
                String vanilla = vanillaOf.get("minecraft:" + kind.toLowerCase(java.util.Locale.ROOT));
                Class<?> c = Class.forName(f[1], false, cl);
                // Paper 에서 이 클래스가 getHandle 을 다시 선언하지 않으면(CraftDonkey 등) 가장 가까운 상위의 것 — 계층은 P1 로 Paper 와 같다
                String desc = null;
                for (Class<?> k = c; k != null && desc == null; k = k.getSuperclass()) desc = handleDesc.get(k.getName().replace('.', '/'));
                if (vanilla == null || desc == null) {
                    bad.add(kind + ": kind table " + vanilla + ", Paper getHandle " + desc);
                    continue;
                }
                Class<?> want = Class.forName(Type.getReturnType(desc).getClassName(), false, cl);
                Method m = c.getMethod("getHandle");
                for (Method x : c.getMethods()) if (x.getName().equals("getHandle") && x.getParameterCount() == 0 && x.getReturnType() == want) m = x;
                if (m.getReturnType() != want) {
                    bad.add(kind + ": no getHandle returning " + want.getName() + " on " + c.getName());
                    continue;
                }
                Set<String> marks = marks(cl, m.getDeclaringClass().getName().replace('.', '/'), "getHandle" + desc);
                if (marks.contains(SHELL)) bad.add(kind + ": " + m.getDeclaringClass().getName() + "#getHandle is a throwing shell");
                if (!want.isAssignableFrom(Class.forName(vanilla, false, cl))) bad.add(kind + ": " + vanilla + " is not a " + want.getName());
                checked++;
            }
            assertEquals(List.of(), bad);
            assertEquals(128, checked, "관측표 엔티티 종류 수");

            // 3) 던지는 getHandle() 이 남은 Craft 클래스
            Set<String> merged = new HashSet<>();
            try (InputStream in = cl.getResourceAsStream("META-INF/furnace/full-shells/merged-classes.txt")) {
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) if (!line.isBlank()) merged.add(line.trim());
            }
            Set<String> throwing = new TreeSet<>();
            for (String craft : handleDesc.keySet()) {
                if (!merged.contains(craft)) continue;   // 통째 껍데기 클래스(Furnace 에 없는 Craft 클래스)는 객체가 없다 — 합친 손 클래스만 본다
                if (marks(cl, craft, "getHandle" + handleDesc.get(craft)).contains(SHELL)) throwing.add(craft);
            }
            Set<String> grew = new TreeSet<>(throwing);
            grew.removeAll(THROWING_ROOTS);
            assertEquals(Set.of(), grew, "던지는 getHandle 이 새로 생겼다(다리를 못 만든 자리)");
            Set<String> shrank = new TreeSet<>(THROWING_ROOTS);
            shrank.removeAll(throwing);
            assertEquals(Set.of(), shrank, "THROWING_ROOTS 에서 지울 것(이제 살아 있다)");
        }
    }

    /** 클래스 owner(로더가 먼저 찾는 바이트 — 껍데기 jar 가 앞)의 메서드 key 에 붙은 표지들. */
    private Set<String> marks(ClassLoader cl, String owner, String key) throws Exception {
        Map<String, Set<String>> byMethod = annotations.get(owner);
        if (byMethod == null) {
            Map<String, Set<String>> out = new HashMap<>();
            try (InputStream in = cl.getResourceAsStream(owner + ".class")) {
                if (in != null) {
                    new ClassReader(in.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            Set<String> s = out.computeIfAbsent(name + descriptor, k -> new HashSet<>());
                            return new MethodVisitor(Opcodes.ASM9) {
                                @Override
                                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                                    s.add(desc);
                                    return null;
                                }
                            };
                        }
                    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
            }
            annotations.put(owner, byMethod = out);
        }
        return byMethod.getOrDefault(key, Set.of());
    }
}
