# T-034 접속 이벤트 실행기 분리 + 플러그인 비동기 실행기 기본값(결정 D1) — 2026-11-15

근거: O12-02(가상 스레드 붙잡힘 452ms vs 플랫폼 1ms), B6 1.7(접속 전 처리기 2초 + 16명 = 4명씩 4물결 9.1초), Y 2.4(전용 캐시 풀이 영향이 작다), 결정 D1(11-14 추천대로 확정: Paper 와 같은 플랫폼 풀 기본, `virtual` 은 옵션).

## 바꾼 것
- **접속 단계 이벤트**(`AsyncPlayerPreLoginEvent`·`AsyncPlayerConfigureEvent`)는 `PluginHost.loginEventExecutor()` — 전용 캐시 플랫폼 풀 `User Authenticator #N`(0부터, 데몬, 60초 놀면 정리)에서 돈다. Paper 1.21.1 `ServerLoginPacketListenerImpl.authenticatorPool`(`Executors.newCachedThreadPool`, 이름 `User Authenticator #%d`)과 같은 모양이다. 플러그인 비동기 실행기와 나뉘어 있어 그쪽이 꽉 막혀도 접속은 줄 서지 않는다. 닫힌 뒤(종료 중) 사전 로그인은 `multiplayer.disconnect.server_shutdown` 으로 끊는다.
- **플러그인 비동기 실행기**(`Furnace#asyncExecutor`·`PluginTasks.async`, 쿠키·리소스 팩 Future 완료, 구조물 찾기)는 `PluginAsyncExecutor`. 새 키 `plugins.async-threads=platform|virtual`(기본 platform, 틀린 값은 기동 거부).
  - platform: `Furnace Async Thread - N`(Paper `CraftAsyncScheduler` 모양: 코어 4, 최대 무제한, 대기열 없음, 30초 놀면 정리, 데몬).
  - virtual: `Furnace Async Virtual - N` 가상 스레드(예전 동작).
  - 작업마다: 잡히지 않은 예외·오류(Throwable, Paper `CraftAsyncTask` 처럼 `NoClassDefFoundError` 등 포함)는 스레드 이름과 함께 `Furnace` 로그에 ERROR(예전에는 서버 기본 처리기가 작업마다 crash 리포트를 썼다 — #402 검토 R1), 끝나면 인터럽트 표시·스레드 이름을 되돌린다(플랫폼 스레드 재사용 때 앞 작업의 인터럽트가 다음 작업에 새지 않게 — 반론 C 보충 ①).
  - 설정은 `PluginManagerImpl.settings` 에서 적용, 풀은 처음 쓸 때 만든다.
- **호환 `plugins.compat.scheduler.async-threads=virtual`**: 예전에는 서버의 `asyncExecutor`(가상)를 빌렸다. 이제 서버 실행기는 기본 platform 이라 호환 스케줄러가 자기 가상 스레드 실행기(`Craft Scheduler Virtual Thread - N` / `Folia Async Scheduler Virtual Thread #N`)를 만들고 닫을 때 닫는다. 옵션 뜻은 그대로다.

## 시험
- `PluginAsyncExecutorTest`: 기본 이름·데몬·플랫폼, virtual 옵션, 인터럽트·이름 되돌리기(같은 스레드 재사용 확인), 예외 로그 + 재사용, 닫힘 거부(안 쓴 채 닫은 뒤 처음 써도 새 풀을 만들지 않음, 검토 R2), Error 로그·일꾼 생존(R1), 설정 키 엄격 파싱, **코어 수 + 2 개 작업이 저마다 synchronized 안에서 장벽을 기다려도 모두 동시에 들어섬**(가상 스레드였다면 운반 스레드 수만큼만 들어서 장벽 시간 초과).
- `LoginPhaseEventsTest.preLoginHandlersBlockingInsideSynchronizedDoNotQueueLogins`: 플러그인 비동기 실행기를 synchronized 대기로 꽉 막은 채, 코어 수 + 2 개 접속의 사전 로그인 처리기가 synchronized 안에서 서로를 기다려도 모두 들어서고 접속이 끝난다. 스레드는 `User Authenticator #N` 플랫폼.
- `LoginPhaseEventsTest.preLoginAfterLoginEventPoolClosedDisconnectsWithServerClosed`: 닫힌 뒤 사전 로그인은 `server_shutdown` 으로 끊기고 처리기는 안 불림(검토 O1).
- 기존 스레드 계약 시험(`LoginPhaseEventsTest.phasesFireInOrderOnOneConnection`, `ConnectionStateApiTest.pluginPackBlocksConfigurationUntilTerminalResponse`)은 "가상 스레드" 대신 "접속 이벤트 전용 플랫폼 스레드"를 본다.

## 남은 것·주의
- 바닐라 결과와 무관(스레드 종류만 바뀜). 이벤트 순서·결과는 같다.
- 플랫폼 풀은 동시에 기다리는 작업 수만큼 스레드가 는다(Paper 와 같은 위험). 접속 폭주 때 사전 로그인 처리기가 오래 걸리면 접속 수만큼 스레드가 생긴다 — Paper 도 같다.
- `SpreadPlayersCommand` 의 땅 읽기는 플러그인 코드가 아니라 서버 코드라 가상 스레드를 그대로 쓴다(synchronized 블로킹 없음).
- JDK 24+(JEP 491)로 올리면 virtual 의 붙잡힘이 사라진다 — 툴체인은 21 고정이라 지금은 해당 없음.
