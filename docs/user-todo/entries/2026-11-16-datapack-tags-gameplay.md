# 데이터 팩 태그 → 게임 동작 (2026-11-16)

데이터 팩이 블록·아이템·엔티티·피해·게임 이벤트 태그를 고치면 이제 서버 규칙(몹 매달림, 위더 면역 등)에도 반영되고 `/reload` 로 갱신된다. 근거: `docs/feature-parity/context-notes/2026-11-16-datapack-tags-gameplay.md`.

## 직접 확인할 것 (작업을 막지 않음)
1. 데이터 팩으로 `minecraft:climbable` 에 블록을 넣고(`data/minecraft/tags/block/climbable.json`, `{"values":["minecraft:stone"]}`) 서버에 접속해 그 블록에 붙어 오를 수 있는지(끊기지 않는지), 팩을 끄고 `/reload` 하면 다시 못 오르는지.
