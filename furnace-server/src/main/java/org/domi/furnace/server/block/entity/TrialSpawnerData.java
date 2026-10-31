// 바닐라 TrialSpawnerData: 시련 생성기의 변하는 상태 — 등록된 플레이어·살아 있는 몹·쿨다운/다음 생성 시각·생성한 수·다음 SpawnData·배출 전리품 표.
// 저장 키: registered_players, current_mobs, cooldown_ends_at, next_mob_spawns_at, total_mobs_spawned, spawn_data, ejecting_loot_table
// (기본값과 같으면 쓰지 않는다 — DFU lenientOptionalFieldOf(이름, 기본값)). 집합은 HashSet 이라 순회 순서가 바닐라와 같은 UUID 해시 순서다.
package org.domi.furnace.server.block.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.block.entity.BaseSpawner.SpawnData;
import org.domi.furnace.server.bossbar.CustomBossEvents;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

public class TrialSpawnerData {

    private static final int DELAY_BETWEEN_PLAYER_SCANS = 20;
    private static final int TRIAL_OMEN_PER_BAD_OMEN_LEVEL = 18000;

    final Set<UUID> detectedPlayers = new HashSet<>();
    final Set<UUID> currentMobs = new HashSet<>();
    long cooldownEndsAt;
    long nextMobSpawnsAt;
    int totalMobsSpawned;
    Optional<SpawnData> nextSpawnData = Optional.empty();
    Optional<String> ejectingLootTable = Optional.empty();
    /** 표시용 몹을 한 번 만들 수 있었는가(바닐라 displayEntity != null — 한 번 만들면 다시 만들지 않는다). */
    private boolean displayEntityCreated;
    private String failedDisplayId;
    /** getDispensingItems 캐시(항목, 가중치 = 개수). */
    private List<WeightedItem> dispensingItems;

    private record WeightedItem(ItemStack stack, int weight) {}

    public Set<UUID> detectedPlayers() { return detectedPlayers; }

    public Set<UUID> currentMobs() { return currentMobs; }

    public long cooldownEndsAt() { return cooldownEndsAt; }

    public long nextMobSpawnsAt() { return nextMobSpawnsAt; }

    public int totalMobsSpawned() { return totalMobsSpawned; }

    public Optional<SpawnData> nextSpawnData() { return nextSpawnData; }

    public Optional<String> ejectingLootTable() { return ejectingLootTable; }

    /** reset: 등록 플레이어·카운터·쿨다운·다음 SpawnData 를 비운다(배출 전리품 표는 남긴다). */
    public void reset() {
        detectedPlayers.clear();
        totalMobsSpawned = 0;
        nextMobSpawnsAt = 0L;
        cooldownEndsAt = 0L;
        currentMobs.clear();
        nextSpawnData = Optional.empty();
    }

    /** hasMobToSpawn: 다음 데이터에 id 가 있거나, 일반 구성에 후보가 있다. */
    public boolean hasMobToSpawn(TrialSpawner spawner, RandomSource random) {
        boolean hasId = getOrCreateNextSpawnData(spawner, random).entity().tags().get("id") instanceof String;
        return hasId || !spawner.getConfig().spawnPotentials().isEmpty();
    }

    public boolean hasFinishedSpawningAllMobs(TrialSpawnerConfig config, int additionalPlayers) { return totalMobsSpawned >= config.calculateTargetTotalMobs(additionalPlayers); }

    public boolean haveAllCurrentMobsDied() { return currentMobs.isEmpty(); }

    public boolean isReadyToSpawnNextMob(long gameTime, TrialSpawnerConfig config, int additionalPlayers) {
        return gameTime >= nextMobSpawnsAt && currentMobs.size() < config.calculateTargetSimultaneousMobs(additionalPlayers);
    }

    /** countAdditionalPlayers: 등록 플레이어 수 − 1(0 미만이면 0). */
    public int countAdditionalPlayers() { return Math.max(0, detectedPlayers.size() - 1); }

