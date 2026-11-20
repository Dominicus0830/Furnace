# graphify(코드 구조 지도) 상세

`CLAUDE.md` 의 graphify 절에서 뺀 갱신 절차다. 지도를 갱신할 때만 읽는다.

- 지도 위치: `graphify-out/` (커밋되는 것은 `graph.html`, `GRAPH_REPORT.md` 뿐. `graph.json` 과 캐시는 무시됨). 스캔 제외 목록은 `.graphifyignore`. `graph.json` 이 없으면 아래 방법으로 다시 만든다.
- 갱신은 **매우 큰 작업 하나(로드맵 단계 하나, 예: 4-2 전체)를 끝냈을 때만** 한다. 커밋마다, 작은 수정마다 돌리지 않는다. git hook 도 설치하지 않는다.
- 방법: `pip install graphifyy` 후 `graphify update .` (코드는 AST 라 LLM 비용 없음). 새로 생긴 문서까지 반영하려면 `/graphify . --update`. 갱신 후 `graph.html`, `GRAPH_REPORT.md` 를 커밋한다.
- 기계 생성 데이터(블록 상태/속성 표, 레지스트리 목록, 월드젠 fixture)는 의미 추출하지 않는다.
- `graph.html`(1.3MB)·`GRAPH_REPORT.md`(97KB)는 읽지 않는다. `graphify query/path/explain` 으로 묻는다.
