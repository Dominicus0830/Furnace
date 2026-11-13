// inventory_changed 가속(T-030)이 결과를 바꾸지 않는지 차분 시험:
// ① 옛 매처(칸을 리스너마다 세는 것)를 시험 코드에 그대로 남겨 같은 기준 JSON·무작위 인벤토리로 대조(지연 사건도 같은지),
//    색인 키는 "그 아이템이 아니면 절대 안 맞는" 필요 조건이어야 한다.
// ② 바닐라 1,399개 발전 과제 플레이어에게 무작위 사건·취소를 같은 순서로 주고, 색인 경로(trigger)와 전부 훑는 기준 경로의
//    달성 순서(advancementDone·awardRecipes 호출 열)와 저장 파일이 같은지 대조.
package org.domi.furnace.server.advancement;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.loot.Bounds;
import org.domi.furnace.server.loot.ItemPredicate;
import org.domi.furnace.server.loot.LootContext;
import org.domi.furnace.server.loot.LootRegistries;
import org.domi.furnace.server.loot.LootStack;
import org.domi.furnace.server.net.protocol.Packet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InventoryChangedIndexTest {

    static final String[] ITEMS = {"minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:cobblestone", "minecraft:dirt",
            "minecraft:oak_log", "minecraft:stone_pickaxe", "minecraft:stone", "minecraft:birch_planks"};

    static final String[] VARIANTS = {
            "{\"items\":[{\"items\":\"minecraft:iron_ingot\"}]}",
            "{\"items\":[{\"items\":[\"minecraft:iron_ingot\",\"minecraft:gold_ingot\"]}]}",
            "{\"items\":[{\"items\":\"iron_ingot\"}]}",
            "{\"items\":[{\"items\":\"#minecraft:logs\"}]}",
            "{\"items\":[{\"items\":\"minecraft:cobblestone\",\"count\":{\"min\":10}}]}",
            "{\"items\":[{\"count\":5}]}",
            "{\"items\":[{\"items\":[]}]}",
            "{\"items\":[{\"items\":\"minecraft:dirt\"},{\"items\":\"minecraft:cobblestone\"}]}",
            "{\"slots\":{\"occupied\":{\"min\":3}},\"items\":[{\"items\":\"minecraft:dirt\"}]}",
            "{\"slots\":{\"empty\":{\"max\":30},\"full\":1}}",
            "{\"slots\":{\"occupied\":2}}",
            "{\"slots\":{\"full\":{\"min\":1}},\"items\":[{\"items\":[\"minecraft:stone\",\"minecraft:dirt\"]}]}",
            "{\"items\":[{\"items\":\"minecraft:stone_pickaxe\",\"predicates\":{\"minecraft:damage\":{\"damage\":{\"min\":0}}}}]}",
            "{}"};

    /** 옛 구현의 매처(T-030 이전 Criterion 그대로): 칸을 이 호출 안에서 센다. */
    static boolean oldMatch(JsonObject c, LootRegistries regs, LootStack[] inventory, LootStack changed) {
        JsonObject slots = c.has("slots") ? c.getAsJsonObject("slots") : new JsonObject();
        Bounds occupied = Bounds.parse(slots.get("occupied")), full = Bounds.parse(slots.get("full")), empty = Bounds.parse(slots.get("empty"));
        List<ItemPredicate> items = new ArrayList<>();
        if (c.has("items")) for (JsonElement x : c.getAsJsonArray("items")) items.add(ItemPredicate.parse(x, regs));
        int f = 0, em = 0, oc = 0;
        for (LootStack s : inventory) {
            if (s == null || s.isEmpty()) em++;
            else { oc++; if (s.count() >= s.maxStackSize()) f++; }
        }
        if (!full.matches(f) || !empty.matches(em) || !occupied.matches(oc)) return false;
        if (items.isEmpty()) return true;
        if (items.size() == 1) return changed != null && !changed.isEmpty() && items.get(0).test(changed, regs);
        List<ItemPredicate> remaining = new ArrayList<>(items);
        for (LootStack s : inventory) {
            if (remaining.isEmpty()) return true;
            if (s != null && !s.isEmpty()) remaining.removeIf(p -> p.test(s, regs));
        }
        return remaining.isEmpty();
    }

    static LootStack[] randomInventory(Random rnd) {
        LootStack[] inv = new LootStack[41];
        double fill = rnd.nextDouble();
        for (int i = 0; i < inv.length; i++) {
            inv[i] = rnd.nextDouble() < fill ? new LootStack(ITEMS[rnd.nextInt(ITEMS.length)], 1 + rnd.nextInt(64)) : LootStack.empty();
            if (rnd.nextInt(10) == 0) inv[i] = null;   // 호출자가 null 을 넘겨도 빈 칸으로 센다
        }
        return inv;
    }

    @Test
    void matcherAndIndexKeyMatchOldImplementation() throws Exception {
        LootRegistries[] regsOut = new LootRegistries[1];
        VanillaAdvancementsTest.vanilla(regsOut);
        LootRegistries regs = regsOut[0];
        Random rnd = new Random(30);
        int trueCount = 0;
        for (String v : VARIANTS) {
            JsonObject cond = JsonParser.parseString(v).getAsJsonObject();
            JsonObject crit = new JsonObject();
            crit.addProperty("trigger", "minecraft:inventory_changed");
            crit.add("conditions", cond);
            Criterion c = Criterion.parse(crit, regs);
            for (int n = 0; n < 4000; n++) {
                LootStack[] inv = randomInventory(rnd);
                LootStack changed = rnd.nextInt(6) == 0 ? LootStack.empty() : rnd.nextInt(8) == 0 ? null
                        : new LootStack(ITEMS[rnd.nextInt(ITEMS.length)], 1 + rnd.nextInt(64));
                boolean old = oldMatch(cond, regs, inv, changed);
                boolean eager = c.matcher().test(new Criterion.InventoryEvent(inv, changed), regs);
                String name = changed == null || changed.isEmpty() ? null : changed.item();
                int[] calls = new int[2];
                boolean lazy = c.matcher().test(new Criterion.InventoryEvent(name, () -> { calls[0]++; return changed; },
                        () -> { calls[1]++; return inv; }, () -> { int oc = 0; for (LootStack x : inv) if (x != null && !x.isEmpty()) oc++; return oc; }), regs);
                assertEquals(old, eager, v + " eager");
                assertEquals(old, lazy, v + " lazy");
                assertTrue(calls[0] <= 1 && calls[1] <= 1, "변환은 사건당 한 번: " + v);
                if (old) {
                    trueCount++;
                    var key = c.inventoryKey();
                    if (key != null) {
                        assertNotNull(name, v);
                        boolean in = key.tag() != null ? regs.tag("minecraft:item", key.tag()).contains(name) : key.items().contains(name);
                        assertTrue(in, "색인 키는 필요 조건: " + v + " / " + name);
                    }
                }
            }
        }
        assertTrue(trueCount > 1000, "시험이 대부분 거짓이면 의미 없다: " + trueCount);
    }

    @Test
    void indexKeysForKnownShapes() throws Exception {
        LootRegistries[] regsOut = new LootRegistries[1];
        VanillaAdvancementsTest.vanilla(regsOut);
        java.util.function.Function<String, Criterion.InventoryKey> key = v -> {
            JsonObject crit = new JsonObject();
            crit.addProperty("trigger", "minecraft:inventory_changed");
            crit.add("conditions", JsonParser.parseString(v));
            return Criterion.parse(crit, regsOut[0]).inventoryKey();
        };
        assertEquals(new Criterion.InventoryKey(java.util.Set.of("minecraft:iron_ingot"), null), key.apply(VARIANTS[0]));
        assertEquals(new Criterion.InventoryKey(java.util.Set.of("minecraft:iron_ingot", "minecraft:gold_ingot"), null), key.apply(VARIANTS[1]));
        assertEquals(new Criterion.InventoryKey(java.util.Set.of("minecraft:iron_ingot"), null), key.apply(VARIANTS[2]));   // 네임스페이스 생략
        assertEquals(new Criterion.InventoryKey(null, "minecraft:logs"), key.apply(VARIANTS[3]));    // 태그
        assertNull(key.apply(VARIANTS[5]));    // items 없는 술어
        assertNull(key.apply(VARIANTS[6]));    // 빈 목록
        assertNull(key.apply(VARIANTS[7]));    // 술어 둘
        assertNull(key.apply(VARIANTS[13]));   // 조건 없음
    }

    // ------------------------------------------------------------------ ② 플레이어 수준

    static final class Rec implements PlayerAdvancements.Host {
        final LootRegistries regs;
        final List<String> done = new ArrayList<>();
        final List<List<ResourceId>> recipes = new ArrayList<>();
        PlayerAdvancements self;

        Rec(LootRegistries regs) { this.regs = regs; }

        public void send(Packet p) {}
        public void awardRecipes(List<ResourceId> r) {
            recipes.add(r);
            for (ResourceId id : r) self.trigger("minecraft:recipe_unlocked", id);
        }
        public void giveExperience(int points) {}
        public void giveLoot(String table) {}
        public void broadcast(Text message) {}
        public boolean announceAdvancements() { return false; }
        public Text displayName() { return Text.text("T"); }
        public LootContext playerContext() { return null; }
        public LootRegistries registries() { return regs; }
        @Override public Text advancementDone(String advancement, Text message) { done.add(advancement); return message; }
    }

    @SuppressWarnings("unchecked")
    static LinkedHashSet<Object> listenerSet(PlayerAdvancements p, String trigger) throws Exception {
        Field f = PlayerAdvancements.class.getDeclaredField("listeners");
        f.setAccessible(true);
        return ((Map<String, LinkedHashSet<Object>>) f.get(p)).get(trigger);
    }

    /** 기준 경로: 대기 표를 표 순서대로 전부 훑어 옛 방식으로 판정하고, 맞은 것을 그 순서로 달성. */
    static void referenceTrigger(PlayerAdvancements p, AdvancementManager m, LootRegistries regs, Criterion.InventoryEvent ev) throws Exception {
        LinkedHashSet<Object> set = listenerSet(p, "minecraft:inventory_changed");
        if (set == null || set.isEmpty()) return;
        List<PlayerAdvancements.Listener> hits = new ArrayList<>();
        for (Object o : set) {
            PlayerAdvancements.Listener l = (PlayerAdvancements.Listener) o;
            Advancement a = m.get(l.advancement());
            Criterion c = a.criteria().get(l.criterion());
            if (c.matcher().test(ev, regs)) hits.add(l);
        }
        for (var l : hits) p.award(m.get(l.advancement()), l.criterion());
    }

    @Test
    void playerHitsAndOrderMatchFullScan(@TempDir Path dir) throws Exception {
        LootRegistries[] regsOut = new LootRegistries[1];
        AdvancementManager m = VanillaAdvancementsTest.vanilla(regsOut);
        LootRegistries regs = regsOut[0];
        Rec ha = new Rec(regs), hb = new Rec(regs);
        Path da = Files.createDirectories(dir.resolve("a")), db = Files.createDirectories(dir.resolve("b"));
        UUID id = UUID.randomUUID();
        PlayerAdvancements a = new PlayerAdvancements(da, id, m, ha), b = new PlayerAdvancements(db, id, m, hb);
        ha.self = a;
        hb.self = b;

        // 색인에 든 아이템과 그렇지 않은 아이템을 섞어 쓴다
        java.util.TreeSet<String> names = new java.util.TreeSet<>();
        for (Advancement adv : m.all()) for (Criterion c : adv.criteria().values()) {
            var k = c.inventoryKey();
            if (k == null) continue;
            if (k.tag() != null) names.addAll(regs.tag("minecraft:item", k.tag())); else names.addAll(k.items());
        }
        List<String> pool = new ArrayList<>(names);
        pool.addAll(List.of("minecraft:dirt", "minecraft:stone", "minecraft:bedrock"));
        List<Advancement> invAdvs = new ArrayList<>();
        for (Advancement adv : m.all()) for (Criterion c : adv.criteria().values()) if (c.trigger().equals("minecraft:inventory_changed")) { invAdvs.add(adv); break; }

        Random rnd = new Random(7);
        int lazyInventoryBuilds = 0, events = 0;
        for (int n = 0; n < 6000; n++) {
            LootStack[] inv = new LootStack[41];
            for (int i = 0; i < inv.length; i++) inv[i] = rnd.nextInt(3) == 0 ? new LootStack(pool.get(rnd.nextInt(pool.size())), 1 + rnd.nextInt(64)) : LootStack.empty();
            LootStack changed = rnd.nextInt(10) == 0 ? LootStack.empty() : new LootStack(pool.get(rnd.nextInt(pool.size())), 1 + rnd.nextInt(64));
            String name = changed.isEmpty() ? null : changed.item();
            int[] built = new int[1];
            a.trigger("minecraft:inventory_changed", new Criterion.InventoryEvent(name, () -> changed, () -> { built[0]++; return inv; },
                    () -> { int oc = 0; for (LootStack x : inv) if (!x.isEmpty()) oc++; return oc; }));
            referenceTrigger(b, m, regs, new Criterion.InventoryEvent(inv, changed));
            lazyInventoryBuilds += built[0];
            events++;
            assertEquals(hb.done, ha.done, "달성 순서(이벤트 " + n + ")");
            assertEquals(hb.recipes, ha.recipes, "레시피 보상 순서(이벤트 " + n + ")");
            if (n % 7 == 0 && !invAdvs.isEmpty()) {
                // 취소·재등록으로 대기 표 순서를 흔든다(같은 작업을 두 플레이어에게)
                Advancement adv = invAdvs.get(rnd.nextInt(invAdvs.size()));
                String crit = adv.criteria().keySet().iterator().next();
                assertEquals(b.revoke(adv, crit), a.revoke(adv, crit));
            }
        }
        assertFalse(ha.done.isEmpty(), "달성이 하나도 없으면 시험이 아니다");
        assertTrue(lazyInventoryBuilds <= events, "칸 변환은 필요한 기준이 있을 때만: " + lazyInventoryBuilds + "/" + events);
        a.save();
        b.save();
        assertEquals(Files.readString(db.resolve("advancements").resolve(id + ".json")).replaceAll("\"[0-9:\\- +]+\"", "T"),
                Files.readString(da.resolve("advancements").resolve(id + ".json")).replaceAll("\"[0-9:\\- +]+\"", "T"), "저장 파일(시각 제외)");
    }
}
