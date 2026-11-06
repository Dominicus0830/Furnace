// D2(불만 표 27·32·37번): 부트스트랩 게임 레지스트리 훅(늑대·그림 변종, 마법부여, JSON 항목, 태그 더하기)이 동기화 레지스트리에
// 데이터째 실리는지, 그리고 켜질 때 건 게임 규칙·마법부여 끄기·난이도 프로필이 플러그인이 꺼지면 정리되는지.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.DataPackManager;
import org.domi.furnace.server.data.DynamicRegistries;
import org.domi.furnace.server.loot.EnchantmentToggles;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.DifficultyModifiers;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.storage.LevelData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameRegistryApiTest {

    private static final String P = "org.domi.furnace.server.plugin.TestGameBoots$";

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private DataPackManager packs;

    @BeforeEach
    void setUp() {
        TestGameBoots.badKey = null;
        EnchantmentToggles.reset();
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (host != null) {
            host.pluginsImpl().disableAll();
            host.pluginsImpl().closeAll();
            host.close();
        }
        if (packs != null) packs.close();
        sched.close();
        EnchantmentToggles.reset();
        DifficultyModifiers.reset();
        TestServers.drainIoPools();
    }

    private PluginBootstrapper boot() throws IOException {
        Path dir = tmp.resolve("plugins");
        PluginBootstrapTest.jar(dir.resolve("game.jar"), "name: Game\nversion: 1\nmain: " + P + "Game\nbootstrapper: " + P + "GameBoot\n", Map.of());
        return PluginBootstrapper.bootstrap(dir);
    }

    private DataPackManager start(PluginBootstrapper boot) {
        LevelData level = LevelData.create("world", 1L, true, false, GameMode.SURVIVAL, false, Difficulty.EASY, List.of(), List.of());
        packs = DataPackManager.start(null, tmp.resolve("world"), level, RegistrySet.load("registries/1.21.1.txt"),
                true, List.of(), List.of(), boot.dataPackHooks());
        return packs;
    }

    @Test
    void bootstrapEntriesJoinSyncedRegistriesWithData() throws Exception {
        PluginBootstrapper boot = boot();
        start(boot);
        RegistrySet regs = RegistrySet.current();
        assertSame(packs.dynamicRegistries().registries(), regs, "읽은 레지스트리가 접속자에게 보낼 것으로 걸린다");

        // 늑대 변종: 경로 순서(moon 은 chestnut 과 pale 사이) — 바닐라 변종 번호가 뒤로 밀린다
        List<String> wolves = regs.entries("minecraft:wolf_variant");
        assertEquals(List.of("minecraft:ashen", "minecraft:black", "minecraft:chestnut", "game:moon", "minecraft:pale",
                "minecraft:rusty", "minecraft:snowy", "minecraft:spotted", "minecraft:striped", "minecraft:woods"), wolves);
        assertNull(regs.data("minecraft:wolf_variant", "minecraft:pale"), "바닐라 항목은 데이터 없이");
        var moon = regs.data("minecraft:wolf_variant", "game:moon");
        assertNotNull(moon, "플러그인 항목은 데이터째");
        assertEquals("game:entity/wolf/moon", moon.getString("wild_texture"));
        assertTrue(moon.tags().get("biomes") instanceof org.domi.furnace.server.net.io.nbt.NbtList);

        assertTrue(regs.entries("minecraft:painting_variant").contains("game:big"));
        assertEquals((byte) 4, regs.data("minecraft:painting_variant", "game:big").tags().get("width"));
        var zap = regs.data("minecraft:enchantment", "game:zap");
        assertEquals("#minecraft:enchantable/sword", zap.getString("supported_items"));
        Object desc = zap.tags().get("description");   // 글자만 있는 Text 는 JSON 문자열 꼴
        assertTrue("Zap".equals(desc) || (desc instanceof org.domi.furnace.server.net.io.nbt.NbtCompound c && "Zap".equals(c.getString("text"))), String.valueOf(desc));
        assertTrue(regs.entries("minecraft:banner_pattern").contains("game:stripe"));
        assertEquals("plugin:Game", packs.dynamicRegistries().source("minecraft:enchantment", "game:zap"));

        // 남의 namespace 키는 거절
        assertNotNull(TestGameBoots.badKey);
        assertFalse(wolves.contains("other:wolf"));

        // 태그 더하기: 데이터 팩 태그 끝에 붙는다
        int zapId = regs.indexOf("minecraft:enchantment", "game:zap");
        int[] table = packs.tags().ids("minecraft:enchantment", "minecraft:in_enchanting_table");
        assertNotNull(table);
        assertEquals(zapId, table[table.length - 1]);
        int bigId = regs.indexOf("minecraft:painting_variant", "game:big");
        assertTrue(java.util.Arrays.stream(packs.tags().ids("minecraft:painting_variant", "minecraft:placeable")).anyMatch(i -> i == bigId));

        // 자연 스폰 변종: plains 는 이제 game:moon(레지스트리 순서상 pale 보다 앞, pale 의 바이옴은 taiga)
        assertEquals(wolves.indexOf("game:moon"), org.domi.furnace.server.entity.Wolf.variantForBiome("minecraft:plains"));
        assertEquals(wolves.indexOf("minecraft:pale"), org.domi.furnace.server.entity.Wolf.variantForBiome("minecraft:taiga"));
        assertEquals(wolves.indexOf("minecraft:woods"), org.domi.furnace.server.entity.Wolf.variantForBiome("minecraft:forest"));
        assertEquals(wolves.indexOf("minecraft:pale"), org.domi.furnace.server.entity.Wolf.paleIndex());

        packs.close();
        packs = null;
        assertEquals(List.of("minecraft:ashen", "minecraft:black", "minecraft:chestnut", "minecraft:pale", "minecraft:rusty",
                "minecraft:snowy", "minecraft:spotted", "minecraft:striped", "minecraft:woods"),
                RegistrySet.current().entries("minecraft:wolf_variant"), "닫으면 내장 목록으로 돌아간다");
    }

    @Test
    void runtimeStateIsCleanedUpWhenPluginDisabled() throws Exception {
        PluginBootstrapper boot = boot();
        start(boot);
        core.dataPacks(packs);
        host = new PluginHost(core, boot.directory());
        host.pluginsImpl().loadAll(boot);
        host.pluginsImpl().enableAll();
        FurnacePlugin game = host.plugins().plugin("Game");
        assertTrue(game.isEnabled());

        // 게임 규칙: 이름에 namespace 가 붙고 /gamerule 아래에 생긴다
        var gamerule = core.commands().getDispatcher().getRoot().getChild("gamerule");
        assertNotNull(gamerule.getChild("game:pvpArena"));
        assertNotNull(gamerule.getChild("game:lives"));
        assertEquals(true, host.gameRules().get(TestGameBoots.pvp));
        host.gameRules().set(TestGameBoots.lives, 7);
        assertEquals(7, core.gameRules().getInt("game:lives"));
        assertTrue(host.gameRules().names().contains("game:lives"));
        assertThrows(IllegalArgumentException.class, () -> host.gameRules().registerBoolean(game, "other:x", false), "남의 namespace");
        assertThrows(IllegalArgumentException.class, () -> host.gameRules().registerBoolean(game, "pvpArena", false), "같은 이름 두 번");

        // 마법부여 끄기·난이도 프로필
        assertTrue(host.gameRegistries().isEnchantmentDisabled("minecraft:mending"));
        assertThrows(IllegalArgumentException.class, () -> host.gameRegistries().disableEnchantment(game, "minecraft:nope"));
        assertEquals("game:brutal", host.difficulty().profile().key());
        assertEquals(Difficulty.HARD, core.levelData().difficulty, "클라에는 base 가 보인다");
        assertEquals(2.0F, DifficultyModifiers.playerDamage());
        assertEquals(1.5F, DifficultyModifiers.local());
        assertEquals(1, boot.tagAdditions().size() / 2, "태그 더하기 두 건");

        host.pluginsImpl().disable(game);
        assertNull(gamerule.getChild("game:pvpArena"), "꺼지면 /gamerule 에서 빠진다");
        assertNull(core.gameRules().rule("game:lives"));
        assertEquals("7", core.gameRules().toStrings().get("game:lives"), "값은 level.dat 에 남는다");
        assertFalse(EnchantmentToggles.isDisabled("minecraft:mending"));
        assertNull(core.difficultyProfile());
        assertEquals(1.0F, DifficultyModifiers.playerDamage());
        assertEquals(Difficulty.HARD, core.levelData().difficulty, "난이도(base)는 그대로 둔다");
        assertEquals(List.of(), boot.tagAdditions(), "태그 몫은 다음 리로드부터 빠진다");
    }

    @Test
    void difficultyCommandDropsProfile() {
        var profile = new org.domi.furnace.api.world.DifficultyProfile("x:soft", org.domi.furnace.api.world.Difficulty.EASY, 0.5F, 1.0F);
        core.setDifficultyProfile("X", profile);
        assertEquals(Difficulty.EASY, core.levelData().difficulty);
        assertEquals(0.5F, DifficultyModifiers.playerDamage());
        assertFalse(core.clearDifficultyProfile("Y"), "남의 프로필은 내리지 않는다");
        core.setDifficulty(Difficulty.NORMAL, true);
        assertNull(core.difficultyProfile(), "/difficulty 는 바닐라 프로필로 돌린다");
        assertEquals(1.0F, DifficultyModifiers.playerDamage());
    }

    @Test
    void dynamicRegistriesFallBackToBuiltinWithoutData() {
        DynamicRegistries d = DynamicRegistries.load(RegistrySet.load("registries/1.21.1.txt"), null, List.of());
        for (String r : DynamicRegistries.DATA_DRIVEN) {
            assertEquals(RegistrySet.load("registries/1.21.1.txt").entries(r), d.registries().entries(r), r);
            assertFalse(d.registries().hasData(r));
        }
    }
}
