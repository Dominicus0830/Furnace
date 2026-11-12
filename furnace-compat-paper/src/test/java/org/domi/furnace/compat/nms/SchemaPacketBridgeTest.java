package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.schema.PacketDef;
import org.domi.furnace.api.packet.schema.PacketSchema;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 스키마 생성 패킷 shim 을 connection.send 로 보내면 범용 패킷으로 바뀌어 나간다(easy-nms E4c-1). */
class SchemaPacketBridgeTest {

    @TempDir
    Path tmp;

    private FurnaceTestServer start() throws Exception {
        // 호환 계층은 버킷 플러그인이 있을 때만 켜진다 — 빈 플러그인 하나
        Path jar = JarCompiler.jar(tmp.resolve("e4c.jar"), "name: E4c\nversion: '1'\nmain: e4c.P\napi-version: '1.21'\n",
                Map.of("e4c.P", "package e4c; public final class P extends org.bukkit.plugin.java.JavaPlugin {}"), Set.of());
        return FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
    }

    @Test
    void everyFieldListKindTheServerCanWriteHasABridgeExceptEmptyValueShims() throws Exception {
        try (FurnaceTestServer server = start()) {
            var packets = Furnace.server().packets();
            Set<String> bridged = new TreeSet<>(), missing = new TreeSet<>();
            for (PacketDef d : PacketSchema.protocol767().packets()) {
                if (d.direction() != PacketDirection.CLIENTBOUND || !(d.state().equals("game") || d.state().equals("common"))) continue;
                PacketType<?> t = PacketTypes.byName(PacketDirection.CLIENTBOUND, "minecraft:" + d.wireName());
                if (t == null || t.typed() || d.custom() || !packets.isReadable(t)) continue;
                (SchemaPacketBridge.supports(d) ? bridged : missing).add(d.wireName());
            }
            // 다리 없는 종류는 값 shim 이 아직 빈 껍데기다(BlockEntityType·MessageSignature.Packed·ChatType.Bound·GlobalPos·MenuType·ServerLinks.UntrustedEntry·NumberFormat)
            assertEquals(Set.of("block_entity_data", "delete_chat", "disguised_chat", "login", "open_screen", "respawn", "server_links", "set_score"), missing,
                    "다리 " + bridged.size() + "종: " + bridged);
            assertEquals(65, bridged.size(), bridged.toString());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static net.minecraft.core.Holder holder(net.minecraft.resources.ResourceKey registry, String name) {
        return new net.minecraft.core.Holder.Reference(net.minecraft.resources.ResourceKey.create(registry, net.minecraft.resources.ResourceLocation.parse(name)), null);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void connectionSendTurnsSchemaShimsIntoGenericPackets() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = server.join("Shim");
            server.tick(2);
            Player p = Bukkit.getPlayer("Shim");
            assertNotNull(p);
            var conn = ((org.bukkit.craftbukkit.entity.CraftPlayer) p).getHandle().connection;
            tp.sync();
            long failuresBefore = tp.decodeFailures();   // 접속 때 player_info_update 의 채팅 세션 초기화는 시험 클라이언트가 읽지 않는다(이 다리와 무관)
            var at = tp.player().location();
            var region = new org.domi.furnace.api.world.BlockPos((int) Math.floor(at.x()), (int) Math.floor(at.y()), (int) Math.floor(at.z()));
            var item = net.minecraft.world.item.ItemStack.fromFurnace(new org.domi.furnace.api.item.ItemStack("minecraft:diamond", 3), 3);
            server.onRegion(region, () -> {
                conn.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(7.5f, 13, 2.25f));
                conn.send(new net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket(new net.minecraft.core.BlockPos(1, 64, -3), false));
                conn.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(net.minecraft.network.protocol.game.ClientboundGameEventPacket.START_RAINING, 0f));
                conn.send(new net.minecraft.network.protocol.game.ClientboundSoundEntityPacket(holder(net.minecraft.core.registries.Registries.SOUND_EVENT, "minecraft:entity.pig.ambient"),
                        net.minecraft.sounds.SoundSource.NEUTRAL, ((org.bukkit.craftbukkit.entity.CraftPlayer) p).getHandle(), 0.5f, 1.5f, 99L));
                conn.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(0, 5, 36, item));
                conn.send(new net.minecraft.network.protocol.game.ClientboundTabListPacket(net.minecraft.network.chat.Component.literal("head"),
                        net.minecraft.network.chat.Component.literal("foot")));
                conn.send(new net.minecraft.network.protocol.game.ClientboundBlockEventPacket(new net.minecraft.core.BlockPos(2, 3, 4),
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("minecraft:note_block")), 1, 2));
                // 바닐라처럼 명시 와이어 크기로 잘린다: 진행도 -1(u8 → 255 = 지우기), 블록 이벤트 300(u8 → 44), 컨테이너 200(i8 → -56)
                conn.send(new net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket(777, new net.minecraft.core.BlockPos(5, 6, 7), -1));
                conn.send(new net.minecraft.network.protocol.game.ClientboundBlockEventPacket(new net.minecraft.core.BlockPos(8, 9, 10),
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("minecraft:chest")), 1, 300));
                conn.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(200, 6, 37, item));
                // 묶음은 번들 하나로
                conn.send(new net.minecraft.network.protocol.game.ClientboundBundlePacket(List.of(
                        new net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket(new net.minecraft.world.level.ChunkPos(100, -7)),
                        new net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket(12, -8))));
                return null;
            });
            var health = tp.awaitPacket(PacketTypes.Clientbound.SET_HEALTH, g -> Integer.valueOf(13).equals(g.get("food")));
            assertEquals(7.5f, health.get("health"));
            assertEquals(2.25f, health.get("saturation"));
            var sign = tp.awaitPacket(PacketTypes.Clientbound.OPEN_SIGN_EDITOR, g -> true);
            assertEquals(new org.domi.furnace.api.world.BlockPos(1, 64, -3), sign.get("pos"));
            assertEquals(false, sign.get("is_front_text"));
            var rain = tp.awaitPacket(PacketTypes.Clientbound.GAME_EVENT, g -> Integer.valueOf(1).equals(g.get("event")));
            assertEquals(0f, rain.get("param"));
            var sound = tp.awaitPacket(PacketTypes.Clientbound.SOUND_ENTITY, g -> Long.valueOf(99L).equals(g.get("seed")));
            assertEquals("minecraft:entity.pig.ambient", sound.get("sound"));
            assertEquals("NEUTRAL", sound.get("source"));
            assertEquals(p.getEntityId(), sound.get("id"));
            var slot = tp.awaitPacket(PacketTypes.Clientbound.CONTAINER_SET_SLOT, g -> Integer.valueOf(36).equals(g.get("slot")));
            var got = (org.domi.furnace.api.item.ItemStack) slot.get("item_stack");
            assertEquals("minecraft:diamond", got.type());
            assertEquals(3, got.amount());
            var tab = tp.awaitPacket(PacketTypes.Clientbound.TAB_LIST, g -> true);
            assertEquals("head", ((org.domi.furnace.api.text.Text) tab.get("header")).plainText());
            assertEquals("foot", ((org.domi.furnace.api.text.Text) tab.get("footer")).plainText());
            var block = tp.awaitPacket(PacketTypes.Clientbound.BLOCK_EVENT, g -> "minecraft:note_block".equals(g.get("block")));
            assertEquals("minecraft:note_block", block.get("block"));
            assertEquals(List.of(1, 2), List.of(block.get("b0"), block.get("b1")));
            assertEquals(255, tp.awaitPacket(PacketTypes.Clientbound.BLOCK_DESTRUCTION, g -> Integer.valueOf(777).equals(g.get("id"))).get("progress"));
            assertEquals(44, tp.awaitPacket(PacketTypes.Clientbound.BLOCK_EVENT, g -> "minecraft:chest".equals(g.get("block"))).get("b1"));
            assertEquals(-56, tp.awaitPacket(PacketTypes.Clientbound.CONTAINER_SET_SLOT, g -> Integer.valueOf(37).equals(g.get("slot"))).get("container_id"));
            var forget = tp.awaitPacket(PacketTypes.Clientbound.FORGET_LEVEL_CHUNK, g -> Long.valueOf(net.minecraft.world.level.ChunkPos.asLong(100, -7)).equals(g.get("pos")));
            assertNotNull(forget);
            var border = tp.awaitPacket(PacketTypes.Clientbound.SET_CHUNK_CACHE_CENTER, g -> Integer.valueOf(-8).equals(g.get("z")));
            assertEquals(12, border.get("x"));
            assertEquals(failuresBefore, tp.decodeFailures(), "보낸 것은 모두 바닐라 와이어로 읽혔다");
            // 번들은 다른 스레드 패킷이 끼지 않고 붙어 온다(구분자는 시험 클라이언트가 모으지 않는다)
            int bundle = tp.received().indexOf(forget);
            assertTrue(bundle >= 0 && tp.received().get(bundle + 1).equals(border), "묶음 안 둘이 붙어 온다: " + tp.received());

            // 다리가 없는 종류(손 코덱)는 지어내지 않고 UnsupportedNmsCall, 필수 값이 null 이면 IllegalArgumentException — 묶음은 아무것도 안 나간다
            tp.clearReceived();
            server.onRegion(region, () -> {
                var refused = assertThrows(UnsupportedNmsCall.class,
                        () -> conn.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(0.5f, 30, 2)));
                assertTrue(refused.getMessage().contains("손 코덱"), refused.getMessage());
                var bad = assertThrows(IllegalArgumentException.class, () -> conn.send(new net.minecraft.network.protocol.game.ClientboundBundlePacket(List.of(
                        new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(1f, 77, 0f),
                        new net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket(null, true)))));
                assertTrue(bad.getMessage().contains("pos"), bad.getMessage());
                // 바꾸기는 되지만 인코딩에서 실패하는 것(레지스트리에 없는 효과)이 묶음 뒤에 있어도 앞의 것은 나가지 않는다
                assertThrows(IllegalArgumentException.class, () -> conn.send(new net.minecraft.network.protocol.game.ClientboundBundlePacket(List.of(
                        new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(1f, 79, 0f),
                        new net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket(1, holder(net.minecraft.core.registries.Registries.MOB_EFFECT, "minecraft:nope"))))));
                conn.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(1f, 78, 0f));
                return null;
            });
            tp.awaitPacket(PacketTypes.Clientbound.SET_HEALTH, g -> Integer.valueOf(78).equals(g.get("food")));
            assertTrue(tp.received(PacketTypes.Clientbound.SET_HEALTH).stream().noneMatch(g -> Integer.valueOf(77).equals(g.get("food")) || Integer.valueOf(79).equals(g.get("food"))),
                    "실패한 묶음의 앞 패킷도 나가지 않는다");
        }
    }
}
