package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.*;

/** T-141 E2c: Container#getInventory 는 그 칸 블록 엔티티의 살아 있는 인벤토리(Paper CraftInventory(tileEntity)) — 소유 스레드에서 곧바로 읽고 쓴다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BukkitContainerInventoryTest {

    FurnaceTestServer server;
    BlockPos pos;
    final String dim = "minecraft:overworld";

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        server.tick(3);
        pos = new BlockPos(11, -20, 11);
    }

    @AfterAll
    void stop() { server.close(); }

    @BeforeEach
    void load() { server.loadChunk(pos.chunkX(), pos.chunkZ()); }

    @Test
    void barrelInventoryIsLive() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:barrel", Map.of()));
            try {
                Barrel b = (Barrel) Compat.block(dim, pos).getState();
                Inventory inv = b.getInventory();
                assertEquals(27, inv.getSize());
                assertEquals(org.bukkit.event.inventory.InventoryType.BARREL, inv.getType());
                inv.setItem(4, new ItemStack(Material.DIAMOND, 3));
                assertEquals(new ItemStack(Material.DIAMOND, 3), inv.getItem(4), "같은 스레드에서 곧바로 읽힌다");
                // 살아 있는 인벤토리: 상태를 새로 떠도, Furnace API 로 읽어도 같은 내용
                assertEquals(new ItemStack(Material.DIAMOND, 3), ((Container) Compat.block(dim, pos).getState()).getInventory().getItem(4));
                assertEquals("minecraft:diamond", w.inventoryAtNow(pos).orElseThrow().get(4).join().type());
                assertInstanceOf(Barrel.class, inv.getHolder(), "주인은 그 칸의 새 블록 상태");
                assertThrows(UnsupportedFeature.class, b::getSnapshotInventory);
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    @Test
    void doubleChestHalfOnlyThroughBlockInventory() {
        BlockPos right = new BlockPos(pos.x() + 1, pos.y(), pos.z());
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:chest", Map.of("facing", "north", "type", "single")));
            try {
                Chest single = (Chest) Compat.block(dim, pos).getState();
                assertEquals(27, single.getInventory().getSize());
                w.block(pos).set(new BlockData("minecraft:chest", Map.of("facing", "north", "type", "left")));
                w.block(right).set(new BlockData("minecraft:chest", Map.of("facing", "north", "type", "right")));
                Chest half = (Chest) Compat.block(dim, pos).getState();
                assertThrows(UnsupportedFeature.class, half::getInventory, "두 칸을 합친 인벤토리는 아직 없다");
                assertEquals(27, half.getBlockInventory().getSize());
            } finally {
                w.block(right).set(BlockData.AIR);
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    @Test
    void unplacedContainerHasNoSnapshotInventoryYet() {
        Container c = (Container) Material.HOPPER.createBlockData().createBlockState();
        assertThrows(UnsupportedFeature.class, c::getInventory);
    }

    /** 양조대(Paper CraftBrewingStand): BrewTime·Fuel 은 사본 NBT 로 update 때 실리고, 인벤토리는 BrewerInventory(3 재료·4 연료). */
    @Test
    void brewingStandTimesAndInventory() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:brewing_stand", Map.of()));
            try {
                org.bukkit.block.BrewingStand b = (org.bukkit.block.BrewingStand) Compat.block(dim, pos).getState();
                assertEquals(0, b.getBrewingTime());
                assertEquals(0, b.getFuelLevel());
                assertEquals(400, b.getRecipeBrewTime());
                assertThrows(IllegalArgumentException.class, () -> b.setRecipeBrewTime(0));
                b.setBrewingTime(123);
                b.setFuelLevel(7);
                assertEquals(0, w.blockEntityNbtNow(pos).orElseThrow().getShort("BrewTime"), "update 전에는 월드 그대로");
                assertTrue(b.update());
                var nbt = w.blockEntityNbtNow(pos).orElseThrow();
                assertEquals(123, nbt.getShort("BrewTime"));
                assertEquals(7, nbt.getByte("Fuel"));
                var again = (org.bukkit.block.BrewingStand) Compat.block(dim, pos).getState();
                assertEquals(123, again.getBrewingTime());
                assertEquals(7, again.getFuelLevel());

                org.bukkit.inventory.BrewerInventory inv = again.getInventory();
                assertEquals(5, inv.getSize());
                assertEquals(org.bukkit.event.inventory.InventoryType.BREWING, inv.getType());
                inv.setIngredient(new ItemStack(Material.NETHER_WART));
                inv.setFuel(new ItemStack(Material.BLAZE_POWDER, 2));
                assertEquals("minecraft:nether_wart", w.inventoryAtNow(pos).orElseThrow().get(3).join().type());
                assertEquals(new ItemStack(Material.BLAZE_POWDER, 2), inv.getFuel());
                assertInstanceOf(org.bukkit.block.BrewingStand.class, inv.getHolder());
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** 다른 스레드에서 부르면 여전히 대기열 — region 이 바쁘면 곧바로 못 읽고(ThreadOwnershipException) 쓰기는 예약돼 겹쳐 보인다. */
    @Test
    void offThreadStaysQueued() throws Exception {
        Inventory inv = server.onRegion(pos, () -> {
            server.api().world(dim).block(pos).set(new BlockData("minecraft:barrel", Map.of()));
            Inventory i = ((Barrel) Compat.block(dim, pos).getState()).getInventory();
            i.setItem(0, new ItemStack(Material.STONE, 2));
            return i;
        });
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        var hold = server.api().world(dim).scheduler(pos).submit(() -> {
            started.countDown();
            try {
                release.await(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });
        assertTrue(started.await(30, java.util.concurrent.TimeUnit.SECONDS));
        try {
            assertThrows(org.domi.furnace.api.concurrent.ThreadOwnershipException.class, () -> inv.getItem(0), "시험 스레드에서 곧바로 돌면 안 된다");
            inv.setItem(1, new ItemStack(Material.DIRT, 1));
            assertEquals(new ItemStack(Material.DIRT, 1), inv.getItem(1), "예약된 쓰기를 겹쳐 본다");
        } finally {
            release.countDown();
        }
        server.await(hold);
        server.tick(1);
        server.onRegion(pos, () -> {
            assertEquals(new ItemStack(Material.DIRT, 1), inv.getItem(1), "예약된 쓰기가 실렸다");
            assertEquals(new ItemStack(Material.STONE, 2), inv.getItem(0));
            server.api().world(dim).block(pos).set(BlockData.AIR);
            return null;
        });
    }

    /** 짝 없는 반쪽 상자(붙여넣기 등): Paper ChestBlock.combine 이 단일로 보고 27칸. 부서진 뒤 쓰기는 IllegalStateException(Paper 는 고아 타일에 조용히). */
    @Test
    void loneHalfChestIsSingleAndBrokenContainerThrows() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:chest", Map.of("facing", "north", "type", "left")));
            try {
                Chest c = (Chest) Compat.block(dim, pos).getState();
                assertEquals(27, c.getInventory().getSize());
                // 짝이 있어도 facing 이 다르면 짝이 아니다
                w.block(new BlockPos(pos.x() + 1, pos.y(), pos.z())).set(new BlockData("minecraft:chest", Map.of("facing", "south", "type", "right")));
                assertEquals(27, ((Chest) Compat.block(dim, pos).getState()).getInventory().getSize());

                w.block(new BlockPos(pos.x() + 1, pos.y(), pos.z())).set(BlockData.AIR);
                w.block(pos).set(new BlockData("minecraft:barrel", Map.of()));
                Inventory inv = ((Barrel) Compat.block(dim, pos).getState()).getInventory();
                w.block(pos).set(BlockData.AIR);
                assertThrows(IllegalStateException.class, () -> inv.getItem(0));
            } finally {
                w.block(new BlockPos(pos.x() + 1, pos.y(), pos.z())).set(BlockData.AIR);
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }
}
