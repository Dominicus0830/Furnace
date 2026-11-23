# Paper 서명표를 로컬에서 만들고 쓰는 법 (2026-11-23)

서명 관문(`signatureGate`)·shim 잠금(`shimLock`/`shimLockCheck`)은 기준 서명표가 있어야 돈다. 클라우드 세션엔 **Mojang 이름 Paper 서버 표**가 없어(paperweight 가 dev-bundle 을 못 받는 곳) 둘을 못 돌린다. 데스크탑 PC(DOMINICUS)에는 있다.

## 위치
- 서명표 캐시: `C:\Users\gagam\.cache\furnace-vanilla-extract\1.21.1\` — `sig-table.tsv.gz`(바닐라), `sig-table-paper-api.tsv.gz`, `sig-table-paper-server.tsv.gz`(Mojang 이름 Paper 서버), `sig-table-libs.tsv.gz`. **커밋하지 않는다**(Mojang 대응표 조건).
- 표의 원료: paperweight 캐시 `C:\Users\gagam\.gradle\caches\paperweight-userdev`(여기서 `:worldgen-oracle:printPaperServerJar` 가 서버 jar 경로를 낸다).

## 쓰는 법 (저장소 루트에서, 모두 `-PskipOracle=true` 는 표를 다 만든 뒤 gradle 에만)
```
bash tools/compat/sig-tables.sh 1.21.1                                   # 표 셋+Paper 서버 표를 캐시에 만든다(처음 한 번, 판이 바뀌면 다시)
./gradlew --offline -PskipOracle=true :vanilla-extract:signatureGate     # 서명 관문(새 차이 0 이어야 함)
./gradlew --offline -PskipOracle=true :furnace-compat-paper:shimLock     # 명세(*.spec)를 고친 뒤 잠금 파일 다시 쓰기
./gradlew --offline -PskipOracle=true :furnace-compat-paper:shimLockCheck
```
`sig-tables.sh` 는 Paper 서버 칸만 오라클을 켠다(스크립트가 알아서). 콘솔이 깨지면 `JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8`. 자세한 규칙: `tools/vanilla-extract/README.md` "서명 관문", `furnace-compat-paper/src/shimspec/README.md`.

## 작업 요령
- PR 브랜치는 별도 worktree 로 받아 작업한다(본인 작업 폴더를 건드리지 않는다). 예: `git worktree add ../Furnace-sig<번호> -b sig<번호> origin/<PR 브랜치>`, 끝나면 `git worktree remove`.
- 바닐라(Paper)에서 추상인 메서드는 손 shim 에서 `default` 로 바꾸지 말고 `*.spec` 에 이름만 적어 추상으로 둔다(잠금을 다시 쓰면 생성 하위 클래스가 던지는 몸통을 받는다). 클래스 상위 사슬이 바닐라와 다르면 기준선 예외 대신 껍데기 조각(`src/shims/java`)으로 맞춘다.
