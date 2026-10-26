// 바닐라 MapItem.update(색 계산)와 MapItem.renderBiomePreviewMap(탐험 지도 바탕) — 순서·수식·난수 없음(두 곳 모두 난수를 쓰지 않는다)까지 그대로.
// 월드는 Terrain 으로 가린다(서버에서는 그 region 이 가진 로드된 청크, 시험에서는 고정 지형).
package org.domi.furnace.server.map;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;

public final class MapRenderer {
    private MapRenderer() { }

    /** 지도가 보는 월드. 모든 좌표는 블록 좌표. */
    public interface Terrain {
        /** DimensionType.hasCeiling(네더). */
        boolean hasCeiling();

        /** getMinBuildHeight. */
        int minY();

        /** 그 청크를 읽을 수 있는가 — 아니면 그 열은 건너뛴다(바닐라는 청크를 불러 와 칠한다 — 아래 TODO). */
        boolean chunkAvailable(int chunkX, int chunkZ);

        /** chunk.getHeight(WORLD_SURFACE, x, z) — 맨 위 비공기 블록 y + 1(빈 열은 minY). */
        int surfaceHeight(int x, int z);

        /** 블록 상태 id(범위 밖은 공기). */
        int blockState(int x, int y, int z);

        /** MapBanner.fromWorld: 그 칸이 깃발 블록 엔티티를 가졌으면 (위치·색·이름), 아니면 null. */
        default MapData.Banner bannerAt(int x, int y, int z) { return null; }
    }

    private static final int BEDROCK = B.defaultState("bedrock"), DIRT = B.defaultState("dirt"), STONE = B.defaultState("stone");
    private static final BlockInfo INFO = B.INFO;

    /** 상태의 지도 색 번호(BlockState.getMapColor). */
    static int mapColor(int state) { return MapColor.ofState(state); }

    /** 상태가 가진 유체(FluidState)가 있는가. */
    private static int fluidOf(int state) { return BlockBehaviors.of(state).getFluidState(state); }

    /** isFaceSturdy(UP, FULL). */
    private static boolean sturdyUp(int state) { return INFO.faceSturdy(state, Dir.UP, BlockInfo.FULL); }

    /** MapItem.update 안의 colorLeftOver: 유체가 있고 위가 단단하지 않으면 그 유체의 레거시 블록으로. */
    private static int withLegacyFluid(int state) {
        int fluid = fluidOf(state);
        return !Fluids.isEmpty(fluid) && !sturdyUp(state) ? Fluids.createLegacyBlock(fluid) : state;
    }

    /**
     * MapItem.update(level, entity, data): 플레이어 둘레 128/배율 칸 격자를 열 단위로 칠한다. 한 틱에는 열 격자 (x & 15) == (step & 15) 인 열(1/16)만,
     * 바뀌었으면 다음 열도 이어서 칠한다. 지도 차원이 다르면 아무것도 안 한다.
     * TODO(지도): 바닐라는 청크를 불러 와(level.getChunk) 먼 열도 칠한다. 여기서는 그 region 이 가진 청크만 칠한다(시야 밖 열은 빈 채로 남음).
     */
    public static void update(Terrain level, String levelDimension, double playerX, double playerZ, MapData data, MapData.Holding holding) {
        if (!levelDimension.equals(data.dimension())) return;
        synchronized (data) {
            int scale = 1 << data.scale();
            int cx = data.centerX(), cz = data.centerZ();
            int px = (int) Math.floor(playerX - cx) / scale + 64;
            int pz = (int) Math.floor(playerZ - cz) / scale + 64;
            int radius = 128 / scale;
            if (level.hasCeiling()) radius /= 2;
            holding.step++;
            boolean forceNext = false;
            int minY = level.minY();
            int[] counts = new int[64];
            int[] order = new int[64];
            for (int x = px - radius + 1; x < px + radius; x++) {
                if ((x & 15) == (holding.step & 15) || forceNext) {
                    forceNext = false;
                    double lastHeight = 0.0;
                    for (int z = pz - radius - 1; z < pz + radius; z++) {
                        if (x >= 0 && z >= -1 && x < 128 && z < 128) {
                            int dist = (x - px) * (x - px) + (z - pz) * (z - pz);
                            boolean edge = dist > (radius - 2) * (radius - 2);
                            int wx0 = (cx / scale + x - 64) * scale;
                            int wz0 = (cz / scale + z - 64) * scale;
                            if (!level.chunkAvailable(Math.floorDiv(wx0, 16), Math.floorDiv(wz0, 16))) continue;
                            int n = 0;   // 센 색 종류 수(처음 나온 순서)
                            java.util.Arrays.fill(counts, 0);
                            int waterDepth = 0;
                            double height = 0.0;
                            if (level.hasCeiling()) {
                                int h = wx0 + wz0 * 231871;
                                h = h * h * 31287121 + h * 11;
                                if ((h >> 20 & 1) == 0) n = add(counts, order, n, mapColor(DIRT), 10);
                                else n = add(counts, order, n, mapColor(STONE), 100);
                                height = 100.0;
                            } else {
                                for (int dx = 0; dx < scale; dx++) {
                                    for (int dz = 0; dz < scale; dz++) {
                                        int wx = wx0 + dx, wz = wz0 + dz;
                                        int y = level.surfaceHeight(wx, wz) + 1;
                                        int state;
                                        if (y <= minY + 1) {
                                            state = BEDROCK;
                                        } else {
                                            do {
                                                state = level.blockState(wx, --y, wz);
                                            } while (mapColor(state) == MapColor.NONE && y > minY);
                                            if (y > minY && !Fluids.isEmpty(fluidOf(state))) {
                                                int y2 = y - 1;
                                                int below;
                                                do {
                                                    below = level.blockState(wx, y2--, wz);
                                                    waterDepth++;
                                                } while (y2 > minY && !Fluids.isEmpty(fluidOf(below)));
                                                state = withLegacyFluid(state);
                                            }
                                        }
                                        data.checkBanners((pos, banner) -> banner.equals(level.bannerAt(pos[0], pos[1], pos[2])), wx, wz);
                                        height += (double) y / (scale * scale);
                                        n = add(counts, order, n, mapColor(state), 1);
                                    }
                                }
                            }
                            waterDepth /= scale * scale;
                            int top = MapColor.NONE;   // Iterables.getFirst(copyHighestCountFirst, NONE): 가장 많은 색, 동률이면 먼저 나온 것
                            int best = 0;
                            for (int i = 0; i < n; i++) {
                                if (counts[order[i]] > best) {
                                    best = counts[order[i]];
                                    top = order[i];
                                }
                            }
                            int brightness;
                            if (top == MapColor.WATER) {
                                double v = waterDepth * 0.1 + ((x + z) & 1) * 0.2;
                                if (v < 0.5) brightness = MapColor.HIGH;
                                else if (v > 0.9) brightness = MapColor.LOW;
                                else brightness = MapColor.NORMAL;
                            } else {
                                double v = (height - lastHeight) * 4.0 / (scale + 4) + (((x + z) & 1) - 0.5) * 0.4;
                                if (v > 0.6) brightness = MapColor.HIGH;
                                else if (v < -0.6) brightness = MapColor.LOW;
                                else brightness = MapColor.NORMAL;
                            }
                            lastHeight = height;
                            if (z >= 0 && dist < radius * radius && (!edge || ((x + z) & 1) != 0)) {
                                forceNext |= data.updateColor(x, z, MapColor.packed(top, brightness));
                            }
                        }
                    }
                }
            }
        }
    }

