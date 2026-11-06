// 1.21.1(프로토콜 767) 변환 계층. id 순서는 바닐라 GameProtocols 의 등록 순서(바이트코드로 확인, 2026-11-06)와 같다.
// 블록 상태는 서버 블록 레지스트리, 엔티티 종류·소리는 데이터 레지스트리(ItemContext)로 이름 ↔ 번호를 바꾼다.
package org.domi.furnace.server.plugin.packet;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.domi.furnace.api.entity.Hand;
import org.domi.furnace.api.packet.OpaquePacket;
import org.domi.furnace.api.packet.Packet;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.PacketTypes.Clientbound;
import org.domi.furnace.api.packet.PacketTypes.Serverbound;
import org.domi.furnace.api.packet.clientbound.*;
import org.domi.furnace.api.packet.serverbound.*;
import org.domi.furnace.api.world.BlockFace;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.SoundCategory;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.plugin.ApiConvert;
import org.domi.furnace.server.world.BlockRegistry;

public final class Protocol767 implements ProtocolCodec {

    public static final int PROTOCOL = 767;

    /** play 서버→클라이언트, id 순서. */
    static final String[] CLIENTBOUND = {
            "bundle_delimiter", "add_entity", "add_experience_orb", "animate", "award_stats", "block_changed_ack",
            "block_destruction", "block_entity_data", "block_event", "block_update", "boss_event", "change_difficulty",
            "chunk_batch_finished", "chunk_batch_start", "chunks_biomes", "clear_titles", "command_suggestions", "commands",
            "container_close", "container_set_content", "container_set_data", "container_set_slot", "cookie_request", "cooldown",
            "custom_chat_completions", "custom_payload", "damage_event", "debug_sample", "delete_chat", "disconnect",
            "disguised_chat", "entity_event", "explode", "forget_level_chunk", "game_event", "horse_screen_open", "hurt_animation",
            "initialize_border", "keep_alive", "level_chunk_with_light", "level_event", "level_particles", "light_update", "login",
            "map_item_data", "merchant_offers", "move_entity_pos", "move_entity_pos_rot", "move_entity_rot", "move_vehicle",
            "open_book", "open_screen", "open_sign_editor", "ping", "pong_response", "place_ghost_recipe", "player_abilities",
            "player_chat", "player_combat_end", "player_combat_enter", "player_combat_kill", "player_info_remove",
            "player_info_update", "player_look_at", "player_position", "recipe", "remove_entities", "remove_mob_effect",
            "reset_score", "resource_pack_pop", "resource_pack_push", "respawn", "rotate_head", "section_blocks_update",
            "select_advancements_tab", "server_data", "set_action_bar_text", "set_border_center", "set_border_lerp_size",
            "set_border_size", "set_border_warning_delay", "set_border_warning_distance", "set_camera", "set_carried_item",
            "set_chunk_cache_center", "set_chunk_cache_radius", "set_default_spawn_position", "set_display_objective",
            "set_entity_data", "set_entity_link", "set_entity_motion", "set_equipment", "set_experience", "set_health",
            "set_objective", "set_passengers", "set_player_team", "set_score", "set_simulation_distance", "set_subtitle_text",
            "set_time", "set_title_text", "set_titles_animation", "sound_entity", "sound", "start_configuration", "stop_sound",
            "store_cookie", "system_chat", "tab_list", "tag_query", "take_item_entity", "teleport_entity", "ticking_state",
            "ticking_step", "transfer", "update_advancements", "update_attributes", "update_mob_effect", "update_recipes",
            "update_tags", "projectile_power", "custom_report_details", "server_links"
    };

    /** play 클라이언트→서버, id 순서. */
    static final String[] SERVERBOUND = {
            "accept_teleportation", "block_entity_tag_query", "change_difficulty", "chat_ack", "chat_command",
            "chat_command_signed", "chat", "chat_session_update", "chunk_batch_received", "client_command", "client_information",
            "command_suggestion", "configuration_acknowledged", "container_button_click", "container_click", "container_close",
            "container_slot_state_changed", "cookie_response", "custom_payload", "debug_sample_subscription", "edit_book",
            "entity_tag_query", "interact", "jigsaw_generate", "keep_alive", "lock_difficulty", "move_player_pos",
            "move_player_pos_rot", "move_player_rot", "move_player_status_only", "move_vehicle", "paddle_boat", "pick_item",
            "ping_request", "place_recipe", "player_abilities", "player_action", "player_command", "player_input", "pong",
            "recipe_book_change_settings", "recipe_book_seen_recipe", "rename_item", "resource_pack", "seen_advancements",
            "select_trade", "set_beacon", "set_carried_item", "set_command_block", "set_command_minecart",
            "set_creative_mode_slot", "set_jigsaw_block", "set_structure_block", "sign_update", "swing", "teleport_to_entity",
            "use_item_on", "use_item"
    };

