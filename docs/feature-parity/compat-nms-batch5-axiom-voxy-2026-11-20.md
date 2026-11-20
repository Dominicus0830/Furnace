# NMS shim 5번 묶음 — Axiom·voxy 빈 곳 (2026-11-20)

4번 묶음 뒤 감사(`nmsShimAudit`)에서 Axiom 86개, voxy 23개가 남았다. 이 묶음은 작은 PR 여러 개(5a~5e)로 나눠 채우고,
도미니코에게 약속한 소요 시간 재추정을 위해 **PR 마다 "자동 연결 가능 비율"과 "걸린 시간"** 을 따로 적는다.

- **자동 연결 가능**: 바닐라 겉모양(이름·서술자·상수)과 이미 있는 Furnace 값만으로 한 줄~몇 줄에 이을 수 있어, 생성기(T-229)와
  연결 명세(T-232)가 생기면 사람이 설계하지 않아도 되는 것. 아직 그 도구가 없어서 이번에는 손으로 썼고, 분류만 한다.
- **설계 필요**: 스레드 소유(주인 region·다른 스레드 규칙), 새 서버 다리, 바닐라 동작 이식이 필요한 것.
- **다리 위 기계적**: 새 서버 다리(스레드 규칙을 지키는 길) 하나를 설계한 뒤에는 그 위에서 한 줄씩 잇기만 하면 되는 것. 다리가 생기면 연결
  명세(`read=owner`·`write` 줄)로 쓸 수 있는 꼴이다.
- **걸린 시간**: 작업 기록(명령 시각, UTC)으로 잰 시작~커밋. 검토·CI·병합 대기는 따로 적는다.

## 묶음 나눔

| 묶음 | 내용 | 주 사용처 |
|---|---|---|
| 5a | 엔티티·플레이어 모습과 탑승 읽기, 작은 바닐라 모양 | Axiom 엔티티 조작, voxy 먼 플레이어 모습 |
| 5a-2 | 플레이어 장비 칸 읽기(`getItemBySlot`) | voxy |
| 5b | 태우기·내리기·순간이동·살아 있는 엔티티 불러오기·저장 | Axiom 엔티티 조작·소환 |
| 5c | 월드 속성(날씨·시간·게임 규칙), 비행 능력·게임 모드 | Axiom 월드 속성 |
| 5d | 블록 놓기(이벤트·마을 지점·빛·블록 엔티티) | Axiom 블록 버퍼 |
| 5e | 코덱·NBT·블록 상태 문자열, 청크 읽기·월드 설정 | Axiom 버퍼, voxy |

## 5a — 모습과 탑승 읽기

**채운 기호 29개**(감사 표 기준, Axiom 21 + voxy 8, 겹치는 것은 한 번).

| 분류 | 개수 | 기호 |
|---|---|---|
| 자동 연결 가능 | 16 | `getBlockX/Y/Z`(엔티티 3·플레이어 2), `registryAccess`, `hasDisconnected`, 플레이어 `getName`, `Entity.RemovalReason`, `EntityType.PLAYER`·`canSerialize`, `Level.isInSpawnableBounds`, `getChunkIfLoaded(BlockPos)`, `BlockState.is(HolderSet)`, `IntegerProperty.min`·`max` |
| 설계 필요 | 13 | `isCrouching`·`isSwimming`·`isInvisible`·`isFallFlying`·`isSpectator`·`hasPermissions`·`getKnownMovement`·`getVehicle`(엔티티·플레이어)·`isPassenger`·`hasPassenger`·`getSelfAndPassengers`·`getIndirectPassengers` |

자동 연결 가능 비율 **55%(16/29)**, 다리 위 기계적 12, 설계 필요 1(`getKnownMovement` 의 바닐라 갈래) + 다리 1건. 설계 필요 13개는 모두 새 서버 다리 하나(`ChunkInternals.entityLook`)를 거친다 — 다리를 한 번
설계하고 나면 게터 하나하나는 기계적이다. 즉 "설계 한 번 + 기계적 게터 여럿" 꼴이고, 다리가 생긴 뒤 비슷한 게터는 자동 연결로 넘어간다.

걸린 시간(작업 기록의 명령 시각, UTC): 첫 명령 20:13:53(1~4번 묶음 병합 보고 직후) → 커밋·PR 20:27, 모두 **약 14분**.
- 20:13~20:18 분석 5분 — 5a 만이 아니라 5번 묶음 전체(Axiom 86·voxy 23)의 빈 곳을 표본 jar 에서 열어 나눈 시간.
- 20:18~20:24 구현 6분, 20:24~20:27 시험·고침 3분.
- 검토 지적 고침(스레드 오류를 예전 값으로 덮던 것 등)은 따로: 20:34~(아래 "검토 뒤 고침").

### 설계

