package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.inventory.InventorySnapshot;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * H4 묶음 D: 결과를 정하는 인벤토리 이벤트(제작·모루·마법 부여·양조·깔때기)와 닫힌 까닭이 Bukkit 처리기에 Paper 모양으로 닿고,
 * 처리기가 바꾼 값이 Furnace 이벤트로 돌아오는지 — 헤드리스 서버의 버스에 Furnace 이벤트를 직접 발행해 본다.
 */
class InventoryResultEventsTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { RecorderPlugin.reset(); }

    private Path recorderJar() throws Exception {
        return Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
    }

    /** 위쪽 칸 top 개 + 플레이어 인벤토리 36칸인 빈 창. */
    static InventorySnapshot window(String key, int top) {
        return new InventorySnapshot(key, Collections.nCopies(top + 36, ItemStack.EMPTY));
    }

    private static InventorySnapshot window(String key, int top, Map<Integer, ItemStack> items) {
        List<ItemStack> l = new ArrayList<>(Collections.nCopies(top + 36, ItemStack.EMPTY));
        items.forEach(l::set);
        return new InventorySnapshot(key, l);
    }

    private static ItemStack item(String type, int n) { return ItemStack.of(type).withAmount(n); }

    @SuppressWarnings("unchecked")
    private static <E extends Event> List<E> seen(Class<E> type) {
        List<E> out = new ArrayList<>();
        for (Event e : RecorderPlugin.SEEN) if (type.isInstance(e)) out.add((E) e);
        return out;
    }

    private interface Body {
        void run(FurnaceTestServer server, Player p) throws Exception;
    }

    private void withServer(Body body) throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            var tp = server.join("Dana");
            server.tick(2);
            body.run(server, tp.player());
        }
    }

    @Test
    void craftItemEventReachesClickHandlersWithRecipeAndCraftingInventory() throws Exception {
        withServer((server, p) -> {
            var bus = server.api().events();
            var snap = window("minecraft:crafting", 10, Map.of(0, item("minecraft:oak_planks", 4), 1, item("minecraft:oak_log", 1)));
            List<String> log = new CopyOnWriteArrayList<>();
            RecorderPlugin.ACTIONS.put("CraftItemEvent", e -> {
                var c = (org.bukkit.event.inventory.CraftItemEvent) e;
                log.add(((Keyed) c.getRecipe()).getKey() + "," + c.getRecipe().getResult().getType() + "," + c.getInventory().getResult().getType()
                        + "," + c.getInventory().getMatrix()[0].getType() + "," + c.getInventory().getMatrix().length + "," + c.getAction() + "," + c.getSlotType()
                        + "," + c.getView().getType());
                c.setCancelled(true);
            });
            var f = new org.domi.furnace.api.event.inventory.CraftItemEvent(p, snap, 0, 0, "pickup", ItemStack.EMPTY, "minecraft:oak_planks");
            bus.call(f);
            // Bukkit 에서 CraftItemEvent 는 InventoryClickEvent 처리기 목록으로 간다 — 클릭 처리기가 CraftItemEvent 를 받는다
            assertEquals(1, seen(org.bukkit.event.inventory.CraftItemEvent.class).size(), RecorderPlugin.SEEN.toString());
            assertEquals(List.of("minecraft:oak_planks,OAK_PLANKS,OAK_PLANKS,OAK_LOG,9,PICKUP_ALL,RESULT,WORKBENCH"), log);
            assertTrue(f.isCancelled(), "Bukkit 처리기의 취소가 Furnace 이벤트로 간다");

            // 결과 칸은 놓을 수 없다: 같은 아이템 커서는 합쳐 들어가면 PICKUP_ALL, 다른 아이템이면 NOTHING(CraftBukkit 규칙)
            RecorderPlugin.reset();
            List<InventoryAction> actions = new CopyOnWriteArrayList<>();
            RecorderPlugin.ACTIONS.put("CraftItemEvent", e -> actions.add(((org.bukkit.event.inventory.CraftItemEvent) e).getAction()));
            bus.call(new org.domi.furnace.api.event.inventory.CraftItemEvent(p, snap, 0, 0, "pickup", item("minecraft:oak_planks", 2), "minecraft:oak_planks"));
            bus.call(new org.domi.furnace.api.event.inventory.CraftItemEvent(p, snap, 0, 0, "pickup", item("minecraft:stone", 2), "minecraft:oak_planks"));
            bus.call(new org.domi.furnace.api.event.inventory.CraftItemEvent(p, snap, 0, 0, "quick_move", ItemStack.EMPTY, "minecraft:oak_planks"));
            assertEquals(List.of(InventoryAction.PICKUP_ALL, InventoryAction.NOTHING, InventoryAction.MOVE_TO_OTHER_INVENTORY), actions);

            // 레시피를 모르면 Paper 처럼 보통 InventoryClickEvent
            RecorderPlugin.reset();
            bus.call(new org.domi.furnace.api.event.inventory.CraftItemEvent(p, snap, 0, 0, "pickup", ItemStack.EMPTY, null));
            var clicks = seen(org.bukkit.event.inventory.InventoryClickEvent.class);
            assertEquals(1, clicks.size());
            assertFalse(clicks.get(0) instanceof org.bukkit.event.inventory.CraftItemEvent);
            assertTrue(clicks.get(0).getInventory() instanceof org.bukkit.inventory.CraftingInventory, "제작 창의 위쪽은 늘 CraftingInventory");
        });
    }

    @Test
    void prepareItemCraftResultIsWrittenBack() throws Exception {
        withServer((server, p) -> {
            var snap = window("minecraft:player", 5, Map.of(1, item("minecraft:oak_log", 1)));
            List<String> log = new CopyOnWriteArrayList<>();
            RecorderPlugin.ACTIONS.put("PrepareItemCraftEvent", e -> {
                var c = (org.bukkit.event.inventory.PrepareItemCraftEvent) e;
                log.add(((Keyed) c.getRecipe()).getKey() + "," + c.isRepair() + "," + c.getInventory().getType() + "," + c.getInventory().getResult().getType()
                        + "," + c.getInventory().getMatrix().length);
                c.getInventory().setResult(new org.bukkit.inventory.ItemStack(Material.DIAMOND, 3));
                log.add("after=" + c.getInventory().getResult().getType());
                try {
                    c.getInventory().setMatrix(new org.bukkit.inventory.ItemStack[4]);
                    log.add("matrix=written");
                } catch (UnsupportedFeature u) {
                    log.add("matrix=unsupported");
                }
            });
            var f = new org.domi.furnace.api.event.inventory.PrepareItemCraftEvent(p, snap, "minecraft:oak_planks", item("minecraft:oak_planks", 4));
            server.api().events().call(f);
            assertEquals(List.of("minecraft:oak_planks,false,CRAFTING,OAK_PLANKS,4", "after=DIAMOND", "matrix=unsupported"), log);
            assertEquals(item("minecraft:diamond", 3), f.result(), "처리기가 둔 결과 칸이 Furnace 결과가 된다");

            log.clear();
            RecorderPlugin.ACTIONS.put("PrepareItemCraftEvent", e -> log.add("repair=" + ((org.bukkit.event.inventory.PrepareItemCraftEvent) e).isRepair()));
            var repair = new org.domi.furnace.api.event.inventory.PrepareItemCraftEvent(p, snap, "minecraft:repair_item", item("minecraft:wooden_sword", 1));
            server.api().events().call(repair);
            assertEquals(List.of("repair=true"), log);
            assertEquals(item("minecraft:wooden_sword", 1), repair.result(), "바꾸지 않으면 그대로");
        });
    }

    @Test
    void prepareAnvilResultAndRepairCostAreWrittenBack() throws Exception {
        withServer((server, p) -> {
            List<String> log = new CopyOnWriteArrayList<>();
            RecorderPlugin.ACTIONS.put("PrepareAnvilEvent", e -> {
                var a = (org.bukkit.event.inventory.PrepareAnvilEvent) e;
                var v = a.getView();
                log.add(a.getResult().getType() + "," + v.getRenameText() + "," + v.getRepairCost() + "," + v.getMaximumRepairCost() + ","
                        + a.getInventory().getFirstItem().getType() + "," + (v.getTopInventory() == a.getInventory()));
                a.setResult(null);
                v.setRepairCost(7);
            });
            var snap = window("minecraft:anvil", 3, Map.of(0, item("minecraft:iron_sword", 1), 2, item("minecraft:iron_sword", 1)));
            var f = new org.domi.furnace.api.event.inventory.PrepareAnvilEvent(p, snap, item("minecraft:iron_sword", 1), 3, "Blade");
            server.api().events().call(f);
            assertEquals(List.of("IRON_SWORD,Blade,3,40,IRON_SWORD,true"), log);
            assertTrue(f.result().isEmpty(), "setResult(null) 은 결과 칸을 비운다");
            assertEquals(7, f.repairCost());
        });
    }

    @Test
    void enchantingOffersAndAppliedEnchantsAreWrittenBack() throws Exception {
        withServer((server, p) -> {
            var bp = new BlockPos(3, 64, 4);
            var snap = window("minecraft:enchantment", 2, Map.of(0, item("minecraft:diamond_sword", 1)));
            List<String> log = new CopyOnWriteArrayList<>();
            RecorderPlugin.ACTIONS.put("PrepareItemEnchantEvent", e -> {
                var pe = (org.bukkit.event.enchantment.PrepareItemEnchantEvent) e;
                var o = pe.getOffers();
                log.add(o[0].getEnchantment().getKey() + ":" + o[0].getEnchantmentLevel() + ":" + o[0].getCost() + "," + o[1] + "," + o[2] + ","
                        + pe.getEnchantmentBonus() + "," + Arrays.toString(pe.getExpLevelCostsOffered()) + "," + (pe.getView().getOffers() == o) + ","
                        + pe.getEnchantBlock().getX() + "," + pe.getItem().getType());
                o[2] = new EnchantmentOffer(Enchantment.UNBREAKING, 2, 9);
            });
            var offers = new ArrayList<>(Arrays.asList(new org.domi.furnace.api.inventory.EnchantmentOffer("minecraft:sharpness", 1, 5),
                    new org.domi.furnace.api.inventory.EnchantmentOffer(null, 0, 8), null));
            var prep = new org.domi.furnace.api.event.inventory.PrepareItemEnchantEvent(p, snap, bp, item("minecraft:diamond_sword", 1), 15, offers);
            server.api().events().call(prep);
            assertEquals(List.of("minecraft:sharpness:1:5,null,null,15,[5, 0, 0],true,3,DIAMOND_SWORD"), log);
            assertEquals(new org.domi.furnace.api.inventory.EnchantmentOffer("minecraft:sharpness", 1, 5), prep.offers().get(0));
            assertEquals(new org.domi.furnace.api.inventory.EnchantmentOffer(null, 0, 8), prep.offers().get(1), "Bukkit 이 null 로 본 단서 없는 제안은 바꾸지 않는다");
            assertEquals(new org.domi.furnace.api.inventory.EnchantmentOffer("minecraft:unbreaking", 2, 9), prep.offers().get(2));

            log.clear();
            RecorderPlugin.ACTIONS.put("EnchantItemEvent", e -> {
                var ee = (org.bukkit.event.enchantment.EnchantItemEvent) e;
                log.add(ee.getEnchantmentHint().getKey() + ":" + ee.getLevelHint() + "," + ee.getEnchantsToAdd() + "," + ee.whichButton() + "," + ee.getExpLevelCost()
                        + "," + ee.getEnchanter().getName());
                try {
                    ee.setExpLevelCost(0);
                } catch (IllegalArgumentException ex) {
                    log.add(ex.getMessage());
                }
                ee.getEnchantsToAdd().put(Enchantment.UNBREAKING, 3);
                ee.setExpLevelCost(2);
            });
            Map<String, Integer> map = new LinkedHashMap<>();
            map.put("minecraft:sharpness", 1);
            map.put("custom:zap", 2);
            var ench = new org.domi.furnace.api.event.inventory.EnchantItemEvent(p, snap, bp, item("minecraft:diamond_sword", 1), 0, 1, map, "minecraft:sharpness", 1);
            server.api().events().call(ench);
            assertEquals(List.of("minecraft:sharpness:1,{Enchantment[minecraft:sharpness]=1},0,1,Dana", "The cost must be greater than 0!"), log);
            assertEquals(Map.of("minecraft:sharpness", 1, "custom:zap", 2, "minecraft:unbreaking", 3), ench.enchants(), "Bukkit 이 모르는 마법 부여는 남는다");
            assertEquals(2, ench.expLevelCost());
        });
    }

    @Test
    void brewResultsAndFuelAreWrittenBack() throws Exception {
        withServer((server, p) -> {
            String w = "minecraft:overworld";
            var bp = new BlockPos(5, 64, 6);
            var awkward = item("minecraft:potion", 1);
            var contents = new InventorySnapshot("minecraft:brewing_stand", List.of(awkward, awkward, ItemStack.EMPTY, item("minecraft:nether_wart", 2),
                    item("minecraft:blaze_powder", 1)));
            List<String> log = new CopyOnWriteArrayList<>();
            RecorderPlugin.ACTIONS.put("BrewEvent", e -> {
                var b = (org.bukkit.event.inventory.BrewEvent) e;
                var c = b.getContents();
                var loc = c.getLocation();
                log.add(b.getResults().size() + "," + b.getResults().get(2).getType() + "," + c.getIngredient().getType() + "," + c.getFuel().getType() + ","
                        + c.getType() + "," + b.getFuelLevel() + "," + loc.getBlockX() + "/" + loc.getBlockY() + "/" + loc.getBlockZ() + "," + b.getBlock().getX());
                try {
                    c.setIngredient(null);
                    log.add("write=ok");
                } catch (UnsupportedFeature u) {
                    log.add("write=unsupported");
                }
                b.getResults().set(1, new org.bukkit.inventory.ItemStack(Material.DIAMOND));
            });
            var brew = new org.domi.furnace.api.event.inventory.BrewEvent(w, bp, contents, List.of(awkward, awkward, ItemStack.EMPTY), 9);
            server.api().events().call(brew);
            assertEquals(List.of("3,AIR,NETHER_WART,BLAZE_POWDER,BREWING,9,5/64/6,5", "write=unsupported"), log);
            assertEquals(List.of(awkward, item("minecraft:diamond", 1), ItemStack.EMPTY), brew.results());

            RecorderPlugin.ACTIONS.put("BrewingStandFuelEvent", e -> {
                var b = (org.bukkit.event.inventory.BrewingStandFuelEvent) e;
                b.setFuelPower(5);
                b.setConsuming(false);
            });
            var fuel = new org.domi.furnace.api.event.inventory.BrewingStandFuelEvent(w, bp, item("minecraft:blaze_powder", 1), 20);
            server.api().events().call(fuel);
            assertEquals(5, fuel.fuelPower());
            assertFalse(fuel.isConsuming());
        });
    }

    @Test
    void hopperMoveSeesBothContainersAndWritesTheItemBack() throws Exception {
        withServer((server, p) -> {
            String w = "minecraft:overworld";
            List<String> log = new CopyOnWriteArrayList<>();
            RecorderPlugin.ACTIONS.put("InventoryMoveItemEvent", e -> {
                var m = (org.bukkit.event.inventory.InventoryMoveItemEvent) e;
                var src = m.getSource();
                var loc = src.getLocation();
                log.add(src.getType() + "," + src.getSize() + "," + src.getItem(0).getType() + "," + loc.getX() + "/" + loc.getY() + "/" + loc.getZ() + ","
                        + m.getDestination().getType() + "," + (m.getInitiator() == m.getDestination()) + "," + m.getItem().getType() + ","
                        + src.getHolder());
                try {
                    src.setItem(0, null);
                    log.add("write=ok");
                } catch (UnsupportedFeature u) {
                    log.add("write=unsupported");
                }
                m.setItem(new org.bukkit.inventory.ItemStack(Material.DIAMOND));
            });
            List<ItemStack> chest = new ArrayList<>(Collections.nCopies(27, ItemStack.EMPTY));
            chest.set(0, item("minecraft:stone", 3));
            var f = new org.domi.furnace.api.event.inventory.InventoryMoveItemEvent(new InventorySnapshot("minecraft:chest", chest), new Location(w, 1.5, 65.5, 2.5),
                    new InventorySnapshot("minecraft:hopper", Collections.nCopies(5, ItemStack.EMPTY)), new Location(w, 1.5, 64.5, 2.5),
                    item("minecraft:stone", 1), false);
            server.api().events().call(f);
            assertEquals(List.of("CHEST,27,STONE,1.0/65.0/2.0,HOPPER,true,STONE,null", "write=unsupported"), log);
            assertEquals(item("minecraft:diamond", 1), f.item());
            assertFalse(f.isCancelled());
        });
    }

    @Test
    void closeReasonIsCarried() throws Exception {
        withServer((server, p) -> {
            server.api().events().call(new org.domi.furnace.api.event.inventory.InventoryCloseEvent(p, window("minecraft:generic_9x3", 27), null,
                    org.domi.furnace.api.event.inventory.InventoryCloseEvent.Reason.DEATH));
            var closes = seen(org.bukkit.event.inventory.InventoryCloseEvent.class);
            assertEquals(1, closes.size());
            assertEquals(org.bukkit.event.inventory.InventoryCloseEvent.Reason.DEATH, closes.get(0).getReason());
            assertEquals(InventoryType.CHEST, closes.get(0).getView().getType());
            assertNull(closes.get(0).getInventory().getLocation(), "블록을 모르는 창은 위치가 없다");
        });
    }
}
