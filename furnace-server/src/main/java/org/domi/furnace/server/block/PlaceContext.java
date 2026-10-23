// 바닐라 UseOnContext + BlockPlaceContext(+ DirectionalPlaceContext). 설치 위치·바라보는 방향 순서를 바닐라 그대로 계산한다.
package org.domi.furnace.server.block;

import org.domi.furnace.server.entity.ItemStack;

public class PlaceContext {

    protected final BlockLevel level;
    protected final BlockActor player;     // null 이면 분배기 등(DirectionalPlaceContext)
    protected final int hand;
    protected final ItemStack stack;
    protected final Hit hit;
    private final Pos relativePos;
    private boolean replaceClicked = true;
    /** DirectionalPlaceContext 의 방향(-1 이면 일반 문맥). */
    protected int direction = -1;

    public PlaceContext(BlockLevel level, BlockActor player, int hand, ItemStack stack, Hit hit) {
        this.level = level;
        this.player = player;
        this.hand = hand;
        this.stack = stack;
        this.hit = hit;
        this.relativePos = hit.pos().relative(hit.face());
        // 바닐라: replaceClicked 가 아직 true 인 채로 판정한다(그 안에서 getClickedPos == 클릭 칸)
        this.replaceClicked = BlockBehaviors.of(level.getBlockState(hit.pos())).canBeReplaced(level.getBlockState(hit.pos()), this);
    }

    /** DirectionalPlaceContext(level, pos, direction, stack, face). */
    public static PlaceContext directional(BlockLevel level, Pos pos, int direction, ItemStack stack, int face) {
        PlaceContext c = new PlaceContext(level, null, BlockActor.MAIN_HAND, stack,
                new Hit(pos, face, pos.x() + 0.5, pos.y(), pos.z() + 0.5, false)) {
            @Override public Pos clickedPos() { return hit.pos(); }
            @Override public boolean canPlace() {
                int s = this.level.getBlockState(hit.pos());
                return BlockBehaviors.of(s).canBeReplaced(s, this);
            }
            @Override public boolean replacingClickedOnBlock() { return canPlace(); }
            @Override public int nearestLookingDirection() { return Dir.DOWN; }
            @Override public int[] nearestLookingDirections() {
                return switch (this.direction) {
                    case Dir.UP -> new int[] {Dir.DOWN, Dir.UP, Dir.NORTH, Dir.EAST, Dir.SOUTH, Dir.WEST};
                    case Dir.NORTH -> new int[] {Dir.DOWN, Dir.NORTH, Dir.EAST, Dir.WEST, Dir.UP, Dir.SOUTH};
                    case Dir.SOUTH -> new int[] {Dir.DOWN, Dir.SOUTH, Dir.EAST, Dir.WEST, Dir.UP, Dir.NORTH};
                    case Dir.WEST -> new int[] {Dir.DOWN, Dir.WEST, Dir.SOUTH, Dir.UP, Dir.NORTH, Dir.EAST};
                    case Dir.EAST -> new int[] {Dir.DOWN, Dir.EAST, Dir.SOUTH, Dir.UP, Dir.NORTH, Dir.WEST};
                    default -> new int[] {Dir.DOWN, Dir.NORTH, Dir.EAST, Dir.SOUTH, Dir.WEST, Dir.UP};
                };
            }
            @Override public int horizontalDirection() { return Dir.axis(this.direction) == Dir.AXIS_Y ? Dir.NORTH : this.direction; }
            @Override public boolean isSecondaryUseActive() { return false; }
            @Override public float rotation() { return Dir.to2D(this.direction) * 90; }
        };
        c.direction = direction;
        return c;
    }

    /** BlockPlaceContext.at(ctx, pos, dir) — 그 칸의 dir 면 가운데를 클릭한 것으로. */
    public static PlaceContext at(PlaceContext c, Pos pos, int dir) {
        return new PlaceContext(c.level, c.player, c.hand, c.stack,
                new Hit(pos, dir, pos.x() + 0.5 + Dir.DX[dir] * 0.5, pos.y() + 0.5 + Dir.DY[dir] * 0.5, pos.z() + 0.5 + Dir.DZ[dir] * 0.5, false));
    }

    // ------------------------------------------------------------------ UseOnContext

    public BlockLevel level() { return level; }

    public BlockActor player() { return player; }

    public int hand() { return hand; }

    public ItemStack itemInHand() { return stack; }

    public Hit hit() { return hit; }

    public int clickedFace() { return hit.face(); }

    public boolean isInside() { return hit.inside(); }

    public double clickX() { return hit.x(); }

    public double clickY() { return hit.y(); }

    public double clickZ() { return hit.z(); }

    /** getHorizontalDirection: 행위자 방향(없으면 북). */
    public int horizontalDirection() { return player == null ? Dir.NORTH : player.direction(); }

    public boolean isSecondaryUseActive() { return player != null && player.isSecondaryUseActive(); }

    /** getRotation: 행위자 yRot 그대로. */
    public float rotation() { return player == null ? 0f : player.yRot(); }

    // ------------------------------------------------------------------ BlockPlaceContext

    /** 놓을 칸: 클릭한 칸이 바뀔 수 있으면 그 칸, 아니면 면 너머. */
    public Pos clickedPos() { return replaceClicked ? hit.pos() : relativePos; }

    public boolean canPlace() {
        if (replaceClicked) return true;
        int s = level.getBlockState(clickedPos());
        return BlockBehaviors.of(s).canBeReplaced(s, this);
    }

    public boolean replacingClickedOnBlock() { return replaceClicked; }

    public int nearestLookingDirection() { return Dir.orderedByNearest(player.xRot(), player.yRot())[0]; }

    public int nearestLookingVerticalDirection() { return Dir.facingAxis(player.xRot(), player.yRot(), Dir.AXIS_Y); }

    public int[] nearestLookingDirections() {
        int[] a = Dir.orderedByNearest(player.xRot(), player.yRot());
        if (replaceClicked) return a;
        int f = Dir.opposite(clickedFace());
        int i = 0;
        while (i < a.length && a[i] != f) i++;
        if (i > 0) {
            System.arraycopy(a, 0, a, 1, i);
            a[0] = f;
        }
        return a;
    }

    /** 손에 든 아이템이 그 블록의 asItem 인가(canBeReplaced 기본 판정 — ItemStack.is(block.asItem())). */
    public boolean holding(int block) {
        return !stack.isEmpty() && stack.itemId() == BlockItems.asItemId(block);
    }
}
