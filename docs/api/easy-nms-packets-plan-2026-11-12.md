# NMS·패킷을 쉽게 — Furnace API 설계안 (2026-11-12)

> 요청: 도미니코 2026-11-12 "FURNACE API 로 개발하는 개발자는 NMS, 패킷처럼 기존 Bukkit API 에서 쓰기 어렵던 것을 쓰기 쉽게 API 구성 필요".
> 바탕: [packets.md](packets.md)(D2 패킷 API), [internals.md](internals.md)(NBT·`Internals` 다리), [virtual-entities.md](virtual-entities.md), [player-views.md](player-views.md), [entity-views.md](entity-views.md), [mob-ai.md](mob-ai.md), 불만 조사 48~54·107행(`docs/feature-parity/paper-spigot-dev-complaints-2026-11-06.md`).
> 관계: Bukkit·Paper 플러그인용 NMS **호환 어댑터**(`docs/feature-parity/compat-nms-adapter-plan-2026-11-11.md`)는 "남이 쓴 NMS 코드를 돌리는 것", 이 문서는 "Furnace 로 새로 쓰는 개발자가 NMS 를 쓸 필요가 없게 하는 것"이다. 둘은 서버 쪽 패킷 스키마(5장)를 함께 쓴다.

## 1. 목표와 원칙

Paper·Spigot 개발자가 NMS·ProtocolLib 을 쓰는 이유는 셋이다: (1) Bukkit 에 없는 기능, (2) 플레이어마다 다르게 보이기(패킷), (3) 성능(검사·갱신 건너뛰기). 그리고 아픈 곳도 셋이다: 매핑·리플렉션, **버전마다 깨짐**(ProtocolLib 반응 상위가 거의 다 "새 버전 지원해 주세요"), 스레드 규칙을 모름.

원칙:

1. **타입 안전** — 숫자 칸 번호·패킷 id·리플렉션 문자열 대신 상수와 레코드. 틀린 값은 컴파일 때, 늦어도 부른 자리에서 `IllegalArgumentException`.
2. **버전 안정** — API 에는 이름(바닐라·Mojang 이름)만 드러나고 번호·바이트 배치는 서버 변환 계층(`ProtocolCodec`)이 버전마다 맞춘다. 새 MC 버전에서 플러그인 코드는 그대로.
3. **한 곳에서 다 됨** — 자주 쓰는 일은 고수준 API(가상 엔티티·화면 덮어쓰기·몹 AI) 한 줄, 드문 일은 패킷 API, 그래도 안 되면 `Internals` 다리. 아래 단으로 내려갈수록 버전 약속이 약해진다는 것을 문서와 타입이 알려 준다.
4. **스레드 규칙은 서버가 맞춘다** — 아무 스레드에서 불러도 되는 것과 소유 스레드에서만 되는 것을 이름(`*Now`)과 예외(`ThreadOwnershipException`)로 가른다(기존 규칙 그대로).
5. **안 쓰면 공짜** — 구독자·덮어쓰기가 없으면 서버 경로에 비용이 없다(기존 패킷 API 규칙 그대로).

## 2. 지금 있는 것 (2026-11-12 기준 브랜치 `be0e7649`)

| 단 | 무엇 | 상태 |
|---|---|---|
| 0 고수준 | 가상 엔티티·NPC·가짜 탭 줄, 플레이어별 가상 블록·바이옴·보더·장비, 엔티티 숨기기·변장, 몹 AI(목표·두뇌·길찾기), 아이템 컴포넌트 | 있음 |
| 1 데이터 | NBT 트리(`api.nbt`), 엔티티·블록 엔티티 NBT 읽기·고치기(= `/data`) | 있음 |
| 2 패킷 | `PacketService`: 보내기·넣기·가로채기(읽기·바꾸기·취소). play 단계 179종 전부 관찰·취소, **타입 래퍼 34종**만 읽기·바꾸기·보내기 | 일부 |
| 3 내부 | `Internals`: 엔티티·칸·서버 내부 객체를 소유 스레드에서 형 검사해 꺼냄. 서버 jar 를 compileOnly | 있음(불안정 단) |

