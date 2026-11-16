package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.domi.furnace.api.packet.Packet;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.clientbound.ClientboundBlockUpdatePacket;
import org.domi.furnace.api.packet.clientbound.ClientboundClearTitlesPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetSubtitleTextPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetTitleTextPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetTitlesAnimationPacket;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.*;

/**
 * T-141 물결 1-C: Player 비행 허가·비행, sendTitle·resetTitle, sendBlockChange.
 * 기대값은 Paper 1.21.1 CraftPlayer 본문(setFlying 은 허가 없이 true 면 예외·바뀔 때만 능력 갱신, setAllowFlight(false) 는 비행도 끔,
 * sendTitle 은 시간 → 제목 → 부제목 순, null 은 건너뜀, 기본 시간 10/70/20, 첫 줄만)에서 옮겼다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BukkitPlayerFlightTitleTest {

    FurnaceTestServer server;
    TestPlayer tp;
    Player player;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        tp = server.join("Iris");
        server.tick(3);
        player = Compat.player(tp.player());
    }

    @AfterAll
    void stop() { server.close(); }

    @Test
    void flightFollowsPaperRules() {
        player.setGameMode(GameMode.SURVIVAL);
        assertFalse(player.getAllowFlight());
        assertFalse(player.isFlying());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> player.setFlying(true));
        assertEquals("Player is not allowed to fly (check #getAllowFlight())", e.getMessage());
        player.setFlying(false);   // 허가 없어도 false 는 된다

        player.setAllowFlight(true);
        assertTrue(player.getAllowFlight());
        assertFalse(player.isFlying(), "허가만으로는 날지 않는다");
        player.setFlying(true);
        assertTrue(player.isFlying());

        player.setAllowFlight(false);
        assertFalse(player.getAllowFlight());
        assertFalse(player.isFlying(), "허가를 끄면 비행도 끝난다");

        player.setGameMode(GameMode.CREATIVE);
        assertTrue(player.getAllowFlight(), "게임 모드가 허가를 다시 정한다");
        player.setGameMode(GameMode.SURVIVAL);
        assertFalse(player.getAllowFlight());
    }

    @Test
    void setFlyingSameValueSendsNoAbilities() {
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(true);
        player.setFlying(true);
        tp.flushReceived();
        tp.clearReceived();
        player.setFlying(true);        // 같은 값: Paper "Only refresh abilities if needed"
        player.setAllowFlight(true);   // 같은 값이어도 setAllowFlight 는 보낸다
        tp.flushReceived();
        assertEquals(1, tp.received(PacketTypes.Clientbound.PLAYER_ABILITIES).size());
        player.setAllowFlight(false);
    }

    @Test
    void blockChangeRejectsNulls() {
        IllegalArgumentException a = assertThrows(IllegalArgumentException.class, () -> player.sendBlockChange(null, Material.STONE.createBlockData()));
        assertEquals("Location cannot be null", a.getMessage());
        IllegalArgumentException b = assertThrows(IllegalArgumentException.class, () -> player.sendBlockChange(player.getLocation(), null));
        assertEquals("BlockData cannot be null", b.getMessage());
    }

    @Test
    void titleSendsTimesThenTextInOrder() {
        tp.clearReceived();
        player.sendTitle("§a제목\n둘째 줄", "부제목", 1, 2, 3);
        tp.awaitPacket(PacketTypes.Clientbound.SET_SUBTITLE_TEXT, p -> true);
        List<Packet> seq = tp.received().stream()
                .filter(p -> p instanceof ClientboundSetTitlesAnimationPacket || p instanceof ClientboundSetTitleTextPacket
                        || p instanceof ClientboundSetSubtitleTextPacket)
                .toList();
        assertEquals(3, seq.size(), seq.toString());
        assertEquals(new ClientboundSetTitlesAnimationPacket(1, 2, 3), seq.get(0));
        assertEquals("제목", ((ClientboundSetTitleTextPacket) seq.get(1)).text().plainText(), "첫 줄만");
        assertEquals("부제목", ((ClientboundSetSubtitleTextPacket) seq.get(2)).text().plainText());

        tp.clearReceived();
        player.sendTitle(null, "아래만");
        tp.awaitPacket(PacketTypes.Clientbound.SET_SUBTITLE_TEXT, p -> p.text().plainText().equals("아래만"));
        assertEquals(List.of(new ClientboundSetTitlesAnimationPacket(10, 70, 20)), tp.received(PacketTypes.Clientbound.SET_TITLES_ANIMATION));
        assertTrue(tp.received(PacketTypes.Clientbound.SET_TITLE_TEXT).isEmpty(), "null 제목은 보내지 않는다");

        tp.clearReceived();
        player.resetTitle();
        assertEquals(new ClientboundClearTitlesPacket(true), tp.awaitPacket(PacketTypes.Clientbound.CLEAR_TITLES, p -> true));
    }

    @Test
    void blockChangeIsClientOnly() {
        Location at = player.getLocation().clone().add(0, 5, 0);
        Material before = server.onRegion(new org.domi.furnace.api.world.BlockPos(at.getBlockX(), at.getBlockY(), at.getBlockZ()),
                () -> at.getBlock().getType());
        tp.clearReceived();
        player.sendBlockChange(at, Material.GOLD_BLOCK.createBlockData());
        ClientboundBlockUpdatePacket pkt = tp.awaitPacket(PacketTypes.Clientbound.BLOCK_UPDATE,
                p -> p.pos().x() == at.getBlockX() && p.pos().y() == at.getBlockY() && p.pos().z() == at.getBlockZ());
        assertEquals("minecraft:gold_block", pkt.block().type());
        Material after = server.onRegion(new org.domi.furnace.api.world.BlockPos(at.getBlockX(), at.getBlockY(), at.getBlockZ()),
                () -> at.getBlock().getType());
        assertEquals(before, after, "서버 세상은 그대로");
    }
}
