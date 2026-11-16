package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.HandlerList;

/**
 * 블록이 어떤 조건으로 만들어진다(콘크리트 가루 굳음, 용암+물이 만든 흑요석·조약돌·현무암 등). BlockGrowEvent 의 하위지만 처리기 목록은 따로다 —
 * BlockGrowEvent 처리기에는 오지 않는다(Bukkit 과 같다). getNewState() 는 바뀐 뒤의 스냅숏이다.
 */
public class BlockFormEvent extends BlockGrowEvent {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    public BlockFormEvent(Block block, BlockState newState) { super(block, newState); }
}