## 3. 공백과 불편 — 개발자가 아직 NMS·ProtocolLib 을 찾게 되는 곳

코드를 읽고 확인한 것만 적는다.

| # | 공백 | 지금 모습 | 왜 아픈가 |
|---|---|---|---|
| G1 | **엔티티 메타데이터가 숫자 칸 번호** | `ClientboundSetEntityDataPacket.entry(0, Value.ofByte(0x40))`, `VirtualEntity.setMetadata(6, …)`. 값 종류 31개 중 9개만(바이트·정수·long·실수·참거짓·문자열·텍스트·옵션 텍스트·자세). 아이템·블록 상태·입자·벡터·쿼터니언(디스플레이 엔티티)·옵션 UUID·위치 등은 `Unsupported` 로 멈춘다 | ProtocolLib `WrappedDataWatcher` 의 가장 흔한 버그: 칸 번호가 버전마다 밀린다. 디스플레이 엔티티(홀로그램 표준)는 벡터·쿼터니언·아이템·블록 상태가 필수인데 지금은 못 보낸다 |
| G2 | **타입 래퍼 34/179** | 나머지 145종은 `OpaquePacket`(관찰·취소만) | 장비·입자·상대 이동·여러 칸 블록·팀(이름표)·탑승·화면 열기·컨테이너 내용·표지판 편집기·블록 금 가기·게임 이벤트·속성·보스바·탭 머리말·지도 그림 … 흔한 ProtocolLib 쓰임이 다 여기 있다 |
| G3 | **여러 패킷을 한 프레임에** | 따로따로 보냄. `bundle_delimiter` 는 Opaque | 엔티티 생성 + 메타데이터 + 장비를 따로 보내면 한 프레임 동안 맨몸·기본 모습이 보인다(깜박임). 바닐라는 번들로 묶는다 |
| G4 | **패킷 시험 도구** | `furnace-test` 의 `TestPlayer` 는 채팅·액션바·플러그인 메시지만 모은다 | 패킷 코드를 클라이언트 없이 단위 시험할 길이 없다 |
| G5 | **엔티티 속성(이동 속도·최대 체력 …)** | 아이템 속성 수정자만 API. 엔티티 속성은 `Internals` 로 내부 `AttributeMap` 을 꺼내야 한다 | NMS 쓰는 가장 흔한 이유 중 하나(불만 17~25행 근처). 버전마다 속성 이름이 바뀐다(1.21.2 `generic.` 접두사 삭제) |
| G6 | **빠른 블록 쓰기** | `World.setBlock(pos, data)` 한 칸, 늘 이웃 갱신·물리 | 큰 범위 편집·미니게임 맵 복구가 NMS `LevelChunkSection.setBlockState` 로 가는 이유. 갱신 끄기·빛 재계산 묶기·청크 한 번 보내기가 없다 |
| G7 | **내부 단 개발 경험** | `compileOnly(files("libs/furnace-server.jar"))`, 어떤 내부 클래스를 쓰는지 운영자가 모름 | 서버 버전을 올릴 때 어느 플러그인이 깨질지 알 수 없다. 소스·문서가 붙은 개발용 묶음이 없다 |
| G8 | **로그인·구성 단계 패킷** | play 단계만 | 구성 단계 플러그인 메시지·쿠키·리소스 팩 선 적용(모드 서버 핸드셰이크)은 패킷 라이브러리로만 됐다 |
| G9 | **ProtocolLib·NMS 에서 옮겨 오는 안내** | 문서가 기능별로 흩어짐 | "ProtocolLib 의 X 는 Furnace 의 Y" 표가 없다 |

이미 메운 것(재확인용): 플레이어별 블록·바이옴·보더·장비 표시, 숨기기·변장, NPC, 몹 목표·두뇌·길찾기, NBT — 다시 만들지 않는다.

## 4. 설계

### 4.1 타입 있는 엔티티 메타데이터 (G1) — 1순위

