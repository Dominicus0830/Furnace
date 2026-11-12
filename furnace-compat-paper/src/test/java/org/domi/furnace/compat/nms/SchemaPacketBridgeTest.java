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
                if (d.direction() != PacketDirection.CLIENTBOUND || !Set.of("game", "common", "cookie", "ping").contains(d.state())) continue;
                PacketType<?> t = PacketTypes.byName(PacketDirection.CLIENTBOUND, "minecraft:" + d.wireName());
                if (t == null || t.typed() || d.custom() || !packets.isReadable(t)) continue;
                (SchemaPacketBridge.supports(d) ? bridged : missing).add(d.wireName());
            }
            // 다리 없는 종류는 값 shim 이 아직 빈 껍데기다(BlockEntityType·MessageSignature.Packed·ChatType.Bound·GlobalPos·MenuType·ServerLinks.UntrustedEntry·NumberFormat)
            assertEquals(Set.of("block_entity_data", "delete_chat", "disguised_chat", "login", "open_screen", "respawn", "server_links", "set_score"), missing,
                    "다리 " + bridged.size() + "종: " + bridged);
            assertEquals(67, bridged.size(), bridged.toString());   // game·common 65 + cookie_request·pong_response(play 연결로도 나가는 cookie·ping 단계)
            assertTrue(bridged.containsAll(Set.of("cookie_request", "pong_response")), bridged.toString());
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
    @Test
    void refusalsSayWhy() {
        var schema = PacketSchema.protocol767();
        assertTrue(SchemaPacketBridge.refusal(schema.packet("game", PacketDirection.SERVERBOUND, "swing").orElseThrow()).contains("serverbound"));
        assertTrue(SchemaPacketBridge.refusal(schema.packet("configuration", PacketDirection.CLIENTBOUND, "finish_configuration").orElseThrow())
                .contains("configuration 단계"));
        assertTrue(SchemaPacketBridge.refusal(schema.packet("game", PacketDirection.CLIENTBOUND, "add_entity").orElseThrow()).contains("타입 레코드"));
        assertTrue(SchemaPacketBridge.refusal(schema.packet("game", PacketDirection.CLIENTBOUND, "set_experience").orElseThrow()).contains("손 코덱"));
        assertTrue(SchemaPacketBridge.refusal(schema.packet("game", PacketDirection.CLIENTBOUND, "open_screen").orElseThrow()).contains("빈 껍데기"));
        assertEquals(null, SchemaPacketBridge.refusal(schema.packet("game", PacketDirection.CLIENTBOUND, "set_health").orElseThrow()));
    }

    @Test
    void soundAndEffectConstantsAreTheRegistryValues() throws Exception {
        try (FurnaceTestServer server = start()) {
            var sounds = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT;
            var ids = Furnace.server().gameRegistries().builtIn("minecraft:sound_event");
            assertEquals(1611, sounds.size());
            assertEquals(ids.size(), sounds.size());
            // 상수 필드 1603개 + 염소 뿔 변형 8개(GOAT_HORN_SOUND_VARIANTS — Guava 목록이라 두지 않음) = 레지스트리 전부, 같은 객체
            Set<String> covered = new TreeSet<>();
            int n = 0;
            for (var f : net.minecraft.sounds.SoundEvents.class.getFields()) {
                if (f.getType() == int.class) continue;
                Object v = f.get(null);
                var e = v instanceof net.minecraft.core.Holder<?> h ? (net.minecraft.sounds.SoundEvent) h.value() : (net.minecraft.sounds.SoundEvent) v;
                var key = sounds.getKey(e);
                assertNotNull(key, f.getName());
                assertTrue(sounds.get(key) == e, f.getName());
                assertTrue(covered.add(key.toString()), "같은 소리 두 번: " + f.getName());
                n++;
            }
            assertEquals(1603, n);
            for (String id : ids) if (!covered.contains(id)) assertTrue(id.startsWith("minecraft:item.goat_horn.sound."), id);
            assertEquals(8, net.minecraft.sounds.SoundEvents.GOAT_HORN_VARIANT_COUNT);
            assertEquals("minecraft:entity.pig.ambient", net.minecraft.sounds.SoundEvents.PIG_AMBIENT.getLocation().toString());
            assertEquals(ids.indexOf("minecraft:ambient.cave"), sounds.getId(net.minecraft.sounds.SoundEvents.AMBIENT_CAVE.value()));
            assertEquals(16f, net.minecraft.sounds.SoundEvents.PIG_AMBIENT.getRange(0.5f));
            assertEquals(32f, net.minecraft.sounds.SoundEvents.PIG_AMBIENT.getRange(2f));
            assertEquals(5f, net.minecraft.sounds.SoundEvent.createFixedRangeEvent(net.minecraft.resources.ResourceLocation.parse("a:b"), 5f).getRange(2f));
            // 기본값 없는 레지스트리(바닐라 MappedRegistry): 모르는 것은 null·-1
            assertEquals(null, sounds.get(net.minecraft.resources.ResourceLocation.parse("minecraft:nope")));
            assertEquals(-1, sounds.getId(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.parse("a:b"))));
            assertTrue(!(sounds instanceof net.minecraft.core.DefaultedRegistry<?>));

            // 효과 39개: 바닐라 이름·순서, 분류·색·즉시 여부는 서버 정의와 같다
            var effects = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT;
            assertEquals(Furnace.server().gameRegistries().builtIn("minecraft:mob_effect"), effects.keySet().stream().map(Object::toString)
                    .sorted(java.util.Comparator.comparingInt(k -> effects.getId(effects.get(net.minecraft.resources.ResourceLocation.parse(k))))).toList());
            var fields = net.minecraft.world.effect.MobEffects.class.getFields();
            assertEquals(39, fields.length);
            for (int i = 0; i < fields.length; i++) {
                var h = (net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>) fields[i].get(null);
                var e = h.value();
                assertEquals(i, effects.getId(e), fields[i].getName());
                var mine = org.domi.furnace.server.effect.MobEffect.byName(h.unwrapKey().orElseThrow().location().toString());
                assertEquals(mine.category().name(), e.getCategory().name(), fields[i].getName());
                assertEquals(mine.color(), e.getColor(), fields[i].getName());
                assertEquals("effect." + h.unwrapKey().orElseThrow().location().toString().replace(':', '.'), e.getDescriptionId());
            }
            assertTrue(net.minecraft.world.effect.MobEffects.HEAL.value().isInstantenous());
            assertTrue(net.minecraft.world.effect.MobEffects.SATURATION.value().isInstantenous());
            assertTrue(!net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED.value().isInstantenous());
            assertTrue(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED.value().isBeneficial());
            assertEquals("minecraft:slowness", net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN.unwrapKey().orElseThrow().location().toString());
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void soundEventsMobEffectsAndDirectSoundsGoOut() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = server.join("Snd");
            server.tick(2);
            Player p = Bukkit.getPlayer("Snd");
            var handle = ((org.bukkit.craftbukkit.entity.CraftPlayer) p).getHandle();
            var conn = handle.connection;
            tp.sync();
            long failuresBefore = tp.decodeFailures();
            var at = tp.player().location();
            var region = new org.domi.furnace.api.world.BlockPos((int) Math.floor(at.x()), (int) Math.floor(at.y()), (int) Math.floor(at.z()));
            var neutral = net.minecraft.sounds.SoundSource.NEUTRAL;
            server.onRegion(region, () -> {
                conn.send(new net.minecraft.network.protocol.game.ClientboundSoundEntityPacket(net.minecraft.sounds.SoundEvents.AMBIENT_CAVE, neutral, handle, 1f, 1f, 1L));
                conn.send(new net.minecraft.network.protocol.game.ClientboundSoundEntityPacket(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON, neutral, handle, 1f, 1f, 2L));
                conn.send(new net.minecraft.network.protocol.game.ClientboundSoundEntityPacket(
                        net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(net.minecraft.sounds.SoundEvents.PIG_AMBIENT), neutral, handle, 1f, 1f, 3L));
                // 직접 정의: 리소스 팩 소리, 고정 범위, 등록된 이름이라도 직접 정의로(바닐라 Holder.direct 와 같은 바이트)
                conn.send(new net.minecraft.network.protocol.game.ClientboundSoundEntityPacket(net.minecraft.core.Holder.direct(
                        net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.parse("myplugin:boom"))), neutral, handle, 1f, 1f, 4L));
                conn.send(new net.minecraft.network.protocol.game.ClientboundSoundEntityPacket(net.minecraft.core.Holder.direct(
                        net.minecraft.sounds.SoundEvent.createFixedRangeEvent(net.minecraft.resources.ResourceLocation.parse("myplugin:far"), 64f)), neutral, handle, 1f, 1f, 5L));
                conn.send(new net.minecraft.network.protocol.game.ClientboundSoundEntityPacket(net.minecraft.core.Holder.direct(
                        net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.parse("minecraft:entity.pig.ambient"))), neutral, handle, 1f, 1f, 6L));
                conn.send(new net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket(p.getEntityId(), net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN));
                conn.send(new net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket(net.minecraft.resources.ResourceLocation.parse("plug:token")));
                conn.send(new net.minecraft.network.protocol.ping.ClientboundPongResponsePacket(4242L));
                return null;
            });
            java.util.function.LongFunction<Object> sound = seed -> tp.awaitPacket(PacketTypes.Clientbound.SOUND_ENTITY, g -> Long.valueOf(seed).equals(g.get("seed"))).get("sound");
            assertEquals("minecraft:ambient.cave", sound.apply(1));
            assertEquals("minecraft:item.armor.equip_iron", sound.apply(2));
            assertEquals("minecraft:entity.pig.ambient", sound.apply(3));
            assertEquals("myplugin:boom", sound.apply(4));
            var far = (org.domi.furnace.api.packet.PacketStruct) sound.apply(5);
            assertEquals("myplugin:far", far.get("sound_id"));
            assertEquals(java.util.Optional.of(64f), far.get("range"));
            var direct = (org.domi.furnace.api.packet.PacketStruct) sound.apply(6);
            assertEquals("minecraft:entity.pig.ambient", direct.get("sound_id"));
            assertEquals(java.util.Optional.empty(), direct.get("range"));
            assertEquals("minecraft:slowness", tp.awaitPacket(PacketTypes.Clientbound.REMOVE_MOB_EFFECT, g -> true).get("effect"));
            assertEquals("plug:token", tp.awaitPacket(PacketTypes.Clientbound.COOKIE_REQUEST, g -> true).get("key"));
            assertEquals(4242L, tp.awaitPacket(PacketTypes.Clientbound.PONG_RESPONSE, g -> true).get("time"));
            assertEquals(failuresBefore, tp.decodeFailures(), "보낸 것은 모두 바닐라 와이어로 읽혔다");

            // 레지스트리에 없는 Reference 소리(바닐라도 번호를 못 찾는다), 묶음 안 묶음·null 은 아무것도 보내지 않고 IllegalArgumentException
            tp.clearReceived();
            server.onRegion(region, () -> {
                var unknown = assertThrows(IllegalArgumentException.class, () -> conn.send(new net.minecraft.network.protocol.game.ClientboundSoundEntityPacket(
                        holder(net.minecraft.core.registries.Registries.SOUND_EVENT, "plug:custom.sound"), neutral, handle, 1f, 1f, 7L)));
                assertTrue(unknown.getMessage().contains("Holder.direct"), unknown.getMessage());
                var inner = new net.minecraft.network.protocol.game.ClientboundBundlePacket(List.of(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(1f, 81, 0f)));
                assertThrows(IllegalArgumentException.class, () -> conn.send(new net.minecraft.network.protocol.game.ClientboundBundlePacket(List.of(
                        new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(1f, 80, 0f), inner))));
                var withNull = new java.util.ArrayList<net.minecraft.network.protocol.Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>>();
                withNull.add(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(1f, 82, 0f));
                withNull.add(null);
                assertThrows(IllegalArgumentException.class, () -> conn.send(new net.minecraft.network.protocol.game.ClientboundBundlePacket(withNull)));
                conn.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(1f, 83, 0f));
                return null;
            });
            tp.awaitPacket(PacketTypes.Clientbound.SET_HEALTH, g -> Integer.valueOf(83).equals(g.get("food")));
            assertTrue(tp.received(PacketTypes.Clientbound.SET_HEALTH).stream().noneMatch(g -> Set.of(80, 81, 82).contains(g.get("food"))), "실패한 묶음은 나가지 않는다");
            assertTrue(tp.received(PacketTypes.Clientbound.SOUND_ENTITY).isEmpty());
        }
    }
}
