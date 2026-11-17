// 블록 광선 판정의 맞은 결과(바닐라 BlockHitResult): 맞은 칸, 맞은 면(블록 바깥쪽을 향한 방향), 맞은 지점(월드 좌표), 블록 안쪽에서 시작했는가.
package org.domi.furnace.api.world;

public record BlockHit(BlockPos pos, Direction face, double x, double y, double z, boolean inside) {}