- 서버 다리 `entityLook(엔티티)` 가 공유 플래그·자세·알린 움직임·날기·관전·권한 단계·탈것·승객 UUID 를 한 번에 돌려준다.
  - 플레이어는 어느 스레드에서나 최근 값(Paper 도 다른 스레드에서 필드를 경쟁 읽기 한다 — 플레이어 게터·속성과 같은 규칙).
  - 다른 엔티티는 주인 스레드에서만. compat 쪽은 `NmsEntities.call` 규칙(틱 스레드가 아니면 주인 region 에 물어 기다림, 다른 region·전역
    스레드는 `ThreadOwnershipException`).
  - 사라진 엔티티는 마지막으로 읽은 값(바닐라도 지운 엔티티의 필드는 그대로 읽힌다). 한 번도 못 읽었으면 예외.
- `getKnownMovement` 는 바닐라대로: 조종하는 승객이 플레이어이고 살아 있으면 그 플레이어가 알린 움직임, 아니면 지금 속도. 플레이어는
  남이 조종하는 탈것에 타 있으면 그 탈것의 값.
- 탈것·승객은 UUID 로 받아 이미 만든 Bukkit 객체를 먼저 쓴다(어느 스레드나 된다). 없으면 그 월드에서 찾는다.
- 아직 월드에 없는 엔티티(FAWE 붙여넣기용)는 바닐라 생성자 모습(플래그 0·서 있음·속도 0·탑승 없음).

### 검토 뒤 고침

- 다른 region·전역 스레드에서 읽으면 한 번 읽어 둔 값이 있어도 `ThreadOwnershipException` 을 그대로 던진다(전에는 "사라짐"과 같은 예외 계열이라
  예전 값으로 덮였다).
- 탈것·승객을 UUID 로 찾을 때도 틀린 스레드면 같은 예외(전에는 조용히 null 이라 `isPassenger()` 참과 모순).
- 플레이어 `getKnownMovement` 는 마지막 이동 패킷이 옮긴 거리라서 바닐라 값과 다를 수 있음을 주석에 적었다(가정).
- 시험 보강: 공유 플래그가 참인 경우(투명), `getKnownMovement` 두 갈래(조종하는 플레이어가 있는 배·없는 돼지), 틀린 스레드.

### 남긴 것

- 엔티티 `getName`(이름표·종류 이름)은 쓰는 표본이 없어 두지 않았다. 플레이어 `getName` 만.
- `getItemBySlot` 는 5a-2 로 뺐다(바닐라는 살아 있는 스택 객체를 주는데 compat 스택은 바꿀 수 있어서, 읽기 전용 사본 설계가 따로 필요).

## 5a-2 — 플레이어 장비 칸 읽기

**채운 기호 1개**(`ServerPlayer.getItemBySlot`, voxy). 자동 연결 가능 **0/1** — 바닐라는 칸에 든 객체 자체를 주는데, Furnace 에서 그 칸은 서버의
주인 스레드가 들고 있어 같은 객체를 줄 수 없다. 어떤 값을 줄지가 설계 문제다.

걸린 시간: 약 5분(20:27:52 PR 직후 ~ 20:32 커밋).

- 접속한 플레이어: 어느 스레드에서나 지금 칸(Bukkit 인벤토리와 같은 값 — 예약된 쓰기까지 보인다)의 **읽기 전용 사본**. 빈 칸은 바닐라처럼
  `ItemStack.EMPTY`, 몸 칸(BODY)은 플레이어에게 없어 빈 스택.
- 읽기 전용 사본을 바꾸는 호출(`setCount`·`grow`·`shrink`·`split`·`set`·`remove`·`applyComponents`)은 `UnsupportedNmsCall` — 고쳐도 칸이 따라가지
  않아 조용히 다른 결과가 나는 것을 막는다. `copy()` 는 다시 바꿀 수 있다.
- 아이템 쓰기(`useOn`·`use`·`useItemOn`)에 읽기 전용 사본이 들어오면 서버에 보내기 전에 거절한다(블록을 놓은 뒤 되쓰다 실패하지 않게).
- 바닐라 생성자로 만든 월드에 없는 플레이어는 든 객체 그대로(갑옷 칸은 넣는 길이 없어 빈 스택).
- 접속한 플레이어의 `getItemInHand` 는 그대로 막아 둔다 — 이 값은 쓰기 문맥(`UseOnContext`)이 고치는 객체라서.
- 결정 기록: `docs/user-todo/entries/2026-11-20-shim5-equipment-read-only.md`. 함정: enum switch 가 만드는 합성 클래스(`ServerPlayer$1`)가 서명 관문을
  깨뜨린다 — `docs/feature-parity/context-notes/2026-11-20-shim5-enum-switch-synthetic.md`.

## 5a 검토 고침

걸린 시간: 약 5분(20:33:52 검토 도착 ~ 20:38:24 푸시).

## 5b 앞부분 — 월드 엔티티 저장·싣기·옮기기·태우기

**채운 기호 7개**(Axiom `ManipulateEntityPacketListener`): `saveWithoutId`·`setPosRaw`·`teleportTo(ServerLevel, …, Set<RelativeMovement>, …)`·
`setYHeadRot`·`startRiding(e, force)`·`stopRiding`·`ejectPassengers`. 그리고 이미 있던 `load` 를 월드의 엔티티에도 되게 했다.

