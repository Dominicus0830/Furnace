package net.minecraft;

/**
 * 게임 공용 상수(Mojang 이름 shim, N2b-6). 값은 바닐라 1.21.1 그대로. 개발용 DEBUG_* 상수와 netty 누수 감지 수준은 두지 않았다.
 * FAWE 1.21.1 어댑터가 {@code getCurrentVersion().getDataVersion().getVersion()} 이 3953·3955 인지로 지원 판을 고른다.
 */
public class SharedConstants {

    public static final boolean SNAPSHOT = false;
    public static final int WORLD_VERSION = 3955;
    public static final String SERIES = "main";
    public static final String VERSION_STRING = "1.21.1";
    public static final int RELEASE_NETWORK_PROTOCOL_VERSION = 767;
    public static final int SNAPSHOT_NETWORK_PROTOCOL_VERSION = 204;
    public static final int SNBT_NAG_VERSION = 3937;
    public static final boolean CRASH_EAGERLY = false;
    public static final int RESOURCE_PACK_FORMAT = 34;
    public static final int DATA_PACK_FORMAT = 48;
    public static final int LANGUAGE_FORMAT = 1;
    public static final int REPORT_FORMAT_VERSION = 1;
    public static final String DATA_VERSION_TAG = "DataVersion";
    public static final int DEFAULT_MINECRAFT_PORT = 25565;
    public static final float MAXIMUM_BLOCK_EXPLOSION_RESISTANCE = 3600000.0f;
    public static final int WORLD_RESOLUTION = 16;
    public static final int MAX_CHAT_LENGTH = 256;
    public static final int MAX_USER_INPUT_COMMAND_LENGTH = 32500;
    public static final int MAX_FUNCTION_COMMAND_LENGTH = 2000000;
    public static final int MAX_PLAYER_NAME_LENGTH = 16;
    public static final int MAX_CHAINED_NEIGHBOR_UPDATES = 1000000;
    public static final int MAX_RENDER_DISTANCE = 32;
    public static final int TICKS_PER_SECOND = 20;
    public static final int MILLIS_PER_TICK = 50;
    public static final int TICKS_PER_MINUTE = 1200;
    public static final int TICKS_PER_GAME_DAY = 24000;
    public static final int WORLD_ICON_SIZE = 64;

    private static volatile WorldVersion CURRENT_VERSION = DetectedVersion.BUILT_IN;

    public SharedConstants() {}

    /** 바닐라는 서버 시작 때 정한다 — 여기서는 처음부터 1.21.1 이고, 플러그인이 바꾸면 그 값을 돌려준다(바닐라 그대로). */
    public static void setVersion(WorldVersion version) { CURRENT_VERSION = version; }

    public static void tryDetectVersion() {}

    public static WorldVersion getCurrentVersion() { return CURRENT_VERSION; }

    public static int getProtocolVersion() { return RELEASE_NETWORK_PROTOCOL_VERSION; }
}