```java
// 키는 바닐라(Mojang) 이름. 칸 번호는 서버가 버전마다 정한다.
EntityDataKey<Byte> FLAGS = EntityDataKeys.Entity.SHARED_FLAGS;
EntityDataKey<Text> TEXT = EntityDataKeys.TextDisplay.TEXT;
EntityDataKey<Vector3f> SCALE = EntityDataKeys.Display.SCALE;
EntityDataKey<ItemStack> ITEM = EntityDataKeys.ItemDisplay.ITEM;
EntityDataKey<Optional<BlockData>> CARRIED = EntityDataKeys.EnderMan.CARRY_STATE;

player.sendPacket(EntityDataPacket.builder(id)
        .set(EntityDataKeys.Entity.SHARED_FLAGS, EntityFlags.of(EntityFlag.GLOWING))   // 비트도 이름으로
        .set(EntityDataKeys.TextDisplay.TEXT, Text.text("상점"))
        .set(EntityDataKeys.Display.SCALE, new Vector3f(2, 2, 2))
        .build());

// 가로챈 패킷에서 이름으로 읽고 바꾸기(이 사람에게만 발광)
packets.listen(this, PacketTypes.Clientbound.SET_ENTITY_DATA, e -> {
    e.packet().get(EntityDataKeys.Entity.SHARED_FLAGS).ifPresent(f ->
        e.setPacket(e.packet().with(EntityDataKeys.Entity.SHARED_FLAGS, (byte) (f | 0x40))));
});
virtualEntity.set(EntityDataKeys.Display.BILLBOARD, Billboard.CENTER);
```

- `EntityDataKey<T>`: 주인 엔티티 부류(`"Display"`, Mojang 클래스 이름) + 칸 이름(`"scale"`) + 값 종류 `EntityDataType<T>`. 같은 키는 같은 객체.
- 값 종류 31개 전부를 API 값으로: `Byte·Integer·Long·Float·String·Text·Optional<Text>·ItemStack·Boolean·Rotations(x,y,z)·BlockPos·Optional<BlockPos>·BlockFace·Optional<UUID>·BlockData·Optional<BlockData>·NbtCompound·Particle·List<Particle>·VillagerData·OptionalInt·Pose·CatVariant… 같은 레지스트리 값은 이름 문자열·Vector3f·Quaternionf`. 레지스트리 번호(고양이·개구리·늑대·그림 종류)는 이름("minecraft:tabby")으로 받는다.
- 서버: 키 → (칸 번호, 직렬화 번호) 표는 **서버 엔티티 클래스의 `entityData.define(...)` 상수에서 나온다**(89개 클래스·199개 정의, 바닐라와 대조된 값). 시험이 "API 키마다 그 부류의 서버 엔티티를 만들어 같은 칸·같은 직렬화로 정의돼 있는지"를 전부 확인하므로 표가 틀릴 수 없다.
- 적용 검사: 키 부류가 그 엔티티 종류에 없으면(예: `TextDisplay.TEXT` 를 좀비에) 부른 자리에서 `IllegalArgumentException`. 가로챈 패킷은 엔티티 종류를 모를 수 있으므로 읽기는 "칸·직렬화가 맞으면" 돌려준다.
- 숫자 칸 API(`entry(int, Value)`)는 남긴다(낮은 단, 문서에 "버전마다 바뀜"). 지금 `Unsupported` 로 멈추던 패킷은 전부 읽히고 다시 보낼 수 있게 된다.

### 4.2 모든 play 패킷을 읽고 쓰기 (G2) — 스키마 기반

손으로 145종을 감싸지 않는다. **서버 쪽 중립 스키마**(패킷마다 필드 이름·값 종류 목록, 손으로 쓴 표 — 바닐라 jar 대조는 커밋하지 않는 로컬 도구)를 하나 두고 두 곳이 쓴다:

1. Furnace API: `OpaquePacket` 자리에 **`GenericPacket`**(종류 + 이름 붙은 필드). 모든 종류를 읽기·바꾸기·보내기.
   ```java
   packets.listen(this, PacketTypes.Clientbound.LEVEL_PARTICLES, e -> {
       GenericPacket p = e.packet();
       if (p.get(Fields.PARTICLE).type().equals("minecraft:explosion")) e.setCancelled(true);
   });
   player.sendPacket(GenericPacket.of(PacketTypes.Clientbound.SET_CAMERA).with("camera_id", npcId));
   ```
   필드 이름은 Mojang 이름(`camera_id`), 값은 4.1 과 같은 버전 중립 값. 필드 키 상수(`PacketField<T>`)를 종류마다 생성해 타입 안전하게 쓰고, 문자열 이름 판도 둔다.
