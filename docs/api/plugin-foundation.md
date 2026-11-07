# 플러그인 기반 (B1)

이벤트 버스·로더 주변 규약. 전부 바닐라 동작에는 영향이 없다.

## 이벤트 발화 스레드
- `Event.isAsync()` — 기본 false. 동기 이벤트는 그 칸·엔티티를 가진 region(또는 전역) 스레드에서 불린다. 다른 region 것을 바로 만지면 `ThreadOwnershipException`.
- 비동기 이벤트(`super(true)`)는 어느 스레드에서든 불릴 수 있으니 처리기는 월드·엔티티를 스케줄러로 넘겨 만진다.
- 우선순위 합성: MONITOR 처리기가 `Cancellable` 의 취소 상태를 바꾸면 처리기마다 한 번 경고한다(다른 우선순위는 검사 비용 없음).

## 디스패치
`@Subscribe` 메서드는 `LambdaMetafactory` 호출 지점으로 만든다(비공개·상위 클래스 메서드 포함, 실패하면 `MethodHandle`). 구독자가 없으면 이벤트 객체를 만들지 않는 기존 규칙은 그대로.

## 자동 정리
플러그인이 꺼지면 코어가 구독·예약 작업·명령·권한 첨부·플러그인 채널을 해제한다. 그 밖의 자원(열린 메뉴·핸들)은 `FurnacePlugin.own(AutoCloseable)` 로 맡기면 `onDisable` 뒤 등록 역순으로 닫힌다(꺼진 뒤에 맡기면 바로 닫는다).

## furnace.properties 키
| 키 | 기본 | 뜻 |
|---|---|---|
| `plugins.fail-fast` | false | 플러그인을 불러오거나 켜다 하나라도 실패하면 서버 시작을 멈춘다 |
| `plugins.data-root` | (빈 값) | 쓰면 데이터 폴더가 `<data-root>/<이름>/` 으로 갈라진다. 설정 폴더 `configFolder()` 는 늘 `plugins/<이름>/` |
| `plugins.log-level.<이름>` | INFO | 플러그인별 로그 수준(JUL 이름). 실행 중에는 `PluginManager.setLogLevel` |
| `plugins.dev-mode` | false | 개발 모드. 켜야 플러그인 핫 리로드가 된다(아래 B8) |
| `plugins.dev-mode.auto-reload` | false | 개발 모드에서 `plugins/*.jar` 가 바뀌면 알아서 다시 읽는다 |
| `plugins.libraries.*` | enabled=false | 설명 파일 `libraries`(Maven 좌표) 자동 로딩 — [plugin-libraries.md](plugin-libraries.md) |

플러그인 로그 줄에는 MDC `plugin` 이 붙는다(logback 패턴 `%X{plugin}`).

## API 버전
설명 파일 `api-version`("1.0")을 서버 `ApiVersion.CURRENT` 와 비교한다: 없으면 경고, 서버보다 새 API 를 요구하면 불러오지 않는다, 지원 하한보다 낮으면 경고. 폐기는 `@DeprecatedApi(since, removeIn, replacement)` 로 표시하고(최소 두 마이너 유지), 폐기된 이벤트에 처리기를 달면 한 번 경고한다.

## 콘솔
콘솔 입력 스레드는 월드를 불러오기 전에 열린다. 그때까지 `stop` 은 바로 종료하고, 나머지는 줄을 세워 두었다가 서버가 준비되면 들어온 순서대로 실행한다.

## 핫 리로드 (B8, 개발 모드 전용)
결정 D4: 운영 서버의 `/reload` 는 만들지 않고, `plugins.dev-mode=true` 일 때만 플러그인 하나를 다시 읽는다. 기본값이면 아무것도 바뀌지 않는다(명령도 보이지 않고 jar 도 그대로 읽는다).

- 쓰는 법: 콘솔·권한 3 이상 `/plugins reload <이름>`, 코드에서는 `PluginManager.reload(name)`(전역 스레드에서 돌고 다시 켠 이름 목록을 future 로 준다). `plugins.dev-mode.auto-reload=true` 면 jar 를 덮어쓸 때 알아서 다시 읽는다(1초마다 크기·수정 시각을 보고, 두 번 연달아 같아야 — 복사가 끝나야 — 읽는다).
- 순서: 새 jar 의 설명 파일 확인(읽을 수 없으면 지금 것을 그대로 둔다) → 대상과 거기 기대는 플러그인을 켠 순서의 반대로 끄기(위 "자동 정리" 전부 + 플러그인 레지스트리 몫·수명주기 훅) → 새 클래스로더로 부트스트랩·`onLoad`·`onEnable`(켠 순서) → 옛 클래스로더는 100틱 뒤에 닫는다(다른 스레드에서 끝나 가는 옛 작업이 클래스를 마저 읽게).
- 함께 다시 읽는 플러그인: `depend`·`softdepend` 로 대상을 적은 것, 대상의 `loadbefore` 에 적힌 것, 그리고 적지 않았어도 실제로 대상 jar 의 클래스를 가져다 쓴 것(클래스로더가 기록한다). 그래야 옛 클래스를 쥔 채 남는 플러그인이 없다. 관계없는 플러그인은 건드리지 않는다.
- 불러오지 않은 이름이라도 `plugins/` 에 그 이름의 jar 가 있으면 새로 불러오고, jar 가 없어졌으면 내리기만 한다.
- jar 사본: 개발 모드는 `plugins/.dev-cache/` 에 복사한 jar 를 읽는다. 원본을 켜 둔 채 덮어써도(윈도우 파일 잠금 포함) 옛 로더가 깨진 jar 를 읽지 않는다. 시작·종료 때 비운다.
- 누수 경고: 옛 클래스로더를 문맥 로더로 가진 스레드가 살아 있으면 바로, 15초 뒤에도 옛 로더가 회수되지 않으면 경고한다(다른 플러그인의 static 필드, 끝내지 않은 스레드, ThreadLocal 등이 원인).
- 한계: 데이터 팩 찾기 훅으로 알린 팩 내용은 다음 `/reload`(데이터 팩) 때 바뀐다. 다시 읽기 직전에 다른 region 스레드에서 돌던 옛 작업은 끝까지 옛 코드로 돈다. 플러그인이 서버 밖(파일·DB)에 남긴 상태는 플러그인 몫이다.
