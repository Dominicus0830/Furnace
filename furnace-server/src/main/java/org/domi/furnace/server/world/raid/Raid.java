// 바닐라 Raid(1.21.1) 서버 핵심: 파도 수·파도별 구성(난수 순서 그대로), 대기 300틱, 보스바, 승리(마을의 영웅)·패배·중지, 축하 600틱.
// 저장(Raid(level, tag)·save): raids.dat 의 Raids 목록 원소. 바닐라처럼 대기 위치·축하 틱·대장·습격자 목록은 저장하지 않는다
//   (습격자는 제 RaidId 로 다시 들어온다).
// 차이: 처치자는 바닐라 DamageSource.getEntity 대신 마지막으로 때린 플레이어(lastHurtByPlayer).
// 차이: 보스바 대상은 이 레벨이 아는 플레이어(services().forEachPlayer) 중 조건을 만족하는 사람.
package org.domi.furnace.server.world.raid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.DifficultyInstance;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.EquipmentSlot;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Raider;
import org.domi.furnace.server.entity.spawn.SpawnPlacements;
import org.domi.furnace.server.entity.spawn.SpawnReason;
import org.domi.furnace.server.net.protocol.play.BossEvent;

public final class Raid {
    public static final int MAX_RAID_OMEN_LEVEL = 5;
    public static final int PRE_RAID_TICKS = 300;
    private static final int MAX_NO_ACTION_TIME = 2400, MAX_CELEBRATION_TICKS = 600, POST_RAID_TICK_LIMIT = 40;
    private static final long MAX_DURATION = 48000L;
    private static final int OUTSIDE_RAID_BOUNDS_TIMEOUT = 30;
    private static final int DEFAULT_MAX_RAIDERS_DISTANCE_SQR = 12544;   // 112²
    static final int VALID_RAID_RADIUS_SQR = 9216;                         // 96²
    private static final int BAR_RED = 2, BAR_NOTCHED_10 = 2;

    private static final Text RAID_NAME = Text.translatable("event.minecraft.raid");
    private static final Text VICTORY = Text.translatable("event.minecraft.raid.victory.full");
    private static final Text DEFEAT = Text.translatable("event.minecraft.raid.defeat.full");

    /** Raid.RaiderType: 순서·파도별 기본 수(인덱스 = 파도 번호, 보너스 파도는 numGroups 칸). */
    public enum RaiderType {
        VINDICATOR("minecraft:vindicator", new int[] {0, 0, 2, 0, 1, 4, 2, 5}),
        EVOKER("minecraft:evoker", new int[] {0, 0, 0, 0, 0, 1, 1, 2}),
        PILLAGER("minecraft:pillager", new int[] {0, 4, 3, 3, 4, 4, 4, 2}),
        WITCH("minecraft:witch", new int[] {0, 0, 0, 0, 3, 0, 0, 1}),
        RAVAGER("minecraft:ravager", new int[] {0, 0, 0, 1, 0, 1, 0, 2});

        final String entityType;
        final int[] spawnsPerWaveBeforeBonus;

        RaiderType(String entityType, int[] spawns) {
            this.entityType = entityType;
            this.spawnsPerWaveBeforeBonus = spawns;
        }
    }

    public enum Status { ONGOING, VICTORY, LOSS, STOPPED }

    private final int id;
    private ChunkLevel level;
    private Pos center;
    private final RandomSource random = RandomSource.create();
    private final int numGroups;
    private final Map<Integer, Raider> groupToLeaderMap = new HashMap<>();
    private final Map<Integer, Set<Raider>> groupRaiderMap = new HashMap<>();
    private final Set<UUID> heroesOfTheVillage = new LinkedHashSet<>();
    private long ticksActive;
    private boolean started;
    private float totalHealth;
    private int raidOmenLevel;
    private boolean active;
    private int groupsSpawned;
    private int postRaidTicks;
    private int raidCooldownTicks;
    private int celebrationTicks;
    private Pos waveSpawnPos;
    private Status status;