2. NMS 호환 어댑터(별도 스레드 "Add full NMS packet schema set"): 같은 스키마에서 `net.minecraft...Packet` shim 을 빌드 때 만들고, 본문 직렬화는 이 범용 코덱에 맡긴다.

자주 쓰는 종류는 스키마 위에 **타입 레코드**를 더 얹는다(편의·문서용, 내용은 같은 스키마): `set_equipment`, `level_particles`, `move_entity_pos/_rot/_pos_rot`, `section_blocks_update`, `set_passengers`, `set_player_team`, `open_screen`·`container_set_content`·`container_set_slot`, `open_sign_editor`, `block_destruction`, `game_event`, `entity_event`, `update_attributes`, `boss_event`, `tab_list`, `map_item_data`, `set_camera`, `set_border_*`. 레코드가 생기면 그 종류의 `PacketType` 값 클래스가 레코드로 바뀐다 — API 호환을 위해 **`GenericPacket` 으로도 늘 볼 수 있게** `PacketEvent.generic()` 을 둔다.

버전 안정: 스키마는 버전마다 하나(`Schema767`), API 필드 이름은 바뀌지 않게 유지하고 바닐라가 필드를 없애면 그 필드는 `isSupported(field)` false.

> 합의(2026-11-12): 스키마는 하나만 둔다. 손으로 쓴 스키마 파일 `furnace-api/src/main/resources/org/domi/furnace/api/packet/schema/protocol-767.schema` 와 파서(`api.packet.schema.PacketSchema`)는 NMS 패킷 스키마 스레드(브랜치 claude/project-thread-qcp8th)가 만들고, 그 위의 서버 범용 코덱(스키마 → 바이트)과 공개 `GenericPacket` 은 이 계획의 E4 가 맡는다. 스키마 필드 이름은 Mojang camelCase(shim 용) — 공개 API 필드 키는 snake_case 로 기계 변환한다. 각도(byte)·속도(i16) 단위 변환은 API 의미 층에서 한다. `particle`·`entity_data` 는 스키마에서 custom 이고 E1 의 `EntityData767` 코덱을 쓴다.

### 4.3 번들 보내기 (G3)

```java
packets.sendBundle(player, List.of(addEntity, entityData, equipment));   // 바닐라 bundle_delimiter 로 감싼다 — 한 프레임에 그려진다
```
가상 엔티티와 엔티티 변장도 생성 묶음을 번들로 보낸다.
바닐라 상한(번들 안 4096개)과 같은 검사. 리스너는 번들 안 패킷을 하나씩 본다.

### 4.4 패킷 시험 도구 (G4)

`furnace-test` 의 `TestPlayer` 에 `received(PacketType)`(그 종류로 받은 패킷 목록, 타입 레코드·`GenericPacket`), `awaitPacket(type, predicate, timeout)`, `clearReceived()`. 플러그인 개발자가 JUnit 으로 "이 명령을 치면 이 사람에게 발광 메타데이터가 간다"를 확인할 수 있게.

### 4.5 고수준으로 올릴 것 (G5·G6)

- **엔티티 속성**: `LivingEntity.attribute(Attribute.MOVEMENT_SPEED)` → `AttributeHandle`(`baseValue`, `value`, `modifiers`, `addModifier`, `removeModifier`). 속성 키는 이름 상수(`"minecraft:generic.movement_speed"`), 1.21.2 이후 이름 변경은 서버가 별칭으로 받는다. 아이템 쪽 `AttributeModifiers` 의 수정자 레코드를 재사용.
- **빠른 블록 쓰기**: `World.editBlocks(BlockRegion, BlockEditOptions, edit -> edit.set(pos, data))` — 소유 region 별로 나눠 돌고(여러 region 이면 각 소유 스레드), 옵션: 이웃 갱신·물리 끔(`updateNeighbors(false)`), 빛 재계산은 끝에 한 번, 바뀐 청크는 섹션 묶음 패킷 한 번. 결과는 바뀐 칸 수 Future. 바닐라 `Level.setBlock` 플래그(1 이웃 갱신, 2 클라이언트 알림, 16 모양 갱신 건너뛰기)에 있는 뜻만 연다 — 새 동작을 지어내지 않는다.

