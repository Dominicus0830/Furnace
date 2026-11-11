## ChunkSection.compact() nonAirCount 를 isAir 로 (2026-11-11)

- 바닐라 1.21.1 `LevelChunkSection`(dvj): `setBlockState`(dvj.a(IIILdtc;Z))와 `recalcBlockCounts`(방문자 dvj$a)는 둘 다 `BlockState.isAir()`(dtc.i)로 비공기 칸을 센다. air·cave_air·void_air 셋 모두 isAir=true 다 (바이트코드 확인).
- Furnace `ChunkSection.setBlock`·`replaceBlocks`·`PaletteStorage.loadIndexed` 는 이미 `isAir` 였지만, 월드젠이 scratch 배열을 열었다가 되돌리는 `compact()` 만 `value != AIR` 로 세서, 동굴 공기·공허 공기 칸을 비공기로 셌다. 동굴 공기만 있는 섹션이 `nonAirCount=4096`(`hasOnlyAir()==false`)이 되어 바닐라(0)와 달랐다. 호출 경로: `ChunkColumn.compactSections()` ← `ChunkPipeline`·`NoiseWorldSource`.
- 수정: `compact()` 도 `!isAir(value)`. 회귀 시험 `ChunkSectionNonAirCountTest`(compact·replaceBlocks·setBlock 세 경로가 같은 값, 동굴/공허 공기만 있는 섹션은 비어 있음).
- 참고(고치지 않음, 동작 영향 없음): `ChunkColumn` 의 상수 이름 `CAVE_AIR_STATE=12958`·`VOID_AIR_STATE=12959` 는 반대다(실제로는 12958=void_air, 12959=cave_air). 둘 다 공기로만 쓰여 결과는 같다.