    // 보스바(ServerBossEvent)
    private final UUID barId = UUID.randomUUID();
    private Text barName = RAID_NAME;
    private float barProgress;
    private boolean barVisible = true;
    private final Set<Player> barPlayers = new LinkedHashSet<>();

    public Raid(int id, ChunkLevel level, Pos center) {
        this.id = id;
        this.level = level;
        this.center = center;
        this.active = true;
        this.raidCooldownTicks = PRE_RAID_TICKS;
        this.barProgress = 0.0F;
        this.numGroups = getNumGroups(level.difficulty());
        this.status = Status.ONGOING;
    }

    /** Raid(level, tag): raids.dat 에서 읽는다. Status 는 대소문자 무시, 모르면 ONGOING. */
    public Raid(ChunkLevel level, org.domi.furnace.server.net.io.nbt.NbtCompound tag) {
        this.level = level;
        this.id = tag.getIntOr0("Id");
        this.started = tag.getBoolean("Started");
        this.active = tag.getBoolean("Active");
        this.ticksActive = tag.tags().get("TicksActive") instanceof Number n ? n.longValue() : 0L;
        this.raidOmenLevel = tag.getIntOr0("BadOmenLevel");
        this.groupsSpawned = tag.getIntOr0("GroupsSpawned");
        this.raidCooldownTicks = tag.getIntOr0("PreRaidTicks");
        this.postRaidTicks = tag.getIntOr0("PostRaidTicks");
        this.totalHealth = tag.getFloat("TotalHealth");
        this.center = new Pos(tag.getIntOr0("CX"), tag.getIntOr0("CY"), tag.getIntOr0("CZ"));
        this.numGroups = tag.getIntOr0("NumGroups");
        Status st = Status.ONGOING;
        if (tag.tags().get("Status") instanceof String s) {
            for (Status v : Status.values()) if (v.name().equalsIgnoreCase(s)) { st = v; break; }
        }
        this.status = st;
        if (tag.tags().get("HeroesOfTheVillage") instanceof org.domi.furnace.server.net.io.nbt.NbtList l) {
            for (Object o : l.items()) {
                if (o instanceof int[] a && a.length == 4) {
                    heroesOfTheVillage.add(new UUID((long) a[0] << 32 | a[1] & 0xFFFFFFFFL, (long) a[2] << 32 | a[3] & 0xFFFFFFFFL));
                }
            }
        }
    }

