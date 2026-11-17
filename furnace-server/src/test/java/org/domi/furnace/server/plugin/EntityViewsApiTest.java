// 보이기·숨기기·변장 플러그인 API: 규칙 저장, 검증, 조회, 플러그인이 꺼질 때 정리.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.api.entity.Disguise;
import org.domi.furnace.api.entity.ProfileProperty;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket.Value;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.view.Appearance;
import org.domi.furnace.server.entity.view.EntityViewRules;
import org.domi.furnace.server.net.protocol.RawPacket;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntityViewsApiTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private final TestPlugins.Gamma a = new TestPlugins.Gamma();
    private final TestPlugins.Gamma b = new TestPlugins.Gamma();

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
    }

    @AfterEach
    void tearDown() throws Exception {
        EntityViewRules.clearForTest();
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private Player online(String name) {
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), name, new EmbeddedChannel());
        core.world().playerOnline(p);
        return p;
    }

    @Test
    void hideShowAndDefaultVisibility_perPlugin() {
        var views = host.entityViews();
        var viewer = host.player(online("Viewer"));
        var target = host.player(online("Target"));
        assertTrue(viewer.canSee(target));
        viewer.hideEntity(a, target);
        assertFalse(viewer.canSee(target));
        assertFalse(views.canSee(viewer, target.uniqueId()));
        assertTrue(target.canSee(viewer), "한쪽만");
        viewer.hideEntity(b, target);
        viewer.showEntity(a, target);
        assertFalse(viewer.canSee(target), "다른 플러그인 숨김이 남았다");
        host.entityViewsImpl().removeAll(b);   // b 가 꺼짐
        assertTrue(viewer.canSee(target));

        viewer.hideEntity(a, viewer);   // 자기 자신은 무시
        assertTrue(viewer.canSee(viewer));

        views.setVisibleByDefault(a, target, false);
        assertFalse(views.isVisibleByDefault(target));
        assertFalse(viewer.canSee(target));
        viewer.showEntity(a, target);
        assertTrue(viewer.canSee(target), "기본 숨김이면 show 로 보여 준다");
        views.setVisibleByDefault(a, target, true);
        assertTrue(views.isVisibleByDefault(target));
    }

    /**
     * Bukkit 숨김·보임 이벤트(CraftPlayer untrackAndHideEntity/trackAndShowEntity): 그 관찰자에게 실제로 보임이 바뀔 때만 한 번.
     * 두 번째 플러그인의 숨김·남은 숨김이 있는 보이기는 조용하고, setVisibleByDefault 는 접속자마다 바뀐 사람에게만 낸다.
     */
    @Test
    void hideAndShowEventsFireOnlyWhenVisibilityChanges() {
        org.domi.furnace.server.TestPluginContexts.init(a, host, "views-a", tmp);
        a.setEnabled(true);
        List<String> events = new java.util.ArrayList<>();
        host.eventsImpl().subscribe(a, org.domi.furnace.api.event.player.PlayerHideEntityEvent.class,
                e -> events.add("hide " + e.player().name() + ">" + e.entity().uniqueId()));
        host.eventsImpl().subscribe(a, org.domi.furnace.api.event.player.PlayerShowEntityEvent.class,
                e -> events.add("show " + e.player().name() + ">" + e.entity().uniqueId()));
        var views = host.entityViews();
        var viewer = host.player(online("Viewer"));
        var other = host.player(online("Other"));
        var target = host.player(online("Target"));
        UUID t = target.uniqueId();

        views.hide(a, viewer, target);
        views.hide(b, viewer, target);   // 이미 숨었다
        views.show(a, viewer, target);   // b 의 숨김이 남았다
        assertEquals(List.of("hide Viewer>" + t), events);
        events.clear();
        views.show(b, viewer, target);
        views.show(b, viewer, target);   // 이미 보인다
        views.hide(a, viewer, viewer);   // 자기 자신은 무시
        assertEquals(List.of("show Viewer>" + t), events);
        events.clear();

        views.show(a, other, target);    // 이미 보인다
        assertEquals(List.of(), events);
        views.setVisibleByDefault(a, target, false);   // 접속자마다 resetAndHide — Target 자신은 빠진다
        assertEquals(List.of("hide Viewer>" + t, "hide Other>" + t), events);
        events.clear();
        views.show(a, other, target);    // 기본 숨김 대상을 Other 에게만 보인다
        assertEquals(List.of("show Other>" + t), events);
        events.clear();
        views.setVisibleByDefault(a, target, true);    // Other 는 이미 보였으니 조용하다
        assertEquals(List.of("show Viewer>" + t), events);
    }

    @Test
    void disguiseValidatesAndResolvesPerViewer() {
        var views = host.entityViews();
        var viewer = host.player(online("Viewer"));
        var other = host.player(online("Other"));
        var target = host.player(online("Target"));
        assertThrows(IllegalArgumentException.class, () -> views.disguise(a, target, Disguise.as("minecraft:no_such_mob")));
        assertThrows(IllegalArgumentException.class, () -> Disguise.as("minecraft:player"));
        assertThrows(IllegalArgumentException.class, () -> Disguise.player("", List.of()));
        assertThrows(IllegalStateException.class, () -> Disguise.as("pig").withNameTagVisible(false));

        Disguise pig = Disguise.as("pig").withCustomName(Text.text("꿀꿀")).withCustomNameVisible(true);
        assertEquals("minecraft:pig", pig.entityType());
        views.disguise(a, target, pig);
        assertEquals(pig, views.disguise(target.uniqueId()).orElseThrow());
        assertEquals(pig, views.disguiseSeenBy(target.uniqueId(), viewer).orElseThrow());

        Disguise steve = Disguise.player("Steve", List.of(ProfileProperty.textures("dGV4", null))).withNameTagVisible(false);
        views.disguise(a, target, viewer, steve);
        assertEquals(steve, views.disguiseSeenBy(target.uniqueId(), viewer).orElseThrow());
        assertEquals(pig, views.disguiseSeenBy(target.uniqueId(), other).orElseThrow());
        views.disguise(a, target, other, Disguise.none());
        assertTrue(views.disguiseSeenBy(target.uniqueId(), other).isEmpty(), "none = 그 사람에게는 진짜 모습");

        // 굳힌 모습: 덮는 칸·프로필 패킷이 버전 변환 계층으로 인코딩된다
        Appearance ap = EntityViewRules.appearanceFor(((PlayerHandle) viewer).handle(), target.uniqueId());
        assertNotNull(ap.profile());
        assertFalse(ap.profile().nameTagVisible());
        assertInstanceOf(RawPacket.class, ap.profile().infoAdd().apply(UUID.randomUUID()));
        Appearance gp = EntityViewRules.globalAppearance(target.uniqueId());
        assertTrue(gp.overrides(2) && gp.overrides(3) && !gp.overrides(0));
        assertInstanceOf(RawPacket.class, gp.metadata().apply(42));

        views.undisguise(target, viewer);
        assertEquals(pig, views.disguiseSeenBy(target.uniqueId(), viewer).orElseThrow(), "플레이어별을 지우면 전역으로");
        views.undisguise(target);
        assertTrue(views.disguise(target.uniqueId()).isEmpty());
    }

    @Test
    void pluginDisableRemovesItsRules_andLeavingViewerDropsTheirs() {
        var views = host.entityViews();
        Player vp = online("Viewer");
        var viewer = host.player(vp);
        var target = host.player(online("Target"));
        views.disguise(a, target, Disguise.as("zombie"));
        viewer.hideEntity(b, target);
        host.entityViewsImpl().removeAll(a);
        assertTrue(views.disguise(target.uniqueId()).isEmpty());
        assertFalse(viewer.canSee(target));
        vp.markDisconnected();
        assertEquals(0, EntityViewRules.targetCount(), "나간 사람의 숨김 규칙도 사라진다");
        assertFalse(EntityViewRules.active());
    }

    @Test
    void metadataOverrideRejectsUnsupportedValues() {
        assertThrows(IllegalArgumentException.class, () -> Disguise.as("pig").withMetadata(16, new org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket.Unsupported(7)));
        assertThrows(IllegalArgumentException.class, () -> Disguise.as("pig").withMetadata(255, Value.ofBoolean(true)));
        assertThrows(IllegalStateException.class, () -> Disguise.none().withSpawnData(1));
    }
}
