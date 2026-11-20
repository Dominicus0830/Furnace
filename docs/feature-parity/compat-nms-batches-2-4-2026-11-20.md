# NMS shim 2~4번 묶음 — 설계와 진행 (2026-11-20, T-245~T-247)

1번 묶음(패킷 버퍼·플러그인 메시지, T-244 `compat-nms-packet-buffer-2026-11-20.md`)에 이은 묶음들이다. 결정 D2-1·D3-1 은 코디네이터가 추천대로 확정했다(11-20, `docs/user-todo/entries/2026-11-20-nms-shim-batch2.md`).

## 진행

| 묶음 | 번호 | 상태 |
|---|---|---|
| 2번 플레이어 목록·틱 스레드 판정 | T-245 | 완료(이 PR). 표본 감사에서 이 묶음 심볼 0(Axiom 없는 심볼 92 → 87, VeinMiner 50 → 47). 시험 `NmsPlayerListTest`(3) |
| 3번 블록 부수기·경험치·속성 | T-246 | 다음 PR |
| 4번 감사 도구 "타는 길" 거르기 + Orebfuscator 9개 | T-247 | 설계만 |

### 2번 묶음에서 한 것

- `PlayerList`(추상) + `DedicatedPlayerList`(전용 서버가 주는 것, Paper 처럼 `DedicatedServer#getPlayerList` 반환형이 공변). 상태 없는 보기: `getPlayers()` 는 그 순간의 바꿀 수 없는 사본, `getPlayer(UUID)`·`getPlayerByName`(대소문자 무시)·`getPlayerCount`·`getMaxPlayers`·`getServer`, `broadcastSystemMessage` 둘(콘솔 한 줄 + 플레이어마다, 함수가 null 을 주면 건너뜀).
- `MinecraftServer#isSameThread` = 호환 계층이 표시한 주 스레드 구간이거나 `FurnaceScheduler#isTickThread`(전역·region 스레드). Paper 1.21.1 은 `TickThread.isTickThread()` 다.
- `MinecraftServer#sendSystemMessage(Component)`(콘솔), `ServerPlayer#sendSystemMessage` 둘(서버 플레이어의 같은 메서드 — 채팅을 숨긴 클라이언트는 overlay 만), `Entity#getServer`.
- 서버 다리 `ChunkInternals#sendSystemMessage`(보내기 큐에 넣을 뿐이라 어느 스레드에서나).
- 서명 관문: `PlayerList` 생성자만 다르다(의도 — 상태 없는 보기라 바닐라 생성자를 두지 않음, 기준선에 이유).

## 설계 초안(묶음별)

근거는 표본 jar(Axiom 6.0.1, VeinMiner 2.14.0, squaremap 1.3.2, Orebfuscator 5.6.2, Geyser 2.12.1)를 `javap -c` 로 본 실제 호출과, Paper 1.21.1 서버 jar 의 바이트코드이다.

### 2번 묶음 — 플레이어 목록과 "틱 스레드인가"

#### 플러그인이 실제로 부르는 것

| 심볼 | 누가 | 쓰는 모양 |
|---|---|---|
| `MinecraftServer.getPlayerList()` | Axiom, VeinMiner | 아래 셋을 부르려고 얻는다 |
| `PlayerList.getPlayers()` | Axiom(5곳) | 전체 플레이어 돌기 |
| `PlayerList.getPlayer(UUID)` | VeinMiner(2곳) | 플레이어 찾기 |
| `PlayerList.broadcastSystemMessage(Component, boolean)` | Axiom(4곳) | 모두에게 알림 |
| `MinecraftServer.isSameThread()` | Axiom(4곳), VeinMiner(1곳) | Axiom: 아니면 `WrongThreadException` 을 던지거나 작업을 미룬다 |
| `ServerPlayer.getServer()` | Axiom | 서버 얻기 |

#### Paper 동작(바이트코드)

- `getPlayers()` 는 내부 리스트 그 자체를 돌려준다(주 스레드에서만 바뀐다).
- `broadcastSystemMessage(c, overlay)` 는 콘솔에 한 줄을 남기고, 플레이어마다 시스템 메시지(overlay 면 액션바)를 보낸다.
- `MinecraftServer.isSameThread()` 는 `TickThread.isTickThread()` 다. 주 스레드뿐 아니라 "틱을 도는 스레드면 참"이라는 뜻이다(Folia 에서는 region 스레드도 참).

