// 바닐라 Ocelot: 생선(ocelot_food)으로 신뢰를 얻으면(1/3) 플레이어를 피하지 않는다. 가까이 있으면 웅크리고 달린다.
// TODO: 거북 새끼 사냥, 자연 스폰 규칙.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Ocelot extends Animal {
    public static final int DATA_TRUSTING = 17;
    private AvoidEntityGoal ocelotAvoidPlayersGoal;
    private TemptGoal temptGoal;

    public Ocelot(ChunkLevel level, int id) {
        super(EntityType.OCELOT, level, id);
        reassessTrustingGoals();
    }

    boolean isTrusting() { return (Boolean) entityData.get(DATA_TRUSTING); }

    private void setTrusting(boolean v) {
        entityData.set(DATA_TRUSTING, v);
        reassessTrustingGoals();
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Trusting", isTrusting());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setTrusting(tag.getBoolean("Trusting"));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_TRUSTING, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    @Override
    protected void registerGoals() {
        temptGoal = new OcelotTemptGoal(this, 0.6, s -> ItemTags.is(s, "minecraft:ocelot_food"), true);
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(3, temptGoal);
        goalSelector.addGoal(7, new LeapAtTargetGoal(this, 0.3F));
        goalSelector.addGoal(8, new OcelotAttackGoal(this));
        goalSelector.addGoal(9, new BreedGoal(this, 0.8));
        goalSelector.addGoal(10, new WaterAvoidingRandomStrollGoal(this, 0.8, 1.0000001E-5F));
        goalSelector.addGoal(11, new LookAtPlayerGoal(this, 10.0F));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal(this, Chicken.class, false));
        // TODO(거북): targetSelector 1 = 땅 위 아기 거북(10칸)
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
    public boolean removeWhenFarAway(double distSqr) { return !isTrusting() && tickCount > 2400; }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 10.0).add(Attribute.MOVEMENT_SPEED, 0.3F).add(Attribute.ATTACK_DAMAGE, 3.0);
    }

    @Override public String getAmbientSound() { return random.nextInt(4) == 0 ? "entity.ocelot.purreow" : "entity.ocelot.ambient"; }

    @Override public int getAmbientSoundInterval() { return 900; }

    @Override protected String getHurtSound(Damage source) { return "entity.ocelot.hurt"; }

    @Override protected String getDeathSound() { return "entity.ocelot.death"; }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        if ((temptGoal == null || temptGoal.isRunning()) && !isTrusting() && isFood(held)
                && distSqr(p) < 9.0) {
            usePlayerItem(p, hand, held);
            if (random.nextInt(3) == 0) {
                setTrusting(true);
                spawnTrustingParticles(true);
                broadcastEntityEvent((byte) 41);
            } else {
                spawnTrustingParticles(false);
                broadcastEntityEvent((byte) 40);
            }
            return true;
        }
        return super.interact(p, hand, held, region);
    }

    private double distSqr(Player p) {
        double dx = x() - p.x(), dy = y() - p.y(), dz = z() - p.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private void spawnTrustingParticles(boolean success) {}

    protected void reassessTrustingGoals() {
        if (ocelotAvoidPlayersGoal == null) ocelotAvoidPlayersGoal = new OcelotAvoidEntityGoal(this, Player.class, 16.0F, 0.8, 1.33);
        goalSelector.removeGoal(ocelotAvoidPlayersGoal);
        if (!isTrusting()) goalSelector.addGoal(4, ocelotAvoidPlayersGoal);
    }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) { return new Ocelot(level, level.nextEntityId()); }

    @Override public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:ocelot_food"); }

    @Override
    public GroupData finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty, GroupData data) {
        if (data == null) data = new GroupData(1.0F);
        return super.finalizeSpawn(r, difficulty, data);
    }

    private static final class OcelotAvoidEntityGoal extends AvoidEntityGoal {
        private final Ocelot ocelot;

        OcelotAvoidEntityGoal(Ocelot o, Class<? extends Targetable> cls, float d, double w, double s) {
            super(o, cls, d, w, s);
            this.ocelot = o;
        }

        @Override public boolean canUse() { return !ocelot.isTrusting() && super.canUse(); }

        @Override public boolean canContinueToUse() { return !ocelot.isTrusting() && super.canContinueToUse(); }
    }

    private static final class OcelotTemptGoal extends TemptGoal {
        private final Ocelot ocelot;

        OcelotTemptGoal(Ocelot o, double speed, java.util.function.Predicate<ItemStack> items, boolean scare) {
            super(o, speed, items, scare);
            this.ocelot = o;
        }

        @Override protected boolean canScare() { return super.canScare() && !ocelot.isTrusting(); }
    }
}
