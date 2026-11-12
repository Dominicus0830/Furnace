// 결과를 정하는 인벤토리 이벤트의 Bukkit 다리(H4 묶음 D): 제작 준비, 모루 준비, 마법 부여 제안·부여, 양조·양조 연료, 깔때기 옮기기.
// 규칙은 Mappings 와 같다 — 처리기가 바꾼 값은 Furnace 이벤트에 되쓰고(바뀌었을 때만), 전달 직전에는 앞선 Furnace 처리기의 값을 Bukkit 쪽에 맞춘다.
// Furnace 이벤트가 알리지 않는 값은 지어내지 않는다(UnsupportedFeature 또는 정직한 기본값).
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.inventory.InventoryType;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.world.BlockPos;

final class InventoryResultMappings {

    private InventoryResultMappings() {}

    private static ItemStack furnace(org.bukkit.inventory.ItemStack b) { return CompatItems.toFurnace(b); }

    /** 빈 것은 null(Paper 의 인벤토리 읽기처럼). */
    private static org.bukkit.inventory.ItemStack bukkitOrNull(ItemStack f) { return f == null || f.isEmpty() ? null : CompatItems.copyOf(f); }

    /** 플레이어가 있는 월드의 블록. */
    private static org.bukkit.block.Block block(org.domi.furnace.api.entity.Player p, BlockPos pos) {
        return pos == null ? null : Compat.block(p.location().world(), pos);
    }

    static void register() {
        registerCraft();
        registerAnvil();
        registerEnchant();
        registerBrew();
        registerMove();
    }

    // ------------------------------------------------------------------ 제작

    private static void registerCraft() {
        // Paper callPreCraftEvent: 결과 칸에 새 결과를 둔 CraftingInventory 로 부르고, 처리기 뒤 결과 칸 값을 쓴다
        EventBridge.reg(org.domi.furnace.api.event.inventory.PrepareItemCraftEvent.class, org.bukkit.event.inventory.PrepareItemCraftEvent.class,
                f -> {
                    var recipe = EventInventories.CompatRecipe.of(f.recipe(), f.result());
                    CompatInventoryView view = CompatInventories.view(f.player(), f.inventory(), f.menu(), null,
                            new CompatInventories.Extras(recipe, true, f.result(), null, null));
                    if (!(view.getTopInventory() instanceof EventInventories.Crafting c)) return null;   // 제작 창이 아니면(있을 수 없다) 건너뛴다
                    return new org.bukkit.event.inventory.PrepareItemCraftEvent(c, view, "minecraft:repair_item".equals(f.recipe()));
                },
                (f, b) -> {
                    ItemStack now = ((EventInventories.Crafting) b.getInventory()).furnaceAt(0);
                    if (!now.equals(f.result())) f.setResult(now);
                }, (f, b) -> {
                    var c = (EventInventories.Crafting) b.getInventory();
                    if (!c.furnaceAt(0).equals(f.result())) c.setResult(bukkitOrNull(f.result()));
                });
    }

    // ------------------------------------------------------------------ 모루

    private static EventInventories.AnvilState anvilState(org.bukkit.event.inventory.PrepareInventoryResultEvent b) {
        return ((EventInventories.Anvil) b.getInventory()).state();
    }

    private static void registerAnvil() {
        // Paper: PrepareAnvilEvent 는 PrepareInventoryResultEvent 의 처리기 목록을 쓴다. 결과는 이벤트 값, 비용은 AnvilView 값
        EventBridge.reg(org.domi.furnace.api.event.inventory.PrepareAnvilEvent.class, org.bukkit.event.inventory.PrepareInventoryResultEvent.class,
                f -> {
                    var st = new EventInventories.AnvilState(f.renameText(), f.repairCost());
                    CompatInventoryView view = CompatInventories.view(f.player(), f.inventory(), f.menu(), null,
                            new CompatInventories.Extras(null, false, null, st, null));
                    if (!(view instanceof CompatInventoryView.Anvil av)) return null;
                    return new org.bukkit.event.inventory.PrepareAnvilEvent(av, bukkitOrNull(f.result()));
                },
                (f, b) -> {
                    ItemStack r = furnace(b.getResult());
                    if (!r.equals(f.result())) f.setResult(r);
                    int cost = anvilState(b).repairCost;
                    if (cost != f.repairCost()) f.setRepairCost(cost);
                }, (f, b) -> {
                    if (!furnace(b.getResult()).equals(f.result())) b.setResult(bukkitOrNull(f.result()));
                    var st = anvilState(b);
                    if (st.repairCost != f.repairCost()) st.repairCost = f.repairCost();
                });
    }

    // ------------------------------------------------------------------ 마법 부여

    private static EnchantmentOffer bukkitOffer(org.domi.furnace.api.inventory.EnchantmentOffer o) {
        if (o == null || o.enchantment() == null) return null;   // CraftBukkit: 단서가 없는 칸은 null
        Enchantment e = Enchantment.fromKey(o.enchantment());
        return e == null ? null : new EnchantmentOffer(e, o.level(), o.cost());
    }

