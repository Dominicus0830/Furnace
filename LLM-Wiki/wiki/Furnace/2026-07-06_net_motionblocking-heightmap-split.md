# 2026-07-06 net — MOTION_BLOCKING heightmap을 WORLD_SURFACE에서 분리

## 작업 요약
클라 청크 패킷 MOTION_BLOCKING heightmap, WORLD_SURFACE(!isAir) 배열 재사용 → vanilla 술어(blocksMotion || fluid) 기반 별도 배열로 분리. 커밋 e5a8bd9.

## 문제 / 목적
`ChunkColumn.motionBlockingHeightmap()` = `surface[]`(WORLD_SURFACE, !isAir) 재사용. vanilla MOTION_BLOCKING = `blocksMotion() || fluid` (mojang-mapped Heightmap.java 대조). 유리·레일·토치 등 blocksMotion=false 컬럼에서 클라 동작(강수 렌더 등) 발산.

## 해결 방법 / 구현 내용
- `ChunkColumn.java`에 `motionBlocking[256]` 신설, `surface[]` 완전 분리.
- 라이브 갱신 = `oceanFloor[]` 동일 "무효화 후 지연 재계산" 패턴 — surface[]류 "미프라임 가드" 버그 클래스 회피.
- **근사**: 저장소에 블록별 콜리전 shape 데이터 없음(vanilla blocksMotion = 콜리전 부피 임계값) → byte-exact 불가. 레일·버튼·토치·표지판·꽃·작물류 이름 화이트리스트. 미커버(드립스톤·눈더미 층수·유리판) = 기존 동작(blocksMotion=true) 유지.
- worldgen 미접촉 — vanilla도 MOTION_BLOCKING worldgen에 안 씀, gen parity 무관.
- 검증: full suite 129 testsuite failures=0, 2파일 커밋(ChunkColumn + ChunkColumnTest).

## 관련 파일
- [[2026-07-06_worldgen_tree-overlap-residual-parity]] — 직전 heightmap 술어 수정(51f6745)과 분리 유지
- 정본: `C:\Users\gagam\.claude\multiagent\tasks\motionblocking-predicate\workers\sonnet-impl\result.md` (근사 범위 상세)
- 수정: `furnace-server/.../world/ChunkColumn.java`, `ChunkColumnTest.java`

## 후속
- 콜리전 shape 데이터 도입 시 화이트리스트 → 정밀 판정 승격.

## 태그
#Furnace #net #BugFix