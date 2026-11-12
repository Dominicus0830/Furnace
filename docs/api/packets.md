# Furnace 패킷 API

> 이 API 위에 쌓은 가상 엔티티·NPC·가짜 탭 줄은 [virtual-entities.md](virtual-entities.md).

플러그인이 패킷을 **보내고**, 클라이언트가 보낸 것처럼 **넣고**, 오가는 패킷을 **가로채서 읽고·바꾸고·버리는** API 다(ProtocolLib 이 하던 일).
`furnace-api` 의 `org.domi.furnace.api.packet` 에 있고, 입구는 `Furnace.server().packets()` 와 `Player.sendPacket`.

결정 D2(공식 패킷 API)와 "1.21.1 이후 버전도 지원" 계획을 따른다: 숫자 패킷 id·바이트 배치는 API 에 드러나지 않는다.
값은 이름 기반 불변 레코드(`BlockData`, 엔티티 종류 `"minecraft:zombie"`, 소리 `"minecraft:..."`, `Text`)이고, 서버의 버전별 변환 계층(`ProtocolCodec`, 지금은 `Protocol767` = 1.21.1)이 실제 와이어 형식과 오간다.

## 빠른 예

```java
PacketService packets = server().packets();

// 1) 가상 블록: 이 플레이어 화면에만 다이아 블록(서버 월드는 그대로)
player.sendPacket(new ClientboundBlockUpdatePacket(new BlockPos(0, 70, 0), BlockData.of("minecraft:diamond_block")));

// 2) 가상 엔티티(NPC 바탕): 서버 엔티티와 겹치지 않는 번호를 받아 띄운다
int id = packets.nextEntityId();
player.sendPacket(ClientboundAddEntityPacket.of(id, UUID.randomUUID(), "minecraft:villager", x, y, z, yaw, 0));

// 3) 그 엔티티 클릭 잡기 — 서버에 없는 번호여도 interact 패킷은 온다
packets.listen(this, PacketTypes.Serverbound.INTERACT, e -> {
    if (e.packet().entityId() != id) return;
    e.setCancelled(true);                                     // 서버가 처리하지 않게
    e.player().scheduler().execute(() -> openShop(e.player()));   // 월드를 건드리는 일은 소유 스레드로
});

// 4) 나가는 채팅 고치기
packets.listen(this, PacketTypes.Clientbound.SYSTEM_CHAT, e ->
    e.setPacket(e.packet().withMessage(Text.text("[서버] ").append(e.packet().message()))));

// 5) 타입 래퍼 없는 패킷도 관찰·취소는 된다
packets.listen(this, PacketTypes.Clientbound.LEVEL_PARTICLES, e -> e.setCancelled(true));
```

## 개념

| 이름 | 뜻 |
|---|---|
| `PacketType<P>` | 방향 + 바닐라 패킷 이름(`minecraft:system_chat`). 상수는 `PacketTypes.Clientbound.*` / `PacketTypes.Serverbound.*`, 이름으로는 `PacketTypes.byName`. 같은 종류는 늘 같은 객체 |
| 타입 래퍼 | `ClientboundXxxPacket` / `ServerboundXxxPacket` 레코드(Mojang 매핑 이름과 같게 지었다). 읽기·바꾸기·보내기 가능 |
| `OpaquePacket` | 아직 래퍼가 없는 종류. 종류만 알고 내용은 못 본다 — 관찰·취소만, 보내기 불가 |
| `PacketEvent` | `player()`, `packet()`, `setPacket()`, `setCancelled()`, `isPluginPacket()` |
| `PacketService` | `listen`, `send`/`sendSilently`, `broadcast`, `receive`/`receiveSilently`, `isSupported`, `nextEntityId`, `protocolVersion` |

- 목록은 play 단계 전체(서버→클라이언트 124종, 클라이언트→서버 55종)다. 바닐라의 이동 패킷 넷(`move_player_pos`·`_pos_rot`·`_rot`·`_status_only`)은 `MOVE_PLAYER` 하나로 묶었다(버전마다 갈래가 달라질 수 있어서). 보낼 때 `hasPosition`·`hasRotation` 으로 알맞은 것이 골라진다.
- 다른 버전에 없는 패킷은 `isSupported(type)` 가 false 이고, 구독해도 불리지 않는다.

### 타입 래퍼가 있는 종류 (34)

서버→클라이언트: `SYSTEM_CHAT`, `SET_ACTION_BAR_TEXT`, `SET_TITLE_TEXT`, `SET_SUBTITLE_TEXT`, `SET_TITLES_ANIMATION`, `CLEAR_TITLES`, `DISCONNECT`, `SET_TIME`, `BLOCK_UPDATE`, `ADD_ENTITY`, `SET_ENTITY_DATA`, `PLAYER_INFO_UPDATE`, `PLAYER_INFO_REMOVE`, `REMOVE_ENTITIES`, `TELEPORT_ENTITY`, `SET_ENTITY_MOTION`, `ROTATE_HEAD`, `ANIMATE`, `SOUND`, `CUSTOM_PAYLOAD`.

