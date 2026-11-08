// K4(PR #89) 사후 검토에서 찾은 것: 메뉴 제목 바꾸기 경합 2건, 한 사람 창만 제목 바꾸기(retitleMenu), stopSound 의 잘못된 소리 이름.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.audience.Sound;
import org.domi.furnace.api.menu.Menu;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.menu.AbstractContainerMenu;
import org.domi.furnace.server.menu.SimpleContainer;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MenuTitleSoundParityTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private EmbeddedChannel ch;
    private Player p;
    private PlayerHandle handle;
    /** 클라이언트 화면에 마지막으로 보낸 제목(open_screen 의 제목). */
    private volatile Text shown;
    /** 연결 스레드에서 open_screen 을 보낸 직후·opened() 전에 끼어들 일(다른 스레드의 setTitle 흉내). */
    private Runnable betweenSendAndOpened = () -> {};

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
        // ConnectionHandler.openPluginMenu / retitlePluginMenu 와 같은 순서를 흉내 내는 호스트
        p.inventoryHost(new Player.InventoryHost() {
            public org.domi.furnace.server.entity.ItemStack slot(int w) { return org.domi.furnace.server.entity.ItemStack.EMPTY; }

            public int selected() { return 0; }

            public void replace(int w, org.domi.furnace.server.entity.ItemStack e, org.domi.furnace.server.entity.ItemStack n) {}

            public int permissionLevel() { return 0; }

            public synchronized void execute(Runnable r) { r.run(); }

            public void openPluginMenu(MenuSession session) {
                synchronized (p.menuLock()) {
                    var m = session.createMenu(1, new SimpleContainer(36), null);
                    shown = session.titleForOpen();       // send(new OpenScreen(..., session.titleForOpen()))
                    p.setContainerMenu(m);
                    betweenSendAndOpened.run();
                    session.opened();                     // 여기서야 Menu 리스너가 붙는다
                }
            }

            public void retitlePluginMenu(MenuSession session, Text title) {
                synchronized (p.menuLock()) {
                    var m = p.containerMenu();
                    if (m == null || m.pluginOwner() != session) return;
                    shown = title;                        // send(new OpenScreen(..., title))
                }
            }

            public void closeContainer(AbstractContainerMenu expect) {
                if (p.containerMenu() == expect) {
                    if (expect.pluginOwner() instanceof MenuSession ms) ms.closed();
                    p.setContainerMenu(null);
                }
            }
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    /** open_screen 을 보낸 뒤·리스너가 붙기 전에 다른 스레드가 바꾼 제목도 화면에 온다(고치기 전에는 옛 제목이 남았다). */
    @Test
    void titleChangedWhileOpeningIsLost() throws Exception {
        Menu menu = Menu.chest(Text.text("상점"), 1);
        Text later = Text.text("상점 - 2쪽");
        betweenSendAndOpened = () -> {
            Thread t = new Thread(() -> menu.setTitle(later));
            t.start();
            try { t.join(); } catch (InterruptedException e) { throw new AssertionError(e); }
        };
        handle.openMenu(menu);
        assertSame(later, menu.title(), "Menu 는 새 제목을 갖고 있다");
        assertEquals(later, shown, "화면도 새 제목");
    }

    /** 두 스레드가 거의 함께 setTitle 해 알림이 거꾸로 도착해도 화면은 마지막 제목이다(고치기 전에는 늦게 온 알림의 제목이 덮었다). */
    @Test
    void racingSetTitleLeavesStaleScreen() throws Exception {
        Menu menu = Menu.chest(Text.text("처음"), 1);
        Text a = Text.text("A"), b = Text.text("B");
        CountDownLatch aAssigned = new CountDownLatch(1), bDone = new CountDownLatch(1);
        // 세션보다 먼저 붙는 리스너: 스레드 A 가 제목을 쓴 뒤 알림을 돌리는 도중에 멈춰 B 가 먼저 끝나게 한다
        menu.addListener(new Menu.Listener() {
            public void slotChanged(Menu m, int slot, org.domi.furnace.api.item.ItemStack item) {}

            public void titleChanged(Menu m, Text title) {
                if (title != a) return;
                aAssigned.countDown();
                try { bDone.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new AssertionError(e); }
            }
        });
        handle.openMenu(menu);
        Thread ta = new Thread(() -> menu.setTitle(a));
        ta.start();
        assertTrue(aAssigned.await(5, TimeUnit.SECONDS));
        menu.setTitle(b);
        bDone.countDown();
        ta.join();
        assertSame(b, menu.title(), "마지막에 쓴 제목은 B");
        assertEquals(b, shown, "화면도 B");
    }

    /** stopSound 도 playSound 처럼 리소스 id 가 아닌 이름을 막는다 — 바닐라 클라이언트는 그 이름을 읽다 실패하면 접속을 끊는다. */
    @Test
    void stopSoundRejectsInvalidResourceLocation() {
        drain();
        assertThrows(IllegalArgumentException.class, () -> handle.stopSound("Entity.Player.Levelup"));
        assertThrows(IllegalArgumentException.class, () -> handle.stopSound(Sound.Source.MASTER, "a b"));
        assertEquals(List.of(), drain(), "아무것도 보내지 않는다");
        handle.stopSound("custom:music/boss_1");
        handle.stopSound(Sound.Source.MUSIC, null);
        assertEquals(List.of(0x6A, 0x6A), drain());
        // 높이는 바닐라 /playsound 처럼 0~2(0.5 밑은 클라이언트가 올린다)
        assertEquals(0.0F, Sound.sound("block.note_block.harp", Sound.Source.RECORD, 1, 0).pitch());
        assertThrows(IllegalArgumentException.class, () -> Sound.sound("a", Sound.Source.MASTER, 1, -0.1F));
    }

    /** retitleMenu 는 그 사람 창만 바꾸고, 닫으면 사라진다. Menu 제목은 그대로. */
    @Test
    void retitleMenuIsPerViewerAndEndsOnClose() {
        Menu menu = Menu.chest(Text.text("상점"), 1);
        handle.openMenu(menu);
        Text mine = Text.text("상점 - 내 쪽");
        handle.retitleMenu(mine);
        assertEquals(mine, shown);
        assertSame(mine, handle.menuTitle(menu));
        assertEquals(Text.text("상점"), menu.title(), "Menu 제목은 그대로");
        menu.setTitle(Text.text("모두에게"));
        assertEquals(mine, shown, "이 사람 창 값이 이긴다");
        handle.closeMenu();
        assertNull(handle.menuTitle(menu));
        handle.retitleMenu(Text.text("닫힌 뒤"));   // 열린 메뉴가 없으면 아무것도 안 한다
        handle.openMenu(menu);
        assertEquals(Text.text("모두에게"), shown, "다시 열면 Menu 제목");
        assertSame(menu.title(), handle.menuTitle(menu));
    }

    private List<Integer> drain() {
        ch.runPendingTasks();
        List<Integer> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf bb = (ByteBuf) o;
            out.add(Types.readVarInt(bb));
            bb.release();
        }
        return out;
    }
}
