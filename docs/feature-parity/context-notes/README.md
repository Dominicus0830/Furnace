# context-notes 새 절 쓰는 법 (병합 충돌 방지 규칙)

**한 줄 규칙: 새 작업의 맥락 기록(바닐라 대조 결과·남은 차이·함정)은 `docs/feature-parity/context-notes.md` 맨 아래에 붙이지 말고 `docs/feature-parity/context-notes/<YYYY-MM-DD>-<묶음 id>.md` 새 파일로 쓴다.**

- 이유: 모든 PR 이 `context-notes.md` 끝에 절을 붙여 같은 자리에서 병합 충돌이 났다.
- 형식은 기존 절과 같다: `## 2026-11-07 제목 (묶음 id)` 로 시작하는 절 하나.
- 기존 절(2026-11-07 이전에 `context-notes.md` 에 있는 것)은 그대로 둔다. 다른 문서가 "context-notes.md 'M21'" 처럼 가리키는 경우, 새 파일에 있으면 "context-notes/2026-11-07-m21.md" 로 쓴다.
- 이미 있는 절을 고쳐야 할 때는 `context-notes.md` 의 해당 절만 최소한으로 고친다.