클라이언트→서버: `CHAT`, `CHAT_COMMAND`, `CLIENT_COMMAND`, `CLIENT_INFORMATION`, `CUSTOM_PAYLOAD`, `INTERACT`, `MOVE_PLAYER`, `PLAYER_ACTION`, `PLAYER_COMMAND`, `SET_CARRIED_ITEM`, `SIGN_UPDATE`, `SWING`, `USE_ITEM`, `USE_ITEM_ON`.

단위: 각도는 도(degree), 속도는 블록/틱(축마다 ±3.9 로 잘림), 좌표는 블록. 와이어의 1/256 바퀴 바이트·1/8000 short·×8 정수는 변환 계층이 맡는다.

## 엔티티 메타데이터 — 이름 있는 칸 (E1, 2026-11-12)

`set_entity_data` 의 칸은 숫자 대신 **이름 있는 키**(`org.domi.furnace.api.entity.data.EntityDataKeys`)로 다룬다. 칸 번호는 마인크래프트 버전마다 밀리므로(ProtocolLib `WrappedDataWatcher` 의 흔한 고장) 서버가 실행 중인 버전에 맞춰 정한다.

```java
// 디스플레이 엔티티 홀로그램: 생성 + 메타데이터
int id = packets.nextEntityId();
player.sendPacket(ClientboundAddEntityPacket.of(id, UUID.randomUUID(), "minecraft:text_display", x, y, z, 0, 0));
player.sendPacket(ClientboundSetEntityDataPacket.builder(id)
        .set(EntityDataKeys.TextDisplay.TEXT, Text.text("상점"))
        .set(EntityDataKeys.Display.SCALE, new Vector3f(2, 2, 2))
        .set(EntityDataKeys.Display.BILLBOARD_RENDER_CONSTRAINTS, (byte) 3)          // 3 = CENTER
        .build());

// 가로챈 메타데이터를 이 사람에게만 발광으로
packets.listen(this, PacketTypes.Clientbound.SET_ENTITY_DATA, e -> e.packet().get(EntityDataKeys.Entity.SHARED_FLAGS).ifPresent(f ->
        e.setPacket(e.packet().with(EntityDataKeys.Entity.SHARED_FLAGS, EntityFlags.set(f, EntityFlags.GLOWING, true)))));
```

- 키 210개 = 바닐라 1.21.1 `SynchedEntityData` 정의 전부. 부류·칸 이름은 Mojang 이름(클래스 이름, 필드 이름에서 `DATA_`·`_ID` 를 뺀 것): `Entity.SHARED_FLAGS`, `LivingEntity.HEALTH`, `Display.SCALE`, `ItemDisplay.ITEM_STACK`, `ArmorStand.HEAD_POSE`, `Player.MODE_CUSTOMISATION` …. `EntityDataKeys.byId("Display.scale")` 로도 찾는다.
- 값 종류 31개(`EntityDataType`) 전부 읽고 쓴다. 자바 값: 아이템 `ItemStack`, 블록 상태 `BlockData`, 벡터 `Vector3f`·`Quaternionf`·`Rotations`(`api.math`), 입자 `Particle`(`api.particle`), NBT `NbtCompound`, 주민 `VillagerData`, 옵션 칸은 `Optional`/`OptionalInt`, 고양이·늑대·개구리·그림 종류는 이름 문자열, 스니퍼·아르마딜로 상태는 소문자 상태 이름.
- `get(key)` 은 그 번호에 **같은 값 종류**가 있을 때만 값을 준다(다른 부류의 같은 번호 칸은 empty). `with(key, v)` 는 바꾸거나 더한 새 패킷, `without(key)` 은 뺀 새 패킷.
- 그 엔티티 종류에 칸이 있는지: `packets.hasEntityData("minecraft:zombie", EntityDataKeys.TextDisplay.TEXT)` → false. 없는 칸을 보내면 클라이언트가 연결을 끊으므로, `VirtualEntity.set(key, v)` 는 이것으로 먼저 막는다.
- 숫자 칸(`entry(int, Value)`, `VirtualEntity.setMetadata`)은 그대로 쓸 수 있다 — 번호는 버전마다 다르다. `Value.of(EntityDataType, 값)` 으로 모든 종류의 값을 만들 수 있다.
- 공통 상태 비트는 `EntityFlags`(`ON_FIRE`·`CROUCHING`·`SPRINTING`·`SWIMMING`·`INVISIBLE`·`GLOWING`·`FALL_FLYING`).
- 표의 근거: 칸 번호(`EntityDataIndex767`)와 종류별 칸 배치(`resources/entity_data/layouts-1.21.1.txt`)는 서버 엔티티 정의에서 만들었고, `EntityDataKeysTest` 가 엔티티 종류 130개를 만들어 하나하나 대조한다. 같은 값을 서버 자신의 `SetEntityData` 와 같은 바이트로 쓰는지도 시험한다.

## 스레드와 순서

