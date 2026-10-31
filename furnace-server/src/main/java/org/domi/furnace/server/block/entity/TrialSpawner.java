// 바닐라 TrialSpawner(+TrialSpawnerState 전이 + PlayerDetector): 시련 생성기의 서버 동작.
// 상태: inactive → waiting_for_players → active → (몹을 다 잡으면) waiting_for_reward_ejection → ejecting_reward → cooldown → waiting_for_players.
// 틱 난수 순서(레벨 난수): [표시용 몹 판정/hasMobToSpawn 의 다음 데이터 고르기 nextInt(합)] → 활성 때 [불길하면 아이템 추첨 nextInt(합)] →
//   몹 생성: [다음 데이터] x(nextDouble×2)·y(nextInt(3))·z(nextDouble×2) → 몹 회전 nextFloat → finalizeSpawn → 생성 뒤 다음 후보 nextInt(합).
// 클라이언트 몫(생략): 입자·주변 소리·표시용 회전 몹(clientTick). 파티클/소리 이벤트(3011~3021)는 서버가 보내는 쪽만 한다.
package org.domi.furnace.server.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Clip;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.block.entity.BaseSpawner.SpawnData;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EquipmentSlot;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.OminousItemSpawner;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.world.GameMode;

public final class TrialSpawner {

    public static final int DETECT_PLAYER_SPAWN_BUFFER = 40;
    private static final int MAX_MOB_TRACKING_DISTANCE_SQR = 47 * 47;
    private static final float SPAWNING_AMBIENT_SOUND_CHANCE = 0.02F;

    static final Prop STATE = B.prop("trial_spawner_state"), OMINOUS = B.prop("ominous");

    /** TrialSpawner.StateAccessor: 블록 엔티티가 맡는 부분(상태 저장, 클라 갱신). */
    public interface StateAccessor {
        void setState(BlockLevel level, TrialSpawnerState state);

        TrialSpawnerState getState();

        void markUpdated();
    }

    /** PlayerDetector.detect(level, pos, radius, requireLineOfSight): 범위 안 플레이어의 UUID. */
    public interface PlayerDetector {
        List<UUID> detect(ChunkLevel level, Pos pos, double radius, boolean requireLineOfSight);

        /** PLAYERS: 관전자·창작 모드를 뺀 (살아 있는) 플레이어, 블록 좌표 거리² &lt; 반경². */
        PlayerDetector PLAYERS = (level, pos, radius, los) -> detectPlayers(level, pos, radius, los, false);
        /** INCLUDING_CREATIVE_PLAYERS: 관전자만 뺀다(금고 기본). */
        PlayerDetector INCLUDING_CREATIVE_PLAYERS = (level, pos, radius, los) -> detectPlayers(level, pos, radius, los, true);
    }

    private TrialSpawnerConfig normalConfig;
    private TrialSpawnerConfig ominousConfig;
    private final TrialSpawnerData data = new TrialSpawnerData();
    private int requiredPlayerRange = 14;
    private int targetCooldownLength = 36000;
    private final StateAccessor stateAccessor;
    private PlayerDetector playerDetector = PlayerDetector.PLAYERS;
    private boolean overridePeacefulAndMobSpawnRule;
    private boolean isOminous;

    public TrialSpawner(StateAccessor stateAccessor) {
        this.stateAccessor = stateAccessor;
        this.normalConfig = TrialSpawnerConfig.DEFAULT;
        this.ominousConfig = TrialSpawnerConfig.DEFAULT;
    }

    public TrialSpawnerData getData() { return data; }

    /** getConfig: 불길한 상태면 불길한 구성. */
    public TrialSpawnerConfig getConfig() { return isOminous ? ominousConfig : normalConfig; }

    public TrialSpawnerConfig getNormalConfig() { return normalConfig; }

    public TrialSpawnerConfig getOminousConfig() { return ominousConfig; }

    public boolean isOminous() { return isOminous; }

    public int getRequiredPlayerRange() { return requiredPlayerRange; }

    public int getTargetCooldownLength() { return targetCooldownLength; }

    public TrialSpawnerState getState() { return stateAccessor.getState(); }

    public void markUpdated() { stateAccessor.markUpdated(); }

    public PlayerDetector getPlayerDetector() { return playerDetector; }

    public void setPlayerDetector(PlayerDetector d) { playerDetector = d; }