    /**
     * tryDetectPlayers: 20틱마다((pos.asLong() + 게임 시각) % 20 == 0). 쿨다운이 아니거나 불길하지 않으면 감지 → 불길한 효과가 있는 플레이어가
     * 있으면 불길해지고(나쁜 징조는 시련 징조로), 새 플레이어를 등록하며 다음 몹 시각을 최소 40틱 뒤로, 이벤트 3013/3019.
     */
    public void tryDetectPlayers(ChunkLevel level, Pos pos, TrialSpawner spawner) {
        boolean skip = (pos.asLong() + level.gameTime()) % DELAY_BETWEEN_PLAYER_SCANS != 0L;
        if (skip) return;
        if (spawner.getState() == TrialSpawnerState.COOLDOWN && spawner.isOminous()) return;
        List<UUID> found = spawner.getPlayerDetector().detect(level, pos, spawner.getRequiredPlayerRange(), true);
        boolean becameOminous = false;
        if (!spawner.isOminous() && !found.isEmpty()) {
            Object[] pair = findPlayerWithOminousEffect(level, found);
            if (pair != null) {
                Player p = (Player) pair[0];
                if (pair[1] == MobEffect.BAD_OMEN) transformBadOmenIntoTrialOmen(p);
                level.levelEvent(null, 3020, new Pos((int) Math.floor(p.x()), (int) Math.floor(p.eyeY()), (int) Math.floor(p.z())), 0);
                spawner.applyOminous(level, pos);
                becameOminous = true;
            }
        }
        if (spawner.getState() != TrialSpawnerState.COOLDOWN || becameOminous) {
            boolean noneYet = detectedPlayers.isEmpty();
            List<UUID> toAdd = noneYet ? found : spawner.getPlayerDetector().detect(level, pos, spawner.getRequiredPlayerRange(), false);
            if (detectedPlayers.addAll(toAdd)) {
                nextMobSpawnsAt = Math.max(level.gameTime() + 40L, nextMobSpawnsAt);
                if (!becameOminous) level.levelEvent(null, spawner.isOminous() ? 3019 : 3013, pos, detectedPlayers.size());
            }
        }
    }

    /** findPlayerWithOminousEffect: 시련 징조가 있는 첫 플레이어, 없으면 나쁜 징조가 있는 마지막 플레이어. {플레이어, 효과} 또는 null. */
    private static Object[] findPlayerWithOminousEffect(ChunkLevel level, List<UUID> uuids) {
        Player bad = null;
        for (UUID u : uuids) {
            Player p = level.playerByUuid(u);
            if (p == null) continue;
            if (p.vitals().hasEffect(MobEffect.TRIAL_OMEN)) return new Object[] {p, MobEffect.TRIAL_OMEN};
            if (p.vitals().hasEffect(MobEffect.BAD_OMEN)) bad = p;
        }
        return bad == null ? null : new Object[] {bad, MobEffect.BAD_OMEN};
    }

    /** transformBadOmenIntoTrialOmen: 나쁜 징조(증폭 a) → 시련 징조 18000 × (a+1) 틱, 증폭 0. */
    static void transformBadOmenIntoTrialOmen(Player player) {
        MobEffectInstance bad = player.vitals().getEffect(MobEffect.BAD_OMEN);
        if (bad == null) return;
        int level = bad.amplifier() + 1;
        int duration = TRIAL_OMEN_PER_BAD_OMEN_LEVEL * level;
        player.vitals().removeEffect(MobEffect.BAD_OMEN);
        player.vitals().addEffect(new MobEffectInstance(MobEffect.TRIAL_OMEN, duration, 0));
    }

    /**
     * resetAfterBecomingOminous: 추적 몹을 치우고(구름 이벤트 3012, 보존 장비 떨굼, 버림), 불길한 구성에 후보가 있으면 다음 데이터를 비우고,
     * 카운터를 0 으로, 다음 몹/아이템 시각을 불길한 구성 간격으로.
     */
    public void resetAfterBecomingOminous(TrialSpawner spawner, ChunkLevel level) {
        for (UUID u : new ArrayList<>(currentMobs)) {
            Entity e = level.entities().get(u);
            if (e == null) continue;
            level.levelEvent(null, 3012, e.blockPosition(), 0);
            if (e instanceof Mob m) m.dropPreservedEquipment();
            e.discard();
        }
        if (!spawner.getOminousConfig().spawnPotentials().isEmpty()) nextSpawnData = Optional.empty();
        totalMobsSpawned = 0;
        currentMobs.clear();
        nextMobSpawnsAt = level.gameTime() + spawner.getOminousConfig().ticksBetweenSpawn();
        spawner.markUpdated();
        cooldownEndsAt = level.gameTime() + spawner.getOminousConfig().ticksBetweenItemSpawners();
    }

