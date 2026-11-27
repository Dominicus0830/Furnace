package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** 탭 목록 이름: Bukkit·Paper API 와 NMS getTabListDisplayName 이 Paper 1.21.1 CraftPlayer 와 같은 값을 준다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlayerListNameTest {

    FurnaceTestServer server;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
    }

    @AfterAll
    void stop() { server.close(); }

    @Test
    void listNameRoundTrips() throws Exception {
        TestPlayer tp = server.join("Dana");
        try {
            Player p = Compat.player(tp.player());
            assertEquals("Dana", p.getPlayerListName(), "정하지 않았으면 이름");
            assertNull(((CraftPlayer) p).getHandle().getTabListDisplayName());
            p.setPlayerListName("§aDana§r!");
            assertEquals("§aDana§r!", p.getPlayerListName());
            assertEquals("Dana!", org.domi.furnace.api.text.serializer.JsonTextSerializer.json().deserialize(net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(p.playerListName())).plainText());
            assertEquals("Dana!", ((CraftPlayer) p).getHandle().getTabListDisplayName().getString());
            p.setPlayerListName("");
            assertNull(((CraftPlayer) p).getHandle().getTabListDisplayName(), "빈 문자열도 기본으로(Paper)");
            p.setPlayerListName("x");
            p.setPlayerListName("Dana");   // 이름과 같으면 기본으로 돌아간다(Paper)
            assertNull(((CraftPlayer) p).getHandle().getTabListDisplayName());
            p.playerListName(net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().deserialize("\"Boss\""));
            assertEquals("Boss", p.getPlayerListName());
            p.playerListName(null);
            assertEquals("Dana", p.getPlayerListName());
        } finally {
            tp.close();
        }
    }
}
