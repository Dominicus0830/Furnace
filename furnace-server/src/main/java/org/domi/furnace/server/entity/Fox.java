// 바닐라 Fox: 낮에 자고, 몰래 다가가(crouch) 덮치는(pounce) 여우. 입에 아이템을 물고 있고, 신뢰하는 플레이어 2명(Trusted)을 기억한다. 눈 바이옴은 흰 여우.
// TODO(마을 산책·가루눈 위 걷기): StrollThroughVillageGoal(마을 구조물)·ClimbOnTopOfPowderSnowGoal. TODO(먹이 효과): 입에 문 음식의 효과는 확률만 적용한다.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Clip;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.control.LookControl;
import org.domi.furnace.server.entity.ai.control.MoveControl;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.world.gen.BiomeTags;

public class Fox extends Animal {
    public static final int DATA_TYPE_ID = 17, DATA_FLAGS_ID = 18, DATA_TRUSTED_0 = 19, DATA_TRUSTED_1 = 20;
    private static final int FLAG_SITTING = 1, FLAG_CROUCHING = 4, FLAG_INTERESTED = 8, FLAG_POUNCING = 16, FLAG_SLEEPING = 32, FLAG_FACEPLANTED = 64, FLAG_DEFENDING = 128;
    private static final Prop BERRY_AGE = B.prop("age");

    private final Predicate<Targetable> preyOrTurtle = t -> t instanceof Chicken || t instanceof Rabbit;
    private NearestAttackableTargetGoal landTargetGoal;
    private NearestAttackableTargetGoal turtleTargetGoal;
    private NearestAttackableTargetGoal fishTargetGoal;
    private float interestedAngle, interestedAngleO;
    float crouchAmount, crouchAmountO;
    private int ticksSinceEaten;

