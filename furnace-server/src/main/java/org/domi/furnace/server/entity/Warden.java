// 바닐라 Warden: 진동(반경 16)과 냄새(Sniffing)로 용의자를 찾고 분노(AngerManagement)가 80 이상이면 포효 뒤 공격한다.
// 근접 30 피해·음파(sonic boom), 땅에서 나오기(emerge)·파고들기(dig, 사라짐), 120틱마다 20칸 어둠.
// 클라이언트 애니메이션 상태는 서버에 필요 없어 생략.
package org.domi.furnace.server.entity;

import java.util.Optional;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.SensorType;
import org.domi.furnace.server.entity.ai.brain.Unit;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.world.gameevent.DynamicGameEventListener;
import org.domi.furnace.server.world.gameevent.GameEvent;
import org.domi.furnace.server.world.gameevent.GameEventContext;
import org.domi.furnace.server.world.gameevent.PositionSource;
import org.domi.furnace.server.world.gameevent.VibrationSystem;

public class Warden extends Monster implements VibrationSystem {
    public static final int DATA_CLIENT_ANGER_LEVEL = 16;
    private static final int VIBRATION_COOLDOWN_TICKS = 40, DARKNESS_DISPLAY_LIMIT = 200, DARKNESS_DURATION = 260, DARKNESS_RADIUS = 20,
            DARKNESS_INTERVAL = 120, ANGERMANAGEMENT_TICK_DELAY = 20, DEFAULT_ANGER = 35, PROJECTILE_ANGER = 10, RECENT_PROJECTILE_TICK_THRESHOLD = 100,
            TOUCH_COOLDOWN_TICKS = 20, ON_HURT_ANGER_BOOST = 20;
    private static final int PROJECTILE_ANGER_DISTANCE = 30;

    private AngerManagement angerManagement = new AngerManagement(this::canTargetEntity,
            org.domi.furnace.server.block.RandomSource.createFor(id(), org.domi.furnace.server.block.RandomSeeds.Purpose.WARDEN_ANGER));
    private VibrationSystem.Data vibrationData = new VibrationSystem.Data();
    private final VibrationSystem.User vibrationUser = new VibrationUser();
    private final DynamicGameEventListener<VibrationSystem.Listener> dynamicListener =
            new DynamicGameEventListener<>(new VibrationSystem.Listener(this));

    public Warden(ChunkLevel level, int id) {
        super(EntityType.WARDEN, level, id);
        xpReward = 5;
        getNavigation().setCanFloat(true);
        setPathfindingMalus(PathType.UNPASSABLE_RAIL, 0.0F);
        setPathfindingMalus(PathType.DAMAGE_OTHER, 8.0F);
        setPathfindingMalus(PathType.POWDER_SNOW, 8.0F);
        setPathfindingMalus(PathType.LAVA, 8.0F);
        setPathfindingMalus(PathType.DAMAGE_FIRE, 0.0F);
        setPathfindingMalus(PathType.DANGER_FIRE, 0.0F);
        brain = makeBrain();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Brain<Warden> makeBrain() {
        Brain<Warden> b = new Brain<>(java.util.List.of(MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_NEMESIS,
                MemoryModuleType.LOOK_TARGET, MemoryModuleType.WALK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.PATH,
                MemoryModuleType.ATTACK_TARGET, MemoryModuleType.ATTACK_COOLING_DOWN, MemoryModuleType.NEAREST_ATTACKABLE, MemoryModuleType.ROAR_TARGET,
                MemoryModuleType.DISTURBANCE_LOCATION, MemoryModuleType.RECENT_PROJECTILE, MemoryModuleType.IS_SNIFFING, MemoryModuleType.IS_EMERGING,
                MemoryModuleType.ROAR_SOUND_DELAY, MemoryModuleType.DIG_COOLDOWN, MemoryModuleType.ROAR_SOUND_COOLDOWN, MemoryModuleType.SNIFF_COOLDOWN,
                MemoryModuleType.TOUCH_COOLDOWN, MemoryModuleType.VIBRATION_COOLDOWN, MemoryModuleType.SONIC_BOOM_COOLDOWN,
                MemoryModuleType.SONIC_BOOM_SOUND_COOLDOWN, MemoryModuleType.SONIC_BOOM_SOUND_DELAY),
                (java.util.List) java.util.List.of(SensorType.NEAREST_PLAYERS, SensorType.WARDEN_ENTITY_SENSOR));
        return WardenAi.makeBrain(this, b);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<Warden> getBrain() { return (Brain<Warden>) brain; }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 500.0).add(Attribute.MOVEMENT_SPEED, 0.3F).add(Attribute.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attribute.ATTACK_KNOCKBACK, 1.5).add(Attribute.ATTACK_DAMAGE, 30.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_CLIENT_ANGER_LEVEL, SetEntityData.SERIALIZER_INT, 0);
    }

