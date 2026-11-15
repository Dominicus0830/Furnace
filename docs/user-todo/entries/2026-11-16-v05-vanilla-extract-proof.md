# V-05 데이터 추출 도구 — 노트북 증명 2건 (2026-11-16)

PR 은 **"클라우드 부분 완료, 노트북 증명 대기"** 상태다. 클라우드에서는 Mojang 호스트(piston-meta·piston-data)가 막혀 바닐라 jar·공식 대응표를 받을 수 없어 아래 둘을 못 했다. **1번 증명 전에는 병합하지 않는다**(수용 조건).
모듈 설명은 `tools/vanilla-extract/README.md`.

준비: 노트북(Dminicus_Laptop)에서 이 PR 브랜치를 받는다. Java 21, python(PATH), 인터넷이 필요하다. 이전 도구가 들어 있던 커밋 = `802ccb4515b92d955b24db9b11850248166012d2`.

| # | 해야 할 일 | 합격 기준 | 어디에 적나 |
|---|---|---|---|
| 1 | **1.21.1 결과가 이전 도구와 같은지**: `./gradlew -PskipOracle=true :vanilla-extract:run -Pmc=1.21.1 -Pmode=proof -PlegacyRef=802ccb4515b92d955b24db9b11850248166012d2` | 표의 모든 도구가 `같음`(전↔후), 포팅 점검의 다른 곳을 하나씩 판단해 이름 추측 오류 0 | PR 댓글에 표 전체 |
| 2 | **1.21.2 시험 실행**: `./gradlew -PskipOracle=true :vanilla-extract:run -Pmc=1.21.2 -Pmode=extract` (결과는 커밋 안 함) | 대응표로 이름이 풀리고 도구가 도는지 — 컴파일 오류·실행 오류를 도구별로 기록(U-02 재료) | PR 댓글에 도구별 성공/실패 |

## 1번이 처음에는 어긋날 것으로 예상되는 곳

도구 소스(`tools/vanilla-extract/src/main/resources/exporters/furnacetools/`)는 Mojang 이름을 **대응표 없이 기억으로 옮겨 쓴 것**이라 컴파일이 안 되거나 이름을 잘못 짚은 곳이 있을 수 있다. 그래서 `proof` 모드에 **포팅 점검**을 넣었다 — 옛 도구 바이트코드를 대응표로 풀어 "옛 코드가 실제로 부르던 Mojang 이름"을 그대로 보여 주므로, 새 도구의 `다름` 목록을 보고 이름을 고치면 된다(추측 필요 없음). 고치고 `-Pmode=port-check`(빠름)로 0 이 될 때까지 반복한 뒤 `proof` 를 한 번 돈다.

알려진 차이:
- `ExportBlockSoundTypes`·`ExportProtocolIds` 는 이전에 서버 바이트코드를 읽는 파이썬이었고 지금은 서버 클래스를 직접 부르는 Java 라서 포팅 점검 대상이 아니다. 전후 결과 비교만 본다(소리 종류 번호 순서는 달라 소리 내용으로 견준다).
- 이전 `ExportBlockAttrs.java` 는 커밋된 `blockattrs_1.21.1.txt` 와 형식이 다르다(그 파일은 옛 버전 도구로 만들어졌다). 합격 판정은 전↔후이고 커밋과의 차이는 참고로만 본다 — 커밋된 파일을 새로 쓸 때는 따로 정한다.
- 결과 머리말의 도구 경로는 옛 위치(`tools/vanilla-data/…`)를 그대로 적는다(커밋된 리소스와 글자까지 같게).

## 클라우드에서 이미 확인한 것

- 단위 시험 `:vanilla-extract:test` 24개 통과: 대응표 읽기(같은 난독화 이름의 필드·메서드를 서술자로 구분), 이름 풀기가 만든 jar 를 실제로 로드·실행(하위 클래스 이름으로 부른 부모 멤버, 람다 invokedynamic, 서명 파일 제거), 준비 단계 전체(가짜 번들러 → 이름 풀기 → 데이터 생성기 실행 → 캐시 재사용), 포팅 점검, 검증기(.gz·소리 내용 비교).
- 파이썬 도구 4개(`block-tags`·`tags`·`builtin-registries`·`damage-types`)는 저장소의 `tools/vanilla-data/server-1.21.1/data` 와 커밋된 `builtin-1.21.1.txt` 로 만든 입력으로 돌려 **커밋된 리소스와 바이트 동일**함을 확인했다. 나머지 둘(`block-definitions`·`item-components`)은 데이터 생성기 reports 가 필요해 노트북에서 본다.
