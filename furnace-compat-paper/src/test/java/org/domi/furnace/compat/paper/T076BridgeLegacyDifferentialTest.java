package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiConsumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.enchantments.Enchantment;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** T-076 (PR #321 검토 R2):  처리기 여럿·Furnace 처리기 끼어듦·남겨 둔 참조 변경에서 실제 다리 push/pull = 옛 push/pull (값·setItem 횟수). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class T076BridgeLegacyDifferentialTest {

    FurnaceTestServer server;

    @BeforeAll
    void start() throws Exception {
        Path tmp = java.nio.file.Files.createTempDirectory("r321");
        Path jar = Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
        server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        server.tick(1);
    }

    @AfterAll
    void stop() { server.close(); }

    // 옛 코드(기준 브랜치) 그대로
    static final BiConsumer<org.domi.furnace.api.event.inventory.InventoryMoveItemEvent, org.bukkit.event.inventory.InventoryMoveItemEvent> OLD_PUSH = (f, b) -> {
        var now = CompatItems.toFurnace(b.getItem());
        if (!now.equals(f.item())) f.setItem(now);
    };
    static final BiConsumer<org.domi.furnace.api.event.inventory.InventoryMoveItemEvent, org.bukkit.event.inventory.InventoryMoveItemEvent> OLD_PULL = (f, b) -> {
        if (!CompatItems.toFurnace(b.getItem()).equals(f.item())) b.setItem(CompatItems.copyOf(f.item()));
    };

    @SuppressWarnings("unchecked")
    static Object mapping() throws Exception {
        Field by = EventBridge.class.getDeclaredField("BY_BUKKIT");
        by.setAccessible(true);
        var ms = (List<Object>) ((Map<Class<?>, List<Object>>) by.get(null)).get(org.bukkit.event.inventory.InventoryMoveItemEvent.class);
        assertTrue(ms != null && !ms.isEmpty(), "다리 등록 안 됨");
        return ms.get(0);
    }

    @SuppressWarnings("unchecked")
    static <T> T field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(o);
    }

    static org.domi.furnace.api.item.ItemStack furnaceItem(Random r) {
        String[] types = {"minecraft:stone", "minecraft:diamond", "minecraft:dirt"};
        var s = new org.domi.furnace.api.item.ItemStack(types[r.nextInt(types.length)], 1 + r.nextInt(64));
        if (r.nextBoolean()) s = s.withComponent("minecraft:custom_name", "'n" + r.nextInt(3) + "'");
        return s;
    }

    /** 한 세계(옛 또는 새)의 상태 — 같은 난수열로 같은 연산을 한다. */
    static final class World {
        final org.domi.furnace.api.event.inventory.InventoryMoveItemEvent f;
        final org.bukkit.event.inventory.InventoryMoveItemEvent b;
        final List<org.bukkit.inventory.ItemStack> kept = new ArrayList<>();
        final List<org.domi.furnace.api.item.ItemStack> seenF = new ArrayList<>();
        int fSets, bSets;
        World(org.domi.furnace.api.item.ItemStack start) {
            f = new org.domi.furnace.api.event.inventory.InventoryMoveItemEvent(null, null, null, null, start, true);
            b = new org.bukkit.event.inventory.InventoryMoveItemEvent(null, CompatItems.copyOf(start), null, true) {
                @Override public void setItem(org.bukkit.inventory.ItemStack itemStack) { bSets++; super.setItem(itemStack); }
            };
            seenF.add(start);
        }
    }

    static void bukkitHandler(World w, Random r) {
        var b = w.b;
        switch (r.nextInt(12)) {
            case 0 -> b.getItem().setAmount(r.nextInt(5));
            case 1 -> b.getItem().setAmount(b.getItem().getAmount());
            case 2 -> b.getItem().setType(r.nextBoolean() ? Material.STONE : Material.DIRT);
            case 3 -> {
                var meta = b.getItem().getItemMeta();
                meta.setDisplayName("m" + r.nextInt(2));
                b.getItem().setItemMeta(meta);
            }
            case 4 -> b.getItem().addUnsafeEnchantment(Enchantment.SHARPNESS, 1 + r.nextInt(2));
            case 5 -> b.setItem(new org.bukkit.inventory.ItemStack(r.nextBoolean() ? Material.STONE : Material.DIAMOND, 1 + r.nextInt(3)));
            case 6 -> w.kept.add(b.getItem());                                         // 참조 남겨 두기
            case 7 -> { if (!w.kept.isEmpty()) w.kept.get(r.nextInt(w.kept.size())).setAmount(r.nextInt(4)); else r.nextInt(4); }   // 남긴 참조 변경(낡았을 수도)
            case 8 -> { if (!w.kept.isEmpty()) b.setItem(w.kept.get(r.nextInt(w.kept.size()))); else r.nextInt(1); }
            case 9 -> b.setItem(b.getItem());
            default -> b.getItem().getType();
        }
    }

    static void furnaceHandler(World w, Random r) {
        var f = w.f;
        switch (r.nextInt(6)) {
            case 0 -> { var x = furnaceItem(r); w.seenF.add(x); f.setItem(x); }
            case 1 -> f.setItem(new org.domi.furnace.api.item.ItemStack(f.item().type(), f.item().amount(), f.item().components(), f.item().removedComponents()));
            case 2 -> f.setItem(w.seenF.get(r.nextInt(w.seenF.size())));            // 옛 참조 되돌리기(ABA)
            case 3 -> { w.seenF.add(f.item()); }
            default -> { }
        }
    }

    @Test
    void bridgeEqualsLegacyUnderInterleavedHandlers() throws Exception {
        Object m = mapping();
        BiConsumer<Object, Object> push = field(m, "push");
        BiConsumer<Object, Object> pull = field(m, "pull");
        long seed = 321;
        int skipped = 0;
        for (int round = 0; round < 2000; round++) {
            var start = furnaceItem(new Random(seed + round));
            World oldW = new World(start), newW = new World(start);
            Random ro = new Random(seed * 31 + round), rn = new Random(seed * 31 + round);
            for (int h = 0; h < 12; h++) {
                // Bukkit 처리기 하나: pull → 처리기 → push
                OLD_PULL.accept(oldW.f, oldW.b);
                pull.accept(newW.f, newW.b);
                bukkitHandler(oldW, ro);
                bukkitHandler(newW, rn);
                var ob = oldW.f.item(); var nb = newW.f.item();
                OLD_PUSH.accept(oldW.f, oldW.b);
                push.accept(newW.f, newW.b);
                if (oldW.f.item() != ob) oldW.fSets++;
                if (newW.f.item() != nb) newW.fSets++;
                // 사이에 Furnace 처리기
                furnaceHandler(oldW, ro);
                furnaceHandler(newW, rn);
                // 처리기 밖에서 남긴 참조 고치기(다른 작업)
                if (ro.nextInt(5) == 0 & rn.nextInt(5) == 0) {
                    int a = ro.nextInt(4), c = rn.nextInt(4);
                    if (!oldW.kept.isEmpty()) oldW.kept.get(oldW.kept.size() - 1).setAmount(a);
                    if (!newW.kept.isEmpty()) newW.kept.get(newW.kept.size() - 1).setAmount(c);
                }
                String where = "round " + round + " h " + h;
                assertEquals(oldW.f.item(), newW.f.item(), where);
                assertEquals(oldW.b.getItem(), newW.b.getItem(), where);
                assertEquals(oldW.fSets, newW.fSets, where + " f.setItem 횟수");
                assertEquals(oldW.bSets, newW.bSets, where + " b.setItem 횟수");
            }
        }
        System.out.println("probe ok");
    }
}
