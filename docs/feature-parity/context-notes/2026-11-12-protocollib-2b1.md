# 2026-11-12 ProtocolLib 2b-1 메모

- ProtocolLib 5.4.0 의 보내는 쪽 가로채기는 파이프라인 처리기가 아니다. 주입 때 `Connection` 의 `Channel` 필드를 `NettyChannelProxy` 로 갈아 끼우고, 바닐라가 `channel.eventLoop().execute(작업)` 으로 올리는 작업의 `Packet` 형식 필드를 반사로 읽어 바꾸거나(필드 되쓰기) 취소(null)한다. 그래서 작업은 이름 있는 클래스·비 final `Packet` 필드여야 한다(`Connection.Send`).
- ProtocolLib 은 `ServerCommonPacketListenerImpl` 의 "Packet 인자 하나인 비정적 void 메서드 중 첫 번째"를 보내기 메서드로 쓴다(`getMethodList(...).get(0)`, 순서 미정). 그래서 이 클래스에 같은 모양의 보조 메서드를 두면 안 된다 — 변환·전송 본문은 `PacketDelivery` 로 옮겼다.
- Furnace 는 퇴장 때 Bukkit 이벤트를 듣는 처리기가 있을 때만 `PlayerQuitEvent` 를 Bukkit 으로 전달한다(EventBridge). 가상 채널 닫기는 그 push 안에 있어 Bukkit 처리기가 없으면 불리지 않지만, 그 경우 채널을 보는 라이브러리도 없다. 활성 여부는 접속 상태(`isOnline`)를 따로 따라간다.
- 저장소 시험 `VirtualChannelTest` 는 ProtocolLib 대신 `Proxy` 로 같은 가로채기를 흉내 낸다. 진짜 jar 확인 방법은 `compat-protocollib-registry-2026-11-12.md`(merged jar + 탐침 플러그인).
- 탐침을 gradle 로 돌릴 때는 첫 실행이 `--offline` 없이 온라인이어야 realWorld 구성이 풀린다.
