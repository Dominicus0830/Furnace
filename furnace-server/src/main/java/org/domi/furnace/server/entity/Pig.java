// 바닐라 Pig: 당근·감자·사탕무를 좋아하고 안장을 얹고 탈 수 있다. 당근 낚싯대를 들면 조종(가속 ItemBasedSteering). 클라이언트가 움직임을 정하고 서버는 move_vehicle 로 받는다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Pig extends Animal {
    /** 17 안장, 18 가속 시간(Pig.DATA_SADDLE_ID, DATA_BOOST_TIME). */
    public static final int DATA_SADDLE = 17, DATA_BOOST_TIME = 18;

    public Pig(ChunkLevel level, int id) { super(EntityType.PIG, level, id); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 10.0).add(Attribute.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SADDLE, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_BOOST_TIME, SetEntityData.SERIALIZER_INT, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.25));
        goalSelector.addGoal(3, new BreedGoal(this, 1.0));
        goalSelector.addGoal(4, new TemptGoal(this, 1.2, s -> ItemTools.itemName(s).equals("minecraft:carrot_on_a_stick"), false));
        goalSelector.addGoal(4, new TemptGoal(this, 1.2, s -> ItemTags.is(s, "minecraft:pig_food"), false));
        goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, 6.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:pig_food"); }

    @Override
    public void thunderHit(org.domi.furnace.server.block.ChunkLevel level, LightningBolt bolt) {
        if (level.difficulty() != 0) {
            ZombifiedPiglin piglin = new ZombifiedPiglin(level, level.nextEntityId());
            piglin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "minecraft:golden_sword"), 1));
            piglin.persistenceRequired = true;
            convertTo(piglin, false);
        } else {
            super.thunderHit(level, bolt);
        }
    }

    public boolean isSaddled() { return (Boolean) entityData.get(DATA_SADDLE); }

    public void setSaddled(boolean v) { entityData.set(DATA_SADDLE, v); }

    // ItemBasedSteering(bti): 가속 중 여부와 경과 틱. 총 시간은 DATA_BOOST_TIME 에 동기화(클라이언트가 속도 계수에 쓴다).
    private boolean boosting;
    private int boostTime;

    /** ItemBasedSteering.boost(random): 가속 중이면 거짓, 아니면 시작하고 총 시간 nextInt(841)+140 틱을 동기화한다. */
    public boolean boost() {
        if (boosting) return false;
        boosting = true;
        boostTime = 0;
        entityData.set(DATA_BOOST_TIME, random.nextInt(841) + 140);
        return true;
    }

    /** ItemBasedSteering.tickBoost: 경과가 총 시간을 넘으면(비교 뒤 증가) 가속 끝. */
    private void tickBoost() {
        if (boosting && boostTime++ > (Integer) entityData.get(DATA_BOOST_TIME)) boosting = false;
    }

    /** Pig.getControllingPassenger: 안장이 있고 첫 승객이 당근 낚싯대를 들고 있는 플레이어면 그 플레이어, 아니면 Mob 기본. */
    @Override
    public Rider getControllingPassenger() {
        if (isSaddled() && firstPassenger() instanceof Player p && isHoldingCarrotStick(p)) return p;
        return super.getControllingPassenger();
    }

    private static boolean isHoldingCarrotStick(Player p) {
        var inv = p.inventory();
        return ItemTools.itemName(inv.get(inv.heldWindowSlot())).equals("minecraft:carrot_on_a_stick")
                || ItemTools.itemName(inv.get(45)).equals("minecraft:carrot_on_a_stick");
    }

    /** Pig.tickRidden: 탄 플레이어의 방향(pitch 는 절반)을 따르고 가속 시간을 진행한다. */
    @Override
    protected void tickRidden(Player p) {
        super.tickRidden(p);
        setYRot(p.yaw());
        setXRot(p.pitch() * 0.5F);
        yHeadRot = yBodyRot = yRot();
        yRotO = yRot();
        tickBoost();
    }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        // Pig.mobInteract(cfv:85): 먹이가 아니고 안장이 있고 승객이 없고 웅크리지 않았으면 탄다
        if (!isFood(held) && isSaddled() && !isVehicle() && !p.sneaking()) {
            p.startRiding(this, false);
            return true;
        }
        if (super.interact(p, hand, held, region)) return true;
        if (!held.isEmpty() && ItemTools.itemName(held).equals("minecraft:saddle") && isAlive() && !isBaby() && !isSaddled()) {
            level.playSound(null, x(), y(), z(), "entity.pig.saddle", "neutral", 0.5F, 1.0F);
            setSaddled(true);
            if (!p.vitals().isCreative()) {
                var inv = p.inventory();
                inv.set(hand == 0 ? inv.heldWindowSlot() : 45, held.withCount(held.count() - 1));
            }
            return true;
        }
        return false;
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        if (isSaddled()) spawnAtLocation(new ItemStack(ItemContext.id("minecraft:item", "saddle"), 1));
    }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) { return new Pig(level, level.nextEntityId()); }

    @Override public String getAmbientSound() { return "entity.pig.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.pig.hurt"; }

    @Override protected String getDeathSound() { return "entity.pig.death"; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Saddle", isSaddled());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setSaddled(tag.getBoolean("Saddle"));
    }
}
