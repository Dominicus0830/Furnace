# 2026-11-12 ProtocolLib 2b-1 메모

- ProtocolLib 5.4.0 의 보내는 쪽 가로채기는 파이프라인 처리기가 아니다. 주입 때 `Connection` 의 `Channel` 필드를 `NettyChannelProxy` 로 갈아 끼우고, 바닐라가 `channel.eventLoop().execute(작업)` 으로 올리는 작업의 `Packet` 형식 필드를 반사로 읽어 바꾸거나(필드 되쓰기) 취소(null)한다. 그래서 작업은 이름 있는 클래스·비 final `Packet` 필드여야 한다(`Connection.Send`).
- ProtocolLib 은 `ServerCommonPacketListenerImpl` 의 "Packet 인자 하나인 비정적 void 메서드 중 첫 번째"를 보내기 메서드로 쓴다(`getMethodList(...).get(0)`, 순서 미정). 그래서 이 클래스에 같은 모양의 보조 메서드를 두면 안 된다 — 변환·전송 본문은 `PacketDelivery` 로 옮겼다.
- 가상 채널 닫기는 Furnace `PlayerQuitEvent` MONITOR 구독(EventBridge: Bukkit 이벤트를 듣는 플러그인이 하나라도 있으면 플러그인마다 한 번)과 `retire()` 에서. 활성 여부는 접속 상태(`isOnline`)를 따로 따라간다.
- Netty `EmbeddedChannel` 의 출력 버퍼는 한 스레드 전용이다 — 가상 루프가 `inEventLoop()=true` 를 모든 스레드에 답하므로 쓰기는 파이프라인 맨 앞 끝 처리기(`furnace_sink`)에서 끝내 unsafe 를 건드리지 않는다(PR #269 검토 R1: 8스레드 쓰기 때 채널이 스스로 닫혀 ProtocolLib 주입이 걷히던 문제).
- 저장소 시험 `VirtualChannelTest` 는 ProtocolLib 대신 `Proxy` 로 같은 가로채기를 흉내 낸다. 진짜 jar 확인 방법은 `compat-protocollib-registry-2026-11-12.md`(merged jar + 탐침 플러그인).
- 탐침을 gradle 로 돌릴 때는 첫 실행이 `--offline` 없이 온라인이어야 realWorld 구성이 풀린다.