#### 설계

1. **`PlayerList` shim(얇은 보기).** 서버 상태를 따로 들지 않고 부를 때마다 Furnace 의 접속 플레이어에서 만든다.
   - `getPlayers()` 는 그 순간의 사본(불변 리스트)을 준다.
     - Paper 와 다른 점: 바깥 스레드에서 돌려도 안전하지만, 리스트를 쥐고 있어도 나중 변화는 보이지 않는다.
     - 플러그인이 이 리스트를 직접 고치는 일은 Paper 에서도 금지에 가깝다. 고치려 하면 `UnsupportedOperationException` 이 난다.
   - `getPlayer(UUID)` 는 서버의 UUID 표로 바로 찾는다(돌지 않는다).
   - `broadcastSystemMessage` 는 플레이어마다 그 플레이어의 region 으로 보낸다. 콘솔 줄은 Paper 와 같은 문구로 남긴다.
2. **`MinecraftServer.isSameThread()`.** `Furnace.server().scheduler().isTickThread()`(전역 또는 region 스레드)이거나 `PrimaryThread.isPrimary()`(호환 계층이 표시한 구간)이면 참이다.
   - 이것이 Paper 의 "틱 스레드" 뜻과 같다. Bukkit `isPrimaryThread` 와 거의 같은 판정이다.
   - **주의(결정 D2-1, 추천대로 확정):** Axiom 은 이것이 참이면 그 자리에서 월드를 고친다. region 스레드에서 다른 region 의 블록을 만지면 Furnace 는 기존 NMS 쓰기 경로(주인 스레드로 넘기기·거울)를 탄다.
     - Axiom 작업은 보통 Bukkit 동기 작업(전역 스레드 + 표시)에서 돈다. 그래서 region 스레드가 참이 되는 경우는 이벤트 리스너 안에서 부를 때뿐이다.
     - 추천: Paper 와 같게 "틱 스레드면 참"으로 둔다.
     - 대안: 전역 스레드·표시 구간만 참으로 두기. Axiom 은 작업을 다음 틱으로 미뤄 동작은 하지만, Paper 와 결과 시점이 달라진다.
3. `ServerPlayer.getServer()` 는 `MinecraftServer.getServer()` 를 돌려준다(접속 안 한 가짜 플레이어도 같다).
4. **시험 범위.** 사본·찾기, 방송이 각 플레이어의 region 에서 나가는지, `isSameThread` 를 전역 스레드·region 스레드·풀 스레드·임의 스레드에서 확인한다(앞의 둘은 참, 뒤의 둘은 거짓).

크기 S~M. 모델은 Sonnet medium 이면 충분하다(동시성은 이미 있는 판정을 쓴다).

### 3번 묶음 — 블록 부수기·경험치·속성

#### 플러그인이 실제로 부르는 것

| 심볼 | 누가 | 쓰는 모양 |
|---|---|---|
| `ServerPlayer.gameMode`(필드), `ServerPlayerGameMode.destroyBlock(BlockPos)` | VeinMiner | 맥으로 이어진 광석을 **플레이어가 부순 것처럼** 하나씩 부순다 |
| `ServerPlayerGameMode.getGameModeForPlayer()` | squaremap | 관전자 숨기기 |
| `Block.getExpDrop(state, level, pos, tool, dropExperience)`(Paper 가 더한 메서드) | VeinMiner | 부순 블록의 경험치 계산 |
| `Attributes.BLOCK_BREAK_SPEED`·`ARMOR`(Holder), `Player.getAttribute(Holder)`, `AttributeInstance.getValue()`·`addTransientModifier`·`removeModifier(ResourceLocation)`, `AttributeModifier(ResourceLocation, double, Operation)`·`Operation.ADD_MULTIPLIED_BASE` | VeinMiner, squaremap | VeinMiner 는 채굴 속도를 잠깐 바꾼다. squaremap 은 방어 수치를 지도에 표시한다 |

#### Paper 동작

- `Block.getExpDrop` 의 기본값은 0이다. `DropExperienceBlock` 등은 `dropExperience` 가 참이면 `tryDropExperience(level, pos, tool, xpRange)` 다.
  - 즉 난수 소비와 "섬세한 손길이면 0" 이 들어 있다. 서버의 `behavior.expDrop(state, level, pos, tool)` 이 같은 일을 한다(`CraftBlock#breakNaturally` 가 이미 쓴다).
