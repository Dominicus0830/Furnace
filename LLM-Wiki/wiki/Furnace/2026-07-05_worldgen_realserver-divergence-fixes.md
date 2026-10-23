# 2026-07-05 실서버 발산 4건 — 조사·수정

## 작업 요약
사용자 육안 4건(버섯 과다·연못 없음·떠있는 나무·스폰 낙하) root-cause 조사 후 수정 3커밋. 정본 tasks/realserver-{divergence,fixes}/.

## 결과
1. **스폰 낙하** — confirm 게이트 vs 스폰청크 전송 비커플링. AND 커플링 `836846f` + 단위테스트 4케이스.
2. **버섯 과다** — 바닐라도 ChunkStatus상 FEATURES가 LIGHT 선행 → gen 중 광량 미계산으로 동일하게 뿌림. Furnace 버그 아님. light 시스템(M5) 후 자연 해소. 근사 수정 금지 판단 유지.
3. **떠있는 나무** — FeatureWorldView가 읽기/쓰기를 origin ±1로 뭉침. vanilla는 읽기 grid center ±2 / 쓰기 origin ±1 분리 → 수정 `8d8a29f` (성능 469→442ms, 회귀 없음). **단 (2,0)/(3,3) mismatch는 이 수정과 무관 — spill 창 가설 반증됨.** 잔여 원인 = 인접 두 나무 상호 isFree/순서 발산 추정(미확정). 상한 테스트로 고정.
4. **연못(-3,-6)** — noise/aquifer 0 mismatch(기각). carver 단계 32건 전부 y=62 dirt vs grass_block = **vanilla WorldCarver regrass(reachedSurface/topMaterial: carve가 지표 관통 시 노출 dirt를 biome top material로 복원) 통째 부재**. 웅덩이 자체는 있고 가장자리가 흙으로 보이는 것. fixture 잠금 `c42644f`, 수정은 SurfaceRule을 carver 체인에 연결하는 M~L 후속.

## 교훈
- 이분 탐색(stage별 oracle fixture: aquifer→carver→feature)이 육안 제보를 30분 내 root-cause로 환원.
- "light 없어서 X가 이상하다"류 제보는 바닐라 gen-시점 semantics 먼저 확인 — 바닐라도 같으면 버그 아님.

## 후속 큐
carver-regrass(M~L) → tree-overlap 발산(미확정) → mega-tree-placers → coral 등 17종 → 실서버 .mca 게이트.

## 관련 파일
[[2026-07-05_worldgen_fancy-oak-placer-parity]], [[2026-07-05_worldgen_milestone-gap-survey]]

## 태그
#Furnace #worldgen #BugFix
