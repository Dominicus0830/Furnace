# 다차원 호스팅 설계 요약 (2026-10-26)

오버월드 하나만 띄우던 서버를 바닐라처럼 오버월드·네더·엔드 세 레벨로 띄운다. 관측 결과(패킷 순서, 저장 형식, 포털 좌표)는 바닐라
1.21.1 을 따르고, 스레드 구조는 Furnace 의 region 소유권을 그대로 지킨다.

## 1. 구조
- `DimensionType`: 바닐라 내장 차원 타입 3개(높이·논리 높이·하늘빛·천장·초고온·좌표 비·고정 시각)와 레벨 키, 저장 폴더
  (오버월드 = 월드 폴더, 네더 `DIM-1`, 엔드 `DIM1`). 생성기는 `NoiseWorldSource.Dimension`.
- `World` 하나 = 차원 하나. 청크 저장소(region/entities/poi), 생성기, `Regionizer`(작업 스레드 포함)가 월드마다 따로다.
- `Dimensions`(서버 전체): 등록할 때 접속자 목록·월드 시계·엔티티 id 를 오버월드와 공유시킨다.
  - 접속자 목록 공유: 탭 목록은 차원과 무관(바닐라 PlayerList). 차원의 플레이어(`forEachPlayerHere`)는 `Player.dimension()` 으로 거른다.
  - 시계 공유: 바닐라 네더·엔드는 `DerivedLevelData` 라 시간·날씨 데이터가 오버월드 것이다. 하늘빛이 없는 차원은 비 세기가 늘 0
    (바닐라 `advanceWeatherCycle` 이 `hasSkyLight` 일 때만 세기를 바꾼다) → 그 차원의 `isRaining` 은 거짓, 세기 변화 패킷은
    오버월드 플레이어에게만, 비 시작/끝은 모두에게.
- region 스레드 풀: **월드마다 따로** 둔다. 지금 `Regionizer` 의 대기열·잠금·작업 스레드가 월드 하나에 묶여 있어서다(Folia 도
  월드마다 regionizer). 쉬는 차원의 스레드는 조건 변수에서 잠들어 비용이 거의 없다. 한 풀을 여러 월드가 나눠 쓰게 하려면
  대기열을 월드 밖으로 빼야 한다 — 성능 측정 뒤 제안할 후보.
- 스케줄러의 `RegionPos` 라우팅(플러그인 region 스케줄러)은 오버월드만 건다(API 의 RegionPos 에 차원이 없다).
- 블록 레벨 서비스: 오버월드는 `ServerCore.levelServices`, 다른 차원은 그것을 감싼 `DimensionServices`(차원 타입, 그 차원의
  플레이어, 날씨 없음, 그 차원의 바이옴·전리품 월드).
- 높이: `ChunkLevel.minBuildHeight/maxBuildHeight` 가 차원 타입에서 온다. 청크 패킷의 빛 섹션은 `(minY>>4)-1` 부터 섹션 수 + 2,
  하늘빛 없는 차원은 하늘빛 마스크를 모두 비운다. `ChunkLight` 배열은 오버월드 크기(-5..20 섹션)를 그대로 쓰고 차원 범위만 읽고 쓴다.

## 2. 차원 이동 (스레드)
원천 차원과 목적 차원의 레벨은 서로 다른 region 스레드 소유다. 바닐라는 한 스레드에서 목적지를 계산하고 바로 옮기지만, Furnace 는
**두 단계**로 나눈다.
1. 원천 region 스레드: 포털이 `prepareTravel` 로 원천 쪽 정보(목적 차원, 좌표 비를 적용한 목표 위치, 원천 포털 사각형과 그 안의
   상대 위치, 몸 크기)를 담은 `PortalTravel` 을 만든다. 엔티티는 저장 NBT 로 떠서 CHANGED_DIMENSION 으로 빼고, 플레이어는 region
   에서 떼어 낸다(탭 목록은 그대로, 주변 관찰자에게는 엔티티 제거).
