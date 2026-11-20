# T-205 빛 표면 묶음 B — 설계·결과 (2026-11-16)

관련: `compat-real-plugin-gaps-2026-11-16.md` 2장 묶음 B, `compat-nms-n2b5-relight-2026-11-16.md` 8장(이 작업을 나눈 곳), `optimization-tasks.md` T-205,
공개 스냅샷 N2b-3(`compat-nms-n2b3-2026-11-14.md`), 거울 N2b-4, 붙잡기 T-197.

## 0. 결론

- FAWE 2.16.2 `v1_21_R1` 이 부르는 빛 NMS 면 전부(아래 1장 표, `javap` 로 뽑음)를 **Paper 1.21.1 Moonrise(Starlight) 원문 그대로의 규칙**으로 서버 빛 위에 놓았다.
  FAWE 전용 코드는 없다 — 같은 Mojang 이름을 부르는 어떤 플러그인도 같은 길을 탄다.
- 핵심 사실(원문 확인, 2장): Paper 의 `getDataLayerData` 는 **새 사본**을 주고 `queueSectionData` 는 **아무 일도 안 한다**. 그래서 FAWE 의
  `removeSectionLighting`·`fillLightNibble`(받은 배열에 직접 쓰기)은 Paper 에서 서버 빛을 바꾸지 못한다. Furnace 도 같게 했다 — T-205 표에 적혀 있던
  "queueSectionData 는 거울 일지 쓰기"는 바닐라(Spigot) 동작이고 Paper 와 다르므로 하지 않는다(사용자 확인 항목 `docs/user-todo/entries/archive/2026-11-16-t205-light-surface.md`).
- 다른 스레드 읽기(FAWE 일꾼): 주인 region 이 틱 끝에 내는 **공개 스냅샷에 빛 층을 복사 없이** 넣는다. 넘긴 층은 서버가 다음 쓰기 때 복사한다(쓰기 시 복사).
  스냅샷을 내지 않는 청크는 쓰기 길에 비트 검사 하나만 는다.
- `checkBlock`: 아무 스레드에서 걸고 곧바로 돌아온다. 주인 region 이 그 청크의 거울 쓰기를 먼저 들여온 뒤 원천+검사를 건다(`LevelLight.blockChanged`).
  빛 다시 계산(T-197)이 둘레를 계산하는 중이면 그 검사는 T-197 붙잡기 규칙대로 끼운 뒤로 미뤄진다(Paper 는 relight 동안 빛 대기열 전체를 미룬다).

## 1. 범위 — FAWE 가 부르는 빛 기호 (`javap -c`, FAWE 2.16.2 `impl/fawe/v1_21_R1` 전부)

| 기호 | 부르는 곳 | 이 작업 |
|---|---|---|
| `ServerChunkCache#getLightEngine()ThreadedLevelLightEngine`, `ChunkSource#getLightEngine()LevelLightEngine`, `ServerLevel#getLightEngine()` | 곳곳 | `ChunkSource` 추상 메서드 + `ServerChunkCache` 좁힌 반환, `Level#getLightEngine` |
| `ThreadedLevelLightEngine#getLayerListener(LightLayer)` | `getSkyLight/getEmittedLight/fillLightNibble/removeSectionLighting` | `LevelLightEngine` 에(상위 클래스로 해석) |
| `LayerLightEventListener#getDataLayerData(SectionPos)` | 위 넷 | 사본(2장 P1) |
| `LevelLightEngine#queueSectionData` | 위(빈 섹션일 때) | Paper 처럼 무시(P2) |
| `ThreadedLevelLightEngine#checkBlock`·`LevelLightEngine#checkBlock` | NativeAccess `updateLightingForBlock` | 주인에게 검사(P3) |
| `ServerLevel#getRawBrightness(BlockPos,int)` | `getEmittedLight`(값은 버림) | Starlight 식(P4) |
| `LevelChunk#setLightCorrect` | 편집 끝 콜백 | 이미 있음(N2b-6) — 하늘빛 읽개가 이 표시를 본다(P1) |

같이 넣은 바닐라 면(같은 클래스, Paper 동작이 분명한 것): `LayerLightEventListener#getLightValue`, `DummyLightLayerEventListener`, `LightEventListener`,
`Level#getBrightness`, `LevelLightEngine#getRawBrightness/setLightEnabled/propagateLightSources/retainData/getDebugData/getLightSectionCount/getMin·MaxLightSection`.
`hasLightWork`·`runLightUpdates`·`updateSectionStatus` 는 모양만 두고 `UnsupportedNmsCall` 로 던진다 — Furnace 의 빛 대기열은 region 마다 그 스레드에서 돌아
아무 스레드에서 "세계 전체 빛 일"을 묻거나 돌릴 수 없다(값을 지어내지 않는다). FAWE 는 이 셋을 부르지 않는다.

