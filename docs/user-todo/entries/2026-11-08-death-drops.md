# 플레이어 사망 드롭 (death-drops) — 2026-11-08

`PlayerDeathEvent#getDrops` 가 던지던 문제를 고쳤다(`docs/feature-parity/compat-death-drops-2026-11-08.md`). 결정할 것은 없다. 직접 확인하면 좋은 것:

- 실제 무덤(grave)·keep-inventory 류 플러그인 jar 로 죽어 보기: 죽을 때 오류 로그가 없는지, 무덤에 들어간 아이템이 바닥에 중복으로 안 떨어지는지.
- 사망 드롭이 이제 `PlayerDropItemEvent` 를 부르지 않는다(Paper 와 같다 — 이전엔 불러서 취소하면 인벤토리로 돌아왔다). 드롭 감시 플러그인이 사망 드롭을 못 보게 되는 것이 의도된 동작인지 확인.
- 남은 차이(취소·몹 사망 드롭·`getItemsToKeep` 등)는 위 문서의 "아직 다른 점" 참고.
