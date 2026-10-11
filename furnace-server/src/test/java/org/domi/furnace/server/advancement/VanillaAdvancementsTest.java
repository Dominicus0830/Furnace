// 바닐라 1.21.1 과 같은지: 발전 과제 1,399개 로드·매니저 순서(바닐라 서버에서 뽑은 순서의 SHA-256)·뿌리 순서·배치 x,
// 그리고 새 플레이어 첫 틱의 레시피 ADD → update_advancements 바이트(바닐라 캡처, 진행 맵 순서·시각은 바닐라도 매번 달라 제외).
package org.domi.furnace.server.advancement;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.data.PackRepository;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.ResourceManager;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.loot.LootContext;
import org.domi.furnace.server.loot.LootRegistries;
import org.domi.furnace.server.loot.NetworkComponents;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VanillaAdvancementsTest {

    static final String VANILLA_ORDER_SHA256 = "9e67cbb2c8f1ef0b1172a07f19e139f84c5d261ee8f72607c5cdcef5872eab55";
    static final String VANILLA_FIRST_PACKET_PREFIX =
            "01022c6d696e6563726166743a726563697065732f6465636f726174696f6e732f6372616674696e675f7461626c6501166d696e6563726166743a726563697065732f726f6f740001020e6861735f7468655f72656369706511756e6c6f636b5f72696768745f6177617900166d696e6563726166743a726563697065732f726f6f74000001010a696d706f737369626c650000";

    static AdvancementManager vanilla(LootRegistries[] regsOut) throws Exception {
        Path root = Path.of("../tools/vanilla-data/server-1.21.1");
        Assumptions.assumeTrue(Files.isDirectory(root.resolve("data/minecraft/advancement")), "로컬 바닐라 데이터 없음");
        PackRepository repo = new PackRepository(root, null);
        repo.reload();
        repo.setSelected(List.of("vanilla"));
        TagRegistries tags = new TagRegistries(RegistrySet.load("registries/1.21.1.txt"));
        try (ResourceManager rm = repo.openResourceManager()) {
            LootRegistries regs = new LootRegistries(tags, tags.loadAll(rm));
            if (regsOut != null) regsOut[0] = regs;
            return AdvancementManager.load(rm, regs, new NetworkComponents(tags));
        }
    }

    @Test
    void loadsLikeVanilla() throws Exception {
        AdvancementManager m = vanilla(null);
        assertEquals(1399, m.all().size());
        assertEquals(1399, m.treeSize());
        String order = String.join("\n", m.all().stream().map(a -> a.id().toString()).toList());
        assertEquals(VANILLA_ORDER_SHA256, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(order.getBytes())));
        assertEquals(List.of("minecraft:nether/root", "minecraft:adventure/root", "minecraft:end/root", "minecraft:husbandry/root",
                "minecraft:story/root", "minecraft:recipes/root"), m.roots().stream().map(n -> n.advancement().id().toString()).toList());
        // x = 표시 있는 조상 깊이(바닐라와 항상 같다). y 는 바닐라에서도 자식 순서(정체성 해시)에 따라 달라진다.
        assertEquals(0.0f, m.get(ResourceId.parse("story/root")).display().x());
        assertEquals(1.0f, m.get(ResourceId.parse("nether/return_to_sender")).display().x());
        assertEquals(2.0f, m.get(ResourceId.parse("nether/uneasy_alliance")).display().x());
    }

    @Test
    void firstTickSendsRecipeThenAdvancementsLikeVanilla(@TempDir Path dir) throws Exception {
        LootRegistries[] regs = new LootRegistries[1];
        AdvancementManager m = vanilla(regs);
        List<Packet> sent = new ArrayList<>();
        List<List<ResourceId>> recipes = new ArrayList<>();
        PlayerAdvancements[] self = new PlayerAdvancements[1];
        PlayerAdvancements.Host host = new PlayerAdvancements.Host() {
            public void send(Packet p) { sent.add(p); }
            public void awardRecipes(List<ResourceId> r) {
                recipes.add(r);
                for (ResourceId id : r) self[0].trigger("minecraft:recipe_unlocked", id);
            }
            public void giveExperience(int points) {}
            public void giveLoot(String table) {}
            public void broadcast(Text message) { fail("레시피 발전 과제는 알리지 않는다"); }
            public boolean announceAdvancements() { return true; }
            public Text displayName() { return Text.text("Capture"); }
            public LootContext playerContext() { return null; }
            public LootRegistries registries() { return regs[0]; }
        };
        self[0] = new PlayerAdvancements(dir, java.util.UUID.randomUUID(), m, host);
        self[0].trigger("minecraft:tick", null);
        self[0].flushDirty();
        assertEquals(List.of(List.of(ResourceId.parse("crafting_table"))), recipes);
        assertEquals(1, sent.size());
        ByteBuf buf = Unpooled.buffer();
        sent.get(0).write(buf);
        String hex = HexFormat.of().formatHex(buf.array(), 0, buf.writerIndex());
        assertTrue(hex.startsWith(VANILLA_FIRST_PACKET_PREFIX), hex);
        String progress = hex.substring(VANILLA_FIRST_PACKET_PREFIX.length());
        // 진행 2개: recipes/root{impossible: 없음}, crafting_table{unlock_right_away: 시각, has_the_recipe: 없음}
        String root = "166d696e6563726166743a726563697065732f726f6f74010a696d706f737369626c6500";
        String tablePrefix = "2c6d696e6563726166743a726563697065732f6465636f726174696f6e732f6372616674696e675f7461626c650211756e6c6f636b5f72696768745f6177617901";
        assertTrue(progress.startsWith("02"));
        String rest = progress.substring(2);
        String tableTail = "0e6861735f7468655f72656369706500";
        String tableEntry = rest.startsWith(root) ? rest.substring(root.length()) : rest.substring(0, rest.length() - root.length());
        assertTrue(rest.startsWith(root) || rest.endsWith(root), rest);
        assertTrue(tableEntry.startsWith(tablePrefix) && tableEntry.endsWith(tableTail) && tableEntry.length() == tablePrefix.length() + 16 + tableTail.length(), tableEntry);
        // 다음 틱에는 보낼 것이 없다
        self[0].trigger("minecraft:tick", null);
        self[0].flushDirty();
        assertEquals(1, sent.size());
    }

    @Test
    void saveAndReloadKeepsProgress(@TempDir Path dir) throws Exception {
        LootRegistries[] regs = new LootRegistries[1];
        AdvancementManager m = vanilla(regs);
        List<Packet> sent = new ArrayList<>();
        PlayerAdvancements.Host host = new PlayerAdvancements.Host() {
            public void send(Packet p) { sent.add(p); }
            public void awardRecipes(List<ResourceId> r) {}
            public void giveExperience(int points) {}
            public void giveLoot(String table) {}
            public void broadcast(Text message) {}
            public boolean announceAdvancements() { return true; }
            public Text displayName() { return Text.text("Capture"); }
            public LootContext playerContext() { return null; }
            public LootRegistries registries() { return regs[0]; }
        };
        java.util.UUID id = java.util.UUID.randomUUID();
        PlayerAdvancements a = new PlayerAdvancements(dir, id, m, host);
        Advancement story = m.get(ResourceId.parse("story/root"));
        assertTrue(a.award(story, "crafting_table"));
        a.save();
        String json = Files.readString(dir.resolve("advancements").resolve(id + ".json"));
        assertTrue(json.contains("\"minecraft:story/root\": {\n    \"criteria\": {\n      \"crafting_table\": "), json);
        assertTrue(json.endsWith("\"DataVersion\": 3955\n}"), json);
        PlayerAdvancements b = new PlayerAdvancements(dir, id, m, host);
        assertTrue(b.getOrStartProgress(story).isDone());
        b.flushDirty();
        assertFalse(sent.isEmpty());
        assertTrue(((UpdateAdvancements) sent.get(sent.size() - 1)).reset());
    }
}
