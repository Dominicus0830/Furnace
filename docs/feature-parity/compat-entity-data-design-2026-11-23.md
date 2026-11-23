# 엔티티 데이터(메타데이터) shim 설계 — FancyNpcs 묶음 (2026-11-23, 검토 반영판)

> 독립 검토(고쳐서 승인) 필수 변경 5개 반영: 3.1(처음 값 둘), 3.2(차례 기록·최근 사본 읽기·packDirty 값), 3.3(#565 번호표·public/protected 만), 3.4(분류기 조건), 3.6(새 절, 서버 훅), 4·5절. 검토 보고서: `/mnt/project-files/furnace/reviews/entity-data-shim-design-review-2026-11-23.md`.

엔티티 영역 남은 구멍 151개 중 102개가 FancyNpcs 다. 거의 전부 "엔티티 데이터 칸"(바닐라 `SynchedEntityData`)에 기대는 것이라
setter 를 하나씩 손으로 채우기 전에 칸 모델을 먼저 정한다. 이 문서는 설계만이며, 구현은 독립 검토 뒤 단계별 PR 로 한다.

## 1. FancyNpcs 가 실제로 하는 일 (FancyNpcs-2.9.2.334, `Npc_1_21_1` 바이트코드에서 확인)

1. 몹 NPC: `EntityType$EntityFactory.create(type, level)` 로 **월드에 넣지 않는** 엔티티를 만든다. 플레이어 NPC 는 `new ServerPlayer(...)`(가짜 플레이어, 별도 설계).
2. 모양 바꾸기: `Cat#setVariant`, `Sheep#setColor`, `Goat#removeHorns`, `Panda#setMainGene`, `Villager#setVillagerData` … 같은 바닐라 setter,
   또는 `getEntityData().set(Goat.DATA_HAS_LEFT_HORN, …)`·`Player.DATA_PLAYER_MODE_CUSTOMISATION`·`Shulker.DATA_COLOR_ID` 를 직접 쓴다.
3. 보내기: `ReflectionUtils.getValue(entity.getEntityData(), "itemsById")` 로 **배열 필드를 반사로 꺼내** 칸마다 `DataItem#value()` 를 모아
   `new ClientboundSetEntityDataPacket(entity.getId(), list)` 를 보낸다. 생성은 `ClientboundAddEntityPacket(…)`.

즉 필요한 것은 (가) 종류별로 바닐라와 같은 칸 배열(번호·값 종류·처음 값), (나) 클래스별 `DATA_*` 접근자 상수, (다) 칸에 쓰는 setter 들이다.
패킷 쪽(`ClientboundSetEntityDataPacket` shim ↔ Furnace, `EntityDataBridge`)은 이미 있다.

## 2. 이미 있는 것

| 무엇 | 어디 |
|---|---|
| 칸 상수 207개(부류·이름·값 종류), 바닐라 jar 와 대조 끝 | `furnace-api` `EntityDataKeys` |
| 종류별 칸 배치(번호 → 값 종류) | 서버 `EntityData767.layout(kind)`, 리소스 `entity_data/layouts-1.21.1.txt`(서버 `EntityDataKeysTest` 가 대조) |
| 서버 엔티티의 칸 값(처음 값 포함) | 서버 `Entity.entityData`(`SynchedData`), `Entities.create(level, kind)` 로 종류마다 만들 수 있음 |
| shim `SynchedEntityData`(바닐라 모양: `itemsById`, `DataItem` 필드 순서, `packDirty`, `getNonDefaultValues`) | `furnace-compat-paper` `net/minecraft/network/syncher` |
| shim 값 ↔ Furnace 값 변환, 패킷 다리 | `EntityDataBridge` |
| 월드 밖 엔티티(Detached) 와 넣을 때 다시 틀기, 월드 엔티티 쓰기 `furnaceEdit` | 엔티티 fragment, `ChunkInternals.addNmsEntityNow` (#561·#564) |

## 3. 설계

### 3.1 종류별 처음 칸 값: 서버가 한 번 만들어 주는 표 (어댑터 다리 하나)

`ChunkInternals` 에 하나 추가한다.

```java
/** 그 종류의 바닐라 처음 칸 값(번호 순). 서버가 종류마다 한 번 만들어 둔다. 아무 스레드. */
default List<EntityDataSlot> entityDataDefaults(String kind) { throw new UnsupportedOperationException("entityDataDefaults"); }
record EntityDataSlot(int index, EntityDataKey<?> key, Object definedValue, Object constructedValue) {}
```

- **(검토 반영 1) 처음 값은 둘이다.** `definedValue` 는 `defineSynchedData` 에서 정한 값(바닐라 `DataItem.initialValue`, `getNonDefaultValues`·`isSetToDefault` 의 기준),
  `constructedValue` 는 생성자가 고친 뒤 값(바닐라 `DataItem.value` 의 처음 상태, 예: 슬라임 크기·공기 칸). shim `DataItem` 은 두 값을 각각 `initialValue`·`value` 에 넣는다.
  값은 API 값 종류(`EntityDataType` 의 값)로만 넘긴다(서버 내부 형 금지).

- 서버는 시험이 이미 쓰는 길(`Entities.create` 를 시험용 빈 level 에)로 종류마다 엔티티를 하나 만들어 `entityData` 를 읽고 버린다. 결과는 불변 목록으로 캐시.
- 칸 번호·값 종류는 `EntityData767.layout` 과 같아야 한다(시험으로 묶는다). 처음 값은 바닐라 `defineSynchedData` 값이며 서버 엔티티가 이미 바닐라와 대조돼 있다(`EntityDataKeysTest`).
  - 두 값 모두 129종류 전부 바닐라와 대조하는 관문을 둔다(4절 시험 2). 서버가 바닐라와 다르면 서버를 고친다.

이유: 처음 값을 shim 쪽에 손으로 다시 적으면 207칸 × 129종류의 두 번째 정답표가 생긴다. 서버 정의 한 곳만 정답으로 둔다.

### 3.2 shim 엔티티의 `getEntityData()`

- **월드 밖(Detached)**: 처음 부를 때 3.1 표로 `DataItem[] itemsById` 를 만든 진짜 `SynchedEntityData` 를 Detached 에 둔다. 값은 `EntityDataBridge` 의 Furnace→shim 변환을 쓴다.
  FancyNpcs 의 `itemsById` 반사가 그대로 동작하고, `set` 은 그 배열에 쓴다. 바닐라와 같은 의미다.
- 지금 Detached 가 따로 들고 있는 공유 깃발 비트(#561 `furnaceSetFlag`: 투명·발광·불)와 이름·소리 없음·얼음 시간은 이 칸 배열로 옮겨 **한 곳**에 둔다
  (`furnaceLook` 도 칸에서 읽는다). 두 군데 들고 있으면 `setInvisible` 뒤 `getEntityData().get(DATA_SHARED_FLAGS_ID)` 가 어긋난다.
- **월드에 넣을 때 (검토 반영 2)**: 월드 밖에서 칸을 쓸 **때마다** 그 쓰기를 `EntityOp.SetData(keyId, value)` 로 Detached 연산 기록에 차례대로 붙인다(지금 setter 연산과 같은 목록).
  넣을 때 "처음 값과 다른 칸" 을 몰아 쓰지 않는다 — `setCustomName(A)` 뒤 `load(이름 B)` 면 바닐라는 B 인데 몰아 쓰기는 차례가 틀어진다.
  `addNmsEntityNow` 가 기록을 그대로 다시 튼다(서버 쓰기는 3.6 의 훅을 지난다).
- **월드 엔티티**: `getEntityData()` 는 서버 칸을 보는 하위 클래스를 돌려준다. **(검토 반영 4)** `get` 은 어느 스레드에서도 막히지 않게 서버가 주인 스레드에서 갱신해 두는 최근 칸 사본에서 읽는다(ProtocolLib 등이 Netty 스레드에서 읽는다).
  `set` 만 `furnaceEdit(SetData)` 로 주인에게 넘긴다(`NmsEntityOwner` 규칙).
  `itemsById` 반사는 부른 때의 사본이다(반사로 배열을 고쳐도 서버에 안 간다 — 문서·시험에 적음). `packDirty` 는 이 보기로 쓴 칸을 **그 칸의 지금 서버 값**으로 돌려주고 서버 추적기의 더러움 표시는 건드리지 않는다
  (추적기는 주인 region 스레드에서 돈다. 바닐라·Paper 는 표시를 가져가 버리지만 여기서는 남는 차이가 같은 값 패킷 한 번 더 정도다.
  "Paper 와 같게" 원칙의 예외라 결정 항목으로 올림: `docs/user-todo/entries/2026-11-23-entity-data-packdirty.md`).
- 플레이어: 이번 범위 밖(가짜 플레이어 설계 뒤). 진짜 플레이어 `getEntityData` 는 읽기만 먼저.

### 3.3 `DATA_*` 접근자 상수: 기계 생성

- 클래스마다 바닐라 필드 이름(`DATA_HAS_LEFT_HORN`, `DATA_COLOR_ID` …)과 칸 번호·값 종류를 이어 `public static final EntityDataAccessor<T> X = new EntityDataAccessor<>(n, EntityDataSerializers.Y)` 를 만든다.
- **(검토 반영 5)** 입력: #565 의 지문 번호표(엔티티 번호·칸 표)와 Paper jar 의 필드 서명. 정적 초기화 순서로 번호를 짐작하는 새 추출은 만들지 않는다. 그래서 #565 병합이 D2 보다 먼저다.
- 명세에는 Paper 에서 링크할 수 있는 **public/protected 이름만** 적는다. 바닐라에서 private 인 `DATA_*` 가 필요하면 목록을 뽑아 따로 결정을 받는다
  (FancyNpcs 가 쓰는 `Goat.DATA_HAS_LEFT_HORN`·`DATA_HAS_RIGHT_HORN`·`Shulker.DATA_COLOR_ID`·`Player.DATA_PLAYER_MODE_CUSTOMISATION` 의 Paper 접근 수준은 D2 에서 jar 로 확인 — 지금은 가정).
- 커밋되는 것: 명세 줄(Mojang 이름이 들어간 shim 서명 — 지금 명세와 같은 등급)과 잠금 파일. 바닐라 원문·바이트코드는 커밋하지 않는다.
- 접근자 번호는 하위 클래스에서도 같다(바닐라 성질). 시험: 129종류 × 칸마다 `DATA_*` 번호 = `EntityData767.index(key)`.

### 3.4 칸에 쓰는 setter/getter: 프로그램이 채운다

FancyNpcs 102개 중 약 80개는 바닐라 몸통이 아래 네 모양 중 하나다(바닐라 바이트코드를 읽어 분류, 원문 복사 아님).

| 모양 | 바닐라 몸통 | 생성 몸통 |
|---|---|---|
| `SET` | `entityData.set(DATA_X, (박싱) arg)` | `getEntityData().set(DATA_X, arg)` |
| `GET` | `return (언박싱) entityData.get(DATA_X)` | 같은 모양 |
| `BIT` | 바이트 칸에 마스크 켜기/끄기(`setFlag(mask, b)` 류) | 같은 모양, 마스크 상수만 |
| `HOLDER` | 변형 레지스트리 홀더 칸 쓰기(`setVariant(Holder)`) | 3.5 값 종류가 생긴 뒤 |

- 분류기는 바닐라 jar 를 읽기만 한다(서명표 만들 때와 같은 등급). 커밋은 명세·잠금 파일만, 도구 소스에 바닐라 몸통 글을 넣지 않고, 명세에는 public/protected 이름만.
- 생성기에 몸통 종류 `DATA_SET`/`DATA_GET`/`DATA_BIT` 을 더하고, 분류기는 **모양이 정확히 맞을 때만** 줄을 만든다. 조금이라도 다른 몸통(조건, 다른 칸 같이 고침, 이벤트)은 분류하지 않고 지금처럼 "없음" 으로 남긴다.
- 분류 결과는 명세 줄로 떨어지고(사람이 고치지 않음), 잠금 파일과 자동 대조(`vanillaDiff` 틀, T-235)가 값까지 확인한다.
- 남는 것(약 20개: `Panda#roll`, `Camel#sitDown`, `Armadillo#switchToState` …)은 손 fragment. 바닐라에서 칸 외의 상태(애니메이션 시각, 브레인)를 건드리는 것은 월드 밖 엔티티에서 바닐라가 실제로 하는 일만 따른다.

### 3.5 값 종류 다리 넓히기

`EntityDataBridge` 가 아직 못 하는 종류 중 FancyNpcs 가 쓰는 것: `villager_data`, `cat_variant`, `wolf_variant`, `frog_variant`(레지스트리 홀더), `particles`(효과 입자).
shim 쪽 값 클래스(`VillagerData`, 홀더)가 지금 빈 껍데기라서, 이 묶음에서 값 레코드와 변환을 함께 채운다. `vector3`·`quaternion`(디스플레이 변환)은 JOML 로더 문제라 따로.

### 3.6 서버 칸 쓰기의 바닐라 훅 (검토 반영 3)

바닐라 `SynchedEntityData.set` 은 값이 바뀌면 `entity.onSyncedDataUpdated(accessor)` 를 부른다(크기 다시 재기, 상자, 상태 시각 등).
지금 서버 `SynchedData.set` 에는 이 훅이 없고, 같은 일이 Camel·Sniffer·Armadillo·Slime 등 종류별 setter 안에 흩어져 있다.
shim 이 원값만 쓰면 크기·충돌 상자·상태가 바닐라와 달라지므로, D1 에서 서버에 `onSyncedDataUpdated(key)` 훅을 바닐라와 같은 자리에 두고
종류별 setter 에 흩어진 반응을 그 훅으로 옮긴다(바닐라에 있는 재정의만, 종류별로 바닐라 대조). 플러그인 경로와 서버 자체 경로가 같은 반응을 지난다.

## 4. 시험

1. 129종류마다 shim `itemsById` 의 (번호, 값 종류) = `EntityData767.layout`, 처음 값 = 서버 엔티티 처음 값.
2. **관문** 처음 값 두 개의 바닐라 대조, 129종류 전부: 레벨 없이 못 만드는 종류가 많으므로 진짜 레벨이 있는 oracle 또는 넓힌 #565 추출기로 바닐라 값(`initialValue`·`value`)을 뽑아 대조표(지문·값만, 원문 없음)를 만들고 서버 표와 비교. 빠지는 종류 없이.
3. FancyNpcs 모양 시험: 월드 밖 고양이 → `setVariant`·`setCollarColor` → `itemsById` 반사 → `ClientboundSetEntityDataPacket` → 바이트로 인코딩해 같은 동작의 기대 바이트와 비교.
4. 월드 엔티티: 다른 스레드에서 `getEntityData().set` 이 주인에게 넘어가고, 추적기가 다른 플레이어에게 보내는지(봇 없이 서버 시험).
5. 넣기 재생: 월드 밖 쓰기 차례가 `addFreshEntity` 뒤 그대로인지(`setCustomName(A)` → `load(B)` → 넣기 = B).
6. 훅: 서버 칸 쓰기(`Slime` 크기, `Camel` 대시 등)가 바닐라와 같은 크기·상자·상태를 만드는지, 종류별 재정의 목록이 바닐라와 같은지.
7. 다른 스레드 읽기: Netty 같은 스레드에서 월드 엔티티 `get` 이 막히지 않고 최근 값을 돌려주는지.

## 5. 단계

| 단계 | 내용 | 검토 |
|---|---|---|
| D1 | 3.1 다리(두 값) + 3.2 월드 밖 `getEntityData`·차례 기록 + 깃발 비트 통합 + 3.6 서버 훅 + 시험 1·2·5·6 | 설계 코드 → 독립 검토 |
| D2 | 3.3 `DATA_*` 생성(생성기 변경, #565 병합 뒤) + 시험 | 도구 변경 → 독립 검토 |
| D3 | 3.4 분류기·몸통 종류 + 시험 2·3 | 도구 변경 → 독립 검토, 이후 분류 결과 묶음은 자동 대조 통과면 검토 생략(11-23 규칙) |
| D4 | 3.5 값 종류 | 독립 검토 |
| D5 | 3.2 월드 엔티티 보기(최근 사본 읽기) + 시험 4·7 | 동시성 → 독립 검토 |

예상: D1~D3 로 FancyNpcs 102개 중 약 75개, D4 까지 약 90개. 나머지는 가짜 플레이어(`ServerPlayer` 생성) 설계에 묶인다.

## 6. 검토에서 봐 줄 것

- 3.1 의 "서버 정의 한 곳이 정답" 이 맞는가, 아니면 처음 값을 바닐라 jar 에서 따로 뽑아 대조만 서버와 해야 하는가.
- 3.2 월드 엔티티 `packDirty` 를 바닐라와 다르게(추적기 표시 안 가져감) 두는 판단.
- 3.4 분류기가 바닐라 바이트코드를 읽는 것이 "Mojang 원문 커밋 금지" 와 부딪히지 않는가(커밋은 명세 줄만).
