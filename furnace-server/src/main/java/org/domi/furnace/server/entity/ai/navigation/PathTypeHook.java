// 몹별 길찾기 칸 판정 훅(플러그인 API 가 건다). 그 몹 기준으로 바닐라가 정한 칸 종류를 받아 바꿀 종류를 돌려준다(그대로면 같은 값).
// 걸린 훅이 없으면(기본) 길찾기는 바닐라 그대로다.
package org.domi.furnace.server.entity.ai.navigation;

@FunctionalInterface
public interface PathTypeHook {
    PathType apply(int x, int y, int z, PathType vanilla);
}
