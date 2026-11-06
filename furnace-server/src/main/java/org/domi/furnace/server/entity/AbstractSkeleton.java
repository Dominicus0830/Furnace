// 바닐라 AbstractSkeleton: 활(또는 근접)을 든 해골류. 낮에 햇빛에 타고, 활을 들면 거리를 두며 쏘고 아니면 달려가 때린다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.item.ItemContext;

public abstract class AbstractSkeleton extends Monster implements RangedAttackMob {
    private final RangedBowAttackGoal<AbstractSkeleton> bowGoal = new RangedBowAttackGoal<>(this, 1.0, 20, 15.0F);
    private final MeleeAttackGoal meleeGoal = new MeleeAttackGoal(this, 1.2, false) {
        @Override
        public void stop() {
            super.stop();
            AbstractSkeleton.this.setAggressive(false);
        }

        @Override
        public void start() {
            super.start();
            AbstractSkeleton.this.setAggressive(true);
        }
    };

    protected AbstractSkeleton(EntityType type, ChunkLevel level, int id) {
        super(type, level, id);
        reassessWeaponGoal();
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new RestrictSunGoal(this));
        goalSelector.addGoal(3, new FleeSunGoal(this, 1.0));
        goalSelector.addGoal(3, new AvoidEntityGoal(this, Wolf.class, 6.0F, 1.0, 1.2));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, 8.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, IronGolem.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Turtle.class, 10, true, false, Turtle.BABY_ON_LAND_SELECTOR));
    }

    abstract String getStepSound();

    @Override
    protected void aiStep() {
        boolean burn = isSunBurnTick();
        if (burn) {
            ItemStack helmet = getItemBySlot(EquipmentSlot.HEAD);
            if (!helmet.isEmpty()) {
                if (org.domi.furnace.server.item.ItemTools.isDamageable(helmet)) {
                    helmet = org.domi.furnace.server.item.ItemTools.withDamage(helmet, org.domi.furnace.server.item.ItemTools.damage(helmet) + random.nextInt(2));
                    if (org.domi.furnace.server.item.ItemTools.damage(helmet) >= org.domi.furnace.server.item.ItemTools.maxDamage(helmet)) {
                        broadcastEntityEvent((byte) 52);
                        setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    } else {
                        updateItemSlot(EquipmentSlot.HEAD, helmet);   // setDamageValue: 제자리
                    }
                }
                burn = false;
            }
            if (burn) igniteForSeconds(8.0F);
        }
        super.aiStep();
    }

    @Override
    protected void populateDefaultEquipmentSlots(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        super.populateDefaultEquipmentSlots(r, difficulty);
        setItemSlot(EquipmentSlot.MAINHAND, stackOf("minecraft:bow"));
    }

    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        super.finalizeSpawn(r, difficulty);
        populateDefaultEquipmentSlots(r, difficulty);
        populateDefaultEquipmentEnchantments(r, difficulty);
        reassessWeaponGoal();
        setCanPickUpLoot(r.nextFloat() < 0.55F * difficulty.specialMultiplier());
        if (getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            java.time.LocalDate today = java.time.LocalDate.now();
            if (today.getMonthValue() == 10 && today.getDayOfMonth() == 31 && r.nextFloat() < 0.25F) {
                setItemSlot(EquipmentSlot.HEAD, stackOf(r.nextFloat() < 0.1F ? "minecraft:jack_o_lantern" : "minecraft:carved_pumpkin"));
                setDropChance(EquipmentSlot.HEAD, 0.0F);
            }
        }
    }

    public void reassessWeaponGoal() {
        if (level != null && bowGoal != null) {
            goalSelector.removeGoal(meleeGoal);
            goalSelector.removeGoal(bowGoal);
            if (isHolding("minecraft:bow")) {
                int interval = getHardAttackInterval();
                if (level.difficulty() != 3) interval = getAttackInterval();
                bowGoal.setMinAttackInterval(interval);
                goalSelector.addGoal(4, bowGoal);
            } else {
                goalSelector.addGoal(4, meleeGoal);
            }
        }
    }

    protected int getHardAttackInterval() { return 20; }

    protected int getAttackInterval() { return 40; }

    @Override
    public void performRangedAttack(Targetable target, float power) {
        int hand = getWeaponHoldingHand("minecraft:bow");
        Arrow arrow = new Arrow(level, level.nextEntityId(), x(), eyeY() - 0.1F, z());
        arrow.setOwner(this);
        arrow.setEnchantmentEffectsFromEntity(power);
        prepareArrow(arrow);
        double dx = target.x() - x();
        double dy = target.y() + target.bbHeight() * (1.0 / 3.0) - arrow.y();
        double dz = target.z() - z();
        double h = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + h * 0.2F, dz, 1.6F, 14 - level.difficulty() * 4);
        playSound("entity.skeleton.shoot", 1.0F, 1.0F / (random.nextFloat() * 0.4F + 0.8F));
        level.addFreshEntity(arrow);
    }

    /** 종류별로 화살에 효과를 더한다(방랑자 느림, 늪 해골 독, 위더 해골 불). */
    protected void prepareArrow(Arrow arrow) {}

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        super.setItemSlot(slot, stack);
        reassessWeaponGoal();
    }

    @Override
    protected void readAdditionalSaveData(org.domi.furnace.server.net.io.nbt.NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        reassessWeaponGoal();
    }
}
