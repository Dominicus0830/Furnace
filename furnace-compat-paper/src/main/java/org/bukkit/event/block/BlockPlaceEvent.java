package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * 플레이어가 블록을 놓는다. CraftBukkit 은 블록을 놓은 뒤에 부르지만 Furnace 는 놓기 전에 불러, {@link #getBlockPlaced()} 가 "놓일 블록"을 보이고
 * {@link #getBlockReplacedState()} 는 그 자리의 원래 상태다. 막는 것은 취소거나 {@link #canBuild()} 가 거짓일 때다.
 */
public class BlockPlaceEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    protected boolean canBuild;
    protected Block placedAgainst;
    protected BlockState replacedBlockState;
    protected ItemStack itemInHand;
    protected Player player;
    protected EquipmentSlot hand;

    @Deprecated
    public BlockPlaceEvent(Block placedBlock, BlockState replacedBlockState, Block placedAgainst, ItemStack itemInHand, Player thePlayer, boolean canBuild) {
        this(placedBlock, replacedBlockState, placedAgainst, itemInHand, thePlayer, canBuild, EquipmentSlot.HAND);
    }

    public BlockPlaceEvent(Block placedBlock, BlockState replacedBlockState, Block placedAgainst, ItemStack itemInHand, Player thePlayer, boolean canBuild,
                           EquipmentSlot hand) {
        super(placedBlock);
        this.placedAgainst = placedAgainst;
        this.itemInHand = itemInHand;
        this.player = thePlayer;
        this.replacedBlockState = replacedBlockState;
        this.canBuild = canBuild;
        this.hand = hand;
    }

    public Player getPlayer() { return player; }

    public Block getBlockPlaced() { return getBlock(); }

    public BlockState getBlockReplacedState() { return replacedBlockState; }

    public Block getBlockAgainst() { return placedAgainst; }

    public ItemStack getItemInHand() { return itemInHand; }

    public EquipmentSlot getHand() { return hand; }

    public boolean canBuild() { return canBuild; }

    public void setBuild(boolean canBuild) { this.canBuild = canBuild; }
}
