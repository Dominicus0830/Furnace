// 바닐라 SignBlock: 표지판 바탕. 물에 잠길 수 있다. 글 편집·염료·밀랍 칠은 블록 엔티티(SignBlockEntity) 동작.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;

public abstract class SignBlock extends BaseEntityBlock {

    protected SignBlock(int block) { super(block); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    /** RotationSegment.convertToSegment(rotation): Math.round(rot * 16/360) & 15. */
    static int segment(float rotation) { return Math.round(rotation * (16.0F / 360.0F)) & 15; }

    /** RotationSegment.convertToSegment(Direction): 수평 2D 값 << 2(세로 방향은 0). */
    static int segment(int dir) { return org.domi.furnace.server.block.Dir.horizontal(dir) ? org.domi.furnace.server.block.Dir.to2D(dir) << 2 : 0; }

    /** RotationSegment.convertToDirection: 0 북, 4 동, 8 남, 12 서, 그 외 없음(-1). */
    static int segmentDirection(int seg) {
        return switch (seg) {
            case 0 -> org.domi.furnace.server.block.Dir.NORTH;
            case 4 -> org.domi.furnace.server.block.Dir.EAST;
            case 8 -> org.domi.furnace.server.block.Dir.SOUTH;
            case 12 -> org.domi.furnace.server.block.Dir.WEST;
            default -> -1;
        };
    }

    static boolean isHangingSignItem(ItemStack s) {
        if (s.isEmpty() || !ItemContext.installed()) return false;
        String n = ItemContext.name("minecraft:item", s.itemId());
        return n != null && n.endsWith("_hanging_sign");
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        // TODO(블록엔티티): SignApplicator(염료·발광 먹물·먹물·벌집)를 글 면에 적용 — 바뀌면 CONSUME, 아니면 기본 상호작용으로.
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        // TODO(블록엔티티·메뉴): 밀랍 칠이면 waxed_sign 소리, 클릭 명령 실행, 아니면 글 편집 화면(OpenSignEditor, 바라보는 면).
        //  빈 표지판(블록 엔티티 기본값)은 짓기가 허락된 플레이어에게 편집 화면을 연다.
        return player.mayBuild() ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
