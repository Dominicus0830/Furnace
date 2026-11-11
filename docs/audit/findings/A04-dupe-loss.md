# A04 복제·소실 탐색 — 조사 보고서 (2026-11-08)

2차 전체 점검 1단계, 파트 A04. 계획은 `docs/audit/second-audit-plan.md` 1장·2장 A04 절.

- **기준 커밋**: `8f8b188f` (`claude/quirky-wright-lra834`, PR #170 병합 시점. PR #167 전리품 상자 수레 포함)
- **조사한 범위**: 플레이어 인벤토리를 쓰는 스레드 지도(연결 이벤트 루프 / 플레이어 region / 블록·엔티티 region), 블록 설치·아이템 사용·엔티티 상호작용의 손 칸 반영, Q·Ctrl+Q·손 바꾸기·인벤토리 창 클릭, 퇴장 저장 순서, 플러그인이 취소한 버리기, 엔티티 용기(상자 수레·배) 클릭 경로, 블록 용기 클릭 경로.
- **조사하지 못한 범위**: region 합치기·나누기 도중 엔티티 이중 소속(A03 스트레스와 겹쳐 A03 결과를 기다림), 차원 이동 중 아이템 엔티티(PR #126·#142 검토 범위와 겹침), 깔때기·수레가 region 경계를 건너 용기를 만지는 경우(경계 임시 티켓 구조 확인만, 시험 없음), 봇 두 개 실제 클라이언트 시험(아래 A04-01 은 진짜 루프백 소켓 시험으로 대신함). H4 묶음 C·D(값 타입·인벤토리 이벤트)는 진행 중이라 제외.
- **제외**: PR #164·#165·#166(사망 드롭·취소·남길 목록), #126(탈것째 포털), #142(엔드 포털 중력 블록) 검토 지적.

## 요약

| 심각도 | 개수 | ID |
|---|---|---|
| 치명 | 2 | A04-01, A04-02 |
| 중간 | 1 | A04-03 |
| 낮음 | 1 | A04-04 |
| 참고 | 2 | A04-05, A04-06 |

뿌리는 하나다. **플레이어 인벤토리는 연결 이벤트 루프에서 바로 바뀌는데(Q·Ctrl+Q·인벤토리 창 클릭·퇴장 저장), 월드 쪽 결과(블록 설치·몹 먹이·던지기)는 region 큐에서 나중에 돈다.** 바닐라는 같은 플레이어의 패킷을 한 스레드에서 받은 순서대로 처리하므로 둘이 섞이지 않는다. Furnace 는 손 칸 반영을 "비교 후 교체(`PlayerInventory.replace`)"로 막아 두었지만, 비교가 실패했을 때 **이미 월드에 낸 결과를 되돌리지 않는다**. 그래서 소실 대신 복제가 된다.

---

## 항목

### A04-01 — 블록 설치 직후 Q·Ctrl+Q 로 설치한 아이템이 복제된다(셜커 상자는 내용물째)

| 칸 | 내용 |
|---|---|
| 심각도 | 치명 |
| 종류 | 스레드 안전 · 바닐라 차이(복제) |
| 근거 | `net/ConnectionHandler.java:3973` `handleUseItemOn` 이 **이벤트 루프에서** 손 스택 `main`·`off` 를 떠 두고 `world.runAtRequested` 로 region 에 넘긴다. region 은 그 스택으로 `BlockPlacement.useItemOn` 을 돌려 블록을 놓은 뒤 `:4078` `inv.replace(slot, expect=main, now=63개)` 로 손을 줄인다. 그 사이 이벤트 루프의 `dropFromSelected`(`:3312`, Q/Ctrl+Q)가 칸을 바꾸면 `PlayerInventory.replace`(`entity/PlayerInventory.java:44`)가 거짓을 돌려주고, 반환값은 버려진다. 블록은 이미 놓였다. 바닐라: `ServerGamePacketListenerImpl.handleUseItemOn` → `ServerPlayerGameMode.useItemOn` → `BlockItem.place` 가 스택을 그 자리에서 줄이고, 다음 패킷 `handlePlayerAction(DROP_ALL_ITEMS)` → `ServerPlayer.drop(true)` 가 남은 63 을 던진다(같은 서버 스레드, 받은 순서). |
| 재현 | `A04-repro/A04DupeReproTest.java.txt`(furnace-server `plugin` 패키지에 두고 `--tests '*A04DupeReproTest'`). region 스레드를 잡아 둔 채 use_item_on → player_action 을 보내고 놓는다. 기준 커밋 결과: `placeThenDropAllBeforeRegionRuns` 돌 64 → **65**(설치 1 + 바닥 64), `placeThenDropOneBeforeRegionRuns` **65**(설치 1 + 인벤토리 63 + 바닥 1), `placeFullShulkerThenDropAllDuplicatesContents` 다이아 64 든 셜커 상자 → **놓인 상자(다이아 64) + 던진 상자(다이아 64)**. 실제 소켓: `A04-repro/A04RealSocketDupeTest.java.txt`(furnace-test 모듈) — 진짜 루프백 접속에서 두 패킷을 이어 쓰기만 했다(잡아 두기 없음). region 한가함 **24/60 회 복제**, region 5ms 바쁨 3/60 회, 설치만 보낸 대조군 0/5. 손 바꾸기(F, `placeThenSwapHandsBeforeRegionRuns`)는 통과 — `swapHands`(`:3356`)는 두 칸을 신원 비교로 함께 바꾸고 플러그인 구독자가 있으면 소유 스레드로 넘겨, 결과가 63(설치 반영)으로 맞았다. |
| 제안 | 손 스택을 region 작업 안에서 뜨게 한다(`handleUseItemOn` 의 `main/off/head` 를 람다 안 `inv.get` 으로). 그래도 region 이 읽은 뒤 이벤트 루프가 바꾸는 좁은 틈이 남으므로, 근본적으로는 **같은 플레이어의 인벤토리 쓰기를 한 줄로 세운다**: Q·Ctrl+Q·인벤토리 창(0) 클릭·손 바꾸기를 플레이어 region 큐로 보내거나(바닐라 순서와 같아짐, 지연 최대 한 틱), 반대로 설치 쪽이 `synchronized(inv)` 안에서 "칸이 그대로인지 확인 → 설치 → 줄이기"를 한 번에 하게 한다(설치 중 플러그인 이벤트가 있어 잠금이 길어짐 — 첫 방식 권장). `replace` 실패를 로그만 남기고 넘어가는 다른 곳(`world/Region.java:1907` `writeBackHand`, `:4084` 갑옷)도 같은 줄 세우기로 함께 해결된다. 파일: `net/ConnectionHandler.java`(`handleUseItemOn`, `dropFromSelected`, 창 0 클릭), `world/Region.java`. 위험: 패킷 순서·클라이언트 예측 재전송, `CancelResyncE2ETest`·`PlayerDeathDropsRaceTest` 영향. 크기 중간. **Opus high**. |

### A04-02 — 퇴장 저장이 큐에 남은 설치보다 먼저 떠져, 다시 접속하면 아이템이 복제된다

| 칸 | 내용 |
|---|---|
| 심각도 | 치명 |
| 종류 | 스레드 안전 · 바닐라 차이(복제) |
| 근거 | `net/ConnectionHandler.java:4147` `channelInactive` 가 이벤트 루프에서 곧바로 `savePlayerSnapshot`(`:4191` → `:4201` `inv.snapshot()`)을 뜬다. 끊기 전에 받아 region 큐로 넘긴 작업(use_item_on 설치, `:4078` 손 줄이기)은 그 뒤에 돌고, region 은 끊긴 플레이어를 다음 틱(`world/Region.java:1218`)에야 뺀다. 그래서 저장 = 줄이기 전 인벤토리, 월드 = 설치 뒤. 바닐라: 패킷은 서버 스레드에서 차례로 처리되고, 끊김 처리(`Connection.handleDisconnection` → `onDisconnect` → `PlayerList.remove` → `save`)도 같은 스레드라 앞 패킷이 먼저 끝나거나, 끊긴 뒤엔 `PacketUtils` 가 `shouldHandleMessage`(연결 살아 있음) 검사로 버린다 — 어느 쪽이든 "저장 64 + 블록 1"은 없다(난독화 jar 대조는 안 함, Mojang 이름 기준 가정). |
| 재현 | `A04DupeReproTest.queuedPlaceAfterDisconnectSnapshotDuplicates`: 돌 64, region 잡아 둠 → use_item_on → 채널 닫기 → 놓음. 기준 커밋: **놓인 블록 1 + 저장 파일 64 = 65**. 실제로는 "설치하고 바로 접속 끊기"를 반복하면 한 번에 1 개씩, 셜커 상자면 내용물째(A04-01 과 같은 이유) 늘어난다. |
| 제안 | 퇴장 저장을 플레이어 region 이 끊김을 처리하는 자리(`Region.java:1218` 블록, 바닐라 `PlayerList.remove` 자리)로 옮기거나, `channelInactive` 가 "이 플레이어 region 큐 비우기 → 그 다음 저장"으로 한 번 건너가게 한다. 끊긴 뒤 큐에서 꺼낸 그 플레이어의 패킷 작업은 바닐라처럼 버리는 편이 단순하다(`players.containsKey`/`isDisconnected` 검사를 region 작업 입구에). 위치는 지금처럼 IO 위치(`ix/iy/iz`)를 넘기면 된다. 파일: `net/ConnectionHandler.java`(`channelInactive`·`savePlayerSnapshot`), `world/Region.java`. 위험: 종료 순서(`ShutdownSequence` 의 `PlayerData.awaitIo`), 끊김 직후 같은 UUID 재접속 읽기 순서(`playerIo` 직렬 실행기 유지 필요), `ConnectionInputTest.disconnect_*`. 크기 중간. **Opus high**. |

### A04-03 — 플러그인이 취소한 버리기 직후 끊기면 그 아이템이 사라진다

| 칸 | 내용 |
|---|---|
| 심각도 | 중간 |
| 종류 | Paper/Spigot 호환 · 스레드 안전(소실) |
| 근거 | `dropFromSelected`(`net/ConnectionHandler.java:3312`)가 이벤트 루프에서 칸을 먼저 줄이고 `spawnDrop` 으로 region 에 던지기를 맡긴다. region 의 `playerDrop`(`world/Region.java:1732`)이 `PlayerDropItemEvent` 취소를 보면 `ClientResync.returnToSlot`(`:1737`)으로 칸에 돌려놓는데, 그 사이 끊겨 저장이 이미 떠졌으면 돌려놓은 아이템은 끊긴 플레이어 객체에만 남고 저장되지 않는다. Paper: `handlePlayerAction` → `ServerPlayer.drop` → `PlayerDropItemEvent` 취소면 같은 스레드에서 바로 손에 되돌린다. 끊김 저장은 그 뒤. |
| 재현 | `A04DupeReproTest.cancelledDropReturnedAfterDisconnectSnapshotIsLost`: 버리기 취소 구독, 다이아 4, region 잡아 둠 → Q → 채널 닫기 → 놓음. 기준 커밋: **저장 3, 바닥 0**(끊긴 객체 인벤토리 4). 하나 사라짐. |
| 제안 | A04-02 를 고치면(저장을 region 쪽 끊김 처리 뒤로) 함께 풀린다. 따로 고친다면 버리기를 region 에서 "이벤트 → 칸 줄이기 → 던지기" 순서로(바닐라·Paper 순서). A04-01 의 줄 세우기와 같은 작업으로 묶는 것을 권장. 크기 작음(A04-01·02 에 얹으면). **Opus high**(묶음으로). |

### A04-04 — region 이 읽은 손 스택으로 칸을 비교 없이 덮어쓰는 곳들(몹 먹이·액자·갑옷 거치대·이름표·스폰 알)

| 칸 | 내용 |
|---|---|
| 심각도 | 낮음(재현 못 함, 코드상 위험) |
| 종류 | 스레드 안전 |
| 근거 | region 스레드에서 `inv.get` 으로 읽고 몇 줄 뒤 `inv.set(ws, held-1)` 로 비교 없이 쓴다: `entity/Mob.java:253` `usePlayerItem`, `entity/ItemFrame.java:151`, `entity/ArmorStand.java:219·224` `swapSlot`, `entity/ItemEffects.java:143·185`. 그 사이 이벤트 루프의 Ctrl+Q 가 칸을 비우면 덮어쓰기가 칸을 되살린다(Ctrl+Q 64 개 + 칸 63 개). 블록 설치 쪽(`replace`)과 달리 비교조차 없다. 틈은 같은 메서드 안 몇 줄이라 좁다. |
| 재현 | `A04DupeReproTest.feedingRacesDropAll`: 아기 소 + 밀 64, 상호작용과 Ctrl+Q 를 거의 동시에 400 회. 기준 커밋 **0/400**(재현 안 됨) — 가정. |
| 제안 | A04-01 의 줄 세우기를 하면 따로 손댈 필요 없다. 줄 세우기를 안 하면 이 다섯 곳을 `replace(ws, held, held-1)` 로 바꾸고 실패면 효과를 내지 않게(먹이 주기 전 확인). 크기 작음. **Sonnet medium**. |

### A04-05 — 인벤토리 창(0) 클릭의 "놓기"가 줍기와 겹치면 주운 개수가 덮어써질 수 있다

| 칸 | 내용 |
|---|---|
| 심각도 | 참고(재현 못 함) |
| 종류 | 스레드 안전 |
| 근거 | 창 0 클릭은 이벤트 루프에서 `menuLock` 만 쥐고 돈다(`net/ConnectionHandler.java` `handleContainerClick`, 블록 없는 창). 커서를 칸에 놓을 때 `PlayerInventoryContainer.set`(`menu/PlayerInventoryContainer.java:23`)은 읽기와 쓰기를 따로 잠근다. 같은 때 region 의 줍기(`entity/ItemEntity.java:195` `inventory().add`, `PlayerInventory` 잠금)가 그 칸에 더하면 덮어쓰기로 사라질 수 있다. 꺼내기(`remove`)는 비교 후 교체라 안전. |
| 재현 | `A04DupeReproTest.inventoryClickPlaceRacesPickupAdd`(줍기를 다른 스레드의 `inventory().add` 로 대신): **0/300** — 가정. |
| 제안 | A04-01 의 줄 세우기(창 0 클릭을 플레이어 region 으로)를 하면 함께 사라진다. 따로는 손대지 않아도 된다. |

### A04-06 — 확인했지만 문제없던 경로(참고)

| 칸 | 내용 |
|---|---|
| 심각도 | 참고 |
| 종류 | 시험 공백 |
| 근거 | ① 엔티티 용기(상자 수레·배·깔때기 수레) 창 클릭: `ContainerBackedMenu.containerBlockPos` 가 엔티티 좌표를 돌려줘 그 region 에서 돌고, `EntityContainer.stillValid` 가 `MenuBlockContext.ownsEntity` 로 소유를 확인한다 — 수레가 다른 region 으로 건너간 뒤 온 클릭은 버려진다. ② 블록 용기 클릭·닫기·부수기는 모두 블록 region 큐에서 차례로 돈다(`runOnBlockRegion`, 청크 내려감 검사 포함). ③ 손 바꾸기는 두 칸 신원 비교(A04-01 재현에서 통과). ④ 퇴장 중 큐에 남은 창 클릭은 `applyContainerClick` 의 `containerMenu() != menu` 검사로 버려진다. ⑤ 퇴장 때 인벤토리 창 제작 격자·커서는 저장 전에 돌려준다(`channelInactive` 의 `removed`). |
| 재현 | ①②④ 코드 확인만. ③ 시험 통과. |
| 제안 | A19 가 ①(수레가 region 경계를 건너는 중 클릭)을 시험으로 남기면 좋다. |

---

## 다음 단계에 넘길 것

- A04-01·02·03(·04·05)은 한 작업 묶음 "플레이어 인벤토리 쓰기 한 줄로 세우기"로 고치는 것을 권장한다. 고친 뒤 이 보고서의 재현 시험 11개(`A04DupeReproTest` 8개 + 소켓 시험 3개)를 그대로 회귀 시험으로 쓸 수 있다(소켓 시험의 대조군 `placeOnlyControl` 포함).
- 도미니코 결정이 필요한 항목은 없다(모두 바닐라 결과로 맞추는 쪽이 정답). 줄 세우기로 Q·창 클릭이 최대 한 틱 늦게 반영되는 것은 관측 결과가 바닐라와 같아지는 방향이다.