| 분류 | 개수 | 기호 |
|---|---|---|
| 자동 연결 가능 | 0 | — |
| 다리 위 기계적 | 5 | `setPosRaw`·`setYHeadRot`·`startRiding`·`stopRiding`·`ejectPassengers` |
| 설계 필요 | 2 | `saveWithoutId`(플레이어는 /data 비교용 키를 뺀다), `teleportTo`(같은 레벨만 — 다른 레벨은 Furnace 에서 비동기라 아직 없음) |

새 다리 1건(`ChunkInternals.entityEditNow(엔티티, EntityEdit)` — 봉인 레코드 하나로 조작을 모아 다리 표면을 늘리지 않았다).

걸린 시간: 약 8분 — 분석 20:32~20:34, 구현·시험 20:38~20:44(그 사이 5a 검토 고침).

- 스레드 규칙은 `save`·`discard` 와 같다(`NmsEntities`: 주인 스레드면 바로, 틱 스레드가 아니면 기다림, 다른 region·전역은 예외).
- 플레이어는 저장 읽기만 되고 이동·탑승은 `UnsupportedNmsCall`(플레이어는 접속 처리기를 거쳐 움직인다 — Bukkit `teleport`·`addPassenger`).
- 월드 엔티티의 `load` 는 `/data` 와 같은 길이다. 넣는 NBT 의 UUID 가 지금과 다르면 거절한다(서버의 엔티티 색인이 깨진다). Axiom 은 같은 엔티티를
  저장해 고친 것을 다시 실으므로 늘 같은 UUID 다.
- 시험에서 바닐라 그대로임을 확인: 내린 직후 강제 없는 `startRiding` 은 거짓(바닐라 `boardingCooldown` 60 틱), `teleportTo` 는 위아래 각을 ±90 으로 자른다.
- **미룸(생성기 스레드와 합의)**: `HangingEntity`·`ItemFrame`·`Marker` 종류별 shim 과 `loadEntityRecursive`·`tryAddFreshEntityWithPassengers`·
  `moveTo`·`saveAsPassenger`·`remove(이유)`·`RelativeMovement.unpack` 은 생성기의 엔티티 사슬 단계(명세 줄과 조각) 위에 다시 얹는다.

## 5c — 월드 속성·게임 모드·능력

**채운 기호 13개**(Axiom 월드 속성·시간·게임 모드·비행 속도 패킷).

| 분류 | 개수 | 기호 |
|---|---|---|
| 자동 연결 가능 | 11 | `ServerLevel.RAIN_DELAY`·`RAIN_DURATION`·`THUNDER_DURATION`, `IntProvider`, `setWeatherParameters`, `setDayTime`, `getGameRules`, `GameRules.getRule`·`RULE_DAYLIGHT`·`BooleanValue`, `ServerPlayer.setGameMode` |
| 설계 필요 | 2 | `ServerPlayer.getAbilities`, `Abilities`(바닐라는 플레이어가 든 객체의 공개 필드를 고친다) |

자동 연결 가능 비율 **85%(11/13)**. 규칙 열쇠 51개는 서버 jar 에서 기계로 뽑아 넣었다(규칙 하나를 쓰는 플러그인이 다른 규칙도 부를 수 있어서).

걸린 시간: 약 6분(20:42~20:48, 5b 시험과 2분 겹침).

- 시간·날씨·규칙은 이미 있는 Furnace 길(Bukkit 어댑터와 같은 것)에 그대로 잇는다. 아무 스레드.
- Paper 와 다른 점(이미 정한 것과 같다): 규칙은 서버에 한 벌, 시간·날씨는 오버월드 시계 하나(다른 월드에서 쓰면 경고 한 번 뒤 무시).
  `setDayTime` 은 접속자에게 곧바로 알린다(바닐라는 다음 20 틱 알림 때) — 시계가 최대 1초 일찍 맞춰질 뿐 값은 같다.
- `Abilities` 는 서버 값을 비춘 보기다. 세터(`setFlyingSpeed` 등)는 곧바로 서버에 쓰고, 공개 필드를 직접 고친 것은 다음 세터나 `onUpdateAbilities`
  (패킷도 보낸다) 때 쓴다. 고친 필드가 없으면 `getAbilities` 가 서버 값으로 다시 맞춘다(게임 모드가 바뀌면 따라온다).

## 지금까지 모음(5a~5c)

| 묶음 | 기호 | 자동 연결 가능 | 다리 위 기계적 | 설계 필요 | 걸린 시간 |
|---|---|---|---|---|---|
| 5a | 29 | 16 | 12 | 1 (+다리 1) | 14분 |
| 5a-2 | 1 | 0 | 0 | 1 | 5분 |
| 5b 앞부분 | 7 | 0 | 5 | 2 (+다리 1) | 8분 |
| 5c | 13 | 11 | 0 | 2 | 6분 |
| 합계 | 50 | 27 (54%) | 17 (34%) | 6 (12%) + 다리 2 | 33분 (+검토 고침 5분) |

다리가 생긴 뒤의 기계적 연결까지 넣으면 88%(44/50)가 사람의 설계 없이 이을 수 있는 꼴이었다. 손으로 쓴 시간의 대부분은 설계 필요 6개와
다리 2건, 그리고 바닐라 값 시험이었다.

