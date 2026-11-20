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

- 접속한 플레이어: 어느 스레드에서나 그 칸의 **살아 있는 보기**(검토 고침 — 처음엔 읽기 전용 사본이었는데 #536 이 손 칸을 같은 꼴로 만들어 맞췄다).
  고치면(`setCount`·`shrink`·`set`·`setDamageValue` …) 그 칸에 되쓰고, 그새 칸이 다른 스택으로 바뀌었으면 칸에서 떨어진다. 손 칸은
  `getItemInHand` 와 같은 칸(`ItemStack.liveHand`), 갑옷 칸은 창 번호 5~8 (`ItemStack.liveSlot`, 새 다리 `windowSlotNow` — 손 칸 다리와 같은 잠금).
  빈 칸은 바닐라처럼 `ItemStack.EMPTY`, 몸 칸(BODY)은 플레이어에게 없어 빈 스택. 서버 인벤토리의 지금 값을 읽으므로 틱 스레드가 아닌 곳에서 Bukkit 으로
  예약한 쓰기는 주인이 넣은 뒤(다음 틱)에 보인다(손 칸 보기와 같다).
- 바닐라 생성자로 만든 월드에 없는 플레이어는 든 객체 그대로(갑옷 칸은 넣는 길이 없어 빈 스택).
- 결정 기록: `docs/user-todo/entries/2026-11-20-shim5-equipment-live-slot.md`. 함정: enum switch 가 만드는 합성 클래스(`ServerPlayer$1`)가 서명 관문을
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
- **알려진 한계(검토 보강)**: `teleportTo`·`setPosRaw`·`load` 로 엔티티를 한 틱 안에 **다른 region 의 청크**로 옮기면, 틱 끝 넘기기
  (`Region.handOffEntities`) 전까지 서버는 위치로 주인을 다시 따지므로 같은 작업 안의 다음 조작(`setYHeadRot`·`startRiding` 등)은
  `ThreadOwnershipException` 이다. 다음 틱(새 주인 region 스레드)부터는 된다. 같은 region 안의 옮기기(Axiom 의 보통 꼴 — 엔티티를 몇 칸 끌기)는 상관없다.
- 플레이어 `saveWithoutId` 는 서버 플레이어 저장 꼴(`/data` 와 같은 것)이라 `SelectedItem` 을 뺀 것 말고도 바닐라 공통 필드 일부(`Motion`·`Fire` 등)가 없다.
  사라진 엔티티의 `saveWithoutId` 는 `UnsupportedNmsCall` 이다(바닐라는 그 객체의 마지막 값을 준다 — 낮은 우선순위 차이).

## 5c — 월드 속성·게임 모드·능력

**채운 기호 13개**(Axiom 월드 속성·시간·게임 모드·비행 속도 패킷).

| 분류 | 개수 | 기호 |
|---|---|---|
| 자동 연결 가능 | 11 | `ServerLevel.RAIN_DELAY`·`RAIN_DURATION`·`THUNDER_DURATION`, `IntProvider`, `setWeatherParameters`, `setDayTime`, `getGameRules`, `GameRules.getRule`·`RULE_DAYLIGHT`·`BooleanValue`, `ServerPlayer.setGameMode` |
| 설계 필요 | 2 | `ServerPlayer.getAbilities`, `Abilities`(바닐라는 플레이어가 든 객체의 공개 필드를 고친다) |

새 다리 1건(`ChunkInternals.playerAbilities`·`setPlayerAbilities` — 능력 일곱 값을 레코드 하나로). 자동 연결 가능 비율 **85%(11/13)**. 규칙 열쇠 51개는 서버 jar 에서 기계로 뽑아 넣었다(규칙 하나를 쓰는 플러그인이 다른 규칙도 부를 수 있어서).

걸린 시간: 약 6분(20:42~20:48, 5b 시험과 2분 겹침).

- 시간·날씨·규칙은 이미 있는 Furnace 길(Bukkit 어댑터와 같은 것)에 그대로 잇는다. 아무 스레드.
- Paper 와 다른 점(이미 정한 것과 같다): 규칙은 서버에 한 벌, 시간·날씨는 오버월드 시계 하나(다른 월드에서 쓰면 경고 한 번 뒤 무시).
  `setDayTime` 은 접속자에게 곧바로 알린다(바닐라는 다음 20 틱 알림 때) — 시계가 최대 1초 일찍 맞춰질 뿐 값은 같다.
- `Abilities` 는 서버 값을 비춘 보기다. 세터(`setFlyingSpeed` 등)는 곧바로 서버에 쓰고, 공개 필드를 직접 고친 것은 다음 세터나 `onUpdateAbilities`
  (패킷도 보낸다) 때 쓴다. 고친 필드가 없으면 `getAbilities` 가 서버 값으로 다시 맞춘다(게임 모드가 바뀌면 따라온다).
  한계: 필드만 고치고 세터·`onUpdateAbilities` 를 부르지 않으면 그 객체는 "고친 상태"로 남아 서버 값(게임 모드 바꾸기 등)을 다시 받지 않는다.
- 바닐라 생성자로 만든 월드에 없는 플레이어(검토 고침): `getAbilities` 는 바닐라처럼 값만 드는 객체(서바이벌 기본값), `setGameMode` 는 같은 모드면 거짓,
  다른 모드와 `onUpdateAbilities` 는 `UnsupportedNmsCall` — Paper 는 연결(null)에 패킷을 보내다 NullPointerException 을 낸다.
- 규칙 값을 바꿀 때 Paper 도 이벤트를 내지 않는다: Paper 1.21.1 `GameRules$BooleanValue`·`IntegerValue.set(값, ServerLevel)` 은 값을 넣고 규칙 콜백
  (`onChanged`)만 부른다(Paper jar 를 javap 로 대조). `WorldGameRuleChangeEvent` 는 `/gamerule`·Bukkit `setGameRule` 길에서만 난다.
- 시험: 규칙 51개(불리언 40·정수 11)를 모두 읽어 Bukkit 규칙 값과 같은지 본다(`NmsWorldPropertiesTest.allFiftyOneRulesRead`).

## 5d-1 — 블록 엔티티 넣기·상태, 빛 성질 (Axiom 블록 버퍼)

**채운 기호 8개.** 무엇보다 Axiom 은 켜질 때(`onEnable`) `AxiomReflection` 이 `LevelChunk.updateBlockEntityTicker(BlockEntity)` 를 리플렉션으로
찾는다. 없으면 플러그인이 켜지지 않으므로 이것부터 채웠다(Paper 와 같은 이름·서명의 private 메서드).

| 분류 | 개수 | 기호 |
|---|---|---|
| 자동 연결 가능 | 4 | `LevelChunk.updateBlockEntityTicker`(할 일 없음), `BlockEntity.isValidBlockState`, `ChunkAccess.getHeightmaps`, `LevelLightEngine.updateSectionStatus`(할 일 없음) |
| 다리 위 기계적 | 2 | `LightEngine.hasDifferentLightProperties`, `BlockStateBase.useShapeForLightOcclusion`(블록 상태 표에 깃발 하나) |
| 설계 필요 | 2 | `LevelChunk.addAndRegisterBlockEntity`, `BlockEntity.setBlockState`(월드의 보기면 서버 블록 엔티티에 쓴다) |

새 다리 1건(`ChunkInternals.blockEntitySetStateNow`, 거울 일지 `BE_STATE`). 자동 연결 가능 비율 **50%(4/8)**, 기계적까지 75%.

걸린 시간: 약 15분(분석 20:52~20:58, 538 검토 고침으로 끊김, 구현 21:05~21:11, 서명 관문·시험 21:12~21:16).

- `addAndRegisterBlockEntity` 는 Paper `setBlockEntity` 판정 그대로: 칸 블록이 블록 엔티티를 갖지 않으면 내부 오류를 찍고 넣지 않는다. 든 상태가 칸과 다르면
  경고(같은 문구)한 뒤 칸 상태로 맞춘다(종류가 못 받는 상태면 바닐라처럼 `IllegalStateException`). 넣은 객체는 그 칸 서버 블록 엔티티의 보기가 되고
  든 내용을 싣는다. 주인 스레드는 곧바로, 다른 스레드는 거울 일지로(지우기→만들기→싣기 차례 그대로).
- 틱터: Furnace 서버는 틱마다 칸의 지금 상태로 할 일을 고르므로(모닥불은 켜졌는가) 틱터를 다시 고를 일이 없다.
- 빛: Furnace 는 청크마다 빛 26층을 다 들고 있어 구역 비었음 상태가 없다. `updateSectionStatus` 는 할 일이 없다(Paper 의 Starlight 도 비었음 지도
  갱신만 줄 세운다). `LightEngine` 은 정적 도우미만 있고 생성자는 private(서버에 층별 빛 엔진 객체가 없다).
- 대조 근거: Paper 1.21.1 jar(`paper-1.21.1.jar`)의 `LevelChunk.setBlockEntity` 를 `javap -c -constants` 로 봤다 — 문구 세 줄(`Trying to set block
  entity %s …`·`{}` 꼴 경고·`Block state mismatch … updating`)이 글자 그대로 같고, 블록 엔티티 못 갖는 칸은 `ServerInternalException` 을 만들어
  `printStackTrace`·`reportInternalException` 한 뒤 넣지 않는다(Furnace 는 같은 문구를 오류 로그로 찍는다). 로거는 `LogUtils.getLogger()`(=`LevelChunk`
  클래스 이름)라 Furnace 도 `LevelChunk` 이름 로거를 쓴다. `ChunkAccess.getHeightmaps` 의 반환형은 같은 jar 에서 `Collection<Map$Entry<…>>` 다.
- 시험 `NmsBlockBufferTest`(4, 검토 뒤 +4): 주인·다른 스레드로 통 넣기(방향 상태·든 아이템이 서버에 들어가고 저장된다), 블록 엔티티 없는 칸은 아무것도 안 함,
  빛 성질·높이 지도 종류 차례. 검토 보강: 이미 있는 블록 엔티티 위에 새것 넣기(주인·다른 스레드 — 옛 보기는 지움 표시·지우기 직전 내용, 서버엔 새 내용),
  다른 스레드의 한 작업 안에서 넣기→상태 바꾸기→싣기, 넣기→바로 지우기를 한 틱에 맞추기, 든 상태가 칸과 다르면 칸 상태로 맞추고 종류가 못 받는 상태면
  `IllegalStateException`(서버에 블록 엔티티 안 들어감).

## 5d-2 — 마을 관심 지점(POI) 넣기·지우기

Axiom 블록 버퍼는 칸마다 옛 상태와 새 상태의 관심 지점 종류(`PoiTypes.forState`)를 견주어, 다르면 옛것을 지우고(`PoiManager.remove`) 새것을
넣는다(`PoiManager.add`). **Axiom 이 부르는 기호 4개.**

| 분류 | 개수 | 기호 |
|---|---|---|
| 자동 연결 가능 | 1 | `ServerLevel.getPoiManager` |
| 다리 위 기계적 | 3 | `PoiTypes.forState`(서버 바닐라 표), `PoiManager.add`·`remove`(T-204 블록 효과 길에 종류 두 개) |
| 설계 필요 | 0 | — |

받침으로 `PoiType`(바닐라와 같은 record)·`PoiTypes` 열쇠 20개·`hasPoi`·`BuiltInRegistries.POINT_OF_INTEREST_TYPE` 를 같이 넣었다(기계적).
새 다리 1건(`BlockStateInternals.poiTypes`·`poiType` — 종류 표와 상태별 종류, `EFFECT_POI_ADD`·`EFFECT_POI_REMOVE`). 자동 연결 가능 비율 **25%(1/4)**,
기계적까지 100%.

걸린 시간: 약 6분(21:16~21:22, 538 CI 대기 중에).

- 넣기·지우기는 서버의 바닐라 이식(`PoiManager`·`PoiSection`)을 그 칸 주인 스레드에서 부른다. 다른 스레드면 주인 작업으로 넘긴다(앞서 그 청크에
  쓴 것이 먼저 들어가고 최대 한 틱 늦다). Paper 는 틱 스레드가 아니면 "Accessing poi chunk off-main" 예외다 — Axiom 은 틱 스레드에서 부른다.
- 이미 다른 종류가 있으면 바닐라처럼 오류를 남기고 덮어쓰고, 없는 것을 지우면 바닐라처럼 오류만 남긴다. 청크가 로드돼 있지 않으면 아무것도 안 한다.
- `PoiManager` 는 바닐라처럼 구역 저장소(`SectionStorage`)를 상속하지 않는다(서명 관문 기준선에 의도한 차이로 적음 — 서버가 들고 있다).
  레지스트리 밖의 종류(`Holder.direct(new PoiType(…))`)는 `UnsupportedNmsCall`. Paper 와 다른 점 세 가지는 `docs/user-todo/entries/2026-11-20-axiom-poi.md`.
- 기준선 병합 순서: #537(squaremap)과 같은 기준선 파일에 줄을 끼워 넣으므로 나중에 병합하는 쪽이 기준 브랜치를 합치고 `:vanilla-extract:signatureGate` 를
  다시 돈다(이 PR 은 #537 병합 뒤 합쳐 다시 돌렸다).
- 시험(`NmsBlockBufferTest` 에 4개): 종류 표(벌집·종·침대 머리만·자석석, 같은 종류는 같은 손잡이, 레지스트리 20개), 주인·다른 스레드로 넣고 지우기를
  서버 POI 로 확인, 레지스트리 밖 종류 거절.

## 5d-3 — 블록 놓기 패킷 길(Axiom SetBlockPacketListener)

Axiom 이 한 칸·여러 칸 놓기 패킷을 받으면 블록 요청 번호를 알리고(ack), 놓을 칸마다 자기 `CraftBlockState` 하위 클래스로 Bukkit 놓기 이벤트를 부르고,
놓은 뒤 아이템의 블록 엔티티 데이터·컴포넌트를 싣고 `setPlacedBy` 를 부른다. **채운 기호 9개.**

| 분류 | 개수 | 기호 |
|---|---|---|
| 자동 연결 가능 | 2 | `CraftBlock.notchToBlockFace`(+`blockFaceToNotch`), `BlockMultiPlaceEvent`(Bukkit API) |
| 다리 위 기계적 | 5 | `CraftEventFactory.callBlockPlaceEvent`·`callBlockMultiPlaceEvent`(Paper 판정, 스폰 보호 다리), `BlockItem.updateCustomBlockEntityTag`, `BlockEntity.applyComponentsFromItemStack`, `Block.setPlacedBy`(놓기 뒷일 다리) |
| 설계 필요 | 2 | `CraftBlockState(World, BlockPos, BlockState)`(플러그인이 상속 — 추상 클래스를 구체로), `ServerGamePacketListenerImpl.ackBlockChangesUpTo`(알림 차례) |

새 다리 3건(`ChunkInternals.ackBlockChanges`·`underSpawnProtection`·`placeStepNow`). 자동 연결 가능 비율 **22%(2/9)**, 기계적까지 78%.

걸린 시간: 약 10분(21:22~21:35 중 538 병합·539 올리기 3분 뺌).

- `CraftBlockState` 는 Paper 처럼 구체 클래스가 됐다. Paper 생성자 `(World, BlockPos, BlockState)` 로 만든 하위 클래스(Axiom `AxiomPlacingCraftBlockState`)는
  Bukkit 표면을 그 칸의 보통 스냅숏(`CompatBlockState`)에 맡긴다(월드를 읽지 않는다). Furnace 하위 클래스는 전처럼 스스로 구현한다. `getBlock` 은 Paper 처럼
  `CraftBlock`(공변 반환형). 서명 관문 기준선의 "shim 만 abstract" 줄을 지웠다.
- 놓기 이벤트: Paper `canBuild`(오버월드·스폰 보호 반경·OP 있음·이 플레이어 OP 아님)를 서버의 `isUnderSpawnProtection` 으로 본다(놓는 레벨의 차원).
  이벤트는 Bukkit 처리기에만 간다(Paper 와 같다 — 서버의 블록 놓기가 아니다). 여러 칸 이벤트는 `BlockPlaceEvent` 처리기 목록을 같이 쓴다.
- ack: 그 플레이어가 선 칸의 region 틱 끝, 그 틱의 블록 변경 방송 뒤에 나간다(서버 블록 요청과 같은 길 — 바닐라는 다음 연결 틱 첫머리, 차례가 같다).
  음수는 바닐라 문구로 `IllegalArgumentException`.
- `updateCustomBlockEntityTag`: 관리자만 쓸 수 있는 종류는 Paper 처럼 관리자 블록 권한이거나 즉시 짓기 + `minecraft.nbt.place` 권한. 서버 쪽 블록 놓기
  (`BlockPlacement`)는 아직 권한 갈래가 없다(바닐라 판정) — 서버 길은 이번 범위가 아니라 그대로 둔다.
- `applyComponentsFromItemStack`: 월드의 보기만(종류별 컴포넌트 읽기가 서버에만 있다). 월드 밖 블록 엔티티는 `UnsupportedNmsCall`.
- `setPlacedBy`: 놓는 이는 null 이거나 플레이어(월드에 없는 가짜 플레이어 포함). 다른 생물은 `UnsupportedNmsCall`. 둘레 청크까지 맞춘 뒤 부른다(침대 머리).
- 남긴 것: Axiom 은 `BedBlock`·`DoublePlantBlock`·`DoorBlock` 이면 `setPlacedBy` 를 건너뛰는데 지금 shim 블록 객체는 그 클래스가 아니라 늘 부른다(침대·문을
  두 칸 다 놓은 뒤라 한 번 더 같은 칸을 쓴다). 블록·아이템 종류 계층(T-231)은 생성기 스레드가 맡았다 — 그때 `BlockItem` 손 shim 도 옮긴다.
- 시험 `NmsBlockPlaceTest`(5): ack 패킷과 음수, 플러그인이 만든 상태로 한 칸·여러 칸 이벤트(취소·손·대표 칸), 아이템 블록 엔티티 데이터·이름·명령 블록 거절,
  침대 `setPlacedBy` 가 머리 칸을 놓는다, 면 변환.

## 5e-1 — voxy 가 디스크에서 먼 지형 읽기

voxy(0.22.0, 1.21.1 판)는 플레이어가 멀리 보는 지형을 **디스크에 저장된 청크 NBT** 에서 읽어 섹션 코덱으로 풀고 다시 패킷으로 묶는다. 읽는 길은 둘이다:
설정 `background_priority`(기본 켬)면 Paper moonrise `RegionFileIOThread.loadDataAsync`(우선순위 LOW), 끄면 바닐라 `ChunkMap.read`.
moonrise 클래스는 NMS 점검 도구가 세지 않는 이름(`ca/spottedleaf/…`)이라 표에 없었지만 기본 설정이 타는 길이라 같이 채웠다.

| 분류 | 개수 | 기호 |
|---|---|---|
| 자동 연결 가능 | 3 | `RegionFileIOThread$RegionFileType`, `PrioritisedExecutor$Priority`(Paper jar 의 번호·판정 그대로), `Cancellable` |
| 다리 위 기계적 | 2 | `ChunkMap.read`, `RegionFileIOThread.loadDataAsync`(그리고 같은 길의 `loadData`·`isRegionFileThread`) |
| 설계 필요 | 0 | — |

새 다리 1건(`ChunkInternals.storedNbt` — 서버 `World.readStoredAsync`: 그 파일의 IO 차례에서 region/·poi/·entities/ 의 NBT 를 읽는다).
자동 연결 가능 비율 **60%(3/5)**, 기계적까지 100%. 걸린 시간: 약 12분(21:43~21:55, 539 CI 대기 중에. 도구 점검 확인 시간은 뺐다).

- Paper `ChunkMap.read` 는 부른 스레드에서 막고 읽어 끝난 future 를 준다(moonrise `loadData`). Furnace 는 바닐라처럼 기다리지 않는 future 다 — 같은 파일의
  앞선 저장 뒤에 줄 서므로 저장 대기분이 보이는 끝 상태는 같다. 옛 판 올림(DataFixer)은 바닐라·Paper 처럼 하지 않는다.
- moonrise `loadData` 는 Paper 처럼 어느 스레드에서나 막고 기다린다(구역 스레드에서 부르면 그 구역 틱이 늦는다 — Paper 의 주 스레드와 같다).
- 시험 `NmsChunkDiskReadTest`(4): 평지 월드에 블록을 놓고 저장한 뒤 두 길로 읽어 바닐라 꼴(`sections[].block_states.palette`)에 그 블록이 있는가, 없는
  청크는 빈 값·(null, null) 콜백, COMPLETING 우선순위 거절, 우선순위 번호.
- **점검 표 주의:** 앞서 표본 폴더에 있던 voxy jar 는 26.1.2 판(클래스 판 69)이라 점검 도구의 ASM 이 읽지 못해 심볼 0개로 나왔다. 1.21.1 판(0.22.0)으로
  다시 돌리면 243개 중 234개 있음, 남은 8개는 코덱·`VarInt`·`Biomes`·`ENTITY_TYPE`·`paperConfig` 이다(5e-2).

## 지금까지 모음(5a~5d-3)

| 묶음 | 기호 | 자동 연결 가능 | 다리 위 기계적 | 설계 필요 | 걸린 시간 |
|---|---|---|---|---|---|
| 5a | 29 | 16 | 12 | 1 (+다리 1) | 14분 |
| 5a-2 | 1 | 0 | 0 | 1 | 5분 |
| 5b 앞부분 | 7 | 0 | 5 | 2 (+다리 1) | 8분 |
| 5c | 13 | 11 | 0 | 2 (+다리 1) | 6분 |
| 5d-1 | 8 | 4 | 2 | 2 (+다리 1) | 15분 |
| 5d-2 | 4 | 1 | 3 | 0 (+다리 1) | 6분 |
| 5d-3 | 9 | 2 | 5 | 2 (+다리 3) | 10분 |
| 합계 | 71 | 34 (48%) | 27 (38%) | 10 (14%) + 다리 8 | 62분 (+5a 검토 고침 5분) |

다리가 생긴 뒤의 기계적 연결까지 넣으면 86%(61/71, 5c 까지는 44/50 = 88%)가 사람의 설계 없이 이을 수 있는 꼴이었다. 손으로 쓴 시간의 대부분은 설계 필요 10개와
다리 8건, 그리고 바닐라 값 시험이었다.

5a~5c 시간: 묶음별 시간을 더하면 33분이지만 5b 와 5c 가 2분 겹쳐 실제 손 시간은 약 31분이다. 여기에 5a 검토 고침 5분을 더하면 첫 명령 20:13:53 부터
5c 문서 커밋 20:48:21 까지 약 34.5분과 맞는다.

