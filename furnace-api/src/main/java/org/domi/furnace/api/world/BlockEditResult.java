// 빠른 블록 쓰기(E7) 결과.
package org.domi.furnace.api.world;

/**
 * @param changed   바뀐 칸 수(바닐라 Level.setBlock 이 참을 돌려준 칸)
 * @param unchanged 이미 그 상태였거나 바닐라가 바꾸지 않은 칸 수(같은 상태, 공기만 있는 섹션에 공기 등)
 * @param skipped   청크가 로드돼 있지 않았거나 월드 높이 밖이라 건너뛴 칸 수(청크를 불러오거나 만들지 않는다)
 */
public record BlockEditResult(int changed, int unchanged, int skipped) {

    public static final BlockEditResult EMPTY = new BlockEditResult(0, 0, 0);

    /** 두 결과를 더한다. */
    public BlockEditResult plus(BlockEditResult o) {
        return new BlockEditResult(changed + o.changed, unchanged + o.unchanged, skipped + o.skipped);
    }
}
