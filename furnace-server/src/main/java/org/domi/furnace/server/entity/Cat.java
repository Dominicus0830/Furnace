// 바닐라 Cat: 길들이기(생선 1/3), 앉기, 목걸이 색, 고양이 변종(보름달엔 11종), 플레이어를 피함(길들이기 전).
// TODO: 마녀 오두막/마을에서 검은 고양이 변종
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Cat extends TamableAnimal {
    public static final int DATA_VARIANT = 19, DATA_IS_LYING = 20, DATA_RELAX_STATE_ONE = 21, DATA_COLLAR_COLOR = 22;
    private static final String[] VARIANTS = {"tabby", "black", "red", "siamese", "british_shorthair", "calico", "persian", "ragdoll", "white", "jellie", "all_black"};
    private AvoidEntityGoal avoidPlayersGoal;
    private TemptGoal temptGoal;

    public Cat(ChunkLevel level, int id) {
        super(EntityType.CAT, level, id);
        reassessTameGoals();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_VARIANT, SetEntityData.SERIALIZER_CAT_VARIANT, 0);
        entityData.define(DATA_IS_LYING, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_RELAX_STATE_ONE, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_COLLAR_COLOR, SetEntityData.SERIALIZER_INT, DyeColor.RED.id());
    }

    @Override
    protected void registerGoals() {
        temptGoal = new CatTemptGoal(this, 0.6, s -> ItemTags.is(s, "minecraft:cat_food"), true);
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(1, new TamablePanicGoal(this, 1.5));
        goalSelector.addGoal(2, new SitWhenOrderedToDoGoal(this));
        goalSelector.addGoal(3, new CatRelaxOnOwnerGoal(this));
        goalSelector.addGoal(4, temptGoal);
        goalSelector.addGoal(5, new CatLieOnBedGoal(this, 1.1, 8));
        goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 10.0F, 5.0F));
        goalSelector.addGoal(7, new CatSitOnBlockGoal(this, 0.8));
        goalSelector.addGoal(8, new LeapAtTargetGoal(this, 0.3F));
        goalSelector.addGoal(9, new OcelotAttackGoal(this));
        goalSelector.addGoal(10, new BreedGoal(this, 0.8));
        goalSelector.addGoal(11, new WaterAvoidingRandomStrollGoal(this, 0.8, 1.0000001E-5F));
        goalSelector.addGoal(12, new LookAtPlayerGoal(this, 10.0F));
        targetSelector.addGoal(1, new CatNonTameTarget(this, Rabbit.class));
        targetSelector.addGoal(1, new NonTameRandomTargetGoal(this, Turtle.class, false, Turtle.BABY_ON_LAND_SELECTOR));
    }

    public int variant() { return (Integer) entityData.get(DATA_VARIANT); }

    public void setVariant(int v) { entityData.set(DATA_VARIANT, v); }

    public DyeColor getCollarColor() { return DyeColor.byId((Integer) entityData.get(DATA_COLLAR_COLOR)); }

    private void setCollarColor(DyeColor c) { entityData.set(DATA_COLLAR_COLOR, c.id()); }

    public boolean isLying() { return (Boolean) entityData.get(DATA_IS_LYING); }

    public void setLying(boolean v) { entityData.set(DATA_IS_LYING, v); }

    public boolean isRelaxStateOne() { return (Boolean) entityData.get(DATA_RELAX_STATE_ONE); }

    void setRelaxStateOne(boolean v) { entityData.set(DATA_RELAX_STATE_ONE, v); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 10.0).add(Attribute.MOVEMENT_SPEED, 0.3F).add(Attribute.ATTACK_DAMAGE, 3.0);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("variant", "minecraft:" + VARIANTS[Math.min(variant(), VARIANTS.length - 1)]);
        tag.putByte("CollarColor", (byte) getCollarColor().id());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.tags().get("variant") instanceof String v) {
            for (int i = 0; i < VARIANTS.length; i++) if (("minecraft:" + VARIANTS[i]).equals(v)) setVariant(i);
        }
        if (tag.contains("CollarColor")) setCollarColor(DyeColor.byId(tag.getByte("CollarColor")));
    }

    @Override
    protected void customServerAiStep() {
        if (moveControl.hasWanted()) {
            double s = moveControl.getSpeedModifier();
            if (s == 0.6) {
                setPose(POSE_CROUCHING);
                setSprinting(false);
            } else if (s == 1.33) {
                setPose(POSE_STANDING);
                setSprinting(true);
            } else {
                setPose(POSE_STANDING);
                setSprinting(false);
            }
        } else {
            setPose(POSE_STANDING);
            setSprinting(false);
        }
    }

    @Override
    public String getAmbientSound() {
        if (isTame()) {
            if (isInLove()) return "entity.cat.purr";
            return random.nextInt(4) == 0 ? "entity.cat.purreow" : "entity.cat.ambient";
        }
        return "entity.cat.stray_ambient";
    }

    @Override protected String getHurtSound(Damage source) { return "entity.cat.hurt"; }

    @Override protected String getDeathSound() { return "entity.cat.death"; }

    @Override
    protected void usePlayerItem(Player p, int hand, ItemStack held) {
        if (isFood(held)) playSound("entity.cat.eat", 1.0F, 1.0F);
        super.usePlayerItem(p, hand, held);
    }

    @Override
    public void tick() {
        super.tick();
        if (temptGoal != null && temptGoal.isRunning() && !isTame() && tickCount % 100 == 0) playSound("entity.cat.beg_for_food", 1.0F, 1.0F);
    }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) {
        Cat child = new Cat(level, level.nextEntityId());
        if (partner instanceof Cat p) {
            child.setVariant(random.nextBoolean() ? variant() : p.variant());
            if (isTame()) {
                child.setOwnerUUID(getOwnerUUID());
                child.setTame(true, true);
                child.setCollarColor(random.nextBoolean() ? getCollarColor() : p.getCollarColor());
            }
        }
        return child;
    }

    @Override
    public boolean canMate(Animal o) {
        if (!isTame()) return false;
        return !(o instanceof Cat c) ? false : c.isTame() && super.canMate(o);
    }

    @Override
    public GroupData finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty, GroupData data) {
        data = super.finalizeSpawn(r, difficulty, data);
        boolean fullMoon = level.moonBrightness() > 0.9F;
        setVariant(level.random().nextInt(fullMoon ? 11 : 10));
        // #cats_spawn_as_black(swamp_hut) 조각 안이면 all_black + 유지. CatSpawner 는 moveTo 전에 불러 원점 기준이다(바닐라 그대로).
        if (level.structureWithPieceAt(blockPosition(), "minecraft:swamp_hut") != null) {
            setVariant(10);
            persistenceRequired = true;
        }
        return data;
    }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        String name = held.isEmpty() ? "" : ItemTools.itemName(held);
        if (isTame()) {
            if (isOwnedBy(p)) {
                if (name.endsWith("_dye")) {
                    String c = name.substring("minecraft:".length(), name.length() - "_dye".length());
                    for (DyeColor d : DyeColor.values()) {
                        if (d.key().equals(c) && d != getCollarColor()) {
                            setCollarColor(d);
                            usePlayerItem(p, hand, held);
                            return true;
                        }
                    }
                } else if (isFood(held) && health() < (float) attributeValue(Attribute.MAX_HEALTH)) {
                    float nutrition = ItemTools.component(held, "minecraft:food") instanceof java.util.Map<?, ?> f && f.get("nutrition") instanceof Number n ? n.floatValue() : 1.0F;
                    usePlayerItem(p, hand, held);
                    heal(nutrition);
                    return true;
                }
                boolean handled = super.interact(p, hand, held, region);
                if (!handled) {
                    setOrderedToSit(!isOrderedToSit());
                    return true;
                }
                return handled;
            }
        } else if (isFood(held)) {
            usePlayerItem(p, hand, held);
            if (random.nextInt(3) == 0 && tameAllowed(p)) {
                tame(p);
                setOrderedToSit(true);
                broadcastEntityEvent((byte) 7);
            } else {
                broadcastEntityEvent((byte) 6);
            }
            return true;
        }
        return super.interact(p, hand, held, region);
    }

    @Override public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:cat_food"); }

    @Override public boolean removeWhenFarAway(double distSqr) { return !isTame() && tickCount > 2400; }

    @Override
    protected void applyTamingSideEffects() {}

    @Override
    public void setTame(boolean tame, boolean side) {
        super.setTame(tame, side);
        reassessTameGoals();
    }

    protected void reassessTameGoals() {
        if (avoidPlayersGoal == null) avoidPlayersGoal = new CatAvoidEntityGoal(this, Player.class, 16.0F, 0.8, 1.33);
        goalSelector.removeGoal(avoidPlayersGoal);
        if (!isTame()) goalSelector.addGoal(4, avoidPlayersGoal);   // 바닐라 cfh.gA: iconst_4 — 유혹(4)과 같아 서로 끊지 못한다
    }

    private static final class CatAvoidEntityGoal extends AvoidEntityGoal {
        private final Cat cat;

        CatAvoidEntityGoal(Cat c, Class<? extends Targetable> cls, float d, double w, double s) {
            super(c, cls, d, w, s);
            this.cat = c;
        }

        @Override public boolean canUse() { return !cat.isTame() && super.canUse(); }

        @Override public boolean canContinueToUse() { return !cat.isTame() && super.canContinueToUse(); }
    }

    private static final class CatTemptGoal extends TemptGoal {
        private final Cat cat;
        private Player selectedPlayer;

        CatTemptGoal(Cat c, double speed, java.util.function.Predicate<ItemStack> items, boolean scare) {
            super(c, speed, items, scare);
            this.cat = c;
        }

        @Override
        public void tick() {
            super.tick();
            if (selectedPlayer == null && cat.random().nextInt(adjustedTickDelay(600)) == 0) selectedPlayer = player;
            else if (cat.random().nextInt(adjustedTickDelay(500)) == 0) selectedPlayer = null;
        }

        @Override protected boolean canScare() { return selectedPlayer != null && selectedPlayer == player ? false : super.canScare(); }

        @Override public boolean canUse() { return super.canUse() && !cat.isTame(); }
    }

    private static final class CatNonTameTarget extends NearestAttackableTargetGoal {
        private final Cat cat;

        CatNonTameTarget(Cat c, Class<?> type) {
            super(c, type, 10, false, false, null);
            this.cat = c;
        }

        @Override public boolean canUse() { return !cat.isTame() && super.canUse(); }
    }

    /** CatLieOnBedGoal: 길든 고양이가 가까운 침대(반경 8, 위아래 6)에 올라가 눕는다. */
    static final class CatLieOnBedGoal extends MoveToBlockGoal {
        private final Cat cat;

        CatLieOnBedGoal(Cat cat, double speed, int range) {
            super(cat, speed, range, 6);
            this.cat = cat;
            this.verticalSearchStart = -2;
            setFlags(java.util.EnumSet.of(Flag.JUMP, Flag.MOVE));
        }

        @Override public boolean canUse() { return cat.isTame() && !cat.isOrderedToSit() && !cat.isLying() && super.canUse(); }

        @Override
        public void start() {
            super.start();
            cat.setInSittingPose(false);
        }

        @Override protected int nextStartTick(org.domi.furnace.server.entity.PathfinderMob mob) { return 40; }

        @Override
        public void stop() {
            super.stop();
            cat.setLying(false);
        }

        @Override
        public void tick() {
            super.tick();
            cat.setInSittingPose(false);
            if (!isReachedTarget()) cat.setLying(false);
            else if (!cat.isLying()) cat.setLying(true);
        }

        @Override
        protected boolean isValidTarget(org.domi.furnace.server.block.BlockLevel level, org.domi.furnace.server.block.Pos pos) {
            return level.isEmptyBlock(pos.above()) && org.domi.furnace.server.block.B.isTag(level.getBlockState(pos), "minecraft:beds");
        }
    }

    /** CatSitOnBlockGoal: 길든 고양이가 닫힌 상자·불 붙은 화로·침대 발 쪽에 앉는다. */
    static final class CatSitOnBlockGoal extends MoveToBlockGoal {
        private final Cat cat;

        CatSitOnBlockGoal(Cat cat, double speed) {
            super(cat, speed, 8);
            this.cat = cat;
        }

        @Override public boolean canUse() { return cat.isTame() && !cat.isOrderedToSit() && super.canUse(); }

        @Override
        public void start() {
            super.start();
            cat.setInSittingPose(false);
        }

        @Override
        public void stop() {
            super.stop();
            cat.setInSittingPose(false);
        }

        @Override
        public void tick() {
            super.tick();
            cat.setInSittingPose(isReachedTarget());
        }

        @Override
        protected boolean isValidTarget(org.domi.furnace.server.block.BlockLevel level, org.domi.furnace.server.block.Pos pos) {
            if (!level.isEmptyBlock(pos.above())) return false;
            int state = level.getBlockState(pos);
            if (org.domi.furnace.server.block.B.is(state, "minecraft:chest")) {
                return !(level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.OpenersBlockEntity o) || o.openCount < 1;
            }
            if (org.domi.furnace.server.block.B.is(state, "minecraft:furnace") && org.domi.furnace.server.block.B.prop("lit").is(state)) return true;
            // 침대는 머리가 아닌 쪽만(part 속성이 없으면 true)
            return org.domi.furnace.server.block.B.isTag(state, "minecraft:beds") && !"head".equals(org.domi.furnace.server.block.B.prop("part").valueName(state));
        }
    }

    /** CatRelaxOnOwnerGoal: 자는 주인의 침대 곁에서 쉬다가, 새벽에 아침 선물을 가져다 놓는다. */
    static final class CatRelaxOnOwnerGoal extends Goal {
        private final Cat cat;
        private Player ownerPlayer;
        private org.domi.furnace.server.block.Pos goalPos;
        private int onBedTicks;

        CatRelaxOnOwnerGoal(Cat cat) { this.cat = cat; }

        @Override
        public boolean canUse() {
            if (!cat.isTame()) return false;
            if (cat.isOrderedToSit()) return false;
            Player owner = cat.getOwner();
            if (owner == null) return false;
            ownerPlayer = owner;
            if (!owner.isSleeping()) return false;
            if (distSqr(owner) > 100.0) return false;
            var pos = owner.blockPosition();
            int state = cat.level().getBlockState(pos);
            if (org.domi.furnace.server.block.B.isTag(state, "minecraft:beds")) {
                var facing = org.domi.furnace.server.block.B.prop("facing");
                goalPos = facing.has(state) ? pos.relative(org.domi.furnace.server.block.Dir.opposite(org.domi.furnace.server.block.Dir.byName(facing.valueName(state)))) : pos;
                return !spaceIsOccupied();
            }
            return false;
        }

        private double distSqr(Player p) {
            double dx = cat.x() - p.x(), dy = cat.y() - p.y(), dz = cat.z() - p.z();
            return dx * dx + dy * dy + dz * dz;
        }

        private boolean spaceIsOccupied() {
            var box = new org.domi.furnace.server.entity.AABB(goalPos.x(), goalPos.y(), goalPos.z(), goalPos.x() + 1, goalPos.y() + 1, goalPos.z() + 1).inflate(2.0);
            for (Cat other : cat.level().getEntitiesOfClass(Cat.class, box, c -> true)) {
                if (other != cat && (other.isLying() || other.isRelaxStateOne())) return true;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return cat.isTame() && !cat.isOrderedToSit() && ownerPlayer != null && ownerPlayer.isSleeping() && goalPos != null && !spaceIsOccupied();
        }

        @Override
        public void start() {
            if (goalPos != null) {
                cat.setInSittingPose(false);
                cat.getNavigation().moveTo(goalPos.x(), goalPos.y(), goalPos.z(), 1.100000023841858);
            }
        }

        @Override
        public void stop() {
            cat.setLying(false);
            float time = org.domi.furnace.server.block.B.timeOfDay(cat.level());
            if (ownerPlayer.sleepCounter() >= 100 && time > 0.77 && time < 0.8 && cat.level().random().nextFloat() < 0.7) giveMorningGift();
            onBedTicks = 0;
            cat.setRelaxStateOne(false);
            cat.getNavigation().stop();
        }

        private void giveMorningGift() {
            var random = cat.random();
            var pos = cat.isPassenger() ? cat.vehicle().blockPosition() : cat.blockPosition();
            cat.randomTeleport(pos.x() + random.nextInt(11) - 5, pos.y() + random.nextInt(5) - 2, pos.z() + random.nextInt(11) - 5, false);
            var at = cat.blockPosition();
            var level = cat.level();
            for (ItemStack s : level.giftLoot("minecraft:gameplay/cat_morning_gift", cat.lootEntity(), cat.x(), cat.y(), cat.z())) {
                float rad = cat.yBodyRot() * (float) (Math.PI / 180.0);
                level.addFreshEntity(new ItemEntity(level, level.nextEntityId(),
                        at.x() - org.domi.furnace.server.entity.ai.control.Mth.sin(rad), at.y(), at.z() + org.domi.furnace.server.entity.ai.control.Mth.cos(rad), s));
            }
        }

        @Override
        public void tick() {
            if (ownerPlayer != null && goalPos != null) {
                cat.setInSittingPose(false);
                cat.getNavigation().moveTo(goalPos.x(), goalPos.y(), goalPos.z(), 1.100000023841858);
                if (distSqr(ownerPlayer) < 2.5) {
                    onBedTicks++;
                    if (onBedTicks > adjustedTickDelay(16)) {
                        cat.setLying(true);
                        cat.setRelaxStateOne(false);
                    } else {
                        cat.faceTarget(ownerPlayer, 45.0F, 45.0F);
                        cat.setRelaxStateOne(true);
                    }
                } else {
                    cat.setLying(false);
                }
            }
        }
    }

    /** Cat.hiss: 하악질 소리(팬텀이 가까이 있을 때). */
    public void hiss() { playSound("entity.cat.hiss", getSoundVolume(), getVoicePitch()); }
}
