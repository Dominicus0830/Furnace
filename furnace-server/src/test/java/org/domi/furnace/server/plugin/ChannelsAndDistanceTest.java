// 플러그인 채널 등록(minecraft:register 송신)·채팅 지우기(delete_chat)·시야/시뮬레이션 거리 변경(set_chunk_cache_radius·set_simulation_distance).
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.PluginChannels;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.CustomPayloadPacket;
import org.domi.furnace.server.net.protocol.play.DeleteChat;
import org.domi.furnace.server.net.protocol.play.SetChunkCacheRadius;
import org.domi.furnace.server.net.protocol.play.SetSimulationDistance;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChannelsAndDistanceTest {

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
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private record Sent(int id, ByteBuf body) {}

    private final java.util.Map<Player, EmbeddedChannel> channels = new java.util.HashMap<>();

    private Player online(String name) {
        EmbeddedChannel ch = new EmbeddedChannel();
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), name, ch);
        channels.put(p, ch);
        core.world().playerOnline(p);
        return p;
    }

    private List<Sent> sent(Player p) {
        EmbeddedChannel ch = channels.get(p);
        ch.runPendingTasks();
        List<Sent> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf bb = (ByteBuf) o;
            out.add(new Sent(Types.readVarInt(bb), bb));
        }
        return out;
    }

    /** custom_payload 들을 "채널=본문" 문자열로. */
    private List<String> payloads(Player p) {
        List<String> out = new ArrayList<>();
        for (Sent s : sent(p)) {
            if (s.id() != CustomPayloadPacket.ID_PLAY) continue;
            String ch = Types.readString(s.body(), 32767);
            byte[] rest = new byte[s.body().readableBytes()];
            s.body().readBytes(rest);
            out.add(ch + "=" + new String(rest, StandardCharsets.UTF_8).replace('\0', '|'));
        }
        return out;
    }

    @Test
    void registeredChannelsAreAnnouncedAndWithdrawn() {
        Player early = online("Early");
        host.registerPluginChannel(a, "x:one");
        host.registerPluginChannel(b, "x:one");   // 같은 채널을 둘이 — 다시 알리지 않는다
        host.registerPluginChannel(a, "x:two");
        assertEquals(List.of("minecraft:register=x:one", "minecraft:register=x:two"), payloads(early));
        assertEquals(java.util.Set.of("x:one", "x:two"), host.pluginChannels());

        Player late = online("Late");
        host.sendRegisteredChannels(late);
        assertEquals(List.of("minecraft:register=x:one|x:two"), payloads(late), "접속 직후 한 번에, 이름순");

        host.unregisterPluginChannel(a, "x:one");   // b 가 아직 쓴다
        assertEquals(List.of(), payloads(early));
        host.removeChannels(a);                     // a 가 꺼진다 → x:two 만 내려간다
        assertEquals(List.of("minecraft:unregister=x:two"), payloads(early));
        assertEquals(java.util.Set.of("x:one"), host.pluginChannels());

        assertThrows(IllegalArgumentException.class, () -> host.registerPluginChannel(a, "minecraft:register"));
        assertThrows(IllegalArgumentException.class, () -> host.registerPluginChannel(a, "Bad:Name"));
        assertThrows(IllegalArgumentException.class, () -> host.registerPluginChannel(a, "nonamespace"));
    }

    @Test
    void noAnnouncementWhenNothingRegistered() {
        Player p = online("P");
        host.sendRegisteredChannels(p);
        assertEquals(List.of(), sent(p));
    }

    @Test
    void deleteChatSendsFullSignatureWhenNotCached() {
        Player p = online("P");
        byte[] sig = new byte[256];
        for (int i = 0; i < sig.length; i++) sig[i] = (byte) i;
        host.player(p).deleteChatMessage(sig);
        List<Sent> out = sent(p);
        assertEquals(1, out.size());
        assertEquals(DeleteChat.ID, out.get(0).id());
        ByteBuf body = out.get(0).body();
        assertEquals(0, Types.readVarInt(body), "캐시에 없으면 id 0 + 서명 256바이트");
        byte[] got = new byte[body.readableBytes()];
        body.readBytes(got);
        assertArrayEquals(sig, got);
        assertThrows(IllegalArgumentException.class, () -> host.player(p).deleteChatMessage(new byte[3]));
    }

    @Test
    void viewDistanceChangeResizesPlayersAndSendsChunkCacheRadius() {
        Player p = online("P");
        p.clientViewDistance(6);
        host.setViewDistance(4);
        assertEquals(4, host.viewDistance());
        assertEquals(4, core.world().viewDistance());
        assertEquals(4, p.viewDistance(), "min(서버 4, 클라이언트 6)");
        List<Sent> out = sent(p);
        assertEquals(1, out.size());
        assertEquals(SetChunkCacheRadius.ID, out.get(0).id());
        assertEquals(4, Types.readVarInt(out.get(0).body()));

        host.setViewDistance(99);   // 2~32 로 자른다
        assertEquals(32, core.world().viewDistance());
        assertEquals(6, p.viewDistance(), "클라이언트 렌더 거리가 더 작다");
        assertEquals(32, Types.readVarInt(sent(p).get(0).body()));
    }

    @Test
    void simulationDistanceChangeSendsPacketAndFeedsLevelServices() {
        Player p = online("P");
        host.setSimulationDistance(5);
        assertEquals(5, host.simulationDistance());
        assertEquals(5, core.world().levelServices().simulationDistance(), "region 이 다음 틱부터 읽는 값");
        List<Sent> out = sent(p);
        assertEquals(1, out.size());
        assertEquals(SetSimulationDistance.ID, out.get(0).id());
        assertEquals(5, Types.readVarInt(out.get(0).body()));
        host.setSimulationDistance(1);
        assertEquals(2, host.simulationDistance());
    }
}
