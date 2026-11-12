# Bukkit 플러그인 폴더에 넣으면 바로 작동 (H5 결정 반영) — 2026-11-12

결정(도미니코, 2026-11-12): "버킷 플러그인 폴더에 넣으면 바로 작동, 폴더가 비면 기능을 아예 안 켜서 최적화."

## 알림 (바뀐 것)

- **Spigot 이름 NMS 대응표가 기본으로 켜졌다.** `plugins.compat.nms.spigot-mappings` 기본이 `off` → `download`. Spigot 이름으로 컴파일된 NMS 플러그인(예: CommandAPI 일반 판, dynmap·TAB 의 1.21.1 모듈)을 `plugins/bukkit-plugins/` 에 넣기만 하면, 처음 켤 때 한 번 Mojang·Spigot 매핑을 받아 바꿔 올린다. 그런 플러그인이 없으면 받지 않는다.
- **기존 서버의 furnace.properties 도 옮긴다.** 예전 기본으로 자동 기록된 `plugins.compat.nms.spigot-mappings=off` 줄은 다음 기동 때 `download` 로 바뀐다(설정 형식 1 → 2, 원본은 `furnace.properties.v1.bak`). 일부러 끄고 싶으면 다시 `off` 로 적으면 그대로 둔다.
- **Bukkit 플러그인이 없으면 호환 계층은 아무것도 하지 않는다**(이미 그랬고, 이번에 대응표까지 시험으로 묶었다): 어댑터 클래스를 올리지 않고, 호환 jar 를 꺼내지 않고, 라이브러리·매핑을 받지 않는다. `plugins/bukkit-plugins/` 빈 폴더는 넣을 곳을 알려 주려고 만들어 둔다.

## 직접 확인하면 좋은 것

- 인터넷 되는 서버에 Spigot 이름 NMS 플러그인 하나를 `plugins/bukkit-plugins/` 에 넣고 처음 켰을 때 로그의 "Spigot BuildData <커밋> … 고정했다" 한 줄(2026-11-08-h7d 항목의 확인과 같다). 인터넷이 없으면 "받지 못했다" 경고 한 줄이 나오고 플러그인은 켜진다(그 NMS 호출만 실패).