    /** 시험용: 평화로움·mobSpawning 규칙을 무시(바닐라 overridePeacefulAndMobSpawnRule). */
    public void overridePeacefulAndMobSpawnRule() { overridePeacefulAndMobSpawnRule = true; }

    /** 시험용: 구성을 바꾼다. */
    public void setConfigs(TrialSpawnerConfig normal, TrialSpawnerConfig ominous) {
        normalConfig = normal;
        ominousConfig = ominous;
    }

    // ------------------------------------------------------------------ NBT(TrialSpawner.codec)

    /**
     * 불러오기: normal_config 가 있으면 ominous_config 는 일반 구성 위에 덮어 쓴다(normal.copy().merge(ominous)), 그 뒤 필드별 lenient 해석.
     * 루트 필드 = normal_config·ominous_config·(데이터 필드들)·target_cooldown_length·required_player_range.
     */
    public void load(NbtCompound tag) {
        NbtCompound t = tag;
        if (tag.tags().get("normal_config") instanceof NbtCompound normal) {
            t = tag.copy();
            NbtCompound merged = normal.copy();
            org.domi.furnace.server.net.io.nbt.NbtTags.merge(merged, tag.getCompound("ominous_config"));
            t.putCompound("ominous_config", merged);
        }
        normalConfig = t.tags().get("normal_config") instanceof NbtCompound c ? TrialSpawnerConfig.load(c) : TrialSpawnerConfig.DEFAULT;
        ominousConfig = t.tags().get("ominous_config") instanceof NbtCompound c ? TrialSpawnerConfig.load(c) : TrialSpawnerConfig.DEFAULT;
        data.load(t);
        targetCooldownLength = t.tags().get("target_cooldown_length") instanceof Number n && n.intValue() >= 0 ? n.intValue() : 36000;
        requiredPlayerRange = t.tags().get("required_player_range") instanceof Number n && n.intValue() >= 1 && n.intValue() <= 128 ? n.intValue() : 14;
    }

    /** 저장: 기본값과 같은 필드는 쓰지 않는다. 불길한 구성은 일반과 같으면 기본값(=생략) 취급(getOminousConfigForSaving). */
    public void save(NbtCompound tag) {
        if (!normalConfig.sameAs(TrialSpawnerConfig.DEFAULT)) tag.putCompound("normal_config", normalConfig.save());
        TrialSpawnerConfig ominousForSave = !ominousConfig.sameAs(normalConfig) ? ominousConfig : TrialSpawnerConfig.DEFAULT;
        if (!ominousForSave.sameAs(TrialSpawnerConfig.DEFAULT)) tag.putCompound("ominous_config", ominousForSave.save());
        data.save(tag);
        if (targetCooldownLength != 36000) tag.putInt("target_cooldown_length", targetCooldownLength);
        if (requiredPlayerRange != 14) tag.putInt("required_player_range", requiredPlayerRange);
    }

    // ------------------------------------------------------------------ 불길한 전환

    /** applyOminous(overrideOminous): 블록 상태 ominous=true, 이벤트 3020(1), 불길한 구성으로 초기화. */
    public void applyOminous(ChunkLevel level, Pos pos) {
        level.setBlock(pos, OMINOUS.with(level.getBlockState(pos), true), 3);
        level.levelEvent(null, 3020, pos, 1);
        isOminous = true;
        data.resetAfterBecomingOminous(this, level);
    }

    /** removeOminous: 블록 상태 ominous=false. */
    public void removeOminous(ChunkLevel level, Pos pos) {
        level.setBlock(pos, OMINOUS.with(level.getBlockState(pos), false), 3);
        isOminous = false;
    }

    /** canSpawnInLevel: 평화로움이 아니고 doMobSpawning(시험용 덮어쓰기 가능). */
    public boolean canSpawnInLevel(ChunkLevel level) {
        if (overridePeacefulAndMobSpawnRule) return true;
        return level.difficulty() != 0 && level.gameRule("doMobSpawning");
    }

    // ------------------------------------------------------------------ 틱

    /** tickServer(level, pos, ominous): 몹 추적 정리 → 상태 전이. */
    public void tickServer(ChunkLevel level, Pos pos, boolean ominous) {
        isOminous = ominous;
        TrialSpawnerState state = getState();
        if (data.currentMobs.removeIf(u -> isMobUntracked(level, pos, u))) {
            data.nextMobSpawnsAt = level.gameTime() + getConfig().ticksBetweenSpawn();
        }
        TrialSpawnerState next = tickAndGetNext(state, pos, level);
        if (next != state) stateAccessor.setState(level, next);
    }

