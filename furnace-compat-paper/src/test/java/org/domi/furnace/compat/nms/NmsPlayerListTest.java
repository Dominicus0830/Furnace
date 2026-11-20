package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedPlayerList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * NMS shim 2번 묶음: 접속자 목록({@code MinecraftServer#getPlayerList}, Axiom·VeinMiner)과 {@code MinecraftServer#isSameThread}(Paper 1.21.1 은
 * {@code TickThread.isTickThread()} — 틱을 도는 스레드면 참, 결정 D2-1).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsPlayerListTest {

    FurnaceTestServer server;
    TestPlayer alice, bob;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        alice = server.join("Alice");
        bob = server.join("Bob");
        server.tick(3);
    }

    @AfterAll
    void stop() { server.close(); }

    private static ServerPlayer handle(TestPlayer tp) { return ((org.bukkit.craftbukkit.entity.CraftPlayer) Compat.player(tp.player())).getHandle(); }

    @Test
    void playerListIsTheDedicatedViewOfOnlinePlayers() {
        MinecraftServer ms = MinecraftServer.getServer();
        PlayerList list = ms.getPlayerList();
        assertTrue(list instanceof DedicatedPlayerList, "Paper 처럼 전용 서버 목록");
        assertSame(list, ((net.minecraft.server.dedicated.DedicatedServer) ms).getPlayerList());
        assertSame(ms, list.getServer());

        List<ServerPlayer> players = list.getPlayers();
        assertEquals(Set.of("Alice", "Bob"), players.stream().map(p -> p.getBukkitEntity().getName()).collect(Collectors.toSet()));
        assertTrue(players.contains(handle(alice)), "CraftPlayer#getHandle 과 같은 객체");
        assertThrows(UnsupportedOperationException.class, () -> players.clear(), "사본은 고칠 수 없다");
        assertEquals(2, list.getPlayerCount());
        assertEquals(Bukkit.getMaxPlayers(), list.getMaxPlayers());

        UUID id = alice.player().uniqueId();
        assertSame(handle(alice), list.getPlayer(id));
        assertNull(list.getPlayer(UUID.randomUUID()));
        assertSame(handle(bob), list.getPlayerByName("bob"), "이름은 대소문자를 가리지 않는다");
        assertNull(list.getPlayerByName("nobody"));
        assertSame(ms, handle(alice).getServer());
    }

    @Test
    void broadcastSystemMessageReachesEveryoneAsSystemChat() {
        PlayerList list = MinecraftServer.getServer().getPlayerList();
        list.broadcastSystemMessage(Component.literal("hello all"), false);
        list.broadcastSystemMessage(Component.literal("bar"), true);
        for (TestPlayer tp : List.of(alice, bob)) {
            var chat = tp.awaitPacket(PacketTypes.Clientbound.SYSTEM_CHAT, g -> "hello all".equals(g.message().plainText()));
            assertFalse(chat.actionBar());
            var bar = tp.awaitPacket(PacketTypes.Clientbound.SYSTEM_CHAT, g -> "bar".equals(g.message().plainText()));
            assertTrue(bar.actionBar(), "overlay 는 system_chat 의 액션바 자리(바닐라 sendSystemMessage(c, true))");
        }
        // 플레이어마다 다른 문장, null 이면 건너뛴다
        list.broadcastSystemMessage(Component.literal("console"), p -> p.getBukkitEntity().getName().equals("Alice") ? Component.literal("only alice") : null, false);
        alice.awaitPacket(PacketTypes.Clientbound.SYSTEM_CHAT, g -> "only alice".equals(g.message().plainText()));
        assertTrue(bob.received(PacketTypes.Clientbound.SYSTEM_CHAT).stream().noneMatch(g -> g.message().plainText().contains("alice")));
    }

    @Test
    void isSameThreadIsTrueOnTickThreadsLikePaper() throws Exception {
        MinecraftServer ms = MinecraftServer.getServer();
        assertFalse(ms.isSameThread(), "시험 스레드는 틱 스레드가 아니다");
        assertTrue(server.onGlobal(ms::isSameThread), "전역 스레드");
        var at = alice.player().location();
        var pos = new org.domi.furnace.api.world.BlockPos((int) Math.floor(at.x()), (int) Math.floor(at.y()), (int) Math.floor(at.z()));
        assertTrue(server.onRegion(pos, ms::isSameThread), "region 스레드도 참(Paper TickThread.isTickThread·Folia 와 같다, 결정 D2-1)");
        AtomicBoolean other = new AtomicBoolean(true);
        Thread t = new Thread(() -> other.set(ms.isSameThread()));
        t.start();
        t.join();
        assertFalse(other.get(), "틱 스레드가 아닌 스레드");
        try (var scope = org.domi.furnace.compat.thread.PrimaryThread.enter()) {
            assertTrue(ms.isSameThread(), "호환 계층이 Bukkit 주 스레드로 표시한 구간");
        }
    }
}
