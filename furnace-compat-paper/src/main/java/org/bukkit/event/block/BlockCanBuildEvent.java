package org.bukkit.event.block;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.EquipmentSlot;

/** 블록을 놓을 수 있는지 판정한다. Furnace 는 아직 알리지 않는다. */
public class BlockCanBuildEvent extends BlockEvent {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    protected boolean buildable;
    protected BlockData blockData;
    private final Player player;
    private final EquipmentSlot hand;

    public BlockCanBuildEvent(Block block, BlockData type, boolean canBuild) { this(block, null, type, canBuild); }

    public BlockCanBuildEvent(Block block, Player player, BlockData type, boolean canBuild) { this(block, player, type, canBuild, EquipmentSlot.HAND); }

    public BlockCanBuildEvent(Block block, Player player, BlockData type, boolean canBuild, EquipmentSlot hand) {
        super(block);
        this.player = player;
        this.blockData = type;
        this.buildable = canBuild;
        this.hand = hand;
    }

    public boolean isBuildable() { return buildable; }

    public void setBuildable(boolean cancel) { this.buildable = cancel; }

    public Material getMaterial() { return blockData.getMaterial(); }

    public BlockData getBlockData() { return blockData; }

    public Player getPlayer() { return player; }

    public EquipmentSlot getHand() { return hand; }
}