    private static int add(int[] counts, int[] order, int n, int color, int occurrences) {
        if (counts[color] == 0) order[n++] = color;
        counts[color] += occurrences;
        return n;
    }

    /** 바이옴 판정: 그 칸(블록 좌표, y=0)의 바이옴이 물 윤곽(#water_on_map_outlines)인가. */
    @FunctionalInterface
    public interface BiomeWater {
        boolean at(int blockX, int blockZ);
    }

    /**
     * MapItem.renderBiomePreviewMap(level, stack): 지도 128x128 칸마다 (칸 좌표 * 배율)의 바이옴이 물 윤곽이면 COLOR_ORANGE, 아니면 지도 칸 주변 8칸 중
     * 몇 칸이 물인지로 가장자리(COLOR_BROWN)와 해칭 무늬를 정한다. 난수 없음.
     */
    public static void renderBiomePreview(MapData data, BiomeWater water) {
        if (data == null) return;
        int scale = 1 << data.scale();
        int cx = data.centerX(), cz = data.centerZ();
        boolean[] flags = new boolean[128 * 128];
        int ox = cx / scale - 64, oz = cz / scale - 64;
        for (int z = 0; z < 128; z++) {
            for (int x = 0; x < 128; x++) flags[z * 128 + x] = water.at((ox + x) * scale, (oz + z) * scale);
        }
        synchronized (data) {
            for (int x = 1; x < 127; x++) {
                for (int z = 1; z < 127; z++) {
                    int neighbours = 0;
                    for (int dx = -1; dx < 2; dx++) {
                        for (int dz = -1; dz < 2; dz++) {
                            if ((dx != 0 || dz != 0) && flags[(z + dz) * 128 + x + dx]) neighbours++;
                        }
                    }
                    int brightness = MapColor.LOWEST;
                    int color = MapColor.NONE;
                    if (flags[z * 128 + x]) {
                        color = MapColor.COLOR_ORANGE;
                        if (neighbours > 7 && z % 2 == 0) {
                            switch ((x + (int) (Dir.sin(z + 0.0F) * 7.0F)) / 8 % 5) {   // Mth.sin: 바닐라 룩업표
                                case 0, 4 -> brightness = MapColor.LOW;
                                case 1, 3 -> brightness = MapColor.NORMAL;
                                case 2 -> brightness = MapColor.HIGH;
                                default -> { }
                            }
                        } else if (neighbours > 7) {
                            color = MapColor.NONE;
                        } else if (neighbours > 5) {
                            brightness = MapColor.NORMAL;
                        } else if (neighbours > 3) {
                            brightness = MapColor.LOW;
                        } else if (neighbours > 1) {
                            brightness = MapColor.LOW;
                        }
                    } else if (neighbours > 0) {
                        color = MapColor.COLOR_BROWN;
                        brightness = neighbours > 3 ? MapColor.NORMAL : MapColor.LOWEST;
                    }
                    if (color != MapColor.NONE) data.setColor(x, z, MapColor.packed(color, brightness));
                }
            }
        }
    }
}
