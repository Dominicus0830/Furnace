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

    /** 저장 파일의 시각 값("yyyy-MM-dd HH:mm:ss Z") — 달성 시각은 두 플레이어가 다르므로 지우고 비교한다. */
    static final String TIME_STRING = "\"\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d [+-]\\d{4}\"";

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
        String fb = Files.readString(db.resolve("advancements").resolve(id + ".json")).replaceAll(TIME_STRING, "T");
        String fa = Files.readString(da.resolve("advancements").resolve(id + ".json")).replaceAll(TIME_STRING, "T");
        if (!fa.equals(fb)) {
            String[] la = fa.split("\n"), lb = fb.split("\n");
            for (int i = 0; i < Math.min(la.length, lb.length); i++) if (!la[i].equals(lb[i])) fail("저장 파일 첫 차이 줄 " + i + ": [" + la[i] + "] vs 기준 [" + lb[i] + "] (줄 수 " + la.length + "/" + lb.length + ")");
            fail("저장 파일 줄 수 " + la.length + "/" + lb.length);
        }
    }

    // ------------------------------------------------------------------ player 조건(공유 난수)·레지스트리 객체

    /** player 조건이 공유 난수를 쓰므로 판정 순서가 곧 결과다. */
    static final class RandomHost implements PlayerAdvancements.Host {
        final LootRegistries regs;
        final org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource random;
        final List<String> done = new ArrayList<>();

        RandomHost(LootRegistries regs, long seed) {
            this.regs = regs;
            this.random = new org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource(seed);
        }

        public void send(Packet p) {}
        public void awardRecipes(List<ResourceId> r) {}
        public void giveExperience(int points) {}
        public void giveLoot(String table) {}
        public void broadcast(Text message) {}
        public boolean announceAdvancements() { return false; }
        public Text displayName() { return Text.text("T"); }
        public LootContext playerContext() { return new LootContext(null, null, new org.domi.furnace.server.loot.LootParams(), random); }
        public LootRegistries registries() { return regs; }
        @Override public Text advancementDone(String advancement, Text message) { done.add(advancement); return message; }
    }

    /** load 를 거치지 않고 발전 과제 몇 개만으로 매니저를 만든다(regs 는 null — 태그 기준은 항상 후보). */
    static AdvancementManager manager(Map<ResourceId, Advancement> all) throws Exception {
        var k = AdvancementManager.class.getDeclaredConstructor(Map.class);
        k.setAccessible(true);
        AdvancementManager m = k.newInstance(all);
        var b = AdvancementManager.class.getDeclaredMethod("buildTree");
        b.setAccessible(true);
        b.invoke(m);
        return m;
    }

    /** 옛 전부 훑기: 표 순서로 매처 → player 조건, 맞은 것을 차례로 달성. */
    static void referenceWithPlayer(PlayerAdvancements p, AdvancementManager m, RandomHost h, Object ev) throws Exception {
        LinkedHashSet<Object> set = listenerSet(p, "minecraft:inventory_changed");
        if (set == null || set.isEmpty()) return;
        LootContext ctx = null;
        List<PlayerAdvancements.Listener> hits = new ArrayList<>();
        for (Object o : set) {
            var l = (PlayerAdvancements.Listener) o;
            Criterion c = m.get(l.advancement()).criteria().get(l.criterion());
            if (!c.matcher().test(ev, h.regs)) continue;
            if (!c.player().isEmpty()) {
                if (ctx == null) ctx = h.playerContext();
                if (!org.domi.furnace.server.loot.LootCondition.allOf(c.player(), ctx)) continue;
            }
            hits.add(l);
        }
        for (var l : hits) p.award(m.get(l.advancement()), l.criterion());
    }

    static Advancement adv(String id, String conditions, LootRegistries regs) {
        return Advancement.parse(ResourceId.parse(id), JsonParser.parseString(
                "{\"criteria\":{\"c\":{\"trigger\":\"minecraft:inventory_changed\",\"conditions\":" + conditions + "}}}"), regs);
    }

    /** 리뷰 R1: 후보(색인 아이템 → general 순)가 표 순서와 달라도 player 조건(random_chance)은 표 순서로 판정해야 한다. */
    @Test
    void playerConditionsAreEvaluatedInListenerOrder(@TempDir Path dir) throws Exception {
        LootRegistries[] regsOut = new LootRegistries[1];
        VanillaAdvancementsTest.vanilla(regsOut);
        LootRegistries regs = regsOut[0];
        String rc = "\"player\":[{\"condition\":\"minecraft:random_chance\",\"chance\":0.5}]";
        Map<ResourceId, Advancement> all = new java.util.LinkedHashMap<>();
        // 표 순서: general 먼저, dirt 색인 기준 나중 (색인 경로는 dirt 후보를 먼저 만난다)
        all.put(ResourceId.parse("probe:g"), adv("probe:g", "{" + rc + "}", regs));
        all.put(ResourceId.parse("probe:d"), adv("probe:d", "{" + rc + ",\"items\":[{\"items\":\"minecraft:dirt\"}]}", regs));
        AdvancementManager m = manager(all);
        RandomHost ha = new RandomHost(regs, 42), hb = new RandomHost(regs, 42);
        UUID id = UUID.randomUUID();
        PlayerAdvancements a = new PlayerAdvancements(Files.createDirectories(dir.resolve("a")), id, m, ha);
        PlayerAdvancements b = new PlayerAdvancements(Files.createDirectories(dir.resolve("b")), id, m, hb);
        LootStack[] inv = new LootStack[41];
        java.util.Arrays.fill(inv, LootStack.empty());
        LootStack dirt = new LootStack("minecraft:dirt", 1);
        int both = 0, single = 0;
        for (int n = 0; n < 400; n++) {
            a.trigger("minecraft:inventory_changed", new Criterion.InventoryEvent(inv, dirt));
            referenceWithPlayer(b, m, hb, new Criterion.InventoryEvent(inv, dirt));
            assertEquals(hb.done, ha.done, "이벤트 " + n);
            if (ha.done.size() == 2) both++; else if (ha.done.size() == 1) single++;
            ha.done.clear();
            hb.done.clear();
            for (Advancement x : all.values()) { a.revoke(x, "c"); b.revoke(x, "c"); }
        }
        // 둘 다 후보인 사건(정렬 경로)과 난수가 가른 사건이 모두 있어야 시험이 의미 있다
        assertTrue(both > 50 && single > 50, "정렬 경로·난수 분기가 실제로 일어나야 한다: 둘 다 " + both + ", 하나 " + single);
    }

    /** 리뷰 R2: 호스트의 레지스트리 객체가 매니저가 읽을 때 쓴 것과 달라도(리로드 사이) 색인이 전부 훑기와 같다. */
    @Test
    void indexUsesManagerRegistriesNotHostRegistries(@TempDir Path dir) throws Exception {
        java.nio.file.Path root = Path.of("../tools/vanilla-data/server-1.21.1");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(root.resolve("data/minecraft/advancement")), "로컬 바닐라 데이터 없음");
        var repo = new org.domi.furnace.server.data.PackRepository(root, null);
        repo.reload();
        repo.setSelected(List.of("vanilla"));
        var tags = new org.domi.furnace.server.data.TagRegistries(org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt"));
        AdvancementManager m;
        LootRegistries regs2;
        try (var rm = repo.openResourceManager()) {
            var ids = tags.loadAll(rm);
            LootRegistries regs1 = new LootRegistries(tags, ids);
            m = AdvancementManager.load(rm, regs1, new org.domi.furnace.server.loot.NetworkComponents(tags));
            // 리로드가 태그를 바꾼 상황: 아이템 태그에서 oak_log 를 뺀 새 레지스트리 객체
            int oak = tags.id("minecraft:item", ResourceId.parse("oak_log"));
            Map<String, Map<String, int[]>> ids2 = new java.util.HashMap<>(ids);
            Map<String, int[]> items2 = new java.util.HashMap<>();
            for (var e : ids.get("minecraft:item").entrySet()) items2.put(e.getKey(), java.util.Arrays.stream(e.getValue()).filter(i -> i != oak).toArray());
            ids2.put("minecraft:item", items2);
            regs2 = new LootRegistries(tags, ids2);
        }
        RandomHost ha = new RandomHost(regs2, 1), hb = new RandomHost(regs2, 1);
        UUID id = UUID.randomUUID();
        PlayerAdvancements a = new PlayerAdvancements(Files.createDirectories(dir.resolve("a")), id, m, ha);
        PlayerAdvancements b = new PlayerAdvancements(Files.createDirectories(dir.resolve("b")), id, m, hb);
        LootStack[] inv = new LootStack[41];
        java.util.Arrays.fill(inv, LootStack.empty());
        LootStack oak = new LootStack("minecraft:oak_log", 1);
        a.trigger("minecraft:inventory_changed", new Criterion.InventoryEvent(inv, oak));
        referenceWithPlayer(b, m, hb, new Criterion.InventoryEvent(inv, oak));
        assertFalse(hb.done.isEmpty(), "oak_log 로 달성되는 것이 있어야 한다(oak_planks 등)");
        assertEquals(hb.done, ha.done);
    }
}
