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

// 5) 타입 래퍼가 없는 종류는 GenericPacket — 이름 붙은 필드로 읽고 바꾸고 보낸다(E4a)
packets.listen(this, PacketTypes.Clientbound.GAME_EVENT, e -> {
    if (e.packet().get("event").equals(1)) e.setCancelled(true);       // 비 시작을 숨긴다
});
player.sendPacket(GenericPacket.of(PacketTypes.Clientbound.SET_CAMERA).with("camera_id", npcId));

// 6) 서버가 아직 내용을 못 푸는 종류도 관찰·취소는 된다
packets.listen(this, PacketTypes.Clientbound.LEVEL_PARTICLES, e -> e.setCancelled(true));
```

## 개념

| 이름 | 뜻 |
|---|---|
| `PacketType<P>` | 방향 + 바닐라 패킷 이름(`minecraft:system_chat`). 상수는 `PacketTypes.Clientbound.*` / `PacketTypes.Serverbound.*`, 이름으로는 `PacketTypes.byName`. 같은 종류는 늘 같은 객체 |
| 타입 래퍼 | `ClientboundXxxPacket` / `ServerboundXxxPacket` 레코드(Mojang 매핑 이름과 같게 지었다). 읽기·바꾸기·보내기 가능 |
| `GenericPacket` | 타입 래퍼가 없는 종류. 바닐라 필드 이름(snake_case)으로 읽기·바꾸기·보내기(아래 "범용 패킷"). 서버가 그 버전에서 못 푸는 종류면 `readable()` false — 관찰·취소만 |
| `PacketEvent` | `player()`, `packet()`, `setPacket()`, `setCancelled()`, `isPluginPacket()` |
| `PacketService` | `listen`, `send`/`sendSilently`, `sendBundle`, `broadcast`, `receive`/`receiveSilently`, `isSupported`, `isReadable`, `fields`, `nextEntityId`, `protocolVersion` |

- 목록은 play 단계 전체(서버→클라이언트 124종, 클라이언트→서버 55종)다. 바닐라의 이동 패킷 넷(`move_player_pos`·`_pos_rot`·`_rot`·`_status_only`)은 `MOVE_PLAYER` 하나로 묶었다(버전마다 갈래가 달라질 수 있어서). 보낼 때 `hasPosition`·`hasRotation` 으로 알맞은 것이 골라진다.
- 다른 버전에 없는 패킷은 `isSupported(type)` 가 false 이고, 구독해도 불리지 않는다.

### 타입 래퍼가 있는 종류 (34)

서버→클라이언트: `SYSTEM_CHAT`, `SET_ACTION_BAR_TEXT`, `SET_TITLE_TEXT`, `SET_SUBTITLE_TEXT`, `SET_TITLES_ANIMATION`, `CLEAR_TITLES`, `DISCONNECT`, `SET_TIME`, `BLOCK_UPDATE`, `ADD_ENTITY`, `SET_ENTITY_DATA`, `PLAYER_INFO_UPDATE`, `PLAYER_INFO_REMOVE`, `REMOVE_ENTITIES`, `TELEPORT_ENTITY`, `SET_ENTITY_MOTION`, `ROTATE_HEAD`, `ANIMATE`, `SOUND`, `CUSTOM_PAYLOAD`.

클라이언트→서버: `CHAT`, `CHAT_COMMAND`, `CLIENT_COMMAND`, `CLIENT_INFORMATION`, `CUSTOM_PAYLOAD`, `INTERACT`, `MOVE_PLAYER`, `PLAYER_ACTION`, `PLAYER_COMMAND`, `SET_CARRIED_ITEM`, `SIGN_UPDATE`, `SWING`, `USE_ITEM`, `USE_ITEM_ON`.

단위: 각도는 도(degree), 속도는 블록/틱(축마다 ±3.9 로 잘림), 좌표는 블록. 와이어의 1/256 바퀴 바이트·1/8000 short·×8 정수는 변환 계층이 맡는다.

## 범용 패킷 — 이름 붙은 필드 (E4a, 2026-11-12)

타입 래퍼가 없는 종류는 모두 `GenericPacket` 이다. 필드는 바닐라(Mojang) 필드 이름을 snake_case 로 바꾼 것이고 순서도 바닐라와 같다.
종류마다 어떤 필드가 있는지는 `packets.fields(type)`(이름 + 값 종류), 내용을 읽을 수 있는지는 `packets.isReadable(type)`.

```java
static final PacketField<Integer> EVENT = PacketField.of("event", Integer.class);   // 같은 필드를 여러 번 쓰면 상수로