    /** isMobUntracked: 엔티티가 없거나 죽었거나 다른 차원이거나 47칸(거리² &gt; 2209) 밖. 다른 region 으로 넘어간 몹은 못 찾는다(가정). */
    private static boolean isMobUntracked(ChunkLevel level, Pos pos, UUID uuid) {
        Entity e = level.entities().get(uuid);
        if (e == null || !e.isAlive()) return true;
        Pos bp = e.blockPosition();
        long dx = bp.x() - pos.x(), dy = bp.y() - pos.y(), dz = bp.z() - pos.z();
        return dx * dx + dy * dy + dz * dz > MAX_MOB_TRACKING_DISTANCE_SQR;
    }

    /** TrialSpawnerState.tickAndGetNext. */
    TrialSpawnerState tickAndGetNext(TrialSpawnerState state, Pos pos, ChunkLevel level) {
        TrialSpawnerConfig config = getConfig();
        switch (state) {
            case INACTIVE:
                return data.getOrCreateDisplayEntity(this, level, TrialSpawnerState.WAITING_FOR_PLAYERS) ? TrialSpawnerState.WAITING_FOR_PLAYERS : state;
            case WAITING_FOR_PLAYERS:
                if (!canSpawnInLevel(level)) {
                    data.reset();
                    return state;
                } else if (!data.hasMobToSpawn(this, level.random())) {
                    return TrialSpawnerState.INACTIVE;
                } else {
                    data.tryDetectPlayers(level, pos, this);
                    return data.detectedPlayers.isEmpty() ? state : TrialSpawnerState.ACTIVE;
                }
            case ACTIVE: {
                if (!canSpawnInLevel(level)) {
                    data.reset();
                    return TrialSpawnerState.WAITING_FOR_PLAYERS;
                } else if (!data.hasMobToSpawn(this, level.random())) {
                    return TrialSpawnerState.INACTIVE;
                }
                int extra = data.countAdditionalPlayers();
                data.tryDetectPlayers(level, pos, this);
                if (isOminous) spawnOminousItemSpawner(level, pos);
                if (data.hasFinishedSpawningAllMobs(config, extra)) {
                    if (data.haveAllCurrentMobsDied()) {
                        data.cooldownEndsAt = level.gameTime() + targetCooldownLength;
                        data.totalMobsSpawned = 0;
                        data.nextMobSpawnsAt = 0L;
                        return TrialSpawnerState.WAITING_FOR_REWARD_EJECTION;
                    }
                } else if (data.isReadyToSpawnNextMob(level.gameTime(), config, extra)) {
                    spawnMob(level, pos).ifPresent(uuid -> {
                        data.currentMobs.add(uuid);
                        data.totalMobsSpawned++;
                        data.nextMobSpawnsAt = level.gameTime() + config.ticksBetweenSpawn();
                        SpawnData next = config.pickSpawnData(level.random());
                        if (next != null) {
                            data.nextSpawnData = Optional.of(next);
                            markUpdated();
                        }
                    });
                }
                return state;
            }
            case WAITING_FOR_REWARD_EJECTION:
                if (data.isReadyToOpenShutter(level.gameTime(), 40.0F, targetCooldownLength)) {
                    level.playSound(null, pos, "minecraft:block.trial_spawner.open_shutter", "blocks", 1.0F, 1.0F);
                    return TrialSpawnerState.EJECTING_REWARD;
                }
                return state;
            case EJECTING_REWARD:
                if (!data.isReadyToEjectItems(level.gameTime(), 30.0F, targetCooldownLength)) {
                    return state;
                } else if (data.detectedPlayers.isEmpty()) {
                    level.playSound(null, pos, "minecraft:block.trial_spawner.close_shutter", "blocks", 1.0F, 1.0F);
                    data.ejectingLootTable = Optional.empty();
                    return TrialSpawnerState.COOLDOWN;
                } else {
                    if (data.ejectingLootTable.isEmpty()) data.ejectingLootTable = Optional.ofNullable(config.pickLootTableToEject(level.random()));
                    data.ejectingLootTable.ifPresent(t -> ejectReward(level, pos, t));
                    data.detectedPlayers.remove(data.detectedPlayers.iterator().next());
                    return state;
                }
            case COOLDOWN:
                data.tryDetectPlayers(level, pos, this);
                if (!data.detectedPlayers.isEmpty()) {
                    data.totalMobsSpawned = 0;
                    data.nextMobSpawnsAt = 0L;
                    return TrialSpawnerState.ACTIVE;
                } else if (data.isCooldownFinished(level.gameTime())) {
                    removeOminous(level, pos);
                    data.reset();
                    return TrialSpawnerState.WAITING_FOR_PLAYERS;
                }
                return state;
            default:
                return state;
        }
    }