    public int getClientAngerLevel() { return (Integer) entityData.get(DATA_CLIENT_ANGER_LEVEL); }

    private void syncClientAngerLevel() { entityData.set(DATA_CLIENT_ANGER_LEVEL, getActiveAnger()); }

    // ------------------------------------------------------------------ 상태

    public boolean isDiggingOrEmerging() { return getPose() == POSE_DIGGING || getPose() == POSE_EMERGING; }

    /** 파고들기·나오기 동안 몸 높이 1(EntityDimensions.scalable(폭, 1)). */
    @Override
    public void setPose(int pose) {
        super.setPose(pose);
        boolean low = pose == POSE_DIGGING || pose == POSE_EMERGING;
        float h = low ? 1.0F : EntityType.WARDEN.height();
        setDimensions(EntityType.WARDEN.width(), h, h * 0.85F);
    }

    @Override
    public boolean isInvulnerableTo(Damage source) {
        return isDiggingOrEmerging() && !source.bypassesInvulnerability() || super.isInvulnerableTo(source);
    }

    @Override public boolean ignoreExplosion() { return isDiggingOrEmerging(); }

    @Override public boolean dampensVibrations() { return true; }

    @Override public boolean removeWhenFarAway(double d) { return false; }

    @Override public boolean isPushable() { return !isDiggingOrEmerging() && super.isPushable(); }

    /** getWalkTargetValue: 어디든 0(밝기를 따지지 않는다 — 소환 검사 통과). */
    @Override public float getWalkTargetValue(Pos pos, ChunkLevel level) { return 0.0F; }

    @Override protected float nextStep() { return moveDist + 0.55F; }

    /** Warden.playStepSound: 블록과 상관없이 warden.step 크기 10. */
    @Override protected void playStepSound(Pos pos, int state) { playSound("minecraft:entity.warden.step", 10.0F, 1.0F); }

    @Override protected float getSoundVolume() { return 4.0F; }

    @Override public String soundSource() { return "hostile"; }

    @Override
    protected String getAmbientSound() {
        return getPose() != POSE_ROARING && !isDiggingOrEmerging() ? getAngerLevel().ambientSound : null;
    }

    @Override protected String getHurtSound(Damage source) { return "minecraft:entity.warden.hurt"; }

    @Override protected String getDeathSound() { return "minecraft:entity.warden.death"; }