    /** isReadyToOpenShutter(level, delay, targetCooldownLength): 쿨다운 시작 + delay 틱이 지났다. */
    public boolean isReadyToOpenShutter(long gameTime, float delay, int targetCooldownLength) {
        long start = cooldownEndsAt - targetCooldownLength;
        return (float) gameTime >= (float) start + delay;
    }

    /** isReadyToEjectItems(level, interval, targetCooldownLength): 쿨다운 시작부터 interval 틱마다. */
    public boolean isReadyToEjectItems(long gameTime, float interval, int targetCooldownLength) {
        long start = cooldownEndsAt - targetCooldownLength;
        return (float) (gameTime - start) % interval == 0.0F;
    }

    public boolean isCooldownFinished(long gameTime) { return gameTime >= cooldownEndsAt; }

    /** setEntityId: 다음 데이터의 엔티티 id 를 바꾼다. */
    public void setEntityId(TrialSpawner spawner, RandomSource random, String type) {
        getOrCreateNextSpawnData(spawner, random).entity().putString("id", type);
    }

    /**
     * getOrCreateNextSpawnData: 있으면 그것. 없으면 일반 구성 후보에서 하나(nextInt(합) — 후보가 있고 합이 0 이 아닐 때만 난수를 쓴다),
     * 후보가 비었으면 빈 SpawnData. 만든 뒤 markUpdated.
     */
    public SpawnData getOrCreateNextSpawnData(TrialSpawner spawner, RandomSource random) {
        if (nextSpawnData.isPresent()) return nextSpawnData.get();
        List<BaseSpawner.Weighted> pots = spawner.getConfig().spawnPotentials();
        Optional<SpawnData> picked = pots.isEmpty() ? nextSpawnData : Optional.ofNullable(spawner.getConfig().pickSpawnData(random));
        nextSpawnData = Optional.of(picked.orElseGet(SpawnData::new));
        spawner.markUpdated();
        return nextSpawnData.get();
    }

    /**
     * getOrCreateDisplayEntity: 표시용 몹을 만들 수 있는지(다음 데이터에 id 가 있고 그 종류를 만들 수 있다). 서버는 몹을 쓰지 않고
     * INACTIVE → WAITING_FOR_PLAYERS 판정에만 쓴다. 한 번 만들면 다시 만들지 않는다. 몹을 만들 수 없는 상태면 false.
     */
    public boolean getOrCreateDisplayEntity(TrialSpawner spawner, ChunkLevel level, TrialSpawnerState state) {
        if (!state.hasSpinningMob()) return false;
        if (!displayEntityCreated) {
            NbtCompound tag = getOrCreateNextSpawnData(spawner, level.random()).entity();
            if (tag.tags().get("id") instanceof String id && !id.equals(failedDisplayId)) {
                if (org.domi.furnace.server.entity.EntityType.byName(id) != null && TrialSpawner.canCreate(level, id)) displayEntityCreated = true;
                else failedDisplayId = id;   // 만들 수 없는 종류는 같은 id 로 매 틱 다시 만들어 보지 않는다(엔티티 번호 낭비)
            }
        }
        return displayEntityCreated;
    }

    /** getUpdateTag(state): ACTIVE 면 next_mob_spawns_at, 다음 데이터가 있으면 spawn_data. */
    public NbtCompound getUpdateTag(TrialSpawnerState state) {
        NbtCompound t = new NbtCompound();
        if (state == TrialSpawnerState.ACTIVE) t.putLong("next_mob_spawns_at", nextMobSpawnsAt);
        nextSpawnData.ifPresent(d -> t.putCompound("spawn_data", d.save()));
        return t;
    }