- `destroyBlock(pos)` 는 바닐라 `ServerPlayerGameMode#destroyBlock` 에 Paper 의 `BlockBreakEvent` 가 더해진 흐름이다. 서버에는 `BlockBreaking.destroyBlock` 으로 이미 이식돼 있다.

#### 설계

1. **`ServerPlayerGameMode` shim.** `ServerPlayer.gameMode` 필드에 플레이어마다 하나씩 둔다.
   - `getGameModeForPlayer()` 는 Furnace 게임 모드를 `GameType` 으로 돌려준다.
   - `destroyBlock(pos)` 는 `ChunkInternals` 에 새 다리 `destroyBlockAsPlayer(player, x, y, z)` 를 둔다. 그 칸의 주인 스레드에서 `BlockBreaking.destroyBlock` 을 그대로 부른다(이벤트·드롭·도구 닳기·통계 모두 서버 쪽 그대로).
   - 다른 스레드에서 부르면 기존 `NmsOwner.call` 로 주인 스레드에 넘기고 기다린다. 틱 스레드에서 다른 region 을 기다리면 교착이 될 수 있어, 틱 스레드면 기존 규칙대로 주인 스레드로 넘기고 결과를 기다리지 않는 쪽을 쓴다.
   - **결정 D3-1(추천대로 확정):** 이 경우 돌려줄 boolean 은 "넘겼다"(true)가 된다.
     - 추천: 같은 region 이면 바로 부수고 진짜 결과를 준다. 다른 region 이면 넘기고 true 를 준다.
     - VeinMiner 는 결과로 다음 칸을 계속할지 정하므로, 다른 region 칸에서는 한 칸 더 시도할 수 있다.
     - 대안: 다른 region 이면 false(부수지 않음). VeinMiner 가 region 경계에서 멈춘다.
   - 접속 안 한 가짜 플레이어는 T-212 의 규칙대로(Paper 처럼 이벤트 보내기) 같은 다리를 탄다.
2. **`Block.getExpDrop`.** 다리 하나로 서버 `behavior.expDrop` 을 부른다(`dropExperience` 가 거짓이면 0). 난수는 그 레벨의 난수를 써서 바닐라와 같은 차례가 된다.
3. **속성.** `Attributes` 상수(Holder, 바닐라 이름 31개)를 레지스트리에서 만든다. `LivingEntity.getAttribute(Holder)` 는 Furnace 공개 API `AttributeInstance` 를 감싼 `AttributeInstance` shim 을 준다. `getValue`·`getBaseValue`·`setBaseValue`·`addTransientModifier`·`addPermanentModifier`·`removeModifier(ResourceLocation)`·`getModifier`·`hasModifier` 를 둔다.
   - 기존 `AttributeModifier` shim 레코드를 그대로 쓴다. id 는 `ResourceLocation` 문자열과 Furnace 수정자 id 로 서로 바꾼다.
   - 엔티티 상태라 주인 스레드 규칙을 따른다: 읽기는 어디서나 그 순간 값, 쓰기는 주인 스레드로 넘긴다. 기존 Bukkit `AttributeInstance` 와 같은 길이다.
4. **시험 범위.**
   - `destroyBlock` 이 손으로 부술 때와 같은 결과인지 본다(`BlockBreakEvent`·드롭·도구 내구도·경험치). 기존 블록 부수기 정답 시험과 견준다.
   - `getExpDrop` 이 Paper 값과 같은지 본다(같은 시드).
   - 채굴 속도 수정자를 넣고 뺀 뒤 값이 원래대로 돌아오는지 본다.

크기 M. 모델은 Opus medium 을 추천한다(블록 부수기 경로를 다리로 연결하고 스레드를 넘기는 일).

### 4번 묶음 — 옛 Spigot 이름과 `Class.forName` 감지

#### 알아낸 것(표본 jar 를 직접 열어 봄)

