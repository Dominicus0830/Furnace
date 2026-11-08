// Furnace 인벤토리 이벤트 → Bukkit 창(InventoryView)·클릭 종류·결과(InventoryAction)·끌어 나누기.
// 규칙은 CraftBukkit 의 창 클릭 처리(ServerGamePacketListenerImpl#handleContainerClick 의 ClickType·InventoryAction 고르기,
// AbstractContainerMenu 의 끌어 나누기)를 따른다. 칸이 그 아이템을 받을 수 있는지(mayPlace)는 Furnace 이벤트가 알리지 않아
// 비었거나 같은 아이템이면 받는 것으로 본다.
package org.domi.furnace.compat.paper;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.domi.furnace.api.inventory.InventorySnapshot;
import org.domi.furnace.api.item.ItemStack;

final class CompatInventories {

    private CompatInventories() {}

    private static final int PLAYER_SLOTS = 36;

    // ------------------------------------------------------------------ 창

    /** 이벤트의 창. 플러그인 메뉴면 그 메뉴의 인벤토리를 위쪽에 둔다. */
    static CompatInventoryView view(org.domi.furnace.api.entity.Player fp, InventorySnapshot snap, org.domi.furnace.api.menu.Menu menu, ItemStack cursor) {
        CraftPlayer p = (CraftPlayer) Compat.player(fp);
        CompatMenuInventory own = CompatMenuInventory.of(menu);
        if (own != null) return new CompatInventoryView(p, own, own.getType(), own.title(), cursor);
        InventoryType type = menu != null ? menuType(menu.type()) : type(snap.type(), snap.size());
        int top = topSize(snap.type(), snap.size());
        Inventory inv = new SnapshotInventory(type, snap.contents().subList(0, Math.min(top, snap.size())), top, snap.type());
        String title = menu != null ? Compat.legacy(menu.title()) : type.getDefaultTitle();
        return new CompatInventoryView(p, inv, type, title, cursor);
    }

    /** 이벤트 밖(getOpenInventory): 지금 열린 화면. 플러그인 메뉴가 아니면 위쪽 칸 내용은 모른다. */
    static CompatInventoryView openView(CraftPlayer p) {
        var fp = p.furnaceHandle();
        var menu = fp.currentMenu();
        CompatMenuInventory own = CompatMenuInventory.of(menu);
        if (own != null) return new CompatInventoryView(p, own, own.getType(), own.title(), null);
        if (menu != null) {
            InventoryType t = menuType(menu.type());
            return new CompatInventoryView(p, new SnapshotInventory(t, null, menu.size(), menu.type().key()), t, Compat.legacy(menu.title()), null);
        }
        String key = fp.openContainerType();
        if (key == null) key = "minecraft:player";
        InventoryType t = type(key, -1);
        int top = key.equals("minecraft:player") ? 5 : t.getDefaultSize();
        return new CompatInventoryView(p, new SnapshotInventory(t, null, top, key), t, t.getDefaultTitle(), null);
    }

    /** 위쪽 칸 수: 플레이어 인벤토리 창은 2×2 제작(결과 포함 5), 나머지는 창 칸에서 플레이어 인벤토리 36 을 뺀 것. */
    static int topSize(String key, int total) {
        if (key.equals("minecraft:player")) return 5;
        return Math.max(0, total - PLAYER_SLOTS);
    }

    static InventoryType menuType(org.domi.furnace.api.menu.MenuType t) {
        return switch (t) {
            case DISPENSER -> InventoryType.DISPENSER;
            case HOPPER -> InventoryType.HOPPER;
            default -> InventoryType.CHEST;
        };
    }

    /** Furnace 창 종류 키(서버 메뉴 이름) → Bukkit 종류. 상자 모양(통·엔더 상자·당나귀 등)은 구별할 수 없어 CHEST. */
    static InventoryType type(String key, int total) {
        String k = key.startsWith("minecraft:") ? key.substring(10) : key;
        return switch (k) {
            case "player" -> InventoryType.CRAFTING;
            case "dispenser" -> InventoryType.DISPENSER;
            case "hopper" -> InventoryType.HOPPER;
            case "furnace" -> InventoryType.FURNACE;
            case "blast_furnace" -> InventoryType.BLAST_FURNACE;
            case "smoker" -> InventoryType.SMOKER;
            case "crafting" -> InventoryType.WORKBENCH;
            case "enchantment" -> InventoryType.ENCHANTING;
            case "brewing_stand" -> InventoryType.BREWING;
            case "merchant" -> InventoryType.MERCHANT;
            case "anvil" -> InventoryType.ANVIL;
            case "smithing" -> InventoryType.SMITHING;
            case "beacon" -> InventoryType.BEACON;
            case "shulker_box" -> InventoryType.SHULKER_BOX;
            case "lectern" -> InventoryType.LECTERN;
            case "loom" -> InventoryType.LOOM;
            case "cartography" -> InventoryType.CARTOGRAPHY;
            case "grindstone" -> InventoryType.GRINDSTONE;
            case "stonecutter" -> InventoryType.STONECUTTER;
            case "crafter" -> InventoryType.CRAFTER;
            default -> InventoryType.CHEST;
        };
    }

