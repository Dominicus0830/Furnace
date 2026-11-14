// 기본 region 설정의 띠(다른 region 까지 최소 거리)가 region 한정으로 읽는 바닐라 반경보다 커야 결과가 바닐라와 같다(T-101).
// 반경을 늘리거나 기본 shift·merge-radius 를 줄이는 변경이 이 조건을 깨면 여기서 잡는다. 목록: docs/feature-parity/region-boundary-radii.md
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.types.PortalForcer;
import org.domi.furnace.server.config.Config;
import org.junit.jupiter.api.Test;

class RegionBandRadiusTest {

    /** 서로 다른 region 의 구역 사이에는 merge-radius 개의 빈 구역이 있다 → 두 region 블록 사이 축 거리는 적어도 band+1. */
    static int bandBlocks(int shift, int mergeRadius) {
        return mergeRadius * (1 << shift) * 16;
    }

    @Test
    void defaultBandCoversRegionLocalVanillaRadii() {
        int band = bandBlocks(Config.DEFAULT.regionSectionShift(), Config.DEFAULT.regionMergeRadius());
        assertEquals(128, band, "기본(shift 3, r1) 띠 128블록 → 다른 region 블록은 129블록부터");
        // region 한정으로 읽는 반경 가운데 128 을 넘는 것은 없어야 한다(같으면 1블록 여유)
        assertTrue(ChunkLevel.LIGHTNING_ROD_SEARCH_RADIUS <= band, "피뢰침 찾기 반경");
        assertTrue(PortalForcer.searchRadius(false) <= band, "오버월드 지옥문 찾기 반경");
        assertTrue(PortalForcer.searchRadius(true) <= band, "네더 지옥문 찾기 반경");
    }

    @Test
    void shift2WithRadius1WouldBreakTheLightningRod() {
        // O01-01 에서 s2r1 을 철회한 이유: 띠 64 < 피뢰침 128
        assertTrue(ChunkLevel.LIGHTNING_ROD_SEARCH_RADIUS > bandBlocks(2, 1));
        assertTrue(ChunkLevel.LIGHTNING_ROD_SEARCH_RADIUS <= bandBlocks(2, 2));
    }
}
