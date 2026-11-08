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
| 2. `DISABLE_PLUGINS` | 켠 순서의 반대로 `onDisable`. 끝나면 그 플러그인의 이벤트·명령·작업이 정리된다. 모두 끝난 뒤 형식 어댑터가 `PluginFormat.afterPluginsDisabled` 로 한 번 마무리한다(Bukkit 어댑터: 돌고 있는 비동기 작업을 최대 5초 기다리고 남으면 로그) | 접속자·네트워크·region·스케줄러 |
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

## 4. 구성 단계에서 클라이언트와 주고받기 (`PlayerConnection`)

`AsyncPlayerConfigureEvent`(가상 스레드)에서 구성 단계를 멈춰 놓고 클라이언트와 주고받을 수 있다(불만 표 6번). 모든 메서드는 어느 스레드에서나 부를 수 있고, 패킷은 연결의 이벤트 루프로 넘겨 보낸다.

| 메서드 | 단계 | 하는 일 |
|---|---|---|
| `sendResourcePack(ResourcePack)` → `Future<ResourcePackStatus>` | 구성·플레이 | 리소스 팩을 내려받게 하고, **끝 상태 응답**(적용 성공·거절·다운로드 실패·잘못된 주소·재적용 실패·버림)이 오면 완료. `ACCEPTED`·`DOWNLOADED` 는 중간 상태라 Future 가 안 끝난다. 필수 팩을 거절하면 서버가 내보낸다(Future 는 `DECLINED`) |
| `removeResourcePack(id)` / `removeAllResourcePacks()` | 구성·플레이 | 팩을 빼고, 기다리던 Future 는 `DISCARDED` 로 완료 |
| `requestCookie(key)` → `Future<byte[]>` | 구성 | 클라이언트에 저장된 쿠키 읽기(없으면 `null`). 요청하지 않은 쿠키 응답은 버린다 |
| `storeCookie(key, value)` | 구성 | 쿠키 저장(키 `namespace:path`, 값 최대 5120바이트) |
| `transfer(host, port)` | 구성·플레이 | 다른 서버로 옮기기 |
| `quitReason()` | 끊긴 뒤 | 끊긴 이유(아래) |

- 서버 `resource-pack`(server.properties)과 따로 센다: 플러그인 팩의 응답은 서버 팩 대기를 끝내지 않는다.
- 구성 단계가 이미 끝났다(구성 끝 패킷을 보냈다)거나 허용되지 않는 단계면, Future 는 `IllegalStateException` 으로 완료되고 `void` 메서드는 그 예외를 던진다. 잘못된 인자(쿠키 키 꼴, 5120바이트 초과)는 `IllegalArgumentException`.
- **끊기면 풀린다**: 연결이 끊기면 기다리던 모든 Future 가 `ConnectionClosedException` 으로 끝나 `get()` 에서 풀려난다(Paper 에서 구성 처리기 스레드가 누수되던 #13365 와 같은 문제를 막는다). 그래도 1초 뒤 구성 이벤트 처리기가 안 돌아오면(다른 일로 블로킹 중) 그 스레드에 인터럽트를 건다. 연결이 이미 끊겼으면 처리기를 돌리지 않는다.
- 응답을 영원히 안 주는 클라이언트에 대비한 시간 제한은 부르는 쪽이 건다(`future.orTimeout(10, SECONDS)`). 서버 쪽 한도는 `connection.configuration.timeout-seconds`(기본 없음).
- Future 의 후속 처리(`thenApply` 등)는 네트워크 스레드가 아니라 플러그인 비동기 실행기에서 돈다.
- 구독자가 없으면 흐름과 패킷은 바닐라와 같다.

## 5. 끊긴 이유 (`QuitReason`)

`PlayerQuitEvent.reason()`(플레이에 들어온 뒤)와 `PlayerConnection.quitReason()`(어느 단계든, 끊긴 뒤)가 같은 값을 준다. 처음 남긴 이유가 이긴다.

| 값 | 언제 |
|---|---|
| `DISCONNECTED` | 클라이언트가 스스로 나갔다(서버가 끊은 것이 아님) |
| `KICKED` | 사유를 보여 주고 서버가 내보냈다(킥·밴·허용 목록·중복 로그인·플러그인·자리비움·스팸·채팅 검증 실패 등) |
| `TIMED_OUT` | 읽기 타임아웃·keep-alive 무응답·로그인 30초 같은 접속 단계 시간 제한 |
| `ERRONEOUS_STATE` | 잘못된 패킷·디코드 실패·속도 제한 초과 — 서버가 사유 없이(또는 "Too many packets"로) 닫았다 |
| `SERVER_SHUTDOWN` | 서버 종료 때 내보냄(관리자 킥과 구분) |
