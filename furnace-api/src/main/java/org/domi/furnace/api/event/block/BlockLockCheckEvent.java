// 플레이어가 용기나 신호기를 열려 할 때 잠금 검사 직전에(Paper BlockLockCheckEvent) 그 블록을 소유한 region 스레드에서 불린다.
// 잠기지 않은 블록이나 관전자에게도 불린다. 큰 상자는 반쪽마다 한 번씩 불린다. 첫째는 type=right 상자이고, 막히면 둘째는 불리지 않는다.
package org.domi.furnace.api.event.block;

import java.util.Objects;
import org.domi.furnace.api.audience.Sound;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.BlockPos;

public final class BlockLockCheckEvent extends BlockEvent {

    /** ALLOW: 열쇠와 관계없이 연다. DENY: 막는다(관전자도). DEFAULT: 바닐라 검사(관전자이거나 열쇠가 맞으면 연다). */
    public enum Result { ALLOW, DEFAULT, DENY }

    private final Player player;
    private final String lock;
    private final ItemStack heldKey;
    private ItemStack customKey;
    private Result result = Result.DEFAULT;
    private Text lockedMessage;
    private Sound lockedSound;

    public BlockLockCheckEvent(String world, BlockPos pos, Player player, String lock, ItemStack heldKey, Text lockedMessage, Sound lockedSound) {
        super(world, pos);
        this.player = player;
        this.lock = lock;
        this.heldKey = heldKey == null ? ItemStack.EMPTY : heldKey;
        this.lockedMessage = lockedMessage;
        this.lockedSound = lockedSound;
    }

    public Player player() { return player; }

    /** 블록의 열쇠 이름(잠기지 않았으면 null). */
    public String lock() { return lock; }

    /** 블록을 누를 때 주 손에 든 것(빈 손이면 빈 아이템). */
    public ItemStack heldItem() { return heldKey; }

    /** 검사에 쓸 열쇠. 따로 정하지 않았으면 블록을 누를 때 주 손에 든 것이다. */
    public ItemStack keyItem() { return customKey != null ? customKey : heldKey; }

    public void setKeyItem(ItemStack key) { this.customKey = Objects.requireNonNull(key, "key"); }

    /** 따로 정한 열쇠를 지우고 주 손으로 되돌린다. */
    public void resetKeyItem() { this.customKey = null; }

    public boolean isUsingCustomKeyItem() { return customKey != null; }

    public Result result() { return result; }

    public void setResult(Result result) { this.result = Objects.requireNonNull(result, "result"); }

    /** 막힐 때 액션 바에 띄울 글(null 이면 안 띄운다). 기본은 바닐라 container.isLocked. */
    public Text lockedMessage() { return lockedMessage; }

    public void setLockedMessage(Text lockedMessage) { this.lockedMessage = lockedMessage; }

    /** 막힐 때 그 플레이어에게만 들릴 소리(null 이면 없음). 기본은 block.chest.locked. */
    public Sound lockedSound() { return lockedSound; }

    public void setLockedSound(Sound lockedSound) { this.lockedSound = lockedSound; }

    /** 막고 알림 글·소리를 정한다. */
    public void denyWithMessageAndSound(Text lockedMessage, Sound lockedSound) {
        this.result = Result.DENY;
        this.lockedMessage = lockedMessage;
        this.lockedSound = lockedSound;
    }
}