    private static final int STRING_MAX = 32767;
    private static final String ENTITY_TYPES = "minecraft:entity_type", SOUNDS = "minecraft:sound_event";

    private interface Decoder { Packet decode(int id, ByteBuf b); }

    private interface Encoder<P extends Packet> { int encode(P p, ByteBuf out); }

    private record Codec<P extends Packet>(Decoder dec, Encoder<P> enc) {}

    private final PacketType<?>[] cbTypes = new PacketType<?>[CLIENTBOUND.length];
    private final PacketType<?>[] sbTypes = new PacketType<?>[SERVERBOUND.length];
    private final Map<PacketType<?>, int[]> ids = new IdentityHashMap<>();
    private final Map<PacketType<?>, Codec<?>> codecs = new IdentityHashMap<>();
    private final Supplier<BlockRegistry> blocks;

    public Protocol767(Supplier<BlockRegistry> blocks) {
        this.blocks = blocks;
        Map<PacketType<?>, List<Integer>> tmp = new IdentityHashMap<>();
        for (int i = 0; i < CLIENTBOUND.length; i++) {
            cbTypes[i] = lookup(PacketDirection.CLIENTBOUND, CLIENTBOUND[i]);
            tmp.computeIfAbsent(cbTypes[i], k -> new ArrayList<>()).add(i);
        }
        for (int i = 0; i < SERVERBOUND.length; i++) {
            String n = SERVERBOUND[i].startsWith("move_player_") ? "move_player" : SERVERBOUND[i];
            sbTypes[i] = lookup(PacketDirection.SERVERBOUND, n);
            tmp.computeIfAbsent(sbTypes[i], k -> new ArrayList<>()).add(i);
        }
        tmp.forEach((t, l) -> ids.put(t, l.stream().mapToInt(Integer::intValue).toArray()));
        registerClientbound();
        registerServerbound();
    }

    private static volatile BlockRegistry fallbackBlocks;

    /** 서버 블록 레지스트리(월드가 아직 받지 않았으면 이 버전의 표를 한 번 읽어 쓴다 — 번호는 같다). */
    private BlockRegistry blocks() {
        BlockRegistry r = blocks.get();
        if (r != null) return r;
        r = fallbackBlocks;
        if (r == null) {
            synchronized (Protocol767.class) {
                if (fallbackBlocks == null) fallbackBlocks = BlockRegistry.load("blocks/1.21.1.txt");
                r = fallbackBlocks;
            }
        }
        return r;
    }

    private static PacketType<?> lookup(PacketDirection d, String name) {
        PacketType<?> t = PacketTypes.byName(d, name);
        if (t == null) throw new IllegalStateException("PacketTypes 에 없는 패킷: " + d + " " + name);
        return t;
    }

    @Override
    public int protocolVersion() { return PROTOCOL; }

    @Override
    public PacketType<?> clientboundType(int id) { return id >= 0 && id < cbTypes.length ? cbTypes[id] : null; }

    @Override
    public PacketType<?> serverboundType(int id) { return id >= 0 && id < sbTypes.length ? sbTypes[id] : null; }

    @Override
    public int[] ids(PacketType<?> type) {
        int[] a = ids.get(type);
        return a == null ? new int[0] : a;
    }

    private int id(PacketType<?> type) {
        int[] a = ids.get(type);
        if (a == null) throw new IllegalArgumentException("이 서버 버전에 없는 패킷: " + type);
        return a[0];
    }

