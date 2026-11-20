# 콘솔 메시지 규칙 상세 (2026-11-16)

`CLAUDE.md` 의 "콘솔 메시지" 줄의 전문이다. 콘솔 문구·`Lang` 키·호환 계층 문구를 건드릴 때만 읽는다.

- 운영자가 콘솔에서 보는 `log.info/warn/error` 문장은 한글·영어를 코드에 직접 적지 않고 `furnace-lang/en.properties`·`ko.properties` 에 키를 넣어 `Lang.t("키", 인자…)` 로 쓴다.
- 예외: 바닐라·Paper 와 문구가 같은 줄, 예외 메시지, DEBUG 는 영어 그대로 코드에 둔다.
- 호환 계층(`furnace-compat-paper`)은 서버 `Lang` 을 볼 수 없어 `CompatLang.t("키", 인자…)` 와 `furnace-compat-lang/{en,ko}.properties` 를 쓴다(`CompatLangTest`).
- `warnOnce(…)` 같은 보조 메서드도 한글 문구를 직접 받지 않고 키를 받는다.
- 아직 안 옮긴 파일(모듈/경로)은 `furnace-server/src/test/resources/lang-migration-pending.txt` 에 있고, 옮기면 그 줄을 지운다.
- `LangBundlesTest` 가 `HangulTextScanner` 로 server·compat·api 전체를 본다 — 로그뿐 아니라 명령 응답·보고서·메트릭 설명 등 예외·assert·DEBUG 밖의 한글 문자열 전부.
