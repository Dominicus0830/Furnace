## 2026-11-08 minecraft: 접두 바닐라 명령 (P1 후속, namespaced-commands)

PR #153 의 후속. Paper 1.21.1 은 바닐라 명령마다 `minecraft:<이름>` 노드를 만든다(Paper `Brigadier-based-command-API.patch` 의 `Commands` 생성자). 그 동작을 맞췄다. 원문은 동작 비교용으로만 읽었다.

- **만드는 법.** `BuiltinCommands.registerAll` 끝에서 바닐라 루트 명령(별칭 tp·tell·w·tm 포함 전부) 이름마다 `minecraft:<이름>` 리터럴을 더한다. 실행(`executes`)·권한 조건(`requires`)은 **리다이렉트를 끝까지 따라간** 대상 노드의 것을 그대로 쓰고, 리다이렉트도 그 노드를 가리킨다(Paper 주석: Brigadier 는 중첩 리다이렉트를 못 푼다 — `minecraft:tp` 는 `tp` 가 아니라 `teleport` 로). 같은 노드를 가리키므로 게임 규칙 추가처럼 나중에 대상 자식이 바뀌어도 따라간다. 바닐라 명령만 대상 — Furnace 전용·플러그인 명령은 기존대로 `플러그인:이름` 만 갖는다. `execute run`·`return run`·`/execute` 안은 루트로 리다이렉트하므로 `execute run minecraft:say x` 도 된다.
- **권한.** 별칭의 `requires` 는 대상과 같은 객체라 보이는 사람·실행 가능한 사람이 원래 명령과 정확히 같다(레벨 규칙). Paper 의 `minecraft.command.<이름>` Bukkit 권한 노드는 Furnace 에 없다 — 이 차이는 PR #153 노트의 "바닐라 명령 거절 문장" 항목과 같은 뿌리.
- **`commands.send-namespaced`(새 키, 기본 true = Paper).** Paper `spigot.yml commands.send-namespaced` 에 해당. true 면 접속자 명령 트리에 별칭이 실리고 이름 자리 자동완성에도 나온다. false 면 클라이언트 트리의 루트 `이름공간:이름`(minecraft:·플러그인:)과 접속자 패킷 응답의 이름 자리(첫 낱말) 자동완성에서 `:` 든 후보를 뺀다(콘솔 자동완성은 거르지 않는다)(Paper `fillUsableCommands`·`Don-t-tab-complete-namespaced-commands…patch`) — **서버에서는 계속 실행**된다. 새 옵션 기본을 바닐라와 같게 둔다는 R4 규칙의 예외: 코디네이터 지시가 "Paper 가 내는 접두 명령을 낸다"였다.
- **/help.** 바닐라 `/help` 는 루트 목록을 그대로 출력하므로 별칭을 걸러 이름당 한 줄로 둔다(바닐라 `/help` 의 목록은 클라이언트에 보이는 한 줄이 적을수록 읽기 좋아 걸렀다. Paper 의 바닐라 help(`/minecraft:help`)는 접두 줄도 보여 주므로 이 부분은 Paper 와 다른 의도적 선택이다 — 원문으로 Bukkit HelpMap 을 확인하지 않았다). `/help minecraft:tp` 같은 리다이렉트 노드는 바닐라 `/help tp` 처럼 빈 결과.
- **Bukkit.dispatchCommand.** `CraftServer.dispatchCommand` 는 이미 `commands.hasCommand(이름)` 으로 트리를 보므로 코드 변경 없이 `minecraft:give ...` 가 true 로 돈다(대소문자 무시, 빈 칸 정리 — PR #153 규칙). 모르는 접두(`bukkit:say`)는 false + 바닐라 오류.
- **남은 차이.** ① Bukkit `CommandMap` 은 바닐라 명령을 모른다(`getCommand("minecraft:give")` null — Paper 는 VanillaCommandWrapper). ② `PlayerCommandSendEvent` 가 목록에서 `minecraft:` 이름을 지우는 플러그인 호환은 호환 층의 기존 한계 그대로. ③ Paper 설정 가져오기는 `commands.send-namespaced` 를 아직 옮기지 않는다(SPIGOT `commands` 전체가 unsupported 로 선언) — 후속.
- **시험.** `NamespacedCommandsTest`(server: 모든 바닐라 루트에 별칭·리다이렉트·권한 객체 동일, 실행, 권한별 트리, 자동완성, /help, send-namespaced 끄기), `VanillaCommandTreeTest`(별칭 건너뛰기), `CommandParityTest.minecraftPrefixedVanillaCommandsDispatchLikePaper`(compat).

- **알려진 작은 차이(독립 검토).** ① Paper(Spigot) 거르기는 모든 단계의 `:` 든 자식을 빼지만 Furnace 는 루트만 뺀다(플러그인 하위 리터럴에 `:` 가 있을 때만 다름). ② 이름이 `minecraft` 인 플러그인의 명령 등록 경고 문구가 실제와 다를 수 있다(Bukkit 의 예약 이름 규칙은 원문 미확인) — 후속.

- **후속 처리(command-parity follow-ups).** ① send-namespaced=false 가 이제 Spigot `fillUsableCommands` 처럼 **모든 단계**의 ':' 든 자식(하위 리터럴 포함)을 클라이언트 트리에서 뺀다(`CommandTreePacket.collect`). ② 이름이 `minecraft` 인 플러그인은 `minecraft:이름` 이 바닐라 별칭과 겹쳐 둘 다 등록되지 않으므로, 그 경우 경고가 "'이름' 과(와) 'minecraft:이름' 이(가) 둘 다 이미 있어 등록하지 않는다" 로 나온다(예전엔 'minecraft:이름 로만 등록한다' 로 거짓이었다). Bukkit 예약 이름 규칙 자체는 원문을 보지 않았다. ③ `PlayerSignCommandPreprocessEvent.getSign()` 은 **건너뜀** — 호환 층에 Bukkit `BlockState`/`Sign` 이 없어(`Block.java` 주석: BlockState 아직 없음) 싸게 추가할 수 없다. 면은 `getSide()` 로 알 수 있다. 시험: `PluginCommandNamespaceTest`.

