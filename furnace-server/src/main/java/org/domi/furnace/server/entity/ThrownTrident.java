// 바닐라 ThrownTrident: 던진 삼지창. 피해 8, 맞히면 튕겨 나가고(속도 ×(-0.01,-0.1,-0.01)), 충성 마법이 있으면 던진 뒤 주인에게 돌아온다.
// 집전(channeling): 뇌우 중 하늘이 보이는 대상을 맞히거나 피뢰침을 맞히면 번개(마법 효과 summon_entity + play_sound). 꿰뚫기(impaling) 피해 추가.
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

    public ThrownTrident(ChunkLevel level, int id, Targetable owner, ItemStack stack) {
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
        float damage = 8.0F + impalingBonus(target);
        Targetable owner = getOwner();
        Damage dmg = new Damage("trident", false, false, false);
        dealtDamage = true;
        boolean hurt;
        if (target instanceof LivingEntity le) hurt = le.hurtByVia(dmg, damage, owner == null ? null : owner, this);
        else hurt = target.hurtByMob(dmg, damage, owner instanceof Mob m ? m : null);
        if (hurt) {
            if (target instanceof Entity e && e.type() == EntityType.ENDERMAN) return;
            channelingPostAttack(target);
            if (target instanceof LivingEntity le) {
                if (owner instanceof LivingEntity lo) lo.setLastHurtMob(target);
                doPostHurtEffects(le);
            }
        }
        setDeltaMovement(vx() * -0.01, vy() * -0.1, vz() * -0.01);
        playSound("item.trident.hit", 1.0F, 1.0F);
    }

    /** EnchantmentHelper.modifyDamage: 꿰뚫기 — 대상이 #sensitive_to_impaling 이면 +2.5 × 레벨. */
    private float impalingBonus(Targetable target) {
        int lvl = ItemTools.enchantmentLevel(getPickupItem(), "minecraft:impaling");
        if (lvl <= 0 || !(target instanceof Entity e) || !e.type().is("minecraft:sensitive_to_impaling")) return 0.0F;
        return 2.5F + 2.5F * (lvl - 1);
    }

    private boolean hasChanneling() { return ItemTools.enchantmentLevel(getPickupItem(), "minecraft:channeling") > 0; }

    /** channeling post_attack: 뇌우이고 맞은 대상 위치에서 하늘이 보이면 그 위치에 번개 + 천둥 소리(대상의 소리 분류). */
    private void channelingPostAttack(Targetable victim) {
        if (!hasChanneling() || !level.isThundering() || !level.canSeeSky(victim.blockPosition())) return;
        boolean silent = victim instanceof Entity e && e.isSilent();
        summonLightning(victim.x(), victim.y(), victim.z(), silent, victim instanceof Entity e ? e.soundSource() : "players");
    }

    /** channeling hit_block: 뇌우이고 맞은 점(블록 안으로 자른 위치)에서 하늘이 보이고 피뢰침이면 번개. */
    @Override
    protected void onHitBlock(Hit hit) {
        int state = level.getBlockState(hit.blockPos());
        super.onHitBlock(hit);
        if (!hasChanneling() || !level.isThundering()) return;
        var bp = hit.blockPos();
        double ox = Math.clamp(hit.x(), bp.x(), bp.x() + 1.0), oy = Math.clamp(hit.y(), bp.y(), bp.y() + 1.0), oz = Math.clamp(hit.z(), bp.z(), bp.z() + 1.0);
        var at = new org.domi.furnace.server.block.Pos(floor(ox), floor(oy), floor(oz));
        if (!level.canSeeSky(at) || !org.domi.furnace.server.block.B.is(state, "lightning_rod")) return;
        summonLightning(ox, oy, oz, isSilent(), soundSource());
    }

    /**
     * SummonEntityEffect(lightning_bolt) + PlaySoundEffect: 종류 고르기(레벨 난수 nextInt(1)), EntityType.spawn(칸 가운데·무작위 yRot)으로 넣은 뒤
     * 정확한 위치로 옮기고, 소리(5, 1).
     */
    private void summonLightning(double ox, double oy, double oz, boolean silent, String source) {
        int bx = floor(ox), by = floor(oy), bz = floor(oz);
        if (by < -20000000 || by >= 20000000 || bx < -30000000 || bz < -30000000 || bx >= 30000000 || bz >= 30000000) return;
        level.random().nextInt(1);
        var bolt = new LightningBolt(level, level.nextEntityId());
        bolt.moveTo(bx + 0.5, by, bz + 0.5, org.domi.furnace.server.entity.ai.control.Mth.wrapDegrees(level.random().nextFloat() * 360.0F), 0.0F);
        level.addFreshEntity(bolt);
        // TODO(발전 과제): LightningBolt.setCause(소유 플레이어) — 번개 원인 기록이 아직 없다
        bolt.moveTo(ox, oy, oz, bolt.yRot(), bolt.xRot());
        if (!silent) level.playSound(null, ox, oy, oz, "item.trident.thunder", source, 5.0F, 1.0F);
    }

    /** ThrownTrident.playerTouch: 소유자이거나 소유자가 없을 때만. */
    @Override
    public void playerTouch(Player player) {
        if (ownedBy(player) || getOwner() == null) super.playerTouch(player);
    }

    /** tryPickup: 원래 규칙 또는 돌아오는 중(noPhysics)인 자기 삼지창은 인벤토리로. */
    @Override
    protected boolean tryPickup(Player player) {
        return super.tryPickup(player) || isNoPhysics() && ownedBy(player) && player.inventory().add(getPickupItem(), false, 64, false).isEmpty();
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
