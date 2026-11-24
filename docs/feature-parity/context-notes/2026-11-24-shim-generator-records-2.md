# shim 생성기: 레코드 직접 쓴 메서드 구분·손 상위 아래 던지는 몸통 (2026-11-24)

- 잠금 `X` 줄: 서명표에서 equals/hashCode/toString 이 final 이 아니면(자동 생성은 `public final`) 바닐라가 직접 쓴 것. 생성기는 이런 레코드의 조각에 그 메서드 선언이 없으면 멈춘다. 이 규칙이 잡은 기존 오류: `RecipeHolder`(바닐라는 id 만 비교)·`MessageSignature`(바닐라는 `Arrays.equals`/`Arrays.hashCode`/Base64 문자열) — 이전 손 shim 은 자동 생성 몸통이라 달랐다. 둘 다 조각에 바닐라 바이트코드대로 썼다(Paper 서버 jar 를 javap 로 읽음).
- `stubs()`: 상위 사슬에 손 shim 이 있어도, 그 손 shim 소스에 같은 이름 선언이 없으면 던지는 몸통을 만든다(Container 가족·Sign 의 `CommandSource` 문제). 조각이 구현한 상위 메서드는 `implementedByFragment` 로 인식해 건너뛴다.
- 영역 1(블록)이 올린 중첩 열거형 문제(`CommandBlockEntity$Mode` 등)는 재현 자료만으로 원인을 못 가렸다: 명세에 `class …$Mode`(상수 전부)를 적고 `shimLock` 다음 컴파일하는 순서로 다시 시도해 결과를 알려 달라.
