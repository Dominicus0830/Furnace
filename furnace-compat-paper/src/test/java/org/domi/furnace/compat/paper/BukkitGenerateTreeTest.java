package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import org.bukkit.BlockChangeDelegate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.*;

/**
 * T-141 E5: TreeType·BlockChangeDelegate·World#generateTree. 기대 동작은 Paper 1.21.1 CraftWorld·CraftRegionAccessor#generateTree:
 * TreeType → TreeFeatures 표, 주어진 Random 을 그대로 feature 난수로(RandomSourceWrapper), delegate 판은 월드를 바꾸지 않고
 * 기록한 칸마다 setBlockData, CHORUS_PLANT 는 바닥 검사 없이 generatePlant 뒤 true.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BukkitGenerateTreeTest {

    FurnaceTestServer server;
    World world;
    BlockPos origin;
    int next;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        // region 가장자리를 피한 청크(나무는 3×3 청크를 쓴다), 슈퍼플랫 땅(-61..-60) 위 공중
        origin = new BlockPos(4 * 16, -20, 4 * 16);
        for (int dx = -1; dx <= 2; dx++)
            for (int dz = -1; dz <= 2; dz++) server.loadChunk(origin.chunkX() + dx, origin.chunkZ() + dz);
        world = Compat.world(server.overworld());
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T owner(Supplier<T> task) { return server.onRegion(origin, task); }

    /** 시험마다 다른 자리(서로 6칸 이상 떨어진, 같은 두 청크 안). 바로 아래는 흙. */
    Location fresh() {
        int i = next++;
        Location at = new Location(world, origin.x() + 3 + (i % 3) * 9, origin.y(), origin.z() + 3 + (i / 3) * 9);
        owner(() -> { at.clone().add(0, -1, 0).getBlock().setType(Material.DIRT); return null; });
        return at;
    }

    Material type(Location l, int dx, int dy, int dz) { return owner(() -> l.clone().add(dx, dy, dz).getBlock().getType()); }

    @Test
    void ownerThreadOnly() {
        Location at = fresh();
        assertThrows(ThreadOwnershipException.class, () -> world.generateTree(at, TreeType.TREE));
    }

    @Test
    void everyTreeTypeMapsToAnExistingFeature() {
        Location at = fresh();
        BlockPos p = new BlockPos(at.getBlockX(), at.getBlockY(), at.getBlockZ());
        var handle = ((org.bukkit.craftbukkit.CraftWorld) world).furnaceHandle();
        for (TreeType t : TreeType.values()) {
            String id = org.bukkit.craftbukkit.CraftWorld.treeFeature(t);
            if (t == TreeType.CHORUS_PLANT) {
                assertNull(id);
                continue;
            }
            // 반영하지 않는 기록 판이라 월드를 바꾸지 않는다. 모르는 id 면 IllegalArgumentException
            assertDoesNotThrow(() -> owner(() -> handle.placeFeatureNow(id, p, new Random(1L), false)), t + " → " + id);
        }
        assertEquals("minecraft:oak", org.bukkit.craftbukkit.CraftWorld.treeFeature(TreeType.CUSTOM), "모르는 종류는 OAK(Paper default)");
        assertEquals("minecraft:crimson_fungus_planted", org.bukkit.craftbukkit.CraftWorld.treeFeature(TreeType.CRIMSON_FUNGUS), "보통 월드는 planted");
        assertEquals(Material.AIR, type(at, 0, 0, 0));
    }

    @Test
    void seededRandomGrowsTheSameTreeAndAdvancesTheRandom() {
        Location a = fresh();
        Random r1 = new Random(2024L);
        assertTrue(owner(() -> world.generateTree(a, r1, TreeType.BIRCH)));
        assertEquals(Material.BIRCH_LOG, type(a, 0, 0, 0));
        List<Material> first = column(a);
        long after1 = r1.nextLong();

        // 같은 자리를 비우고 같은 시드로 다시 키우면 같은 나무, Random 도 같은 만큼 나아간다(잎 거리는 자리마다 다를 수 있어 같은 자리에서)
        owner(() -> {
            for (int y = 0; y <= 12; y++)
                for (int x = -3; x <= 3; x++)
                    for (int z = -3; z <= 3; z++) a.clone().add(x, y, z).getBlock().setType(Material.AIR);
            return null;
        });
        Random r2 = new Random(2024L);
        assertTrue(owner(() -> world.generateTree(a, r2, TreeType.BIRCH)));
        assertEquals(first, column(a));
        assertEquals(after1, r2.nextLong());
        assertNotEquals(new Random(2024L).nextLong(), after1, "feature 가 플러그인 Random 에서 뽑았다");
    }

    List<Material> column(Location l) {
        List<Material> out = new ArrayList<>();
        for (int y = 0; y <= 12; y++) out.add(type(l, 0, y, 0));
        return out;
    }

    @Test
    void delegateGetsEveryWriteAndWorldStaysUntouched() {
        Location at = fresh();
        List<String> calls = new ArrayList<>();
        BlockChangeDelegate d = new BlockChangeDelegate() {
            @Override public boolean setBlockData(int x, int y, int z, BlockData data) { calls.add(x + "," + y + "," + z + " " + data.getAsString()); return true; }
            @Override public BlockData getBlockData(int x, int y, int z) { throw new AssertionError("Paper 는 부르지 않는다"); }
            @Override public int getHeight() { throw new AssertionError("Paper 는 부르지 않는다"); }
            @Override public boolean isEmpty(int x, int y, int z) { throw new AssertionError("Paper 는 부르지 않는다"); }
        };
        assertTrue(owner(() -> world.generateTree(at, TreeType.TREE, d)));
        assertFalse(calls.isEmpty());
        assertEquals(at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ() + " minecraft:oak_log[axis=y]", calls.get(0), "원점 통나무부터");
        assertTrue(calls.stream().anyMatch(c -> c.contains("minecraft:oak_leaves")), calls.toString());
        assertEquals(calls.size(), calls.stream().map(c -> c.substring(0, c.indexOf(' '))).distinct().count(), "칸마다 한 번");
        assertEquals(Material.AIR, type(at, 0, 0, 0), "월드는 그대로");
        assertEquals(Material.AIR, type(at, 0, 3, 0));
    }

    @Test
    void failedGrowthReturnsFalseAndDelegateIsNotCalled() {
        Location at = fresh();
        owner(() -> { at.clone().add(0, -1, 0).getBlock().setType(Material.AIR); return null; });   // 거대 버섯은 흙·균사체 위만
        List<String> calls = new ArrayList<>();
        BlockChangeDelegate d = new BlockChangeDelegate() {
            @Override public boolean setBlockData(int x, int y, int z, BlockData data) { calls.add(data.getAsString()); return true; }
            @Override public BlockData getBlockData(int x, int y, int z) { return null; }
            @Override public int getHeight() { return 0; }
            @Override public boolean isEmpty(int x, int y, int z) { return true; }
        };
        assertFalse(owner(() -> world.generateTree(at, TreeType.RED_MUSHROOM, d)));
        assertEquals(List.of(), calls);
        assertFalse(owner(() -> world.generateTree(at, new Random(3L), TreeType.BROWN_MUSHROOM)));
        assertEquals(Material.AIR, type(at, 0, 0, 0));
    }

    @Test
    void chorusPlantGrowsWithoutEndStoneAndReturnsTrue() {
        Location at = fresh();
        assertTrue(owner(() -> world.generateTree(at, new Random(5L), TreeType.CHORUS_PLANT)));
        assertEquals(Material.CHORUS_PLANT, type(at, 0, 0, 0));
    }
}
