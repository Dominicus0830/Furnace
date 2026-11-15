package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.level.GameType;
import org.domi.furnace.api.entity.GameMode;
import org.domi.furnace.api.entity.ProfileProperty;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.clientbound.ClientboundPlayerInfoUpdatePacket;
import org.domi.furnace.api.packet.clientbound.ClientboundPlayerInfoUpdatePacket.Action;
import org.domi.furnace.api.text.Text;
import org.junit.jupiter.api.Test;

/**
 * T-181: 탭 목록 갱신 shim 이 바닐라 모양이고(ProtocolLib 이 반사로 찾는 자리) Furnace 패킷과 같은 값으로 오간다.
 * authlib 이 없는 서버에서는 까닭을 밝히고 되돌리지 않는다.
 */
class PlayerInfoShimTest {

    private static final Class<?> SHIM = net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.class;

    private static byte[] rsaKey() throws Exception {
        var g = java.security.KeyPairGenerator.getInstance("RSA");
        g.initialize(1024);
        return g.generateKeyPair().getPublic().getEncoded();
    }

    @Test
    void innerClassesAreWhereTheLibraryLooksForThem() {
        // ProtocolLib: PlayerInfoData = getClasses()[0], 동작 enum = getClasses()[1] (바닐라 jar 의 InnerClasses 순서)
        assertEquals(List.of(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry.class,
                net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.class), Arrays.asList(SHIM.getClasses()));
        assertEquals(RemoteChatSession.Data.class, RemoteChatSession.class.getClasses()[0]);
        assertEquals(ProfilePublicKey.Data.class, ProfilePublicKey.class.getClasses()[0]);
    }

    @Test
    void entryHasTheVanillaPublicConstructor() throws Exception {
        // ProtocolLib PlayerInfoData 변환기가 찾는 생성자(1.19.3+)
        Constructor<?> k = net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry.class.getConstructor(UUID.class,
                com.mojang.authlib.GameProfile.class, boolean.class, int.class, GameType.class, Component.class, RemoteChatSession.Data.class);
        assertTrue(Modifier.isPublic(k.getModifiers()));
    }

    @Test
    void theLibraryCanMakeAnEmptyPacketAndFillItLater() throws Exception {
        // ProtocolLib InstanceCreator: 매개변수 가장 적은 생성자를 기본값(null)으로 불러 예외가 안 나는 것을 쓴다
        Object made = null;
        int least = Integer.MAX_VALUE;
        for (Constructor<?> k : SHIM.getDeclaredConstructors()) {
            if (k.getParameterCount() > least) continue;
            k.setAccessible(true);
            try {
                Object o = k.newInstance(new Object[k.getParameterCount()]);
                if (k.getParameterCount() < least || made == null) made = o;
                least = k.getParameterCount();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // 다음 후보
            }
        }
        var p = assertInstanceOf(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.class, made);
        assertTrue(p.actions().isEmpty());
        assertTrue(p.entries().isEmpty());
    }

    @Test
    void joinPacketGoesToTheShimAndBackUnchanged() throws Exception {
        UUID id = UUID.randomUUID();
        var session = new ClientboundPlayerInfoUpdatePacket.ChatSession(UUID.randomUUID(), Instant.ofEpochMilli(1_800_000_000_000L), rsaKey(),
                new byte[] {9, 8, 7});
        var props = List.of(new ProfileProperty("textures", "dmFsdWU=", "c2ln"), new ProfileProperty("extra", "x", null));
        var api = new ClientboundPlayerInfoUpdatePacket(EnumSet.allOf(Action.class), List.of(
                new ClientboundPlayerInfoUpdatePacket.Entry(id, "Steve", props, GameMode.CREATIVE, true, 42, Text.text("tab"), session)));

        var shim = (net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket) PacketDelivery.fromFurnace(api);
        assertEquals(EnumSet.allOf(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.class), shim.actions());
        var e = shim.entries().getFirst();
        assertEquals(id, e.profileId());
        assertEquals("Steve", e.profile().getName());
        assertEquals(id, e.profile().getId());
        assertEquals(2, e.profile().getProperties().values().size());
        assertEquals(GameType.CREATIVE, e.gameMode());
        assertEquals(42, e.latency());
        assertNotNull(e.chatSession());
        assertArrayEquals(session.publicKey(), e.chatSession().profilePublicKey().key().getEncoded());
        assertEquals(shim.entries(), shim.newEntries());

        assertEquals(api, shim.toFurnace(), "같은 값으로 돌아온다");
    }

    @Test
    void updatesWithoutAddPlayerCarryAnEmptyNamedProfile() {
        UUID id = UUID.randomUUID();
        var api = new ClientboundPlayerInfoUpdatePacket(EnumSet.of(Action.UPDATE_LATENCY), List.of(
                new ClientboundPlayerInfoUpdatePacket.Entry(id, null, List.of(), GameMode.SURVIVAL, false, 7, null)));
        var shim = (net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket) PacketDelivery.fromFurnace(api);
        // 바닐라 클라이언트가 이 동작만 읽을 때와 같다: GameProfile(uuid, "")
        assertEquals("", shim.entries().getFirst().profile().getName());
        assertTrue(shim.newEntries().isEmpty());
        assertEquals(api, shim.toFurnace());
    }

    @Test
    void addPlayerWithoutAProfileSaysWhichEntry() {
        var shim = net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.of(
                EnumSet.of(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER),
                List.of(new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry(UUID.randomUUID(), null, true, 0,
                        GameType.SURVIVAL, null, null)));
        var ex = assertThrows(IllegalArgumentException.class, shim::toFurnace);
        assertTrue(ex.getMessage().contains("entries[0].profile(ADD_PLAYER)"), ex.getMessage());
    }

    @Test
    void withoutAuthlibTheBridgeSaysWhy() {
        assertNull(PacketDelivery.irreversibleReason(PacketTypes.Clientbound.PLAYER_INFO_UPDATE));
        Profiles.simulateMissingForTest(true);
        try {
            String why = PacketDelivery.irreversibleReason(PacketTypes.Clientbound.PLAYER_INFO_UPDATE);
            assertEquals(Profiles.MISSING, why);
            assertTrue(why.contains("plugins.compat.libraries.mojang"), why);
            assertTrue(!PacketDelivery.reversible(PacketTypes.Clientbound.PLAYER_INFO_UPDATE));
            var api = new ClientboundPlayerInfoUpdatePacket(EnumSet.of(Action.UPDATE_LISTED), List.of(
                    new ClientboundPlayerInfoUpdatePacket.Entry(UUID.randomUUID(), null, List.of(), GameMode.SURVIVAL, true, 0, null)));
            assertThrows(UnsupportedNmsCall.class, () -> PacketDelivery.fromFurnace(api));
            assertNull(Profiles.of(UUID.randomUUID(), "x", List.of()));
        } finally {
            Profiles.simulateMissingForTest(false);
        }
        assertTrue(Profiles.available());
    }
}
