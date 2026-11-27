# 영역 8 손 shim 이전 1묶음 (2026-11-27)

`net/minecraft/server`·`commands` 손 shim 35개 중 30개를 명세 조각으로 옮겼다(`server-commands.spec`). javap 전후 비교(3004 클래스): 다름 3개뿐, 모두 바닐라와 같아지는 방향
(MinecraftServer 가 AutoCloseable, DedicatedServer 가 ServerInterface 를 구현, CommandSource 멤버 순서).

못 옮긴 5개와 이유(상위·형식이 아직 shim 에 없다 — 영역 8 채우기와 함께 옮긴다):
- `CommandSourceStack`: Paper 가 `io.papermc.paper.command.brigadier.PaperCommandSourceStack` 을 구현하는데 그 형식이 shim 에 없다.
- `ServerCommonPacketListenerImpl`·`ServerGamePacketListenerImpl`: Paper 가 `CraftPlayer$TransferCookieConnection` 을 구현한다. 바깥 CraftPlayer 가 손 클래스라 생성기가 안쪽 인터페이스를 못 만든다.
- `ChunkMap`·`ChunkHolder`·`ThreadedLevelLightEngine`·`PathPackResources`: 바닐라 상위(ChunkStorage·GenerationChunkHolder·LevelLightEngine·AbstractPackResources)의 생성자·close 를 아직 이을 수 없다.

함정: `CommandSource` 같은 손 인터페이스를 명세로 옮기면 추상 메서드(잠금 H 줄)가 사라져 Sign·Lectern 이 컴파일 실패한다 → 추상 메서드 이름을 명세에 적고 조각에서 지운다.
`CompatLangTest` 는 src/shims/java 도 훑도록 고쳤다. 서명 관문 기준선 새 줄 3(LevelTimeAccess 상위 인터페이스 부재), 낡은 줄 1(DedicatedServer) 정리.
시험: compat 1697개 중 CompatLangTest 1개 실패 → 위 수정 후 통과(재실행은 그 시험만).
