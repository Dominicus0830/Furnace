// 바닐라 ThrownTrident: 던진 삼지창. 피해 8, 맞히면 튕겨 나가고(속도 ×(-0.01,-0.1,-0.01)), 충성 마법이 있으면 던진 뒤 주인에게 돌아온다.
// TODO(번개 소환·플레이어 줍기): 번개 소환(channeling)과 충성 삼지창의 플레이어 인벤토리 직접 반환은 번개/인벤토리 연동이 생기면.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class ThrownTrident extends AbstractArrow {
    public static final int DATA_LOYALTY = 10, DATA_FOIL = 11;
    private boolean dealtDamage;
    public int clientSideReturnTridentTickCount;

    public ThrownTrident(ChunkLevel level, int id) {
        super(EntityType.TRIDENT, level, id);
        setSoundEvent("item.trident.hit_ground");
    }

    public ThrownTrident(ChunkLevel level, int id, LivingEntity owner, ItemStack stack) {
        this(level, id);
        pickupItem = stack;
        setPos(owner.x(), owner.eyeY() - 0.1F, owner.z());
        setOwner(owner);
        entityData.set(DATA_LOYALTY, loyaltyOf(stack));
        boolean enchanted = ItemTools.component(stack, ItemTools.ENCHANTMENTS) instanceof java.util.Map<?, ?> m && m.get("levels") instanceof java.util.Map<?, ?> lv && !lv.isEmpty();
        entityData.set(DATA_FOIL, ItemTools.component(stack, "minecraft:enchantment_glint_override") instanceof Boolean b ? b : enchanted);
    }

    private static byte loyaltyOf(ItemStack stack) { return (byte) Math.clamp(ItemTools.enchantmentLevel(stack, "minecraft:loyalty"), 0, 127); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_LOYALTY, SetEntityData.SERIALIZER_BYTE, (byte) 0);
        entityData.define(DATA_FOIL, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    @Override
    public void tick() {
        if (inGroundTime > 4) dealtDamage = true;
        Targetable owner = getOwner();
        int loyalty = (Byte) entityData.get(DATA_LOYALTY);
        if (loyalty > 0 && (dealtDamage || isNoPhysics()) && owner != null) {
            if (!(owner.isAlive() && !(owner instanceof Player p && p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR))) {
                if (pickup == Pickup.ALLOWED) {
                    var ie = new ItemEntity(level, level.nextEntityId(), x(), y() + 0.1F, z(), getPickupItem());
                    ie.setDefaultPickUpDelay();
                    level.addFreshEntity(ie);
                }
                discard();
            } else {
                setNoPhysics(true);
                double dx = owner.x() - x(), dy = owner.eyeY() - y(), dz = owner.z() - z();
                setPosRaw(x(), y() + dy * 0.015 * loyalty, z());
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double d = 0.05 * loyalty;
                double ux = len < 1.0E-5F ? 0.0 : dx / len, uy = len < 1.0E-5F ? 0.0 : dy / len, uz = len < 1.0E-5F ? 0.0 : dz / len;
                setDeltaMovement(vx() * 0.95 + ux * d, vy() * 0.95 + uy * d, vz() * 0.95 + uz * d);
                if (clientSideReturnTridentTickCount == 0) playSound("item.trident.return", 10.0F, 1.0F);
                clientSideReturnTridentTickCount++;
            }
        }
        super.tick();
    }

    @Override
    protected Hit findHitEntity(double fx, double fy, double fz, double tx, double ty, double tz) {
        return dealtDamage ? null : super.findHitEntity(fx, fy, fz, tx, ty, tz);
    }

    @Override
    protected void onHitEntity(Hit hit) {
        Targetable target = hit.target();
        float damage = 8.0F;
        Targetable owner = getOwner();
        Damage dmg = new Damage("trident", false, false, false);
        dealtDamage = true;
        boolean hurt;
        if (target instanceof LivingEntity le) hurt = le.hurtByVia(dmg, damage, owner == null ? null : owner, this);
        else hurt = target.hurtByMob(dmg, damage, owner instanceof Mob m ? m : null);
        if (hurt) {
            if (target instanceof Entity e && e.type() == EntityType.ENDERMAN) return;
            if (target instanceof LivingEntity le) {
                if (owner instanceof LivingEntity lo) lo.setLastHurtMob(target);
                doPostHurtEffects(le);
            }
        }
        setDeltaMovement(vx() * -0.01, vy() * -0.1, vz() * -0.01);
        playSound("item.trident.hit", 1.0F, 1.0F);
    }

    @Override protected String defaultHitGroundSound() { return "item.trident.hit_ground"; }

    @Override protected ItemStack getDefaultPickupItem() { return new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "trident"), 1); }

    @Override protected float waterInertia() { return 0.99F; }

    @Override
    protected void tickDespawn() {
        if (pickup != Pickup.ALLOWED || (Byte) entityData.get(DATA_LOYALTY) <= 0) super.tickDespawn();
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("DealtDamage", dealtDamage);
        tag.putCompound("item", ItemStackNbt.write(getPickupItem()));
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        dealtDamage = tag.getBoolean("DealtDamage");
        if (tag.tags().get("item") instanceof NbtCompound c) {
            ItemStack s = ItemStackNbt.read(c);
            if (!s.isEmpty()) pickupItem = s;
        }
        entityData.set(DATA_LOYALTY, loyaltyOf(getPickupItem()));
    }
}
