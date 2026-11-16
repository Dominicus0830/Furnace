# ProtocolLib 다리 1단계 — 바닐라 대조·함정 (2026-11-12)

- **번호의 출처**: 1.21.1 번들러 jar 안 `server-1.21.1.jar` 의 `*PacketTypes`(필드 ← `ldc "<이름>"` + 방향 팩토리)와 `*Protocols` 등록 람다(순서 = 번호)를 `javap -c` 로 읽었다. 난독화 이름(1.21.1): PacketTypes — zy common, abi configuration, abq cookie, agg game, aik handshake, aiu login, ajk ping, ajt status. Protocols — abj configuration, agh game, ail handshake, aiv login, aju status. PacketType = zi, ProtocolInfo = wh(Unbound = wh$a), IdDispatchCodec = yw(Builder yw$a, Entry yw$b), StreamCodec = yx, ConnectionProtocol = vu, PacketFlow = zh(첫 상수 SERVERBOUND), ProtocolInfoBuilder = zl.
- **방향 팩토리 글자는 클래스마다 다르다**: `zy.a` 는 clientbound 인데 `aik.a`(핸드셰이크)는 serverbound. 방향은 팩토리 본문의 `zh.a/b` 로 읽어야 한다(처음엔 글자로 추정해 핸드셰이크가 clientbound 로 나왔다).
- **번들**: 게임 clientbound 의 0번은 `bundle_delimiter` 다. `bundle`(BundlePacket 종류)은 `PacketTypes` 필드로만 있고 번호가 없다 — 등록 람다의 3인자 `zl.a(type, fn, delimiter)` 가 구분자에게 번호를 준다.
- **ProtocolLib 은 번호가 아니라 클래스 이름으로 종류를 잇는다**: `PacketType.fromCurrent(protocol, sender, id, class)` 는 클래스 이름 지도에서 찾고, 번호가 다르면 `onIdMismatch` 만 부른다. 그래서 `getCurrentId()` 는 ProtocolLib 이 내장한 최신 버전 번호(예: UPDATE_TIME 106, 바닐라 1.21.1 은 100)이고 우리 번호와 다른 게 정상이다. 종류 연결은 클래스 이름이 맞아야 한다(그래서 스키마의 Mojang 클래스 이름이 중요하다).
- **`ProtocolInfo.Unbound`**: 1.21.1 에는 `SimpleUnboundProtocol`·`UnboundProtocol` 클래스가 없다(1.20.5 에만). ProtocolLib 은 후보 이름 셋(`...SimpleUnboundProtocol`, `ProtocolInfo$a`, `ProtocolInfo$Unbound`)을 시험하고 `UnboundProtocol` 은 없으면 건너뛴다. 게임의 `bind` 는 `Function` 하나만 받는다(컨텍스트 인자 없음).
- **Maven Central 의 ProtocolLib**: 그룹이 `net.dmulloy2`(옛 `com.comphenix.protocol` 은 404). jar 는 byte-buddy 를 싣지 않는다 — 합쳐서 시험.
- `FriendlyByteBuf` 위임은 netty 4.1.115 의 `ByteBuf` 추상 메서드 180개 + `ReferenceCounted` 에서 물려받는 3개(`refCnt`·`release`·`release(int)`)다. 생성기(javap 기반)는 후자를 못 본다.
