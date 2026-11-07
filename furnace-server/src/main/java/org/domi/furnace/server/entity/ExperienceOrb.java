// 바닐라 ExperienceOrb: 경험치 구슬. 중력 0.03·물속 떠오름·용암에서 튐, 가까운 같은 값 구슬과 합치기(count), 8칸 안 플레이어를 따라가기,
// 6000틱 뒤 사라짐, 체력 5, 줍기(수선 아이템에 먼저 쓰고 남은 만큼 경험치).
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class ExperienceOrb extends Entity {
    @Override public boolean isAttackable() { return false; }


    private static final int LIFETIME = 6000;

    private int age;
    private int health = 5;
    private int value;
    private int count = 1;
    private Player followingPlayer;

    public ExperienceOrb(ChunkLevel level, int id) {
        super(EntityType.EXPERIENCE_ORB, level, id);
    }

    public ExperienceOrb(ChunkLevel level, int id, double x, double y, double z, int value) {
        this(level, id);
        setPos(x, y, z);
        setYRot((float) (random.nextDouble() * 360.0));
        setDeltaMovement((random.nextDouble() * 0.2F - 0.1F) * 2.0, random.nextDouble() * 0.2 * 2.0, (random.nextDouble() * 0.2F - 0.1F) * 2.0);
        this.value = value;
    }

    @Override protected double defaultGravity() { return 0.03; }

    public int value() { return value; }

    public int count() { return count; }

    /** 클라이언트에 보내는 값은 합친 개수를 뺀 이 구슬 하나의 값(add_experience_orb 의 short). */
    @Override
    public org.domi.furnace.server.net.protocol.Packet customSpawnPacket(double bx, double by, double bz) {
        return new org.domi.furnace.server.net.protocol.play.AddExperienceOrb(id(), bx, by, bz, value);
    }

    @Override
    public org.domi.furnace.server.block.Pos getBlockPosBelowThatAffectsMyMovement() { return getOnPos(0.999999F); }

    @Override
    public void tick() {
        super.tick();
        xo = x();
        yo = y();
        zo = z();
        if (isEyeInWater()) {
            setDeltaMovement(vx() * 0.99F, Math.min(vy() + 5.0E-4F, 0.06F), vz() * 0.99F);
        } else {
            applyGravity();
        }
        if (org.domi.furnace.server.block.Fluids.isLava(level.getFluidState(blockPosition()))) {
            setDeltaMovement((random.nextFloat() - random.nextFloat()) * 0.2F, 0.2F, (random.nextFloat() - random.nextFloat()) * 0.2F);
        }
        if (!level.noCollision(boundingBox())) moveTowardsClosestSpace(x(), (boundingBox().minY() + boundingBox().maxY()) / 2.0, z());
        if (tickCount % 20 == 1) scanForEntities();
        if (followingPlayer != null && (followingPlayer.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR || followingPlayer.vitals().isDeadOrDying())) followingPlayer = null;
        if (followingPlayer != null) {
            double dx = followingPlayer.x() - x(), dy = followingPlayer.y() + (followingPlayer.eyeY() - followingPlayer.y()) / 2.0 - y(), dz = followingPlayer.z() - z();
            double d = dx * dx + dy * dy + dz * dz;
            if (d < 64.0) {
                double f = 1.0 - Math.sqrt(d) / 8.0;
                double len = Math.sqrt(d);
                double k = len < 1.0E-5 ? 0.0 : f * f * 0.1 / len;   // normalize() * (f*f*0.1)
                setDeltaMovement(vx() + dx * k, vy() + dy * k, vz() + dz * k);
            }
        }
        move(MOVER_SELF, vx(), vy(), vz());
        float f = 0.98F;
        if (onGround()) f = frictionBelow() * 0.98F;
        setDeltaMovement(vx() * f, vy() * 0.98, vz() * f);
        if (onGround()) setDeltaMovement(vx(), vy() * -0.9, vz());
        age++;
        if (age >= LIFETIME) discard();
    }

    private void scanForEntities() {
        if (followingPlayer == null || distanceSqr(followingPlayer.x(), followingPlayer.y(), followingPlayer.z()) > 64.0) followingPlayer = level.getNearestPlayer(this, 8.0);
        for (ExperienceOrb o : level.getEntitiesOfClass(ExperienceOrb.class, boundingBox().inflate(0.5), this::canMerge)) merge(o);
    }

    private double distanceSqr(double ex, double ey, double ez) {
        double dx = ex - x(), dy = ey - y(), dz = ez - z();
        return dx * dx + dy * dy + dz * dz;
    }

    /** ExperienceOrb.award: 값을 크기 단계로 나눠 가까운 같은 값 구슬에 합치거나 새로 만든다. */
    public static void award(ChunkLevel level, double x, double y, double z, int amount) {
        while (amount > 0) {
            int v = getExperienceValue(amount);
            amount -= v;
            if (!tryMergeToExisting(level, x, y, z, v)) level.addFreshEntity(new ExperienceOrb(level, level.nextEntityId(), x, y, z, v));
        }
    }

    private static boolean tryMergeToExisting(ChunkLevel level, double x, double y, double z, int value) {
        AABB box = new AABB(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5);
        int r = level.random().nextInt(40);
        List<ExperienceOrb> list = level.getEntitiesOfClass(ExperienceOrb.class, box, o -> canMerge(o, r, value));
        if (!list.isEmpty()) {
            ExperienceOrb o = list.get(0);
            o.count++;
            o.age = 0;
            return true;
        }
        return false;
    }

    private boolean canMerge(ExperienceOrb o) { return o != this && canMerge(o, id(), value); }

    private static boolean canMerge(ExperienceOrb o, int id, int value) { return !o.isRemoved() && (o.id() - id) % 40 == 0 && o.value == value; }

    private void merge(ExperienceOrb o) {
        count += o.count;
        age = Math.min(age, o.age);
        o.discard();
    }

    @Override
    public boolean hurt(Damage source, float amount) {
        if (isInvulnerableTo(source)) return false;
        markHurt();
        health = (int) (health - amount);
        if (health <= 0) discard();
        return true;
    }

    @Override
    public void playerTouch(Player player) {
        if (player.takeXpDelay == 0) {
            player.takeXpDelay = 2;
            player.take(this, 1);
            int rest = repairPlayerItems(player, value);
            if (rest > 0) player.vitals().giveExperiencePoints(level.gameEvents().playerExpChange(player, rest));   // 플러그인 PlayerExpChangeEvent
            count--;
            if (count == 0) discard();
        }
    }

    /** 수선(mending): 손상된 수선 장비 하나를 무작위로 골라 경험치를 2배로 고치고, 남은 만큼을 돌려준다(재귀). */
    private int repairPlayerItems(Player player, int amount) {
        List<int[]> candidates = new ArrayList<>();   // {창 칸}
        PlayerInventory inv = player.inventory();
        int[] slots = {inv.heldWindowSlot(), 45, 8, 7, 6, 5};   // 주 손 · 왼손 · 발 · 다리 · 가슴 · 머리
        for (int ws : slots) {
            ItemStack s = inv.get(ws);
            if (!s.isEmpty() && ItemTools.enchantmentLevel(s, "minecraft:mending") > 0 && ItemTools.damage(s) > 0) candidates.add(new int[]{ws});
        }
        if (candidates.isEmpty()) return amount;
        int ws = candidates.get(player.random().nextInt(candidates.size()))[0];
        ItemStack s = inv.get(ws);
        int repair = amount * 2;   // repair_with_xp: multiply 2
        int fixed = Math.min(repair, ItemTools.damage(s));
        inv.set(ws, ItemTools.withDamage(s, ItemTools.damage(s) - fixed));
        if (fixed > 0) {
            int left = amount - fixed * amount / repair;
            if (left > 0) return repairPlayerItems(player, left);
        }
        return 0;
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        tag.putShort("Health", (short) health);
        tag.putShort("Age", (short) age);
        tag.putShort("Value", (short) value);
        tag.putInt("Count", count);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        health = tag.getShort("Health");
        age = tag.getShort("Age");
        value = tag.getShort("Value");
        count = Math.max(tag.getIntOr0("Count"), 1);
    }

    /** ExperienceOrb.getExperienceValue: 구슬 크기 단계(2477, 1237, 617, 307, 149, 73, 37, 17, 7, 3, 1). */
    public static int getExperienceValue(int amount) {
        if (amount >= 2477) return 2477;
        if (amount >= 1237) return 1237;
        if (amount >= 617) return 617;
        if (amount >= 307) return 307;
        if (amount >= 149) return 149;
        if (amount >= 73) return 73;
        if (amount >= 37) return 37;
        if (amount >= 17) return 17;
        if (amount >= 7) return 7;
        return amount >= 3 ? 3 : 1;
    }
}
