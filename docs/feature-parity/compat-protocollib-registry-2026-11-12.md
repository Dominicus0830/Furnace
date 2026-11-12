# ProtocolLib 다리 1단계 — 패킷 등록부 (2026-11-12)

계획 `protocollib-bridge-plan-2026-11-11.md` 의 1단계("패킷 등록부")를 했고, 2단계("연결·파이프라인 모양")는 ProtocolLib 이 켜지는 데 필요한 만큼만(접속 수신 객체) 했다.
결과: **진짜 ProtocolLib 5.4.0 이 `onLoad`·`onEnable` 오류 없이 켜지고**, `/protocol version·dump·listeners` 가 돌며, 다른 플러그인이
`ProtocolLibrary.getProtocolManager()` 로 `PacketType` 을 읽고 `PacketContainer` 를 만들어 필드를 읽고 쓸 수 있다.
**아직 안 되는 것**: 접속한 플레이어의 패킷을 가로채거나(리스너가 안 불린다), `sendServerPacket` 으로 보내는 것 — 3단계(패킷 객체 다리)와 파이프라인 주입이 필요하다.

## 무엇이 들어갔나

| 조각 | 위치 | 내용 |
|---|---|---|
| 번호 표 | `furnace-api/.../packet/schema/protocol-767-ids.txt` + `ProtocolIds` | 1.21.1 프로토콜 등록부의 **번호 223개**(게임 124/58, 구성 17/8, 로그인 6/5, 상태 2/2, 핸드셰이크 1)와 PacketTypes 종류 202개. 바닐라 클래스에서 뽑은 번호·이름뿐(코드 아님). |
| 추출 도구 | `tools/compat/protocol-ids-extract.py`(로컬) | 바닐라 `*PacketTypes` 의 필드 ← 와이어 이름, `*Protocols` 의 등록 순서 = 번호. 압축 푼 server jar 폴더와 `javap` 만 있으면 된다. |
| 등록부 shim(손) | `net.minecraft.network.{ConnectionProtocol, ProtocolInfo(+Unbound), codec.IdDispatchCodec, codec.StreamCodec, codec.StreamEncoder/Decoder}`, `protocol.{PacketFlow, PacketType, BundlerInfo}` | 바닐라 1.21.1 의 이름·모양(상수 순서, 제네릭, `byId` 목록, `Entry(serializer, type)`). 번호 조회 지도는 fastutil 대신 일반 지도. |
| 등록부 shim(생성) | Gradle `generateNmsProtocolShims` → `<상태>PacketTypes`(8개)·`<프로토콜>Protocols`(5개) | 필드 이름은 `CLIENTBOUND_ADD_ENTITY` 꼴(바닐라 규칙), 제네릭 인자 = 스키마의 패킷 클래스. `*Protocols` 는 `ShimProtocols.unbound(...)` 로 번호 순서 틀을 만든다. 구성·로그인·상태·핸드셰이크는 바닐라처럼 묶은(bound) 상수도 둔다. |
| 버퍼 | `net.minecraft.network.FriendlyByteBuf`·`RegistryFriendlyByteBuf` | netty `ByteBuf` 위임(기계 생성, `tools/compat/gen-friendly-bytebuf.py`) + 가변 정수·UTF-8 문자열·UUID·블록 위치·ResourceLocation·enum·배열 읽기/쓰기. 컴포넌트·NBT·아이템은 3단계. |
| 접속 수신 | `MinecraftServer#getConnection()` → `net.minecraft.server.network.ServerConnectionListener`(`channels`: `List<ChannelFuture>`, `connections`), `net.minecraft.network.Connection`(활성 아닌 자리표시 `channel`, `send`·`read`·`disconnect`·`getPacketListener`), `ServerCommonPacketListenerImpl.connection` | ProtocolLib 이 소켓 채널 목록을 갈아 끼우는 자리. **목록은 비어 있고, 연결의 채널은 닫힌 자리표시**다(아래 "왜 비었나"). 접속·재접속·핫 리로드에서 ProtocolLib 이 `PlayerConnection` 의 `Connection` 필드를 찾을 수 있어 오류 없이 "주입 안 됨"으로 건너뛴다. 플러그인이 `getConnection()` 을 부르면 서버 로그에 경고를 한 번 남긴다. |
| 서버 제공 라이브러리 | `commons-lang` 2.6(`org.apache.commons.lang.Validate`) | Paper·Spigot 이 classpath 에 싣는 옛 Bukkit API 의존. ProtocolLib 이 `NoClassDefFoundError` 로 죽던 원인. 다른 Central 라이브러리와 같은 규칙(표지 패키지가 보일 때만, 고정 sha256). `docs/api/plugin-libraries.md`. |

