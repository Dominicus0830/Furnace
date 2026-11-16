# T-198 NMS 블록 엔티티 쓰기 (2026-11-16)

앞 단계: N2b-6(`compat-nms-n2b6-2026-11-16.md` 5장 2), 거울 일지 N2b-4(`compat-nms-n2b4-2026-11-15.md`). 과제 정의: `docs/roadmap/optimization-tasks.md` T-198.

## 1. 무엇이 문제였나

FAWE 2.16.2 는 블록 엔티티가 든 섹션을 바꿀 때 아래 순서로 NMS 를 부른다(`PaperweightGetBlocks.internalCall`, `PaperweightPlatformAdapter.removeBeacon`, `NativeAccess.updateTileEntity`).

| 단계 | 스레드 | 부르는 것 |
|---|---|---|
| 지울 타일 기록(`//set`·`//paste` 가 덮는 칸) | FAWE 작업 스레드 | `LevelChunk#getBlockEntity(pos)` → `removeBlockEntity(pos)` → 지운 타일의 `saveWithId`(되돌리기 기록) |
| 신호기 | 작업 스레드 | `blockEntities.remove(pos)`, 리플렉션 `removeGameEventListener`·`removeBlockEntityTicker`, `remove` 필드 |
| 섹션 바꾸기 | 작업 스레드 | 섹션 CAS(N2b-4 거울) |
| 새 타일 싣기 | 동기 작업(주 스레드 = Furnace 전역 스레드의 region 빌림) | `Level#getBlockEntity(pos)`(바닐라 IMMEDIATE — 없으면 만든다), 없거나 지워졌으면 `removeBlockEntity`, `loadWithComponents(tag, registryAccess)` |
| 신호기 끄기 | 동기 작업 | `BeaconBlockEntity.playSound(level, pos, BEACON_DEACTIVATE)`, `new BeaconDeactivatedEvent(CraftBlock.at(level, pos)).callEvent()` |

이 가운데 쓰기 쪽(지우기·만들기·싣기·setChanged)이 하나도 없어 그 청크 편집이 통째로 실패했다. 또 서버의 `getBlockEntity` 는 블록 엔티티가 빠진 칸에 새로 만들지 않았다(바닐라는 만든다).

## 2. 한 것

### 서버
- `ChunkLevel#getBlockEntity` = 바닐라 `Level#getBlockEntity`(IMMEDIATE): 그 칸 블록이 블록 엔티티를 가지는 종류인데 없으면 `createBlockEntity` 로 만들어 등록한다. 아직 해석하지 않은 생성 NBT(서버 틀이 없는 종류)가 있는 칸은 덮지 않는다(바닐라는 pendingBlockEntities 를 먼저 쓴다). `getBlockEntityIfPresent` = CHECK.
- `ChunkInternals` 다리 7개: `blockEntityNow`(CHECK/IMMEDIATE), `removeBlockEntityNow`, `blockEntityRemoved`, `blockEntitySaveNow`, `blockEntityLoadNow`, `blockEntityChangedNow`(저장 표시 + 공기가 아니면 비교기 알림 — 바닐라 정적 setChanged), `normalizeBlockEntityNbt`(떨어진 블록 엔티티가 서버처럼 읽고 다시 쓴 꼴). 생성 NBT 는 같은 열·같은 칸이면 같은 손잡이(`GeneratedBlockEntityRef`).

