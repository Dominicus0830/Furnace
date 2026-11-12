// 마인크래프트 play 단계 패킷 종류 전체(서버가 아는 모든 패킷). 이름은 바닐라 패킷 이름이고 버전이 바뀌어도 그대로 쓴다.
// 타입 레코드가 있는 종류는 레코드로, 나머지는 GenericPacket(이름 붙은 필드)으로 읽고 바꾼다. 서버가 그 버전에서 풀지 못하는 종류는 관찰·취소만(PacketService.isReadable).
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

        private static PacketType<GenericPacket> generic(String name) { return register(PacketDirection.CLIENTBOUND, name, GenericPacket.class); }

        public static final PacketType<GenericPacket> BUNDLE_DELIMITER = generic("bundle_delimiter");
        public static final PacketType<ClientboundAddEntityPacket> ADD_ENTITY = typed("add_entity", ClientboundAddEntityPacket.class);
        public static final PacketType<GenericPacket> ADD_EXPERIENCE_ORB = generic("add_experience_orb");
        public static final PacketType<ClientboundAnimatePacket> ANIMATE = typed("animate", ClientboundAnimatePacket.class);
        public static final PacketType<GenericPacket> AWARD_STATS = generic("award_stats");
        public static final PacketType<GenericPacket> BLOCK_CHANGED_ACK = generic("block_changed_ack");
        public static final PacketType<GenericPacket> BLOCK_DESTRUCTION = generic("block_destruction");
        public static final PacketType<GenericPacket> BLOCK_ENTITY_DATA = generic("block_entity_data");
        public static final PacketType<GenericPacket> BLOCK_EVENT = generic("block_event");
        public static final PacketType<ClientboundBlockUpdatePacket> BLOCK_UPDATE = typed("block_update", ClientboundBlockUpdatePacket.class);
        public static final PacketType<GenericPacket> BOSS_EVENT = generic("boss_event");
        public static final PacketType<GenericPacket> CHANGE_DIFFICULTY = generic("change_difficulty");
        public static final PacketType<GenericPacket> CHUNK_BATCH_FINISHED = generic("chunk_batch_finished");
        public static final PacketType<GenericPacket> CHUNK_BATCH_START = generic("chunk_batch_start");
        public static final PacketType<GenericPacket> CHUNKS_BIOMES = generic("chunks_biomes");
        public static final PacketType<ClientboundClearTitlesPacket> CLEAR_TITLES = typed("clear_titles", ClientboundClearTitlesPacket.class);
        public static final PacketType<GenericPacket> COMMAND_SUGGESTIONS = generic("command_suggestions");
        public static final PacketType<GenericPacket> COMMANDS = generic("commands");
        public static final PacketType<GenericPacket> CONTAINER_CLOSE = generic("container_close");
        public static final PacketType<GenericPacket> CONTAINER_SET_CONTENT = generic("container_set_content");
        public static final PacketType<GenericPacket> CONTAINER_SET_DATA = generic("container_set_data");
        public static final PacketType<GenericPacket> CONTAINER_SET_SLOT = generic("container_set_slot");
        public static final PacketType<GenericPacket> COOKIE_REQUEST = generic("cookie_request");
        public static final PacketType<GenericPacket> COOLDOWN = generic("cooldown");
        public static final PacketType<GenericPacket> CUSTOM_CHAT_COMPLETIONS = generic("custom_chat_completions");
        public static final PacketType<ClientboundCustomPayloadPacket> CUSTOM_PAYLOAD = typed("custom_payload", ClientboundCustomPayloadPacket.class);
        public static final PacketType<GenericPacket> DAMAGE_EVENT = generic("damage_event");
        public static final PacketType<GenericPacket> DEBUG_SAMPLE = generic("debug_sample");
        public static final PacketType<GenericPacket> DELETE_CHAT = generic("delete_chat");
        public static final PacketType<ClientboundDisconnectPacket> DISCONNECT = typed("disconnect", ClientboundDisconnectPacket.class);
        public static final PacketType<GenericPacket> DISGUISED_CHAT = generic("disguised_chat");
        public static final PacketType<GenericPacket> ENTITY_EVENT = generic("entity_event");
        public static final PacketType<GenericPacket> EXPLODE = generic("explode");
        public static final PacketType<GenericPacket> FORGET_LEVEL_CHUNK = generic("forget_level_chunk");
        public static final PacketType<GenericPacket> GAME_EVENT = generic("game_event");
        public static final PacketType<GenericPacket> HORSE_SCREEN_OPEN = generic("horse_screen_open");
        public static final PacketType<GenericPacket> HURT_ANIMATION = generic("hurt_animation");
        public static final PacketType<GenericPacket> INITIALIZE_BORDER = generic("initialize_border");
        public static final PacketType<GenericPacket> KEEP_ALIVE = generic("keep_alive");
        public static final PacketType<GenericPacket> LEVEL_CHUNK_WITH_LIGHT = generic("level_chunk_with_light");
        public static final PacketType<GenericPacket> LEVEL_EVENT = generic("level_event");
        public static final PacketType<GenericPacket> LEVEL_PARTICLES = generic("level_particles");
        public static final PacketType<GenericPacket> LIGHT_UPDATE = generic("light_update");
        public static final PacketType<GenericPacket> LOGIN = generic("login");
        public static final PacketType<GenericPacket> MAP_ITEM_DATA = generic("map_item_data");
        public static final PacketType<GenericPacket> MERCHANT_OFFERS = generic("merchant_offers");
        public static final PacketType<GenericPacket> MOVE_ENTITY_POS = generic("move_entity_pos");
        public static final PacketType<GenericPacket> MOVE_ENTITY_POS_ROT = generic("move_entity_pos_rot");
        public static final PacketType<GenericPacket> MOVE_ENTITY_ROT = generic("move_entity_rot");
        public static final PacketType<GenericPacket> MOVE_VEHICLE = generic("move_vehicle");
        public static final PacketType<GenericPacket> OPEN_BOOK = generic("open_book");
        public static final PacketType<GenericPacket> OPEN_SCREEN = generic("open_screen");
        public static final PacketType<GenericPacket> OPEN_SIGN_EDITOR = generic("open_sign_editor");
        public static final PacketType<GenericPacket> PING = generic("ping");
        public static final PacketType<GenericPacket> PONG_RESPONSE = generic("pong_response");
        public static final PacketType<GenericPacket> PLACE_GHOST_RECIPE = generic("place_ghost_recipe");
        public static final PacketType<GenericPacket> PLAYER_ABILITIES = generic("player_abilities");
        public static final PacketType<GenericPacket> PLAYER_CHAT = generic("player_chat");
        public static final PacketType<GenericPacket> PLAYER_COMBAT_END = generic("player_combat_end");
        public static final PacketType<GenericPacket> PLAYER_COMBAT_ENTER = generic("player_combat_enter");
        public static final PacketType<GenericPacket> PLAYER_COMBAT_KILL = generic("player_combat_kill");
        public static final PacketType<ClientboundPlayerInfoRemovePacket> PLAYER_INFO_REMOVE = typed("player_info_remove", ClientboundPlayerInfoRemovePacket.class);
        public static final PacketType<ClientboundPlayerInfoUpdatePacket> PLAYER_INFO_UPDATE = typed("player_info_update", ClientboundPlayerInfoUpdatePacket.class);
        public static final PacketType<GenericPacket> PLAYER_LOOK_AT = generic("player_look_at");
        public static final PacketType<GenericPacket> PLAYER_POSITION = generic("player_position");
        public static final PacketType<GenericPacket> RECIPE = generic("recipe");
        public static final PacketType<ClientboundRemoveEntitiesPacket> REMOVE_ENTITIES = typed("remove_entities", ClientboundRemoveEntitiesPacket.class);
        public static final PacketType<GenericPacket> REMOVE_MOB_EFFECT = generic("remove_mob_effect");
        public static final PacketType<GenericPacket> RESET_SCORE = generic("reset_score");
        public static final PacketType<GenericPacket> RESOURCE_PACK_POP = generic("resource_pack_pop");
        public static final PacketType<GenericPacket> RESOURCE_PACK_PUSH = generic("resource_pack_push");
        public static final PacketType<GenericPacket> RESPAWN = generic("respawn");
        public static final PacketType<ClientboundRotateHeadPacket> ROTATE_HEAD = typed("rotate_head", ClientboundRotateHeadPacket.class);
        public static final PacketType<GenericPacket> SECTION_BLOCKS_UPDATE = generic("section_blocks_update");
        public static final PacketType<GenericPacket> SELECT_ADVANCEMENTS_TAB = generic("select_advancements_tab");
        public static final PacketType<GenericPacket> SERVER_DATA = generic("server_data");
        public static final PacketType<ClientboundSetActionBarTextPacket> SET_ACTION_BAR_TEXT = typed("set_action_bar_text", ClientboundSetActionBarTextPacket.class);
        public static final PacketType<GenericPacket> SET_BORDER_CENTER = generic("set_border_center");
        public static final PacketType<GenericPacket> SET_BORDER_LERP_SIZE = generic("set_border_lerp_size");
        public static final PacketType<GenericPacket> SET_BORDER_SIZE = generic("set_border_size");
        public static final PacketType<GenericPacket> SET_BORDER_WARNING_DELAY = generic("set_border_warning_delay");
        public static final PacketType<GenericPacket> SET_BORDER_WARNING_DISTANCE = generic("set_border_warning_distance");
        public static final PacketType<GenericPacket> SET_CAMERA = generic("set_camera");
        public static final PacketType<GenericPacket> SET_CARRIED_ITEM = generic("set_carried_item");
        public static final PacketType<GenericPacket> SET_CHUNK_CACHE_CENTER = generic("set_chunk_cache_center");
        public static final PacketType<GenericPacket> SET_CHUNK_CACHE_RADIUS = generic("set_chunk_cache_radius");
        public static final PacketType<GenericPacket> SET_DEFAULT_SPAWN_POSITION = generic("set_default_spawn_position");
        public static final PacketType<GenericPacket> SET_DISPLAY_OBJECTIVE = generic("set_display_objective");
        public static final PacketType<ClientboundSetEntityDataPacket> SET_ENTITY_DATA = typed("set_entity_data", ClientboundSetEntityDataPacket.class);
        public static final PacketType<GenericPacket> SET_ENTITY_LINK = generic("set_entity_link");
        public static final PacketType<ClientboundSetEntityMotionPacket> SET_ENTITY_MOTION = typed("set_entity_motion", ClientboundSetEntityMotionPacket.class);
        public static final PacketType<GenericPacket> SET_EQUIPMENT = generic("set_equipment");
        public static final PacketType<GenericPacket> SET_EXPERIENCE = generic("set_experience");
        public static final PacketType<GenericPacket> SET_HEALTH = generic("set_health");
        public static final PacketType<GenericPacket> SET_OBJECTIVE = generic("set_objective");
        public static final PacketType<GenericPacket> SET_PASSENGERS = generic("set_passengers");
        public static final PacketType<GenericPacket> SET_PLAYER_TEAM = generic("set_player_team");
        public static final PacketType<GenericPacket> SET_SCORE = generic("set_score");
        public static final PacketType<GenericPacket> SET_SIMULATION_DISTANCE = generic("set_simulation_distance");
        public static final PacketType<ClientboundSetSubtitleTextPacket> SET_SUBTITLE_TEXT = typed("set_subtitle_text", ClientboundSetSubtitleTextPacket.class);
        public static final PacketType<ClientboundSetTimePacket> SET_TIME = typed("set_time", ClientboundSetTimePacket.class);
        public static final PacketType<ClientboundSetTitleTextPacket> SET_TITLE_TEXT = typed("set_title_text", ClientboundSetTitleTextPacket.class);
        public static final PacketType<ClientboundSetTitlesAnimationPacket> SET_TITLES_ANIMATION = typed("set_titles_animation", ClientboundSetTitlesAnimationPacket.class);
        public static final PacketType<GenericPacket> SOUND_ENTITY = generic("sound_entity");
        public static final PacketType<ClientboundSoundPacket> SOUND = typed("sound", ClientboundSoundPacket.class);
        public static final PacketType<GenericPacket> START_CONFIGURATION = generic("start_configuration");
        public static final PacketType<GenericPacket> STOP_SOUND = generic("stop_sound");
        public static final PacketType<GenericPacket> STORE_COOKIE = generic("store_cookie");
        public static final PacketType<ClientboundSystemChatPacket> SYSTEM_CHAT = typed("system_chat", ClientboundSystemChatPacket.class);
        public static final PacketType<GenericPacket> TAB_LIST = generic("tab_list");
        public static final PacketType<GenericPacket> TAG_QUERY = generic("tag_query");
        public static final PacketType<GenericPacket> TAKE_ITEM_ENTITY = generic("take_item_entity");
        public static final PacketType<ClientboundTeleportEntityPacket> TELEPORT_ENTITY = typed("teleport_entity", ClientboundTeleportEntityPacket.class);
        public static final PacketType<GenericPacket> TICKING_STATE = generic("ticking_state");
        public static final PacketType<GenericPacket> TICKING_STEP = generic("ticking_step");
        public static final PacketType<GenericPacket> TRANSFER = generic("transfer");
        public static final PacketType<GenericPacket> UPDATE_ADVANCEMENTS = generic("update_advancements");
        public static final PacketType<GenericPacket> UPDATE_ATTRIBUTES = generic("update_attributes");
        public static final PacketType<GenericPacket> UPDATE_MOB_EFFECT = generic("update_mob_effect");
        public static final PacketType<GenericPacket> UPDATE_RECIPES = generic("update_recipes");
        public static final PacketType<GenericPacket> UPDATE_TAGS = generic("update_tags");
        public static final PacketType<GenericPacket> PROJECTILE_POWER = generic("projectile_power");
        public static final PacketType<GenericPacket> CUSTOM_REPORT_DETAILS = generic("custom_report_details");
        public static final PacketType<GenericPacket> SERVER_LINKS = generic("server_links");
    }

    /** 클라이언트→서버. */
    public static final class Serverbound {

        private Serverbound() {}

        private static <P extends Packet> PacketType<P> typed(String name, Class<P> c) { return register(PacketDirection.SERVERBOUND, name, c); }

        private static PacketType<GenericPacket> generic(String name) { return register(PacketDirection.SERVERBOUND, name, GenericPacket.class); }

        public static final PacketType<GenericPacket> ACCEPT_TELEPORTATION = generic("accept_teleportation");
        public static final PacketType<GenericPacket> BLOCK_ENTITY_TAG_QUERY = generic("block_entity_tag_query");
        public static final PacketType<GenericPacket> CHANGE_DIFFICULTY = generic("change_difficulty");
        public static final PacketType<GenericPacket> CHAT_ACK = generic("chat_ack");
        public static final PacketType<ServerboundChatCommandPacket> CHAT_COMMAND = typed("chat_command", ServerboundChatCommandPacket.class);
        public static final PacketType<GenericPacket> CHAT_COMMAND_SIGNED = generic("chat_command_signed");
        public static final PacketType<ServerboundChatPacket> CHAT = typed("chat", ServerboundChatPacket.class);
        public static final PacketType<GenericPacket> CHAT_SESSION_UPDATE = generic("chat_session_update");
        public static final PacketType<GenericPacket> CHUNK_BATCH_RECEIVED = generic("chunk_batch_received");
        public static final PacketType<ServerboundClientCommandPacket> CLIENT_COMMAND = typed("client_command", ServerboundClientCommandPacket.class);
        public static final PacketType<ServerboundClientInformationPacket> CLIENT_INFORMATION = typed("client_information", ServerboundClientInformationPacket.class);
        public static final PacketType<GenericPacket> COMMAND_SUGGESTION = generic("command_suggestion");
        public static final PacketType<GenericPacket> CONFIGURATION_ACKNOWLEDGED = generic("configuration_acknowledged");
        public static final PacketType<GenericPacket> CONTAINER_BUTTON_CLICK = generic("container_button_click");
        public static final PacketType<GenericPacket> CONTAINER_CLICK = generic("container_click");
        public static final PacketType<GenericPacket> CONTAINER_CLOSE = generic("container_close");
        public static final PacketType<GenericPacket> CONTAINER_SLOT_STATE_CHANGED = generic("container_slot_state_changed");
        public static final PacketType<GenericPacket> COOKIE_RESPONSE = generic("cookie_response");
        public static final PacketType<ServerboundCustomPayloadPacket> CUSTOM_PAYLOAD = typed("custom_payload", ServerboundCustomPayloadPacket.class);
        public static final PacketType<GenericPacket> DEBUG_SAMPLE_SUBSCRIPTION = generic("debug_sample_subscription");
        public static final PacketType<GenericPacket> EDIT_BOOK = generic("edit_book");
        public static final PacketType<GenericPacket> ENTITY_TAG_QUERY = generic("entity_tag_query");
        public static final PacketType<ServerboundInteractPacket> INTERACT = typed("interact", ServerboundInteractPacket.class);
        public static final PacketType<GenericPacket> JIGSAW_GENERATE = generic("jigsaw_generate");
        public static final PacketType<GenericPacket> KEEP_ALIVE = generic("keep_alive");
        public static final PacketType<GenericPacket> LOCK_DIFFICULTY = generic("lock_difficulty");
        public static final PacketType<ServerboundMovePlayerPacket> MOVE_PLAYER = typed("move_player", ServerboundMovePlayerPacket.class);
        public static final PacketType<GenericPacket> MOVE_VEHICLE = generic("move_vehicle");
        public static final PacketType<GenericPacket> PADDLE_BOAT = generic("paddle_boat");
        public static final PacketType<GenericPacket> PICK_ITEM = generic("pick_item");
        public static final PacketType<GenericPacket> PING_REQUEST = generic("ping_request");
        public static final PacketType<GenericPacket> PLACE_RECIPE = generic("place_recipe");
        public static final PacketType<GenericPacket> PLAYER_ABILITIES = generic("player_abilities");
        public static final PacketType<ServerboundPlayerActionPacket> PLAYER_ACTION = typed("player_action", ServerboundPlayerActionPacket.class);
        public static final PacketType<ServerboundPlayerCommandPacket> PLAYER_COMMAND = typed("player_command", ServerboundPlayerCommandPacket.class);
        public static final PacketType<GenericPacket> PLAYER_INPUT = generic("player_input");
        public static final PacketType<GenericPacket> PONG = generic("pong");
        public static final PacketType<GenericPacket> RECIPE_BOOK_CHANGE_SETTINGS = generic("recipe_book_change_settings");
        public static final PacketType<GenericPacket> RECIPE_BOOK_SEEN_RECIPE = generic("recipe_book_seen_recipe");
        public static final PacketType<GenericPacket> RENAME_ITEM = generic("rename_item");
        public static final PacketType<GenericPacket> RESOURCE_PACK = generic("resource_pack");
        public static final PacketType<GenericPacket> SEEN_ADVANCEMENTS = generic("seen_advancements");
        public static final PacketType<GenericPacket> SELECT_TRADE = generic("select_trade");
        public static final PacketType<GenericPacket> SET_BEACON = generic("set_beacon");
        public static final PacketType<ServerboundSetCarriedItemPacket> SET_CARRIED_ITEM = typed("set_carried_item", ServerboundSetCarriedItemPacket.class);
        public static final PacketType<GenericPacket> SET_COMMAND_BLOCK = generic("set_command_block");
        public static final PacketType<GenericPacket> SET_COMMAND_MINECART = generic("set_command_minecart");
        public static final PacketType<GenericPacket> SET_CREATIVE_MODE_SLOT = generic("set_creative_mode_slot");
        public static final PacketType<GenericPacket> SET_JIGSAW_BLOCK = generic("set_jigsaw_block");
        public static final PacketType<GenericPacket> SET_STRUCTURE_BLOCK = generic("set_structure_block");
        public static final PacketType<ServerboundSignUpdatePacket> SIGN_UPDATE = typed("sign_update", ServerboundSignUpdatePacket.class);
        public static final PacketType<ServerboundSwingPacket> SWING = typed("swing", ServerboundSwingPacket.class);
        public static final PacketType<GenericPacket> TELEPORT_TO_ENTITY = generic("teleport_to_entity");
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
