package org.domi.furnace.server.world;

/**
 * 바닐라 1.21.1 {@code ChunkTrackingView}: 플레이어에게 보내는 청크의 모양. 정사각이 아니라 둥근 모양이다 —
 * 시야 16 에서 1021청크(정사각 33×33 = 1089 가 아님). 축 방향으로는 시야 + 1 칸까지 간다(둘레 테두리 포함).
 * 보낼 청크 집합·내릴 청크 집합·전송 직전 시야 확인이 모두 이 판정 하나를 쓴다.
 */
public final class ChunkTrackingView {
    private ChunkTrackingView() {}

    /** 시야 viewDistance 일 때 중심에서 어느 축으로든 가장 멀리 가는 칸 수(바닐라 Positioned.minX/maxX). */
    public static int reach(int viewDistance) {
        return viewDistance + 1;
    }

    /** 바닐라 Positioned.contains(x, z) — 플레이어가 받아야 하는 청크인가(둘레 포함). */
    public static boolean contains(int centerX, int centerZ, int viewDistance, int x, int z) {
        return isWithinDistance(centerX, centerZ, viewDistance, x, z, true);
    }

    /** 바닐라 isInViewDistance — 둘레(가장자리 한 겹)를 뺀 안쪽. */
    public static boolean containsInner(int centerX, int centerZ, int viewDistance, int x, int z) {
        return isWithinDistance(centerX, centerZ, viewDistance, x, z, false);
    }

    /** 바닐라 ChunkTrackingView.isWithinDistance 그대로(원문 변수 이름만 풀었다). */
    static boolean isWithinDistance(int centerX, int centerZ, int viewDistance, int x, int z, boolean includeOuter) {
        int dx = Math.max(0, Math.abs(x - centerX) - 1);
        int dz = Math.max(0, Math.abs(z - centerZ) - 1);
        long major = Math.max(0, Math.max(dx, dz) - (includeOuter ? 1 : 0));
        long minor = Math.min(dx, dz);
        return minor * minor + major * major < (long) viewDistance * viewDistance;
    }
}
