# tools/vanilla-data

바닐라 데이터 추출 도구는 **`tools/vanilla-extract`**(Gradle 모듈, V-05)로 옮겼다. 쓰는 법은 `tools/vanilla-extract/README.md`.

이 폴더에는 `server-1.21.1/`(번들러 jar 의 내부 서버 jar 를 풀어 둔 데이터 폴더 — 태그·레시피·전리품·월드젠 JSON, 언어 파일, `version.json`)만 남는다.
바닐라 jar 와 생성 결과물은 커밋하지 않는다(Mojang EULA, 저장소 규칙 7). 커밋하는 것은 추출된 리소스와 도구뿐이다.

옛 난독화 이름 도구(`Export*.java`, `extract_*.py`, `tools/compat/protocol-ids-extract.py`)는 git 기록에 있다(마지막 위치 커밋 `802ccb451`).
