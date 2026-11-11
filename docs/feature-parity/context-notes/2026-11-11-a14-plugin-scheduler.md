# 2026-11-11 A14 수정 — 플러그인 작업 정리·전역 지연 틱

A14 점검(`docs/audit/findings/A14-plugin-api-contract.md`)의 A14-01~04·06~09·11·12 수정. A14-05(책 편집 취소)는 클라이언트 확인 대기라 건드리지 않았다.

- **전역 지연 = 서버 틱(A14-04·12)**: `GlobalThread` 는 이제 `ScheduledThreadPoolExecutor`(스레드 1개)다. 첫 `executeDelayed` 때 50ms 고정 박자 박동을 걸고, 박동마다 `ticksPerBeat`(ServerCore 가 넣는다: sprint 중 0, 아니면 rate/20) 만큼 틱을 쌓아 때가 된 작업을 예약 순서대로 그 자리에서 돌린다. `/tick freeze` 중에도 세고(Paper 도 정지 중 heartbeat 는 돈다), `/tick sprint` 중에는 `ServerCore.tickSprint` 가 sprint 틱마다 `sprintTick()` 으로 하나씩 낸다. 0·1 은 다음 틱(예전엔 0 이 곧바로였다). 수동 시계는 같은 큐를 `advanceManualTick` 으로 돌린다.
- **벽시계가 맞는 내부 작업은 따로**: keep-alive(바닐라 `keepConnectionAlive` 는 `Util.getMillis()` 15초)·스폰 게이트 대비 해제는 `GlobalThread.executeAfterMillis` 로 바꿨다. 그대로 두면 `/tick rate 1` 에서 keep-alive 가 300초마다라 클라이언트가 시간 초과로 끊긴다. 자동 저장(`autosaveTicks`)은 바닐라도 `tickCount` 로 세므로 틱 기준이 맞다. `FixedRateBeat`(전역 게임 틱 박동)도 JDK `delayedExecutor` 대신 `executeAfterNanos` 로 예약한다.
- **JDK 지연 실행기 스레드(A14-09)**: `CompletableFutureDelayScheduler` 는 처음 쓰는 쪽의 AccessControlContext·문맥 로더를 평생 쥔다. 서버는 이제 그것을 거의 안 쓰지만(구조물 재시도·EntityOps 는 남음) `GlobalThread` 생성자에서 한 번 써서 서버 스택에서 만들어 둔다.
- **꺼진 플러그인 작업(A14-02)**: `ProfiledScheduling` 이 감싼 TASK 는 소유 플러그인이 꺼졌으면 돌지 않고 작업 참조를 놓는다. 반복 작업(전역·region `runAtFixedRate`, entity `runAtFixedRate`)은 손잡이를 감싼 작업에 알려 두어 다음 차례에 스스로 취소한다. 비동기 실행기(ASYNC)는 건드리지 않는다(onDisable 에서 넘긴 저장을 잃지 않게).
- **PluginTasks(A14-01)**: 한 번짜리는 손잡이를 먼저 만들어 추적에 넣고 돌 때(또는 취소 때) 뺀다. 손잡이가 작업 람다를 쥐지 않는다.
- **isAsync(A14-03)**: `AsyncPlayerPreLoginEvent`·`AsyncPlayerConfigureEvent`·`AsyncChatEvent` 만 true. 나머지 연결 스레드 이벤트는 Paper 에서 동기라 false 로 두고 `Event`·`plugin-foundation.md` 에 "연결 스레드 이벤트" 종류로 적었다.
- **정리(A14-06·07·08)**: 스폰 필터는 `disable()` 에서 `WorldSpawnPolicy.removeFiltersOf`. 이벤트 버스는 빈 목록 키를 지우고(넣기는 `compute` 안에서 — 지우기와 엇갈려 처리기를 잃지 않게), `unregisterAll` 때 `baked` 를 비우고 `monitorWarned`·`deprecationWarned`·통계에서 그 플러그인 몫을 뺀다. `setEnabled` 를 로더 밖에서 부르면 `PluginContext.lifecycleCall()` 로 가려 관리자 `enable/disable` 로 넘긴다. 꺼진 플러그인의 명령은 Bukkit 문구("Cannot execute command … plugin is disabled.")를 로그에 남기고 거부한다.
- 몹 목표·길찾기 훅은 지연 정리 그대로 두고 `mob-ai.md` 에 정리 시점을 적었다(A14-06 나머지·A14-10).
- 재현 시험은 `furnace-server/.../plugin/A14PluginLifecycleReproTest`(12개)로 옮겼다. `measure_*` 단독 실행도 로더 0개.