- **Orebfuscator 5.6.2.** `Class.forName("net.minecraft.core.BlockPos")` 와 `Blocks.AIR` 필드가 있으면 Mojang 이름 서버로 보고 `nms.v1_21_R1_mojang` 어댑터를 고른다.
  - 순위 표의 `WorldServer`·`IBlockData`·`ChunkSection` 같은 Spigot 이름 구멍은 옛 판 어댑터(v1_20_R1 등) 것이라 Furnace 에서는 타지 않는다.
  - 실제로 타는 어댑터의 구멍은 **9개**다:
    - `RegionFile`·`RegionFileVersion`(저장된 .mca 를 직접 읽음)
    - `ServerChunkCache.getChunkFuture`·`isChunkLoaded`
    - `BlockEntity.getUpdatePacket`
    - `BlockState.hasBlockEntity`
    - `DefaultedRegistry.getTags`
    - `DedicatedServer.forceSynchronousWrites`
    - `StateDefinition.getPossibleStates` 반환형(ImmutableList)
- **Geyser 2.12.1.** 프로토콜 번호로 `adapters.paper.v766` 을 고른다(1.21.1 = 767 이하 최대). 그 어댑터가 부르는 NMS 는 지금 shim 에 **모두 있다**(구멍 0). 순위 표의 구멍은 Spigot 이름 어댑터와 더 새 판 어댑터(v768·v774) 것이다.

#### 설계

1. **감사 도구에 "타는 길" 거르기를 더한다.** 판 이름이 붙은 패키지(`v1_20_R1`, `v1_21_R1_mojang`, `v766` 같은 꼴)는 1.21.1·프로토콜 767 에 맞는 것만 센다.
   - Spigot 이름 어댑터는 Mojang 이름 서버에서 안 타므로 "건너뜀"으로 따로 센다.
   - 이것은 특정 플러그인 맞춤이 아니라 판 이름 패키지라는 흔한 관례를 읽는 일반 규칙이다. 맞지 않는 플러그인은 지금처럼 전부 센다.
   - 그러면 순위 표와 지원 비율이 실제 구멍만 보여 준다.
2. **Orebfuscator 9개 채우기.** 가장 큰 것은 `RegionFile` 이다.
   - 서버에 이미 anvil 형식 `world/anvil/RegionFile`·`RegionFileStore` 가 있다. 그래서 바닐라 모양 `RegionFile` shim 을 그 위에 얇게 씌우는 쪽이 된다.
   - 다만 저장 중인 청크를 다른 스레드가 읽을 때의 순서는 서버 IO 풀 단일 경로 규칙을 따라야 한다. 이 부분은 설계 검토가 필요하다.
   - 나머지 8개는 작다.
3. **감지용 빈 클래스 금지 유지.** `Class.forName` 감지 대상은 주요 멤버를 채운 뒤에만 연다(설계 4.4 그대로).
   - Orebfuscator 의 감지(BlockPos·Blocks.AIR)는 이미 참이다. 그래서 위 9개를 채우기 전에는 Orebfuscator 가 첫 구멍에서 `UnsupportedNmsLink` 로 멈춘다(지금도 그렇다).

크기: 도구 S, Orebfuscator M(RegionFile 에 달림). 모델은 도구 Sonnet medium, RegionFile 은 Opus medium.

### Spigot 이름 표본 33개 다시 재기(대응표가 있는 PC)

- 장비 스레드(데스크탑, 폴더 E:\Project\IdeaProjects\Furnace)에서 Spigot 대응표 파일(`plugins.compat.spigot-mappings`)을 켜고 같은 표본 100개로 `nmsShimAudit` 을 다시 돌린다.
- 위 1번의 "타는 길" 거르기를 먼저 넣은 뒤에 재면 Geyser 처럼 여러 판을 담은 jar 의 가짜 구멍이 빠진다.
- 결과로 지원 비율(지금 80.2%, 목표 95%)과 다음 묶음 순서를 다시 낸다.

### 결정 요약(쉬운 말) — 둘 다 추천대로 확정(11-20)

| 번호 | 묻는 것 | 추천 | 안 하면 |
|---|---|---|---|
| D2-1 | Axiom 같은 플러그인이 "지금 서버 틱 스레드인가?" 물을 때 region 스레드도 "예"로 할까 | 예(Paper 와 같은 뜻) | 작업이 한 틱 늦게 돈다 |
| D3-1 | VeinMiner 가 다른 region 칸을 부술 때 결과를 기다리지 않고 "부쉈다"로 답할까 | 같은 region 은 진짜 결과, 다른 region 은 넘기고 "예" | region 경계에서 연쇄 채굴이 멈춘다 |
