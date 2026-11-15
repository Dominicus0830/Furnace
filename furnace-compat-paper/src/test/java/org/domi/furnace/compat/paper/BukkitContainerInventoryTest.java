package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.domi.furnace.api.nbt.NbtCompound;
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
                // 사본 인벤토리는 상태를 뜰 때의 칸(그 뒤 살아 있는 쪽에 넣은 것은 없다), 주인은 Paper 스냅숏처럼 없다
                Inventory snap = b.getSnapshotInventory();
                assertEquals(27, snap.getSize());
                assertEquals(org.bukkit.event.inventory.InventoryType.BARREL, snap.getType());
                assertNull(snap.getItem(4));
                assertNull(snap.getHolder());
                assertEquals(pos.x(), inv.getLocation().getBlockX(), "살아 있는 인벤토리 위치는 그 칸");
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** 큰 상자: getInventory 는 54칸(왼쪽 = type=right 상자, 바닐라 CompoundContainer 첫째), getBlockInventory 는 이 칸의 27칸. */
    @Test
    void doubleChestInventoryJoinsHalves() {
        BlockPos right = new BlockPos(pos.x() + 1, pos.y(), pos.z());
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:chest", Map.of("facing", "north", "type", "single")));
            try {
                Chest single = (Chest) Compat.block(dim, pos).getState();
                assertEquals(27, single.getInventory().getSize());
                w.block(pos).set(new BlockData("minecraft:chest", Map.of("facing", "north", "type", "left")));
                w.block(right).set(new BlockData("minecraft:chest", Map.of("facing", "north", "type", "right")));
                Chest leftTyped = (Chest) Compat.block(dim, pos).getState();
                Chest rightTyped = (Chest) Compat.block(dim, right).getState();
                assertEquals(27, leftTyped.getBlockInventory().getSize());
                Inventory big = leftTyped.getInventory();
                assertEquals(54, big.getSize());
                var dbl = assertInstanceOf(org.bukkit.inventory.DoubleChestInventory.class, big);
                rightTyped.getBlockInventory().setItem(0, new ItemStack(Material.DIAMOND, 1));
                leftTyped.getBlockInventory().setItem(0, new ItemStack(Material.EMERALD, 2));
                assertEquals(new ItemStack(Material.DIAMOND, 1), big.getItem(0), "칸 0..26 = type=right 상자");
                assertEquals(new ItemStack(Material.EMERALD, 2), big.getItem(27), "칸 27..53 = type=left 상자");
                big.setItem(30, new ItemStack(Material.GOLD_INGOT, 5));
                assertEquals(new ItemStack(Material.GOLD_INGOT, 5), leftTyped.getBlockInventory().getItem(3));
                assertEquals("minecraft:gold_ingot", w.inventoryAtNow(pos).orElseThrow().get(3).join().type(), "살아 있는 블록 엔티티에 들어갔다");
                assertEquals(54, rightTyped.getInventory().getSize(), "어느 반쪽에서 열어도 같은 합친 인벤토리");
                assertEquals(new ItemStack(Material.GOLD_INGOT, 5), rightTyped.getInventory().getItem(30));
                var holder = assertInstanceOf(org.bukkit.block.DoubleChest.class, dbl.getHolder());
                assertInstanceOf(Chest.class, holder.getLeftSide());
                assertEquals(right.x(), ((Chest) holder.getLeftSide()).getX());
                assertEquals(pos.x() + 0.5, dbl.getLocation().getX(), 1e-9, "두 칸의 가운데");
                big.clear();
                assertNull(leftTyped.getBlockInventory().getItem(0));
                assertNull(rightTyped.getBlockInventory().getItem(0));
            } finally {
                w.block(right).set(BlockData.AIR);
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** 놓이지 않은 용기 상태: getInventory 는 사본 인벤토리(Paper CraftContainer — 놓이지 않았으면 getSnapshotInventory). */
    @Test
    void unplacedContainerUsesSnapshotInventory() {
        Container c = (Container) Material.HOPPER.createBlockData().createBlockState();
        Inventory inv = c.getInventory();
        assertEquals(5, inv.getSize());
        assertEquals(org.bukkit.event.inventory.InventoryType.HOPPER, inv.getType());
        inv.setItem(2, new ItemStack(Material.STONE, 4));
        assertSame(inv, c.getSnapshotInventory());
        assertEquals(new ItemStack(Material.STONE, 4), c.getSnapshotInventory().getItem(2));
        assertNull(inv.getLocation());
    }

    /** 사본 인벤토리를 고치고 update 하면 블록 엔티티 Items 로 실린다(컴포넌트 포함). update 전에는 블록 그대로. */
    @Test
    void snapshotInventoryAppliesOnUpdate() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:barrel", Map.of()));
            try {
                Barrel b = (Barrel) Compat.block(dim, pos).getState();
                b.getInventory().setItem(1, new ItemStack(Material.DIAMOND, 1));   // 살아 있는 쪽
                Barrel s = (Barrel) Compat.block(dim, pos).getState();
                ItemStack named = new ItemStack(Material.STICK, 2);
                var meta = named.getItemMeta();
                meta.setDisplayName("Key");
                named.setItemMeta(meta);
                s.getSnapshotInventory().setItem(5, named);
                assertEquals(new ItemStack(Material.DIAMOND, 1), s.getSnapshotInventory().getItem(1), "사본은 뜰 때의 칸");
                assertTrue(w.inventoryAtNow(pos).orElseThrow().get(5).join().isEmpty(), "update 전에는 블록 그대로");
                assertTrue(s.update());
                var live = ((Barrel) Compat.block(dim, pos).getState()).getInventory();
                assertEquals(named, live.getItem(5), "이름 컴포넌트까지 실렸다");
                assertEquals(new ItemStack(Material.DIAMOND, 1), live.getItem(1));
                // 다 비우고 update 하면 블록도 빈다
                Barrel e = (Barrel) Compat.block(dim, pos).getState();
                e.getSnapshotInventory().clear();
                assertTrue(e.update());
                assertTrue(((Barrel) Compat.block(dim, pos).getState()).getInventory().isEmpty());
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** 화로의 사본 인벤토리는 Paper 처럼 FurnaceInventory. */
    @Test
    void snapshotInventoryOfFurnaceIsTyped() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:furnace", Map.of("facing", "north", "lit", "false")));
            try {
                var f = (org.bukkit.block.Furnace) Compat.block(dim, pos).getState();
                assertInstanceOf(org.bukkit.inventory.FurnaceInventory.class, f.getSnapshotInventory(), "Paper CraftInventoryFurnace");
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
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

    /** 화로(Paper CraftFurnace): 시간 셋은 사본 NBT, setBurnTime 은 lit 도 맞춘다. 인벤토리는 FurnaceInventory(0 굽는 것·1 연료·2 결과, isFuel·canSmelt). */
    @Test
    void furnaceTimesAndInventory() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:furnace", Map.of("facing", "north", "lit", "false")));
            try {
                org.bukkit.block.Furnace f = (org.bukkit.block.Furnace) Compat.block(dim, pos).getState();
                assertEquals(0, f.getBurnTime());
                assertEquals(1.0, f.getCookSpeedMultiplier());
                assertThrows(IllegalArgumentException.class, () -> f.setCookSpeedMultiplier(-1));
                f.setBurnTime((short) 300);
                f.setCookTime((short) 50);
                f.setCookTimeTotal(200);
                assertEquals("true", ((CompatBlockData) f.getBlockData()).furnace().properties().get("lit"), "SPIGOT-844: 불붙이기");
                assertTrue(f.update());
                var nbt = w.blockEntityNbtNow(pos).orElseThrow();
                assertEquals(50, nbt.getShort("CookTime"));
                assertEquals(200, nbt.getShort("CookTimeTotal"));
                assertEquals("true", w.block(pos).data().properties().get("lit"));
                var again = (org.bukkit.block.Furnace) Compat.block(dim, pos).getState();
                assertEquals(50, again.getCookTime());
                assertFalse(again.hasRecipeUsedCount(org.bukkit.NamespacedKey.minecraft("iron_ingot_from_smelting_raw_iron")));

                org.bukkit.inventory.FurnaceInventory inv = again.getInventory();
                assertEquals(org.bukkit.event.inventory.InventoryType.FURNACE, inv.getType());
                inv.setSmelting(new ItemStack(Material.RAW_IRON));
                inv.setFuel(new ItemStack(Material.COAL, 3));
                assertEquals("minecraft:raw_iron", w.inventoryAtNow(pos).orElseThrow().get(0).join().type());
                assertEquals(new ItemStack(Material.COAL, 3), inv.getFuel());
                assertTrue(inv.isFuel(new ItemStack(Material.COAL)));
                // 태그 연료(판자 등)는 서버 FuelRegistry 가 데이터 팩 태그(ItemContext)를 본다 — 시험 서버엔 그 태그가 걸리지 않아 여기선 재지 않는다
                assertFalse(inv.isFuel(new ItemStack(Material.STONE)));
                assertFalse(inv.isFuel(null));
                // canSmelt 는 레시피 표를 본다 — 시험 서버는 데이터 팩 없이 떠 레시피 표가 없어(플러그인 레시피도 못 건다) 여기선 비었을 때만 잰다
                assertFalse(inv.canSmelt(null));
                assertFalse(inv.canSmelt(new ItemStack(Material.AIR)));
                assertInstanceOf(org.bukkit.block.Furnace.class, inv.getHolder());
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:smoker", Map.of()));
            try {
                var inv = ((org.bukkit.block.Smoker) Compat.block(dim, pos).getState()).getInventory();
                assertEquals(org.bukkit.event.inventory.InventoryType.SMOKER, inv.getType());
                assertInstanceOf(org.bukkit.block.Furnace.class, inv.getHolder());
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** 용광로 한 판(#379 검토 시험): BurnTime NBT, 다시 뜬 세 시간, setBurnTime(0)→lit=false, 배율 검사, RecipesUsed 읽기, 결과 칸. */
    @Test
    void blastFurnaceStateRoundTrip() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:blast_furnace", Map.of("facing", "north", "lit", "false")));
            try {
                var f = (org.bukkit.block.Furnace) Compat.block(dim, pos).getState();
                f.setBurnTime((short) 300);
                f.setCookTime((short) 7);
                f.setCookTimeTotal(123);
                assertTrue(f.update());
                var nbt = w.blockEntityNbtNow(pos).orElseThrow();
                assertEquals(300, nbt.getShort("BurnTime"), "M01");
                var again = (org.bukkit.block.Furnace) Compat.block(dim, pos).getState();
                assertEquals(300, again.getBurnTime());
                assertEquals(7, again.getCookTime());
                assertEquals(123, again.getCookTimeTotal(), "M04");
                // SPIGOT-844 끄기: burnTime 0 이면 lit=false
                again.setBurnTime((short) 0);
                assertEquals("false", ((CompatBlockData) again.getBlockData()).furnace().properties().get("lit"), "M02");
                // 배율 검사 문구·NaN
                assertThrows(IllegalArgumentException.class, () -> again.setCookSpeedMultiplier(200.5), "M07");
                assertThrows(IllegalArgumentException.class, () -> again.setCookSpeedMultiplier(Double.NaN), "M08");
                // 배율: 사본의 총 시간을 다시 잰다(시험 서버엔 레시피가 없어 기본 200) — ceil(200 / 2.5) = 80
                again.setCookSpeedMultiplier(2.5);
                assertEquals(2.5, again.getCookSpeedMultiplier());
                assertEquals(80, again.getCookTimeTotal());
                assertTrue(again.update());
                assertEquals(2.5, w.blockEntityNbtNow(pos).orElseThrow().getDouble("Paper.CookSpeedMultiplier"), "서버 화로가 배율을 들고 저장한다");
                var reread = (org.bukkit.block.Furnace) Compat.block(dim, pos).getState();
                assertEquals(2.5, reread.getCookSpeedMultiplier());
                reread.setCookSpeedMultiplier(1.0);
                assertTrue(reread.update());
                assertFalse(w.blockEntityNbtNow(pos).orElseThrow().contains("Paper.CookSpeedMultiplier"), "1.0 이면 바닐라 저장 그대로");
                // RecipesUsed 읽기
                org.domi.furnace.api.nbt.NbtCompound n = w.blockEntityNbtNow(pos).orElseThrow();
                org.domi.furnace.api.nbt.NbtCompound used = new org.domi.furnace.api.nbt.NbtCompound();
                used.putInt("minecraft:iron_ingot_from_blasting_raw_iron", 4);
                n.putCompound("RecipesUsed", used);
                w.setBlockEntityNbtNow(pos, n);
                var third = (org.bukkit.block.Furnace) Compat.block(dim, pos).getState();
                var key = org.bukkit.NamespacedKey.minecraft("iron_ingot_from_blasting_raw_iron");
                assertTrue(third.hasRecipeUsedCount(key));
                assertEquals(4, third.getRecipeUsedCount(key), "M10");
                // 결과 칸
                var inv = third.getInventory();
                assertEquals(org.bukkit.event.inventory.InventoryType.BLAST_FURNACE, inv.getType());
                inv.setResult(new ItemStack(Material.IRON_INGOT, 2));
                assertEquals("minecraft:iron_ingot", w.inventoryAtNow(pos).orElseThrow().get(2).join().type(), "M14");
                assertEquals(new ItemStack(Material.IRON_INGOT, 2), inv.getResult(), "M12");
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** 창 이벤트의 위쪽 화로 인벤토리(사본)도 Paper 처럼 FurnaceInventory 다(#379 검토 R3). */
    @Test
    void eventFurnaceInventoryIsTyped() {
        var contents = java.util.List.of(org.domi.furnace.api.item.ItemStack.of("minecraft:raw_iron"), org.domi.furnace.api.item.ItemStack.of("minecraft:coal"),
                org.domi.furnace.api.item.ItemStack.EMPTY);
        org.bukkit.inventory.Inventory top = new EventInventories.Furnace(org.bukkit.event.inventory.InventoryType.BLAST_FURNACE, contents, 3, "minecraft:blast_furnace", null);
        var f = assertInstanceOf(org.bukkit.inventory.FurnaceInventory.class, top);
        assertEquals(Material.RAW_IRON, f.getSmelting().getType());
        assertEquals(Material.COAL, f.getFuel().getType());
        assertTrue(f.isFuel(f.getFuel()));
        assertFalse(f.canSmelt(null));
        assertNull(f.getHolder());
    }

    // ------------------------------------------------------------------ #397 검토 시험(사본 NBT 왕복·전리품 표·방향별 칸 순서)

    static org.domi.furnace.api.item.ItemStack api(String id, int n, Map<String, String> c, Set<String> removed) {
        return new org.domi.furnace.api.item.ItemStack(id, n, c, removed);
    }

    static final org.domi.furnace.api.item.ItemStack[] RICH = {
            api("minecraft:diamond_sword", 1, Map.of(
                    "minecraft:damage", "5",
                    "minecraft:custom_name", "'{\"text\":\"Key\",\"color\":\"red\"}'",
                    "minecraft:enchantments", "{levels:{\"minecraft:sharpness\":5},show_in_tooltip:0b}",
                    "minecraft:lore", "['\"line one\"','{\"text\":\"two\",\"italic\":false}']",
                    "minecraft:unbreakable", "{}",
                    "minecraft:attribute_modifiers", "{modifiers:[{type:\"minecraft:generic.attack_damage\",id:\"minecraft:review\",amount:3.25d,operation:\"add_value\",slot:\"mainhand\"}]}"),
                    Set.of()),
            api("minecraft:stick", 7, Map.of(
                    "minecraft:custom_data", "{a:1b,arr:[I;1,2,3],big:9000000000L,d:1.5d,f:2.5f,l:[L;5L,6L],bytes:[B;1B,2B],s:3s,str:\"x'y\\\"z\",nested:[{z:1},{z:2}],\"odd key\":1}"),
                    Set.of()),
            api("minecraft:apple", 3, Map.of(), Set.of("minecraft:food")),
            api("minecraft:shulker_box", 1, Map.of(
                    "minecraft:container", "[{slot:0,item:{id:\"minecraft:stone\",count:3}},{slot:5,item:{id:\"minecraft:diamond\",count:1,components:{\"minecraft:custom_name\":'\"inner\"'}}}]"),
                    Set.of()),
            api("minecraft:potion", 1, Map.of("minecraft:potion_contents", "{potion:\"minecraft:swiftness\",custom_color:123456}"), Set.of()),
    };

    /** 살아 있는 칸에 넣은 아이템을 사본 인벤토리로 읽으면 API 아이템이 그대로(컴포넌트 SNBT 까지) 같고, 손대고 update 해도 그대로 돌아온다. */
    @Test
    void snapshotReadAndWriteRoundTripRichComponents() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:barrel", Map.of()));
            try {
                var liveInv = w.inventoryAtNow(pos).orElseThrow();
                for (int i = 0; i < RICH.length; i++) liveInv.set(i * 3, RICH[i]).join();
                var liveForms = liveInv.contents().join();
                Barrel b = (Barrel) Compat.block(dim, pos).getState();
                Inventory snap = b.getSnapshotInventory();
                var snapForms = ((CompatInventory) snap).handle.contents().join();
                for (int i = 0; i < liveForms.size(); i++) {
                    assertEquals(liveForms.get(i), snapForms.get(i), "칸 " + i + " 사본 읽기");
                    assertEquals(b.getInventory().getItem(i), snap.getItem(i), "칸 " + i + " Bukkit 아이템");
                }
                // 사본에 손대고(맨 끝 칸 하나 넣고) update → 다른 칸은 그대로
                snap.setItem(26, new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIRT, 1));
                assertTrue(b.update());
                var after = w.inventoryAtNow(pos).orElseThrow().contents().join();
                for (int i = 0; i < 26; i++) assertEquals(liveForms.get(i), after.get(i), "칸 " + i + " 왕복");
                assertEquals("minecraft:dirt", after.get(26).type());
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** 전리품 표가 있는 사본: 사본 인벤토리에 넣어도 update 때 Items 를 쓰지 않는다(바닐라 trySaveLootTable). */
    @Test
    void lootTableSnapshotDoesNotWriteItems() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:chest", Map.of("facing", "north", "type", "single")));
            try {
                NbtCompound n = w.blockEntityNbtNow(pos).orElseThrow();
                n.putString("LootTable", "minecraft:chests/simple_dungeon");
                assertTrue(w.setBlockEntityNbtNow(pos, n));
                Chest c = (Chest) Compat.block(dim, pos).getState();
                assertTrue(c.getSnapshotInventory().isEmpty());
                c.getSnapshotInventory().setItem(0, new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND, 1));
                NbtCompound sn = ((org.bukkit.craftbukkit.block.CraftBlockEntityState) c).getSnapshotNBT();
                assertFalse(sn.contains("Items") && !sn.getList("Items").values().isEmpty(), "전리품 표가 있으면 Items 를 쓰지 않는다: " + sn);
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** 네 방향 모두: Bukkit 왼쪽(칸 0..26) = type=right 상자. */
    @Test
    void doubleChestOrderAllFacings() {
        record Case(String facing, int dx, int dz) {}
        // type=left 의 짝은 facing 시계 방향 칸
        var cases = java.util.List.of(new Case("north", 1, 0), new Case("east", 0, 1), new Case("south", -1, 0), new Case("west", 0, -1));
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            for (Case k : cases) {
                BlockPos other = new BlockPos(pos.x() + k.dx, pos.y(), pos.z() + k.dz);
                w.block(pos).set(new BlockData("minecraft:chest", Map.of("facing", k.facing, "type", "left")));
                w.block(other).set(new BlockData("minecraft:chest", Map.of("facing", k.facing, "type", "right")));
                try {
                    w.inventoryAtNow(other).orElseThrow().set(0, api("minecraft:diamond", 1, Map.of(), Set.of())).join();
                    w.inventoryAtNow(pos).orElseThrow().set(0, api("minecraft:emerald", 1, Map.of(), Set.of())).join();
                    Inventory big = ((Chest) Compat.block(dim, pos).getState()).getInventory();
                    assertEquals(54, big.getSize(), k.facing);
                    assertEquals(org.bukkit.Material.DIAMOND, big.getItem(0).getType(), k.facing + " 왼쪽 = type=right");
                    assertEquals(org.bukkit.Material.EMERALD, big.getItem(27).getType(), k.facing);
                } finally {
                    w.block(other).set(BlockData.AIR);
                    w.block(pos).set(BlockData.AIR);
                }
            }
            return null;
        });
    }

    /** 사본 NBT 의 Items 에는 비지 않은 칸만 적힌다(바닐라 ContainerHelper.saveAllItems). */
    @Test
    void snapshotItemsSkipEmptySlots() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:barrel", Map.of()));
            try {
                Barrel b = (Barrel) Compat.block(dim, pos).getState();
                b.getSnapshotInventory().setItem(4, new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIRT, 2));
                var sent = ((org.bukkit.craftbukkit.block.CraftBlockEntityState) b).getSnapshotNBT().getList("Items").values();
                assertEquals(1, sent.size(), "사본 NBT 에도 빈 칸은 없다(Paper getSnapshotNBT): " + sent);
                assertTrue(b.update());
                var items = w.blockEntityNbtNow(pos).orElseThrow().getList("Items").values();
                assertEquals(1, items.size(), "빈 칸은 쓰지 않는다: " + items);
                assertEquals((byte) 4, ((org.domi.furnace.api.nbt.NbtCompound) items.get(0)).getByte("Slot"));
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** 사본 인벤토리 위치는 Paper 처럼 놓인 상태에서도 null(사본 블록 엔티티는 레벨이 없다). */
    @Test
    void placedSnapshotInventoryHasNoLocation() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:barrel", Map.of()));
            try {
                Barrel b = (Barrel) Compat.block(dim, pos).getState();
                assertNull(b.getSnapshotInventory().getLocation());
                assertNotNull(b.getInventory().getLocation(), "살아 있는 인벤토리는 그 칸");
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }
}
