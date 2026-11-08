# 청크 티켓 (W4 / 불만 표 46번)

플러그인이 청크를 **로드된 채로 붙잡는** API다. 입구는 `Furnace.server().chunkTickets()`. Paper 의 `addPluginChunkTicket` 에 해당하고, 반경과 틱 여부를 고를 수 있다.

## 빠른 예

```java
var tickets = server().chunkTickets();
ChunkTicket t = tickets.add(this, world, chunkX, chunkZ, 2, true);   // 중심 둘레 5x5 청크, 엔티티 틱까지
t.loaded().thenRun(() -> log("다 올라왔다"));
// ... 필요 없어지면
t.release();                                                         // 또는 try-with-resources
```

플러그인이 꺼지면(핫 리로드 포함) 그 플러그인의 티켓은 서버가 모두 놓는다. 따로 정리 코드를 쓰지 않아도 된다.

## 규칙

- **붙잡기**: 티켓이 덮는 청크는 플레이어가 없어도, 퇴출 유예가 지나도 내려가지 않는다. 아직 안 올라온 청크는 그 청크를 가진 region 이 불러온다(없는 땅은 만든다). `ChunkTicket.loaded()` 는 모두 올라오면 끝난다.
- **ticking**: 참이면 그 청크의 엔티티·블록 엔티티가 플레이어 없이도 틱을 받는다(`/forceload` 와 같은 단계). 거짓이면 올려 두기만 한다(청크 읽기·쓰기, 구조물 놓기·저장 같은 작업용). 무작위 틱·자연 스폰은 `/forceload` 처럼 플레이어 가까이에서만 돈다.
- **겹침**: 같은 청크에 티켓이 여럿 겹쳐도 된다(여러 플러그인이든 한 플러그인이든). 모두 풀려야 보통 규칙대로(플레이어가 없고 퇴출 유예가 지나면) 내려간다. `/forceload`·포털·드래곤 티켓과도 독립이다.
- **저장 안 됨**: 티켓은 서버를 다시 켜면 사라진다. 플러그인이 켤 때 다시 건다(`/forceload` 는 `chunks.dat` 에 저장된다).
- **한도**: 한 티켓 반경 0..16, 한 플러그인이 걸 수 있는 청크 수는 겹침 포함 기본 16384(`ChunkTickets.MAX_CHUNKS_PER_OWNER` 는 이 기본값). 서버 운영자가 `furnace.properties` 의 `plugins.chunk-tickets.max-per-plugin`(1 이상)으로 바꿀 수 있고, 잘못된 값이면 기동이 멈춘다. 넘으면 `IllegalStateException`. 꺼진 플러그인의 `add` 도 `IllegalStateException`.
- **조회**: `tickets(owner)`, `tickets(world)`, `ticketsAt(world, cx, cz)`, `isHeld(world, cx, cz)`. 아무 스레드.
- 월드가 내려가면 그 월드의 티켓은 `isActive()` 가 거짓이 된다.

## 구현 메모

`World.addPluginTicket/removePluginTicket` 이 청크 키별 개수 표 두 개(붙잡기, 엔티티 틱)를 센다. `World.ticketed`(퇴출 판단)와 `Region.blockEntityTicking`(엔티티 틱 판단)이 이 표를 본다. 장부(누가 무엇을 걸었나)는 `ChunkTicketsImpl` 에 있고 `PluginManagerImpl.disable` 이 `releaseAll` 을 부른다.
