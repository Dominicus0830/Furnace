# Furnace 작업 규칙

Furnace 는 처음부터 새로 쓰는 멀티스레드 Minecraft 1.21.1 서버(Java 21 / Gradle)다. 목표는 바닐라/Paper 와 관측 결과가 똑같으면서 더 빠른 서버다.

## 시작할 때
1. `docs/handoff/START.md`(4KB 요약판)만 먼저 읽는다. 큰 문서(옛·새 인수인계서, `context-notes.md` 342KB, `graphify-out/`, `docs/user-todo.md`)는 **통째로 읽지 않는다.** START.md 의 표대로 필요한 절만 줄 범위로 읽거나 `grep -n` 으로 찾는다(통째 읽기는 세션마다 십수만 토큰을 낭비한다).
2. 다음 작업은 지시받은 것을 따른다. 현황은 `git log`·열린 PR·`docs/user-todo/entries/` 최근 파일에서 본다(문서에 박힌 현황은 낡는다). 접속 시험은 오프라인 서버 + mineflayer 봇(`tools/bot-test/README.md`, 결과 `docs/feature-parity/bot-connection-test-*.md`).
3. `LLM-Wiki/` 는 개발 지식 베이스다. `wiki/Furnace/` 에 과거 작업 기록이 있다. 찾는 주제가 있을 때만 grep.

## 규칙 (요약, 상세는 인수인계 문서 3장)
- 사용자에게는 한국어로 말한다. 커밋 메시지와 코드 주석도 한국어, 간결하게.
- 커밋 전에 `ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-api:test :furnace-server:test` 가 통과해야 한다(CI 는 이 외에 샘플 플러그인 컴파일·호환 계층·fatJar 도 돌린다 — `docs/ci.md`). 백그라운드로 돌리고 로그를 기다린다(로그는 통째로 출력하지 말고 `tools/ci/log-summary.sh <로그>` 로 결과·실패만 본다). gradle 은 동시에 두 개 돌리지 않는다.
- 작업 브랜치는 세션에 지정된 브랜치만 쓴다. PR 은 사용자가 요청할 때만 만든다. 푸시 출력은 숨기지 않고 그대로 보여 준다.
- 병렬 워커로 일할 때는 인수인계서 6장을 따른다: 워커는 격리 worktree 에서 일하고 푸시하지 않으며, 독립 검토는 읽기 전용(`git show`/`git diff`)으로 하고, 병합은 하나씩 `git branch --show-current` 로 브랜치를 확인한 뒤 컴파일과 전체 시험을 거친다.
- 포팅은 직접 한다(포팅 서브에이전트 금지). 바닐라 코드는 난수 호출 순서까지 그대로 이식한다.
- Mojang 바닐라 데이터·디컴파일 소스는 커밋하지 않는다. Mojang/papermc 호스트 접근을 우회하지 않는다.
- 커밋 메시지·코드·PR 에 모델 ID 를 넣지 않는다.
- **콘솔 메시지(2026-11-16):** 운영자가 콘솔에서 보는 `log.info/warn/error` 문장은 한글·영어를 코드에 직접 적지 않고 `furnace-lang/{en,ko}.properties` 에 키를 넣어 `Lang.t("키", 인자…)` 로 쓴다(호환 계층은 `CompatLang.t` + `furnace-compat-lang/`; 바닐라·Paper 와 같은 문구·예외 메시지·DEBUG 는 영어로 코드에 둔다). 안 옮긴 파일은 `lang-migration-pending.txt` 에 있고 옮기면 줄을 지운다. 상세·예외 전부: `docs/ops/console-lang.md`.
- **태그(2026-11-16):** 병합 묶음·마일스톤에 깃 태그(`base/<MC>/<UTC 날짜>-<N>`, `ms/<MC>/<슬러그>`)를 단다. 판 완성 태그 `<MC>` 는 도미니코의 판단 때만. 태그는 옮기거나 지우지 않고 기준 브랜치가 초록인 커밋에만 찍는다. 도구 `tools/release/furnace-tag.sh`, 규칙 전문 `docs/ops/version-tags.md`.
- **문서 충돌 방지(2026-11-07):** 사용자 확인·결정 항목은 `docs/user-todo.md` 를 고치지 말고 `docs/user-todo/entries/<날짜>-<묶음 id>.md` 새 파일로, 바닐라 대조·함정 기록은 `docs/feature-parity/context-notes.md` 끝에 붙이지 말고 `docs/feature-parity/context-notes/<날짜>-<묶음 id>.md` 새 파일로 쓴다(`README.md` 참고). 새 절을 같은 파일 같은 자리에 붙이면 PR 마다 병합 충돌이 난다.

## graphify (코드 구조 지도)
- **코드 위치·연결 관계는 파일을 뒤지기 전에 먼저 `graphify query "<질문>"`, `graphify path "A" "B"`, `graphify explain "X"`** 로 묻는다(`graph.html`·`GRAPH_REPORT.md` 는 읽지 않는다). 포팅할 코드의 내용은 지도가 아니라 **직접 읽는다.**
- 갱신은 로드맵 단계 하나 같은 매우 큰 작업을 끝냈을 때만(커밋마다·hook 금지). 절차: `docs/ops/graphify.md`.

@AGENTS.md
