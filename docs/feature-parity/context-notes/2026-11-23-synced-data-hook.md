# 서버 칸 쓰기 훅(onSyncedDataUpdated) — 엔티티 데이터 D1a (2026-11-23)

설계: `docs/feature-parity/compat-entity-data-design-2026-11-23.md` 3.6절(독립 검토 필수 변경 3).

바닐라 `SynchedEntityData.set` 은 값이 바뀌면 `entity.onSyncedDataUpdated(accessor)` 를 부르고 나서 dirty 로 둔다. 서버 `SynchedData` 도 같은 순서로 엔티티 훅을 부르게 했다
(define·setQuiet 는 부르지 않음 — 바닐라도 define 때는 안 부르고, ItemEntity 스택 제자리 고치기는 set 을 거치지 않는다).
그동안 종류별 setter 안에 흩어져 있던 반응을 훅으로 옮겨, 서버 자신의 쓰기와 플러그인(NMS shim)의 칸 쓰기가 같은 반응을 지나게 했다.

## 바닐라 1.21.1 재정의 33개 분류 (Paper 1.21.1 jar 바이트코드로 확인)

| 옮김(서버에서 보이는 반응) | 반응 |
|---|---|
| Entity | POSE → refreshDimensions (서버 `refreshDimensions()` 새 자리, 기본 없음; AgeableMob 은 refreshBabyDimensions) |
| AgeableMob | BABY → refreshDimensions (setAge 가 따로 재던 것 제거) |
| Zombie·Piglin·Zoglin | BABY → refreshDimensions (setBaby 안 setDimensions 를 `refreshDimensions()` 재정의로) |
| Slime | SIZE → refreshDimensions, setYRot(yHeadRot), yBodyRot = yHeadRot, 물속이면 `random.nextInt(20) == 0` 일 때 물보라 — **전에는 방향·물보라 난수가 빠져 있었음** |
| Phantom | SIZE → updatePhantomSizeInfo — 바닐라 1.21.1 jar 에서 updatePhantomSizeInfo 를 부르는 곳은 훅 하나뿐. 그래서 처음 값과 같은 `setPhantomSize(0)`(finalizeSpawn·저장 읽기)은 공격력을 넣지 않아 크기 0 팬텀의 공격력 기본값은 몬스터 기본값 2 다. **이전 서버는 늘 6 을 넣었다(바닐라와 달랐음, 이 PR 로 맞춤)** |
| Pufferfish | PUFF_STATE → refreshDimensions |
| Interaction | WIDTH·HEIGHT → 상자 다시 만들기 |
| AreaEffectCloud | RADIUS → refreshDimensions |
| Shulker | ATTACH_FACE → setBoundingBox(makeBoundingBox()) |
| ArmorStand | CLIENT_FLAGS → refreshDimensions (blocksBuilding 은 서버에 아직 없음) |
| ItemFrame | ITEM → onItemChanged → recalculateBoundingBox (1.21.1 상자는 지도와 무관; 서버 스택은 불변이라 setEntityRepresentation 없음) |
| Painting | VARIANT → recalculateBoundingBox, 칸 번호로 이름 따라가기 |
| Camel | DASH(첫 틱 제외) → 쿨다운 0 이면 55. 자세는 Entity 훅이 크기 재기(setPose 재정의 제거) |
| Sniffer | STATE → refreshDimensions (애니메이션은 클라이언트 쪽) |
| Warden | 자기 재정의는 애니메이션뿐이지만 크기는 Entity POSE 훅 → refreshDimensions(파고들기·나오기 높이 1). setPose 재정의를 `refreshDimensions()` 재정의로 |
| FishingHook | BITING → biting 필드·아래로 당기기(syncronizedRandom). HOOKED_ENTITY 는 0 일 때만 비움 — region 안에 번호로 엔티티를 바로 찾는 길이 없어 0 이 아닌 값은 setHookedEntity 가 넣은 대상을 쓴다(가정: 플러그인이 이 칸에 다른 번호를 쓰는 경우는 드묾) |
| MinecartCommandBlock | LAST_OUTPUT → setLastOutput, COMMAND_NAME → setCommand(성공 횟수 0) |

| 두지 않음 | 까닭 |
|---|---|
| LivingEntity(SLEEPING_POS·LIVING_ENTITY_FLAGS), Pig·Strider(BOOST_TIME), EnderMan(CREEPY), EnderDragon(PHASE), Breeze | `level.isClientSide` 갈래만 있음 |
| Warden·Frog·Sniffer 애니메이션 상태, Display·TextDisplay·ItemDisplay·BlockDisplay | 애니메이션 상태·그리기 상태(클라이언트만 씀) |
| Guardian(ATTACK_TARGET) | 클라이언트 캐시 필드만 비움 |
| ItemEntity(ITEM) | setEntityRepresentation — 서버 스택은 불변 |
| Armadillo | 재정의가 super 만 부름(상태 틱은 서버 tick 이 따로 맞춤) |

## 함정
- **저장 읽기 차례:** 바닐라 `Entity.load` 는 회전을 읽은 직후(readAdditionalSaveData 전)에 머리·몸 방향을 yRot 으로 맞춘다. 서버는 이걸 LivingEntity.readAdditionalSaveData 안에서 하고 있어, Slime 이 super 전에 부르는 setSize 의 훅(`setYRot(yHeadRot)`)이 저장된 방향을 0 으로 덮었다(검토에서 찾음). 맞추는 자리를 `Entity.syncLoadedRotation()`(회전 직후)으로 옮겼다. 바닐라는 이 자리에서 `setYHeadRot` 를 불러 Goat 의 머리 돌림 제한이 옛 몸 방향 기준으로 걸리지만, 서버는 필드를 바로 넣는다(전과 같음, 후속 과제).
- 훅은 "값이 바뀔 때만" 불린다. 생성자에서 같은 값으로 setter 를 부르고 반응(크기·상자)에 기대던 곳은 setter 에 직접 호출을 남겼다(Slime.setSize 의 refreshDimensions, Painting.setVariant 의 recalculate — 바닐라 setSize 도 set 뒤 refreshDimensions 를 직접 부른다).
- AgeableMob 크기는 바닐라 서버도 나이 필드(`getAge`)로 잰다. 칸(BABY)만 바꾸면 훅은 불려도 어른 크기 그대로다(시험 `SyncedDataHookTest`).
- 전체 서버 시험은 이 변경으로 그대로 통과했다(로컬 43분, 금 파일 무변경).
