package org.bukkit.event.block;

import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** 한 번에 여러 칸을 놓는다(침대·문 등). 처리기 목록은 {@link BlockPlaceEvent} 것을 같이 쓴다(Bukkit 과 같다). 첫 칸이 대표 칸이다. */
public class BlockMultiPlaceEvent extends BlockPlaceEvent {

    private final List<BlockState> states;

    @Deprecated
    public BlockMultiPlaceEvent(List<BlockState> states, Block clicked, ItemStack itemInHand, Player thePlayer, boolean canBuild) {
        this(states, clicked, itemInHand, thePlayer, canBuild, EquipmentSlot.HAND);
    }

    public BlockMultiPlaceEvent(List<BlockState> states, Block clicked, ItemStack itemInHand, Player thePlayer, boolean canBuild, EquipmentSlot hand) {
        super(states.get(0).getBlock(), states.get(0), clicked, itemInHand, thePlayer, canBuild, hand);
        this.states = List.copyOf(states);
    }

    /** 바뀌기 전 칸 상태들(바꿀 수 없는 목록). */
    public List<BlockState> getReplacedBlockStates() { return states; }
}
