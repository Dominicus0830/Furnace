## 2026-11-08 Bukkit 플러그인 메시징 (H4 묶음 E)

- 설계·차이: `docs/feature-parity/compat-h4e-messaging-2026-11-08.md`. 처음엔 기억 기반(Spigot 기준)으로 짰고, 독립 검토가 Paper 1.21.1 패치 목록(raw.githubusercontent.com, Paper main 커밋 `33ff3dff`)으로 대조해 예약 채널·길이 한도를 Paper 규칙으로 고쳤다. 나머지 기억 기반 부분 — `validateAndCorrectChannel` 의 두 방향 바꿈, `CraftPlayer.sendPluginMessage` 의 "클라이언트가 알린 채널만" 거르기, `PlayerChannelEvent` 가 처리기 목록을 가진 구조, custom_payload 를 주 스레드에서 처리하는 점.
- 서버 쪽 변화: `PluginChannels` 가 알린 순서를 지키고(`add`/`remove` 가 실제로 바뀐 채널을 돌려준다), `ConnectionHandler` 가 play 단계 register/unregister 마다, 구성 단계 채널은 play 진입 때 `PlayerJoinEvent` 직전에 `PlayerChannelEvent` 를 부른다. 위 context-notes.md "플러그인 채널 등록" 절의 "받은 메시지는 등록과 관계없이 PluginMessageEvent" 는 Furnace API 그대로이고, Bukkit 쪽은 받는 채널을 등록한 플러그인의 리스너만 부른다.
- 함정: 시험용 Recorder jar 는 `config.yml` 이 있어야 켜진다(`saveDefaultConfig`). 연결 스레드가 넘긴 전역 작업은 `TestPlayer#sync()` 의 `onGlobal` 이 기다려 준다.
