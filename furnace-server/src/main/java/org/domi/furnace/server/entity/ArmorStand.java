// 바닐라 ArmorStand: 손 2칸·방어구 4칸을 놓는 거치대. 우클릭한 높이로 칸을 골라 아이템을 바꿔 끼우고, 때리면 5틱 안에 두 번째 타격에 부서진다(아이템과 장비를 떨군다).
// 자세는 머리·몸·양팔·양다리 회전(데이터 16~21), 플래그(작게·팔·받침 없음·마커)는 15 번.
// TODO: 부서질 때 나무 입자, 커스텀 이름 아이템 이름, 탑승자 처리(없음), 밀림(isPushable) 무시.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class ArmorStand extends LivingEntity {

    /** ArmorStand.isPushable = false. */
    @Override public boolean isPushable() { return false; }

    /** ArmorStand.pushEntities: 거리² 0.2 이하의 탈 수 있는 광산 수레만 민다. */
    @Override
    protected void pushEntities() {
        if (level == null) return;
        for (Entity e : level.getEntities(this, boundingBox(), en -> en instanceof AbstractMinecart m && m.getMinecartType() == AbstractMinecart.Type.RIDEABLE)) {
            double dx = x() - e.x(), dy = y() - e.y(), dz = z() - e.z();
            if (dx * dx + dy * dy + dz * dz <= 0.2) e.push(this);
        }
    }

    /** ArmorStand.thunderHit: 번개에 맞아도 아무 일 없다. */
    @Override
    public void thunderHit(org.domi.furnace.server.block.ChunkLevel level, LightningBolt bolt) {}

    public static final int DATA_CLIENT_FLAGS = 15, DATA_HEAD_POSE = 16, DATA_BODY_POSE = 17, DATA_LEFT_ARM_POSE = 18, DATA_RIGHT_ARM_POSE = 19,
            DATA_LEFT_LEG_POSE = 20, DATA_RIGHT_LEG_POSE = 21;
    public static final int FLAG_SMALL = 1, FLAG_SHOW_ARMS = 4, FLAG_NO_BASE_PLATE = 8, FLAG_MARKER = 16;
    private static final String[] POSE_NAMES = {"Head", "Body", "LeftArm", "RightArm", "LeftLeg", "RightLeg"};
    private static final int[] POSE_IDS = {DATA_HEAD_POSE, DATA_BODY_POSE, DATA_LEFT_ARM_POSE, DATA_RIGHT_ARM_POSE, DATA_LEFT_LEG_POSE, DATA_RIGHT_LEG_POSE};
    private static final float[][] POSE_DEFAULTS = {{0, 0, 0}, {0, 0, 0}, {-10, 0, -10}, {-15, 0, 10}, {-1, 0, -1}, {1, 0, 1}};

    private final ItemStack[] handItems = {ItemStack.EMPTY, ItemStack.EMPTY};
    private final ItemStack[] armorItems = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private int disabledSlots;
    private long lastHit = Long.MIN_VALUE / 2;

    public ArmorStand(ChunkLevel level, int id) { super(EntityType.ARMOR_STAND, level, id); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_CLIENT_FLAGS, SetEntityData.SERIALIZER_BYTE, (byte) 0);
        for (int i = 0; i < 6; i++) entityData.define(POSE_IDS[i], SetEntityData.SERIALIZER_ROTATIONS, POSE_DEFAULTS[i].clone());
    }

    // ------------------------------------------------------------------ 장비

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot.type()) {
            case HAND -> handItems[slot.index()];
            case HUMANOID_ARMOR -> armorItems[slot.index()];
            case ANIMAL_ARMOR -> ItemStack.EMPTY;
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        switch (slot.type()) {
            case HAND -> handItems[slot.index()] = stack;
            case HUMANOID_ARMOR -> armorItems[slot.index()] = stack;
            case ANIMAL_ARMOR -> { }
        }
    }

    public boolean canUseSlot(EquipmentSlot slot) { return slot != EquipmentSlot.BODY; }

    // ------------------------------------------------------------------ 플래그

    private boolean flag(int f) { return ((Byte) entityData.get(DATA_CLIENT_FLAGS) & f) != 0; }

    private void setFlag(int f, boolean on) {
        byte b = (Byte) entityData.get(DATA_CLIENT_FLAGS);
        entityData.set(DATA_CLIENT_FLAGS, (byte) (on ? b | f : b & ~f));
    }

    public boolean isSmall() { return flag(FLAG_SMALL); }

    public void setSmall(boolean v) { setFlag(FLAG_SMALL, v); refreshDims(); }

    public boolean showArms() { return flag(FLAG_SHOW_ARMS); }

    public void setShowArms(boolean v) { setFlag(FLAG_SHOW_ARMS, v); }

    public boolean noBasePlate() { return flag(FLAG_NO_BASE_PLATE); }

    public void setNoBasePlate(boolean v) { setFlag(FLAG_NO_BASE_PLATE, v); }

    public boolean isMarker() { return flag(FLAG_MARKER); }

    /** getPistonPushReaction: 마커면 IGNORE. */
    @Override
    public boolean ignoresPistonPush() { return isMarker(); }

    public void setMarker(boolean v) {
        setFlag(FLAG_MARKER, v);
        noPhysics = isMarker() || isNoGravity();
        refreshDims();
    }

    private void refreshDims() {
        if (isMarker()) setDimensions(0.0F, 0.0F, 0.0F);
        else if (isSmall()) setDimensions(0.5F, 0.9875F, 0.88875F);
        else setDimensions(type.width(), type.height(), type.eyeHeight());
    }

    public float[] pose(int index) { return (float[]) entityData.get(POSE_IDS[index]); }

    public void setPose(int index, float[] rot) { entityData.set(POSE_IDS[index], rot); }

    @Override public boolean isPickable() { return !isMarker(); }

    // ------------------------------------------------------------------ 틱

    @Override
    public void tick() {
        super.tick();
        yBodyRotO = yBodyRot = yRot();
        yHeadRotO = yHeadRot = yRot();
    }

    // ------------------------------------------------------------------ 우클릭

    /** ArmorStand.interactAt: hitY 는 거치대 발 기준 높이. 칸을 바꿔 끼웠으면 true(안 되면 클라이언트가 이어서 일반 우클릭을 보낸다). */
    public boolean interactAt(Player p, double hitY, int hand) {
        var inv = p.inventory();
        int ws = hand == 0 ? inv.heldWindowSlot() : 45;
        ItemStack held = inv.get(ws);
        if (isMarker() || held.itemId() == ItemContext.id("minecraft:item", "minecraft:name_tag")) return false;
        if (p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) return true;
        EquipmentSlot target = Mob.equipmentSlotFor(held);
        if (held.isEmpty()) {
            EquipmentSlot byHeight = slotAt(hitY);
            // 손이 비었으면 높이로 고른 칸을 시도하고, 막혔으면 빈 손 기본 칸(주 손)을 쓴다
            EquipmentSlot chosen = isDisabled(byHeight) ? target : byHeight;
            return canUseSlot(chosen) && swapSlot(p, chosen, held, ws);
        }
        if (isDisabled(target)) return false;
        if (target.type() == EquipmentSlot.Type.HAND && !showArms()) return false;
        return swapSlot(p, target, held, ws);
    }

    private EquipmentSlot slotAt(double hitY) {
        EquipmentSlot r = EquipmentSlot.MAINHAND;
        boolean small = isSmall();
        double d = hitY;
        if (d >= 0.1 && d < 0.1 + (small ? 0.8 : 0.45) && canUseSlot(EquipmentSlot.FEET)) r = EquipmentSlot.FEET;
        else if (d >= 0.9 + (small ? 0.3 : 0.0) && d < 0.9 + (small ? 1.0 : 0.7) && canUseSlot(EquipmentSlot.CHEST)) r = EquipmentSlot.CHEST;
        else if (d >= 0.4 && d < 0.4 + (small ? 1.0 : 0.8) && canUseSlot(EquipmentSlot.LEGS)) r = EquipmentSlot.LEGS;
        else if (d >= 1.6 && canUseSlot(EquipmentSlot.HEAD)) r = EquipmentSlot.HEAD;
        else if (!canUseSlot(EquipmentSlot.MAINHAND) && canUseSlot(EquipmentSlot.OFFHAND)) r = EquipmentSlot.OFFHAND;
        return r;
    }

    public boolean isSlotDisabled(EquipmentSlot slot) { return isDisabled(slot); }

    private boolean isDisabled(EquipmentSlot slot) {
        return (disabledSlots & 1 << slotBit(slot)) != 0 || slot.type() == EquipmentSlot.Type.HAND && !showArms();
    }

    /** EquipmentSlot.getFilterBit 의 아래 8비트: 바닐라 번호(주 손 0, 보조 손 5, 발 1, 다리 2, 가슴 3, 머리 4). */
    private static int slotBit(EquipmentSlot s) {
        return switch (s) {
            case MAINHAND -> 0;
            case FEET -> 1;
            case LEGS -> 2;
            case CHEST -> 3;
            case HEAD -> 4;
            case OFFHAND -> 5;
            case BODY -> 6;
        };
    }

    private boolean swapSlot(Player p, EquipmentSlot slot, ItemStack held, int windowSlot) {
        ItemStack cur = getItemBySlot(slot);
        if (!cur.isEmpty() && (disabledSlots & 1 << slotBit(slot) + 8) != 0) return false;
        if (cur.isEmpty() && (disabledSlots & 1 << slotBit(slot) + 16) != 0) return false;
        var inv = p.inventory();
        if (p.vitals().isCreative() && cur.isEmpty() && !held.isEmpty()) {
            setItemSlot(slot, held.withCount(1));
            return true;
        }
        if (held.isEmpty() || held.count() <= 1) {
            setItemSlot(slot, held);
            inv.set(windowSlot, cur);
            return true;
        }
        if (!cur.isEmpty()) return false;
        setItemSlot(slot, held.withCount(1));
        inv.set(windowSlot, held.withCount(held.count() - 1));
        return true;
    }

    // ------------------------------------------------------------------ 피해

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (isDeadOrDying() || isRemoved()) return false;
        if (source.bypassesInvulnerability()) {
            kill();
            return false;
        }
        if (isInvulnerableTo(source) || isInvisible() || isMarker()) return false;
        if (source.explosion()) {
            brokenByAnything();
            kill();
            return false;
        }
        if (DamageType.keyHasTag(source.type(), "ignites_armor_stands")) {
            if (isOnFire()) causeDamage(source, 0.15F);
            else igniteForSeconds(5.0F);
            return false;
        }
        if (DamageType.keyHasTag(source.type(), "burns_armor_stands") && health() > 0.5F) {
            causeDamage(source, 4.0F);
            return false;
        }
        boolean canBreak = DamageType.keyHasTag(source.type(), "can_break_armor_stand");
        boolean alwaysKills = DamageType.keyHasTag(source.type(), "always_kills_armor_stands");
        if (!canBreak && !alwaysKills) return false;
        Targetable direct = pendingDirectOrAttacker(attacker);
        if (direct instanceof Player pl && (pl.gameMode() == org.domi.furnace.server.world.GameMode.ADVENTURE || pl.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR)) return false;
        if (direct instanceof Player pl && pl.vitals().isCreative()) {
            playBrokenSound();
            kill();
            return true;
        }
        long now = level.gameTime();
        if (now - lastHit > 5L && !alwaysKills) {
            broadcastEntityEvent((byte) 32);
            lastHit = now;
        } else {
            brokenByPlayer(attacker);
            kill();
        }
        return true;
    }

    private Targetable pendingDirectOrAttacker(Targetable attacker) {
        Entity d = directCause();
        return d == null ? attacker : d instanceof Targetable t ? t : attacker;
    }

    private void causeDamage(Damage source, float amount) {
        float h = health() - amount;
        if (h <= 0.5F) {
            brokenByAnything();
            kill();
        } else {
            setHealth(h);
        }
    }

    private void brokenByPlayer(Targetable attacker) {
        ItemStack item = new ItemStack(ItemContext.id("minecraft:item", "minecraft:armor_stand"), 1);
        level.popResource(blockPosition(), item);
        brokenByAnything();
    }

    private void brokenByAnything() {
        playBrokenSound();
        Pos above = blockPosition().above();
        for (int i = 0; i < handItems.length; i++) {
            if (!handItems[i].isEmpty()) {
                level.popResource(above, handItems[i]);
                handItems[i] = ItemStack.EMPTY;
            }
        }
        for (int i = 0; i < armorItems.length; i++) {
            if (!armorItems[i].isEmpty()) {
                level.popResource(above, armorItems[i]);
                armorItems[i] = ItemStack.EMPTY;
            }
        }
    }

    private void playBrokenSound() { level.playSound(null, x(), y(), z(), "minecraft:entity.armor_stand.break", soundSource(), 1.0F, 1.0F); }

    @Override
    public void kill() {
        remove(RemovalReason.KILLED);
        gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ENTITY_DIE);
    }

    // ------------------------------------------------------------------ 저장

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        NbtList armor = new NbtList(NbtIo.TAG_COMPOUND), hands = new NbtList(NbtIo.TAG_COMPOUND);
        for (ItemStack s : armorItems) armor.add(ItemStackNbt.write(s));
        for (ItemStack s : handItems) hands.add(ItemStackNbt.write(s));
        tag.putList("ArmorItems", armor);
        tag.putList("HandItems", hands);
        tag.putBoolean("Small", isSmall());
        tag.putBoolean("ShowArms", showArms());
        tag.putInt("DisabledSlots", disabledSlots);
        tag.putBoolean("NoBasePlate", noBasePlate());
        if (isMarker()) tag.putBoolean("Marker", true);
        NbtCompound pose = new NbtCompound();
        for (int i = 0; i < 6; i++) {
            float[] v = pose(i);
            if (!java.util.Arrays.equals(v, POSE_DEFAULTS[i])) {
                NbtList l = new NbtList(NbtIo.TAG_FLOAT);
                for (float f : v) l.add(f);
                pose.putList(POSE_NAMES[i], l);
            }
        }
        tag.putCompound("Pose", pose);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.tags().get("ArmorItems") instanceof NbtList l) {
            for (int i = 0; i < armorItems.length && i < l.size(); i++) armorItems[i] = l.get(i) instanceof NbtCompound c ? ItemStackNbt.read(c) : ItemStack.EMPTY;
        }
        if (tag.tags().get("HandItems") instanceof NbtList l) {
            for (int i = 0; i < handItems.length && i < l.size(); i++) handItems[i] = l.get(i) instanceof NbtCompound c ? ItemStackNbt.read(c) : ItemStack.EMPTY;
        }
        setSmall(tag.getBoolean("Small"));
        setShowArms(tag.getBoolean("ShowArms"));
        disabledSlots = tag.getIntOr0("DisabledSlots");
        setNoBasePlate(tag.getBoolean("NoBasePlate"));
        setMarker(tag.getBoolean("Marker"));
        if (tag.tags().get("Pose") instanceof NbtCompound pose) {
            for (int i = 0; i < 6; i++) {
                float[] v = POSE_DEFAULTS[i].clone();
                if (pose.tags().get(POSE_NAMES[i]) instanceof NbtList l && l.size() == 3) {
                    for (int k = 0; k < 3; k++) if (l.get(k) instanceof Number n) v[k] = n.floatValue();
                }
                setPose(i, v);
            }
        }
    }
}
