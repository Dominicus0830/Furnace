package org.bukkit.event.inventory;

import org.bukkit.block.Block;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.inventory.ItemStack;

/** 화로가 아이템 하나를 제련했다. 처리기 목록은 BlockCookEvent 것을 나눠 쓴다(Bukkit 과 같다 — 이 클래스에는 getHandlerList 가 없다). */
public class FurnaceSmeltEvent extends BlockCookEvent {

    public FurnaceSmeltEvent(Block furnace, ItemStack source, ItemStack result) { super(furnace, source, result); }
}