packets.listen(this, PacketTypes.Clientbound.UPDATE_MOB_EFFECT, e -> {
    GenericPacket p = e.packet();
    if (p.get("effect").equals("minecraft:invisibility")) e.setPacket(p.with("flags", (byte) 0));   // 입자·아이콘 숨김
});
packets.send(player, GenericPacket.of(PacketTypes.Clientbound.SET_BORDER_WARNING_DISTANCE).with("warning_blocks", 0));
```

| 바닐라 필드 | 값 |
|---|---|
| `int`·`long`·`short`·`byte`·`float`·`double`·`boolean` | `Integer`·`Long`·`Short`·`Byte`·`Float`·`Double`·`Boolean` — 바닐라 필드에 담긴 단위 그대로(회전은 1/256 바퀴 byte, 속도는 1/8000). 단위를 바꾼 편의 값은 타입 래퍼가 준다 |
| `String`·`UUID`·`Component` | `String`·`UUID`·`Text` |
| `ResourceLocation`·`ResourceKey`, 레지스트리 값(아이템·블록·엔티티 종류·메뉴·효과·소리·차원 종류) | 이름 문자열 `"minecraft:stone"`. 등록되지 않은 소리 이름은 바닐라처럼 직접 정의로 나간다. 소리는 `PacketStruct{sound_id, range Optional<Float>}`(바닐라 SoundEvent 코덱 필드 이름)도 받는다 — 언제나 직접 정의(고정 범위 포함)로 나가고, 직접 정의가 등록된 이름이거나 고정 범위가 있으면 읽을 때도 이 꼴로 온다. 필드 선언 종류는 String 이라 `with` 로 String 자리에 PacketStruct 를 넣을 수는 없다(묶음을 새로 만든다) |
| `BlockPos`·`GlobalPos`·`BlockState`·`ItemStack`·`CompoundTag` | `BlockPos`·`GlobalPos`·`BlockData`·`ItemStack`·`NbtCompound` |
| enum | 바닐라 상수 이름 문자열(`"MAIN_HAND"`). 이전 게임 모드처럼 바닐라가 -1 로 "없음"을 쓰는 것은 `Optional<String>` |
| `byte[]`·`int[]`·`long[]` / `List`·`Set` / `Optional`·없을 수 있는 값 | 배열(넣고 꺼낼 때 복사) / `List` / `Optional` |
| 패킷 안 구조(`CommonPlayerSpawnInfo` 등) | `PacketStruct`(같은 get/with) |
| `ChunkPos`·`SectionPos` | 바닐라가 쓰는 묶은 `long` |

- `with` 는 이미 있는 필드면 같은 값 종류만 받는다(`Integer` 자리에 `Long` 은 `IllegalArgumentException`). 보낼 때(그리고 리스너의 `setPacket` 때 바로) 필드가 빠졌거나 모르는 이름·범위를 넘는 값(u8 에 256)·바닐라 문법이 아닌 이름(`"minecraft:Bad Name"`)·32767자 넘는 문자열이면 `IllegalArgumentException`.
  필드마다 더 작은 바닐라 상한도 검사한다(리소스 팩 hash 40자, 쿠키 payload 5120바이트, 명령 제안 32500자, 책 쪽 200개·쪽 8192자·제목 128자 등). 바닐라가 `Set` 인 필드(`login` 의 `levels`)는 같은 값을 두 번 넣으면 거부한다.
- 읽을 수 있는 종류라도 패킷 하나가 디코드에 실패하면(클라이언트가 보낸 잘못된 패킷 등) 그 패킷만 `readable()` false 로 리스너에 간다.
- 값은 넣고 꺼낼 때 복사된다(배열·`NbtCompound` — 목록·`Optional` 안에 있어도). 꺼낸 값을 바꿔도 패킷은 그대로다.
- **1.21.1 에서 읽는 종류 133개**(서버→클라 92, 클라→서버 41). 필드 나열 110개 + 손 코덱 23개(아래 "손 코덱 종류"). 나머지 12개(입자·폭발·청크·빛·
  명령 트리·레시피 둘·발전 과제·태그 묶음·서명된 채팅·상인 거래·번들 구분자)는 `readable()` false 로 관찰·취소만 된다.
- 필드 목록은 중립 스키마(`furnace-api/.../packet/schema/protocol-767.schema`) 하나에서 나온다 — NMS 호환 계층의 패킷 shim 과 같은 파일이다.
- 와이어 확인: 서버가 실제로 보내는 종류는 바이트 왕복 시험(`GenericPacketRoundTripTest`), 나머지는 바닐라 jar 바이트코드와 하나씩 대조했다(`docs/feature-parity/context-notes/2026-11-12-easy-nms-e4a.md`).
  손 코덱 종류는 바닐라 jar 의 패킷 코덱을 직접 돌려 같은 바이트를 확인했다(`2026-11-12-easy-nms-e4b.md`).

### 손 코덱 종류 (E4b, 2026-11-12)

바닐라가 비트 묶음·조건부·순서를 바꿔 쓰는 종류(스키마 `custom`). 이름은 같은 snake_case 규칙이고, **필드 순서는 와이어 순서**다. 비트 묶음 바이트는 `Boolean` 필드 여럿으로,
조건이 있어야 나가는 부분은 `Optional<PacketStruct>` 로 나뉜다.

| 종류 | 필드 (와이어 순서) |
|---|---|
| `player_abilities`(→클라) | `invulnerable`·`is_flying`·`can_fly`·`instabuild`(비트 1·2·4·8) `flying_speed`·`walking_speed` |
| `set_experience` | `experience_progress` `experience_level` `total_experience` |
| `stop_sound` | `source` `Optional<String>`(SoundSource) · `name` `Optional<String>` |
| `player_look_at` | `from_anchor` `x` `y` `z` · `at_entity` `Optional<{entity, to_anchor}>` |
| `player_position` | `x` `y` `z` `y_rot` `x_rot` · `relative_arguments` `List<String>`(`X`·`Y`·`Z`·`Y_ROT`·`X_ROT`, 비트 집합) · `id` |
| `move_entity_pos` / `_pos_rot` / `_rot` | `entity_id` (`xa` `ya` `za` Short) (`y_rot` `x_rot` Byte) `on_ground` — 종류에 있는 것만 |
| `set_objective` | `objective_name` · `method`(0 추가·1 제거·2 바꿈) · `parameters` `Optional<{display_name, render_type, number_format}>` — method 0·2 일 때만 있다 |
| `set_equipment` | `entity` · `slots` `List<{slot, item}>`(EquipmentSlot 이름, 빈 목록은 못 보낸다 — 바닐라가 적어도 한 칸을 읽는다) |
| `section_blocks_update` | `section_pos` `Long` · `positions` `List<Short>`(구역 안 `x<<8 \| z<<4 \| y`, 0~4095) · `states` `List<BlockData>`(같은 길이) |
| `damage_event` | `entity_id` `source_type`(damage_type 이름) `source_cause_id` `source_direct_id`(-1 = 없음) `source_position` `Optional<{x,y,z}>` |
| `player_abilities`(→서버) | `is_flying` |
| `player_input` | `xxa` `zza` `is_jumping` `is_shift_key_down` |
| `seen_advancements` | `action` · `tab` `Optional<String>` — `OPENED_TAB` 일 때만 있다 |
| `set_command_block` | `pos` `command` `mode` `track_output` `conditional` `automatic` |
| `set_structure_block` | `pos` `update_type` `mode` `name` `offset` `size`(좌표마다 i8 인 `BlockPos`) `mirror` `rotation` `data`(128자까지 — 바닐라 readUtf(128)) `integrity` `seed` `ignore_entities` `show_air` `show_bounding_box` |
| `boss_event` | `id` · `operation` `{type, …}` — `ADD {name, progress, color, overlay, darken_screen, play_music, create_world_fog}`, `REMOVE {}`, `UPDATE_PROGRESS {progress}`, `UPDATE_NAME {name}`, `UPDATE_STYLE {color, overlay}`, `UPDATE_PROPERTIES {darken_screen, play_music, create_world_fog}` |
| `set_player_team` | `name` · `method`(0 추가·1 제거·2 바꿈·3 선수 넣기·4 선수 빼기) · `parameters` `Optional<{display_name, options, nametag_visibility, collision_rule, color, player_prefix, player_suffix}>`(method 0·2, 두 문자열은 40자까지, `color` 는 ChatFormatting 이름) · `players` `Optional<List<String>>`(method 0·3·4) |
| `update_attributes` | `entity_id` · `attributes` `List<{attribute, base, modifiers List<{id, amount, operation}>}>` |
| `map_item_data` | `map_id` `scale` `locked` · `decorations` `Optional<List<{type, x, y, rot, name}>>` · `color_patch` `Optional<{width, height, start_x, start_y, map_colors}>`(width 1 이상, 보낼 때 `map_colors` 길이 = width×height, start+크기 ≤ 128 — 어기면 클라이언트가 끊기므로 거부) |
| `award_stats` | `stats` `List<{type, value, amount}>`(`type` 은 stat_type 이름, `value` 는 그 종류의 레지스트리 이름 — `minecraft:mined` 면 블록. 읽은 값은 늘 `minecraft:` 이 붙으므로 줄여 쓴 값과 `equals` 가 다르다) |
| `container_click` | `container_id` `state_id` `slot_num` `button_num` `click_type` · `changed_slots` `List<{slot, item}>`(128칸까지, 같은 칸 두 번 안 됨 — 클라이언트가 같은 칸을 두 번 보내면 읽을 때 바닐라처럼 마지막 값 하나로 합친다) · `carried_item` |

숫자 형식(`set_objective`·`set_score` 의 `number_format`)은 `{type:"minecraft:blank"}`·`{type:"minecraft:styled", style:<스타일 NbtCompound>}`·
`{type:"minecraft:fixed", value:<Text>}`, 바닐라 `Vec3` 은 `{x, y, z}`(`Double`). `block_entity_data` 의 `type` 은 블록 엔티티 종류 이름(`"minecraft:sign"`).
채팅 쪽 값: `MessageSignature.Packed` = `{id, full_signature Optional<byte[]>}`(id -1 이면 256바이트 서명 전체), `ChatType.Bound` = `{chat_type, name, target_name}`,
`LastSeenMessages.Update` = `{offset, acknowledged}`(최근 20개 확인 비트를 정수로 — 읽기는 바닐라처럼 3바이트 24비트까지 받지만 20비트를 넘은 값은 다시 쓸 수 없다), 인자 서명 = `List<{name, signature}>`(8개까지, 서명 256바이트),
채팅 세션 = `{session_id, profile_public_key {expires_at, key, key_signature}}`(`key` 는 RSA X.509 — 읽기·쓰기 모두 아니면 거부, 바닐라 디코드 실패와 같다), `Instant` = epoch 밀리초 `Long`.
`server_links` 원소는 `{known_type, label, url}`(둘 중 하나만), `custom_report_details` 의 `details` 는 키가 이름인 `PacketStruct`(값 String, 32개·키 128자·값 4096자까지).
styled 의 `style` 은 보낼 때 바닐라 Style 로 읽히는지 검사한다(틀린 색 이름·문자열 참거짓 등은 바닐라 클라이언트가 접속을 끊으므로 `IllegalArgumentException`, 모르는 키는 바닐라처럼 무시).

- `relative_arguments` 처럼 비트 집합인 목록은 읽으면 비트 순서로 나온다. 다른 순서로 만든 값과 `equals` 는 다르지만 같은 바이트로 나간다.
- `fields(type)` 는 바깥 필드만 준다(`Optional<PacketStruct>` 안쪽 필드는 위 표).

### 예전 코드에서 바뀐 점 (E4a)

- `OpaquePacket` 은 없어졌다. 타입 래퍼가 없는 상수는 `PacketType<GenericPacket>` 이다. `packet.readable()` 이 false 인 것만 예전 `OpaquePacket` 처럼 관찰·취소만 된다.
- 읽을 수 있는 `GenericPacket` 종류는 리스너에서 `setPacket` 으로 바꿀 수 있다(예전에는 `UnsupportedOperationException`).

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

- 키 207개 = 바닐라 1.21.1 `SynchedEntityData` 정의 전부. 부류·칸 이름은 Mojang 이름(클래스 이름, 필드 이름에서 `DATA_`·`_ID` 를 뺀 것): `Entity.SHARED_FLAGS`, `LivingEntity.HEALTH`, `Display.SCALE`, `ItemDisplay.ITEM_STACK`, `ArmorStand.HEAD_POSE`, `Player.MODE_CUSTOMISATION` …. `EntityDataKeys.byId("Display.scale")` 로도 찾는다.
- 값 종류 31개(`EntityDataType`) 전부 읽고 쓴다. 자바 값: 아이템 `ItemStack`, 블록 상태 `BlockData`, 벡터 `Vector3f`·`Quaternionf`·`Rotations`(`api.math`), 입자 `Particle`(`api.particle`), NBT `NbtCompound`, 주민 `VillagerData`, 옵션 칸은 `Optional`/`OptionalInt`, 고양이·늑대·개구리·그림 종류는 이름 문자열, 스니퍼·아르마딜로 상태는 소문자 상태 이름.
- `get(key)` 은 그 번호에 **같은 값 종류**가 있을 때만 값을 준다(다른 부류의 같은 번호 칸은 empty). `with(key, v)` 는 바꾸거나 더한 새 패킷, `without(key)` 은 뺀 새 패킷.
- 그 엔티티 종류에 칸이 있는지: `packets.hasEntityData("minecraft:zombie", EntityDataKeys.TextDisplay.TEXT)` → false. 칸의 주인 부류(`key.owner()`)가 그 종류의 바닐라 계층에 있어야 한다 — `interaction` 에 `LivingEntity.HEALTH` 는 번호·값 종류가 우연히 맞아도 false. 없는 칸을 보내면 클라이언트가 연결을 끊으므로, `VirtualEntity.set(key, v)` 는 이것으로 먼저 막는다. 보낼 수 없는 값(없는 고양이 종류 이름 등)도 `set` 에서 바로 `IllegalArgumentException` 이다.
- 서버 없이 도는 플러그인 단위 시험에서는 칸 번호 표가 없어 `key.index()`·`builder().set`·`get`·`with` 가 `IllegalStateException` 을 낸다(표는 서버가 한 번만 건다).
- `optional_block_state` 에 공기를 넣으면 `Optional.empty()` 로 바뀐다(바닐라 와이어에서 공기와 없음이 같은 0).
- 가로챈 패킷에서 값 하나를 못 읽으면(이 버전이 모르는 직렬화 번호, 범위 밖 종류 번호, 직접 정의된 그림·늑대 종류) 그 칸을 `Unsupported` 로 남기고 멈춘다. 그런 패킷은 관찰·취소만 되고 다시 보낼 수 없다(`hasUnsupported()`).
- 숫자 칸(`entry(int, Value)`, `VirtualEntity.setMetadata`)은 그대로 쓸 수 있다 — 번호는 버전마다 다르다. `Value.of(EntityDataType, 값)` 으로 모든 종류의 값을 만들 수 있다.
- 공통 상태 비트는 `EntityFlags`(`ON_FIRE`·`CROUCHING`·`SPRINTING`·`SWIMMING`·`INVISIBLE`·`GLOWING`·`FALL_FLYING`).
- 표의 근거: 칸 번호(`EntityDataIndex767`)와 종류별 칸 배치(`resources/entity_data/layouts-1.21.1.txt`)는 서버 엔티티 정의에서 만들었고, `EntityDataKeysTest` 가 엔티티 종류 130개를 만들어 하나하나 대조한다. 같은 값을 서버 자신의 `SetEntityData` 와 같은 바이트로 쓰는지도 시험한다. 배치·번호·값 종류·주인 부류(`resources/entity_data/owners-1.21.1.txt`)는 바닐라 1.21.1 서버 jar 바이트코드와도 대조했다(2026-11-12 독립 검토, 130/130).

### 예전 코드에서 바뀐 점 (E1)

- `Value` 는 sealed 인데 허용 하위 타입에 `Typed`(아이템·블록 상태·벡터·입자·NBT …)가 늘었다. `Value` 를 `switch` 로 나눌 때는 **`default` 를 둔다** — 없으면 소스는 컴파일되지 않고, 예전에 컴파일한 jar 는 `Typed` 를 만나면 `MatchException` 이 난다.
- 예전에 `Unsupported` 로 오던 아이템·입자·블록 상태 칸이 이제 `Typed` 로 온다. 그래서 그런 패킷의 `hasUnsupported()` 가 false 가 되고, 디코드가 그 자리에서 멈추지 않아 뒤 칸까지 들어온다.
- 예전 레코드 9종(`ByteValue` … `PoseValue`)과 `ofXxx` 팩토리는 그대로다. `Typed` 는 이 9종의 값 종류를 받지 않는다(`Value.of` 를 쓴다).

## 스레드와 순서

- `PacketService` 의 모든 메서드는 **어느 스레드에서 불러도 된다.** 보낼 값은 부른 스레드에서 바로 인코딩되므로 없는 블록·엔티티 종류 같은 오류는 그 자리에서 `IllegalArgumentException` 으로 난다.
- 리스너는 **그 플레이어 연결의 네트워크 스레드**에서 패킷 순서대로 불린다. 한 플레이어의 패킷은 늘 같은 스레드라 순서가 섞이지 않는다. 대신 그 스레드는 그 연결의 모든 패킷을 처리하므로 리스너 안에서 파일·DB·월드 변경 같은 오래 걸리는 일은 하지 말고 `Player.scheduler()`·`Furnace.scheduler()`·`asyncExecutor()` 로 넘긴다.
- 값은 불변 레코드라 다른 스레드에 넘겨도 안전하다. `PacketEvent` 객체 자체는 리스너가 불리는 동안만 쓴다.
- 우선순위는 이벤트와 같다(`EventPriority`, LOWEST 먼저·MONITOR 마지막). 취소된 패킷도 다음 리스너에게 간다(`isCancelled()` 로 확인). **MONITOR 리스너가 바꾸거나 취소하면 `IllegalStateException`.**
- 리스너가 던진 예외는 로그만 남기고 다음 리스너로 넘어간다(연결은 끊기지 않는다). 플러그인이 꺼지면 그 플러그인의 리스너는 자동으로 풀린다.

## 비용

- 구독자가 없는 종류는 연결당 배열 한 칸 확인뿐이다(디코드·할당 없음). 등록이 바뀔 때만 "id → 리스너 배열" 표를 다시 굽는다.
- 구독한 종류는 패킷마다 한 번 디코드한다(타입 래퍼·`GenericPacket` 모두). 바꾸지 않으면 이미 만든 바이트를 그대로 보낸다(다시 인코딩하지 않음). 내용을 못 푸는 종류는 디코드하지 않는다.
- `broadcast` 는 바이트를 한 번만 만들어 모든 접속자에게 나눈다.

## 보내기·넣기

| 메서드 | 리스너 | 용도 |
|---|---|---|
| `send(player, p)` / `player.sendPacket(p)` | 거친다(`isPluginPacket()` = true) | 보통 보내기 |
| `sendSilently(player, p)` | 안 거친다 | 다른 플러그인이 건드리면 안 되는 패킷 |
| `broadcast(p)` | 거친다 | 모든 접속자 |
| `sendBundle(player, List)` / `player.sendPacketBundle(p…)` | 거친다(안의 패킷 하나씩) | 여러 패킷을 한 프레임에(아래) |
| `sendBundleSilently(player, List)` | 안 거친다 | |
| `receive(player, p)` | 거친다 | 클라이언트가 보낸 것처럼 서버에 넣기(진짜 패킷과 같은 검사·처리) |
| `receiveSilently(player, p)` | 안 거친다 | |

보내는 패킷은 서버 상태를 바꾸지 않는다(가상 블록·가상 엔티티). 진짜로 바꾸려면 월드·엔티티 API 를 쓴다.

### 번들 — 여러 패킷을 한 프레임에 (E2, 2026-11-12)

```java
packets.sendBundle(player, List.of(
        ClientboundAddEntityPacket.of(id, uuid, "minecraft:zombie", x, y, z, 0, 0),
        ClientboundSetEntityDataPacket.builder(id).set(EntityDataKeys.Entity.SHARED_FLAGS, EntityFlags.of(EntityFlags.GLOWING)).build(),
        equipment));
