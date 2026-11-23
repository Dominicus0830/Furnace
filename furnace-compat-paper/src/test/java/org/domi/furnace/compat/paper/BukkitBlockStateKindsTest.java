package org.domi.furnace.compat.paper;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.*;

/**
 * T-141 E2: 블록 엔티티가 있는 블록의 상태는 Paper 와 같은 형(Chest·Sign·TileState 등, Craft* 클래스)이다.
 * 놓이지 않은 상태(BlockData#createBlockState)는 Paper 처럼 월드·블록·청크를 물으면 IllegalStateException 이고 update 는 참.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BukkitBlockStateKindsTest {

    FurnaceTestServer server;
    BlockPos pos;
    String dim = "minecraft:overworld";

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        server.tick(3);
        pos = new BlockPos(5, -20, 5);
        server.loadChunk(pos.chunkX(), pos.chunkZ());
    }

    @AfterAll
    void stop() { server.close(); }

    private BlockState placedState(String type, Map<String, String> props) {
        return server.onRegion(pos, () -> {
            server.api().world(dim).block(pos).set(new BlockData(type, props));
            Block b = Compat.block(dim, pos);
            BlockState s = b.getState();
            server.api().world(dim).block(pos).set(BlockData.AIR);
            return s;
        });
    }

    /** T-264 P2c: 재료마다 블록 상태 클래스가 Paper 관측표(blockstate.*)와 같다 — 블록 엔티티 없는 블록은 CraftBlockState 그대로. */
    @Test
    void stateClassesMatchPaperObservation() throws Exception {
        java.util.List<String> wrong = new java.util.ArrayList<>();
        int matched = 0;
        try (var in = BukkitBlockStateKindsTest.class.getResourceAsStream("/craft-identity-paper-1.21.1.tsv")) {
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                if (!line.startsWith("blockstate.")) continue;
                String[] t = line.split("\t");
                String key = t[0].substring("blockstate.".length());
                if (key.contains(".") || t[1].startsWith("!")) continue;
                Material m = Material.matchMaterial(key);
                if (m == null || !m.isBlock()) {
                    wrong.add(key + ": no block material");
                    continue;
                }
                String got = m.createBlockData().createBlockState().getClass().getName();
                if (t[1].equals(got)) matched++;
                else wrong.add(key + ": paper=" + t[1] + " furnace=" + got);
            }
        }
        assertEquals(java.util.List.of(), wrong);
        assertTrue(matched > 1000, "matched " + matched);
    }

    @Test
    void placedStatesHavePaperTypes() {
        BlockState chest = placedState("minecraft:chest", Map.of());
        assertInstanceOf(org.bukkit.block.Chest.class, chest);
        assertInstanceOf(org.bukkit.block.Container.class, chest);
        assertInstanceOf(org.bukkit.block.TileState.class, chest);
        assertInstanceOf(org.bukkit.loot.Lootable.class, chest);
        assertEquals("org.bukkit.craftbukkit.block.CraftChest", chest.getClass().getName());
        assertEquals(Material.CHEST, chest.getType());
        assertTrue(chest.isPlaced());
        assertEquals(pos.x(), chest.getX());

        assertInstanceOf(org.bukkit.block.Chest.class, placedState("minecraft:trapped_chest", Map.of()));
        assertInstanceOf(org.bukkit.block.Sign.class, placedState("minecraft:oak_wall_sign", Map.of()));
        BlockState hanging = placedState("minecraft:oak_hanging_sign", Map.of());
        assertInstanceOf(org.bukkit.block.HangingSign.class, hanging);
        assertInstanceOf(org.bukkit.block.Sign.class, hanging, "HangingSign 은 Sign 이다");
        assertInstanceOf(org.bukkit.block.CreatureSpawner.class, placedState("minecraft:spawner", Map.of()));
        assertInstanceOf(org.bukkit.block.BlastFurnace.class, placedState("minecraft:blast_furnace", Map.of()));
        assertInstanceOf(org.bukkit.block.Furnace.class, placedState("minecraft:smoker", Map.of()));
        assertInstanceOf(org.bukkit.block.ShulkerBox.class, placedState("minecraft:blue_shulker_box", Map.of()));
        assertInstanceOf(org.bukkit.block.Skull.class, placedState("minecraft:zombie_wall_head", Map.of()));
        assertInstanceOf(org.bukkit.block.EnchantingTable.class, placedState("minecraft:enchanting_table", Map.of()));
        assertInstanceOf(org.bukkit.block.Beehive.class, placedState("minecraft:bee_nest", Map.of()));
        assertInstanceOf(org.bukkit.block.EntityBlockStorage.class, placedState("minecraft:beehive", Map.of()));

        BlockState stone = placedState("minecraft:stone", Map.of());
        assertFalse(stone instanceof org.bukkit.block.TileState, "블록 엔티티가 없으면 일반 상태");
        assertEquals(org.bukkit.craftbukkit.block.CraftBlockState.class, stone.getClass());
        BlockState brewing = placedState("minecraft:brewing_stand", Map.of());
        assertInstanceOf(org.bukkit.block.BrewingStand.class, brewing);
        assertInstanceOf(org.bukkit.block.Container.class, brewing);
    }

    @Test
    void updateWritesBackThroughTypedState() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:barrel", Map.of()));
            BlockState s = Compat.block(dim, pos).getState();
            assertInstanceOf(org.bukkit.block.Barrel.class, s);
            s.setBlockData(CompatBlockData.create("minecraft:barrel[facing=down,open=false]"));
            assertTrue(s.update());
            assertEquals("down", w.block(pos).data().properties().get("facing"));
            w.block(pos).set(new BlockData("minecraft:stone", Map.of()));
            assertFalse(s.update(), "종류가 바뀌었으면 force 없이 쓰지 않는다");
            w.block(pos).set(BlockData.AIR);
            return null;
        });
    }

    @Test
    void unplacedStateFollowsPaper() {
        BlockState s = Material.CHEST.createBlockData().createBlockState();
        assertInstanceOf(org.bukkit.block.Chest.class, s);
        assertFalse(s.isPlaced());
        assertEquals(0, s.getX());
        assertEquals(0, s.getY());
        assertEquals(0, s.getZ());
        assertNull(s.getLocation().getWorld());
        var e = assertThrows(IllegalStateException.class, s::getWorld);
        assertEquals("The blockState must be placed to call this method", e.getMessage());
        assertThrows(IllegalStateException.class, s::getBlock);
        assertThrows(IllegalStateException.class, s::getChunk);
        assertTrue(s.update(true), "놓이지 않은 상태의 update 는 참");
        assertEquals(Material.CHEST, s.getType());

        BlockState plain = Material.STONE.createBlockData().createBlockState();
        assertEquals(org.bukkit.craftbukkit.block.CraftBlockState.class, plain.getClass());
        assertNotEquals(plain, s);
        assertEquals(plain, Material.STONE.createBlockData().createBlockState(), "같은 클래스·자리·데이터면 같다");
        assertInstanceOf(io.papermc.paper.block.MovingPiston.class, Material.MOVING_PISTON.createBlockData().createBlockState());
    }

    /** 생성기 표가 서버 표(바닐라 BlockEntityType)의 모든 종류를 다룬다. */
    @Test
    void tableCoversEveryBlockEntityType() throws Exception {
        var g = server.api().gameRegistries();
        var serverTypes = new java.util.TreeSet<String>();
        for (String b : g.builtIn("minecraft:block")) {
            String t = g.blockEntityType(b);
            if (t != null) serverTypes.add(t);
        }
        var tableTypes = new java.util.TreeSet<String>();
        for (String line : Files.readAllLines(Path.of("../tools/compat/bukkit-block-state-hierarchy-" + GameVersion.RESOURCE_TAG + ".txt"))) {
            if (line.startsWith("T ")) tableTypes.add("minecraft:" + line.split(" ")[1]);
        }
        assertEquals(serverTypes, tableTypes);
    }

    /** 같은 자리·같은 데이터라도 형이 다르면 같지 않다(Paper equals 는 getClass 를 본다). */
    @Test
    void equalsComparesClass() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:chest", Map.of()));
            try {
                BlockState typed = Compat.block(dim, pos).getState();
                BlockState plain = BlockStateSnapshots.of(dim, pos, w.block(pos).data());
                assertEquals(((CompatBlockData) typed.getBlockData()).furnace(), ((CompatBlockData) plain.getBlockData()).furnace());
                assertNotEquals(typed, plain);
                assertNotEquals(plain, typed);
                assertEquals(typed, Compat.block(dim, pos).getState());
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** 로드 안 된 칸의 setBlockData 는 여전히 던진다(같은 상태 비교로 실패를 삼키지 않는다). */
    @Test
    void setBlockDataOnUnloadedCellThrows() {
        BlockPos far = new BlockPos(1_000_000, -20, 1_000_000);
        server.onRegion(far, () -> {
            var b = Compat.block(dim, far);
            assertThrows(IllegalStateException.class, () -> b.setBlockData(Material.STONE.createBlockData()));
            return null;
        });
    }
}
