// 바닐라 SnowGolem: 눈덩이를 던지는 골렘(체력 4). 지나간 자리에 눈이 쌓이고, 따뜻한 바이옴·비에서는 녹는다. 가위로 호박 머리를 깎을 수 있다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.world.gen.BiomeTags;

public class SnowGolem extends AbstractGolem implements RangedAttackMob {
    public static final int DATA_PUMPKIN_ID = 16;
    private static final byte PUMPKIN_FLAG = 16;

    public SnowGolem(ChunkLevel level, int id) { super(EntityType.SNOW_GOLEM, level, id); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 4.0).add(Attribute.MOVEMENT_SPEED, 0.2F);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new RangedAttackGoal(this, 1.25, 20, 10.0F));
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0, 1.0000001E-5F));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, 6.0F));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal(this, Mob.class, 10, true, false, t -> t instanceof Enemy));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_PUMPKIN_ID, SetEntityData.SERIALIZER_BYTE, PUMPKIN_FLAG);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Pumpkin", hasPumpkin());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Pumpkin")) setPumpkin(tag.getBoolean("Pumpkin"));
    }

    @Override
    public boolean isSensitiveToWater() { return true; }

    @Override
    protected void aiStep() {
        super.aiStep();
        Pos pos = blockPosition();
        if (BiomeTags.is(level.biomeAt(pos.x(), pos.y(), pos.z()), "minecraft:snow_golem_melts")) hurt(new Damage("on_fire", true, false, false), 1.0F);
        if (!level.gameRule("mobGriefing")) return;
        int snow = B.defaultState(B.id("minecraft:snow"));
        for (int i = 0; i < 4; i++) {
            int bx = (int) Math.floor(x() + (i % 2 * 2 - 1) * 0.25F);
            int by = (int) Math.floor(y());
            int bz = (int) Math.floor(z() + (i / 2 % 2 * 2 - 1) * 0.25F);
            Pos p = new Pos(bx, by, bz);
            if (B.isAir(level.getBlockState(p)) && B.behavior(snow).canSurvive(snow, level, p)) level.setBlock(p, snow, 3);
        }
    }

    @Override
    public void performRangedAttack(Targetable target, float power) {
        Snowball snowball = new Snowball(level, level.nextEntityId());
        snowball.startAt(this);
        double ty = target.eyeY() - 1.1F;
        double dx = target.x() - x();
        double dy = ty - snowball.y();
        double dz = target.z() - z();
        double h = Math.sqrt(dx * dx + dz * dz) * 0.2F;
        snowball.shoot(dx, dy + h, dz, 1.6F, 12.0F);
        playSound("entity.snow_golem.shoot", 1.0F, 0.4F / (random.nextFloat() * 0.4F + 0.8F));
        level.addFreshEntity(snowball);
    }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        if (!held.isEmpty() && ItemTools.itemName(held).equals("minecraft:shears") && readyForShearing()) {
            shear();
            hurtHeldItem(p, hand, held, 1);
            return true;
        }
        return false;
    }

    public void shear() {
        level.playSound(null, x(), y(), z(), "entity.snow_golem.shear", "players", 1.0F, 1.0F);
        setPumpkin(false);
        spawnAtLocation(new ItemStack(ItemContext.id("minecraft:item", "carved_pumpkin"), 1), 1.7F);
    }

    public boolean readyForShearing() { return isAlive() && hasPumpkin(); }

    public boolean hasPumpkin() { return ((Byte) entityData.get(DATA_PUMPKIN_ID) & PUMPKIN_FLAG) != 0; }

    public void setPumpkin(boolean v) {
        byte b = (Byte) entityData.get(DATA_PUMPKIN_ID);
        entityData.set(DATA_PUMPKIN_ID, (byte) (v ? b | PUMPKIN_FLAG : b & ~PUMPKIN_FLAG));
    }

    @Override public String getAmbientSound() { return "entity.snow_golem.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.snow_golem.hurt"; }

    @Override protected String getDeathSound() { return "entity.snow_golem.death"; }
}