    @Override
    @SuppressWarnings("unchecked")
    public Packet decode(PacketType<?> type, int id, ByteBuf body) {
        Codec<?> c = codecs.get(type);
        if (c == null) return new OpaquePacket((PacketType<OpaquePacket>) type);
        return c.dec().decode(id, body);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public int encode(Packet packet, ByteBuf out) {
        if (packet instanceof OpaquePacket) throw new IllegalArgumentException("OpaquePacket 은 보낼 수 없다: " + packet.type());
        Codec c = codecs.get(packet.type());
        if (c == null) throw new IllegalArgumentException("이 서버 버전에서 보낼 수 없는 패킷: " + packet.type());
        return c.enc().encode(packet, out);
    }

    private <P extends Packet> void codec(PacketType<P> type, Decoder dec, Encoder<P> enc) {
        if (!type.typed()) throw new IllegalStateException("타입 래퍼 없는 종류: " + type);
        codecs.put(type, new Codec<>(dec, enc));
    }

    // ------------------------------------------------------------------ 공통 조각

    private static float readAngle(ByteBuf b) { return b.readByte() * 360.0f / 256.0f; }

    private static void writeAngle(ByteBuf b, float deg) { Types.writeAngle(b, deg); }

    private static double readVelocity(ByteBuf b) { return b.readShort() / 8000.0; }

    private static void writeVelocity(ByteBuf b, double v) { b.writeShort((int) (Math.clamp(v, -3.9, 3.9) * 8000.0)); }

    private static BlockPos readPos(ByteBuf b) {
        int[] p = Types.readBlockPos(b);
        return new BlockPos(p[0], p[1], p[2]);
    }

    private static void writePos(ByteBuf b, BlockPos p) { Types.writeBlockPos(b, p.x(), p.y(), p.z()); }

    private static <E extends Enum<E>> E enumOf(E[] values, int i, String what) {
        if (i < 0 || i >= values.length) throw new IllegalArgumentException(what + " 범위 밖: " + i);
        return values[i];
    }

    private static Hand hand(int i) { return enumOf(Hand.values(), i, "손"); }

    private static byte[] rest(ByteBuf b) {
        byte[] a = new byte[b.readableBytes()];
        b.readBytes(a);
        return a;
    }

    private static String registryName(String registry, int id) {
        String n = ItemContext.installed() ? ItemContext.name(registry, id) : null;
        if (n == null) throw new IllegalArgumentException(registry + " 에 없는 번호: " + id);
        return n;
    }

    private static int registryId(String registry, String name) {
        Integer id = ItemContext.installed() ? ItemContext.id(registry, name) : null;
        if (id == null) throw new IllegalArgumentException(registry + " 에 없는 이름: " + name);
        return id;
    }

    // ------------------------------------------------------------------ 서버→클라이언트

    private void registerClientbound() {
        codec(Clientbound.SYSTEM_CHAT, (id, b) -> new ClientboundSystemChatPacket(NetworkText.read(b), b.readBoolean()),
                (p, o) -> { NetworkText.write(o, p.message()); o.writeBoolean(p.actionBar()); return id(Clientbound.SYSTEM_CHAT); });
        codec(Clientbound.SET_ACTION_BAR_TEXT, (id, b) -> new ClientboundSetActionBarTextPacket(NetworkText.read(b)),
                (p, o) -> { NetworkText.write(o, p.text()); return id(Clientbound.SET_ACTION_BAR_TEXT); });
        codec(Clientbound.SET_TITLE_TEXT, (id, b) -> new ClientboundSetTitleTextPacket(NetworkText.read(b)),
                (p, o) -> { NetworkText.write(o, p.text()); return id(Clientbound.SET_TITLE_TEXT); });
        codec(Clientbound.SET_SUBTITLE_TEXT, (id, b) -> new ClientboundSetSubtitleTextPacket(NetworkText.read(b)),
                (p, o) -> { NetworkText.write(o, p.text()); return id(Clientbound.SET_SUBTITLE_TEXT); });
        codec(Clientbound.DISCONNECT, (id, b) -> new ClientboundDisconnectPacket(NetworkText.read(b)),
                (p, o) -> { NetworkText.write(o, p.reason()); return id(Clientbound.DISCONNECT); });
        codec(Clientbound.SET_TITLES_ANIMATION, (id, b) -> new ClientboundSetTitlesAnimationPacket(b.readInt(), b.readInt(), b.readInt()),
                (p, o) -> { o.writeInt(p.fadeIn()); o.writeInt(p.stay()); o.writeInt(p.fadeOut()); return id(Clientbound.SET_TITLES_ANIMATION); });
        codec(Clientbound.CLEAR_TITLES, (id, b) -> new ClientboundClearTitlesPacket(b.readBoolean()),
                (p, o) -> { o.writeBoolean(p.resetTimes()); return id(Clientbound.CLEAR_TITLES); });
        codec(Clientbound.SET_TIME, (id, b) -> new ClientboundSetTimePacket(b.readLong(), b.readLong()),
                (p, o) -> { o.writeLong(p.gameTime()); o.writeLong(p.dayTime()); return id(Clientbound.SET_TIME); });
        codec(Clientbound.BLOCK_UPDATE, (id, b) -> {
            BlockPos pos = readPos(b);
            return new ClientboundBlockUpdatePacket(pos, ApiConvert.block(Types.readVarInt(b), blocks()));
        }, (p, o) -> {
            int state = ApiConvert.stateId(p.block(), blocks());
            writePos(o, p.pos());
            Types.writeVarInt(o, state);
            return id(Clientbound.BLOCK_UPDATE);
        });
        codec(Clientbound.ADD_ENTITY, (id, b) -> {
            int eid = Types.readVarInt(b);
            java.util.UUID uuid = Types.readUuid(b);
            String type = registryName(ENTITY_TYPES, Types.readVarInt(b));
            double x = b.readDouble(), y = b.readDouble(), z = b.readDouble();
            float pitch = readAngle(b), yaw = readAngle(b), head = readAngle(b);
            int data = Types.readVarInt(b);
            return new ClientboundAddEntityPacket(eid, uuid, type, x, y, z, pitch, yaw, head, data, readVelocity(b), readVelocity(b), readVelocity(b));
        }, (p, o) -> {
            int type = registryId(ENTITY_TYPES, p.entityType());
            Types.writeVarInt(o, p.entityId());
            Types.writeUuid(o, p.uuid());
            Types.writeVarInt(o, type);
            o.writeDouble(p.x());
            o.writeDouble(p.y());
            o.writeDouble(p.z());
            writeAngle(o, p.pitch());
            writeAngle(o, p.yaw());
            writeAngle(o, p.headYaw());
            Types.writeVarInt(o, p.data());
            writeVelocity(o, p.velocityX());
            writeVelocity(o, p.velocityY());
            writeVelocity(o, p.velocityZ());
            return id(Clientbound.ADD_ENTITY);
        });
        codec(Clientbound.REMOVE_ENTITIES, (id, b) -> {
            int n = Types.readVarInt(b);
            if (n < 0 || n > b.readableBytes()) throw new IllegalArgumentException("엔티티 수 오류: " + n);
            List<Integer> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(Types.readVarInt(b));
            return new ClientboundRemoveEntitiesPacket(l);
        }, (p, o) -> {
            Types.writeVarInt(o, p.entityIds().size());
            for (int e : p.entityIds()) Types.writeVarInt(o, e);
            return id(Clientbound.REMOVE_ENTITIES);
        });
        codec(Clientbound.TELEPORT_ENTITY, (id, b) -> {
            int eid = Types.readVarInt(b);
            double x = b.readDouble(), y = b.readDouble(), z = b.readDouble();
            float yaw = readAngle(b), pitch = readAngle(b);
            return new ClientboundTeleportEntityPacket(eid, x, y, z, yaw, pitch, b.readBoolean());
        }, (p, o) -> {
            Types.writeVarInt(o, p.entityId());
            o.writeDouble(p.x());
            o.writeDouble(p.y());
            o.writeDouble(p.z());
            writeAngle(o, p.yaw());
            writeAngle(o, p.pitch());
            o.writeBoolean(p.onGround());
            return id(Clientbound.TELEPORT_ENTITY);
        });
        codec(Clientbound.SET_ENTITY_MOTION, (id, b) -> new ClientboundSetEntityMotionPacket(Types.readVarInt(b), readVelocity(b), readVelocity(b), readVelocity(b)),
                (p, o) -> {
                    Types.writeVarInt(o, p.entityId());
                    writeVelocity(o, p.velocityX());
                    writeVelocity(o, p.velocityY());
                    writeVelocity(o, p.velocityZ());
                    return id(Clientbound.SET_ENTITY_MOTION);
                });
        codec(Clientbound.ROTATE_HEAD, (id, b) -> new ClientboundRotateHeadPacket(Types.readVarInt(b), readAngle(b)),
                (p, o) -> { Types.writeVarInt(o, p.entityId()); writeAngle(o, p.headYaw()); return id(Clientbound.ROTATE_HEAD); });
        codec(Clientbound.ANIMATE, (id, b) -> {
            int eid = Types.readVarInt(b);
            ClientboundAnimatePacket.Animation a = switch (b.readUnsignedByte()) {
                case 0 -> ClientboundAnimatePacket.Animation.SWING_MAIN_HAND;
                case 2 -> ClientboundAnimatePacket.Animation.WAKE_UP;
                case 3 -> ClientboundAnimatePacket.Animation.SWING_OFF_HAND;
                case 4 -> ClientboundAnimatePacket.Animation.CRITICAL_HIT;
                case 5 -> ClientboundAnimatePacket.Animation.MAGIC_CRITICAL_HIT;
                default -> throw new IllegalArgumentException("모르는 애니메이션");
            };
            return new ClientboundAnimatePacket(eid, a);
        }, (p, o) -> {
            Types.writeVarInt(o, p.entityId());
            o.writeByte(switch (p.animation()) {
                case SWING_MAIN_HAND -> 0;
                case WAKE_UP -> 2;
                case SWING_OFF_HAND -> 3;
                case CRITICAL_HIT -> 4;
                case MAGIC_CRITICAL_HIT -> 5;
            });
            return id(Clientbound.ANIMATE);
        });
        codec(Clientbound.SOUND, (id, b) -> {
            int holder = Types.readVarInt(b);
            String sound;
            Float range = null;
            if (holder == 0) {
                sound = Types.readString(b, STRING_MAX);
                if (b.readBoolean()) range = b.readFloat();
            } else {
                sound = registryName(SOUNDS, holder - 1);
            }
            SoundCategory cat = enumOf(SoundCategory.values(), Types.readVarInt(b), "소리 분류");
            double x = b.readInt() / 8.0, y = b.readInt() / 8.0, z = b.readInt() / 8.0;
            return new ClientboundSoundPacket(sound, range, cat, x, y, z, b.readFloat(), b.readFloat(), b.readLong());
        }, (p, o) -> {
            Integer reg = p.fixedRange() == null && ItemContext.installed() ? ItemContext.id(SOUNDS, p.sound()) : null;
            if (reg != null) {
                Types.writeVarInt(o, reg + 1);
            } else {
                Types.writeVarInt(o, 0);
                Types.writeString(o, p.sound());
                o.writeBoolean(p.fixedRange() != null);
                if (p.fixedRange() != null) o.writeFloat(p.fixedRange());
            }
            Types.writeVarInt(o, p.category().ordinal());
            o.writeInt((int) (p.x() * 8.0));
            o.writeInt((int) (p.y() * 8.0));
            o.writeInt((int) (p.z() * 8.0));
            o.writeFloat(p.volume());
            o.writeFloat(p.pitch());
            o.writeLong(p.seed());
            return id(Clientbound.SOUND);
        });
        codec(Clientbound.CUSTOM_PAYLOAD, (id, b) -> new ClientboundCustomPayloadPacket(Types.readString(b, STRING_MAX), rest(b)),
                (p, o) -> {
                    byte[] data = p.data();
                    if (data.length > 1048576) throw new IllegalArgumentException("페이로드가 너무 크다: " + data.length);
                    Types.writeString(o, p.channel());
                    o.writeBytes(data);
                    return id(Clientbound.CUSTOM_PAYLOAD);
                });
    }

    // ------------------------------------------------------------------ 클라이언트→서버

    private static final int MOVE_POS = 0x1A, MOVE_POS_ROT = 0x1B, MOVE_ROT = 0x1C, MOVE_STATUS = 0x1D;

    private void registerServerbound() {
        codec(Serverbound.CHAT, (id, b) -> {
            String msg = Types.readString(b, 256);
            java.time.Instant ts = java.time.Instant.ofEpochMilli(b.readLong());
            long salt = b.readLong();
            byte[] sig = null;
            if (b.readBoolean()) {
                sig = new byte[256];
                b.readBytes(sig);
            }
            int offset = Types.readVarInt(b);
            int bits = b.readUnsignedByte() | b.readUnsignedByte() << 8 | b.readUnsignedByte() << 16;
            return new ServerboundChatPacket(msg, ts, salt, sig, offset, bits);
        }, (p, o) -> {
            Types.writeString(o, p.message());
            o.writeLong(p.timestamp().toEpochMilli());
            o.writeLong(p.salt());
            byte[] sig = p.signature();
            o.writeBoolean(sig != null);
            if (sig != null) o.writeBytes(sig);
            Types.writeVarInt(o, p.lastSeenOffset());
            int bits = p.lastSeenAcknowledged() & 0xFFFFF;
            o.writeByte(bits & 0xFF);
            o.writeByte(bits >>> 8 & 0xFF);
            o.writeByte(bits >>> 16 & 0xFF);
            return id(Serverbound.CHAT);
        });
        codec(Serverbound.CHAT_COMMAND, (id, b) -> new ServerboundChatCommandPacket(Types.readString(b, 256)),
                (p, o) -> { Types.writeString(o, p.command()); return id(Serverbound.CHAT_COMMAND); });
        codec(Serverbound.CLIENT_COMMAND, (id, b) -> new ServerboundClientCommandPacket(enumOf(ServerboundClientCommandPacket.Action.values(), Types.readVarInt(b), "client_command")),
                (p, o) -> { Types.writeVarInt(o, p.action().ordinal()); return id(Serverbound.CLIENT_COMMAND); });
        codec(Serverbound.CLIENT_INFORMATION, (id, b) -> {
            String locale = Types.readString(b, 16);
            int vd = b.readByte();
            var chat = enumOf(ServerboundClientInformationPacket.ChatVisibility.values(), Types.readVarInt(b), "chat_visibility");
            boolean colors = b.readBoolean();
            int skin = b.readUnsignedByte();
            var arm = enumOf(ServerboundClientInformationPacket.MainArm.values(), Types.readVarInt(b), "main_arm");
            return new ServerboundClientInformationPacket(locale, vd, chat, colors, skin, arm, b.readBoolean(), b.readBoolean());
        }, (p, o) -> {
            Types.writeString(o, p.locale());
            o.writeByte(p.viewDistance());
            Types.writeVarInt(o, p.chatVisibility().ordinal());
            o.writeBoolean(p.chatColors());
            o.writeByte(p.skinParts());
            Types.writeVarInt(o, p.mainArm().ordinal());
            o.writeBoolean(p.textFiltering());
            o.writeBoolean(p.allowsListing());
            return id(Serverbound.CLIENT_INFORMATION);
        });
        codec(Serverbound.CUSTOM_PAYLOAD, (id, b) -> new ServerboundCustomPayloadPacket(Types.readString(b, STRING_MAX), rest(b)),
                (p, o) -> { Types.writeString(o, p.channel()); o.writeBytes(p.data()); return id(Serverbound.CUSTOM_PAYLOAD); });
        codec(Serverbound.INTERACT, (id, b) -> {
            int eid = Types.readVarInt(b);
            var action = enumOf(ServerboundInteractPacket.Action.values(), Types.readVarInt(b), "interact");
            float tx = 0, ty = 0, tz = 0;
            if (action == ServerboundInteractPacket.Action.INTERACT_AT) { tx = b.readFloat(); ty = b.readFloat(); tz = b.readFloat(); }
            Hand hand = action == ServerboundInteractPacket.Action.ATTACK ? null : hand(Types.readVarInt(b));
            return new ServerboundInteractPacket(eid, action, hand, tx, ty, tz, b.readBoolean());
        }, (p, o) -> {
            Types.writeVarInt(o, p.entityId());
            Types.writeVarInt(o, p.action().ordinal());
            if (p.action() == ServerboundInteractPacket.Action.INTERACT_AT) { o.writeFloat(p.targetX()); o.writeFloat(p.targetY()); o.writeFloat(p.targetZ()); }
            if (p.action() != ServerboundInteractPacket.Action.ATTACK) Types.writeVarInt(o, p.hand().ordinal());
            o.writeBoolean(p.sneaking());
            return id(Serverbound.INTERACT);
        });
        codec(Serverbound.MOVE_PLAYER, (id, b) -> switch (id) {
            case MOVE_POS -> ServerboundMovePlayerPacket.position(b.readDouble(), b.readDouble(), b.readDouble(), b.readBoolean());
            case MOVE_POS_ROT -> ServerboundMovePlayerPacket.positionRotation(b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readBoolean());
            case MOVE_ROT -> ServerboundMovePlayerPacket.rotation(b.readFloat(), b.readFloat(), b.readBoolean());
            default -> ServerboundMovePlayerPacket.statusOnly(b.readBoolean());
        }, (p, o) -> {
            if (p.hasPosition()) { o.writeDouble(p.x()); o.writeDouble(p.y()); o.writeDouble(p.z()); }
            if (p.hasRotation()) { o.writeFloat(p.yaw()); o.writeFloat(p.pitch()); }
            o.writeBoolean(p.onGround());
            return p.hasPosition() ? (p.hasRotation() ? MOVE_POS_ROT : MOVE_POS) : (p.hasRotation() ? MOVE_ROT : MOVE_STATUS);
        });
        codec(Serverbound.PLAYER_ACTION, (id, b) -> {
            var action = enumOf(ServerboundPlayerActionPacket.Action.values(), Types.readVarInt(b), "player_action");
            BlockPos pos = readPos(b);
            BlockFace face = enumOf(BlockFace.values(), b.readUnsignedByte(), "면");
            return new ServerboundPlayerActionPacket(action, pos, face, Types.readVarInt(b));
        }, (p, o) -> {
            Types.writeVarInt(o, p.action().ordinal());
            writePos(o, p.pos());
            o.writeByte(p.face().ordinal());
            Types.writeVarInt(o, p.sequence());
            return id(Serverbound.PLAYER_ACTION);
        });
        codec(Serverbound.PLAYER_COMMAND, (id, b) -> {
            int eid = Types.readVarInt(b);
            var action = enumOf(ServerboundPlayerCommandPacket.Action.values(), Types.readVarInt(b), "player_command");
            return new ServerboundPlayerCommandPacket(eid, action, Types.readVarInt(b));
        }, (p, o) -> {
            Types.writeVarInt(o, p.entityId());
            Types.writeVarInt(o, p.action().ordinal());
            Types.writeVarInt(o, p.data());
            return id(Serverbound.PLAYER_COMMAND);
        });
        codec(Serverbound.SET_CARRIED_ITEM, (id, b) -> new ServerboundSetCarriedItemPacket(b.readShort()),
                (p, o) -> { o.writeShort(p.slot()); return id(Serverbound.SET_CARRIED_ITEM); });
        codec(Serverbound.SIGN_UPDATE, (id, b) -> {
            BlockPos pos = readPos(b);
            boolean front = b.readBoolean();
            List<String> lines = new ArrayList<>(4);
            for (int i = 0; i < 4; i++) lines.add(Types.readString(b, 384));
            return new ServerboundSignUpdatePacket(pos, front, lines);
        }, (p, o) -> {
            writePos(o, p.pos());
            o.writeBoolean(p.frontText());
            for (String l : p.lines()) Types.writeString(o, l);
            return id(Serverbound.SIGN_UPDATE);
        });
        codec(Serverbound.SWING, (id, b) -> new ServerboundSwingPacket(hand(Types.readVarInt(b))),
                (p, o) -> { Types.writeVarInt(o, p.hand().ordinal()); return id(Serverbound.SWING); });
        codec(Serverbound.USE_ITEM, (id, b) -> {
            Hand h = hand(Types.readVarInt(b));
            int seq = Types.readVarInt(b);
            return new ServerboundUseItemPacket(h, seq, b.readFloat(), b.readFloat());
        }, (p, o) -> {
            Types.writeVarInt(o, p.hand().ordinal());
            Types.writeVarInt(o, p.sequence());
            o.writeFloat(p.yaw());
            o.writeFloat(p.pitch());
            return id(Serverbound.USE_ITEM);
        });
        codec(Serverbound.USE_ITEM_ON, (id, b) -> {
            Hand h = hand(Types.readVarInt(b));
            BlockPos pos = readPos(b);
            BlockFace face = enumOf(BlockFace.values(), Types.readVarInt(b), "면");
            float cx = b.readFloat(), cy = b.readFloat(), cz = b.readFloat();
            boolean inside = b.readBoolean();
            return new ServerboundUseItemOnPacket(h, pos, face, cx, cy, cz, inside, Types.readVarInt(b));
        }, (p, o) -> {
            Types.writeVarInt(o, p.hand().ordinal());
            writePos(o, p.pos());
            Types.writeVarInt(o, p.face().ordinal());
            o.writeFloat(p.cursorX());
            o.writeFloat(p.cursorY());
            o.writeFloat(p.cursorZ());
            o.writeBoolean(p.insideBlock());
            Types.writeVarInt(o, p.sequence());
            return id(Serverbound.USE_ITEM_ON);
        });
    }
}