    private static org.domi.furnace.api.inventory.EnchantmentOffer furnaceOffer(EnchantmentOffer b) {
        return b == null ? null : new org.domi.furnace.api.inventory.EnchantmentOffer(b.getEnchantment().getKey().toString(), b.getEnchantmentLevel(), b.getCost());
    }

    /** Furnace 마법 부여 맵 → Bukkit(Bukkit 이 모르는 키 — 데이터 팩 마법 부여 — 는 빠진다). */
    private static Map<Enchantment, Integer> bukkitEnchants(Map<String, Integer> m) {
        Map<Enchantment, Integer> out = new LinkedHashMap<>();
        for (var e : m.entrySet()) {
            Enchantment en = Enchantment.fromKey(e.getKey());
            if (en != null) out.put(en, e.getValue());
        }
        return out;
    }

    private static void registerEnchant() {
        EventBridge.reg(org.domi.furnace.api.event.inventory.PrepareItemEnchantEvent.class, org.bukkit.event.enchantment.PrepareItemEnchantEvent.class,
                f -> {
                    EnchantmentOffer[] offers = new EnchantmentOffer[3];
                    for (int i = 0; i < 3 && i < f.offers().size(); i++) offers[i] = bukkitOffer(f.offers().get(i));
                    CompatInventoryView view = CompatInventories.view(f.player(), f.inventory(), f.menu(), null,
                            new CompatInventories.Extras(null, false, null, null, offers));
                    if (!(view instanceof CompatInventoryView.Enchantment ev)) return null;
                    return new org.bukkit.event.enchantment.PrepareItemEnchantEvent(Compat.player(f.player()), ev, block(f.player(), f.table()),
                            CompatItems.copyOf(f.item()), offers, f.bookshelves());
                },
                (f, b) -> {   // 바뀐 칸만 되쓴다 — 단서 없이 비용만 있던 Furnace 제안(Bukkit 에서 null)을 건드리지 않게
                    var offers = b.getOffers();
                    for (int i = 0; i < 3 && i < f.offers().size(); i++) {
                        if (!Objects.equals(offers[i], bukkitOffer(f.offers().get(i)))) f.offers().set(i, furnaceOffer(offers[i]));
                    }
                }, (f, b) -> {
                    var offers = b.getOffers();
                    for (int i = 0; i < 3 && i < f.offers().size(); i++) {
                        var want = bukkitOffer(f.offers().get(i));
                        if (!Objects.equals(offers[i], want)) offers[i] = want;
                    }
                });
        EventBridge.reg(org.domi.furnace.api.event.inventory.EnchantItemEvent.class, org.bukkit.event.enchantment.EnchantItemEvent.class,
                f -> {
                    CompatInventoryView view = CompatInventories.view(f.player(), f.inventory(), f.menu(), null);
                    Enchantment hint = f.enchantmentHint() == null ? null : Enchantment.fromKey(f.enchantmentHint());
                    return new org.bukkit.event.enchantment.EnchantItemEvent(Compat.player(f.player()), view, block(f.player(), f.table()),
                            CompatItems.copyOf(f.item()), f.expLevelCost(), bukkitEnchants(f.enchants()), hint, hint == null ? -1 : f.levelHint(),
                            f.whichButton());
                },
                (f, b) -> {
                    if (b.getExpLevelCost() != f.expLevelCost()) f.setExpLevelCost(b.getExpLevelCost());
                    var now = b.getEnchantsToAdd();
                    if (!now.equals(bukkitEnchants(f.enchants()))) {
                        // Bukkit 이 모르는 키(데이터 팩 마법 부여)는 남기고, 아는 키는 처리기의 맵으로 바꾼다
                        f.enchants().keySet().removeIf(k -> Enchantment.fromKey(k) != null);
                        for (var e : now.entrySet()) f.enchants().put(e.getKey().getKey().toString(), e.getValue());
                    }
                }, (f, b) -> {
                    if (b.getExpLevelCost() != f.expLevelCost() && f.expLevelCost() > 0) b.setExpLevelCost(f.expLevelCost());
                    var want = bukkitEnchants(f.enchants());
                    if (!b.getEnchantsToAdd().equals(want)) {
                        b.getEnchantsToAdd().clear();
                        b.getEnchantsToAdd().putAll(want);
                    }
                });
    }

    // ------------------------------------------------------------------ 양조

    private static org.bukkit.Location blockLocation(String world, BlockPos pos) {
        return new org.bukkit.Location(Compat.world(world), pos.x(), pos.y(), pos.z());
    }

