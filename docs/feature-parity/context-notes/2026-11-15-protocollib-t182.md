# ProtocolLib 청크·빛 패킷(T-182) 함정 (2026-11-15)

- ProtocolLib `WrappedLevelChunkData.ChunkData`·`LightData` 는 **static 초기화에서** `(FriendlyByteBuf 하위, int, int)` 생성자를 찾는다. 없으면 클래스 초기화가 실패해 `getLevelChunkData()` 를 쓰는 플러그인(Orebfuscator)이 통째로 멈춘다 — 그 생성자가 꼭 있어야 한다.
- 블록 엔티티 항목 클래스는 이름이 아니라 `ClientboundLevelChunkPacketData` 의 List 필드 **형식 인자**에서 찾는다(`getBlockEntityInfoClass`). 필드를 `List<?>` 나 원시 `List` 로 선언하면 실패한다.
- `WrappedRegistry` 는 `BuiltInRegistries`·`Registry` 의 공개 필드를 훑어 형식 인자로 레지스트리를 고른다. `Registry<BlockEntityType<?>>` 는 와일드카드의 윗경계가 Object 라 원시 형식 `BlockEntityType` 으로 등록된다.
- `PacketShimSchemaTest` 는 스키마의 `private` 표시와 shim 클래스의 접근 제한이 같아야 한다 — 바닐라 `BlockEntityInfo` 는 private.
- 서버 내장 레지스트리 표(`registries/builtin-1.21.1.txt`, reports/registries.json 에서 뽑음)에는 블록 엔티티 종류가 없다. 코드 표 `BlockEntity.TYPES` 를 `builtIn` 에 넣었다.
- 시험 서버에서 청크 패킷을 다시 받으려면 Bukkit `World#refreshChunk(x, z)` 뒤 `server.tick()`.
- 범용 패킷(`PacketStruct`)은 값을 넣고 꺼낼 때 배열을 복사한다 — 청크 buffer 도 복사된다(리스너가 들을 때만).