## 2. Paper 가 하는 일 (원문: `Tuinity/Moonrise` `mc/1.21.1`)

`LevelLightEngineMixin`·`ThreadedLevelLightEngineMixin`·`StarLightInterface`·`SWMRNibbleArray`:

- **P1 층 읽개**(`StarLightInterface` 생성자의 `skyReader`·`blockReader`, `getLayerListener` 는 같은 객체를 준다. 하늘빛 없는 차원은 `DummyLightLayerEventListener`):
  - 하늘빛 `getDataLayerData`: `getAnyChunkNow` 가 null 이거나 `!isLightCorrect()` 이거나 LIGHT 단계 전이면 null, 빛 섹션 범위 밖 null, 비움 지도 없으면 null,
    아니면 `SWMRNibbleArray#toVanillaNibble()` — **`new DataLayer(storageVisible.clone())`**(INIT), `new DataLayer()`(UNINIT), null(NULL·HIDDEN).
  - 블록빛 `getDataLayerData`: 청크 null 이거나 범위 밖 null, 아니면 같은 `toVanillaNibble()`(isLightCorrect 를 안 본다).
  - 하늘빛 `getLightValue`(`getSkyLightValue`): 하늘빛 없으면 0. 청크 null·`!isLightCorrect`·LIGHT 전이면 **15**, 빛 섹션 위면 15, 아래면 맨 아래 빛 섹션 바닥(y = 섹션×16),
    니블이 있으면 그 값, NULL 니블이면 비움 지도로 위에서 끌어내린 값.
  - 블록빛 `getLightValue`: 범위 밖·청크 null 이면 0, 아니면 니블 값.
  - 읽개의 `checkBlock` 은 엔진의 `checkBlock`, `runLightUpdates`·`setLightEnabled`·`propagateLightSources` 는 `UnsupportedOperationException`.
- **P2 `queueSectionData`**: 두 믹스인 모두 `@Overwrite` 로 빈 몸통("do not allow modification of data from the non-chunk load hooks"). `setLightEnabled`·
  `propagateLightSources`·`retainData` 도 빈 몸통, `getDebugData` 는 `"n/a"`.
- **P3 `checkBlock`**(`ThreadedLevelLightEngineMixin`): `queueTaskForSection` — 가운데 청크가 없거나 LIGHT 전이면 버리고, `blockChange` 가 높이 밖을 버린다.
  아니면 청크 작업 티켓을 걸고 빛 대기열에 넣는다(아무 스레드, 기다리지 않음). Starlight 의 검사는 그 칸의 하늘빛을 원천(위 열)까지 다시 본다.
- **P4 `getRawBrightness`**: `sky = getSkyLightValue − 어둠; sky == 15 이면 15, 아니면 max(sky, getBlockLightValue)`.

## 3. 설계

### 3.1 서버 다리 (`ChunkInternals`, 불안정 내부)

| 메서드 | 스레드 | 뜻 |
|---|---|---|
| `hasSkyLight(world)` | 아무 | 차원 하늘빛 |
| `lightReadyNow(world, cx, cz)` | 주인 | 로드·빛 켜짐 |
| `lightNow(world, x, y, z, sky)` | 주인 | 저장된 값(빛 자료 없으면 −1) |
| `lightLayerNow(world, cx, sy, cz, sky)` | 주인 | 층 사본(없거나 범위 밖이면 null) |
| `checkLight(world, x, y, z)` | 아무, 기다리지 않음 | 검사 걸기(3.3) |
| `ChunkView#lightReady/light/lightLayer` | 아무 | 위 셋의 공개 스냅샷 판 |

Starlight 규칙(15·0·범위·isLightCorrect·어둠 식)은 호환 계층 `NmsLight` 에 둔다 — 서버는 저장된 값만 준다. 읽는 곳은 블록 읽기(N2b-3)와 같다:
주인 스레드(전역 빌림 포함)면 살아 있는 값, 아니면 공개 스냅샷(없으면 region 이 아닌 스레드는 한 틱 기다리고, 다른 region·전역 스레드는 `IllegalStateException` —
`awaitChunkView` 와 같은 교착 방지 규칙). FAWE 일꾼은 청크 shim 을 쥐고 있어 스냅샷이 늘 나 있다.

### 3.2 공개 스냅샷의 빛 — 쓰기 시 복사

