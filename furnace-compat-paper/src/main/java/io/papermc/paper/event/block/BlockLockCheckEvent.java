// Paper BlockLockCheckEvent: 플레이어가 용기·신호기를 열려 할 때 잠금 검사 직전. 잠기지 않은 블록·관전자에게도 불린다.
// 결과 ALLOW 면 열쇠와 관계없이 열고, DENY 면 막는다(관전자도). DEFAULT 는 바닐라 검사(관전자이거나 열쇠가 맞으면 연다).
// Adventure 판 알림 글·소리(getLockedMessage·setLockedSound·denyWithMessageAndSound)는 없다(NoSuchMethodError) — Furnace API 이벤트에는 있다.
package io.papermc.paper.event.block;

import io.papermc.paper.block.LockableTileState;
import java.util.Objects;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.inventory.ItemStack;

public class BlockLockCheckEvent extends BlockEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;
    private final ItemStack heldKey;
    private ItemStack itemStack;
    private Result result = Result.DEFAULT;

    /** heldKey: 누를 때의 주 손(null 이면 지금 주 손 — Paper 와 같다). 서버 내부용. */
    public BlockLockCheckEvent(Block block, Player player, ItemStack heldKey) {
        super(block);
        this.player = player;
        this.heldKey = heldKey;
    }

    public LockableTileState getBlockState() {
        BlockState state = getBlock().getState();
        if (!(state instanceof LockableTileState lockable)) {
            throw new IllegalStateException("Block state of lock-checked block is no longer a lockable tile state!");
        }
        return lockable;
    }

    public Player getPlayer() { return player; }

    /** 검사에 쓸 열쇠 — 따로 정하지 않았으면 주 손에 든 것. */
    public ItemStack getKeyItem() {
        if (itemStack != null) return itemStack;
        return heldKey != null ? heldKey : player.getInventory().getItemInMainHand();
    }

    public void setKeyItem(ItemStack stack) {
        if (stack == null) throw new IllegalArgumentException("stack cannot be null");
        this.itemStack = stack;
    }

    public void resetKeyItem() { this.itemStack = null; }

    public boolean isUsingCustomKeyItemStack() { return itemStack != null; }

    public Result getResult() { return result; }

    public void setResult(Result result) { this.result = Objects.requireNonNull(result, "result"); }

    @Override
    public HandlerList getHandlers() { return HANDLER_LIST; }

    public static HandlerList getHandlerList() { return HANDLER_LIST; }
}
