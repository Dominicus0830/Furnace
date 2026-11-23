package org.domi.furnace.server.entity;

import static org.domi.furnace.server.block.Dir.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 바닐라 HangingEntity#rotate·mirror 표(1.21.1): 돌림 서수 0 없음·1 시계 90·2 180·3 반시계 90, 대칭 1 LEFT_RIGHT·2 FRONT_BACK.
 * 구조물 템플릿 배치(TemplatePlacer)와 플러그인 rotate 가 같은 표를 쓴다.
 */
class HangingRotationTableTest {

    @Test
    void horizontalDirectionsTurnLikeVanilla() {
        int[][] cases = {   // {방향, 시계 90, 180, 반시계 90}
            {NORTH, EAST, SOUTH, WEST},
            {EAST, SOUTH, WEST, NORTH},
            {SOUTH, WEST, NORTH, EAST},
            {WEST, NORTH, EAST, SOUTH},
        };
        for (int[] c : cases) {
            assertEquals(c[0], HangingEntity.rotatedDirection(c[0], 0));
            assertEquals(c[1], HangingEntity.rotatedDirection(c[0], 1));
            assertEquals(c[2], HangingEntity.rotatedDirection(c[0], 2));
            assertEquals(c[3], HangingEntity.rotatedDirection(c[0], 3));
        }
    }

    @Test
    void upAndDownNeverTurn() {
        for (int r = 0; r < 4; r++) {
            assertEquals(UP, HangingEntity.rotatedDirection(UP, r));
            assertEquals(DOWN, HangingEntity.rotatedDirection(DOWN, r));
        }
    }

    /** Entity#rotate 와 달리 시계 90 이 +270, 반시계 90 이 +90 이다. */
    @Test
    void addedDegreesAreSwappedAgainstEntity() {
        assertEquals(0.0F, HangingEntity.rotateDegrees(0));
        assertEquals(270.0F, HangingEntity.rotateDegrees(1));
        assertEquals(180.0F, HangingEntity.rotateDegrees(2));
        assertEquals(90.0F, HangingEntity.rotateDegrees(3));
    }

    /** Mirror#getRotation: LEFT_RIGHT 는 Z 축 방향, FRONT_BACK 은 X 축 방향만 180. 위·아래와 대칭 없음은 그대로. */
    @Test
    void mirrorFlipsOnlyAlongItsAxis() {
        for (int d : ALL) {
            assertEquals(0, HangingEntity.mirrorRotation(0, d));
            assertEquals(axis(d) == AXIS_Z ? 2 : 0, HangingEntity.mirrorRotation(1, d), NAME[d]);
            assertEquals(axis(d) == AXIS_X ? 2 : 0, HangingEntity.mirrorRotation(2, d), NAME[d]);
        }
    }
}
