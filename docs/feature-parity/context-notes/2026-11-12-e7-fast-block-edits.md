# 빠른 블록 쓰기(E7) — 바닐라 대조·함정 (2026-11-12)

- 바닐라 `Level.setBlock` 플래그 1 경로는 `blockUpdated(pos, 옛 블록)` + 비교기 출력 갱신, `/fill`·`/clone`·`/setblock` 은 플래그 2 로 놓은 뒤 `blockUpdated(pos, 지금 블록)`(비교기 출력 갱신 없음). 그래서 `AFTER_EDIT` 는 이웃 갱신 시점이 `/fill` 과 같고 IMMEDIATE 와는 다르다. 다만 `/fill` 은 `BlockInput.place`(gk.a)에서 놓기 전에 `updateFromNeighbourShapes`(dfy.b)로 상태를 이웃에 맞춘다 — editBlocks 는 준 상태 그대로(검토 R2, 돌 옆 울타리 east true/false). Furnace `BlockCommands.fill`·`CloneCommand` 와 같은 순서로 대조 시험(`BlockEditApiTest.afterEditMatchesFillCommandUpdateOrder`).
- 플래그 2 를 빼면 그 칸에서 번진 모양 갱신(`updateNeighbourShapes` → `updateOrDestroy`, 플래그 `& ~(1|32)` 그대로 전달)도 클라이언트에 안 간다. 그래서 청크째 재전송(CHUNKS)은 플래그 2 를 남기고, 다시 보내는 청크의 쌓인 칸 갱신만 버린다(`ChunkLevel.discardChanges`). 이웃 청크로 번진 갱신은 칸 갱신으로 간다.
- `onPlace`·`onRemove` 는 플래그와 무관하게 늘 돈다(모래 예약 틱, 상자 쏟기, 레드스톤 선 세기). "물리 끔"은 바닐라에 없다 — Bukkit `applyPhysics=false` 의 CraftBukkit 전용 플래그(1024, onPlace 건너뛰기)는 열지 않았다.
- `Clearable.tryClear` 는 같은 상태인 칸에도 바닐라 `/setblock` 이 부른다(내용물만 비고 "설정 실패"). API `clearContainers` 는 상태가 바뀌는 칸만 비운다 — API 옵션의 뜻이라 바닐라 명령 경로는 그대로.
- 변경 방송 칸 집합: 바닐라 `ChunkHolder.changedBlocksPerSection` 는 `ShortOpenHashSet`(해시 순서), Furnace 는 예전부터 오름차순(TreeSet). 4096비트 배열로 바꿔도 순서는 예전과 같다. 클라이언트에는 순서가 보이지 않는다.
- 압축 전 바이트만 보면 청크째 재전송(빛 배열 포함, 평지 청크 하나 약 54 KB)이 섹션 갱신(섹션 하나 약 8~12 KB)보다 크다. 압축 뒤에는 반대(16 청크 4.5 KB 대 111~128 KB) — 바이트 비교는 압축 문턱(256)을 넣고 해야 한다.
- 시험에서 청크 재전송 뒤 구독 ready 표시는 `chunkDelivered` 가 region 작업으로 하므로, 청크 패킷이 채널에 보인 직후에는 아직 false 일 수 있다 — region 왕복을 한 번 더 기다린다.
- 청크 다시 보내기의 재구독은 `World.resubscribeIfPresent`(computeIfPresent) — 구독 해제는 플레이어 region 스레드(`updateChunks`)에서 청크 주인 스레드와 동시에 돌 수 있어, 무조건 넣으면 떠난 플레이어 구독이 영구히 남는다(검토 R1). 재생성 교체도 같은 함수를 쓴다.
- `serializedChunkBody(cp)`(inline=false)는 스냅샷을 다음 작업 큐 차례에 뜬다 — 재전송은 `inlineOnOwner=true` 로 그 자리에서 떠야 앞의 빛 계산이 뜻이 있다(검토 R5).
