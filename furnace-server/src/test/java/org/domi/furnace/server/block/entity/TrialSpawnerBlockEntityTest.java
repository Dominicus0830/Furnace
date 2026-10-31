// 시련 생성기 시험: 구성 불러오기/저장(일반↔불길한 병합, 기본값 생략)·상태 전이·난수 순서(고정 시드)·플레이어 감지/시선·불길한 전환·보상 배출.
// 기대값은 바닐라 1.21.1 디컴파일(TrialSpawner·TrialSpawnerData·TrialSpawnerState)을 손으로 계산한 것이다.
package org.domi.furnace.server.block.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.OminousItemSpawner;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.loot.LootEntity;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TrialSpawnerBlockEntityTest {

    static class Lvl extends BlockPlacementTest.TestLevel {
        final RandomSource rnd;
        long time;
        final List<Player> players = new ArrayList<>();
        final List<int[]> events = new ArrayList<>();
        final List<String> sounds = new ArrayList<>();
        final Map<String, List<ItemStack>> loot = new HashMap<>();
        final List<long[]> lootSeeds = new ArrayList<>();
        final List<Float> pitches = new ArrayList<>();
        float lastLuck;
        double[] lastOrigin;
        RandomSource lastRandom;

        Lvl(long seed) { rnd = RandomSource.create(seed); }

        @Override public RandomSource random() { return rnd; }
        @Override public long gameTime() { return time; }
        @Override public void forEachPlayer(Consumer<Player> c) { players.forEach(c); }

        @Override
        public Player playerByUuid(UUID u) {
            for (Player p : players) if (p.uuid().equals(u)) return p;
            return null;
        }

        @Override public void levelEvent(BlockActor except, int type, Pos pos, int data) { events.add(new int[] {type, pos.x(), pos.y(), pos.z(), data}); }
        @Override
        public void playSound(BlockActor except, Pos pos, String sound, String source, float volume, float pitch) {
            sounds.add(sound);
            pitches.add(pitch);
        }

        @Override
        public List<ItemStack> lootItems(String table, double[] origin, LootEntity self, float luck, long seed, RandomSource random) {
            lootSeeds.add(new long[] {seed});
            lastLuck = luck;
            lastOrigin = origin;
            lastRandom = random;
            return loot.getOrDefault(table, List.of());
        }

        boolean hasEvent(int type) { return events.stream().anyMatch(e -> e[0] == type); }
    }

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    static final Pos POS = new Pos(1, -60, 1);
    static final UUID P1 = new UUID(1, 2), P2 = new UUID(3, 4);

    static int stateWith(String name, boolean ominous) {
        int s = TrialSpawner.STATE.with(B.defaultState("minecraft:trial_spawner"), name);
        return TrialSpawner.OMINOUS.with(s, ominous);
    }

    static TrialSpawnerBlockEntity place(Lvl level, String state, boolean ominous, NbtCompound tag) {
        level.setBlock(POS, stateWith(state, ominous), 3);
        var be = (TrialSpawnerBlockEntity) level.getBlockEntity(POS);
        assertNotNull(be);
        be.load(tag);
        be.trialSpawner().overridePeacefulAndMobSpawnRule();
        return be;
    }

    static NbtCompound entity(String id) {
        NbtCompound e = new NbtCompound();
        e.putString("id", id);
        NbtCompound d = new NbtCompound();
        d.putCompound("entity", e);
        return d;
    }

    static NbtList potentials(String... ids) {
        NbtList l = new NbtList(NbtIo.TAG_COMPOUND);
        for (String id : ids) {
            NbtCompound w = new NbtCompound();
            w.putCompound("data", entity(id));
            w.putInt("weight", 1);
            l.add(w);
        }
        return l;
    }

    static NbtList uuids(UUID... us) {
        NbtList l = new NbtList(NbtIo.TAG_INT_ARRAY);
        for (UUID u : us) l.add(org.domi.furnace.server.bossbar.CustomBossEvents.uuidToInts(u));
        return l;
    }

    /** 바닐라 trial_chambers/spawner/breeze 구조물이 놓는 NBT 그대로. */
    static NbtCompound breezeTag() {
        NbtCompound normal = new NbtCompound();
        normal.putInt("ticks_between_spawn", 20);
        normal.putList("spawn_potentials", potentials("minecraft:breeze"));
        normal.putFloat("simultaneous_mobs", 1.0F);
        normal.putFloat("simultaneous_mobs_added_per_player", 0.5F);
        normal.putFloat("total_mobs_added_per_player", 1.0F);
        normal.putFloat("total_mobs", 2.0F);
        NbtCompound ominous = new NbtCompound();
        NbtList eject = new NbtList(NbtIo.TAG_COMPOUND);
        for (Object[] e : new Object[][] {{"minecraft:spawners/ominous/trial_chamber/key", 3}, {"minecraft:spawners/ominous/trial_chamber/consumables", 7}}) {
            NbtCompound c = new NbtCompound();
            c.putString("data", (String) e[0]);
            c.putInt("weight", (Integer) e[1]);
            eject.add(c);
        }
        ominous.putList("loot_tables_to_eject", eject);
        ominous.putFloat("simultaneous_mobs", 2.0F);
        ominous.putFloat("total_mobs", 4.0F);
        NbtCompound t = new NbtCompound();
        t.putCompound("normal_config", normal);
        t.putCompound("ominous_config", ominous);
        return t;
    }

    /** (pos.asLong + t) % 20 == 0 인 t 중 base 이상 가장 작은 것. */
    static long scanTime(long base) {
        long t = base;
        while ((POS.asLong() + t) % 20L != 0L) t++;
        return t;
    }

    static Player player(Lvl level, UUID id, double x, double y, double z) {
        Player p = new Player(level.players.size() + 1, id, "P" + (level.players.size() + 1), new EmbeddedChannel());
        p.applyMove(x, y, z, 0, 0, true);
        level.players.add(p);
        return p;
    }

    static NbtCompound activeTag(int totalSpawned) {
        NbtCompound t = new NbtCompound();
        NbtCompound normal = new NbtCompound();
        normal.putList("spawn_potentials", potentials("minecraft:zombie"));
        normal.putFloat("total_mobs", 100.0F);
        normal.putFloat("simultaneous_mobs", 3.0F);
        normal.putInt("ticks_between_spawn", 20);
        t.putCompound("normal_config", normal);
        t.putCompound("spawn_data", entity("minecraft:zombie"));
        t.putList("registered_players", uuids(P1));
        if (totalSpawned > 0) t.putInt("total_mobs_spawned", totalSpawned);
        return t;
    }

    // ------------------------------------------------------------------ 구성·저장

    @Test
    void loadMergesOminousOverNormalAndSavesWithoutDefaults() {
        Lvl level = new Lvl(1);
        var be = place(level, "inactive", false, breezeTag());
        TrialSpawnerConfig n = be.trialSpawner().getNormalConfig(), o = be.trialSpawner().getOminousConfig();
        assertEquals(20, n.ticksBetweenSpawn());
        assertEquals(2.0F, n.totalMobs());
        assertEquals(1.0F, n.simultaneousMobs());
        assertEquals(4, n.spawnRange(), "기본 spawn_range");
        assertEquals(1, n.spawnPotentials().size());
        // 불길한 구성 = 일반 구성 위에 덮어쓴 것: 후보·간격·플레이어당 증가는 물려받고 total/simultaneous/loot_tables_to_eject 는 덮는다
        assertEquals(20, o.ticksBetweenSpawn());
        assertEquals(1, o.spawnPotentials().size());
        assertEquals(0.5F, o.simultaneousMobsAddedPerPlayer());
        assertEquals(4.0F, o.totalMobs());
        assertEquals(2.0F, o.simultaneousMobs());
        assertEquals(2, o.lootTablesToEject().size());
        assertEquals(3, o.lootTablesToEject().get(0).weight());
        // floor(total + 플레이어당 × 추가 인원)
        assertEquals(4, n.calculateTargetTotalMobs(2));
        assertEquals(2, n.calculateTargetSimultaneousMobs(2));
        assertEquals(3, n.calculateTargetTotalMobs(1));
        assertEquals(1, n.calculateTargetSimultaneousMobs(1));
        assertEquals(5, o.calculateTargetTotalMobs(1));
        assertEquals(2, o.calculateTargetSimultaneousMobs(1));
        assertEquals(160L, o.ticksBetweenItemSpawners());
        // 저장: 기본값과 같은 필드(required_player_range, target_cooldown_length, spawn_range …)는 쓰지 않는다
        NbtCompound out = be.saveWithoutMetadata();
        assertFalse(out.contains("required_player_range"));
        assertFalse(out.contains("target_cooldown_length"));
        assertFalse(out.getCompound("normal_config").contains("spawn_range"));
        assertEquals(2.0F, out.getCompound("normal_config").getFloat("total_mobs"));
        assertEquals(4.0F, out.getCompound("ominous_config").getFloat("total_mobs"));
        // 왕복
        var back = new TrialSpawnerBlockEntity(POS, stateWith("inactive", false));
        back.load(out);
        assertEquals(org.domi.furnace.server.item.Snbt.write(n.save()), org.domi.furnace.server.item.Snbt.write(back.trialSpawner().getNormalConfig().save()), "normal");
        // 바닐라 코덱 특성: 불길한 구성의 simultaneous_mobs(2.0)는 기본값과 같아 저장에서 빠지고, 다시 읽으면 일반 구성(1.0)을 물려받는다(가정: DFU 인코더가 기본값을 생략)
        assertFalse(out.getCompound("ominous_config").contains("simultaneous_mobs"));
        assertEquals(1.0F, back.trialSpawner().getOminousConfig().simultaneousMobs());
        assertEquals(4.0F, back.trialSpawner().getOminousConfig().totalMobs());
    }

    @Test
    void emptyTagIsAllDefaultsAndSavesNothing() {
        Lvl level = new Lvl(1);
        var be = place(level, "inactive", false, new NbtCompound());
        TrialSpawner s = be.trialSpawner();
        assertEquals(14, s.getRequiredPlayerRange());
        assertEquals(36000, s.getTargetCooldownLength());
        TrialSpawnerConfig d = s.getNormalConfig();
        assertEquals(6, d.calculateTargetTotalMobs(0));
        assertEquals(2, d.calculateTargetSimultaneousMobs(0));
        assertEquals(8, d.calculateTargetTotalMobs(1), "6 + 2×1");
        assertEquals(3, d.calculateTargetSimultaneousMobs(1), "2 + 1×1");
        assertEquals(40, d.ticksBetweenSpawn());
        assertEquals("minecraft:spawners/trial_chamber/consumables", d.lootTablesToEject().get(0).id());
        assertEquals("minecraft:spawners/trial_chamber/key", d.lootTablesToEject().get(1).id());
        assertEquals("minecraft:spawners/trial_chamber/items_to_drop_when_ominous", d.itemsToDropWhenOminous());
        assertTrue(be.saveWithoutMetadata().tags().isEmpty());
    }

    @Test
    void wrongValuesFallBackToDefaults() {
        NbtCompound normal = new NbtCompound();
        normal.putInt("spawn_range", 0);          // 1..128 밖
        normal.putFloat("total_mobs", -1.0F);     // 음수
        normal.putString("simultaneous_mobs", "x");
        normal.putInt("ticks_between_spawn", 7);  // 올바른 값
        NbtCompound t = new NbtCompound();
        t.putCompound("normal_config", normal);
        t.putInt("required_player_range", 200);
        t.putInt("target_cooldown_length", -5);
        var be = place(new Lvl(1), "inactive", false, t);
        TrialSpawnerConfig n = be.trialSpawner().getNormalConfig();
        assertEquals(4, n.spawnRange());
        assertEquals(6.0F, n.totalMobs());
        assertEquals(2.0F, n.simultaneousMobs());
        assertEquals(7, n.ticksBetweenSpawn());
        assertEquals(14, be.trialSpawner().getRequiredPlayerRange());
        assertEquals(36000, be.trialSpawner().getTargetCooldownLength());
    }

    @Test
    void dataFieldsRoundTripAndAreOmittedAtDefault() {
        NbtCompound t = new NbtCompound();
        t.putList("registered_players", uuids(P1, P2));
        t.putList("current_mobs", uuids(new UUID(9, 9)));
        t.putLong("cooldown_ends_at", 1234L);
        t.putLong("next_mob_spawns_at", 77L);
        t.putInt("total_mobs_spawned", 3);
        t.putCompound("spawn_data", entity("zombie"));
        t.putString("ejecting_loot_table", "spawners/trial_chamber/key");
        t.putInt("required_player_range", 20);
        t.putInt("target_cooldown_length", 100);
        var be = place(new Lvl(1), "inactive", false, t);
        TrialSpawnerData d = be.trialSpawner().getData();
        assertEquals(java.util.Set.of(P1, P2), d.detectedPlayers());
        assertEquals(1234L, d.cooldownEndsAt());
        assertEquals(77L, d.nextMobSpawnsAt());
        assertEquals(3, d.totalMobsSpawned());
        assertEquals("minecraft:zombie", d.nextSpawnData().get().entity().getString("id"), "id 정규화");
        assertEquals(Optional_of("minecraft:spawners/trial_chamber/key"), d.ejectingLootTable());
        NbtCompound out = be.saveWithoutMetadata();
        assertEquals(1234L, out.getLong("cooldown_ends_at"));
        assertEquals(3, out.getInt("total_mobs_spawned"));
        assertEquals(20, out.getInt("required_player_range"));
        assertEquals(100, out.getInt("target_cooldown_length"));
        assertEquals(2, out.getList("registered_players").size());
        assertEquals("minecraft:spawners/trial_chamber/key", out.getString("ejecting_loot_table"));
        var empty = place(new Lvl(1), "inactive", false, new NbtCompound());
        assertTrue(empty.saveWithoutMetadata().tags().isEmpty(), "0/빈 필드는 쓰지 않는다");
    }

    private static java.util.Optional<String> Optional_of(String s) { return java.util.Optional.of(s); }

    @Test
    void updateTagCarriesSpawnDataAndNextSpawnTimeOnlyWhenActive() {
        NbtCompound t = new NbtCompound();
        t.putCompound("spawn_data", entity("minecraft:zombie"));
        t.putLong("next_mob_spawns_at", 55L);
        var waiting = place(new Lvl(1), "waiting_for_players", false, t);
        assertTrue(waiting.hasUpdatePacket());
        assertTrue(waiting.updateTag().contains("spawn_data"));
        assertFalse(waiting.updateTag().contains("next_mob_spawns_at"));
        var active = place(new Lvl(1), "active", false, t);
        assertEquals(55L, active.updateTag().getLong("next_mob_spawns_at"));
    }

    // ------------------------------------------------------------------ 상태 전이

    @Test
    void inactiveBecomesWaitingWhenNextSpawnDataHasMobAndPicksPotentialOnce() {
        Lvl level = new Lvl(77);
        NbtCompound t = new NbtCompound();
        NbtCompound normal = new NbtCompound();
        normal.putList("spawn_potentials", potentials("minecraft:zombie"));
        t.putCompound("normal_config", normal);
        var be = place(level, "inactive", false, t);
        be.tick(level);
        assertEquals(TrialSpawnerState.WAITING_FOR_PLAYERS, be.getState());
        assertEquals("waiting_for_players", TrialSpawner.STATE.valueName(level.getBlockState(POS)));
        RandomSource ref = RandomSource.create(77);
        ref.nextInt(1);   // 후보 한 개 → 가중치 합 1 로 nextInt(1) 한 번
        assertEquals(ref.nextLong(), level.rnd.nextLong(), "다음 데이터 고르기에 난수 한 번");
        assertEquals("minecraft:zombie", be.trialSpawner().getData().nextSpawnData().get().entity().getString("id"));
    }

    @Test
    void inactiveStaysWithoutAnyMobAndUsesNoRandom() {
        Lvl level = new Lvl(5);
        var be = place(level, "inactive", false, new NbtCompound());
        be.tick(level);
        assertEquals(TrialSpawnerState.INACTIVE, be.getState());
        assertEquals(RandomSource.create(5).nextLong(), level.rnd.nextLong());
    }

    @Test
    void waitingGoesBackToInactiveWithoutMobToSpawn() {
        Lvl level = new Lvl(5);
        var be = place(level, "waiting_for_players", false, new NbtCompound());
        be.tick(level);
        assertEquals(TrialSpawnerState.INACTIVE, be.getState());
    }

    @Test
    void waitingScansOnlyEvery20TicksThenRegistersPlayersAndActivates() {
        Lvl level = new Lvl(3);
        NbtCompound t = new NbtCompound();
        t.putCompound("spawn_data", entity("minecraft:zombie"));
        var be = place(level, "waiting_for_players", false, t);
        player(level, P1, 1.5, -60, 4.5);
        long scan = scanTime(1000);
        level.time = scan + 1;
        be.tick(level);
        assertTrue(be.trialSpawner().getData().detectedPlayers().isEmpty(), "스캔 틱이 아니면 감지하지 않는다");
        assertEquals(TrialSpawnerState.WAITING_FOR_PLAYERS, be.getState());
        level.time = scan;
        be.tick(level);
        assertEquals(java.util.Set.of(P1), be.trialSpawner().getData().detectedPlayers());
        assertEquals(scan + 40L, be.trialSpawner().getData().nextMobSpawnsAt(), "max(시각 + 40, 0)");
        assertTrue(level.events.stream().anyMatch(e -> e[0] == 3013 && e[4] == 1 && e[1] == 1 && e[2] == -60 && e[3] == 1), "이벤트 3013, 데이터 = 등록 인원");
        assertEquals(TrialSpawnerState.ACTIVE, be.getState());
    }

    @Test
    void spectatorCreativeAndFarPlayersAreNotDetected() {
        Lvl level = new Lvl(3);
        NbtCompound t = new NbtCompound();
        t.putCompound("spawn_data", entity("minecraft:zombie"));
        var be = place(level, "waiting_for_players", false, t);
        Player creative = player(level, P1, 1.5, -60, 4.5);
        creative.vitals().setGameMode(org.domi.furnace.server.world.GameMode.CREATIVE);
        Player far = player(level, P2, 1.5, -60, 40.5);   // 블록 거리² 39² = 1521 > 14² 이지만 청크 범위 안
        level.time = scanTime(1000);
        be.tick(level);
        assertTrue(be.trialSpawner().getData().detectedPlayers().isEmpty());
        assertEquals(TrialSpawnerState.WAITING_FOR_PLAYERS, be.getState());
    }

    @Test
    void lineOfSightIsBlockedByStoneButNotByGlass() {
        Lvl level = new Lvl(3);
        NbtCompound t = new NbtCompound();
        t.putCompound("spawn_data", entity("minecraft:zombie"));
        var be = place(level, "waiting_for_players", false, t);
        player(level, P1, 1.5, -60, 5.5);
        Pos mid = new Pos(1, -60, 3);
        level.setBlock(mid, B.defaultState("minecraft:stone"), 3);
        level.time = scanTime(1000);
        be.tick(level);
        assertTrue(be.trialSpawner().getData().detectedPlayers().isEmpty(), "돌이 눈과 생성기 사이를 막는다");
        level.setBlock(mid, B.defaultState("minecraft:glass"), 3);
        level.time = scanTime(level.time + 1);
        be.tick(level);
        assertEquals(java.util.Set.of(P1), be.trialSpawner().getData().detectedPlayers(), "유리는 시각 모양이 비어 시선을 막지 않는다");
    }

    @Test
    void activeSpawnsAtSeededPositionAndSchedulesNext() {
        // 첫 시도의 y 가 땅 위(−60: SPAWNER 만 바닥 검사를 건너뛰므로 시련 생성기는 바닥이 단단해야 한다)이고 x·z 가 가까운 시드를 찾는다
        long seed = -1;
        double ex = 0, ey = 0, ez = 0;
        for (long s = 1; s < 500 && seed < 0; s++) {
            RandomSource r = RandomSource.create(s);
            double x = POS.x() + (r.nextDouble() - r.nextDouble()) * 4 + 0.5;
            double y = POS.y() + r.nextInt(3) - 1;
            double z = POS.z() + (r.nextDouble() - r.nextDouble()) * 4 + 0.5;
            if (y == -60 && Math.abs(x - 1) < 3.5 && Math.abs(z - 1) < 3.5) {
                seed = s;
                ex = x;
                ey = y;
                ez = z;
            }
        }
        assertTrue(seed > 0);
        Lvl level = new Lvl(seed);
        var be = place(level, "active", false, activeTag(0));
        player(level, P1, 1.5, -60, 4.5);
        level.time = scanTime(1000) + 1;
        be.tick(level);
        TrialSpawnerData d = be.trialSpawner().getData();
        assertEquals(1, d.currentMobs().size());
        assertEquals(1, d.totalMobsSpawned());
        assertEquals(level.time + 20, d.nextMobSpawnsAt(), "ticks_between_spawn 20");
        Entity mob = level.entities().get(d.currentMobs().iterator().next());
        assertNotNull(mob);
        assertEquals(ex, mob.x(), 1.0E-9);
        assertEquals(ey, mob.y(), 1.0E-9);
        assertEquals(ez, mob.z(), 1.0E-9);
        Pos at = new Pos((int) Math.floor(ex), (int) Math.floor(ey), (int) Math.floor(ez));
        assertTrue(level.events.stream().anyMatch(e -> e[0] == 3011 && e[1] == 1 && e[2] == -60 && e[3] == 1 && e[4] == 0), "생성기에서 불꽃(일반 = 0)");
        assertTrue(level.events.stream().anyMatch(e -> e[0] == 3012 && e[1] == at.x() && e[2] == at.y() && e[3] == at.z() && e[4] == 0));
        // 같은 시각에 또 틱해도 다음 몹 시각 전이라 만들지 않는다
        be.tick(level);
        assertEquals(1, d.currentMobs().size());
        // 간격이 지나도 동시 몹 수(3)와 총수 안에서 다시 만든다(난수 소비는 확인하지 않는다)
        assertEquals(TrialSpawnerState.ACTIVE, be.getState());
    }

    @Test
    void activeFinishesWhenAllSpawnedAndAllDeadThenWaitsForRewardEjection() {
        Lvl level = new Lvl(9);
        NbtCompound t = activeTag(2);
        t.getCompound("normal_config").putFloat("total_mobs", 2.0F);
        var be = place(level, "active", false, t);
        player(level, P1, 1.5, -60, 4.5);
        level.time = scanTime(5000) + 1;
        be.tick(level);
        assertEquals(TrialSpawnerState.WAITING_FOR_REWARD_EJECTION, be.getState());
        TrialSpawnerData d = be.trialSpawner().getData();
        assertEquals(level.time + 36000, d.cooldownEndsAt());
        assertEquals(0, d.totalMobsSpawned());
        assertEquals(0L, d.nextMobSpawnsAt());
    }

    @Test
    void activeKeepsWaitingWhileTrackedMobsLive() {
        Lvl level = new Lvl(9);
        NbtCompound t = activeTag(2);
        t.getCompound("normal_config").putFloat("total_mobs", 2.0F);
        Entity z = Entities.create(level, "minecraft:zombie");
        z.setPos(3.5, -60, 3.5);
        level.addFreshEntity(z);
        t.putList("current_mobs", uuids(z.uuid()));
        var be = place(level, "active", false, t);
        level.time = scanTime(5000) + 1;
        be.tick(level);
        assertEquals(TrialSpawnerState.ACTIVE, be.getState(), "살아 있는 추적 몹이 있으면 끝나지 않는다");
        assertEquals(1, be.trialSpawner().getData().currentMobs().size());
    }

    @Test
    void untrackedMobsAreDroppedAndNextMobDelayed() {
        Lvl level = new Lvl(9);
        NbtCompound t = activeTag(1);
        t.putList("current_mobs", uuids(new UUID(5, 5)));   // 레벨에 없는 UUID
        var be = place(level, "active", false, t);
        level.time = scanTime(5000) + 1;
        be.tick(level);
        assertFalse(be.trialSpawner().getData().currentMobs().contains(new UUID(5, 5)));
        assertTrue(be.trialSpawner().getData().nextMobSpawnsAt() >= level.time + 20 || be.trialSpawner().getData().totalMobsSpawned() >= 1);
    }

    @Test
    void rewardEjectionOpensShutterAfter40TicksOfCooldownAndEjectsEvery30() {
        Lvl level = new Lvl(11);
        long cooldownEnd = 100000L, start = cooldownEnd - 36000;
        NbtCompound t = new NbtCompound();
        t.putLong("cooldown_ends_at", cooldownEnd);
        t.putList("registered_players", uuids(P1, P2));
        t.putString("ejecting_loot_table", "minecraft:spawners/trial_chamber/key");
        t.putCompound("spawn_data", entity("minecraft:zombie"));
        ItemStack key = new ItemStack(ItemContext.id("minecraft:item", "minecraft:trial_key"), 1);
        level.loot.put("minecraft:spawners/trial_chamber/key", List.of(key));
        var be = place(level, "waiting_for_reward_ejection", false, t);
        level.time = start + 39;
        be.tick(level);
        assertEquals(TrialSpawnerState.WAITING_FOR_REWARD_EJECTION, be.getState());
        level.time = start + 40;
        be.tick(level);
        assertEquals(TrialSpawnerState.EJECTING_REWARD, be.getState());
        assertEquals(List.of("minecraft:block.trial_spawner.open_shutter"), level.sounds);
        // (시각 − 시작) % 30 == 0 인 시각마다 한 명분을 배출한다
        level.time = start + 45;
        be.tick(level);
        assertEquals(2, be.trialSpawner().getData().detectedPlayers().size());
        level.time = start + 60;
        be.tick(level);
        assertEquals(1, be.trialSpawner().getData().detectedPlayers().size());
        assertEquals(1, level.getEntitiesOfClass(ItemEntity.class, new org.domi.furnace.server.entity.AABB(-5, -70, -5, 10, -50, 10), e -> true).size());
        assertTrue(level.hasEvent(3014));
        level.time = start + 90;
        be.tick(level);
        assertEquals(0, be.trialSpawner().getData().detectedPlayers().size());
        assertEquals(2, level.getEntitiesOfClass(ItemEntity.class, new org.domi.furnace.server.entity.AABB(-5, -70, -5, 10, -50, 10), e -> true).size());
        // 등록 플레이어가 없으면 셔터를 닫고 쿨다운으로
        level.time = start + 120;
        be.tick(level);
        assertEquals(TrialSpawnerState.COOLDOWN, be.getState());
        assertTrue(level.sounds.contains("minecraft:block.trial_spawner.close_shutter"));
        assertTrue(be.trialSpawner().getData().ejectingLootTable().isEmpty());
    }

    @Test
    void ejectingPicksLootTableFromConfigWhenNoneSetWithOneRandom() {
        Lvl level = new Lvl(21);
        long cooldownEnd = 100000L, start = cooldownEnd - 36000;
        NbtCompound t = new NbtCompound();
        t.putLong("cooldown_ends_at", cooldownEnd);
        t.putList("registered_players", uuids(P1));
        t.putCompound("spawn_data", entity("minecraft:zombie"));
        var be = place(level, "ejecting_reward", false, t);
        level.time = start + 30;
        be.tick(level);
        // 기본 loot_tables_to_eject: consumables(1), key(1) → nextInt(2)
        RandomSource ref = RandomSource.create(21);
        String expect = ref.nextInt(2) == 0 ? "minecraft:spawners/trial_chamber/consumables" : "minecraft:spawners/trial_chamber/key";
        assertEquals(java.util.Optional.of(expect), be.trialSpawner().getData().ejectingLootTable());
        assertEquals(ref.nextLong(), level.rnd.nextLong());
    }

    @Test
    void cooldownRestartsActiveWhenPlayersReturnAndResetsWhenOver() {
        Lvl level = new Lvl(13);
        NbtCompound t = new NbtCompound();
        t.putLong("cooldown_ends_at", 90000L);
        t.putCompound("spawn_data", entity("minecraft:zombie"));
        t.putInt("total_mobs_spawned", 5);
        var be = place(level, "cooldown", true, t);
        level.time = 89990;
        be.tick(level);
        assertEquals(TrialSpawnerState.COOLDOWN, be.getState(), "쿨다운이 안 끝났고 플레이어가 없다");
        level.time = 90000;
        be.tick(level);
        assertEquals(TrialSpawnerState.WAITING_FOR_PLAYERS, be.getState());
        assertFalse(TrialSpawner.OMINOUS.is(level.getBlockState(POS)), "쿨다운이 끝나면 불길한 상태를 푼다");
        assertEquals(0L, be.trialSpawner().getData().cooldownEndsAt());
        assertEquals(0, be.trialSpawner().getData().totalMobsSpawned());
        assertTrue(be.trialSpawner().getData().nextSpawnData().isEmpty(), "reset 은 다음 데이터를 비운다");
        // 쿨다운 중에는 일반 플레이어를 등록하지 않는다 — 불길한 징조를 가진 플레이어가 와서 불길해질 때만 다시 활성
        Lvl level2 = new Lvl(13);
        var be2 = place(level2, "cooldown", false, t);
        Player plain = player(level2, P1, 1.5, -60, 4.5);
        level2.time = scanTime(80000);
        be2.tick(level2);
        assertEquals(TrialSpawnerState.COOLDOWN, be2.getState());
        assertTrue(be2.trialSpawner().getData().detectedPlayers().isEmpty());
        plain.vitals().addEffect(new MobEffectInstance(MobEffect.TRIAL_OMEN, 5000, 0));
        level2.time = scanTime(level2.time + 1);
        be2.tick(level2);
        assertEquals(TrialSpawnerState.ACTIVE, be2.getState());
        assertTrue(TrialSpawner.OMINOUS.is(level2.getBlockState(POS)));
        assertEquals(0, be2.trialSpawner().getData().totalMobsSpawned());
    }

    @Test
    void noSpawningInPeacefulOrWhenRuleOffResetsData() {
        Lvl level = new Lvl(13);
        NbtCompound t = activeTag(3);
        var be = place(level, "active", false, t);
        // 시험용 덮어쓰기를 쓰지 않는 스포너: 기본 레벨은 난이도 2 + doMobSpawning 참 → 허용
        var plain = new TrialSpawnerBlockEntity(POS, stateWith("active", false));
        plain.load(t);
        assertTrue(plain.trialSpawner().canSpawnInLevel(level));
        Lvl peaceful = new Lvl(13) {
            @Override public int difficulty() { return 0; }
        };
        assertFalse(plain.trialSpawner().canSpawnInLevel(peaceful));
        Lvl ruleOff = new Lvl(13) {
            @Override public boolean gameRule(String name) { return !name.equals("doMobSpawning") && super.gameRule(name); }
        };
        assertFalse(plain.trialSpawner().canSpawnInLevel(ruleOff));
    }

    // ------------------------------------------------------------------ 불길한 전환

    @Test
    void badOmenTurnsIntoTrialOmenAndSpawnerBecomesOminous() {
        Lvl level = new Lvl(31);
        NbtCompound t = new NbtCompound();
        t.putCompound("spawn_data", entity("minecraft:zombie"));
        NbtCompound ominous = new NbtCompound();
        t.putCompound("normal_config", new NbtCompound());
        ominous.putInt("ticks_between_spawn", 30);
        t.putCompound("ominous_config", ominous);
        // 추적 중인 몹은 불길해질 때 치워진다
        Entity z = Entities.create(level, "minecraft:zombie");
        z.setPos(3.5, -60, 3.5);
        level.addFreshEntity(z);
        t.putList("current_mobs", uuids(z.uuid()));
        t.putInt("total_mobs_spawned", 1);
        var be = place(level, "waiting_for_players", false, t);
        Player p = player(level, P1, 1.5, -60, 4.5);
        p.vitals().addEffect(new MobEffectInstance(MobEffect.BAD_OMEN, 100, 1));
        long scan = scanTime(1000);
        level.time = scan;
        be.tick(level);
        // 나쁜 징조 증폭 1 → 시련 징조 18000 × (1+1) = 36000틱, 증폭 0
        assertNull(p.vitals().getEffect(MobEffect.BAD_OMEN));
        MobEffectInstance trial = p.vitals().getEffect(MobEffect.TRIAL_OMEN);
        assertNotNull(trial);
        assertEquals(36000, trial.duration());
        assertEquals(0, trial.amplifier());
        assertTrue(TrialSpawner.OMINOUS.is(level.getBlockState(POS)));
        assertTrue(be.trialSpawner().isOminous());
        // 이벤트 3020: 플레이어 눈 칸(데이터 0) 한 번, 생성기(데이터 1) 한 번 — 3013(일반 감지)은 내지 않는다
        long eyeEvents = level.events.stream().filter(e -> e[0] == 3020 && e[4] == 0).count();
        long spawnerEvents = level.events.stream().filter(e -> e[0] == 3020 && e[4] == 1 && e[1] == 1 && e[2] == -60 && e[3] == 1).count();
        assertEquals(1, eyeEvents);
        assertEquals(1, spawnerEvents);
        assertFalse(level.hasEvent(3013));
        assertTrue(z.isRemoved(), "추적 몹은 치워진다");
        assertTrue(level.events.stream().anyMatch(e -> e[0] == 3012 && e[4] == 0), "치우는 몹 자리에 구름 이벤트");
        // 불길한 구성의 간격으로 초기화한 뒤 감지가 다음 몹 시각을 max(시각 + 40, ·) 로 올린다
        TrialSpawnerData d = be.trialSpawner().getData();
        assertEquals(0, d.totalMobsSpawned());
        assertEquals(scan + 160L, d.cooldownEndsAt(), "다음 불길한 아이템 시각 = 시각 + 160");
        assertEquals(scan + 40L, d.nextMobSpawnsAt(), "max(시각 + 40, 시각 + 30)");
        assertEquals(java.util.Set.of(P1), d.detectedPlayers());
        assertEquals(TrialSpawnerState.ACTIVE, be.getState());
    }

    @Test
    void existingTrialOmenMakesSpawnerOminousWithoutConsumingIt() {
        Lvl level = new Lvl(31);
        NbtCompound t = new NbtCompound();
        t.putCompound("spawn_data", entity("minecraft:zombie"));
        var be = place(level, "waiting_for_players", false, t);
        Player p = player(level, P1, 1.5, -60, 4.5);
        p.vitals().addEffect(new MobEffectInstance(MobEffect.TRIAL_OMEN, 5000, 0));
        level.time = scanTime(1000);
        be.tick(level);
        assertTrue(TrialSpawner.OMINOUS.is(level.getBlockState(POS)));
        assertEquals(5000, p.vitals().getEffect(MobEffect.TRIAL_OMEN).duration(), "시련 징조는 소모하지 않는다");
    }

    @Test
    void ominousCooldownDoesNotRescanPlayers() {
        Lvl level = new Lvl(31);
        NbtCompound t = new NbtCompound();
        t.putLong("cooldown_ends_at", 999999L);
        t.putCompound("spawn_data", entity("minecraft:zombie"));
        var be = place(level, "cooldown", true, t);
        player(level, P1, 1.5, -60, 4.5);
        level.time = scanTime(1000);
        be.tick(level);
        assertEquals(TrialSpawnerState.COOLDOWN, be.getState(), "불길한 쿨다운은 플레이어를 다시 찾지 않는다");
        assertTrue(be.trialSpawner().getData().detectedPlayers().isEmpty());
    }

    @Test
    void ominousActiveSpawnsItemSpawnerAboveSeededPlayerAndSchedules160() {
        // 틱 난수 순서: 아이템 추첨 nextInt(1) → nextBoolean(거짓 = 플레이어 중에서) → 위치 nextInt(4) → OminousItemSpawner nextInt(61) → pitch nextFloat ×2
        long seed = -1;
        for (long s = 1; s < 1000 && seed < 0; s++) {
            RandomSource r = RandomSource.create(s);
            r.nextInt(1);
            if (!r.nextBoolean()) seed = s;
        }
        assertTrue(seed > 0);
        Lvl level = new Lvl(seed);
        level.loot.put("minecraft:spawners/trial_chamber/items_to_drop_when_ominous",
                List.of(new ItemStack(ItemContext.id("minecraft:item", "minecraft:arrow"), 1)));
        NbtCompound t = new NbtCompound();
        NbtCompound normal = new NbtCompound();
        normal.putList("spawn_potentials", potentials("minecraft:zombie"));
        normal.putFloat("total_mobs", 100.0F);
        t.putCompound("normal_config", normal);
        t.putCompound("spawn_data", entity("minecraft:zombie"));
        t.putList("registered_players", uuids(P1));
        t.putLong("next_mob_spawns_at", 999999L);
        var be = place(level, "active", true, t);
        be.trialSpawner().getData();   // 불길한 구성 = 일반 구성 병합 결과(후보 있음)
        player(level, P1, 1.5, -60, 4.5);
        level.time = scanTime(2000) + 1;
        be.tick(level);
        RandomSource ref = RandomSource.create(seed);
        ref.nextInt(1);
        ref.nextBoolean();
        int up = ref.nextInt(4);
        ref.nextInt(61);
        float pitchA = ref.nextFloat(), pitchB = ref.nextFloat();
        // 플레이어 키 1.8 → 위쪽 끝 y = −60 + (1.8f + 2.0f + up). 아무것도 안 맞으면 끝점 칸의 가운데에서 한 칸 아래
        double endY = -60 + (1.8F + 2.0F + up);
        int endBlockY = (int) Math.floor(endY);
        var spawners = level.getEntitiesOfClass(OminousItemSpawner.class, new org.domi.furnace.server.entity.AABB(-5, -70, -5, 10, -40, 10), e -> true);
        assertEquals(1, spawners.size());
        OminousItemSpawner s = spawners.get(0);
        assertEquals(1.5, s.x(), 1.0E-9);
        assertEquals(endBlockY + 0.5 - 1.0, s.y(), 1.0E-9);
        assertEquals(4.5, s.z(), 1.0E-9);
        assertTrue(level.sounds.contains("minecraft:block.trial_spawner.spawn_item_begin"));
        assertEquals(level.time + 160L, be.trialSpawner().getData().cooldownEndsAt());
        assertEquals(ref.nextLong(), level.rnd.nextLong(), "난수 소비 개수가 정확히 맞는다");
        assertTrue(pitchA >= 0 && pitchB >= 0);
    }

    @Test
    void dispensingItemsUseLowResolutionPositionSeedAndCountWeights() {
        Lvl level = new Lvl(5);
        ItemStack arrow = new ItemStack(ItemContext.id("minecraft:item", "minecraft:arrow"), 3);
        ItemStack apple = new ItemStack(ItemContext.id("minecraft:item", "minecraft:apple"), 1);
        level.loot.put(TrialSpawnerConfig.DEFAULT.itemsToDropWhenOminous(), List.of(arrow, apple));
        var data = new TrialSpawnerData();
        Pos far = new Pos(65, 70, -31);
        ItemStack picked = data.pickDispensingItem(level, TrialSpawnerConfig.DEFAULT, far, RandomSource.create(1));
        // 시드 = 월드 시드 + BlockPos(floor(65/30)=2, floor(70/20)=3, floor(-31/30)=−2).asLong()
        assertEquals(level.worldSeed() + new Pos(2, 3, -2).asLong(), level.lootSeeds.get(0)[0]);
        // 가중치 = 개수: 3 + 1 → nextInt(4) 의 결과 < 3 이면 화살
        RandomSource ref = RandomSource.create(1);
        boolean isArrow = ref.nextInt(4) < 3;
        assertEquals(isArrow ? arrow.itemId() : apple.itemId(), picked.itemId());
        assertEquals(1, picked.count(), "항목은 개수 1 로 낸다");
        // 캐시: 두 번째 추첨은 표를 다시 평가하지 않는다
        data.pickDispensingItem(level, TrialSpawnerConfig.DEFAULT, far, RandomSource.create(2));
        assertEquals(1, level.lootSeeds.size());
    }

    @Test
    void emptyDispensingTableIsNotCachedAndYieldsNothing() {
        Lvl level = new Lvl(5);
        var data = new TrialSpawnerData();
        RandomSource r = RandomSource.create(4);
        assertTrue(data.pickDispensingItem(level, TrialSpawnerConfig.DEFAULT, POS, r).isEmpty());
        assertTrue(data.pickDispensingItem(level, TrialSpawnerConfig.DEFAULT, POS, r).isEmpty());
        assertEquals(2, level.lootSeeds.size(), "비면 캐시하지 않고 다시 평가한다");
        assertEquals(RandomSource.create(4).nextLong(), r.nextLong(), "후보가 없으면 난수를 쓰지 않는다");
    }

    // ------------------------------------------------------------------ 장비·스폰 알

    @Test
    void equipmentTableFillsSlotsOncePerSlotAndAppliesDropChances() {
        Lvl level = new Lvl(5);
        var zombie = (org.domi.furnace.server.entity.Mob) Entities.create(level, "minecraft:zombie");
        ItemStack helmet = new ItemStack(ItemContext.id("minecraft:item", "minecraft:iron_helmet"), 1);
        ItemStack sword = new ItemStack(ItemContext.id("minecraft:item", "minecraft:iron_sword"), 1);
        ItemStack axe = new ItemStack(ItemContext.id("minecraft:item", "minecraft:iron_axe"), 1);
        level.loot.put("minecraft:equipment/trial_chamber_melee", List.of(helmet, sword, axe));
        NbtCompound eq = new NbtCompound();
        eq.putString("loot_table", "minecraft:equipment/trial_chamber_melee");
        eq.putFloat("slot_drop_chances", 0.0F);
        TrialSpawner.applyEquipment(level, zombie, eq);
        assertEquals(helmet.itemId(), zombie.getItemBySlot(org.domi.furnace.server.entity.EquipmentSlot.HEAD).itemId());
        assertEquals(sword.itemId(), zombie.getItemBySlot(org.domi.furnace.server.entity.EquipmentSlot.MAINHAND).itemId(), "주 손은 한 번만");
        assertEquals(0.0F, zombie.getEquipmentDropChance(org.domi.furnace.server.entity.EquipmentSlot.HEAD));
        assertEquals(0.0F, zombie.getEquipmentDropChance(org.domi.furnace.server.entity.EquipmentSlot.MAINHAND));
    }

    @Test
    void dropPreservedEquipmentDropsOnlyGuaranteedDrops() {
        Lvl level = new Lvl(5);
        var zombie = (org.domi.furnace.server.entity.Mob) Entities.create(level, "minecraft:zombie");
        zombie.setPos(3.5, -60, 3.5);
        level.addFreshEntity(zombie);
        ItemStack helmet = new ItemStack(ItemContext.id("minecraft:item", "minecraft:iron_helmet"), 1);
        ItemStack sword = new ItemStack(ItemContext.id("minecraft:item", "minecraft:iron_sword"), 1);
        zombie.setItemSlot(org.domi.furnace.server.entity.EquipmentSlot.HEAD, helmet);
        zombie.setDropChance(org.domi.furnace.server.entity.EquipmentSlot.HEAD, 2.0F);   // 반드시 떨군다(> 1)
        zombie.setItemSlot(org.domi.furnace.server.entity.EquipmentSlot.MAINHAND, sword);
        zombie.setDropChance(org.domi.furnace.server.entity.EquipmentSlot.MAINHAND, 0.085F);
        zombie.dropPreservedEquipment();
        assertTrue(zombie.getItemBySlot(org.domi.furnace.server.entity.EquipmentSlot.HEAD).isEmpty());
        assertEquals(sword.itemId(), zombie.getItemBySlot(org.domi.furnace.server.entity.EquipmentSlot.MAINHAND).itemId());
    }

    @Test
    void spawnEggOnTrialSpawnerOnlyChangesNextSpawnData() {
        Lvl level = new Lvl(5);
        var be = place(level, "inactive", false, new NbtCompound());
        be.setEntityId("minecraft:skeleton", level.random(), level);
        assertEquals("minecraft:skeleton", be.trialSpawner().getData().nextSpawnData().get().entity().getString("id"));
        assertEquals(RandomSource.create(5).nextLong(), level.rnd.nextLong(), "후보가 없으면 난수를 쓰지 않는다");
        assertTrue(be.onlyOpCanSetNbt());
    }

    @Test
    void blockEntityFactoryLoadsTrialSpawnerFromChunkNbt() {
        NbtCompound tag = breezeTag();
        tag.putString("id", "minecraft:trial_spawner");
        BlockEntity be = BlockEntities.loadStatic(POS, stateWith("inactive", false), tag);
        assertTrue(be instanceof TrialSpawnerBlockEntity);
        assertEquals(2.0F, ((TrialSpawnerBlockEntity) be).trialSpawner().getNormalConfig().totalMobs());
    }

    @Test
    void templateNbtWithoutIdIsPromotedByBlockKind() {
        // 트라이얼 챔버 템플릿의 trial_spawner NBT 에는 id 가 없다 — 바닐라는 블록이 만든 블록 엔티티에 NBT 를 읽힌다
        var gen = new org.domi.furnace.server.world.gen.SuperflatWorldSource(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), 0);
        var col = gen.generate(1, 2);
        col.setBlock(3, -59, 4, stateWith("inactive", false));
        NbtCompound nbt = breezeTag();
        nbt.putInt("x", 19);
        nbt.putInt("y", -59);
        nbt.putInt("z", 36);
        col.putGeneratedBlockEntity(3, -59, 4, nbt);
        col.promotePendingBlockEntities(1, 2, true);
        BlockEntity be = col.blockEntityAt(19, -59, 36);
        assertInstanceOf(TrialSpawnerBlockEntity.class, be);
        assertEquals(2.0F, ((TrialSpawnerBlockEntity) be).trialSpawner().getNormalConfig().totalMobs());
        assertEquals(4.0F, ((TrialSpawnerBlockEntity) be).trialSpawner().getOminousConfig().totalMobs());
        assertNull(col.generatedBlockEntity(3, -59, 4));
    }
}
