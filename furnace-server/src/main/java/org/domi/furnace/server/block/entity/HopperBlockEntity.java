// 바닐라 HopperBlockEntity(1.21.1): 5칸 용기. 틱마다 쿨다운을 줄이고, 0 이 되면 켜져 있을 때 먼저 바라보는 쪽 용기로 하나 내보내고(ejectItems)
// 위의 용기·아이템 엔티티에서 하나 빨아들인다(suckInItems). 무엇이든 옮겼으면 쿨다운 8, setChanged(비교기). 빈 깔때기로 넣으면 받는 깔때기에
// 쿨다운 8(같은 틱에 먼저 돈 깔때기에서 받으면 7). 위에 떨어진 아이템 엔티티는 entityInside 로도 빨아들인다.
// 용기 찾기(getContainerAt): 블록 엔티티 용기(상자는 겹상자로), 없으면 그 칸 가운데 1×1×1 안의 용기 엔티티 중 레벨 난수로 하나.
// 면별 규칙(WorldlyContainer): 화로·용광로·훈연기, 양조기, 셜커 상자. 쌓기는 아이템 최대 수량(용기 99 제한은 깔때기 setItem 의 limitSize).
// 생략: 퇴비통(WorldlyContainerHolder)·주크박스·조각된 책장·제작기는 Furnace 에 용기 블록 엔티티가 없어 용기로 보지 않는다.
//   용기 엔티티의 전리품 표는 풀지 않는다(바닐라는 칸을 읽을 때 푼다).
package org.domi.furnace.server.block.entity;