    public Fox(ChunkLevel level, int id) {
        super(EntityType.FOX, level, id);
        this.lookControl = new FoxLookControl();
        this.moveControl = new FoxMoveControl();
        setPathfindingMalus(org.domi.furnace.server.entity.ai.navigation.PathType.DANGER_OTHER, 0.0F);
        setPathfindingMalus(org.domi.furnace.server.entity.ai.navigation.PathType.DAMAGE_OTHER, 0.0F);
        setCanPickUpLoot(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_TYPE_ID, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_FLAGS_ID, SetEntityData.SERIALIZER_BYTE, (byte) 0);
        entityData.define(DATA_TRUSTED_0, SetEntityData.SERIALIZER_OPTIONAL_UUID, null);
        entityData.define(DATA_TRUSTED_1, SetEntityData.SERIALIZER_OPTIONAL_UUID, null);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 0.3F).add(Attribute.MAX_HEALTH, 10.0).add(Attribute.FOLLOW_RANGE, 32.0).add(Attribute.ATTACK_DAMAGE, 2.0)
                .add(Attribute.SAFE_FALL_DISTANCE, 5.0);
    }

    @Override
    protected void registerGoals() {
        landTargetGoal = new NearestAttackableTargetGoal(this, Animal.class, 10, false, false, t -> t instanceof Chicken || t instanceof Rabbit);
        turtleTargetGoal = new NearestAttackableTargetGoal(this, Turtle.class, 10, false, false, t -> t instanceof Turtle tt && tt.isBaby() && !tt.isInWater());
        fishTargetGoal = new NearestAttackableTargetGoal(this, AbstractFish.class, 20, false, false, t -> t instanceof AbstractSchoolingFish);
        goalSelector.addGoal(0, new FoxFloatGoal());
        goalSelector.addGoal(1, new FaceplantGoal());
        goalSelector.addGoal(2, new FoxPanicGoal(2.2));
        goalSelector.addGoal(3, new FoxBreedGoal(1.0));
        goalSelector.addGoal(4, new AvoidEntityGoal(this, Player.class, t -> true, 16.0F, 1.6, 1.4,
                t -> !(t instanceof Player p) || p.gameMode() != org.domi.furnace.server.world.GameMode.SPECTATOR && p.gameMode() != org.domi.furnace.server.world.GameMode.CREATIVE && !p.sneaking() && !trusts(p.uuid()) && !isDefending()));
        goalSelector.addGoal(4, new AvoidEntityGoal(this, Wolf.class, t -> t instanceof Wolf w && !w.isTame() && !isDefending(), 8.0F, 1.6, 1.4, t -> true));
        goalSelector.addGoal(4, new AvoidEntityGoal(this, PolarBear.class, t -> !isDefending(), 8.0F, 1.6, 1.4, t -> true));
        goalSelector.addGoal(5, new StalkPreyGoal());
        goalSelector.addGoal(6, new FoxPounceGoal());
        goalSelector.addGoal(6, new SeekShelterGoal(1.25));
        goalSelector.addGoal(7, new FoxMeleeAttackGoal(1.2F, true));
        goalSelector.addGoal(7, new SleepGoal());
        goalSelector.addGoal(8, new FoxFollowParentGoal(1.25));
        // 9: StrollThroughVillageGoal(32, 200) — TODO(마을)
        goalSelector.addGoal(10, new EatBerriesGoal(1.2F, 12, 1));
        goalSelector.addGoal(10, new LeapAtTargetGoal(this, 0.4F));
        goalSelector.addGoal(11, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(11, new FoxSearchForItemsGoal());
        goalSelector.addGoal(12, new FoxLookAtPlayerGoal());
        goalSelector.addGoal(13, new PerchAndSearchGoal());
        targetSelector.addGoal(3, new DefendTrustedTargetGoal());
    }

    // ------------------------------------------------------------------ 상태 비트

    private boolean flag(int f) { return ((Byte) entityData.get(DATA_FLAGS_ID) & f) != 0; }

    private void setFlag(int f, boolean on) {
        byte b = (Byte) entityData.get(DATA_FLAGS_ID);
        entityData.set(DATA_FLAGS_ID, (byte) (on ? b | f : b & ~f));
    }

    public boolean isSitting() { return flag(FLAG_SITTING); }

    public void setSitting(boolean v) { setFlag(FLAG_SITTING, v); }

    public boolean isFaceplanted() { return flag(FLAG_FACEPLANTED); }

    void setFaceplanted(boolean v) { setFlag(FLAG_FACEPLANTED, v); }

    boolean isDefending() { return flag(FLAG_DEFENDING); }

    void setDefending(boolean v) { setFlag(FLAG_DEFENDING, v); }

    public boolean isSleeping() { return flag(FLAG_SLEEPING); }

    void setSleeping(boolean v) { setFlag(FLAG_SLEEPING, v); }

    public boolean isPouncing() { return flag(FLAG_POUNCING); }

    public void setPouncing(boolean v) { setFlag(FLAG_POUNCING, v); }

    public boolean isCrouching() { return flag(FLAG_CROUCHING); }

    public void setIsCrouching(boolean v) { setFlag(FLAG_CROUCHING, v); }

    public boolean isInterested() { return flag(FLAG_INTERESTED); }

    public void setIsInterested(boolean v) { setFlag(FLAG_INTERESTED, v); }

    public boolean isFullyCrouched() { return crouchAmount == 3.0F; }

    void wakeUp() { setSleeping(false); }

    void clearStates() {
        setIsInterested(false);
        setIsCrouching(false);
        setSitting(false);
        setSleeping(false);
        setDefending(false);
        setFaceplanted(false);
    }

    boolean canMove() { return !isSleeping() && !isSitting() && !isFaceplanted(); }

    public String foxType() { return (Integer) entityData.get(DATA_TYPE_ID) == 1 ? "snow" : "red"; }

    private void setFoxType(int t) { entityData.set(DATA_TYPE_ID, t); }

    List<UUID> trustedUuids() {
        List<UUID> l = new ArrayList<>();
        l.add((UUID) entityData.get(DATA_TRUSTED_0));
        l.add((UUID) entityData.get(DATA_TRUSTED_1));
        return l;
    }

    void addTrustedUuid(UUID u) {
        if (entityData.get(DATA_TRUSTED_0) != null) entityData.set(DATA_TRUSTED_1, u);
        else entityData.set(DATA_TRUSTED_0, u);
    }

    boolean trusts(UUID u) { return trustedUuids().contains(u); }

    // ------------------------------------------------------------------ 저장·생성

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        NbtList trusted = new NbtList((byte) 11);
        for (UUID u : trustedUuids()) if (u != null) trusted.add(uuidToInts(u));
        tag.put("Trusted", trusted);
        tag.putBoolean("Sleeping", isSleeping());
        tag.putString("Type", foxType());
        tag.putBoolean("Sitting", isSitting());
        tag.putBoolean("Crouching", isCrouching());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.tags().get("Trusted") instanceof NbtList l) {
            for (int i = 0; i < l.size(); i++) {
                if (l.get(i) instanceof int[] u && u.length == 4) addTrustedUuid(new UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL));
            }
        }
        setSleeping(tag.getBoolean("Sleeping"));
        setFoxType("snow".equals(tag.tags().get("Type")) ? 1 : 0);
        setSitting(tag.getBoolean("Sitting"));
        setIsCrouching(tag.getBoolean("Crouching"));
        setTargetGoals();
    }

    /** FoxGroupData: 처음 정한 종류와 몇 마리째인지. */
    public static final class FoxGroupData extends AgeableMob.GroupData {
        public final int type;

        public FoxGroupData(int type) {
            super(false);
            this.type = type;
        }
    }

    @Override
    public AgeableMob.GroupData finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty, AgeableMob.GroupData data) {
        Pos p = blockPosition();
        int type = BiomeTags.is(level.biomeAt(p.x(), p.y(), p.z()), "minecraft:spawns_snow_foxes") ? 1 : 0;
        boolean baby = false;
        if (data instanceof FoxGroupData g) {
            type = g.type;
            if (g.groupSize >= 2) baby = true;
        } else {
            data = new FoxGroupData(type);
        }
        setFoxType(type);
        if (baby) setAge(-24000);
        setTargetGoals();
        populateDefaultEquipmentSlots(r);
        return super.finalizeSpawn(r, difficulty, data);
    }

    private void setTargetGoals() {
        targetSelector.removeGoal(landTargetGoal);
        targetSelector.removeGoal(turtleTargetGoal);
        targetSelector.removeGoal(fishTargetGoal);
        if (foxType().equals("red")) {
            targetSelector.addGoal(4, landTargetGoal);
            targetSelector.addGoal(4, turtleTargetGoal);
            targetSelector.addGoal(6, fishTargetGoal);
        } else {
            targetSelector.addGoal(4, fishTargetGoal);
            targetSelector.addGoal(6, landTargetGoal);
            targetSelector.addGoal(6, turtleTargetGoal);
        }
    }

    private void populateDefaultEquipmentSlots(org.domi.furnace.server.block.RandomSource r) {
        if (r.nextFloat() < 0.2F) {
            float f = r.nextFloat();
            String item;
            if (f < 0.05F) item = "emerald";
            else if (f < 0.2F) item = "egg";
            else if (f < 0.4F) item = r.nextBoolean() ? "rabbit_foot" : "rabbit_hide";
            else if (f < 0.6F) item = "wheat";
            else if (f < 0.8F) item = "leather";
            else item = "feather";
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ItemContext.id("minecraft:item", item), 1));
        }
    }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) {
        Fox child = new Fox(level, level.nextEntityId());
        child.setFoxType(random.nextBoolean() ? ((Integer) entityData.get(DATA_TYPE_ID)) : (Integer) ((Fox) partner).entityData.get(DATA_TYPE_ID));
        return child;
    }

    @Override
    public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:fox_food"); }

    @Override
    protected float babyScale() { return 0.5F; }

    @Override
    protected void refreshBabyDimensions() {
        EntityType t = type();
        if (isBaby()) setDimensions(t.width() * 0.5F, t.height() * 0.5F, 0.2975F);
        else setDimensions(t.width(), t.height(), t.eyeHeight());
    }

    // ------------------------------------------------------------------ 틱

    private boolean canEat(ItemStack s) { return ItemTools.component(s, "minecraft:food") instanceof java.util.Map && getTarget() == null && onGround() && !isSleeping(); }

    @Override
    protected void aiStep() {
        if (isAlive() && isEffectiveAi()) {
            ticksSinceEaten++;
            ItemStack s = getItemBySlot(EquipmentSlot.MAINHAND);
            if (canEat(s)) {
                if (ticksSinceEaten > 600) {
                    ItemStack result = finishEating(s);
                    if (!result.isEmpty()) setItemSlot(EquipmentSlot.MAINHAND, result);
                    else setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    ticksSinceEaten = 0;
                } else if (ticksSinceEaten > 560 && random.nextFloat() < 0.1F) {
                    playSound("entity.fox.eat", 1.0F, 1.0F);
                    broadcastEntityEvent((byte) 45);
                }
            }
            Targetable t = getTarget();
            if (t == null || !t.isAlive()) {
                setIsCrouching(false);
                setIsInterested(false);
            }
        }
        if (isSleeping() || isDeadOrDying()) {
            jumping = false;
            xxa = 0.0F;
            zza = 0.0F;
        }
        super.aiStep();
        if (isDefending() && random.nextFloat() < 0.05F) playSound("entity.fox.aggro", 1.0F, 1.0F);
    }

    /** ItemStack.finishUsingItem(food) 의 단순형: 먹는 소리와 확률 효과(난수만), 하나 소비. */
    private ItemStack finishEating(ItemStack s) {
        playSound("entity.generic.eat", 1.0F, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.4F);
        if (ItemTools.component(s, "minecraft:food") instanceof java.util.Map<?, ?> f && f.get("effects") instanceof List<?> fx) {
            for (Object o : fx) {
                if (o instanceof java.util.Map<?, ?> m) {
                    float prob = m.get("probability") instanceof Number x ? x.floatValue() : 1.0F;
                    random.nextFloat();   // 확률 판정 — TODO(효과 적용)
                }
            }
        }
        return s.withCount(s.count() - 1);
    }

    @Override
    public void tick() {
        super.tick();
        if (isEffectiveAi()) {
            boolean inWater = isInWater();
            if (inWater || getTarget() != null || level.isThundering()) wakeUp();
            if (inWater || isSleeping()) setSitting(false);
            if (isFaceplanted() && level.random().nextFloat() < 0.2F) {
                Pos p = blockPosition();
                level.levelEvent(null, 2001, p, level.getBlockState(p));
            }
        }
        interestedAngleO = interestedAngle;
        interestedAngle += ((isInterested() ? 1.0F : 0.0F) - interestedAngle) * 0.4F;
        crouchAmountO = crouchAmount;
        if (isCrouching()) {
            crouchAmount += 0.2F;
            if (crouchAmount > 3.0F) crouchAmount = 3.0F;
        } else {
            crouchAmount = 0.0F;
        }
        // Entity.updatePose: 자는 중 → SLEEPING, 몸을 낮추면 CROUCHING
        setPose(isSleeping() ? POSE_SLEEPING : isCrouching() ? POSE_CROUCHING : POSE_STANDING);
    }

    @Override
    public void setTarget(Targetable t) {
        if (isDefending() && t == null) setDefending(false);
        super.setTarget(t);
    }

    @Override
    public boolean canHoldItem(ItemStack stack) {
        ItemStack held = getItemBySlot(EquipmentSlot.MAINHAND);
        return held.isEmpty() || ticksSinceEaten > 0 && ItemTools.component(stack, "minecraft:food") instanceof java.util.Map && !(ItemTools.component(held, "minecraft:food") instanceof java.util.Map);
    }

    private void spitOutItem(ItemStack stack) {
        if (!stack.isEmpty()) {
            double[] v = Clip.viewVector(xRot(), yRot());
            ItemEntity ie = new ItemEntity(level, level.nextEntityId(), x() + v[0], y() + 1.0, z() + v[2], stack);
            ie.setPickUpDelay(40);
            ie.setThrower(this);
            playSound("entity.fox.spit", 1.0F, 1.0F);
            level.addFreshEntity(ie);
        }
    }

    private void dropItemStack(ItemStack stack) {
        level.addFreshEntity(new ItemEntity(level, level.nextEntityId(), x(), y(), z(), stack));
    }

    @Override
    protected void pickUpItem(ItemEntity ie) {
        ItemStack stack = ie.getItem();
        if (canHoldItem(stack)) {
            int count = stack.count();
            if (count > 1) dropItemStack(stack.withCount(count - 1));
            spitOutItem(getItemBySlot(EquipmentSlot.MAINHAND));
            onItemPickup(ie);
            setItemSlotAndDropWhenKilled(EquipmentSlot.MAINHAND, stack.withCount(1));
            ie.broadcastTake(this, 1);
            ie.discard();
            ticksSinceEaten = 0;
        }
    }

    @Override
    protected void usePlayerItem(Player p, int hand, ItemStack held) {
        if (isFood(held)) playSound("entity.fox.eat", 1.0F, 1.0F);
        super.usePlayerItem(p, hand, held);
    }

    @Override
    public String getAmbientSound() {
        if (isSleeping()) return "entity.fox.sleep";
        if (!level.isDay() && random.nextFloat() < 0.1F) {
            List<Player> near = new ArrayList<>();
            level.forEachPlayerIn(boundingBox().inflate(16.0, 16.0, 16.0), q -> { if (q.gameMode() != org.domi.furnace.server.world.GameMode.SPECTATOR) near.add(q); });
            if (near.isEmpty()) return "entity.fox.screech";
        }
        return "entity.fox.ambient";
    }

    @Override protected String getHurtSound(Damage source) { return "entity.fox.hurt"; }

    @Override protected String getDeathSound() { return "entity.fox.death"; }

    @Override
    protected void playAmbientSound() {
        String s = getAmbientSound();
        if ("entity.fox.screech".equals(s)) playSound(s, 2.0F, getVoicePitch());
        else makeSound(s);
    }

    /** 앞쪽 6칸(3칸 높이)이 모두 뚫려(교체 가능) 덮칠 수 있나. */
    static boolean isPathClear(Fox fox, Targetable target) {
        double dz = target.z() - fox.z();
        double dx = target.x() - fox.x();
        double slope = dz / dx;
        for (int i = 0; i < 6; i++) {
            double zz = slope == 0.0 ? 0.0 : dz * (i / 6.0F);
            double xx = slope == 0.0 ? dx * (i / 6.0F) : zz / slope;
            for (int j = 1; j < 4; j++) {
                Pos p = new Pos((int) Math.floor(fox.x() + xx), (int) Math.floor(fox.y() + j), (int) Math.floor(fox.z() + zz));
                if (!org.domi.furnace.server.world.BlockAttrs.get().isReplaceable(fox.level.getBlockState(p))) return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ 제어

    private final class FoxLookControl extends LookControl {
        FoxLookControl() { super(Fox.this); }

        @Override
        public void tick() {
            if (!isSleeping()) super.tick();
        }

        @Override
        protected boolean resetXRotOnTick() { return !isPouncing() && !isCrouching() && !isInterested() && !isFaceplanted(); }
    }

    private final class FoxMoveControl extends MoveControl {
        FoxMoveControl() { super(Fox.this); }

        @Override
        public void tick() {
            if (canMove()) super.tick();
        }
    }

    // ------------------------------------------------------------------ 목표

    /** 위협으로 보는 대상(수줍은 몹 포함): 닭·토끼·몬스터, 길들이지 않은 길들일 수 있는 동물, 신뢰하지 않는 플레이어 등. */
    private final class AlertablePredicate implements Predicate<Targetable> {
        @Override
        public boolean test(Targetable t) {
            if (t instanceof Fox) return false;
            if (t instanceof Chicken || t instanceof Rabbit || t instanceof Monster) return true;
            if (t instanceof TamableAnimal ta) return !ta.isTame();
            if (t instanceof Player p && (p.vitals().isSpectator() || p.vitals().isCreative())) return false;
            if (t instanceof Player p) return !trusts(p.uuid());
            return t instanceof Entity;   // TODO(자는 몹·웅크린 몹 제외)
        }
    }

    private abstract class FoxBehaviorGoal extends Goal {
        private final TargetingConditions alertableTargeting = TargetingConditions.forCombat().range(12.0).ignoreLineOfSight().selector(new AlertablePredicate());

        protected boolean hasShelter() {
            Pos p = new Pos((int) Math.floor(x()), (int) Math.floor(boundingBox().maxY()), (int) Math.floor(z()));
            return !level.canSeeSky(p) && getWalkTargetValue(p) >= 0.0F;
        }

        protected boolean alertable() {
            return !level.getEntitiesOfClass(LivingEntity.class, boundingBox().inflate(12.0, 6.0, 12.0), e -> alertableTargeting.test(Fox.this, e)).isEmpty()
                    || playersNear();
        }

        private boolean playersNear() {
            boolean[] found = {false};
            level.forEachPlayerIn(boundingBox().inflate(12.0, 6.0, 12.0), p -> { if (alertableTargeting.test(Fox.this, p)) found[0] = true; });
            return found[0];
        }
    }

    private final class FoxFloatGoal extends FloatGoal {
        FoxFloatGoal() { super(Fox.this); }

        @Override public void start() { super.start(); clearStates(); }

        @Override
        public boolean canUse() { return isInWater() && fluidHeight(true) > 0.25 || isInLava(); }
    }

    private final class FaceplantGoal extends Goal {
        private int countdown;

        FaceplantGoal() { setFlags(EnumSet.of(Flag.LOOK, Flag.JUMP, Flag.MOVE)); }

        @Override public boolean canUse() { return isFaceplanted(); }

        @Override public boolean canContinueToUse() { return canUse() && countdown > 0; }

        @Override public void start() { countdown = adjustedTickDelay(40); }

        @Override public void stop() { setFaceplanted(false); }

        @Override public void tick() { countdown--; }
    }

    private final class FoxPanicGoal extends PanicGoal {
        FoxPanicGoal(double speed) { super(Fox.this, speed); }

        @Override protected boolean shouldPanic() { return !isDefending() && super.shouldPanic(); }
    }

    private final class FoxBreedGoal extends BreedGoal {
        FoxBreedGoal(double speed) { super(Fox.this, speed); }

        @Override
        public void start() {
            clearStates();
            ((Fox) partner).clearStates();
            super.start();
        }

        @Override
        protected void breed() {
            Fox fox = Fox.this;
            Fox other = (Fox) partner;
            Fox child = (Fox) fox.getBreedOffspring(fox.level, other);
            if (child != null) {
                UUID a = fox.getLoveCauseUuid(), b = other.getLoveCauseUuid();
                if (a != null) child.addTrustedUuid(a);
                if (b != null && !b.equals(a)) child.addTrustedUuid(b);
                fox.setAge(6000);
                other.setAge(6000);
                fox.resetLove();
                other.resetLove();
                child.setAge(-24000);
                child.moveTo(fox.x(), fox.y(), fox.z(), 0.0F, 0.0F);
                fox.level.addFreshEntity(child);
                fox.broadcastEntityEvent((byte) 18);
                if (fox.level.gameRule("doMobLoot")) {
                    fox.level.addFreshEntity(new ExperienceOrb(fox.level, fox.level.nextEntityId(), fox.x(), fox.y(), fox.z(), fox.random.nextInt(7) + 1));
                }
            }
        }
    }

    private final class StalkPreyGoal extends Goal {
        StalkPreyGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override
        public boolean canUse() {
            if (isSleeping()) return false;
            Targetable t = getTarget();
            if (t == null || !t.isAlive() || !(t instanceof Chicken || t instanceof Rabbit)) return false;
            double dx = x() - t.x(), dy = y() - t.y(), dz = z() - t.z();
            return dx * dx + dy * dy + dz * dz > 36.0 && !isCrouching() && !isInterested() && !jumping;
        }

        @Override
        public void start() {
            setSitting(false);
            setFaceplanted(false);
        }

        @Override
        public void stop() {
            Targetable t = getTarget();
            if (t != null && isPathClear(Fox.this, t)) {
                setIsInterested(true);
                setIsCrouching(true);
                getNavigation().stop();
                getLookControl().setLookAtPlayerOrEntity(t, getMaxHeadYRot(), getMaxHeadXRot());
            } else {
                setIsInterested(false);
                setIsCrouching(false);
            }
        }

        @Override
        public void tick() {
            Targetable t = getTarget();
            if (t != null) {
                getLookControl().setLookAtPlayerOrEntity(t, getMaxHeadYRot(), getMaxHeadXRot());
                double dx = x() - t.x(), dy = y() - t.y(), dz = z() - t.z();
                if (dx * dx + dy * dy + dz * dz <= 36.0) {
                    setIsInterested(true);
                    setIsCrouching(true);
                    getNavigation().stop();
                } else {
                    getNavigation().moveTo(t, 1.5);
                }
            }
        }
    }

    private final class FoxPounceGoal extends Goal {
        FoxPounceGoal() { setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE)); }

        @Override
        public boolean canUse() {
            if (!isFullyCrouched()) return false;
            Targetable t = getTarget();
            if (t == null || !t.isAlive()) return false;
            boolean clear = isPathClear(Fox.this, t);
            if (!clear) {
                getNavigation().createPath(t, 0);
                setIsCrouching(false);
                setIsInterested(false);
            }
            return clear;
        }

        @Override
        public boolean canContinueToUse() {
            Targetable t = getTarget();
            if (t == null || !t.isAlive()) return false;
            double d = vy();
            return (!(d * d < 0.05F) || !(Math.abs(xRot()) < 15.0F) || !onGround()) && !isFaceplanted();
        }

        @Override public boolean isInterruptable() { return false; }

        @Override
        public void start() {
            setJumping(true);
            setPouncing(true);
            setIsInterested(false);
            Targetable t = getTarget();
            if (t != null) {
                getLookControl().setLookAtPlayerOrEntity(t, 60.0F, 30.0F);
                double dx = t.x() - x(), dy = t.y() - y(), dz = t.z() - z();
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len > 1.0E-5F) {
                    setDeltaMovement(vx() + dx / len * 0.8, vy() + 0.9, vz() + dz / len * 0.8);
                } else {
                    setDeltaMovement(vx(), vy() + 0.9, vz());
                }
            }
            getNavigation().stop();
        }

        @Override
        public void stop() {
            setIsCrouching(false);
            crouchAmount = 0.0F;
            crouchAmountO = 0.0F;
            setIsInterested(false);
            setPouncing(false);
        }

        @Override
        public void tick() {
            Targetable t = getTarget();
            if (t != null) getLookControl().setLookAtPlayerOrEntity(t, 60.0F, 30.0F);
            if (!isFaceplanted()) {
                double vx = vx(), vy = vy(), vz = vz();
                if (vy * vy < 0.03F && xRot() != 0.0F) {
                    setXRot(xRot() + 0.2F * Mth.wrapDegrees(0.0F - xRot()));
                } else {
                    double h = Math.sqrt(vx * vx + vz * vz);
                    double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
                    double d = Math.signum(-vy) * Math.acos(h / len) * 180.0F / (float) Math.PI;
                    setXRot((float) d);
                }
            }
            if (t != null && distanceTo(t) <= 2.0F) {
                doHurtTarget(t);
            } else if (xRot() > 0.0F && onGround() && (float) vy() != 0.0F && B.is(level.getBlockState(blockPosition()), "minecraft:snow")) {
                setXRot(60.0F);
                setTarget(null);
                setFaceplanted(true);
            }
        }
    }

    private double distanceTo(Targetable t) {
        double dx = x() - t.x(), dy = y() - t.y(), dz = z() - t.z();
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private final class SeekShelterGoal extends FleeSunGoal {
        private int interval = reducedTickDelay(100);

        SeekShelterGoal(double speed) { super(Fox.this, speed); }

        @Override
        public boolean canUse() {
            if (isSleeping() || mob.getTarget() != null) return false;
            if (level.isThundering() && level.canSeeSky(blockPosition())) return setWantedPos();
            if (interval > 0) {
                interval--;
                return false;
            }
            interval = 100;
            Pos p = blockPosition();
            return level.isDay() && level.canSeeSky(p) && setWantedPos();   // TODO(마을): isVillage 이면 제외
        }

        @Override
        public void start() {
            clearStates();
            super.start();
        }
    }

    private final class FoxMeleeAttackGoal extends MeleeAttackGoal {
        FoxMeleeAttackGoal(double speed, boolean followEvenIfNotSeen) { super(Fox.this, speed, followEvenIfNotSeen); }

        @Override
        protected void checkAndPerformAttack(Targetable target) {
            if (canPerformAttack(target)) {
                resetAttackCooldown();
                doHurtTarget(target);
                playSound("entity.fox.bite", 1.0F, 1.0F);
            }
        }

        @Override
        public void start() {
            setIsInterested(false);
            super.start();
        }

        @Override
        public boolean canUse() { return !isSitting() && !isSleeping() && !isCrouching() && !isFaceplanted() && super.canUse(); }
    }

    private final class SleepGoal extends FoxBehaviorGoal {
        private final int WAIT_TIME_BEFORE_SLEEP = reducedTickDelay(140);
        private int countdown = random.nextInt(WAIT_TIME_BEFORE_SLEEP);

        SleepGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }

        @Override
        public boolean canUse() {
            return xxa == 0.0F && yya == 0.0F && zza == 0.0F ? canSleep() || isSleeping() : false;
        }

        @Override public boolean canContinueToUse() { return canSleep(); }

        private boolean canSleep() {
            if (countdown > 0) {
                countdown--;
                return false;
            }
            return level.isDay() && hasShelter() && !alertable();   // TODO(가루눈): !isInPowderSnow
        }

        @Override
        public void stop() {
            countdown = random.nextInt(WAIT_TIME_BEFORE_SLEEP);
            clearStates();
        }

        @Override
        public void start() {
            setSitting(false);
            setIsCrouching(false);
            setIsInterested(false);
            setJumping(false);
            setSleeping(true);
            getNavigation().stop();
            getMoveControl().setWantedPosition(x(), y(), z(), 0.0);
        }
    }

    private final class FoxFollowParentGoal extends FollowParentGoal {
        FoxFollowParentGoal(double speed) { super(Fox.this, speed); }

        @Override public boolean canUse() { return !isDefending() && super.canUse(); }

        @Override public boolean canContinueToUse() { return !isDefending() && super.canContinueToUse(); }

        @Override
        public void start() {
            clearStates();
            super.start();
        }
    }

    private final class EatBerriesGoal extends MoveToBlockGoal {
        private int ticksWaited;

        EatBerriesGoal(double speed, int range, int vrange) { super(Fox.this, speed, range, vrange); }

        @Override public double acceptedDistance() { return 2.0; }

        @Override public boolean shouldRecalculatePath() { return tryTicks % 100 == 0; }

        @Override
        protected boolean isValidTarget(BlockLevel level, Pos pos) {
            int s = level.getBlockState(pos);
            return B.is(s, "minecraft:sweet_berry_bush") && BERRY_AGE.intValue(s) >= 2 || B.is(s, "minecraft:cave_vines") && B.prop("berries").is(s)
                    || B.is(s, "minecraft:cave_vines_plant") && B.prop("berries").is(s);
        }

        @Override
        public void tick() {
            if (isReachedTarget()) {
                if (ticksWaited >= 40) onReachedTarget();
                else ticksWaited++;
            } else if (random.nextFloat() < 0.05F) {
                playSound("entity.fox.sniff", 1.0F, 1.0F);
            }
            super.tick();
        }

        private void onReachedTarget() {
            if (level.gameRule("mobGriefing")) {
                int s = level.getBlockState(blockPos);
                if (B.is(s, "minecraft:sweet_berry_bush")) pickSweetBerries(s);
                else if (B.prop("berries").has(s) && B.prop("berries").is(s)) pickGlowBerry(s);
            }
        }

        private void pickGlowBerry(int s) {
            level.popResource(blockPos, B.stack("minecraft:glow_berries", 1));
            level.playSound(null, blockPos, "minecraft:block.cave_vines.pick_berries", "blocks", 1.0F, level.random().nextFloat() * (1.2F - 0.8F) + 0.8F);
            level.setBlock(blockPos, B.prop("berries").with(s, false), 2);
        }

        private void pickSweetBerries(int s) {
            int age = BERRY_AGE.intValue(s);
            int amount = 1 + level.random().nextInt(2) + (age == 3 ? 1 : 0);
            ItemStack held = getItemBySlot(EquipmentSlot.MAINHAND);
            if (held.isEmpty()) {
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ItemContext.id("minecraft:item", "sweet_berries"), 1));
                amount--;
            }
            if (amount > 0) level.popResource(blockPos, new ItemStack(ItemContext.id("minecraft:item", "sweet_berries"), amount));
            playSound("minecraft:block.sweet_berry_bush.pick_berries", 1.0F, 1.0F);
            level.setBlock(blockPos, BERRY_AGE.withInt(s, 1), 2);
        }

        @Override public boolean canUse() { return !isSleeping() && super.canUse(); }

        @Override
        public void start() {
            ticksWaited = 0;
            setSitting(false);
            super.start();
        }
    }

    private final class FoxSearchForItemsGoal extends Goal {
        private final Predicate<ItemEntity> ALLOWED = e -> !e.hasPickUpDelay() && e.isAlive();

        FoxSearchForItemsGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            if (!getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) return false;
            if (getTarget() != null || getLastHurtByMob() != null) return false;
            if (!canMove()) return false;
            if (random.nextInt(reducedTickDelay(10)) != 0) return false;
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, boundingBox().inflate(8.0, 8.0, 8.0), ALLOWED);
            return !items.isEmpty() && getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
        }

        @Override
        public void tick() {
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, boundingBox().inflate(8.0, 8.0, 8.0), ALLOWED);
            if (getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && !items.isEmpty()) getNavigation().moveToEntityAt(items.get(0).blockPosition(), 1.2F);
        }

        @Override
        public void start() {
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, boundingBox().inflate(8.0, 8.0, 8.0), ALLOWED);
            if (!items.isEmpty()) getNavigation().moveToEntityAt(items.get(0).blockPosition(), 1.2F);
        }
    }

    private final class FoxLookAtPlayerGoal extends LookAtPlayerGoal {
        FoxLookAtPlayerGoal() { super(Fox.this, 24.0F); }

        @Override public boolean canUse() { return super.canUse() && !isFaceplanted() && !isInterested(); }

        @Override public boolean canContinueToUse() { return super.canContinueToUse() && !isFaceplanted() && !isInterested(); }
    }

    private final class PerchAndSearchGoal extends FoxBehaviorGoal {
        private double relX, relZ;
        private int lookTime, looksRemaining;

        PerchAndSearchGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override
        public boolean canUse() {
            return getLastHurtByMob() == null && random.nextFloat() < 0.02F && !isSleeping() && getTarget() == null && getNavigation().isDone() && !alertable()
                    && !isPouncing() && !isCrouching();
        }

        @Override public boolean canContinueToUse() { return looksRemaining > 0; }

        @Override
        public void start() {
            resetLook();
            looksRemaining = 2 + random.nextInt(3);
            setSitting(true);
            getNavigation().stop();
        }

        @Override public void stop() { setSitting(false); }

        @Override
        public void tick() {
            lookTime--;
            if (lookTime <= 0) {
                looksRemaining--;
                resetLook();
            }
            getLookControl().setLookAt(x() + relX, eyeY(), z() + relZ, getMaxHeadYRot(), getMaxHeadXRot());
        }

        private void resetLook() {
            double a = (Math.PI * 2) * random.nextDouble();
            relX = Math.cos(a);
            relZ = Math.sin(a);
            lookTime = adjustedTickDelay(80 + random.nextInt(20));
        }
    }

    private final class DefendTrustedTargetGoal extends NearestAttackableTargetGoal {
        private Targetable trustedLastHurt;
        private Targetable trusted;
        private int timestamp;

        DefendTrustedTargetGoal() {
            super(Fox.this, LivingEntity.class, 10, false, false, t -> t instanceof LivingEntity le && le.getLastHurtMob() != null && le.getLastHurtMobTimestamp() < tickCount + 600 && !trusts(NeutralMob.uuidOf(le)));
        }

        @Override
        public boolean canUse() {
            if (randomInterval > 0 && random.nextInt(randomInterval) != 0) return false;
            for (UUID u : trustedUuids()) {
                if (u != null && level.getTargetableByUuid(u) instanceof Targetable owner) {
                    trusted = owner;
                    trustedLastHurt = owner.getLastHurtMob();
                    int ts = owner.getLastHurtMobTimestamp();
                    return ts != timestamp && trustedLastHurt != null && targetConditions.test(Fox.this, trustedLastHurt);
                }
            }
            return false;
        }

        @Override
        public void start() {
            setTarget(trustedLastHurt);
            target = trustedLastHurt;
            if (trusted != null) timestamp = trusted.getLastHurtMobTimestamp();
            playSound("entity.fox.aggro", 1.0F, 1.0F);
            setDefending(true);
            wakeUp();
            super.start();
        }
    }
}
