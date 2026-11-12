// 빠른 블록 쓰기(E7)의 쓰기 목록. World.editBlocks 가 부른 스레드에서 채우고, 서버가 칸마다 그 칸을 가진 region 스레드에서 적용한다.
package org.domi.furnace.api.world;

/**
 * 바꿀 칸 목록. {@link World#editBlocks} 에 넘긴 함수 안에서만 쓴다 — 함수가 끝나면 닫히고 그 뒤 부르면 {@link IllegalStateException}.
 * 월드를 읽지 않는다(목록만 쌓는다). 같은 칸을 여러 번 적으면 마지막 값이 남는다.
 * 월드 높이 밖 칸은 버리고 결과의 {@link BlockEditResult#skipped()} 로 센다.
 * 알 수 없는 블록·속성이면 그 자리에서 {@link IllegalArgumentException}.
 */
public interface BlockEditor {

    /** 그 칸을 data 로 바꾸도록 적는다. */
    void set(int x, int y, int z, BlockData data);

    default void set(BlockPos pos, BlockData data) { set(pos.x(), pos.y(), pos.z(), data); }

    /** 두 꼭짓점(양끝 포함)이 이루는 상자 전체를 data 로. */
    default void fill(BlockPos a, BlockPos b, BlockData data) {
        int minX = Math.min(a.x(), b.x()), minY = Math.min(a.y(), b.y()), minZ = Math.min(a.z(), b.z());
        int maxX = Math.max(a.x(), b.x()), maxY = Math.max(a.y(), b.y()), maxZ = Math.max(a.z(), b.z());
        for (int y = minY; y <= maxY; y++)
            for (int z = minZ; z <= maxZ; z++)
                for (int x = minX; x <= maxX; x++) set(x, y, z, data);
    }

    /** 지금까지 적은 칸 수(같은 칸은 한 번, 높이 밖은 빼고). */
    int size();
}
