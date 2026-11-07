package org.domi.furnace.compat.realworld;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.KitPlugin;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * H4: 실제 Bukkit·Paper API 로 컴파일된 jar 가 호환 계층 위에서 어디까지 가는지 — 후퇴를 막는 기준표 시험.
 * 지금은 대부분 못 돈다(그게 측정 결과다). 좋아지면 {@code compatReport} 로 기준표를 새로 쓰고, 나빠지면 여기서 막힌다.
 */
class RealWorldCompatTest {

    @TempDir
    Path tmp;

    @Test
    void classFileScannerReadsTheSymbolsACompiledClassNeeds() throws Exception {
        byte[] bytes;
        try (var in = KitPlugin.class.getResourceAsStream("KitPlugin.class")) {
            bytes = in.readAllBytes();
        }
        ClassRefs r = ClassRefs.parse(bytes);
        assertEquals("org/domi/furnace/compat/paper/fixtures/KitPlugin", r.name);
        assertEquals("org/bukkit/plugin/java/JavaPlugin", r.superName);
        assertTrue(r.interfaces.contains("org/bukkit/event/Listener"), r.interfaces.toString());
        assertTrue(r.classes.contains("org/bukkit/inventory/ItemStack"));
        assertTrue(r.members.stream().anyMatch(m -> m.owner().equals("org/bukkit/Material") && m.name().equals("DIAMOND_SWORD") && m.field()),
                "필드 참조(Material.DIAMOND_SWORD)를 읽는다");
        assertTrue(r.members.stream().anyMatch(m -> m.name().equals("<init>") && m.owner().equals("org/bukkit/inventory/ItemStack")),
                "생성자 참조를 읽는다");
    }

    @Test
    void apiIndexResolvesLikeTheJvm() {
        ApiIndex idx = new ApiIndex(getClass().getClassLoader());
        // 상위 인터페이스에서 상속한 메서드(Player ← HumanEntity ← Entity/CommandSender …)
        assertTrue(idx.hasMember(new ClassRefs.Member("org/bukkit/entity/Player", "getName", "()Ljava/lang/String;", false)));
        // 반환형까지 맞아야 한다 — 다르면 런타임에 NoSuchMethodError
        assertFalse(idx.hasMember(new ClassRefs.Member("org/bukkit/entity/Player", "getName", "()I", false)));
        assertFalse(idx.hasMember(new ClassRefs.Member("org/bukkit/entity/Player", "noSuchThing", "()V", false)));
        assertTrue(idx.hasMember(new ClassRefs.Member("org/bukkit/Material", "DIAMOND_SWORD", "Lorg/bukkit/Material;", true)));
        assertTrue(idx.hasMember(new ClassRefs.Member("org/bukkit/inventory/ItemStack", "<init>", "(Lorg/bukkit/Material;I)V", false)));
        assertFalse(idx.hasMember(new ClassRefs.Member("org/bukkit/inventory/ItemStack", "<init>", "(Lorg/bukkit/Material;ZZ)V", false)));
        // Object 메서드는 인터페이스 참조라도 해석된다
        assertTrue(idx.hasMember(new ClassRefs.Member("org/bukkit/entity/Player", "hashCode", "()I", false)));
        assertNotNull(idx.find("org/bukkit/Bukkit"));
        assertEquals(null, idx.find("org/bukkit/NoSuchClass"));
    }

    @Test
    void kindOfClassifiesWhatTheServerMustProvide() {
        assertEquals(Coverage.Kind.BUKKIT, Coverage.kindOf("org/bukkit/Bukkit"));
        assertEquals(Coverage.Kind.CRAFTBUKKIT, Coverage.kindOf("org/bukkit/craftbukkit/v1_21_R1/CraftServer"));
        assertEquals(Coverage.Kind.PAPER, Coverage.kindOf("io/papermc/paper/threadedregions/scheduler/GlobalRegionScheduler"));
        assertEquals(Coverage.Kind.SPIGOT, Coverage.kindOf("net/md_5/bungee/api/chat/BaseComponent"));
        assertEquals(Coverage.Kind.NMS, Coverage.kindOf("net/minecraft/server/level/ServerPlayer"));
        assertEquals(Coverage.Kind.ADVENTURE, Coverage.kindOf("net/kyori/adventure/text/Component"));
        assertEquals(Coverage.Kind.SERVER_LIB, Coverage.kindOf("com/google/common/collect/Lists"));
        assertEquals(null, Coverage.kindOf("java/lang/String"));
        assertEquals(null, Coverage.kindOf("org/jetbrains/annotations/NotNull"));
        assertEquals(null, Coverage.kindOf("some/other/Library"), "서버가 주지 않는 라이브러리는 판단 대상이 아니다");
    }

