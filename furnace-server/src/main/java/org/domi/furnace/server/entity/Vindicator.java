// 바닐라 Vindicator: 도끼를 든 일러저. 플레이어·주민·철 골렘을 노린다. 'Johnny' 이름이면 모든 생명체를 공격한다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class Vindicator extends AbstractIllager {
    private boolean isJohnny;

    public Vindicator(ChunkLevel level, int id) { super(EntityType.VINDICATOR, level, id); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 0.35F).add(Attribute.FOLLOW_RANGE, 12.0).add(Attribute.MAX_HEALTH, 24.0).add(Attribute.ATTACK_DAMAGE, 5.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new VindicatorBreakDoorGoal(this));
        goalSelector.addGoal(2, new RaiderOpenDoorGoal(this));
        goalSelector.addGoal(3, new HoldGroundAttackGoal(this, 10.0F));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Villager.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, IronGolem.class, true));
        targetSelector.addGoal(4, new VindicatorJohnnyAttackGoal(this));
        goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6));
        goalSelector.addGoal(9, new InteractGoal(this, 3.0F, 1.0F));
        goalSelector.addGoal(10, new LookAtMobGoal(this, Mob.class, 8.0F, 0.02F));
    }

    /** Vindicator.customServerAiStep: 습격 중인 자리(isRaided)에서만 문을 열 수 있다. */
    @Override
    protected void customServerAiStep() {
        if (!isNoAi() && getNavigation() instanceof org.domi.furnace.server.entity.ai.navigation.GroundPathNavigation g) {
            g.setCanOpenDoors(level().raids().getRaidAt(blockPosition()) != null);
        }
        super.customServerAiStep();
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        if (isJohnny) tag.putBoolean("Johnny", true);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.tags().get("Johnny") != null) isJohnny = tag.getBoolean("Johnny");
    }

    @Override
    public IllagerArmPose getArmPose() {
        if (isAggressive()) return IllagerArmPose.ATTACKING;
        return isCelebrating() ? IllagerArmPose.CELEBRATING : IllagerArmPose.CROSSED;
    }

    @Override
    protected void populateDefaultEquipmentSlots(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        if (getCurrentRaid() == null) setItemSlot(EquipmentSlot.MAINHAND, stackOf("minecraft:iron_axe"));   // 바닐라 cks.a: getCurrentRaid()==null
    }

    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        super.finalizeSpawn(r, difficulty);
        ((org.domi.furnace.server.entity.ai.navigation.GroundPathNavigation) getNavigation()).setCanOpenDoors(true);
        populateDefaultEquipmentSlots(r, difficulty);
        populateDefaultEquipmentEnchantments(r, difficulty);
    }

    @Override
    public void applyRaidBuffs(int wave, boolean captain) {
        ItemStack axe = stackOf("minecraft:iron_axe");
        var raid = getCurrentRaid();
        if (raid == null) return;
        if (random.nextFloat() <= raid.getEnchantOdds()) {
            // 파도가 보통 난이도의 습격 수(5)를 넘으면 post_wave_5 제공자
            String provider = wave > org.domi.furnace.server.world.raid.Raid.getNumGroups(2) ? "minecraft:raid/vindicator_post_wave_5" : "minecraft:raid/vindicator";
            var difficulty = level.getCurrentDifficultyAt(blockPosition());
            axe = org.domi.furnace.server.item.EnchantTools.enchantFromProvider(axe, level.lootData(), provider, difficulty.specialMultiplier(), random);
        }
        setItemSlot(EquipmentSlot.MAINHAND, axe);
    }

    /** Vindicator.setCustomName: 이름이 정확히 "Johnny" 가 되면 영구히 모든 생명체를 노린다. */
    @Override
    public void setCustomName(org.domi.furnace.api.text.Text name) {
        super.setCustomName(name);
        if (!isJohnny && name != null && name.getString().equals("Johnny")) isJohnny = true;
    }

    boolean isJohnny() { return isJohnny; }

    /** Vindicator.VindicatorJohnnyAttackGoal: 'Johnny' 이면 간격 없이 보이고 닿는 모든 생명체(갑옷 거치대 제외)를 노린다. */
    static final class VindicatorJohnnyAttackGoal extends NearestAttackableTargetGoal {
        private final Vindicator vindicator;

        VindicatorJohnnyAttackGoal(Vindicator mob) {
            super(mob, LivingEntity.class, 0, true, true, t -> !(t instanceof ArmorStand));
            this.vindicator = mob;
        }

        @Override public boolean canUse() { return vindicator.isJohnny && super.canUse(); }

        @Override
        public void start() {
            super.start();
            vindicator.setNoActionTime(0);
        }
    }

    @Override public String getAmbientSound() { return "entity.vindicator.ambient"; }

    @Override protected String getDeathSound() { return "entity.vindicator.death"; }

    @Override protected String getHurtSound(Damage source) { return "entity.vindicator.hurt"; }

    @Override public String getCelebrateSound() { return "entity.vindicator.celebrate"; }

    /** VindicatorBreakDoorGoal: 습격 중에만, 보통 이상 난이도에서 문을 부순다. */
    static final class VindicatorBreakDoorGoal extends BreakDoorGoal {
        private final Vindicator vindicator;

        VindicatorBreakDoorGoal(Vindicator mob) {
            super(mob, 6, difficulty -> difficulty == 2 || difficulty == 3);
            this.vindicator = mob;
            setFlags(java.util.EnumSet.of(Flag.MOVE));
        }

        @Override public boolean canContinueToUse() { return vindicator.hasActiveRaid() && super.canContinueToUse(); }

        @Override
        public boolean canUse() {
            return vindicator.hasActiveRaid() && vindicator.random().nextInt(adjustedTickDelay(10)) == 0 && super.canUse();
        }

        @Override
        public void start() {
            super.start();
            vindicator.setNoActionTime(0);
        }
    }
}