### 호환 shim
- `BlockEntity` 두 갈래: **월드의 보기**(서버 블록 엔티티 객체 하나에 묶임, 같은 청크 shim·같은 칸·같은 서버 객체면 같은 보기 — 바닐라는 그 객체 자체)와 **떨어진 것**(플러그인이 만든 것, 자기 NBT 를 든다). 지워진 보기도 그 객체의 마지막 내용을 저장한다(FAWE 되돌리기 기록).
- `LevelChunk#getBlockEntity(pos, EntityCreationType)`·`removeBlockEntity`·`blockEntities`(읽기·`remove` 되는 표, `put` 은 아직 없음 → `UnsupportedNmsCall`)·private `removeGameEventListener`·`removeBlockEntityTicker`(떨어진 것이면 할 일 없음 — `remove` 가 이미 뗐다).
- `Level#getBlockEntity`(주인 스레드 IMMEDIATE, 아니면 null — Paper 가 틱 스레드 밖에서 null 인 것과 같다), `Level#removeBlockEntity`, `Level#playSound(null, pos, …)`.
- 다른 스레드의 쓰기는 **거울 일지**(N2b-4)에 넣고 주인이 맞춤 지점에서 재생한다: `BE_REMOVE`·`BE_CREATE`·`BE_LOAD`·`BE_CHANGED`. 쓴 스레드는 자기 쓰기를 바로 본다(블록 엔티티 덧칠). 블록이 아직 들어오지 않아 만들 수 없는 `BE_CREATE` 는 그 맞춤의 섹션 들여오기 뒤로 미루고, 같은 칸의 뒤 작업도 함께 미룬다(FAWE: 섹션 CAS 직후 IMMEDIATE).
- 다른 스레드의 저장(`saveWithId`)은 주인 region 에 넘겨 최대 10초 기다린다. region·전역 스레드는 기다리지 않고 `ThreadOwnershipException`(교착 규칙 — PR #382 R1). 전역 스레드의 빌림 구간(Bukkit 동기 작업)은 먼저 그 region 을 빌려 본다(`ownsChunk`) — 빌리면 바로 읽는다.
- **지운 타일의 꼴(검토 위험 1)**: 다른 스레드가 지운 블록 엔티티는 주인이 지우기를 재생할 때 지우기 직전 꼴을 떠 보기에 둔다. 지운 객체는 더 바뀌지 않으므로, FAWE 가 지운 타일을 되돌리기 기록에 저장할 때 첫 하나만 주인을 기다리고(그 맞춤이 그 청크의 지우기를 모두 재생한다) 나머지는 바로 쓴다. 보기에 다시 실으면 꼴은 버린다.
- **보기 묶기(검토 R2)**: 다른 스레드가 공개 판에서 얻은 보기(아직 서버 객체에 묶이지 않은 것)는 주인이 틱 끝 공개 때와 청크를 내리기 직전에 그 칸의 지금 서버 객체에 묶는다 — FAWE 가 `//copy` 한 타일을 원본 청크가 내려간 뒤 게으르게 저장해도 그 객체의 내용이 나온다(Paper 는 객체가 내용을 든다).
- **묶기와 재생 순서(재검토 R5)**: 그 칸에 아직 재생하지 않은 블록 엔티티 일지(지우기·만들기)가 남아 있으면 공개 때 묶지 않는다 — 재생이 묶는다. 만들기 재생은 보기가 지워진 옛 객체에 묶여 있으면 새 객체로 바꿔 묶는다. 공개 때 훑는 것은 묶이지 않은 보기 모음뿐이다(큰 `//copy` 뒤에도 묶인 보기 수천 개를 틱마다 훑지 않는다).
- **다른 스레드 IMMEDIATE(검토 R3)**: 재생은 그 스레드가 본 블록(자기가 바꾼 섹션 포함)이 서버 칸에 들어온 뒤에만 만든다 — 들여오기 전 옛 블록(예: 화로)으로 다른 종류를 만들지 않는다.
- `BeaconBlockEntity.playSound`(생성기 `tools/compat/gen-nms-block-entities.py`), Paper `BeaconDeactivatedEvent`, `CraftBlock.at`, `CraftBlockEntityState#getTileEntity`, `ClientboundBlockEntityDataPacket` 공개 생성자·`create(BlockEntity, BiFunction)`.

## 3. 시험

- 새 `NmsBlockEntityWriteTest`(호환, 실서버): 주인 보기 = 서버 객체·지운 뒤 마지막 내용·IMMEDIATE 다시 만들기, 다른 스레드 `Level#getBlockEntity` null, setChanged(주인·다른 스레드), **`//set` 순서**(작업 스레드 지우기 → 저장에 내용물), **`//paste` 순서**(섹션 CAS → 동기 작업 IMMEDIATE + 싣기 → 서버 상자에 내용물), 다른 스레드 IMMEDIATE 가 블록 들여오기를 기다림, 스포너 `//set`·`//paste`, 신호기 지우기 길 전체, 떨어진 블록 엔티티·패킷.
- 검토(PR #447) 뒤 더한 것: 게임이 놓은 상자(묶이지 않은 보기)로 준비, 지운 타일 여러 개 저장(첫 하나만 기다림·꼴), 스컬크 감지기 리스너(NMS 지우기·게임 길), 내린 뒤 저장, 다음 공개 때 묶기, 다른 스레드 IMMEDIATE 가 옛 블록을 쓰지 않음(주인 작업 안에서 돌려 한 맞춤에 넣음 — 수정을 빼면 실패함을 확인), setChanged 는 region 저장 대상 표시를 단언.
- 새 `BlockEntityImmediateTest`(서버): 빠진 블록 엔티티 다시 만들기, 블록 엔티티 없는 블록, 생성 NBT 를 덮지 않음.
- 바꾼 시험: `NmsLiveBlockEntityTest`(옛 `furnaceInWorld` → `Level#getBlockEntity`·청크 `getBlockEntity`).

## 4. 성능

바뀐 서버 경로는 `getBlockEntity` 가 빈 칸에서 그 칸 블록을 한 번 더 읽고 "블록 엔티티 종류인가" 표를 보는 것뿐이다(바닐라도 같은 일을 한다). 클라우드에서 임시 측정(시험 레벨, 흙·공기 칸 4,096개 × 2,000번, 7판, 측정 코드는 커밋하지 않음): 빈 칸 한 번에 옛 CHECK 24~38 ns, 새 IMMEDIATE 25~35 ns — 판마다 +1~6 ns. 블록 엔티티가 있는 칸은 그대로다. 호환 쪽은 새 길(블록 엔티티 쓰기)만 더했고 기존 블록 읽기·쓰기 길은 바꾸지 않았다(거울 맞춤의 추가 비용은 블록 엔티티 덧칠이 있을 때만).

## 5. 남은 것·가정

- **서버 버그 함께 고침(검토 R1)**: `RegionLevel.removeBlockEntity` 가 게임 이벤트 리스너를 떼지 않았다 — 스컬크 감지기를 부수거나 바꿔도 리스너가 남아 진동을 받았다(이 PR 전부터). 바닐라처럼 뗀다.
- **바닐라 확인**: `broadcastBlockEntityIfNeeded`(틱 끝 블록 변경 방송)는 바닐라도 `Level#getBlockEntity`(IMMEDIATE)라, 같은 틱에 블록을 놓고 블록 엔티티만 지우면 방송 때 다시 만들어진다(서버가 이제 바닐라처럼 그렇게 한다).

1. **수용 조건 실측은 아직** — "상자·스포너가 있는 곳 `//set`, 상자 `//copy`·`//paste`(내용물 포함)가 Paper 와 같은 region 결과"는 FAWE 실제 jar 로 데스크탑에서 잰다(N2b-6 시나리오에 블록 엔티티 장면 추가). 이 PR 의 시험은 FAWE 가 부르는 순서를 그대로 흉내 낸 것이다.
2. `blockEntities.put`·`setBlockEntity`(블록 엔티티 객체를 청크에 바로 넣기)는 없다 — FAWE 2.16.2 는 부르지 않는다. 부르면 `UnsupportedNmsCall` 로 알린다.
3. **실측할 위험(검토)**: 살아 있는 타일을 다른 스레드가 저장하면(`//copy` 의 게으른 저장) 타일마다 주인 region 인박스를 한 번 기다린다(인박스는 틱마다 비움 — 평균 반 틱~한 틱). 상자 512개 `//copy`·`//stack`, `//set`·`//undo`, 다른 region 으로 `//stack` 을 데스크탑 시나리오(`tools/compat/fawe-paper/scenario_t198.py` B6~B9)에서 Paper 와 걸린 시간·결과로 잰다. 결과에 따라 묶어 저장하기를 정한다. 또 지운 타일 꼴 뜨기는 주인 틱에서 돈다(아무도 저장하지 않아도) — 상자 빽빽한 곳 `//set` 직후 틱 p95 를 같은 시나리오(B7 앞뒤 `mspt`)와 서버 측정으로 재고 +1 ms 를 넘으면 저장 요청이 있을 때만 뜨게 바꾼다.
4. `Level#getBlockEntity` 는 로드돼 있지 않은 청크면 null — Paper 1.21.1 은 틱 스레드에서 불러온다(region 스레드에서 멈춰 서서 불러오지 않는다는 스케줄러 규칙 6 때문의 차이).
5. 가정: 다른 스레드가 읽는 블록 엔티티 목록은 주인이 틱 끝에 낸 공개 판 + 자기 쓰기(N2b-3 과 같은 규칙). 주인이 같은 틱에 놓은 블록 엔티티는 다음 틱부터 보인다.
6. 종류별 필드·메서드(컨테이너 `getItem` 등)는 여전히 없다 — 내용은 NBT 로만 오간다.