    /**
     * getDispensingItems: items_to_drop_when_ominous 표를 낮은 해상도 위치 시드로 한 번 평가해, 항목을 개수 1 · 가중치 = 개수로 모은다.
     * 비면 캐시하지 않는다.
     */
    List<WeightedItem> getDispensingItems(ChunkLevel level, TrialSpawnerConfig config, Pos pos) {
        if (dispensingItems != null) return dispensingItems;
        long seed = lowResolutionPosition(level, pos);
        List<ItemStack> items = level.lootItems(config.itemsToDropWhenOminous(), null, null, 0.0F, seed, null);
        if (items.isEmpty()) return List.of();
        List<WeightedItem> list = new ArrayList<>();
        for (ItemStack s : items) list.add(new WeightedItem(s.withCount(1), s.count()));
        dispensingItems = list;
        return list;
    }

    /** 불길한 아이템 하나를 가중치 추첨(합이 0 이면 빈 스택). 난수 한 번. */
    ItemStack pickDispensingItem(ChunkLevel level, TrialSpawnerConfig config, Pos pos, RandomSource random) {
        List<WeightedItem> list = getDispensingItems(level, config, pos);
        WeightedItem w = TrialSpawnerConfig.pick(list, WeightedItem::weight, random);
        return w == null ? ItemStack.EMPTY : w.stack();
    }

    /** lowResolutionPosition: 월드 시드 + BlockPos(floor(x/30), floor(y/20), floor(z/30)).asLong(). */
    static long lowResolutionPosition(ChunkLevel level, Pos pos) {
        Pos q = new Pos((int) Math.floor(pos.x() / 30.0F), (int) Math.floor(pos.y() / 20.0F), (int) Math.floor(pos.z() / 30.0F));
        return level.worldSeed() + q.asLong();
    }

    // ------------------------------------------------------------------ NBT

    private static Set<UUID> loadUuids(Object o) {
        Set<UUID> out = new HashSet<>();
        if (o instanceof NbtList l) {
            for (Object e : l.items()) if (e instanceof int[] a && a.length == 4) out.add(CustomBossEvents.uuidFromInts(a));
        }
        return out;
    }

    private static void saveUuids(NbtCompound t, String key, Set<UUID> set) {
        if (set.isEmpty()) return;
        NbtList l = new NbtList(NbtIo.TAG_INT_ARRAY);
        for (UUID u : set) l.add(CustomBossEvents.uuidToInts(u));
        t.putList(key, l);
    }

    /** 코덱 해석(lenient — 필드마다 실패하면 기본값). */
    public void load(NbtCompound t) {
        detectedPlayers.clear();
        detectedPlayers.addAll(loadUuids(t.tags().get("registered_players")));
        currentMobs.clear();
        currentMobs.addAll(loadUuids(t.tags().get("current_mobs")));
        cooldownEndsAt = t.tags().get("cooldown_ends_at") instanceof Number n ? n.longValue() : 0L;
        nextMobSpawnsAt = t.tags().get("next_mob_spawns_at") instanceof Number n ? n.longValue() : 0L;
        totalMobsSpawned = t.tags().get("total_mobs_spawned") instanceof Number n && n.intValue() >= 0 ? n.intValue() : 0;
        nextSpawnData = Optional.ofNullable(SpawnData.parse(t.tags().get("spawn_data")));
        ejectingLootTable = t.tags().get("ejecting_loot_table") instanceof String s && BaseSpawner.normalizeId(s) != null
                ? Optional.of(BaseSpawner.normalizeId(s)) : Optional.empty();
    }

    public void save(NbtCompound t) {
        saveUuids(t, "registered_players", detectedPlayers);
        saveUuids(t, "current_mobs", currentMobs);
        if (cooldownEndsAt != 0L) t.putLong("cooldown_ends_at", cooldownEndsAt);
        if (nextMobSpawnsAt != 0L) t.putLong("next_mob_spawns_at", nextMobSpawnsAt);
        if (totalMobsSpawned != 0) t.putInt("total_mobs_spawned", totalMobsSpawned);
        nextSpawnData.ifPresent(d -> t.putCompound("spawn_data", d.save()));
        ejectingLootTable.ifPresent(s -> t.putString("ejecting_loot_table", s));
    }
}
