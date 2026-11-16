# 양동이 물이 구역을 무작위 틱 대상으로 만든다 (2026-11-14)

출처: N2b 설계 검토(PR #351, R6·R17)가 찾은 플러그인과 무관한 바닐라 차이.

## 바닐라 1.21.1 (LevelChunkSection, 서버 jar 바이트코드로 확인)
- `setBlockState`: 옛 상태가 공기가 아니면 nonEmpty-1, 무작위 틱이면 tickingBlockCount-1. **옛 유체가 비어 있지 않으면 tickingFluidCount-1.** 새 상태도 같게 +1.
- `recalcBlockCounts`(생성자 = 디스크 적재): 블록이 공기가 아니면 nonEmpty+, 무작위 틱이면 tickingBlock+. 유체가 비어 있지 않으면 nonEmpty+(두 번 셈), **`fluid.isRandomlyTicking()`(용암)일 때만 tickingFluid+.**
- `isRandomlyTicking() = tickingBlockCount > 0 || tickingFluidCount > 0`.
- 그래서 양동이로 물을 놓은 구역은 대상이 되고(물을 치우면 아님), 디스크에서 다시 읽은 구역은 물이 가득해도 대상이 아니다. 적재 구역의 물을 깨면 유체 수가 음수가 되어 다시 놓아도 한동안 대상이 아니다(바닐라 특이점, 그대로 따름).
- 대상인 구역에서 `tickChunk` 는 randomTickSpeed 번 칸을 뽑는다(`randValue` 가 나아감). 물 칸은 무작위 틱이 아니라 아무 일도 없다 → 관측되는 차이는 `randValue` 소비뿐.

## Furnace 수정
- `ChunkSection` 에 바닐라 `tickingFluidCount` 를 둔다(`fluidCount`): 칸 쓰기는 유체가 비어 있지 않은 상태를 ±1, 다시 셈(적재·통째 넣기·scratch 닫기)은 용암만 센다. 센 값이 모를(-1) 때 첫 칸 쓰기는 쓰기 전 블록으로 다시 센 뒤 ±1 을 쌓는다(바닐라 열과 같아야 해서).
- T-053 마스크: 비트는 "블록 수 ≠ 0 또는 유체 수 > 0" 이면 켜짐. 구역 쓰기가 비트를 고치는 구조는 그대로.
- 정답: `section-ticking-golden.txt` — 바닐라 구역 클래스를 직접 돌려 뽑은 칸 쓰기·재계수·전체 비우기 열(`SectionTickingVanillaParityTest`).

## 남은 차이(이 PR 범위 밖)
- **생성 직후 구역**: 바닐라는 생성이 `setBlockState` 로 칸을 채워 바다·물 구역이 새로 생성된 동안(저장 뒤 다시 읽기 전까지) 대상이다. Furnace 생성은 통째 넣기(재계수)라 아니다. 같게 하면 바다 구역 전부가 무작위 틱 루프를 돌아 T-053 이득이 줄어든다 — 도미니코 결정 필요(관측 차이는 `randValue` 소비뿐).
