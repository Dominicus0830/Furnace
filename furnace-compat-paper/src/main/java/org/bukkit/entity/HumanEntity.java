package org.bukkit.entity;

import org.bukkit.GameMode;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public interface HumanEntity extends LivingEntity, InventoryHolder {

    @Override
    PlayerInventory getInventory();

    /** 옛 이름: 주 손 아이템(비어 있어도 null 이 아니라 공기 스택). */
    ItemStack getItemInHand();

    void setItemInHand(ItemStack item);

    GameMode getGameMode();

    void setGameMode(GameMode mode);

    /**
     * 그 인벤토리를 창으로 연다. Furnace 는 플러그인이 만든 인벤토리(Bukkit.createInventory)만 열 수 있다. 여는 일은 연결 스레드에서
     * 이뤄지므로 InventoryOpenEvent 가 취소돼도 창 객체를 돌려준다(Paper 는 null).
     */
    org.bukkit.inventory.InventoryView openInventory(org.bukkit.inventory.Inventory inventory);

    /** 열린 창을 닫는다(플레이어 인벤토리만 보이게). */
    void closeInventory();

    /** Paper: 사유를 붙여 닫는다. Furnace 의 InventoryCloseEvent 사유는 늘 UNKNOWN 이다. */
    default void closeInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason reason) { closeInventory(); }

    /** 지금 열린 창(아무것도 없으면 플레이어 인벤토리 창). */
    org.bukkit.inventory.InventoryView getOpenInventory();
}
