# 스폰 청크와 빈 차원 규칙 — 결정·확인 (2026-11-12)

플레이어가 없어도 스폰 청크(게임 규칙 `spawnChunkRadius`, 기본 2)의 레드스톤이 바닐라 1.21.1 처럼 돈다. 함께 바닐라의 "빈 차원 15초 뒤 엔티티·블록 엔티티 쉬기"도 들어갔다(자세한 대조: `docs/feature-parity/context-notes/2026-11-12-spawn-chunks-empty-time.md`).

## 정할 것 (작업을 막지 않음)

1. **접속자 없는 차원의 몹·호퍼를 15초 뒤 멈출까?** 바닐라는 멈추고(플레이어도 `/forceload` 도 없을 때), Spigot·Paper 1.21.1 은 기본으로 늘 돌린다(CraftBukkit `flag1 = true || …`, Paper `unsupported-settings.disable-world-ticking-when-empty` 기본 false). furnace.properties `world.tick-when-empty` 로 고를 수 있게 했다: `vanilla` | `always` | `auto`.
   추천: **기본 `auto`** — Bukkit 플러그인이 없으면 바닐라대로 멈추고, 있으면 Paper 처럼 늘 돈다(NPC·홀로그램·미니게임 대기 월드가 Paper 와 같이 동작). 지금 기본값도 `auto` 다.
2. **추가 월드(`<이름>/`, Bukkit createWorld)에도 스폰 청크를 둘까?** 바닐라는 오버월드만, Paper 는 월드마다(자기 게임 규칙으로) 둔다. 지금 Furnace 는 바닐라대로 주 오버월드만이다.
   추천: **추가 월드에도 켠다**. 주 네더·엔드는 바닐라대로 두지 않는다. 고르면 작은 후속 PR 로 한다.

## 직접 확인하면 좋은 것
- 서버를 켜고 아무도 접속하지 않은 채 스폰 근처 관측기 시계·호퍼를 콘솔로 놓았을 때 바닐라 서버와 같은지(시계는 돌고 호퍼는 15초 뒤 멈춤 — Bukkit 플러그인이 없을 때).
