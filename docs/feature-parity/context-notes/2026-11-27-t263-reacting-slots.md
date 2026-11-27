# T-263 S2 — 반응 있는 칸에 쓰는 setter 13개 확인 (2026-11-27)

결정: `docs/user-todo/entries/2026-11-27-t263-s2-shape-proof.md`(도미니코 11-27 "ㅇㅇ") — 반응 있는 칸의 쓰기는 모양 증명에서 빼고,
Furnace 서버가 칸이 바뀔 때 바닐라와 같은 반응을 하는지 코드로 확인한 뒤 넣는다. 반응이 빠졌으면 먼저 구현한다.

## 길

연결 줄 `@idiom(entity-data-set, 칸)` → `NmsEntityData.set` → 월드 엔티티는 `NmsWorldEntityData.set` → 주인 스레드에서 서버 칸
(`SynchedData.set`) → 값이 바뀌면 `Entity.onSyncedDataUpdated(칸 번호)`(바닐라 `SynchedEntityData.set` → `onSyncedDataUpdated` 와 같은 자리).
그래서 확인할 것은 **Furnace 서버 엔티티의 훅이 바닐라 훅과 같은 서버 쪽 효과를 내는가** 하나다. 월드 밖 엔티티는 같은 서버 엔티티에 조작을
재생하므로 같은 훅을 지난다. 바닐라 훅은 바닐라 1.21.1 서버 jar 를 javap 로 읽어 분류했고(코드는 옮기지 않음), Paper 서버 jar 의 훅은
필드 소유 클래스 표기만 다르고 같다(Camel·BlockDisplay 는 `Entity.firstTick`·`Display.updateRenderState` 처럼 접근 범위만 넓힘).

## 줄마다

| setter | 바닐라 훅이 하는 일 | 서버 쪽 효과 | Furnace | 줄 |
|---|---|---|---|---|
| Display 높이·너비 | `updateCulling()` | 없음 — `noCulling`·그리기 범위는 클라이언트 그리기만 쓴다 | 훅 없음(맞음) | 넣음 |
| Display 보간 시간 | `updateInterpolationDuration = true` | 없음 — `tick()` 의 `isClientSide` 갈래만 읽는다 | 훅 없음(맞음) | 넣음 |
| BlockDisplay 블록, ItemDisplay 아이템 | `updateRenderState = true` | 없음 — 위와 같이 클라이언트 갈래만 | 훅 없음(맞음) | 넣음 |
| Guardian 공격 대상 | `clientSideAttackTime = 0`, `clientSideCachedAttackTarget = null` | 없음 — 서버 `getActiveAttackTarget` 은 `getTarget()`, 두 필드는 `aiStep` 의 클라이언트 갈래·그리기만 읽는다 | 훅 없음(맞음) | 넣음 |
| Camel 돌진 | `!firstTick` 이면 `dashCooldown = dashCooldown == 0 ? 55 : dashCooldown` | 있음 | 같은 훅(`DASH_COOLDOWN_TICKS = 55`) | 넣음 |
| Painting 종류 | `recalculateBoundingBox()` | 있음 | 같은 훅(종류 이름 갱신 + 상자 다시 재기) | 넣음 |
| Shulker 붙은 면 | `setBoundingBox(makeBoundingBox())` | 있음 | 같은 훅(`refreshBoundingBox` — Shulker `makeBb`) | 넣음 |
| Interaction 높이·너비 | `setBoundingBox(makeBoundingBox())` — 상자는 칸 너비·높이(`getDimensions()`)로, 몸 크기(`getBbWidth`)는 그대로 | 있음 | **고침**: 전에는 몸 크기(`dimW`·`dimH`)까지 바꿨고 새 엔티티 상자가 0×0 이었다. 이제 `makeBb` 가 칸으로 상자를 만들고 훅·저장 읽기는 상자만 다시 만든다. 몸 크기는 자세가 바뀔 때(`refreshDimensions`)만 칸을 따른다(눈 높이 0.85 배, noPhysics 라 자리 밀기 없음) | 넣음 |
| Armadillo 상태 | `inStateTicks = 0` | 있음(껍질 숨기·겁먹기 판정) | **고침**: 전에는 `switchToState` 안에서만 0 으로 돌렸다(칸에 바로 쓰면 빠짐). 이제 훅에서 | 넣음 |
| Pufferfish 부풀기 | `refreshDimensions()` | 있음 — 크기와 **자리 밀기**(`fudgePositionAfterSizeChange`: 커지면 빈 곳을 찾아 위치를 옮김, 첫 틱·noPhysics·4 넘는 몸·플레이어 제외) | 크기는 같지만 **자리 밀기가 없다** — Furnace `refreshDimensions` 전부(아기 자람·슬라임 등)에 빠져 있다 | **뺌**(자리 밀기 이식 뒤) |

시험: `SyncedDataHookTest`(상호작용 상자·몸 크기, 아르마딜로 칸 쓰기 → 상태 틱 0).
줄 파일: `furnace-compat-paper/src/shimspec/links-1.21.1/2-entity-core.links`(손 줄 12, `proof=review`).