    // ------------------------------------------------------------------ 클릭

    /** 창 밖(-999). */
    static final int OUTSIDE = -999;

    static boolean isDrag(String clickType) { return clickType.equals("quick_craft"); }

    static ClickType click(String type, int slot, int button) {
        return switch (type) {
            case "pickup" -> slot == OUTSIDE ? (button == 0 ? ClickType.WINDOW_BORDER_LEFT : ClickType.WINDOW_BORDER_RIGHT)
                    : button == 0 ? ClickType.LEFT : button == 1 ? ClickType.RIGHT : ClickType.UNKNOWN;
            case "quick_move" -> button == 0 ? ClickType.SHIFT_LEFT : button == 1 ? ClickType.SHIFT_RIGHT : ClickType.UNKNOWN;
            case "swap" -> button >= 0 && button < 9 ? ClickType.NUMBER_KEY : button == 40 ? ClickType.SWAP_OFFHAND : ClickType.UNKNOWN;
            case "clone" -> ClickType.MIDDLE;
            case "throw" -> slot == OUTSIDE ? (button == 0 ? ClickType.WINDOW_BORDER_LEFT : ClickType.WINDOW_BORDER_RIGHT)
                    : button == 0 ? ClickType.DROP : button == 1 ? ClickType.CONTROL_DROP : ClickType.UNKNOWN;
            case "pickup_all" -> ClickType.DOUBLE_CLICK;
            default -> ClickType.UNKNOWN;
        };
    }

    /**
     * CraftBukkit 의 InventoryAction 고르기. current = 클릭한 칸(창 밖이면 EMPTY), cursor = 커서, hotbar = SWAP 의 그 핫바·왼손 칸 아이템,
     * creative = 창조 모드(CLONE 은 창조에서만 복제).
     */
    static InventoryAction action(String type, int slot, int button, ItemStack current, ItemStack cursor, ItemStack hotbar, boolean creative) {
        switch (type) {
            case "pickup" -> {
                if (button != 0 && button != 1) return InventoryAction.UNKNOWN;
                if (slot == OUTSIDE) {
                    if (cursor.isEmpty()) return InventoryAction.NOTHING;
                    return button == 0 ? InventoryAction.DROP_ALL_CURSOR : InventoryAction.DROP_ONE_CURSOR;
                }
                if (slot < 0) return InventoryAction.NOTHING;
                if (current.isEmpty()) {
                    if (cursor.isEmpty()) return InventoryAction.NOTHING;
                    return button == 0 ? InventoryAction.PLACE_ALL : InventoryAction.PLACE_ONE;
                }
                if (cursor.isEmpty()) return button == 0 ? InventoryAction.PICKUP_ALL : InventoryAction.PICKUP_HALF;
                if (current.isSimilar(cursor)) {
                    int max = maxStack(current);
                    int toPlace = button == 0 ? cursor.amount() : 1;
                    toPlace = Math.min(toPlace, max - current.amount());
                    toPlace = Math.min(toPlace, 64 - current.amount());   // 칸 최대(사용자·바닐라 용기 64)
                    if (toPlace == 1) return InventoryAction.PLACE_ONE;
                    if (toPlace == cursor.amount()) return InventoryAction.PLACE_ALL;
                    if (toPlace < 0) return toPlace != -1 ? InventoryAction.PICKUP_SOME : InventoryAction.PICKUP_ONE;
                    if (toPlace != 0) return InventoryAction.PLACE_SOME;
                    return InventoryAction.NOTHING;
                }
                return cursor.amount() <= 64 ? InventoryAction.SWAP_WITH_CURSOR : InventoryAction.NOTHING;
            }
            case "quick_move" -> {
                if (slot < 0) return InventoryAction.NOTHING;
                return current.isEmpty() ? InventoryAction.NOTHING : InventoryAction.MOVE_TO_OTHER_INVENTORY;
            }
            case "swap" -> {
                if (slot < 0 || !(button >= 0 && button < 9 || button == 40)) return InventoryAction.NOTHING;
                if (!current.isEmpty() || !hotbar.isEmpty()) return InventoryAction.HOTBAR_SWAP;
                return InventoryAction.NOTHING;
            }
            case "clone" -> {
                if (slot < 0 || current.isEmpty() || !cursor.isEmpty() || !creative) return InventoryAction.NOTHING;
                return InventoryAction.CLONE_STACK;
            }
            case "throw" -> {
                if (slot < 0 || current.isEmpty()) return InventoryAction.NOTHING;
                return button == 0 ? InventoryAction.DROP_ONE_SLOT : button == 1 ? InventoryAction.DROP_ALL_SLOT : InventoryAction.NOTHING;
            }
            case "pickup_all" -> {
                return slot >= 0 && !cursor.isEmpty() ? InventoryAction.COLLECT_TO_CURSOR : InventoryAction.NOTHING;
            }
            default -> {
                return InventoryAction.UNKNOWN;
            }
        }
    }