2. 목적 월드: 탐색 범위의 청크를 미리 올린 뒤(디스크에 있는 청크 + 생성 범위 청크), 그 청크들이 한 region 에 모이면 그 region
   스레드에서 `complete(level)` 로 목적지(`DimensionTransition`)를 확정하고 엔티티를 넣는다. 플레이어는 연결 스레드가 패킷을 보낸 뒤
   목적 region 이 편입한다.
- 시험 레벨(한 스레드에 두 레벨)은 예전처럼 `getPortalDestination` → `changeDimension` 동기 경로를 그대로 쓴다.
- 플레이어 패킷 순서(바닐라 ServerPlayer.changeDimension): respawn(데이터 보존 3) → change_difficulty → 권한(entity_event·commands)
  → player_position → set_chunk_cache_center → player_abilities → sendLevelInfo(보더·시간·스폰·[비]·청크 대기·틱 상태) →
  sendAllPlayerInfo(인벤토리·든 칸) → 효과 → 포털 소리(level_event 1032) → (다음 틱부터) 청크·체력·경험치.

## 3. 포털
- 지옥문: `NetherPortalBlock.entityInside`, 대기 시간(플레이어 `playersNetherPortalDefaultDelay`/무적이면 creative delay,
  그 밖 0), `PortalForcer`(POI `nether_portal` 탐색 반경 네더 16/오버월드 128, 없으면 생성 — 나선 16, 흑요석 틀),
  `BlockUtil.getLargestRectangleAround`, `PortalShape.getRelativePosition/findCollisionFreePosition`. 좌표 비 = 원천/목적 coordinateScale.
- 엔드 포털: 엔드로 가면 (100,50,0) 단상, 플레이어는 1 아래. 엔드에서 나가면 처음이면 크레딧(WIN_GAME) → client_command 0 →
  오버월드 리스폰(데이터 보존 1), 본 적 있으면 리스폰 위치로.
- 포털 티켓: 도착 위치 청크 주변(반경 3)을 300틱 동안 내리지 않는다(바닐라 TicketType.PORTAL). 엔드 드래곤 전투는 (0,0) 반경 9.

## 4. 저장
- 차원별 `DIM-1/region`, `DIM1/entities` … 와 `data/raids.dat`(엔드는 `raids_end.dat`). 플레이어 `Dimension` 을 저장하고 접속 때 그 차원으로.
- 엔드 드래곤 전투는 level.dat `DragonFight`.
- 종료·`/save-all`·자동 저장이 모든 차원을 flush 한다.

## 5. 남은 차이·생략 (구현하며 갱신)
- 차원 이동이 비동기라 목적 청크가 준비될 때까지 몇 틱 걸린다(바닐라는 같은 틱).
- 다른 차원 보더: Furnace 는 보더 하나를 공유한다(바닐라는 재시작 직후 네더·엔드 보더가 기본값이고 변경만 따라간다).
- 포털 탐색 때 디스크에 있는 청크를 통째로 올린다(바닐라는 POI 만 읽는다) — 결과는 같고 비용만 다르다.
- `ChunkLight` 가 오버월드 크기라 네더 바닥 아래 빛이 차원 범위 밖 섹션(-2 이하)을 지나 돌아올 수 있다(사실상 일어나지 않음).
- 플레이어의 포털 칸 검사는 매 틱 한다(바닐라는 이동 처리의 move → checkInsideBlocks).
- 오버월드가 아닌 리스폰 차원(네더 정박기)은 블록 검사가 없어 "리스폰 블록 없음"으로 본다. 침대·정박기 자체가 아직 없다.
- `nether_travel` 발전 과제(enteredNetherPosition)는 없다. `changed_dimension` 은 있다.
- 엔티티가 지나갈 때 승객은 내려놓는다(바닐라는 승객도 함께 옮겨 다시 태운다).
- 드래곤 전투는 엔드 (0,0) 청크를 가진 region 의 레벨에만 묶인다 — 중앙 섬 밖 다른 region 의 수정은 전투를 모른다.
- 플러그인 RegionPos 스케줄러와 일부 명령(`/locate`·`/summon`·`/setblock`·`/fill` 은 실행자 차원을 쓴다)을 빼면 서버 API 는 오버월드 기준이다.
