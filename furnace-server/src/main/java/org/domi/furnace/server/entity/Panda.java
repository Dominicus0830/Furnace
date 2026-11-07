// 바닐라 Panda: 유전자 두 개(주·숨은)로 성격이 정해지는 곰. 대나무를 먹이로 앉아서 먹고, 아기는 구르고 재채기한다.
// 성격: 보통·게으름·걱정·장난·갈색·약함·공격. 번식은 주변에 대나무가 있어야 한다(없으면 32틱 동안 고개를 젓는다).
// TODO(입자): 먹을 때·재채기 입자는 서버에서 난수 소비만 한다.
package org.domi.furnace.server.entity;

import java.util.EnumSet;
import java.util.List;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.control.MoveControl;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Panda extends Animal {
    /** canBeLeashed(디컴파일 y): 판다(cft)는 못 맨다. */
    @Override
    public boolean canBeLeashed() { return false; }


    public static final int DATA_UNHAPPY_COUNTER = 17, DATA_SNEEZE_COUNTER = 18, DATA_EAT_COUNTER = 19, DATA_MAIN_GENE_ID = 20, DATA_HIDDEN_GENE_ID = 21, DATA_ID_FLAGS = 22;
    private static final int FLAG_SNEEZE = 2, FLAG_ROLL = 4, FLAG_SIT = 8, FLAG_ON_BACK = 16;

    public enum Gene {
        NORMAL(0, "normal", false), LAZY(1, "lazy", false), WORRIED(2, "worried", false), PLAYFUL(3, "playful", false), BROWN(4, "brown", true), WEAK(5, "weak", true),
        AGGRESSIVE(6, "aggressive", false);

        public final int id;
        public final String key;
        public final boolean recessive;

        Gene(int id, String key, boolean recessive) {
            this.id = id;
            this.key = key;
            this.recessive = recessive;
        }

        static Gene byId(int id) { return values()[id >= 0 && id < values().length ? id : 0]; }

        static Gene byName(String s) {
            for (Gene g : values()) if (g.key.equals(s)) return g;
            return NORMAL;
        }

        static Gene getVariantFromGenes(Gene main, Gene hidden) {
            if (main.recessive) return main == hidden ? main : NORMAL;
            return main;
        }

        static Gene getRandom(org.domi.furnace.server.block.RandomSource r) {
            int i = r.nextInt(16);
            if (i == 0) return LAZY;
            if (i == 1) return WORRIED;
            if (i == 2) return PLAYFUL;
            if (i == 4) return AGGRESSIVE;
            if (i < 9) return WEAK;
            return i < 11 ? BROWN : NORMAL;
        }
    }

    boolean gotBamboo, didBite;
    public int rollCounter;
    private double rollX, rollZ;
    private float sitAmount, sitAmountO, onBackAmount, onBackAmountO, rollAmount, rollAmountO;
    PandaLookAtPlayerGoal lookAtPlayerGoal;
    private int sitCooldown, lieOnBackCooldown, breedUnhappyCooldown;

    public Panda(ChunkLevel level, int id) {
        super(EntityType.PANDA, level, id);
        moveControl = new MoveControl(this) {
            @Override
            public void tick() {
                if (canPerformAction()) super.tick();
            }
        };
        if (!isBaby()) setCanPickUpLoot(true);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 0.15F).add(Attribute.ATTACK_DAMAGE, 6.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_UNHAPPY_COUNTER, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_SNEEZE_COUNTER, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_EAT_COUNTER, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_MAIN_GENE_ID, SetEntityData.SERIALIZER_BYTE, (byte) 0);
        entityData.define(DATA_HIDDEN_GENE_ID, SetEntityData.SERIALIZER_BYTE, (byte) 0);
        entityData.define(DATA_ID_FLAGS, SetEntityData.SERIALIZER_BYTE, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new PandaPanicGoal(this, 2.0));
        goalSelector.addGoal(2, new PandaBreedGoal(this, 1.0));
        goalSelector.addGoal(3, new PandaAttackGoal(this, 1.2F, true));
        goalSelector.addGoal(4, new TemptGoal(this, 1.0, s -> ItemTags.is(s, "minecraft:panda_food"), false));
        goalSelector.addGoal(6, new PandaAvoidGoal(this, Player.class, 8.0F, 2.0, 2.0));
        goalSelector.addGoal(6, new PandaAvoidGoal(this, Monster.class, 4.0F, 2.0, 2.0));
        goalSelector.addGoal(7, new PandaSitGoal());
        goalSelector.addGoal(8, new PandaLieOnBackGoal());
        goalSelector.addGoal(8, new PandaSneezeGoal());
        lookAtPlayerGoal = new PandaLookAtPlayerGoal(this, 6.0F);
        goalSelector.addGoal(9, lookAtPlayerGoal);
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        goalSelector.addGoal(12, new PandaRollGoal());
        goalSelector.addGoal(13, new FollowParentGoal(this, 1.25));
        goalSelector.addGoal(14, new WaterAvoidingRandomStrollGoal(this, 1.0));
        targetSelector.addGoal(1, new PandaHurtByTargetGoal(this).setAlertOthers());
    }

    @Override
    public boolean canHoldItem(ItemStack stack) {
        EquipmentSlot slot = equipmentSlotFor(stack);
        return !getItemBySlot(slot).isEmpty() ? false : slot == EquipmentSlot.MAINHAND && super.canHoldItem(stack);
    }

    // ------------------------------------------------------------------ 데이터

    public int getUnhappyCounter() { return (Integer) entityData.get(DATA_UNHAPPY_COUNTER); }

    public void setUnhappyCounter(int v) { entityData.set(DATA_UNHAPPY_COUNTER, v); }

    public int getSneezeCounter() { return (Integer) entityData.get(DATA_SNEEZE_COUNTER); }

    public void setSneezeCounter(int v) { entityData.set(DATA_SNEEZE_COUNTER, v); }

    private int getEatCounter() { return (Integer) entityData.get(DATA_EAT_COUNTER); }

    private void setEatCounter(int v) { entityData.set(DATA_EAT_COUNTER, v); }

    private boolean getFlag(int f) { return ((Byte) entityData.get(DATA_ID_FLAGS) & f) != 0; }

    private void setFlag(int f, boolean on) {
        byte b = (Byte) entityData.get(DATA_ID_FLAGS);
        entityData.set(DATA_ID_FLAGS, (byte) (on ? b | f : b & ~f));
    }

    public boolean isSneezing() { return getFlag(FLAG_SNEEZE); }

    public void sneeze(boolean v) {
        setFlag(FLAG_SNEEZE, v);
        if (!v) setSneezeCounter(0);
    }

    public boolean isSitting() { return getFlag(FLAG_SIT); }

    public void sit(boolean v) { setFlag(FLAG_SIT, v); }

    public boolean isOnBack() { return getFlag(FLAG_ON_BACK); }

    public void setOnBack(boolean v) { setFlag(FLAG_ON_BACK, v); }

    public boolean isRolling() { return getFlag(FLAG_ROLL); }

    public void roll(boolean v) { setFlag(FLAG_ROLL, v); }

    public boolean isEating() { return getEatCounter() > 0; }

    public void eat(boolean v) { setEatCounter(v ? 1 : 0); }

    public Gene getMainGene() { return Gene.byId((Byte) entityData.get(DATA_MAIN_GENE_ID)); }

    public void setMainGene(Gene g) {
        if (g.id > 6) g = Gene.getRandom(random);
        entityData.set(DATA_MAIN_GENE_ID, (byte) g.id);
    }

    public Gene getHiddenGene() { return Gene.byId((Byte) entityData.get(DATA_HIDDEN_GENE_ID)); }

    public void setHiddenGene(Gene g) {
        if (g.id > 6) g = Gene.getRandom(random);
        entityData.set(DATA_HIDDEN_GENE_ID, (byte) g.id);
    }

    public Gene getVariant() { return Gene.getVariantFromGenes(getMainGene(), getHiddenGene()); }

    public boolean isLazy() { return getVariant() == Gene.LAZY; }

    public boolean isWorried() { return getVariant() == Gene.WORRIED; }

    public boolean isPlayful() { return getVariant() == Gene.PLAYFUL; }

    public boolean isBrown() { return getVariant() == Gene.BROWN; }

    public boolean isWeak() { return getVariant() == Gene.WEAK; }

    @Override public boolean isAggressive() { return getVariant() == Gene.AGGRESSIVE; }

    public boolean isScared() { return isWorried() && level.isThundering(); }

    /** Panda.canPerformAction: 뒤집힘·겁먹음·먹는 중·구르는 중·앉은 중이 아니다. */
    public boolean canPerformAction() { return !isOnBack() && !isScared() && !isEating() && !isRolling() && !isSitting(); }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("MainGene", getMainGene().key);
        tag.putString("HiddenGene", getHiddenGene().key);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setMainGene(Gene.byName(tag.getString("MainGene")));
        setHiddenGene(Gene.byName(tag.getString("HiddenGene")));
    }

    // ------------------------------------------------------------------ 번식·태어남

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) {
        Panda child = new Panda(level, level.nextEntityId());
        if (partner instanceof Panda other) child.setGeneFromParents(this, other);
        child.setAttributes();
        return child;
    }

    public void setGeneFromParents(Panda p1, Panda p2) {
        if (p2 == null) {
            if (random.nextBoolean()) {
                setMainGene(p1.getOneOfGenesRandomly());
                setHiddenGene(Gene.getRandom(random));
            } else {
                setMainGene(Gene.getRandom(random));
                setHiddenGene(p1.getOneOfGenesRandomly());
            }
        } else if (random.nextBoolean()) {
            setMainGene(p1.getOneOfGenesRandomly());
            setHiddenGene(p2.getOneOfGenesRandomly());
        } else {
            setMainGene(p2.getOneOfGenesRandomly());
            setHiddenGene(p1.getOneOfGenesRandomly());
        }
        if (random.nextInt(32) == 0) setMainGene(Gene.getRandom(random));
        if (random.nextInt(32) == 0) setHiddenGene(Gene.getRandom(random));
    }

    private Gene getOneOfGenesRandomly() { return random.nextBoolean() ? getMainGene() : getHiddenGene(); }

    public void setAttributes() {
        if (isWeak()) attributes().get(Attribute.MAX_HEALTH).setBaseValue(10.0);
        if (isLazy()) attributes().get(Attribute.MOVEMENT_SPEED).setBaseValue(0.07F);
    }

    @Override
    public GroupData finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty, GroupData data) {
        setMainGene(Gene.getRandom(r));
        setHiddenGene(Gene.getRandom(r));
        setAttributes();
        if (data == null) data = new GroupData(0.2F);
        return super.finalizeSpawn(r, difficulty, data);
    }

    // ------------------------------------------------------------------ 틱

    @Override
    public boolean doHurtTarget(Targetable t) {
        if (!isAggressive()) didBite = true;
        return super.doHurtTarget(t);
    }

    @Override
    public void tick() {
        super.tick();
        if (isWorried()) {
            if (level.isThundering() && !isInWater()) {
                sit(true);
                eat(false);
            } else if (!isEating()) {
                sit(false);
            }
        }
        Targetable target = getTarget();
        if (target == null) {
            gotBamboo = false;
            didBite = false;
        }
        if (getUnhappyCounter() > 0) {
            if (target != null) faceTarget(target, 90.0F, 90.0F);
            if (getUnhappyCounter() == 29 || getUnhappyCounter() == 14) playSound("entity.panda.cant_breed", 1.0F, 1.0F);
            setUnhappyCounter(getUnhappyCounter() - 1);
        }
        if (isSneezing()) {
            setSneezeCounter(getSneezeCounter() + 1);
            if (getSneezeCounter() > 20) {
                sneeze(false);
                afterSneeze();
            } else if (getSneezeCounter() == 1) {
                playSound("entity.panda.pre_sneeze", 1.0F, 1.0F);
            }
        }
        if (isRolling()) tickRoll();
        else rollCounter = 0;
        if (isSitting()) setXRot(0.0F);
        handleEating();
        updateSitAmount();
        updateOnBackAnimation();
        updateRollAmount();
    }

    private void handleEating() {
        if (!isEating() && isSitting() && !isScared() && !getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && random.nextInt(80) == 1) eat(true);
        else if (getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() || !isSitting()) eat(false);
        if (isEating()) {
            addEatingParticles();
            if (getEatCounter() > 80 && random.nextInt(20) == 1) {
                if (getEatCounter() > 100 && isFoodOrCake(getItemBySlot(EquipmentSlot.MAINHAND))) {
                    setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.EAT);   // cft:343
                    sit(false);
                }
                eat(false);
                return;
            }
            setEatCounter(getEatCounter() + 1);
        }
    }

    private void addEatingParticles() {
        if (getEatCounter() % 5 == 0) {
            playSound("entity.panda.eat", 0.5F + 0.5F * random.nextInt(2), (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
            for (int i = 0; i < 6; i++) {
                for (int k = 0; k < 5; k++) random.nextFloat();   // 입자 속도/위치 난수(Math.random 한 번은 엔티티 난수가 아니다)
            }
        }
    }

    private void updateSitAmount() {
        sitAmountO = sitAmount;
        sitAmount = isSitting() ? Math.min(1.0F, sitAmount + 0.15F) : Math.max(0.0F, sitAmount - 0.19F);
    }

    private void updateOnBackAnimation() {
        onBackAmountO = onBackAmount;
        onBackAmount = isOnBack() ? Math.min(1.0F, onBackAmount + 0.15F) : Math.max(0.0F, onBackAmount - 0.19F);
    }

    private void updateRollAmount() {
        rollAmountO = rollAmount;
        rollAmount = isRolling() ? Math.min(1.0F, rollAmount + 0.15F) : Math.max(0.0F, rollAmount - 0.19F);
    }

    private void tickRoll() {
        rollCounter++;
        if (rollCounter > 32) {
            roll(false);
        } else {
            double mx = vx(), my = vy(), mz = vz();
            if (rollCounter == 1) {
                float yaw = yRot() * (float) (Math.PI / 180.0);
                float f = isBaby() ? 0.1F : 0.2F;
                rollX = mx + -Mth.sin(yaw) * f;
                rollZ = mz + Mth.cos(yaw) * f;
                setDeltaMovement(rollX, 0.27, rollZ);
            } else if (rollCounter != 7.0F && rollCounter != 15.0F && rollCounter != 23.0F) {
                setDeltaMovement(rollX, my, rollZ);
            } else {
                setDeltaMovement(0.0, isInWater() ? 0.27 : my, 0.0);
            }
        }
    }

    private void afterSneeze() {
        playSound("entity.panda.sneeze", 1.0F, 1.0F);
        for (Panda p : level.getEntitiesOfClass(Panda.class, boundingBox().inflate(10.0), x -> true)) {
            if (!p.isBaby() && p.onGround() && !p.isInWater() && p.canPerformAction()) p.jumpFromGround();
        }
        if (level.gameRule("doMobLoot")) {
            for (ItemStack s : level.entityDrops("minecraft:gameplay/panda_sneeze", lootEntity(), x(), y(), z(), "generic", null, null, 0L)) spawnAtLocation(s);
        }
    }

    public boolean isFoodOrCake(ItemStack s) { return isFood(s) || ItemTools.itemName(s).equals("minecraft:cake"); }

    @Override
    public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:panda_food"); }

    @Override
    protected void pickUpItem(ItemEntity ie) {
        ItemStack stack = ie.getItem();
        if (getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && isPandaItem(ie)) {
            onItemPickup(ie);
            setItemSlotAndDropWhenKilled(EquipmentSlot.MAINHAND, stack);
            ie.broadcastTake(this, stack.count());
            ie.discard();
        }
    }

    static boolean isPandaItem(ItemEntity ie) {
        ItemStack s = ie.getItem();
        String n = ItemTools.itemName(s);
        return (n.equals("minecraft:bamboo") || n.equals("minecraft:cake")) && ie.isAlive() && !ie.hasPickUpDelay();
    }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        sit(false);
        return super.hurtBy(source, amount, attacker);
    }

    void tryToSit() {
        if (!isInWater()) {
            setZza(0.0F);
            navigation.stop();
            sit(true);
        }
    }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        if (isScared()) return false;
        if (isOnBack()) {
            setOnBack(false);
            return true;
        }
        if (isFood(held)) {
            if (getTarget() != null) gotBamboo = true;
            if (isBaby()) {
                usePlayerItem(p, hand, held);
                ageUp((int) (-getAge() / 20 * 0.1F), true);
            } else if (getAge() == 0 && canFallInLove()) {
                usePlayerItem(p, hand, held);
                setInLove(p);
            } else {
                if (isSitting() || isInWater()) return false;
                tryToSit();
                eat(true);
                ItemStack inHand = getItemBySlot(EquipmentSlot.MAINHAND);
                if (!inHand.isEmpty() && !p.vitals().isCreative()) spawnAtLocation(inHand);
                setItemSlot(EquipmentSlot.MAINHAND, held.withCount(1));
                usePlayerItem(p, hand, held);
            }
            return true;
        }
        return false;
    }

    @Override
    public String getAmbientSound() {
        if (isAggressive()) return "entity.panda.aggressive_ambient";
        return isWorried() ? "entity.panda.worried_ambient" : "entity.panda.ambient";
    }

    @Override protected String getDeathSound() { return "entity.panda.death"; }

    @Override protected String getHurtSound(Damage source) { return "entity.panda.hurt"; }

    // ------------------------------------------------------------------ 목표

    static class PandaPanicGoal extends PanicGoal {
        private final Panda panda;

        PandaPanicGoal(Panda panda, double speed) {
            super(panda, speed, "minecraft:panic_environmental_causes");
            this.panda = panda;
        }

        @Override
        public boolean canContinueToUse() {
            if (panda.isSitting()) {
                panda.getNavigation().stop();
                return false;
            }
            return super.canContinueToUse();
        }
    }

    static class PandaBreedGoal extends BreedGoal {
        private final Panda panda;
        private int unhappyCooldown;

        PandaBreedGoal(Panda panda, double speed) {
            super(panda, speed);
            this.panda = panda;
        }

        @Override
        public boolean canUse() {
            if (!super.canUse() || panda.getUnhappyCounter() != 0) return false;
            if (!canFindBamboo()) {
                if (unhappyCooldown <= panda.tickCount) {
                    panda.setUnhappyCounter(32);
                    unhappyCooldown = panda.tickCount + 600;
                    if (panda.isEffectiveAi()) {
                        Player p = panda.level().getNearestPlayer(panda, 8.0);
                        panda.lookAtPlayerGoal.setTarget(p);
                    }
                }
                return false;
            }
            return true;
        }

        private boolean canFindBamboo() {
            Pos pos = panda.blockPosition();
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 8; j++) {
                    for (int k = 0; k <= j; k = k > 0 ? -k : 1 - k) {
                        for (int l = k < j && k > -j ? j : 0; l <= j; l = l > 0 ? -l : 1 - l) {
                            if (B.is(panda.level().getBlockState(pos.offset(k, i, l)), "minecraft:bamboo")) return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    static class PandaAttackGoal extends MeleeAttackGoal {
        private final Panda panda;

        PandaAttackGoal(Panda panda, double speed, boolean followEvenIfNotSeen) {
            super(panda, speed, followEvenIfNotSeen);
            this.panda = panda;
        }

        @Override public boolean canUse() { return panda.canPerformAction() && super.canUse(); }
    }

    static class PandaAvoidGoal extends AvoidEntityGoal {
        private final Panda panda;

        PandaAvoidGoal(Panda panda, Class<? extends Targetable> cls, float maxDist, double walk, double sprint) {
            super(panda, cls, t -> true, maxDist, walk, sprint, t -> !(t instanceof Entity e) || !e.isSpectator());
            this.panda = panda;
        }

        @Override public boolean canUse() { return panda.isWorried() && panda.canPerformAction() && super.canUse(); }
    }

    class PandaSitGoal extends Goal {
        PandaSitGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            if (sitCooldown <= tickCount && !isBaby() && !isInWater() && canPerformAction() && getUnhappyCounter() <= 0) {
                List<ItemEntity> list = level.getEntitiesOfClass(ItemEntity.class, boundingBox().inflate(6.0), Panda::isPandaItem);
                return !list.isEmpty() || !getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return !isInWater() && (isLazy() || random.nextInt(reducedTickDelay(600)) != 1) ? random.nextInt(reducedTickDelay(2000)) != 1 : false;
        }

        @Override
        public void tick() {
            if (!isSitting() && !getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) tryToSit();
        }

        @Override
        public void start() {
            List<ItemEntity> list = level.getEntitiesOfClass(ItemEntity.class, boundingBox().inflate(8.0), Panda::isPandaItem);
            if (!list.isEmpty() && getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) navigation.moveTo(list.get(0).x(), list.get(0).y(), list.get(0).z(), 1.2F);
            else if (!getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) tryToSit();
            sitCooldown = 0;
        }

        @Override
        public void stop() {
            ItemStack s = getItemBySlot(EquipmentSlot.MAINHAND);
            if (!s.isEmpty()) {
                spawnAtLocation(s);
                setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                int i = isLazy() ? random.nextInt(50) + 10 : random.nextInt(150) + 10;
                sitCooldown = tickCount + i * 20;
            }
            sit(false);
        }
    }

    class PandaLieOnBackGoal extends Goal {
        @Override
        public boolean canUse() { return lieOnBackCooldown < tickCount && isLazy() && canPerformAction() && random.nextInt(reducedTickDelay(400)) == 1; }

        @Override
        public boolean canContinueToUse() {
            return !isInWater() && (isLazy() || random.nextInt(reducedTickDelay(600)) != 1) ? random.nextInt(reducedTickDelay(2000)) != 1 : false;
        }

        @Override
        public void start() {
            setOnBack(true);
            lieOnBackCooldown = 0;
        }

        @Override
        public void stop() {
            setOnBack(false);
            lieOnBackCooldown = tickCount + 200;
        }
    }

    static class PandaLookAtPlayerGoal extends LookAtPlayerGoal {
        private final Panda panda;

        PandaLookAtPlayerGoal(Panda panda, float dist) {
            super(panda, dist);
            this.panda = panda;
        }

        void setTarget(Player p) { lookAt = p; }

        @Override public boolean canContinueToUse() { return lookAt != null && super.canContinueToUse(); }

        @Override
        public boolean canUse() {
            if (panda.random().nextFloat() >= probability) return false;
            if (lookAt == null) lookAt = panda.level().getNearestPlayer(panda, lookDistance);
            return panda.canPerformAction() && lookAt != null;
        }

        @Override
        public void tick() {
            if (lookAt != null) super.tick();
        }
    }

    static class PandaHurtByTargetGoal extends HurtByTargetGoal {
        private final Panda panda;

        PandaHurtByTargetGoal(Panda panda) {
            super(panda);
            this.panda = panda;
        }

        @Override
        public boolean canContinueToUse() {
            if (panda.gotBamboo || panda.didBite) {
                panda.setTarget(null);
                return false;
            }
            return super.canContinueToUse();
        }

        @Override
        protected void alertOther(Mob other, Targetable target) {
            if (other instanceof Panda && other.isAggressive()) other.setTarget(target);
        }
    }

    class PandaRollGoal extends Goal {
        PandaRollGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }

        @Override
        public boolean canUse() {
            if ((isBaby() || isPlayful()) && onGround()) {
                if (!canPerformAction()) return false;
                float yaw = yRot() * (float) (Math.PI / 180.0);
                float dx = -Mth.sin(yaw), dz = Mth.cos(yaw);
                int i = Math.abs(dx) > 0.5 ? (int) Math.signum((double) dx) : 0;
                int j = Math.abs(dz) > 0.5 ? (int) Math.signum((double) dz) : 0;
                if (B.isAir(level.getBlockState(blockPosition().offset(i, -1, j)))) return true;
                return isPlayful() && random.nextInt(reducedTickDelay(60)) == 1 ? true : random.nextInt(reducedTickDelay(500)) == 1;
            }
            return false;
        }

        @Override public boolean canContinueToUse() { return false; }

        @Override public void start() { roll(true); }

        @Override public boolean isInterruptable() { return false; }
    }

    class PandaSneezeGoal extends Goal {
        @Override
        public boolean canUse() {
            if (isBaby() && canPerformAction()) {
                return isWeak() && random.nextInt(reducedTickDelay(500)) == 1 ? true : random.nextInt(reducedTickDelay(6000)) == 1;
            }
            return false;
        }

        @Override public boolean canContinueToUse() { return false; }

        @Override public void start() { sneeze(true); }
    }

    /** Panda.playStepSound: panda.step 크기 0.15(블록 소리를 쓰지 않는다). */
    @Override
    protected void playStepSound(org.domi.furnace.server.block.Pos pos, int state) { playSound("minecraft:entity.panda.step", 0.15F, 1.0F); }
}
