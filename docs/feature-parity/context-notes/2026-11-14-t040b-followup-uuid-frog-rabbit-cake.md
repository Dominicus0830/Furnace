## 2026-11-14 T-040b 후속: 월드 단위 UUID 중복 거부, 개구리 알·토끼 당근 게임 이벤트, 케이크 체력 재전송

근거: PR #330 독립 검토(`furnace/reviews/t040b-pr330.md`) R3(케이크)·R4·R5. R1(밭 낙하 피해)·R3 의 손 재전송·R6 은 #330 에 이미 반영됐다. 몹 생물 판정(R2)은 다른 스레드 담당이라 건드리지 않았다.
대조: 바닐라 1.21.1 디컴파일(extracted_minecraft_data), CraftBukkit `CakeBlock` 은 Paper `ver/1.21.4` 합본 패치에서 동작 확인(코드는 옮기지 않음).

### 1. 같은 UUID 를 월드 단위로 거부 (R5)
- 바닐라 `PersistentEntitySectionManager.addEntity` 는 `knownUuids` 로 레벨 전체에서 중복 UUID 를 거부한다("UUID of added entity already exists"). Furnace 의 `EntityStore.add` 는 region 저장소 단위라 다른 region 의 같은 UUID 를 막지 못했고, 월드 UUID 색인은 `put` 이라 나중 것이 덮은 뒤 먼저 지워지면 살아 있는 쪽을 잃을 수 있었다.
- `ChunkLevel.claimUuid(e)`(기본 참) 를 `RegionLevel` 이 월드 `entityUuidIndex` 의 `putIfAbsent` 로 구현. 이미 **살아 있는**(`isRemoved` 아닌) 다른 엔티티가 쥐고 있으면 거짓 → `addFreshEntity` 는 저장소에서 빼고 레벨을 되돌린 뒤 `false`, `adoptEntity` 는 저장소에서 빼고 받지 않는다(옛 region 중복 처리와 같은 뜻). 지워진 엔티티가 남긴 칸은 `replace` 로 갈아 끼운다.
- 색인에는 `EntitySpawnEvent` 취소·처리기가 지운 경우 `indexEntity(e,false)` 로 바로 놓는다. `addFreshEntityWithPassengers` 의 미리 검사에도 `uuidTakenElsewhere` 를 더했다.
- 같은 객체가 region 사이를 넘어가는 중(`prev == e`)은 통과한다. 핸드오프 때 색인은 빼지 않는다.
- 가정: 청크 내리기·저장으로 저장소에서 빠지는 엔티티는 `setRemoved`(UNLOADED_TO_CHUNK) → `entityRemoved` 를 거쳐 색인에서 빠진다(`chunkEntitiesForSave(..., true)`). 이 길로 안 빠지는 유령이 있으면 그 UUID 는 다시 못 들어온다 — 시험·전체 시험에서 걸리지 않았다.

### 2. 개구리 알 (R4)
- 바닐라 1.21.1 `TryLaySpawnOnWaterNearLand`: `setBlock(3)` → `gameEvent(BLOCK_PLACE, Context.of(mob, state))` → `playSound(FROG_LAY_SPAWN, SoundSource.BLOCKS)`. 게임 이벤트가 없었고 소리 분류가 `neutral` 이었다(소리 분류는 클라이언트 볼륨 슬라이더에 영향).

### 3. 토끼 당근 (R4)
- 바닐라 `RaidGardenGoal`(나이>0): `setBlock(age-1, 2)` → `gameEvent(BLOCK_CHANGE, Context.of(rabbit))`(새 상태 없음) → `levelEvent(2001)`. 빠져 있던 BLOCK_CHANGE 를 더했다(진동 감지기·감시자 등이 듣는다).
- 나이 0 갈래: 바닐라는 `setBlock(air, 2)` 뒤 `destroyBlock(pos, true, rabbit)` — 이미 공기라 `destroyBlock` 이 바로 false(드롭·이벤트 없음)라 관측 결과는 같다. 엔티티 인자만 토끼로 맞췄다.

### 4. 케이크 먹기 막힘 (검토 R3)
- Paper 는 EntityChangeBlockEvent 를 막으면 `sendHealthUpdate`(체력·허기·포화 SetHealth)를 다시 보낸다. 클라이언트가 먹기를 미리 예측해 허기를 올려 두기 때문. `BlockActor.resyncVitals()`(기본 빈 몸)를 더하고 `PlayerActor` 가 `PlayerVitals.currentHealthPacket()` 을 보낸다. 마지막 보낸 값 기록은 건드리지 않는다.

### 시험
`BlockEventGameEventsTest`(3): 개구리 이벤트·소리 순서·분류, 토끼 block_change, `resyncVitals` SetHealth. `PluginApiEntityChangeBlockTest.sameUuidInAnotherRegionIsRejectedAndKeepsTheLiveOne`: 다른 region 같은 UUID 거부(단건·승객 묶음), 거부된 쪽을 지워도 색인 유지, 살아 있는 쪽을 지운 뒤에는 다시 받는다.
