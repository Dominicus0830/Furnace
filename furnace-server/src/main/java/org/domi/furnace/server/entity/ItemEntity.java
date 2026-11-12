// 바닐라 ItemEntity: 땅에 떨어진 아이템. 중력 0.04·공기 저항 0.98·디딘 블록 미끄러움, 물·용암에서 뜨기, 블록에 끼면 빈 쪽으로
// 밀려나기, 가까운 같은 아이템과 합치기(움직였으면 2틱·아니면 40틱마다), 6000틱 뒤 사라짐, 체력 5(불·선인장·폭발), 플레이어 줍기.
package org.domi.furnace.server.entity;

import java.util.UUID;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemStackCodecs;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public final class ItemEntity extends Entity {
    @Override public boolean isAttackable() { return false; }


    public static final int DATA_ITEM = 8;

    private static final int LIFETIME = 6000, INFINITE_PICKUP_DELAY = 32767, INFINITE_LIFETIME = -32768;

    private int age;
    private int pickupDelay;
    private int health = 5;
    private UUID thrower;
    private UUID target;
    public final float bobOffs;

    public ItemEntity(ChunkLevel level, int id) {
        super(EntityType.ITEM, level, id);
        this.bobOffs = random.nextFloat() * (float) Math.PI * 2.0F;
        setYRot(random.nextFloat() * 360.0F);
    }

    /** ItemEntity(level, x, y, z, stack): 속도는 레벨 난수로(x·z ±0.1, y 0.2). */
    public ItemEntity(ChunkLevel level, int id, double x, double y, double z, ItemStack stack) {
        this(level, id, x, y, z, stack, level.random().nextDouble() * 0.2 - 0.1, 0.2, level.random().nextDouble() * 0.2 - 0.1);
    }

    public ItemEntity(ChunkLevel level, int id, double x, double y, double z, ItemStack stack, double vx, double vy, double vz) {
        this(level, id);
        setPos(x, y, z);
        setDeltaMovement(vx, vy, vz);
        setItem(stack);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_ITEM, SetEntityData.SERIALIZER_ITEM, ItemStack.EMPTY);
    }

    public ItemStack getItem() { return (ItemStack) entityData.get(DATA_ITEM); }

    /** dampensVibrations: 아이템이 #dampens_vibrations(양털 등)면. */
    @Override
    public boolean dampensVibrations() { return org.domi.furnace.server.item.ItemTags.is(getItem(), "minecraft:dampens_vibrations"); }

    public void setItem(ItemStack stack) { entityData.set(DATA_ITEM, stack); }

    /** 바닐라는 스택을 제자리에서 줄인다(dirty 없이) — 클라이언트는 다음 갱신 전까지 옛 개수를 본다. */
    private void shrinkInPlace(ItemStack now) { entityData.setQuiet(DATA_ITEM, now); }

    @Override
    protected double defaultGravity() { return 0.04; }

    @Override
    public boolean fireImmune() { return ItemTools.component(getItem(), "minecraft:fire_resistant") != null || super.fireImmune(); }

    /** 아이템은 0.999999 아래 블록의 미끄러움을 본다. */
    @Override
    public Pos getBlockPosBelowThatAffectsMyMovement() { return getOnPos(0.999999F); }

    @Override
    public void tick() {
        if (getItem().isEmpty()) {
            discard();
            return;
        }
        super.tick();
        if (pickupDelay > 0 && pickupDelay != INFINITE_PICKUP_DELAY) pickupDelay--;
        xo = x();
        yo = y();
        zo = z();
        double pvx = vx(), pvy = vy(), pvz = vz();
        if (isInWater() && fluidHeight(true) > 0.1F) {
            setUnderwaterMovement();
        } else if (isInLava() && fluidHeight(false) > 0.1F) {
            setUnderLavaMovement();
        } else {
            applyGravity();
        }
        noPhysics = !level.noCollision(this, boundingBox().deflate(1.0E-7));
        if (noPhysics) moveTowardsClosestSpace(x(), (boundingBox().minY() + boundingBox().maxY()) / 2.0, z());
        if (!onGround() || vx() * vx() + vz() * vz() > 1.0E-5F || (tickCount + id()) % 4 == 0) {
            move(MOVER_SELF, vx(), vy(), vz());
            float f = 0.98F;
            if (onGround()) f = frictionBelow() * 0.98F;
            setDeltaMovement(vx() * f, vy() * 0.98, vz() * f);
            if (onGround() && vy() < 0.0) setDeltaMovement(vx(), vy() * -0.5, vz());
        }
        boolean moved = floor(xo) != floor(x()) || floor(yo) != floor(y()) || floor(zo) != floor(z());
        int interval = moved ? 2 : 40;
        if (tickCount % interval == 0 && isMergable()) mergeWithNeighbours();
        if (age != INFINITE_LIFETIME) age++;
        hasImpulse |= updateInWaterStateAndDoFluidPushing();
        double ddx = vx() - pvx, ddy = vy() - pvy, ddz = vz() - pvz;
        if (ddx * ddx + ddy * ddy + ddz * ddz > 0.01) hasImpulse = true;
        if (age >= LIFETIME) discard();
    }

    private void setUnderwaterMovement() {
        setDeltaMovement(vx() * 0.99F, vy() + (double) (vy() < 0.06F ? 5.0E-4F : 0.0F), vz() * 0.99F);
    }

    private void setUnderLavaMovement() {
        setDeltaMovement(vx() * 0.95F, vy() + (double) (vy() < 0.06F ? 5.0E-4F : 0.0F), vz() * 0.95F);
    }

    private void mergeWithNeighbours() {
        if (!isMergable()) return;
        for (ItemEntity other : level.getEntitiesOfClass(ItemEntity.class, boundingBox().inflate(0.5, 0.0, 0.5), e -> e != this && e.isMergable())) {
            if (other.isMergable()) {
                tryToMerge(other);
                if (isRemoved()) break;
            }
        }
    }

    private boolean isMergable() {
        ItemStack s = getItem();
        return isAlive() && pickupDelay != INFINITE_PICKUP_DELAY && age != INFINITE_LIFETIME && age < LIFETIME && s.count() < ItemStackCodecs.maxStackSize(s);
    }

    private void tryToMerge(ItemEntity other) {
        ItemStack mine = getItem(), theirs = other.getItem();
        if (java.util.Objects.equals(target, other.target) && areMergable(mine, theirs)) {
            if (theirs.count() < mine.count()) merge(this, mine, other, theirs);
            else merge(other, theirs, this, mine);
        }
    }

    /** areMergable(a, b): 합친 개수가 b 의 최대 이하이고 같은 아이템·컴포넌트. */
    public static boolean areMergable(ItemStack a, ItemStack b) {
        return b.count() + a.count() <= ItemStackCodecs.maxStackSize(b) && a.sameItemSameComponents(b);
    }

    private static void merge(ItemEntity dest, ItemStack destStack, ItemEntity src, ItemStack srcStack) {
        int n = Math.min(Math.min(ItemStackCodecs.maxStackSize(destStack), 64) - destStack.count(), srcStack.count());
        dest.setItem(destStack.withCount(destStack.count() + n));
        ItemStack left = srcStack.withCount(srcStack.count() - n);
        src.shrinkInPlace(left);
        dest.pickupDelay = Math.max(dest.pickupDelay, src.pickupDelay);
        dest.age = Math.min(dest.age, src.age);
        if (left.isEmpty()) src.discard();
    }

    @Override
    public boolean hurt(Damage source, float amount) { return hurtBy(source, amount, null); }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (isInvulnerableTo(source)) return false;
        ItemStack s = getItem();
        if (!s.isEmpty() && "minecraft:nether_star".equals(ItemTools.itemName(s)) && source.explosion()) return false;
        if (source.fire() && ItemTools.component(s, "minecraft:fire_resistant") != null) return false;   // canBeHurtBy
        markHurt();
        health = (int) ((float) health - amount);
        gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ENTITY_DAMAGE, attacker);   // cjh: source.getEntity()
        if (health <= 0) {
            onDestroyed(s);
            discard();
        }
        return true;
    }

    /** Item.onDestroyed(itemEntity): 상자류(container)·꾸러미(bundle_contents)는 안의 아이템을 그 자리에 떨군다. */
    private void onDestroyed(ItemStack s) {
        Object contents = ItemTools.component(s, "minecraft:container");
        if (contents == null) contents = ItemTools.component(s, "minecraft:bundle_contents");
        if (!(contents instanceof java.util.List<?> list)) return;
        for (Object o : list) {
            ItemStack inner = o instanceof ItemStack is ? is : o instanceof java.util.Map<?, ?> m && m.get("item") instanceof ItemStack is ? is : null;
            if (inner != null && !inner.isEmpty()) level.addFreshEntity(new ItemEntity(level, level.nextEntityId(), x(), y(), z(), inner));
        }
    }

    // ------------------------------------------------------------------ 줍기(playerTouch)

    @Override
    public void playerTouch(Player player) {
        ItemStack s = getItem();
        int count = s.count();
        if (pickupDelay != 0 || target != null && !target.equals(player.uuid())) return;
        if (!level.gameEvents().playerPickup(player, s)) return;   // 플러그인 PlayerPickupItemEvent 취소
        ItemStack left = player.inventory().add(s, player.hasInfiniteMaterials(), ItemStackCodecs.maxStackSize(s), ItemTools.damage(s) > 0);
        // Inventory.add 는 마지막 시도에서 다 넣었을 때만 참이다 — 일부만 들어가면 스택만 줄고 줍기 처리는 하지 않는다
        if (!left.isEmpty()) {
            if (left.count() != count) shrinkInPlace(left);
            return;
        }
        player.take(this, count);
        discard();
        player.awardStat("minecraft:picked_up:" + ItemTools.itemName(s), count);
        AdvTriggers.pickedUpByPlayer(this, s.withCount(count), player);   // 바닐라: ServerPlayer.onItemPickup(count 되돌린 스택)
    }

    // ------------------------------------------------------------------ 줍기 지연·수명

    public void setDefaultPickUpDelay() { pickupDelay = 10; }

    public void setNoPickUpDelay() { pickupDelay = 0; }

    public void setNeverPickUp() { pickupDelay = INFINITE_PICKUP_DELAY; }

    public void setPickUpDelay(int ticks) { pickupDelay = ticks; }

    /** 몹이 주울 때 줍기 애니메이션 패킷(take_item_entity)을 보낸다. */
    public void broadcastTake(LivingEntity taker, int count) {
        if (tracker != null) tracker.broadcast(new org.domi.furnace.server.net.protocol.play.TakeItemEntity(id(), taker.id(), count));
    }

    public boolean hasPickUpDelay() { return pickupDelay > 0; }

    public int pickupDelay() { return pickupDelay; }

    public void setUnlimitedLifetime() { age = INFINITE_LIFETIME; }

    public void setExtendedLifetime() { age = -6000; }

    public void makeFakeItem() {
        setNeverPickUp();
        age = LIFETIME - 1;
    }

    public int age() { return age; }

    public void setThrower(Entity e) { thrower = e.uuid(); }

    public void setThrower(UUID u) { thrower = u; }

    public UUID thrower() { return thrower; }

    /** ItemEntity.getOwner: 던진 것(UUID 로 찾는다, 없으면 null). */
    public Targetable getOwnerEntity() { return thrower == null || level() == null ? null : level().getTargetableByUuid(thrower); }

    public void setTarget(UUID u) { target = u; }

    // ------------------------------------------------------------------ 저장

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        tag.putShort("Health", (short) health);
        tag.putShort("Age", (short) age);
        tag.putShort("PickupDelay", (short) pickupDelay);
        if (thrower != null) tag.putIntArray("Thrower", uuidToInts(thrower));
        if (target != null) tag.putIntArray("Owner", uuidToInts(target));
        ItemStack s = getItem();
        if (!s.isEmpty()) {
            NbtCompound it = new NbtCompound();
            it.putString("id", ItemTools.itemName(s));
            it.putInt("count", s.count());
            NbtCompound components = org.domi.furnace.server.item.ItemNbt.write(s.patch());
            if (components != null) it.putCompound(org.domi.furnace.server.item.ItemNbt.KEY, components);
            tag.putCompound("Item", it);
        }
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        health = tag.getShort("Health");
        age = tag.getShort("Age");
        if (tag.contains("PickupDelay")) pickupDelay = tag.getShort("PickupDelay");
        if (tag.tags().get("Owner") instanceof int[] o && o.length == 4) target = ints(o);
        if (tag.tags().get("Thrower") instanceof int[] t && t.length == 4) thrower = ints(t);
        ItemStack s = ItemStack.EMPTY;
        if (tag.tags().get("Item") instanceof NbtCompound it && it.tags().get("id") instanceof String name) {
            Integer itemId = ItemContext.id("minecraft:item", name);
            int count = it.tags().get("count") instanceof Number c ? c.intValue() : 1;
            if (itemId != null && itemId > 0 && count > 0) {
                var patch = org.domi.furnace.server.item.ItemNbt.read(it, name, "furnace:components");
                if (patch != null) s = new ItemStack(itemId, count, patch);
            }
        }
        setItem(s);
        if (getItem().isEmpty()) discard();
    }

    private static UUID ints(int[] u) {
        return new UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL);
    }
}
