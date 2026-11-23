package org.domi.furnace.compat.nms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.domi.furnace.api.internal.ChunkInternals;

/**
 * 서버가 연 창을 NMS shim 의 {@code AbstractContainerMenu} 로 비추는 곳(NMS shim "컨테이너 묶음"). 창의 번호·종류·칸 수·칸 값은 서버 창에서 읽는다
 * ({@link ChunkInternals#menuLookNow}·{@link ChunkInternals#menuSlotNow}) — 아무 스레드에서나 읽을 수 있고, 창이 가진 컨테이너 칸(상자·제작 결과 …)은 그 컨테이너를 쥔
 * 스레드로 건너가지 않고 클라이언트에 마지막으로 알린 값을 본다.
 */
public final class NmsMenus {

    private NmsMenus() {}

    /** 창 하나의 서버 쪽 끈: 플레이어 핸들과 창 번호, 마지막으로 읽은 모습(창이 닫힌 뒤에도 이 값이 남는다 — 바닐라도 닫힌 창 객체는 마지막 상태를 든다). */
    public static final class Live {
        private final org.domi.furnace.api.entity.Player handle;
        private final int containerId;
        private volatile ChunkInternals.MenuLook last;

        Live(org.domi.furnace.api.entity.Player handle, int containerId, ChunkInternals.MenuLook first) {
            this.handle = handle;
            this.containerId = containerId;
            this.last = first;
        }

        public org.domi.furnace.api.entity.Player handle() { return handle; }

        public int containerId() { return containerId; }

        /** 지금 모습. 창이 이미 닫혔으면 마지막으로 본 것. */
        public ChunkInternals.MenuLook look() {
            try {
                return last = NmsChunks.bridge().menuLookNow(handle, containerId);
            } catch (IllegalStateException closed) {
                ChunkInternals.MenuLook l = last;
                if (l == null) throw closed;
                return l;
            }
        }

        ItemStack slot(int menuIndex, boolean playerSlot) {
            org.domi.furnace.api.item.ItemStack now;
            try {
                now = NmsChunks.bridge().menuSlotNow(handle, containerId, menuIndex);
            } catch (IllegalStateException closed) {
                throw new UnsupportedNmsCall("Slot#getItem", "the window " + containerId + " is closed - a closed window keeps no slot contents here");
            }
            return ItemStack.liveMenuSlot(handle, containerId, menuIndex, playerSlot, now);
        }
    }

    /** 첫 모습을 읽어 끈을 만든다(번호 -1 이면 지금 열린 창). */
    public static Live open(org.domi.furnace.api.entity.Player handle, int containerId) {
        ChunkInternals.MenuLook first = NmsChunks.bridge().menuLookNow(handle, containerId);
        return new Live(handle, first.containerId(), first);
    }

    /** 창이 가진 컨테이너 칸 몇 개를 하나의 컨테이너로 본 것(칸 k = 창의 menuIndex[k] 번 칸). 읽기만 된다. */
    static final class OwnedContainer implements Container {
        private final Live live;
        private final int[] menuIndex;

        OwnedContainer(Live live, int[] menuIndex) {
            this.live = live;
            this.menuIndex = menuIndex;
        }

