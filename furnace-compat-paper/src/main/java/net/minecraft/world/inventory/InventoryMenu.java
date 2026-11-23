package net.minecraft.world.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.domi.furnace.compat.nms.NmsMenus;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;

/**
 * 플레이어 인벤토리 창(번호 0, Mojang 이름 shim). 칸: 0 제작 결과, 1..4 제작 격자, 5..8 머리·가슴·다리·발, 9..35 주, 36..44 핫바, 45 왼손.
 * 접속한 플레이어의 것({@code player.inventoryMenu})은 서버의 인벤토리 창을 비춘다 — 제작 결과·격자 5칸은 창의 컨테이너 칸(읽기만), 나머지는 그 플레이어의
 * {@link Inventory} 의 칸이다. 바닐라 생성자로 만든 것은 서버와 이어지지 않은 제 제작 칸을 든다. 클릭·옮기기(quickMoveStack)·제작 레시피는 없다.
 */
public class InventoryMenu extends AbstractContainerMenu {

    public static final int CONTAINER_ID = 0;
    public static final int RESULT_SLOT = 0;
    public static final int CRAFT_SLOT_START = 1;
    public static final int CRAFT_SLOT_COUNT = 4;
    public static final int CRAFT_SLOT_END = 5;
    public static final int ARMOR_SLOT_START = 5;
    public static final int ARMOR_SLOT_COUNT = 4;
    public static final int ARMOR_SLOT_END = 9;
    public static final int INV_SLOT_START = 9;
    public static final int INV_SLOT_END = 36;
    public static final int USE_ROW_SLOT_START = 36;
    public static final int USE_ROW_SLOT_END = 45;
    public static final int SHIELD_SLOT = 45;

    public final boolean active;
    private final Player owner;
    private final NmsMenus.Live live;

    /** 바닐라 생성자: 서버와 이어지지 않은 제 제작 칸을 가진 인벤토리 창. */
    public InventoryMenu(Inventory inventory, boolean active, Player owner) {
        super(null, 0);
        this.active = active;
        this.owner = owner;
        this.live = null;
        net.minecraft.world.Container result = new org.domi.furnace.compat.nms.NmsLocalContainer(1), craft = new org.domi.furnace.compat.nms.NmsLocalContainer(4);
        addSlot(new Slot(result, 0, 154, 28));
        for (int i = 0; i < 4; i++) addSlot(new Slot(craft, i, 98 + i % 2 * 18, 18 + i / 2 * 18));
        int[] map = NmsMenus.inventoryMenuPlayerIndex();
        for (int i = CRAFT_SLOT_END; i < 46; i++) addSlot(new Slot(inventory, map[i], 0, 0));
    }

    private InventoryMenu(Inventory inventory, Player owner, NmsMenus.Live live) {
        super(null, 0);
        this.active = true;
        this.owner = owner;
        this.live = live;
        for (Slot s : NmsMenus.inventoryMenuSlots(live, inventory)) addSlot(s);
    }

    /** 호환 모듈 전용 — 접속한 플레이어의 서버 인벤토리 창을 비추는 보기. */
    public static InventoryMenu furnaceLive(Inventory inventory, Player owner, org.domi.furnace.api.entity.Player handle) {
        return new InventoryMenu(inventory, owner, NmsMenus.inventoryMenuLive(handle));
    }

    @Override
    public ItemStack getCarried() {
        if (live == null) return super.getCarried();
        return NmsMenus.carriedCopy(live.look().carried());
    }

    @Override
    public void setCarried(ItemStack stack) {
        if (live != null) throw new UnsupportedNmsCall("AbstractContainerMenu#setCarried", "the item on the cursor of the server's inventory window belongs to the player's connection");
        super.setCarried(stack);
    }

    @Override
    public int getStateId() { return live == null ? super.getStateId() : live.look().stateId(); }

    @Override
    public int incrementStateId() {
        if (live != null) throw new UnsupportedNmsCall("AbstractContainerMenu#incrementStateId", "the state number of the server's inventory window is kept by the server");
        return super.incrementStateId();
    }

    /** 접속한 플레이어의 인벤토리 창: 그 플레이어가 지금 보는 Bukkit 창(열린 창이 없으면 플레이어 인벤토리 보기). 제 칸의 인벤토리 창은 Bukkit 창이 없다. */
    @Override
    public org.bukkit.inventory.InventoryView getBukkitView() {
        if (live == null) return super.getBukkitView();
        return org.domi.furnace.compat.nms.NmsMenus.bukkitView(owner, live);
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        throw new UnsupportedNmsCall("AbstractContainerMenu#quickMoveStack", "shift-clicking is handled by the server's own window");
    }

    public int getResultSlotIndex() { return 0; }

    public int getGridWidth() { return 2; }

    public int getGridHeight() { return 2; }

    public int getSize() { return 5; }

    public static boolean isHotbarSlot(int slot) { return slot >= 36 && slot < 45 || slot == 45; }

    public boolean shouldMoveToInventory(int slot) { return slot != getResultSlotIndex(); }
}
