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
            // 다리 없는 종류는 값 shim 이 아직 빈 껍데기다(BlockEntityType·MessageSignature.Packed·ChatType.Bound·GlobalPos·MenuType·ServerLinks.UntrustedEntry)
            assertEquals(Set.of("block_entity_data", "delete_chat", "disguised_chat", "login", "open_screen", "respawn", "server_links"), missing,
                    "다리 " + bridged.size() + "종: " + bridged);
            // game·common 66(set_score 는 E4c-2 숫자 형식 shim 으로) + cookie_request·pong_response(play 연결로도 나가는 cookie·ping 단계)
            assertEquals(68, bridged.size(), bridged.toString());
            assertTrue(bridged.containsAll(Set.of("cookie_request", "pong_response", "set_score")), bridged.toString());
        }
    }

    @Test
    void everyHandCodecKindTheServerCanWriteHasABridgeExceptBossBarAndStats() throws Exception {
        try (FurnaceTestServer server = start()) {
            var packets = Furnace.server().packets();
            Set<String> bridged = new TreeSet<>(), missing = new TreeSet<>();
            for (PacketDef d : PacketSchema.protocol767().packets()) {
                if (d.direction() != PacketDirection.CLIENTBOUND || !Set.of("game", "common", "cookie", "ping").contains(d.state())) continue;
                PacketType<?> t = PacketTypes.byName(PacketDirection.CLIENTBOUND, "minecraft:" + d.wireName());
                if (t == null || t.typed() || !d.custom() || !packets.isReadable(t)) continue;
                (SchemaPacketBridge.supports(d) ? bridged : missing).add(d.wireName());
            }
            assertEquals(Set.of("award_stats", "boss_event"), missing, "다리 " + bridged.size() + "종: " + bridged);
            assertEquals(Set.of("damage_event", "map_item_data", "move_entity_pos", "move_entity_pos_rot", "move_entity_rot", "player_abilities", "player_look_at",
                    "player_position", "section_blocks_update", "set_equipment", "set_experience", "set_objective", "set_player_team", "stop_sound",
                    "update_attributes"), bridged);
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

            // 다리가 없는 종류(보스바)는 지어내지 않고 UnsupportedNmsCall, 필수 값이 null 이면 IllegalArgumentException — 묶음은 아무것도 안 나간다
            tp.clearReceived();
            server.onRegion(region, () -> {
                var refused = assertThrows(UnsupportedNmsCall.class, () -> conn.send(construct(net.minecraft.network.protocol.game.ClientboundBossEventPacket.class,
                        java.util.UUID.randomUUID(), null)));
                assertTrue(refused.getMessage().contains("보스바"), refused.getMessage());
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
        assertEquals(null, SchemaPacketBridge.refusal(schema.packet("game", PacketDirection.CLIENTBOUND, "set_experience").orElseThrow()));
        assertTrue(SchemaPacketBridge.refusal(schema.packet("game", PacketDirection.CLIENTBOUND, "boss_event").orElseThrow()).contains("보스바"));
        assertTrue(SchemaPacketBridge.refusal(schema.packet("game", PacketDirection.CLIENTBOUND, "award_stats").orElseThrow()).contains("통계"));
        assertTrue(SchemaPacketBridge.refusal(schema.packet("game", PacketDirection.CLIENTBOUND, "player_info_update").orElseThrow()).contains("손 코덱")
                || SchemaPacketBridge.refusal(schema.packet("game", PacketDirection.CLIENTBOUND, "player_info_update").orElseThrow()).contains("타입 레코드"));
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
            // 규칙에서 벗어나는 Mojang 이름(검토 PR #263 R1 — 1.21.1 Mojang 이름 소스와 1603개 대조)
            for (String name : List.of("ALLAY_THROW", "FROGSPAWNSTEP", "SCULK_BLOCK_SPREAD", "SCULK_BLOCK_CHARGE", "SCULK_BLOCK_BREAK", "SCULK_BLOCK_FALL",
                    "SCULK_BLOCK_HIT", "SCULK_BLOCK_PLACE", "SCULK_BLOCK_STEP", "SCULK_CLICKING", "SCULK_CLICKING_STOP", "BELL_BLOCK", "WAXED_SIGN_INTERACT_FAIL",
                    "MOOSHROOM_MILK_SUSPICIOUSLY", "BREEZE_WIND_CHARGE_BURST", "BRUSH_SAND_COMPLETED", "MUSIC_BIOME_DEEP_DARK", "APPLY_EFFECT_BAD_OMEN")) {
                assertNotNull(net.minecraft.sounds.SoundEvents.class.getField(name), name);
            }
            assertEquals("minecraft:block.sculk.spread", net.minecraft.sounds.SoundEvents.SCULK_BLOCK_SPREAD.getLocation().toString());
            assertTrue(net.minecraft.world.effect.MobEffects.HEAL.value() instanceof net.minecraft.world.effect.InstantenousMobEffect);
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
                // 등록된 효과를 담은 직접 정의: 바닐라 holderRegistry 처럼 그 번호로(검토 PR #263 R3)
                conn.send(new net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket(p.getEntityId() + 1000,
                        net.minecraft.core.Holder.direct(net.minecraft.world.effect.MobEffects.DIG_SPEED.value())));
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
            assertEquals("minecraft:slowness", tp.awaitPacket(PacketTypes.Clientbound.REMOVE_MOB_EFFECT, g -> Integer.valueOf(p.getEntityId()).equals(g.get("entity_id"))).get("effect"));
            assertEquals("minecraft:haste", tp.awaitPacket(PacketTypes.Clientbound.REMOVE_MOB_EFFECT, g -> Integer.valueOf(p.getEntityId() + 1000).equals(g.get("entity_id"))).get("effect"));
            assertEquals("plug:token", tp.awaitPacket(PacketTypes.Clientbound.COOKIE_REQUEST, g -> true).get("key"));
            assertEquals(4242L, tp.awaitPacket(PacketTypes.Clientbound.PONG_RESPONSE, g -> true).get("time"));
            assertEquals(failuresBefore, tp.decodeFailures(), "보낸 것은 모두 바닐라 와이어로 읽혔다");

            // 레지스트리에 없는 Reference 소리(바닐라도 번호를 못 찾는다), 묶음 안 묶음·null 은 아무것도 보내지 않고 IllegalArgumentException
            tp.clearReceived();
            server.onRegion(region, () -> {
                var unknown = assertThrows(IllegalArgumentException.class, () -> conn.send(new net.minecraft.network.protocol.game.ClientboundSoundEntityPacket(
                        holder(net.minecraft.core.registries.Registries.SOUND_EVENT, "plug:custom.sound"), neutral, handle, 1f, 1f, 7L)));
                assertTrue(unknown.getMessage().contains("Holder.direct"), unknown.getMessage());
                // 레지스트리에 없는 효과의 직접 정의는 바닐라도 번호를 못 찾는다
                var stray = new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.NEUTRAL, 0) {};
                assertThrows(IllegalArgumentException.class, () -> conn.send(new net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket(1,
                        net.minecraft.core.Holder.direct(stray))));
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

    /** private 생성자 shim 을 만든다(바닐라 생성자가 Furnace 에 아직 없는 객체 — Abilities·PlayerTeam·Objective 등 — 를 받을 때 플러그인이 반사로 만드는 것과 같다). */
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
        throw new AssertionError("생성자 없음: " + c.getName() + " " + args.length);
    }

    /** DataFixerUpper Pair 와 같은 접근자(시험 경로에 DFU 가 없다 — 다리는 이름으로 getFirst·getSecond 를 부른다). */
    public static final class Pair<A, B> {
        private final A first;
        private final B second;

        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }

        public A getFirst() { return first; }

        public B getSecond() { return second; }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void handCodecShimsGoOutInTheServerShape() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = server.join("Hand");
            server.tick(2);
            Player p = Bukkit.getPlayer("Hand");
            var conn = ((org.bukkit.craftbukkit.entity.CraftPlayer) p).getHandle().connection;
            tp.sync();
            long failuresBefore = tp.decodeFailures();
            var at = tp.player().location();
            var region = new org.domi.furnace.api.world.BlockPos((int) Math.floor(at.x()), (int) Math.floor(at.y()), (int) Math.floor(at.z()));
            var rl = (java.util.function.Function<String, net.minecraft.resources.ResourceLocation>) net.minecraft.resources.ResourceLocation::parse;
            var item = net.minecraft.world.item.ItemStack.fromFurnace(new org.domi.furnace.api.item.ItemStack("minecraft:iron_helmet", 1), 1);
            var stone = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(rl.apply("minecraft:stone")).defaultBlockState();
            var gold = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(rl.apply("minecraft:gold_block")).defaultBlockState();
            var game = "net.minecraft.network.protocol.game.";
            server.onRegion(region, () -> {
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
                        new Pair<>(net.minecraft.world.entity.EquipmentSlot.HEAD, item), new Pair<>(net.minecraft.world.entity.EquipmentSlot.MAINHAND, item))));
                // 상태가 더 길면 바닐라처럼 positions 길이까지만
                conn.send(construct(net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket.class, net.minecraft.core.SectionPos.of(3, 4, -5),
                        new short[] {0x123, 0x000}, new net.minecraft.world.level.block.state.BlockState[] {stone, gold, stone}));
                conn.send(new net.minecraft.network.protocol.game.ClientboundDamageEventPacket(905, holder(net.minecraft.core.registries.Registries.DAMAGE_TYPE, "minecraft:fall"),
                        -1, 7, java.util.Optional.of(new net.minecraft.world.phys.Vec3(1, 2, 3))));
                // 팀: 추가(parameters + players), 제거(바닐라가 쓰지 않는 parameters·players 는 버린다)
                var params = construct(net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.Parameters.class,
                        net.minecraft.network.chat.Component.literal("Red"), net.minecraft.network.chat.Component.literal("["),
                        net.minecraft.network.chat.Component.literal("]"), "always", "never", net.minecraft.ChatFormatting.RED, 3);
                conn.send(construct(net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.class, 0, "red", List.of("Hand", "Bob"), java.util.Optional.of(params)));
                conn.send(construct(net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.class, 1, "blue", List.of("x"), java.util.Optional.of(params)));
                // 점수판 목표: 숫자 형식 셋, 제거(method 1)
                conn.send(construct(net.minecraft.network.protocol.game.ClientboundSetObjectivePacket.class, "o1", net.minecraft.network.chat.Component.literal("Kills"),
                        net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER,
                        java.util.Optional.of(new net.minecraft.network.chat.numbers.StyledFormat(net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.ChatFormatting.RED).withBold(true))), 0));
                conn.send(construct(net.minecraft.network.protocol.game.ClientboundSetObjectivePacket.class, "o2", net.minecraft.network.chat.Component.literal("Hp"),
                        net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.HEARTS, java.util.Optional.of(net.minecraft.network.chat.numbers.BlankFormat.INSTANCE), 2));
                conn.send(construct(net.minecraft.network.protocol.game.ClientboundSetObjectivePacket.class, "o3", null, null, java.util.Optional.empty(), 1));
                conn.send(new net.minecraft.network.protocol.game.ClientboundSetScorePacket("Hand", "o1", 12, java.util.Optional.empty(),
                        java.util.Optional.of(new net.minecraft.network.chat.numbers.FixedFormat(net.minecraft.network.chat.Component.literal("MAX")))));
                conn.send(construct(net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket.class, 906, List.of(
                        new net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket.AttributeSnapshot(holder(net.minecraft.core.registries.Registries.ATTRIBUTE,
                                "minecraft:generic.scale"), 2.0, List.of(new net.minecraft.world.entity.ai.attributes.AttributeModifier(rl.apply("plug:grow"), 0.5,
                                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE))))));
                byte[] colors = new byte[] {1, 2, 3, 4, 5, 6};
                conn.send(new net.minecraft.network.protocol.game.ClientboundMapItemDataPacket(new net.minecraft.world.level.saveddata.maps.MapId(77), (byte) 2, true,
                        List.of(new net.minecraft.world.level.saveddata.maps.MapDecoration(net.minecraft.world.level.saveddata.maps.MapDecorationTypes.RED_X,
                                        (byte) 10, (byte) -10, (byte) 4, java.util.Optional.of(net.minecraft.network.chat.Component.literal("X"))),
                                new net.minecraft.world.level.saveddata.maps.MapDecoration(net.minecraft.core.Holder.direct(
                                        net.minecraft.world.level.saveddata.maps.MapDecorationTypes.TRIAL_CHAMBERS.value()), (byte) 0, (byte) 0, (byte) 0, java.util.Optional.empty())),
                        new net.minecraft.world.level.saveddata.maps.MapItemSavedData.MapPatch(5, 6, 3, 2, colors)));
                conn.send(new net.minecraft.network.protocol.game.ClientboundMapItemDataPacket(new net.minecraft.world.level.saveddata.maps.MapId(78), (byte) 0, false,
                        (java.util.Collection) null, null));
                return null;
            });
            var ab = tp.awaitPacket(PacketTypes.Clientbound.PLAYER_ABILITIES, g -> Float.valueOf(0.07f).equals(g.get("flying_speed")));
            assertEquals(List.of(true, false, true, false, 0.1f), List.of(ab.get("invulnerable"), ab.get("is_flying"), ab.get("can_fly"), ab.get("instabuild"), ab.get("walking_speed")));
            var xp = tp.awaitPacket(PacketTypes.Clientbound.SET_EXPERIENCE, g -> Integer.valueOf(30).equals(g.get("total_experience")));
            assertEquals(List.of(0.5f, 2, 30), List.of(xp.get("experience_progress"), xp.get("experience_level"), xp.get("total_experience")));
            var stopName = tp.awaitPacket(PacketTypes.Clientbound.STOP_SOUND, g -> ((java.util.Optional<?>) g.get("name")).isPresent());
            assertEquals(java.util.Optional.of("minecraft:entity.pig.ambient"), stopName.get("name"));
            assertEquals(java.util.Optional.empty(), stopName.get("source"));
            var stopSource = tp.awaitPacket(PacketTypes.Clientbound.STOP_SOUND, g -> ((java.util.Optional<?>) g.get("source")).isPresent());
            assertEquals(java.util.Optional.of("WEATHER"), stopSource.get("source"));
            assertEquals(java.util.Optional.empty(), stopSource.get("name"));
            var look = tp.awaitPacket(PacketTypes.Clientbound.PLAYER_LOOK_AT, g -> Double.valueOf(1.5).equals(g.get("x")));
            assertEquals("EYES", look.get("from_anchor"));
            assertEquals(java.util.Optional.empty(), look.get("at_entity"));
            var lookEntity = tp.awaitPacket(PacketTypes.Clientbound.PLAYER_LOOK_AT, g -> Double.valueOf(4.0).equals(g.get("x")));
            var target = (org.domi.furnace.api.packet.PacketStruct) ((java.util.Optional<?>) lookEntity.get("at_entity")).orElseThrow();
            assertEquals(List.of("FEET", 4321, "EYES"), List.of(lookEntity.get("from_anchor"), target.get("entity"), target.get("to_anchor")));
            var pos = tp.awaitPacket(PacketTypes.Clientbound.MOVE_ENTITY_POS, g -> Integer.valueOf(901).equals(g.get("entity_id")));
            assertEquals(List.of((short) 40, (short) -8, (short) 0, true), List.of(pos.get("xa"), pos.get("ya"), pos.get("za"), pos.get("on_ground")));
            var posRot = tp.awaitPacket(PacketTypes.Clientbound.MOVE_ENTITY_POS_ROT, g -> Integer.valueOf(902).equals(g.get("entity_id")));
            assertEquals(List.of((short) 3, (byte) 64, (byte) -32, false), List.of(posRot.get("za"), posRot.get("y_rot"), posRot.get("x_rot"), posRot.get("on_ground")));
            var rot = tp.awaitPacket(PacketTypes.Clientbound.MOVE_ENTITY_ROT, g -> Integer.valueOf(903).equals(g.get("entity_id")));
            assertEquals(List.of((byte) 10, (byte) 20, true), List.of(rot.get("y_rot"), rot.get("x_rot"), rot.get("on_ground")));
            var eq = tp.awaitPacket(PacketTypes.Clientbound.SET_EQUIPMENT, g -> Integer.valueOf(904).equals(g.get("entity")));
            var slots = (List<org.domi.furnace.api.packet.PacketStruct>) eq.get("slots");
            assertEquals(List.of("HEAD", "MAINHAND"), slots.stream().map(x -> x.get("slot")).toList());
            assertEquals("minecraft:iron_helmet", ((org.domi.furnace.api.item.ItemStack) slots.get(0).get("item")).type());
            var sec = tp.awaitPacket(PacketTypes.Clientbound.SECTION_BLOCKS_UPDATE, g -> true);
            assertEquals(net.minecraft.core.SectionPos.of(3, 4, -5).asLong(), sec.get("section_pos"));
            assertEquals(List.of((short) 0x123, (short) 0), sec.get("positions"));
            assertEquals(List.of("minecraft:stone", "minecraft:gold_block"),
                    ((List<org.domi.furnace.api.world.BlockData>) sec.get("states")).stream().map(org.domi.furnace.api.world.BlockData::type).toList());
            var dmg = tp.awaitPacket(PacketTypes.Clientbound.DAMAGE_EVENT, g -> Integer.valueOf(905).equals(g.get("entity_id")));
            assertEquals(List.of("minecraft:fall", -1, 7), List.of(dmg.get("source_type"), dmg.get("source_cause_id"), dmg.get("source_direct_id")));
            assertEquals(2.0, ((org.domi.furnace.api.packet.PacketStruct) ((java.util.Optional<?>) dmg.get("source_position")).orElseThrow()).get("y"));
            var red = tp.awaitPacket(PacketTypes.Clientbound.SET_PLAYER_TEAM, g -> "red".equals(g.get("name")));
            var rp = (org.domi.furnace.api.packet.PacketStruct) ((java.util.Optional<?>) red.get("parameters")).orElseThrow();
            assertEquals(List.of("Red", 3, "always", "never", "RED", "[", "]"), List.of(((org.domi.furnace.api.text.Text) rp.get("display_name")).plainText(), rp.get("options"),
                    rp.get("nametag_visibility"), rp.get("collision_rule"), rp.get("color"), ((org.domi.furnace.api.text.Text) rp.get("player_prefix")).plainText(),
                    ((org.domi.furnace.api.text.Text) rp.get("player_suffix")).plainText()));
            assertEquals(java.util.Optional.of(List.of("Hand", "Bob")), red.get("players"));
            var blue = tp.awaitPacket(PacketTypes.Clientbound.SET_PLAYER_TEAM, g -> "blue".equals(g.get("name")));
            assertEquals(List.of(1, java.util.Optional.empty(), java.util.Optional.empty()), List.of(blue.get("method"), blue.get("parameters"), blue.get("players")));
            var o1 = tp.awaitPacket(PacketTypes.Clientbound.SET_OBJECTIVE, g -> "o1".equals(g.get("objective_name")));
            var o1p = (org.domi.furnace.api.packet.PacketStruct) ((java.util.Optional<?>) o1.get("parameters")).orElseThrow();
            assertEquals("INTEGER", o1p.get("render_type"));
            var styled = (org.domi.furnace.api.packet.PacketStruct) ((java.util.Optional<?>) o1p.get("number_format")).orElseThrow();
            assertEquals("minecraft:styled", styled.get("type"));
            var style = (org.domi.furnace.api.nbt.NbtCompound) styled.get("style");
            assertEquals("red", style.getString("color"));
            assertTrue(style.getBoolean("bold"));
            var o2 = tp.awaitPacket(PacketTypes.Clientbound.SET_OBJECTIVE, g -> "o2".equals(g.get("objective_name")));
            var o2p = (org.domi.furnace.api.packet.PacketStruct) ((java.util.Optional<?>) o2.get("parameters")).orElseThrow();
            assertEquals("HEARTS", o2p.get("render_type"));
            assertEquals("minecraft:blank", ((org.domi.furnace.api.packet.PacketStruct) ((java.util.Optional<?>) o2p.get("number_format")).orElseThrow()).get("type"));
            var o3 = tp.awaitPacket(PacketTypes.Clientbound.SET_OBJECTIVE, g -> "o3".equals(g.get("objective_name")));
            assertEquals(List.of(1, java.util.Optional.empty()), List.of(o3.get("method"), o3.get("parameters")));
            var score = tp.awaitPacket(PacketTypes.Clientbound.SET_SCORE, g -> "Hand".equals(g.get("owner")));
            var fixed = (org.domi.furnace.api.packet.PacketStruct) ((java.util.Optional<?>) score.get("number_format")).orElseThrow();
            assertEquals("MAX", ((org.domi.furnace.api.text.Text) fixed.get("value")).plainText());
            var attrs = tp.awaitPacket(PacketTypes.Clientbound.UPDATE_ATTRIBUTES, g -> Integer.valueOf(906).equals(g.get("entity_id")));
            var snap = ((List<org.domi.furnace.api.packet.PacketStruct>) attrs.get("attributes")).get(0);
            assertEquals(List.of("minecraft:generic.scale", 2.0), List.of(snap.get("attribute"), snap.get("base")));
            var mod = ((List<org.domi.furnace.api.packet.PacketStruct>) snap.get("modifiers")).get(0);
            assertEquals(List.of("plug:grow", 0.5, "ADD_MULTIPLIED_BASE"), List.of(mod.get("id"), mod.get("amount"), mod.get("operation")));
            var map = tp.awaitPacket(PacketTypes.Clientbound.MAP_ITEM_DATA, g -> Integer.valueOf(77).equals(g.get("map_id")));
            assertEquals(List.of((byte) 2, true), List.of(map.get("scale"), map.get("locked")));
            var decos = (List<org.domi.furnace.api.packet.PacketStruct>) ((java.util.Optional<?>) map.get("decorations")).orElseThrow();
            assertEquals(List.of("minecraft:red_x", "minecraft:trial_chambers"), decos.stream().map(x -> x.get("type")).toList());
            assertEquals(List.of((byte) 10, (byte) -10, (byte) 4), List.of(decos.get(0).get("x"), decos.get(0).get("y"), decos.get(0).get("rot")));
            assertEquals("X", ((org.domi.furnace.api.text.Text) ((java.util.Optional<?>) decos.get(0).get("name")).orElseThrow()).plainText());
            var patch = (org.domi.furnace.api.packet.PacketStruct) ((java.util.Optional<?>) map.get("color_patch")).orElseThrow();
            assertEquals(List.of(3, 2, 5, 6), List.of(patch.get("width"), patch.get("height"), patch.get("start_x"), patch.get("start_y")));
            assertTrue(java.util.Arrays.equals(new byte[] {1, 2, 3, 4, 5, 6}, (byte[]) patch.get("map_colors")));
            var bare = tp.awaitPacket(PacketTypes.Clientbound.MAP_ITEM_DATA, g -> Integer.valueOf(78).equals(g.get("map_id")));
            assertEquals(List.of(java.util.Optional.empty(), java.util.Optional.empty()), List.of(bare.get("decorations"), bare.get("color_patch")));
            assertEquals(failuresBefore, tp.decodeFailures(), "보낸 것은 모두 바닐라 와이어로 읽혔다");

            // 바닐라도 쓰다 실패하는 값은 아무것도 보내지 않고 IllegalArgumentException
            tp.clearReceived();
            server.onRegion(region, () -> {
                java.util.function.Consumer<net.minecraft.network.protocol.Packet<?>> bad = pk -> assertThrows(IllegalArgumentException.class, () -> conn.send(pk), pk.toString());
                bad.accept(new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(1, List.of()));   // 빈 목록: 클라이언트가 적어도 한 칸을 읽는다
                bad.accept(new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(1, List.of("not a pair")));
                bad.accept(construct(net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket.class, net.minecraft.core.SectionPos.of(0, 0, 0),
                        new short[] {1, 2}, new net.minecraft.world.level.block.state.BlockState[] {stone}));
                bad.accept(new net.minecraft.network.protocol.game.ClientboundDamageEventPacket(1, net.minecraft.core.Holder.direct(null), -1, -1, java.util.Optional.empty()));
                bad.accept(construct(net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.class, 2, "t", List.of(), java.util.Optional.empty()));
                bad.accept(construct(net.minecraft.network.protocol.game.ClientboundSetObjectivePacket.class, "o", net.minecraft.network.chat.Component.literal("o"),
                        net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER, java.util.Optional.of((net.minecraft.network.chat.numbers.NumberFormat) n -> null), 0));
                bad.accept(new net.minecraft.network.protocol.game.ClientboundMapItemDataPacket(new net.minecraft.world.level.saveddata.maps.MapId(1), (byte) 0, false,
                        List.of(new net.minecraft.world.level.saveddata.maps.MapDecoration(net.minecraft.core.Holder.direct(new net.minecraft.world.level.saveddata.maps.MapDecorationType(
                                rl.apply("plug:pin"), true, -1, false, true)), (byte) 0, (byte) 0, (byte) 0, java.util.Optional.empty())), null));
                conn.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(0.25f, 1, 99));
                return null;
            });
            tp.awaitPacket(PacketTypes.Clientbound.SET_EXPERIENCE, g -> Integer.valueOf(99).equals(g.get("experience_level")));
            for (var t : List.of(PacketTypes.Clientbound.SET_EQUIPMENT, PacketTypes.Clientbound.SECTION_BLOCKS_UPDATE, PacketTypes.Clientbound.DAMAGE_EVENT,
                    PacketTypes.Clientbound.SET_PLAYER_TEAM, PacketTypes.Clientbound.SET_OBJECTIVE, PacketTypes.Clientbound.MAP_ITEM_DATA)) {
                assertTrue(tp.received(t).isEmpty(), t.toString());
            }
            assertTrue(tp.isConnected());
        }
    }

    @Test
    void mapDecorationTypesAreTheVanillaTable() throws Exception {
        try (FurnaceTestServer server = start()) {
            var reg = net.minecraft.core.registries.BuiltInRegistries.MAP_DECORATION_TYPE;
            var ids = Furnace.server().gameRegistries().builtIn("minecraft:map_decoration_type");
            assertEquals(35, reg.size());
            var fields = net.minecraft.world.level.saveddata.maps.MapDecorationTypes.class.getFields();
            assertEquals(35, fields.length);
            for (int i = 0; i < fields.length; i++) {
                @SuppressWarnings("unchecked")
                var h = (net.minecraft.core.Holder<net.minecraft.world.level.saveddata.maps.MapDecorationType>) fields[i].get(null);
                assertEquals(ids.get(i), h.unwrapKey().orElseThrow().location().toString(), fields[i].getName());   // 선언 순서 = 레지스트리 순서
                assertEquals(i, reg.getId(h.value()));
            }
            var mansion = net.minecraft.world.level.saveddata.maps.MapDecorationTypes.WOODLAND_MANSION.value();
            assertEquals("minecraft:woodland_mansion", mansion.assetId().toString());
            assertEquals(List.of(true, 5393476, true, false), List.of(mansion.showOnItemFrame(), mansion.mapColor(), mansion.explorationMapElement(), mansion.trackCount()));
            var player = net.minecraft.world.level.saveddata.maps.MapDecorationTypes.PLAYER.value();
            assertEquals(List.of(false, -1, false, true), List.of(player.showOnItemFrame(), player.mapColor(), player.explorationMapElement(), player.trackCount()));
            assertTrue(!player.hasMapColor());
            assertEquals("minecraft:white_banner", net.minecraft.world.level.saveddata.maps.MapDecorationTypes.WHITE_BANNER.value().assetId().toString());
            assertEquals(10066329, net.minecraft.world.level.saveddata.maps.MapDecorationTypes.SWAMP_HUT.value().mapColor());
            assertEquals(12741452, net.minecraft.world.level.saveddata.maps.MapDecorationTypes.TRIAL_CHAMBERS.value().mapColor());
            assertEquals("map_7", new net.minecraft.world.level.saveddata.maps.MapId(7).key());
            assertEquals(net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.BY_ID.apply(9));
            assertEquals("add_multiplied_total", net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL.getSerializedName());
            assertEquals("12", net.minecraft.network.chat.numbers.StyledFormat.SIDEBAR_DEFAULT.format(12).getString());
            assertEquals("", net.minecraft.network.chat.numbers.BlankFormat.INSTANCE.format(12).getString());
        }
    }
}
