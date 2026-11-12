# 플러그인 시험: 헤드리스 시험 서버 (`furnace-test`)

플러그인을 실제 Furnace 서버 위에서 JUnit 으로 시험한다. 흉내 낸 서버(MockBukkit 같은 것)가 아니라 **운영 서버와 같은 코드**가
한 JVM 안에서 뜬다. 다른 점은 둘뿐이다.

- **메모리 월드**: 평지 오버월드 하나. 청크를 디스크에 쓰지 않고, 구독자가 없어도 내리지 않는다(시험이 만진 칸이 사라지지 않는다).
  설정·플레이어 데이터 같은 파일은 임시 폴더에 쓰고 서버를 닫을 때 지운다.
- **결정적 틱**: 서버는 스스로 시간을 진행하지 않는다. `tick(n)` 을 부를 때만 한 틱씩 돌고, 모든 region 이 그 틱을 마쳐야 돌아온다.

## 넣기

```kotlin
// build.gradle.kts (플러그인 프로젝트)
repositories { mavenLocal(); mavenCentral() }
dependencies {
    compileOnly("org.domi.furnace:furnace-api:1.21.1-SNAPSHOT")
    testImplementation("org.domi.furnace:furnace-test:1.21.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))   // JUnit 5 아무 판
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
```

지금은 Furnace 저장소에서 로컬로 배포한다: `./gradlew -PskipOracle=true publishToMavenLocal`
(`furnace-api`, `furnace-server`, `furnace-test` 셋이 `org.domi.furnace:*:1.21.1-SNAPSHOT` 으로 올라간다. 버전은 `-PfurnaceVersion=...`).

## 쓰기

```java
class MyPluginTest {
    @RegisterExtension
    final FurnaceTestExtension furnace = FurnaceTestExtension.with(b -> b.plugin(MyPlugin.class));

    @Test
    void greetsOnJoin(FurnaceTestServer server) {
        TestPlayer steve = server.join("Steve");            // 실제 접속 흐름(핸드셰이크 → 로그인 → 구성 → 플레이)
        steve.awaitMessage(s -> s.contains("Welcome"));      // 입장 이벤트에서 보낸 메시지

        steve.command("/home set");                          // 서버가 처리한 뒤 돌아온다
        assertTrue(steve.messageLines().contains("Home set."));

        server.tick(20 * 5);                                 // 정확히 100틱
        assertEquals(1, server.plugin(MyPlugin.class).reminders());
    }

    @Test
    void placesBlock(FurnaceTestServer server) {
        var w = server.overworld();
        var pos = new BlockPos(0, -60, 0);
        assertTrue(server.await(w.setBlock(pos, BlockData.of("minecraft:stone"))));   // 틱 없이 끝난다
        assertEquals("minecraft:stone", server.onRegion(pos, () -> w.blockNow(pos).orElseThrow().type()));
        assertTrue(server.execute("fill 0 -60 0 2 -60 2 minecraft:glass").success());
    }
}
```

`try (var server = FurnaceTestServer.builder().plugin(MyPlugin.class).start()) { ... }` 처럼 직접 띄워도 된다.

### 플러그인 올리기

| 방법 | 쓰는 때 |
|---|---|
| `plugin(MyPlugin.class)` | 보통. 시험 클래스패스에서 `main` 이 그 클래스인 `furnace-plugin.yml`(yaml·toml·json)을 찾아, 설명 파일과 자원(`config.yml`, `datapack/` 등 — 클래스 빼고)만 담은 jar 를 만들어 올린다. 부트스트랩·`onLoad`·`onEnable`·데이터 팩 훅이 운영 서버와 같은 경로로 돈다. 메인 클래스는 시험 코드와 **같은 클래스**라 `server.plugin(MyPlugin.class)` 로 바로 캐스트된다. 설명 파일이 없으면 이름=클래스 단순 이름으로 만든다. |
| `pluginJar(path)` | 빌드한 jar 를 그대로(운영처럼 자기 클래스로더). 의존 플러그인 jar 를 함께 올릴 때. 클래스는 시험 코드와 다른 클래스로더라 캐스트할 수 없다. |

여러 클래스패스 플러그인이 같은 이름의 자원(`config.yml`)을 가지면 부모 클래스로더가 먼저 찾는 쪽이 보인다 — 그럴 때는 `pluginJar` 를 쓴다.
불러오기·켜기가 실패하면 서버 시작이 멈춘다(`failFast(false)` 로 끌 수 있다).

### 틱과 기다리기

- `tick()` / `tick(n)`: 한 틱 = 전역 예약 작업(`scheduler().global()`) → 전역 틱(함수·보더·날씨·시간) → 모든 region 틱. Bukkit 처럼 전역 예약 작업은
  시간이 오르기 전, region 작업은 그 뒤에 돈다. `runLater(10)` 은 정확히 10번째 `tick()` 에서 돈다. region 수·작업 스레드 수와 상관없이 같다.
