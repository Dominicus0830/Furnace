package org.bukkit.event.world;

import java.util.Collection;
import java.util.List;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;

/**
 * Paper 1.21.1 LootGenerateEvent: 전리품 표가 컨테이너를 채우기 직전(표를 굴린 뒤, 칸에 나누기 전). 취소하면 채우지 않고, setLoot 로 바꾼 목록이 칸에 나뉜다.
 * 서버의 상자 열기(plugin=false)와 {@code LootTable.fillInventory}(plugin=true) 둘 다에서 불린다.
 */
public class LootGenerateEvent extends WorldEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private boolean cancelled;
    private final Entity entity;
    private final InventoryHolder inventoryHolder;
    private final LootTable lootTable;
    private final LootContext lootContext;
    private final List<ItemStack> loot;
    private final boolean plugin;

    public LootGenerateEvent(World world, Entity entity, InventoryHolder inventoryHolder, LootTable lootTable, LootContext lootContext,
                             List<ItemStack> items, boolean plugin) {
        super(world);
        this.entity = entity;
        this.inventoryHolder = inventoryHolder;
        this.lootTable = lootTable;
        this.lootContext = lootContext;
        this.loot = items;
        this.plugin = plugin;
    }

    public Entity getEntity() { return entity; }

    public InventoryHolder getInventoryHolder() { return inventoryHolder; }

    public LootTable getLootTable() { return lootTable; }

    public LootContext getLootContext() { return lootContext; }

    public void setLoot(Collection<ItemStack> loot) {
        this.loot.clear();
        if (loot != null) this.loot.addAll(loot);
    }

    public List<ItemStack> getLoot() { return loot; }

    public boolean isPlugin() { return plugin; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
