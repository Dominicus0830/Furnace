# T-015 섹션 팔레트 바로 쓰기 (2026-11-14)

- 전송(`ChunkSerializer`)·저장(`AnvilChunkCodec.toNbt`)이 섹션을 `int[4096]` 으로 펼치지 않고 스냅샷의 묶인 팔레트(`PaletteStorage` 읽기 사본)에서
  `FirstSeenIndex` 로 "첫 등장 순" 팔레트를 다시 매겨 쓴다. 저장 순이 이미 첫 등장 순이고 비트 수가 같으면 묶인 `long[]` 을 복사 없이 그대로 쓴다.
- 옛 경로(펼친 배열 → `Palette.write`·`blockStatesFromArray`)는 지우지 않았다: 펼친 배열만 가진 `Section`(공개 생성자, scratch 가 열린 섹션 사본)과
  직접 저장(블록 257종 이상·바이옴 9종 이상)이 탄다. 차분 시험 `PaletteDirectWriteParityTest` 의 오라클도 이 옛 경로다.
- **불변식**: 간접 저장소 팔레트의 값은 서로 겹치지 않는다(`idFor` 는 찾은 뒤 덧붙이고 `loadIndexed` 는 겹치는 NBT 값을 합친다). `FirstSeenIndex` 는
  번호가 다르면 값도 다르다고 보고 번호로만 센다 — 팔레트에 같은 값을 두 번 넣는 길을 새로 만들면 바이트가 달라진다.
- `FirstSeenIndex` 는 스레드별 하나다. `Section.blockIndex()`/`biomeIndex()` 결과는 그 스레드의 다음 호출 전까지만 쓴다(블록 쓰고 바이옴 부르기 순서).
- 바닐라와의 관계(옛 그대로, 이번에 바뀐 것 없음): 디스크는 바닐라 `PalettedContainer.pack` 처럼 첫 등장 순으로 압축한다(바이트 동일 목표). 전송은 바닐라가
  살아 있는 팔레트를 옛 항목째 그대로 보내는 데 비해 Furnace 는 첫 등장 순으로 압축해 보낸다 — 저장소 성장 이력이 바닐라와 달라 어차피 바이트가 같을 수 없고,
  클라이언트가 푸는 블록은 같다. "저장소 팔레트 그대로 보내기"는 Furnace 의 지금 바이트를 바꾸므로 하지 않았다(차분 시험의 변이 점검이 그 지름길을 잡는다).
- 섹션 쓰기 쪽(`ChunkSection`, T-064 얼린 구역 `ownSection`/`lightFrozen`, T-053 마스크 `attach`)은 건드리지 않았다. 스냅샷을 뜨는 시점(`readCopy`)도 같다.
