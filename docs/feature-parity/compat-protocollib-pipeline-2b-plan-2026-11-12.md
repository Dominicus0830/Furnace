# ProtocolLib 다리 2b — 파이프라인 주입(패킷 가로채기) 설계 (2026-11-12)

계획 `protocollib-bridge-plan-2026-11-11.md` 의 2단계 나머지("연결·파이프라인 모양") 와 3단계("패킷 객체 다리")를 **한 설계**로 묶는다. 앞 단계(PR #240·#244·#246·#248~#249)로
진짜 ProtocolLib 5.4.0 은 켜지고 `PacketContainer`·값 래퍼·`WrappedDataWatcher` 가 돌지만, 접속한 플레이어의 패킷은 아직 ProtocolLib 리스너를 지나지 않는다.
Furnace 쪽 연결점이 이제 모두 있다: 범용 패킷 값(`GenericPacket`, E4a/b), shim → `GenericPacket` 변환(`SchemaPacketBridge`, E4c-1), 손 코덱 종류(E4b/E4c-2).
이 문서는 ProtocolLib 이 **실제로 어디에 끼어드는지**(5.4.0 소스 대조)와, 그 자리에 Furnace 를 어떻게 이을지, 몇 조각으로 나눌지를 정한다.

## 1. ProtocolLib 5.4.0 이 끼어드는 자리 (소스 `ProtocolLib-5.4.0-sources.jar` 대조)

핵심 발견: **보내는 쪽은 파이프라인 처리기가 아니라 `Connection.channel` 필드의 이벤트 루프 가로채기**다. 바닐라 `Connection` 이 패킷을 보낼 때
`channel.eventLoop().execute(작업)` 으로 올리는 것을 이용한다.

| 자리 | ProtocolLib 동작 | Furnace 가 있어야 하는 것 |
|---|---|---|
| 주입 시작(플레이어) | `PlayerLoginEvent`·`PlayerJoinEvent`(LOWEST)·켤 때 접속자 전부 → `getInjector(player).inject()`. `MinecraftFields.getNetworkManager(player)` 로 `PlayerConnection` 의 `Connection` 필드 → 그 안의 **정확히 `Channel` 형식인 비정적 필드**를 읽는다 | 이미 있음(PR #240) |
| 주입(`NettyChannelInjector.inject`) | **채널이 활성**이어야 한다(`channel.isActive()`; 비활성이면 조용히 건너뜀 — 지금까지의 자리표시 채널이 이것). 활성이면 `Connection.channel` 필드를 `NettyChannelProxy`(이벤트 루프만 바꿔친 프록시)로 **갈아 끼운다**. 그리고 파이프라인에서 `"encoder"`(또는 구성 단계 `"outbound_config"`) 뒤에 `protocol_lib_wire_packet_encoder`, `"decoder"`(`"inbound_config"`) 앞에 `protocol_lib_inbound_protocol_getter`, 뒤에 `protocol_lib_inbound_interceptor` 를 `addAfter`/`addBefore` — **이름이 없으면 NoSuchElementException** | 활성 가상 채널 + 이름이 바닐라와 같은 `decoder`·`encoder` 처리기 |
| 지금 단계(`ChannelProtocolUtil`) | 1.20.5+ 는 파이프라인의 `network.PacketEncoder`/`PacketDecoder` **클래스의 처리기**를 찾아 그 안의 `ProtocolInfo` 필드 → `id()` 로 단계(PLAY 등)를 안다 | `PacketEncoder`·`PacketDecoder` shim(필드 `ProtocolInfo`) |
| **나가는 패킷 가로채기** | `NettyEventLoopProxy.execute(Runnable)` → `processOutbound(작업)`: 작업 객체에서 **`Packet` 의 하위 형식인 필드**를 반사로 찾아 패킷을 꺼내 `PacketType` 을 알아내고, 그 종류에 ProtocolLib 리스너(`hasOutboundListener`)가 있을 때만 `PacketEvent` 를 만든다. 리스너가 패킷을 바꾸면 **그 필드에 되쓰고**, 취소하면 `null` 을 돌려줘 작업을 버린다. 리스너·마커가 없으면 그냥 통과 | `Connection#send` 가 `channel.eventLoop().execute(작업)`(작업에 `Packet` 필드)으로 올릴 것 — **이번 PR(2b-1)** |
| 서버가 직접 보내기(`sendServerPacket`) | `NettyChannelInjector.sendClientboundPacket` → (주입돼 있을 때만) `PacketListenerInvoker.send` → `ServerCommonPacketListenerImpl#send(Packet)`(Packet 인자 하나인 비정적 void 메서드를 **형식으로** 찾는다) → 바닐라는 `connection.send` → 위의 이벤트 루프 가로채기를 다시 지난다. `filtered=false` 면 `skippedPackets` 로 리스너를 건너뛴다 | 같은 경로(2b-1) |
| 들어오는 패킷 가로채기 | 디코더 **뒤** `InboundPacketInterceptor.channelRead`: 메시지가 NMS `Packet` 이면 종류를 찾고, 리스너가 있을 때만 `processInbound` → 바꾸거나 취소하고 `ctx.fireChannelRead` 로 다음 처리기(`packet_handler` = `Connection`)에 넘긴다 | 디코드된 shim 패킷을 `decoder` 처리기 문맥에서 `fireChannelRead` 로 흘리고, 끝(`Connection`)에서 다시 Furnace 패킷으로 바꿀 것(2b-3) |
| 클라이언트 패킷 흉내(`receiveClientPacket`) | `PacketListenerInvoker.read` → `Connection.read(ctx=null, packet)`(이름이 `read`, 인자 `ChannelHandlerContext, Packet` 인 메서드) | 2b-3 |
| 끊기 | `PacketListenerInvoker.disconnect` → 처리자 `disconnect(Component)` | 이미 있음 |
| 채널이 닫히면 | `channel.closeFuture()` 에 걸어 둔 리스너가 주입을 걷고 프록시를 원래 채널로 되돌린다 | 접속이 끝나면 가상 채널을 닫을 것 |
| 로그인 전 단계(핸드셰이크·상태·로그인) | `ServerConnectionListener` 의 `channels`(`List<ChannelFuture>`)를 **갈아 끼워** 새 소켓마다 `protocol_lib_inbound_inject` 처리기를 끼운다 | 범위 밖(§6) — Furnace `PacketService` 도 play 단계만 다룬다 |

바이트 와이어 패킷(`sendWirePacket`)은 `channel.writeAndFlush(WirePacket)` 로 파이프라인 끝까지 가서 `ByteBuf` 로 나온다 — 가상 채널은 이를 버리고 플러그인 이름으로 한 번 경고한다(Furnace 패킷 API 에 "원본 바이트 보내기"는 없다).

## 2. 설계 원칙

1. **없으면 비용 0**: ProtocolLib 이 없는 서버에서는 어떤 변환도 하지 않는다. 가상 채널은 접속마다 하나(작은 객체)이고, 패킷 변환(shim ↔ Furnace)은 **ProtocolLib 이 그 종류를 듣고 있을 때만** 한다.
2. **Furnace 의 스레드 규칙을 지킨다**: Furnace 는 한 플레이어의 패킷을 늘 같은 네트워크 스레드에서 순서대로 다룬다. 가상 채널의 이벤트 루프는 호출 스레드에서 바로 실행하는 형태다(`execute` = 그 자리에서 `run`). 바닐라도 ProtocolLib 의 가로채기는 **보낸 스레드에서** 일어나므로 같은 모양이다. 줄 세우기는 Furnace 의 `PacketService` 가 이미 한다.
3. **변환은 두 방향 모두 `GenericPacket`·타입 레코드를 기준으로**: Furnace 쪽 값은 이미 버전 중립이고(이름 붙은 필드·`Text`·`BlockData`…) 서버 코덱이 와이어를 만든다. shim 쪽 값은 바닐라 모양(Mojang 이름 클래스)이다. 둘 사이 변환표를 한 군데(`SchemaPacketBridge` 와 그 반대 방향)에 둔다.
4. **ProtocolLib 이 "자기 일"을 하게 둔다**: 우리는 파이프라인에 올바른 이름·형식의 처리기를 두고 올바른 객체를 흘리기만 한다. ProtocolLib 의 `PacketEvent`·우선순위·비동기·마커·후처리는 손대지 않는다.

## 3. 나가는 패킷 — 두 갈래

### 3-1. 플러그인이 NMS·ProtocolLib 로 직접 보낸 것 (shim → Furnace) — **2b-1, 이 PR**

```
ProtocolLib.sendServerPacket / player.connection.send(packet) / ServerCommonPacketListenerImpl#send
  → Connection#send(packet)
  → channel.eventLoop().execute(Send{packet})        ← ProtocolLib 이 channel 을 프록시로 바꿔 둔 자리
       NettyEventLoopProxy → processOutbound(Send)   ← 리스너가 packet 을 바꾸거나(필드 되쓰기) 취소(null)
  → Send.run() → PacketDelivery.deliver(player, packet) → SchemaPacketBridge.toFurnace / NmsPacket.toFurnace
  → Furnace player.sendPacket(...)
```

- 지금까지는 `send` 가 곧바로 변환·전송이라 ProtocolLib 이 끼어들 곳이 없었고, 채널이 비활성이라 `sendServerPacket` 자체가 조용히 버려졌다. 이제 `sendServerPacket` 이 지원 종류(스키마 변환 가능한 clientbound 종류 + 손 shim)에서 실제로 클라이언트에 도착하고, 다른 ProtocolLib 플러그인의 **나가는 리스너가 그 패킷을 본다**(바꾸기·취소 포함).
- 한계: Furnace 자신이 만든 패킷(채팅·엔티티 생성·월드 상태 등)은 이 경로를 지나지 않는다(§3-2).

### 3-2. Furnace 가 스스로 보내는 패킷 (Furnace → shim → ProtocolLib → Furnace) — **2b-2(구현됨, §10)**

바닐라 서버의 거의 모든 패킷은 서버 코드가 만들어 `connection.send` 로 보낸다. Furnace 서버는 자기 레코드·범용 코덱으로 바이트를 만든다. 이 갈래를 ProtocolLib 에 보이게 하려면 **Furnace `PacketService` 리스너**로 건다:

```
Furnace 서버 패킷(P: 타입 레코드 또는 GenericPacket)
  → 브리지 리스너(그 종류를 ProtocolLib 이 들을 때만 구독, EventPriority.HIGHEST — 바이트에 가장 가깝게)
  → fromFurnace(P) → shim S              ← 새 변환(스키마 필드 역변환 + 손 shim)
  → connection.channel.eventLoop().execute(Send{S})   ← 3-1 과 같은 자리 → ProtocolLib 리스너
  → 작업이 실행되면 S → toFurnace → 다시 Furnace PacketEvent 에 setPacket(바뀌었을 때)·setCancelled(취소했을 때)
```

- **구독은 ProtocolLib 이 듣는 종류만**: `ProtocolManager#getPacketListeners()`(공개 API)의 보내기·받기 허용 목록(`ListeningWhitelist`)의 합을 `PacketType`(클래스 이름 → Furnace `PacketType`)으로 바꿔 종류마다 구독한다. 등록이 바뀌는 때(`PluginEnableEvent`/`PluginDisableEvent` 뒤 1틱, 그 밖에는 느슨한 주기 점검 20틱)만 다시 계산한다. 구독자가 없는 종류는 서버가 비용을 안 쓴다(`PacketService` 의 약속)는 점을 그대로 이용 — ProtocolLib 리스너가 하나도 없으면 변환도 0.
- **무한 되돌기 방지**: 3-1 로 나간 패킷(`isPluginPacket` = true)은 이미 ProtocolLib 을 지났으므로 브리지 리스너가 건너뛴다.
- **되돌릴 때 비교**: 리스너가 shim 필드를 바꿨는지는 정확히 알 수 없으므로(`StructureModifier` 가 필드를 직접 쓴다), **리스너가 불린 종류는 무조건 다시 변환**한다(비용은 듣는 종류에 한함). 변환이 실패하면(shim 이 Furnace 가 못 쓰는 값으로 바뀜) 원본을 보내고 플러그인 이름으로 한 번 경고.
- **비동기·주 스레드 리스너**: ProtocolLib 은 `ListenerOptions.SYNC` 리스너(주 스레드 요구)가 있으면 패킷을 주 스레드로 되돌려 다시 보낸다(`ProtocolLibrary.getScheduler().runTask`). Furnace 에는 "주 스레드"가 지역별 틱 스레드이므로 `Bukkit.isPrimaryThread()` 의미(호환 모듈 쪽 정의)를 따른다 — 그 규칙을 먼저 확인한다(§7 열린 문제 O1).
- **번들**: Furnace 의 번들은 안의 패킷을 하나씩 리스너에 보여 준다(`PacketService`). shim 쪽은 `ClientboundBundlePacket` 하나로 보내는 코드와 구분 패킷이 따로라서, 3-2 는 안의 패킷을 개별로 다루고 번들 구분자는 건드리지 않는다.

### 필요한 새 변환 `SchemaPacketBridge.fromFurnace` (2b-2 의 대부분)

`toFurnace` 의 값 변환(`Conv`)의 역: 이름 붙은 필드 → 생성 shim 의 생성자 인자 순서.

| Furnace 값 | shim 값 | 비고 |
|---|---|---|
| `Integer/Long/…`·`String`·`UUID`·`Boolean` | 같음 | 명시 와이어 정수는 좁힌 값이므로 `byte`/`short` 로 되돌릴 때 부호에 주의 |
| `Text` | `Component`(`Component.of(Text)`; 반대는 `Component.furnace`) | |
| api `BlockPos`·`BlockData`·`ItemStack`·`NbtCompound`·`GlobalPos` | shim 대응 | 반대 변환기는 대부분 shim 에 있다(`BlockPos.furnace()` 등의 반대) |
| 이름 문자열(레지스트리 값·`ResourceKey`·`Holder`) | `Holder.Reference`·`ResourceKey`·레지스트리 값 | `Holder` 직접 정의 소리·효과 규칙은 `toFurnace` 의 반대 |
| enum 이름 | 상수 | |
| `PacketStruct` | 내포 record·class 의 생성자 호출 | `toFurnace` 가 만든 계획의 역 |
| `Optional<…>` | nullable | |

- 타입 레코드 종류(34종)는 손으로 쓴 shim 쌍(`NmsPacket.toFurnace` 의 반대 `fromFurnace`)이 필요하다. 먼저 자주 가로채는 종류(채팅·액션바·타이틀·엔티티 생성·제거·이동·메타데이터·장비·블록 변경·플레이어 정보)만 하고 나머지는 "아직 shim 없음"으로 리스너에서 건너뛴다(관찰만 못 함 — 문서에 종류 표).
- 구현 단위는 종류가 아니라 **값 종류별 변환기 + 시험**이다: 값 하나마다 왕복(`toFurnace(fromFurnace(x)) == x`) 시험이 가능하다.

## 4. 들어오는 패킷 (Furnace → shim → ProtocolLib → Furnace) — **2b-3**

```
Furnace 서버가 받은 패킷(PacketService 리스너 HIGHEST, ProtocolLib 이 그 종류를 들을 때만)
  → fromFurnace(P) → shim S (serverbound)
  → virtual.pipeline().context("decoder").fireChannelRead(S)   ← 이름 있는 "decoder" 문맥에서 다음 처리기로
       protocol_lib_inbound_interceptor → processInbound → 리스너가 S 를 바꾸거나 취소
       → ctx.fireChannelRead(S') → "packet_handler" = Connection#channelRead → read(ctx, S')
  → read 가 S' → toFurnace(serverbound) → 바뀌었으면 Furnace PacketEvent.setPacket, 취소됐으면 setCancelled
```

- **serverbound 변환**: `SchemaPacketBridge.compile` 은 지금 clientbound 만 받는다(`serverbound 패킷이다…` 거절). 같은 계획 표를 serverbound 에도 쓰고(필드 이름·타입은 스키마에 이미 있다), `fromFurnace`/`toFurnace` 두 방향을 serverbound 에도 연다.
- `receiveClientPacket`(ProtocolLib 이 서버에 받은 것처럼 넣기)은 `Connection.read(ctx=null, S)` 로 온다 → 같은 끝처리 → Furnace `PacketService.receive`(플러그인 패킷, 리스너 한 번 더 지나지 않도록 `receiveSilently`).
- 채팅 서명(`ServerboundChatPacket`)처럼 바꾸면 서명이 맞지 않는 패킷은 Furnace 가 이미 문서화한 대로 관찰·취소만 의미가 있다 — ProtocolLib 으로 바꿔도 같다.

## 5. 가상 채널 사양 (2b-1 에서 구현)

`VirtualChannel`(Netty `EmbeddedChannel` 하위 클래스, 접속마다 하나, 접속이 끊기면 닫힘):

| 항목 | 값 | 이유 |
|---|---|---|
| 활성 | 접속(`Player.isOnline`)이 붙어 있는 동안 | ProtocolLib `inject()`·`isConnected()` |
| 원격 주소 | `Player.address()` | `getAddress()`, 로그 |
| 채널 번호 | 접속마다 다름(`DefaultChannelId`) | 채널 그룹·맵 키 |
| 이벤트 루프 | 호출 스레드에서 바로 실행: `execute`·`submit`·`invokeAll` = 그 자리 `run`(끝난 future), `schedule*` = Netty 공용 예약기, `inEventLoop`=true (동적 프록시) | 바닐라처럼 보낸 스레드에서 가로채기가 일어나게. 처리기를 `eventLoop().submit(...)` 로 끼우는 흔한 모양도 동작 |
| 파이프라인 | `furnace_sink`(쓰기의 끝), `decoder`(`PacketDecoder`, `ProtocolInfo` PLAY/SERVERBOUND), `encoder`(`PacketEncoder`, PLAY/CLIENTBOUND), `packet_handler`(= `Connection`) | ProtocolLib 이 이름·클래스로 찾는다. 처리기는 바이트 변환을 하지 않는다(Furnace 가 바이트를 만든다) |
| 쓰기 | 끝 처리기(`furnace_sink`)가 `write` 를 끝낸다: `Packet` → `PacketDelivery`(성공·실패를 promise 로), `ByteBuf`(와이어 패킷) → 해제 + 실패 + 플러그인 이름으로 한 번 경고. `flush` 는 넘기지 않는다 | Netty 출력 버퍼·unsafe 는 한 스레드 전용인데 이 루프는 모든 스레드가 "안"이므로 아예 건드리지 않는다(여러 스레드 쓰기 안전, PR #269 검토 R1) |
| 닫기 | Furnace `PlayerQuitEvent` MONITOR(Bukkit 이벤트를 듣는 플러그인이 하나라도 있으면 걸림)·`retire()` | ProtocolLib 이 `closeFuture` 로 프록시·처리기를 걷고 `InjectionFactory` 를 비운다 |

`Connection` shim 은 netty `ChannelInboundHandlerAdapter` 가 되어 파이프라인의 `packet_handler` 자리를 맡는다(ProtocolLib `InjectionFactory.findNetworkManager` 가 파이프라인에서 `Connection` 하위 클래스를 찾는다 — 소켓 주입 경로를 열 때 쓰인다).

## 6. 범위 밖 / 한계

- **play 단계만**: Furnace `PacketService` 가 play 만 다룬다. 핸드셰이크·상태(서버 목록 핑)·로그인·구성 단계 패킷은 ProtocolLib 에 안 보인다(`ListeningWhitelist` 에 그 종류를 넣은 리스너는 조용히 불리지 않는다). 소켓 주입 경로(`channels` 갈아 끼우기)는 열지 않는다 — 열면 접속마다 로그인 단계 파이프라인(바닐라 처리기 이름)도 필요하다.
- **와이어 패킷** 보내기·바이트 수준 읽기(`WirePacket`)는 지원하지 않는다.
- **디코드 못 하는 종류**: Furnace 가 내용을 못 푸는 종류(`isReadable == false`)는 shim 으로 못 만든다 → ProtocolLib 이 볼 수 없다.
- **Bukkit·Paper 이벤트 순서**: ProtocolLib 은 바닐라에서 인코더 바로 앞(가장 바이트에 가까운 쪽)에 있다. Furnace 쪽은 `EventPriority.HIGHEST` 로 걸어 다른 Furnace 플러그인 리스너 뒤에서 보지만 `MONITOR`(관찰 전용)는 아니다 — 그래서 ProtocolLib `MONITOR` 우선순위 리스너가 값을 바꾸면 그대로 반영된다(바닐라와 같다).

## 7. 열린 문제

| # | 문제 | 지금 생각 |
|---|---|---|
| O1 | `Bukkit.isPrimaryThread()` 의미(호환 모듈). SYNC 리스너 재예약이 무한히 되돌지 않으려면 이 값이 "어느 스레드에서든 일관"해야 한다 | 2b-2 에서 정함(§10): 가로채는 동안 네트워크 스레드를 주 스레드로 표시해 그 자리에서 부른다(미루면 패킷 순서가 바뀐다). 결정 기록 `docs/user-todo/entries/2026-11-12-protocollib-2b2.md` |
| O2 | `ProtocolManager#getPacketListeners()` 로 구독을 따라잡는 지연(최대 20틱) | 플러그인이 켜질 때(`onEnable`)에 리스너를 붙이는 것이 99% 라 `PluginEnableEvent` 뒤 1틱 동기화로 충분. 런타임 추가는 20틱 주기로 따라잡고, 그 사이 패킷은 못 본다는 한계를 문서에 적는다 |
| O3 | shim 필드가 바뀌었는지 모를 때 매번 재변환하는 비용 | 2b-2 에서 정함: 듣는 종류만, 라이브러리에 넘기기 전 shim 을 한 번 Furnace 값으로 바꿔 두고(기준) 돌아온 shim 의 값과 `equals` — 같으면 원래 바이트 그대로 |
| O4 | 가상 채널 닫기 시점 | 해결: Furnace `PlayerQuitEvent`(MONITOR)·재접속/정리 때 `retire()`·로그인 거부(`forgetDenied` → `retire()`)에서 닫는다. 바닐라는 채널이 먼저 닫히고 퇴장 이벤트가 나중이지만 퇴장 중 보내기는 어느 쪽이든 안 나가므로 관찰 차이 없음 |
| O5 | 동시에 여러 스레드가 같은 접속에 보낼 때 ProtocolLib 리스너 재진입 | 바닐라와 같은 호출 스레드 가로채기라 ProtocolLib 이 이미 견딘다. 다만 Furnace 쪽 `PacketService` 는 한 플레이어 순서를 보장하므로 변환 단계에서는 잠금이 없다 |

## 8. 단계

| 단계 | 내용 | 끝나면 | 상태 |
|---|---|---|---|
| **2b-1** | 활성 가상 채널 + `decoder`·`encoder`·`packet_handler` + `Connection#send` 의 이벤트 루프 경로 + 접속 종료 시 닫기. `PacketDelivery` 로 끝처리 분리 | ProtocolLib 이 접속마다 **주입**되고, `sendServerPacket`·플러그인이 보낸 패킷이 도착하고 나가는 리스너가 그 패킷을 보고 바꾸고 취소한다 | 이 PR |
| 2b-2 | `SchemaPacketReverse`(값 종류별 역변환) + `TypedPacketBridge`(타입 레코드 종류) + `ProtocolLibBridge`(구독 동기화·가로채기) | Furnace 가 스스로 보내는 패킷을 ProtocolLib 리스너가 본다·바꾼다·취소한다 | 구현(§10) |
| 2b-3 | serverbound 변환 + 들어오는 가로채기 + `receiveClientPacket` | 클라이언트 패킷(이동·채팅·클릭)을 ProtocolLib 리스너가 본다·바꾼다·취소한다. `Connection#read` 가 실제로 동작 | |
| 2b-4 | 값 종류 확장: 남은 빈 껍데기(`global_pos`·`number_format`·`Holder` 직접 정의)·손 코덱(custom) 종류의 shim | 대부분의 종류가 shim 으로 오간다. 종류별 표를 문서에 | |

각 단계의 시험은 (1) 저장소 단위 시험(shim 모양·변환 왕복·이벤트 루프 의미), (2) 진짜 ProtocolLib 5.4.0 jar 를 넣은 `liveProbe`/탐침(커밋하지 않음, 방법은 `compat-protocollib-registry-2026-11-12.md`), (3) 봇 접속 시험이다.

## 9. 2b-1 구현 요약 (이 PR)

- `org.domi.furnace.compat.nms.VirtualChannel`: 위 §5(`Connection` 이 `furnace_sink` 끝 처리기를 단다).
- `net.minecraft.network.{PacketEncoder, PacketDecoder}`: 파이프라인의 `encoder`·`decoder` 자리(PLAY 단계 `ProtocolInfo` 필드).
- `net.minecraft.network.Connection`: `ChannelInboundHandlerAdapter`, `channel` 필드 = 가상 채널, `send` = `channel.eventLoop().execute(Send)`(작업에 `Packet` 필드), 접속 종료 시 `disconnected()`.
- `org.domi.furnace.compat.nms.PacketDelivery`: `ServerCommonPacketListenerImpl#send` 가 하던 변환·묶음 처리 본문을 옮김(동작 그대로). 처리자의 `send` 는 `connection.send` 로 잇는다.
- Furnace 퇴장 이벤트(MONITOR) 뒤 `CraftPlayer.disconnected()` → `ServerCommonPacketListenerImpl.compatDisconnected()` → 가상 채널 닫기.

검토(PR #269) 반영: 쓰기 끝 처리기(여러 스레드 안전)·`submit`/`schedule`·`channel.writeAndFlush(패킷)` 직접 쓰기·`Send.run` 이 바닐라처럼 `channel.writeAndFlush` 로 써서 ProtocolLib 의 "이미 처리함" 스레드 로컬이 꺼짐·채널 번호·라이브러리 없을 때(`channel == virtual`) 바로 전달하는 빠른 길.
ProtocolLib 5.4.0 자체 동작(Furnace 버그 아님): `NettyChannelProxy` 에 `id()` 가 없어 주입된 뒤 `connection.channel.id()` 는 `AbstractMethodError`(Paper 도 같음), 묶음 안 패킷을 모두 취소하면 빈 묶음을 쓴다(`PacketDelivery` 는 빈 묶음을 보내지 않아 결과 같음).

## 10. 2b-2 구현 요약

```
Furnace 서버 패킷 P (네트워크 스레드, PacketService 리스너 EventPriority.HIGHEST, ProtocolLib 이 듣는 종류만)
  → 표지 확인(DeliveredPackets: 플러그인이 NMS·ProtocolLib 로 보내 이미 라이브러리를 지난 것이면 건너뜀)
  → S = PacketDelivery.fromFurnace(P)   (GenericPacket → SchemaPacketReverse, 타입 레코드 → TypedPacketBridge)
  → 기준 B = toFurnace(S)
  → PrimaryThread 표시 안에서 Connection#compatIntercept(S)
       channel.eventLoop().execute(Capture{S}) → ProtocolLib processOutbound(리스너) → Capture.run → channel.writeAndFlush(S') → furnace_sink 가 받아 둠
  → null(취소)이면 setCancelled · 다른 종류·묶음이면 이 패킷 취소 + 그것을 보냄(표지) · toFurnace(S') ≠ B 면 setPacket
```

- 코드: `compat/nms/SchemaPacketReverse`(스키마 필드 나열·손 코덱 종류의 역변환 — 생성 shim 의 모든 필드 생성자로 만든다), `compat/nms/TypedPacketBridge`(타입 레코드 17종 — 생성 shim 6종은 두 방향), `compat/nms/DeliveredPackets`(무한 되돌기 방지 표지, 10초 지난 것은 버림), `Connection#compatIntercept`(Capture 작업 — Send 와 같은 모양), `compat/paper/ProtocolLibBridge`(켜기·구독·리스너), `BukkitPluginAdapter`(켜고 끈 뒤 알림).
- 구독: ProtocolLib 플러그인이 켜지면 시작. `ProtocolManager#getPacketListeners()` 의 보내기 허용 목록(서버 쪽 종류)의 `getPacketClass()` → 스키마 `packetByClass` → Furnace 종류. 다른 버킷 플러그인이 켜지고 꺼진 뒤 1틱, 그리고 20틱마다 다시 계산(바뀌었을 때만 구독을 갈아 끼운다). 구독 주인은 ProtocolLib 플러그인이라 ProtocolLib 이 꺼지면 같이 풀린다.
- 되돌리지 못하는 종류(ProtocolLib 리스너가 들어도 Furnace 가 스스로 보내는 것은 안 보인다 — 플러그인이 보낸 것은 보인다; 처음 한 번 INFO 로 알린다). 시험 `SchemaPacketReverseTest.reversibleKindsTable` 이 이 표를 지킨다:

| 묶음 | 종류 | 까닭 |
|---|---|---|
| 타입 레코드 | `set_entity_data`·`player_info_update`·`custom_payload` | shim 이 아직 없다(메타데이터 값 직렬화·GameProfile·페이로드 종류) — 2b-4 |
| 빈 값 shim | `block_entity_data`·`delete_chat`·`disguised_chat`·`login`·`open_screen`·`respawn`·`server_links` | `toFurnace` 도 없다(값 shim 이 빈 껍데기) |
| 손 코덱 | `boss_event`·`award_stats` | `toFurnace` 도 없다 |
| 손 코덱 | `set_player_team`·`set_objective`·`update_attributes`·`map_item_data` | 역변환이 아직 없다 — 2b-4 |
| 값 하나 | `set_score`·`set_objective` 의 styled 숫자 형식 | NBT 스타일 → shim Style 이 없다(그 패킷만 건너뛰고 경고 한 번) |

- 시험: `SchemaPacketReverseTest`(시험 클라이언트가 받은 모든 패킷 — 접속 때 서버가 보내는 것 + 손 코덱·타입 레코드 표본 — 을 `toFurnace(fromFurnace(v)).equals(v)` 로 돌림, 50종 이상), `ProtocolLibBridgeTest`(채널 프록시로 흉내 낸 라이브러리가 Furnace 패킷 API·서버 채팅을 보고 바꾸고 취소하고 다른 종류로 바꿈, 가로채는 동안 주 스레드, 한 번만 봄, 구독 풀림).
- 바닐라와 다른 점: 속도(1/8000)는 서버 코덱이 읽은 값(short/8000)을 반올림으로 되돌린다(잘라 버리면 2.04775 같은 값이 1 어긋난다). 라이브러리가 손대지 않으면 원래 바이트가 나가므로 결과는 같다.
- 알려진 서버 쪽 사실(이 PR 범위 밖): 서버 코덱의 속도 쓰기 `(int)(v * 8000)` 는 자기가 읽은 값 `s / 8000.0` 을 374개 값에서 s−1 로 쓴다(예 16380). Furnace 패킷 리스너가 그 패킷을 setPacket 하면 1/8000 어긋날 수 있다.

