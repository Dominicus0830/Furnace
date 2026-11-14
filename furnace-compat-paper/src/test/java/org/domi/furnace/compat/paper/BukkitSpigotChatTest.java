package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.clientbound.ClientboundSystemChatPacket;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.*;

/**
 * T-141 물결 1-E1: Player.spigot() 의 BungeeCord 컴포넌트 보내기(Paper 1.21.1 CraftPlayer.spigot — 시스템 채팅 패킷, ACTION_BAR 만 핫바 위),
 * 서버 쪽 보내는 이의 옛 § 글 전달(CraftBukkit ServerCommandSender), 엔티티는 아무 일 없음(CraftEntity), World#getBiomeProvider.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BukkitSpigotChatTest {

    FurnaceTestServer server;
    TestPlayer tp;
    Player player;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        tp = server.join("Juno");
        server.tick(3);
        player = Compat.player(tp.player());
    }

    @AfterAll
    void stop() { server.close(); }

    ClientboundSystemChatPacket await(String plain) {
        server.tick(1);
        return tp.awaitPacket(PacketTypes.Clientbound.SYSTEM_CHAT, p -> p.message().plainText().equals(plain));
    }

    @Test
    void componentGoesToChatWithStyle() {
        tp.clearReceived();
        TextComponent red = new TextComponent("빨강");
        red.setColor(ChatColor.RED);
        red.setBold(true);
        player.spigot().sendMessage(red);
        var pkt = await("빨강");
        assertFalse(pkt.actionBar(), "SYSTEM 은 채팅 창");
        assertEquals(NamedTextColor.RED, pkt.message().style().color());
        assertSame(player.spigot(), player.spigot(), "같은 Spigot 객체(Paper 는 필드 하나)");
    }

    @Test
    void severalComponentsAreOneMessage() {
        tp.clearReceived();
        player.spigot().sendMessage(new TextComponent("가"), new TextComponent("나"), new TextComponent("다"));
        assertNotNull(await("가나다"));
        assertEquals(1, tp.received(PacketTypes.Clientbound.SYSTEM_CHAT).stream().filter(p -> p.message().plainText().contains("가")).count(), "한 패킷");
    }

    @Test
    void actionBarOnlyForActionBarType() {
        tp.clearReceived();
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("핫바"));
        assertTrue(await("핫바").actionBar());
        player.spigot().sendMessage(ChatMessageType.CHAT, new TextComponent("채팅"));
        assertFalse(await("채팅").actionBar(), "CHAT 도 시스템 채팅(채팅 창)");
        player.spigot().sendMessage(UUID.randomUUID(), new TextComponent("보낸이"));
        assertFalse(await("보낸이").actionBar());
    }

    @Test
    void parsedLikePaper() {
        tp.clearReceived();
        BaseComponent[] parts = TextComponent.fromLegacyText("§a초록§r 보통");
        player.spigot().sendMessage(parts);
        var pkt = await("초록 보통");
        // Paper: ComponentSerializer JSON → 바닐라 JSON 해석. 같은 JSON 을 바닐라 해석기로 읽은 것과 같아야 한다
        var expected = net.minecraft.network.chat.Component.furnace(org.bukkit.craftbukkit.util.CraftChatMessage.fromJSON(
                net.md_5.bungee.chat.ComponentSerializer.toString(parts)));
        assertEquals(expected, pkt.message());
    }

    @Test
    void serverSendersGetLegacyText() {
        List<String> got = new ArrayList<>();
        InvocationHandler h = (proxy, m, args) -> {
            if (m.isDefault()) return InvocationHandler.invokeDefault(proxy, m, args);
            if (m.getName().equals("sendMessage") && args.length == 1 && args[0] instanceof String s) { got.add(s); return null; }
            if (m.getName().equals("getName")) return "fake";
            throw new UnsupportedOperationException(m.getName());
        };
        CommandSender fake = (CommandSender) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {CommandSender.class}, h);
        TextComponent c = new TextComponent("경고");
        c.setColor(ChatColor.RED);
        fake.spigot().sendMessage(c);
        fake.spigot().sendMessage(UUID.randomUUID(), new TextComponent("a"), new TextComponent("b"));
        assertEquals(List.of(TextComponent.toLegacyText(c), TextComponent.toLegacyText(new TextComponent("a"), new TextComponent("b"))), got);
        assertTrue(got.get(0).startsWith("§c"), got.get(0));
    }

    @Test
    void entitiesIgnoreComponents() {
        World world = player.getWorld();
        var loc = tp.player().location();
        BlockPos at = new BlockPos((int) Math.floor(loc.x()), -20, (int) Math.floor(loc.z()));
        Entity pig = server.onRegion(at, () -> world.spawnEntity(new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5), EntityType.PIG));
        try {
            assertDoesNotThrow(() -> pig.spigot().sendMessage(new TextComponent("x")));
            assertDoesNotThrow(() -> pig.spigot().sendMessage(UUID.randomUUID(), new TextComponent("x")));
        } finally {
            server.onRegion(at, () -> { pig.remove(); return null; });
        }
    }

    @Test
    void offlinePlayerSendsNothing() {
        TestPlayer gone = server.join("Gone");
        server.tick(3);
        Player p = Compat.player(gone.player());
        Player.Spigot s = p.spigot();
        gone.close();
        server.tick(5);
        assertFalse(p.isOnline());
        assertDoesNotThrow(() -> s.sendMessage(new TextComponent("늦음")));
    }

    /** Paper: getRawAddress 는 소켓 상대(포워딩이면 프록시), getAddress 는 포워딩된 플레이어 주소. */
    @Test
    void rawAddressIsSocketPeerUnderForwarding() {
        var handle = ((org.domi.furnace.server.plugin.PlayerHandle) tp.player()).handle();
        var raw = player.spigot().getRawAddress();
        assertNotNull(raw);
        assertEquals(raw, player.getAddress(), "포워딩이 없으면 같다");
        var forwarded = new java.net.InetSocketAddress("203.0.113.7", 40000);
        handle.remoteAddress(forwarded);
        try {
            assertEquals(forwarded, player.getAddress());
            assertEquals(raw, player.spigot().getRawAddress(), "raw 는 프록시(소켓) 쪽 그대로");
        } finally {
            handle.remoteAddress(null);
        }
    }

    /** Paper: 온라인 플레이어 중 못 보는 사람, 고칠 수 없는 집합. */
    @Test
    void hiddenPlayersFollowEntityViews() {
        TestPlayer other = server.join("Hidden");
        server.tick(3);
        var owner = new org.domi.furnace.api.plugin.FurnacePlugin() {};
        try {
            assertTrue(player.spigot().getHiddenPlayers().isEmpty());
            tp.player().hideEntity(owner, other.player());
            var hidden = player.spigot().getHiddenPlayers();
            assertEquals(java.util.Set.of(Compat.player(other.player())), hidden);
            assertThrows(UnsupportedOperationException.class, () -> hidden.clear());
            tp.player().showEntity(owner, other.player());
            assertTrue(player.spigot().getHiddenPlayers().isEmpty());
        } finally {
            other.close();
            server.tick(3);
        }
    }

    /** Paper: 살아 있거나 오프라인이면 respawn 은 아무 일도 하지 않는다. */
    @Test
    void respawnIsNoOpWhenAlive() {
        assertTrue(player.getHealth() > 0);
        assertDoesNotThrow(() -> player.spigot().respawn());
    }

    @Test
    void biomeProviderIsNullWithoutPluginProvider() {
        assertNull(player.getWorld().getBiomeProvider());
        org.bukkit.generator.WorldInfo info = player.getWorld();
        assertEquals(player.getWorld().getName(), info.getName());
        assertThrows(UnsupportedFeature.class, info::vanillaBiomeProvider);
    }
}
