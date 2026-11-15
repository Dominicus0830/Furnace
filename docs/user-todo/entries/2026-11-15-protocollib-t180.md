# ProtocolLib T-180 — 접속자가 보낸 패킷을 ProtocolLib 받는 리스너에 보이기 (2026-11-15)

반치트·채팅 필터처럼 **클라이언트가 보낸 패킷**(이동·상호작용·블록 캐기·블록에 쓰기·채팅·명령 등)을 듣는 ProtocolLib 플러그인이 이제 그 패킷을 보고, 바꾸고, 취소한다. 지금까지는 한 번도 불리지 않았다.

## 정한 것 (결정할 것 없음)

**T180-D1. 받는 리스너를 어느 스레드에서 부를까 → ProtocolLib 기본 동작 그대로(Paper 와 같음).** 처음에는 보내는 쪽(2b-2 D1-A)처럼 그 자리에서 "주 스레드"로 표시했지만,
독립 검토(PR #389, 진짜 ProtocolLib 5.4.0 실측)에서 전제가 틀린 것을 확인했다: ProtocolLib 받는 리스너는 **기본이 Netty 스레드(비동기)** 이고,
`ListenerOptions.SYNC` 를 준 리스너만 라이브러리가 스스로 다음 틱 주 스레드로 미룬다(보내는 쪽과 반대).

- 기본 리스너: 네트워크 스레드, `event.isAsync()==true`, `Bukkit.isPrimaryThread()==false` — Paper 와 같다. "isAsync 면 동기 작업을 예약" 하는 플러그인이 맞게 동작한다.
- SYNC 리스너: ProtocolLib 이 주 스레드(Furnace 전역 스레드)로 미뤄 부르고, 서버는 그 뒤 패킷을 한 번 처리한다 — 블록·엔티티 API 가 안전하다. 그 종류는 Paper 처럼 최대 1틱 늦다.
- 리스너가 다른 종류로 바꾼 패킷·`receiveClientPacket` 으로 넣은 패킷은 그 자리에서 처리된다(클라이언트가 뒤이어 보낸 패킷보다 먼저, Paper 와 같다).

## 아직 안 보이는 것 (서버 시작 뒤 ProtocolLib 로그에 INFO 한 줄로 나온다)

- 플러그인 메시지(`custom_payload`) — Bukkit `registerIncomingPluginChannel` 은 그대로 된다.
- 인벤토리 클릭(`container_click`) — Bukkit `InventoryClickEvent` 는 그대로 된다.

쓰는 플러그인이 이 둘을 ProtocolLib 로 듣는다면 알려 주세요(후속 작업 순서 조정).

## 직접 확인할 것 (선택)

- 실제 반치트·채팅 필터(ProtocolLib 기반)를 넣고 접속해 이동·공격·채팅이 그 플러그인에 걸리는지.
