// 플레이어별 화면 덮어쓰기 플러그인 API: 가상 블록·바이옴 층, 개인 보더, 장비 덮어쓰기 — 검증, 보내는 패킷, 소유권, 플러그인이 꺼질 때 정리.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.domi.furnace.api.entity.EquipmentSlot;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerViewsApiTest {

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
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        core.registries(org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt"));
        host = core.plugins();
    }

    @AfterEach
    void tearDown() throws Exception {
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private Player online(String name) {
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), name, new org.domi.furnace.server.InlineEmbeddedChannel());
        core.world().playerOnline(p);
        return p;
    }

    private record Sent(int id, ByteBuf body) {}

    private static List<Sent> drain(Player p) {
        EmbeddedChannel ch = (EmbeddedChannel) p.channel();
        ch.runPendingTasks();
        List<Sent> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf b = (ByteBuf) o;
            out.add(new Sent(Types.readVarInt(b), b));
        }
        return out;
    }

    private static final int BLOCK_UPDATE = 0x09, SECTION_UPDATE = 0x49, INIT_BORDER = 0x25, SET_EQUIPMENT = 0x5B;

    private org.domi.furnace.api.world.World overworld() { return host.world("minecraft:overworld"); }

    @Test
    void virtualBlockIsStoredPerViewerAndSentOnce() {
        Player vp = online("Viewer"), other = online("Other");
        var viewer = host.player(vp);
        var views = host.playerViews();
        BlockPos pos = new BlockPos(3, 70, -5);
        drain(vp);
        views.setBlock(a, viewer, overworld(), pos, BlockData.parse("minecraft:oak_stairs[facing=north]"));
        assertEquals("minecraft:oak_stairs", views.virtualBlock(viewer, overworld(), pos).orElseThrow().type());
        assertEquals("north", views.virtualBlock(viewer, overworld(), pos).orElseThrow().properties().get("facing"));
        assertTrue(views.virtualBlock(host.player(other), overworld(), pos).isEmpty(), "다른 사람에게는 없다");
        var sent = drain(vp);
        assertEquals(1, sent.size());
        assertEquals(BLOCK_UPDATE, sent.get(0).id);
        assertTrue(drain(other).isEmpty());

        assertThrows(IllegalArgumentException.class, () -> views.setBlock(a, viewer, overworld(), pos, BlockData.of("minecraft:no_such_block")));
        assertThrows(IllegalArgumentException.class, () -> views.setBlock(a, viewer, overworld(), pos, BlockData.parse("minecraft:stone[facing=north]")));
        assertTrue(views.virtualBlock(viewer, overworld(), pos).isPresent(), "잘못된 요청이 기존 층을 지우지 않는다");
    }

    @Test
    void manyBlocksAreSentAsSectionUpdates_andFillIsCapped() {
        Player vp = online("Viewer");
        var viewer = host.player(vp);
        var views = host.playerViews();
        drain(vp);
        views.fillBlocks(a, viewer, overworld(), new BlockPos(0, 64, 0), new BlockPos(15, 64, 15), BlockData.of("minecraft:glass"));
        var sent = drain(vp);
        assertEquals(1, sent.size(), "한 구역이면 패킷 하나");
        assertEquals(SECTION_UPDATE, sent.get(0).id);
        assertTrue(views.virtualBlock(viewer, overworld(), new BlockPos(7, 64, 9)).isPresent());
        assertTrue(views.virtualBlock(viewer, overworld(), new BlockPos(7, 65, 9)).isEmpty());

        // 두 구역에 걸치면 둘
        views.setBlocks(a, viewer, overworld(), Map.of(new BlockPos(1, 64, 1), BlockData.of("minecraft:stone"), new BlockPos(17, 64, 1), BlockData.of("minecraft:stone")));
        assertEquals(2, drain(vp).size());

        assertThrows(IllegalArgumentException.class, () -> views.fillBlocks(a, viewer, overworld(), new BlockPos(0, 0, 0), new BlockPos(100, 100, 100), BlockData.of("minecraft:stone")));
        views.clearBlocks(a, viewer, overworld());
        assertTrue(views.virtualBlock(viewer, overworld(), new BlockPos(7, 64, 9)).isEmpty());
        assertFalse(vp.viewLayers().blocks.active());
    }

    @Test
    void ownershipAndPluginDisable() {
        Player vp = online("Viewer");
        var viewer = host.player(vp);
        var views = host.playerViews();
        BlockPos p1 = new BlockPos(1, 64, 1), p2 = new BlockPos(2, 64, 2);
        views.setBlock(a, viewer, overworld(), p1, BlockData.of("minecraft:stone"));
        views.setBlock(a, viewer, overworld(), p2, BlockData.of("minecraft:stone"));
        views.setBlock(b, viewer, overworld(), p1, BlockData.of("minecraft:glass"));   // p1 은 b 가 가져간다
        views.setBiome(a, viewer, overworld(), p2, "minecraft:desert");
        assertEquals("minecraft:desert", views.virtualBiome(viewer, overworld(), p2).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> views.setBiome(a, viewer, overworld(), p2, "minecraft:no_such_biome"));

        views.clearBlock(a, viewer, overworld(), p1);   // a 것이 아니라 변화 없음
        assertEquals("minecraft:glass", views.virtualBlock(viewer, overworld(), p1).orElseThrow().type());

        host.playerViewsImpl().removeAll(a);   // a 가 꺼짐
        assertEquals("minecraft:glass", views.virtualBlock(viewer, overworld(), p1).orElseThrow().type());
        assertTrue(views.virtualBlock(viewer, overworld(), p2).isEmpty());
        assertTrue(views.virtualBiome(viewer, overworld(), p2).isEmpty());
        host.playerViewsImpl().removeAll(b);
        assertFalse(vp.viewLayers().active());
    }

    @Test
    void personalBorderIsPushedAndReset() {
        Player vp = online("Viewer");
        var viewer = host.player(vp);
        var views = host.playerViews();
        drain(vp);
        assertFalse(views.hasBorder(viewer));
        var border = views.border(a, viewer);
        assertTrue(views.hasBorder(viewer));
        assertEquals(1, drain(vp).stream().filter(s -> s.id == INIT_BORDER).count());
        assertEquals(core.border().getSize(), border.size(), "서버 보더의 지금 값으로 시작");

        border.center(10, 20);
        border.size(40);
        var sent = drain(vp);
        assertEquals(2, sent.size());
        ByteBuf last = sent.get(1).body;
        assertEquals(10.0, last.readDouble());   // 중심 x
        assertEquals(20.0, last.readDouble());   // 중심 z
        last.readDouble();                       // 이전 크기
        assertEquals(40.0, last.readDouble());   // 새 크기
        assertTrue(border.contains(29, 20));
        assertFalse(border.contains(30, 20), "끝 선은 밖");

        border.lerpSize(100, 10_000);
        assertEquals(100, border.lerpTarget());
        assertEquals(40, border.size(), 1.0);
        drain(vp);

        assertFalse(border.enforced());
        border.enforce(true);
        assertTrue(border.enforced());
        assertSame(vp.viewLayers().border(), vp.viewLayers().enforcedBorder());
        assertSame(vp.viewLayers().border(), ((org.domi.furnace.api.view.PlayerBorder) views.border(b, viewer)).enforced() ? vp.viewLayers().border() : null);

        views.resetBorder(viewer);
        assertFalse(views.hasBorder(viewer));
        assertEquals(1, drain(vp).stream().filter(s -> s.id == INIT_BORDER).count(), "서버 보더를 다시 보낸다");
        border.size(5);   // 걷힌 뒤의 변경은 더 보내지 않는다
        assertTrue(drain(vp).isEmpty());
        assertNull(vp.viewLayers().enforcedBorder());
    }

    @Test
    void borderOwnedByPluginIsResetWhenItIsDisabled() {
        Player vp = online("Viewer");
        var viewer = host.player(vp);
        host.playerViews().border(a, viewer).size(30);
        host.playerViewsImpl().removeAll(b);
        assertTrue(host.playerViews().hasBorder(viewer), "다른 플러그인이 꺼져도 남는다");
        host.playerViewsImpl().removeAll(a);
        assertFalse(host.playerViews().hasBorder(viewer));
    }

    @Test
    void equipmentOverrideIsSentAndRemembered() {
        Player vp = online("Viewer"), tp = online("Target");
        var viewer = host.player(vp);
        var target = host.player(tp);
        var views = host.playerViews();
        drain(vp);
        ItemStack helmet = ItemStack.of("minecraft:diamond_helmet");
        views.setEquipment(a, viewer, target, EquipmentSlot.HEAD, helmet);
        var sent = drain(vp);
        assertEquals(1, sent.size());
        assertEquals(SET_EQUIPMENT, sent.get(0).id);
        assertEquals(tp.entityId(), Types.readVarInt(sent.get(0).body));
        assertEquals(helmet, views.virtualEquipment(viewer, target, EquipmentSlot.HEAD).orElseThrow());
        assertTrue(views.virtualEquipment(viewer, target, EquipmentSlot.FEET).isEmpty());
        assertTrue(views.virtualEquipment(host.player(tp), host.player(vp), EquipmentSlot.HEAD).isEmpty(), "viewer 마다 따로");

        assertThrows(IllegalArgumentException.class, () -> views.setEquipment(a, viewer, target, EquipmentSlot.HEAD, ItemStack.of("minecraft:no_such_item")));
        views.setEquipment(b, viewer, target, EquipmentSlot.HEAD, ItemStack.EMPTY);   // 빈 칸으로 보이게(b 가 가져감)
        assertTrue(views.virtualEquipment(viewer, target, EquipmentSlot.HEAD).orElseThrow().isEmpty());

        views.clearEquipment(a, viewer, target, EquipmentSlot.HEAD);   // b 것이라 그대로
        assertTrue(views.virtualEquipment(viewer, target, EquipmentSlot.HEAD).isPresent());
        drain(vp);
        host.playerViewsImpl().removeAll(b);
        assertTrue(views.virtualEquipment(viewer, target, EquipmentSlot.HEAD).isEmpty());
        assertFalse(vp.viewLayers().equipment.active());
    }
}