- `ChunkColumn.publishReadView` 가 `ChunkLight.shareLayersWithView(sky[], block[])` 로 층 **참조**를 넘긴다(26×2 참조 복사). `ChunkLight` 는 넘긴 층을 비트(`viewSky`·`viewBlock`)로
  표시하고, `writableSky/Block` 이 표시된 층에 처음 쓸 때 복사한다(기존 공유 상수 ZERO·FULL 규칙에 비트 하나를 더함). 게시(`publishTo`)·relight 설치(`adoptLayersFrom`)·
  `fillSkyFull`·`compact` 처럼 층을 갈아 끼우는 길은 비트를 지운다(새 층은 스냅샷 것이 아니다).
- 판 재사용: 블록이 그대로여도 층 참조가 하나라도 다르면(= 그 층이 쓰였다) 새 판을 낸다(`ChunkReadView.sameLight`). 빛이 바뀌면 `lightTouched` 로 `modCount` 도 오르지만
  참조 비교가 정확하다(같은 참조 = 넘긴 뒤 안 쓰였다).
- 빛 작업 모드(T-064): 묶음이 도는 동안 보이는 층은 아무도 고치지 않으므로 공개 때의 층은 한 순간의 값이다. 쓰기 사본은 원래 처음 쓸 때 복사한다.
- `getDataLayerData` 는 Paper 처럼 부를 때마다 2048 바이트 사본을 준다.

### 3.3 `checkLight` — 검사 걸기

- 주인 스레드(전역 빌림 포함): `WorldHandle.ownedRegion`(거울 대기가 있으면 맞춤 지점에서 들여온다) → `ChunkLevel.checkLightAt`.
- 다른 스레드: `World.submitAtOwnedChunk` 로 주인에게 보낸다(로드돼 있지 않으면 region 을 만들지 않고 버린다). 주인 작업은 `syncMirrorIfPending` 뒤 `checkLightAt` —
  FAWE NativeAccess 가 다른 스레드에서 shim 에 쓰고 곧 `checkBlock` 을 불러도 검사가 그 블록을 본다.
- `checkLightAt`: 높이 밖·청크 없음·빛 꺼짐이면 false(Paper 처럼 버림), 아니면 `LevelLight.blockChanged`(하늘빛 원천 칸 고치기 + 두 엔진 검사). 원천까지 고치는 이유:
  Starlight 검사는 원천을 따로 두지 않고 열을 다시 보므로, 원시 쓰기(빛·원천을 안 고침 — N2b-4 D2) 뒤 checkBlock 이 Paper 와 같은 빛이 되려면 원천도 맞춰야 한다
  (`SkyLightEngine.updateSource` 는 바닐라 `ChunkSkyLightSources.update` 그대로라 이미 맞으면 아무것도 바꾸지 않는다).
- 계산은 그 region 의 다음 빛 계산(틱 끝, 빛 작업 모드면 다음 묶음). 바뀐 섹션은 보통 길(`markLightChanged`)로 저장 대상·빛 갱신 패킷이 된다.
- relight 붙잡기: 검사는 `LevelLight` 검사 집합에 들어가므로 `applyHold` 가 그대로 붙잡는다(T-197 4.3). 시험 `LightSurfaceTest#checkDuringRelightIsHeldUntilInstalled`.

## 4. Paper 와 다른 점 (알고 둔 것)

1. **NULL 니블**: Starlight 는 빛이 닿을 일이 없는 섹션의 니블을 비워 두고(null) `getDataLayerData` 가 null 을 준다(FAWE 는 그 자리를 "하늘 아래"로 보고 15 로 채운다).
   Furnace 는 모든 빛 섹션에 실제 값을 가지므로 그 자리에서 실제 빛(대개 땅속 0)을 준다. 니블의 null 여부는 Starlight 엔진 내부 상태라 옮기지 않았다 —
   값은 Furnace 가 "그 칸의 실제 빛"이고, 하늘 위 섹션처럼 Paper 가 값을 갖는 곳에서는 같다. 엔진 값 차이 자체는 T-202 범위.
2. **다른 region·전역 스레드의 첫 읽기**: Paper 는 아무 스레드에서 막힘 없이 읽는다. Furnace 는 공개 스냅샷이 아직 없는 청크를 다른 region·전역 스레드가 읽으면
   `IllegalStateException`(블록 읽기와 같은 교착 방지 규칙). shim 을 쥔 청크(FAWE)는 스냅샷이 늘 있어 해당하지 않는다.
