// 바닐라 Llama: 힘(1~5)·털색 4가지, 침을 뱉어 늑대/피격 대상을 공격, 캐러밴으로 줄지어 걷는다. 상자를 달면 칸 수 = 3 × 힘.
// 카펫은 몸통 갑옷 칸(BODY)에 입는다 — 양털 카펫 태그만, 늘 칸을 쓸 수 있다(바닐라 canUseSlot=true, isBodyArmorItem=wool_carpets).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Llama extends AbstractChestedHorse implements RangedAttackMob {
    public static final int DATA_STRENGTH_ID = 19, DATA_VARIANT_ID = 20;
    private boolean didSpit;
    private Llama caravanHead, caravanTail;

    public Llama(ChunkLevel level, int id) { this(EntityType.LLAMA, level, id); }

    protected Llama(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    public boolean isTraderLlama() { return false; }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_STRENGTH_ID, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_VARIANT_ID, SetEntityData.SERIALIZER_INT, 0);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new RunAroundLikeCrazyGoal(this, 1.2));
        goalSelector.addGoal(2, new LlamaFollowCaravanGoal(this, 2.1F));
        goalSelector.addGoal(3, new RangedAttackGoal(this, 1.25, 40, 20.0F));
        goalSelector.addGoal(3, new PanicGoal(this, 1.2));
        goalSelector.addGoal(4, new BreedGoal(this, 1.0));
        goalSelector.addGoal(5, new TemptGoal(this, 1.25, s -> ItemTags.is(s, "minecraft:llama_tempt_items"), false));
        goalSelector.addGoal(6, new FollowParentGoal(this, 1.0));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, 6.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new LlamaHurtByTargetGoal(this));
        targetSelector.addGoal(2, new LlamaAttackWolfGoal(this));
    }

    /** LlamaHurtByTargetGoal: 침을 뱉은 직후엔 다음 틱에 목표를 놓는다. */
    private static class LlamaHurtByTargetGoal extends HurtByTargetGoal {
        LlamaHurtByTargetGoal(Llama llama) { super(llama); }

        @Override
        public boolean canContinueToUse() {
            if (mob instanceof Llama l && l.didSpit) {
                l.didSpit = false;
                return false;
            }
            return super.canContinueToUse();
        }
    }

    /** LlamaAttackWolfGoal: 길들이지 않은 늑대(16칸 간격, 도달 가능)를 노리되 탐색 거리는 1/4. */
    private static class LlamaAttackWolfGoal extends NearestAttackableTargetGoal {
        LlamaAttackWolfGoal(Llama llama) { super(llama, Wolf.class, 16, false, true, t -> !((Wolf) t).isTame()); }

        @Override
        protected double getFollowDistance() { return super.getFollowDistance() * 0.25; }
    }

    // ------------------------------------------------------------------ 힘·털색

    private void setStrength(int v) { entityData.set(DATA_STRENGTH_ID, Math.max(1, Math.min(5, v))); }

    private void setRandomStrength(org.domi.furnace.server.block.RandomSource r) {
        int bound = r.nextFloat() < 0.04F ? 5 : 3;
        setStrength(1 + r.nextInt(bound));
    }

    public int getStrength() { return (Integer) entityData.get(DATA_STRENGTH_ID); }

    public int variant() { return Math.floorMod((Integer) entityData.get(DATA_VARIANT_ID), 4); }

    public void setVariant(int v) { entityData.set(DATA_VARIANT_ID, v); }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", variant());
        tag.putInt("Strength", getStrength());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        setStrength(tag.getIntOr0("Strength"));
        super.readAdditionalSaveData(tag);
        setVariant(tag.getIntOr0("Variant"));
    }

    @Override
    public int inventoryColumns() { return hasChest() ? getStrength() : 0; }

    @Override
    public AgeableMob.GroupData finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty, AgeableMob.GroupData data) {
        setRandomStrength(r);
        int v;
        if (data instanceof LlamaGroupData g) {
            v = g.variant;
        } else {
            v = r.nextInt(4);
            data = new LlamaGroupData(v);
        }
        setVariant(v);
        return super.finalizeSpawn(r, difficulty, data);
    }

    public static final class LlamaGroupData extends AgeableMob.GroupData {
        public final int variant;

        LlamaGroupData(int variant) {
            super(true);
            this.variant = variant;
        }
    }

    // ------------------------------------------------------------------ 먹이

    @Override
    public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:llama_food"); }

    /** canUseSlot: 라마는 몸통 칸(카펫)을 늘 쓸 수 있다. */
    @Override public boolean canUseBodySlot() { return true; }

    /** isBodyArmorItem: 양털 카펫. */
    @Override public boolean isBodyArmorItem(ItemStack s) { return !s.isEmpty() && ItemTags.is(s, "minecraft:wool_carpets"); }

    @Override
    protected boolean handleEating(Player p, ItemStack s) {
        int age = 0, temperDelta = 0;
        float heal = 0.0F;
        boolean used = false;
        String n = ItemTools.itemName(s);
        if (n.equals("minecraft:wheat")) {
            age = 10;
            temperDelta = 3;
            heal = 2.0F;
        } else if (n.equals("minecraft:hay_block")) {
            age = 90;
            temperDelta = 6;
            heal = 10.0F;
            if (isTamed() && getAge() == 0 && canFallInLove()) {
                used = true;
                setInLove(p);
            }
        }
        if (health() < maxHealth() && heal > 0.0F) {
            heal(heal);
            used = true;
        }
        if (isBaby() && age > 0) {
            ageUp(age);
            used = true;
        }
        if (temperDelta > 0 && (used || !isTamed()) && getTemper() < getMaxTemper()) {
            used = true;
            modifyTemper(temperDelta);
        }
        if (used && !isSilent()) {
            playSound("entity.llama.eat", 1.0F, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.2F);
        }
        return used;
    }

    @Override
    public boolean isImmobile() { return health() <= 0.0F || isEating(); }

    @Override protected boolean canPerformRearing() { return false; }

    @Override protected boolean canEatGrass() { return false; }

    @Override public int getMaxTemper() { return 30; }

    @Override
    protected void followMother() {
        if (!inCaravan() && isBaby()) super.followMother();
    }

    // ------------------------------------------------------------------ 소리

    @Override public String getAmbientSound() { return "entity.llama.ambient"; }

    @Override protected String getAngrySound() { return "entity.llama.angry"; }

    @Override protected String getHurtSound(Damage source) { return "entity.llama.hurt"; }

    @Override protected String getDeathSound() { return "entity.llama.death"; }

    @Override protected String getEatingSound() { return "entity.llama.eat"; }

    @Override
    protected void playChestEquipsSound() { playSound("entity.llama.chest", 1.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F); }

    // ------------------------------------------------------------------ 번식

    @Override
    public boolean canMate(Animal other) { return other != this && other instanceof Llama && canParent() && ((Llama) other).canParent(); }

    protected Llama makeNewLlama(ChunkLevel level) { return new Llama(level, level.nextEntityId()); }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) {
        Llama child = makeNewLlama(level);
        setOffspringAttributes(partner, child);
        Llama other = (Llama) partner;
        int i = random.nextInt(Math.max(getStrength(), other.getStrength())) + 1;
        if (random.nextFloat() < 0.03F) i++;
        child.setStrength(i);
        child.setVariant(random.nextBoolean() ? variant() : other.variant());
        return child;
    }

    // ------------------------------------------------------------------ 침

    private void spit(Targetable target) {
        LlamaSpit spit = new LlamaSpit(level, level.nextEntityId());
        spit.startAt(this);
        double dx = target.x() - x();
        double dy = target.y() + target.bbHeight() * 0.3333333333333333 - spit.y();
        double dz = target.z() - z();
        double h = Math.sqrt(dx * dx + dz * dz) * 0.2F;
        spit.shoot(dx, dy + h, dz, 1.5F, 10.0F);
        if (!isSilent()) playSound("entity.llama.spit", 1.0F, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.2F);
        level.addFreshEntity(spit);
        didSpit = true;
    }

    @Override
    public void performRangedAttack(Targetable target, float power) { spit(target); }

    // ------------------------------------------------------------------ 캐러밴


    public boolean inCaravan() { return caravanHead != null; }

    public boolean hasCaravanTail() { return caravanTail != null; }

    public Llama getCaravanHead() { return caravanHead; }

    public void joinCaravan(Llama head) {
        caravanHead = head;
        head.caravanTail = this;
    }

    public void leaveCaravan() {
        if (caravanHead != null) caravanHead.caravanTail = null;
        caravanHead = null;
    }

    public double distSqr(Entity e) {
        double dx = e.x() - x(), dy = e.y() - y(), dz = e.z() - z();
        return dx * dx + dy * dy + dz * dz;
    }
}
