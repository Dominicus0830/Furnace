package net.minecraft.world.level.saveddata.maps;

import net.minecraft.core.Holder;
import org.domi.furnace.compat.nms.NmsMapDecorations;

/**
 * 바닐라 지도 표시 종류(Mojang 이름 shim, easy-nms E4c-2). 필드 35개·선언 순서·이름은 바닐라 1.21.1 과 같다(선언 순서 = 레지스트리 번호 순서).
 * 값은 등록된 손잡이(Holder.Reference)다.
 */
public final class MapDecorationTypes {

    private MapDecorationTypes() {}

    public static final Holder<MapDecorationType> PLAYER = NmsMapDecorations.holder("player");
    public static final Holder<MapDecorationType> FRAME = NmsMapDecorations.holder("frame");
    public static final Holder<MapDecorationType> RED_MARKER = NmsMapDecorations.holder("red_marker");
    public static final Holder<MapDecorationType> BLUE_MARKER = NmsMapDecorations.holder("blue_marker");
    public static final Holder<MapDecorationType> TARGET_X = NmsMapDecorations.holder("target_x");
    public static final Holder<MapDecorationType> TARGET_POINT = NmsMapDecorations.holder("target_point");
    public static final Holder<MapDecorationType> PLAYER_OFF_MAP = NmsMapDecorations.holder("player_off_map");
    public static final Holder<MapDecorationType> PLAYER_OFF_LIMITS = NmsMapDecorations.holder("player_off_limits");
    public static final Holder<MapDecorationType> WOODLAND_MANSION = NmsMapDecorations.holder("mansion");
    public static final Holder<MapDecorationType> OCEAN_MONUMENT = NmsMapDecorations.holder("monument");
    public static final Holder<MapDecorationType> WHITE_BANNER = NmsMapDecorations.holder("banner_white");
    public static final Holder<MapDecorationType> ORANGE_BANNER = NmsMapDecorations.holder("banner_orange");
    public static final Holder<MapDecorationType> MAGENTA_BANNER = NmsMapDecorations.holder("banner_magenta");
    public static final Holder<MapDecorationType> LIGHT_BLUE_BANNER = NmsMapDecorations.holder("banner_light_blue");
    public static final Holder<MapDecorationType> YELLOW_BANNER = NmsMapDecorations.holder("banner_yellow");
    public static final Holder<MapDecorationType> LIME_BANNER = NmsMapDecorations.holder("banner_lime");
    public static final Holder<MapDecorationType> PINK_BANNER = NmsMapDecorations.holder("banner_pink");
    public static final Holder<MapDecorationType> GRAY_BANNER = NmsMapDecorations.holder("banner_gray");
    public static final Holder<MapDecorationType> LIGHT_GRAY_BANNER = NmsMapDecorations.holder("banner_light_gray");
    public static final Holder<MapDecorationType> CYAN_BANNER = NmsMapDecorations.holder("banner_cyan");
    public static final Holder<MapDecorationType> PURPLE_BANNER = NmsMapDecorations.holder("banner_purple");
    public static final Holder<MapDecorationType> BLUE_BANNER = NmsMapDecorations.holder("banner_blue");
    public static final Holder<MapDecorationType> BROWN_BANNER = NmsMapDecorations.holder("banner_brown");
    public static final Holder<MapDecorationType> GREEN_BANNER = NmsMapDecorations.holder("banner_green");
    public static final Holder<MapDecorationType> RED_BANNER = NmsMapDecorations.holder("banner_red");
    public static final Holder<MapDecorationType> BLACK_BANNER = NmsMapDecorations.holder("banner_black");
    public static final Holder<MapDecorationType> RED_X = NmsMapDecorations.holder("red_x");
    public static final Holder<MapDecorationType> DESERT_VILLAGE = NmsMapDecorations.holder("village_desert");
    public static final Holder<MapDecorationType> PLAINS_VILLAGE = NmsMapDecorations.holder("village_plains");
    public static final Holder<MapDecorationType> SAVANNA_VILLAGE = NmsMapDecorations.holder("village_savanna");
    public static final Holder<MapDecorationType> SNOWY_VILLAGE = NmsMapDecorations.holder("village_snowy");
    public static final Holder<MapDecorationType> TAIGA_VILLAGE = NmsMapDecorations.holder("village_taiga");
    public static final Holder<MapDecorationType> JUNGLE_TEMPLE = NmsMapDecorations.holder("jungle_temple");
    public static final Holder<MapDecorationType> SWAMP_HUT = NmsMapDecorations.holder("swamp_hut");
    public static final Holder<MapDecorationType> TRIAL_CHAMBERS = NmsMapDecorations.holder("trial_chambers");
}