3. 다른 스레드 읽기는 최대 한 틱 옛 값(주인이 틱 끝에 낸 판). Paper 도 다른 스레드에서는 빛 일꾼과 경쟁 중의 보이는 값을 읽는다.
4. `hasLightWork`·`runLightUpdates`·`updateSectionStatus` 는 던진다(1장).
5. **오버월드보다 높이가 넓은 데이터 팩 차원**: 서버 빛 배열(`ChunkLight`)은 오버월드 빛 섹션 범위(−5~20)만 가진다(기존 한계 — 그 밖은 서버가 빛을 계산하지 않는다).
   그 밖의 칸은 "빛 자료 없음"으로 보고 Starlight 가장자리 규칙대로 하늘빛 15·블록빛 0, `getDataLayerData` 는 null 을 준다(예외를 던지지 않는다).
   Paper 는 그 높이에도 실제 빛이 있다. 바닐라 차원(오버월드·네더·엔드)은 모두 범위 안이다.

## 5. 성능

- 스냅샷을 내지 않는 청크(대부분): 빛 쓰기마다 `writable*` 에 비트 검사 하나(`viewSky & bit`, 같은 캐시 줄의 필드). 측정 6장.
- 스냅샷을 내는 청크(shim 이 붙었거나 최근 다른 스레드가 읽은 청크): 공개마다 참조 52개 복사, 다음 틱에 그 층에 처음 쓸 때 2 KB 복사 한 번(층마다 틱당 최대 한 번).
- `checkLight` 다른 스레드: 작업 하나(그 region 의 받은 일 처리 예산 안). relight 예산·붙잡기 구조는 그대로 쓴다(새 예산·우회 없음).
- M3(relight 틱 영향) 경로는 바뀌지 않았다(relight 준비·설치·계산 코드 그대로, 쓰기 길 비트 검사만).

## 6. 측정

### 6.1 가장 나쁜 경우(클라우드 컨테이너, 2026-11-16)

`LightSurfaceTest#watchedChunkLampBench`(`-Dfurnace.bench=true`): 다른 스레드가 보는 청크를 **매 틱** 공개하면서 매 틱 발광석 16개를 켜고 끈다(워밍업 300 + 측정 1000 틱, 공개 없음/매 틱 공개 교대 5라운드). 바뀌기 전 코드(5a52b5db9)는 같은 측정을 빛 없는 공개로 돌렸다(커밋 안 한 비교용 사본).

| 코드 | 공개 없음(라운드 0~4, µs/틱) | 매 틱 공개 | 중앙값 없음 → 공개 |
|---|---|---|---|
| 전(5a52b5db9) | 1734, 1733, 1524, 1685, 1754 | 1585, 1526, 1587, 1557, 2030 | 1733 → 1585 |
| 후(T-205) | 1571, 1511, 1580, 1521, 1532 | 1821, 1668, 1628, 1667, 1510 | 1532 → 1667 |

- 틱 시간은 빛 계산(발광석 16개 켜고 끄기)이 거의 전부이고 라운드 사이 흔들림이 ±15 % 다. 공개 몫 차이(전 −148 µs, 후 +135 µs)는 이 흔들림 안이다.
- 늘어난 일의 상한은 셈으로 보인다: 공개 한 번에 층 참조 52개 복사 + 공개 뒤 첫 쓰기에서 바뀐 층만 2 KB 복사(이 장면은 섹션 2~3개). 보지 않는 청크는 공개가 없으므로 0 이다.
- M3 합격선(틱 영향 +1 ms) 안이다. 기존 `AsyncLightRegionTest#lamps400Bench` 경로는 공개가 없어 표시 비트 검사 한 번만 더해진다.

## 7. 시험

| 시험 | 본다 |
|---|---|
| `ChunkLightViewShareTest`(서버) | 넘긴 층은 쓰기 때 복사, 게시·갈아 끼우기는 표시를 지운다 |
| `LightSurfaceTest#readViewHoldsTheLightOfItsMoment` | 스냅샷은 그 순간의 빛, 빛만 바뀌어도 새 판, 사본을 고쳐도 판은 그대로 |
| `LightSurfaceTest#checkAfterRawWritesMatchesNormalWrites`(빛 작업 끔·켬) | 원시 쓰기 + 칸마다 검사 = 보통 쓰기 세계(3×3 청크 빛 전체·하늘빛 원천) |
| `LightSurfaceTest#checkDuringRelightIsHeldUntilInstalled` | relight 중 건 검사는 붙잡혔다가 설치 뒤 계산 |
| `LightSurfaceTest#checkIsDroppedOutsideHeightOrUnloaded` | 높이 밖·로드 안 된 청크는 버림 |
| `NmsLightSurfaceTest`(호환, 7개) | Paper 규칙 P1~P4: 같은 읽개, 사본·queueSectionData 무시, 다른 스레드 = 주인 값, 가장자리 15/0/null, isLightCorrect=false, 다른 스레드 checkBlock 이 원시 쓰기를 밝힘, 빈 몸통·던지는 면 |
