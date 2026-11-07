# 다중 월드 (W1, 불만 표 38·42행)

Folia 에서 깨진 "월드 만들기·내리기"를 Furnace 의 region 스케줄러 위에서 되살린다. 기본 세 차원(오버월드·네더·엔드)은 지금과 같고,
추가 월드는 실행 중에 올리고 내린다. 차원별 시드는 기본 꺼짐(바닐라: 모든 차원이 같은 시드).

## 1. 플러그인 API

```java
Furnace server = Furnace.server();
WorldCreator spec = WorldCreator.named("mining")              // 폴더 = <서버 폴더>/mining/
        .environment(WorldEnvironment.NORMAL)                 // NORMAL · NETHER · THE_END
        .generator(WorldGenerator.DEFAULT)                    // DEFAULT · LARGE_BIOMES · AMPLIFIED · FLAT · VOID
        .seed(12345L)                                         // 비우면 주 월드 시드
        .build();
server.createWorld(spec).thenAccept(world -> {
    // world.dimension() == "minecraft:mining", world.name() == "mining"
});
// 나중에(플레이어를 다른 월드로 옮긴 뒤)
server.unloadWorld(world, true).thenAccept(ok -> { /* 플레이어가 있거나 취소되면 false */ });
```

| 항목 | 규칙 |
|---|---|
| 이름 | 영문·숫자·`_ . -` 1~64자. 주 월드 이름과 `<주 월드>_nether`·`<주 월드>_the_end`(Paper 가져오기 폴더), `plugins` 같은 서버 폴더는 쓸 수 없다. 대소문자만 다른 두 월드는 동시에 못 올린다 |
| 레벨 키 | 기본 `minecraft:<소문자 이름>`(Paper `WorldCreator` 와 같다). `.key("myplugin:mining")` 로 바꿀 수 있다. 바닐라 세 키·이미 쓰는 키는 거절 |
| 폴더 배치 | Paper·CraftBukkit 과 같다: NORMAL → `<이름>/region`, NETHER → `<이름>/DIM-1/region`, THE_END → `<이름>/DIM1/region`. `entities/`·`poi/`·`data/` 도 같은 자리. `<이름>/level.dat` 에 시드·스폰·드래곤 전투·설명서(`furnace:world`, 바닐라·Paper 는 무시하는 키)가 남는다 |
| 이미 있는 폴더 | 그 level.dat 의 시드가 이긴다(Bukkit 과 같다). 생성기·환경은 넘긴 설명서를 쓴다. 같은 이름이 이미 올라와 있으면 같은 설명서일 때 그 월드를 돌려주고, 다르면 실패 |
| 차원 타입 | 비우면 환경의 바닐라 타입(`minecraft:overworld`·`the_nether`·`the_end`). `minecraft:overworld_caves` 와 플러그인이 부트스트랩에서 등록한 타입(W2, [bootstrap-registries.md](bootstrap-registries.md) 6장)도 된다. 꺼진 플러그인의 타입은 거절 |
| 높이 | 노이즈 생성기(DEFAULT·LARGE_BIOMES·AMPLIFIED)는 그 계열 원래 높이(오버월드 -64..320, 네더·엔드 0..256) 타입만 받는다(바닐라와 같은 지형을 지키려고). FLAT·VOID 는 아무 높이나 된다 |
| 스레드 | `createWorld`·`unloadWorld` 는 아무 스레드에서 불러도 되고 바로 future 를 돌려준다. 준비(폴더 잠금·level.dat·생성기)는 월드 전용 스레드에서 차례로 돈다. 결과 future 는 전역 스레드에서 끝난다 |
| 이벤트 | `WorldLoadEvent`(틱을 켠 뒤), `WorldUnloadEvent`(취소 가능, 이때 월드 안에 플레이어는 없다). 기본 세 차원에는 오지 않는다(그때는 `ServerLoadEvent`) |
| 내린 월드 | 그 손잡이의 `isLoaded()` 는 false, 블록·엔티티 작업은 실패로 끝난다. 같은 이름을 다시 올리면 새 손잡이다 |
| 찾기 | `server.world("minecraft:mining")`(키), `server.worldNamed("mining")`(폴더 이름), `server.worlds()`(오버월드 → 네더 → 엔드 → 추가 월드 올린 순서) |

`World` 손잡이에 `seed()`(그 월드 시드), `dimensionType()`(타입 키), `environment()`, `isLoaded()` 가 있다.

