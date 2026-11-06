// 게임플레이 트리거 연결 — 바닐라 1.21.1 발전 과제로 트리거별 달성, 중복 달성 방지, 저장·재로드 뒤 다시 일어나지 않음,
// 처치 점수 기준(totalKillCount·playerKillCount·teamkill·killedByTeam·통계 기준) 갱신.
package org.domi.furnace.server.advancement;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.data.PackRepository;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.ResourceManager;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.KillCredit;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.loot.LootBlockState;
import org.domi.furnace.server.loot.LootContext;
import org.domi.furnace.server.loot.LootData;
import org.domi.furnace.server.loot.LootEntity;
import org.domi.furnace.server.loot.LootParams;
import org.domi.furnace.server.loot.LootRegistries;
import org.domi.furnace.server.loot.LootStack;
import org.domi.furnace.server.loot.LootWorld;
import org.domi.furnace.server.loot.NetworkComponents;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.scores.ChatFormatting;
import org.domi.furnace.server.scores.Objective;
import org.domi.furnace.server.scores.ObjectiveCriteria;
import org.domi.furnace.server.scores.PlayerTeam;
import org.domi.furnace.server.scores.ScoreHolder;
import org.domi.furnace.server.scores.ServerScoreboard;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdvancementTriggersTest {

    static AdvancementManager manager;
    static LootRegistries regs;
    static LootData loot;

    @BeforeAll
    static void load() throws Exception {
        Path root = Path.of("../tools/vanilla-data/server-1.21.1");
        Assumptions.assumeTrue(java.nio.file.Files.isDirectory(root.resolve("data/minecraft/advancement")), "로컬 바닐라 데이터 없음");
        PackRepository repo = new PackRepository(root, null);
        repo.reload();
        repo.setSelected(List.of("vanilla"));
        TagRegistries tags = new TagRegistries(RegistrySet.load("registries/1.21.1.txt"));
        try (ResourceManager rm = repo.openResourceManager()) {
            regs = new LootRegistries(tags, tags.loadAll(rm));
            loot = LootData.load(rm, regs);
            manager = AdvancementManager.load(rm, regs, new NetworkComponents(tags));
        }
    }

    /** 플레이어는 (0, 64, 0). */
    static final class Host implements PlayerAdvancements.Host {
        final List<Packet> sent = new ArrayList<>();
        int xp;
        final List<String> functions = new ArrayList<>();
        final NoiseRandom random = new XoroshiroRandomSource(1L);
        final LootWorld world = new LootWorld() {
            public NoiseRandom random() { return random; }
            public NoiseRandom randomSequence(ResourceId id) { return random; }
        };

        public void send(Packet p) { sent.add(p); }
        public void awardRecipes(List<ResourceId> r) {}
        public void giveExperience(int points) { xp += points; }
        public void giveLoot(String table) {}
        public void broadcast(Text message) {}
        public boolean announceAdvancements() { return false; }
        public Text displayName() { return Text.text("P"); }
        public LootContext playerContext() {
            LootEntity me = new LootEntity() {
                public String type() { return "minecraft:player"; }
                public double[] position() { return new double[]{0, 64, 0}; }
            };
            var params = new LootParams().with(LootParams.THIS_ENTITY, me).with(LootParams.ORIGIN, new double[]{0, 64, 0});
            return new LootContext(loot, world, params, random);
        }
        public LootRegistries registries() { return regs; }
        @Override public void runFunction(String f) { functions.add(f); }
    }

    static LootEntity entity(String type, double x, double y, double z) {
        return new LootEntity() {
            public String type() { return type; }
            public double[] position() { return new double[]{x, y, z}; }
        };
    }

    static boolean done(PlayerAdvancements a, String adv, String criterion) {
        var p = a.getOrStartProgress(manager.get(ResourceId.parse(adv)));
        return p.criterion(criterion) != null && p.criterion(criterion).isDone();
    }

    @Test
    void playerKilledEntityMatchesEntityAndKillingBlow(@TempDir Path dir) {
        Host h = new Host();
        PlayerAdvancements a = new PlayerAdvancements(dir, UUID.randomUUID(), manager, h);
        a.triggerKill("minecraft:player_killed_entity", entity("minecraft:zombie", 3, 64, 0), KillCredit.source("minecraft:player_attack", null, null));
        assertTrue(done(a, "adventure/root", "killed_something"));
        assertTrue(done(a, "adventure/kill_a_mob", "minecraft:zombie"));
        assertFalse(done(a, "adventure/kill_a_mob", "minecraft:skeleton"));
        // 저격 대결: 스켈레톤, 수평 50 이상, 투사체 피해
        a.triggerKill("minecraft:player_killed_entity", entity("minecraft:skeleton", 60, 64, 0), KillCredit.source("minecraft:player_attack", null, null));
        assertFalse(done(a, "adventure/sniper_duel", "killed_skeleton"), "투사체가 아니면 안 된다");
        a.triggerKill("minecraft:player_killed_entity", entity("minecraft:skeleton", 10, 64, 0), KillCredit.source("minecraft:arrow", null, null));
        assertFalse(done(a, "adventure/sniper_duel", "killed_skeleton"), "가까우면 안 된다");
        int before = h.xp;
        a.triggerKill("minecraft:player_killed_entity", entity("minecraft:skeleton", 60, 64, 0), KillCredit.source("minecraft:arrow", null, null));
        assertTrue(done(a, "adventure/sniper_duel", "killed_skeleton"));
        assertEquals(50, h.xp - before, "sniper_duel 보상 경험치 50(kill_a_mob 은 첫 처치 때 이미 끝났다)");
    }

    @Test
    void awardsOnceAndSurvivesReload(@TempDir Path dir) {
        Host h = new Host();
        UUID id = UUID.randomUUID();
        PlayerAdvancements a = new PlayerAdvancements(dir, id, manager, h);
        a.trigger("minecraft:tick", null);
        a.flushDirty();
        a.triggerKill("minecraft:entity_killed_player", entity("minecraft:zombie", 1, 64, 0), KillCredit.source("minecraft:mob_attack", null, null));
        assertTrue(done(a, "adventure/root", "killed_by_something"));
        a.flushDirty();
        int packets = h.sent.size();
        // 이미 이룬 조건은 다시 일으켜도 바뀌지 않는다(듣는 쪽이 빠졌다)
        a.triggerKill("minecraft:entity_killed_player", entity("minecraft:zombie", 1, 64, 0), KillCredit.source("minecraft:mob_attack", null, null));
        a.flushDirty();
        assertEquals(packets, h.sent.size());
        a.save();
        PlayerAdvancements b = new PlayerAdvancements(dir, id, manager, h);
        assertTrue(done(b, "adventure/root", "killed_by_something"));
        assertTrue(b.getOrStartProgress(manager.get(ResourceId.parse("adventure/root"))).isDone());
        assertFalse(b.hasListeners("minecraft:entity_killed_player"), "재로드 뒤 이룬 과제의 듣는 쪽은 다시 등록되지 않는다");
    }

    @Test
    void totemTriggersUsedTotem(@TempDir Path dir) {
        org.domi.furnace.server.item.ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
        Player p = new Player(7, UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
        p.vitals().loadGameTypes(null, org.domi.furnace.server.world.GameMode.SURVIVAL, false);
        var dry = new org.domi.furnace.server.entity.PlayerVitals.Surroundings(true, false, false, false, null, false, -64);
        for (int i = 0; i < 60; i++) p.vitals().tick(dry);   // 접속 무적 지나기
        PlayerAdvancements a = new PlayerAdvancements(dir, p.uuid(), manager, new Host());
        p.advancements(a);
        var inv = p.inventory();
        inv.set(inv.heldWindowSlot(), new org.domi.furnace.server.entity.ItemStack(
                org.domi.furnace.server.item.ItemContext.id("minecraft:item", "minecraft:totem_of_undying"), 1));
        assertFalse(done(a, "adventure/totem_of_undying", "used_totem"));
        p.vitals().hurt(org.domi.furnace.server.entity.damage.DamageType.GENERIC, 100f);
        assertEquals(1f, p.vitals().health(), 0.0f);
        assertTrue(done(a, "adventure/totem_of_undying", "used_totem"));
    }

    @Test
    void husbandryAndTradeTriggers(@TempDir Path dir) {
        Host h = new Host();
        PlayerAdvancements a = new PlayerAdvancements(dir, UUID.randomUUID(), manager, h);
        a.triggerBred(entity("minecraft:cow", 1, 64, 0), entity("minecraft:cow", 2, 64, 0), entity("minecraft:cow", 1, 64, 1));
        assertTrue(done(a, "husbandry/breed_an_animal", "bred"));
        assertTrue(done(a, "husbandry/bred_all_animals", "minecraft:cow"));
        assertFalse(done(a, "husbandry/bred_all_animals", "minecraft:pig"));
        a.triggerTame(entity("minecraft:wolf", 1, 64, 0));
        assertTrue(done(a, "husbandry/tame_an_animal", "tamed_animal"));
        a.triggerTrade(entity("minecraft:villager", 1, 64, 0), new LootStack("minecraft:emerald", 1));
        assertTrue(done(a, "adventure/trade", "traded"));
        a.trigger("minecraft:consume_item", new LootStack("minecraft:apple", 1));
        assertTrue(done(a, "husbandry/root", "consumed_item"));
        a.trigger("minecraft:filled_bucket", new LootStack("minecraft:cod_bucket", 1));
        assertTrue(done(a, "husbandry/tactical_fishing", "cod_bucket"));
        assertFalse(done(a, "husbandry/tactical_fishing", "salmon_bucket"));
        assertTrue(a.hasListeners("minecraft:enter_block"));
        a.trigger("minecraft:enter_block", LootBlockState.parse("minecraft:stone"));
        assertFalse(done(a, "end/enter_end_gateway", "entered_end_gateway"));
        a.trigger("minecraft:enter_block", LootBlockState.parse("minecraft:end_gateway"));
        assertTrue(done(a, "end/enter_end_gateway", "entered_end_gateway"));
    }

    @Test
    void killScoreUpdatesCriteria() {
        ServerScoreboard board = new ServerScoreboard(List::of, null);
        Player killer = new Player(1, UUID.randomUUID(), "K", new ConcurrentEmbeddedChannel());
        Player victim = new Player(2, UUID.randomUUID(), "V", new ConcurrentEmbeddedChannel());
        killer.scoreboard(board);
        victim.scoreboard(board);
        Objective total = board.addObjective("total", ObjectiveCriteria.KILL_COUNT_ALL, Text.text("t"), ObjectiveCriteria.RenderType.INTEGER, false, null);
        Objective players = board.addObjective("players", ObjectiveCriteria.KILL_COUNT_PLAYERS, Text.text("p"), ObjectiveCriteria.RenderType.INTEGER, false, null);
        Objective tk = board.addObjective("tk", ObjectiveCriteria.byName("teamkill.red").orElseThrow(), Text.text("tk"), ObjectiveCriteria.RenderType.INTEGER, false, null);
        Objective kbt = board.addObjective("kbt", ObjectiveCriteria.byName("killedByTeam.blue").orElseThrow(), Text.text("kbt"), ObjectiveCriteria.RenderType.INTEGER, false, null);
        Objective pk = board.addObjective("pk", ObjectiveCriteria.byName("minecraft.custom:minecraft.player_kills").orElseThrow(), Text.text("pk"), ObjectiveCriteria.RenderType.INTEGER, false, null);
        PlayerTeam red = board.addPlayerTeam("r");
        red.color(ChatFormatting.RED);
        PlayerTeam blue = board.addPlayerTeam("b");
        blue.color(ChatFormatting.BLUE);
        board.addPlayerToTeam("V", red);
        board.addPlayerToTeam("K", blue);
        ScoreHolder k = ScoreHolder.forNameOnly("K"), v = ScoreHolder.forNameOnly("V");
        // 다른 region(주인 아닌 스레드)에서 죽었다: 킬러 상태는 그 자리에서 바뀌지 않고 킬러 주인 틱에 반영된다
        Thread other = new Thread(() -> KillCredit.award(killer, victim, KillCredit.source("minecraft:player_attack", killer, killer)));
        other.start();
        try { other.join(); } catch (InterruptedException e) { throw new AssertionError(e); }
        assertNull(board.getPlayerScoreInfo(k, total), "주인 스레드 밖에서는 바로 바꾸지 않는다");
        killer.drainOwnerTasks();
        victim.drainOwnerTasks();
        assertEquals(1, board.getPlayerScoreInfo(k, total).value());
        assertEquals(1, board.getPlayerScoreInfo(k, players).value());
        assertEquals(1, board.getPlayerScoreInfo(k, tk).value());
        assertEquals(1, board.getPlayerScoreInfo(v, kbt).value());
        assertEquals(1, board.getPlayerScoreInfo(k, pk).value());
        assertNull(board.getPlayerScoreInfo(v, total), "죽은 쪽은 처치 수가 없다");
        // 자기 자신을 죽인 것은 세지 않는다(ServerPlayer.awardKillScore 의 killed != this)
        KillCredit.award(killer, killer, KillCredit.source("minecraft:player_attack", killer, killer));
        killer.drainOwnerTasks();
        assertEquals(1, board.getPlayerScoreInfo(k, total).value());
    }
}
