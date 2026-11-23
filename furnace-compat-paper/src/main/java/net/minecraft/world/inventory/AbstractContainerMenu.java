package net.minecraft.world.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 창(Mojang 이름 shim). 두 가지 객체가 있다.
 * <ul>
 *   <li><b>서버가 연 창의 보기</b> — {@code ServerPlayer.containerMenu}·{@code inventoryMenu} 가 가리킨다. 번호·종류·칸 수는 서버 창과 같고, 칸은 서버 창의 칸을 비춘다:
 *       플레이어 인벤토리 칸은 지금 값(고치면 그 칸에 되쓴다), 창이 가진 컨테이너 칸(상자·제작 결과 …)은 클라이언트에 마지막으로 알린 값의 사본이며 고치면
 *       {@code UnsupportedNmsCall}(그 컨테이너를 쥔 스레드만 바꿀 수 있다 — Bukkit {@code InventoryView} 로 바꾼다). 이 객체에 칸 클릭·시동 같은 서버 창의 동작은 없다.</li>
 *   <li><b>플러그인이 만든 창</b> — 하위 클래스가 {@code addSlot} 으로 칸을 채운다. 서버의 창이 아니므로 {@code player.containerMenu} 에 넣어도 서버는 모른다.</li>
 * </ul>
 */
@org.domi.furnace.compat.nms.NmsInheritable
public abstract class AbstractContainerMenu {

    public static final int SLOT_CLICKED_OUTSIDE = -999;
    public static final int QUICKCRAFT_TYPE_CHARITABLE = 0;
    public static final int QUICKCRAFT_TYPE_GREEDY = 1;
    public static final int QUICKCRAFT_TYPE_CLONE = 2;
    public static final int QUICKCRAFT_HEADER_START = 0;
    public static final int QUICKCRAFT_HEADER_CONTINUE = 1;
    public static final int QUICKCRAFT_HEADER_END = 2;
    public static final int CARRIED_SLOT_SIZE = Integer.MAX_VALUE;

    public final NonNullList<Slot> slots = NonNullList.create();
    public final int containerId;
    private final MenuType<?> menuType;
    private ItemStack carried = ItemStack.EMPTY;
    private int stateId;

    protected AbstractContainerMenu(MenuType<?> menuType, int containerId) {
        this.menuType = menuType;
        this.containerId = containerId;
    }

    /** 생성기 전용 생성자: 생성 하위 클래스(RecipeBookMenu 등)가 부른다. 창 상태는 쓰지 않는다. */
    protected AbstractContainerMenu(org.domi.furnace.compat.nms.ShimCtor tag) {
        this.menuType = null;
        this.containerId = 0;
    }

    protected Slot addSlot(Slot slot) {
        slot.index = slots.size();
        slots.add(slot);
        return slot;
    }

    public MenuType<?> getType() {
        if (menuType == null) throw new UnsupportedOperationException("Unable to construct this menu by type");
        return menuType;
    }

    public Slot getSlot(int index) { return slots.get(index); }

    public NonNullList<ItemStack> getItems() {
        NonNullList<ItemStack> out = NonNullList.create();
        for (Slot s : slots) out.add(s.getItem());
        return out;
    }

    public ItemStack getCarried() { return carried; }

    public void setCarried(ItemStack stack) { this.carried = java.util.Objects.requireNonNull(stack, "stack"); }

    public int getStateId() { return stateId; }

    public int incrementStateId() {
        stateId = stateId + 1 & 32767;
        return stateId;
    }

    public boolean isValidSlotIndex(int slot) { return slot == -1 || slot == SLOT_CLICKED_OUTSIDE || slot >= 0 && slot < slots.size(); }

    /**
     * CraftBukkit·Paper 가 창에 더한 메서드: 이 창의 Bukkit {@code InventoryView}. 서버가 연 창의 보기는 그 플레이어가 지금 보는 Bukkit 창이다. 플러그인이 만든
     * 창은 {@code UnsupportedNmsCall}(서버의 창이 아니라 Bukkit 창이 없다).
     */
    public org.bukkit.inventory.InventoryView getBukkitView() {
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("AbstractContainerMenu#getBukkitView", "a menu object a plugin created is not a window of the server");
    }

    public abstract boolean stillValid(Player player);

    public abstract ItemStack quickMoveStack(Player player, int slot);
}
