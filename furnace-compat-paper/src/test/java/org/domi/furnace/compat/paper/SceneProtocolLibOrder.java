// 호환 기준 장면 compat-protocollib-order — ProtocolLib 다리(#277)가 가로챈 패킷이 나가는 순서를 시나리오 5개로 기록한다(PacketTape 식 "받은 순서" 열).
// T-013(송신 경로 MPSC 큐 + 한 버퍼 인코딩)이 지켜야 하는 순서 계약(wave2-rebuttal-compat.md 2장 A~D)의 지금 모습을 굳힌다:
//   1 안 바꿈 · 2 바꿈(값 바꾸기·취소·다른 종류로 바꾸기) · 3 리스너 안에서 같은 플레이어에게 보내기(지금 패킷 앞) · 4 묶음(리스너 안 송신은 묶음 앞)
//   5 add_entity 를 취소하고 묶음으로 바꿔 보내기 + 뒤따르는 메타데이터(묶음 뒤에 와야 한다 — 계약 A).
// 진짜 ProtocolLib 은 쓰지 않는다 — 연결 채널의 이벤트 루프만 프록시로 갈아 끼워 다리가 가로채는 자리를 흉내 낸다(ProtocolLibBridgeTest 와 같은 방법).
// 줄은 시험 클라이언트가 받은 순서의 글자열이다(시계·엔티티 번호·바이트 해시를 쓰지 않는다): t<게임시각> c:<글> add:<종류> meta.
// 한 걸음(벤치) = 채팅 패킷을 리스너(프록시)를 거쳐 한 묶음 보내기.
package org.domi.furnace.compat.paper;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.clientbound.ClientboundAddEntityPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetTimePacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSystemChatPacket;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.server.identity.SceneHash;
import org.domi.furnace.server.scene.Probe;
import org.domi.furnace.server.scene.Size;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;

public final class SceneProtocolLibOrder extends CompatScene {

    @Override public String name() { return "compat-protocollib-order"; }

    @Override public String unit() { return "batch"; }

    @Override public String covers() { return "T-013 ProtocolLib 순서 계약 A~D(PacketTape 5장면), T-075"; }

    private static Field packetField(Object action) {
        for (Field f : action.getClass().getDeclaredFields()) {
            if (Packet.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                return f;
            }
        }
        throw new AssertionError("작업에 Packet 형식 필드가 없다: " + action.getClass());
    }

    /** 패킷 라이브러리처럼 channel 의 이벤트 루프만 프록시로 바꿔 끼운다. hook 은 작업 안의 패킷을 받아 바꿀 패킷(같은 것이면 그대로)이나 null(취소)을 돌려준다. */
    private static void install(Connection c, Function<Object, Object> hook) throws Exception {
        Channel real = c.channel;
        EventLoop realLoop = real.eventLoop();
        EventLoop loop = (EventLoop) Proxy.newProxyInstance(EventLoop.class.getClassLoader(), new Class<?>[] {EventLoop.class}, (p, m, a) -> {
            if (m.getName().equals("execute")) {
                Runnable r = (Runnable) a[0];
                Field f = packetField(r);
                Object packet = f.get(r);
                Object out = hook.apply(packet);
                if (out == null) return null;
                if (out != packet) f.set(r, out);
                realLoop.execute(r);
                return null;
            }
            return m.invoke(realLoop, a);
        });
        Channel proxy = (Channel) Proxy.newProxyInstance(Channel.class.getClassLoader(), new Class<?>[] {Channel.class}, (p, m, a) ->
                m.getName().equals("eventLoop") ? loop : m.invoke(real, a));
        Field ch = Connection.class.getDeclaredField("channel");
        ch.setAccessible(true);
        ch.set(c, proxy);
    }

    /** 받은 것 중 장면이 보는 종류만 순서대로. 시간 패킷은 장면 표지(4000 이상)만(서버가 스스로 보내는 시각 갱신은 뺀다). */
    private static String order(TestPlayer tp) {
        List<String> out = new ArrayList<>();
        for (var p : tp.received()) {
            if (p instanceof ClientboundSetTimePacket t && t.gameTime() >= 4000L) out.add("t" + t.gameTime());
            else if (p instanceof ClientboundSystemChatPacket m && !m.message().plainText().isBlank()) out.add("c:" + m.message().plainText());
            else if (p instanceof ClientboundAddEntityPacket a && a.entityType().equals("minecraft:pig")) out.add("add:pig");
            else if (p.getClass().getSimpleName().equals("ClientboundSetEntityDataPacket")) out.add("meta");
        }
        return String.join(" ", out);
    }

