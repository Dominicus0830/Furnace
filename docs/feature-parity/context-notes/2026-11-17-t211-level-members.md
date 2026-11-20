# T-211 3단계 — 서버 레벨 나머지 멤버 (2026-11-17)

설계: `docs/feature-parity/compat-nms-t211-world-surface-2026-11-17.md` 9·10장.

- **FAWE 가 이 멤버들을 부르는 곳은 거의 없다.** `FaweBlockStateListPopulator` 의 재정의(서버 레벨로 넘기기)와 `clearContainerBlockContents`(`Clearable`)뿐이다.
  지형지물·구조물 배치는 서버 코드가 돌리므로 populator 의 재정의를 부르지 않는다. 다른 플러그인·바닐라 꼴 코드를 위한 것이다.
- **Paper `destroyBlock` 은 바닐라와 난수 순서가 다르다.** 경험치(`getExpDrop`)를 맨 앞에서 뽑고(`drop` 이 거짓이어도), 떨굼은 경험치 없이 한 뒤 뽑아 둔 경험치를 낸다.
  바닐라는 떨굼 안(`spawnAfterBreak`)에서만 뽑는다. Furnace 서버는 바닐라 순서를 따른다(T-204 `getFullStatus` 와 같은 기준). `BlockDestroyEvent` 는 T-224.
- **서버 `Clearables.tryClear` 빈칸**: 주크박스·독서대가 빠져 있었고 장식 단지의 전리품을 풀지 않았다. 그래서 `/setblock` 으로 음반 든 주크박스를 바꾸면
  `onRemove` 가 음반을 떨궜다(바닐라는 먼저 비워 떨구지 않는다). 이제 레벨을 받아 바닐라 `clearContent` 대로 한다. 독서대 `setBook(EMPTY)` 은 `has_book` 을
  끄지 않는다(바닐라 그대로 — 그 뒤 블록을 바꾸므로 보이지 않는다). 열린 옛 PR #215(A13-02)가 같은 빈칸을 다른 방식으로 고치려 했다.
- **비노출 함정**: 주인이 아닌 스레드의 `getChunkNow`(= `getChunkIfLoadedImmediately`)는 공개본이 아직 없으면 null 이다(다음 틱부터 보인다). 시험은 주인 스레드에서 본다.
- **바이옴 확대 지터는 이웃 청크의 quart 를 고를 수 있다**(가장자리 두 칸). 시험 좌표는 로드된 청크 안쪽으로 잡는다. 로드되지 않은 이웃이면 `getUncachedNoiseBiome`.
- `FeatureFlagSet#hashCode` 는 fastutil `HashCommon.mix` 식(`x * 0x9E3779B97F4A7C15L`, `^>>>32`, `^>>>16`)을 그대로 옮겼다(호환 계층에 fastutil 이 없다).
