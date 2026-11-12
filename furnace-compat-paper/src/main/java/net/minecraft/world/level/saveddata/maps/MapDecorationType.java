package net.minecraft.world.level.saveddata.maps;

import net.minecraft.resources.ResourceLocation;

/**
 * 지도 표시 종류(Mojang 이름 shim, easy-nms E4c-2). 바닐라 1.21.1 과 같은 record 다. 바닐라 종류는 {@link MapDecorationTypes}.
 * 코덱 필드는 두지 않았다.
 */
public record MapDecorationType(ResourceLocation assetId, boolean showOnItemFrame, int mapColor, boolean explorationMapElement, boolean trackCount) {

    public static final int NO_MAP_COLOR = -1;

    public boolean hasMapColor() { return mapColor != NO_MAP_COLOR; }
}
