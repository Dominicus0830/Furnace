// 바닐라 FireworkRocketEntity: 폭죽 로켓. 위로 날다 수명(10 × 비행 시간 + 무작위)이 다하거나 무언가에 맞으면 터지고, 폭발 장식이 있으면 5 칸 안 생물에게 피해를 준다.
// 겉날개 비행 중 쓴 로켓은 플레이어에 붙어(attached) 매 틱 그 deltaMovement 를 가속한다. 폭발 장식 입자는 클라이언트가 이벤트 17 로 그린다.
package org.domi.furnace.server.entity;

import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class FireworkRocketEntity extends Projectile {
    public static final int DATA_ID_FIREWORKS_ITEM = 8, DATA_ATTACHED_TO_TARGET = 9, DATA_SHOT_AT_ANGLE = 10;
    private int life, lifetime;
    /** 붙은 플레이어(바닐라 attachedToEntity — 저장하지 않는다). */
    private Player attached;

    public FireworkRocketEntity(ChunkLevel level, int id) { super(EntityType.FIREWORK_ROCKET, level, id); }

    public FireworkRocketEntity(ChunkLevel level, int id, double x, double y, double z, ItemStack stack) {
        this(level, id);
        setPos(x, y, z);
        entityData.set(DATA_ID_FIREWORKS_ITEM, stack.withCount(1));
        int flight = 1;
        if (ItemTools.component(stack, "minecraft:fireworks") instanceof Map<?, ?> m && m.get("flight_duration") instanceof Number n) flight += n.intValue();
        setDeltaMovement(random.triangle(0.0, 0.002297), 0.05, random.triangle(0.0, 0.002297));
        lifetime = 10 * flight + random.nextInt(6) + random.nextInt(7);
    }

    /** new FireworkRocketEntity(level, owner, x, y, z, stack): 난수 소비는 owner 없는 생성자와 같고 소유자만 정한다. */
    public FireworkRocketEntity(ChunkLevel level, int id, Targetable owner, double x, double y, double z, ItemStack stack) {
        this(level, id, x, y, z, stack);
        setOwner(owner);
    }

    /** new FireworkRocketEntity(level, stack, shooter): 그 자리에서 만들고(난수 소비 같음) 소유자·붙은 대상을 정한다. */
    public FireworkRocketEntity(ChunkLevel level, int id, ItemStack stack, Player shooter) {
        this(level, id, shooter.x(), shooter.y(), shooter.z(), stack);
        setOwner(shooter);
        entityData.set(DATA_ATTACHED_TO_TARGET, shooter.entityId());
        attached = shooter;
    }

    public Player attachedTo() { return attached; }

    /**
     * 붙은 로켓의 가속: look·0.1 + (look·1.5 − v)·0.5. 반환은 새 속도.
     */
    public static double[] boost(double vx, double vy, double vz, double lx, double ly, double lz) {
        return new double[] {vx + (lx * 0.1 + (lx * 1.5 - vx) * 0.5), vy + (ly * 0.1 + (ly * 1.5 - vy) * 0.5), vz + (lz * 0.1 + (lz * 1.5 - vz) * 0.5)};
    }

    /** LivingEntity.getHandHoldingItemAngle(로켓): 주 손(로켓이 다른 손에만 있으면 그 손) 쪽 yaw ±80° 시선의 절반. */
    private static double[] handAngle(Player p) {
        var inv = p.inventory();
        boolean main = "minecraft:firework_rocket".equals(ItemTools.itemName(inv.get(36 + inv.selected())));
        boolean off = "minecraft:firework_rocket".equals(ItemTools.itemName(inv.get(45)));
        boolean right = p.mainArm() == 1;
        if (off && !main) right = !right;
        double[] v = org.domi.furnace.server.block.Clip.viewVector(0.0F, p.yaw() + (right ? 80 : -80));
        return new double[] {v[0] * 0.5, v[1] * 0.5, v[2] * 0.5};
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ID_FIREWORKS_ITEM, SetEntityData.SERIALIZER_ITEM, defaultItem());
        entityData.define(DATA_ATTACHED_TO_TARGET, SetEntityData.SERIALIZER_OPTIONAL_INT, null);
        entityData.define(DATA_SHOT_AT_ANGLE, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    private static ItemStack defaultItem() { return new ItemStack(ItemContext.id("minecraft:item", "minecraft:firework_rocket"), 1); }

    public ItemStack getItem() { return (ItemStack) entityData.get(DATA_ID_FIREWORKS_ITEM); }

    public void setShotAtAngle(boolean b) { entityData.set(DATA_SHOT_AT_ANGLE, b); }

    public boolean isShotAtAngle() { return (Boolean) entityData.get(DATA_SHOT_AT_ANGLE); }

    @Override public boolean isPickable() { return false; }

    private List<?> explosions() {
        return ItemTools.component(getItem(), "minecraft:fireworks") instanceof Map<?, ?> m && m.get("explosions") instanceof List<?> l ? l : List.of();
    }

    @Override
    public void tick() {
        super.tick();
        if (entityData.get(DATA_ATTACHED_TO_TARGET) != null) {
            // 붙은 플레이어의 상태는 같은 region 스레드일 때만 바꾼다. 다른 region 이면 위치(volatile)만 따라가
            // 다음 틱 handOffEntities 가 로켓을 플레이어 region 으로 넘긴다(그 사이 가속은 빠진다).
            Player p = attached;
            if (p != null && p.currentLevel != level) {
                setPos(p.x(), p.y(), p.z());
                double[] m = p.knownMovement();
                setDeltaMovement(m[0], m[1], m[2]);
            } else if (p != null) {
                double[] hand;
                if (p.isFallFlying()) {
                    double[] look = org.domi.furnace.server.block.Clip.viewVector(p.pitch(), p.yaw());
                    double[] v = boost(p.flyVx, p.flyVy, p.flyVz, look[0], look[1], look[2]);
                    p.flyVx = v[0];
                    p.flyVy = v[1];
                    p.flyVz = v[2];
                    hand = handAngle(p);
                } else {
                    hand = new double[3];
                }
                setPos(p.x() + hand[0], p.y() + hand[1], p.z() + hand[2]);
                double[] m = p.serverMovement();
                setDeltaMovement(m[0], m[1], m[2]);
            }
        } else {
            if (!isShotAtAngle()) {
                double d = horizontalCollision ? 1.0 : 1.15;
                setDeltaMovement(vx() * d, vy(), vz() * d);
                setDeltaMovement(vx(), vy() + 0.04, vz());
            }
            double dx = vx(), dy = vy(), dz = vz();
            move(MOVER_SELF, dx, dy, dz);
            setDeltaMovement(dx, dy, dz);
        }
        if (!noPhysics) {
            Hit hit = findHit(x(), y(), z(), x() + vx(), y() + vy(), z() + vz());
            if (hit != null) {
                onHit(hit);
                hasImpulse = true;
            }
        }
        double h = Math.sqrt(vx() * vx() + vz() * vz());
        setYRot(lerpRotation(yRotO, (float) (org.domi.furnace.server.entity.ai.control.Mth.atan2(vx(), vz()) * 180.0F / (float) Math.PI)));
        setXRot(lerpRotation(xRotO, (float) (org.domi.furnace.server.entity.ai.control.Mth.atan2(vy(), h) * 180.0F / (float) Math.PI)));
        if (life == 0 && !isSilent()) level.playSound(null, x(), y(), z(), "minecraft:entity.firework_rocket.launch", "ambient", 3.0F, 1.0F);
        life++;
        if (life > lifetime) explode();
    }

    private void explode() {
        broadcastEntityEvent((byte) 17);
        dealExplosionDamage();
        discard();
    }

    @Override
    protected void onHitEntity(Hit hit) {
        super.onHitEntity(hit);
        explode();
    }

    @Override
    protected void onHitBlock(Hit hit) {
        int state = level.getBlockState(hit.blockPos());
        org.domi.furnace.server.block.B.behavior(state).entityInside(state, level, hit.blockPos(), this);
        if (!explosions().isEmpty()) explode();
        super.onHitBlock(hit);
    }

    private void dealExplosionDamage() {
        List<?> ex = explosions();
        float amount = ex.isEmpty() ? 0.0F : 5.0F + ex.size() * 2;
        if (amount <= 0.0F) return;
        Player p = attached;
        if (p != null) {   // 붙은 대상은 거리·시야 없이 전부 — 플레이어 소유 스레드에서
            Rider owner = getOwner() instanceof Rider r ? r : null;
            var src = org.domi.furnace.server.entity.damage.DamageSource.by(
                    org.domi.furnace.server.entity.damage.DamageType.byKey("fireworks"), this, owner);
            float a = amount;
            p.runOnOwner(() -> p.vitals().hurt(src, a));
        }
        Damage src = new Damage("fireworks", false, true, false);
        AABB box = boundingBox().inflate(5.0);
        for (Entity e : level.getEntities(this, box, en -> en instanceof LivingEntity)) {
            if ((Object) e == p) continue;   // 바닐라: living != attachedToEntity (붙은 대상은 위에서 따로 맞았다)
            double d = (e.x() - x()) * (e.x() - x()) + (e.y() - y()) * (e.y() - y()) + (e.z() - z()) * (e.z() - z());
            if (d > 25.0 || !canSee(e.x(), e.y(), e.z()) && !canSee(e.x(), e.y() + 0.5 * e.bbHeight(), e.z())) continue;
            ((LivingEntity) e).hurtByVia(src, amount * (float) Math.sqrt((5.0 - Math.sqrt(d)) / 5.0), getOwner(), this);
        }
    }

    private boolean canSee(double tx, double ty, double tz) { return org.domi.furnace.server.block.Clip.clipCollider(level, x(), y(), z(), tx, ty, tz).miss(); }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Life", life);
        tag.putInt("LifeTime", lifetime);
        tag.putCompound("FireworksItem", ItemStackNbt.write(getItem()));
        tag.putBoolean("ShotAtAngle", isShotAtAngle());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        life = tag.getIntOr0("Life");
        lifetime = tag.getIntOr0("LifeTime");
        entityData.set(DATA_ID_FIREWORKS_ITEM, tag.tags().get("FireworksItem") instanceof NbtCompound c ? ItemStackNbt.read(c) : defaultItem());
        if (tag.contains("ShotAtAngle")) setShotAtAngle(tag.getBoolean("ShotAtAngle"));
    }

    /** 폭죽은 속도가 아니라 대상 위치 − 폭죽 위치. */
    @Override
    public double[] hurtKnockbackDirection(Targetable target) { return new double[] {target.x() - x(), target.z() - z()}; }
}
