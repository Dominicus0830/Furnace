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
 * 블록에 대고 쓰기 문맥(Mojang 이름 shim, T-204). 필드·메서드는 바닐라 1.21.1 과 같다. 플레이어를 받는 생성자는 손에 든 것을 플레이어에서 읽어야 해서
 * 아직 없다({@code UnsupportedNmsCall}) — 놓기 계산은 플레이어 없는 문맥만 서버로 이어진다({@link BlockPlaceContext}).
 */
@org.domi.furnace.compat.nms.NmsInheritable   // 플러그인 문맥(FAWE 가변 문맥)이 상속한다 — 값은 가상 메서드로 읽는다
public class UseOnContext {

    private final Player player;
    private final InteractionHand hand;
    private final BlockHitResult hitResult;
    private final Level level;
    private final ItemStack itemStack;

    public UseOnContext(Player player, InteractionHand hand, BlockHitResult hitResult) {
        this(player.level(), player, hand, heldBy(player, hand), hitResult);
    }

    protected UseOnContext(Level level, Player player, InteractionHand hand, ItemStack stack, BlockHitResult hitResult) {
        this.player = player;
        this.hand = hand;
        this.hitResult = hitResult;
        this.itemStack = stack;
        this.level = level;
    }

    private static ItemStack heldBy(Player player, InteractionHand hand) {
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("UseOnContext(Player, …)", "reading the held item of a player is not available yet");
    }

    protected final BlockHitResult getHitResult() { return hitResult; }

    public BlockPos getClickedPos() { return hitResult.getBlockPos(); }

    public Direction getClickedFace() { return hitResult.getDirection(); }

    public Vec3 getClickLocation() { return hitResult.getLocation(); }

    public boolean isInside() { return hitResult.isInside(); }

    public ItemStack getItemInHand() { return itemStack; }

    public Player getPlayer() { return player; }

    public InteractionHand getHand() { return hand; }

    public Level getLevel() { return level; }

    /** 바닐라: 플레이어가 없으면 북, 있으면 그 플레이어가 바라보는 수평 방향. */
    public Direction getHorizontalDirection() { return player == null ? Direction.NORTH : Direction.fromYRot(player.getYRot()); }

    /** 바닐라: 플레이어가 웅크렸는가(없으면 거짓). */
    public boolean isSecondaryUseActive() {
        if (player == null) return false;
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("UseOnContext#isSecondaryUseActive", "a player's sneaking state is not available yet");
    }

    public float getRotation() { return player == null ? 0.0F : player.getYRot(); }
}
