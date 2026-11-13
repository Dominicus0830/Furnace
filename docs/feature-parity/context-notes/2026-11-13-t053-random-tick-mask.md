# T-053 무작위 틱 구역 마스크 (2026-11-13)

`ChunkLevel.tickChunk` 는 바닐라 `ServerLevel.tickChunk` 처럼 구역마다 `isRandomlyTicking()` 을 확인한다. 노이즈 지형 빈 틱에서 이 확인(구역 객체 24개 따라가기)이 region 틱의 약 25% 였다(O07-04). 무작위 틱 받는 구역은 청크당 평균 1.3개다.

## 구조

- `ChunkColumn.randomTickMask`(long): 비트 i 가 꺼져 있으면 구역 i 의 센 값(`tickingCount`)이 0 이다. 켜진 비트는 "있을 수도"(센 값 > 0, 모름 -1, cowCopy 공유 구역). 64 이상 구역(높이 1024 넘는 데이터 팩 차원)은 비트 없이 늘 확인한다.
- 비트는 **구역 자신의 쓰기 메서드**가 고친다(`ChunkSection.ticking(n)` — 센 값이 0 ↔ 0 아님으로 바뀔 때만). `sections()` 배열을 받아 구역에 직접 쓰는 바깥 경로도 빠지지 않는다. 구역은 주인 컬럼과 자리 번호를 안다(`attach`).
- 컬럼 배열의 원소를 갈아 끼우는 곳은 `ChunkColumn.replaceSection` 하나뿐이어야 한다(옛 객체 `detach`, 새 객체 `attach`). 새로 갈아 끼우는 경로(예: T-064 빛 작업의 얼린 구역 복사)도 이것을 거쳐야 한다 — 안 거치면 새 객체에 주인이 없어 비트가 안 켜지고 무작위 틱이 조용히 빠진다.
- cowCopy 사본은 공유 구역의 주인이 아니므로 비트를 모두 켜 둔다. 복사해 가지면 그때 맞춘다.
- 루프는 마스크를 구역마다 다시 읽는다(`nextRandomTickingSection`). 아래 구역의 무작위 틱이 위 구역에 블록을 놓으면(사탕수수가 구역 경계를 넘어 자람) 그 구역도 같은 틱에 돈다 — 옛 루프·바닐라와 같다. 틱 머리에 마스크를 굳히면 이것이 달라진다(변이 시험 `detectsMaskFrozenAtTickStart`).
- `-ea`(시험)에서는 `randomTickSections` 머리마다 `randomTickMaskCovers()` 단언: 꺼진 비트 구역은 주인이 이 컬럼이고 센 값 0 이며, 지금 블록을 다시 세어도 0.

## 결과 동일 증명

- 꺼진 비트 구역은 원래도 `isRandomlyTicking()` 이 거짓이라 `blockRandomPos`(randValue)·레벨 난수를 쓰지 않는다. 켜진 비트는 지금처럼 확인한다.
- `RandomTickMaskTest`: 쓰기 경로 10종(컬럼 setBlock·생성 블록·구역 직접 쓰기·디스크 적재 두 꼴·scratch+compact·copy·cowCopy·센 값 캐시·생성 결과 통째)을 섞은 무작위 연산 열에서 매 연산 뒤 마스크로 고른 구역 집합 == 칸 전수 집합 + 단언 검사. 변이 점검 포함.
- `RandomTickMaskParityTest`: 레거시 오라클 `LegacyRandomTickSections`(c470effb 의 옛 루프)와 새 루프를 같은 씨앗 레벨 두 개에서 돌려 블록 전부·레벨 난수·randValue 비교. 변이 점검 포함.

## 덤

- 구역 루프가 칸마다 `sections[si]` 를 다시 읽는다. 지금 기준에서는 같은 객체라 결과가 같고, 빛 작업(T-064)이 칸 사이에 얼린 구역을 복사본으로 갈아 끼워도 새 객체를 읽는다.
