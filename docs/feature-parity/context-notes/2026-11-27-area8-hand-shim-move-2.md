# 영역 8 손 shim 이전 2묶음 (2026-11-27)

ChunkMap·ChunkHolder·ThreadedLevelLightEngine 을 명세 조각으로 옮겼다. javap 전후(3013 클래스): 다름 3개, 모두 바닐라 상위·인터페이스가 새로 붙는 방향
(ChunkHolder→GenerationChunkHolder, ChunkMap→ChunkStorage·PlayerProvider·GeneratingChunkMap, ThreadedLevelLightEngine→AutoCloseable).
- 생성 상위(ChunkStorage·GenerationChunkHolder)를 잇는 조각 생성자는 `super(ShimCtor.TAG)`.
- `ChunkStorage#close`·`ThreadedLevelLightEngine#close` 는 빈 몸통: 바닐라는 IO 일꾼·빛 실행기를 닫지만 Furnace 는 청크 IO 를 서버 IO 풀이, 빛 콜백을 요청별 스레드가 가져 닫을 것이 없다.
남은 3개: CommandSourceStack(Paper API 형식 `io.papermc.paper.command.brigadier.CommandSourceStack` 이 Furnace API 에 없음 — T-240 몫), 서버 패킷 수신기 2개(`CraftPlayer$TransferCookieConnection` — 바깥 CraftPlayer 가 손 클래스), PathPackResources(AbstractPackResources 생성자).