    /** Raid.save: 바닐라 키 순서 그대로. */
    public org.domi.furnace.server.net.io.nbt.NbtCompound save() {
        var tag = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        tag.putInt("Id", id);
        tag.putBoolean("Started", started);
        tag.putBoolean("Active", active);
        tag.putLong("TicksActive", ticksActive);
        tag.putInt("BadOmenLevel", raidOmenLevel);
        tag.putInt("GroupsSpawned", groupsSpawned);
        tag.putInt("PreRaidTicks", raidCooldownTicks);
        tag.putInt("PostRaidTicks", postRaidTicks);
        tag.putFloat("TotalHealth", totalHealth);
        tag.putInt("NumGroups", numGroups);
        tag.putString("Status", status.name().toLowerCase(java.util.Locale.ROOT));
        tag.putInt("CX", center.x());
        tag.putInt("CY", center.y());
        tag.putInt("CZ", center.z());
        var heroes = new org.domi.furnace.server.net.io.nbt.NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_INT_ARRAY);
        for (UUID u : heroesOfTheVillage) {
            long m = u.getMostSignificantBits(), l = u.getLeastSignificantBits();
            heroes.add(new int[] {(int) (m >> 32), (int) m, (int) (l >> 32), (int) l});
        }
        tag.putList("HeroesOfTheVillage", heroes);
        return tag;
    }

    /** getNumGroups: 쉬움 3, 보통 5, 어려움 7(평화로움 0). */
    public static int getNumGroups(int difficulty) {
        return switch (difficulty) {
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 7;
            default -> 0;
        };
    }

    /** 이 습격의 전체 파도 수(보너스 파도 포함): 나쁜 징조 단계가 2 이상이면 하나 더. */
    public static int totalWaves(int difficulty, int raidOmenLevel) {
        return getNumGroups(difficulty) + (raidOmenLevel > 1 ? 1 : 0);
    }

    public int id() { return id; }

    public Pos center() { return center; }

    void level(ChunkLevel l) { this.level = l; }

    public ChunkLevel level() { return level; }

    public Status status() { return status; }

    public int numGroups() { return numGroups; }

    public int groupsSpawned() { return groupsSpawned; }

    public int raidOmenLevel() { return raidOmenLevel; }

    public void setRaidOmenLevel(int v) { raidOmenLevel = v; }

    public int maxRaidOmenLevel() { return MAX_RAID_OMEN_LEVEL; }

    public int raidCooldownTicks() { return raidCooldownTicks; }

    public float barProgress() { return barProgress; }

    public Text barName() { return barName; }

    public boolean isStarted() { return started; }

    public boolean isActive() { return active; }

    public boolean isOver() { return status == Status.VICTORY || status == Status.LOSS; }

    public boolean isVictory() { return status == Status.VICTORY; }

    public boolean isLoss() { return status == Status.LOSS; }

    public boolean isStopped() { return status == Status.STOPPED; }

    public boolean hasFirstWaveSpawned() { return groupsSpawned > 0; }

    /** isBetweenWaves: 첫 웨이브가 나왔고 살아 있는 습격자가 없으며 쉬는 시간이 남았다. */
    public boolean isBetweenWaves() { return hasFirstWaveSpawned() && getTotalRaidersAlive() == 0 && raidCooldownTicks > 0; }

    public Set<UUID> heroesOfTheVillage() { return heroesOfTheVillage; }

    // ------------------------------------------------------------------ 파도

    private boolean isFinalWave() { return groupsSpawned == numGroups; }

    private boolean hasBonusWave() { return raidOmenLevel > 1; }

    private boolean hasSpawnedBonusWave() { return groupsSpawned > numGroups; }

    private boolean shouldSpawnBonusGroup() { return isFinalWave() && getTotalRaidersAlive() == 0 && hasBonusWave(); }

    public boolean hasMoreWaves() { return hasBonusWave() ? !hasSpawnedBonusWave() : !isFinalWave(); }

    private boolean shouldSpawnGroup() {
        return raidCooldownTicks == 0 && (groupsSpawned < numGroups || shouldSpawnBonusGroup()) && getTotalRaidersAlive() == 0;
    }

    public int getTotalRaidersAlive() {
        int n = 0;
        for (Set<Raider> s : groupRaiderMap.values()) n += s.size();
        return n;
    }

    public float getHealthOfLivingRaiders() {
        float f = 0.0F;
        for (Set<Raider> s : groupRaiderMap.values()) for (Raider r : s) f += r.health();
        return f;
    }

    /** getDefaultNumSpawns. */
    public int getDefaultNumSpawns(RaiderType type, int wave, boolean bonus) {
        return bonus ? type.spawnsPerWaveBeforeBonus[numGroups] : type.spawnsPerWaveBeforeBonus[wave];
    }

    /** getPotentialBonusSpawns(종류, 난수, 파도, 지역 난이도, 보너스): 난수 소비 순서 그대로. */
    public static int getPotentialBonusSpawns(RaiderType type, RandomSource random, int wave, int difficulty, boolean bonus) {
        boolean easy = difficulty == 1, normal = difficulty == 2;
        int j;
        switch (type) {
            case WITCH -> {
                if (easy || wave <= 2 || wave == 4) return 0;
                j = 1;
            }
            case PILLAGER, VINDICATOR -> {
                if (easy) j = random.nextInt(2);
                else if (normal) j = 1;
                else j = 2;
            }
            case RAVAGER -> j = !easy && bonus ? 1 : 0;
            default -> { return 0; }
        }
        return j > 0 ? random.nextInt(j + 1) : 0;
    }

    // ------------------------------------------------------------------ 나쁜 징조

    /** absorbRaidOmen: 습격 징조 단계를 더한다(최대 5). 플레이어에게 습격 징조가 없으면 거짓. */
    public boolean absorbRaidOmen(Player p) {
        MobEffectInstance e = p.vitals().getEffect(MobEffect.RAID_OMEN);
        if (e == null) return false;
        raidOmenLevel += e.amplifier() + 1;
        raidOmenLevel = Math.max(0, Math.min(raidOmenLevel, MAX_RAID_OMEN_LEVEL));
        if (!hasFirstWaveSpawned()) {
            p.awardStat("minecraft:raid_trigger", 1);
            p.triggerAdvancement(a -> a.triggerPlayerOnly("minecraft:voluntary_exile"));   // 바닐라 VOLUNTARY_EXILE
        }
        return true;
    }

    // ------------------------------------------------------------------ 틱

    /** Raid.tick. */
    public void tick() {
        if (isStopped()) return;
        if (status == Status.ONGOING) {
            boolean was = active;
            active = level.hasChunkAt(center);
            if (level.difficulty() == 0) {
                stop();
                return;
            }
            if (was != active) setBarVisible(active);
            if (!active) return;
            if (!level.isVillage(center)) moveRaidCenterToNearbyVillageSection();
            if (!level.isVillage(center)) {
                if (groupsSpawned > 0) status = Status.LOSS;
                else stop();
            }
            ticksActive++;
            if (ticksActive >= MAX_DURATION) {
                stop();
                return;
            }
            int alive = getTotalRaidersAlive();
            if (alive == 0 && hasMoreWaves()) {
                if (raidCooldownTicks > 0) {
                    boolean has = waveSpawnPos != null;
                    boolean find = !has && raidCooldownTicks % 5 == 0;
                    if (has && !level.hasChunkAt(waveSpawnPos)) find = true;
                    if (find) {
                        int j = 0;
                        if (raidCooldownTicks < 100) j = 1;
                        else if (raidCooldownTicks < 40) j = 2;   // 바닐라도 닿지 않는 갈래
                        waveSpawnPos = getValidSpawnPos(j);
                    }
                    if (raidCooldownTicks == PRE_RAID_TICKS || raidCooldownTicks % 20 == 0) updatePlayers();
                    raidCooldownTicks--;
                    setBarProgress(Math.max(0.0F, Math.min(1.0F, (float) (PRE_RAID_TICKS - raidCooldownTicks) / 300.0F)));
                } else if (raidCooldownTicks == 0 && groupsSpawned > 0) {
                    raidCooldownTicks = PRE_RAID_TICKS;
                    setBarName(RAID_NAME);
                    return;
                }
            }
            if (ticksActive % 20L == 0L) {
                updatePlayers();
                updateRaiders();
                if (alive > 0 && alive <= 2) {
                    setBarName(RAID_NAME.append(Text.text(" - "), Text.translatable("event.minecraft.raid.raiders_remaining", alive)));
                } else {
                    setBarName(RAID_NAME);
                }
            }
            boolean sounded = false;
            int k = 0;
            while (shouldSpawnGroup()) {
                Pos p = waveSpawnPos != null ? waveSpawnPos : findRandomSpawnPos(k, 20);
                if (p != null) {
                    started = true;
                    spawnGroup(p);
                    if (!sounded) {
                        playSound(p);
                        sounded = true;
                    }
                } else {
                    k++;
                }
                if (k > 3) {
                    stop();
                    break;
                }
            }
            if (isStarted() && !hasMoreWaves() && alive == 0) {
                if (postRaidTicks < POST_RAID_TICK_LIMIT) {
                    postRaidTicks++;
                } else {
                    status = Status.VICTORY;
                    for (UUID u : heroesOfTheVillage) {
                        Player p = findPlayer(u);
                        if (p != null && !p.isSpectator()) {
                            p.vitals().addEffect(new MobEffectInstance(MobEffect.HERO_OF_THE_VILLAGE, 48000, raidOmenLevel - 1, false, false, true));
                            p.awardStat("minecraft:raid_win", 1);
                            p.triggerAdvancement(a -> a.triggerPlayerOnly("minecraft:hero_of_the_village"));   // 바닐라 HERO_OF_THE_VILLAGE
                        }
                    }
                }
            }
        } else if (isOver()) {
            celebrationTicks++;
            if (celebrationTicks >= MAX_CELEBRATION_TICKS) {
                stop();
                return;
            }
            if (celebrationTicks % 20 == 0) {
                updatePlayers();
                setBarVisible(true);
                if (isVictory()) {
                    setBarProgress(0.0F);
                    setBarName(VICTORY);
                } else {
                    setBarName(DEFEAT);
                }
            }
        }
    }

    /** moveRaidCenterToNearbyVillageSection: 둘레 ±2 섹션 중 마을인 섹션 가운데(+8) 가장 가까운 곳으로(x → y → z 순서, 같으면 먼저). */
    private void moveRaidCenterToNearbyVillageSection() {
        int sx = center.x() >> 4, sy = center.y() >> 4, sz = center.z() >> 4;
        Pos best = null;
        long bd = Long.MAX_VALUE;
        for (int z = sz - 2; z <= sz + 2; z++) {
            for (int y = sy - 2; y <= sy + 2; y++) {
                for (int x = sx - 2; x <= sx + 2; x++) {
                    Pos c = new Pos((x << 4) + 8, (y << 4) + 8, (z << 4) + 8);
                    if (!level.isVillage(c)) continue;
                    long dx = c.x() - center.x(), dy = c.y() - center.y(), dz = c.z() - center.z();
                    long d = dx * dx + dy * dy + dz * dz;
                    if (d < bd) {
                        bd = d;
                        best = c;
                    }
                }
            }
        }
        if (best != null) center = best;
    }

    private Pos getValidSpawnPos(int i) {
        for (int j = 0; j < 3; j++) {
            Pos p = findRandomSpawnPos(i, 1);
            if (p != null) return p;
        }
        return null;
    }

    /** findRandomSpawnPos: 레벨 난수로 각도·흔들림, 지표 높이, 마을 밖(또는 i ≥ 2)이면서 파괴수가 설 수 있는 칸. */
    private Pos findRandomSpawnPos(int i, int tries) {
        int k = i == 0 ? 2 : 2 - i;
        RandomSource lr = level.random();
        for (int l = 0; l < tries; l++) {
            float f = lr.nextFloat() * (float) (Math.PI * 2);
            int n = center.x() + (int) Math.floor(Dir.cos(f) * 32.0F * k) + lr.nextInt(5);
            int o = center.z() + (int) Math.floor(Dir.sin(f) * 32.0F * k) + lr.nextInt(5);
            int p = level.getWorldSurfaceHeight(n, o);
            Pos m = new Pos(n, p, o);
            if (!level.isVillage(m) || i >= 2) {
                if (level.hasChunksAt(m.x() - 10, m.z() - 10, m.x() + 10, m.z() + 10) && level.hasChunkAt(m)
                        && (SpawnPlacements.isSpawnPositionOk("minecraft:ravager", level, m)
                            || org.domi.furnace.server.block.B.is(level.getBlockState(m.below()), "snow")
                               && org.domi.furnace.server.block.B.isAir(level.getBlockState(m)))) {
                    return m;
                }
            }
        }
        return null;
    }

    /** spawnGroup: 종류 순서대로 기본 수 + 추가 수(Raid 전용 난수). 첫 지도자 가능 몹이 파도 대장, 파괴수는 파도에 따라 기수를 태운다. */
    private void spawnGroup(Pos pos) {
        boolean leader = false;
        int wave = groupsSpawned + 1;
        totalHealth = 0.0F;
        DifficultyInstance di = level.getCurrentDifficultyAt(pos);
        boolean bonus = shouldSpawnBonusGroup();
        for (RaiderType t : RaiderType.values()) {
            int j = getDefaultNumSpawns(t, wave, bonus) + getPotentialBonusSpawns(t, random, wave, di.difficulty(), bonus);
            int k = 0;
            for (int l = 0; l < j; l++) {
                if (!(Entities.create(level, t.entityType) instanceof Raider r)) break;
                if (!leader && r.canBeLeader()) {
                    r.setPatrolLeader(true);
                    setLeader(wave, r);
                    leader = true;
                }
                joinRaid(wave, r, pos, false);
                if (t == RaiderType.RAVAGER) {
                    Raider rider = null;
                    if (wave == getNumGroups(2)) rider = Entities.create(level, "minecraft:pillager") instanceof Raider x ? x : null;
                    else if (wave >= getNumGroups(3)) {
                        String id = k == 0 ? "minecraft:evoker" : "minecraft:vindicator";
                        rider = Entities.create(level, id) instanceof Raider x ? x : null;
                    }
                    k++;
                    if (rider != null) {
                        joinRaid(wave, rider, pos, false);
                        rider.moveTo(pos.x(), pos.y(), pos.z(), 0.0F, 0.0F);
                        rider.startRiding(r);
                    }
                }
            }
        }
        waveSpawnPos = null;
        groupsSpawned++;
        updateBossbar();
    }

    /** joinRaid. */
    public void joinRaid(int wave, Raider r, Pos pos, boolean existing) {
        if (!addWaveMob(wave, r, true)) return;
        r.setCurrentRaid(this);
        r.setWave(wave);
        r.setCanJoinRaid(true);
        r.setTicksOutsideRaid(0);
        if (!existing && pos != null) {
            r.setPos(pos.x() + 0.5, pos.y() + 1.0, pos.z() + 0.5);
            r.setFinalizeReason(SpawnReason.EVENT);
            r.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(pos));
            r.applyRaidBuffs(wave, false);
            r.setOnGround(true);
            level.addFreshEntityWithPassengers(r);
        }
    }

    public boolean addWaveMob(int wave, Raider r, boolean updateHealth) {
        Set<Raider> set = groupRaiderMap.computeIfAbsent(wave, w -> new LinkedHashSet<>());
        for (Raider o : set) if (o.uuid().equals(r.uuid())) return false;
        set.add(r);
        if (updateHealth) totalHealth += r.health();
        updateBossbar();
        return true;
    }

    public void setLeader(int wave, Raider r) {
        groupToLeaderMap.put(wave, r);
        r.setItemSlot(EquipmentSlot.HEAD, Raider.leaderBanner());
        r.setDropChance(EquipmentSlot.HEAD, 2.0F);
    }

    public Raider getLeader(int wave) { return groupToLeaderMap.get(wave); }

    public void removeLeader(int wave) { groupToLeaderMap.remove(wave); }

    /** removeFromRaid: wanderedOut 이면 전체 체력에서도 뺀다. */
    public void removeFromRaid(Raider r, boolean wanderedOut) {
        Set<Raider> set = groupRaiderMap.get(r.getWave());
        if (set != null && set.remove(r)) {
            if (wanderedOut) totalHealth -= r.health();
            r.setCurrentRaid(null);
            updateBossbar();
        }
    }

    /** 저장소로 보류될 때: 소속 습격자에게 번호만 남기고 목록을 비운다. */
    void detachRaiders() {
        for (Set<Raider> set : groupRaiderMap.values()) for (Raider r : set) r.parkRaid(id);
        groupRaiderMap.clear();
        groupToLeaderMap.clear();
        removeAllBarPlayers();
    }

    public void addHeroOfTheVillage(Player p) { heroesOfTheVillage.add(p.uuid()); }

    /** updateRaiders: 사라졌거나 112칸 밖이면 빼고, 600틱 넘게 마을 밖에서 오래 놀면 바깥 시간을 센다(30 이면 뺀다). */
    private void updateRaiders() {
        List<Raider> remove = new ArrayList<>();
        for (Set<Raider> set : groupRaiderMap.values()) {
            for (Raider r : set) {
                Pos p = r.blockPosition();
                long dx = center.x() - p.x(), dy = center.y() - p.y(), dz = center.z() - p.z();
                if (r.isRemoved() || r.level() != level || dx * dx + dy * dy + dz * dz >= DEFAULT_MAX_RAIDERS_DISTANCE_SQR) {
                    remove.add(r);
                } else if (r.tickCount > 600) {
                    if (!level.isVillage(p) && r.getNoActionTime() > MAX_NO_ACTION_TIME) r.setTicksOutsideRaid(r.getTicksOutsideRaid() + 1);
                    if (r.getTicksOutsideRaid() >= OUTSIDE_RAID_BOUNDS_TIMEOUT) remove.add(r);
                }
            }
        }
        for (Raider r : remove) removeFromRaid(r, true);
    }

    public void stop() {
        active = false;
        removeAllBarPlayers();
        status = Status.STOPPED;
    }

    // ------------------------------------------------------------------ 보스바·소리

    /** updateBossbarInternal(습격자가 맞을 때도 부른다). */
    public void updateBossbar() {
        setBarProgress(totalHealth <= 0.0F ? 0.0F : Math.max(0.0F, Math.min(1.0F, getHealthOfLivingRaiders() / totalHealth)));
    }

    /** updatePlayers: 살아 있고 그 자리의 습격(getRaidAt)이 이 습격인 플레이어. */
    private void updatePlayers() {
        Set<Player> valid = new LinkedHashSet<>();
        level.forEachPlayer(p -> {
            if (!p.vitals().isDeadOrDying() && level.raids().getNearbyRaid(p.blockPosition(), VALID_RAID_RADIUS_SQR) == this) valid.add(p);
        });
        for (Player p : valid) if (!barPlayers.contains(p)) addBarPlayer(p);
        for (Player p : new ArrayList<>(barPlayers)) if (!valid.contains(p)) removeBarPlayer(p);
    }

    public Set<Player> barPlayers() { return barPlayers; }

    private void addBarPlayer(Player p) {
        if (barPlayers.add(p) && barVisible) p.send(BossEvent.add(barId, barName, barProgress, BAR_RED, BAR_NOTCHED_10, 0));
    }

    private void removeBarPlayer(Player p) {
        if (barPlayers.remove(p) && barVisible) p.send(BossEvent.remove(barId));
    }

    private void removeAllBarPlayers() {
        for (Player p : new ArrayList<>(barPlayers)) removeBarPlayer(p);
    }

    private void setBarVisible(boolean v) {
        if (v == barVisible) return;
        barVisible = v;
        for (Player p : barPlayers) p.send(v ? BossEvent.add(barId, barName, barProgress, BAR_RED, BAR_NOTCHED_10, 0) : BossEvent.remove(barId));
    }

    private void setBarProgress(float f) {
        if (f == barProgress) return;
        barProgress = f;
        if (barVisible) for (Player p : barPlayers) p.send(BossEvent.progress(barId, f));
    }

    private void setBarName(Text t) {
        if (t.equals(barName)) return;
        barName = t;
        if (barVisible) for (Player p : barPlayers) p.send(BossEvent.name(barId, t));
    }

    /** playSound: 각 플레이어 쪽으로 13칸 당긴 자리에서 습격 뿔피리(64칸 안이거나 보스바 대상). 씨앗은 Raid 난수. */
    private void playSound(Pos pos) {
        long seed = random.nextLong();
        double cx = pos.x() + 0.5, cz = pos.z() + 0.5;
        level.forEachPlayer(p -> {
            double d = Math.sqrt((cx - p.x()) * (cx - p.x()) + (cz - p.z()) * (cz - p.z()));
            double e = p.x() + 13.0 / d * (cx - p.x());
            double g = p.z() + 13.0 / d * (cz - p.z());
            if (d <= 64.0 || barPlayers.contains(p)) {
                Integer sid = org.domi.furnace.server.item.ItemContext.installed()
                        ? org.domi.furnace.server.item.ItemContext.id("minecraft:sound_event", "minecraft:event.raid.horn") : null;
                p.send(new org.domi.furnace.server.net.protocol.play.SoundPacket(sid == null ? -1 : sid, "minecraft:event.raid.horn",
                        org.domi.furnace.server.net.protocol.play.SoundPacket.source("neutral"), e, p.y(), g, 64.0F, 1.0F, seed));
            }
        });
    }

    private Player findPlayer(UUID u) {
        Player[] out = {null};
        level.forEachPlayer(p -> { if (p.uuid().equals(u)) out[0] = p; });
        return out[0];
    }
}
