# 블록·레시피 플러그인 API (작업 묶음 D1, 2026-11-06)

Paper/Spigot 불만 표 34·35·36행. 모두 버전 중립 타입(블록은 레지스트리 키 + 속성 이름, 아이템은 레지스트리 키 + 컴포넌트 문자열)이라 1.21.1 이후 버전에서도 같은 값으로 쓴다.

## 1. `Block`(살아 있는 칸) / `BlockSnapshot`(복사)

| 타입 | 성격 | 스레드 |
|---|---|---|
| `Block` | 위치만 든 손잡이. 만드는 데 읽기가 없다. `data()`·`set()`·`setProperty()` 는 지금 상태를 본다 | 읽기·쓰기는 그 칸을 가진 region 스레드만(아니면 `ThreadOwnershipException`). 비동기판 `snapshotAsync()`·`setAsync()` 는 아무 스레드 |
| `BlockSnapshot` | 읽은 순간의 `world`·`pos`·`data` 불변 기록(예전 `Block` 레코드) | 아무 스레드 |

- `World.block(pos)` → `Block`. `World.blockAt(pos)`/`blockNow(pos)` 는 이제 `BlockSnapshot` 을 돌려준다.
- 이벤트가 들고 오는 블록(`PlayerInteractEvent.clickedBlock()`·`ProjectileHitEvent.hitBlock()`·`PlayerBedEnterEvent.bed()`)은 이벤트 시점의 `BlockSnapshot` 이다. 같은 칸의 살아 있는 손잡이는 `snapshot.live()`, `BlockEvent.liveBlock()`.
- **호환**: `Block` 이 레코드에서 인터페이스가 되어 `new Block(...)` 호출과 `Optional<Block>` 변수는 바뀐다(`var` 와 `.pos()`·`.type()`·`.data()` 호출은 그대로). 아직 1.0 전이고 API 가 밖에 배포되기 전이라 이름을 바로 바꿨다. 이후 같은 종류의 변경은 D5(대체 API 먼저, 폐기는 두 마이너 버전 유지)를 따른다.

## 2. 타입 있는 블록 속성

- `BlockProperty<T>` — 이름 + 값 종류. `bool`·`integer`·`text`(블록마다 값 집합이 다른 열거: half·shape·part·type·face …)·`of(이름, 열거형)`.
- `BlockProperties` — `WATERLOGGED`·`LIT`·`POWERED`·`OPEN`…, `AGE`·`LEVEL`·`POWER`…, `FACING`(`Direction`)·`AXIS`(`Axis`)·`HALF`·`SHAPE`·`PART`·`TYPE`·`FACE` 등.
- `BlockData.get(prop)`·`property(prop)`(Optional)·`has`·`with(prop, value)`·`without(prop)`. 문자열 맵(`properties()`·`with(String, String)`)은 그대로 있다. 서버가 읽어 준 상태는 속성을 모두 적고 있다.
- `Furnace.blockTypes()` → `BlockTypes`/`BlockType`: 블록마다 가진 속성(`PropertyInfo` 이름·종류 BOOLEAN/INTEGER/ENUM·가능한 값 목록, 바닐라 순서), `defaultData()`, `stateCount()`, `isValid(BlockData)`. 값은 서버의 블록 상태 표(`BlockRegistry`)에서 처음 물을 때 계산해 캐시한다. 쓸 때(`setBlock`/`Block.set`)는 지금처럼 틀린 이름·속성이면 `IllegalArgumentException`.

## 3. 레시피 레지스트리 — `Furnace.recipes()`

```java
server.recipes().register(this, "myplugin:bones",
    new Recipe.Shaped(List.of("G", "G"), Map.of('G', RecipeChoice.item("gravel")), new ItemStack("minecraft:bone", 3)));
server.recipes().remove("minecraft:stick");          // 데이터 팩 레시피도 내릴 수 있다
server.recipes().restore("minecraft:stick");
```

- 값: `Recipe.Shaped`·`Shapeless`·`Cooking`(화로·용광로·훈연기·모닥불)·`Stonecutting`·`SmithingTransform`, 읽기 전용 `Special`(갑옷 염색 같은 특수 제작, 대장장이 장식). 재료는 `RecipeChoice`(아이템 목록·태그·섞음·비움).
- **한 표**: 제작대·2×2·제작기(크래프터)·화로류·석재 절단기·대장장이 작업대가 모두 `RecipeManager` 의 같은 표를 읽는다(`findCrafting`·`findCooking`·`findStonecutting`·`findSmithing`). 따로 붙이는 길이 없다.
- 등록은 데이터 팩 파서와 같은 검사를 거친다(모양 1~3×1~3·쓰지 않는 글자·정의 없는 글자·재료 1~9개·결과 수량 1..99·아이템/태그 존재). 틀리면 `IllegalArgumentException` 이고 표는 그대로다. 같은 키는 거부(바꾸려면 `remove` 뒤 `register`).
- **순서**: 제작은 맵 순서의 첫 일치다. 플러그인 레시피는 데이터 팩 레시피 뒤에 붙는다. 데이터 팩 레시피를 바꾸려면 `remove` + 같은 키로 `register`.
- **스레드·재전송**: 어느 스레드에서든 부를 수 있다. 새 표를 통째로 만든 뒤 `volatile` 로 바꿔 끼우므로 읽는 쪽(region 스레드)은 락이 없고 옛 표나 새 표 하나만 본다. 바뀌면 접속자에게 `update_recipes` 를 다시 보내고, 내려간 레시피는 레시피 북에서 잊게 한다(`recipe` REMOVE).
- **수명**: 플러그인이 꺼지면 그 플러그인 레시피가 함께 내려간다. `/reload` 로 매니저가 새로 만들어져도 플러그인 레시피와 "내린 데이터 팩 레시피" 기록을 다시 입힌다(새 데이터 팩이 같은 키를 정의하면 플러그인 쪽을 내리고 경고).
- 레시피 북: 등록만으로는 아무도 배우지 않는다(바닐라와 같다). `Player.discoverRecipes(keys)`·`undiscoverRecipes(keys)`·`hasDiscoveredRecipe(key)`.
- 제작 이벤트(`PrepareItemCraftEvent`·`CraftItemEvent`)는 이전 묶음에서 이미 있고 플러그인 레시피에도 똑같이 발화한다.

## 4. 남긴 것

- 대장장이 장식(`smithing_trim`)·특수 제작은 값으로 등록하지 못한다(읽기만).
- 결과가 입력에 따라 달라지는 레시피(`assemble` 콜백), 레시피별 재료 컴포넌트 일치(1.21.1 `Ingredient` 에는 없음 — 이후 버전 대비 API 변경 필요)는 다음 단계.
- 레시피 변경 이벤트(`RecipeRegisterEvent` 같은 것)는 만들지 않았다. 필요하면 `RecipeRegistry` 리스너로.
