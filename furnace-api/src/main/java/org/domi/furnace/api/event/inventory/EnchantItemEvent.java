// 플레이어가 마법 부여대의 제안을 눌러 마법을 부여하기 직전 — 메뉴를 처리하는 스레드에서 불린다.
// enchants() 가 부여될 마법(id → 레벨)이고 이 맵을 고치면 그대로 부여된다. 취소하거나, 바뀐 expLevelCost 가 플레이어 레벨(창작 모드 제외)보다 크거나,
// enchants() 가 비면 아무 일도 없다(레벨·청금석도 그대로). 경험치 레벨은 눌린 칸 번호 + 1 만큼, 청금석도 같은 수만큼 든다.
package org.domi.furnace.api.event.inventory;

import java.util.Map;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.inventory.InventorySnapshot;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.world.BlockPos;

public final class EnchantItemEvent extends InventoryEvent implements Cancellable {

    private final BlockPos table;
    private final ItemStack item;
    private final int whichButton;
    private int expLevelCost;
    private final Map<String, Integer> enchants;
    private final String enchantmentHint;
    private final int levelHint;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public EnchantItemEvent(Player player, InventorySnapshot inventory, BlockPos table, ItemStack item, int whichButton, int expLevelCost, Map<String, Integer> enchants) {
        this(player, inventory, table, item, whichButton, expLevelCost, enchants, null, -1);
    }

    /** enchantmentHint·levelHint: 눌린 제안이 단서로 보여 준 마법 부여 id 와 레벨(단서가 없으면 null, -1). */
    public EnchantItemEvent(Player player, InventorySnapshot inventory, BlockPos table, ItemStack item, int whichButton, int expLevelCost, Map<String, Integer> enchants,
                            String enchantmentHint, int levelHint) {
        super(player, inventory);
        this.enchantmentHint = enchantmentHint;
        this.levelHint = levelHint;
        this.table = table;
        this.item = item;
        this.whichButton = whichButton;
        this.expLevelCost = expLevelCost;
        this.enchants = enchants;
    }

    public BlockPos table() { return table; }

    /** 마법을 부여할 아이템(부여 전). */
    public ItemStack item() { return item; }

    /** 눌린 제안 칸 0~2. */
    public int whichButton() { return whichButton; }

    /** 제안에 표시된 비용(요구 레벨). 플레이어 레벨이 이 값보다 낮으면 부여되지 않는다. */
    public int expLevelCost() { return expLevelCost; }

    public void setExpLevelCost(int expLevelCost) { this.expLevelCost = expLevelCost; }

    /** 부여될 마법(id → 레벨). 순서대로 합쳐진다. */
    public Map<String, Integer> enchants() { return enchants; }

    /** 눌린 제안이 단서로 보여 준 마법 부여 id(없으면 null). */
    public String enchantmentHint() { return enchantmentHint; }

    /** 눌린 제안이 단서로 보여 준 레벨(없으면 -1). */
    public int levelHint() { return levelHint; }
}
