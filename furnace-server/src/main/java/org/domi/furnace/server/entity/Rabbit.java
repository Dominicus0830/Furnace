// 바닐라 Rabbit: 깡충깡충 뛰는 이동·점프 제어, 변종(털 색·킬러 토끼), 당근 밭 털기, 늑대·플레이어·몬스터에게서 도망.
// TODO: 킬러 토끼의 이름 표시(사용자 지정 이름 기능 필요), 점프 입자·소리 분류(킬러 토끼 hostile).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.attribute.AttributeModifier;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.control.JumpControl;
import org.domi.furnace.server.entity.ai.control.MoveControl;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Rabbit extends Animal {
    public static final int DATA_TYPE = 17;
    public static final int TYPE_BROWN = 0, TYPE_WHITE = 1, TYPE_BLACK = 2, TYPE_WHITE_SPLOTCHED = 3, TYPE_GOLD = 4, TYPE_SALT = 5, TYPE_EVIL = 99;
    private static final String EVIL_ATTACK_MODIFIER = "minecraft:evil";

    private int jumpTicks, jumpDuration;
    private boolean wasOnGround;
    private int jumpDelayTicks;
    int moreCarrotTicks;

    public Rabbit(ChunkLevel level, int id) {
        super(EntityType.RABBIT, level, id);
        this.jumpControl = new RabbitJumpControl(this);
        this.moveControl = new RabbitMoveControl(this);
        setSpeedModifier(0.0);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 3.0).add(Attribute.MOVEMENT_SPEED, 0.3F).add(Attribute.ATTACK_DAMAGE, 3.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_TYPE, SetEntityData.SERIALIZER_INT, TYPE_BROWN);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(1, new ClimbOnTopOfPowderSnowGoalStub());
        goalSelector.addGoal(1, new RabbitPanicGoal(this, 2.2));
        goalSelector.addGoal(2, new BreedGoal(this, 0.8));
        goalSelector.addGoal(3, new TemptGoal(this, 1.0, s -> ItemTags.is(s, "minecraft:rabbit_food"), false));
        goalSelector.addGoal(4, new RabbitAvoidEntityGoal(this, Player.class, 8.0F, 2.2, 2.2));
        goalSelector.addGoal(4, new RabbitAvoidEntityGoal(this, Wolf.class, 10.0F, 2.2, 2.2));
        goalSelector.addGoal(4, new RabbitAvoidEntityGoal(this, Monster.class, 4.0F, 2.2, 2.2));
        goalSelector.addGoal(5, new RaidGardenGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(11, new LookAtPlayerGoal(this, 10.0F));
    }

    /** ClimbOnTopOfPowderSnowGoal(cab): 가루눈 위로 올라서는 목표. TODO: 가루눈 지원 때 실제 구현으로 바꾼다. */
    private static final class ClimbOnTopOfPowderSnowGoalStub extends org.domi.furnace.server.entity.ai.Goal {
        @Override public boolean canUse() { return false; }
    }

    @Override
    protected float getJumpPower() {
        float f = 0.3F;
        if (horizontalCollision || moveControl.hasWanted() && moveControl.getWantedY() > y() + 0.5) f = 0.5F;
        var path = navigation.getPath();
        if (path != null && !path.isDone()) {
            double[] p = path.getNextEntityPos(this);
            if (p[1] > y() + 0.5) f = 0.5F;
        }
        if (moveControl.getSpeedModifier() <= 0.6) f = 0.2F;
        return super.getJumpPower(f / 0.42F);
    }

    @Override
    public void jumpFromGround() {
        super.jumpFromGround();
        double speed = moveControl.getSpeedModifier();
        if (speed > 0.0) {
            double h = vx() * vx() + vz() * vz();
            if (h < 0.01) moveRelative(0.1F, 0.0F, 0.0F, 1.0F);
        }
        broadcastEntityEvent((byte) 1);
    }

    public void setSpeedModifier(double speed) {
        navigation.setSpeedModifier(speed);
        moveControl.setWantedPosition(moveControl.getWantedX(), moveControl.getWantedY(), moveControl.getWantedZ(), speed);
    }

    @Override
    public void setJumping(boolean j) {
        super.setJumping(j);
        if (j) playSound(getJumpSound(), 1.0F, ((random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F) * 0.8F);
    }

    public void startJumping() {
        setJumping(true);
        jumpDuration = 10;
        jumpTicks = 0;
    }

    @Override
    protected void customServerAiStep() {
        if (jumpDelayTicks > 0) jumpDelayTicks--;
        if (moreCarrotTicks > 0) {
            moreCarrotTicks -= random.nextInt(3);
            if (moreCarrotTicks < 0) moreCarrotTicks = 0;
        }
        if (onGround()) {
            if (!wasOnGround) {
                setJumping(false);
                checkLandingDelay();
            }
            if (getVariant() == TYPE_EVIL && jumpDelayTicks == 0) {
                Targetable t = getTarget();
                if (t != null && distSqr(t) < 16.0) {
                    facePoint(t.x(), t.z());
                    moveControl.setWantedPosition(t.x(), t.y(), t.z(), moveControl.getSpeedModifier());
                    startJumping();
                    wasOnGround = true;
                }
            }
            RabbitJumpControl jc = (RabbitJumpControl) jumpControl;
            if (!jc.wantJump()) {
                if (moveControl.hasWanted() && jumpDelayTicks == 0) {
                    var path = navigation.getPath();
                    double wx = moveControl.getWantedX(), wz = moveControl.getWantedZ();
                    if (path != null && !path.isDone()) {
                        double[] p = path.getNextEntityPos(this);
                        wx = p[0];
                        wz = p[2];
                    }
                    facePoint(wx, wz);
                    startJumping();
                }
            } else if (!jc.canJump()) {
                enableJumpControl();
            }
        }
        wasOnGround = onGround();
    }

    private double distSqr(Targetable t) {
        double dx = x() - t.x(), dy = y() - t.y(), dz = z() - t.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private void facePoint(double px, double pz) {
        setYRot((float) (org.domi.furnace.server.entity.ai.control.Mth.atan2(pz - z(), px - x()) * 180.0F / (float) Math.PI) - 90.0F);
    }

    private void enableJumpControl() { ((RabbitJumpControl) jumpControl).setCanJump(true); }

    private void disableJumpControl() { ((RabbitJumpControl) jumpControl).setCanJump(false); }

    private void setLandingDelay() {
        if (moveControl.getSpeedModifier() < 2.2) jumpDelayTicks = 10;
        else jumpDelayTicks = 1;
    }

    private void checkLandingDelay() {
        setLandingDelay();
        disableJumpControl();
    }

    @Override
    public void tick() {
        super.tick();
        if (jumpTicks != jumpDuration) {
            jumpTicks++;
        } else if (jumpDuration != 0) {
            jumpTicks = 0;
            jumpDuration = 0;
            setJumping(false);
        }
    }

    @Override
    public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:rabbit_food"); }

    public int getVariant() { return (Integer) entityData.get(DATA_TYPE); }

    public void setVariant(int v) {
        if (v == TYPE_EVIL) {
            attributes().get(Attribute.ARMOR).setBaseValue(8.0);
            goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.4, true));
            targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
            targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true));
            targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Wolf.class, true));
            attributes().get(Attribute.ATTACK_DAMAGE).addOrReplaceModifier(new AttributeModifier(EVIL_ATTACK_MODIFIER, 5.0, AttributeModifier.ADD_VALUE));
        } else {
            attributes().get(Attribute.ATTACK_DAMAGE).removeModifier(EVIL_ATTACK_MODIFIER);
        }
        entityData.set(DATA_TYPE, v);
    }

    /**
     * Rabbit.getRandomRabbitVariant: 난수(nextInt(100))를 먼저 뽑고 바이옴 태그로 가른다.
     * 바이옴을 못 읽는 경우(주인 region 이 아닌 이웃 청크로 확대 지터가 넘어간 경계 등)는 어느 태그도 아닌 것으로 취급한다(가정).
     */
    static int variantForSpawn(ChunkLevel level, Pos pos) {
        int r = level.random().nextInt(100);
        if (level.biomeIs(pos, "minecraft:spawns_white_rabbits")) return r < 80 ? TYPE_WHITE : TYPE_WHITE_SPLOTCHED;
        if (level.biomeIs(pos, "minecraft:spawns_gold_rabbits")) return TYPE_GOLD;
        return r < 50 ? TYPE_BROWN : (r < 90 ? TYPE_SALT : TYPE_BLACK);
    }

    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        setVariant(variantForSpawn(level, blockPosition()));
        super.finalizeSpawn(r, difficulty);
    }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) {
        Rabbit child = new Rabbit(level, level.nextEntityId());
        int v = variantForSpawn(level, blockPosition());
        if (random.nextInt(20) != 0) {
            if (partner instanceof Rabbit p && random.nextBoolean()) v = p.getVariant();
            else v = getVariant();
        }
        child.setVariant(v);
        return child;
    }

    protected String getJumpSound() { return "entity.rabbit.jump"; }

    @Override public String getAmbientSound() { return "entity.rabbit.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.rabbit.hurt"; }

    @Override protected String getDeathSound() { return "entity.rabbit.death"; }

    @Override public String soundSource() { return getVariant() == TYPE_EVIL ? "hostile" : "neutral"; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("RabbitType", getVariant());
        tag.putInt("MoreCarrotTicks", moreCarrotTicks);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        int t = tag.getInt("RabbitType");
        setVariant(t >= 0 && t <= 5 || t == TYPE_EVIL ? t : TYPE_BROWN);
        moreCarrotTicks = tag.getInt("MoreCarrotTicks");
    }

    boolean wantsMoreFood() { return moreCarrotTicks <= 0; }

    // ------------------------------------------------------------------ 제어

    static final class RabbitJumpControl extends JumpControl {
        private final Rabbit rabbit;
        private boolean canJump;

        RabbitJumpControl(Rabbit rabbit) {
            super(rabbit);
            this.rabbit = rabbit;
        }

        boolean wantJump() { return jump; }

        boolean canJump() { return canJump; }

        void setCanJump(boolean v) { canJump = v; }

        @Override
        public void tick() {
            if (jump) {
                rabbit.startJumping();
                jump = false;
            }
        }
    }

    static final class RabbitMoveControl extends MoveControl {
        private final Rabbit rabbit;
        private double nextJumpSpeed;

        RabbitMoveControl(Rabbit rabbit) {
            super(rabbit);
            this.rabbit = rabbit;
        }

        @Override
        public void tick() {
            if (rabbit.onGround() && !rabbit.isJumping() && !((RabbitJumpControl) rabbit.jumpControl).wantJump()) {
                rabbit.setSpeedModifier(0.0);
            } else if (hasWanted()) {
                rabbit.setSpeedModifier(nextJumpSpeed);
            }
            super.tick();
        }

        @Override
        public void setWantedPosition(double x, double y, double z, double speed) {
            if (rabbit.isInWater()) speed = 1.5;
            super.setWantedPosition(x, y, z, speed);
            if (speed > 0.0) nextJumpSpeed = speed;
        }
    }

    // ------------------------------------------------------------------ 목표

    static final class RabbitPanicGoal extends PanicGoal {
        private final Rabbit rabbit;

        RabbitPanicGoal(Rabbit rabbit, double speed) {
            super(rabbit, speed);
            this.rabbit = rabbit;
        }

        @Override
        public void tick() {
            super.tick();
            rabbit.setSpeedModifier(speedModifier);
        }
    }

    static final class RabbitAvoidEntityGoal extends AvoidEntityGoal {
        private final Rabbit rabbit;

        RabbitAvoidEntityGoal(Rabbit rabbit, Class<? extends Targetable> cls, float dist, double walk, double sprint) {
            super(rabbit, cls, dist, walk, sprint);
            this.rabbit = rabbit;
        }

        @Override
        public boolean canUse() { return rabbit.getVariant() != TYPE_EVIL && super.canUse(); }
    }

    static final class RaidGardenGoal extends MoveToBlockGoal {
        private final Rabbit rabbit;
        private boolean wantsToRaid;
        private boolean canRaid;

        RaidGardenGoal(Rabbit rabbit) {
            super(rabbit, 0.7F, 16);
            this.rabbit = rabbit;
        }

        @Override
        public boolean canUse() {
            if (nextStartTick <= 0) {
                if (!rabbit.level().gameRule("mobGriefing")) return false;
                canRaid = false;
                wantsToRaid = rabbit.wantsMoreFood();
            }
            return super.canUse();
        }

        @Override
        public boolean canContinueToUse() { return canRaid && super.canContinueToUse(); }

        @Override
        public void tick() {
            super.tick();
            rabbit.getLookControl().setLookAt(blockPos.x() + 0.5, blockPos.y() + 1, blockPos.z() + 0.5, 10.0F, rabbit.getMaxHeadXRot());
            if (isReachedTarget()) {
                var level = rabbit.level();
                Pos above = blockPos.above();
                int state = level.getBlockState(above);
                if (canRaid && B.is(state, "minecraft:carrots")) {
                    int age = B.prop("age").intValue(state);
                    if (age == 0) {
                        level.setBlock(above, org.domi.furnace.server.block.Blocks.get().defaultState("minecraft:air"), 2, 512);
                        level.destroyBlock(above, true, null, 512);
                    } else {
                        level.setBlock(above, B.prop("age").withInt(state, age - 1), 2, 512);
                        level.levelEvent(null, 2001, above, state);
                    }
                    rabbit.moreCarrotTicks = 40;
                }
                canRaid = false;
                nextStartTick = 10;
            }
        }

        @Override
        protected boolean isValidTarget(BlockLevel level, Pos pos) {
            int state = level.getBlockState(pos);
            if (B.is(state, "minecraft:farmland") && wantsToRaid && !canRaid) {
                int above = level.getBlockState(pos.above());
                if (B.is(above, "minecraft:carrots") && B.prop("age").intValue(above) == B.prop("age").size(above) - 1) {
                    canRaid = true;
                    return true;
                }
            }
            return false;
        }
    }
}
