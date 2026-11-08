// Bukkit.createInventory 가 만드는 인벤토리 — Furnace 보관함 메뉴(Menu#storage) 위의 Bukkit Inventory.
// 칸은 메뉴가 갖고(어느 스레드에서 읽고 써도 곧바로 된다), 열면 Player#openMenu 로 보인다. 메뉴의 클릭 처리기는 모든 클릭을 허용하고
// 취소 여부는 Bukkit InventoryClickEvent·InventoryDragEvent 처리기가 정한다(Bukkit 의 가상 인벤토리와 같다 — 취소하지 않으면 아이템이 움직인다).
package org.domi.furnace.compat.paper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryHolder;
import org.domi.furnace.api.menu.Menu;
import org.domi.furnace.api.menu.MenuType;

public final class CompatMenuInventory extends CompatInventory {

    /** 메뉴 → 그 메뉴를 감싼 Bukkit 인벤토리(이벤트에서 같은 객체를 돌려주려고 — helper 의 Gui 는 equals 로 견준다). */
    private static final Map<Menu, WeakReference<CompatMenuInventory>> BY_MENU = new WeakHashMap<>();

    private final Menu menu;
    private final String title;

    private CompatMenuInventory(Menu menu, InventoryType type, InventoryHolder owner, String title) {
        super(new Slots(menu), type, () -> owner);
        this.menu = menu;
        this.title = title;
    }

    /** Bukkit.createInventory(owner, size, title): 9..54 의 9 배수. */
    public static CompatMenuInventory chest(InventoryHolder owner, int size, String title) {
        if (size < 9 || size > 54 || size % 9 != 0) {
            throw new IllegalArgumentException("Size for custom inventory must be a multiple of 9 between 9 and 54 slots (got " + size + ")");
        }
        return create(MenuType.chest(size / 9), InventoryType.CHEST, owner, title);
    }

    /** Bukkit.createInventory(owner, type[, title]): Furnace 메뉴 모양이 있는 종류만. */
    public static CompatMenuInventory ofType(InventoryHolder owner, InventoryType type, String title) {
        java.util.Objects.requireNonNull(type, "type");
        if (!type.isCreatable()) throw new IllegalArgumentException("Inventory Type '" + type + "' is not creatable");
        MenuType mt = switch (type) {
            case CHEST, ENDER_CHEST, BARREL, SHULKER_BOX -> MenuType.CHEST_3;
            case DISPENSER, DROPPER -> MenuType.DISPENSER;
            case HOPPER -> MenuType.HOPPER;
            default -> throw new UnsupportedFeature("Bukkit.createInventory(" + type + ")",
                    "Furnace 메뉴 프레임워크에 그 화면 모양이 아직 없다(상자·발사기·깔때기 모양만 된다)");
        };
        return create(mt, type, owner, title == null ? type.getDefaultTitle() : title);
    }

    private static CompatMenuInventory create(MenuType mt, InventoryType type, InventoryHolder owner, String title) {
        String t = title == null ? InventoryType.CHEST.getDefaultTitle() : title;
        Menu menu = Menu.of(mt, Compat.text(t)).storage(true).onClick(c -> c.allow());   // 취소는 Bukkit 처리기가 정한다
        CompatMenuInventory inv = new CompatMenuInventory(menu, type, owner, t);
        synchronized (BY_MENU) { BY_MENU.put(menu, new WeakReference<>(inv)); }
        return inv;
    }

    /** 이 호환 계층이 만든 메뉴면 그 Bukkit 인벤토리, 아니면 null. */
    static CompatMenuInventory of(Menu menu) {
        if (menu == null) return null;
        synchronized (BY_MENU) {
            WeakReference<CompatMenuInventory> r = BY_MENU.get(menu);
            return r == null ? null : r.get();
        }
    }

    Menu menu() { return menu; }

    /** HumanEntity#openInventory: 플러그인이 만든 인벤토리만 열 수 있다. */
    public static org.bukkit.inventory.InventoryView open(org.bukkit.craftbukkit.entity.CraftPlayer p, org.bukkit.inventory.Inventory inventory) {
        if (!(inventory instanceof CompatMenuInventory own)) {
            throw new UnsupportedFeature("HumanEntity#openInventory(" + inventory.getType() + ")",
                    "Furnace 는 플러그인이 만든 인벤토리(Bukkit.createInventory)만 창으로 열 수 있다 — 블록·엔티티 보관함 창 열기는 아직 없다");
        }
        p.furnaceHandle().openMenu(own.menu);
        return new CompatInventoryView(p, own, own.getType(), own.title, null);
    }

    /** HumanEntity#getOpenInventory. */
    public static org.bukkit.inventory.InventoryView openView(org.bukkit.craftbukkit.entity.CraftPlayer p) { return CompatInventories.openView(p); }

    /** createInventory 의 제목(§ 서식 문자열). InventoryView#setTitle 은 그 창만 바꾸고 이 값·메뉴 제목은 그대로다(Paper 와 같다). */
    String title() { return title; }

    @Override
    public List<HumanEntity> getViewers() {
        List<HumanEntity> out = new ArrayList<>();
        for (var p : menu.viewers()) out.add(Compat.player(p));
        return out;
    }

    /** Furnace Inventory 모양으로 본 메뉴 칸 — 메뉴는 어느 스레드에서나 곧바로 읽고 쓴다. */
    private record Slots(Menu menu) implements org.domi.furnace.api.inventory.Inventory {
        @Override
        public String type() { return menu.type().key(); }

        @Override
        public int size() { return menu.size(); }

        @Override
        public CompletableFuture<org.domi.furnace.api.item.ItemStack> get(int slot) {
            try {
                return CompletableFuture.completedFuture(menu.item(slot));
            } catch (IndexOutOfBoundsException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public CompletableFuture<Void> set(int slot, org.domi.furnace.api.item.ItemStack item) {
            try {
                menu.set(slot, item);
                return CompletableFuture.completedFuture(null);
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public CompletableFuture<List<org.domi.furnace.api.item.ItemStack>> contents() { return CompletableFuture.completedFuture(menu.items()); }
    }
}