    static int maxStack(ItemStack s) { return CompatItems.copyOf(s).getMaxStackSize(); }

    /** 창 칸 번호 → SWAP 의 핫바·왼손 칸이 창에서 몇 번인지(플레이어 인벤토리 창이면 36..44·45, 다른 창은 위쪽 뒤 27..35, 왼손은 창에 없다). */
    static int hotbarRaw(String key, int total, int button) {
        int top = topSize(key, total);
        if (button == 40) return key.equals("minecraft:player") ? 45 : -1;
        return key.equals("minecraft:player") ? 36 + button : top + 27 + button;
    }

    // ------------------------------------------------------------------ 끌어 나누기

    /** 플레이어마다 진행 중인 끌어 나누기(연결 스레드 한 줄이라 플레이어 안에서는 차례대로 온다). */
    private static final Map<UUID, Drag> DRAGS = new ConcurrentHashMap<>();

    private static final class Drag {
        final int kind;   // 0 고르게(왼쪽), 1 하나씩(오른쪽), 2 가득(가운데·창조)
        final Set<Integer> slots = new LinkedHashSet<>();

        Drag(int kind) { this.kind = kind; }
    }

    /** 끝난 끌어 나누기: 칸 → 놓인 뒤 아이템, 남은 커서. */
    record DragResult(int kind, Map<Integer, ItemStack> placed, ItemStack newCursor, ItemStack oldCursor) {}

    /**
     * quick_craft 한 단계를 기록한다(바닐라 AbstractContainerMenu#doClick 의 단계 규칙). 마지막 단계에서 둘 이상의 칸이 모였으면 그 결과,
     * 아니면 null(한 칸이면 바닐라는 보통 클릭으로 바꾼다 — 그때도 Bukkit 이벤트는 없다).
     */
    static DragResult dragStep(UUID player, int slot, int button, InventorySnapshot snap, ItemStack cursor, boolean creative) {
        int stage = button & 3;
        int kind = (button >> 2) & 3;
        switch (stage) {
            case 0 -> {
                if (kind == 2 && !creative || kind > 2) DRAGS.remove(player);
                else DRAGS.put(player, new Drag(kind));
                return null;
            }
            case 1 -> {
                Drag d = DRAGS.get(player);
                if (d == null || slot < 0 || slot >= snap.size()) return null;
                ItemStack in = snap.get(slot);
                boolean fits = in.isEmpty() || in.isSimilar(cursor) && in.amount() <= maxStack(cursor);
                if (!cursor.isEmpty() && fits && (d.kind == 2 || cursor.amount() > d.slots.size())) d.slots.add(slot);
                return null;
            }
            case 2 -> {
                Drag d = DRAGS.remove(player);
                if (d == null || d.slots.size() < 2 || cursor.isEmpty()) return null;
                int max = maxStack(cursor);
                int count = cursor.amount();
                Map<Integer, ItemStack> placed = new LinkedHashMap<>();
                for (int s : d.slots) {
                    ItemStack in = snap.get(s);
                    int have = in.isEmpty() ? 0 : in.amount();
                    int place = switch (d.kind) {
                        case 0 -> cursor.amount() / d.slots.size();
                        case 1 -> 1;
                        default -> max;
                    };
                    int total = Math.min(place + have, max);
                    count -= total - have;
                    placed.put(s, cursor.withAmount(total));
                }
                return new DragResult(d.kind, placed, count > 0 ? cursor.withAmount(count) : ItemStack.EMPTY, cursor);
            }
            default -> {
                DRAGS.remove(player);
                return null;
            }
        }
    }
}
