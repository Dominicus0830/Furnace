# 범용 NMS 어댑터 N3 — 패킷 클래스 전체(중립 스키마 → 빌드 때 shim 생성) — 2026-11-12

도미니코 결정(2026-11-12): 패킷 shim 은 40종이 아니라 **스키마 전부**. 기존 Paper 플러그인이 패킷 클래스가 없어 클래스째 죽는 문제를 없애는 것이 목적.
계획 `compat-nms-adapter-plan-2026-11-11.md` 5장 3의 안전안(손으로 쓴 중립 스키마 커밋, shim 은 빌드 때 생성, 바닐라 대조는 로컬 도구)을 따랐다.

## 무엇이 들어갔나

| 조각 | 위치 | 내용 |
|---|---|---|
| 중립 스키마 | `furnace-api/src/main/resources/org/domi/furnace/api/packet/schema/protocol-767.schema` | 1.21.1 패킷 **202종 전부**(핸드셰이크 1·상태 2·핑 2·쿠키 2·로그인 9·설정 7·공통 16·게임 163). Mojang 클래스·필드 이름, 필드 타입(바닐라 선언 순서), record/class/singleton, 내포 타입·enum 상수, 와이어 힌트, custom 표지. 바닐라 코드 없음 |
| 파서 | `org.domi.furnace.api.packet.schema`(`PacketSchema.protocol767()`) | 서버 범용 코덱(별도 스레드 "Design easy NMS and packet API")과 shim 생성기가 같이 쓴다 |
| 생성기 | `furnace-compat-paper/src/shimgen` + Gradle `generateNmsPacketShims` | 스키마 → `build/generated/sources/nms-packets` 의 Java 262개(패킷·내포 타입·없는 net.minecraft 필드 타입 빈 shim·처리자 인터페이스). 커밋하지 않는다. 손으로 쓴 shim 이 `src/main/java` 에 있으면 그 클래스는 만들지 않는다 |
| 덧붙임 | `src/shimgen/resources/packet-shim-extras.txt` | 필드 나열로 안 떨어지는 바닐라 편의 생성자·변환 접근자·상수(엔티티 생성자, 각도·속도 변환, GameEvent 종류 상수, 이동 패킷 하위 클래스 등) |
| 대조 도구 | `tools/compat/packet-schema-check/`(로컬) | 바닐라 jar 와 모양 대조. 2026-11-12: 202종·이름 짝 306개 일치 |

## 생성된 shim 의 모양
- 패킷: `net.minecraft.network.protocol.<상태>.<Mojang 이름>`, `Packet<처리자>` 구현, 필드는 바닐라와 같은 이름·타입·순서(`private final`, 이동 패킷 부모는 `protected final`).
  record 는 record(접근자 = 필드 이름), class 는 스키마 `get=` 접근자, singleton 은 `INSTANCE`.
- 생성자: 모든 필드 생성자는 바닐라에 같은 public 생성자가 있을 때만 public(아니면 private) — 그래서 바닐라에 없는 서술자로 플러그인이 링크되지 않는다. 패킷은 `packet` 줄, 보조 class 는 `base`·`class` 줄의 `new` 플래그로 정한다(abstract 부모는 protected).
- 처리자 인터페이스: 1.21.1 계층 그대로 — 핑 처리자는 `PacketListener` 만 잇고, 서버 쪽 처리자(쿠키·핸드셰이크·공통·로그인·상태)는 `game.ServerPacketListener`(Mojang 이름은 가정, 구조는 바이트코드 확인)를 거친다.
- 필드 타입 중 호환 계층에 없는 net.minecraft 타입(파일 60여 개: SoundEvent·MobEffect·EntityType·ParticleOptions·ChatType.Bound 등)은 빈 shim 을 만든다. 멤버를 부르면 기존 덫(UnsupportedNmsLink)이 그 자리에서 잡는다.
- Mojang 라이브러리 타입(authlib GameProfile·DFU Pair·fastutil IntList/Int2ObjectMap/Object2IntMap)은 컴파일 경로에 없어 `Object` 로 지웠다 → 그 타입을 받는 생성자(예: `ServerboundContainerClickPacket` 의 모든 필드 생성자)는 서술자가 바닐라와 달라 호출 위치에서 덫에 걸린다. N-lib 가 이 라이브러리를 컴파일 경로에 올리면 스키마의 `type lib` 줄만으로 원래 타입이 된다(생성기 수정 필요 — 지금은 늘 지운다).
- 모든 생성 패킷은 `SchemaPacket`(`schemaKey()`·`schemaValues()`)을 구현한다 — 범용 코덱이 붙으면 보내기 다리가 이것만 본다.