    @Override
    public void play(Size size, SceneHash.Recorder rec, Probe probe) throws Exception {
        Path tmp = Files.createTempDirectory("compat-scene-plb");
        Path jar = JarCompiler.jar(tmp.resolve("plb.jar"), "name: Plb\nversion: '1'\nmain: plb.P\napi-version: '1.21'\n",
                Map.of("plb.P", "package plb; public final class P extends org.bukkit.plugin.java.JavaPlugin {}"), Set.of());
        FurnaceTestServer s = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        try (s) {
            TestPlayer tp = s.join("Lib");
            attachAll(s);
            tp.sync();
            var player = tp.player();
            var owner = CompatPluginManager.adapterOrNull(Bukkit.getPluginManager().getPlugin("Plb"));
            Set<Class<?>> listened = Set.of(net.minecraft.network.protocol.game.ClientboundSetTimePacket.class,
                    net.minecraft.network.protocol.game.ClientboundSystemChatPacket.class,
                    net.minecraft.network.protocol.game.ClientboundAddEntityPacket.class);
            ProtocolLibBridge bridge = ProtocolLibBridge.start(owner, () -> (Collection<Class<?>>) (Collection<?>) listened);
            try {
                bridge.resync();
                var conn = ((CraftPlayer) Bukkit.getPlayer("Lib")).getHandle().connection.compatConnection();
                List<String> seen = new CopyOnWriteArrayList<>();
                boolean[] cancelAdd = {false};
                install(conn, packet -> {
                    if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetTimePacket t) {
                        seen.add("time" + t.getGameTime());
                        if (t.getGameTime() == 4001L) return new net.minecraft.network.protocol.game.ClientboundSetTimePacket(4777L, 8L, true);
                    }
                    if (packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket m) {
                        String text = net.minecraft.network.chat.Component.furnace(m.content()).plainText();
                        seen.add("chat:" + text);
                        if (text.equals("secret")) return null;
                        if (text.equals("nest") || text.equals("b2")) {   // 리스너 안의 sendServerPacket·sendMessage
                            ((CraftPlayer) Bukkit.getPlayer("Lib")).getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetTimePacket(4242L, 1L, true));
                            Bukkit.getPlayer("Lib").sendMessage("echo-" + text);
                        }
                        if (text.equals("swap")) return new net.minecraft.network.protocol.game.ClientboundSetTimePacket(4343L, 1L, true);
                    }
                    if (packet instanceof net.minecraft.network.protocol.game.ClientboundAddEntityPacket) {
                        seen.add("add");
                        if (cancelAdd[0]) {
                            // 취소하고 같은 플레이어에게 묶음으로 바꿔 보낸다(계약 A: 뒤따르는 메타데이터는 이 묶음 뒤)
                            Furnace.server().packets().sendBundle(player, List.of(new ClientboundSystemChatPacket(Text.text("x1"), false),
                                    new ClientboundSystemChatPacket(Text.text("x2"), false)));
                            return null;
                        }
                    }
                    return packet;
                });
                var packets = Furnace.server().packets();

                // 1) 안 바꿈
                tp.clearReceived();
                packets.send(player, new ClientboundSystemChatPacket(Text.text("a1"), false));
                packets.send(player, new ClientboundSetTimePacket(4001L + 1000L, 1L));
                packets.send(player, new ClientboundSystemChatPacket(Text.text("a2"), false));
                tp.awaitPacket(PacketTypes.Clientbound.SYSTEM_CHAT, m -> m.message().plainText().equals("a2"));
                rec.text("1-unchanged " + order(tp) + " | listener " + seen);
                seen.clear();

                // 2) 바꿈: 4001 → 4777, "secret" 취소, "swap" → t4343
                tp.clearReceived();
                packets.send(player, new ClientboundSetTimePacket(4001L, 1L));
                packets.send(player, new ClientboundSystemChatPacket(Text.text("secret"), false));
                packets.send(player, new ClientboundSystemChatPacket(Text.text("swap"), false));
                packets.send(player, new ClientboundSystemChatPacket(Text.text("after"), false));
                tp.awaitPacket(PacketTypes.Clientbound.SYSTEM_CHAT, m -> m.message().plainText().equals("after"));
                rec.text("2-changed " + order(tp) + " | listener " + seen);
                seen.clear();

                // 3) 리스너 안에서 같은 플레이어에게 보내기
                tp.clearReceived();
                player.sendMessage(Text.text("nest"));
                player.sendMessage(Text.text("tail"));
                tp.awaitPacket(PacketTypes.Clientbound.SYSTEM_CHAT, m -> m.message().plainText().equals("tail"));
                rec.text("3-send-inside " + order(tp) + " | listener " + seen);
                seen.clear();

                // 4) 묶음: b2 에서 보낸 것은 묶음 첫 패킷 b1 보다 앞
                tp.clearReceived();
                packets.sendBundle(player, List.of(new ClientboundSystemChatPacket(Text.text("b1"), false), new ClientboundSystemChatPacket(Text.text("b2"), false)));
                packets.send(player, new ClientboundSystemChatPacket(Text.text("b3"), false));
                tp.awaitPacket(PacketTypes.Clientbound.SYSTEM_CHAT, m -> m.message().plainText().equals("b3"));
                rec.text("4-bundle " + order(tp) + " | listener " + seen);
                seen.clear();

                // 5) add_entity 를 취소하고 묶음으로 바꿔 보내기 + 뒤따르는 메타데이터
                cancelAdd[0] = true;
                tp.clearReceived();
                var loc = player.location();
                s.execute("summon minecraft:pig " + loc.x() + " " + loc.y() + " " + (loc.z() + 3));
                s.tick(3);
                tp.awaitPacket(PacketTypes.Clientbound.SYSTEM_CHAT, m -> m.message().plainText().equals("x2"));
                tp.flushReceived();
                String five = order(tp);
                // 메타데이터가 이 묶음 앞에 나가면 클라이언트가 모르는 엔티티의 메타데이터를 버린다 — 순서만 적는다(메타 개수는 서버 버전에 달려 있어 "있다/없다"만)
                rec.text("5-cancel-then-bundle " + five.replaceAll("meta( meta)+", "meta") + " | listener-saw-add " + seen.contains("add"));
                seen.clear();
                cancelAdd[0] = false;

                // 벤치: 리스너 한 개가 채팅을 지나는 묶음
                int rounds = size.pick(1, 30), per = size.pick(100, 5_000);
                for (int r = 0; r < rounds; r++) {
                    probe.before(r);
                    for (int i = 0; i < per; i++) packets.send(player, new ClientboundSystemChatPacket(Text.text("bench"), false));
                    s.tick(1);
                    probe.after(r);
                }
            } finally {
                bridge.stop();
            }
        }
    }
}
