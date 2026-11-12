// 스키마 생성 패킷 shim(N3)의 바닐라 동작 대조 — PR #237 독립 검토 R1~R7. 기대값은 바닐라 1.21.1 바이트코드로 확인한 것(검토 보고서 n3-pr237).
package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.ClientboundPacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ServerboundPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.ping.ClientPongPacketListener;
import net.minecraft.network.protocol.ping.ServerPingPacketListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.junit.jupiter.api.Test;

class PacketShimVanillaBehaviorTest {

    private static Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    /** 바닐라 Mth.floor — int 로 자른 뒤 1 을 뺀다. */
    private static int mthFloor(double d) { int i = (int) d; return d < i ? i - 1 : i; }

    private static int mthFloor(float f) { int i = (int) f; return f < i ? i - 1 : i; }

    private static ClientboundAddEntityPacket addEntity(float xRot, float yRot, double yHeadRot) {
        return new ClientboundAddEntityPacket(1, new UUID(0, 1), 0, 0, 0, xRot, yRot, null, 0, new Vec3(0, 0, 0), yHeadRot);
    }

    @Test
    void addEntityHeadRotationIsPackedInDouble() throws Exception {
        double head = 1.40625 - 1e-12;   // 1/256 바퀴 바로 아래 — float 로 깎으면 한 칸 올라간다
        assertEquals((byte) mthFloor(head * 256.0 / 360.0), field(addEntity(0f, 0f, head), "yHeadRot"));
        assertEquals((byte) 0, field(addEntity(0f, 0f, head), "yHeadRot"));
    }

    @Test
    void addEntityAnglesUseMthFloor() throws Exception {
        float big = -1e10f;   // Math.floor 는 int 범위를 넘으면 0 으로, Mth.floor 는 -1 로
        assertEquals((byte) mthFloor(big * 256.0F / 360.0F), field(addEntity(big, 0f, 0.0), "xRot"));
        for (float f : new float[] {-720f, -181.40625f, -1.40625f, -0.001f, 0f, 1.40625f, 90f, 179.9f, 359.99f, 1e6f}) {
            assertEquals((byte) mthFloor(f * 256.0F / 360.0F), field(addEntity(f, f, f), "xRot"), "xRot " + f);
            assertEquals((byte) mthFloor(f * 256.0F / 360.0F), field(addEntity(f, f, f), "yRot"), "yRot " + f);
            assertEquals((byte) mthFloor((double) f * 256.0 / 360.0), field(addEntity(f, f, f), "yHeadRot"), "yHeadRot " + f);
        }
    }

    @Test
    void listenerHierarchyMatchesVanilla() throws Exception {
        // 1.21.1: 핑 처리자는 PacketListener 만 잇는다
        assertFalse(ClientboundPacketListener.class.isAssignableFrom(ClientPongPacketListener.class));
        assertFalse(ServerboundPacketListener.class.isAssignableFrom(ServerPingPacketListener.class));
        assertTrue(PacketListener.class.isAssignableFrom(ClientPongPacketListener.class));
        // 서버 쪽 처리자는 game.ServerPacketListener(Serverbound 의 하위)를 거친다
        Class<?> sp = Class.forName("net.minecraft.network.protocol.game.ServerPacketListener");
        assertTrue(ServerboundPacketListener.class.isAssignableFrom(sp));
        for (String n : List.of("cookie.ServerCookiePacketListener", "handshake.ServerHandshakePacketListener", "common.ServerCommonPacketListener",
                "login.ServerLoginPacketListener", "status.ServerStatusPacketListener", "game.ServerGamePacketListener",
                "configuration.ServerConfigurationPacketListener")) {
            assertTrue(sp.isAssignableFrom(Class.forName("net.minecraft.network.protocol." + n)), n);
        }
        for (String n : List.of("common.ClientCommonPacketListener", "login.ClientLoginPacketListener", "status.ClientStatusPacketListener")) {
            assertTrue(List.of(Class.forName("net.minecraft.network.protocol." + n).getInterfaces()).contains(ClientboundPacketListener.class), n);
        }
    }

