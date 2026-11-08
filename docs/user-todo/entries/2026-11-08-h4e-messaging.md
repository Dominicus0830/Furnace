# Bukkit 플러그인 메시징 (H4 묶음 E) — 2026-11-08

Bukkit 의 `Messenger`·`PluginMessageListener`·`sendPluginMessage`·BungeeCord 채널·채널 등록 이벤트를 넣었다. 설계·Bukkit 과 다른 점: `docs/feature-parity/compat-h4e-messaging-2026-11-08.md`.

- **직접 확인(권장):** BungeeCord 나 Velocity(`bungee-plugin-message-channel = true`) 뒤에 Furnace 를 두고, BungeeCord 채널을 쓰는 Bukkit 플러그인(예: 서버 이동 메뉴 플러그인의 `Connect`, `PlayerCount`)을 한 번 써 보세요. 서버 이동이 되고 플레이어 수가 나오면 됩니다. 이 컨테이너에는 프록시가 없어 가짜 클라이언트로만 확인했습니다.
- **결정(작음):** 이상한 클라이언트 입력 처리. 채널을 128개 넘게 등록하거나 `:` 없는·대문자 채널 이름을 등록하는 클라이언트를 Paper 는 연결을 끊습니다(검토에서 Paper 1.21.1 패치로 확인). Furnace 는 128개를 넘는 것만 버리고, 잘못된 이름은 `minecraft:이름` 으로 고쳐 받습니다. 후보: ① 지금처럼 두기(추천 — 정상 클라이언트·모드에는 영향 없음), ② Paper 처럼 연결 끊기. 답이 없으면 ①.
- **참고:** 이 묶음 뒤 helper 의 다음 장벽은 `org.bukkit.plugin.SimplePluginManager`, CommandAPI 는 Spigot 이름 NMS(H7d 변환표)입니다.
