# ProtocolLib 을 돌리는 단계별 바닐라 네트워크 다리 — 계획 (2026-11-11)

도미니코 결정(2026-11-08 결정 카드 "단계별 바닐라 네트워크 다리"): ProtocolLib 을 따로 흉내 내지 않고, ProtocolLib 이 리플렉션으로 기대는
바닐라(Mojang 이름) 네트워크 모양을 NMS shim(H7 틀)으로 단계마다 만들어 **진짜 ProtocolLib jar** 가 돌게 한다. 여러 PR·여러 주에 걸친다.
각 단계는 실제 ProtocolLib 5.4.0 jar 를 `liveProbe`·봇 접속으로 돌려 "다음 장애물"을 기록하고 끝난다.

## 왜 단계가 필요한가

ProtocolLib 은 세 층에서 바닐라를 본다(디컴파일 대조, 5.4.0):

1. **패킷 등록부**(`PacketRegistry` → `ProtocolRegistry_1_20_5`): `network.ConnectionProtocol`·`network.protocol.PacketFlow`·`ProtocolInfo`(+`Unbound#bind(Function)`)·
   `<상태>.<상태>Protocols`(handshake·status·login·configuration·game)·`GameProtocols$Context`·`network.codec.IdDispatchCodec`(`byId` 목록의 `Entry(type, serializer)`)·
   `network.protocol.PacketType`, 그리고 `*PacketTypes` 의 static final 필드로 "패킷 클래스 ↔ 번호 ↔ 방향"을 만든다. **지금 막힌 곳**(onLoad).
2. **서버 연결과 파이프라인**(`NetworkManagerInjector`·`InjectionFactory`·`NettyChannelInjector`): `MinecraftServer` 인스턴스에서 인자 없는 메서드로
   `ServerConnectionListener` 를 얻고, 그 객체의 `List<ChannelFuture>` 필드를 자기 `ListeningList` 로 갈아 끼워 새 연결마다 채널 초기화에 끼어든다.
   플레이어마다 `CraftPlayer#getHandle().connection.connection`(NMS `Connection`)의 `Channel` 필드를 찾고, 파이프라인에서 `Connection` 핸들러와
   `"encoder"`/`"decoder"`(구성 단계는 `"outbound_config"`/`"inbound_config"`) 이름의 핸들러 옆에 자기 핸들러를 넣는다.
3. **패킷 객체**: 파이프라인을 지나는 것이 바닐라 패킷 객체(record·클래스)라고 보고, `StructureModifier` 로 필드를 **선언 순서·타입**으로 읽고 쓴다.
   Furnace 는 자체 `Packet` record 를 `ByteBuf` 로 써서 내보내므로 이 층이 근본 차이다.

## 단계

| 단계 | 할 일 | 끝나면 |
|---|---|---|
| 1. 패킷 등록부 | 위 1층의 shim: 상태·방향 enum, 상태별 `*Protocols`(바닐라 1.21.1 패킷 번호 표 — `reports/packets.json` 이 정본), `IdDispatchCodec`·`StreamCodec`·`FriendlyByteBuf` 껍데기, 패킷 클래스 이름(약 200개, 아직 필드 없음)과 `*PacketTypes` | ProtocolLib onLoad 통과, `PacketType` 표가 바닐라와 같다(번호 대조 시험) |
| 2. 연결·파이프라인 모양 | `MinecraftServer#getConnection()` → `ServerConnectionListener`(실제 Furnace 수신 `ChannelFuture` 목록), `ServerPlayer.connection`·`ServerGamePacketListenerImpl.connection`·NMS `Connection`(채널 필드)과 파이프라인의 바닐라 핸들러 이름 | ProtocolLib onEnable 통과, 접속마다 주입. 패킷 리스너는 아직 패킷 객체를 못 받는다 |
| 3. 패킷 객체 다리 | Furnace `Packet` ↔ 바닐라 모양 패킷 객체 변환 층을 파이프라인의 `encoder`/`decoder` 자리에 둔다(ProtocolLib 핸들러가 그 사이에 낀다). 패킷 묶음별로: 채팅·제목·탭 목록·스코어보드 → 엔티티 생성·메타데이터·장비 → 블록·청크 | ProtocolLib 리스너가 받은 패킷을 읽고 바꾸고 취소하고 직접 보낼 수 있다(묶음마다 봇 시험) |
| 4. 값 래퍼 | `WrappedDataWatcher`(`SynchedEntityData`)·`WrappedChatComponent`(`Component` 직렬화)·`BukkitConverters`(아이템·블록 상태) 등 ProtocolLib 이 쓰는 값 변환 | ProtocolLib 을 쓰는 실제 플러그인(홀로그램·TAB 류)으로 봇 시험 |

## 지킬 것

- 패킷 번호·필드 순서는 추측하지 않는다 — 1.21.1 `reports/packets.json` 과 바닐라 jar(`javap -c -p`)로 확인한다(.claude/rules/network-packets.md).
- 바닐라 디컴파일 소스·데이터는 커밋하지 않는다. shim 은 이름·모양만 맞춘 Furnace 코드다(H5·H7 와 같은 원칙).
- 3단계 변환 층은 ProtocolLib 이 없는 서버에서 비용이 없어야 한다(그 핸들러는 리스너가 있을 때만 끼운다). 성능 측정을 함께 남긴다.
- Furnace 자체 패킷 API(`docs/api/packets.md`)와 겹치는 일은 3단계에서 같은 변환 표를 쓰도록 맞춘다.

## 지금(2026-11-11) 상태

`onLoad` 의 `PacketRegistry.<clinit>` 에서 `network.EnumProtocol`/`network.ConnectionProtocol` 을 못 찾아 실패, 이어서 `onEnable` 이 "does not support plugin reloaders" 를 찍고 꺼진다.
LuckPerms·Vault 는 같은 서버에서 정상(`docs/feature-parity/compat-luckperms-2026-11-11.md`). 1단계는 별도 스레드·PR 로 시작한다.

## 갱신(2026-11-12, PR #240)

1단계(패킷 등록부)와 2단계 최소분(연결 모양: `ServerCommonPacketListenerImpl.connection`, 닫힌 자리표시 `Connection.channel`)을 끝냈다. ProtocolLib 5.4.0 은 켜지고 `PacketContainer` 생성·읽기·쓰기가 되며, 접속·핫 리로드도 오류가 없다.
가로채기·`sendServerPacket` 은 아직 아니다(2b 파이프라인 주입, 3 코덱, 4 값 래퍼). 자세히: `compat-protocollib-registry-2026-11-12.md`.

4단계 첫 묶음(채팅 컴포넌트·엔티티 종류·블록 데이터·열거형, 4a)은 `compat-protocollib-registry-2026-11-12.md` 끝 절에 있다. 2b(파이프라인 주입)는 3단계 코덱("쉬운 NMS·패킷" 계획 E4)이 있어야 의미가 있어 그 뒤로 둔다.
