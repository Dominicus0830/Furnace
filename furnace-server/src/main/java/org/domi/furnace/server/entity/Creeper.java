// 바닐라 Creeper: 대상이 가까우면 멈춰 부풀어(도화선 30틱) 폭발한다. 부싯돌·화염구로 점화, 떨어지면 도화선이 빨리 탄다.
// TODO: 번개를 맞아 충전(powered)되는 것(번개 개체 필요), 폭발 뒤 남는 효과 구름, 다른 크리퍼 폭발로 머리 떨구기.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Creeper extends Monster {
    public static final int DATA_SWELL_DIR = 16, DATA_IS_POWERED = 17, DATA_IS_IGNITED = 18;
    private int oldSwell, swell;
    private int maxSwell = 30;
    private int explosionRadius = 3;

    public Creeper(ChunkLevel level, int id) { super(EntityType.CREEPER, level, id); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SWELL_DIR, SetEntityData.SERIALIZER_INT, -1);
        entityData.define(DATA_IS_POWERED, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_IS_IGNITED, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new SwellGoal(this));
        goalSelector.addGoal(3, new AvoidEntityGoal(this, Ocelot.class, 6.0F, 1.0, 1.2));
        goalSelector.addGoal(3, new AvoidEntityGoal(this, Cat.class, 6.0F, 1.0, 1.2));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, 8.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal(this, Player.class, true));
        targetSelector.addGoal(2, new HurtByTargetGoal(this));
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier) {
        boolean r = super.causeFallDamage(distance, multiplier);
        swell += (int) (distance * 1.5F);
        if (swell > maxSwell - 5) swell = maxSwell - 5;
        return r;
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        if ((Boolean) entityData.get(DATA_IS_POWERED)) tag.putBoolean("powered", true);
        tag.putShort("Fuse", (short) maxSwell);
        tag.putByte("ExplosionRadius", (byte) explosionRadius);
        tag.putBoolean("ignited", isIgnited());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_IS_POWERED, tag.getBoolean("powered"));
        if (tag.contains("Fuse")) maxSwell = tag.getIntOr0("Fuse");
        if (tag.contains("ExplosionRadius")) explosionRadius = tag.getIntOr0("ExplosionRadius");
        if (tag.getBoolean("ignited")) ignite();
    }

    @Override
    public void tick() {
        if (isAlive()) {
            oldSwell = swell;
            if (isIgnited()) setSwellDir(1);
            int i = getSwellDir();
            if (i > 0 && swell == 0) playSound("entity.creeper.primed", 1.0F, 0.5F);
            swell += i;
            if (swell < 0) swell = 0;
            if (swell >= maxSwell) {
                swell = maxSwell;
                explodeCreeper();
            }
        }
        super.tick();
    }

    @Override
    public void thunderHit(org.domi.furnace.server.block.ChunkLevel level, LightningBolt bolt) {
        super.thunderHit(level, bolt);
        entityData.set(DATA_IS_POWERED, true);
    }

    public boolean isPowered() { return (Boolean) entityData.get(DATA_IS_POWERED); }

    @Override
    protected String getHurtSound(Damage source) { return "entity.creeper.hurt"; }

    @Override
    protected String getDeathSound() { return "entity.creeper.death"; }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        if (!held.isEmpty() && ItemTags.is(held, "minecraft:creeper_igniters")) {
            String sound = ItemTools.itemName(held).equals("minecraft:fire_charge") ? "item.firecharge.use" : "item.flintandsteel.use";
            level.playSound(null, x(), y(), z(), sound, soundSource(), 1.0F, random.nextFloat() * 0.4F + 0.8F);
            ignite();
            if (ItemTools.isDamageable(held)) {
                var inv = p.inventory();
                int ws = hand == 0 ? inv.heldWindowSlot() : 45;
                var actor = new org.domi.furnace.server.block.PlayerActor(p, hand == 0 ? held : ItemStack.EMPTY, hand == 1 ? held : ItemStack.EMPTY, 0);
                org.domi.furnace.server.block.BlockBreaking.hurtAndBreak(level, actor, hand, held, 1);
                if (actor.handChanged(hand)) inv.set(ws, actor.itemInHand(hand));
            } else if (!p.vitals().isCreative()) {
                var inv = p.inventory();
                inv.set(hand == 0 ? inv.heldWindowSlot() : 45, held.withCount(held.count() - 1));
            }
            return true;
        }
        return super.interact(p, hand, held, region);
    }

    private void explodeCreeper() {
        float f = isPowered() ? 2.0F : 1.0F;
        dead = true;
        level.explode(this, x(), y(), z(), explosionRadius * f, false, level.destroyType("MOB"));
        discard();
    }

    public int getSwellDir() { return (Integer) entityData.get(DATA_SWELL_DIR); }

    public void setSwellDir(int d) { entityData.set(DATA_SWELL_DIR, d); }

    public boolean isIgnited() { return (Boolean) entityData.get(DATA_IS_IGNITED); }

    public void ignite() { entityData.set(DATA_IS_IGNITED, true); }
}