        @Override
        public int getContainerSize() { return menuIndex.length; }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < menuIndex.length; i++) if (!getItem(i).isEmpty()) return false;
            return true;
        }

        @Override
        public ItemStack getItem(int slot) { return slot >= 0 && slot < menuIndex.length ? live.slot(menuIndex[slot], false) : ItemStack.EMPTY; }

        private static UnsupportedNmsCall readOnly(String what) {
            return new UnsupportedNmsCall("Container#" + what, "these slots belong to the container of the window; only the thread that owns that container may change"
                    + " them (use the Bukkit InventoryView of the window)");
        }

        @Override
        public ItemStack removeItem(int slot, int amount) { throw readOnly("removeItem"); }

        @Override
        public ItemStack removeItemNoUpdate(int slot) { throw readOnly("removeItemNoUpdate"); }

        @Override
        public void setItem(int slot, ItemStack stack) { throw readOnly("setItem"); }

        @Override
        public void setChanged() {}

        @Override
        public boolean stillValid(Player player) { return true; }

        @Override
        public void clearContent() { throw readOnly("clearContent"); }


        @Override
        public java.util.List<ItemStack> getContents() {
            java.util.List<ItemStack> out = new java.util.ArrayList<>(getContainerSize());
            for (int k = 0; k < getContainerSize(); k++) out.add(getItem(k));
            return out;
        }

        @Override
        public int getMaxStackSize() { return 99; }

        @Override
        public void setMaxStackSize(int size) { throw bukkitSide("setMaxStackSize"); }

        @Override
        public org.bukkit.Location getLocation() { throw bukkitSide("getLocation"); }

        @Override
        public org.bukkit.inventory.InventoryHolder getOwner() { throw bukkitSide("getOwner"); }

        @Override
        public java.util.List<org.bukkit.entity.HumanEntity> getViewers() { throw bukkitSide("getViewers"); }

        @Override
        public void onOpen(org.bukkit.craftbukkit.entity.CraftHumanEntity who) { throw bukkitSide("onOpen"); }

        @Override
        public void onClose(org.bukkit.craftbukkit.entity.CraftHumanEntity who) { throw bukkitSide("onClose"); }

        private static org.domi.furnace.compat.nms.UnsupportedNmsCall bukkitSide(String what) {
            return new org.domi.furnace.compat.nms.UnsupportedNmsCall("Container#" + what, "the Bukkit-side view of this window container is not part of the shim (use the Bukkit Inventory)");
        }
    }

    /** 칸 목록: 플레이어 인벤토리 칸은 그 플레이어의 {@link Inventory} 의 칸, 나머지는 창의 컨테이너 칸. */
    static List<Slot> slots(Live live, Inventory inventory, int[] playerIndex) {
        List<Integer> owned = new ArrayList<>();
        for (int i = 0; i < playerIndex.length; i++) if (playerIndex[i] < 0) owned.add(i);
        int[] ownedIndex = owned.stream().mapToInt(Integer::intValue).toArray();
        OwnedContainer container = new OwnedContainer(live, ownedIndex);
        List<Slot> out = new ArrayList<>(playerIndex.length);
        int k = 0;
        for (int i = 0; i < playerIndex.length; i++) out.add(playerIndex[i] >= 0 ? new Slot(inventory, playerIndex[i], 0, 0) : new Slot(container, k++, 0, 0));
        return out;
    }

    /** 열린 창(인벤토리 창이 아닌)의 보기. */
    public static AbstractContainerMenu view(Live live, Inventory inventory, ChunkInternals.MenuLook look) {
        return new View(live, inventory, look);
    }

    /** 서버가 연 창(상자·제작대·플러그인 메뉴 …)의 보기 — 칸 클릭·시동 같은 서버 창의 동작은 없다. */
    private static final class View extends AbstractContainerMenu {
        private final Live live;
        private final Player owner;

        View(Live live, Inventory inventory, ChunkInternals.MenuLook look) {
            super(MenuType.furnaceOf(look.type()), look.containerId());
            this.live = live;
            this.owner = inventory.player;
            for (Slot s : slots(live, inventory, look.playerIndex())) addSlot(s);
        }

        @Override
        public ItemStack getCarried() { return carriedCopy(live.look().carried()); }

        @Override
        public void setCarried(ItemStack stack) {
            throw new UnsupportedNmsCall("AbstractContainerMenu#setCarried", "the item on the cursor of a window the server opened belongs to the player's connection;"
                    + " it cannot be set from here");
        }

        @Override
        public int getStateId() { return live.look().stateId(); }

        @Override
        public org.bukkit.inventory.InventoryView getBukkitView() { return bukkitView(owner, live); }

        @Override
        public int incrementStateId() {
            throw new UnsupportedNmsCall("AbstractContainerMenu#incrementStateId", "the state number of a window the server opened is kept by the server");
        }

        @Override
        public boolean stillValid(Player player) {
            throw new UnsupportedNmsCall("AbstractContainerMenu#stillValid", "the view of a window the server opened has no validity rule of its own");
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slot) {
            throw new UnsupportedNmsCall("AbstractContainerMenu#quickMoveStack", "shift-clicking is handled by the server's own window");
        }
    }

    /** 그 창의 Bukkit 보기: 지금 열려 있는 창이 이 창일 때만(닫힌 창은 Bukkit 에 보기가 없다). */
    public static org.bukkit.inventory.InventoryView bukkitView(Player owner, Live live) {
        if (!(owner.getBukkitEntity() instanceof org.bukkit.craftbukkit.entity.CraftPlayer cp)) {
            throw new UnsupportedNmsCall("AbstractContainerMenu#getBukkitView", "only a connected player has a window view");
        }
        var view = cp.getOpenInventory();
        try {
            if (NmsChunks.bridge().menuLookNow(live.handle(), -1).containerId() != live.containerId()) {
                throw new UnsupportedNmsCall("AbstractContainerMenu#getBukkitView", "the window " + live.containerId() + " is no longer open");
            }
        } catch (IllegalStateException | IllegalArgumentException gone) {
            throw new UnsupportedNmsCall("AbstractContainerMenu#getBukkitView", "the window " + live.containerId() + " is no longer open");
        }
        return view;
    }

    /** 커서에 든 스택의 사본(서버의 커서는 연결이 쥐고 있어 사본을 고쳐도 커서는 안 바뀐다). */
    public static ItemStack carriedCopy(org.domi.furnace.api.item.ItemStack carried) {
        return carried.isEmpty() ? ItemStack.EMPTY : ItemStack.fromFurnace(carried, carried.amount());
    }

    /** 인벤토리 창(번호 0)의 칸 → 플레이어 Inventory 번호(제작 결과·격자 5칸은 -1). 서버의 InventoryMenu 와 같은 배치. */
    public static int[] inventoryMenuPlayerIndex() {
        int[] a = new int[46];
        java.util.Arrays.fill(a, -1);
        for (int i = 0; i < 4; i++) a[5 + i] = 39 - i;   // 머리·가슴·다리·발
        for (int i = 9; i < 36; i++) a[i] = i;
        for (int i = 0; i < 9; i++) a[36 + i] = i;
        a[45] = 40;
        return a;
    }

    /** 접속한 플레이어의 인벤토리 창 보기용 끈(창 번호 0). */
    public static Live inventoryMenuLive(org.domi.furnace.api.entity.Player handle) { return new Live(handle, 0, null); }

    public static List<Slot> inventoryMenuSlots(Live live, Inventory inventory) { return slots(live, inventory, inventoryMenuPlayerIndex()); }
}