## 아직 안 되는 것(정직하게)
- **보내기**(2026-11-12 easy-nms E4c-1 갱신): 손으로 쓴 shim 11종은 타입 레코드로, 생성 패킷 중 필드 나열 종류 65종은 `SchemaPacketBridge` 가 `GenericPacket` 으로 바꿔 서버 범용 코덱으로 내보낸다(값: Component → Text, BlockPos → api BlockPos, enum → 상수 이름, Holder·ResourceKey·ResourceLocation → 이름, Item·Block·EntityType → 레지스트리 이름, BlockState → BlockData, 내포 record·class → PacketStruct, nullable → Optional). 아직 안 나가는 생성 패킷: 손 코덱 종류(custom — set_experience·boss_event 등, E4c-2 대응 표), 타입 레코드 종류(add_entity·set_entity_data·teleport_entity 등 9종, E4c-3), 값 shim 이 빈 껍데기인 8종(block_entity_data·delete_chat·disguised_chat·login·open_screen·respawn·server_links·set_score). 이것들은 `connection.send` 에서 `UnsupportedNmsCall`. 필수 필드가 null 이면 `IllegalArgumentException`(바닐라도 쓰다 실패한다). int 필드의 명시 와이어(u8·i8·u16·i16)는 바닐라 writeByte·writeShort 처럼 아래 비트로 자른다(블록 부수기 진행도 -1 → 255 = 지우기, 컨테이너 번호 200 → i8 -56). 검토(PR #259)가 바닐라 1.21.1 jar 로 65종 × 2벌 130 프레임을 대조해 모두 같은 바이트였다. 아직 없는 것: 필드별 길이 상한(resource_pack_push.hash 40자, store_cookie.payload 5120바이트 — 스키마에 상한 표기가 없다), 직접 정의 Holder(SoundEvent 값 shim 이 빈 껍데기라 지금은 만들 수 없다).
  - 로드 보고·`plugins.compat.nms.strict`: 보내기 다리가 없는 clientbound 생성 패킷의 **생성자**를 부르는 jar 는 그 생성자를 "생성 패킷, 보내기 미지원"으로 세어 미지원 수에 넣는다(`NmsCompatReport.sendUnsupported` = SchemaPacket 이고 NmsPacket 도 아니고 `SchemaPacketBridge.supports` 도 아닌 것). 클래스 참조·instanceof·필드 리플렉션은 지원으로 센다.
  - `STREAM_CODEC`·`type()`·`handle()` 은 아직 생성하지 않는다. 1.21.1 에서 public 생성자가 `(Entity)` 뿐인 패킷(Teleport·SetEntityMotion·SectionBlocksUpdate·LevelChunkWithLight 등)을 `STREAM_CODEC.decode` 로 만드는 플러그인 경로는 막혀 있다 — E4 때 스키마 와이어로 함께 생성한다.
- **받기**: 플러그인 Netty 처리기에 serverbound 패킷 객체를 넘기는 파이프라인 shim(N3a)은 이번 범위 밖. 지금 효과는 "클래스가 있어서 플러그인 클래스가 죽지 않고, instanceof·필드 리플렉션·생성이 된다"까지.
- `ClientboundBundlePacket` 보내기는 바닐라처럼 번들 하나(`sendPacketBundle`)로 나간다 — 안의 패킷을 모두 먼저 바꾸고 인코딩해 보고, 하나라도 못 하면 아무것도 보내지 않는다.
- `Entity` 를 받는 바닐라 생성자는 있으나 `Entity#getId` 가 네트워크 id 를 내보내지 않아 그 자리에서 `UnsupportedNmsCall`(H7 의 기존 방침).
- 접근자 이름(get=)은 난독화돼 jar 로 확인할 수 없다 — Mojang 이름 기억 기준(가정). 필드 이름도 같다.
- 손으로 쓴 `ClientboundRemoveEntitiesPacket` 은 fastutil 이 없어 `int[]` 로 담는다(스키마는 `IntList`).
- 바꾼 것: 손으로 쓴 `ClientboundSetTitleText/SetSubtitleText/SetActionBarTextPacket` 을 바닐라처럼 record 로(접근자 `text()`; 바닐라에 없던 `getText()` 는 뺐다). 처리자 표지 `ClientCommonPacketListener`·`ClientGamePacketListener` 는 생성기가 1.21.1 계층(쿠키·퐁 처리자 포함)으로 만든다. 손으로 쓴 `ClientboundDisconnectPacket` 의 `isTerminal()=true` 덮어쓰기는 바닐라 1.21.1 에 없어(바이트코드 확인) 뺐다.
- 독립 검토(PR #237, `furnace/reviews/n3-pr237.md`) 반영: AddEntity 각도를 바닐라 `Mth.floor` 로, 머리 각도는 double 로 계산. 지도 편의 생성자 `ClientboundMapItemDataPacket(MapId, byte, boolean, Collection, MapPatch)`·`ClientboundRespawnPacket#shouldKeep`. `ContainerSetContent` 편의 생성자는 크기 고정 `NonNullList` 에 각 스택·들고 있는 스택을 `copy()`, `Explode` 의 `toBlow` 는 가변 `ArrayList`(바닐라와 같게).

## 시험
- `furnace-api` `PacketSchemaTest`: 202종 읽기, play 패킷 목록(`PacketTypes`)과 양방향 맞물림, 이름·타입·와이어 풀이, 문법 오류 줄 번호.
- `furnace-compat-paper` `PacketShimSchemaTest`: 202종 모두 클래스·덫 색인 있음, 필드 이름·순서·소거 타입이 스키마와 같음(record 는 구성 요소), enum 상수, 보조 타입 접근 수준, 덧붙임 변환(각도·속도·소리 좌표·이동 패킷 플래그).
- `furnace-compat-paper` `PacketShimVanillaBehaviorTest`(9): 각도 포장(Mth.floor·double 머리 각도), 처리자 계층, 로드 보고의 "보내기 미지원" 갈래, 지도 편의 생성자, `shouldKeep`, 보조 타입 생성자 공개 여부, 컨테이너·폭발 목록 복사.
- 로컬 대조 도구 `tools/compat/packet-schema-check`: 두 번째 인자로 shim 클래스 폴더를 주면 처리자 계층(19개 인터페이스)과 shim public 서술자가 바닐라에도 있는지까지 본다.