## 확인 (진짜 ProtocolLib 5.4.0)

Maven Central 에 `net.dmulloy2:ProtocolLib:5.4.0` 이 있다(`com.comphenix.protocol` 이 아님). 그 jar 는 byte-buddy 를 싣지 않는다 — 시험 jar 는
`ProtocolLib-5.4.0.jar` 와 `net.bytebuddy:byte-buddy:1.17.5` 를 한 jar 로 합쳐 만들고(`zip`), 폴더에 두어
`./gradlew -PskipOracle=true :furnace-compat-paper:liveProbe -Pjar=ProtocolLib -PextraJars=<폴더> -PlibraryCache=online -Pcommands="protocol version;protocol dump"` 로 돌린다.

실측(2026-11-12, 이 PR):
- `onLoad`·`onEnable` 오류 없음, `Disabling` 까지 깨끗. `/protocol version`·`listeners`·`dump` 정상(`/protocol check` 는 업데이터가 없는 환경이라 NPE — 플러그인 쪽 사정).
- ProtocolLib `PacketType.values()` 중 `isSupported()` 198개. 우리 표의 1.21.1 패킷 클래스 202개 중 **번들 구분자 하나만 빼고 전부** ProtocolLib 종류에 연결됨
  (번들은 ProtocolLib 이 따로 다룬다). 구성 단계 종류도 1.21.1 에 있는 것은 모두 지원(`SHOW_DIALOG` 등은 1.21.6+ 라 없는 게 맞다).
  `getCurrentId()` 는 ProtocolLib 이 내장한 최신 버전 번호라 우리 번호와 다르다 — ProtocolLib 은 번호가 아니라 **클래스 이름**으로 종류를 잇는다(`PacketType.fromCurrent`).
- 다른 플러그인(`depend: [ProtocolLib]`)에서 `createPacket` 은 지원 종류 198개 전부 성공. `UPDATE_TIME` 의 `getLongs().write/read`, `SPAWN_ENTITY` 의 정수·UUID·double 읽기/쓰기 동작.
- **안 되는 것(독립 검토 R4)**: `deepClone` 은 18종에서 실패한다. 코덱 미연결(`USE_ENTITY`·`BOSS`·`ADVANCEMENTS`·상태 `SERVER_INFO`, 3단계), `WrappedChatComponent` 초기화 실패(`CraftChatMessage` 없음 — `SYSTEM_CHAT`·`CHAT`·`DISGUISED_CHAT`·타이틀 셋·`PLAYER_LIST_HEADER_FOOTER`·`SCOREBOARD_OBJECTIVE`·`SERVER_DATA`·`OPEN_WINDOW`·`PLAYER_COMBAT_KILL`·`KICK_DISCONNECT`·로그인 `DISCONNECT` 13종, 첫 실패 뒤엔 채팅 컴포넌트 읽기·쓰기 전부 막힘), `BLOCK_CHANGE`(`WrappedBlockData` 가 BlockState→byte 정적 메서드를 못 찾음). `getEntityTypeModifier()` 도 실패(`EntityType.getKey` 없음). 4단계(값 래퍼)의 첫 대상은 `CraftChatMessage`·`WrappedBlockData`·`EntityType.getKey`.
- 접속한 플레이어가 있어도(검토 R1·R2) `PlayerLoginEvent`·`PlayerJoinEvent` 오류가 없고, 플레이어 접속 중에 ProtocolLib 을 핫 리로드해도 켜진다(시험 `PacketPipelineShapeTest`, 검토자의 재현 시험을 같은 jar 로 돌려 확인).
- 검토(독립)는 번호 표 223개를 데이터 생성기 보고서 `packets.json`(`java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --reports`)로 대조해 전부 일치함을 확인했다. 다음 판 갱신 때 추출 도구 검증에 이 방법을 쓴다(단 "어느 PacketTypes 클래스가 선언했나"는 보고서에 없어 바이트코드 추출이 여전히 필요).

