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

    /** Container.stopOpen(player): 창을 닫았다 — 엔티티의 region 스레드에서 부른다. 상자 배·상자 수레는 container_close(깔때기 수레는 없음). */
    default void stopOpen(Player p) { }

    /** 열지 않은 전리품 표를 연 사람 없이 푼다(Container.getItem·isEmpty·removeItem·부술 때의 unpackChestVehicleLootTable(null)). 엔티티의 region 스레드에서. */
    default void unpackLootTable() {
        if (lootTable() != null) asEntity().level().unpackContainerLoot(this);
    }

    /** 창 제목 = Entity.getDisplayName: 이름표 이름(없으면 종류 이름)에 팀 접두사·색·접미사. */
    default org.domi.furnace.api.text.Text containerTitle() {
        var e = asEntity();
        return org.domi.furnace.server.scores.PlayerTeam.formatNameForTeam(e.getTeam(), e.getName());
    }

    /** interactWithContainerVehicle(player): 창을 연다(서버에서는 늘 CONSUME). */
    default void interactWithContainerVehicle(Player p) {
        Player.InventoryHost host = p.inventoryHost();
        if (host != null) host.openEntityContainer(this);
    }
}
