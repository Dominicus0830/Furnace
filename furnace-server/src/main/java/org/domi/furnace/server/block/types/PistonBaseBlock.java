// 바닐라 PistonBaseBlock: 피스톤·끈끈이 피스톤. 신호가 바뀌면 블록 이벤트(0 늘이기, 1·2 줄이기)를 걸고, triggerEvent 에서
// 밀 블록들을 움직이는 피스톤(+ PistonMovingBlockEntity)으로 바꾼다. 마무리는 블록 엔티티가 2틱 뒤에 한다.
// 바닐라의 HashMap<BlockPos, BlockState> 순회 순서(이웃 갱신 순서를 정한다)를 같은 해시로 재현한다.
package org.domi.furnace.server.block.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.entity.PistonMovingBlockEntity;
import org.domi.furnace.server.entity.ItemStack;

public class PistonBaseBlock extends DirectionalBlock {

    static final Prop EXTENDED = B.prop("extended");
    private static final Prop TYPE = B.prop("type");
    public static final int TRIGGER_EXTEND = 0, TRIGGER_CONTRACT = 1, TRIGGER_DROP = 2;
    private static final int PISTON = B.id("piston"), STICKY_PISTON = B.id("sticky_piston"), PISTON_HEAD = B.id("piston_head"),
            MOVING_PISTON = B.id("moving_piston"), OBSIDIAN = B.id("obsidian"), CRYING_OBSIDIAN = B.id("crying_obsidian"),
            RESPAWN_ANCHOR = B.id("respawn_anchor"), REINFORCED_DEEPSLATE = B.id("reinforced_deepslate");

    private final boolean isSticky;

    public PistonBaseBlock(int block) {
        super(block);
        this.isSticky = block == STICKY_PISTON;
    }

    /** 바닐라 Vec3i.hashCode 를 쓰는 좌표 열쇠 — HashMap 순회 순서를 바닐라와 같게. */
    private record Key(int x, int y, int z) {
        Key(Pos p) { this(p.x(), p.y(), p.z()); }

        Pos pos() { return new Pos(x, y, z); }

        @Override
        public int hashCode() { return (y + z * 31) * 31 + x; }
    }

