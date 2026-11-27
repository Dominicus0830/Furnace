# 전체 껍데기 기계 채우기 1 — 레코드·열거형 값·상수·단순 생성자/읽기 (T-262, 2026-11-23)

## 한 일
- 바닐라 jar(Mojang 이름)에서 **값만** 뽑는 추출기 `FillExtractor`(vanilla-extract 새 모드 `fill-table`)와 표 형식 `FillTable`. 몸통의 명령은 저장하지 않고 값·필드 이름·인자 자리만 남긴다(PacketLayout·본문 지문표와 같은 선례). 표는 캐시에만 두고 커밋하지 않는다.
- 전체 껍데기 생성기가 표를 읽어 채운다(`Fills` 계획 + `FullShells` 몸통). 채운 멤버는 `@NmsShellMember` 표지가 없어 연결기가 "있음"으로 본다.
- 공통 형식: 채움 표 줄 5종, 영역 파일 `fill-areas.txt`, 이름 없는 기준선 `fills-baseline-<판>.txt`, 바닐라 대조 시험 `FillParityTest`. 형식 설명은 `furnace-compat-paper/src/shimspec/README.md` 마지막 절.

## 채우는 것
| 종류 | 알아보는 몸통 | 채운 모양 |
|---|---|---|
| 단순 생성자(P) | 상위 호출(인자 자리·리터럴) 뒤 `this.f = 인자|리터럴` 만 | 같은 모양 그대로 |
| 단순 읽기(G) | `return this.f` | 같은 모양 |
| 정적 리터럴(S) | 정적 초기화의 `리터럴; putstatic`, 그 필드 대입이 클래스 전체에 하나뿐 | 정적 초기화에서 대입 |
| 열거형 상수(V) | `new E; dup; 이름; 서수; 리터럴 인자…; invokespecial; putstatic` | 상수를 같은 인자로 만듦(전 상수가 리터럴일 때만) |
| 레코드 기본 메서드(T) | `ObjectMethods` 부트스트랩 | javac 와 같은 부트스트랩 |

채우지 않는 것(모양이 다르면 껍데기로 남음): 검증 있는 레코드 생성자, 계산·호출이 섞인 생성자, 필드 초기화에 호출이 있는 클래스, 상수 중 하나라도 리터럴 아닌 인자(예: 람다·다른 클래스 상수)가 있는 열거형, 상수 몸통이 있는 추상 열거형.

## 검증
- 바닐라 jar 에서 추출: 줄 9,581(생성자 3,406 · 읽기 4,235 · 정적 8 · 열거형 상수 1,189 · 기본 메서드 743; 상수 336개는 리터럴 아닌 인자라 `!`).
- 이 컨테이너에서(Paper 서버 표 대신 **바닐라 서버 jar 로 만든 대용 표** 사용 — 클라우드는 Paper 표를 못 만든다): 껍데기 4,675 NMS 중 채움 — 생성자 1,875 · 읽기 1,925 · 정적 2 · 열거형 129개(상수 504) · 레코드 기본 메서드 15. 전부 올리기·초기화 오류 0.
- `FillParityTest`(바닐라 jar 를 격리 로더에 올려 같은 입력으로 견줌): 채운 클래스 1,862, 열거형 상수 504, 객체 1,682, 읽기 528, 레코드 검사 589, **불일치 0**. 이 시험이 처음 돌 때 상수 몸통이 있는 열거형(`CopyCustomDataFunction$MergeStrategy`)에서 생성자만 채우고 상수는 0 인자로 만들어 읽기가 null 을 돌려주는 틀린 채움을 잡았고, 그 뒤 "열거형은 전부 채우거나 전혀 안 채운다" 규칙(고정점)으로 고쳤다.
- 숫자는 대용 표 기준이다. **실제 Paper 표 기준 숫자·지문·기준선은 노트북에서 쓴다**(아래).

## 짧은 값 몸통 (T-263 S0, 2026-11-27)

- 표 형식 2: `M 소유자 이름 서술자 s|i 종류 값` 줄 — K 리터럴 반환, E 빈 void, A 인자·`this` 반환, R 정적 필드 반환(명령 두 개, 빈 몸통은 하나가 전부일 때만; try 없음). 인터페이스의 default·정적 메서드도 뽑는다.
- 네 곳: 추출기 `FillExtractor.valueBody`, 표 `FillTable.Method`, 계획 `Fills`(정적 여부가 Paper 와 같을 때, R 은 그 필드가 살아 있을 때 — 연결기 세계 `LinkWorld.liveStaticField`, 상위 사슬에 초기화 덫이 있으면 채우지 않음), 대조 `FillParityTest.checkValueBody`.
- 껍데기만이 아니라 **손 shim 에 합치기(T-261)가 더하는 멤버**에도 쓴다(`ShellMerge.valueBody`). 생성기가 쓴 멤버 목록을 jar 의 `META-INF/furnace/full-shells/value-bodies.txt` 에 넣고 대조가 그 목록을 본다.
- 첫 측정(Paper 서버 서명표, 바닐라∩Paper 표): 껍데기 662(K 338·E 178·R 94·A 52) + 손 shim 1,561. 대조 1,395개 일치(인자·this 동일성 37, 정적 필드 객체 225), 대조 못 함 551(추상·인터페이스), 손 shim 정적 초기화가 서버 필요 277(소리 등 R), 실패 0.

## 남은 일 / 다음
- 실제 Paper 표로 `tools/compat/sig-tables.sh` → `./gradlew -PskipOracle=true -PfullShellsFpWrite=true -PfullShellsBaselineWrite=true :furnace-compat-paper:generateFullShells` 로 `fill-tables-1.21.1.fp` 와 `fills-baseline-1.21.1/`(영역 파일)를 쓰고 커밋(2026-11-23 노트북에서 했다). 그 전에는 CI vanilla-gates 의 전체 껍데기 단계가 지문·기준선 불일치로 실패한다(의도 — 표가 바뀌면 알려 주는 관문).
- 후속 종류 후보(각각 위 README 의 네 곳 규칙): 리터럴 배열, 다른 열거형 상수 참조 인자, 인터페이스 상수 표, Paper 가 바꾼 클래스(지금은 바닐라 jar 에서만 뽑음 — Paper 전용·CraftBukkit 은 안 채워짐).

## 검토 반영 (2026-11-23, 변경 요청 4건)
1. 정적 리터럴은 정적 초기화의 첫 분기·switch·throw·try 앞 직선 구간에서만 받는다(삼항·if·try·반복 안 대입은 표에 없음 — `FillExtractorTest`).
2. 채움 표를 Paper 서버 jar 에서도 뽑아(`fill-table-paper.tsv.gz`) 두 표에서 **줄이 같은 것만** 채운다. 한쪽만 있으면 채우지 않는다.
3. 기준선을 영역별 파일(`fills-baseline-<판>/<영역>.txt`)로 나누고 CI 는 채운 수가 줄 때만 실패한다. 채움 표 지문은 서명표 지문과 따로(`fill-tables-<판>.fp`). 추출기를 바꿀 때만 노트북 재생성이 필요하다.
4. `FillParityTest`: JDK 참조 인자에 자리마다 다른 객체를 넣어 동일성으로 견주고(읽기 1,817개·참조 390개 대조, 전엔 528), 레코드 `equals` 도 견주며, 종류별 최소 대조 수·`noVanillaClass` 상한을 단언한다. 죽은 식 정리, 초기화 덫 클래스는 기준선 채움 수에서 뺀다.
