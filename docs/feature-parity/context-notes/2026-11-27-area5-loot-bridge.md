# 영역 5 루트 테이블 다리 (2026-11-27)

- 호환 계층 `LootTable.getRandomItems` → `NmsChunks.bridge().lootRoll` → 서버 `ChunkInternalsImpl.lootRoll` → `ServerCore.rollLoot`(서버 루트 시스템). 매개변수는 `ChunkInternals.LootRollParams` 레코드로 건넨다.
- 개체가 있으면 `NmsEntities.call` 로 소유 스레드에서 굴린다(개체 읽기는 소유 스레드만). 서버 루트 월드의 난수는 스레드 안전(ThreadLocalRandom).
- 옮긴 조각: LootContextParam·Params·ParamSet·ParamSets·LootParams·LootTable(머리는 `class X {`, 제네릭 금지).
- `LootContextParams.DAMAGE_SOURCE` 는 DamageSource 가 아직 main 컴파일에 없어 raw 형으로 둠(소거 뒤 같음).
- damage_source: 호환 계층에 DamageSource 형이 아직 없어 typeHolder·getEntity·getDirectEntity 를 이름으로 읽는다(형이 생기면 직접 호출로). 서버는 damage_type 태그 표로 `is` 를 답한다.
- block_entity: 호환 BlockEntity 의 저장 꼴을 API 로 보내고 서버가 떨어진 블록 엔티티에 실어 collectComponents(스레드 제약 없음).
- 아직 안 되는 것: RandomSource 중복 정의, 서버 레지스트리에서 표 얻기(영역 8).
- 검토 반영: 개체 없이 위치만 준 굴리기는 `NmsOwner.call` 로 그 칸 청크 주인 스레드에서(위치·바이옴 조건이 조용히 거짓이 되지 않게). 플러그인 동적 드롭 콜백(`LootParams.DynamicDrop`)은 API 소비자 꼴로 서버에 넘겨 `LootParams.dynamicDrop` 에 건다(표의 `minecraft:dynamic` 항목이 바닐라처럼 호출).
- 시험: LootRollSeedTest(시드 준 경우 결정적·월드 난수 안 씀, 0 이면 표의 난수 시퀀스), LootRollBlockEntityTest(저장 꼴 → 떨어진 블록 엔티티 → 컴포넌트).
- 검토 반영 2: is_direct 는 원인·직접 가해자의 UUID 가 같으면 같은 LootEntity 를 줘서 판정(API 객체 동일성에 기대지 않음). 이름으로 읽다 껍데기가 던진 오류는 그대로 던지고, 메서드 자체가 없는 값만 UnsupportedNmsCall.
- 끝에서 끝까지 시험 NmsLootBridgeTest(위치만 준 굴리기·시드·동적 드롭 sherds·피해 원인 모양·메서드 없는 값). 로컬 바닐라 데이터(tools/vanilla-data)가 없으면 건너뛴다.
- **엔티티 영역 할 일**: 호환 계층에 실제 `DamageSource` 형(typeHolder·getEntity·getDirectEntity)이 생기면 `LootTable.getRandomItems` 의 이름으로 읽기(리플렉션)를 직접 호출로 바꾸고, `LootContextParams.DAMAGE_SOURCE` 의 raw 형을 `LootContextParam<DamageSource>` 로 되돌린다.
