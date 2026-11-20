# T-247 Orebfuscator 길 채우기에서 알게 된 것 (2026-11-20)

- **블록 엔티티 갱신 패킷 종류 표.** 서버 `BlockEntity.hasUpdatePacket()` 은 "이식한 종류만 켠다" 였는데, 신호기·모닥불·전달체·장식 단지가 빠져 있었다.
  - 바닐라 1.21.1 에서 `getUpdatePacket` 을 직접 가진 클래스는 15개다(Paper jar 를 javap 로 훑음): Banner, Beacon, Bed, Brushable, Campfire, Conduit, DecoratedPot, Jigsaw, Sign(걸린 표지판은 상속), Skull, Spawner, Structure, TheEndGateway, TrialSpawner, vault/Vault.
  - 새 종류를 이식할 때 이 표와 맞춘다.
- **같은 상태 `setBlock` 은 아무것도 보내지 않는다.** `setBlockStateInChunk` 가 같은 상태면 -1 을 돌려준다(바닐라 `LevelChunk.setBlockState` 가 null). 그래서 바닐라에서 `sendBlockUpdated(pos, s, s, 3)` 인 곳을 `setBlock(pos, s, 3)` 으로 옮기면 클라이언트 갱신이 사라진다. 모닥불 `markUpdated`·`cookTick` 이 그랬다. `BlockLevel.sendBlockUpdated(pos)` 를 쓴다.
- **공유 로더와 라이브러리 형.** 호환 계층 클래스는 공유 로더에 있고, Guava·fastutil·DFU 는 그 패키지를 부르는 플러그인에만 붙는다. 그러니 shim 의 필드 형이나 생성 경로에 그 형을 두면 안 된다. 그것을 부르는 플러그인만 닿는 메서드 안에만 둔다(`StateDefinition.getPossibleStates` 는 `volatile Object` 칸에 담아 둔다).