```

- 바닐라 `bundle_delimiter` 로 감싼다 — 클라이언트가 안의 패킷을 한 프레임에 처리한다. 엔티티 생성·메타데이터·장비를 따로 보내면 한 프레임 동안 맨몸·기본 모습이 보이는데(깜박임), 번들이면 처음부터 다 갖춘 모습으로 나타난다. 서버도 엔티티를 보여 줄 때 이렇게 보낸다.
- 다른 스레드가 같은 플레이어에게 보낸 패킷이 번들 사이에 끼지 않는다(연결 스레드 작업 하나로 쓴다).
- 모두 먼저 인코딩한다. 하나라도 못 하면 아무것도 보내지 않고 `IllegalArgumentException`. 최대 `PacketService.BUNDLE_MAX`(4096, 바닐라 클라이언트 상한 — 넘으면 클라이언트가 끊는다). 구분자는 서버가 넣으므로 목록에 넣지 않는다.
- 리스너는 안의 패킷을 하나씩 본다. 취소하면 그 패킷만 빠지고, 모두 취소되면 아무것도 보내지 않는다. 구분자(`BUNDLE_DELIMITER`)는 리스너에 보이지 않는다 — 서버가 짝을 맞춰 직접 쓴다(취소되면 클라이언트가 멈춘다).
- 서버 쪽에서 번들 도중 패킷 쓰기가 실패하면 그 번들은 통째로 보내지 않는다(구분자가 열린 채 남지 않게).
- 가상 엔티티(`VirtualEntity`)·엔티티 변장·플레이어 생성 묶음도 번들로 간다(바닐라 `ServerEntity.addPairing`).

## 주의

- **채팅 서명**: `ServerboundChatPacket.message` 를 바꾸면 서명이 맞지 않는다. 채팅 내용을 바꾸려면 `AsyncChatEvent` 를 쓰고, 이 패킷은 관찰·취소에 쓴다.
- 범위는 play 단계뿐이다(로그인·구성 단계 패킷은 다루지 않는다).
- `ClientboundDisconnectPacket` 은 클라이언트에 사유만 보인다. 연결을 끊으려면 `Player.kick`.
- 서버가 연결을 끊을 때 보내는 disconnect(킥·종료)는 연결을 닫는 경로라 리스너를 거치지 않는다. 사유를 바꾸려면 `PlayerKickEvent` 를 쓴다.

## 다음에 더할 것

타입 래퍼: 장비(`set_equipment`), 입자(`level_particles` — 값은 이미 있는 `Particle`), 상대 이동(`move_entity_*`), 블록 여러 칸(`section_blocks_update`). 래퍼가 늘면 `GenericPacket` 이던 종류가 타입 래퍼로 바뀐다(E5) — 그때도 `GenericPacket` 으로 볼 길을 둔다(D5: API 는 semver).

## 서버 구현 위치

- API: `furnace-api/.../api/packet/`(+ `entity/Hand`, `world/BlockFace`, `world/SoundCategory`).
- 구현: `furnace-server/.../plugin/packet/` — `PacketServiceImpl`(등록·연결별 `Tap`), `ProtocolCodec`(버전 계층 인터페이스), `Protocol767`(1.21.1 id 표·코덱; id 순서는 바닐라 `GameProtocols` 등록 순서를 바이트코드로 확인), `NetworkText`(네트워크 NBT ↔ `Text`), `GenericCodec767`(스키마로 `GenericPacket` 읽고 쓰기), `GenericHand767`(custom 종류 손 코덱).
- 연결 지점: 나가는 쪽은 `SharedChunkFrames.encode`(모든 play 송신이 지나는 곳), 들어오는 쪽은 `ConnectionHandler.dispatchPlay`. 플레이어가 play 에 들어올 때 채널 속성 `PacketTap.KEY` 로 붙는다. `receive` 는 `ConnectionHandler.injectPlay` 로 이벤트 루프에 넣는다.
- 새 MC 버전 지원: `ProtocolCodec` 구현을 하나 더 만들고 `PluginHost` 에서 고른다. API 타입은 바꾸지 않는다.
- 시험: `PacketApiTest`(id 표·왕복·가로채기), `PacketApiFlowTest`(실제 연결에서 바꾸기·취소·receive), `GenericPacketTest`(범용 코덱 고정 바이트·검사), furnace-test `GenericPacketRoundTripTest`(서버가 보낸 바이트 왕복), `furnace-api` 의 `PacketTypesTest`·`GenericPacketApiTest`.
