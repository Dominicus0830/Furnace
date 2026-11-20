# NMS shim 5번 묶음 — Axiom·voxy 빈 곳 (2026-11-20)

4번 묶음 뒤 감사(`nmsShimAudit`)에서 Axiom 86개, voxy 23개가 남았다. 이 묶음은 작은 PR 여러 개(5a~5e)로 나눠 채우고,
도미니코에게 약속한 소요 시간 재추정을 위해 **PR 마다 "자동 연결 가능 비율"과 "걸린 시간"** 을 따로 적는다.

- **자동 연결 가능**: 바닐라 겉모양(이름·서술자·상수)과 이미 있는 Furnace 값만으로 한 줄~몇 줄에 이을 수 있어, 생성기(T-229)와
  연결 명세(T-232)가 생기면 사람이 설계하지 않아도 되는 것. 아직 그 도구가 없어서 이번에는 손으로 썼고, 분류만 한다.
- **설계 필요**: 스레드 소유(주인 region·다른 스레드 규칙), 새 서버 다리, 바닐라 동작 이식이 필요한 것.
- **걸린 시간**: 작업 시작부터 PR 을 올릴 때까지(구현·시험). 검토·CI·병합 대기는 따로 적는다.

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

자동 연결 가능 비율 **55%(16/29)**. 설계 필요 13개는 모두 새 서버 다리 하나(`ChunkInternals.entityLook`)를 거친다 — 다리를 한 번
설계하고 나면 게터 하나하나는 기계적이다. 즉 "설계 한 번 + 기계적 게터 여럿" 꼴이고, 다리가 생긴 뒤 비슷한 게터는 자동 연결로 넘어간다.

걸린 시간: 구현·시험 약 25분(20:13~20:38 UTC).

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

### 남긴 것

- 엔티티 `getName`(이름표·종류 이름)은 쓰는 표본이 없어 두지 않았다. 플레이어 `getName` 만.
- `getItemBySlot` 는 5a-2 로 뺐다(바닐라는 살아 있는 스택 객체를 주는데 compat 스택은 바꿀 수 있어서, 읽기 전용 사본 설계가 따로 필요).