    private static void registerBrew() {
        // Paper: getResults() 는 병 칸 0~2 에 들어갈 결과 목록(빈 것은 AIR) — 고친 목록이 그대로 칸에 들어간다
        EventBridge.reg(org.domi.furnace.api.event.inventory.BrewEvent.class, org.bukkit.event.inventory.BrewEvent.class,
                f -> {
                    var c = f.contents();
                    var inv = new EventInventories.Brewer(c.contents(), Math.max(5, c.size()), c.type(), blockLocation(f.world(), f.pos()));
                    List<org.bukkit.inventory.ItemStack> results = new ArrayList<>(3);
                    for (ItemStack s : f.results()) results.add(CompatItems.copyOf(s));
                    return new org.bukkit.event.inventory.BrewEvent(Compat.block(f.world(), f.pos()), inv, results, f.fuelLevel());
                },
                (f, b) -> {
                    List<ItemStack> now = new ArrayList<>(b.getResults().size());
                    for (var s : b.getResults()) now.add(furnace(s));
                    if (!now.equals(f.results())) {
                        f.results().clear();
                        f.results().addAll(now);
                    }
                }, (f, b) -> {
                    List<ItemStack> cur = new ArrayList<>(b.getResults().size());
                    for (var s : b.getResults()) cur.add(furnace(s));
                    if (!cur.equals(f.results())) {
                        b.getResults().clear();
                        for (ItemStack s : f.results()) b.getResults().add(CompatItems.copyOf(s));
                    }
                });
        EventBridge.reg(org.domi.furnace.api.event.inventory.BrewingStandFuelEvent.class, org.bukkit.event.inventory.BrewingStandFuelEvent.class,
                f -> new org.bukkit.event.inventory.BrewingStandFuelEvent(Compat.block(f.world(), f.pos()), CompatItems.copyOf(f.fuel()), f.fuelPower()),
                (f, b) -> {
                    if (b.getFuelPower() != f.fuelPower()) f.setFuelPower(b.getFuelPower());
                    if (b.isConsuming() != f.isConsuming()) f.setConsuming(b.isConsuming());
                }, (f, b) -> {
                    if (b.getFuelPower() != f.fuelPower()) b.setFuelPower(f.fuelPower());
                    if (b.isConsuming() != f.isConsuming()) b.setConsuming(f.isConsuming());
                });
    }

    // ------------------------------------------------------------------ 깔때기 옮기기

    /** 용기 종류 키(블록 엔티티·엔티티 종류) → Bukkit 인벤토리 종류. */
    static InventoryType containerType(String key) {
        String k = key.startsWith("minecraft:") ? key.substring(10) : key;
        return switch (k) {
            case "hopper", "hopper_minecart" -> InventoryType.HOPPER;
            case "barrel" -> InventoryType.BARREL;
            case "shulker_box" -> InventoryType.SHULKER_BOX;
            case "furnace" -> InventoryType.FURNACE;
            case "blast_furnace" -> InventoryType.BLAST_FURNACE;
            case "smoker" -> InventoryType.SMOKER;
            case "brewing_stand" -> InventoryType.BREWING;
            case "dispenser" -> InventoryType.DISPENSER;
            case "dropper" -> InventoryType.DROPPER;
            case "crafter" -> InventoryType.CRAFTER;
            case "chiseled_bookshelf" -> InventoryType.CHISELED_BOOKSHELF;
            case "jukebox" -> InventoryType.JUKEBOX;
            case "decorated_pot" -> InventoryType.DECORATED_POT;
            case "composter" -> InventoryType.COMPOSTER;
            case "lectern" -> InventoryType.LECTERN;
            default -> InventoryType.CHEST;   // 상자·덫 상자·큰 상자·상자 수레 등
        };
    }

    /** 이벤트의 용기 사본. 블록이면 Paper 처럼 블록 좌표(모서리), 수레면 엔티티 위치. */
    private static SnapshotInventory container(org.domi.furnace.api.inventory.InventorySnapshot snap, org.domi.furnace.api.world.Location at) {
        InventoryType t = containerType(snap.type());
        boolean entity = snap.type().endsWith("_minecart");
        var w = Compat.world(at.world());
        var loc = entity ? new org.bukkit.Location(w, at.x(), at.y(), at.z())
                : new org.bukkit.Location(w, Math.floor(at.x()), Math.floor(at.y()), Math.floor(at.z()));
        if (t == InventoryType.BREWING) return new EventInventories.Brewer(snap.contents(), snap.size(), snap.type(), loc);
        return new SnapshotInventory(t, snap.contents(), snap.size(), snap.type(), i -> false, loc);
    }

    private static void registerMove() {
        EventBridge.reg(org.domi.furnace.api.event.inventory.InventoryMoveItemEvent.class, org.bukkit.event.inventory.InventoryMoveItemEvent.class,
                f -> new org.bukkit.event.inventory.InventoryMoveItemEvent(container(f.source(), f.sourceLocation()), CompatItems.copyOf(f.item()),
                        container(f.destination(), f.destinationLocation()), f.initiatorIsSource()),
                (f, b) -> {
                    ItemStack now = furnace(b.getItem());
                    if (!now.equals(f.item())) f.setItem(now);
                }, (f, b) -> {
                    if (!furnace(b.getItem()).equals(f.item())) b.setItem(CompatItems.copyOf(f.item()));
                });
    }
}
