// A06-02: 전리품 exploration_map(난파선 지도 상자·바다 폐허 상자의 묻힌 보물 지도)이 빈 지도가 아니라 구조물을 가리키는 지도를 준다.
package org.domi.furnace.server.map;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.loot.LootStack;
import org.domi.furnace.server.loot.LootWorld;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.structure.StructureLocator;
import org.domi.furnace.server.data.DataPackManager;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChestBlockEntity;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.storage.LevelData;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExplorationMapLootTest {
    @TempDir Path tmp;

    private ServerCore noiseCore() {
        return TestServers.core(tmp, new FurnaceSchedulerImpl(50_000_000L, 1),
            reg -> NoiseWorldSource.overworld(1234L, reg, RegistrySet.load("registries/1.21.1.txt")));
    }

    private static LootWorld lootWorld(ServerCore core) throws Exception {
        Method m = ServerCore.class.getDeclaredMethod("lootWorldFor", World.class);
        m.setAccessible(true);
        return (LootWorld) m.invoke(core, core.world());
    }

    @Test
    void lootExplorationMapPointsAtBuriedTreasure() throws Exception {
        ServerCore core = noiseCore();
        NoiseWorldSource gen = NoiseWorldSource.of(core.world().worldSource());
        StructureLocator.Found f = gen.structureLocator().findNearest(List.of("minecraft:buried_treasure"), 0, 64, 0, 50);
        assertNotNull(f, "시드 1234 원점 반경 50 안에 묻힌 보물이 있어야 시험이 뜻이 있다");
        LootWorld lw = lootWorld(core);
        // 바닐라 shipwreck_map: destination #on_treasure_maps, 반경 50, 배율 1, 장식 red_x
        int[] pos = lw.findNearestMapStructure("minecraft:on_treasure_maps", new double[] {0.5, 64, 0.5}, 50, true);
        assertNotNull(pos);
        assertArrayEquals(new int[] {f.x(), f.z()}, pos);
        LootStack map = lw.createExplorationMap(pos[0], pos[1], 1, "minecraft:red_x");
        assertNotNull(map);
        assertEquals("minecraft:filled_map", map.item());
        assertEquals(1, map.count());
        assertNotNull(map.get("minecraft:map_id"), "지도 id 가 있어야 한다");
        assertNotNull(map.get("minecraft:map_decorations"), "목표 표식(+) 이 있어야 한다");
        assertNull(map.get("minecraft:item_name"), "이름은 전리품 표의 set_name 이 붙인다");
    }

    /** 상자를 여는 길(unpackLoot)은 상자가 있는 월드의 생성기로 지도를 만든다 — 추가 월드 상자가 주 월드 구조물을 가리키지 않게. */
    @Test
    void chestMapPointsAtStructureOfItsOwnWorld() throws Exception {
        Path vanilla = Path.of("../tools/vanilla-data/server-1.21.1");
        Assumptions.assumeTrue(Files.isDirectory(vanilla.resolve("data/minecraft/loot_table")), "로컬 바닐라 데이터 없음");
        ServerCore core = noiseCore();
        var ld = LevelData.create("world", 1234L, true, false, GameMode.SURVIVAL, false, Difficulty.EASY, List.of("vanilla"), List.of());
        core.dataPacks(DataPackManager.start(vanilla, tmp, ld, RegistrySet.load("registries/1.21.1.txt"), true, List.of("vanilla"), List.of()));
        var reg = BlockRegistry.load("blocks/1.21.1.txt");
        World extra = new World(NoiseWorldSource.overworld(777L, reg, RegistrySet.load("registries/1.21.1.txt")),
                new ChunkWorkerPool(1), new FurnaceSchedulerImpl(50_000_000L, 1), 1, 16, 2, 2, reg.directBits(), 7, TestServers.ioPool());
        extra.worldDir(tmp.resolve("extra"));
        double[] o = {0.5, 100.5, 0.5};
        int[] mainPos = lootWorld(core).findNearestMapStructure("minecraft:on_treasure_maps", o, 50, false);
        Method m = ServerCore.class.getDeclaredMethod("lootWorldFor", World.class);
        m.setAccessible(true);
        int[] extraPos = ((LootWorld) m.invoke(core, extra)).findNearestMapStructure("minecraft:on_treasure_maps", o, 50, false);
        assertNotNull(mainPos);
        assertNotNull(extraPos);
        assertFalse(java.util.Arrays.equals(mainPos, extraPos), "두 시드의 가장 가까운 묻힌 보물이 달라야 시험이 뜻이 있다");
        for (var w : List.of(core.world(), extra)) {
            var chest = new ChestBlockEntity(0, 100, 0, new ItemStack[27]);
            chest.lootTable = "minecraft:chests/shipwreck_map";
            chest.lootSeed = 1L;   // 바닐라 정답지(chests-1.21.1.txt)에서 23번 칸이 지도인 시드
            core.unpackLoot(w, chest, null);
            Map<?, ?> deco = (Map<?, ?>) ItemTools.component(chest.items[23], "minecraft:map_decorations");
            Map<?, ?> plus = (Map<?, ?>) deco.get("+");
            int[] want = w == extra ? extraPos : mainPos;
            assertEquals((double) want[0], ((Number) plus.get("x")).doubleValue(), "x " + w);
            assertEquals((double) want[1], ((Number) plus.get("z")).doubleValue(), "z " + w);
        }
    }

    @Test
    void lootExplorationMapFindsNothingWhenStructuresDisabled() throws Exception {
        ServerCore core = noiseCore();
        var ld = core.getClass().getDeclaredField("levelData");
        ld.setAccessible(true);
        ((org.domi.furnace.server.world.storage.LevelData) ld.get(core)).generateFeatures = false;
        assertNull(lootWorld(core).findNearestMapStructure("minecraft:on_treasure_maps", new double[] {0.5, 64, 0.5}, 50, true));
    }
}
