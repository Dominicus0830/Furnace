# 영역 8 손 shim 이전 4묶음 (2026-11-27)

서버 패킷 수신기 `ServerCommonPacketListenerImpl`·`ServerGamePacketListenerImpl` 을 명세 조각으로 옮겼다(PR 642 로 `CraftPlayer.TransferCookieConnection` 이 생겨 가능).
javap 전후(3048 클래스): 다름 2개 — 두 클래스에 바닐라 인터페이스(ServerCommonPacketListener·ServerGamePacketListener·ServerPlayerConnection·TickablePacketListener·TransferCookieConnection)가 붙는 방향뿐.
새로 생긴 인터페이스 메서드 4개를 던지는 몸통 대신 실제로 구현: `isTransferred`(연결의 전송 여부), `getProtocol`(이 shim 은 게임 단계뿐이라 PLAY),
`sendPacket`(= send), `kickPlayer(reason, cause)`(새 Furnace API `Player.kick(Text, Cause)` → 내보내기 이벤트 원인 칸). 남은 손 shim: PathPackResources.
