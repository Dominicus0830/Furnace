package net.minecraft.world.item.context;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 블록 놓기 문맥(Mojang 이름 shim, T-204). 바닐라 1.21.1 그대로: 생성자가 클릭한 칸을 바꿀 수 있는지(replaceClicked)를 그 레벨에서 읽어 정한다.
 * FAWE {@code PaperweightFaweMutableBlockPlaceContext} 처럼 값 메서드를 재정의하고 {@code replaceClicked} 필드에 쓰는 하위 클래스가 그대로 돈다.
 * 이 문맥으로 부르는 {@code getStateForPlacement}·{@code canBeReplaced} 는 서버 블록 동작을 이 객체의 가상 메서드로 돌린다({@code NmsPlacement}).
 * 플레이어를 바라보는 방향 순서(getNearestLooking*)는 플레이어가 없으면 바닐라처럼 NullPointerException 이다.
 */
public class BlockPlaceContext extends UseOnContext {

    private final BlockPos relativePos;
    protected boolean replaceClicked = true;

    public BlockPlaceContext(Player player, InteractionHand hand, ItemStack stack, BlockHitResult hitResult) {
        this(player.level(), player, hand, stack, hitResult);
    }

    public BlockPlaceContext(UseOnContext ctx) {
        this(ctx.getLevel(), ctx.getPlayer(), ctx.getHand(), ctx.getItemInHand(), ctx.getHitResult());
    }

    protected BlockPlaceContext(Level level, Player player, InteractionHand hand, ItemStack stack, BlockHitResult hitResult) {
        super(level, player, hand, stack, hitResult);
        this.relativePos = hitResult.getBlockPos().relative(hitResult.getDirection());
        this.replaceClicked = level.getBlockState(hitResult.getBlockPos()).canBeReplaced(this);
    }

    /** 바닐라: 그 칸의 dir 면 가운데를 클릭한 문맥. */
    public static BlockPlaceContext at(BlockPlaceContext ctx, BlockPos pos, Direction dir) {
        return new BlockPlaceContext(ctx.getLevel(), ctx.getPlayer(), ctx.getHand(), ctx.getItemInHand(), new BlockHitResult(
                new Vec3(pos.getX() + 0.5 + dir.getStepX() * 0.5, pos.getY() + 0.5 + dir.getStepY() * 0.5, pos.getZ() + 0.5 + dir.getStepZ() * 0.5),
                dir, pos, false));
    }

    @Override
    public BlockPos getClickedPos() { return replaceClicked ? super.getClickedPos() : relativePos; }

    public boolean canPlace() { return replaceClicked || getLevel().getBlockState(getClickedPos()).canBeReplaced(this); }

    public boolean replacingClickedOnBlock() { return replaceClicked; }

    public Direction getNearestLookingDirection() { return nearest(getPlayer())[0]; }

    /** 바닐라 Direction.getFacingAxis(player, Y): 올려다보면 위, 아니면 아래. */
    public Direction getNearestLookingVerticalDirection() {
        Player p = java.util.Objects.requireNonNull(getPlayer());
        return p.getXRot() < 0.0F ? Direction.UP : Direction.DOWN;
    }

    public Direction[] getNearestLookingDirections() {
        Direction[] a = nearest(getPlayer());
        if (replaceClicked) return a;
        Direction f = getClickedFace();
        int i = 0;
        while (i < a.length && a[i] != f.getOpposite()) i++;
        if (i > 0) {
            System.arraycopy(a, 0, a, 1, i);
            a[0] = f.getOpposite();
        }
        return a;
    }

    /** 바닐라 Direction.orderedByNearest(entity) — 플레이어가 없으면 NullPointerException(바닐라). */
    private static Direction[] nearest(Player player) {
        java.util.Objects.requireNonNull(player);
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("BlockPlaceContext#getNearestLookingDirection(s)", "placement with a player is not available yet");
    }
}
