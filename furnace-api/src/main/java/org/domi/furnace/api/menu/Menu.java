// 메뉴 — 상자·발사기·깔때기 모양의 화면에 아이템을 늘어놓고, 칸마다 클릭 처리기를 다는 GUI.
// 플레이어의 실제 인벤토리나 블록과 무관하다: 칸은 이 객체가 갖고, 열려 있는 모든 사람에게 같은 내용이 보인다.
// 메뉴 안에서 아이템은 꺼내지거나 넣어지지 않는다(MenuClick.allow 를 부르기 전까지). 내용을 바꾸면 보고 있는 사람 화면이 곧바로 따라온다.
//
//   Menu menu = Menu.chest(Text.text("상점"), 3)
//       .fillEmpty(ItemStack.of("gray_stained_glass_pane"))
//       .button(13, ItemStack.of("diamond"), click -> { click.player().sendMessage(Text.text("구매!")); click.close(); })
//       .onClose(p -> p.sendMessage(Text.text("안녕히!")));
//   player.openMenu(menu);
//
// 보관함 메뉴(storage(true))는 상자처럼 움직인다: 허용된 클릭이 메뉴 칸을 실제로 바꾸고, 보는 사람 모두 같은 칸을 쓴다(Bukkit createInventory 와 같은 뜻).
//
// 어느 스레드에서 만들고 바꿔도 된다. 처리기는 그 플레이어의 연결 스레드에서 불린다.
package org.domi.furnace.api.menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.text.Text;

public final class Menu {

    private static final System.Logger LOG = System.getLogger("furnace.menu");

    /** 서버 구현이 열린 화면에 변경을 전하는 데 쓴다. 플러그인은 쓸 일이 없다. */
    public interface Listener {
        void slotChanged(Menu menu, int slot, ItemStack item);

        /** 제목이 바뀌었다. */
        default void titleChanged(Menu menu, Text title) {}
    }

    private final MenuType type;
    private volatile Text title;
    private final Object lock = new Object();
    private final ItemStack[] items;
    @SuppressWarnings("unchecked")
    private final Consumer<MenuClick>[] handlers;
    private volatile Consumer<MenuClick> anyClick;
    private volatile Consumer<Player> openHandler, closeHandler;
    private volatile boolean storage;
    private final Object storageLock = new Object();
    private final Set<Player> viewers = new LinkedHashSet<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    @SuppressWarnings("unchecked")
    private Menu(MenuType type, Text title) {
        this.type = Objects.requireNonNull(type, "type");
        this.title = Objects.requireNonNull(title, "title");
        this.items = new ItemStack[type.size()];
        Arrays.fill(items, ItemStack.EMPTY);
        this.handlers = new Consumer[type.size()];
    }

    public static Menu of(MenuType type, Text title) { return new Menu(type, title); }

    /** 줄 수 1~6 의 상자 모양. */
    public static Menu chest(Text title, int rows) { return new Menu(MenuType.chest(rows), title); }

    public MenuType type() { return type; }

    public Text title() { return title; }

    /**
     * 제목을 바꾼다. 열려 있는 화면에는 같은 창 번호로 화면을 다시 열어(open_screen) 제목을 갈아 끼우고 내용·커서를 다시 보낸다 —
     * 클라이언트에서 화면이 한 번 다시 그려지지만 창은 닫히지 않는다(Paper InventoryView.setTitle 과 같은 방식).
     */
    public Menu setTitle(Text title) {
        Objects.requireNonNull(title, "title");
        if (title.equals(this.title)) return this;
        this.title = title;
        for (Listener l : listeners) l.titleChanged(this, title);
        return this;
    }

    /** 위쪽 칸 수. */
    public int size() { return items.length; }

    private void check(int slot) {
        if (slot < 0 || slot >= items.length) throw new IndexOutOfBoundsException("칸 " + slot + " (0.." + (items.length - 1) + ")");
    }

    public ItemStack item(int slot) {
        check(slot);
        synchronized (lock) { return items[slot]; }
    }

    /** 모든 칸의 사본. */
    public List<ItemStack> items() {
        synchronized (lock) { return List.of(items); }
    }

    // ------------------------------------------------------------------ 내용

    /** 칸에 아이템만 둔다(처리기는 그대로). 빈 아이템이면 비운다. */
    public Menu set(int slot, ItemStack item) {
        check(slot);
        Objects.requireNonNull(item, "item");
        if (storage) {
            synchronized (storageLock) { return set0(slot, item); }   // 보는 사람의 클릭과 한 줄로
        }
        return set0(slot, item);
    }

    private Menu set0(int slot, ItemStack item) {
        ItemStack now = item.isEmpty() ? ItemStack.EMPTY : item;
        synchronized (lock) {
            if (items[slot].equals(now)) return this;
            items[slot] = now;
        }
        for (Listener l : listeners) l.slotChanged(this, slot, now);
        return this;
    }

    /** 칸에 아이템을 두고 눌렀을 때 부를 처리기를 단다. */
    public Menu button(int slot, ItemStack item, Consumer<MenuClick> onClick) {
        Objects.requireNonNull(onClick, "onClick");
        set(slot, item);
        synchronized (lock) { handlers[slot] = onClick; }
        return this;
    }

