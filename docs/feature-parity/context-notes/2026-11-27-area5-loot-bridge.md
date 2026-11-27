# 영역 5 루트 테이블 다리 (2026-11-27)

- 호환 계층 `LootTable.getRandomItems` → `NmsChunks.bridge().lootRoll` → 서버 `ChunkInternalsImpl.lootRoll` → `ServerCore.rollLoot`(서버 루트 시스템). 매개변수는 `ChunkInternals.LootRollParams` 레코드로 건넨다.
- 개체가 있으면 `NmsEntities.call` 로 소유 스레드에서 굴린다(개체 읽기는 소유 스레드만). 서버 루트 월드의 난수는 스레드 안전(ThreadLocalRandom).
- 옮긴 조각: LootContextParam·Params·ParamSet·ParamSets·LootParams·LootTable(머리는 `class X {`, 제네릭 금지).
- `LootContextParams.DAMAGE_SOURCE` 는 DamageSource 가 아직 main 컴파일에 없어 raw 형으로 둠(소거 뒤 같음).
- 아직 안 되는 것: `damage_source`/`block_entity` 매개변수(UnsupportedNmsCall), RandomSource 중복 정의, 서버 레지스트리에서 표 얻기(영역 8).
