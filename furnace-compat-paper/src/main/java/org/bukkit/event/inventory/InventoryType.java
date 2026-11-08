package org.bukkit.event.inventory;

/**
 * 인벤토리 종류(Paper 1.21.1 과 같은 상수·기본 크기·기본 제목). 호환 계층이 만드는 인벤토리는 플레이어 인벤토리, createInventory 의
 * CHEST·DISPENSER·DROPPER·HOPPER(·ENDER_CHEST·BARREL·SHULKER_BOX 모양 상자), 이벤트 시점 창 내용이다.
 */
public enum InventoryType {
    CHEST(27, "Chest"),
    DISPENSER(9, "Dispenser"),
    DROPPER(9, "Dropper"),
    FURNACE(3, "Furnace"),
    WORKBENCH(10, "Crafting"),
    CRAFTING(5, "Crafting", false),
    ENCHANTING(2, "Enchanting"),
    BREWING(5, "Brewing"),
    PLAYER(41, "Player"),
    CREATIVE(9, "Creative", false),
    MERCHANT(3, "Villager", false),
    ENDER_CHEST(27, "Ender Chest"),
    ANVIL(3, "Repairing"),
    SMITHING(4, "Upgrade Gear"),
    BEACON(1, "container.beacon"),
    HOPPER(5, "Item Hopper"),
    SHULKER_BOX(27, "Shulker Box"),
    BARREL(27, "Barrel"),
    BLAST_FURNACE(3, "Blast Furnace"),
    LECTERN(1, "Lectern"),
    SMOKER(3, "Smoker"),
    LOOM(4, "Loom"),
    CARTOGRAPHY(3, "Cartography Table"),
    GRINDSTONE(3, "Repair & Disenchant"),
    STONECUTTER(2, "Stonecutter"),
    COMPOSTER(1, "Composter", false),
    CHISELED_BOOKSHELF(6, "Chiseled Bookshelf", false),
    JUKEBOX(1, "Jukebox", false),
    DECORATED_POT(1, "Decorated Pot", false),
    CRAFTER(9, "Crafter"),
    @Deprecated(since = "1.20.1", forRemoval = true)
    SMITHING_NEW(4, "Upgrade Gear");

    private final int size;
    private final String title;
    private final boolean creatable;

    InventoryType(int size, String title) { this(size, title, true); }

    InventoryType(int size, String title, boolean creatable) {
        this.size = size;
        this.title = title;
        this.creatable = creatable;
    }

    public int getDefaultSize() { return size; }

    @Deprecated
    public String getDefaultTitle() { return title; }

    /** Bukkit.createInventory 로 만들 수 있는 종류인가(Paper 의 표 — Furnace 가 실제로 만들 수 있는 것은 그 일부다). */
    public boolean isCreatable() { return creatable; }

    /** 창 안 칸의 쓰임. */
    public enum SlotType {
        RESULT, CRAFTING, ARMOR, CONTAINER, QUICKBAR, OUTSIDE, FUEL
    }
}
