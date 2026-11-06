# 부트스트랩 단계와 플러그인 레지스트리 (B3)

불만 표 9번(레지스트리 고정 전 플러그인 코드 실행)과 33번(플러그인끼리 공유하는 정의 레지스트리)을 받는 바탕이다.
데이터 주도 등록(D2: 몹 변종·마법부여·게임 규칙, W2: 바이옴·차원 타입)은 여기 수명주기 훅을 더하는 식으로 붙는다.

## 1. 서버 시작 순서

| 순서 | 하는 일 | 플러그인 쪽 |
|---|---|---|
| 1 | 설정 읽기 | |
| 2 | **플러그인 부트스트랩**: jar 찾기 → 의존 순서 → api-version 검사 → 클래스로더 → `bootstrapper` 호출 | `PluginBootstrap.bootstrap(ctx)` |
| 3 | 블록·아이템·동기화 레지스트리 읽기, 월드 생성기 준비 | |
| 4 | 데이터 팩 찾기·켜기(태그·레시피·전리품·발전 과제·함수) | `LifecycleEvents.DATAPACK_DISCOVERY` 처리기 |
| 5 | 월드·차원 준비 | |
| 6 | 메인 인스턴스 만들기(`createPlugin` 또는 인자 없는 생성자) → `onLoad` | `FurnacePlugin.onLoad` |
| 7 | 스폰 준비 → `onEnable` → 함수 파싱 → 접속 받기 | `FurnacePlugin.onEnable` |

- 찾기·순서·api-version·부트스트랩 단계의 실패도 `plugins.fail-fast`(B1) 판단에 들어간다. 플러그인별 로거 수준(`plugins.log-level.*`)은 부트스트랩부터 적용된다.
- 부트스트랩 중에는 `Furnace.server()`·월드·플레이어·스케줄러가 없다(`IllegalStateException`).
- `bootstrapper` 가 던지면 그 플러그인은 불러오지 않고, 그 플러그인이 등록한 레지스트리·항목·훅을 모두 되돌린다. 그 플러그인을 `depend` 로 둔 플러그인도 빠진다(`softdepend` 는 그대로 뜬다).
- 수명주기 훅 등록은 자기 부트스트랩이 도는 동안만 된다(그 뒤에는 `IllegalStateException`). 플러그인이 꺼지면 훅도 떨어진다.

## 2. 설명 파일

```yaml
name: Sample
version: 1.0.0
main: org.domi.furnace.plugintest.SamplePlugin
bootstrapper: org.domi.furnace.plugintest.SampleBootstrap   # 없어도 된다
```

## 3. 플러그인 데이터 팩

```java
ctx.lifecycle().register(LifecycleEvents.DATAPACK_DISCOVERY, packs -> packs.discover("datapack", "main"));
```

- jar 안 `datapack/pack.mcmeta` 가 있어야 한다(바닐라 폴더 팩과 같은 메타데이터·형식 검사·오버레이).
- 팩 id 는 `plugin/<namespace>/<id>`, `/datapack list` 에는 `(plugin)` 출처로 보인다.
- 켜기 규칙은 월드 `datapacks/` 팩과 같다: 처음 보이면 자동으로 켜지고, `/datapack disable` 로 끄면 `level.dat` 의 Disabled 에 남아 다음 시작에도 꺼져 있다. 그래서 플러그인 팩 하나는 같은 내용을 `world/datapacks/` 에 넣은 것과 결과가 같다.
- `/reload`·`/datapack` 이 팩을 다시 찾을 때마다 처리기가 다시 불린다. 플러그인이 꺼진 뒤에는 보이지 않는다.

## 4. 플러그인 레지스트리

```java
// 만드는 쪽(Core): 부트스트랩에서 만들고, onLoad 에서 고정
PluginRegistry<Ability> r = ctx.registries().create("core:abilities", Ability.class);
// 넣는 쪽(Addon, depend: [Core]): 부트스트랩이나 onLoad 에서
ctx.registries().registry("core:abilities", Ability.class).register("addon:fly", new Ability("fly"));
```

- 보기(`PluginRegistries`)는 한 플러그인에 묶여 있다 — `BootstrapContext.registries()` 와 `FurnacePlugin.registries()` 는 같은 저장소의 같은 플러그인 보기다.
- 키는 `namespace:path`(바닐라 리소스 위치 규칙). 만드는 레지스트리와 넣는 항목의 namespace 는 자기 플러그인 namespace(`PluginDescription.namespace()` = 이름 소문자, 공백 → `_`)여야 한다. 그래서 남의 항목을 덮거나 뺄 수 없다.
- 값은 레지스트리를 만들 때 정한 타입으로 넣을 때 검사한다. 상위 타입 보기로 꺼내 읽을 수 있다.
- 고정(`freeze`)은 만든 플러그인만 한다. 고정 뒤에는 넣기·빼기가 안 된다.
- 꺼질 때 정리: 그 플러그인이 만든 레지스트리는 통째로 사라지고(`isRemoved`), 남의 레지스트리에 넣은 항목은 고정돼 있어도 빠진다. 빠지는 항목은 `RegistryListener.removed` 로 알린다.
- 아무 스레드에서 써도 된다. 읽기는 잠금 없는 스냅숏, 쓰기는 레지스트리 단위 잠금이다.

## 5. 버전 중립 (D2·D5)

- 훅 종류(`LifecycleEventType`)·키는 문자열 리소스 위치이고 마인크래프트 내부 타입을 드러내지 않는다. 마인크래프트 버전이 바뀌어도 이름은 유지하고, 없어지는 단계는 폐기 기간(두 마이너)을 거친다.
- 플러그인 데이터 팩은 데이터 팩 형식(`pack_format`)으로 버전을 가리고, 바닐라와 같은 형식 범위·오버레이 규칙을 따른다.

## 6. 아직 없는 것(다음 묶음)

- 게임 레지스트리(마법부여·바이옴·차원 타입·몹 변종)에 항목을 더하는 훅: 지금 동적 레지스트리는 `registries/1.21.1.txt` 고정 목록이라 D2·W2 가 데이터 팩에서 읽게 바꾸면서 `LifecycleEvents` 에 훅을 더한다.
