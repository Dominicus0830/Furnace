// 바닐라 CrafterBlock: 신호가 새로 들어오면 4틱 뒤 안의 3×3 재료로 제작해 앞쪽(orientation 의 앞)으로 내보낸다.
// 앞에 용기가 있으면 깔때기 규칙으로 넣고(제작기거나 결과가 용기 한도를 넘으면 한 개씩), 남으면 아이템으로 쏘아 낸다(속도 6, levelEvent 1049·2010).
// 남은 재료(양동이 등)도 같은 방식으로 내보낸 뒤 칸마다 하나씩 줄인다. 제작하면 crafting=true 를 6틱 동안.
// 우클릭하면 제작기 화면(CrafterMenu, crafter_3x3)을 연다.
// 월드에 내놓은 아이템마다 17×17×17 안 플레이어에게 crafter_recipe_crafted(레시피, 만들 때의 재료 칸들).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.block.entity.CrafterBlockEntity;
import org.domi.furnace.server.block.entity.HopperBlockEntity;
import org.domi.furnace.server.entity.ItemStack;

public class CrafterBlock extends BaseEntityBlock {

    private static final Prop CRAFTING = B.prop("crafting"), TRIGGERED = B.prop("triggered"), ORIENTATION = B.prop("orientation");

    public CrafterBlock(int block) { super(block); }

    @Override
    public org.domi.furnace.server.block.entity.BlockEntity newBlockEntity(Pos pos, int state) {
        CrafterBlockEntity be = new CrafterBlockEntity(pos, state);
        be.setTriggered(TRIGGERED.has(state) && TRIGGERED.is(state));
        return be;
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) {
        return level.getBlockEntity(pos) instanceof CrafterBlockEntity be ? be.redstoneSignal() : 0;
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        boolean signal = level.hasNeighborSignal(pos);
        boolean triggered = TRIGGERED.is(state);
        Object be = level.getBlockEntity(pos);
        if (signal && !triggered) {
            level.scheduleTick(pos, block, 4);
            level.setBlock(pos, TRIGGERED.with(state, true), 2);
            if (be instanceof CrafterBlockEntity c) c.setTriggered(true);
        } else if (!signal && triggered) {
            level.setBlock(pos, CRAFTING.with(TRIGGERED.with(state, false), false), 2);
            if (be instanceof CrafterBlockEntity c) c.setTriggered(false);
        }
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) { dispenseFrom(state, level, pos); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int front = Dir.opposite(ctx.nearestLookingDirection());
        int top = switch (front) {
            case Dir.DOWN -> Dir.opposite(ctx.horizontalDirection());
            case Dir.UP -> ctx.horizontalDirection();
            default -> Dir.UP;
        };
        int s = ORIENTATION.with(defaultState(), Dir.NAME[front] + "_" + Dir.NAME[top]);
        return TRIGGERED.with(s, ctx.level().hasNeighborSignal(ctx.clickedPos()));
    }

    @Override
    public void setPlacedBy(BlockLevel level, Pos pos, int state, BlockActor placer, ItemStack stack) {
        if (TRIGGERED.is(state)) level.scheduleTick(pos, block, 4);
    }

