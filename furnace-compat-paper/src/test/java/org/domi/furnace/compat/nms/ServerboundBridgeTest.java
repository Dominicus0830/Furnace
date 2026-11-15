package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.entity.Hand;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.ServerboundPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundChatCommandPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundChatPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundClientCommandPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundClientInformationPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundInteractPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundMovePlayerPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundPlayerActionPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundPlayerCommandPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundSetCarriedItemPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundSignUpdatePacket;
import org.domi.furnace.api.packet.serverbound.ServerboundSwingPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundUseItemOnPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundUseItemPacket;
import org.domi.furnace.api.world.BlockFace;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ProtocolLib 다리 2b-3(T-180): 클라이언트가 보낸 패킷(serverbound) ↔ shim. 타입 레코드 12종과 범용 종류가 같은 값으로 돌아오고(왕복),
 * shim 값은 바닐라 모양이다(패킷 라이브러리가 필드로 읽는다). 바꿀 수 없는 종류는 까닭을 밝힌다.
 */
class ServerboundBridgeTest {

    @TempDir
    Path tmp;

    private FurnaceTestServer start() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("sb.jar"), "name: Sb\nversion: '1'\nmain: sb.P\napi-version: '1.21'\n",
                Map.of("sb.P", "package sb; public final class P extends org.bukkit.plugin.java.JavaPlugin {}"), Set.of());
        return FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
    }

    private static byte[] signature() {
        byte[] b = new byte[256];
        for (int i = 0; i < b.length; i++) b[i] = (byte) (i * 7);
        return b;
    }

    /** 표본: 타입 레코드 12종(이동은 넷 모두, 상호작용은 셋 모두). */
    static List<ServerboundPacket> typedSamples() {
        return List.of(
                new ServerboundChatCommandPacket("give @p diamond 3"),
                new ServerboundChatPacket("hello", Instant.ofEpochMilli(1_731_600_000_123L), 42L, null, 3, 0b1010_0000_0000_0000_0101),
                new ServerboundChatPacket("signed", Instant.ofEpochMilli(5L), -7L, signature(), 0, 0xFFFFF),
                new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN),
                new ServerboundClientInformationPacket("ko_kr", 12, ServerboundClientInformationPacket.ChatVisibility.SYSTEM, false, 0x7F,
                        ServerboundClientInformationPacket.MainArm.LEFT, true, false),
                ServerboundInteractPacket.attack(17, true),
                ServerboundInteractPacket.interact(18, Hand.OFF_HAND, false),
                new ServerboundInteractPacket(19, ServerboundInteractPacket.Action.INTERACT_AT, Hand.MAIN_HAND, 0.25f, 1.375f, -0.3f, true),
                ServerboundMovePlayerPacket.position(10.5, 64.0, -3.25, true),
                ServerboundMovePlayerPacket.positionRotation(1.0, 2.0, 3.0, 91.5f, -12.25f, false),
                ServerboundMovePlayerPacket.rotation(-179f, 89.9f, true),
                ServerboundMovePlayerPacket.statusOnly(false),
                new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, new BlockPos(5, -60, 7), BlockFace.WEST, 33),
                new ServerboundPlayerCommandPacket(901, ServerboundPlayerCommandPacket.Action.START_RIDING_JUMP, 77),
                new ServerboundSetCarriedItemPacket(8),
                new ServerboundSignUpdatePacket(new BlockPos(-1, 70, 2), false, List.of("a", "", "줄 셋", "four")),
                new ServerboundSwingPacket(Hand.OFF_HAND),
                // 칸 안 위치는 float 로 오간다 — 0.1f 같은 값도 바닐라처럼 double 로 더하고 float 로 빼면 같다
                new ServerboundUseItemOnPacket(Hand.MAIN_HAND, new BlockPos(1000, 64, -2000), BlockFace.UP, 0.1f, 1.0f, 0.9999f, false, 12),
                new ServerboundUseItemOnPacket(Hand.OFF_HAND, new BlockPos(-29_999_999, -64, 29_999_999), BlockFace.NORTH, 0.5f, 0.3f, 0f, true, 13),
                new ServerboundUseItemPacket(Hand.OFF_HAND, 14, 45.5f, -30f));
    }

    @Test
    void typedRecordsComeBackTheSameAndTheShimsLookVanilla() throws Exception {
        try (FurnaceTestServer server = start()) {
            Map<String, String> failures = new TreeMap<>();
            for (ServerboundPacket p : typedSamples()) {
                var shim = ServerboundBridge.fromFurnace(p);
                assertEquals(p.type(), ServerboundBridge.furnaceType(shim.getClass()), "shim 클래스의 종류: " + shim.getClass());
                ServerboundPacket back = ServerboundBridge.toFurnace(shim);
                if (!same(p, back)) failures.put(p.toString(), String.valueOf(back));
            }
            assertEquals(Map.of(), failures, "되돌린 값이 달라졌다");

            // 패킷 라이브러리가 읽는 바닐라 모양
            var move = ServerboundBridge.fromFurnace(ServerboundMovePlayerPacket.positionRotation(1, 2, 3, 4f, 5f, true));
            assertInstanceOf(net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot.class, move);
            assertInstanceOf(net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot.class,
                    ServerboundBridge.fromFurnace(ServerboundMovePlayerPacket.rotation(1f, 2f, false)));
            var useOn = (net.minecraft.network.protocol.game.ServerboundUseItemOnPacket) ServerboundBridge.fromFurnace(
                    new ServerboundUseItemOnPacket(Hand.MAIN_HAND, new BlockPos(3, 4, 5), BlockFace.EAST, 0.5f, 0.25f, 1f, false, 2));
            assertEquals(new net.minecraft.world.phys.Vec3(3.5, 4.25, 6.0).toString(), useOn.getHitResult().getLocation().toString(), "맞은 점 = 블록 좌표 + 칸 안 위치");
            assertEquals(net.minecraft.core.Direction.EAST, useOn.getHitResult().getDirection());
            assertEquals(net.minecraft.world.phys.HitResult.Type.BLOCK, useOn.getHitResult().getType());
            var chat = (net.minecraft.network.protocol.game.ServerboundChatPacket) ServerboundBridge.fromFurnace(
                    new ServerboundChatPacket("x", Instant.EPOCH, 1L, signature(), 2, 0b101));
            assertEquals(256, chat.signature().bytes().length);
            assertEquals(java.util.BitSet.valueOf(new long[] {0b101}), chat.lastSeenMessages().acknowledged());
            var info = (net.minecraft.network.protocol.common.ServerboundClientInformationPacket) ServerboundBridge.fromFurnace(typedSamples().get(4));
            assertEquals(net.minecraft.world.entity.HumanoidArm.LEFT, info.information().mainHand());
            assertEquals(net.minecraft.world.entity.player.ChatVisiblity.SYSTEM, info.information().chatVisibility());
        }
    }

    /** 바이트 배열(서명)이 든 레코드는 equals 가 배열 객체를 비교한다 — 내용으로 비교. */
    private static boolean same(ServerboundPacket a, ServerboundPacket b) {
        if (a instanceof ServerboundChatPacket x && b instanceof ServerboundChatPacket y) {
            return x.message().equals(y.message()) && x.timestamp().equals(y.timestamp()) && x.salt() == y.salt()
                    && java.util.Arrays.equals(x.signature(), y.signature()) && x.lastSeenOffset() == y.lastSeenOffset()
                    && x.lastSeenAcknowledged() == y.lastSeenAcknowledged();
        }
        return a.equals(b);
    }

    /** 범용 종류: 플러그인이 만들 법한 shim → Furnace → shim → Furnace 가 같고, 서버 코덱이 그 값을 쓸 수 있다(받은 것처럼 넣기). */
    @Test
    void genericKindsComeBackTheSameAndTheServerCanEncodeThem() throws Exception {
        try (FurnaceTestServer server = start()) {
            var tp = server.join("Gen");
            tp.sync();
            var item = net.minecraft.world.item.ItemStack.fromFurnace(new org.domi.furnace.api.item.ItemStack("minecraft:iron_helmet", 1), 1);
            byte[] sig = new byte[256];
            sig[0] = 7;
            var rsa = java.security.KeyPairGenerator.getInstance("RSA");
            rsa.initialize(1024);
            List<net.minecraft.network.protocol.Packet<?>> shims = List.of(
                    new net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket("me 서명", Instant.ofEpochMilli(1_700_000_000_000L), 42L,
                            new net.minecraft.commands.arguments.ArgumentSignatures(List.of(
                                    new net.minecraft.commands.arguments.ArgumentSignatures.Entry("action", new net.minecraft.network.chat.MessageSignature(sig)))),
                            new net.minecraft.network.chat.LastSeenMessages.Update(0, new java.util.BitSet())),
                    new net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket(new net.minecraft.network.chat.RemoteChatSession.Data(
                            java.util.UUID.randomUUID(), new net.minecraft.world.entity.player.ProfilePublicKey.Data(Instant.ofEpochMilli(1_800_000_000_000L),
                                    rsa.generateKeyPair().getPublic(), new byte[] {1, 2, 3}))),
                    new net.minecraft.network.protocol.game.ServerboundRenameItemPacket("새 이름"),
                    new net.minecraft.network.protocol.game.ServerboundPlayerInputPacket(0.98f, -0.5f, true, false),
                    new net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket(
                            net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket.Action.OPENED_TAB,
                            net.minecraft.resources.ResourceLocation.parse("minecraft:story/root")),
                    new net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket(
                            net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket.Action.CLOSED_SCREEN, null),
                    new net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket(new net.minecraft.core.BlockPos(1, 2, 3),
                            net.minecraft.world.level.block.entity.StructureBlockEntity.UpdateType.SAVE_AREA,
                            net.minecraft.world.level.block.state.properties.StructureMode.SAVE, "my:house", new net.minecraft.core.BlockPos(-1, 0, 2),
                            new net.minecraft.core.Vec3i(5, 6, 7), net.minecraft.world.level.block.Mirror.LEFT_RIGHT,
                            net.minecraft.world.level.block.Rotation.CLOCKWISE_90, "", true, false, true, 0.75f, 123456789L),
                    new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(36, item),
                    new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(3),
                    new net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket(5, "/give @p "),
                    new net.minecraft.network.protocol.common.ServerboundKeepAlivePacket(987654321L),
                    new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(4),
                    new net.minecraft.network.protocol.game.ServerboundPickItemPacket(7),
                    new net.minecraft.network.protocol.common.ServerboundResourcePackPacket(java.util.UUID.randomUUID(),
                            net.minecraft.network.protocol.common.ServerboundResourcePackPacket.Action.DECLINED));
            Map<String, String> failures = new TreeMap<>();
            Set<String> checked = new TreeSet<>();
            for (var shim : shims) {
                String name = shim.getClass().getSimpleName();
                try {
                    ServerboundPacket f = ServerboundBridge.toFurnace(shim);
                    ServerboundPacket back = ServerboundBridge.toFurnace(ServerboundBridge.fromFurnace(f));
                    if (!f.equals(back)) failures.put(name, f + " → " + back);
                    else checked.add(f.type().name());
                    // 서버 코덱이 쓸 수 있는 값인가(쓸 수 없으면 여기서 던진다)
                    // (번호가 맞아야 하는 확인 패킷은 서버 상태를 건드리므로 넣지 않는다)
                    if (!(shim instanceof net.minecraft.network.protocol.common.ServerboundKeepAlivePacket
                            || shim instanceof net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket)) {
                        Furnace.server().packets().receiveSilently(tp.player(), f);
                    }
                } catch (RuntimeException e) {
                    failures.put(name, e.toString());
                }
            }
            tp.sync();
            assertEquals(Map.of(), failures);
            assertTrue(tp.isConnected(), "넣은 패킷으로 연결이 끊기지 않았다: " + tp.disconnectReason());
            assertEquals(13, checked.size(), checked.toString());
        }
    }

    /** 바꾸지 못하는 받은 종류와 그 까닭(plan §4 표와 같다). */
    @Test
    void unsupportedKindsSayWhy() throws Exception {
        try (FurnaceTestServer server = start()) {
            var packets = Furnace.server().packets();
            Map<String, String> why = new TreeMap<>();
            for (PacketType<?> t : packets.supportedTypes(PacketDirection.SERVERBOUND)) {
                if (!t.typed() && !packets.isReadable(t)) continue;
                String r = ServerboundBridge.refusal(t);
                if (r != null) why.put(t.name().substring("minecraft:".length()), r);
            }
            assertEquals(Set.of("container_click", "custom_payload"), why.keySet(), "받은 패킷 중 shim 으로 못 오가는 종류: " + why);
            assertTrue(ServerboundBridge.refusal(PacketTypes.Serverbound.CUSTOM_PAYLOAD).contains("registerIncomingPluginChannel"));
            var cc = PacketTypes.byName(PacketDirection.SERVERBOUND, "minecraft:container_click");
            assertTrue(ServerboundBridge.refusal(cc).contains("Int2ObjectMap"), ServerboundBridge.refusal(cc));
            // play 연결 밖·clientbound shim 은 받을 수 없다고 밝힌다
            var e = assertThrows(UnsupportedNmsCall.class, () -> ServerboundBridge.toFurnace(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(1f, 2, 3f)));
            assertTrue(e.getMessage().contains("PacketService.receive"), e.getMessage());
            // 바닐라 모양이 아닌 값은 IllegalArgumentException(필수 값 null)
            assertThrows(IllegalArgumentException.class, () -> ServerboundBridge.toFurnace(new net.minecraft.network.protocol.game.ServerboundSwingPacket(null)));
            // 채팅 확인 비트가 20칸을 넘으면 바닐라도 쓰다 실패한다
            var big = new net.minecraft.network.protocol.game.ServerboundChatPacket("x", Instant.EPOCH, 0L, null,
                    new net.minecraft.network.chat.LastSeenMessages.Update(0, java.util.BitSet.valueOf(new long[] {1L << 20})));
            assertThrows(IllegalArgumentException.class, () -> ServerboundBridge.toFurnace(big));
        }
    }
}
