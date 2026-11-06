// 부트스트랩 단계(B3): bootstrapper 가 onLoad 전·서버 파사드 없이 의존 순서로 불리는지, createPlugin, 실패 격리·되돌리기,
// 수명주기 훅 등록 기간, 플러그인 jar 데이터 팩(월드 팩과 같은 켜기 규칙), 플러그인 레지스트리의 소유·고정·정리.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.domi.furnace.api.plugin.lifecycle.LifecycleEvents;
import org.domi.furnace.api.registry.PluginRegistries;
import org.domi.furnace.api.registry.PluginRegistry;
import org.domi.furnace.api.registry.RegistryListener;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.DataPackManager;
import org.domi.furnace.server.data.Pack;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.plugin.TestBootstraps.Ability;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.storage.LevelData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginBootstrapTest {

    private static final String P = "org.domi.furnace.server.plugin.TestBootstraps$";

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;

    @BeforeEach
    void setUp() {
        TestPlugins.LOG.clear();
        TestBootstraps.leakedLifecycle = null;
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
    }

    @AfterEach
    void tearDown() {
        if (host != null) {
            host.pluginsImpl().disableAll();
            host.pluginsImpl().closeAll();
            host.close();
        }
        sched.close();
    }

    /** 설명 파일 + 추가 항목(경로 → 내용)으로 jar 를 만든다. 클래스는 시험 클래스패스(부모 로더)에서 찾는다. */
    static void jar(Path file, String descriptor, Map<String, String> extra) throws IOException {
        Files.createDirectories(file.getParent());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new JarEntry("furnace-plugin.yml"));
            out.write(descriptor.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            for (var e : extra.entrySet()) {
                out.putNextEntry(new JarEntry(e.getKey()));
                out.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }

    private static Map<String, String> corePack() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("datapack/pack.mcmeta", "{\"pack\": {\"pack_format\": 48, \"description\": \"core pack\"}}");
        m.put("datapack/data/core/tags/item/shiny.json", "{\"values\": [\"minecraft:diamond\", \"minecraft:emerald\"]}");
        return m;
    }

    /** Core(레지스트리·데이터 팩) + Addon(depend Core, createPlugin). */
    private Path coreAndAddon() throws IOException {
        Path dir = tmp.resolve("plugins");
        jar(dir.resolve("addon.jar"), "name: Addon\nversion: 1\nmain: " + P + "Addon\nbootstrapper: " + P + "AddonBoot\ndepend: [Core]\n", Map.of());
        jar(dir.resolve("core.jar"), "name: Core\nversion: 1\nmain: " + P + "Core\nbootstrapper: " + P + "CoreBoot\n", corePack());
        return dir;
    }

    private PluginHost load(PluginBootstrapper boot) {
        host = new PluginHost(core, boot.directory());
        host.pluginsImpl().loadAll(boot);
        host.pluginsImpl().enableAll();
        return host;
    }

    @Test
    void bootstrapRunsFirstInDependencyOrderWithoutServer() throws Exception {
        PluginBootstrapper boot = PluginBootstrapper.bootstrap(coreAndAddon());
        assertEquals(List.of("core:bootstrap:no-server", "addon:bootstrap"), TestPlugins.LOG,
                "부트스트랩은 의존 순서로, 메인 인스턴스·onLoad 전에, 서버 파사드 없이 돈다");
        PluginRegistry<Ability> r = boot.registryStore().view("Core", "core").registry("core:abilities", Ability.class);
        assertEquals(List.of("core:dash", "addon:fly"), r.keys());
        assertEquals("Addon", r.entryOwner("addon:fly"));

        load(boot);
        assertEquals(List.of("core:bootstrap:no-server", "addon:bootstrap", "core:load:[core:dash, addon:fly]",
                "addon:load:from-bootstrap:Addon", "core:enable"), TestPlugins.LOG,
                "onLoad 는 모든 부트스트랩 뒤, createPlugin 이 만든 인스턴스를 쓴다");
        assertTrue(r.isFrozen());
        assertThrows(IllegalStateException.class, () -> r.register("core:late", new Ability("late")), "고정 뒤에는 넣을 수 없다");
    }

    @Test
    void disableCleansUpOwnedEntriesAndRegistries() throws Exception {
        load(PluginBootstrapper.bootstrap(coreAndAddon()));
        var pm = host.pluginsImpl();
        PluginRegistry<Ability> fromAddon = pm.plugin("Addon").registries().registry("core:abilities", Ability.class);
        TestPlugins.LOG.clear();

        pm.disable(pm.plugin("Addon"));
        assertEquals(List.of("core:dash"), fromAddon.keys(), "꺼진 플러그인이 넣은 항목은 고정된 레지스트리에서도 빠진다");
        assertEquals(List.of("core:removed:addon:fly"), TestPlugins.LOG, "빠진 항목은 알림으로 알린다");

        pm.disable(pm.plugin("Core"));
        assertTrue(fromAddon.isRemoved(), "만든 플러그인이 꺼지면 레지스트리가 사라진다");
        assertEquals(0, fromAddon.size());
        assertNull(pm.plugin("Addon").registries().registry("core:abilities", Ability.class));
        assertThrows(IllegalStateException.class, () -> fromAddon.register("addon:again", new Ability("x")));
    }

    @Test
    void failedBootstrapIsRolledBackAndDependentsSkipped() throws Exception {
        Path dir = coreAndAddon();
        jar(dir.resolve("bad.jar"), "name: Bad\nversion: 1\nmain: " + P + "Plain\nbootstrapper: " + P + "BadBoot\ndepend: [Core]\n", Map.of());
        jar(dir.resolve("needsbad.jar"), "name: NeedsBad\nversion: 1\nmain: " + P + "Plain\ndepend: [Bad]\n", Map.of());
        jar(dir.resolve("softbad.jar"), "name: SoftBad\nversion: 1\nmain: " + P + "Plain\nsoftdepend: [Bad]\n", Map.of());
        jar(dir.resolve("wrong.jar"), "name: Wrong\nversion: 1\nmain: " + P + "Plain\nbootstrapper: " + P + "NotABootstrap\n", Map.of());
        PluginBootstrapper boot = PluginBootstrapper.bootstrap(dir);
        assertEquals(List.of("Addon", "Core", "SoftBad"), boot.prepared().stream().map(p -> p.description().name()).sorted().toList(),
                "부트스트랩에 실패한 플러그인과 그것을 필수 의존하는 플러그인은 빠진다");
        PluginRegistries view = boot.registryStore().view("Core", "core");
        assertNull(view.registry("bad:things", String.class), "실패한 플러그인이 만든 레지스트리는 되돌린다");
        assertEquals(List.of("core:dash", "addon:fly"), view.registry("core:abilities", Ability.class).keys(), "남의 레지스트리에 넣은 항목도 되돌린다");
        assertEquals(List.of("Core"), boot.lifecycle().owners(LifecycleEvents.DATAPACK_DISCOVERY), "실패한 플러그인의 훅은 떼어진다");
        load(boot);
        assertNotNull(host.plugins().plugin("SoftBad"));
        assertNull(host.plugins().plugin("Bad"));
    }

    @Test
    void lifecycleRegistrationClosesAfterBootstrap() throws Exception {
        PluginBootstrapper.bootstrap(coreAndAddon());
        assertNotNull(TestBootstraps.leakedLifecycle);
        assertThrows(IllegalStateException.class,
                () -> TestBootstraps.leakedLifecycle.register(LifecycleEvents.DATAPACK_DISCOVERY, reg -> {}),
                "부트스트랩이 끝난 뒤의 훅 등록은 거절한다");
    }

    @Test
    void pluginDatapackBehavesLikeWorldPack() throws Exception {
        PluginBootstrapper boot = PluginBootstrapper.bootstrap(coreAndAddon());
        String id = "plugin/core/main";
        LevelData level = LevelData.create("world", 1L, true, false, GameMode.SURVIVAL, false, Difficulty.EASY, List.of(), List.of());
        try (DataPackManager packs = DataPackManager.start(null, tmp.resolve("world"), level, RegistrySet.load("registries/1.21.1.txt"),
                true, List.of(), List.of(), boot::discoverPacks)) {
            Pack pack = packs.repository().pack(id);
            assertNotNull(pack, "데이터 팩 찾기 훅이 알린 jar 안 폴더가 팩이 된다");
            assertEquals(Pack.Source.PLUGIN, pack.source());
            assertTrue(packs.repository().selectedIds().contains(id), "새로 보인 플러그인 팩은 월드 팩처럼 자동으로 켜진다");
            assertEquals(2, packs.tags().ids("minecraft:item", "core:shiny").length, "팩의 태그가 실린다");
            assertTrue(level.enabledPacks.contains(id), "level.dat DataPacks.Enabled 에 남는다");
        }
        // 서버 관리자가 끈 팩은(level.dat Disabled) 다시 켜지지 않는다 — 바닐라 월드 팩과 같다
        LevelData old = LevelData.create("world", 1L, true, false, GameMode.SURVIVAL, false, Difficulty.EASY, List.of(), List.of());
        old.enabledPacks = List.of();
        old.disabledPacks = List.of(id);
        try (DataPackManager packs = DataPackManager.start(null, tmp.resolve("world"), old, RegistrySet.load("registries/1.21.1.txt"),
                false, List.of(), List.of(), boot::discoverPacks)) {
            assertFalse(packs.repository().selectedIds().contains(id));
            assertNull(packs.tags().ids("minecraft:item", "core:shiny"));
        }
    }

    @Test
    void datapackHookStopsWhenPluginDisabled() throws Exception {
        PluginBootstrapper boot = PluginBootstrapper.bootstrap(coreAndAddon());
        assertEquals(List.of("plugin/core/main"), boot.discoverPacks().stream().map(p -> p.id()).toList());
        load(boot);
        host.pluginsImpl().disable(host.plugins().plugin("Core"));
        assertEquals(List.of(), boot.discoverPacks(), "꺼진 플러그인의 팩은 다음 찾기부터 보이지 않는다");
    }

    @Test
    void registryRulesNamespaceTypeAndOwnership() {
        PluginRegistryStore store = new PluginRegistryStore();
        PluginRegistries a = store.view("Alpha Plugin", "alpha_plugin");
        PluginRegistries b = store.view("Beta", "beta");
        assertThrows(IllegalArgumentException.class, () -> a.create("beta:stuff", String.class), "남의 namespace 로 만들 수 없다");
        assertThrows(IllegalArgumentException.class, () -> a.create("stuff", String.class), "namespace 생략 불가");
        assertThrows(IllegalArgumentException.class, () -> a.create("alpha_plugin:Bad", String.class), "대문자는 리소스 위치가 아니다");
        PluginRegistry<CharSequence> ra = a.create("alpha_plugin:words", CharSequence.class);
        assertThrows(IllegalArgumentException.class, () -> b.create("alpha_plugin:words", String.class));
        assertThrows(IllegalArgumentException.class, () -> b.registry("alpha_plugin:words", String.class), "값 타입이 하위 타입이면 거절");
        PluginRegistry<Object> rb = b.registry("alpha_plugin:words", Object.class);
        assertThrows(IllegalArgumentException.class, () -> rb.register("beta:n", 42), "실제 값 타입으로 검사한다");
        assertThrows(IllegalArgumentException.class, () -> rb.register("alpha_plugin:x", "x"), "항목 namespace 도 자기 것만");
        rb.register("beta:hello", "hello");
        assertThrows(IllegalArgumentException.class, () -> rb.register("beta:hello", "again"), "같은 키 두 번 불가");
        assertThrows(IllegalArgumentException.class, () -> ra.unregister("beta:hello"), "남의 항목은 뺄 수 없다");
        assertThrows(IllegalStateException.class, rb::freeze, "만든 플러그인만 고정한다");
        List<String> seen = new java.util.ArrayList<>();
        rb.addListener(new RegistryListener<>() {
            @Override public void added(String k, Object v) { seen.add("+" + k); }
            @Override public void removed(String k, Object v) { seen.add("-" + k); }
        });
        ra.register("alpha_plugin:a", "a");
        assertTrue(rb.unregister("beta:hello"));
        assertFalse(rb.unregister("beta:hello"));
        assertEquals(List.of("+alpha_plugin:a", "-beta:hello"), seen);
        store.removeOwner("Beta");
        ra.register("alpha_plugin:b", "b");
        assertEquals(List.of("+alpha_plugin:a", "-beta:hello"), seen, "꺼진 플러그인의 알림은 떨어진다");
        assertEquals(Map.of("alpha_plugin:a", "a", "alpha_plugin:b", "b"), ra.entries());
    }

    @Test
    void jarPrefixNormalizesAndRejectsEscapes() {
        assertEquals("", PluginBootstrapper.jarPrefix("/"));
        assertEquals("datapack/", PluginBootstrapper.jarPrefix("/datapack/"));
        assertEquals("packs/core/", PluginBootstrapper.jarPrefix("packs\\core"));
        assertThrows(IllegalArgumentException.class, () -> PluginBootstrapper.jarPrefix("packs/../x"));
        assertThrows(IllegalArgumentException.class, () -> PluginBootstrapper.jarPrefix("a//b"));
    }
}
