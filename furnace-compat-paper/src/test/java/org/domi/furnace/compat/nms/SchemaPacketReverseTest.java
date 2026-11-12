package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.Packet;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.clientbound.ClientboundAddEntityPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundAnimatePacket;
import org.domi.furnace.api.packet.clientbound.ClientboundBlockUpdatePacket;
import org.domi.furnace.api.packet.clientbound.ClientboundClearTitlesPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundDisconnectPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundPlayerInfoRemovePacket;
import org.domi.furnace.api.packet.clientbound.ClientboundRemoveEntitiesPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundRotateHeadPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetActionBarTextPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityMotionPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetSubtitleTextPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetTimePacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetTitleTextPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetTitlesAnimationPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSoundPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSystemChatPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundTeleportEntityPacket;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.SoundCategory;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ProtocolLib 다리 2b-2: Furnace 패킷 → shim(fromFurnace) → Furnace 패킷(toFurnace)이 같은 값으로 돌아온다.
 * 표본은 시험 클라이언트가 실제로 받은 패킷(접속 때 서버가 보내는 것 + 플러그인이 보낸 shim·Furnace 패킷) — 서버 코덱이 읽은 값 그대로다.
 */
class SchemaPacketReverseTest {

    @TempDir
    Path tmp;

    private FurnaceTestServer start() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("rev.jar"), "name: Rev\nversion: '1'\nmain: rev.P\napi-version: '1.21'\n",
                Map.of("rev.P", "package rev; public final class P extends org.bukkit.plugin.java.JavaPlugin {}"), Set.of());
        return FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
    }

    @AfterEach
    void resetPair() { SchemaPacketReverse.pairOfOverride = null; }

    /** 시험 경로에 DataFixerUpper 가 없다 — 같은 접근자의 Pair 와 그 of. */
    public static Object pair(Object a, Object b) { return new SchemaPacketBridgeTest.Pair<>(a, b); }

    private static <T> T construct(Class<T> c, Object... args) {
        for (var k : c.getDeclaredConstructors()) {
            if (k.getParameterCount() != args.length) continue;
            try {
                k.setAccessible(true);
                return c.cast(k.newInstance(args));
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                // 다음 생성자
            }
        }
        throw new AssertionError("생성자 없음: " + c.getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static net.minecraft.core.Holder holder(net.minecraft.resources.ResourceKey registry, String name) {
        return new net.minecraft.core.Holder.Reference(net.minecraft.resources.ResourceKey.create(registry, net.minecraft.resources.ResourceLocation.parse(name)), null);
    }

    /** 패킷 하나를 돌려 보고 다르면 까닭을 남긴다. */
    private static void roundTrip(Packet v, Map<String, String> failures, Set<String> checked) {
        PacketType<?> t = v.type();
        if (!PacketDelivery.reversible(t)) return;
        if (v instanceof org.domi.furnace.api.packet.GenericPacket g && !g.readable()) return;
        try {
            ClientboundPacket back = PacketDelivery.convert(PacketDelivery.fromFurnace(v));
            if (!back.equals(v)) failures.putIfAbsent(t.name(), "다름: " + v + " → " + back);
            else checked.add(t.name());
        } catch (RuntimeException e) {
            failures.putIfAbsent(t.name(), e.toString());
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void everythingTheClientReceivesComesBackTheSame() throws Exception {
        SchemaPacketReverse.pairOfOverride = SchemaPacketReverseTest.class.getMethod("pair", Object.class, Object.class);
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = server.join("Rev");
            server.tick(5);
            Player p = Bukkit.getPlayer("Rev");
            var conn = ((org.bukkit.craftbukkit.entity.CraftPlayer) p).getHandle().connection;
            tp.sync();
            var at = tp.player().location();
            var region = new BlockPos((int) Math.floor(at.x()), (int) Math.floor(at.y()), (int) Math.floor(at.z()));
            var rl = (java.util.function.Function<String, net.minecraft.resources.ResourceLocation>) net.minecraft.resources.ResourceLocation::parse;
            var item = net.minecraft.world.item.ItemStack.fromFurnace(new org.domi.furnace.api.item.ItemStack("minecraft:iron_helmet", 1), 1);
            var stone = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(rl.apply("minecraft:stone")).defaultBlockState();
            var gold = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(rl.apply("minecraft:gold_block")).defaultBlockState();
            var neutral = net.minecraft.sounds.SoundSource.NEUTRAL;
            var handle = ((org.bukkit.craftbukkit.entity.CraftPlayer) p).getHandle();
            server.onRegion(region, () -> {
                // 스키마 shim(필드 나열·손 코덱) — SchemaPacketBridgeTest 와 같은 표본
                conn.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(7.5f, 13, 2.25f));
                conn.send(new net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket(new net.minecraft.core.BlockPos(1, 64, -3), false));
                conn.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(net.minecraft.network.protocol.game.ClientboundGameEventPacket.START_RAINING, 0f));
                conn.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(200, 6, 37, item));
                conn.send(new net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket(777, new net.minecraft.core.BlockPos(5, 6, 7), -1));
                conn.send(new net.minecraft.network.protocol.game.ClientboundBlockEventPacket(new net.minecraft.core.BlockPos(8, 9, 10),
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(rl.apply("minecraft:chest")), 1, 300));
                conn.send(new net.minecraft.network.protocol.game.ClientboundTabListPacket(net.minecraft.network.chat.Component.literal("head"),
                        net.minecraft.network.chat.Component.literal("foot")));
                conn.send(new net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket(new net.minecraft.world.level.ChunkPos(100, -7)));
                conn.send(new net.minecraft.network.protocol.game.ClientboundSoundEntityPacket(net.minecraft.sounds.SoundEvents.AMBIENT_CAVE, neutral, handle, 1f, 1f, 1L));
                conn.send(new net.minecraft.network.protocol.game.ClientboundSoundEntityPacket(net.minecraft.core.Holder.direct(
                        net.minecraft.sounds.SoundEvent.createFixedRangeEvent(rl.apply("myplugin:far"), 64f)), neutral, handle, 1f, 1f, 5L));
                conn.send(new net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket(p.getEntityId(), net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN));
                conn.send(new net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket(rl.apply("plug:token")));
                conn.send(new net.minecraft.network.protocol.ping.ClientboundPongResponsePacket(4242L));
                conn.send(construct(net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket.class, true, false, true, false, 0.07f, 0.1f));
                conn.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(0.5f, 30, 2));
                conn.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(rl.apply("minecraft:entity.pig.ambient"), null));
                conn.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(null, net.minecraft.sounds.SoundSource.WEATHER));
                conn.send(new net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, 1.5, 2.5, 3.5));
                conn.send(construct(net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket.class, 4.0, 5.0, 6.0, 4321,
                        net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.FEET, net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, true));
                conn.send(new net.minecraft.network.protocol.game.ClientboundMoveEntityPacket.Pos(901, (short) 40, (short) -8, (short) 0, true));
                conn.send(new net.minecraft.network.protocol.game.ClientboundMoveEntityPacket.PosRot(902, (short) 1, (short) 2, (short) 3, (byte) 64, (byte) -32, false));
                conn.send(new net.minecraft.network.protocol.game.ClientboundMoveEntityPacket.Rot(903, (byte) 10, (byte) 20, true));
                conn.send(new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(904, List.of(
                        pair(net.minecraft.world.entity.EquipmentSlot.HEAD, item), pair(net.minecraft.world.entity.EquipmentSlot.MAINHAND, item))));
                conn.send(construct(net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket.class, net.minecraft.core.SectionPos.of(3, 4, -5),
                        new short[] {0x123, 0x000}, new net.minecraft.world.level.block.state.BlockState[] {stone, gold}));
                conn.send(new net.minecraft.network.protocol.game.ClientboundDamageEventPacket(905, holder(net.minecraft.core.registries.Registries.DAMAGE_TYPE, "minecraft:fall"),
                        -1, 7, java.util.Optional.of(new net.minecraft.world.phys.Vec3(1, 2, 3))));
                conn.send(new net.minecraft.network.protocol.game.ClientboundDamageEventPacket(906, holder(net.minecraft.core.registries.Registries.DAMAGE_TYPE, "minecraft:magic"),
                        0, 0, java.util.Optional.empty()));
                conn.send(new net.minecraft.network.protocol.game.ClientboundSetScorePacket("Rev", "o1", 12, java.util.Optional.empty(),
                        java.util.Optional.of(new net.minecraft.network.chat.numbers.FixedFormat(net.minecraft.network.chat.Component.literal("MAX")))));
                conn.send(new net.minecraft.network.protocol.game.ClientboundSetScorePacket("Rev", "o2", 3, java.util.Optional.of(net.minecraft.network.chat.Component.literal("n")),
                        java.util.Optional.of(net.minecraft.network.chat.numbers.BlankFormat.INSTANCE)));
                return null;
            });
            // 타입 레코드 종류는 Furnace 패킷 API 로
            var api = tp.player();
            api.sendPacket(new ClientboundSetTitleTextPacket(Text.text("T")));
            api.sendPacket(new ClientboundSetSubtitleTextPacket(Text.text("S")));
            api.sendPacket(new ClientboundSetActionBarTextPacket(Text.text("A")));
            api.sendPacket(new ClientboundClearTitlesPacket(true));
            api.sendPacket(new ClientboundSetTitlesAnimationPacket(1, 2, 3));
            api.sendPacket(new ClientboundSystemChatPacket(Text.text("hi"), false));
            api.sendPacket(new ClientboundRemoveEntitiesPacket(List.of(5, 6, 7)));
            api.sendPacket(new ClientboundSetEntityMotionPacket(8, 0.1, -3.95, 2.04775));   // 2.04775*8000 = 16382(정수로 바꾸면 1 어긋나는 값)
            api.sendPacket(new ClientboundSetTimePacket(1234L, -6000L));
            api.sendPacket(new ClientboundPlayerInfoRemovePacket(List.of(java.util.UUID.randomUUID())));
            api.sendPacket(new ClientboundAddEntityPacket(9, java.util.UUID.randomUUID(), "minecraft:zombie", 1.5, 64, -2.25, -45f, 190f, 359f, 0, 0.5, -0.25, 3.9));
            api.sendPacket(new ClientboundRotateHeadPacket(9, -91f));
            api.sendPacket(new ClientboundAnimatePacket(9, ClientboundAnimatePacket.Animation.CRITICAL_HIT));
            api.sendPacket(new ClientboundTeleportEntityPacket(9, 3.25, 70, -8, 720f, -30f, true));
            api.sendPacket(new ClientboundBlockUpdatePacket(new BlockPos(1, 2, 3), new BlockData("minecraft:oak_stairs", Map.of("facing", "east", "half", "top"))));
            api.sendPacket(new ClientboundSoundPacket("minecraft:entity.pig.ambient", null, SoundCategory.NEUTRAL, 1.125, 2, -3.5, 0.5f, 1.5f, 77L));
            api.sendPacket(new ClientboundSoundPacket("myplugin:boom", 32f, SoundCategory.BLOCKS, 0, 0, 0, 1f, 1f, 78L));
            api.sendPacket(new ClientboundSoundPacket("myplugin:quiet", null, SoundCategory.AMBIENT, 0, 0, 0, 1f, 1f, 79L));
            tp.awaitPacket(PacketTypes.Clientbound.SOUND, g -> g.seed() == 79L);
            tp.awaitPacket(PacketTypes.Clientbound.SET_SCORE, g -> "o2".equals(g.get("objective_name")));
            server.tick(2);
            tp.flushReceived();

            Map<String, String> failures = new TreeMap<>();
            Set<String> checked = new TreeSet<>();
            for (Packet v : tp.received()) roundTrip(v, failures, checked);
            // 연결을 끊는 패킷은 받아 볼 수 없다 — 값만
            roundTrip(new ClientboundDisconnectPacket(Text.text("bye")), failures, checked);
            // 바닐라 Animate 의 1(맞음 — 1.21.1 에서는 hurt_animation)은 Furnace 레코드에 없다 — 지어내지 않고 까닭을 밝힌다
            var hurt = construct(net.minecraft.network.protocol.game.ClientboundAnimatePacket.class, 3, 1);
            var refused = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> PacketDelivery.convert(hurt));
            assertTrue(refused.getMessage().contains("애니메이션"), refused.getMessage());
            assertEquals(Map.of(), failures, "되돌린 값이 달라진 종류");
            // 접속 때 서버가 보내는 것(청크·엔티티·시간 등)과 위 표본을 모두 돌렸다
            for (String must : List.of("minecraft:set_health", "minecraft:game_event", "minecraft:container_set_slot", "minecraft:sound_entity", "minecraft:player_abilities",
                    "minecraft:stop_sound", "minecraft:player_look_at", "minecraft:move_entity_pos_rot", "minecraft:set_equipment", "minecraft:section_blocks_update",
                    "minecraft:damage_event", "minecraft:set_score", "minecraft:cookie_request", "minecraft:pong_response", "minecraft:set_title_text",
                    "minecraft:set_entity_motion", "minecraft:add_entity", "minecraft:teleport_entity", "minecraft:block_update", "minecraft:sound",
                    "minecraft:disconnect", "minecraft:set_time", "minecraft:set_entity_motion", "minecraft:player_position")) {
                assertTrue(checked.contains(must), must + " 를 돌려 보지 못했다: " + checked);
            }
        }
    }

    /** 2b-2 에서 되돌리는 종류·못 하는 종류 표(문서 compat-protocollib-pipeline-2b-plan §3-2 표와 같다). */
    @Test
    void reversibleKindsTable() throws Exception {
        try (FurnaceTestServer server = start()) {
            var packets = Furnace.server().packets();
            Set<String> yes = new TreeSet<>(), no = new TreeSet<>();
            for (PacketType<?> t : packets.supportedTypes(PacketDirection.CLIENTBOUND)) {
                if (SchemaPacketReverse.def(t).isEmpty()) continue;   // play 연결 밖
                if (!t.typed() && !packets.isReadable(t)) continue;
                (PacketDelivery.reversible(t) ? yes : no).add(t.name().substring("minecraft:".length()));
            }
            assertEquals(new TreeSet<>(List.of(
                    // 타입 레코드: 메타데이터·플레이어 정보·플러그인 메시지(2b-4)
                    "custom_payload", "player_info_update", "set_entity_data",
                    // 값 shim 이 빈 껍데기(toFurnace 도 없다)
                    "block_entity_data", "delete_chat", "disguised_chat", "login", "open_screen", "respawn", "server_links",
                    // 손 코덱: toFurnace 도 없다(보스바·통계), 역변환이 아직 없다(팀·목표·속성·지도)
                    "award_stats", "boss_event", "map_item_data", "set_objective", "set_player_team", "update_attributes")), no, "되돌리는 것 " + yes.size() + "종: " + yes);
        }
    }
}