    @Test
    void realJarCoverageNeverFallsBelowTheBaseline() throws Exception {
        Properties baseline = Baseline.load();
        List<Path> jars = RealJars.list();
        List<Coverage.JarResult> results = Coverage.scan(jars, new ApiIndex(getClass().getClassLoader()));
        assertFalse(results.isEmpty());
        StringBuilder regress = new StringBuilder();
        StringBuilder improved = new StringBuilder();
        for (Coverage.JarResult r : results) {
            String key = "missing." + r.jar;
            String base = baseline.getProperty(key);
            assertNotNull(base, "기준표에 없는 jar — compatReport 를 돌려 기준표를 다시 쓴다: " + r.jar);
            int allowed = Integer.parseInt(base.trim());
            if (r.apiMissing() > allowed) regress.append(r.jar).append(": 없는 심볼 ").append(r.apiMissing()).append(" > 기준 ").append(allowed).append('\n');
            else if (r.apiMissing() < allowed) improved.append(r.jar).append(": ").append(allowed).append(" → ").append(r.apiMissing()).append('\n');
        }
        if (!improved.isEmpty()) System.out.println("[H4] 좋아졌다 — compatReport 로 기준표를 갱신하세요:\n" + improved);
        assertTrue(regress.isEmpty(), "호환 계층이 후퇴했다(실제 jar 가 요구하는 API 가 빠졌다):\n" + regress);
    }

    @Test
    void realPluginsAreReportedCleanlyAndNeverTakeTheServerDown() throws Exception {
        Properties baseline = Baseline.load();
        List<Path> pluginJars = RealJars.list().stream().filter(p -> RealJars.pluginName(p) != null).toList();
        assertFalse(pluginJars.isEmpty(), "실제 플러그인 jar(plugin.yml)가 하나도 없다");
        int n = 0;
        for (Path jar : pluginJars) {
            var result = LivePluginProbe.run(jar, RealJars.pluginName(jar), 5, tmp.resolve("live" + n++));
            String key = "live." + jar.getFileName();
            String base = baseline.getProperty(key);
            assertNotNull(base, "기준표에 없는 플러그인 — compatReport 를 돌려 기준표를 다시 쓴다: " + jar.getFileName());
            LivePluginProbe.Status floor = LivePluginProbe.Status.valueOf(base.trim());
            assertTrue(result.status().ordinal() >= floor.ordinal(),
                    jar.getFileName() + " 기동 상태가 후퇴했다: " + result.status() + " < 기준 " + floor + " (처음 오류 " + result.firstError() + ")");
            if (result.status() != LivePluginProbe.Status.ENABLED) {
                assertNotNull(result.firstError(), jar.getFileName() + " 이 켜지지 못했는데 로그에 원인이 없다 — 실패가 조용히 삼켜졌다");
            }
        }
    }

    @Test
    void aFailingRealPluginDoesNotStopAnotherPluginFromEnabling() throws Exception {
        Path real = RealJars.find("helper");
        Path recorder = Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: 1.0
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
        RecorderPlugin.reset();
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).directory(tmp.resolve("iso")).pluginJar(real).pluginJar(recorder).start()) {
            assertNotNull(server.plugin("Recorder"));
            assertTrue(server.plugin("Recorder").isEnabled(), "실패한 실제 플러그인 옆에서도 다른 플러그인은 켜져야 한다");
            server.tick(3);
        }
        assertTrue(Files.exists(tmp.resolve("iso")));
    }
}
