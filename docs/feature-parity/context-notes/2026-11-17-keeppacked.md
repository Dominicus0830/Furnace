# 2026-11-17 블록 엔티티 `keepPacked` 저장

## 증상
FAWE 대조(T-198·N2b-6)에서 Furnace 가 저장한 블록 엔티티에 `keepPacked:0b` 가 없었다(Paper 는 저장하는 모든 블록 엔티티에 있음). 다시 만들지 않은 청크에서도 같았다 — Paper 월드의 청크를 읽어 되쓸 때, 상자(`ChestBlockEntity` 로 해석)와 서버가 만든 타일은 `keepPacked` 가 사라지고, NBT 그대로 든 것만 우연히 남았다.

## 바닐라 동작(1.21.1, 디컴파일 소스는 커밋하지 않음 — 기억과 실서버 `.mca` 관측에 근거, 정확한 메서드 본문은 가정)
- `LevelChunk.getBlockEntityNbtForSaving`: 살아 있는 블록 엔티티는 `saveWithFullMetadata` 뒤에 `putBoolean("keepPacked", false)`. 아직 풀지 않은 대기 NBT 만 `true`.
- 읽을 때 `keepPacked:1b` 면 NBT 를 풀지 않고 대기로 둔다. 저장 직전 `getBlockEntity` 가 대기 NBT 를 승격하므로, 완성(full) 청크에 쓰이는 값은 항상 `false` 다.
- Furnace 는 `Status=full` 청크만 쓴다.

## 고침
`AnvilChunkCodec.toNbt` 가 쓰는 모든 블록 엔티티(상자, 종류별, NBT 그대로 든 생성·보존분)에 `keepPacked:0b` 를 마지막 키로 붙인다. 디스크에서 `1b` 로 읽어 온 것도 `0b` 로 되쓴다(바닐라도 승격 뒤 `false`). 시험: `AnvilChunkCodecTest.savedBlockEntitiesCarryKeepPackedFalse`.

## 손대지 않은 것
- 플러그인·NMS 의 `BlockEntity.saveWithFullMetadata()`(`blockEntitySaveNow`)에는 `keepPacked` 가 없다 — 바닐라도 `saveWithFullMetadata` 자체에는 없고 청크 저장 길에서만 붙는다.
- 블록 엔티티 안 키 순서(해시 맵 순서)는 별개 차이로 남는다.
- 실제 Paper 와의 `.mca` 대조(데스크탑 재측정)는 하지 않았다 — 단위 시험까지만.
