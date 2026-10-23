# 2026-07-04 worldgen — M-opt P0 quick win 2건 (스카이라이트·vgrad 캐시)

## 작업 요약
스카이라이트 상수 배열 static 캐시(6ea7516) + SurfaceSystem vertical_gradient positional factory 캐시(f0968fb).

## 문제 / 목적
M-opt 서베이 P0 즉효 2건.
1. ChunkSerializer, 전체-스카이라이트(레벨 15 균일) 배열 청크 전송마다 재생성.
2. SurfaceSystem.buildSurface(실측 10ms/42ms), vertical_gradient 조건마다 `fromHashOf`(MD5) 재계산 — SurfaceSystem.java:367.

## 해결 방법 / 구현 내용
1. `FULL_SKYLIGHT` static final 배열 + 정적 초기화. 루프 재할당 제거. `buf.writeBytes` 복사라 공유 배열 무변조 안전. 전/후 serialize 출력 55898B 동일.
2. vgrad 캐시 6줄 — buildNoise 기존 캐시 패턴 미러. 벤치 buildSurface 10→9ms (vertical_gradient 좁은 y 대역만 평가. MD5 비중 예상보다 작음).

## 주의
`fromHashOfPositional()`은 `fromHashOf()`와 다른 알고리즘 — 치환 금지 (parity 깨짐).

## 관련 파일
[[2026-07-04_m-opt_병목-서베이-실측]], [[2026-07-04_worldgen_vein-noVein-cell-skip]]

## 태그
#Furnace #worldgen #Feature #Performance