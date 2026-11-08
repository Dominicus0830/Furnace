# Paper 플러그인용 라이브러리 필요 시 내려받기 (H4 묶음 B) — 2026-11-08

Bukkit·Paper 플러그인이 쓰는 Guava·Adventure·SnakeYAML·Brigadier 등 13개 묶음을, 서버 jar 에 넣지 않고 그 플러그인이 있을 때만 받게 했다(결정 4·5 ②+③). 설계·측정: `docs/feature-parity/compat-h4b-libraries-2026-11-08.md`.

- **직접 확인(권장):** 인터넷이 되는 서버에서 Paper 플러그인(예: helper 를 쓰는 플러그인, CommandAPI)을 `plugins/bukkit-plugins/` 에 넣고 한 번 켜 보세요. 로그에 `서버 제공 라이브러리 [...] 을(를) 준비한다` 와 `... 를 Maven Central/Mojang 라이브러리 저장소에서 받는다` 가 나오고 `plugins/.libraries/` 에 jar 가 생기면 됩니다. 이 컨테이너는 Central 이 429, Mojang 저장소가 막혀 있어 실제 내려받기는 확인하지 못했습니다(캐시 경로는 확인).
- **확인:** 기본값 세 개 — `plugins.compat.libraries=needed`(쓰는 묶음만), `plugins.compat.libraries.mojang=true`(결정 ③), `plugins.compat.libraries.declared=true`(plugin.yml 의 `libraries:` 를 Paper·Spigot 처럼 받음 — R1 "기본 꺼짐"과 다르게 호환 플러그인만은 켜짐). 다르게 원하시면 알려 주세요.
- **결정(작음):** 결정 4의 "Paper API 클래스도 필요할 때만 받기". `org.bukkit.*` 클래스는 우리가 새로 쓴 호환 계층이라 받을 곳이 없고(진짜 paper-api 는 CraftBukkit 없이 안 돌고 이름이 겹친다), 크기도 0.6 MB 로 서버 jar 의 3% 입니다. 후보: ① 지금처럼 서버 jar 에 둔다(추천), ② 호환 모듈을 별도 jar 로 떼어 Bukkit 플러그인이 있을 때만 읽는다(내려받을 곳이 필요 — 예: Furnace 릴리스). 답이 없으면 ①.
- **참고:** helper·CommandAPI 는 라이브러리 장벽을 넘었지만 아직 켜지지 않습니다. 다음 장벽은 `PluginMessageListener`(묶음 E 플러그인 메시징) → helper 는 `SimplePluginManager`, CommandAPI 는 Spigot 이름 NMS(H7d 변환표).
