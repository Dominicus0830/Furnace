# ProtocolLib 5.4.0 실측 점검과 NMS 구멍 목록 (2026-11-14)

도미니코 지시(11-14): "nms 은 다 지원되어야 해. 기존 페이퍼 플러그인 웬만해선 싹 다 돌아가야 하니깐". 도미니코가 올린 **실제 ProtocolLib 5.4.0 jar**(10MB, byte-buddy 재배치판, `paperweight-mappings-namespace: mojang`)와 기동 로그로 무엇이 되고 무엇이 안 되는지 실측했다. 기준 커밋 `e19110ca6`(#359 병합 뒤).

## 1. 방법

- 헤드리스 `FurnaceTestServer` 에 진짜 ProtocolLib jar + 시험 플러그인(Probe)을 올린다. Probe 는 켜진 뒤 ProtocolLib 공개 API 를 하나씩 부르고 결과를 `PROBE R|이름|OK/FAIL|…` 한 줄로 남긴다. 시험 플레이어(`TestPlayer`)가 받은 패킷도 기록해 "보냈다"가 아니라 **클라이언트에 닿았다**를 확인한다.
  - 정적 훑기: `MinecraftReflection`·`EnumWrappers`·`BukkitConverters` 의 인자 없는 공개 정적 메서드 전부.
  - 패킷 훑기: ProtocolLib 이 아는 모든 `PacketType` 에 `createPacket` → 값 읽기 → `deepClone`.
  - 값 래퍼: 채팅(텍스트·JSON·레거시·Adventure), `WrappedBlockData`, `WrappedDataWatcher`/`WrappedDataValue`, `NbtFactory`, 입자, 소리, 속성, 주민 데이터, `WrappedGameProfile`, `WrappedServerPing`.
  - 플레이어: 핸들·연결·`Connection`·프로토콜 번호·`getEntityFromID`·`getEntityWatcher`·`getEntityTrackers`.
  - 보내기 22종(`sendServerPacket`), `receiveClientPacket` 2종, 모든 지원 종류에 송수신 리스너를 건 뒤 실제 접속·채팅·명령·이동·상태 핑.
- 재현물: 프로젝트 공유 폴더 `furnace/reviews/protocollib-audit/`(`AuditPlibProbeTest.java.txt`, 고치기 전·뒤 로그). jar 는 커밋하지 않는다(GPL 플러그인, 사용자 파일). authlib·DataFixerUpper 는 `libraries.minecraft.net` 이 막힌 환경이라 바닐라 번들 jar 안의 사본을 로컬 캐시에 풀어 썼다.
- 인기 플러그인 사용 빈도: InteractiveChat·NoCheatPlus·Orebfuscator·VentureChat·PacketWrapper·LibsDisguises·Citizens2·HolographicDisplays 소스를 훑어 ProtocolLib 패킷 종류와 NMS 클래스 사용을 셌다.

## 2. 결과 요약 (고친 뒤 기준)

- Probe 항목 61개 중 **OK 45, 실패 16**(고치기 전 OK 42, 실패 19). 실패 16 중 `utility.isPaper` 1건은 Probe 의 잘못(5.4.0 에 없는 메서드)이다. 반대로 `send.team` 은 "보냄"으로 나오지만 ProtocolLib 주입기 안에서 조용히 실패해 클라이언트에 안 닿는다(G7). 그래서 실제 실패는 16건이다.
- 패킷 훑기: 지원 종류 198개 중 만들기·읽기·복제 194개 OK, 복제 실패 4개. 44개는 1.21.2 이후 종류라 ProtocolLib 도 1.21.1 에서 "지원 안 함"이다(Paper 와 같다).
- 정적 훑기: `MinecraftReflection` 109개 중 22개 실패, `EnumWrappers` 28개 중 8개 null, `BukkitConverters` 37개 중 1개.

### 2-1. 되는 것

| 영역 | 확인 내용 |
|---|---|
| 켜기 | 오류 없이 켜짐. 프로토콜 767, `MinecraftVersion` 1.21.1 |
| 채팅 값 | `WrappedChatComponent` 텍스트·JSON·레거시·`fromChatMessage`, Adventure `Component` 변환 |
| 블록·아이템 | `WrappedBlockData`(돌·계단 상태, Bukkit 에서), 아이템 변환기·CraftItemStack 거울, `MinecraftKey`, `BlockPosition`, 엔티티 종류 변환 |
| 메타데이터 값 | `WrappedDataWatcher` 만들기·쓰기(바이트·Optional 채팅·아이템·블록 위치), `WrappedDataValue` |
| 플레이어 | `getHandle`·연결·`Connection`·`getEntityFromID` |
| 보내기 → 클라이언트 도착 | 시스템 채팅, 제목, 액션바, 아머스탠드 스폰, **메타데이터(홀로그램 이름표·자기 발광, 이번에 고침)**, 장비, 순간이동, 블록 변경, **소리(이번에 고침)**, 표지판 편집기, 슬롯, 카메라, 필터 없이 보내기, 방송 |
| 나가는 리스너 | 접속 때 Furnace 가 보낸 패킷 중 되돌릴 수 있는 종류(능력치·청크 묶음 시작/끝·엔티티 상태·경험치·**메타데이터(이번에 고침)** …)에 리스너가 불림 |
| `receiveClientPacket` | 리스너가 불림(명령·팔 휘두르기). 서버가 실제로 처리했는지는 이 Probe 로는 확인 못 함 |
| NBT | **`NbtFactory.ofCompound`/`ofList`·값 넣기·핸들(이번에 고침)** |

### 2-2. 진짜 구멍 (Paper 1.21.1 에서는 되는 것)

| # | 증상 | 원인 | 쓰는 플러그인(예) |
|---|---|---|---|
| G1 | 실제 클라이언트가 보낸 패킷(채팅·명령·이동·블록 파기·설치·상호작용)이 **ProtocolLib 수신 리스너에 하나도 안 보임** | 다리 2b-3(들어오는 패킷) 미구현 | NoCheatPlus(BLOCK_DIG·ENTITY_ACTION·BLOCK_PLACE·PONG·이동), InteractiveChat(CHAT·CHAT_COMMAND), 모든 반치트·채팅 필터 |
| G2 | `PLAYER_INFO` 를 만들어 보내면 `ClassCastException: …ClientboundPlayerInfoUpdatePacket$Entry not an enum` | ProtocolLib 은 동작 enum 을 `PLAYER_INFO.getPacketClass().getClasses()[1]` 로 찾는다. 바닐라 1.21.1 jar(`aea`)의 InnerClasses 순서는 `Entry`(`aea$b`) → `Action`(`aea$a`)인데 shim 은 javac 가 `Action` → `Entry` 로 적어 `Entry` 를 enum 으로 잡는다. 게다가 `Entry.profile` 이 `Object`(GameProfile 지움). 서버가 보내는 `player_info_update` 는 되돌리기 미구현 | 탭 목록·NPC·변장(LibsDisguises, Citizens) |
| G3 | `WrappedGameProfile.fromPlayer`, `WrappedServerPing` 정적 초기화 실패 | NMS `Player` 에 `GameProfile` 필드가 없음(shimgen 이 authlib 타입을 지움) | 서버 목록 MOTD·핑 플러그인, 스킨·NPC |
| G4 | 서버가 보내는 26종이 리스너에 안 보임: **`level_chunk_with_light`(MAP_CHUNK)**·`light_update`·`player_chat`·`disguised_chat`·`delete_chat`·`player_info_update`·`set_player_team`·`set_objective`·`boss_event`·`commands`·`custom_payload`·`open_screen`·`merchant_offers`·`map_item_data`·`level_particles`·`explode`·`update_attributes`·`block_entity_data`·`login`·`respawn`·`recipe`·`update_recipes`·`update_tags`·`update_advancements`·`award_stats`·`server_links` | 타입 레코드 → shim 되돌리기 미구현(기동 로그에 한 줄로 이미 알림) | Orebfuscator(MAP_CHUNK·MULTI_BLOCK_CHANGE), InteractiveChat(PLAYER_CHAT), 보스바·스코어보드 플러그인 |
| G5 | 핸드셰이크·상태(핑)·로그인 단계 패킷이 리스너에 안 보임 | 다리가 play 단계만 다룸(2b 설계 6장) | 서버 목록 플러그인, 로그인 보호 |
| G6 | `ENTITY_DESTROY` 쓰기 `FieldAccessException: Field index 0 is out of bounds` | shimgen 이 fastutil `IntList` 필드를 지움 | NoCheatPlus, 홀로그램, NPC |
| G7 | `SCOREBOARD_TEAM` 을 만들어 보내면 주입기 안에서 `Can not get final …Parameters.displayName on java.lang.Object` | 빈 버퍼로 만든 패킷의 `Optional<Parameters>` 안에 `Parameters` 가 아닌 객체. `Team$CollisionRule`·`Team$Visibility` 클래스도 없음 | 이름표 색·숨기기 플러그인 |
| G8 | 입자(`WrappedParticle`)·`WORLD_PARTICLES` 보내기 실패 | `ParticleType`/`ParticleOptions` shim 이 빈 껍데기 | 효과 플러그인 |
| G9 | `WrappedAttribute` 빌더 NPE(`registry is null`) | 속성 레지스트리 shim 없음. `update_attributes` 되돌리기도 없음 | 속성 조작 |
| G10 | `WrappedVillagerData` 초기화 실패 | `VillagerType`·`VillagerProfession` 클래스 없음(메타데이터 villager_data·변형 종류도 함께) | 주민 변장 |
| G11 | `getEntityWatcher` 실패, `getEntityTrackers` 실패 | NMS `Entity` 에 `SynchedEntityData` 필드 없음, `ChunkMap`·`ChunkMap$TrackedEntity` 없음 | NoCheatPlus, 변장 |
| G12 | NBT 바이너리·문자열 직렬화(`NbtBinarySerializer`·`NbtTextSerializer`) 실패 | `NbtAccounter`·`NbtIo` shim 없음 | 아이템 NBT 저장 플러그인 |
| G13 | `deepClone` 실패 4종: `boss_event`·`interact`·`update_advancements`·`status_response` | 해당 shim 의 코덱 경로 | 패킷 복사하는 리스너 |
| G14 | `MinecraftReflection` 이 못 찾는 1.21.1 클래스: `Stats`, `ServerStatus$Version`/`$Players`, `GsonHelper`, `BlockEntity`, `PlayerList`(모양 검색), `NbtOps`, `Crypt` 등 | shim 없음 | 각 기능 쓸 때만 |

### 2-3. 무해한 실패 (Paper 1.21.1 에서도 똑같이 실패·null)

- `WorldType`, `IntHashMap`, `ChatComponentText`, `Style$Serializer`, `MultiBlockChangeInfo`, `ProfilePublicKey` — 1.21.1 바닐라에 없는 옛 클래스.
- `EnumWrappers` 의 Particle·Title·WorldBorder·Combat·ScoreboardAction·ChatType 이 null — 1.21.1 에서 사라진 enum.
- `GameProtocols$Context` 등 1.21.2 이후 클래스, 1.21.2 이후 `PacketType` 44종.
- `getParticleClass`·`WorldTypeConverter` null.
- `NbtFactory.fromItemTag` 실패 — 1.20.5 부터 `ItemStack` 에 `CompoundTag` 필드가 없다(Paper 도 같음, **가정**: Paper 실서버로 확인하지 않았다).
- 도미니코 기동 로그의 ProtocolLib "NMS 클래스를 찾았으나 없다" 경고 21줄은 모두 **별명 시도**다. ProtocolLib `getMinecraftClass(별명…)` 는 Spigot 이름 → Mojang 이름 → 옛 이름 순으로 찾아서, 앞 이름이 없어도 뒤 이름으로 찾는다. `GameProtocols$a`/`$Context`(1.21.5 이후)만 끝까지 없고, 이것도 Paper 1.21.1 에서 없다. Probe 처럼 여러 기능을 건드리면 같은 종류의 경고가 339줄까지 늘어난다(→ 결정 항목 D1).

## 3. 이 PR 에서 고친 것

| 구멍 | 고친 내용 | 시험 |
|---|---|---|
| 메타데이터(set_entity_data) 두 방향 | `EntityDataBridge`: shim `EntityDataSerializers` 상수 이름 ↔ Furnace `EntityDataType` 이름으로 잇고 값마다 바꾼다. 21종(byte·int·long·float·string·boolean·optional_uuid·optional_unsigned_int·component·optional_component·item_stack·rotations·block_pos·optional_block_pos·direction·block_state·optional_block_state·compound_tag·pose·sniffer_state·armadillo_state). 못 바꾸는 종류(입자·주민·변형·optional_global_pos·vector3·quaternion)가 하나라도 있으면 **패킷 전체를 거절**한다 — 일부 칸만 버리면 엔티티 모양이 Paper 와 달라진다 | `EntityDataBridgeTest` 3개(모든 종류 왕복, 못 하는 종류 전체 거절, NMS 로 보낸 홀로그램이 클라이언트에 그대로 도착), `SchemaPacketReverseTest` 표 갱신 |
| NBT `TagTypes` | `TagType`·`TagTypes` shim, 13개 태그 클래스에 바닐라처럼 **클래스 안의 익명 `TYPE`**(ProtocolLib 이 `getType(n).getClass().getEnclosingClass()` 로 태그 클래스를 찾는다). 이름·표시 이름·`isValue` 는 바닐라 1.21.1 jar 문자열 상수와 대조, 범위 밖 번호는 `INVALID[n]`/`UNKNOWN_n` | `NbtTypesAndSoundShimTest` |
| `CraftSound` | Paper 1.21.1 과 같은 정적 메서드 넷(`minecraftToBukkit`·`bukkitToMinecraft`·`bukkitToMinecraftHolder`·`minecraftHolderToBukkit`). 레지스트리에 없는 소리는 Paper 처럼 `NoSuchElementException`(ProtocolLib 이 잡아 null 로 바꾼다) | `NbtTypesAndSoundShimTest`(모든 `Sound` 왕복 포함) |

재실측: `nbt.compound`·`sound.converter`·`send.sound` 가 실패 → OK, 메타데이터 보내기 두 건이 클라이언트에 실제 값으로 도착(`ByteValue 32`·`OptionalTextValue "Holo"`·`ByteValue 64`), 접속 때 Furnace 가 보낸 메타데이터에 나가는 리스너가 불림.

## 4. 다른 인기 플러그인의 1.21.1 NMS 모듈 (클래스 단위)

1.21.1 전용 모듈의 `import net.minecraft…`·`org.bukkit.craftbukkit…` 를 지금 shim 목록과 비교했다(멤버 단위는 아님 — 실제 기동 때 `NmsCompatReport` 가 멤버까지 센다).

| 플러그인(모듈) | 이름 체계 | 클래스 | 없는 것 |
|---|---|---|---|
| InteractiveChat `V1_21_1` | Spigot(대응표로 Mojang 이름으로 바꿔 비교) | 67 | 18: 발전 과제 6종(`Advancement`·`DisplayInfo`·`AdvancementType`·`AdvancementRequirements`·`AdvancementRewards`·`Criterion`, `ImpossibleTrigger`), `DataComponentPatch`, `NbtIo`, `ChatDecorator`·`ComponentSerialization`·`PlayerChatMessage`, `Equipable`·`PlayerHeadItem`, `CraftRegistry`, 지도 `CraftMapView`·`CraftMapCursor`·`RenderData` |
| LibsDisguises `v1_21_R1` | Mojang | 35 | 16: `ChunkMap`·`ServerEntity`, `EntityDimensions`·`HumanoidArm`, `NbtOps`, `RegistryLayer`, `FeatureFlagSet`, 점수판 `Objective`·`ScoreHolder`·`CraftScoreboard`, `CraftArt`·`CraftCat`·`CraftFrog`·`CraftWolf`, `CraftRegistry`·`CraftNamespacedKey` |
| Orebfuscator `v1_21_R1` | Mojang | 24 | 4: `EmptyBlockGetter`, `BlockEntity`, `RegionFile`·`RegionFileVersion` |
| FAWE 2.16.2(도미니코 기동 로그) | Mojang | 566 심볼 | shim 312, 등급 C 65, 없음 189 — N2b 묶음에서 진행 중 |

Citizens2(현재 소스는 1.21.1 모듈 없음)·HolographicDisplays(1.21 모듈 없음)·Essentials·AuthMe·TitleManager 는 1.21.1 에서 NMS 를 직접 쓰지 않거나 ProtocolLib·Bukkit API 로만 간다.

**같은 로그에서 본 Bukkit API 구멍(NMS 아님)**: FAWE 가 접속·손 바꾸기·상호작용마다 `NoSuchMethodError: Material.isLegacy()`·`Player.isSneaking()`, `NoClassDefFoundError: org.bukkit.Effect` 를 낸다. Bukkit 어댑터에 세 가지가 없다(T-141 API 확장 물결 대상). LuckPerms 는 종료 때 H2 의 JVM 종료 훅(`OnExitDatabaseCloser`)에서 `NoClassDefFoundError: org/h2/api/ErrorCode` — 플러그인 클래스 로더가 닫힌 뒤 훅이 돈 것으로 보이며 원인은 **가정**이다.

## 5. 우선순위 (TASK 는 `docs/roadmap/optimization-tasks.md` 10장)

- **P0**: G1 들어오는 패킷(2b-3) — 반치트·채팅 플러그인 전부가 기댄다. G2+G3 PLAYER_INFO 두 방향 + GameProfile. G4 중 `level_chunk_with_light`·`light_update`(Orebfuscator).
- **P1**: G5 로그인·상태 단계 + `WrappedServerPing`. G6 fastutil·authlib 타입 살리기(shimgen). G7 팀. G8 입자. G9·G10 속성·주민. G11 엔티티 감시자·추적기. G4 의 채팅·보스바·점수판·명령 등 나머지. G13 복제. 다른 플러그인 모듈의 없는 클래스(발전 과제·지도·점수판·`ChunkMap`).
- **P2**: G12 NBT 입출력, G14 나머지 클래스, 별명 시도 경고 정리(D1), FAWE 가 부른 Bukkit API 3종(T-141 로).
