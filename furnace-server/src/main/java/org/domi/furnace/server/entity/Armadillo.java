// 바닐라 Armadillo: 브레인으로 움직이는 동물. 위협을 느끼면 몸을 말고(rolling → scared → unrolling), 5~10분마다 아르마딜로 비늘을 떨구며, 솔로 쓸어 비늘을 얻는다.
// TODO(애니메이션): 몸 마는 연출용 엔티티 이벤트 64(내다보기)만 보낸다. TODO(발소리·끈·탑승): 걷는 소리, 목줄, 탑승 판정은 엔진에 아직 없다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.PlayerActor;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.SensorType;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Armadillo extends Animal {
    public static final int DATA_STATE = 17;
    public static final float BABY_SCALE = 0.6F;
    public static final float MAX_HEAD_ROTATION_EXTENT = 32.5F;
    public static final int SCARE_CHECK_RANGE_XZ = 7;
    public static final int SCARE_CHECK_RANGE_Y = 2;

    public enum ArmadilloState {
        IDLE("idle", false, 0, 0),
        ROLLING("rolling", true, 10, 1),
        SCARED("scared", true, 50, 2),
        UNROLLING("unrolling", true, 30, 3);

        private final String name;
        private final boolean threatened;
        private final int animationDuration;
        private final int id;

        ArmadilloState(String name, boolean threatened, int animationDuration, int id) {
            this.name = name;
            this.threatened = threatened;
            this.animationDuration = animationDuration;
            this.id = id;
        }

        public static ArmadilloState fromName(String n) {
            for (ArmadilloState s : values()) if (s.name.equals(n)) return s;
            return IDLE;
        }

        public static ArmadilloState byId(int id) {
            for (ArmadilloState s : values()) if (s.id == id) return s;
            return IDLE;
        }

        public boolean shouldHideInShell(long inStateTicks) {
            return switch (this) {
                case IDLE -> false;
                case ROLLING -> inStateTicks > 5L;
                case SCARED -> true;
                case UNROLLING -> inStateTicks < 26L;
            };
        }

        public String getSerializedName() { return name; }

        public boolean isThreatened() { return threatened; }

        public int animationDuration() { return animationDuration; }

        public int id() { return id; }
    }

    private long inStateTicks;
    private int scuteTime;
    private ArmadilloState lastSyncedState = ArmadilloState.IDLE;
    private Targetable currentAttacker;

    public Armadillo(ChunkLevel level, int id) {
        super(EntityType.ARMADILLO, level, id);
        getNavigation().setCanFloat(true);
        scuteTime = pickNextScuteDropTime();
        brain = makeBrain();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Brain<Armadillo> makeBrain() {
        Brain<Armadillo> b = new Brain<>(java.util.List.of(MemoryModuleType.IS_PANICKING, MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY, MemoryModuleType.WALK_TARGET,
                MemoryModuleType.LOOK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.PATH, MemoryModuleType.NEAREST_LIVING_ENTITIES,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.TEMPTING_PLAYER, MemoryModuleType.TEMPTATION_COOLDOWN_TICKS, MemoryModuleType.GAZE_COOLDOWN_TICKS,
                MemoryModuleType.IS_TEMPTED, MemoryModuleType.BREED_TARGET, MemoryModuleType.NEAREST_VISIBLE_ADULT, MemoryModuleType.DANGER_DETECTED_RECENTLY),
                (java.util.List) java.util.List.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.HURT_BY, SensorType.ARMADILLO_TEMPTATIONS, SensorType.NEAREST_ADULT,
                        SensorType.ARMADILLO_SCARE_DETECTED));
        return ArmadilloAi.makeBrain(b);
    }

    @SuppressWarnings("unchecked")
    public Brain<Armadillo> armadilloBrain() { return (Brain<Armadillo>) brain; }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 12.0).add(Attribute.MOVEMENT_SPEED, 0.14);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_STATE, SetEntityData.SERIALIZER_ARMADILLO_STATE, ArmadilloState.IDLE.id());
    }

    public boolean isScared() { return getState() != ArmadilloState.IDLE; }

    public boolean shouldHideInShell() { return getState().shouldHideInShell(inStateTicks); }

    public boolean shouldSwitchToScaredState() { return getState() == ArmadilloState.ROLLING && inStateTicks > ArmadilloState.ROLLING.animationDuration(); }

    public ArmadilloState getState() { return ArmadilloState.byId((Integer) entityData.get(DATA_STATE)); }

    public void switchToState(ArmadilloState state) {
        entityData.set(DATA_STATE, state.id());
        if (state != lastSyncedState) {   // onSyncedDataUpdated: 상태가 바뀌면 상태 틱을 0 으로
            lastSyncedState = state;
            inStateTicks = 0L;
        }
    }

    @Override protected float babyScale() { return BABY_SCALE; }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) { return new Armadillo(level, level.nextEntityId()); }

    @Override
    protected void customServerAiStep() {
        armadilloBrain().tick(level, this);
        ArmadilloAi.updateActivity(this);
        if (isAlive() && !isBaby() && --scuteTime <= 0) {
            playSound("entity.armadillo.scute_drop", 1.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
            spawnAtLocation(new ItemStack(ItemContext.id("minecraft:item", "armadillo_scute"), 1));
            scuteTime = pickNextScuteDropTime();
        }
        super.customServerAiStep();
    }

    private int pickNextScuteDropTime() { return random.nextInt(20 * 60 * 5) + 20 * 60 * 5; }

    @Override
    protected void aiStep() {
        super.aiStep();
        inStateTicks++;
    }

    @Override public int getMaxHeadXRot() { return isScared() ? 0 : 32; }

    public boolean isScaredBy(Targetable entity) {
        if (!boundingBox().inflate(SCARE_CHECK_RANGE_XZ, SCARE_CHECK_RANGE_Y, SCARE_CHECK_RANGE_XZ).intersects(entity.boundingBox())) return false;
        if (entity instanceof Entity e && e.type().is("minecraft:undead")) return true;
        if (getLastHurtByMob() == entity) return true;
        if (entity instanceof Player p) {
            if (p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) return false;
            return p.sprinting();   // TODO(탑승): 탑승 중인 플레이어도 무섭게 본다
        }
        return false;
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("state", getState().getSerializedName());
        tag.putInt("scute_time", scuteTime);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        switchToState(ArmadilloState.fromName(tag.tags().get("state") instanceof String s ? s : "idle"));
        if (tag.tags().containsKey("scute_time")) scuteTime = tag.getInt("scute_time");
    }

    public void rollUp() {
        if (!isScared()) {
            stopInPlace();
            resetLove();
            makeSound("entity.armadillo.roll");
            switchToState(ArmadilloState.ROLLING);
        }
    }

    public void rollOut() {
        if (isScared()) {
            makeSound("entity.armadillo.unroll_finish");
            switchToState(ArmadilloState.IDLE);
        }
    }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (isScared()) amount = (amount - 1.0F) / 2.0F;
        currentAttacker = attacker;
        try {
            return super.hurtBy(source, amount, attacker);
        } finally {
            currentAttacker = null;
        }
    }

    @Override
    protected void actuallyHurt(Damage source, float amount) {
        super.actuallyHurt(source, amount);
        if (!isNoAi() && !isDeadOrDying()) {
            if (currentAttacker != null) {
                getBrain().setMemoryWithExpiry(MemoryModuleType.DANGER_DETECTED_RECENTLY, true, 80L);
                if (canStayRolledUp()) rollUp();
            } else if (org.domi.furnace.server.entity.damage.DamageType.byKey(source.type()).isInTag("minecraft:panic_environmental_causes")) {
                rollOut();
            }
        }
    }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        if (!held.isEmpty() && ItemTools.itemName(held).equals("minecraft:brush") && brushOffScute()) {
            var inv = p.inventory();
            int ws = hand == 0 ? inv.heldWindowSlot() : 45;
            PlayerActor actor = new PlayerActor(p, hand == 0 ? held : ItemStack.EMPTY, hand == 1 ? held : ItemStack.EMPTY, 0);
            org.domi.furnace.server.block.BlockBreaking.hurtAndBreak(level, actor, hand, held, 16);
            if (actor.handChanged(hand)) inv.set(ws, actor.itemInHand(hand));
            return true;
        }
        return isScared() ? false : super.interact(p, hand, held, region);
    }

    @Override
    public void ageUp(int seconds, boolean forced) {
        if (isBaby() && forced) makeSound("entity.armadillo.eat");
        super.ageUp(seconds, forced);
    }

    public boolean brushOffScute() {
        if (isBaby()) return false;
        spawnAtLocation(new ItemStack(ItemContext.id("minecraft:item", "armadillo_scute"), 1));
        playSound("entity.armadillo.brush", 1.0F, 1.0F);
        return true;
    }

    public boolean canStayRolledUp() { return !isPanicking() && !isInLiquid(); }   // TODO(끈·탑승): 목줄/탑승 중이 아닐 것

    @Override
    public void setInLove(Player cause) {
        super.setInLove(cause);
        makeSound("entity.armadillo.eat");
    }

    @Override public boolean canFallInLove() { return super.canFallInLove() && !isScared(); }

    @Override public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:armadillo_food"); }

    @Override public String getAmbientSound() { return isScared() ? null : "entity.armadillo.ambient"; }

    @Override protected String getDeathSound() { return "entity.armadillo.death"; }

    @Override protected String getHurtSound(Damage source) { return isScared() ? "entity.armadillo.hurt_reduced" : "entity.armadillo.hurt"; }
}