- `PacketService` 의 모든 메서드는 **어느 스레드에서 불러도 된다.** 보낼 값은 부른 스레드에서 바로 인코딩되므로 없는 블록·엔티티 종류 같은 오류는 그 자리에서 `IllegalArgumentException` 으로 난다.
- 리스너는 **그 플레이어 연결의 네트워크 스레드**에서 패킷 순서대로 불린다. 한 플레이어의 패킷은 늘 같은 스레드라 순서가 섞이지 않는다. 대신 그 스레드는 그 연결의 모든 패킷을 처리하므로 리스너 안에서 파일·DB·월드 변경 같은 오래 걸리는 일은 하지 말고 `Player.scheduler()`·`Furnace.scheduler()`·`asyncExecutor()` 로 넘긴다.
- 값은 불변 레코드라 다른 스레드에 넘겨도 안전하다. `PacketEvent` 객체 자체는 리스너가 불리는 동안만 쓴다.
- 우선순위는 이벤트와 같다(`EventPriority`, LOWEST 먼저·MONITOR 마지막). 취소된 패킷도 다음 리스너에게 간다(`isCancelled()` 로 확인). **MONITOR 리스너가 바꾸거나 취소하면 `IllegalStateException`.**
- 리스너가 던진 예외는 로그만 남기고 다음 리스너로 넘어간다(연결은 끊기지 않는다). 플러그인이 꺼지면 그 플러그인의 리스너는 자동으로 풀린다.

## 비용

- 구독자가 없는 종류는 연결당 배열 한 칸 확인뿐이다(디코드·할당 없음). 등록이 바뀔 때만 "id → 리스너 배열" 표를 다시 굽는다.
- 구독한 타입 래퍼 종류는 패킷마다 한 번 디코드한다. 바꾸지 않으면 이미 만든 바이트를 그대로 보낸다(다시 인코딩하지 않음). `OpaquePacket` 종류는 디코드하지 않는다.
- `broadcast` 는 바이트를 한 번만 만들어 모든 접속자에게 나눈다.

## 보내기·넣기

| 메서드 | 리스너 | 용도 |
|---|---|---|
| `send(player, p)` / `player.sendPacket(p)` | 거친다(`isPluginPacket()` = true) | 보통 보내기 |
| `sendSilently(player, p)` | 안 거친다 | 다른 플러그인이 건드리면 안 되는 패킷 |
| `broadcast(p)` | 거친다 | 모든 접속자 |
| `receive(player, p)` | 거친다 | 클라이언트가 보낸 것처럼 서버에 넣기(진짜 패킷과 같은 검사·처리) |
| `receiveSilently(player, p)` | 안 거친다 | |

보내는 패킷은 서버 상태를 바꾸지 않는다(가상 블록·가상 엔티티). 진짜로 바꾸려면 월드·엔티티 API 를 쓴다.

## 주의

- **채팅 서명**: `ServerboundChatPacket.message` 를 바꾸면 서명이 맞지 않는다. 채팅 내용을 바꾸려면 `AsyncChatEvent` 를 쓰고, 이 패킷은 관찰·취소에 쓴다.
- 범위는 play 단계뿐이다(로그인·구성 단계 패킷은 다루지 않는다).
- `ClientboundDisconnectPacket` 은 클라이언트에 사유만 보인다. 연결을 끊으려면 `Player.kick`.
- 서버가 연결을 끊을 때 보내는 disconnect(킥·종료)는 연결을 닫는 경로라 리스너를 거치지 않는다. 사유를 바꾸려면 `PlayerKickEvent` 를 쓴다.

## 다음에 더할 것

장비(`set_equipment`), 입자(`level_particles` — 값은 이미 있는 `Particle`), 상대 이동(`move_entity_*`), 블록 여러 칸(`section_blocks_update`). 래퍼가 늘어도 기존 API 는 그대로다 — `OpaquePacket` 이던 종류가 타입 래퍼로 바뀐다(D5: API 는 semver, 래퍼 추가는 마이너).

## 서버 구현 위치

- API: `furnace-api/.../api/packet/`(+ `entity/Hand`, `world/BlockFace`, `world/SoundCategory`).
- 구현: `furnace-server/.../plugin/packet/` — `PacketServiceImpl`(등록·연결별 `Tap`), `ProtocolCodec`(버전 계층 인터페이스), `Protocol767`(1.21.1 id 표·코덱; id 순서는 바닐라 `GameProtocols` 등록 순서를 바이트코드로 확인), `NetworkText`(네트워크 NBT ↔ `Text`).
- 연결 지점: 나가는 쪽은 `SharedChunkFrames.encode`(모든 play 송신이 지나는 곳), 들어오는 쪽은 `ConnectionHandler.dispatchPlay`. 플레이어가 play 에 들어올 때 채널 속성 `PacketTap.KEY` 로 붙는다. `receive` 는 `ConnectionHandler.injectPlay` 로 이벤트 루프에 넣는다.
- 새 MC 버전 지원: `ProtocolCodec` 구현을 하나 더 만들고 `PluginHost` 에서 고른다. API 타입은 바꾸지 않는다.
- 시험: `PacketApiTest`(id 표·왕복·가로채기), `PacketApiFlowTest`(실제 연결에서 바꾸기·취소·receive), `furnace-api` 의 `PacketTypesTest`.