- `tick()` 은 먼저 모든 region 이 앞서 받은 작업을 비우게 한 뒤 틱을 낸다. 그래서 시험 스레드에서 건 `runLater(d)` 도 "지난 틱 뒤"로 세어져 정확히 d 번째 `tick()` 에 돈다.
- 틱 사이에도 서버 스레드는 **작업 큐를 비운다**. 그래서 API future(블록 읽기·쓰기, 엔티티 조회, 명령)는 틱 없이 끝난다 — `server.await(future)`.
- `onRegion(pos, supplier)`: 그 칸의 region 스레드에서 동기 API(`blockNow` 등)를 쓴다. `onGlobal(supplier)`: 전역 스레드에서.
- `loadChunk(x, z)`: 청크를 올린다. 처음에는 스폰 둘레 3×3 이 올라와 있다(`spawnChunkRadius`).
- 벽시계로는 아무것도 진행하지 않는다: `Thread.sleep` 해도 시간·반복 작업·몹이 그대로다.
- 결정성의 범위: 한 region 안의 순서(같은 틱 지연 작업은 건 차례)와 "몇 번째 틱에 도는가"는 정해진다. region **사이**에 넘기는 작업
  (A region 이 B region 에 `execute`)은 B 가 그 틱을 이미 돌았는지에 따라 같은 틱 또는 다음 틱에 붙을 수 있다 — `regionThreads(1)`(기본)이면
  늘 같다. 전역 `runLater(0)` 은 시험 서버에서 다음 틱 머리(운영 서버는 바로 다음 전역 작업 차례)에 돈다.

### 명령

- `execute("give Steve diamond")`: 콘솔("Server", 레벨 4 — 운영 콘솔과 같은 발신자)로 끝까지 돌린다. `CommandOutput` 에 성공 여부·결과 값·
  그 사이 콘솔에 나온 출력(`lines()`, `text()`)이 담긴다.
- `consoleMessages()`: 시작부터 콘솔에 나온 모든 메시지.

### 가짜 플레이어 (`TestPlayer`)

- `server.join(name)`: 루프백 소켓으로 접속해 서버 접속자 목록에 들 때까지 기다린다. 오프라인 UUID. keep-alive·텔레포트 확인·청크 묶음 응답은
  읽기 스레드가 알아서 한다.
- `command(...)`, `chat(...)`, `moveTo(x, y, z, onGround)`, `sendPacket(id, body)`: 보낸 뒤 **장벽**을 거쳐 돌아온다 — 같은 연결의 다음 패킷이
  서버에 닿고, 전역 스레드·그 플레이어의 region·연결의 네트워크 스레드가 그 뒤에 넣은 빈 작업을 마칠 때까지. 바로 다음 줄에서 결과를 확인해도 된다.
  이동처럼 서버가 **다음 틱**에 반영하는 것은 `tick()` 뒤에 본다. 처리 중에 다른 region·비동기 작업으로 한 번 더 넘기는 일(예: 멀리 텔레포트)은
  장벽이 덮지 않으니 `tick()`·`awaitMessage` 로 기다린다.
- 틱을 내지 않아도 접속은 유지된다: keep-alive 도 시험 틱으로 세고(300틱마다), 오면 읽기 스레드가 답한다. 운영과 같은 스팸 방지
  (메시지마다 +20, 틱마다 -1, 200 넘으면 킥 — OP 는 빼고)가 켜져 있으므로 틱 없이 채팅·명령을 열 번 넘게 보내면 쫓겨난다 — 사이사이 `tick()` 을
  내거나 OP 를 준다. 가짜 플레이어는 처음에 OP 가 아니다(`execute("op Steve")`).
- `messages()` / `messageLines()`: 받은 시스템 메시지(명령 출력·공지·플러그인 `sendMessage`), `actionBars()`. 서버 쪽에서 직렬화 전 `Text` 를 그대로 모은다.
  `awaitMessage(조건)` 은 비동기로 오는 메시지를 기다린다.
- **받은 패킷**(E3, 2026-11-12): `received(PacketTypes.Clientbound.X)` 는 이 플레이어가 실제로 받은 그 종류의 패킷을 버전 중립 레코드로(받은 순서) 준다.
  다른 플러그인 리스너가 바꾸거나 취소한 뒤 소켓으로 온 그대로다. 부르면 먼저 **받은 패킷 장벽**(`flushReceived()` — 서버가 ping 을 보내 돌아올 때까지)을 거치므로,
  보낸 바로 다음 줄에서 확인해도 된다. `received()` 는 전부, `clearReceived()` 는 비우기(그 전에 온 것까지 읽고 버린다).
  `awaitPacket(type, 조건)` 은 맞는 패킷이 올 때까지(틱 없이, 시간 제한) 기다리고, 오지 않으면 받은 그 종류 목록을 담아 실패한다.
  타입 래퍼가 있는 종류(`type.typed()`)만 모은다. 오래 도는 시험의 메모리를 위해 최근 20000개만 남긴다.
  접속 때 받은 것(입장 메시지·엔티티 생성 등)도 들어 있으니 확인할 동작 앞에서 `clearReceived()` 로 시작한다.
  킥·접속 끊김 뒤에는 장벽 대신 연결 끝까지 다 읽은 것을 돌려준다.

  ```java
  steve.command("/glow-shop");
  var data = steve.awaitPacket(PacketTypes.Clientbound.SET_ENTITY_DATA, d -> d.entityId() == shopId);
  assertTrue(EntityFlags.has(data.get(EntityDataKeys.Entity.SHARED_FLAGS).orElseThrow(), EntityFlags.GLOWING));
  ```