### 4.6 내부 단 다듬기 (G7)

- **개발용 묶음**: `:furnace-server:devBundle` — API + 서버 클래스 + 소스 jar 를 한 묶음으로(`compileOnly` 하나). 배포 위치(Maven 저장소)는 결정 항목(7장).
- **내부 사용 보고**: 플러그인 로드 때 `org.domi.furnace.server.*` 참조를 훑어(H7 `NmsCompatReport` 와 같은 방식, 바이트코드 상수 풀) "이 플러그인은 내부 클래스 N개를 쓴다"를 한 줄 로그·`/furnace plugins internals` 로 보인다. 서버 버전을 올릴 때 다시 확인할 목록이 된다.
- `@Internal.Stable`/`@Internal.Unstable` 주석: 내부 클래스 중 자주 쓰여 이름을 지키기로 한 것(예: `Mob#goalSelector`)만 Stable 로 표시하고 문서에 모은다.

### 4.7 로그인·구성 단계 (G8) — 뒤로

`PacketPhase.CONFIGURATION` 종류(플러그인 메시지·쿠키·리소스 팩·알려진 팩). 수요가 확인되면(모드 서버 핸드셰이크) 연다. 지금은 문서에 "play 만"을 유지.

### 4.8 옮겨 오기 안내 (G9)

`docs/api/from-protocollib-nms.md`: ProtocolLib(`PacketContainer`·`StructureModifier`·`WrappedDataWatcher`·`PacketAdapter`) → Furnace, NMS 흔한 쓰임(`EntityPlayer.connection.send`, `DataWatcher`, `PathfinderGoal`, `AttributeInstance`, `ChunkSection.setType`) → Furnace 표. 단마다 버전 약속을 한 줄로.

## 5. 서버 구조 (요약)

- API: `org.domi.furnace.api.packet`(+ `packet.data` 메타데이터 키·값 종류, `packet.field` 범용 필드), 값 타입 `Vector3f`·`Quaternionf`·`Particle`·`Rotations` 는 `api.math`·`api.particle` 새 패키지.
- 서버: `plugin/packet/Protocol767`(타입 레코드 코덱), `EntityDataTable767`(키 → 칸·직렬화, 서버 엔티티 정의에서), `Schema767`(4.2, 공유), `GenericCodec`(스키마로 읽고 쓰기).
- 새 버전 지원: `Protocol7xx`·`EntityDataTable7xx`·`Schema7xx` 를 더 만들고 고른다. API 는 그대로.

## 6. 순서 — 작은 PR 로

