# 2026-07-04 worldgen — worldSurfaceHeightAt 청크 단위 캐시 (M-opt P1-1)

## 작업 요약
구조물 배치 판정 조회 1회당 청크 전체 재생성(42ms) → 청크 단위 메모이제이션으로 38.95→1.91ms/call. 커밋 6013343.

## 문제 / 목적
worldSurfaceHeightAt(NoiseWorldSource)이 매 호출 generate()로 ChunkColumn 전체를 만들고 높이 1개만 취한 뒤 폐기. ChunkPipeline은 청크 하나의 generateFull()마다 최대 289좌표(9-grid + 반경 8 링)를 ForkJoinPool 병렬로 applyStructureStarts — 인접 청크 링 범위가 겹쳐 동일 청크가 반복 재생성.

## 해결 방법 / 구현 내용
- `ConcurrentHashMap<Long, int[]>` 캐시 — 키=청크 좌표 팩킹, 값=int[256](16×16 SURFACE 높이, `height(...)-1` 동일 로직).
- `computeIfAbsent`로 키당 계산 1회 보장. generate() 내부에 재귀 경로 없음 확인(computeIfAbsent 재진입 안전).
- parity 안전: generate(cx,cz)=seed+좌표 순수 함수(aquiferRandom=stateless XoroshiroPositionalRandomFactory) — 메모이제이션은 RNG 소비·heightmap 게이팅 불변.
- 수명: 월드 수명, 만료 없음(구조물 배치 후보 청크만 타는 희소 경로라 수용).
- 검증: 유닛 4종(유효성·독립 엔트리·8스레드 동시성·실측), StructureFeatureParityTest 3종 0 mismatch, full suite green.

## 잔여 개선 여지
9-grid 청크는 proto.col용 generate()와 캐시용 generate()가 별도 인스턴스 — 공유 시 추가 절감 가능(캐시 외 리팩터링이라 미착수).

## 관련 파일
[[2026-07-04_m-opt_병목-서베이-실측]], [[2026-07-04_io_compression-regionfile-버퍼링]]

## 태그
#Furnace #worldgen #Feature #Performance
