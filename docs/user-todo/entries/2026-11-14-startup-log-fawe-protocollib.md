# 시작 로그(FAWE·ProtocolLib) 후속 — 2026-11-14

분석·수정 기록: `docs/feature-parity/context-notes/2026-11-14-startup-log-fawe-protocollib.md`.

## 도미니코가 확인할 것
1. 이 PR 병합 뒤 같은 플러그인 4개로 다시 띄워, 아래가 사라졌는지 본다: FAWE·ProtocolLib "Server reload detected", ProtocolLib `ConversationCanceller` 오류, ProtocolLib "unable to check for updates", NMS 클래스 경고 9줄(→ INFO 한 줄), FAWE 다른 버전 어댑터 경고.
2. 남는 것은 정상이다: FAWE `BlockTypesCache` AIOOBE·`WorldInitEvent` SEVERE(둘 다 FAWE NMS 어댑터가 없어서 생김, Paper 에서도 어댑터가 없으면 같다), 첫 실행 `server.properties` ERROR(바닐라와 같은 문구).
3. 게임 안에서 ProtocolLib `/filter add ...` 처럼 대화로 입력받는 명령이 동작하는지(채팅이 다른 사람에게 퍼지지 않아야 한다).

## 다음 작업 후보 (결정 필요 없음, 순서 제안)

Tag API·`AsyncTabCompleteEvent`·`PlayerCommandSendEvent` 는 PR #337 이 이미 넣었다("doesn't support Tags" 와 FAWE 탭 완성도 함께 확인).

1. FAWE NMS 어댑터(N 계열): `DataComponentPatch.CODEC` 부터 FAWE 보고서의 없는 심볼 330개. 이것이 풀려야 FAWE 가 실제로 동작한다.
2. `Player.spigot()`·`Server.spigot()`(이번에 `SpigotConfig` 가 생겨 Spigot 판별을 통과하는 플러그인이 이어서 부를 수 있다).
3. 추가 월드의 `WorldInitEvent`(WorldLoadEvent 는 이미 알린다).