    @Override
    public void setPlacedBy(BlockLevel level, Pos pos, int state, BlockActor placer, ItemStack stack) { checkIfExtend(level, pos, state); }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) { checkIfExtend(level, pos, state); }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (!B.is(oldState, block) && level.getBlockEntity(pos) == null) checkIfExtend(level, pos, state);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return EXTENDED.with(withFacing(defaultState(), Dir.opposite(ctx.nearestLookingDirection())), false);
    }

    private void checkIfExtend(BlockLevel level, Pos pos, int state) {
        int dir = facing(state);
        boolean signal = getNeighborSignal(level, pos, dir);
        if (signal && !EXTENDED.is(state)) {
            if (new PistonStructureResolver(level, pos, dir, true).resolve()) level.blockEvent(pos, block, TRIGGER_EXTEND, dir);
        } else if (!signal && EXTENDED.is(state)) {
            Pos p = pos.relative(dir, 2);
            int s = level.getBlockState(p);
            int type = TRIGGER_CONTRACT;
            if (B.is(s, MOVING_PISTON) && facing(s) == dir && level.getBlockEntity(p) instanceof PistonMovingBlockEntity m && m.isExtending()
                    && (m.getProgress(0.0F) < 0.5F || level.gameTime() == m.getLastTicked() || level.isHandlingTick())) {
                type = TRIGGER_DROP;
            }
            level.blockEvent(pos, block, type, dir);
        }
    }

    /** getNeighborSignal: 앞쪽을 뺀 다섯 이웃, 자기 칸 아래, 위 칸의 이웃(아래 제외)에서 신호 — 위 칸 신호(준연결)도 본다. */
    private static boolean getNeighborSignal(BlockLevel level, Pos pos, int facing) {
        for (int d : Dir.ALL) if (d != facing && level.hasSignal(pos.relative(d), d)) return true;
        if (level.hasSignal(pos, Dir.DOWN)) return true;
        Pos above = pos.above();
        for (int d : Dir.ALL) if (d != Dir.DOWN && level.hasSignal(above.relative(d), d)) return true;
        return false;
    }

    @Override
    public boolean triggerEvent(int state, BlockLevel level, Pos pos, int type, int data) {
        int dir = facing(state);
        int extended = EXTENDED.with(state, true);
        boolean signal = getNeighborSignal(level, pos, dir);
        if (signal && (type == TRIGGER_CONTRACT || type == TRIGGER_DROP)) {
            level.setBlock(pos, extended, 2);
            return false;
        }
        if (!signal && type == TRIGGER_EXTEND) return false;

        if (type == TRIGGER_EXTEND) {
            if (!moveBlocks(level, pos, dir, true)) return false;
            level.setBlock(pos, extended, 67);
            level.playSound(null, pos, "minecraft:block.piston.extend", "blocks", 0.5F, level.random().nextFloat() * 0.25F + 0.6F);
            level.gameEvent("minecraft:block_activate", pos, null, extended);
        } else {
            if (level.getBlockEntity(pos.relative(dir)) instanceof PistonMovingBlockEntity m) m.finalTick(level);
            int moving = TYPE.with(MovingPistonBlock.FACING.with(B.defaultState(MOVING_PISTON), Dir.NAME[dir]), isSticky ? "sticky" : "normal");
            level.setBlock(pos, moving, 20);
            level.setBlockEntity(new PistonMovingBlockEntity(pos, moving, withFacing(defaultState(), data & 7), dir, false, true));
            level.blockUpdated(pos, MOVING_PISTON);
            B.updateNeighbourShapes(moving, level, pos, 2);
            if (isSticky) {
                Pos p = pos.offset(Dir.DX[dir] * 2, Dir.DY[dir] * 2, Dir.DZ[dir] * 2);
                int s = level.getBlockState(p);
                boolean finished = false;
                if (B.is(s, MOVING_PISTON) && level.getBlockEntity(p) instanceof PistonMovingBlockEntity m
                        && m.getDirection() == dir && m.isExtending()) {
                    m.finalTick(level);
                    finished = true;
                }
                if (!finished) {
                    if (type != TRIGGER_CONTRACT || B.isAir(s) || !isPushable(s, level, p, Dir.opposite(dir), false, dir)
                            || !B.INFO.pushReaction(B.block(s)).equals("normal") && !B.is(s, PISTON) && !B.is(s, STICKY_PISTON)) {
                        level.removeBlock(pos.relative(dir), false);
                    } else {
                        moveBlocks(level, pos, dir, false);
                    }
                }
            } else {
                level.removeBlock(pos.relative(dir), false);
            }
            level.playSound(null, pos, "minecraft:block.piston.contract", "blocks", 0.5F, level.random().nextFloat() * 0.15F + 0.6F);
            level.gameEvent("minecraft:block_deactivate", pos, null, moving);
        }
        return true;
    }

    /** isPushable(state, level, pos, motionDir, allowDestroy, pistonFacing). */
    public static boolean isPushable(int state, BlockLevel level, Pos pos, int motionDir, boolean allowDestroy, int pistonFacing) {
        if (pos.y() < level.minBuildHeight() || pos.y() > level.maxBuildHeight() - 1 || !level.withinBorder(pos)) return false;
        if (B.isAir(state)) return true;
        int b = B.block(state);
        if (b == OBSIDIAN || b == CRYING_OBSIDIAN || b == RESPAWN_ANCHOR || b == REINFORCED_DEEPSLATE) return false;
        if (motionDir == Dir.DOWN && pos.y() == level.minBuildHeight()) return false;
        if (motionDir == Dir.UP && pos.y() == level.maxBuildHeight() - 1) return false;
        if (b != PISTON && b != STICKY_PISTON) {
            if (B.INFO.destroyTime(b) == -1.0F) return false;
            switch (B.INFO.pushReaction(b)) {
                case "block": return false;
                case "destroy": return allowDestroy;
                case "push_only": return motionDir == pistonFacing;
                default: break;
            }
        } else if (EXTENDED.is(state)) {
            return false;
        }
        return !B.INFO.hasBlockEntity(state);
    }

    /** moveBlocks: 부술 블록은 드롭하고 공기로, 밀 블록은 한 칸 앞에 움직이는 피스톤으로, 비워진 칸은 공기로 — 그 뒤 모양·이웃 갱신. */
    private boolean moveBlocks(BlockLevel level, Pos pos, int dir, boolean extending) {
        Pos front = pos.relative(dir);
        if (!extending && B.is(level.getBlockState(front), PISTON_HEAD)) level.setBlock(front, B.defaultState("minecraft:air"), 20);
        PistonStructureResolver resolver = new PistonStructureResolver(level, pos, dir, extending);
        if (!resolver.resolve()) return false;
        Map<Key, Integer> deleted = new HashMap<>();
        List<Pos> toPush = resolver.getToPush();
        List<Integer> pushStates = new ArrayList<>();
        for (Pos p : toPush) {
            int s = level.getBlockState(p);
            pushStates.add(s);
            deleted.put(new Key(p), s);
        }
        List<Pos> toDestroy = resolver.getToDestroy();
        int[] all = new int[toPush.size() + toDestroy.size()];
        int moveDir = extending ? dir : Dir.opposite(dir);
        int j = 0;
        for (int k = toDestroy.size() - 1; k >= 0; k--) {
            Pos p = toDestroy.get(k);
            int s = level.getBlockState(p);
            Object be = B.INFO.hasBlockEntity(s) ? level.getBlockEntity(p) : null;
            level.dropResources(s, p, be, null, ItemStack.EMPTY);
            level.setBlock(p, B.defaultState("minecraft:air"), 18);
            level.gameEvent("minecraft:block_destroy", p, null, s);
            // addDestroyBlockEffect 는 클라이언트 쪽(서버 Level 은 아무것도 안 한다)
            all[j++] = s;
        }
        int movingBase = MovingPistonBlock.FACING.with(B.defaultState(MOVING_PISTON), Dir.NAME[dir]);
        for (int l = toPush.size() - 1; l >= 0; l--) {
            Pos p = toPush.get(l);
            int s = level.getBlockState(p);
            Pos to = p.relative(moveDir);
            deleted.remove(new Key(to));
            level.setBlock(to, movingBase, 68);
            level.setBlockEntity(new PistonMovingBlockEntity(to, movingBase, pushStates.get(l), dir, extending, false));
            all[j++] = s;
        }
        if (extending) {
            String t = isSticky ? "sticky" : "normal";
            int head = TYPE.with(MovingPistonBlock.FACING.with(B.defaultState(PISTON_HEAD), Dir.NAME[dir]), t);
            int moving = TYPE.with(movingBase, t);
            deleted.remove(new Key(front));
            level.setBlock(front, moving, 68);
            level.setBlockEntity(new PistonMovingBlockEntity(front, moving, head, dir, true, true));
        }
        int air = B.defaultState("minecraft:air");
        for (Key k : deleted.keySet()) level.setBlock(k.pos(), air, 82);
        for (Map.Entry<Key, Integer> e : deleted.entrySet()) {
            Pos p = e.getKey().pos();
            int s = e.getValue();
            B.behavior(s).updateIndirectNeighbourShapes(s, level, p, 2, BlockLevel.UPDATE_LIMIT);
            B.updateNeighbourShapes(air, level, p, 2);
            B.behavior(air).updateIndirectNeighbourShapes(air, level, p, 2, BlockLevel.UPDATE_LIMIT);
        }
        j = 0;
        for (int k = toDestroy.size() - 1; k >= 0; k--) {
            int s = all[j++];
            Pos p = toDestroy.get(k);
            B.behavior(s).updateIndirectNeighbourShapes(s, level, p, 2, BlockLevel.UPDATE_LIMIT);
            level.updateNeighborsAt(p, B.block(s));
        }
        for (int k = toPush.size() - 1; k >= 0; k--) level.updateNeighborsAt(toPush.get(k), B.block(all[j++]));
        if (extending) level.updateNeighborsAt(front, PISTON_HEAD);
        return true;
    }
}
