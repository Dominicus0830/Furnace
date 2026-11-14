# T-141 물결 1 — C: Player 비행·제목·가상 블록, Block 빛·드롭·breakNaturally (2026-11-14)

FAWE 2.16.2 가 부르는데 Furnace 에 없던 Player·Block 메서드. 기준은 Paper 1.21.1 CraftPlayer·CraftBlock 본문(Youer 1.21.1 사본)과 Paper `33ff3dff` 패치.

## Player
- `setFlying(true)` 는 허가(mayfly)가 없으면 `IllegalArgumentException("Player is not allowed to fly (check #getAllowFlight())")`. 값이 바뀔 때만 능력 패킷을 보낸다(Paper "Only refresh abilities if needed").
- `setAllowFlight(false)` 는 날던 것도 끈다. 값이 같아도 능력 패킷을 보낸다. 게임 모드를 바꾸면 그 모드 값으로 돌아간다(바닐라 updatePlayerAbilities).
- `sendTitle(t, s, in, stay, out)`: 시간 패킷 → 제목(null 이 아니면) → 부제목(null 이 아니면). 글은 레거시 문자열의 **첫 줄만**(CraftChatMessage.fromString(s)[0]).
  2인자 판은 10/70/20. `resetTitle` = clear_titles(reset=true).
- `sendBlockChange(Location, BlockData)`: block_update 한 장, 월드 검사 없음(Paper 와 같다). 끊긴 플레이어면 아무것도 안 한다.
- 미룸: `setViewDistance`(Furnace 에 플레이어별 시야 상한이 없다 — Paper 1.21.1 은 moonrise 로 플레이어 불러오기 거리를 바꾼다), `sendBlockChange(Location, Material, byte)`(옛 데이터 값).

## Block
- `getLightLevel` = 바닐라 getMaxLocalRawBrightness(하늘빛 − 하늘 어두움, 블록빛 중 큰 값). `getLightFromSky`·`getLightFromBlocks` 는 원래 값.
- `getDrops(tool)`: tool 이 null 이면 도구 검사 없이 빈손으로 전리품 표를 굴린다. 아니면 CraftBlockData.isPreferredTool(도구가 필요 없는 블록 또는 맞는 도구)일 때만.
- `breakNaturally(tool, triggerEffect, dropExperience)` 순서(Improve-Block-breakNaturally-API + Properly-handle-experience-dropping 패치 반영):
  1. 블록이 `minecraft:air` 가 아니고(동굴·공허 공기는 통과) 도구 조건이 맞으면 `dropResources(…, dropExperience=false)` → 효과(불이면 1009, 아니면 2001) → dropExperience 면 `popExperience(getExpDrop)`.
  2. `removeBlock(pos, false)`(그 자리 유체를 남긴다) → 바뀌었으면 `Block.destroy`.
  3. 드롭 조건이 맞았으면 얼음(살얼음 포함)은 afterDestroy(아래가 막혔거나 액체면 물, 뜨거운 차원이면 사라짐), 거북알은 decreaseEggs(하나만 줄인다).
  4. 반환 = 칸이 바뀌었고 드롭 조건도 맞았음.
- **함정: Youer 사본은 2번째 패치(dropResources(…, false))가 빠져 있다.** 그대로 옮기면 dropExperience=false 여도 광석·스포너가 경험치를 준다.
- 경험치 양을 따로 묻도록 서버 `BlockBehavior.expDrop`(Paper getExpDrop)을 두고, 광석·스컬크 셋·스포너의 spawnAfterBreak 는 그것을 써서 popExperience 한다(난수·순서는 바닐라 그대로).
- `getState(boolean)` 은 getState() 와 같다(Furnace BlockState 는 블록 엔티티 내용을 담지 않는다).
- 모두 소유 region 스레드 전용(아니면 ThreadOwnershipException), 청크가 없으면 IllegalStateException.

## 시험의 한계
- 시험 서버에는 바닐라 전리품 표가 없어 드롭 내용은 늘 비어 있다. breakNaturally 의 도구 조건은 반환값으로 재고, 내용은 서버 VanillaBlockLootTest(로컬 바닐라 데이터)가 같은 길을 잰다.
  **getDrops 의 도구 조건(맞지 않는 도구면 빈 목록)은 어느 시험도 재지 못한다**(전리품 표가 없으면 맞는 도구도 빈 목록) — PR #370 검토 R5, 바닐라 데이터를 시험 서버에 붙일 수 있을 때 보강.
- 경험치 양·난수 순서는 서버 BlockExperienceDropTest(고정 시드, 바닐라 공식과 대조)가, "한 번만 준다"는 compat experienceGivenOnce(스컬크 촉매 합 5)가 잰다.
- 하늘빛: 하늘이 없는 차원(네더·엔드)은 ChunkLevel.getSkyLight 가 늘 0(바닐라 DUMMY 층) — 전에는 빛 자료가 없거나 높이 위 끝이면 15 였다(검토 R4).