    /** Containers.dropContentsOnDestroy: 다른 블록이 되면 내용물을 쏟고 비교기에 알린다. */
    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!B.is(newState, block) && level.getBlockEntity(pos) instanceof CrafterBlockEntity be) {
            level.dropContents(pos, be.items);
            level.updateNeighbourForOutputSignal(pos, block);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        // 바닐라 useWithoutItem: 블록 엔티티가 제작기면 openMenu, 결과는 CONSUME
        if (level.getBlockEntity(pos) instanceof CrafterBlockEntity) player.openContainer(pos);
        return InteractionResult.CONSUME;
    }

    /** orientation 이름("north_up")의 앞 방향. */
    static int front(int state) {
        String n = ORIENTATION.valueName(state);
        return Dir.byName(n.substring(0, n.indexOf('_')));
    }

    /** dispenseFrom: 레시피를 찾지 못하거나 결과가 비면 1050. */
    protected void dispenseFrom(int state, BlockLevel level, Pos pos) {
        if (!(level.getBlockEntity(pos) instanceof CrafterBlockEntity be)) return;
        var input = be.asCraftInput();
        var rm = level.recipes() instanceof org.domi.furnace.server.recipe.RecipeManager m ? m : null;
        var entry = rm == null || input.size() == 0 ? null : rm.findCrafting(input, null);
        if (entry == null) {
            level.levelEvent(null, 1050, pos, 0);
            return;
        }
        ItemStack result = entry.recipe().assemble(input, rm.context());
        if (result.isEmpty()) {
            level.levelEvent(null, 1050, pos, 0);
            return;
        }
        be.setCraftingTicksRemaining(6);
        level.setBlock(pos, CRAFTING.with(state, true), 2);
        result = org.domi.furnace.server.map.MapItems.postProcess(result);   // onCraftedBySystem
        var ingredients = new java.util.ArrayList<org.domi.furnace.server.loot.LootStack>();   // 바닐라 getItems(): 줄이기 전 아홉 칸
        for (ItemStack s : be.items) ingredients.add(org.domi.furnace.server.ServerCore.lootStackOf(s));
        var recipeId = entry.id();
        dispenseItem(level, pos, be, result, state, recipeId, ingredients);
        for (ItemStack rest : rm.remainingItems(input)) {
            if (!rest.isEmpty()) dispenseItem(level, pos, be, rest, state, recipeId, ingredients);
        }
        for (int i = 0; i < be.items.length; i++) {
            ItemStack s = be.items[i];
            if (!s.isEmpty()) be.items[i] = s.withCount(s.count() - 1);
        }
        be.setChanged(level);
    }

    private static void dispenseItem(BlockLevel level, Pos pos, CrafterBlockEntity be, ItemStack stack, int state,
                                     org.domi.furnace.server.data.ResourceId recipeId, java.util.List<org.domi.furnace.server.loot.LootStack> ingredients) {
        int dir = front(state);
        HopperBlockEntity.Inv target = HopperBlockEntity.getContainerAt(level, pos.relative(dir));
        HopperBlockEntity.Inv self = null;   // addItem 의 원천(깔때기 쿨다운 판정에만 쓰인다 — 제작기는 깔때기가 아니다)
        ItemStack copy = stack;
        int face = Dir.opposite(dir);
        if (target != null && (target.blockEntity() instanceof CrafterBlockEntity || stack.count() > target.maxStackSize(stack))) {
            while (!copy.isEmpty()) {
                ItemStack rest = HopperBlockEntity.addItem(self, target, copy.withCount(1), face);
                if (!rest.isEmpty()) break;
                copy = copy.withCount(copy.count() - 1);
            }
        } else if (target != null) {
            while (!copy.isEmpty()) {
                int c = copy.count();
                copy = HopperBlockEntity.addItem(self, target, copy, face);
                if (c == copy.count()) break;
            }
        }
        if (!copy.isEmpty()) {
            double x = pos.x() + 0.5 + Dir.DX[dir] * 0.7, y = pos.y() + 0.5 + Dir.DY[dir] * 0.7, z = pos.z() + 0.5 + Dir.DZ[dir] * 0.7;
            spawnItem(level, copy, 6, dir, x, y, z);
            var box = org.domi.furnace.server.entity.AABB.of(pos.x() + 0.5 - 8.5, pos.y() + 0.5 - 8.5, pos.z() + 0.5 - 8.5,
                    pos.x() + 0.5 + 8.5, pos.y() + 0.5 + 8.5, pos.z() + 0.5 + 8.5);
            level.forEachPlayerIn(box, p -> p.triggerAdvancement(a ->
                    a.trigger("minecraft:crafter_recipe_crafted", new org.domi.furnace.server.advancement.Criterion.RecipeCraftedEvent(recipeId, ingredients))));
            level.levelEvent(null, 1049, pos, 0);
            level.levelEvent(null, 2010, pos, dir);
        }
    }

    /** DefaultDispenseItemBehavior.spawnItem: 높이 보정, ItemEntity 생성(속도 난수 2번), 속도 nextDouble·triangle 3번. */
    public static void spawnItem(BlockLevel level, ItemStack stack, int speed, int dir, double x, double y, double z) {
        double yy = y - (Dir.axis(dir) == Dir.AXIS_Y ? 0.125 : 0.15625);
        RandomSource r = level.random();
        if (level instanceof org.domi.furnace.server.block.ChunkLevel cl) {
            var ie = new org.domi.furnace.server.entity.ItemEntity(cl, cl.nextEntityId(), x, yy, z, stack);
            double d = r.nextDouble() * 0.1 + 0.2;
            ie.setDeltaMovement(r.triangle(Dir.DX[dir] * d, 0.0172275 * speed), r.triangle(0.2, 0.0172275 * speed), r.triangle(Dir.DZ[dir] * d, 0.0172275 * speed));
            cl.addFreshEntity(ie);
        } else {   // 엔티티를 담지 못하는 레벨: 같은 수의 난수만
            r.nextDouble();
            r.nextDouble();
            double d = r.nextDouble() * 0.1 + 0.2;
            r.triangle(Dir.DX[dir] * d, 0.0172275 * speed);
            r.triangle(0.2, 0.0172275 * speed);
            r.triangle(Dir.DZ[dir] * d, 0.0172275 * speed);
        }
    }
}
