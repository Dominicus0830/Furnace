// Structures.place 의 결과.
package org.domi.furnace.api.structure;

import org.domi.furnace.api.world.BlockPos;

/**
 * @param status  결과
 * @param min     놓은 영역의 모서리(회전·대칭 뒤의 상자). PLACED 가 아니면 null
 * @param max     반대쪽 모서리(포함). PLACED 가 아니면 null
 */
public record PlaceResult(Status status, BlockPos min, BlockPos max) {

    public enum Status {
        /** 놓았다. */
        PLACED,
        /** 그 이름의 템플릿이 없다(월드 generated/, 켜진 데이터 팩, 내장 어디에도). */
        TEMPLATE_NOT_FOUND,
        /** 템플릿은 있지만 크기가 0이거나 팔레트가 없다. */
        EMPTY_TEMPLATE,
        /** 놓을 영역의 청크가 모두 한 region 에 올라와 있지 못했다(그 청크가 높이 밖이거나 다른 region 에 걸렸다) — 아무것도 놓지 않았다. */
        NOT_LOADED,
        /** 영역이 월드 높이(차원 타입의 최소~최대 y) 밖으로 나간다 — 아무것도 놓지 않았다. */
        OUT_OF_WORLD,
        /** 영역이 한 번에 놓을 수 있는 크기(약 19x19 청크)를 넘는다 — 아무것도 놓지 않았다. */
        TOO_LARGE,
        /** 팔레트 번호가 템플릿의 팔레트 수를 넘는다. */
        INVALID_PALETTE,
        /** 배치기가 놓지 못했다(영역 밖 접근 등) — 아무것도 옮기지 않았다. */
        FAILED
    }

    public boolean placed() { return status == Status.PLACED; }
}
