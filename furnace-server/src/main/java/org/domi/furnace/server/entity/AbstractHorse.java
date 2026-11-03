// 바닐라 AbstractHorse: 말 종류 공통 — 템퍼(길들이기 진행도)·안장/먹이/뒷발 서기, 임의 능력치(체력·속도·점프), 번식 시 부모 능력치 교배.
// 탑승: 빈손 우클릭으로 올라타고, 안장을 얹으면 탄 플레이어가 조종한다(이동은 클라이언트가 move_vehicle 로 보낸다 —
// 서버는 회전만 맞추고 속도를 지운다). 길들이기는 RunAroundLikeCrazyGoal 의 템퍼 판정. 인벤토리 창은 HorseInventoryMenu(웅크려 우클릭·타고 인벤토리 키).
package org.domi.furnace.server.entity;

import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract class AbstractHorse extends Animal {

    /** AbstractHorse.isPushable: 누가 타고 있으면 밀리지 않는다. */
    @Override public boolean isPushable() { return !isVehicle() && super.isPushable(); }
    public static final int DATA_ID_FLAGS = 17;
    public static final int FLAG_TAME = 2, FLAG_SADDLE = 4, FLAG_BRED = 8, FLAG_EATING = 16, FLAG_STANDING = 32, FLAG_OPEN_MOUTH = 64;
    private static final TargetingConditions MOMMY_TARGETING = TargetingConditions.forNonCombat().range(16.0).ignoreLineOfSight().selector(t -> t instanceof AbstractHorse h && h.isBred());
    /** 한 줄에 놓이는 칸(저장·인벤토리 크기 계산에 쓰는 열 수)와 인벤토리: 0번 칸이 안장. */
    protected ItemStack[] inventory;
    private int eatingCounter;
    private int mouthCounter;
    private int standCounter;
    public int tailCounter;
    public int sprintCounter;
    protected int gallopSoundCounter;
    private int temper;
    /** 뒷발로 선 애니메이션(standAnim·standAnimO)과 점프 중 서기 미끄러짐(allowStandSliding) — 승객 위치에 쓴다. */
    protected float standAnim, standAnimO;
    protected boolean allowStandSliding;
    private UUID ownerUUID;

    protected AbstractHorse(EntityType type, ChunkLevel level, int id) {
        super(type, level, id);
        createInventory();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new PanicGoal(this, 1.2));
        goalSelector.addGoal(1, new RunAroundLikeCrazyGoal(this, 1.2));
        goalSelector.addGoal(2, new BreedGoal(this, 1.0, AbstractHorse.class));
        goalSelector.addGoal(4, new FollowParentGoal(this, 1.0));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, 6.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        if (canPerformRearing()) goalSelector.addGoal(9, new RandomStandGoal(this));
        addBehaviourGoals();
    }

    protected void addBehaviourGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(3, new TemptGoal(this, 1.25, s -> ItemTags.is(s, "minecraft:horse_tempt_items"), false));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ID_FLAGS, SetEntityData.SERIALIZER_BYTE, (byte) 0);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.JUMP_STRENGTH, 0.7).add(Attribute.MAX_HEALTH, 53.0).add(Attribute.MOVEMENT_SPEED, 0.225F).add(Attribute.STEP_HEIGHT, 1.0)
                .add(Attribute.SAFE_FALL_DISTANCE, 6.0).add(Attribute.FALL_DAMAGE_MULTIPLIER, 0.5);
    }

    // ------------------------------------------------------------------ 상태 비트

    private boolean getFlag(int f) { return ((Byte) entityData.get(DATA_ID_FLAGS) & f) != 0; }

    private void setFlag(int f, boolean on) {
        byte b = (Byte) entityData.get(DATA_ID_FLAGS);
        entityData.set(DATA_ID_FLAGS, (byte) (on ? b | f : b & ~f));
    }

    public boolean isTamed() { return getFlag(FLAG_TAME); }

    public void setTamed(boolean v) { setFlag(FLAG_TAME, v); }

    public boolean isBred() { return getFlag(FLAG_BRED); }

    public void setBred(boolean v) { setFlag(FLAG_BRED, v); }

    public boolean isEating() { return getFlag(FLAG_EATING); }

    public void setEating(boolean v) { setFlag(FLAG_EATING, v); }

    /** AbstractHorse.checkLeashDistance: 6칸 넘게 당기면 풀을 뜯다가 멈춘다(PathfinderMob 의 5칸 제한은 부르지 않는다). */
    @Override
    protected boolean checkLeashDistance(Rider holder, float dist) {
        if (dist > 6.0F && isEating()) setEating(false);
        return true;
    }

    public boolean isStanding() { return getFlag(FLAG_STANDING); }

    public void setStanding(boolean v) {
        if (v) setEating(false);
        setFlag(FLAG_STANDING, v);
    }

    public boolean isSaddled() { return getFlag(FLAG_SADDLE); }

    public int getTemper() { return temper; }

    public void setTemper(int t) { temper = t; }

    public int modifyTemper(int delta) {
        int t = Math.clamp(getTemper() + delta, 0, getMaxTemper());
        setTemper(t);
        return t;
    }

    public int getMaxTemper() { return 100; }

    public UUID getOwnerUUID() { return ownerUUID; }

    public void setOwnerUUID(UUID u) { ownerUUID = u; }

    /** 첫 승객이 플레이어인가. */
    public boolean hasPlayerPassenger() { return firstPassenger() instanceof Player; }

    public Player playerPassenger() { return firstPassenger() instanceof Player p ? p : null; }

    @Override
    public boolean isImmobile() { return super.isImmobile() && isVehicle() && isSaddled() || isEating() || isStanding(); }

    /** AbstractHorse.tameWithName: 주인을 정하고 길들인 뒤 tame_animal, 하트 이벤트(7). */
    public boolean tameWithName(Player p) {
        ownerUUID = p.uuid();
        setTamed(true);
        var me = KillCredit.snapshot(lootEntity());
        p.runOnOwner(() -> {
            var adv = p.advancements();
            if (adv != null) adv.triggerTame(me);
        });
        broadcastEntityEvent((byte) 7);
        return true;
    }

    /** Saddleable.isSaddleable: 살아 있고 다 자랐고 길들여졌다. */
    public boolean isSaddleable() { return isAlive() && !isBaby() && isTamed(); }

    /** Saddleable.equipSaddle: 안장 칸에 넣는다(칸이 바뀌면 containerChanged). */
    public void equipSaddle(ItemStack saddle) {
        inventory[0] = saddle;
        containerChanged();
    }

    /** containerChanged: 안장 상태를 다시 맞추고, 틱 20 이 지나 새로 안장이 얹히면 소리. */
    protected void containerChanged() {
        boolean was = isSaddled();
        updateContainerEquipment();
        if (tickCount > 20 && !was && isSaddled()) playSound(getSaddleSoundEvent(), 0.5F, 1.0F);
    }

    protected String getSaddleSoundEvent() { return "entity.horse.saddle"; }

    /** getControllingPassenger: 안장이 있고 첫 승객이 플레이어면 그가 조종한다. */
    @Override
    public Rider getControllingPassenger() {
        return isSaddled() && firstPassenger() instanceof Player p ? p : super.getControllingPassenger();
    }

    /** tickRidden: 몸·머리 방향을 탄 플레이어 시선(세로는 절반)으로. 서버에서는 조종이 클라이언트 쪽이라 점프·이동은 하지 않는다. */
    @Override
    protected void tickRidden(Player p) {
        setYRot(p.yaw());
        setXRot(p.pitch() * 0.5F);
        yRotO = yBodyRot = yHeadRot = yRot();
    }

    /** positionRider: 승객의 몸 방향을 내 몸 방향으로. */
    @Override
    protected void placeRider(Rider r) {
        super.placeRider(r);
        if (r instanceof LivingEntity le) le.yBodyRot = yBodyRot;
    }

    /** getPassengerAttachmentPoint: 뒷발로 선 정도(standAnimO)만큼 위·뒤로. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Rider r, float scale) {
        return super.getPassengerAttachmentPoint(r, scale)
                .add(new Vec3(0.0, 0.15 * standAnimO * scale, -0.7 * standAnimO * scale).yRot(-yRot() * (float) (Math.PI / 180.0)));
    }

    /** getDismountLocationForPassenger: 주 손 쪽 옆, 안 되면 반대쪽, 그래도 안 되면 내 자리. */
    @Override
    public Vec3 getDismountLocationForPassenger(Rider r) {
        float pw = r instanceof Entity e ? e.bbWidth() : 0.6F;
        boolean rightHanded = !(r instanceof Mob m) || !m.isLeftHanded();   // TODO(주 손): 플레이어 주 손 설정은 오른손으로 본다
        Vec3 v = collisionHorizontalEscapeVector(bbWidth(), pw, yRot() + (rightHanded ? 90.0F : -90.0F));
        Vec3 p = DismountHelper.findDismountLocation(this, v, r);
        if (p != null) return p;
        Vec3 v2 = collisionHorizontalEscapeVector(bbWidth(), pw, yRot() + (!rightHanded ? 90.0F : -90.0F));
        Vec3 p2 = DismountHelper.findDismountLocation(this, v2, r);
        return p2 != null ? p2 : new Vec3(x(), y(), z());
    }

    /** handleStartJump(power): 점프 시작 — 뒷발 서기·점프 소리(점프 자체는 클라이언트가 한다). */
    public void handleStartJump(int power) {
        allowStandSliding = true;
        standIfPossible();
        playSound("entity.horse.jump", 0.4F, 1.0F);
    }

    /** PlayerRideableJumping.canJump: 안장이 있다. */
    public boolean canJump() { return isSaddled(); }

    public void handleStopJump() {}

    /** AbstractHorse.causeFallDamage: 피해가 있으면 나와 승객의 승객까지 같은 피해(LivingEntity 의 것을 부르지 않는다). */
    @Override
    public boolean causeFallDamage(float distance, float multiplier) {
        if (distance > 1.0F) playSound("entity.horse.land", 0.4F, 1.0F);
        int dmg = calculateFallDamage(distance, multiplier);
        if (dmg <= 0) return false;
        Damage fall = new Damage("fall", false, false, false);
        hurt(fall, dmg);
        if (isVehicle()) {
            java.util.List<Rider> riders = new java.util.ArrayList<>();
            collectIndirectPassengers(riders);
            for (Rider r : riders) if (r instanceof Entity e) e.hurt(fall, dmg);   // TODO(플레이어 낙하): 탄 플레이어
        }
        // TODO(소리): playBlockFallSound
        return true;
    }

    // ------------------------------------------------------------------ 인벤토리

    public int inventoryColumns() { return 0; }

    public final int getInventorySize() { return inventoryColumns() * 3 + 1; }

    protected void createInventory() {
        ItemStack[] old = inventory;
        inventory = new ItemStack[getInventorySize()];
        java.util.Arrays.fill(inventory, ItemStack.EMPTY);
        if (old != null) {
            int n = Math.min(old.length, inventory.length);
            for (int i = 0; i < n; i++) if (!old[i].isEmpty()) inventory[i] = old[i];
        }
        updateContainerEquipment();
    }

    protected void updateContainerEquipment() {
        setFlag(FLAG_SADDLE, !inventory[0].isEmpty());
    }

    public boolean isSaddleItem(ItemStack s) { return !s.isEmpty() && ItemTools.itemName(s).equals("minecraft:saddle"); }

    // ------------------------------------------------------------------ 인벤토리 창·몸통 갑옷

    /** 지금 인벤토리(0 안장, 1.. 상자 칸)를 메뉴용 Container 로 — 이 배열에 매인다(createInventory 로 바뀌면 hasInventoryChanged). */
    public org.domi.furnace.server.menu.Container inventoryContainer() { return new HorseContainer(this, inventory); }

    /** hasInventoryChanged(container): 창이 쥔 인벤토리가 지금 것이 아니다. */
    public boolean hasInventoryChanged(org.domi.furnace.server.menu.Container c) { return !(c instanceof HorseContainer h && h.items == inventory); }

    /** 바닐라 SimpleContainer + containerChanged 리스너: 칸이 바뀌면(setChanged) 말이 안장 상태를 다시 맞춘다. */
    static final class HorseContainer implements org.domi.furnace.server.menu.Container {
        final AbstractHorse horse;
        final ItemStack[] items;

        HorseContainer(AbstractHorse horse, ItemStack[] items) {
            this.horse = horse;
            this.items = items;
        }

        @Override public int size() { return items.length; }

        @Override public ItemStack get(int i) { return i >= 0 && i < items.length ? items[i] : ItemStack.EMPTY; }

        @Override public void set(int i, ItemStack s) {
            items[i] = s == null ? ItemStack.EMPTY : s;
            setChanged();
        }

        @Override public ItemStack remove(int i, int amount) {
            ItemStack cur = get(i);
            if (cur.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            int n = Math.min(amount, cur.count());
            items[i] = cur.withCount(cur.count() - n);
            setChanged();
            return cur.withCount(n);
        }

        @Override public ItemStack removeNoUpdate(int i) {
            ItemStack cur = get(i);
            if (i >= 0 && i < items.length) items[i] = ItemStack.EMPTY;
            return cur;
        }

        /** 그 자리 개수 바꾸기 — 뒤따르는 Slot.setChanged 가 알린다. */
        @Override public void replaceInPlace(int i, ItemStack s) { items[i] = s; }

        @Override public void setChanged() { horse.containerChanged(); }

        @Override public boolean stillValid(org.domi.furnace.server.menu.MenuPlayer p) { return true; }

        @Override public int[] containerPos() { return org.domi.furnace.server.world.MenuBlockContext.blockPos(horse); }
    }

    /** getBodyArmorAccess: 몸통 갑옷 칸 하나짜리 용기. 놓으면 setBodyArmorItem, 떼면 그 자리 스택만 줄인다(소리 없음). */
    public org.domi.furnace.server.menu.Container bodyArmorAccess() {
        AbstractHorse self = this;
        return new org.domi.furnace.server.menu.Container() {
            @Override public int size() { return 1; }

            @Override public ItemStack get(int i) { return i == 0 ? getItemBySlot(EquipmentSlot.BODY) : ItemStack.EMPTY; }

            @Override public void set(int i, ItemStack s) { if (i == 0) setBodyArmorItem(s == null ? ItemStack.EMPTY : s); }

            @Override public ItemStack remove(int i, int amount) {
                if (i != 0) return ItemStack.EMPTY;
                ItemStack cur = get(0);
                if (cur.isEmpty() || amount <= 0) return ItemStack.EMPTY;
                int n = Math.min(amount, cur.count());
                setItemSlot(EquipmentSlot.BODY, cur.withCount(cur.count() - n));   // split: 그 자리에서 줄인다
                return cur.withCount(n);
            }

            @Override public ItemStack removeNoUpdate(int i) { return remove(i, Integer.MAX_VALUE); }

            @Override public void replaceInPlace(int i, ItemStack s) { if (i == 0) setItemSlot(EquipmentSlot.BODY, s); }

            @Override public void setChanged() { }

            /** 타고 있거나 4칸 여유 안. */
            @Override public boolean stillValid(org.domi.furnace.server.menu.MenuPlayer p) {
                return p.isRiding(self) || p.canInteractWithEntity(boundingBox(), 4.0);
            }

            @Override public int[] containerPos() { return org.domi.furnace.server.world.MenuBlockContext.blockPos(self); }
        };
    }

    /** canUseSlot(BODY): 몸통 갑옷을 입을 수 있는가(말만). */
    public boolean canUseBodySlot() { return false; }

    /** isBodyArmorItem: 이 말이 입는 몸통 갑옷인가. */
    public boolean isBodyArmorItem(ItemStack s) { return false; }

    /** isWearingBodyArmor. */
    public boolean isWearingBodyArmor() { return !getItemBySlot(EquipmentSlot.BODY).isEmpty(); }

    /** Mob.setBodyArmorItem → setItemSlotAndDropWhenKilled(BODY): setItemSlot 안에서 onEquipItem 이 장착 소리를 낸다. */
    public void setBodyArmorItem(ItemStack s) {
        ItemStack old = getItemBySlot(EquipmentSlot.BODY);
        setItemSlotAndDropWhenKilled(EquipmentSlot.BODY, s);
        onBodyArmorEquip(old, s);
    }

    /**
     * LivingEntity.onEquipItem(BODY, old, new): 둘 다 비지 않았고 다른 것이며 첫 틱이 아니면, 새 것이 몸통에 입는 장비일 때
     * 그 장착 소리(엔티티 난수 씨앗), 그리고 새 것이 장착 아이템(Equipable — 몸통 갑옷·양탄자)이면 equip, 아니면 unequip.
     */
    public void onBodyArmorEquip(ItemStack oldItem, ItemStack newItem) {
        boolean bothEmpty = newItem.isEmpty() && oldItem.isEmpty();
        if (bothEmpty || org.domi.furnace.server.menu.Stacks.isSameItemSameComponents(oldItem, newItem) || firstTick) return;
        String sound = bodyEquipSound(newItem);
        if (!isSilent() && sound != null) level.playSeededSound(null, x(), y(), z(), sound, soundSource(), 1.0F, 1.0F, random.nextLong());
        boolean equipable = sound != null || !newItem.isEmpty() && ItemTags.is(newItem, "minecraft:wool_carpets");
        gameEvent(equipable ? org.domi.furnace.server.world.gameevent.GameEvent.EQUIP : org.domi.furnace.server.world.gameevent.GameEvent.UNEQUIP);
    }

    /** Equipable.get(stack) 이 몸통 칸이면 그 장착 소리(말 갑옷은 재료별, 늑대 갑옷), 아니면 null. */
    static String bodyEquipSound(ItemStack s) {
        if (s.isEmpty()) return null;
        return switch (ItemTools.itemName(s)) {
            case "minecraft:leather_horse_armor" -> "item.armor.equip_leather";
            case "minecraft:iron_horse_armor" -> "item.armor.equip_iron";
            case "minecraft:golden_horse_armor" -> "item.armor.equip_gold";
            case "minecraft:diamond_horse_armor" -> "item.armor.equip_diamond";
            case "minecraft:wolf_armor" -> "item.armor.equip_wolf";
            default -> null;
        };
    }

    /** equipBodyArmor(player, stack): 하나를 입히고 손의 것을 하나 쓴다(크리에이티브면 그대로). */
    protected void equipBodyArmor(Player p, int hand, ItemStack held) {
        setBodyArmorItem(held.withCount(1));
        usePlayerItem(p, hand, held);
    }

    /** HasCustomInventoryScreen.openCustomInventoryScreen: (아무도 안 탔거나 그가 탔고) 길들였으면 인벤토리 창. */
    public void openCustomInventoryScreen(Player p) {
        if ((!isVehicle() || hasPassenger(p)) && isTamed()) {
            Player.InventoryHost host = p.inventoryHost();
            if (host != null) host.openHorseInventory(this, inventoryContainer());
        }
    }

    // ------------------------------------------------------------------ 소리·틱

    @Override
    protected float getSoundVolume() { return 0.8F; }

    @Override
    public int getAmbientSoundInterval() { return 400; }

    public int getAmbientStandInterval() { return getAmbientSoundInterval(); }

    public float getSoundVolumePublic() { return getSoundVolume(); }

    protected String getEatingSound() { return null; }

    protected String getAngrySound() { return null; }

    public String getAmbientStandSound() { return getAmbientSound(); }

    protected boolean canPerformRearing() { return true; }

    protected boolean canEatGrass() { return true; }

    void moveTail() { tailCounter = 1; }

    public void standIfPossible() {
        if (canPerformRearing() && isEffectiveAi()) {
            standCounter = 1;
            setStanding(true);
        }
    }

    public void makeMad() {
        if (!isStanding()) {
            standIfPossible();
            playSound(getAngrySound(), getSoundVolume(), getVoicePitch());
        }
    }

    private void eating() {
        openMouth();
        if (!isSilent()) {
            String s = getEatingSound();
            if (s != null) playSound(s, 1.0F, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.2F);
        }
    }

    private void openMouth() {
        mouthCounter = 1;
        setFlag(FLAG_OPEN_MOUTH, true);
    }

    @Override
    protected void aiStep() {
        if (random.nextInt(200) == 0) moveTail();
        super.aiStep();
        if (isAlive()) {
            if (random.nextInt(900) == 0 && deathTime == 0) heal(1.0F);
            if (canEatGrass()) {
                if (!isEating() && random.nextInt(300) == 0 && B.is(level.getBlockState(blockPosition().below()), "minecraft:grass_block")) setEating(true);
                if (isEating() && ++eatingCounter > 50) {
                    eatingCounter = 0;
                    setEating(false);
                }
            }
            followMother();
        }
    }

    protected void followMother() {
        if (isBred() && isBaby() && !isEating()) {
            AbstractHorse mother = level.getNearestEntity(AbstractHorse.class, MOMMY_TARGETING, this, x(), y(), z(), boundingBox().inflate(16.0));
            if (mother != null) {
                double dx = x() - mother.x(), dy = y() - mother.y(), dz = z() - mother.z();
                if (dx * dx + dy * dy + dz * dz > 4.0) navigation.createPath(mother, 0);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (mouthCounter > 0 && ++mouthCounter > 30) {
            mouthCounter = 0;
            setFlag(FLAG_OPEN_MOUTH, false);
        }
        if (isEffectiveAi() && standCounter > 0 && ++standCounter > 20) {
            standCounter = 0;
            setStanding(false);
        }
        if (tailCounter > 0 && ++tailCounter > 8) tailCounter = 0;
        if (sprintCounter > 0) {
            sprintCounter++;
            if (sprintCounter > 300) sprintCounter = 0;
        }
        standAnimO = standAnim;
        if (isStanding()) {
            standAnim += (1.0F - standAnim) * 0.4F + 0.05F;
            if (standAnim > 1.0F) standAnim = 1.0F;
        } else {
            allowStandSliding = false;
            standAnim += (0.8F * standAnim * standAnim * standAnim - standAnim) * 0.6F - 0.05F;
            if (standAnim < 0.0F) standAnim = 0.0F;
        }
    }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        boolean hurt = super.hurtBy(source, amount, attacker);
        if (hurt && random.nextInt(3) == 0) standIfPossible();
        return hurt;
    }


    // ------------------------------------------------------------------ 먹이·상호작용

    @Override
    public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:horse_food"); }

    /**
     * AbstractHorse.mobInteract: 누가 탔거나 아기면 동물 기본(먹이). 길들인 말에 웅크려 쓰면 인벤토리(TODO). 손에 든 것이 있으면
     * 그 아이템의 상호작용(안장 → SaddleItem.interactLivingEntity: 크리에이티브여도 하나 쓴다), 아니면 올라탄다.
     */
    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        if (isVehicle() || isBaby()) return super.interact(p, hand, held, region);
        if (isTamed() && p.sneaking()) {
            openCustomInventoryScreen(p);
            return true;
        }
        if (!held.isEmpty()) {
            if (isSaddleItem(held) && !isSaddled() && isSaddleable()) {
                var inv = p.inventory();
                inv.set(hand == 0 ? inv.heldWindowSlot() : 45, held.withCount(held.count() - 1));
                equipSaddle(held.withCount(1));
                level.gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.EQUIP, new Vec3(x(), y(), z()),
                        org.domi.furnace.server.world.gameevent.GameEventContext.of(this));   // SaddleItem.interactLivingEntity
                return true;
            }
            if (canUseBodySlot() && isBodyArmorItem(held) && !isWearingBodyArmor()) {
                equipBodyArmor(p, hand, held);
                return true;
            }
        }
        doPlayerRide(p);
        return true;
    }

    /** 먹이를 줄 때(밀·설탕·황금 당근 등): 체력/성장/템퍼를 올리고 먹는 소리. */
    protected boolean handleEating(Player p, ItemStack s) {
        boolean used = false;
        float heal = 0.0F;
        int age = 0;
        int temperDelta = 0;
        String n = ItemTools.itemName(s);
        switch (n) {
            case "minecraft:sugar" -> { heal = 2.0F; age = 20; temperDelta = 3; }
            case "minecraft:wheat" -> { heal = 1.0F; age = 30; temperDelta = 3; }
            case "minecraft:hay_block" -> { heal = 20.0F; age = 180; }
            case "minecraft:apple" -> { heal = 3.0F; age = 60; temperDelta = 3; }
            case "minecraft:golden_carrot" -> {
                heal = 4.0F; age = 60; temperDelta = 5;
                if (isTamed() && getAge() == 0 && canFallInLove()) {
                    used = true;
                    setInLove(p);
                }
            }
            case "minecraft:golden_apple", "minecraft:enchanted_golden_apple" -> {
                heal = 10.0F; age = 240; temperDelta = 10;
                if (isTamed() && getAge() == 0 && canFallInLove()) {
                    used = true;
                    setInLove(p);
                }
            }
            default -> { }
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
            modifyTemper(temperDelta);
            used = true;
        }
        if (used) {
            eating();
            gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.EAT);
        }
        return used;
    }

    /** 먹이 상호작용(소비 포함). */
    protected boolean fedFood(Player p, int hand, ItemStack s) {
        boolean ok = handleEating(p, s);
        if (ok) usePlayerItem(p, hand, s);
        return ok;
    }

    /** doPlayerRide: 풀 뜯기·서기를 멈추고 플레이어 시선을 말 방향으로 맞춰 태운다. */
    protected void doPlayerRide(Player p) {
        setEating(false);
        setStanding(false);
        p.applyRotation(yRot(), xRot(), p.onGround());
        p.startRiding(this, false);
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        for (ItemStack s : inventory) if (!s.isEmpty()) spawnAtLocation(s);
    }

    // ------------------------------------------------------------------ 저장

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("EatingHaystack", isEating());
        tag.putBoolean("Bred", isBred());
        tag.putInt("Temper", getTemper());
        tag.putBoolean("Tame", isTamed());
        if (ownerUUID != null) tag.putIntArray("Owner", uuidToInts(ownerUUID));
        if (!inventory[0].isEmpty()) tag.putCompound("SaddleItem", ItemStackNbt.write(inventory[0]));
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setEating(tag.getBoolean("EatingHaystack"));
        setBred(tag.getBoolean("Bred"));
        setTemper(tag.getIntOr0("Temper"));
        setTamed(tag.getBoolean("Tame"));
        if (tag.tags().get("Owner") instanceof int[] u && u.length == 4) {
            ownerUUID = new UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL);
        }
        if (tag.tags().get("SaddleItem") instanceof NbtCompound c) {
            ItemStack s = ItemStackNbt.read(c);
            if (isSaddleItem(s)) inventory[0] = s;
        }
        updateContainerEquipment();
    }

    @Override
    public boolean canMate(Animal other) { return false; }

    /** 번식할 수 있는 상태: 탑승자 없이 길들여졌고 다 자라 체력이 가득하며 사랑 중. */
    protected boolean canParent() { return !isVehicle() && !isPassenger() && isTamed() && !isBaby() && health() >= maxHealth() && isInLove(); }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) { return null; }

    // ------------------------------------------------------------------ 능력치 교배·무작위

    private static final float MIN_HEALTH = a(i -> 0), MAX_HEALTH = a(i -> i - 1);
    private static final float MIN_JUMP = (float) b(() -> 0.0), MAX_JUMP = (float) b(() -> 1.0);
    private static final float MIN_SPEED = (float) c(() -> 0.0), MAX_SPEED = (float) c(() -> 1.0);

    protected void setOffspringAttributes(AgeableMob partner, AbstractHorse child) {
        setOffspringAttribute(partner, child, Attribute.MAX_HEALTH, MIN_HEALTH, MAX_HEALTH);
        setOffspringAttribute(partner, child, Attribute.JUMP_STRENGTH, MIN_JUMP, MAX_JUMP);
        setOffspringAttribute(partner, child, Attribute.MOVEMENT_SPEED, MIN_SPEED, MAX_SPEED);
    }

    private void setOffspringAttribute(AgeableMob partner, AbstractHorse child, Attribute a, double min, double max) {
        double v = createOffspringAttribute(attributeValue(a), ((AbstractHorse) partner).attributeValue(a), min, max, random);
        child.attributes().get(a).setBaseValue(v);
    }

    static double createOffspringAttribute(double v1, double v2, double min, double max, org.domi.furnace.server.block.RandomSource r) {
        if (max <= min) throw new IllegalArgumentException("Incorrect range for an attribute");
        v1 = Math.clamp(v1, min, max);
        v2 = Math.clamp(v2, min, max);
        double d = 0.15 * (max - min);
        double d1 = Math.abs(v1 - v2) + d * 2.0;
        double d2 = (v1 + v2) / 2.0;
        double d3 = (r.nextDouble() + r.nextDouble() + r.nextDouble()) / 3.0 - 0.5;
        double d4 = d2 + d1 * d3;
        if (d4 > max) return max - (d4 - max);
        if (d4 < min) return min + (min - d4);
        return d4;
    }

    /** generateMaxHealth(IntUnaryOperator): 15 + op(8) + op(9). */
    protected static float a(IntUnaryOperator op) { return 15.0F + op.applyAsInt(8) + op.applyAsInt(9); }

    /** generateJumpStrength: 0.4 + r × 0.2 × 3. */
    protected static double b(DoubleSupplier r) { return 0.4F + r.getAsDouble() * 0.2 + r.getAsDouble() * 0.2 + r.getAsDouble() * 0.2; }

    /** generateSpeed: (0.45 + r × 0.3 × 3) × 0.25. */
    protected static double c(DoubleSupplier r) { return (0.45F + r.getAsDouble() * 0.3 + r.getAsDouble() * 0.3 + r.getAsDouble() * 0.3) * 0.25; }

    protected void randomizeAttributes(org.domi.furnace.server.block.RandomSource r) { }

    @Override
    public AgeableMob.GroupData finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty, AgeableMob.GroupData data) {
        if (data == null) data = new AgeableMob.GroupData(0.2F);
        randomizeAttributes(r);
        return super.finalizeSpawn(r, difficulty, data);
    }
    @Override public int getMaxSpawnClusterSize() { return 6; }
}
