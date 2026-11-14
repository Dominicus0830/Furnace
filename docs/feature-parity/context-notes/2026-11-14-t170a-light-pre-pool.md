# T-170a 빛 미리 계산을 생성 풀 밖으로 (2026-11-14)

- `Region.loadChunk` 의 `thenApplyAsync(…, world.genPool())`(빛·POI·블록 엔티티 승격)를 `World.lightPreExecutor()`(`furnace-light-pre-N`, 코어/2 스레드, 데몬, 우선순위 NORM-1, 모든 차원 공유)로 옮겼다.
- 이유: genPool 은 생성 요청 대기열(FIFO)이라 생성이 끝난 청크의 빛 단계가 수백 개 뒤에 다시 줄을 섰다(`hop.genpool` 14~20초, W60-explore 2장). 디스크에서 읽은 청크도 남의 새 땅 생성에 막혔다.
- 결과 동일성: 이 단계는 아직 설치 전인 `ChunkColumn` 하나만 만지고(`precomputeLight`, `PoiManager.restore`, `promotePendingBlockEntities`) 이웃 가장자리는 올릴 때 맞춘다. 어느 스레드에서 돌든 결과가 같고 청크 간 순서 의존이 없다. 공유 상태 `world.lightStale()` 읽기는 원래도 생성 풀 여러 스레드에서 하던 것.
- 측정(32·60명 장면의 `hop.genpool`)은 이 PR 에서 하지 않았다 — 구조상 genPool 대기가 사라지므로 `hop.genpool` 은 빛 풀 대기(작업 수 ≈ 초당 완성 수 × 2~4ms)로 바뀐다. T-170 PR 에서 같은 장면과 함께 재면 된다.
- T-170(생성 대기열 재설계)이 같은 자리를 고치므로 이 PR 을 먼저 병합.