    // ------------------------------------------------------------------ 불길한 아이템 생성기

    /**
     * spawnOminousItemSpawner: 불길한 아이템 하나를 추첨(난수 한 번 — 비어 있으면 없음)하고, 시각이 되었으면 감지된 플레이어/몹 위에
     * OminousItemSpawner 를 놓고(시작 소리 pitch 난수 2번), 다음 시각을 160틱 뒤로.
     */
    private void spawnOminousItemSpawner(ChunkLevel level, Pos pos) {
        ItemStack stack = data.pickDispensingItem(level, getConfig(), pos, level.random());
        if (stack.isEmpty() || level.gameTime() < data.cooldownEndsAt) return;
        calculatePositionToSpawnSpawner(level, pos).ifPresent(v -> {
            OminousItemSpawner e = OminousItemSpawner.create(level, stack);
            e.setPos(v[0], v[1], v[2]);
            level.addFreshEntity(e);
            float pitch = (level.random().nextFloat() - level.random().nextFloat()) * 0.2F + 1.0F;
            level.playSound(null, new Pos((int) Math.floor(v[0]), (int) Math.floor(v[1]), (int) Math.floor(v[2])),
                    "minecraft:block.trial_spawner.spawn_item_begin", "hostile", 1.0F, pitch);
            data.cooldownEndsAt = level.gameTime() + ominousConfig.ticksBetweenItemSpawners();
        });
    }

    private static double sqr(int v) { return (double) v * v; }

    /** calculatePositionToSpawnSpawner: 범위 안 살아 있는 플레이어 중에서(없으면 없음) 몹 또는 플레이어를 골라 그 위쪽. 후보 = {x, y, z, 키}. */
    private Optional<double[]> calculatePositionToSpawnSpawner(ChunkLevel level, Pos pos) {
        double cx = pos.x() + 0.5, cy = pos.y() + 0.5, cz = pos.z() + 0.5;
        double limit = sqr(requiredPlayerRange);
        List<double[]> players = new ArrayList<>();
        for (UUID u : data.detectedPlayers) {
            Player p = level.playerByUuid(u);
            if (p == null || p.gameMode() == GameMode.CREATIVE || p.isSpectator() || !p.isAlive()) continue;
            double dx = p.x() - cx, dy = p.y() - cy, dz = p.z() - cz;
            if (dx * dx + dy * dy + dz * dz <= limit) players.add(new double[] {p.x(), p.y(), p.z(), p.bbHeight()});
        }
        if (players.isEmpty()) return Optional.empty();
        // selectEntityToSpawnItemAbove: 난수 nextBoolean — 참이면 추적 중인 몹, 거짓이면 플레이어 중에서(둘 이상이면 nextInt(크기))
        boolean fromMobs = level.random().nextBoolean();
        List<double[]> list = players;
        if (fromMobs) {
            list = new ArrayList<>();
            for (UUID u : data.currentMobs) {
                Entity e = level.entities().get(u);
                if (e == null || !e.isAlive()) continue;
                double dx = e.x() - cx, dy = e.y() - cy, dz = e.z() - cz;
                if (dx * dx + dy * dy + dz * dz <= limit) list.add(new double[] {e.x(), e.y(), e.z(), e.bbHeight()});
            }
        }
        if (list.isEmpty()) return Optional.empty();
        double[] target = list.size() == 1 ? list.get(0) : list.get(level.random().nextInt(list.size()));
        return calculatePositionAbove(target, level);
    }

