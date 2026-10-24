// 바닐라 Animal: 사랑 모드(InLove), 먹이 주기, 번식(새끼 낳기·경험치), 걷기 선호(풀 블록 10).
package org.domi.furnace.server.entity;

import java.util.UUID;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public abstract class Animal extends AgeableMob {
    protected int inLove;
    private UUID loveCause;

    protected Animal(EntityType type, ChunkLevel level, int id) {
        super(type, level, id);
        setPathfindingMalus(org.domi.furnace.server.entity.ai.navigation.PathType.DANGER_FIRE, 16.0F);
        setPathfindingMalus(org.domi.furnace.server.entity.ai.navigation.PathType.DAMAGE_FIRE, -1.0F);
    }

    /** 이 아이템이 먹이인가(Animal.isFood). */
    public abstract boolean isFood(ItemStack stack);

    @Override
    protected void aiStep() {
        super.aiStep();
        if (getAge() != 0) inLove = 0;
        if (inLove > 0) {
            inLove--;
            if (inLove % 10 == 0) broadcastEntityEvent((byte) 18);   // 하트 입자는 클라이언트가 난수로 그린다
        }
    }

    @Override
    public float getWalkTargetValue(Pos pos, ChunkLevel level) {
        if (org.domi.furnace.server.block.Blocks.get().nameOfState(level.getBlockState(pos.below())).equals("minecraft:grass_block")) return 10.0F;
        float f = level.getMaxLocalRawBrightness(pos) / 15.0F;   // LevelReader.getPathfindingCostFromLightLevels (오버월드 ambientLight 0)
        return f / (4.0F - 3.0F * f) - 0.5F;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) { return false; }

    @Override
    public int getAmbientSoundInterval() { return 120; }

    @Override
    protected int getExperienceReward() { return 1 + level.random().nextInt(3); }

    @Override
    public boolean canBreed() { return true; }

    /** Animal.mobInteract: 먹이를 줘서 사랑 모드로 만들거나 새끼를 키운다. */
    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        if (isFood(held)) {
            int a = getAge();
            if (a == 0 && canFallInLove()) {
                usePlayerItem(p, hand, held);
                setInLove(p);
                return true;
            }
            if (isBaby()) {
                usePlayerItem(p, hand, held);
                ageUp(getSpeedUpSecondsWhenFeeding(-a), true);
                return true;
            }
        }
        return false;
    }

    protected static int getSpeedUpSecondsWhenFeeding(int ticksUntilAdult) { return (int) (ticksUntilAdult / 20 * 0.1F); }

    protected void usePlayerItem(Player p, int hand, ItemStack held) {
        if (p.vitals().isCreative()) return;
        var inv = p.inventory();
        int ws = hand == 0 ? inv.heldWindowSlot() : 45;
        inv.set(ws, held.withCount(held.count() - 1));
    }

    public boolean canFallInLove() { return inLove <= 0; }

    public void setInLove(Player cause) {
        inLove = 600;
        if (cause != null) loveCause = cause.uuid();
        broadcastEntityEvent((byte) 18);
    }

    public void setInLoveTime(int t) { inLove = t; }

    public int inLoveTime() { return inLove; }

    public boolean isInLove() { return inLove > 0; }

    public void resetLove() { inLove = 0; }

    public boolean canMate(Animal o) {
        if (o == this) return false;
        return o.getClass() == getClass() && isInLove() && o.isInLove();
    }

    /** Animal.spawnChildFromBreeding: 새끼를 낳고 부모 쿨다운(6000)·경험치. */
    public void spawnChildFromBreeding(ChunkLevel level, Animal partner) {
        AgeableMob child = getBreedOffspring(level, partner);
        if (child != null) {
            child.setBaby(true);
            child.moveTo(x(), y(), z(), 0.0F, 0.0F);
            finalizeBreeding(level, partner, child);
            level.addFreshEntity(child);
        }
    }

    public void finalizeBreeding(ChunkLevel level, Animal partner, AgeableMob child) {
        setAge(6000);
        partner.setAge(6000);
        resetLove();
        partner.resetLove();
        broadcastEntityEvent((byte) 18);
        if (level.gameRule("doMobLoot")) {
            ExperienceOrb.award(level, x(), y(), z(), random.nextInt(7) + 1);
        }
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("InLove", inLove);
        if (loveCause != null) tag.putIntArray("LoveCause", uuidToInts(loveCause));
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        inLove = tag.getInt("InLove");
        loveCause = tag.tags().get("LoveCause") instanceof int[] u && u.length == 4
                ? new UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL) : null;
    }

    /** ItemUtils.createFilledResult: 한 개를 쓰고 결과 아이템을 같은 손에(남으면 인벤토리·바닥에). */
    protected void fillResult(Player p, int hand, ItemStack held, String result, org.domi.furnace.server.world.Region region) {
        if (p.vitals().isCreative()) return;
        var inv = p.inventory();
        int ws = hand == 0 ? inv.heldWindowSlot() : 45;
        ItemStack rest = held.withCount(held.count() - 1);
        ItemStack out = new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", result.replace("minecraft:", "")), 1);
        if (rest.isEmpty()) {
            inv.set(ws, out);
        } else {
            inv.set(ws, rest);
            ItemStack left = inv.add(out, false, 64, false);
            if (!left.isEmpty()) region.playerDrop(p, left, false, true);
        }
    }
}
