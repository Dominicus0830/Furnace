# A03 스케줄러·region·전역 스레드 안전 — 2차 전체 점검 조사 보고 (2026-11-08)

- 기준: 브랜치 `claude/quirky-wright-lra834`, 커밋 `8f8b188f`(PR #170 병합 시점). 코드는 고치지 않았다.
- 계획: `docs/audit/second-audit-plan.md` 1장·2장 A03.
- 재현 시험: `docs/audit/findings/A03-repro/*.java.txt`(5개). 쓰는 법은 맨 아래 "재현 시험 돌리기".
- 환경: 4코어·15GB 클라우드 컨테이너, gradle 9.5.0(`-PskipOracle=true`), JUnit 단독 실행.

## 요약

| 심각도 | 수 | ID |
|---|---|---|
| 치명 | 1 | A03-01 |
| 높음 | 1 | A03-02 |
| 중간 | 3 | A03-03, A03-04, A03-05 |
| 낮음 | 2 | A03-06, A03-07 |
| 참고 | 3 | A03-08, A03-09, A03-10 |

도미니코 결정이 필요한 항목은 없다(모두 "바닐라와 관측 결과가 같아야 한다"로 답이 정해진다). 그래서 `docs/user-todo/entries/` 파일은 만들지 않았다.

## 조사한 범위

- `world/SharedScheduler`(선택기·깨우기·상한·스핀), `world/Regionizer`(구역·합치기·나누기·작업 보내기·drain 차례·빌림 진입·close), `world/Region`(작업 큐·지연 작업·step/sprint 상태·합치기/나누기 데이터 이동·엔티티 넘겨주기), `world/TickState`, `sched/GlobalThread`, `FurnaceMain`의 전역 박동(`FixedRateBeat`)·자동 저장(`Repeating`)·종료 순서(`ShutdownSequence` 단계 몸통), `ServerWatchdog`·`Regionizer.stalledNanos`, `World.flushAllDrained`·`holdTicks`·`quiesce`·`closeForUnload`, `ServerCore.scheduledTick`.
- 스트레스: 무작위가 아닌 "다리 구역 넣기·빼기"로 합치기·나누기를 30~60초 동안 18~36회 되풀이하면서 생산자 스레드 3개가 청크 15~30개에 작업 수십만~수백만 개를 보냈다. `ThreadMXBean.findDeadlockedThreads`를 100ms마다 봤다.
- 바닐라 대조: `MinecraftServer.tickServer`(1.21.1, 난독화 `a(BooleanSupplier)`)를 `javap`로 봤다(자동 저장 계수).

## 조사하지 못한 범위(까닭)

- **5장 색인에서 뺀 것**: `RegionBorrow`(PR #144), 비동기 풀(#155·#159), 차원 이동·차원별 보더(#114), 포털 미리 올리기(#129). 빌림 쪽은 진입점만 읽었다.
- **region 경계 너머 블록 업데이트 순서**: region 사이에는 늘 반경(`merge-radius`) 이상의 빈 구역이 있어 이웃 블록 업데이트가 경계를 직접 넘지 못한다고 읽었다(이웃 청크가 올라오면 합쳐진다). 그래서 따로 시험하지 않았다. 경계를 넘는 것은 작업(`enqueue`)과 엔티티 넘겨주기뿐이고, 둘 다 아래 A03-01·A03-02로 다뤘다.
- **`/tick sprint` lockstep·`idle-pause`**: 기존 시험(`SharedPoolS4Test.sprintLockstepCompletesAcrossThreeDimensions`, idle-pause 시험)을 읽기만 했다. 새 재현은 만들지 않았다.
- **봇 접속 시험**: 하지 않았다. 패킷 순서 뒤바뀜(A03-02)이 실제 클라이언트에 어떻게 보이는지는 서버 단위 재현으로만 확인했다.

---

## 항목

### A03-01 종료 저장 중에도 게임 틱이 돌아, 차원 사이를 건너는 엔티티가 복제되거나 사라진다

| 칸 | 내용 |
|---|---|
| 심각도 | **치명**(엔티티·아이템 복제·소실) — 결과가 복제라 A04 와 겹친다. 원인은 종료 순서(A03 범위)라 여기 적고, 점검 스레드가 ID 를 정리한다 |
| 종류 | 스레드 안전 · 바닐라 차이 |
| 근거 | `FurnaceMain.java:485-506` FLUSH_WORLDS 는 차원마다 차례로 `w.flushAllDrained(...)` 를 부를 뿐 게임 틱을 붙잡지 않는다. region 은 `STOP_SCHEDULER`(`FurnaceMain.java:528`, `scheduler.close()`)까지 계속 돈다 — 그 사이 `PlayerData.awaitIo`(최대 30초), IO 풀 종료 대기(최대 30+30초)가 있다. 백업은 같은 문제를 알고 막는다: `maintenance/WorldBackup.java:1-4`("붙잡은 동안 엔티티·플레이어 넘겨주기가 없으므로 region 사이에서 엔티티가 둘이 되거나 사라지지 않는다"), `:176-180`(`holdTicks(true)` + `quiesce()`). 차원 이동은 떠나는 쪽에서 `Entity.depart`(`entity/Entity.java:540-556`, `CHANGED_DIMENSION` 으로 뺌) 뒤 목적 region 작업으로 도착한다(`Entity.java:572` `moveAcross`, `World.java:1523` `travel`). 같은 차원 안 region 사이도 `Region.handOffEntities`(`world/Region.java:1512-1531`)가 "빼고 → 작업으로 넣기"다. 바닐라는 `MinecraftServer.runServer` 루프가 끝난 뒤 `stopServer → saveAllChunks` 라 저장 뒤 틱이 없다 |
| 재현 | `ReviewA03ShutdownTest.entityCrossingBetweenDimensionFlushesIsDuplicatedOrLost`: Anvil 저장소를 쓰는 오버월드·네더 두 월드, 소 한 마리씩. 종료 순서처럼 오버월드 `flushAllDrained` → (오버월드 region tickCount 가 300ms 동안 11→18, 12→19 로 계속 오른다) → 그 사이 소 둘이 실제 차원 이동 경로(`teleportToDimension`)로 서로 반대 차원으로 건넌다 → 네더 `flushAllDrained` → 저장소 비우기 → 디스크에서 다시 읽기. **3/3회**: 오버월드→네더 소 **2마리**(바닐라 1), 네더→오버월드 소 **0마리**(바닐라 1). 시험의 이동은 명령으로 일으켰지만, 실제 서버에서는 마지막 저장 뒤에도 도는 게임 틱(포털·엔더 진주·`/tp` 함수·아이템 운반 포털)이 같은 이동을 일으킨다 |
| 영향 | 포털로 아이템·몹을 나르는 장치(아이템 청크 로더·포털 운반 농장), 종료 때 포털을 지나던 주민·탈것. 차원 순서상 오버월드에서 나간 것은 복제, 오버월드로 들어온 것은 소실. 같은 차원 안 region 사이(`handOffEntities`)도 region 마다 저장 시점이 달라 드물게 같은 일이 난다(가정 — 재현하지 않음). 추가 월드 내리기(`WorldManager.java:497-508` flushAll → `closeForUnload`)도 틱을 붙잡지 않는다(같은 원인, 가정) |
| 제안 | FLUSH_WORLDS 머리에서 백업과 같은 기준점을 잡는다: `core.holdGlobalTicks(true)`, 모든 차원 `holdTicks(true)`, `quiesceGlobal()`·각 `quiesce()` 완료를 기다린 뒤 한 번 더 모든 region 작업 큐를 비우는 차례(이미 떠난 엔티티의 도착 작업이 저장보다 먼저 돌게)를 두고 나서 flush. 종료는 되돌리지 않으므로 놓지 않는다. `WorldManager` 내리기도 같은 순서. 파일: `FurnaceMain.java`, `WorldManager.java`, 문서 `docs/api/lifecycle.md`(FLUSH_WORLDS 설명). 위험: 붙잡은 동안 도는 플러그인 onDisable 이 끝난 뒤라 플러그인 계약은 그대로다. 크기 **작음~중간**. 권장 **Opus high**(A04 와 함께 복제 시험을 정식 시험으로) |

### A03-02 region 합치기·나누기 도중 같은 청크로 보낸 작업의 순서가 뒤바뀐다

| 칸 | 내용 |
|---|---|
| 심각도 | **높음**(API 계약 위반, 플레이어 패킷 순서) |
| 종류 | 스레드 안전 · Paper/Spigot 호환(Folia 계약) |
| 근거 | API 문서 `furnace-api/.../sched/FurnaceScheduler.java:10-12`: "작업은 실행 시점의 주인 region 에서, 그 region 의 다른 작업과 **같은 스레드 순서로** 돈다". 실제 경로: `Regionizer.enqueue`(`world/Regionizer.java:351`)는 잠금 없이 `ownerOf` 로 주인을 읽고 그 region `inbox` 에 넣는다. ① 흡수 `absorb`(`:866`)는 구역 주인을 먼저 새 region 으로 바꾸고(`:872`) `absorbData`(`:879`) 뒤에야 `reroute(o)`(`:881`, `:370` — 죽은 큐의 작업을 하나씩 다시 `enqueue`)로 옛 작업을 **새 큐 끝에** 붙인다. 주인이 바뀐 순간부터 생산자가 넣는 새 작업은 새 큐에 바로 들어가므로, 아직 옮기지 않은 옛 작업보다 앞선다. 양보(`yielded`)한 region 은 한 틱 내내 작업이 쌓여 옮길 양이 크다. ② 나누기 `split`(`:886`)도 구역 주인을 먼저 바꾸고(`:950`) `splitData`(`:955`) 뒤 큐를 비워(`:960`) 다시 보낸다. ③ 남의 청크 작업을 꺼낸 region 은 지금 주인 큐 끝으로 다시 보낸다(`Region.java:168`). 넷티 스레드가 보내는 플레이어 패킷 처리(`ConnectionHandler` 의 `world.runAt(...)` 40여 곳 — 땅 파기·아이템 사용·메뉴 클릭·탈것 조종)와 플러그인 `region(pos).execute` 가 모두 이 경로다 |
| 재현 | `ReviewA03OrderTest.perChunkFifoSurvivesMergeAndSplit`(생산자 3, 30초, 다리 구역 넣기·빼기 18회): **5/5회 실패**. 다리 구역에도 보낼 때(다리가 계속 다시 생겨 거의 늘 합쳐진 채 흡수가 되풀이된다) 뒤바뀜 9 778·8 421건(청크 29·30개), 고정 구역에만 보낼 때(region 수 1↔5 로 합치기·나누기가 번갈아 난다) 6 579·8 357건(청크 15개 모두). 보내는 속도를 낮춰도(`-Dfurnace.a03.parkNanos=2000000`, 생산자당 초당 약 4천 개, 60초, 합치기·나누기 36회) **65건**(청크 8개). 모든 실행에서 빠진 작업·두 번 돈 작업·교착(ThreadMXBean)은 0 이다. 예: "chunk 0 p0: 26 after 43" — 26~42번이 43번 뒤에 돌았다 |
| 영향 | 같은 플레이어가 같은 칸에 보낸 패킷(예: 파기 시작→끝, 사용→놓기, 같은 메뉴 클릭 연속)이 region 이 합쳐지는 순간 거꾸로 처리될 수 있다. 결과는 행동 실패·블록 되돌림·메뉴 어긋남이고, 메뉴 클릭 순서가 뒤바뀌면 복제·소실 가능성도 있다(A04 로 넘겨 확인 요청). 플러그인 작업 순서에 기대는 코드(설정 → 읽기)도 깨진다. 합치기는 플레이어가 서로 다가갈 때마다 나므로 드물지 않다 |
| 제안 | 주인을 바꾸는 것과 옛 큐를 옮기는 것을 생산자에게 한 번에 보이게 한다. 방법 예: (가) `Task` 에 서버 전체 증가 번호를 달고, 흡수·나누기 때 옮긴 작업을 새 큐에 번호 순으로 끼워 넣거나(큐를 번호 우선 큐로) 꺼낼 때 두 큐를 번호로 합친다. (나) region 마다 "앞으로 넘기기(forward)" 표시를 두고, 주인을 바꾸기 **전에** 옛 큐를 통째로 새 큐 앞에 붙인 뒤 주인을 바꾸며, 그 사이 옛 큐에 늦게 들어온 것은 생산자가 스스로 다시 보낸다(지금 `enqueue` 의 `!r.alive → reroute` 처럼). (다) `enqueue` 를 읽기 잠금, 흡수·나누기를 쓰기 잠금으로(가장 단순하지만 넷티 스레드 경로에 잠금이 생긴다 — 성능 측정 필요). `Region.java:168` 의 다시 보내기도 같은 규칙. 파일: `Regionizer.java`, `Region.java`. 정식 시험은 재현 시험을 줄여 쓰면 된다(10초·뒤바뀜 0). 크기 **중간**. 권장 **Opus high** |

### A03-03 `/tick step` 도중 region 이 합쳐지면 원래 엔티티가 남은 걸음을 잃는다

| 칸 | 내용 |
|---|---|
| 심각도 | 중간 |
| 종류 | 바닐라 차이 |
| 근거 | 남은 걸음은 region 마다 따로(`Region.stepsLeft`, `Region.java:1119-1130`)이고 세대가 바뀔 때만 다시 채운다(`:1193-1195`). 새 region 은 걸음 0(`Regionizer.java:335`), 나뉜 조각은 부모 값(`:940`). 흡수 `absorb`(`:866`)·`absorbData`(`Region.java:268`)는 걸음을 맞추지 않는다 — 흡수하는 쪽 값만 남는다. sprint·수동 시계는 병합을 미루는 규칙이 있지만(`sprintCompatible` `:807`, `manualCompatible` `:833`) step 에는 없다. 바닐라 1.21.1 `ServerTickRateManager.stepGameIfPaused` 는 서버 틱 수 하나로 세므로 `/tick step N` 동안 올라와 있는 모든 엔티티가 남은 걸음을 다 받는다 |
| 재현 | `ReviewA03StepMergeTest.stepCountSurvivesMerge`: 정지 → `/tick step 40` → 약 12걸음 뒤 새 region(걸음 0)을 만들고 다리 구역으로 합친다. 3회 실행 18번 중 **3번** 원래 소가 **12걸음**에서 멈췄다(바닐라 40). 나머지 15번은 원래 소 40걸음이고, 그때 새 소는 27걸음을 받았다 — 이것은 `context-notes.md:173`("`/tick step` 도중 새로 생긴 region 은 그 step 을 받지 않고 정지 상태로 남고")와도 다르지만 바닐라(약 28)에 가깝다 |
| 영향 | `/tick step` 으로 장치를 한 틱씩 보는 사람이 플레이어 근처에서 새 청크가 올라올 때(이동·강제 청크) 일부 엔티티가 멈춘 것처럼 본다. 드물다 |
| 제안 | 걸음을 region 이 아니라 전역 진행으로 센다: `TickState` 에 이번 세대에서 "전역이 낸 걸음 수"를 두고, region 은 자기가 마친 걸음 수와 비교해 앞서지 않게(sprint lockstep 과 같은 방식) 한다. 그러면 새 region 도 남은 걸음을 받아 바닐라와 같아지고, 병합은 sprint 처럼 마친 수가 같을 때까지만 미룬다. 문서 `context-notes.md:173` 도 고친다. 파일: `TickState.java`, `Region.java`, `Regionizer.java`. 크기 **작음~중간**. 권장 **Sonnet high** |

### A03-04 공유 풀에서 무거운 차원이 둘이면 가벼운 셋째 차원이 매 틱 늦는다

| 칸 | 내용 |
|---|---|
| 심각도 | 중간 |
| 종류 | 성능 · 스레드 안전(공정성) — 측정은 A17 와 겹친다 |
| 근거 | 상한은 차원마다 `T−1`(`SharedScheduler.java:119`, 선택 `:229`)이라, 다른 차원 큐에 일이 있어도 무거운 차원 둘이 합쳐 워커 T 개를 모두 채울 수 있다. `shared-scheduler-default-2026-11-07.md` "남은 것" 마지막 줄이 이 경우를 "측정하지 않았다"고 적었다 |
| 재현 | `ReviewA03TwoHeavyTest.lightDimensionWithTwoHeavyNeighbours`(워커 4, 무거운 차원은 60ms 틱 region 4개): **3/3회**. 무거운 차원 1개(대조)일 때 가벼운 차원 틱 간격 50±10ms 벗어남 0/74, p50 50.0ms. 무거운 차원 2개일 때 60/74~74/74 벗어남, p50 60.0~60.1ms, 최대 60.2~63.2ms — 가벼운 차원이 약 16.7 TPS 로 돈다. 차원별 풀(`shared-region-pool=false`)에서는 OS 가 나눠 줘 늦지 않는다(같은 문서 C 표) |
| 영향 | 오버월드와 네더 둘 다에 렉 기계가 있으면 엔드(또는 추가 월드)에 있는 플레이어가 이유 없이 느려진다. 기본값이 공유 풀(Q5)이라 기본 설정에서 난다 |
| 제안 | 상한을 "차원마다 T−1"에서 "지금 도는 틱이 없는데 할 일이 있는 차원이 하나라도 있으면, 나머지 차원 합이 T−1 을 넘지 않게"로 바꾼다(가장 굶은 차원 몫 워커 하나를 늘 남긴다). 또는 굶음 판정(class 0)이 된 차원이 있으면 다른 차원은 새 틱을 꺼내지 않는다. 파일: `SharedScheduler.java`(`select`, `Regionizer.tryTake` 상한 확인). 측정: `MultiDimensionTickBench.overloadScenarios` 에 "무거운 차원 둘" 장면을 더한다. 크기 **작음**. 권장 **Opus high**(스케줄러 공정성) |

### A03-05 전역 박동이 던진 예외는 로그 없이 사라지고 그 박동의 나머지(시간·날씨·예약 함수)를 건너뛴다

| 칸 | 내용 |
|---|---|
| 심각도 | 중간 |
| 종류 | 스레드 안전(실패 처리) · 관측 |
| 근거 | `FurnaceMain.FixedRateBeat.run`(`FurnaceMain.java:723-765`)은 `task.run()`(`:752`, `ServerCore.scheduledTick`)을 try/finally 로만 감싸고, 다음 박동은 `CompletableFuture.runAsync(this, … scheduler.global().execute(r))`(`:772`)로 건다. `GlobalThread.run`(`sched/GlobalThread.java:77-88`)은 예외를 그 future 에 담을 뿐이고 박동은 future 를 버린다. 자동 저장 `Repeating`(`FurnaceMain.java:706-716`)도 같다. `ServerCore.tick()`(`ServerCore.java:921-945`)에서 감싼 것은 차원 스포너뿐이다 — `functions.tick()`(#tick 함수), 보더, 날씨, 잠, 예약 함수가 던지면 사라진다. region 틱은 `Regionizer.runOne` 이 `[TICK] region #N 실패` 로 남긴다. 바닐라·Paper 는 `tickServer` 예외를 크래시 리포트로 남기고 멈춘다 |
| 재현 | `ReviewA03BeatErrorTest.exceptionInGlobalBeatIsNotLogged`: 늘 던지는 박동 600ms — **3/3회** 박동 12번 모두 예외, 경고 이상 로그 **0건**, 예외 뒤 단계 0번 실행. 워치독도 박동이 계속 돌아 알아채지 못한다 |
| 영향 | 전역 틱 쪽 버그(또는 플러그인이 #tick 함수에 단 명령)가 시간·날씨를 조용히 멈추게 해도 운영자가 알 길이 없다 |
| 제안 | 박동·`Repeating` 의 몸통을 try/catch 로 감싸 `[TICK] 전역 틱 실패` 를 남기고(같은 원인 경고는 묶어서), 몇 번 연속이면 크래시 리포트(바닐라처럼 멈출지, 경고만 할지는 운영 정책 — 기본은 로그만으로 충분). 파일: `FurnaceMain.java`, 필요하면 `GlobalThread`. 크기 **작음**. 권장 **Sonnet medium** |

### A03-06 추가 월드를 내릴 때 region 작업 큐에 남은 작업의 future 가 영원히 끝나지 않는다

| 칸 | 내용 |
|---|---|
| 심각도 | 낮음 |
| 종류 | 스레드 안전(작업 유실) |
| 근거 | `World.closeForUnload`(`world/World.java:182-187`) → `Regionizer.close`(`Regionizer.java:1404-1416`)는 `closed=true` 만 세운다. 그 뒤 `tryTake` 는 null, `schedule`(`:785`)은 넣지 않으므로 큐에 남은 작업은 돌지도 실패로 끝나지도 않는다. 닫힌 뒤 새로 넣는 것은 `RejectedExecutionException` 으로 곧바로 실패한다(`World.java:313-336`, 대조) |
| 재현 | `ReviewA03ShutdownTest.tasksQueuedAtUnloadNeverComplete`: 지연 작업 차례에 region 을 300ms 붙잡은 동안 `submitAtChunk` 를 하나 넣고 `closeForUnload` → **3/3회** 그 future 가 3초 안에 끝나지 않는다 |
| 영향 | 마지막 저장(`WorldManager.java:501`)과 닫기(`:508`) 사이에 플러그인·명령이 그 월드에 보낸 작업이 사라지고, 결과를 기다리는 쪽(`join`)은 멈춘다. 전역 스레드가 기다리면 서버 전체가 멈춘다(워치독이 죽인다) — 가정, 그런 호출자를 찾지는 않았다 |
| 제안 | `Regionizer.close` 에서 남은 region 큐를 비워 각 작업을 실패로 끝내거나(작업에 실패 통로가 없으면 `submitAtChunk` 의 future 를 `RejectedExecutionException` 으로), A03-01 의 붙잡기 뒤 "큐 비우기 차례"를 한 번 돌린 다음 닫는다. 파일: `Regionizer.java`, `World.java`. 크기 **작음**. 권장 **Sonnet medium** |

### A03-07 level.dat 자동 저장·전역 지연 작업이 틱이 아니라 벽시계(틱 × 50ms)로 센다

| 칸 | 내용 |
|---|---|
| 심각도 | 낮음 |
| 종류 | 바닐라 차이 |
| 근거 | `FurnaceMain.java:552` 자동 저장은 `scheduler.global().executeDelayed(new Repeating(...), autosaveTicks)` 이고 `GlobalThread.executeDelayed`(`sched/GlobalThread.java:124-139`)는 `delayTicks * 50ms` 벽시계 지연이다. 바닐라 1.21.1 `MinecraftServer.tickServer`(`a(BooleanSupplier)`, javap 확인)는 `tickServer` 마다 `ticksUntilAutosave`(필드 `U`)를 하나씩 줄여 0 이 되면 저장한다 — `tickServer` 는 `/tick rate` 만큼 불리고 `/tick freeze` 중에도 불린다. region 청크 자동 저장은 region 틱으로 센다(`Region.autosaveCounter`)라 이 항목은 level.dat·전역 쪽만이다 |
| 재현 | 코드 대조만(시험 없음). `/tick rate 100` 이면 바닐라는 1분마다, Furnace 는 5분마다 level.dat 를 저장한다. `/tick sprint` 중에도 같다 |
| 영향 | 작다. 크래시 때 level.dat(시간·날씨·게임 규칙) 되돌림 폭이 바닐라와 다르다 |
| 제안 | 자동 저장을 전역 박동 안에서 `ServerCore.tick()` 횟수로 센다(박동이 낸 틱 수 = rate 반영). 플러그인 `GlobalScheduler.executeDelayed` 의 틱 의미는 A14 계약이라 여기서는 고치지 않고 A14 로 넘긴다(A03-08). 크기 **작음**. 권장 **Haiku low**~**Sonnet medium** |

### A03-08 (참고, A14·A15 로 넘김) 플러그인 전역 지연 작업은 `/tick rate`·`freeze`·서버 지연과 무관하게 벽시계로 돈다

- 근거: `GlobalThread.executeDelayed`(`sched/GlobalThread.java:133-138`). Bukkit 스케줄러(Paper `mainThreadHeartbeat`)는 서버 틱으로 센다. 렉이 걸리면 Paper 의 `runTaskLater(…, 20)` 는 늦게 돌지만 Furnace 는 1초에 돈다. 바닐라 결과가 아니라 API 계약이라 A14(Furnace API)·A15(Bukkit 스케줄러 어댑터)가 판단한다.

### A03-09 (참고, A19 로 넘김) CI 의 기본 시험은 차원별 풀 경로로 돌고, 운영 기본값(공유 풀)은 `SharedPoolS4Test` 몇 개만 덮는다

- 근거: `shared-scheduler-default-2026-11-07.md` "시험" 마지막 줄, `SharedScheduler.TEST_SHARED`(`SharedScheduler.java:90`). 서버 전체 시험을 공유 풀로 돌린 기록은 그 문서의 두 번(3038·3039개, 실패 0)뿐이다. CI 조각 하나를 `-Dfurnace.test.sharedRegionPool=true` 로 돌릴지 A19 가 정한다.

### A03-10 (참고) 교착·유실은 찾지 못했다

- `ReviewA03OrderTest` 실행 5번(합계 수천만 작업, 합치기·나누기 18~36회)에서 `findDeadlockedThreads` 표본 0, 빠진 작업 0, 두 번 돈 작업 0. `RegionWide`(저장) 작업은 나누기 때 조각마다 복제되고 흡수 때 한 번 더 돌 수 있다(`Regionizer.java` `split`·`reroute`) — 저장이라 결과는 같다.
- 워치독(`ServerWatchdog`, `Regionizer.stalledNanos`)은 틱 중인 region 의 시작 시각과 큐 머리 지연을 본다. 전역 스레드가 빌린 region(`RegionBorrow`)은 `running` 에도 큐에도 없어 region 쪽으로는 안 보이지만, 그동안 전역 박동이 멈추므로 전역 탐침이 잡는다 — 문제로 보지 않았다.

---

## 재현 시험 돌리기

파일을 컴파일 대상 자리로 복사해 돌린다(커밋하지 않는다).

| 파일 | 놓을 곳 | 명령(`--tests`) | 기준 커밋 결과 |
|---|---|---|---|
| `ReviewA03ShutdownTest.java.txt` | `furnace-server/src/test/java/org/domi/furnace/server/world/ReviewA03ShutdownTest.java` | `org.domi.furnace.server.world.ReviewA03ShutdownTest` | 두 시험 모두 실패 3/3 |
| `ReviewA03OrderTest.java.txt` | `…/server/world/ReviewA03OrderTest.java` | `org.domi.furnace.server.world.ReviewA03OrderTest` (선택: `-Dfurnace.a03.toBridges=false`, `-Dfurnace.a03.parkNanos=2000000`, `-Dfurnace.a03.durationMs=60000`) | 실패 5/5(몰아치기 4, 낮은 속도 1) |
| `ReviewA03StepMergeTest.java.txt` | `…/server/world/ReviewA03StepMergeTest.java` | `org.domi.furnace.server.world.ReviewA03StepMergeTest` | 실패 2/3(시도 18번 중 3번 걸음 잃음) |
| `ReviewA03TwoHeavyTest.java.txt` | `…/server/world/ReviewA03TwoHeavyTest.java` | `org.domi.furnace.server.world.ReviewA03TwoHeavyTest` | 실패 3/3 |
| `ReviewA03BeatErrorTest.java.txt` | `furnace-server/src/test/java/org/domi/furnace/server/ReviewA03BeatErrorTest.java` | `org.domi.furnace.server.ReviewA03BeatErrorTest` | 실패 3/3 |

명령 예: `./gradlew -PskipOracle=true :furnace-server:test --tests 'org.domi.furnace.server.world.ReviewA03OrderTest' -i | grep A03-`. 모두 고쳐진 뒤에는 통과해야 한다(ShutdownTest 의 첫 시험은 고친 종료 순서를 시험 안에서 따라 하도록 바꿔야 한다 — 지금 시험은 FurnaceMain 의 순서를 손으로 흉내 낸다).
