// 마인크래프트 play 단계 패킷 종류 전체(서버가 아는 모든 패킷). 이름은 바닐라 패킷 이름이고 버전이 바뀌어도 그대로 쓴다.
// 타입 래퍼가 있는 종류는 내용을 읽고 바꿀 수 있고, 나머지(OpaquePacket)는 관찰·취소만 된다.
// 이 목록에 있지만 실행 중인 서버 버전에 없는 패킷이면 PacketService.isSupported 가 false 다.
// 이동 패킷(바닐라 move_player_pos·_pos_rot·_rot·_status_only 넷)은 MOVE_PLAYER 하나로 묶었다 — 보낼 때 알맞은 것을 고른다.
package org.domi.furnace.api.packet;

import org.domi.furnace.api.packet.clientbound.ClientboundAddEntityPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundPlayerInfoRemovePacket;
import org.domi.furnace.api.packet.clientbound.ClientboundPlayerInfoUpdatePacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundAnimatePacket;
import org.domi.furnace.api.packet.clientbound.ClientboundBlockUpdatePacket;
import org.domi.furnace.api.packet.clientbound.ClientboundClearTitlesPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundCustomPayloadPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundDisconnectPacket;
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
import org.domi.furnace.api.packet.serverbound.ServerboundChatCommandPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundChatPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundClientCommandPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundClientInformationPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundCustomPayloadPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundInteractPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundMovePlayerPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundPlayerActionPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundPlayerCommandPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundSetCarriedItemPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundSignUpdatePacket;
import org.domi.furnace.api.packet.serverbound.ServerboundSwingPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundUseItemOnPacket;
import org.domi.furnace.api.packet.serverbound.ServerboundUseItemPacket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PacketTypes {

    private PacketTypes() {}

    private static final Map<String, PacketType<?>> CLIENTBOUND_BY_NAME = new LinkedHashMap<>();
    private static final Map<String, PacketType<?>> SERVERBOUND_BY_NAME = new LinkedHashMap<>();

    /** 서버→클라이언트. */
    public static final class Clientbound {

        private Clientbound() {}

        private static <P extends Packet> PacketType<P> typed(String name, Class<P> c) { return register(PacketDirection.CLIENTBOUND, name, c); }

        private static PacketType<OpaquePacket> opaque(String name) { return register(PacketDirection.CLIENTBOUND, name, OpaquePacket.class); }

        public static final PacketType<OpaquePacket> BUNDLE_DELIMITER = opaque("bundle_delimiter");
        public static final PacketType<ClientboundAddEntityPacket> ADD_ENTITY = typed("add_entity", ClientboundAddEntityPacket.class);
        public static final PacketType<OpaquePacket> ADD_EXPERIENCE_ORB = opaque("add_experience_orb");
        public static final PacketType<ClientboundAnimatePacket> ANIMATE = typed("animate", ClientboundAnimatePacket.class);
        public static final PacketType<OpaquePacket> AWARD_STATS = opaque("award_stats");
        public static final PacketType<OpaquePacket> BLOCK_CHANGED_ACK = opaque("block_changed_ack");
        public static final PacketType<OpaquePacket> BLOCK_DESTRUCTION = opaque("block_destruction");
        public static final PacketType<OpaquePacket> BLOCK_ENTITY_DATA = opaque("block_entity_data");
        public static final PacketType<OpaquePacket> BLOCK_EVENT = opaque("block_event");
        public static final PacketType<ClientboundBlockUpdatePacket> BLOCK_UPDATE = typed("block_update", ClientboundBlockUpdatePacket.class);
        public static final PacketType<OpaquePacket> BOSS_EVENT = opaque("boss_event");
        public static final PacketType<OpaquePacket> CHANGE_DIFFICULTY = opaque("change_difficulty");
        public static final PacketType<OpaquePacket> CHUNK_BATCH_FINISHED = opaque("chunk_batch_finished");
        public static final PacketType<OpaquePacket> CHUNK_BATCH_START = opaque("chunk_batch_start");
        public static final PacketType<OpaquePacket> CHUNKS_BIOMES = opaque("chunks_biomes");
        public static final PacketType<ClientboundClearTitlesPacket> CLEAR_TITLES = typed("clear_titles", ClientboundClearTitlesPacket.class);
        public static final PacketType<OpaquePacket> COMMAND_SUGGESTIONS = opaque("command_suggestions");
        public static final PacketType<OpaquePacket> COMMANDS = opaque("commands");
        public static final PacketType<OpaquePacket> CONTAINER_CLOSE = opaque("container_close");
        public static final PacketType<OpaquePacket> CONTAINER_SET_CONTENT = opaque("container_set_content");
        public static final PacketType<OpaquePacket> CONTAINER_SET_DATA = opaque("container_set_data");
        public static final PacketType<OpaquePacket> CONTAINER_SET_SLOT = opaque("container_set_slot");
        public static final PacketType<OpaquePacket> COOKIE_REQUEST = opaque("cookie_request");
        public static final PacketType<OpaquePacket> COOLDOWN = opaque("cooldown");
        public static final PacketType<OpaquePacket> CUSTOM_CHAT_COMPLETIONS = opaque("custom_chat_completions");
        public static final PacketType<ClientboundCustomPayloadPacket> CUSTOM_PAYLOAD = typed("custom_payload", ClientboundCustomPayloadPacket.class);
        public static final PacketType<OpaquePacket> DAMAGE_EVENT = opaque("damage_event");
        public static final PacketType<OpaquePacket> DEBUG_SAMPLE = opaque("debug_sample");
        public static final PacketType<OpaquePacket> DELETE_CHAT = opaque("delete_chat");
        public static final PacketType<ClientboundDisconnectPacket> DISCONNECT = typed("disconnect", ClientboundDisconnectPacket.class);
        public static final PacketType<OpaquePacket> DISGUISED_CHAT = opaque("disguised_chat");
        public static final PacketType<OpaquePacket> ENTITY_EVENT = opaque("entity_event");
        public static final PacketType<OpaquePacket> EXPLODE = opaque("explode");
        public static final PacketType<OpaquePacket> FORGET_LEVEL_CHUNK = opaque("forget_level_chunk");
        public static final PacketType<OpaquePacket> GAME_EVENT = opaque("game_event");
        public static final PacketType<OpaquePacket> HORSE_SCREEN_OPEN = opaque("horse_screen_open");
        public static final PacketType<OpaquePacket> HURT_ANIMATION = opaque("hurt_animation");
        public static final PacketType<OpaquePacket> INITIALIZE_BORDER = opaque("initialize_border");
        public static final PacketType<OpaquePacket> KEEP_ALIVE = opaque("keep_alive");
        public static final PacketType<OpaquePacket> LEVEL_CHUNK_WITH_LIGHT = opaque("level_chunk_with_light");
        public static final PacketType<OpaquePacket> LEVEL_EVENT = opaque("level_event");
        public static final PacketType<OpaquePacket> LEVEL_PARTICLES = opaque("level_particles");
        public static final PacketType<OpaquePacket> LIGHT_UPDATE = opaque("light_update");
        public static final PacketType<OpaquePacket> LOGIN = opaque("login");
        public static final PacketType<OpaquePacket> MAP_ITEM_DATA = opaque("map_item_data");
        public static final PacketType<OpaquePacket> MERCHANT_OFFERS = opaque("merchant_offers");
        public static final PacketType<OpaquePacket> MOVE_ENTITY_POS = opaque("move_entity_pos");
        public static final PacketType<OpaquePacket> MOVE_ENTITY_POS_ROT = opaque("move_entity_pos_rot");
        public static final PacketType<OpaquePacket> MOVE_ENTITY_ROT = opaque("move_entity_rot");
        public static final PacketType<OpaquePacket> MOVE_VEHICLE = opaque("move_vehicle");
        public static final PacketType<OpaquePacket> OPEN_BOOK = opaque("open_book");
        public static final PacketType<OpaquePacket> OPEN_SCREEN = opaque("open_screen");
        public static final PacketType<OpaquePacket> OPEN_SIGN_EDITOR = opaque("open_sign_editor");
        public static final PacketType<OpaquePacket> PING = opaque("ping");
        public static final PacketType<OpaquePacket> PONG_RESPONSE = opaque("pong_response");
        public static final PacketType<OpaquePacket> PLACE_GHOST_RECIPE = opaque("place_ghost_recipe");
        public static final PacketType<OpaquePacket> PLAYER_ABILITIES = opaque("player_abilities");
        public static final PacketType<OpaquePacket> PLAYER_CHAT = opaque("player_chat");
        public static final PacketType<OpaquePacket> PLAYER_COMBAT_END = opaque("player_combat_end");
        public static final PacketType<OpaquePacket> PLAYER_COMBAT_ENTER = opaque("player_combat_enter");
        public static final PacketType<OpaquePacket> PLAYER_COMBAT_KILL = opaque("player_combat_kill");
        public static final PacketType<ClientboundPlayerInfoRemovePacket> PLAYER_INFO_REMOVE = typed("player_info_remove", ClientboundPlayerInfoRemovePacket.class);
        public static final PacketType<ClientboundPlayerInfoUpdatePacket> PLAYER_INFO_UPDATE = typed("player_info_update", ClientboundPlayerInfoUpdatePacket.class);
        public static final PacketType<OpaquePacket> PLAYER_LOOK_AT = opaque("player_look_at");
        public static final PacketType<OpaquePacket> PLAYER_POSITION = opaque("player_position");
        public static final PacketType<OpaquePacket> RECIPE = opaque("recipe");
        public static final PacketType<ClientboundRemoveEntitiesPacket> REMOVE_ENTITIES = typed("remove_entities", ClientboundRemoveEntitiesPacket.class);
        public static final PacketType<OpaquePacket> REMOVE_MOB_EFFECT = opaque("remove_mob_effect");
        public static final PacketType<OpaquePacket> RESET_SCORE = opaque("reset_score");
        public static final PacketType<OpaquePacket> RESOURCE_PACK_POP = opaque("resource_pack_pop");
        public static final PacketType<OpaquePacket> RESOURCE_PACK_PUSH = opaque("resource_pack_push");
        public static final PacketType<OpaquePacket> RESPAWN = opaque("respawn");
        public static final PacketType<ClientboundRotateHeadPacket> ROTATE_HEAD = typed("rotate_head", ClientboundRotateHeadPacket.class);
        public static final PacketType<OpaquePacket> SECTION_BLOCKS_UPDATE = opaque("section_blocks_update");
        public static final PacketType<OpaquePacket> SELECT_ADVANCEMENTS_TAB = opaque("select_advancements_tab");
        public static final PacketType<OpaquePacket> SERVER_DATA = opaque("server_data");
        public static final PacketType<ClientboundSetActionBarTextPacket> SET_ACTION_BAR_TEXT = typed("set_action_bar_text", ClientboundSetActionBarTextPacket.class);
        public static final PacketType<OpaquePacket> SET_BORDER_CENTER = opaque("set_border_center");
        public static final PacketType<OpaquePacket> SET_BORDER_LERP_SIZE = opaque("set_border_lerp_size");
        public static final PacketType<OpaquePacket> SET_BORDER_SIZE = opaque("set_border_size");
        public static final PacketType<OpaquePacket> SET_BORDER_WARNING_DELAY = opaque("set_border_warning_delay");
        public static final PacketType<OpaquePacket> SET_BORDER_WARNING_DISTANCE = opaque("set_border_warning_distance");
        public static final PacketType<OpaquePacket> SET_CAMERA = opaque("set_camera");
        public static final PacketType<OpaquePacket> SET_CARRIED_ITEM = opaque("set_carried_item");
        public static final PacketType<OpaquePacket> SET_CHUNK_CACHE_CENTER = opaque("set_chunk_cache_center");
        public static final PacketType<OpaquePacket> SET_CHUNK_CACHE_RADIUS = opaque("set_chunk_cache_radius");
        public static final PacketType<OpaquePacket> SET_DEFAULT_SPAWN_POSITION = opaque("set_default_spawn_position");
        public static final PacketType<OpaquePacket> SET_DISPLAY_OBJECTIVE = opaque("set_display_objective");
        public static final PacketType<ClientboundSetEntityDataPacket> SET_ENTITY_DATA = typed("set_entity_data", ClientboundSetEntityDataPacket.class);
        public static final PacketType<OpaquePacket> SET_ENTITY_LINK = opaque("set_entity_link");
        public static final PacketType<ClientboundSetEntityMotionPacket> SET_ENTITY_MOTION = typed("set_entity_motion", ClientboundSetEntityMotionPacket.class);
        public static final PacketType<OpaquePacket> SET_EQUIPMENT = opaque("set_equipment");
        public static final PacketType<OpaquePacket> SET_EXPERIENCE = opaque("set_experience");
        public static final PacketType<OpaquePacket> SET_HEALTH = opaque("set_health");
        public static final PacketType<OpaquePacket> SET_OBJECTIVE = opaque("set_objective");
        public static final PacketType<OpaquePacket> SET_PASSENGERS = opaque("set_passengers");
        public static final PacketType<OpaquePacket> SET_PLAYER_TEAM = opaque("set_player_team");
        public static final PacketType<OpaquePacket> SET_SCORE = opaque("set_score");
        public static final PacketType<OpaquePacket> SET_SIMULATION_DISTANCE = opaque("set_simulation_distance");
        public static final PacketType<ClientboundSetSubtitleTextPacket> SET_SUBTITLE_TEXT = typed("set_subtitle_text", ClientboundSetSubtitleTextPacket.class);
        public static final PacketType<ClientboundSetTimePacket> SET_TIME = typed("set_time", ClientboundSetTimePacket.class);
        public static final PacketType<ClientboundSetTitleTextPacket> SET_TITLE_TEXT = typed("set_title_text", ClientboundSetTitleTextPacket.class);
        public static final PacketType<ClientboundSetTitlesAnimationPacket> SET_TITLES_ANIMATION = typed("set_titles_animation", ClientboundSetTitlesAnimationPacket.class);
        public static final PacketType<OpaquePacket> SOUND_ENTITY = opaque("sound_entity");
        public static final PacketType<ClientboundSoundPacket> SOUND = typed("sound", ClientboundSoundPacket.class);
        public static final PacketType<OpaquePacket> START_CONFIGURATION = opaque("start_configuration");
        public static final PacketType<OpaquePacket> STOP_SOUND = opaque("stop_sound");
        public static final PacketType<OpaquePacket> STORE_COOKIE = opaque("store_cookie");
        public static final PacketType<ClientboundSystemChatPacket> SYSTEM_CHAT = typed("system_chat", ClientboundSystemChatPacket.class);
        public static final PacketType<OpaquePacket> TAB_LIST = opaque("tab_list");
        public static final PacketType<OpaquePacket> TAG_QUERY = opaque("tag_query");
        public static final PacketType<OpaquePacket> TAKE_ITEM_ENTITY = opaque("take_item_entity");
        public static final PacketType<ClientboundTeleportEntityPacket> TELEPORT_ENTITY = typed("teleport_entity", ClientboundTeleportEntityPacket.class);
        public static final PacketType<OpaquePacket> TICKING_STATE = opaque("ticking_state");
        public static final PacketType<OpaquePacket> TICKING_STEP = opaque("ticking_step");
        public static final PacketType<OpaquePacket> TRANSFER = opaque("transfer");
        public static final PacketType<OpaquePacket> UPDATE_ADVANCEMENTS = opaque("update_advancements");
        public static final PacketType<OpaquePacket> UPDATE_ATTRIBUTES = opaque("update_attributes");
        public static final PacketType<OpaquePacket> UPDATE_MOB_EFFECT = opaque("update_mob_effect");
        public static final PacketType<OpaquePacket> UPDATE_RECIPES = opaque("update_recipes");
        public static final PacketType<OpaquePacket> UPDATE_TAGS = opaque("update_tags");
        public static final PacketType<OpaquePacket> PROJECTILE_POWER = opaque("projectile_power");
        public static final PacketType<OpaquePacket> CUSTOM_REPORT_DETAILS = opaque("custom_report_details");
        public static final PacketType<OpaquePacket> SERVER_LINKS = opaque("server_links");
    }

    /** 클라이언트→서버. */
    public static final class Serverbound {

        private Serverbound() {}

        private static <P extends Packet> PacketType<P> typed(String name, Class<P> c) { return register(PacketDirection.SERVERBOUND, name, c); }

        private static PacketType<OpaquePacket> opaque(String name) { return register(PacketDirection.SERVERBOUND, name, OpaquePacket.class); }

        public static final PacketType<OpaquePacket> ACCEPT_TELEPORTATION = opaque("accept_teleportation");
        public static final PacketType<OpaquePacket> BLOCK_ENTITY_TAG_QUERY = opaque("block_entity_tag_query");
        public static final PacketType<OpaquePacket> CHANGE_DIFFICULTY = opaque("change_difficulty");
        public static final PacketType<OpaquePacket> CHAT_ACK = opaque("chat_ack");
        public static final PacketType<ServerboundChatCommandPacket> CHAT_COMMAND = typed("chat_command", ServerboundChatCommandPacket.class);
        public static final PacketType<OpaquePacket> CHAT_COMMAND_SIGNED = opaque("chat_command_signed");
        public static final PacketType<ServerboundChatPacket> CHAT = typed("chat", ServerboundChatPacket.class);
        public static final PacketType<OpaquePacket> CHAT_SESSION_UPDATE = opaque("chat_session_update");
        public static final PacketType<OpaquePacket> CHUNK_BATCH_RECEIVED = opaque("chunk_batch_received");
        public static final PacketType<ServerboundClientCommandPacket> CLIENT_COMMAND = typed("client_command", ServerboundClientCommandPacket.class);
        public static final PacketType<ServerboundClientInformationPacket> CLIENT_INFORMATION = typed("client_information", ServerboundClientInformationPacket.class);
        public static final PacketType<OpaquePacket> COMMAND_SUGGESTION = opaque("command_suggestion");
        public static final PacketType<OpaquePacket> CONFIGURATION_ACKNOWLEDGED = opaque("configuration_acknowledged");
        public static final PacketType<OpaquePacket> CONTAINER_BUTTON_CLICK = opaque("container_button_click");
        public static final PacketType<OpaquePacket> CONTAINER_CLICK = opaque("container_click");
        public static final PacketType<OpaquePacket> CONTAINER_CLOSE = opaque("container_close");
        public static final PacketType<OpaquePacket> CONTAINER_SLOT_STATE_CHANGED = opaque("container_slot_state_changed");
        public static final PacketType<OpaquePacket> COOKIE_RESPONSE = opaque("cookie_response");
        public static final PacketType<ServerboundCustomPayloadPacket> CUSTOM_PAYLOAD = typed("custom_payload", ServerboundCustomPayloadPacket.class);
        public static final PacketType<OpaquePacket> DEBUG_SAMPLE_SUBSCRIPTION = opaque("debug_sample_subscription");
        public static final PacketType<OpaquePacket> EDIT_BOOK = opaque("edit_book");
        public static final PacketType<OpaquePacket> ENTITY_TAG_QUERY = opaque("entity_tag_query");
        public static final PacketType<ServerboundInteractPacket> INTERACT = typed("interact", ServerboundInteractPacket.class);
        public static final PacketType<OpaquePacket> JIGSAW_GENERATE = opaque("jigsaw_generate");
        public static final PacketType<OpaquePacket> KEEP_ALIVE = opaque("keep_alive");
        public static final PacketType<OpaquePacket> LOCK_DIFFICULTY = opaque("lock_difficulty");
        public static final PacketType<ServerboundMovePlayerPacket> MOVE_PLAYER = typed("move_player", ServerboundMovePlayerPacket.class);
        public static final PacketType<OpaquePacket> MOVE_VEHICLE = opaque("move_vehicle");
        public static final PacketType<OpaquePacket> PADDLE_BOAT = opaque("paddle_boat");
        public static final PacketType<OpaquePacket> PICK_ITEM = opaque("pick_item");
        public static final PacketType<OpaquePacket> PING_REQUEST = opaque("ping_request");
        public static final PacketType<OpaquePacket> PLACE_RECIPE = opaque("place_recipe");
        public static final PacketType<OpaquePacket> PLAYER_ABILITIES = opaque("player_abilities");
        public static final PacketType<ServerboundPlayerActionPacket> PLAYER_ACTION = typed("player_action", ServerboundPlayerActionPacket.class);
        public static final PacketType<ServerboundPlayerCommandPacket> PLAYER_COMMAND = typed("player_command", ServerboundPlayerCommandPacket.class);
        public static final PacketType<OpaquePacket> PLAYER_INPUT = opaque("player_input");
        public static final PacketType<OpaquePacket> PONG = opaque("pong");
        public static final PacketType<OpaquePacket> RECIPE_BOOK_CHANGE_SETTINGS = opaque("recipe_book_change_settings");
        public static final PacketType<OpaquePacket> RECIPE_BOOK_SEEN_RECIPE = opaque("recipe_book_seen_recipe");
        public static final PacketType<OpaquePacket> RENAME_ITEM = opaque("rename_item");
        public static final PacketType<OpaquePacket> RESOURCE_PACK = opaque("resource_pack");
        public static final PacketType<OpaquePacket> SEEN_ADVANCEMENTS = opaque("seen_advancements");
        public static final PacketType<OpaquePacket> SELECT_TRADE = opaque("select_trade");
        public static final PacketType<OpaquePacket> SET_BEACON = opaque("set_beacon");
        public static final PacketType<ServerboundSetCarriedItemPacket> SET_CARRIED_ITEM = typed("set_carried_item", ServerboundSetCarriedItemPacket.class);
        public static final PacketType<OpaquePacket> SET_COMMAND_BLOCK = opaque("set_command_block");
        public static final PacketType<OpaquePacket> SET_COMMAND_MINECART = opaque("set_command_minecart");
        public static final PacketType<OpaquePacket> SET_CREATIVE_MODE_SLOT = opaque("set_creative_mode_slot");
        public static final PacketType<OpaquePacket> SET_JIGSAW_BLOCK = opaque("set_jigsaw_block");
        public static final PacketType<OpaquePacket> SET_STRUCTURE_BLOCK = opaque("set_structure_block");
        public static final PacketType<ServerboundSignUpdatePacket> SIGN_UPDATE = typed("sign_update", ServerboundSignUpdatePacket.class);
        public static final PacketType<ServerboundSwingPacket> SWING = typed("swing", ServerboundSwingPacket.class);
        public static final PacketType<OpaquePacket> TELEPORT_TO_ENTITY = opaque("teleport_to_entity");
        public static final PacketType<ServerboundUseItemOnPacket> USE_ITEM_ON = typed("use_item_on", ServerboundUseItemOnPacket.class);
        public static final PacketType<ServerboundUseItemPacket> USE_ITEM = typed("use_item", ServerboundUseItemPacket.class);
    }

    private static <P extends Packet> PacketType<P> register(PacketDirection d, String name, Class<P> c) {
        PacketType<P> t = new PacketType<>(d, "minecraft:" + name, c);
        Map<String, PacketType<?>> m = d == PacketDirection.CLIENTBOUND ? CLIENTBOUND_BY_NAME : SERVERBOUND_BY_NAME;
        if (m.putIfAbsent(t.name(), t) != null) throw new IllegalStateException("중복 패킷 이름: " + t);
        return t;
    }

    private static void init() {
        // 중첩 클래스 초기화 — 이름 조회 전에 상수가 모두 등록되게
        Object a = Clientbound.ADD_ENTITY, b = Serverbound.CHAT;
    }

    /**
     * 이름으로 찾는다(없으면 null). "system_chat" 처럼 네임스페이스를 빼도 된다.
     */
    public static PacketType<?> byName(PacketDirection direction, String name) {
        init();
        String key = name.indexOf(':') < 0 ? "minecraft:" + name : name;
        return (direction == PacketDirection.CLIENTBOUND ? CLIENTBOUND_BY_NAME : SERVERBOUND_BY_NAME).get(key);
    }

    /** 그 방향의 모든 종류(바닐라 순서). */
    public static List<PacketType<?>> all(PacketDirection direction) {
        init();
        return Collections.unmodifiableList(new ArrayList<>((direction == PacketDirection.CLIENTBOUND ? CLIENTBOUND_BY_NAME : SERVERBOUND_BY_NAME).values()));
    }
}