- `player()`: 플러그인이 보는 플레이어 핸들. `close()`: 나가고 서버가 목록에서 뺄 때까지 기다린다.

### 설정 (`FurnaceTestServer.builder()`)

`flatLayers(...)`, `generator(...)`(예: 노이즈 생성기), `seed`, `gameMode`, `difficulty`(기본 NORMAL), `gameRule("doMobSpawning", "false")`,
`viewDistance`, `spawnChunkRadius`, `regionThreads`(늘려도 틱 결과는 같다), `serverProperty(key, value)`, `timeout(ms)`(틱·기다림 한도, 기본 30초),
`directory(path)`(임시 폴더 대신 — 닫아도 지우지 않는다).

## 한계

- 오버월드 하나만 띄운다(네더·엔드·추가 월드 없음 — `createWorld` 는 쓸 수 없다). 바닐라 데이터 팩(공식 서버 jar 에서 추출)은 읽지 않으므로
  태그·전리품·레시피는 서버 내장 목록만 있다. 데이터 팩 함수(`/function`)도 없다.
- `/tick rate`·`/tick sprint` 는 시험 서버에서 뜻이 없다(틱은 `tick()` 만 낸다 — sprint 는 쓰지 않는다). `/tick freeze` 는 운영처럼 월드 진행만 멈춘다.
- 로그: `furnace-server` 가 logback 을 함께 끌어온다. 시험 로그를 줄이려면 시험 자원에 `logback-test.xml` 을 둔다.
- `Furnace.server()` 정적 진입점은 지금 떠 있는 서버 하나만 가리킨다. 서버 둘을 **동시에** 띄우면 먼저 뜬 쪽이다 — 시험은 차례로 돌린다
  (JUnit 병렬 실행을 켜지 않는다). 서버를 닫으면 다음 서버가 다시 맡는다.
- 틱 중에 새로 생긴 region(멀리 새 청크)은 그 다음 틱부터 돈다. 청크 생성·읽기는 비동기라, 틱과 겹치게 올린 청크가 몇 번째 틱에 붙는지는
  정해지지 않는다 — 결과를 정확히 맞춰야 하면 `loadChunk` 로 먼저 올린 뒤 틱을 낸다.
- `internals()` 는 서버 내부(`ServerCore`)다. 공개 API 로 확인할 수 없을 때만 쓰고, 버전 사이 호환은 보장하지 않는다.

## 서버 쪽 구조 (기여자용)

- `Regionizer` 수동 시계(`World.useManualClock`/`advanceManualTick`/`manualTickDone`): region 마다 마친 시험 틱 수(`manualDone`)를 세고, 목표에 닿은
  region 은 sprint lockstep 의 기다림처럼 작업 큐만 비운다(지연 작업 시계도 멈춘다). 마친 수가 다른 region 은 합치지 않고, 나뉜 조각은 부모 수를,
  새 region 은 지금 목표를 이어받는다. 운영 서버는 켜지 않는다.
- `GlobalThread.useManualClock`: 전역 지연 작업을 벽시계 대신 `advanceManualTick` 이 센 틱으로 돌린다.
- 플러그인 region 스케줄러(`World.ChunkScheduler.executeDelayed`): 그 청크의 region 스레드 안에서 부르면 작업 큐를 거치지 않고 곧바로 건다
  (지연은 최소 1 — 0 이면 같은 틱 안에서 다시 걸려 끝나지 않는다). 예전에는 늘 큐를 거쳐 다음 틱 머리에서 걸려 한 틱 밀렸고, 그래서
  `runAtFixedRate(…, 1, 1)` 이 두 틱마다 돌았다(운영 서버에도 해당하는 고침).
- region 지연 작업은 같은 틱이면 건 차례(일련번호)로 돈다 — 합치기·나누기를 거쳐도 유지된다.
- 병합: 마친 시험 틱 수가 다른 region 은 합치지 않는다(지금 틱을 도는 중인 region 은 +1 로 센다). 양보(yield)로 큐에 다시 들어간 region 도
  `manualAdvance` 가 깨운다.