    /** calculatePositionAbove: 엔티티 위 (키 + 2 + nextInt(4)) 까지 충돌 모양으로 쏴서 맞은 칸 가운데의 한 칸 아래. 충돌 모양이 비어 있어야 한다. */
    private static Optional<double[]> calculatePositionAbove(double[] e, ChunkLevel level) {
        double fx = e[0], fy = e[1], fz = e[2];
        double ty = fy + (((float) e[3]) + 2.0F + level.random().nextInt(4));
        Clip.Result hit = Clip.clipCollider(level, fx, fy, fz, fx, ty, fz);
        Pos hp = hit.pos();
        double[] v = {hp.x() + 0.5, hp.y() + 0.5 - 1.0, hp.z() + 0.5};
        Pos vp = new Pos((int) Math.floor(v[0]), (int) Math.floor(v[1]), (int) Math.floor(v[2]));
        int st = level.getBlockState(vp);
        return !B.behavior(st).getCollisionShape(st, level, vp, null).isEmpty() ? Optional.empty() : Optional.of(v);
    }

    // ------------------------------------------------------------------ 몹 생성

    /** 이 종류를 만들 수 있는가(Entities 에 이식됐는가) — 표시용 몹 판정. */
    static boolean canCreate(ChunkLevel level, String id) {
        try {
            return Entities.create(level, id) != null;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * spawnMob: 위치(Pos 태그 또는 spawn_range 안 무작위) → 충돌·시선·스폰 규칙(SPAWNER)·사용자 빛 조건 → 엔티티 생성(회전 nextFloat) →
     * 몹이면 장애물·finalizeSpawn(id 뿐일 때)·영속·장비 → 추가 → 이벤트 3011/3012(불길하면 1)·entity_place.
     */
    Optional<UUID> spawnMob(ChunkLevel level, Pos pos) {
        RandomSource random = level.random();
        SpawnData sd = data.getOrCreateNextSpawnData(this, random);
        NbtCompound tag = sd.entity();
        String id = tag.tags().get("id") instanceof String s ? s : null;
        EntityType type = id == null ? null : EntityType.byName(id);
        if (type == null) return Optional.empty();
        NbtList posList = tag.tags().get("Pos") instanceof NbtList l && l.elementType() == NbtIo.TAG_DOUBLE ? l : null;
        int n = posList == null ? 0 : posList.size();
        int range = getConfig().spawnRange();
        double x = n >= 1 ? ((Number) posList.get(0)).doubleValue() : pos.x() + (random.nextDouble() - random.nextDouble()) * range + 0.5;
        double y = n >= 2 ? ((Number) posList.get(1)).doubleValue() : pos.y() + random.nextInt(3) - 1;
        double z = n >= 3 ? ((Number) posList.get(2)).doubleValue() : pos.z() + (random.nextDouble() - random.nextDouble()) * range + 0.5;
        if (!level.noCollision(type.makeBoundingBox(x, y, z))) return Optional.empty();
        if (!inLineOfSight(level, pos, x, y, z)) return Optional.empty();
        Pos at = new Pos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        if (!org.domi.furnace.server.entity.spawn.SpawnPlacements.checkSpawnRules(id, level, org.domi.furnace.server.entity.spawn.SpawnReason.TRIAL_SPAWNER, at,
                level.random())) {
            return Optional.empty();
        }
        if (sd.lightLimits() != null && !sd.validPosition(level, at)) return Optional.empty();
        Entity e = BaseSpawner.loadEntity(level, tag, id, x, y, z);
        if (e == null) return Optional.empty();
        e.moveTo(e.x(), e.y(), e.z(), random.nextFloat() * 360.0F, 0.0F);
        if (e instanceof Mob mob) {
            if (!mob.checkSpawnObstruction(level)) return Optional.empty();
            boolean onlyId = tag.tags().size() == 1 && tag.tags().get("id") instanceof String;
            if (onlyId) mob.finalizeSpawnGroup(level.random(), level.getCurrentDifficultyAt(mob.blockPosition()), null);
            mob.setPersistenceRequired();
            if (sd.equipment() instanceof NbtCompound eq) applyEquipment(level, mob, eq);
        }
        if (!level.addFreshEntityWithPassengers(e)) return Optional.empty();
        int flame = isOminous ? 1 : 0;
        level.levelEvent(null, 3011, pos, flame);
        level.levelEvent(null, 3012, at, flame);
        level.gameEventFrom("minecraft:entity_place", at, e, level.getBlockState(at));
        return Optional.of(e.uuid());
    }

    /** inLineOfSight(level, 생성기 가운데, 몹 위치): 몹 → 생성기 가운데로 시각 모양 시선이 생성기 칸에서 끝나거나 아무것도 안 맞는다. */
    private static boolean inLineOfSight(ChunkLevel level, Pos spawnerPos, double fx, double fy, double fz) {
        Clip.Result r = Clip.clipVisual(level, fx, fy, fz, spawnerPos.x() + 0.5, spawnerPos.y() + 0.5, spawnerPos.z() + 0.5);
        return r.pos().equals(spawnerPos) || r.miss();
    }

    // ------------------------------------------------------------------ 장비(EquipmentTable)

    /**
     * Mob.equip(EquipmentTable): 장비 전리품 표를 몹 위치·몹 자신으로 평가해, 아이템마다 칸을 고른다 — 방어구류는 그 칸(이미 썼으면 건너뜀),
     * 나머지는 주 손(이미 썼으면 건너뜀). 방어구·몸 칸은 1개로 자르고, slot_drop_chances(숫자 하나 = 모든 칸, 또는 칸 이름 → 숫자)를 적용한다.
     * 가정: 방어구 칸 판정은 Mob.equipmentSlotFor(이름 기준)로 Equipable 을 대신한다.
     */
    static void applyEquipment(ChunkLevel level, Mob mob, NbtCompound eq) {
        if (!(eq.tags().get("loot_table") instanceof String table) || table.equals("minecraft:empty")) return;
        java.util.Map<EquipmentSlot, Float> chances = new java.util.EnumMap<>(EquipmentSlot.class);
        Object sdc = eq.tags().get("slot_drop_chances");
        if (sdc instanceof Number num) {
            for (EquipmentSlot s : EquipmentSlot.values()) chances.put(s, num.floatValue());
        } else if (sdc instanceof NbtCompound c) {
            for (EquipmentSlot s : EquipmentSlot.values()) if (c.tags().get(s.slotName()) instanceof Number num) chances.put(s, num.floatValue());
        }
        List<ItemStack> items = level.lootItems(table, new double[] {mob.x(), mob.y(), mob.z()}, mob.lootEntity(), 0.0F, 0L, null);
        List<EquipmentSlot> used = new ArrayList<>();
        for (ItemStack item : items) {
            if (item.isEmpty()) continue;
            EquipmentSlot slot = Mob.equipmentSlotFor(item);
            if (used.contains(slot)) continue;
            ItemStack equipped = slot.type() == EquipmentSlot.Type.HAND ? item : item.withCount(Math.min(item.count(), 1));
            mob.setItemSlot(slot, equipped);
            Float chance = chances.get(slot);
            if (chance != null) mob.setDropChance(slot, chance);
            used.add(slot);
        }
    }

    // ------------------------------------------------------------------ 보상 배출

    /**
     * ejectReward: 표를 비어 있는 문맥으로(표의 random_sequence/월드 난수) 평가해 생성기 위 1.2 칸에서 위로 던진다(속도 2). 나온 것이 있으면 이벤트 3014.
     */
    void ejectReward(ChunkLevel level, Pos pos, String lootTable) {
        List<ItemStack> items = level.lootItems(lootTable, null, null, 0.0F, 0L, null);
        if (items.isEmpty()) return;
        for (ItemStack item : items) {
            org.domi.furnace.server.block.types.CrafterBlock.spawnItem(level, item, 2, Dir.UP, pos.x() + 0.5, pos.y() + 1.2, pos.z() + 0.5);
        }
        level.levelEvent(null, 3014, pos, 0);
    }

    // ------------------------------------------------------------------ 플레이어 감지

    /** dse.PLAYERS/INCLUDING_CREATIVE: 블록 좌표 거리² &lt; 반경², 관전자(·창작) 제외, 요구 시 눈 → 생성기 가운데 시각 시선. */
    static List<UUID> detectPlayers(ChunkLevel level, Pos pos, double radius, boolean requireLineOfSight, boolean includeCreative) {
        List<UUID> out = new ArrayList<>();
        double limit = radius * radius;
        level.forEachPlayer(p -> {
            if (p.isDisconnected()) return;
            Pos bp = p.blockPosition();
            long dx = bp.x() - pos.x(), dy = bp.y() - pos.y(), dz = bp.z() - pos.z();
            if (!((double) (dx * dx + dy * dy + dz * dz) < limit)) return;
            if (p.isSpectator() || !includeCreative && p.gameMode() == GameMode.CREATIVE) return;
            if (requireLineOfSight && !inLineOfSight(level, pos, p.x(), p.eyeY(), p.z())) return;
            out.add(p.uuid());
        });
        return out;
    }
}
