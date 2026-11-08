# Bukkit SimplePluginManager (H4 다음 장벽) — 2026-11-08

helper 가 막히던 `SimplePluginManager` 를 넣었고, helper 5.6.14 가 처음으로 켜져 `/helper` 명령까지 돈다(서버 제공 라이브러리 캐시를 준 상태). 설계·Bukkit 과 다른 점: `docs/feature-parity/compat-h4-simplepluginmanager-2026-11-08.md`.

- **직접 확인(권장):** helper 를 쓰는 실제 플러그인 jar 를 주시면(결정 8) `-PextraJars` 로 켜 보고 다음 장벽을 재겠습니다. 정적 분석으로는 helper 쪽 기능을 부를 때 BossBar·ArmorStand·인벤토리 클릭 이벤트·Scoreboard 가 먼저 막힐 것으로 보입니다.
- **결정(작음): 동기 이벤트를 "주 스레드" 밖에서 부를 때.** Paper 는 `callEvent` 에서 `... may only be triggered synchronously.` 로 던집니다. Furnace 는 게임 이벤트가 region 스레드에서 와서, 그대로 따라 하면 리스너 안에서 부르는 플러그인 자체 이벤트가 모두 실패합니다. 후보: ① 지금처럼 막지 않기(추천), ② Paper 처럼 던지기. 답이 없으면 ①. (비동기 이벤트를 주 스레드에서 부르는 쪽은 Paper 처럼 막습니다.)
- **참고:** LuckPerms 같은 권한 플러그인은 `SimplePluginManager` 의 권한 지도와 플레이어의 `PermissibleBase` 를 리플렉션으로 바꿔 끼웁니다. 지도는 이제 있지만 Furnace 권한 계산에는 닿지 않습니다 — 권한 플러그인 지원은 따로 묶음으로 열어야 합니다(권한 첨부 `addAttachment` 도 아직 없습니다).
