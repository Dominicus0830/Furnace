package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent.Cause;
import org.domi.furnace.api.event.player.PlayerKickEvent;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** Paper TransferCookieConnection 네 메서드(서버 패킷 수신기): 원인을 싣고 내보내기, 늘 PLAY, 전송 연결 아님. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsConnectionCookieTest {

    FurnaceTestServer server;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        org.bukkit.Bukkit.setServer(CraftServer.INSTANCE);
    }

    @AfterAll
    void stop() { server.close(); }

    @Test
    void kickCarriesTheCauseAndTheRestAnswers() throws Exception {
        TestPlayer tp = server.join("Kira");
        var causes = new CopyOnWriteArrayList<PlayerKickEvent.Cause>();
        server.api().events().subscribe(server.plugin(CompatTestPlugin.class), PlayerKickEvent.class, e -> causes.add(e.cause()));
        Player p = Compat.player(tp.player());
        var conn = ((CraftPlayer) p).getHandle().connection;
        assertEquals(ConnectionProtocol.PLAY, conn.getProtocol());
        assertFalse(conn.isTransferred());
        conn.kickPlayer(Component.literal("spam"), Cause.SPAM);
        for (int i = 0; i < 200 && causes.isEmpty(); i++) { server.tick(); Thread.sleep(10); }
        assertEquals(java.util.List.of(PlayerKickEvent.Cause.SPAM), causes);
    }
}