| PR | 내용 | 크기 | 기대 |
|---|---|---|---|
| E1 ✓ | 4.1 타입 있는 메타데이터: `EntityDataKey`·`EntityDataType` 31종·`EntityDataKeys`(서버 정의 전부)·`EntityFlags`, 패킷 레코드 `get/with`, `VirtualEntity.set`, 서버 표·시험 | 중 | G1 해소, 디스플레이 엔티티 홀로그램 가능 |
| E2 ✓ | 4.3 번들(`PacketService.sendBundle`) + 가상 엔티티·플레이어(변장 포함) 생성 묶음 번들로 | 소 | 깜박임 제거 |
| E3 ✓ | 4.4 `TestPlayer.received/awaitPacket/clearReceived/flushReceived` | 소 | 패킷 단위 시험 |
| E4 (E4a ✓, E4b-1 ✓, E4b-2 ✓, E4c-1 ✓, E4c-1b ✓, E4c-2 ✓) | 4.2 범용 스키마·`GenericPacket`(NMS 스키마 스레드와 합의한 쪽이 서버 스키마를 먼저) | 대 → E4a: 범용 코덱·`GenericPacket`, 필드 나열 종류 102개(2026-11-12). E4b-1: 손 코덱 18종·number_format·블록 엔티티 종류 → 122개. E4b-2: 보스바·팀·속성·지도·통계·채팅 값 등 11종 → 133개. 남은 12개(입자·청크·빛·명령 트리·레시피·발전 과제·태그·서명된 채팅·상인 거래·번들 구분자)는 E5 타입 레코드에서. E4c-1: NMS 생성 패킷 shim 의 `connection.send` 를 이 코덱에 이음(필드 나열 65종, 묶음은 번들 하나로). E4c-1b: 검토 R2~R8 — 필드 길이 상한, cookie·ping 단계(67종), 직접 정의 소리, SoundEvents·MobEffects shim. E4c-2: 손 코덱 종류 대응 표 15종(보스바·통계는 shim 이 비어 거절)·속성 수정자·지도·숫자 형식 값 shim(set_score 도 → 필드 나열 68종), E4c-3: 타입 레코드 종류 | 179종 전부 읽고 쓰기 |
| E5 (12종 범용 읽기 ✓) | 4.2 자주 쓰는 타입 레코드 1차(장비·입자·상대 이동·여러 칸·탑승·팀) | 중 → 먼저 남은 12종(입자·폭발·청크·빛·명령 트리·서명 채팅·레시피 둘·발전 과제·태그·상인 거래·번들 구분자)을 손 코덱(`GenericComplex767`)으로 → 범용 종류 145개 전부 읽고 씀, 바닐라 jar 132 프레임 같은 바이트(2026-11-12). 타입 레코드(장비·입자 등)는 다음 | 범용 종류 전부 이름 붙은 필드로 |
| E6 ✓ | 4.5 엔티티 속성 API: `api.entity.attribute`(`Attribute` 29종·`AttributeModifier`·`AttributeHandle`·`AttributeInstance`), `LivingEntity#attribute/attributes/registerAttribute`, `LivingEntityState#attribute`, transient 수정자, `AttributeMap#snapshot` — `docs/api/entity-attributes.md` | 중 | G5 해소 |
| E7 ✓ | 4.5 빠른 블록 쓰기 — `World.editBlocks(options, edit)`·`editBlocksNow`, 옵션은 바닐라 플래그 1·2·16 과 `/fill` 방식(놓은 뒤 이웃 갱신·`Clearable.tryClear`)만, region 별 동시 적용·틱 나누기·청크째 재전송. 계획의 `BlockRegion` 인자는 빼고 쓰기 목록이 범위를 정한다. [fast-block-edits.md](fast-block-edits.md) | 중~대 | 52만 칸: World.setBlock 칸마다 약 2 s → DEFAULT 약 0.4 s, FAST 약 0.12 s·패킷 압축 뒤 약 4 KB |
| E8 | 4.6 개발용 묶음·내부 사용 보고 | 소~중 | |
| E9 | 4.8 옮겨 오기 안내 문서(E1~E7 반영하며 늘림) | 소 | |

각 PR 은 독립 검토 스레드를 거쳐 CI `test` 통과·충돌 없음이면 병합한다.

## 7. 결정이 필요한 것

`docs/user-todo/entries/2026-11-12-easy-nms-packets.md`:

1. **개발용 묶음(E8)을 어디에 올릴까** — 추천: 지금은 CI 산출물·릴리스 첨부 jar 로만, 공개 Maven 저장소(GitHub Packages 등)는 첫 공개 릴리스 때.
2. **원시 바이트 패킷 단(프로토콜 번호 + 바이트)을 열까** — 추천: 열지 않는다. 범용 스키마(E4)가 모든 종류를 이름으로 덮으므로 바이트 단은 버전마다 깨지는 길만 하나 더 늘린다.

## 8. 하지 않는 것

- 서버 내부 패킷 레코드(`net.protocol.play.*`)를 API 에 그대로 내보내기 — D2 와 어긋난다.
- 특정 플러그인(예: 특정 홀로그램 플러그인) 맞춤 API — 범용 단만 만든다.
- 바닐라와 관측 결과가 다른 "빠른" 동작 기본값 — 빠른 블록 쓰기는 옵션으로만, 기본은 바닐라 `setBlock` 과 같다.
