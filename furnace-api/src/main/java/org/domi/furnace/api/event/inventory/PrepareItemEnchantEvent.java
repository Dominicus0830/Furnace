// 마법 부여대의 제안 세 가지를 정한 뒤 클라이언트에 보내기 직전 — 메뉴를 처리하는 스레드에서 불린다.
// offers() 는 칸 0~2 의 제안(제안이 없으면 null)이고, 목록 안에서 바꾸면 그대로 보인다. 취소하면 세 칸 모두 제안이 없다.
// 클라이언트가 보는 값은 비용·단서 마법 부여·단서 레벨뿐이고, 실제로 부여되는 마법은 EnchantItemEvent 에서 정해진다.
package org.domi.furnace.api.event.inventory;

import java.util.List;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.inventory.EnchantmentOffer;
import org.domi.furnace.api.inventory.InventorySnapshot;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.world.BlockPos;

public final class PrepareItemEnchantEvent extends InventoryEvent implements Cancellable {

    private final BlockPos table;
    private final ItemStack item;
    private final int bookshelves;
    private final List<EnchantmentOffer> offers;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public PrepareItemEnchantEvent(Player player, InventorySnapshot inventory, BlockPos table, ItemStack item, int bookshelves, List<EnchantmentOffer> offers) {
        super(player, inventory);
        this.table = table;
        this.item = item;
        this.bookshelves = bookshelves;
        this.offers = offers;
    }

    /** 마법 부여대 칸. */
    public BlockPos table() { return table; }

    /** 마법을 부여할 아이템. */
    public ItemStack item() { return item; }

    /** 둘레 책장이 주는 마법 부여 힘(최대 15). */
    public int bookshelves() { return bookshelves; }

    /** 크기 3. 이 목록에서 칸을 바꾸거나 null 로 두면 된다. */
    public List<EnchantmentOffer> offers() { return offers; }
}
