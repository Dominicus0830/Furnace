package org.bukkit.event.player;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * 플레이어가 공기·블록을 클릭했다(손마다 한 번). Bukkit 처럼 두 결과가 따로 있다: 클릭한 블록 쓰기({@link #useInteractedBlock()})와
 * 손에 든 아이템 쓰기({@link #useItemInHand()}). 블록이 없으면(공기 클릭) 블록 쓰기는 처음부터 DENY 라 {@link #isCancelled()} 가 true 다(Bukkit 과 같다).
 * Furnace 서버로 되돌릴 때는 둘을 하나의 취소로 합친다 — 규칙은 {@code Mappings} 의 PlayerInteractEvent 짝 주석.
 */
public class PlayerInteractEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    protected ItemStack item;
    protected Action action;
    protected Block blockClicked;
    protected BlockFace blockFace;
    private Result useClickedBlock;
    private Result useItemInHand;
    private final EquipmentSlot hand;
    private final Vector clickedPosition;

    public PlayerInteractEvent(Player who, Action action, ItemStack item, Block clickedBlock, BlockFace clickedFace) {
        this(who, action, item, clickedBlock, clickedFace, EquipmentSlot.HAND);
    }

    public PlayerInteractEvent(Player who, Action action, ItemStack item, Block clickedBlock, BlockFace clickedFace, EquipmentSlot hand) {
        this(who, action, item, clickedBlock, clickedFace, hand, null);
    }

    public PlayerInteractEvent(Player who, Action action, ItemStack item, Block clickedBlock, BlockFace clickedFace, EquipmentSlot hand, Vector clickedPosition) {
        super(who);
        this.action = action;
        this.item = item;
        this.blockClicked = clickedBlock;
        this.blockFace = clickedFace;
        this.hand = hand;
        this.clickedPosition = clickedPosition;
        this.useItemInHand = Result.DEFAULT;
        this.useClickedBlock = clickedBlock == null ? Result.DENY : Result.ALLOW;
    }

    public Action getAction() { return action; }

    /** Bukkit: 블록 쓰기가 DENY 인가(옛 방식 — 두 결과를 보려면 useInteractedBlock·useItemInHand). */
    @Deprecated
    @Override
    public boolean isCancelled() { return useInteractedBlock() == Result.DENY; }

    /** Bukkit: 두 결과를 함께 DENY 로, 풀면 DENY 였던 것만 DEFAULT 로. */
    @Deprecated
    @Override
    public void setCancelled(boolean cancel) {
        setUseInteractedBlock(cancel ? Result.DENY : useInteractedBlock() == Result.DENY ? Result.DEFAULT : useInteractedBlock());
        setUseItemInHand(cancel ? Result.DENY : useItemInHand() == Result.DENY ? Result.DEFAULT : useItemInHand());
    }

    /** 손에 든 아이템. 빈 손이면 null(CraftBukkit 과 같다). */
    public ItemStack getItem() { return item; }

    public Material getMaterial() { return hasItem() ? item.getType() : Material.AIR; }

    public boolean hasBlock() { return blockClicked != null; }

    public boolean hasItem() { return item != null; }

    public boolean isBlockInHand() { return hasItem() && item.getType().isBlock(); }

    public Block getClickedBlock() { return blockClicked; }

    /** 클릭한 면. 공기 클릭은 CraftBukkit 처럼 SOUTH. */
    public BlockFace getBlockFace() { return blockFace; }

    public Result useInteractedBlock() { return useClickedBlock; }

    public void setUseInteractedBlock(Result useInteractedBlock) { this.useClickedBlock = useInteractedBlock; }

    public Result useItemInHand() { return useItemInHand; }

    public void setUseItemInHand(Result useItemInHand) { this.useItemInHand = useItemInHand; }

    public EquipmentSlot getHand() { return hand; }

    /** 블록 안에서 클릭한 점(블록 기준 상대 좌표). Furnace 상호작용 이벤트가 알리지 않아 null(Bukkit 도 null 을 허용한다). */
    public Vector getClickedPosition() { return clickedPosition == null ? null : clickedPosition.clone(); }

    /** Paper: 클릭한 점의 월드 위치. getClickedPosition 이 없으면 null. */
    public Location getInteractionPoint() {
        if (blockClicked == null || clickedPosition == null) return null;
        return blockClicked.getLocation().add(clickedPosition);
    }
}
