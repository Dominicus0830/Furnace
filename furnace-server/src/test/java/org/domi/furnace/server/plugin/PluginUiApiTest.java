// 플러그인 UI API: 보스바·사이드바(패킷 순서와 바뀐 것만 보내기)·메뉴(클릭 취소 기본, allow, 칸 변경 반영, 열기/닫기 처리기).
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.domi.furnace.api.bossbar.BossBar;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.menu.ClickKind;
import org.domi.furnace.api.menu.Menu;
import org.domi.furnace.api.scoreboard.Sidebar;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.menu.AbstractContainerMenu;
import org.domi.furnace.server.menu.ClickType;
import org.domi.furnace.server.menu.SimpleContainer;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginUiApiTest {

    private static final int BOSS = 0x0A, OBJECTIVE = 0x5E, DISPLAY = 0x57, SCORE = 0x61, RESET = 0x44;

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private EmbeddedChannel ch;
    private Player p;
    private PlayerHandle handle;

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        ch = new EmbeddedChannel();
        p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "A", ch);
        p.inventory(new PlayerInventory());
        p.vitals().env(core.vitalsEnv());
        handle = host.player(p);
    }

    @AfterEach
    void tearDown() throws Exception {
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    /** 나간 패킷 번호들(보스바는 UUID 뒤 행동 번호를 "id:행동" 으로). */
    private List<String> drain() {
        ch.runPendingTasks();
        List<String> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf bb = (ByteBuf) o;
            int id = Types.readVarInt(bb);
            if (id == BOSS) {
                bb.skipBytes(16);
                out.add(id + ":" + Types.readVarInt(bb));
            } else out.add(Integer.toString(id));
            bb.release();
        }
        return out;
    }

    // ------------------------------------------------------------------ 보스바

    @Test
    void bossBarSendsOnlyWhatChanged() {
        BossBar bar = BossBar.of(Text.text("용"), 0.5f);
        handle.showBossBar(bar);
        assertEquals(List.of("10:0"), drain(), "추가");
        handle.showBossBar(bar);
        assertEquals(List.of(), drain(), "이미 보이면 아무것도 안 보낸다");
        bar.progress(0.25f).name(Text.text("새 이름")).color(BossBar.Color.RED).overlay(BossBar.Overlay.NOTCHED_10).addFlag(BossBar.Flag.DARKEN_SCREEN);
        assertEquals(List.of("10:2", "10:3", "10:4", "10:4", "10:5"), drain());
        bar.progress(0.25f).color(BossBar.Color.RED);
        assertEquals(List.of(), drain(), "같은 값이면 보내지 않는다");
        handle.hideBossBar(bar);
        assertEquals(List.of("10:1"), drain());
        bar.progress(1f);
        assertEquals(List.of(), drain(), "숨긴 뒤에는 따라가지 않는다");
    }

    @Test
    void bossBarClampsProgressAndRejectsNaN() {
        BossBar bar = BossBar.of(Text.text("x"), 2f);
        assertEquals(1f, bar.progress());
        bar.progress(-3f);
        assertEquals(0f, bar.progress());
        assertThrows(IllegalArgumentException.class, () -> bar.progress(Float.NaN));
    }

    @Test
    void bossBarSubscriptionIsDroppedOnDisconnect() {
        BossBar bar = BossBar.of(Text.text("x"), 0f);
        handle.showBossBar(bar);
        drain();
        handle.dispose();
        bar.progress(1f);
        assertEquals(List.of(), drain());
    }

    // ------------------------------------------------------------------ 사이드바

    @Test
    void sidebarShowsLinesAndDiffs() {
        Sidebar sb = Sidebar.of(Text.text("제목"), List.of(Text.text("a"), Text.text("b"), Text.text("c")));
        handle.showSidebar(sb);
        assertEquals(List.of("94", "87", "97", "97", "97"), drain(), "목표 추가 → 슬롯 → 줄 3개");
        sb.line(1, Text.text("B"));
        assertEquals(List.of("97"), drain(), "바뀐 줄 하나만");
        sb.title(Text.text("새 제목"));
        assertEquals(List.of("94"), drain(), "제목만");
        sb.addLine(Text.text("d"));
        assertEquals(List.of("97", "97", "97", "97"), drain(), "줄 수가 늘면 점수가 밀리므로 전부");
        sb.lines(Text.text("a"), Text.text("B"));
        assertEquals(List.of("97", "97", "68", "68"), drain(), "줄어들면 전부 다시 + 남는 줄 지우기");
        handle.hideSidebar(sb);
        assertEquals(List.of("87", "94"), drain(), "슬롯 비우기 → 목표 제거");
    }

    @Test
    void sidebarReplacedByAnotherAndLimitedTo15Lines() {
        Sidebar a = Sidebar.of(Text.text("A"), List.of(Text.text("1")));
        Sidebar b = Sidebar.of(Text.text("B"), List.of(Text.text("2")));
        handle.showSidebar(a);
        drain();
        handle.showSidebar(b);
        assertEquals(List.of("87", "94", "94", "87", "97"), drain(), "A 를 걷고 B 를 건다");
        handle.hideSidebar(a);
        assertEquals(List.of(), drain(), "보이지 않는 사이드바를 숨기라고 해도 B 는 그대로");
        List<Text> many = new ArrayList<>();
        for (int i = 0; i < 16; i++) many.add(Text.text("l" + i));
        assertThrows(IllegalArgumentException.class, () -> a.lines(many));
    }

    // ------------------------------------------------------------------ 메뉴

    /** 연결 없이 메뉴 열기를 흉내 내는 호스트: 즉시 실행, 열면 서버 메뉴를 만들어 건다. */
    private void useFakeHost() {
        var events = new PluginGameEvents(host);
        p.inventoryHost(new Player.InventoryHost() {
            public org.domi.furnace.server.entity.ItemStack slot(int w) { return org.domi.furnace.server.entity.ItemStack.EMPTY; }

            public int selected() { return 0; }

            public void replace(int w, org.domi.furnace.server.entity.ItemStack e, org.domi.furnace.server.entity.ItemStack n) {}

            public int permissionLevel() { return 0; }

            public void execute(Runnable r) { r.run(); }

            public void openPluginMenu(MenuSession session) {
                var m = session.createMenu(1, new SimpleContainer(36), null);
                session.titleForOpen();   // ConnectionHandler 가 open_screen 에 싣는 제목
                p.setContainerMenu(m);
                session.opened();
            }

            public void retitlePluginMenu(MenuSession session, Text title) { retitles.add(session.api().title() == title ? "new" : "stale"); }

            public void closeContainer(AbstractContainerMenu expect) {
                if (p.containerMenu() == expect) {
                    events.inventoryClose(p, expect);
                    p.setContainerMenu(null);
                }
            }
        });
    }

    private final List<String> retitles = new ArrayList<>();

    private boolean click(int slot, int button, ClickType type) {
        return new PluginGameEvents(host).inventoryClick(p, p.containerMenu(), slot, button, type);
    }

    @Test
    void menuClicksAreCancelledUnlessAllowed() {
        useFakeHost();
        List<String> log = new CopyOnWriteArrayList<>();
        Menu menu = Menu.chest(Text.text("상점"), 3)
                .button(13, ItemStack.of("diamond"), c -> log.add("buy:" + c.kind() + ":" + c.slot() + ":" + c.clicked().type()))
                .button(14, ItemStack.of("emerald"), c -> {
                    log.add("free");
                    c.allow();
                })
                .onClick(c -> log.add("any:" + c.windowSlot() + ":" + c.inMenu()))
                .onOpen(pl -> log.add("open"))
                .onClose(pl -> log.add("close"));
        handle.openMenu(menu);
        assertSame(menu, handle.currentMenu());
        assertEquals(List.of(handle), List.copyOf(menu.viewers()));
        assertEquals("diamond", p.containerMenu().getSlot(13).getItem() == null ? "" : org.domi.furnace.server.item.ItemContext.name("minecraft:item", p.containerMenu().getSlot(13).getItem().itemId()).substring(10));

        assertFalse(click(13, 1, ClickType.PICKUP), "처리기가 allow 를 안 부르면 취소");
        assertEquals("buy:RIGHT:13:minecraft:diamond", log.get(1));
        assertTrue(click(14, 0, ClickType.PICKUP), "allow 하면 통과");
        assertFalse(click(30, 0, ClickType.QUICK_MOVE), "플레이어 인벤토리 칸 클릭도 취소(any 처리기는 불린다)");
        assertEquals("any:30:false", log.get(log.size() - 1));
        assertFalse(click(5, 3, ClickType.SWAP));
        assertEquals(ClickKind.NUMBER_KEY, kindOf(5, 3, ClickType.SWAP));
        assertEquals(ClickKind.SWAP_OFFHAND, kindOf(5, 40, ClickType.SWAP));
        assertEquals(ClickKind.CONTROL_DROP, kindOf(5, 1, ClickType.THROW));
        assertEquals(ClickKind.SHIFT_LEFT, kindOf(5, 0, ClickType.QUICK_MOVE));
        assertEquals(ClickKind.DOUBLE_CLICK, kindOf(5, 0, ClickType.PICKUP_ALL));

        handle.closeMenu();
        assertNull(handle.currentMenu());
        assertEquals(List.of(), List.copyOf(menu.viewers()));
        assertEquals("close", log.get(log.size() - 1));
        assertEquals("open", log.get(0));
    }

    private ClickKind kindOf(int slot, int button, ClickType type) {
        List<ClickKind> seen = new ArrayList<>();
        Menu m = handle.currentMenu();
        m.onClick(c -> seen.add(c.kind()));
        click(slot, button, type);
        return seen.get(0);
    }

    @Test
    void storageMenuWritesClickResultsBackToTheMenu() {
        useFakeHost();
        Menu menu = Menu.chest(Text.text("보관함"), 1).storage(true).onClick(c -> c.allow());
        menu.set(0, ItemStack.of("stone").withAmount(10));
        handle.openMenu(menu);
        var open = p.containerMenu();
        assertTrue(menu.isStorage());
        var session = (MenuSession) open.pluginOwner();
        // 클릭 처리 중 이 사람 용기에서 일어난 변화(0번 칸 돌을 2번 칸으로 옮김)가 메뉴 칸으로 돌아간다
        session.runStorageClick(() -> {
            var stone = open.getSlot(0).getItem();
            open.getSlot(0).set(org.domi.furnace.server.entity.ItemStack.EMPTY);
            open.getSlot(2).set(stone);
        });
        assertTrue(menu.item(0).isEmpty(), "옮긴 칸은 메뉴에서도 비었다: " + menu.item(0));
        assertEquals(10, menu.item(2).amount(), "놓은 칸이 메뉴에 남는다");
        // 보관함이 아닌 메뉴는 보는 사람마다 사본이라 클릭 결과가 메뉴로 돌아가지 않는다
        handle.closeMenu();
        Menu plain = Menu.chest(Text.text("상점"), 1).onClick(c -> c.allow());
        plain.set(0, ItemStack.of("stone"));
        handle.openMenu(plain);
        assertFalse(plain.isStorage());
        p.containerMenu().getSlot(0).set(org.domi.furnace.server.entity.ItemStack.EMPTY);
        assertTrue(plain.item(0).type().endsWith("stone"), "보관함이 아닌 메뉴의 칸은 그대로: " + plain.item(0));
    }

    @Test
    void menuChangesReachTheOpenScreenAndThrowingHandlerCancels() {
        useFakeHost();
        Menu menu = Menu.chest(Text.text("m"), 1).button(0, ItemStack.of("stone"), c -> {
            throw new IllegalStateException("boom");
        });
        handle.openMenu(menu);
        var open = p.containerMenu();
        assertEquals(9 + 36, open.slots.size());
        assertFalse(open.getSlot(1).hasItem());
        menu.set(1, ItemStack.of("apple").withAmount(5));
        assertTrue(open.getSlot(1).hasItem());
        assertEquals(5, open.getSlot(1).getItem().count());
        menu.clear(1);
        assertFalse(open.getSlot(1).hasItem());
        assertFalse(click(0, 0, ClickType.PICKUP), "처리기가 예외를 던지면 클릭은 취소로 둔다");
    }

    @Test
    void menuWithUnknownItemFailsBeforeOpening() {
        useFakeHost();
        Menu menu = Menu.chest(Text.text("m"), 1).set(0, ItemStack.of("no_such_item"));
        assertThrows(IllegalArgumentException.class, () -> handle.openMenu(menu));
        assertNull(handle.currentMenu());
    }

    @Test
    void menuTypesBuildMatchingServerMenus() {
        useFakeHost();
        for (var type : org.domi.furnace.api.menu.MenuType.values()) {
            handle.openMenu(Menu.of(type, Text.text("t")));
            var m = p.containerMenu();
            assertEquals(type.size() + 36, m.slots.size(), type.name());
            assertEquals(type.key(), m.menuType());
            handle.closeMenu();
        }
    }

    @Test
    void menuTitleChangeReachesOpenScreenOnly() {
        useFakeHost();
        Menu menu = Menu.chest(Text.text("앞"), 1);
        menu.setTitle(Text.text("닫힌 동안은 알릴 곳이 없다"));
        assertEquals(List.of(), retitles);
        handle.openMenu(menu);
        Text next = Text.text("뒤");
        menu.setTitle(next);
        assertSame(next, menu.title());
        assertEquals(List.of("new"), retitles);
        menu.setTitle(next);   // 같은 제목이면 다시 안 보낸다
        assertEquals(List.of("new"), retitles);
        handle.closeMenu();
        menu.setTitle(Text.text("닫힌 뒤"));
        assertEquals(List.of("new"), retitles);
    }

    @Test
    void soundAndStopSoundPackets() {
        var s = org.domi.furnace.api.audience.Sound.sound("entity.player.levelup", org.domi.furnace.api.audience.Sound.Source.PLAYER, 0.5F, 1.5F);
        handle.playSound(s);
        handle.playSound(s, 10, 64, 10);
        handle.stopSound(org.domi.furnace.api.audience.Sound.Source.MASTER, "minecraft:entity.player.levelup");
        handle.stopAllSounds();
        handle.sendActionBar(Text.text("바"));
        assertEquals(List.of("104", "104", "106", "106", "76"), drain());
        assertThrows(IllegalArgumentException.class, () -> org.domi.furnace.api.audience.Sound.sound("Bad Name", org.domi.furnace.api.audience.Sound.Source.MASTER));
        assertThrows(IllegalArgumentException.class, () -> org.domi.furnace.api.audience.Sound.sound("a.b", org.domi.furnace.api.audience.Sound.Source.MASTER, 1, 3));
    }
}
