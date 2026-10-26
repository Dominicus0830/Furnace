// 바닐라 Mob: 몹 플래그(NoAI·왼손잡이·공격 중)와 영속(PersistenceRequired). AI·길찾기는 아직 없다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.entity.ai.GoalSelector;
import org.domi.furnace.server.entity.ai.control.BodyRotationControl;
import org.domi.furnace.server.entity.ai.control.JumpControl;
import org.domi.furnace.server.entity.ai.control.LookControl;
import org.domi.furnace.server.entity.ai.control.MoveControl;
import org.domi.furnace.server.entity.ai.navigation.GroundPathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract class Mob extends LivingEntity {

    /** Mob.DATA_MOB_FLAGS_ID = 15: 1 NoAI, 2 왼손잡이, 4 공격 중. */
    public static final int DATA_MOB_FLAGS = 15;

    protected boolean persistenceRequired;
    /** xpReward: 종류별 기본 경험치(적대 몹 5, 동물은 별도 규칙, 주민 0). */
    protected int xpReward;
    protected final GoalSelector goalSelector = new GoalSelector();
    protected final GoalSelector targetSelector = new GoalSelector();
    protected LookControl lookControl;
    protected MoveControl moveControl;
    protected JumpControl jumpControl;
    protected BodyRotationControl bodyRotationControl;
    protected PathNavigation navigation;
    /** 브레인을 쓰는 몹이 생성자에서 채운다. 없으면 null. */
    protected org.domi.furnace.server.entity.ai.brain.Brain<? extends Mob> brain;
    private final java.util.EnumMap<PathType, Float> pathfindingMalus = new java.util.EnumMap<>(PathType.class);

    protected Mob(EntityType type, ChunkLevel level, int id) {
        super(type, level, id);
        lookControl = new LookControl(this);
        moveControl = new MoveControl(this);
        jumpControl = new JumpControl(this);
        bodyRotationControl = new BodyRotationControl(this);
        navigation = createNavigation(level);
        registerGoals();
    }

    /** 종류별 목표 등록(vanilla registerGoals). 하위 클래스 필드는 아직 초기화 전이므로 쓰지 말 것. */
    protected void registerGoals() {}

    @Override
    protected void createAttributes(org.domi.furnace.server.attribute.AttributeMap map) {   // Mob.createMobAttributes
        super.createAttributes(map);
        map.add(Attribute.FOLLOW_RANGE, 16.0);
    }

    protected PathNavigation createNavigation(ChunkLevel level) { return new GroundPathNavigation(this, level); }

    public PathNavigation getNavigation() { return navigation; }

    public org.domi.furnace.server.entity.ai.brain.Brain<? extends Mob> getBrain() { return brain; }

    /** Mob.onPathfindingStart / onPathfindingDone: 길찾기 앞뒤 훅(꿀벌 등이 비용을 바꾼다). */
    public void onPathfindingStart() {}

    public void onPathfindingDone() {}

    public float getPathfindingMalus(PathType type) {
        Float f = pathfindingMalus.get(type);
        return f == null ? type.malus() : f;
    }

    public void setPathfindingMalus(PathType type, float malus) { pathfindingMalus.put(type, malus); }

    /** 서 있는 칸으로 칠 수 있는 유체인가(canStandOnFluid) — 기본 아니오. */
    public boolean canStandOnFluid(int fluid) { return false; }

    // ------------------------------------------------------------------ 장비

    public static final float DEFAULT_EQUIPMENT_DROP_CHANCE = 0.085F;
    private final ItemStack[] handItems = {ItemStack.EMPTY, ItemStack.EMPTY};
    private final ItemStack[] armorItems = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private ItemStack bodyArmorItem = ItemStack.EMPTY;
    private final float[] handDropChances = {DEFAULT_EQUIPMENT_DROP_CHANCE, DEFAULT_EQUIPMENT_DROP_CHANCE};
    private final float[] armorDropChances = {DEFAULT_EQUIPMENT_DROP_CHANCE, DEFAULT_EQUIPMENT_DROP_CHANCE, DEFAULT_EQUIPMENT_DROP_CHANCE, DEFAULT_EQUIPMENT_DROP_CHANCE};
    private float bodyArmorDropChance = DEFAULT_EQUIPMENT_DROP_CHANCE;
    private boolean canPickUpLoot;

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot.type()) {
            case HAND -> handItems[slot.index()];
            case HUMANOID_ARMOR -> armorItems[slot.index()];
            case ANIMAL_ARMOR -> bodyArmorItem;
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        switch (slot.type()) {
            case HAND -> handItems[slot.index()] = stack;
            case HUMANOID_ARMOR -> armorItems[slot.index()] = stack;
            case ANIMAL_ARMOR -> bodyArmorItem = stack;
        }
    }

    public float getEquipmentDropChance(EquipmentSlot slot) {
        return switch (slot.type()) {
            case HAND -> handDropChances[slot.index()];
            case HUMANOID_ARMOR -> armorDropChances[slot.index()];
            case ANIMAL_ARMOR -> bodyArmorDropChance;
        };
    }

    public void setDropChance(EquipmentSlot slot, float chance) {
        switch (slot.type()) {
            case HAND -> handDropChances[slot.index()] = chance;
            case HUMANOID_ARMOR -> armorDropChances[slot.index()] = chance;
            case ANIMAL_ARMOR -> bodyArmorDropChance = chance;
        }
    }

    public boolean canPickUpLoot() { return canPickUpLoot; }

    public void setCanPickUpLoot(boolean v) { canPickUpLoot = v; }

    /**
     * Mob.dropCustomDeathLoot → 장비 드롭: 칸마다 드롭 확률(0 이면 안 떨굼, 1 초과면 반드시)로, 플레이어가 잡았거나 확률이 1 초과일 때 떨군다.
     * 닳는 아이템은 내구도가 무작위로 더 닳는다. TODO(약탈 인챈트로 확률 보정, 장비 떨굼 방지 인챈트).
     */
    @Override
    protected void dropCustomDeathLoot(Damage source, boolean byPlayer) {
        super.dropCustomDeathLoot(source, byPlayer);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = getItemBySlot(slot);
            float chance = getEquipmentDropChance(slot);
            if (chance == 0.0F) continue;
            boolean preserve = chance > 1.0F;
            if (!stack.isEmpty() && (byPlayer || preserve) && random.nextFloat() < chance) {
                if (!preserve && org.domi.furnace.server.item.ItemTools.isDamageable(stack)) {
                    int max = org.domi.furnace.server.item.ItemTools.maxDamage(stack);
                    stack = org.domi.furnace.server.item.ItemTools.withDamage(stack, max - random.nextInt(1 + random.nextInt(Math.max(max - 3, 1))));
                }
                spawnAtLocation(stack);
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    /** Mob.getExperienceReward: xpReward 가 있으면 장비(드롭 확률 1 이하)마다 1+nextInt(3) 추가(방어구 → 손 → 몸 순). */
    @Override
    protected int getExperienceReward() {
        if (xpReward > 0) {
            int i = xpReward;
            for (int k = 0; k < armorItems.length; k++) if (!armorItems[k].isEmpty() && armorDropChances[k] <= 1.0F) i += 1 + random.nextInt(3);
            for (int k = 0; k < handItems.length; k++) if (!handItems[k].isEmpty() && handDropChances[k] <= 1.0F) i += 1 + random.nextInt(3);
            if (!bodyArmorItem.isEmpty() && bodyArmorDropChance <= 1.0F) i += 1 + random.nextInt(3);
            return i;
        }
        return xpReward;
    }

    // ------------------------------------------------------------------ 생성(finalizeSpawn)

    private static final String RANDOM_SPAWN_BONUS_ID = "minecraft:random_spawn_bonus";

    /** Mob.mobInteract: 플레이어가 우클릭했을 때. 처리했으면 true. */
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) { return false; }

    /** stack.hurtAndBreak(amount, player, hand): 손에 든 도구의 내구도를 깎는다(다 닳으면 부서진다). */
    protected void hurtHeldItem(Player p, int hand, ItemStack held, int amount) {
        var inv = p.inventory();
        int ws = hand == 0 ? inv.heldWindowSlot() : 45;
        var actor = new org.domi.furnace.server.block.PlayerActor(p, hand == 0 ? held : ItemStack.EMPTY, hand == 1 ? held : ItemStack.EMPTY, 0);
        org.domi.furnace.server.block.BlockBreaking.hurtAndBreak(level, actor, hand, held, amount);
        if (actor.handChanged(hand)) inv.set(ws, actor.itemInHand(hand));
    }

    /** stack.hurtAndBreak(amount, mob, slot): 몹이 든 도구의 내구도를 깎는다(내구성은 레벨 난수, 다 닳으면 하나 줄고 부서짐). */
    protected void hurtEquipped(EquipmentSlot slot, int amount) {
        ItemStack stack = getItemBySlot(slot);
        if (!ItemTools.isDamageable(stack)) return;
        if (amount > 0) {
            int unbreaking = ItemTools.enchantmentLevel(stack, "minecraft:unbreaking");
            if (unbreaking > 0) {
                boolean armor = org.domi.furnace.server.item.ItemContext.isInTag("minecraft:item", "minecraft:enchantable/armor", stack.itemId());
                float chance = armor ? (2.0F + 2.0F * (unbreaking - 1)) / (10.0F + 5.0F * (unbreaking - 1)) : (float) unbreaking / (2.0F + (unbreaking - 1));
                int removed = 0;
                for (int j = 0; j < amount; j++) if (level.random().nextFloat() < chance) removed++;
                amount -= removed;
            }
            if (amount <= 0) return;
        }
        int newDamage = ItemTools.damage(stack) + amount;
        ItemStack damaged = ItemTools.withDamage(stack, newDamage);
        if (newDamage >= ItemTools.maxDamage(stack)) setItemSlot(slot, damaged.withCount(damaged.count() - 1));
        else setItemSlot(slot, damaged);
    }

    /** usePlayerItem(player, hand, stack): 크리에이티브가 아니면 손에 든 것 하나를 소비한다(stack.consume(1, player)). */
    protected void usePlayerItem(Player p, int hand, ItemStack held) {
        if (p.vitals().isCreative()) return;
        var inv = p.inventory();
        int ws = hand == 0 ? inv.heldWindowSlot() : 45;
        inv.set(ws, held.withCount(held.count() - 1));
    }

    /** Entity.getFluidJumpThreshold: 눈높이가 0.4 미만이면 0, 아니면 0.4. */
    public double getFluidJumpThreshold() { return dimEye < 0.4 ? 0.0 : 0.4; }

    /** Mob.convertTo(type, keepEquipment): 이 몹 자리에 대신 쓸 새 몹(target)을 위치·아기·무AI·유지 설정을 넘겨 추가하고 자신은 지운다. */
    public <T extends Mob> T convertTo(T target, boolean keepEquipment) {
        if (isRemoved() || target == null) return null;
        target.moveTo(x(), y(), z(), yRot(), xRot());
        if (isBaby()) target.setBabyForConversion(true);
        target.setNoAi(isNoAi());
        if (persistenceRequired) target.persistenceRequired = true;
        if (keepEquipment) {
            target.setCanPickUpLoot(canPickUpLoot());
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack item = getItemBySlot(slot);
                if (!item.isEmpty()) {
                    target.setItemSlot(slot, item);
                    setItemSlot(slot, ItemStack.EMPTY);
                    target.setDropChance(slot, getEquipmentDropChance(slot));
                }
            }
        }
        level.addFreshEntity(target);
        discard();
        return target;
    }

    /** Entity.killedEntity: 이 몹이 죽였을 때(false 면 죽은 쪽의 드롭을 막는다). */
    public boolean killedEntity(LivingEntity victim) { return true; }

    /** 변환된 몹을 아기로 만든다(AgeableMob·Zombie 가 덮어쓴다). */
    protected void setBabyForConversion(boolean baby) {}

    /** Mob.isHolding(item): 두 손 중 하나에 그 아이템이 있다. */
    public boolean isHolding(String itemName) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack s = getItemBySlot(slot);
            if (!s.isEmpty() && org.domi.furnace.server.item.ItemTools.itemName(s).equals(itemName)) return true;
        }
        return false;
    }

    /** Mob.lookAt(target, yMax, xMax): 몸을 대상으로 돌린다(몹 시선 제어와는 별도로 yRot/xRot 직접). */
    public void faceTarget(Targetable t, float maxYRotIncrease, float maxXRotIncrease) {
        double dx = t.x() - x(), dz = t.z() - z();
        double dy = t.eyeY() - eyeY();
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (org.domi.furnace.server.entity.ai.control.Mth.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F;
        float pitch = (float) (-(org.domi.furnace.server.entity.ai.control.Mth.atan2(dy, h) * 180.0F / (float) Math.PI));
        setXRot(rotateTo(xRot(), pitch, maxXRotIncrease));
        setYRot(rotateTo(yRot(), yaw, maxYRotIncrease));
    }

    private static float rotateTo(float cur, float tgt, float max) {
        float diff = org.domi.furnace.server.entity.ai.control.Mth.wrapDegrees(tgt - cur);
        if (diff > max) diff = max;
        if (diff < -max) diff = -max;
        return cur + diff;
    }

    /** ProjectileUtil.getWeaponHoldingHand: 주 손에 있으면 0, 아니면 1. */
    public int getWeaponHoldingHand(String itemName) {
        ItemStack s = getItemBySlot(EquipmentSlot.MAINHAND);
        return !s.isEmpty() && org.domi.furnace.server.item.ItemTools.itemName(s).equals(itemName) ? 0 : 1;
    }

    /** Mob.spawnAnim: 나타날 때 연기 입자(엔티티 이벤트 20). */
    public void spawnAnim() { broadcastEntityEvent((byte) 20); }

    // ------------------------------------------------------------------ 아이템 줍기

    /** Mob.aiStep: 줍기 가능한 몹은 주변(1, 0, 1)의 아이템 엔티티를 장비로 주워 든다. */
    @Override
    protected void aiStep() {
        super.aiStep();
        if (canPickUpLoot() && isAlive() && level.gameRule("mobGriefing")) {
            for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, boundingBox().inflate(1.0, 0.0, 1.0), x -> true)) {
                if (!ie.isRemoved() && !ie.getItem().isEmpty() && !ie.hasPickUpDelay() && wantsToPickUp(ie.getItem())) pickUpItem(ie);
            }
        }
    }

    public boolean canHoldItem(ItemStack stack) { return true; }

    /** BehaviorUtils.isHoldingUsableProjectileWeapon: 손에 든 발사 무기를 이 몹이 쓸 수 있다(석궁 든 피글린 등). */
    public boolean isHoldingUsableProjectileWeapon() { return false; }

    public boolean wantsToPickUp(ItemStack stack) { return canHoldItem(stack); }

    /** Mob.getEquipmentSlotForItem: 이름으로 방어구 칸을 고르고 나머지는 주 손. */
    public static EquipmentSlot equipmentSlotFor(ItemStack stack) {
        String n = org.domi.furnace.server.item.ItemTools.itemName(stack);
        if (n.endsWith("_helmet") || n.equals("minecraft:carved_pumpkin") || n.endsWith("_head") || n.endsWith("_skull")) return EquipmentSlot.HEAD;
        if (n.endsWith("_chestplate") || n.equals("minecraft:elytra")) return EquipmentSlot.CHEST;
        if (n.endsWith("_leggings")) return EquipmentSlot.LEGS;
        if (n.endsWith("_boots")) return EquipmentSlot.FEET;
        if (n.equals("minecraft:shield")) return EquipmentSlot.OFFHAND;
        return EquipmentSlot.MAINHAND;
    }

    /** canReplaceCurrentItem 의 단순형: 칸이 비어 있을 때만 바꾼다. TODO(점수): 칼·활·방어구의 공격력·방어력 비교. */
    protected boolean canReplaceCurrentItem(ItemStack candidate, ItemStack existing) { return existing.isEmpty(); }

    /** Mob.equipItemIfPossible: 알맞은 칸에 장비로 든다(방어구 칸이 안 되면 주 손). 든 만큼을 돌려준다. */
    public ItemStack equipItemIfPossible(ItemStack stack) {
        EquipmentSlot slot = equipmentSlotFor(stack);
        ItemStack current = getItemBySlot(slot);
        boolean flag = canReplaceCurrentItem(stack, current);
        if (slot.type() == EquipmentSlot.Type.HUMANOID_ARMOR && !flag) {
            slot = EquipmentSlot.MAINHAND;
            current = getItemBySlot(slot);
            flag = current.isEmpty();
        }
        if (flag && canHoldItem(stack)) {
            double d = getEquipmentDropChance(slot);
            if (!current.isEmpty() && Math.max(random.nextFloat() - 0.1F, 0.0F) < d) spawnAtLocation(current);
            if (slot.type() == EquipmentSlot.Type.HUMANOID_ARMOR && stack.count() > 1) {
                ItemStack one = stack.withCount(1);
                setItemSlotAndDropWhenKilled(slot, one);
                return one;
            }
            setItemSlotAndDropWhenKilled(slot, stack);
            return stack;
        }
        return ItemStack.EMPTY;
    }

    /** setItemSlotAndDropWhenKilled: 장비로 들고, 죽으면 확실히 떨구며(드롭 확률 2.0 → 줍는 것은 무조건) 사라지지 않게 한다. */
    public void setItemSlotAndDropWhenKilled(EquipmentSlot slot, ItemStack stack) {
        setItemSlot(slot, stack);
        setDropChance(slot, 2.0F);
        persistenceRequired = true;
    }

    protected void pickUpItem(ItemEntity ie) {
        ItemStack stack = ie.getItem();
        ItemStack equipped = equipItemIfPossible(stack);
        if (!equipped.isEmpty()) {
            onItemPickup(ie);
            ie.broadcastTake(this, equipped.count());
            ItemStack left = stack.withCount(stack.count() - equipped.count());
            if (left.isEmpty()) ie.discard();
            else ie.setItem(left);
        }
    }

    public void onItemPickup(ItemEntity ie) {}

    /** Mob.removeWhenFarAway: 멀리 있으면 사라져도 되나(기본 참, 동물은 거짓). */
    public boolean removeWhenFarAway(double distSqr) { return true; }

    public boolean isPersistenceRequired() { return persistenceRequired; }

    /** 몹 수 세기에서 빠지는가(유지 몹). */
    public boolean isPersistentForCaps() { return persistenceRequired || requiresCustomPersistence(); }

    /** Mob.requiresCustomPersistence: 탑승·끈에 매인 몹 등(아직 해당 체계 없음). */
    protected boolean requiresCustomPersistence() { return false; }

    /** Mob.shouldDespawnInPeaceful: 적대 몹은 평화로움에서 사라진다. */
    protected boolean shouldDespawnInPeaceful() { return this instanceof Enemy; }

    /** Mob.checkDespawn: 평화로움이면 적대 몹 제거, 아니면 가장 가까운 플레이어와의 거리로 사라질지 정한다(128 넘으면 즉시, 32 넘고 600틱 무행동이면 1/800). */
    public void checkDespawn() {
        if (level.difficulty() == 0 && shouldDespawnInPeaceful()) {
            discard();
        } else if (!isPersistenceRequired() && !requiresCustomPersistence()) {
            Player nearest = level.getNearestPlayer(this, -1.0);
            if (nearest != null) {
                double dx = nearest.x() - x(), dy = nearest.y() - y(), dz = nearest.z() - z();
                double d = dx * dx + dy * dy + dz * dz;
                int far = org.domi.furnace.server.entity.spawn.EntityMeta.category(type.name()).despawnDistance();
                if (d > (double) far * far && removeWhenFarAway(d)) discard();
                int near = org.domi.furnace.server.entity.spawn.MobCategory.NO_DESPAWN_DISTANCE;
                if (noActionTime > 600 && random.nextInt(800) == 0 && d > (double) near * near && removeWhenFarAway(d)) discard();
                else if (d < (double) near * near) noActionTime = 0;
            }
        } else {
            noActionTime = 0;
        }
    }

    @Override
    public void tick() {
        if (!isRemoved()) checkDespawn();
        super.tick();
    }

    // ---- 자연 스폰 훅(NaturalSpawner)

    /** Mob.checkSpawnRules(level, reason): 이 몹 개체의 추가 검사(기본 참). */
    public boolean checkSpawnRules(ChunkLevel level, org.domi.furnace.server.entity.spawn.SpawnReason reason) { return true; }

    /** Mob.checkSpawnObstruction: 몸이 액체 안이 아니고 플레이어와 겹치지 않는다. */
    public boolean checkSpawnObstruction(ChunkLevel level) { return !level.containsAnyLiquid(boundingBox()) && level.isUnobstructed(org.domi.furnace.server.entity.spawn.NaturalSpawner.shapeOf(boundingBox())); }

    /** Mob.getMaxSpawnClusterSize: 한 번에 자연 스폰되는 최대 수. */
    public int getMaxSpawnClusterSize() { return 4; }

    /** Mob.isMaxGroupSizeReached: 이 수만큼 한 무리로 났으면 무리를 끊는다. */
    public boolean isMaxGroupSizeReached(int size) { return false; }

    /** 무리 데이터를 이어 주는 finalizeSpawn(AgeableMob·떼 물고기가 덮어쓴다). */
    public Object finalizeSpawnGroup(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty, Object group) {
        finalizeSpawn(r, difficulty);
        return group;
    }

    /** Entity.getLightLevelDependentMagicValue: 눈 높이 칸의 밝기(청크가 없으면 0). 오버월드는 ambientLight 0 → 곡선 f/(4-3f). */
    public float getLightLevelDependentMagicValue() {
        Pos pos = new Pos((int) Math.floor(x()), (int) Math.floor(eyeY()), (int) Math.floor(z()));
        int light = level.getMaxLocalRawBrightness(pos);
        float f = light / 15.0F;
        return f / (4.0F - 3.0F * f);
    }

    /** Mob.isSunBurnTick: 낮에 하늘이 뚫린 곳에서 비·물에 젖지 않았을 때 가끔 참. */
    protected boolean isSunBurnTick() {
        if (!level.isDay()) return false;
        float f = getLightLevelDependentMagicValue();
        Pos pos = new Pos((int) Math.floor(x()), (int) Math.floor(eyeY()), (int) Math.floor(z()));
        boolean wet = isInWaterRainOrBubble() || isInPowderSnow();
        return f > 0.5F && random.nextFloat() * 30.0F < (f - 0.4F) * 2.0F && !wet && level.canSeeSky(pos);
    }

    public boolean isLeftHanded() { return ((Byte) entityData.get(DATA_MOB_FLAGS) & 2) != 0; }

    public void setLeftHanded(boolean v) {
        byte b = (Byte) entityData.get(DATA_MOB_FLAGS);
        entityData.set(DATA_MOB_FLAGS, (byte) (v ? b | 2 : b & ~2));
    }

    /** 스폰 직후 주변 소리를 내기 위한 접근자(EntityType.spawn 의 playAmbientSound). */
    public String ambientSoundForSpawn() { return getAmbientSound(); }

    /** Mob.finalizeSpawn: 따라가기 범위에 영구 무작위 보너스(삼각분포)와 왼손잡이(5%). 레벨 난수를 쓴다. */
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource levelRandom, DifficultyInstance difficulty) {
        var follow = attributes().get(Attribute.FOLLOW_RANGE);
        if (!follow.hasModifier(RANDOM_SPAWN_BONUS_ID)) {
            follow.addOrReplaceModifier(new org.domi.furnace.server.attribute.AttributeModifier(RANDOM_SPAWN_BONUS_ID,
                    levelRandom.triangle(0.0, 0.11485000000000001), org.domi.furnace.server.attribute.AttributeModifier.ADD_MULTIPLIED_BASE));
        }
        setLeftHanded(levelRandom.nextFloat() < 0.05F);
    }

    /** Mob.getEquipmentForSlot(slot, tier): 방어구 재질 등급 0 가죽 · 1 금 · 2 사슬 · 3 철 · 4 다이아몬드. */
    public static String getEquipmentForSlot(EquipmentSlot slot, int tier) {
        String piece = switch (slot) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            case FEET -> "boots";
            default -> null;
        };
        if (piece == null || tier < 0 || tier > 4) return null;
        return "minecraft:" + new String[] {"leather", "golden", "chainmail", "iron", "diamond"}[tier] + "_" + piece;
    }

    protected static ItemStack stackOf(String item) {
        Integer id = org.domi.furnace.server.item.ItemContext.id("minecraft:item", item.replace("minecraft:", ""));
        return id == null ? ItemStack.EMPTY : new ItemStack(id, 1);
    }

    /**
     * Mob.populateDefaultEquipmentSlots: 0.15 × 특수 배율 확률로 방어구 등급을 정하고(최대 +3 단계, 확률 0.095씩),
     * 발 → 다리 → 가슴 → 머리 순으로 채운다(난이도 어려움 0.1, 아니면 0.25 확률로 중간에 멈춤).
     */
    protected void populateDefaultEquipmentSlots(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        if (r.nextFloat() < 0.15F * difficulty.specialMultiplier()) {
            int tier = r.nextInt(2);
            float stop = level.difficulty() == 3 ? 0.1F : 0.25F;
            if (r.nextFloat() < 0.095F) tier++;
            if (r.nextFloat() < 0.095F) tier++;
            if (r.nextFloat() < 0.095F) tier++;
            boolean first = true;
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.type() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
                ItemStack cur = getItemBySlot(slot);
                if (!first && r.nextFloat() < stop) break;
                first = false;
                if (cur.isEmpty()) {
                    String item = getEquipmentForSlot(slot, tier);
                    if (item != null) setItemSlot(slot, stackOf(item));
                }
            }
        }
    }

    /** VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT. */
    public static final String MOB_SPAWN_EQUIPMENT = "minecraft:mob_spawn_equipment";

    /**
     * Mob.populateDefaultEquipmentEnchantments: 손(0.25)·방어구(0.5) × 특수 배율 확률로 마법 부여 제공자 mob_spawn_equipment 를 건다.
     * 난수는 호출자가 넘긴 것(finalizeSpawn 의 레벨 난수)만 쓴다. 데이터 팩이 없으면 확률 굴림만 하고 아무것도 걸지 않는다.
     */
    protected void populateDefaultEquipmentEnchantments(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        enchantSpawnedEquipment(EquipmentSlot.MAINHAND, r, 0.25F, difficulty);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.type() == EquipmentSlot.Type.HUMANOID_ARMOR) enchantSpawnedEquipment(slot, r, 0.5F, difficulty);
        }
    }

    /** Mob.enchantSpawnedEquipment: 확률을 넘기면 EnchantmentHelper.enchantItemFromProvider 로 걸고 그 칸에 다시 놓는다. */
    private void enchantSpawnedEquipment(EquipmentSlot slot, org.domi.furnace.server.block.RandomSource r, float chance, DifficultyInstance difficulty) {
        ItemStack s = getItemBySlot(slot);
        if (!s.isEmpty() && r.nextFloat() < chance * difficulty.specialMultiplier()) {
            setItemSlot(slot, org.domi.furnace.server.item.EnchantTools.enchantFromProvider(s, level.lootData(), MOB_SPAWN_EQUIPMENT,
                    difficulty.specialMultiplier(), r));
        }
    }

    // ------------------------------------------------------------------ 제한 구역(restrictTo)

    private Pos restrictCenter = Pos.ZERO;
    private float restrictRadius = -1.0F;

    public boolean isWithinRestriction(Pos pos) {
        if (restrictRadius == -1.0F) return true;
        long dx = pos.x() - restrictCenter.x(), dy = pos.y() - restrictCenter.y(), dz = pos.z() - restrictCenter.z();
        return (double) (dx * dx + dy * dy + dz * dz) < (double) (restrictRadius * restrictRadius);
    }

    public void restrictTo(Pos center, int radius) {
        restrictCenter = center;
        restrictRadius = radius;
    }

    public Pos getRestrictCenter() { return restrictCenter; }

    public float getRestrictRadius() { return restrictRadius; }

    public void clearRestriction() { restrictRadius = -1.0F; }

    public boolean hasRestriction() { return restrictRadius != -1.0F; }

    // ------------------------------------------------------------------ 대상·전투

    protected Targetable target;
    private final java.util.Set<Integer> seenTargets = new java.util.HashSet<>(), unseenTargets = new java.util.HashSet<>();

    public Targetable getTarget() { return target; }

    /** Mob.getTargetFromBrain: 브레인 몹의 공격 대상은 ATTACK_TARGET 기억이다. */
    protected Targetable getTargetFromBrain() {
        return brain == null ? null : brain.getMemory(org.domi.furnace.server.entity.ai.brain.MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    public void setTarget(Targetable t) { target = t; }

    protected boolean hasTarget() { return target != null; }

    /** canAttackType: 이 몹이 그 종류를 때릴 수 있는가(기본 모두, 가스트 같은 것만 제외). */
    public boolean canAttackType(Targetable t) { return true; }

    public boolean isAggressive() { return ((Byte) entityData.get(DATA_MOB_FLAGS) & 4) != 0; }

    public void setAggressive(boolean v) {
        byte b = (Byte) entityData.get(DATA_MOB_FLAGS);
        entityData.set(DATA_MOB_FLAGS, (byte) (v ? b | 4 : b & ~4));
    }

    /** Sensing.hasLineOfSight: 틱마다 비우는 캐시. */
    public boolean hasLineOfSight(Targetable t) {
        if (seenTargets.contains(t.id())) return true;
        if (unseenTargets.contains(t.id())) return false;
        boolean seen = rayHasLineOfSight(t);
        (seen ? seenTargets : unseenTargets).add(t.id());
        return seen;
    }

    /** 아이템 같은 비생물 엔티티까지의 시선(Mob.hasLineOfSight(Entity), 캐시 없음). */
    public boolean hasLineOfSightToEntity(Entity e) {
        double dx = e.x() - x(), dy = e.eyeY() - eyeY(), dz = e.z() - z();
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) > 128.0) return false;
        return org.domi.furnace.server.block.Clip.clipCollider(level, x(), eyeY(), z(), e.x(), e.eyeY(), e.z()).miss();
    }

    /** LivingEntity.hasLineOfSight: 눈에서 눈까지(128칸 안) 충돌 모양에 안 막힌다. */
    private boolean rayHasLineOfSight(Targetable t) {
        double dx = t.x() - x(), dy = t.eyeY() - eyeY(), dz = t.z() - z();
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) > 128.0) return false;
        return org.domi.furnace.server.block.Clip.clipCollider(level, x(), eyeY(), z(), t.x(), t.eyeY(), t.z()).miss();
    }

    private static final double MELEE_REACH = Math.sqrt(2.04F) - 0.6F;

    /** isWithinMeleeAttackRange: 내 몸통을 가로로 (루트 2.04 − 0.6) 넓힌 상자가 상대 몸통과 겹친다. */
    public boolean isWithinMeleeAttackRange(Targetable t) {
        return boundingBox().inflate(MELEE_REACH, 0.0, MELEE_REACH).intersects(t.boundingBox());
    }

    /** Mob.doHurtTarget: 공격력 속성만큼 때리고, 맞으면 밀치기(attack_knockback)·되받는 감속·마지막으로 때린 대상 기록. TODO(인챈트 효과·무기 후처리). */
    public boolean doHurtTarget(Targetable t) {
        float damage = (float) attributeValue(Attribute.ATTACK_DAMAGE);
        boolean hurt = t.hurtByMob(new Damage("mob_attack", false, false, false), damage, this);
        if (hurt) {
            float kb = (float) attributeValue(Attribute.ATTACK_KNOCKBACK);
            if (kb > 0.0F) {
                t.knockback(kb * 0.5F, org.domi.furnace.server.entity.ai.control.Mth.sin(yRot() * (float) (Math.PI / 180.0)), -org.domi.furnace.server.entity.ai.control.Mth.cos(yRot() * (float) (Math.PI / 180.0)));
                setDeltaMovement(vx() * 0.6, vy(), vz() * 0.6);
            }
            setLastHurtMob(t);
            doPostHurtEffects(t);
        }
        return hurt;
    }

    protected void doPostHurtEffects(Targetable t) {}

    /** Mob.getMaxFallDistance: 대상이 없으면 3, 있으면 체력과 난이도에 따라. */
    public int getMaxFallDistance() {
        if (!hasTarget()) return (int) Math.floor(3.0F);
        int i = (int) (health() - maxHealth() * 0.33F);
        i -= (3 - level.difficulty()) * 4;
        if (i < 0) i = 0;
        return (int) Math.floor(i + 3.0F);
    }

    public boolean isInLiquid() { return isInWaterOrBubble() || isInLava(); }

    public boolean isPassenger() { return false; }

    /** Mob.tickHeadTurn: 몸통 회전은 BodyRotationControl 이 정한다(걸음 거리 인자는 그대로). */
    @Override
    protected void tickHeadTurn(float targetBody) { bodyRotationControl.clientTick(); }

    public LookControl getLookControl() { return lookControl; }

    public MoveControl getMoveControl() { return moveControl; }

    public JumpControl getJumpControl() { return jumpControl; }

    public GoalSelector goalSelector() { return goalSelector; }

    public GoalSelector targetSelector() { return targetSelector; }

    public int getHeadRotSpeed() { return 10; }

    public int getMaxHeadXRot() { return 40; }

    public int getMaxHeadYRot() { return 75; }

    /** 길찾기가 진행 중인가 — 아직 내비게이션이 없다. */
    public boolean navigationIsInProgress() { return navigation.isInProgress(); }

    @Override
    public void setSpeed(float v) {
        super.setSpeed(v);
        setZza(v);
    }

    /** 가끔(1000 중 ambientSoundTime 미만) 주변 소리를 낸다. */
    protected int ambientSoundTime;

    /** 테스트용: 기본 경험치. */
    public int xpRewardForTest() { return xpReward; }

    public int getAmbientSoundInterval() { return 80; }

    protected void resetAmbientSoundTime() { ambientSoundTime = -getAmbientSoundInterval(); }

    @Override
    public void baseTick() {
        super.baseTick();
        if (isAlive() && random.nextInt(1000) < ambientSoundTime++) {
            resetAmbientSoundTime();
            playAmbientSound();
        }
    }

    /** Mob.playAmbientSound: 주변 소리를 낸다(여우 등이 재정의). */
    protected void playAmbientSound() { makeSound(getAmbientSound()); }

    @Override
    protected void playHurtSound(Damage source) {
        resetAmbientSoundTime();
        super.playHurtSound(source);
    }

    @Override
    protected boolean isEffectiveAi() { return !isNoAi(); }

    /** Mob.customServerAiStep(vanilla fm — serverAiStep): 감지 → 목표 선택(2틱에 한 번만 전체) → 내비게이션 → 몹 틱 → 제어 순. */
    @Override
    protected final void serverAiStep() {
        noActionTime++;
        seenTargets.clear();
        unseenTargets.clear();
        int i = tickCount + id();
        if (i % 2 != 0 && tickCount > 1) {
            targetSelector.tickRunningGoals(false);
            goalSelector.tickRunningGoals(false);
        } else {
            targetSelector.tick();
            goalSelector.tick();
        }
        navigation.tick();
        customServerAiStep();
        moveControl.tick();
        lookControl.tick();
        jumpControl.tick();
        // TODO(디버그 정보): sendDebugPackets
    }

    protected void customServerAiStep() {}

    /** Mob.stopInPlace: 길찾기를 멈추고 이동 입력을 0 으로. */
    public void stopInPlace() {
        navigation.stop();
        setXxa(0.0F);
        setYya(0.0F);
        setSpeed(0.0F);
    }




    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_MOB_FLAGS, SetEntityData.SERIALIZER_BYTE, (byte) 0);
    }

    public boolean isNoAi() { return ((Byte) entityData.get(DATA_MOB_FLAGS) & 1) != 0; }

    public void setNoAi(boolean v) {
        byte b = (Byte) entityData.get(DATA_MOB_FLAGS);
        entityData.set(DATA_MOB_FLAGS, (byte) (v ? b | 1 : b & ~1));
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        if (brain != null) tag.putCompound("Brain", brain.save());
        tag.putBoolean("CanPickUpLoot", canPickUpLoot);
        tag.putBoolean("PersistenceRequired", persistenceRequired);
        var armor = new org.domi.furnace.server.net.io.nbt.NbtList((byte) 10);
        for (ItemStack st : armorItems) armor.add(ItemStackNbt.write(st));
        tag.putList("ArmorItems", armor);
        var armorChances = new org.domi.furnace.server.net.io.nbt.NbtList((byte) 5);
        for (float f : armorDropChances) armorChances.add(f);
        tag.putList("ArmorDropChances", armorChances);
        var hands = new org.domi.furnace.server.net.io.nbt.NbtList((byte) 10);
        for (ItemStack st : handItems) hands.add(ItemStackNbt.write(st));
        tag.putList("HandItems", hands);
        var handChances = new org.domi.furnace.server.net.io.nbt.NbtList((byte) 5);
        for (float f : handDropChances) handChances.add(f);
        tag.putList("HandDropChances", handChances);
        if (!bodyArmorItem.isEmpty()) {
            tag.putCompound("body_armor_item", ItemStackNbt.write(bodyArmorItem));
            tag.putFloat("body_armor_drop_chance", bodyArmorDropChance);
        }
        tag.putBoolean("LeftHanded", ((Byte) entityData.get(DATA_MOB_FLAGS) & 2) != 0);
        if (isNoAi()) tag.putBoolean("NoAI", true);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (brain != null && tag.tags().get("Brain") instanceof NbtCompound b) brain.load(b);
        if (tag.contains("CanPickUpLoot")) canPickUpLoot = tag.getBoolean("CanPickUpLoot");
        persistenceRequired = tag.getBoolean("PersistenceRequired");
        if (tag.tags().get("ArmorItems") instanceof org.domi.furnace.server.net.io.nbt.NbtList l) {
            for (int i = 0; i < armorItems.length; i++) armorItems[i] = i < l.size() && l.get(i) instanceof NbtCompound c ? ItemStackNbt.read(c) : ItemStack.EMPTY;
        }
        if (tag.tags().get("ArmorDropChances") instanceof org.domi.furnace.server.net.io.nbt.NbtList l) {
            for (int i = 0; i < l.size() && i < armorDropChances.length; i++) armorDropChances[i] = l.get(i) instanceof Number n ? n.floatValue() : 0.0F;
        }
        if (tag.tags().get("HandItems") instanceof org.domi.furnace.server.net.io.nbt.NbtList l) {
            for (int i = 0; i < handItems.length; i++) handItems[i] = i < l.size() && l.get(i) instanceof NbtCompound c ? ItemStackNbt.read(c) : ItemStack.EMPTY;
        }
        if (tag.tags().get("HandDropChances") instanceof org.domi.furnace.server.net.io.nbt.NbtList l) {
            for (int i = 0; i < l.size() && i < handDropChances.length; i++) handDropChances[i] = l.get(i) instanceof Number n ? n.floatValue() : 0.0F;
        }
        if (tag.tags().get("body_armor_item") instanceof NbtCompound c) {
            bodyArmorItem = ItemStackNbt.read(c);
            bodyArmorDropChance = tag.getFloat("body_armor_drop_chance");
        } else {
            bodyArmorItem = ItemStack.EMPTY;
        }
        byte b = (byte) ((tag.getBoolean("LeftHanded") ? 2 : 0));
        entityData.set(DATA_MOB_FLAGS, b);
        setNoAi(tag.getBoolean("NoAI"));
    }
}