월드는 만든 플러그인이 꺼져도(핫 리로드 포함) 그대로 올라와 있다(Bukkit 과 같다). 월드가 플러그인 코드를 쥐지 않으므로 클래스로더가 새지 않는다.
플러그인 차원 타입으로 만든 월드도 그 플러그인이 꺼진 뒤 계속 돈다(타입은 비활성으로만 표시되고 지워지지 않는다 — W2 규칙).

### 내리기 순서

전역 스레드에서 플레이어·오는 중인 차원 이동 확인 + `WorldUnloadEvent` → 목록에서 떼기(그 뒤로 명령·텔레포트가 이 월드를 못 찾는다) → 틱 끄기 →
(save 면) 모든 dirty 청크·엔티티·POI 저장(저장 역압 무시) + 진행 중 청크 읽기 대기 → IO 레인 비우기 → region 작업 스레드 닫기 →
저장소 닫기 → level.dat → 폴더 잠금 풀기. 플레이어가 있거나 그 월드로 오는 중이면 내리지 않는다(Bukkit `unloadWorld` 와 같다 — 먼저 옮길 것). 그래도 차원을 바꾸는 바로 그 순간에 내려지면 그 플레이어는 오버월드 스폰으로 간다.

## 2. 운영자

- 명령 `/worlds list | create <이름> [normal|nether|the_end] [default|large_biomes|amplified|flat|void] [시드] | load <이름> | unload <이름> [true|false]`
  (권한 3). 들어가기는 바닐라 `/execute in minecraft:<이름> run tp @s 0 100 0`.
- `furnace.properties`
  - `worlds.autoload=mining,lobby` — 서버 시작 때 그 폴더의 level.dat 설명서로 다시 올린다(플러그인 onLoad 뒤·onEnable 앞). 기본 없음.
  - `dimension-seed.the_nether=`, `dimension-seed.the_end=` — 기본 네더·엔드의 생성 시드(불만 표 42행). 비우면 주 월드 시드(바닐라).
    숫자 또는 글자(글자는 바닐라 `level-seed` 처럼 hashCode). 이미 만든 청크는 그대로라 도중에 바꾸면 경계가 생긴다. 이 값은 설정에만 있고 level.dat 에는 남기지 않는다.

## 3. 바닐라·Paper 와 같은 것 / 다른 것

- **같은 것**: 기본 세 차원과 그 폴더, 시드를 정하지 않았을 때의 지형. 차원 키에 따라 다른 동작(포털 목적지 — 추가 월드의 지옥문은 네더로,
  엔드 포털은 엔드로, 오버월드 전용 스포너인 팬텀·순찰·고양이·포위·상인은 `minecraft:overworld` 만)은 바닐라처럼 키로 정한다.
  차원 **타입**에 따라 다른 동작은 바닐라처럼 타입 값으로 정한다: 침대(`bedWorks`)·리스폰 앵커(`respawnAnchorWorks`)·피글린 좀비화(`piglinSafe`)·
  습격(`hasRaids`)·영원히 타는 블록(`infiniburn`)·지옥문 좀비 피글린(`natural`)·몬스터 스폰 밝기(`monsterSpawnLight*`)·고정 시각(`fixedTime`).
  예전에는 이 중 여러 개를 차원 키(`minecraft:the_nether` 등)로 정해 두어 기본 세 차원에서만 맞았다. 엔드 타입 월드는 드래곤 전투를 가진다(바닐라 ServerLevel).
- **Paper 와 다른 것**: 추가 월드는 시간·날씨·게임 규칙·난이도·월드 보더를 오버월드와 공유한다(바닐라 데이터 팩 차원과 같은 방식 —
  Paper 는 월드마다 따로). 추가 엔드 월드의 드래곤 전투 기록은 그 월드 level.dat 에 따로 남는다(CraftBukkit 과 같다).
- 접속 중인 클라이언트의 차원 이름 목록(명령 제안용)은 접속할 때 받은 것이라, 그 뒤 올린 월드는 다시 접속해야 `/execute in` 제안에 나온다(Paper 와 같다).
- 시드를 정한 월드는 클라이언트에 보내는 hashed seed(바이옴 경계 섞기)도 그 시드로 보낸다 — 서버 쪽 바이옴 조회와 같게.

## 4. 아직 없는 것

- 플러그인 생성기(청크 생성 단계 훅): W3.
- 평지의 구조물·지형지물, 빈 월드의 시작 발판(바닐라 "The Void" 프리셋의 `void_start_platform`).
- 월드별 시간·날씨·게임 규칙(Paper 방식) — 필요하면 기본 꺼짐 옵션으로 더한다.
