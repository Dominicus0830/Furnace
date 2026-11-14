## 2026-11-14 LuckPerms(H2) 종료 NoClassDefFoundError — /stop 과 JVM 종료 훅 순서 (luckperms-h2-shutdown)

- 증상(빌드 e19110ca 사용자 로그): `/stop` 뒤 `Thread-N` 에서 `NoClassDefFoundError: org/h2/api/ErrorCode`(`OnExitDatabaseCloser.run` → `Database.close` → `Store.close`), 크래시 리포트 생성.
- 원인: Furnace 의 `/stop` 은 `System.exit(0)` 부터 불렀고, 종료 단계(플러그인 끄기·저장)는 JVM 종료 훅 안에서 돌았다. JVM 종료 훅은 동시에 돌므로
  1. H2 가 DB 를 열 때 건 `OnExitDatabaseCloser` 훅이 LuckPerms `onDisable` 과 동시에 돌고,
  2. LuckPerms 가 DB 를 닫으며 훅을 떼려는 `removeShutdownHook` 은 "Shutdown in progress" 로 실패하며(H2 는 삼킨다),
  3. LuckPerms 가 H2 를 올린 자기 라이브러리 로더(`plugins/LuckPerms/libs`)를 닫은 뒤 H2 훅이 처음 쓰는 `ErrorCode` 를 읽다가 실패했다.
  Furnace 플러그인 로더 닫기(`CLOSE_PLUGINS`)는 무관 — 닫은 것은 LuckPerms 자신의 로더다.
- Paper: `/stop` 은 서버 스레드가 `stopServer`(플러그인 끄기·저장)를 마친 뒤 JVM 이 끝나고, `ServerShutdownThread`(JVM 종료 훅)는 신호로 끝날 때만 서버를 멈춘다(CraftBukkit 구조, 이 컨테이너에서 Paper 원문은 확인 못 함 — 가정). 그래서 Paper 에서는 H2 훅이 떨어져 이 오류가 없다.
- 고침: `/stop`(`ServerCore.halt`) → "Server Shutdown Thread" 가 `ShutdownSequence.runThenExit` 로 단계를 다 돈 뒤 `System.exit`. JVM 종료 훅은 그대로 단계를 돌되(Ctrl+C·kill), 다른 스레드가 이미 돌고 있으면 끝날 때까지 기다린다(저장 중 JVM 이 끝나지 않게). 단계 안에서 System.exit 가 불려 그 스레드가 `java.lang.Shutdown` 에 멈췄으면 기다리지 않는다(교착 방지).
- 실측(실제 LuckPerms 5.5.91, H2, 오프라인 헤드리스 기동 → 콘솔 `stop`): 고치기 전 6번 중 5번 오류·크래시 리포트, 고친 뒤 8번 중 0번.
- 시험 `ShutdownHookOrderTest`(별도 JVM): /stop 경로에서 onDisable 의 훅 떼기 성공·닫힌 로더 훅 안 돎, 고치기 전 흐름(exit 먼저)은 훅 떼기 실패(대조), /stop 중 신호가 와도 남은 단계 완료, 단계 안 System.exit 교착 없음. 변이 3종(단계 안 돌기·기다림 없음·exit 감지 없음) 모두 잡힘.
- 남은 차이(이 PR 밖): Furnace 는 아무 스레드의 잡히지 않은 예외도 크래시 리포트를 쓰지만 Paper 는 로그만 남긴다(`CrashReport.install`). Ctrl+C 종료에서는 Paper 와 마찬가지로 플러그인 훅과 동시에 돈다.