    @Override
    public Targetable getTarget() { return getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null); }

    // ------------------------------------------------------------------ 틱

    @Override
    public void tick() {
        VibrationSystem.Ticker.tick(level, vibrationData, vibrationUser);
        if (isPersistenceRequired() || isPassenger()) WardenAi.setDigCooldown(this);
        super.tick();
    }

    @Override
    protected void customServerAiStep() {
        getBrain().tick(level, this);
        super.customServerAiStep();
        if ((tickCount + id()) % DARKNESS_INTERVAL == 0) applyDarknessAround(level, new Vec3(x(), y(), z()), this, DARKNESS_RADIUS);
        if (tickCount % ANGERMANAGEMENT_TICK_DELAY == 0) {
            angerManagement.tick(level, this::canTargetEntity);
            syncClientAngerLevel();
        }
        WardenAi.updateActivity(this);
    }

    @Override
    public boolean doHurtTarget(Targetable t) {
        broadcastEntityEvent((byte) 4);
        playSound("minecraft:entity.warden.attack_impact", 10.0F, getVoicePitch());
        WardenAi.SonicBoom.setCooldown(this, WardenAi.SonicBoom.COOLDOWN);
        return super.doHurtTarget(t);
    }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        boolean hurt = super.hurtBy(source, amount, attacker);
        if (!isNoAi() && !isDiggingOrEmerging() && attacker != null) {
            increaseAngerAt(attacker, AngerManagement.AngerLevel.ANGRY.minimumAnger + ON_HURT_ANGER_BOOST, false);
            Entity direct = directCause();
            boolean isDirect = direct == null || direct == attacker;
            if (getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty() && canTargetEntity(attacker) && (isDirect || closerThan(this, attacker, 5.0))) {
                setAttackTarget(attacker);
            }
        }
        return hurt;
    }

    public void setAttackTarget(Targetable t) {
        getBrain().eraseMemory(MemoryModuleType.ROAR_TARGET);
        getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, t);
        getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        WardenAi.SonicBoom.setCooldown(this, 200);
    }

    // ------------------------------------------------------------------ 분노

    public AngerManagement angerManagement() { return angerManagement; }

    public AngerManagement.AngerLevel getAngerLevel() { return AngerManagement.AngerLevel.byAnger(getActiveAnger()); }

    private int getActiveAnger() { return angerManagement.getActiveAnger(getTarget()); }

    public void clearAnger(Targetable t) { angerManagement.clearAnger(t); }

    public void increaseAngerAt(Targetable t) { increaseAngerAt(t, DEFAULT_ANGER, true); }

    /**
     * Warden.doPush(cmb): AI 가 있고 만지기 쿨다운(20틱)이 없으면 쿨다운을 걸고, 민 대상에게 분노 35(듣는 소리)와 그 칸을 소란 위치로.
     * 생물이 아닌 대상(보트·광산 수레)은 canTargetEntity 가 거짓이라 분노는 없고 쿨다운·소란 위치만 남는다.
     */
    @Override
    protected void doPush(Entity e) {
        touched(e instanceof Targetable t ? t : null, e.blockPosition());
        super.doPush(e);
    }

    @Override
    protected void doPush(Player p) {
        touched(p, p.blockPosition());
        super.doPush(p);
    }

    private void touched(Targetable t, Pos pos) {
        if (!isNoAi() && !getBrain().hasMemoryValue(MemoryModuleType.TOUCH_COOLDOWN)) {
            getBrain().setMemoryWithExpiry(MemoryModuleType.TOUCH_COOLDOWN, Unit.INSTANCE, TOUCH_COOLDOWN_TICKS);
            if (t != null) increaseAngerAt(t);
            WardenAi.setDisturbanceLocation(this, pos);
        }
    }

    public void increaseAngerAt(Targetable t, int amount, boolean playListeningSound) {
        if (!isNoAi() && canTargetEntity(t)) {
            WardenAi.setDigCooldown(this);
            boolean notPlayerTarget = !(getTarget() instanceof Player);
            int a = angerManagement.increaseAnger(t, amount);
            if (t instanceof Player && notPlayerTarget && AngerManagement.AngerLevel.byAnger(a).isAngry()) getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            if (playListeningSound && getPose() != POSE_ROARING) playSound(getAngerLevel().listeningSound, 10.0F, getVoicePitch());
        }
    }

    public Optional<Targetable> getEntityAngryAt() { return getAngerLevel().isAngry() ? angerManagement.getActiveEntity() : Optional.empty(); }

    /** canTargetEntity: 같은 레벨의 살아 있는 생물, 크리에이티브·관전자 아님, 같은 팀 아님, 갑옷 거치대·워든 아님, 무적 아님, 월드 보더 안. */
    public boolean canTargetEntity(Object o) {
        if (o instanceof Player p) {
            var gm = p.gameMode();
            return !isAlliedTo(p) && !p.isDisconnected() && !p.vitals().isDeadOrDying() && gm != org.domi.furnace.server.world.GameMode.CREATIVE
                    && gm != org.domi.furnace.server.world.GameMode.SPECTATOR && level.withinBorder(p.blockPosition());
        }
        if (o instanceof LivingEntity e) {
            return e.level() == level && e != this && !isAlliedTo(e) && e.type() != EntityType.ARMOR_STAND && e.type() != EntityType.WARDEN && !e.isInvulnerable()
                    && !e.isDeadOrDying() && !e.isRemoved() && level.withinBorder(e.blockPosition());
        }
        return false;
    }

    /** Entity.closerThan(e, 가로, 세로). */
    static boolean closerThan(Entity self, Targetable e, double h, double v) {
        double dx = e.x() - self.x(), dy = e.y() - self.y(), dz = e.z() - self.z();
        return dx * dx + dz * dz < h * h && dy * dy < v * v;
    }

    static boolean closerThan(Entity self, Targetable e, double d) {
        double dx = e.x() - self.x(), dy = e.y() - self.y(), dz = e.z() - self.z();
        return dx * dx + dy * dy + dz * dz < d * d;
    }

    // ------------------------------------------------------------------ 어둠·소환

    /** applyDarknessAround → MobEffectUtil.addEffectToPlayersAround(어둠 260틱, 표시 제한 200). */
    public static void applyDarknessAround(ChunkLevel level, Vec3 pos, Entity source, int radius) {
        MobEffectInstance inst = new MobEffectInstance(MobEffect.DARKNESS, DARKNESS_DURATION, 0, false, false);
        level.forEachPlayer(p -> {
            var gm = p.gameMode();
            if (p.isDisconnected() || gm != org.domi.furnace.server.world.GameMode.SURVIVAL && gm != org.domi.furnace.server.world.GameMode.ADVENTURE) return;
            if (source instanceof Targetable src && src.isAlliedTo(p)) return;   // 출처의 아군은 제외
            double dx = p.x() - pos.x(), dy = p.y() - pos.y(), dz = p.z() - pos.z();
            if (!(dx * dx + dy * dy + dz * dz < (double) radius * radius)) return;
            MobEffectInstance cur = p.vitals().getEffect(MobEffect.DARKNESS);
            if (cur == null || cur.amplifier() < inst.amplifier() || cur.endsWithin(DARKNESS_DISPLAY_LIMIT - 1)) p.vitals().addEffect(new MobEffectInstance(inst));
        });
    }

    /** SpawnUtil.trySpawnMob(WARDEN, TRIGGERED, level, pos, 20, 5, 6, ON_TOP_OF_COLLIDER). */
    public static boolean trySpawnTriggered(ChunkLevel level, Pos origin) {
        var r = level.random();
        for (int i = 0; i < 20; i++) {
            int dx = r.nextInt(11) - 5, dz = r.nextInt(11) - 5;
            Pos p = new Pos(origin.x() + dx, origin.y() + 6, origin.z() + dz);
            if (!level.withinBorder(p)) continue;
            Pos found = moveToPossibleSpawnPosition(level, 6, p);
            if (found == null) continue;
            Warden w = new Warden(level, level.nextEntityId());
            w.moveTo(found.x() + 0.5, found.y(), found.z() + 0.5, org.domi.furnace.server.entity.ai.control.Mth.wrapDegrees(r.nextFloat() * 360.0F), 0.0F);
            w.setYHeadRot(w.yRot());
            w.setYBodyRot(w.yRot());
            w.finalizeTriggeredSpawn(level);
            if (w.checkSpawnRules(level, org.domi.furnace.server.entity.spawn.SpawnReason.TRIGGERED) && w.checkSpawnObstruction(level)) {
                level.addFreshEntityWithPassengers(w);
                return true;
            }
            w.discard();
        }
        return false;
    }

    /** moveToPossibleSpawnPosition: 위에서 아래로, 위 칸 충돌 모양이 비었고 그 칸 윗면이 꽉 찬 곳의 위. */
    private static Pos moveToPossibleSpawnPosition(ChunkLevel level, int yOffset, Pos start) {
        int x = start.x(), y = start.y(), z = start.z();
        int aboveState = level.getBlockState(start);
        for (int i = yOffset; i >= -yOffset; i--) {
            y--;
            Pos pos = new Pos(x, y, z), above = new Pos(x, y + 1, z);
            int state = level.getBlockState(pos);
            if (B.behavior(aboveState).getCollisionShape(aboveState, level, above, null).isEmpty()
                    && B.behavior(state).getCollisionShape(state, level, pos, null).isFaceFull(org.domi.furnace.server.block.Dir.UP)) {
                return above;
            }
            aboveState = state;
        }
        return null;
    }

    /**
     * Warden.finalizeSpawn(그 밖의 이유: /summon·알 등): 파고들기 대기 1200 만 두고 Mob.finalizeSpawn.
     * 이것이 없으면 DIG 활동 조건(DIG_COOLDOWN 없음)이 바로 맞아 소환 직후 땅속으로 들어간다.
     */
    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource levelRandom, org.domi.furnace.server.entity.DifficultyInstance difficulty) {
        getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, WardenAi.DIGGING_COOLDOWN);
        super.finalizeSpawn(levelRandom, difficulty);
    }

    /** finalizeSpawn(TRIGGERED): 파고들기 대기 1200, 나오기 자세·기억, agitated 소리, 그다음 Mob.finalizeSpawn. */
    public void finalizeTriggeredSpawn(ChunkLevel level) {
        getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, WardenAi.DIGGING_COOLDOWN);
        setPose(POSE_EMERGING);
        getBrain().setMemoryWithExpiry(MemoryModuleType.IS_EMERGING, Unit.INSTANCE, WardenAi.EMERGE_DURATION);
        playSound("minecraft:entity.warden.agitated", 5.0F, 1.0F);
        super.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(blockPosition()));
        playAmbientSound();
    }

    @Override
    public boolean checkSpawnObstruction(ChunkLevel level) { return super.checkSpawnObstruction(level) && level.noCollision(boundingBox()); }

    // ------------------------------------------------------------------ 진동

    @Override public DynamicGameEventListener<?> dynamicGameEventListener() { return dynamicListener; }

    @Override public Data getVibrationData() { return vibrationData; }

    @Override public User getVibrationUser() { return vibrationUser; }

    private final class VibrationUser implements VibrationSystem.User {
        private final PositionSource positionSource = PositionSource.entity(Warden.this, EntityType.WARDEN.eyeHeight());

        @Override public int getListenerRadius() { return 16; }

        @Override public PositionSource getPositionSource() { return positionSource; }

        @Override public String getListenableEvents() { return GameEvent.WARDEN_CAN_LISTEN; }

        @Override public boolean canTriggerAvoidVibration() { return true; }

        @Override
        public boolean canReceiveVibration(ChunkLevel level, Pos pos, GameEvent event, GameEventContext ctx) {
            if (isNoAi() || isDeadOrDying() || getBrain().hasMemoryValue(MemoryModuleType.VIBRATION_COOLDOWN) || isDiggingOrEmerging() || !level.withinBorder(pos)) return false;
            Object src = ctx.sourceEntity();
            return !((src instanceof LivingEntity || src instanceof Player) && !canTargetEntity(src));
        }

        @Override
        public void onReceiveVibration(ChunkLevel level, Pos pos, GameEvent event, Object source, Object projectileOwner, float distance) {
            if (isDeadOrDying()) return;
            getBrain().setMemoryWithExpiry(MemoryModuleType.VIBRATION_COOLDOWN, Unit.INSTANCE, VIBRATION_COOLDOWN_TICKS);
            broadcastEntityEvent((byte) 61);
            playSound("minecraft:entity.warden.tendril_clicks", 5.0F, getVoicePitch());
            Pos target = pos;
            if (projectileOwner instanceof Targetable owner) {
                if (closerThan(Warden.this, owner, PROJECTILE_ANGER_DISTANCE)) {
                    if (getBrain().hasMemoryValue(MemoryModuleType.RECENT_PROJECTILE)) {
                        if (canTargetEntity(owner)) target = owner.blockPosition();
                        increaseAngerAt(owner);
                    } else {
                        increaseAngerAt(owner, PROJECTILE_ANGER, true);
                    }
                }
                getBrain().setMemoryWithExpiry(MemoryModuleType.RECENT_PROJECTILE, Unit.INSTANCE, RECENT_PROJECTILE_TICK_THRESHOLD);
            } else if (source instanceof Targetable s) {
                increaseAngerAt(s);
            }
            if (!getAngerLevel().isAngry()) {
                Optional<Targetable> active = angerManagement.getActiveEntity();
                if (projectileOwner != null || active.isEmpty() || active.get() == source) WardenAi.setDisturbanceLocation(Warden.this, target);
            }
        }
    }

    // ------------------------------------------------------------------ 저장

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putCompound("anger", angerManagement.save());
        tag.putCompound(Data.NBT_TAG_KEY, vibrationData.save());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.tags().get("anger") instanceof NbtCompound a) {
            angerManagement.load(a);
            syncClientAngerLevel();
        }
        if (tag.tags().get(Data.NBT_TAG_KEY) instanceof NbtCompound l) {
            Data d = Data.load(l);
            if (d != null) vibrationData = d;
        }
    }
}