    /** 칸의 처리기만 바꾼다(null 이면 뗀다). */
    public Menu onClick(int slot, Consumer<MenuClick> handler) {
        check(slot);
        synchronized (lock) { handlers[slot] = handler; }
        return this;
    }

    /** 칸 처리기 다음에, 모든 클릭(플레이어 인벤토리 쪽 포함)에 대해 불린다. */
    public Menu onClick(Consumer<MenuClick> handler) {
        this.anyClick = handler;
        return this;
    }

    /** 칸을 비우고 처리기도 뗀다. */
    public Menu clear(int slot) {
        check(slot);
        synchronized (lock) { handlers[slot] = null; }
        return set(slot, ItemStack.EMPTY);
    }

    public Menu clear() {
        for (int i = 0; i < items.length; i++) clear(i);
        return this;
    }

    /** 비어 있는 칸을 모두 이 아이템으로 채운다(장식용 유리판 등). */
    public Menu fillEmpty(ItemStack filler) {
        for (int i = 0; i < items.length; i++) if (item(i).isEmpty()) set(i, filler);
        return this;
    }

    /** 테두리 칸(바깥 둘레)을 이 아이템으로 채운다. 한 줄짜리면 전부. */
    public Menu border(ItemStack item) {
        int cols = type.columns(), rows = type.rows();
        for (int i = 0; i < items.length; i++) {
            int r = i / cols, c = i % cols;
            if (r == 0 || r == rows - 1 || c == 0 || c == cols - 1) set(i, item);
        }
        return this;
    }

    // ------------------------------------------------------------------ 보관함

    /**
     * 보관함 메뉴로 만든다(기본은 false — 보여 주기만 하는 메뉴). 보관함이면 처리기가 allow() 한 클릭이 바닐라 규칙대로 이 메뉴의 칸을
     * 바꾸고(꺼내기·넣기·나누기·끌어 놓기), 그 결과가 {@link #item(int)} 과 다른 보는 사람 화면에 곧바로 보인다. 보는 사람들의 클릭은
     * 한 번에 하나씩 처리된다(두 사람이 같은 아이템을 동시에 꺼내 복제되지 않는다). 보여 주기만 하는 메뉴에서 allow() 한 클릭은
     * 그 사람 화면에서만 아이템이 움직이고 메뉴 내용은 그대로다. 열기 전에 정한다(연 뒤에 바꾸면 다음 클릭부터 따른다).
     */
    public Menu storage(boolean storage) {
        this.storage = storage;
        return this;
    }

    public boolean isStorage() { return storage; }

    /** 서버 구현 전용: 보관함 메뉴의 클릭 하나를 처리하는 동안 쥐는 잠금(칸 쓰기도 이 잠금 안에서 한다). */
    public Object storageLock() { return storageLock; }

    // ------------------------------------------------------------------ 열기·닫기 처리기

    /** 누군가 이 메뉴를 열었을 때. */
    public Menu onOpen(Consumer<Player> handler) {
        this.openHandler = handler;
        return this;
    }

    /** 누군가 이 메뉴를 닫았을 때(스스로 닫거나, 다른 화면이 열리거나, 접속이 끊겨도). */
    public Menu onClose(Consumer<Player> handler) {
        this.closeHandler = handler;
        return this;
    }

    // ------------------------------------------------------------------ 보는 사람

    /** 지금 이 메뉴를 보고 있는 플레이어(사본). */
    public Collection<Player> viewers() {
        synchronized (lock) { return new ArrayList<>(viewers); }
    }

    // ------------------------------------------------------------------ 서버 구현 전용

    /** 서버 구현 전용. */
    public void addListener(Listener l) { listeners.add(Objects.requireNonNull(l)); }

    /** 서버 구현 전용. */
    public void removeListener(Listener l) { listeners.remove(l); }

    /** 서버 구현 전용: 화면이 열렸다. */
    public void viewerOpened(Player p) {
        synchronized (lock) { viewers.add(p); }
        run(openHandler, p);
    }

    /** 서버 구현 전용: 화면이 닫혔다. */
    public void viewerClosed(Player p) {
        boolean was;
        synchronized (lock) { was = viewers.remove(p); }
        if (was) run(closeHandler, p);
    }

    /** 서버 구현 전용: 클릭을 처리기에 전한다. 처리기가 던진 예외는 기록하고 클릭은 취소로 둔다. */
    public void dispatchClick(MenuClick click) {
        Consumer<MenuClick> slotHandler = null;
        int s = click.slot();
        if (s >= 0) synchronized (lock) { slotHandler = handlers[s]; }
        try {
            if (slotHandler != null) slotHandler.accept(click);
            Consumer<MenuClick> any = anyClick;
            if (any != null) any.accept(click);
        } catch (Throwable t) {
            click.deny();
            LOG.log(System.Logger.Level.ERROR, "메뉴 클릭 처리기 오류", t);
        }
    }

    private static void run(Consumer<Player> h, Player p) {
        if (h == null) return;
        try {
            h.accept(p);
        } catch (Throwable t) {
            LOG.log(System.Logger.Level.ERROR, "메뉴 열기/닫기 처리기 오류", t);
        }
    }
}
