package org.bukkit.loot;

import java.util.Collection;
import java.util.Random;
import org.bukkit.Keyed;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Paper 1.21.1 LootTable — 서버 데이터 팩의 전리품 표. 구현은 {@code CraftLootTable}. */
public interface LootTable extends Keyed {

    Collection<ItemStack> populateLoot(Random random, LootContext context);

    void fillInventory(Inventory inventory, Random random, LootContext context);
}
