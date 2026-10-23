# 2026-07-05 worldgen·전체 — 바닐라 대비 공백 전수조사 2건

## 작업 요약
sonnet 워커 2건: ① 서버 전체 마일스톤 공백 ② 월드젠 feature 전수 대조. Fable 검토·스팟체크 통과. 정본 = `~/.claude/multiagent/tasks/{milestone-gap-survey,worldgen-gap-survey}/workers/sonnet-survey/result.md`.

## 문제 / 목적
남은 마일스톤 계획이 바닐라 1.21.1 대비 뭘 빠뜨렸는지. 사용자 의심: coral.

## 핵심 결과
### 월드젠 (worldgen-gap-survey)
- configured_feature 루트 53종 중 **17종 부재-skip** (default → NoopFeature, 조용히 미생성). **coral 3종 확정** (warm_ocean 산호초 통째 소거) + sea_pickle, huge_mushroom 2종, monster_room(던전), dripstone_cluster/large_dripstone, iceberg, fossil, bamboo, nether 식생 전반, end 전반.
- tree: trunk 5종 + foliage 5종 **부재-fallback** (straight/blob 대체 = 오생성). tree_decorator attached_to_leaves·alter_ground 무시(맹그로브).
- placement modifier: environment_scan·not/all_of 등 미구현 — all_of는 "필터 없이 통과" = 오생성 방향.
- carver 3종 gap 없음. **structure = 최대 gap** (DesertPyramid 1종만, village·stronghold·mineshaft 등 전부 부재. 배치 인프라는 재사용 가능).
- 데이터 정본 경로: `furnace-server/src/main/resources/worldgen/1.21.1/worldgen/` (repo 루트 `data/minecraft/worldgen/`은 빈 잔재).
- 우선순위: coral+sea_pickle → huge_mushroom → monster_room → dripstone → structures.

### 전체 마일스톤 (milestone-gap-survey)
- 신규 공백: 커맨드·권한·whitelist·RCON(0건), advancement·scoreboard(0건), **채팅**(Fable 발견, 패킷 0건), **아이템 엔티티 드롭·줍기**(Fable 발견 — M4a 블록깨기가 드롭 없이 동작).
- 재조정: 블록업데이트(neighbor-update) 전파 = redstone·문·유체·중력블록 공통 선행 → 독립 마일스톤 승격. 전투·생존 코어 = L급, M5와 병렬 트랙 가능(의존 없음).
- Furnace 실체 = 네트워킹 + 청크영속화 + 월드젠뿐. 게임플레이 로직 전무.

## 갱신 마일스톤
A worldgen(fancy-oak✓→mega-tree→coral 등 feature 17종→structures) / B 성능 / C M5 조명·몹 / C' 생존코어(아이템엔티티→체력·허기→크래프팅·화로→채팅) / D' 블록업데이트 전파 / E 운영(커맨드→advancement) / F M6 플러그인API.

## 관련 파일
[[2026-07-05_worldgen_fancy-oak-placer-parity]]

## 태그
#Furnace #worldgen #Design
