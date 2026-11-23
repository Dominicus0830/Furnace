// Bukkit.createInventory 가 만드는 인벤토리 — Furnace 보관함 메뉴(Menu#storage) 위의 Bukkit Inventory.
// 칸은 메뉴가 갖고(어느 스레드에서 읽고 써도 곧바로 된다), 열면 Player#openMenu 로 보인다. 메뉴의 클릭 처리기는 모든 클릭을 허용하고
// 취소 여부는 Bukkit InventoryClickEvent·InventoryDragEvent 처리기가 정한다(Bukkit 의 가상 인벤토리와 같다 — 취소하지 않으면 아이템이 움직인다).
package org.bukkit.craftbukkit.inventory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryHolder;
import org.domi.furnace.api.menu.Menu;
import org.domi.furnace.api.menu.MenuType;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.UnsupportedFeature;
import org.domi.furnace.compat.paper.CompatInventories;
import org.domi.furnace.compat.paper.InventoryParts;

public class CraftInventoryCustom extends CraftInventory {

    /** 메뉴 → 그 메뉴를 감싼 Bukkit 인벤토리(이벤트에서 같은 객체를 돌려주려고 — helper 의 Gui 는 equals 로 견준다). */
    private static final Map<Menu, WeakReference<CraftInventoryCustom>> BY_MENU = new WeakHashMap<>();

    private final Menu menu;
    private final String title;
    /** InventoryView#setTitle 에 준 원 문자열 — 플레이어별, 그때 넘긴 Text 와 짝(같은 Text 가 화면 제목일 때만 쓴다). Paper 는 뷰 필드라 그대로 돌려준다. */
    private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, InventoryParts.RetitledAs> retitled = new java.util.concurrent.ConcurrentHashMap<>();


    private CraftInventoryCustom(Menu menu, InventoryType type, InventoryHolder owner, String title) {
        super(new InventoryParts.MenuSlots(menu), type, () -> owner);
        this.menu = menu;
        this.title = title;
    }

    /** Bukkit.createInventory(owner, size, title): 9..54 의 9 배수. */
    public static CraftInventoryCustom chest(InventoryHolder owner, int size, String title) {
        if (size < 9 || size > 54 || size % 9 != 0) {
            throw new IllegalArgumentException("Size for custom inventory must be a multiple of 9 between 9 and 54 slots (got " + size + ")");
        }
        return create(MenuType.chest(size / 9), InventoryType.CHEST, owner, title);
    }

    /** Bukkit.createInventory(owner, type[, title]): Furnace 메뉴 모양이 있는 종류만. */
    public static CraftInventoryCustom ofType(InventoryHolder owner, InventoryType type, String title) {
        java.util.Objects.requireNonNull(type, "type");
        if (!type.isCreatable()) throw new IllegalArgumentException("Inventory Type '" + type + "' is not creatable");
        MenuType mt = switch (type) {
            case CHEST, ENDER_CHEST, BARREL, SHULKER_BOX -> MenuType.CHEST_3;
            case DISPENSER, DROPPER -> MenuType.DISPENSER;
            case HOPPER -> MenuType.HOPPER;
            default -> throw new UnsupportedFeature("Bukkit.createInventory(" + type + ")",
                    "the Furnace menu framework does not have that screen shape yet (only chest, dispenser and hopper shapes work)");
        };
        return create(mt, type, owner, title == null ? type.getDefaultTitle() : title);
    }

    private static CraftInventoryCustom create(MenuType mt, InventoryType type, InventoryHolder owner, String title) {
        String t = title == null ? InventoryType.CHEST.getDefaultTitle() : title;
        Menu menu = Menu.of(mt, Compat.text(t)).storage(true).onClick(c -> c.allow());   // 취소는 Bukkit 처리기가 정한다
        CraftInventoryCustom inv = new CraftInventoryCustom(menu, type, owner, t);
        synchronized (BY_MENU) { BY_MENU.put(menu, new WeakReference<>(inv)); }
        return inv;
    }

    /** 이 호환 계층이 만든 메뉴면 그 Bukkit 인벤토리, 아니면 null. */
    public static CraftInventoryCustom of(Menu menu) {
        if (menu == null) return null;
        synchronized (BY_MENU) {
            WeakReference<CraftInventoryCustom> r = BY_MENU.get(menu);
            return r == null ? null : r.get();
        }
    }

    public Menu menu() { return menu; }

    /** HumanEntity#openInventory: 플러그인이 만든 인벤토리만 열 수 있다. */
    public static org.bukkit.inventory.InventoryView open(org.bukkit.craftbukkit.entity.CraftPlayer p, org.bukkit.inventory.Inventory inventory) {
        if (!(inventory instanceof CraftInventoryCustom own)) {
            throw new UnsupportedFeature("HumanEntity#openInventory(" + inventory.getType() + ")",
                    "Furnace can only open inventories made by a plugin (Bukkit.createInventory) as a window - opening block or entity storage windows is not available yet");
        }
        p.furnaceHandle().openMenu(own.menu);
        return new CraftInventoryView(p, own, own.getType(), own.title, null);
    }

    /** HumanEntity#getOpenInventory. */
    public static org.bukkit.inventory.InventoryView openView(org.bukkit.craftbukkit.entity.CraftPlayer p) { return CompatInventories.openView(p); }

    /** createInventory 의 제목(§ 서식 문자열). InventoryView#setTitle 은 그 창만 바꾸고 이 값·메뉴 제목은 그대로다(Paper 와 같다). */
    public String titleText() { return title; }

    public void retitled(java.util.UUID player, org.domi.furnace.api.text.Text text, String raw) { retitled.put(player, new InventoryParts.RetitledAs(text, raw)); }

    /** shown 이 그 플레이어의 setTitle 이 넘긴 바로 그 Text 면 원 문자열, 아니면 null. */
    public String retitledRaw(java.util.UUID player, org.domi.furnace.api.text.Text shown) {
        InventoryParts.RetitledAs r = retitled.get(player);
        return r != null && r.text() == shown ? r.raw() : null;
    }

    @Override
    public List<HumanEntity> getViewers() {
        List<HumanEntity> out = new ArrayList<>();
        for (var p : menu.viewers()) out.add(Compat.player(p));
        return out;
    }

    /** Furnace Inventory 모양으로 본 메뉴 칸 — 메뉴는 어느 스레드에서나 곧바로 읽고 쓴다. */
}
