// 바닐라 SkeletonHorse: 해골 말. 번개 덫(SkeletonTrap) 상태면 18000틱 뒤 사라진다. 길들여지지 않았으면 상호작용하지 않는다.
// 덫 상태면 SkeletonTrapGoal 이 10 블록 안에 플레이어가 오면 번개(보이기만)와 해골 기수 넷(이 말 + 새 말 셋)을 낸다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class SkeletonHorse extends AbstractHorse {
    private boolean isTrap;
    private int trapTime;

    public SkeletonHorse(ChunkLevel level, int id) { super(EntityType.SKELETON_HORSE, level, id); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 15.0).add(Attribute.MOVEMENT_SPEED, 0.2F);
    }

    @Override
    protected void randomizeAttributes(org.domi.furnace.server.block.RandomSource r) {
        attributes().get(Attribute.JUMP_STRENGTH).setBaseValue(b(r::nextDouble));
    }

    @Override protected void addBehaviourGoals() { }

    @Override public String getAmbientSound() { return isEyeInWater() ? "entity.skeleton_horse.ambient_water" : "entity.skeleton_horse.ambient"; }

    @Override protected String getDeathSound() { return "entity.skeleton_horse.death"; }

    @Override protected String getHurtSound(Damage source) { return "entity.skeleton_horse.hurt"; }

    @Override
    public void tick() {
        super.tick();
        if (isTrap() && trapTime++ >= 18000) discard();
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("SkeletonTrap", isTrap());
        tag.putInt("SkeletonTrapTime", trapTime);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setTrap(tag.getBoolean("SkeletonTrap"));
        trapTime = tag.getIntOr0("SkeletonTrapTime");
    }

    public boolean isTrap() { return isTrap; }

    private final TrapGoal trapGoal = new TrapGoal(this);

    /** setTrap: 바뀔 때 덫 목표를 넣거나(우선순위 1) 뺀다. */
    public void setTrap(boolean v) {
        if (v == isTrap) return;
        isTrap = v;
        if (v) goalSelector.addGoal(1, trapGoal);
        else goalSelector.removeGoal(trapGoal);
    }

    /** 바닐라 SkeletonTrapGoal. */
    static final class TrapGoal extends org.domi.furnace.server.entity.ai.Goal {
        private final SkeletonHorse horse;

        TrapGoal(SkeletonHorse horse) { this.horse = horse; }

        /** level.hasNearbyAlivePlayer(x, y, z, 10). */
        @Override
        public boolean canUse() {
            boolean[] found = {false};
            double x = horse.x(), y = horse.y(), z = horse.z(), d = 10.0;
            horse.level().forEachPlayerIn(new AABB(x - d, y - d, z - d, x + d, y + d, z + d), p -> {
                double dx = p.x() - x, dy = p.y() - y, dz = p.z() - z;
                if (p.vitals().health() > 0.0F && p.gameMode() != org.domi.furnace.server.world.GameMode.SPECTATOR && dx * dx + dy * dy + dz * dz < d * d) found[0] = true;
            });
            return found[0];
        }

        @Override
        public void tick() {
            ChunkLevel level = horse.level();
            DifficultyInstance diff = level.getCurrentDifficultyAt(horse.blockPosition());
            horse.setTrap(false);
            horse.setTamed(true);
            horse.setAge(0);
            LightningBolt bolt = new LightningBolt(level, level.nextEntityId());
            bolt.moveTo(horse.x(), horse.y(), horse.z(), bolt.yRot(), bolt.xRot());
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
            Skeleton rider = createSkeleton(diff, horse);
            rider.startRiding(horse);
            level.addFreshEntityWithPassengers(rider);
            for (int i = 0; i < 3; i++) {
                SkeletonHorse h = createHorse(diff);
                Skeleton s = createSkeleton(diff, h);
                s.startRiding(h);
                h.addDeltaMovement(horse.random().triangle(0.0, 1.1485), 0.0, horse.random().triangle(0.0, 1.1485));
                h.hasImpulse = true;
                level.addFreshEntityWithPassengers(h);
            }
        }

        private SkeletonHorse createHorse(DifficultyInstance diff) {
            ChunkLevel level = horse.level();
            SkeletonHorse h = new SkeletonHorse(level, level.nextEntityId());
            h.finalizeSpawnGroup(level.random(), diff, null);
            h.setPos(horse.x(), horse.y(), horse.z());
            h.invulnerableTime = 60;
            h.persistenceRequired = true;
            h.setTamed(true);
            h.setAge(0);
            return h;
        }

        private Skeleton createSkeleton(DifficultyInstance diff, AbstractHorse mount) {
            ChunkLevel level = mount.level();
            Skeleton s = new Skeleton(level, level.nextEntityId());
            s.finalizeSpawn(level.random(), diff);
            s.setPos(mount.x(), mount.y(), mount.z());
            s.invulnerableTime = 60;
            s.persistenceRequired = true;
            if (s.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) s.setItemSlot(EquipmentSlot.HEAD, stackOf("minecraft:iron_helmet"));
            enchant(s, EquipmentSlot.MAINHAND, diff);
            enchant(s, EquipmentSlot.HEAD, diff);
            return s;
        }

        /** 마법 부여를 비우고 mob_spawn_equipment 제공자로 건다(해골 자기 난수). */
        private static void enchant(Skeleton s, EquipmentSlot slot, DifficultyInstance diff) {
            ItemStack st = org.domi.furnace.server.item.EnchantTools.update(s.getItemBySlot(slot), java.util.Map::clear);
            st = org.domi.furnace.server.item.EnchantTools.enchantFromProvider(st, s.level().lootData(), Mob.MOB_SPAWN_EQUIPMENT, diff.specialMultiplier(), s.random());
            s.updateItemSlot(slot, st);   // 바닐라는 같은 스택을 고쳐 다시 넣는다(같은 것 — 이벤트 없음)
        }
    }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) { return new SkeletonHorse(level, level.nextEntityId()); }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        return !isTamed() ? false : super.interact(p, hand, held, region);
    }
}
