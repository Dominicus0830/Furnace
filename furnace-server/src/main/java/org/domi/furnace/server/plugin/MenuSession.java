// 플러그인 Menu 하나를 한 플레이어가 열어 본 것 — 그 사람 몫의 용기(칸 내용 사본)와 서버 메뉴를 쥔다.
// 칸 변경은 Menu 리스너로 받아 연결 스레드에서 용기에 반영하고 broadcastChanges 로 클라이언트에 보낸다. 변환 실패(모르는 아이템)는 열기 전에 던진다.
// 보관함 메뉴(Menu#isStorage)는 클릭 하나를 Menu 의 보관함 잠금 안에서 처리한다: 직전에 메뉴 내용을 용기에 다시 읽고, 클릭이 바꾼 위쪽 칸만
// 메뉴에 되돌려 쓴다 — 보는 사람마다 용기 사본이 있어도 클릭이 한 줄로 서서 같은 아이템을 둘이 꺼내는 일이 없다.
package org.domi.furnace.server.plugin;

import java.util.List;
import org.domi.furnace.api.menu.ClickKind;
import org.domi.furnace.api.menu.Menu;
import org.domi.furnace.api.menu.MenuClick;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.menu.AbstractContainerMenu;
import org.domi.furnace.server.menu.ChestMenu;
import org.domi.furnace.server.menu.ClickType;
import org.domi.furnace.server.menu.Container;
import org.domi.furnace.server.menu.DispenserMenu;
import org.domi.furnace.server.menu.HopperMenu;
import org.domi.furnace.server.menu.MenuPlayer;
import org.domi.furnace.server.menu.SimpleContainer;