    @Test
    void reportCountsOnlyPacketsWithoutASendBridgeAsUnsupported() throws Exception {
        String src = """
                package rep;
                public class Use {
                    public static void go(net.minecraft.server.network.ServerCommonPacketListenerImpl c) {
                        c.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(1f, 20, 5f));
                        c.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(0.5f, 30, 2));
                        c.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(null));
                    }
                }
                """;
        Path dir = Files.createTempDirectory("n3-report");
        Path jar = JarCompiler.jar(dir.resolve("r.jar"), "name: R\nversion: '1'\nmain: rep.Use\n", Map.of("rep.Use", src), Set.of());
        NmsCompatReport r = NmsCompatReport.scan("R", jar, NmsSymbols.RUNTIME);
        // 보내기 다리가 없는 생성 패킷(손 코덱 종류 set_experience)의 생성자만 미지원 — 손으로 쓴 제목 패킷·범용 다리로 가는 체력 패킷은 있음
        assertEquals(1, r.unsupported(), r.toString());
        assertTrue(r.missing().stream().anyMatch(m -> m.contains("ClientboundSetExperiencePacket#<init>") && m.contains("보내기 미지원")), r.missing().toString());
        assertTrue(r.missing().stream().noneMatch(m -> m.contains("ClientboundSetTitleTextPacket") || m.contains("ClientboundSetHealthPacket")), r.missing().toString());
    }

    @Test
    void mapItemDataConvenienceConstructor() throws Exception {
        var c = ClientboundMapItemDataPacket.class.getConstructor(MapId.class, byte.class, boolean.class, Collection.class,
                MapItemSavedData.MapPatch.class);
        assertTrue(Modifier.isPublic(c.getModifiers()));
        var p = c.newInstance(null, (byte) 2, true, null, null);
        assertEquals(Optional.empty(), p.decorations());
        assertEquals(Optional.empty(), p.colorPatch());
        var q = c.newInstance(null, (byte) 2, true, List.of(), null);
        assertEquals(Optional.of(List.of()), q.decorations());
    }

    @Test
    void respawnShouldKeep() {
        var p = new ClientboundRespawnPacket(null, ClientboundRespawnPacket.KEEP_ATTRIBUTES);
        assertTrue(p.shouldKeep(ClientboundRespawnPacket.KEEP_ATTRIBUTES));
        assertFalse(p.shouldKeep(ClientboundRespawnPacket.KEEP_ENTITY_DATA));
    }

    @Test
    void helperTypeConstructorsFollowVanillaVisibility() throws Exception {
        assertThrows(NoSuchMethodException.class,
                () -> ClientboundLevelChunkPacketData.class.getConstructor(net.minecraft.nbt.CompoundTag.class, byte[].class, List.class));
        assertEquals(0, ClientboundLightUpdatePacketData.class.getConstructors().length);
        assertEquals(0, ClientboundSetPlayerTeamPacket.Parameters.class.getConstructors().length);
        // 바닐라에 public 이 있는 것은 그대로
        assertTrue(Modifier.isPublic(ClientboundGameEventPacket.Type.class.getDeclaredConstructor(int.class).getModifiers()));
    }

    @Test
    void containerSetContentCopiesLikeVanilla() throws Exception {
        try (var server = org.domi.furnace.test.FurnaceTestServer.builder().start()) {
            ItemStack stone = new ItemStack(Items.STONE, 3);
            ItemStack carried = new ItemStack(Items.DIAMOND_SWORD);
            NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
            items.set(0, stone);
            var p = new ClientboundContainerSetContentPacket(1, 2, items, carried);
            stone.setCount(9);
            carried.setCount(5);
            assertEquals(3, p.getItems().get(0).getCount(), "각 스택을 복사한다");
            assertEquals(1, p.getCarriedItem().getCount(), "들고 있는 스택도 복사한다");
            assertSame(ItemStack.EMPTY, p.getItems().get(1));
            p.getItems().set(1, new ItemStack(Items.STONE));   // 크기 고정이지만 값 교체는 된다
            assertThrows(UnsupportedOperationException.class, () -> p.getItems().add(ItemStack.EMPTY));
        }
    }

    @Test
    void explodeToBlowIsMutableCopy() {
        List<BlockPos> blow = new ArrayList<>(List.of(new BlockPos(1, 2, 3)));
        var p = new ClientboundExplodePacket(0, 0, 0, 1f, blow, null, null, null, null, null);
        blow.clear();
        assertEquals(1, p.getToBlow().size());
        p.getToBlow().add(new BlockPos(0, 0, 0));
        assertEquals(2, p.getToBlow().size());
    }
}
