package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.domi.furnace.api.event.LazyValue;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-076 — Bukkit 이벤트 내용 게으르게.
 * (a) {@code BlockPhysicsEvent} 의 블록 데이터는 처리기가 처음 읽을 때 만든다. 값·종류는 미리 만든 것과 같다.
 * (b) 깔때기 {@code InventoryMoveItemEvent} 의 처리기마다 앞뒤 아이템 비교는 어댑터 {@code ItemStack} 의 변경 번호로 건너뛴다 —
 * 번호가 거짓 "같음"을 만들지 않으려면 값을 바꾸는 모든 메서드가 번호를 올려야 하므로 반사로 전수 확인한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class T076LazyEventContentTest {

    FurnaceTestServer server;

    @BeforeAll
    void start() throws Exception {
        Path tmp = java.nio.file.Files.createTempDirectory("t076");
        Path jar = Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, java.util.Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
        server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        server.join("Dana");
        server.tick(2);
    }

    @AfterAll
    void stop() { server.close(); }

    // ------------------------------------------------------------------ (b) 변경 번호

    /** 값을 바꾸는 공개 메서드. 새 메서드가 생기면 아래 두 목록 어디에도 없어 시험이 깨진다 — 바꾸는지 아닌지 정하게 한다. */
    private static final Set<String> MUTATORS = Set.of("setType", "setAmount", "setDurability", "setItemMeta", "addEnchantment", "addEnchantments",
            "addUnsafeEnchantment", "addUnsafeEnchantments", "removeEnchantment");

    /** 값을 바꾸지 않는 공개 메서드(읽기·복사·직렬화·번호 자체). */
    private static final Set<String> READERS = Set.of("asFurnace", "changeStamp", "getType", "getAmount", "isEmpty", "getMaxStackSize", "getDurability",
            "getPersistentDataContainer", "hasItemMeta", "getItemMeta", "getEnchantments", "containsEnchantment", "getEnchantmentLevel", "serialize",
            "deserialize", "isSimilar", "equals", "hashCode", "clone", "toString");

    @Test
    void everyPublicMethodOfItemStackIsClassifiedAsMutatorOrReader() {
        Set<String> unknown = new TreeSet<>();
        Set<String> seen = new TreeSet<>();
        for (Method m : org.bukkit.inventory.ItemStack.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic()) continue;
            seen.add(m.getName());
            if (!MUTATORS.contains(m.getName()) && !READERS.contains(m.getName())) unknown.add(m.getName());
        }
        assertTrue(unknown.isEmpty(), "분류 안 된 공개 메서드(값을 바꾸면 PlainSlot 쓰기를 거쳐 번호가 올라야 한다): " + unknown);
        for (String name : MUTATORS) assertTrue(seen.contains(name), "분류표에만 있는 메서드: " + name);
        for (String name : READERS) assertTrue(seen.contains(name) || name.equals("getPersistentDataContainer"), "분류표에만 있는 메서드: " + name);
    }

    private static org.bukkit.inventory.ItemStack fresh() {
        var s = new org.bukkit.inventory.ItemStack(Material.DIAMOND_SWORD, 3);
        s.addUnsafeEnchantment(Enchantment.SHARPNESS, 2);
        return s;
    }

    @Test
    void everyMutatorRaisesTheStamp() {
        List<java.util.function.Consumer<org.bukkit.inventory.ItemStack>> ops = new ArrayList<>();
        ops.add(s -> s.setType(Material.STONE));
        ops.add(s -> s.setAmount(7));
        ops.add(s -> s.setDurability((short) 5));
        ops.add(s -> {
            var meta = s.getItemMeta();
            meta.setDisplayName("x");
            s.setItemMeta(meta);
        });
        ops.add(s -> s.setItemMeta(null));
        ops.add(s -> s.addEnchantment(Enchantment.SHARPNESS, 4));
        ops.add(s -> s.addEnchantments(java.util.Map.of(Enchantment.SHARPNESS, 5)));
        ops.add(s -> s.addUnsafeEnchantment(Enchantment.SHARPNESS, 9));
        ops.add(s -> s.addUnsafeEnchantments(java.util.Map.of(Enchantment.SHARPNESS, 8)));
        ops.add(s -> assertEquals(2, s.removeEnchantment(Enchantment.SHARPNESS)));
        for (int i = 0; i < ops.size(); i++) {
            var s = fresh();
            long before = s.changeStamp();
            assertTrue(before >= 0, "평범한 스택은 번호를 추적한다");
            ops.get(i).accept(s);
            assertTrue(s.changeStamp() > before, "변경 연산 " + i + " 가 번호를 올리지 않았다");
        }
        // 연산 이름이 분류표 변경 목록을 모두 덮는지: 위 목록이 쓰는 메서드 이름 = MUTATORS
        assertEquals(MUTATORS.size(), 9);
    }

    @Test
    void readersDoNotChangeTheStampOrTheValue() {
        var s = fresh();
        long stamp = s.changeStamp();
        var value = s.asFurnace();
        s.getType();
        s.getAmount();
        s.isEmpty();
        s.getMaxStackSize();
        s.getDurability();
        s.getPersistentDataContainer();
        s.hasItemMeta();
        s.getItemMeta();
        s.getEnchantments();
        s.getEnchantmentLevel(Enchantment.SHARPNESS);
        s.serialize();
        s.hashCode();
        s.toString();
        s.clone();
        assertEquals(stamp, s.changeStamp());
        assertSame(value, s.asFurnace());
    }

    @Test
    void cloneStartsItsOwnStamp() {
        var s = fresh();
        s.setAmount(9);
        var c = s.clone();
        assertNotSame(s, c);
        assertEquals(s, c);
        long cs = c.changeStamp();
        s.setAmount(10);
        assertEquals(cs, c.changeStamp(), "원본을 바꿔도 사본의 번호는 그대로");
        c.setAmount(1);
        assertTrue(c.changeStamp() > cs);
    }

    @Test
    void mirrorStacksAreNotTracked() {
        // 인벤토리 칸을 비추는 스택은 번호가 없다(-1) — 칸 값은 밖에서도 바뀌므로 늘 값으로 비교
        var s = new org.bukkit.inventory.ItemStack(new ItemSlot() {
            org.domi.furnace.api.item.ItemStack v = org.domi.furnace.api.item.ItemStack.of("minecraft:stone");
            @Override public org.domi.furnace.api.item.ItemStack get() { return v; }
            @Override public void set(org.domi.furnace.api.item.ItemStack x) { v = x; }
        });
        assertEquals(-1, s.changeStamp());
    }

    private static org.domi.furnace.api.item.ItemStack furnaceItem(Random r) {
        String[] types = {"minecraft:stone", "minecraft:diamond", "minecraft:dirt"};
        var s = new org.domi.furnace.api.item.ItemStack(types[r.nextInt(types.length)], 1 + r.nextInt(64));
        if (r.nextBoolean()) s = s.withComponent("minecraft:custom_name", "'n" + r.nextInt(3) + "'");
        return s;
    }

    /** 옛 비교와 새 비교가 어떤 연산 순서에서도 같은 답을 내는가 — Bukkit 쪽 변경·교체, Furnace 쪽 교체(같은 값 다른 참조 포함). */
    @Test
    void moveItemComparisonMatchesTheLegacyComparisonUnderRandomOperations() {
        Random r = new Random(76);
        int skipHits = 0, steps = 0;
        for (int round = 0; round < 300; round++) {
            var f = new org.domi.furnace.api.event.inventory.InventoryMoveItemEvent(null, null, null, null, furnaceItem(r), true);
            var b = new org.bukkit.event.inventory.InventoryMoveItemEvent(null, CompatItems.copyOf(f.item()), null, true);
            for (int step = 0; step < 40; step++) {
                switch (r.nextInt(14)) {
                    case 0 -> b.getItem().setAmount(r.nextInt(70));
                    case 1 -> b.getItem().setType(r.nextBoolean() ? Material.STONE : Material.DIRT);
                    case 2 -> b.getItem().setDurability((short) r.nextInt(3));
                    case 3 -> {
                        var meta = b.getItem().getItemMeta();
                        meta.setDisplayName("m" + r.nextInt(3));
                        b.getItem().setItemMeta(meta);
                    }
                    case 4 -> b.getItem().addUnsafeEnchantment(Enchantment.SHARPNESS, 1 + r.nextInt(3));
                    case 5 -> b.getItem().removeEnchantment(Enchantment.SHARPNESS);
                    case 6 -> b.setItem(new org.bukkit.inventory.ItemStack(r.nextBoolean() ? Material.STONE : Material.DIAMOND, 1 + r.nextInt(3)));
                    case 7 -> b.setItem(b.getItem());                                 // 같은 값의 새 객체
                    case 8 -> f.setItem(furnaceItem(r));                              // Furnace 처리기가 바꿈
                    case 9 -> f.setItem(CompatItems.toFurnace(b.getItem()));          // 같은 값(Bukkit 쪽에서 되쓴 것)
                    case 10 -> f.setItem(new org.domi.furnace.api.item.ItemStack(f.item().type(), f.item().amount(), f.item().components(), f.item().removedComponents()));   // 같은 값 다른 참조
                    case 11 -> b.getItem().setAmount(b.getItem().getAmount());        // 값은 그대로 쓰기
                    case 12 -> b.setItem(CompatItems.copyOf(f.item()));               // 되돌려 맞춤
                    default -> { /* 아무것도 안 함 — 연속 조회 */ }
                }
                boolean legacy = CompatItems.toFurnace(b.getItem()).equals(f.item());
                if (legacy) skipHits++;
                assertEquals(legacy, InventoryResultMappings.moveItemMatches(f, b), "라운드 " + round + " 단계 " + step);
                // 같은 상태에서 한 번 더 — 기억한 같음을 쓰는 길도 같은 답
                assertEquals(legacy, InventoryResultMappings.moveItemMatches(f, b), "라운드 " + round + " 단계 " + step + " (재조회)");
                steps++;
            }
        }
        assertTrue(skipHits > 1000, "같음 경우가 충분히 나와야 시험이 의미 있다: " + skipHits + "/" + steps);
    }

    /** 하위 클래스는 getAmount 를 덮어써 번호 밖에서 값이 바뀔 수 있다 — 추적하지 않고 늘 값으로 비교한다. */
    @Test
    void subclassedStackIsAlwaysComparedByValue() {
        AtomicInteger amount = new AtomicInteger(5);
        var tricky = new org.bukkit.inventory.ItemStack(Material.STONE, 5) {
            @Override public int getAmount() { return amount.get(); }
        };
        var f = new org.domi.furnace.api.event.inventory.InventoryMoveItemEvent(null, null, null, null,
                org.domi.furnace.api.item.ItemStack.of("minecraft:stone").withAmount(5), true);
        var b = new org.bukkit.event.inventory.InventoryMoveItemEvent(null, tricky, null, true);
        assertTrue(InventoryResultMappings.moveItemMatches(f, b));
        amount.set(0);   // 번호는 안 변하지만 값(toFurnace → EMPTY)은 달라진다
        assertFalse(CompatItems.toFurnace(b.getItem()).equals(f.item()));
        assertFalse(InventoryResultMappings.moveItemMatches(f, b));
        amount.set(5);
        assertTrue(InventoryResultMappings.moveItemMatches(f, b));
    }

    // ------------------------------------------------------------------ (a) BlockPhysicsEvent

    /** 모든 블록 상태: 게으른 Bukkit 이벤트의 값·종류 = 미리 만든 것. 두 번 읽어도 사본은 서로 다른 객체이고 같은 값. */
    @Test
    void lazyBukkitPhysicsEventEqualsTheEagerOneForEveryState() {
        var types = Compat.furnace().blockTypes();
        int checked = 0;
        for (int id = 0; id < 26684; id += 1) {
            var d = types.byStateId(id);
            if (d.isEmpty()) continue;
            var data = d.get();
            var eager = new BlockPhysicsEvent((org.bukkit.block.Block) null, CompatBlockData.of(data), (org.bukkit.block.Block) null);
            var lazy = new BlockPhysicsEvent((org.bukkit.block.Block) null, LazyValue.of(data, CompatBlockData::of), (org.bukkit.block.Block) null);
            assertEquals(eager.getChangedType(), lazy.getChangedType(), "상태 " + id);
            assertEquals(eager.getChangedBlockData(), lazy.getChangedBlockData(), "상태 " + id);
            assertEquals(eager.getChangedBlockData().getAsString(), lazy.getChangedBlockData().getAsString(), "상태 " + id);
            assertNotSame(lazy.getChangedBlockData(), lazy.getChangedBlockData(), "읽을 때마다 사본(Paper 의 clone)");
            checked++;
        }
        assertTrue(checked > 20000, "확인한 상태 수 " + checked);
    }

    @Test
    void publicConstructorsStillTakeEagerValues() {
        var data = Compat.furnace().blockTypes().get("minecraft:stone").orElseThrow().defaultData();
        var a = new BlockPhysicsEvent((org.bukkit.block.Block) null, CompatBlockData.of(data));
        assertEquals(Material.STONE, a.getChangedType());
        var b = new BlockPhysicsEvent((org.bukkit.block.Block) null, CompatBlockData.of(data), (org.bukkit.block.Block) null);
        assertEquals(Material.STONE, b.getChangedType());
        assertEquals(CompatBlockData.of(data), b.getChangedBlockData());
    }

    /** 실제 다리: 좌표만 보는 처리기는 블록 값을 만들지 않고, 읽는 처리기는 한 번만 만든다. */
    @Test
    void bridgeBuildsTheBlockDataOnlyWhenAHandlerReadsIt() throws Exception {
        RecorderPlugin.reset();
        {
            FurnaceTestServer s = server;
            var data = Compat.furnace().blockTypes().get("minecraft:oak_stairs").orElseThrow().defaultData();
            List<String> log = new CopyOnWriteArrayList<>();
            AtomicInteger conversions = new AtomicInteger();
            RecorderPlugin.ACTIONS.put("BlockPhysicsEvent", e -> {
                var ev = (BlockPhysicsEvent) e;
                log.add(ev.getBlock().getX() + "," + ev.getBlock().getY() + "," + ev.getSourceBlock().getY());
            });
            var f1 = new org.domi.furnace.api.event.block.BlockPhysicsEvent("minecraft:overworld", new BlockPos(1, 64, 2),
                    LazyValue.of(data, d -> { conversions.incrementAndGet(); return d; }), new BlockPos(1, 65, 2), LazyValue.ready("minecraft:stone"));
            s.api().events().call(f1);
            assertEquals(List.of("1,64,65"), log);
            assertEquals(0, conversions.get(), "좌표만 보는 처리기는 블록 값을 만들지 않는다");

            RecorderPlugin.ACTIONS.put("BlockPhysicsEvent", e -> {
                var ev = (BlockPhysicsEvent) e;
                log.add(ev.getChangedType() + "|" + ev.getChangedBlockData().getAsString());
                ev.getChangedBlockData();
            });
            var f2 = new org.domi.furnace.api.event.block.BlockPhysicsEvent("minecraft:overworld", new BlockPos(1, 64, 2),
                    LazyValue.of(data, d -> { conversions.incrementAndGet(); return d; }), new BlockPos(1, 65, 2), LazyValue.ready("minecraft:stone"));
            s.api().events().call(f2);
            s.api().events().call(f2);
            assertEquals("OAK_STAIRS|" + data, log.get(1));
            assertEquals(log.get(1), log.get(2));
            assertEquals(1, conversions.get(), "읽는 처리기가 있어도 변환은 이벤트당 한 번");
        }
    }

    /** 깔때기 실제 다리: 처리기 여럿이 있어도(아이템을 안 건드림/건드림) 결과 아이템이 옛 동작과 같다. */
    @Test
    void bridgeWritesBackOnlyWhatHandlersChanged() throws Exception {
        RecorderPlugin.reset();
        {
            FurnaceTestServer s = server;
            String w = "minecraft:overworld";
            var snap = new org.domi.furnace.api.inventory.InventorySnapshot("minecraft:chest", java.util.Collections.nCopies(27, org.domi.furnace.api.item.ItemStack.EMPTY));
            var loc = new org.domi.furnace.api.world.Location(w, 1.5, 65.5, 2.5);
            var stone = org.domi.furnace.api.item.ItemStack.of("minecraft:stone").withAmount(1);

            // 안 건드림: 같은 객체 그대로
            RecorderPlugin.ACTIONS.put("InventoryMoveItemEvent", e -> ((org.bukkit.event.inventory.InventoryMoveItemEvent) e).getItem().getType());
            var f0 = new org.domi.furnace.api.event.inventory.InventoryMoveItemEvent(snap, loc, snap, loc, stone, true);
            s.api().events().call(f0);
            assertSame(stone, f0.item());

            // 제자리 수정(Paper 의 getItem 은 사본 없이 같은 객체 — 고치면 반영된다)
            RecorderPlugin.ACTIONS.put("InventoryMoveItemEvent", e -> ((org.bukkit.event.inventory.InventoryMoveItemEvent) e).getItem().setAmount(9));
            var f1 = new org.domi.furnace.api.event.inventory.InventoryMoveItemEvent(snap, loc, snap, loc, stone, true);
            s.api().events().call(f1);
            assertEquals(org.domi.furnace.api.item.ItemStack.of("minecraft:stone").withAmount(9), f1.item());

            // 메타 수정
            RecorderPlugin.ACTIONS.put("InventoryMoveItemEvent", e -> {
                var it = ((org.bukkit.event.inventory.InventoryMoveItemEvent) e).getItem();
                var meta = it.getItemMeta();
                meta.setDisplayName("hop");
                it.setItemMeta(meta);
            });
            var f2 = new org.domi.furnace.api.event.inventory.InventoryMoveItemEvent(snap, loc, snap, loc, stone, true);
            s.api().events().call(f2);
            assertTrue(f2.item().components().toString().contains("hop"), f2.item().toString());

            // setItem 으로 통째 교체 + 같은 값 되쓰기
            RecorderPlugin.ACTIONS.put("InventoryMoveItemEvent", e -> ((org.bukkit.event.inventory.InventoryMoveItemEvent) e)
                    .setItem(new org.bukkit.inventory.ItemStack(Material.DIAMOND, 2)));
            var f3 = new org.domi.furnace.api.event.inventory.InventoryMoveItemEvent(snap, loc, snap, loc, stone, true);
            s.api().events().call(f3);
            assertEquals(org.domi.furnace.api.item.ItemStack.of("minecraft:diamond").withAmount(2), f3.item());
        }
    }
}
