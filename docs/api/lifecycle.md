# 접속 단계 이벤트와 서버 종료 순서 (플러그인 계약)

2026-11-06. Paper·Spigot 불만 표(`docs/feature-parity/paper-spigot-dev-complaints-2026-11-06.md`) 4번(접속 단계별 이벤트·Player 생성 시점 훅)과 8번(종료 순서 계약)을 구현한 결과다.
API 는 버전 중립이다: 내부 패킷 타입을 드러내지 않고, 단계 이름(`ConnectionPhase`)과 연결 정보(`PlayerConnection`)만 보인다. 1.21.1 이후 버전이 단계를 늘리면 enum 에 더한다.

구독자가 없는 이벤트는 객체도 만들지 않고 스레드도 옮기지 않는다(`hasSubscribers` 검사). 구독자가 없으면 접속 흐름과 보내는 패킷은 바닐라와 똑같다.

## 1. 연결 하나 = `PlayerConnection` 하나

- `PlayerConnection` 은 핸드셰이크부터 끊길 때까지 같은 객체다. 아래 이벤트가 모두 같은 객체를 주고, 접속한 뒤에는 `Player.connection()` 이 같은 객체다.
- `id()` 는 서버 실행 동안 연결마다 다른 번호(1부터)라서 로그에서 단계 사이를 이을 수 있다(Paper #13921 이 요청한 "연결 ID").
- 읽기(`hostname`·`hostPort`·`protocolVersion`·`address`·`name`·`uniqueId`·`clientBrand`·`locale`·`phase`)는 어느 스레드에서나 안전하다(최근 값).
- `disconnect(Text)` 는 어느 스레드에서나 부를 수 있다. 플레이 전이면 그 단계의 끊기 패킷, 플레이 중이면 `Player.kick` 과 같다(PlayerKickEvent 를 거친다).
- 서버 목록 조회(상태) 연결은 로그인 이벤트를 받지 않는다. 그쪽은 `ServerListPingEvent`.

## 2. 접속 단계 이벤트 순서

| 순서 | 이벤트 | 스레드 | 할 수 있는 것 | Player |
|---|---|---|---|---|
| 1 | `PlayerHandshakeEvent` | 연결의 네트워크 스레드 | 가상 호스트(`hostname`)·버전·주소로 거르기. 취소하면 로그인 끊기 패킷 | 없음 |
| 2 | `AsyncPlayerPreLoginEvent` | 가상 스레드(블로킹 허용) | 인증 뒤, DB 조회 후 거부 | 없음 |
| 3 | `PlayerLoginEvent` | 연결 스레드 | 밴·화이트리스트·인원 검사 결과를 보고 바꾸기 | 없음 |
| 4 | `AsyncPlayerConfigureEvent` | 가상 스레드(블로킹 허용) | 구성 끝 패킷 직전. 처리기가 돌아올 때까지 구성이 멈춘다(서버가 15초마다 구성 keep-alive). 데이터 미리 읽기·거부 | 없음 |
| 5 | `PlayerCreateEvent` | 연결 스레드 | Player 가 막 생겼고 플레이 패킷을 하나도 보내기 전. 여기서 붙인 권한이 **첫 명령 트리부터** 반영된다(LuckPerms 류, Paper #13176) | 있음(월드 밖) |
| 6 | `PlayerSpawnLocationEvent` | 연결 스레드 | 첫 위치·차원 바꾸기(차원을 알리기 전). 없는 차원이면 무시 | 있음(월드 밖) |
| 7 | `PlayerJoinEvent` | 연결 스레드 | 입장 메시지 바꾸기 | 있음(월드 안) |

- 연결 스레드·네트워크 스레드에서 블로킹하면 그 스레드의 다른 연결이 멈춘다. 블로킹은 2·4번(가상 스레드)에서.
- `AsyncPlayerConfigureEvent` 를 기다리는 시간도 구성 단계 시간에 들어간다. 운영자가 `connection.configuration.timeout-seconds`(기본 0 = 없음)를 걸었으면 그 안에 돌아와야 한다.
- `AsyncPlayerConfigureEvent` 는 처음 들어올 때 한 번이다(플레이 중 재구성 때는 불리지 않는다). 서버 리소스 팩(`server.properties`)이 있으면 팩 응답이 끝난 뒤에 불린다.
- 5·6번에서는 Player 가 아직 region 에 들어가지 않았다. 위치·인벤토리·월드를 바꾸지 말 것(위치는 6번 이벤트의 `spawnLocation` 으로).
- 첫 명령 트리는 이제 바닐라 `sendCommands` 처럼 플레이어 소스로 거른다. 바닐라 명령은 권한 레벨만 보므로 트리가 같고, 플러그인 명령은 그 플레이어의 권한으로 걸러진다(예전에는 플레이어가 없는 소스라 콘솔 권한으로 걸렀다).

## 3. 서버 종료 순서

`ShutdownSequence.Step` 의 선언 순서가 곧 계약이다(`ShutdownSequenceTest` 가 순서를 고정한다). 한 단계가 실패해도 다음 단계는 돈다.

| 단계 | 하는 일 | 이때 살아 있는 것 |
|---|---|---|
| 1. `SHUTDOWN_EVENT` | `Furnace.isStopping()` 이 true 가 되고 `ServerShutdownEvent` 한 번 | 전부 |
| 2. `DISABLE_PLUGINS` | 켠 순서의 반대로 `onDisable`. 끝나면 그 플러그인의 이벤트·명령·작업이 정리된다 | 접속자·네트워크·region·스케줄러 |
| 3. `CLOSE_REMOTE_SERVICES` | RCON·Query·워치독 | 접속자·region |
| 4. `STOP_NETWORK` | 새 접속 막기, 접속자 내보내기(`multiplayer.disconnect.server_shutdown`) | region |
| 5. `FLUSH_WORLDS` | region 스레드에서 dirty 청크·플레이어 저장, 플레이어 데이터 실행기 비우기 | region |
| 6. `CLOSE_STORAGE` | 청크 IO 풀·리전 파일·level.dat·저장소 닫기 | region |
| 7. `STOP_SCHEDULER` | 청크 작업 풀·스케줄러(region 스레드) 정지 | 없음 |
| 8. `CLOSE_PLUGINS` | 플러그인 클래스로더·비동기 실행기 닫기 | 없음 |

플러그인이 기대해도 되는 것:
- **region 정지는 모든 `onDisable` 이 끝난 뒤다.** `onDisable` 에서 `server().scheduler().region(pos).submit(...)` 로 월드 정리를 맡기고 결과를 기다려도 된다(Folia #353 에서 region 틱이 먼저 꺼져 못 하던 일). 종료 스레드는 서버 스레드가 아니므로 `get()`/`join()` 이 거부되지 않는다.
- `onDisable` 동안 접속자는 아직 접속해 있다(메시지·킥·데이터 저장 가능).
- `tasks()` 로 맡긴 작업은 `onDisable` 이 끝나면 취소된다. 끝까지 돌아야 하는 작업은 `onDisable` 안에서 기다린다.
- `onDisable` 에서 리로드와 종료를 가르려면 `server().isStopping()`.