## 왜 `channels` 가 비었나 (2단계의 나머지)

ProtocolLib 은 `channels` 의 서버 소켓 채널 파이프라인 앞에 처리기를 끼워 새 접속마다 자기 처리기를 `"decoder"`·`"encoder"`·`"packet_handler"`(구성은 `"outbound_config"`) 옆에 넣는다.
Furnace 의 파이프라인은 `frameDecoder`·`frameEncoder`·`handler`(ConnectionHandler)로 바닐라와 처리기 이름도, 그 사이를 흐르는 것(패킷 객체 대 프레임 바이트)도 다르다.
실제 채널을 넘기면 접속마다 없는 처리기 이름을 찾다 실패하거나 패킷이 어긋난다 — 그래서 **다리(3단계) 없이는 채우지 않았다.** 지금 ProtocolLib 은 켜지지만 패킷을 못 본다(리스너는 등록되고 불리지 않는다, `sendServerPacket`·`receiveClientPacket` 은 조용히 버려지거나 지원 안 됨 — 그래서 플러그인이 파이프라인 입구를 읽으면 경고를 낸다).

## 남은 일 (계획서 2·3단계, 다음 PR 후보)

1. **2b 파이프라인 주입**: 자리표시 `Connection.channel` 을 활성 가상 채널로 바꾸고(`Connection#send` 는 이미 `ServerCommonPacketListenerImpl#send` 로 이어짐), 바닐라 처리기 이름(`splitter·decoder·prepender·encoder·packet_handler`)의 자리를 Furnace 파이프라인에 ProtocolLib 이 감지될 때만 만든다(없으면 비용 0). `channels` 에 실제 서버 소켓 future.
2. **3 패킷 객체 다리**: `IdDispatchCodec` 의 serializer 를 실제 코덱으로. Furnace 쪽 범용 패킷 코덱(PR #236 계획 E4, 스키마 공유)이 들어오면 `SchemaPacket` 변환으로 잇는다. 묶음 단위(채팅·타이틀 → 엔티티·메타데이터 → 블록·청크)로 봇 시험.
3. **4 값 래퍼**: `WrappedDataWatcher`(`SynchedEntityData`)·`WrappedChatComponent`·`BukkitConverters`.
4. 겸사: Bukkit `org.bukkit.conversations` 패키지가 없어 ProtocolLib 의 `/protocol filter` 명령 등록이 경고로 빠진다(`ConversationCanceller`). 일반 Bukkit API 공백이라 따로 한다.
5. 겸사: 리플렉션으로 후보 이름을 차례로 시험하는 플러그인(ProtocolLib 은 켤 때 9개, 접속·핫 리로드까지 24개, `PacketType.values()` 를 돌며 `isSupported()` 를 묻는 로깅 도구는 많게는 297개)이 "NMS shim 에 아직 없다" 경고를 낸다 — 찾는 쪽이 `ClassNotFoundException` 을 잡고 다음 후보를 시험하는 정상 동작이라 소음이다. 줄이는 방법은 사용자 확인 항목에 적었다.

## 시험
- `furnace-api` `ProtocolIdsTest`(5): 표의 수·잘 알려진 번호·스키마와의 1:1·번호 빈틈/중복·번들 하나만 번호 없음, 문법 오류.
- `furnace-server` `ProtocolIdsMatchServerTableTest`(2): 게임 번호 표가 서버 자신의 play 표(`Protocol767`)와 한 글자도 다르지 않다. 두 표는 따로 바닐라 바이트코드에서 뽑았다.
- `furnace-compat-paper` `PacketRegistryShimTest`(9): ProtocolLib 이 읽는 경로(`*Protocols` static final 틀 → bind → codec → `byId` → Entry → `*PacketTypes` 필드 제네릭 인자)를 그대로 읽어 번호 223개 전부 대조, 종류 202개, `IdDispatchCodec`, 접속 수신 목록 갈아 끼우기, `FriendlyByteBuf` 와이어 바이트(128→`80 01`, -1→`ff ff ff ff 0f`)·위임·감싸기.
