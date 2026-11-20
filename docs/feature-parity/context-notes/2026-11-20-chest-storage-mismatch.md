# 2026-11-20 칸 블록과 안 맞는 블록 엔티티(상자 등) 저장

## 차이
저장 파일이나 월드젠이 남긴 블록 엔티티 NBT 의 종류가 그 칸 블록과 안 맞을 때(예: 돌 자리에 `minecraft:chest`, 덫 상자 자리에 `minecraft:chest`) Furnace 는 경고만 하고 NBT 를 그대로 들고 있다가 다시 저장했다("데이터를 잃지 않게"). 바닐라는 버린다.

## 바닐라 1.21.1 (Paper 1.21.1 jar 의 매핑된 바이트코드로 확인 — 이 부분은 Paper 가 안 건드린 바닐라 메서드)
- `LevelChunk.getBlockEntity(pos, CHECK)`: 대기 맵(`pendingBlockEntities`)에서 **remove** 하고 `promotePendingBlockEntity` 로 올린다. 올리기가 null 이면 그대로 null.
- `promotePendingBlockEntity`: `BlockEntity.loadStatic(pos, state, tag)`. `BlockEntity` 생성자가 `validateBlockState` 로 `type.isValid(state)` 를 검사해 안 맞으면 `IllegalStateException` → `loadStatic` 이 로그만 남기고 null → "Tried to load a block entity for block {} but failed at location {}" 경고 후 버림.
- `getBlockEntityNbtForSaving`: 살아 있는 것만 `keepPacked:0b` 로 저장, 대기 맵에 남은 것은 `true` 로 저장 — 그런데 위 `getBlockEntity` 가 이미 맵에서 뺐으므로 승격에 실패한 것은 저장에 안 나온다(`2026-11-17-keeppacked.md` 와 같은 결론).
- 관측 차이: Furnace 는 그 자리에 나중에 상자를 놓으면 옛 NBT 를 물려받을 수 있었고(`adoptGeneratedChest`), 청크를 저장할 때마다 안 맞는 항목이 .mca 에 계속 남았다.

## 고침
- `AnvilChunkCodec.fromNbt`: 상자·이식한 종류가 칸 블록과 안 맞으면 경고(`codec.be-mismatch` 문구를 "버림" 으로 바꿈)만 하고 저장하지 않는다.
- `ChunkColumn.promote`(월드젠이 남긴 NBT 승격): 같은 조건의 대기 NBT 를 `generatedBlockEntities` 에서 지운다(`dropMismatchedGenerated`, 저장 대상으로 표시). 저장 경로에는 새 검사·복사·락이 없다.
- 시험: `AnvilChunkCodecTest.mismatchedBlockEntityIsDroppedOnLoadLikeVanilla`, `mismatchedGeneratedBlockEntityIsDroppedOnPromote`(수정 전 실패 확인).

## 손대지 않은 것
- 서버가 아직 모르는 종류(`BlockEntities.known` 이 false)는 칸 블록 검사 표가 없어 NBT 그대로 보존한다(바닐라는 종류별 표로 검사한다). 이식할 때 `isValid` 와 함께 같아진다.
- `id` 없는 구조물 템플릿 NBT 는 블록이 정한 종류로 승격하는 기존 길 그대로.
