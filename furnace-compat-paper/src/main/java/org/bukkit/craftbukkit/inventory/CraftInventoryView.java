// 플레이어가 보는 창(Paper 와 같은 이름 — T-264) — 위쪽 인벤토리(플러그인 메뉴면 CraftInventoryCustom, 아니면 이벤트 사본 칸의 CraftInventory*)와
// 아래쪽 플레이어 인벤토리. 칸 규칙은 상위 CraftAbstractInventoryView.
package org.bukkit.craftbukkit.inventory;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.CompatItems;
import org.domi.furnace.compat.paper.UnsupportedFeature;

public class CraftInventoryView extends CraftAbstractInventoryView {

    private final CraftPlayer player;
    private final Inventory top;
    private final InventoryType type;
    private final String title;
    /** 이벤트 시점의 커서(빈 것은 EMPTY). 이벤트 밖에서 만든 창은 null — 커서를 모른다. */
    private final org.domi.furnace.api.item.ItemStack cursor;

    public CraftInventoryView(CraftPlayer player, Inventory top, InventoryType type, String title, org.domi.furnace.api.item.ItemStack cursor) {
        this.player = player;
        this.top = top;
        this.type = type;
        this.title = title;
        this.cursor = cursor;
    }

    @Override
    public Inventory getTopInventory() { return top; }

    @Override
    public Inventory getBottomInventory() { return player.getInventory(); }

    @Override
    public HumanEntity getPlayer() { return player; }

    @Override
    public InventoryType getType() { return type; }

    @Override
    public void setCursor(ItemStack item) {
        throw new UnsupportedFeature("InventoryView#setCursor", "there is no Furnace API to change the cursor item");
    }

    /** 커서 아이템(비었으면 AIR). 이벤트 밖에서 얻은 창은 UnsupportedFeature. */
    @Override
    public ItemStack getCursor() {
        if (cursor == null) throw new UnsupportedFeature("InventoryView#getCursor", "the cursor item is only known inside an inventory event");
        return CompatItems.copyOf(cursor);
    }

    @Override
    public void open() {
        player.openInventory(top);
    }

    /** Paper: 이 창의 제목(setTitle 로 바꿨으면 그 값). 같은 인벤토리를 보는 다른 사람의 setTitle 은 보이지 않는다. */
    @Override
    public String getTitle() {
        if (!(top instanceof CraftInventoryCustom m)) return title;
        org.domi.furnace.api.text.Text shown = player.furnaceHandle().menuTitle(m.menu());
        if (shown == null || shown == m.menu().title()) return m.titleText();
        String raw = m.retitledRaw(player.getUniqueId(), shown);
        return raw != null ? raw : Compat.legacy(shown);
    }

    @Override
    public String getOriginalTitle() { return top instanceof CraftInventoryCustom m ? m.titleText() : title; }

    /**
     * Paper CraftInventoryView#setTitle: 이 플레이어 창에만 open_screen 을 다시 보낸다 — 인벤토리 제목·다른 보는 사람·다음에 여는 창은 그대로.
     * null 은 IllegalArgumentException("Title cannot be null").
     */
    @Override
    public void setTitle(String title) {
        if (title == null) throw new IllegalArgumentException("Title cannot be null");
        if (top instanceof CraftInventoryCustom m) {
            org.domi.furnace.api.text.Text t = Compat.text(title);
            m.retitled(player.getUniqueId(), t, title);   // getTitle 이 서식 문자열을 그대로 돌려주게(Text 를 거치면 §r 이 붙는 등 달라진다)
            player.furnaceHandle().retitleMenu(t);
            return;
        }
        throw new UnsupportedFeature("InventoryView#setTitle", "only the window of an inventory made by a plugin (createInventory) can change its title");
    }
}
