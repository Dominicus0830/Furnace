# 영역 5 루트 표 fill·getRandomItemsRaw (2026-11-27)

- 짝 목록(g4 5-level-chunk-storage) 근거: `LootTable#fill(Container, LootParams, long)`·`getRandomItemsRaw(LootParams, Consumer)` → 서버 `LootTable#fill`·`getRandomItemsRaw`(같은 일 확인: 난수 순서 전리품 → 빈 칸 섞기 → 나누기 → 아이템 섞기, 가득 차면 경고 후 멈춤).
- 다리: `ChunkInternals.lootRollRaw`·`lootFill`(칸 내용 목록 → 같은 길이의 새 내용). 건드리지 않은 칸은 받은 객체 그대로(호출자가 같은 객체인지로 알아본다).
- 빈 스택: raw·fill 은 빈 스택을 그대로 둔다(getRandomItems 만 걸러 냄 — 바닐라와 같다).
- 아직 안 되는 것: `LootContext`·`RandomSource` 를 받는 굴리기(LootContext shim 이 없다).
