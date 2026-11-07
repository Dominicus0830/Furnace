package org.bukkit.inventory;

/**
 * 플레이어 인벤토리: 0~8 단축 칸, 9~35 가방, 36 신발, 37 바지, 38 갑옷, 39 투구, 40 왼손(41칸). 저장 칸은 0~35.
 * 단축 칸 선택(getHeldItemSlot)은 읽기를 아무 스레드에서나 할 수 있고, 바꾸기는 주인 스레드로 예약된다.
 */
public interface PlayerInventory extends Inventory {

    ItemStack[] getArmorContents();

    void setArmorContents(ItemStack[] items);

    /** 왼손 한 칸. */
    ItemStack[] getExtraContents();

    void setExtraContents(ItemStack[] items);

    ItemStack getHelmet();

    void setHelmet(ItemStack helmet);

    ItemStack getChestplate();

    void setChestplate(ItemStack chestplate);

    ItemStack getLeggings();

    void setLeggings(ItemStack leggings);

    ItemStack getBoots();

    void setBoots(ItemStack boots);

    ItemStack getItemInMainHand();

    void setItemInMainHand(ItemStack item);

    ItemStack getItemInOffHand();

    void setItemInOffHand(ItemStack item);

    /** 옛 이름: 주 손. */
    ItemStack getItemInHand();

    void setItemInHand(ItemStack stack);

    ItemStack getItem(EquipmentSlot slot);

    void setItem(EquipmentSlot slot, ItemStack item);

    int getHeldItemSlot();

    void setHeldItemSlot(int slot);

    @Override
    org.bukkit.entity.HumanEntity getHolder();
}
