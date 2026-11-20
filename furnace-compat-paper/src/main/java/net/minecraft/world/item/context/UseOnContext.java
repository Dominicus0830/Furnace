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
 * 블록에 대고 쓰기 문맥(Mojang 이름 shim, T-204). 필드·메서드는 바닐라 1.21.1 과 같다. 플레이어를 받는 생성자는 그 플레이어의 손(바닐라
 * {@code getItemInHand})을 읽는다(T-212 — 지금은 바닐라 생성자로 만든 월드에 없는 플레이어만 손을 읽을 수 있다).
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

    private static ItemStack heldBy(Player player, InteractionHand hand) { return player.getItemInHand(hand); }

    protected final BlockHitResult getHitResult() { return hitResult; }

    /** 호환 모듈 전용 — {@link #getHitResult()}(바닐라는 protected). */
    public BlockHitResult furnaceHitResult() { return hitResult; }

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
    public boolean isSecondaryUseActive() { return player != null && player.isSecondaryUseActive(); }

    public float getRotation() { return player == null ? 0.0F : player.getYRot(); }
}
