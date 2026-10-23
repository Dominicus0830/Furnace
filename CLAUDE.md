# Furnace 작업 규칙

Furnace 는 처음부터 새로 쓰는 멀티스레드 Minecraft 1.21.1 서버(Java 21 / Gradle)다. 목표는 바닐라/Paper 와 관측 결과가 똑같으면서 더 빠른 서버다.

## 시작할 때
1. `docs/handoff/2026-10-18-session-handoff.md` 를 읽는다. 특히 2장(확정된 결정), 3장(규칙), 8장(다음 할 일).
2. 현재 다음 작업은 **4-2 컨테이너 메뉴 프레임워크**다.
3. `LLM-Wiki/` 는 개발 지식 베이스다. `wiki/Furnace/` 에 과거 작업 기록이 있다.

## 규칙 (요약, 상세는 인수인계 문서 3장)
- 사용자에게는 한국어로 말한다. 커밋 메시지와 코드 주석도 한국어, 간결하게.
- 커밋 전에 `ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:test` 가 통과해야 한다. 백그라운드로 돌리고 로그를 기다린다. gradle 은 동시에 두 개 돌리지 않는다.
- 작업 브랜치는 세션에 지정된 브랜치만 쓴다. PR 은 사용자가 요청할 때만 만든다. 푸시 출력은 숨기지 않고 그대로 보여 준다.
- 포팅은 직접 한다(포팅 서브에이전트 금지). 바닐라 코드는 난수 호출 순서까지 그대로 이식한다.
- Mojang 바닐라 데이터·디컴파일 소스는 커밋하지 않는다. Mojang/papermc 호스트 접근을 우회하지 않는다.
- 커밋 메시지·코드·PR 에 모델 ID 를 넣지 않는다.

## graphify (코드 구조 지도)
- 지도 위치: `graphify-out/` (커밋되는 것은 `graph.html`, `GRAPH_REPORT.md` 뿐. `graph.json` 과 캐시는 무시됨). 스캔 제외 목록은 `.graphifyignore`.
- **코드 위치·연결 관계를 찾을 때는 파일을 뒤지기 전에 먼저 `graphify query "<질문>"`, `graphify path "A" "B"`, `graphify explain "X"` 를 쓴다.** `graph.json` 이 없으면 아래 방법으로 다시 만든다.
- 포팅할 코드의 내용은 지도가 아니라 **직접 읽는다.** 지도는 어디를 읽을지 알려 줄 뿐이다.
- **갱신은 매우 큰 작업 하나(로드맵 단계 하나, 예: 4-2 전체)를 끝냈을 때만 한다.** 커밋마다, 작은 수정마다 돌리지 않는다. git hook 도 설치하지 않는다.
- 갱신 방법: `pip install graphifyy` 후 `graphify update .` (코드는 AST 라 LLM 비용 없음). 새로 생긴 문서까지 반영하려면 `/graphify . --update`. 갱신 후 `graph.html`, `GRAPH_REPORT.md` 를 커밋한다.
- 기계 생성 데이터(블록 상태/속성 표, 레지스트리 목록, 월드젠 fixture)는 의미 추출하지 않는다.
