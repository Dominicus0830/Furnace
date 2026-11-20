## 2026-11-20 Paper BlockDestroyEvent — 서버가 블록을 부술 때 (T-224)

대조: Paper 1.21.1-133 jar(`Level#destroyBlock`, `Block#getExpDrop`·`spawnAfterBreak`·`popExperience`, `DropExperienceBlock` 등 경험치 블록 6종,
paper-mojangapi 의 `com.destroystokyo.paper.event.block.BlockDestroyEvent`). Paper jar 전체에서 이 이벤트를 부르는 곳은 `Level#destroyBlock` 하나다.

- **Paper 순서**: 공기면 거짓 → 유체 상태 → 경험치 `getExpDrop(state, level, pos, EMPTY, true)`(구독자가 없어도, `drop` 이 거짓이어도 뽑는다)
  → 처리기가 있으면(`HandlerList.getRegisteredListeners().length > 0`) 이벤트(블록, 새 상태 = 유체의 레거시 블록, 효과 블록 = 원래 블록, 경험치, drop)
  → 취소면 거짓 → 효과 블록이 불 종류(BaseFireBlock)가 아니고 playEffect 면 레벨 이벤트 2001(효과 블록 번호) → willDrop 이면
  `dropResources(원래 상태, …, 부순 엔티티, EMPTY, dropExperience=false)` 뒤 `popExperience(경험치, 엔티티)` → `setBlock(유체 레거시, 3, depth)` → 성공이면
  `BLOCK_DESTROY` 게임 이벤트(원래 상태).
- **Furnace**: 구독자(Furnace 처리기 또는 Bukkit 처리기 다리)가 있을 때만 위 순서 그대로, 없으면 바닐라 순서(효과 → 떨굼 안 `spawnAfterBreak` 에서 경험치).
  Paper 는 구독자가 없어도 경험치를 먼저 뽑아 경험치 블록(광석·스포너·스컬크)의 레벨 난수 차례가 바닐라와 다르다 — Furnace 는 플러그인이 듣지 않으면
  바닐라와 같고, 들으면 Paper 와 같다(T-204 `getFullStatus`·T-211 9.2 의 "바닐라가 정답" 기준과, 플러그인이 보는 값은 Paper 와 같아야 한다는 호환 기준을 함께 맞춤).
- 플레이어가 손으로 부수는 것(`ServerPlayerGameMode#destroyBlock` → `removeBlock`)은 이 길이 아니다(Paper 도 `BlockBreakEvent` 만).
  Furnace 서버에서 `Level#destroyBlock` 을 타는 길: 블록 종류 23곳(선인장·사탕수수·대나무·발판·거북알 등), `Block.updateOrDestroy`, 엔티티(파괴수·위더·양·토끼·좀벌레·
  마을 사람 수확), `/setblock`·`/fill … destroy`, 양동이, 플러그인 `destroyBlock`(Furnace API·NMS `Level#destroyBlock`). 월드 생성(`WorldGenRegion`)은 Paper 도 이벤트 없음.
- **효과 블록 바꾸기의 차이(의도)**: Paper 는 `(CraftBlockData) getEffectBlock()` 캐스트라 null 이면 서버 쪽 예외로 터진다. Furnace 다리는 null·남의 구현이면 효과 블록을 그대로 두고,
  Furnace API 의 `setEffectData(null)` 은 처리기 안에서 NPE. 서버가 모르는 블록 이름이면 경고(`plugin.block-unusable`) 뒤 원래 블록.
- 찾은 별개 구멍(이 묶음에서 고치지 않음): Furnace `ThrownPotion#onHitBlock` 에 바닐라 `dowseFire`(물 물약이 맞힌 칸과 네 옆 칸의 불을 `destroyBlock(pos, false, 물약)`,
  양초·모닥불 끄기)가 없다.
- 시험: `DestroyBlockEventOrderTest`(순서·난수·취소·효과 바꾸기, 시험 레벨), `PluginApiBlockDestroyTest`(플러그인 버스), 호환 `WorldChangeEventsTest#blockDestroyFromTheServer…`.
