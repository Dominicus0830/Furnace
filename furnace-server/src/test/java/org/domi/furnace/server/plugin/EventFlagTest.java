// 이벤트 구독 여부 필드(EventBusImpl.Flag, T-054): 어떤 등록·해제 순서에서도 hasSubscribers 와 같은 값이고,
// 등록이 바뀐 직후 처음 이벤트부터 처리기가 불리며, 서버 구현(PluginGameEvents)의 입구도 그대로 따른다.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.event.EventSubscription;
import org.domi.furnace.api.event.block.BlockEvent;
import org.domi.furnace.api.event.block.BlockPhysicsEvent;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EventFlagTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;

    @BeforeEach
    void setUp() {
        TestPlugins.LOG.clear();
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
    }

    @AfterEach
    void tearDown() {
        if (host != null) {
            host.pluginsImpl().disableAll();
            host.pluginsImpl().closeAll();
            host.close();
        }
        sched.close();
    }

    static class Ping extends Event implements Cancellable {
        boolean cancelled;
        public boolean isCancelled() { return cancelled; }
        public void setCancelled(boolean c) { cancelled = c; }
    }

    static final class SubPing extends Ping {}

    static final class Other extends Event {}

    private FurnacePlugin plugin() throws Exception {
        Path dir = tmp.resolve("plugins");
        PluginSystemTest.jar(dir.resolve("g.jar"), "furnace-plugin.yml", "name: Gamma\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Gamma\n",
                TestPlugins.Gamma.class);
        host = new PluginHost(core, dir);
        host.pluginsImpl().loadAll();
        host.pluginsImpl().enableAll();
        return host.plugins().plugin("Gamma");
    }

    /** 무작위 등록·해제(하위·상위 타입 섞어서) 뒤마다 모든 필드 값 = hasSubscribers. 필드는 일부는 처음부터, 일부는 중간에 받는다. */
    @Test
    void flagAlwaysEqualsHasSubscribersAfterRandomChanges() throws Exception {
        FurnacePlugin g = plugin();
        List<Class<? extends Event>> types = List.of(Event.class, Ping.class, SubPing.class, Other.class, BlockEvent.class, BlockPhysicsEvent.class);
        for (long seed = 0; seed < 50; seed++) {
            Random rnd = new Random(seed);
            var bus = new EventBusImpl(false);
            List<EventBusImpl.Flag> flags = new ArrayList<>();
            for (int i = 0; i < types.size(); i++) flags.add(i % 2 == 0 ? bus.flag(types.get(i)) : null);   // 나머지는 중간에
            List<EventSubscription> subs = new ArrayList<>();
            for (int step = 0; step < 60; step++) {
                int op = rnd.nextInt(10);
                if (op < 5 || subs.isEmpty()) subs.add(bus.subscribe(g, types.get(rnd.nextInt(types.size())), e -> {}));
                else if (op < 9) subs.remove(rnd.nextInt(subs.size())).unsubscribe();
                else bus.unregisterAll(g);
                if (op >= 9) subs.clear();
                if (step == 20) for (int i = 0; i < flags.size(); i++) if (flags.get(i) == null) flags.set(i, bus.flag(types.get(i)));
                for (int i = 0; i < types.size(); i++) {
                    if (flags.get(i) == null) continue;
                    assertEquals(bus.hasSubscribers(types.get(i)), flags.get(i).on(), "seed " + seed + " step " + step + " " + types.get(i).getSimpleName());
                }
            }
            for (int i = 0; i < flags.size(); i++) assertSame(flags.get(i) == null ? bus.flag(types.get(i)) : flags.get(i), bus.flag(types.get(i)), "같은 종류는 같은 필드");
        }
    }

    /** 등록이 돌아온 직후 처음 발행부터 처리기가 불리고, 해제가 돌아온 뒤에는 필드가 꺼져 있다. */
    @Test
    void registrationTakesEffectOnTheVeryNextCall() throws Exception {
        FurnacePlugin g = plugin();
        var bus = new EventBusImpl(false);
        var flag = bus.flag(SubPing.class);
        assertFalse(flag.on());
        List<String> seen = new ArrayList<>();
        var sub = bus.subscribe(g, Event.class, e -> seen.add("any"));   // 상위 타입 구독도 하위 이벤트 필드를 켠다
        assertTrue(flag.on());
        if (flag.on()) bus.call(new SubPing());
        assertEquals(List.of("any"), seen);
        sub.unsubscribe();
        assertFalse(flag.on());
        var sub2 = bus.subscribe(g, SubPing.class, e -> seen.add("sub"));
        assertTrue(flag.on());
        assertFalse(bus.flag(Ping.class).on(), "하위 타입 구독은 상위 이벤트 필드를 켜지 않는다");
        bus.unregisterAll(g);   // 핫 리로드: 플러그인 처리기를 모두 뗀다
        assertFalse(flag.on());
        sub2.unsubscribe();
        assertFalse(bus.hasSubscribers(SubPing.class));
    }

    /** 여러 스레드가 동시에 등록·해제해도 끝난 뒤 필드와 hasSubscribers 가 어긋나지 않는다(마지막 재계산이 모든 등록을 본다). */
    @Test
    void concurrentChangesNeverLeaveFlagsStale() throws Exception {
        FurnacePlugin g = plugin();
        for (int round = 0; round < 100; round++) {
            var bus = new EventBusImpl(false);
            var fPing = bus.flag(Ping.class);
            var fOther = bus.flag(Other.class);
            var fSub = bus.flag(SubPing.class);
            var stop = new AtomicBoolean();
            Thread reader = new Thread(() -> { while (!stop.get()) { fPing.on(); fOther.on(); } });
            reader.start();
            Thread[] ts = new Thread[4];
            var keep = new java.util.concurrent.CopyOnWriteArrayList<EventSubscription>();
            for (int i = 0; i < ts.length; i++) {
                int n = i;
                ts[i] = new Thread(() -> {
                    for (int k = 0; k < 20; k++) {
                        EventSubscription s = n % 2 == 0 ? bus.subscribe(g, Ping.class, e -> {}) : bus.subscribe(g, Other.class, e -> {});
                        if (k % 2 == 0) s.unsubscribe(); else keep.add(s);
                    }
                });
            }
            for (Thread t : ts) t.start();
            for (Thread t : ts) t.join();
            stop.set(true);
            reader.join();
            assertTrue(fPing.on() && fSub.on() && fOther.on(), "남은 등록이 있으면 모두 켜져 있다 (round " + round + ")");
            for (var s : keep) s.unsubscribe();
            assertFalse(fPing.on() || fOther.on() || fSub.on(), "모두 떼면 꺼진다 (round " + round + ")");
            assertEquals(bus.hasSubscribers(Ping.class), fPing.on());
        }
    }

    /** 서버 구현의 입구: 구독 변화가 같은 순간 wants* 에 반영되고(상위 타입 구독 포함), 플러그인을 끄면 꺼진다. */
    @Test
    void pluginGameEventsFollowSubscriptionsImmediately() throws Exception {
        FurnacePlugin g = plugin();
        GameEvents ge = host.gameEvents();
        var bus = host.events();
        assertFalse(ge.wantsBlockPhysics());
        assertFalse(ge.wantsEntityDeath());
        var sub = bus.subscribe(g, BlockPhysicsEvent.class, e -> e.setCancelled(true));
        assertTrue(ge.wantsBlockPhysics());
        assertFalse(ge.wantsEntityDeath(), "다른 이벤트는 켜지지 않는다");
        sub.unsubscribe();
        assertFalse(ge.wantsBlockPhysics());
        var sup = bus.subscribe(g, BlockEvent.class, e -> {});   // 상위 타입 구독도 켠다
        assertTrue(ge.wantsBlockPhysics());
        var all = bus.subscribe(g, Event.class, e -> {});
        assertTrue(ge.wantsEntityDeath() && ge.has(BlockPhysicsEvent.class));
        host.pluginsImpl().disable(g);   // 플러그인을 끄면 처리기도 필드도 꺼진다
        assertFalse(ge.wantsBlockPhysics());
        assertFalse(ge.wantsEntityDeath());
        sup.unsubscribe();
        all.unsubscribe();
    }
}
