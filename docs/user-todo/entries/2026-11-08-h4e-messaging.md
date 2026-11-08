# Bukkit 플러그인 메시징 (H4 묶음 E) — 2026-11-08

Bukkit 의 `Messenger`·`PluginMessageListener`·`sendPluginMessage`·BungeeCord 채널·채널 등록 이벤트를 넣었다. 설계·Bukkit 과 다른 점: `docs/feature-parity/compat-h4e-messaging-2026-11-08.md`.

- **직접 확인(권장):** BungeeCord 나 Velocity(`bungee-plugin-message-channel = true`) 뒤에 Furnace 를 두고, BungeeCord 채널을 쓰는 Bukkit 플러그인(예: 서버 이동 메뉴 플러그인의 `Connect`, `PlayerCount`)을 한 번 써 보세요. 서버 이동이 되고 플레이어 수가 나오면 됩니다. 이 컨테이너에는 프록시가 없어 가짜 클라이언트로만 확인했습니다.
- **결정(확정, 도미니코 2026-11-08):** 이상한 클라이언트 입력은 Paper 와 같게 처리한다. 채널 128개 초과, `:` 없는·대문자·너무 긴 이름은 연결을 끊는다(`-Dpaper.disableChannelLimit` 으로 개수 제한 해제). 확인할 것 없음.
- **참고:** 이 묶음 뒤 helper 의 다음 장벽은 `org.bukkit.plugin.SimplePluginManager`, CommandAPI 는 Spigot 이름 NMS(H7d 변환표)입니다.