public final class MenuSession implements Menu.Listener {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MenuSession.class);

    private final PlayerHandle player;
    private final Menu api;
    private final PluginHost host;
    private final SimpleContainer container;
    private AbstractContainerMenu menu;   // 연결 스레드(menuLock)에서만
    private boolean attached;
    /** Player#retitleMenu 로 이 사람 창에만 준 제목(null 이면 Menu 제목을 따른다). Paper InventoryView#setTitle 처럼 창이 닫히면 사라진다. */
    private volatile Text viewerTitle;
    /** 연결 스레드(menuLock)에서만: 이 사람 화면에 마지막으로 보낸 제목(open_screen). */
    private Text shownTitle;
    /** 닫혔거나 열리지 못했다 — 더는 이 사람 창이 아니다. */
    private volatile boolean ended;

    /** 모르는 아이템이 있으면 IllegalArgumentException. */
    MenuSession(PlayerHandle player, Menu api, PluginHost host) {
        this.player = player;
        this.api = api;
        this.host = host;
        this.container = new SimpleContainer(api.size());
        refresh();
    }

    public Menu api() { return api; }

    /** 이 사람 화면에 보일 제목: retitleMenu 로 준 것, 없으면 Menu 제목. */
    public Text title() {
        Text t = viewerTitle;
        return t != null ? t : api.title();
    }

    /** 연결 스레드에서 menuLock 을 쥔 채: open_screen 에 실을 제목. 보낸 값으로 기억해 둔다(그 뒤 바뀐 것은 opened() 가 다시 보낸다). */
    public Text titleForOpen() {
        shownTitle = title();
        return shownTitle;
    }

    /** 연결 스레드에서: 열기가 취소됐다(InventoryOpenEvent 취소·접속 끊김). */
    public void openFailed() { ended = true; }

    /** 닫혔거나 열리지 못했다. */
    boolean ended() { return ended; }

    /** 이 사람 창의 제목만 바꾼다(Menu·다른 보는 사람은 그대로). 어느 스레드에서든. */
    void setViewerTitle(Text title) {
        viewerTitle = title;
        scheduleTitleSync();
    }

    PlayerHandle player() { return player; }

    private org.domi.furnace.server.world.ItemRegistry items() { return host.core().world().itemRegistry(); }

    private void refresh() {
        List<org.domi.furnace.api.item.ItemStack> all = api.items();
        ItemStack[] conv = new ItemStack[all.size()];
        for (int i = 0; i < conv.length; i++) conv[i] = ApiConvert.item(all.get(i), items());
        for (int i = 0; i < conv.length; i++) container.set(i, conv[i]);
    }

    /** 연결 스레드에서: 서버 메뉴를 만든다. */
    public AbstractContainerMenu createMenu(int containerId, Container playerInventory, MenuPlayer opener) {
        AbstractContainerMenu m = switch (api.type()) {
            case DISPENSER -> new DispenserMenu(containerId, playerInventory, container, null, opener);
            case HOPPER -> new HopperMenu(containerId, playerInventory, container, null, opener);
            default -> new ChestMenu(api.type().key(), containerId, playerInventory, container, api.type().rows(), null, opener);
        };
        m.pluginOwner(this);
        menu = m;
        return m;
    }

    /** 연결 스레드에서, 화면이 실제로 열린 뒤. */
    public void opened() {
        attached = true;
        api.addListener(this);
        try {
            refresh();   // 만든 뒤 열릴 때까지 사이에 바뀐 것
            menu.broadcastChanges();
        } catch (RuntimeException e) {
            log.warn("메뉴 새로고침 실패: {}", e.toString());
        }
        syncTitle();     // open_screen 을 보낸 뒤 리스너가 붙기 전에 바뀐 제목
        api.viewerOpened(player);
    }

    /** 연결 스레드에서, 화면이 닫힐 때(InventoryCloseEvent 와 같은 자리). */
    public void closed() {
        ended = true;
        if (!attached) return;
        attached = false;
        api.removeListener(this);
        menu = null;
        api.viewerClosed(player);
    }

    /**
     * 인벤토리 줄(플레이어 region 스레드)에서 menuLock 을 쥔 채: 보관함 메뉴의 클릭 하나(body = 이벤트 → 바닐라 클릭 재현 → 변경 보내기)를 보관함 잠금 안에서 돌리고,
     * 클릭이 바꾼 위쪽 칸을 메뉴에 되돌려 쓴다. 클릭 처리 중 처리기가 Menu#set 으로 바꾼 칸은 그 값이 이긴다(클릭이 그 칸을 바꾸지 않았다면).
     */
    public void runStorageClick(Runnable body) {
        synchronized (api.storageLock()) {
            refresh();   // 다른 보는 사람·플러그인이 바꾼 것 — 이 사람 용기에 아직 오지 않았을 수 있다
            int n = container.size();
            ItemStack[] before = new ItemStack[n];
            for (int i = 0; i < n; i++) before[i] = container.get(i);
            try {
                body.run();
            } finally {
                for (int i = 0; i < n; i++) {
                    ItemStack now = container.get(i);
                    if (now == before[i] || now.equals(before[i])) continue;
                    api.set(i, ApiConvert.item(now, items()));   // 다른 보는 사람에게도 간다(이 사람 것은 같은 값이라 그대로)
                }
            }
        }
    }

    @Override
    public void slotChanged(Menu m, int slot, org.domi.furnace.api.item.ItemStack item) {
        ItemStack conv;
        try {
            conv = ApiConvert.item(item, items());
        } catch (IllegalArgumentException e) {
            log.warn("메뉴 칸 {} 을 보일 수 없다: {}", slot, e.getMessage());
            return;
        }
        var host0 = player.handle().inventoryHost();
        if (host0 == null) return;
        host0.execute(() -> {
            synchronized (player.handle().menuLock()) {
                if (menu == null) return;
                container.set(slot, conv);
                menu.broadcastChanges();
            }
        });
    }

    /**
     * 알림에 실린 제목은 쓰지 않는다 — 두 스레드가 거의 함께 setTitle 하면 알림이 거꾸로 올 수 있어, 연결 스레드에서 그때의 제목을 다시 읽어
     * 화면과 다를 때만 보낸다.
     */
    @Override
    public void titleChanged(Menu m, Text title) { scheduleTitleSync(); }

    private void scheduleTitleSync() {
        var host0 = player.handle().inventoryHost();
        if (host0 == null) return;
        host0.execute(() -> {
            synchronized (player.handle().menuLock()) { syncTitle(); }
        });
    }

    /** 연결 스레드에서 menuLock 을 쥔 채: 보일 제목이 화면과 다르면 같은 창 번호로 다시 연다. */
    private void syncTitle() {
        if (menu == null || shownTitle == null) return;
        Text now = title();
        if (now.equals(shownTitle)) return;
        var host0 = player.handle().inventoryHost();
        if (host0 == null) return;
        shownTitle = now;
        host0.retitlePluginMenu(this, now);
    }

    /** 클릭을 메뉴 처리기에 전한다. 허용하면 true. */
    boolean click(AbstractContainerMenu m, int windowSlot, int button, ClickType type) {
        ClickKind kind;
        int hotbar = -1;
        switch (type) {
            case PICKUP -> kind = button == 1 ? ClickKind.RIGHT : ClickKind.LEFT;
            case QUICK_MOVE -> kind = button == 1 ? ClickKind.SHIFT_RIGHT : ClickKind.SHIFT_LEFT;
            case SWAP -> {
                if (button == 40) kind = ClickKind.SWAP_OFFHAND;
                else {
                    kind = ClickKind.NUMBER_KEY;
                    hotbar = button;
                }
            }
            case CLONE -> kind = ClickKind.MIDDLE;
            case THROW -> kind = button == 1 ? ClickKind.CONTROL_DROP : ClickKind.DROP;
            case QUICK_CRAFT -> kind = ClickKind.DRAG;
            case PICKUP_ALL -> kind = ClickKind.DOUBLE_CLICK;
            default -> kind = ClickKind.UNKNOWN;
        }
        var clicked = windowSlot >= 0 && windowSlot < m.slots.size() ? ApiConvert.item(m.getSlot(windowSlot).getItem(), items())
                : org.domi.furnace.api.item.ItemStack.EMPTY;
        var cursor = ApiConvert.item(m.getCarried(), items());
        MenuClick click = new MenuClick(player, api, windowSlot < 0 && windowSlot != MenuClick.OUTSIDE ? MenuClick.OUTSIDE : windowSlot,
                kind, hotbar, clicked, cursor);
        api.dispatchClick(click);
        return click.isAllowed();
    }
}