import java.util.List;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.ContainerEntity;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemStackCodecs;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.menu.Stacks;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class HopperBlockEntity extends ContainerBlockEntity implements Hopper {
    public static final int MOVE_ITEM_SPEED = 8;
    public static final int HOPPER_CONTAINER_SIZE = 5;
    /** Hopper.SUCK_AABB = Block.box(0, 11, 0, 16, 32, 16). */
    public static final AABB SUCK_AABB = new AABB(0.0, 11.0 / 16.0, 0.0, 1.0, 2.0, 1.0);

    /** 다음 옮기기까지 남은 틱(바닐라 cooldownTime, 기본 -1). */
    public int cooldownTime = -1;
    /** 마지막으로 틱을 받은 게임 시간(저장 안 함). */
    public long tickedGameTime;

    public HopperBlockEntity(Pos pos, int state) { super("minecraft:hopper", pos, state, HOPPER_CONTAINER_SIZE); }

    @Override public boolean isValid(int state) { return B.is(state, "hopper"); }

    @Override public String defaultTitleKey() { return "container.hopper"; }

    @Override public boolean ticking() { return true; }

    @Override
    public void load(NbtCompound tag) {
        super.load(tag);
        cooldownTime = tag.contains("TransferCooldown") ? tag.getIntOr0("TransferCooldown") : -1;
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        super.saveAdditional(tag);
        tag.putInt("TransferCooldown", cooldownTime);
    }

    // ------------------------------------------------------------------ Hopper(깔때기·깔때기 수레 공통)

    @Override public double levelX() { return pos.x() + 0.5; }

    @Override public double levelY() { return pos.y() + 0.5; }

    @Override public double levelZ() { return pos.z() + 0.5; }

    @Override public boolean isGridAligned() { return true; }

    @Override public Inv hopperInv(BlockLevel level) { return new BlockInv(level, this); }

    private void setCooldown(int t) { cooldownTime = t; }

    private boolean isOnCooldown() { return cooldownTime > 0; }

    private boolean isOnCustomCooldown() { return cooldownTime > 8; }

    // ------------------------------------------------------------------ 틱

    private int facing(int state) { return Dir.byName(B.prop("facing").valueName(state)); }

    private static boolean enabled(int state) { return B.prop("enabled").is(state); }

    /** pushItemsTick. */
    @Override
    public void tick(BlockLevel level) {
        cooldownTime--;
        tickedGameTime = level.gameTime();
        if (!isOnCooldown()) {
            setCooldown(0);
            Inv self = hopperInv(level);
            tryMoveItems(level, self, () -> suckInItems(level, this));
        }
    }

    /** tryMoveItems: 쿨다운이 아니고 켜져 있으면 내보내기 → 빨아들이기. 옮겼으면 쿨다운 8 과 setChanged. */
    private boolean tryMoveItems(BlockLevel level, Inv self, java.util.function.BooleanSupplier suck) {
        int state = level.getBlockState(pos);
        if (!isOnCooldown() && B.is(state, "hopper") && enabled(state)) {
            boolean moved = false;
            if (!self.isEmpty()) moved = ejectItems(level, state, self);
            if (!inventoryFull()) moved |= suck.getAsBoolean();
            if (moved) {
                setCooldown(MOVE_ITEM_SPEED);
                setChanged(level);
                return true;
            }
        }
        return false;
    }

    private boolean inventoryFull() {
        for (ItemStack s : items) if (s.isEmpty() || s.count() != ItemStackCodecs.maxStackSize(s)) return false;
        return true;
    }

    /** ejectItems: 바라보는 쪽 용기가 그 면에서 꽉 차지 않았으면 칸 순서대로 하나씩 넣어 본다. */
    private boolean ejectItems(BlockLevel level, int state, Inv self) {
        int facing = facing(state);
        Inv target = getContainerAt(level, pos.relative(facing));
        if (target == null) return false;
        int dir = Dir.opposite(facing);
        if (isFullContainer(target, dir)) return false;
        for (int i = 0; i < self.size(); i++) {
            ItemStack s = self.get(i);
            if (s.isEmpty()) continue;
            int count = s.count();
            ItemStack rest = addItem(self, target, self.removeItem(i, 1), dir);
            if (rest.isEmpty()) {
                target.setChanged();
                return true;
            }
            self.put(i, s.withCount(count));   // 바닐라: 같은 스택의 수를 되돌린다
            if (count == 1) self.setItem(i, s);
        }
        return false;
    }

    private static int[] getSlots(Inv c, int dir) {
        int[] f = c.slotsForFace(dir);
        if (f != null) return f;
        int[] a = new int[c.size()];
        for (int i = 0; i < a.length; i++) a[i] = i;
        return a;
    }

    private static boolean isFullContainer(Inv c, int dir) {
        for (int i : getSlots(c, dir)) {
            ItemStack s = c.get(i);
            if (s.count() < ItemStackCodecs.maxStackSize(s)) return false;
        }
        return true;
    }

    /**
     * suckInItems(level, hopper): 위 칸 용기가 있으면 아래 면 칸 순서대로 하나 꺼내 보고, 없으면(격자 깔때기는 위 칸이 꽉 찬 충돌 블록이고
     * does_not_block_hoppers 가 아닐 때 막힘) 위의 아이템 엔티티를 넣어 본다.
     */
    public static boolean suckInItems(BlockLevel level, Hopper hopper) {
        Pos above = new Pos((int) Math.floor(hopper.levelX()), (int) Math.floor(hopper.levelY() + 1.0), (int) Math.floor(hopper.levelZ()));   // BlockPos.containing
        int state = level.getBlockState(above);
        Inv self = hopper.hopperInv(level);
        Inv source = getContainerAt(level, above, state, hopper.levelX(), hopper.levelY() + 1.0, hopper.levelZ());
        if (source != null) {
            for (int slot : getSlots(source, Dir.DOWN)) {
                if (tryTakeInItemFromSlot(self, source, slot, Dir.DOWN)) return true;
            }
            return false;
        }
        boolean blocked = hopper.isGridAligned() && B.isCollisionShapeFullBlock(state) && !B.isTag(state, "minecraft:does_not_block_hoppers");
        if (!blocked) {
            for (ItemEntity ie : getItemsAtAndAbove(level, hopper)) {
                if (addItem(self, ie)) return true;
            }
        }
        return false;
    }

    private static boolean tryTakeInItemFromSlot(Inv hopper, Inv source, int slot, int dir) {
        ItemStack s = source.get(slot);
        if (!s.isEmpty() && canTakeItemFromContainer(hopper, source, s, slot, dir)) {
            int count = s.count();
            ItemStack rest = addItem(source, hopper, source.removeItem(slot, 1), -1);
            if (rest.isEmpty()) {
                source.setChanged();
                return true;
            }
            source.put(slot, s.withCount(count));
            if (count == 1) source.setItem(slot, s);
        }
        return false;
    }

    /** addItem(container, itemEntity): 아이템 엔티티의 스택을 넣고, 다 들어가면 엔티티를 지운다. */
    public static boolean addItem(Inv container, ItemEntity ie) {
        boolean all = false;
        ItemStack rest = addItem(null, container, ie.getItem(), -1);
        if (rest.isEmpty()) {
            all = true;
            ie.setItem(ItemStack.EMPTY);
            ie.discard();
        } else {
            ie.setItem(rest);
        }
        return all;
    }

    /** addItem(source, destination, stack, face): 면이 있고 면별 용기면 그 면 칸만, 아니면 모든 칸에 차례로. 남은 것을 돌려준다(dir -1 = null). */
    public static ItemStack addItem(Inv source, Inv dest, ItemStack stack, int dir) {
        int[] face = dir >= 0 ? dest.slotsForFace(dir) : null;
        if (face != null) {
            for (int i = 0; i < face.length && !stack.isEmpty(); i++) stack = tryMoveInItem(source, dest, stack, face[i], dir);
        } else {
            int n = dest.size();
            for (int i = 0; i < n && !stack.isEmpty(); i++) stack = tryMoveInItem(source, dest, stack, i, dir);
        }
        return stack;
    }

    private static boolean canPlaceItemInContainer(Inv c, ItemStack s, int slot, int dir) {
        if (!c.canPlaceItem(slot, s)) return false;
        return !(c.worldly() && !c.canPlaceItemThroughFace(slot, s, dir));
    }

    private static boolean canTakeItemFromContainer(Inv hopper, Inv source, ItemStack s, int slot, int dir) {
        if (!source.canTakeItem(hopper, slot, s)) return false;
        return !(source.worldly() && !source.canTakeItemThroughFace(slot, s, dir));
    }

    /** tryMoveInItem: 빈 칸이면 통째로, 같은 아이템이면 최대 수량까지 합친다. 빈 깔때기가 받으면 그 깔때기 쿨다운 8(앞서 돈 깔때기에서 받으면 7). */
    private static ItemStack tryMoveInItem(Inv source, Inv dest, ItemStack stack, int slot, int dir) {
        ItemStack cur = dest.get(slot);
        if (canPlaceItemInContainer(dest, stack, slot, dir)) {
            boolean moved = false;
            boolean wasEmpty = dest.isEmpty();
            if (cur.isEmpty()) {
                dest.setItem(slot, stack);
                stack = ItemStack.EMPTY;
                moved = true;
            } else if (canMergeItems(cur, stack)) {
                int room = ItemStackCodecs.maxStackSize(stack) - cur.count();
                int j = Math.min(stack.count(), room);
                if (j > 0) {   // 바닐라는 j ≤ 0 이어도 shrink/grow(0) — 바뀌지 않는다
                    stack = stack.withCount(stack.count() - j);
                    dest.put(slot, cur.withCount(cur.count() + j));
                }
                moved = j > 0;
            }
            if (moved) {
                if (wasEmpty && dest.hopper() instanceof HopperBlockEntity h && !h.isOnCustomCooldown()) {
                    int k = 0;
                    if (source != null && source.hopper() instanceof HopperBlockEntity s && h.tickedGameTime >= s.tickedGameTime) k = 1;
                    h.setCooldown(MOVE_ITEM_SPEED - k);
                }
                dest.setChanged();
            }
        }
        return stack;
    }

    private static boolean canMergeItems(ItemStack a, ItemStack b) {
        return a.count() <= ItemStackCodecs.maxStackSize(a) && Stacks.isSameItemSameComponents(a, b);
    }

    /** getItemsAtAndAbove: 빨아들이는 상자(기준 좌표 - 0.5 만큼 옮김) 안의 살아 있는 아이템 엔티티. */
    public static List<ItemEntity> getItemsAtAndAbove(BlockLevel level, Hopper hopper) {
        if (!(level instanceof ChunkLevel cl)) return List.of();
        AABB box = hopper.suckAabb().move(hopper.levelX() - 0.5, hopper.levelY() - 0.5, hopper.levelZ() - 0.5);
        return cl.getEntitiesOfClass(ItemEntity.class, box, Entity::isAlive);
    }

    /** entityInside: 아이템 엔티티가 빨아들이는 상자에 닿으면 넣어 본다(쿨다운·켜짐 조건은 tryMoveItems 그대로). */
    public void entityInside(BlockLevel level, Entity entity) {
        if (entity instanceof ItemEntity ie && !ie.getItem().isEmpty()
                && entity.boundingBox().move(-pos.x(), -pos.y(), -pos.z()).intersects(SUCK_AABB)) {
            Inv self = hopperInv(level);
            tryMoveItems(level, self, () -> addItem(self, ie));
        }
    }

    // ------------------------------------------------------------------ 용기 찾기

    /** getContainerAt(level, pos): 그 칸의 블록 용기, 없으면 칸 가운데의 용기 엔티티. */
    public static Inv getContainerAt(BlockLevel level, Pos p) {
        return getContainerAt(level, p, level.getBlockState(p), p.x() + 0.5, p.y() + 0.5, p.z() + 0.5);
    }

    private static Inv getContainerAt(BlockLevel level, Pos p, int state, double x, double y, double z) {
        Inv c = getBlockContainer(level, p, state);
        if (c == null) c = getEntityContainer(level, x, y, z);
        return c;
    }

    private static Inv getBlockContainer(BlockLevel level, Pos p, int state) {
        if (B.isAir(state)) return null;
        Object be = level.getBlockEntity(p);
        if (!(be instanceof ContainerBlockEntity c) || be instanceof CampfireBlockEntity) return null;   // 모닥불은 바닐라 Container 가 아니다
        if (be instanceof org.domi.furnace.server.world.ChestBlockEntity
                && BlockBehaviors.of(state) instanceof org.domi.furnace.server.block.types.ChestBlock cb) {
            // ChestBlock.getContainer(…, ignoreBlocked = true): 겹상자면 FIRST(오른쪽) 가 앞
            Pos np = cb.doubleChestPartner(level, p, state);
            if (np != null && level.getBlockEntity(np) instanceof ContainerBlockEntity other) {
                Inv self = new BlockInv(level, c), o = new BlockInv(level, other);
                return cb.isRightChest(state) ? new CompoundInv(self, o) : new CompoundInv(o, self);
            }
        }
        return new BlockInv(level, c);
    }

    /** getEntityContainer: 칸 가운데 1×1×1 에 걸친 살아 있는 용기 엔티티 중 레벨 난수로 하나. */
    private static Inv getEntityContainer(BlockLevel level, double x, double y, double z) {
        if (!(level instanceof ChunkLevel cl)) return null;
        List<Entity> list = cl.getEntities(null, new AABB(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5),
                e -> e instanceof ContainerEntity && e.isAlive());
        if (list.isEmpty()) return null;
        return new EntityInv((ContainerEntity) list.get(level.random().nextInt(list.size())));
    }

    // ------------------------------------------------------------------ 용기 보기(바닐라 Container / WorldlyContainer)

    /** 깔때기가 보는 용기. ItemStack 이 불변이라 바닐라의 "그 자리에서 수 바꾸기"는 put 으로. */
    public interface Inv {
        int size();

        ItemStack get(int i);

        /** 바닐라 setItem(부수 효과 포함). */
        void setItem(int i, ItemStack s);

        /** 칸의 스택 수만 바꾼다(부수 효과 없음). */
        void put(int i, ItemStack s);

        /** 바닐라 removeItem(i, n). */
        ItemStack removeItem(int i, int n);

        void setChanged();

        default boolean canPlaceItem(int slot, ItemStack s) { return true; }

        default boolean canTakeItem(Inv target, int slot, ItemStack s) { return true; }

        /** WorldlyContainer 인가. */
        default boolean worldly() { return false; }

        /** getSlotsForFace(dir) — 면별 용기가 아니면 null. */
        default int[] slotsForFace(int dir) { return null; }

        default boolean canPlaceItemThroughFace(int slot, ItemStack s, int dir) { return true; }

        default boolean canTakeItemThroughFace(int slot, ItemStack s, int dir) { return true; }

        /** 이 용기가 깔때기 블록 엔티티면 그것(쿨다운 규칙용). */
        default Object hopper() { return null; }

        default boolean isEmpty() {
            for (int i = 0; i < size(); i++) if (!get(i).isEmpty()) return false;
            return true;
        }
    }

    private static boolean isItem(ItemStack s, String name) { return name.equals(ItemTools.itemName(s)); }

    private static final int[] FURNACE_UP = {0}, FURNACE_DOWN = {2, 1}, FURNACE_SIDES = {1};
    private static final int[] BREWING_UP = {3}, BREWING_DOWN = {0, 1, 2, 3}, BREWING_SIDES = {0, 1, 2, 4};

    /** 블록 엔티티 용기. 쓰기는 그 블록의 region 스레드(깔때기 틱)에서만. */
    static class BlockInv implements Inv {
        final BlockLevel level;
        final ContainerBlockEntity be;

        BlockInv(BlockLevel level, ContainerBlockEntity be) {
            this.level = level;
            this.be = be;
        }

        private void unpack() { if (be.lootTable != null) level.unpackContainerLoot(be); }

        @Override public int size() { return be.items.length; }

        @Override
        public ItemStack get(int i) {
            unpack();
            ItemStack s = be.items[i];
            return s == null ? ItemStack.EMPTY : s;
        }

        @Override
        public void setItem(int i, ItemStack s) {
            unpack();
            ItemStack old = get(i);
            if (be instanceof HopperBlockEntity) {   // 깔때기 setItem: limitSize, setChanged 없음
                int max = Math.min(99, ItemStackCodecs.maxStackSize(s));
                be.items[i] = s.count() > max ? s.withCount(max) : s;
                return;
            }
            be.items[i] = s;
            be.onItemSet(i, old, s, level);
            setChanged();
        }

        @Override public void put(int i, ItemStack s) { be.items[i] = s; }

        @Override
        public ItemStack removeItem(int i, int n) {
            unpack();
            ItemStack cur = get(i);
            if (cur.isEmpty() || n <= 0) return ItemStack.EMPTY;
            int k = Math.min(n, cur.count());
            be.items[i] = cur.withCount(cur.count() - k);
            if (!(be instanceof HopperBlockEntity)) setChanged();   // 깔때기 removeItem 은 setChanged 하지 않는다
            return cur.withCount(k);
        }

        @Override public void setChanged() { be.setChanged(level); }

        @Override public Object hopper() { return be instanceof HopperBlockEntity ? be : null; }

        @Override
        public boolean worldly() {
            return be instanceof FurnaceBlockEntity || be instanceof BrewingStandBlockEntity || be instanceof ShulkerBoxBlockEntity;
        }

        @Override
        public int[] slotsForFace(int dir) {
            if (be instanceof FurnaceBlockEntity) return dir == Dir.DOWN ? FURNACE_DOWN : dir == Dir.UP ? FURNACE_UP : FURNACE_SIDES;
            if (be instanceof BrewingStandBlockEntity) return dir == Dir.UP ? BREWING_UP : dir == Dir.DOWN ? BREWING_DOWN : BREWING_SIDES;
            if (be instanceof ShulkerBoxBlockEntity) {
                int[] a = new int[size()];
                for (int i = 0; i < a.length; i++) a[i] = i;
                return a;
            }
            return null;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack s) {
            if (be instanceof FurnaceBlockEntity) {
                // AbstractFurnaceBlockEntity.canPlaceItem: 결과 칸 불가, 연료 칸은 연료 또는 (연료 칸이 양동이가 아닐 때) 양동이
                if (slot == 2) return false;
                if (slot != 1) return true;
                ItemStack fuel = get(1);
                return org.domi.furnace.server.item.FuelRegistry.isFuel(s) || isItem(s, "minecraft:bucket") && !isItem(fuel, "minecraft:bucket");
            }
            if (be instanceof BrewingStandBlockEntity) {
                // BrewingStandBlockEntity.canPlaceItem: 3 재료, 4 블레이즈 가루, 0~2 물약·유리병(빈 칸일 때)
                if (slot == 3) return org.domi.furnace.server.item.PotionBrewing.isIngredient(s);
                if (slot == 4) return isItem(s, "minecraft:blaze_powder");
                return org.domi.furnace.server.item.PotionBrewing.isBottleSlotItem(s) && get(slot).isEmpty();
            }
            return true;
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack s, int dir) {
            if (be instanceof ShulkerBoxBlockEntity) return !ItemTools.itemName(s).endsWith("shulker_box");   // 셜커 상자 안에 셜커 상자 불가
            return canPlaceItem(slot, s);
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack s, int dir) {
            if (be instanceof FurnaceBlockEntity) {
                return dir != Dir.DOWN || slot != 1 || isItem(s, "minecraft:water_bucket") || isItem(s, "minecraft:bucket");
            }
            if (be instanceof BrewingStandBlockEntity) return slot != 3 || isItem(s, "minecraft:glass_bottle");
            return true;
        }
    }

    /** CompoundContainer(겹상자): 앞 용기 칸 다음 뒤 용기 칸. */
    static final class CompoundInv implements Inv {
        private final Inv a, b;

        CompoundInv(Inv a, Inv b) {
            this.a = a;
            this.b = b;
        }

        @Override public int size() { return a.size() + b.size(); }

        @Override public ItemStack get(int i) { return i >= a.size() ? b.get(i - a.size()) : a.get(i); }

        @Override public void setItem(int i, ItemStack s) { if (i >= a.size()) b.setItem(i - a.size(), s); else a.setItem(i, s); }

        @Override public void put(int i, ItemStack s) { if (i >= a.size()) b.put(i - a.size(), s); else a.put(i, s); }

        @Override public ItemStack removeItem(int i, int n) { return i >= a.size() ? b.removeItem(i - a.size(), n) : a.removeItem(i, n); }

        @Override
        public void setChanged() {
            a.setChanged();
            b.setChanged();
        }

        @Override public boolean canPlaceItem(int i, ItemStack s) { return i >= a.size() ? b.canPlaceItem(i - a.size(), s) : a.canPlaceItem(i, s); }

        @Override public boolean isEmpty() { return a.isEmpty() && b.isEmpty(); }
    }

    /** 용기 엔티티(상자·깔때기 수레, 상자 배). setChanged 는 없다. */
    static final class EntityInv implements Inv {
        private final ItemStack[] items;

        EntityInv(ContainerEntity e) { this.items = e.chestVehicleItems(); }

        @Override public int size() { return items.length; }

        @Override public ItemStack get(int i) { return items[i] == null ? ItemStack.EMPTY : items[i]; }

        @Override public void setItem(int i, ItemStack s) { items[i] = s; }

        @Override public void put(int i, ItemStack s) { items[i] = s; }

        @Override
        public ItemStack removeItem(int i, int n) {
            ItemStack cur = get(i);
            if (cur.isEmpty() || n <= 0) return ItemStack.EMPTY;
            int k = Math.min(n, cur.count());
            items[i] = cur.withCount(cur.count() - k);
            return cur.withCount(k);
        }

        @Override public void setChanged() { }
    }

    /** 깔때기 수레용 칸 보기. */
    public static Inv entityInv(ContainerEntity e) { return new EntityInv(e); }
}
