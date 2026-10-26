// 바닐라 ContainerEntity: 아이템 칸을 가진 탈것(상자 배·상자/깔때기 수레). 열지 않은 전리품 표(LootTable·LootTableSeed)를 가질 수 있다.
package org.domi.furnace.server.entity;

public interface ContainerEntity {

    /** 칸 배열(그 엔티티가 있는 region 스레드에서만 쓴다). */
    ItemStack[] chestVehicleItems();

    /** 열지 않은 전리품 표(없으면 null). */
    String lootTable();

    void setLootTable(String table);

    long lootTableSeed();

    Entity asEntity();

    /** interactWithContainerVehicle(player): 창을 연다(서버에서는 늘 CONSUME). */
    default void interactWithContainerVehicle(Player p) {
        Player.InventoryHost host = p.inventoryHost();
        if (host != null) host.openEntityContainer(this);
    }
}
