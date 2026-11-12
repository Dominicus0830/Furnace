// 플레이어가 블록을 놓기 직전 — 놓을 자리를 소유한 region 스레드에서 불린다. 취소하면 놓지 않는다.
package org.domi.furnace.api.event.block;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;

public final class BlockPlaceEvent extends BlockEvent implements Cancellable, org.domi.furnace.api.event.cause.Caused {

    private final Player player;
    private final String placedType;
    private final BlockPos against;
    private final BlockData placedData;
    private final BlockData replacedData;
    private final ItemStack item;
    private final boolean mainHand;
    private boolean cancelled;

    public BlockPlaceEvent(String world, BlockPos pos, String placedType, BlockPos against, Player player) {
        this(world, pos, BlockData.of(placedType), null, against, player, ItemStack.EMPTY, true);
    }

    /** 놓일 블록의 전체 상태·그 자리의 원래 블록(교체될 블록, 모르면 null)·놓는 아이템·손까지 아는 서버 경로. */
    public BlockPlaceEvent(String world, BlockPos pos, BlockData placedData, BlockData replacedData, BlockPos against, Player player, ItemStack item,
                           boolean mainHand) {
        super(world, pos);
        this.placedData = placedData;
        this.replacedData = replacedData;
        this.placedType = placedData.type();
        this.against = against;
        this.player = player;
        this.item = item;
        this.mainHand = mainHand;
    }

    public Player player() { return player; }

    /** [놓은 플레이어 place]. */
    @Override
    public org.domi.furnace.api.event.cause.CauseChain causeChain() { return player == null ? org.domi.furnace.api.event.cause.CauseChain.NONE : org.domi.furnace.api.event.cause.CauseChain.of(player, org.domi.furnace.api.event.cause.CauseEntry.PLACE); }

    /** 놓일 블록(minecraft:oak_planks 등). */
    public String placedType() { return placedType; }

    /** 놓일 블록의 전체 상태(속성 포함). 상태를 모르는 옛 생성자로 만들었으면 종류의 기본 속성 없는 값. */
    public BlockData placedData() { return placedData; }

    /** 그 자리에 원래 있던(놓으면 교체될) 블록 상태. 서버가 알리지 않은 경로면 null. */
    public BlockData replacedData() { return replacedData; }

    /** 놓는 손의 아이템(이벤트 시점 복사본, 모르면 빈 아이템). */
    public ItemStack item() { return item; }

    /** 주로 쓰는 손으로 놓았는가(거짓이면 왼손). */
    public boolean isMainHand() { return mainHand; }

    /** 클릭한(맞닿은) 블록. */
    public BlockPos against() { return against; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
