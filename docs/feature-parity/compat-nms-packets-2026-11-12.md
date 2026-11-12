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
- 생성자: 모든 필드 생성자는 바닐라에 같은 public 생성자가 있을 때만 public(아니면 private) — 그래서 바닐라에 없는 서술자로 플러그인이 링크되지 않는다.
- 필드 타입 중 호환 계층에 없는 net.minecraft 타입(파일 60여 개: SoundEvent·MobEffect·EntityType·ParticleOptions·ChatType.Bound 등)은 빈 shim 을 만든다. 멤버를 부르면 기존 덫(UnsupportedNmsLink)이 그 자리에서 잡는다.
- Mojang 라이브러리 타입(authlib GameProfile·DFU Pair·fastutil IntList/Int2ObjectMap/Object2IntMap)은 컴파일 경로에 없어 `Object` 로 지웠다 → 그 타입을 받는 생성자(예: `ServerboundContainerClickPacket` 의 모든 필드 생성자)는 서술자가 바닐라와 달라 호출 위치에서 덫에 걸린다. N-lib 가 이 라이브러리를 컴파일 경로에 올리면 스키마의 `type lib` 줄만으로 원래 타입이 된다(생성기 수정 필요 — 지금은 늘 지운다).
- 모든 생성 패킷은 `SchemaPacket`(`schemaKey()`·`schemaValues()`)을 구현한다 — 범용 코덱이 붙으면 보내기 다리가 이것만 본다.

## 아직 안 되는 것(정직하게)
- **보내기**: 손으로 쓴 shim 11종(+묶음 패킷)만 실제로 나간다. 생성 패킷을 `connection.send` 하면 `UnsupportedNmsCall`("모양은 있지만 보내기 변환이 아직 없다") — 범용 코덱(E4, 다른 스레드)이 들어오면 `SchemaPacket` 으로 잇는다.
- **받기**: 플러그인 Netty 처리기에 serverbound 패킷 객체를 넘기는 파이프라인 shim(N3a)은 이번 범위 밖. 지금 효과는 "클래스가 있어서 플러그인 클래스가 죽지 않고, instanceof·필드 리플렉션·생성이 된다"까지.
- `ClientboundBundlePacket` 보내기는 안의 패킷을 묶음 경계 없이 차례로 보낸다(클라이언트가 한 프레임에 모아 처리하는 것까지는 같지 않다).
- `Entity` 를 받는 바닐라 생성자는 있으나 `Entity#getId` 가 네트워크 id 를 내보내지 않아 그 자리에서 `UnsupportedNmsCall`(H7 의 기존 방침).
- 접근자 이름(get=)은 난독화돼 jar 로 확인할 수 없다 — Mojang 이름 기억 기준(가정). 필드 이름도 같다.
- 손으로 쓴 `ClientboundRemoveEntitiesPacket` 은 fastutil 이 없어 `int[]` 로 담는다(스키마는 `IntList`).
- 바꾼 것: 손으로 쓴 `ClientboundSetTitleText/SetSubtitleText/SetActionBarTextPacket` 을 바닐라처럼 record 로(접근자 `text()`; 바닐라에 없던 `getText()` 는 뺐다). 처리자 표지 `ClientCommonPacketListener`·`ClientGamePacketListener` 는 생성기가 1.21.1 계층(쿠키·퐁 처리자 포함)으로 만든다.

## 시험
- `furnace-api` `PacketSchemaTest`: 202종 읽기, play 패킷 목록(`PacketTypes`)과 양방향 맞물림, 이름·타입·와이어 풀이, 문법 오류 줄 번호.
- `furnace-compat-paper` `PacketShimSchemaTest`: 202종 모두 클래스·덫 색인 있음, 필드 이름·순서·소거 타입이 스키마와 같음(record 는 구성 요소), enum 상수, 보조 타입 접근 수준, 덧붙임 변환(각도·속도·소리 좌표·이동 패킷 플래그).
