// 플레이어가 손에 든 것을 쓰거나 블록을 눌렀을 때 — 클릭한 칸(또는 플레이어)을 가진 region 스레드에서 불린다. 취소하면 아무 일도 일어나지 않는다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.world.Block;

public final class PlayerInteractEvent extends PlayerEvent implements Cancellable {

    public enum Action { RIGHT_CLICK_BLOCK, RIGHT_CLICK_AIR }

    private final Action action;
    private final ItemStack item;
    private final Block block;
    private final String face;
    private final boolean mainHand;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    /** block/face 는 RIGHT_CLICK_AIR 이면 null. face 는 down/up/north/south/west/east. */
    public PlayerInteractEvent(Player player, Action action, ItemStack item, Block block, String face, boolean mainHand) {
        super(player);
        this.action = action;
        this.item = item;
        this.block = block;
        this.face = face;
        this.mainHand = mainHand;
    }

    public Action action() { return action; }

    /** 손에 든 아이템(빈 손이면 EMPTY). */
    public ItemStack item() { return item; }

    public Block clickedBlock() { return block; }

    public String blockFace() { return face; }

    public boolean isMainHand() { return mainHand; }
}
