# 게임 레지스트리·마법부여 끄기·게임 규칙·난이도 프로필 (D2)

불만 표 27번(몹 변종 등록), 32번(커스텀 마법부여·마법부여 끄기), 37번(커스텀 게임 규칙·난이도 프로필)을 받는 API 다.
바닥은 B3 부트스트랩([bootstrap-registries.md](bootstrap-registries.md))이고, 범위는 1.21.1 에서 데이터 주도인 것만이다
(고양이·개구리 변종은 1.21.1 에서 내장 레지스트리라 넣을 수 없다).

## 1. 데이터 주도 동기화 레지스트리 (바닐라 동작)

서버 시작 때 데이터 팩을 고른 직후, 아래 레지스트리를 켜진 데이터 팩의 `<레지스트리>/*.json` 에서 읽는다
(바닐라 `RegistryDataLoader` 와 같다). 그래서 **월드 `datapacks/` 에 넣은 늑대 변종·그림·마법부여·현수막 무늬·갑옷 장식·음반도
바닐라처럼 클라이언트에 보인다.** 예전에는 내장 목록 고정이라 데이터 팩 항목이 클라이언트에 가지 않았다.

| 레지스트리 | 데이터 팩 폴더 |
|---|---|
| `minecraft:enchantment` | `enchantment/` |
| `minecraft:wolf_variant` | `wolf_variant/` |
| `minecraft:painting_variant` | `painting_variant/` |
| `minecraft:banner_pattern` | `banner_pattern/` |
| `minecraft:trim_material` · `minecraft:trim_pattern` | `trim_material/` · `trim_pattern/` |
| `minecraft:jukebox_song` | `jukebox_song/` |

- 순서는 파일 id 순(경로 → namespace, 바닐라 `ResourceLocation` 순서)이고 이것이 클라이언트가 받는 번호다. 데이터 팩 항목이
  바닐라 항목 사이에 끼면 바닐라 항목 번호가 밀린다 — 서버 안의 번호는 모두 이 표에서 다시 찾는다.
- 바닐라 팩 그대로인 항목은 데이터 없이 보내고(클라이언트가 아는 `minecraft:core`), 데이터 팩·플러그인 항목과 데이터 팩이 덮어쓴
  바닐라 항목은 네트워크 NBT 를 실어 보낸다(바닐라 `RegistrySynchronization.packRegistry`).
- 시작 때 한 번만 읽는다. `/reload` 로는 바뀌지 않는다(바닐라와 같다). 태그는 리로드마다 다시 읽는다.
- 바이옴·차원 타입은 플러그인이 같은 훅으로 바닐라 뒤에 붙인다([bootstrap-registries.md](bootstrap-registries.md) 6장). 피해 유형·채팅 유형은 아직 내장 목록이다.
- **차이**: 바닐라는 항목 하나라도 읽지 못하면 월드를 열지 않는다. Furnace 는 그 항목만 빼고 오류 로그를 남긴다
  (필수 필드·크기 범위만 검사한다).

데이터 팩 늑대 변종의 `biomes` 와 그림의 `width`·`height`·`minecraft:placeable` 태그는 자연 스폰·그림 놓기에 그대로 쓰인다.
데이터 팩 마법부여는 정의(가중치·비용·지원 아이템·배타 집합·모루 비용·칸)대로 마법 부여대·전리품·거래·모루에 나오지만,
**`effects` 는 아직 계산하지 않는다**(바닐라 마법부여의 효과는 서버에 직접 구현돼 있다). 커스텀 효과는 플러그인 이벤트로 만든다.

## 2. 부트스트랩 훅으로 항목 더하기

```java
public final class MyBoot implements PluginBootstrap {
    @Override
    public void bootstrap(BootstrapContext ctx) {
        ctx.lifecycle().register(LifecycleEvents.GAME_REGISTRIES, reg -> {
            reg.wolfVariant("myplugin:moon", new WolfVariant("myplugin:entity/wolf/moon", "myplugin:entity/wolf/moon_tame",
                    "myplugin:entity/wolf/moon_angry", List.of("#minecraft:is_mountain")));
            reg.paintingVariant("myplugin:sunset", new PaintingVariant(4, 2, "myplugin:sunset"));
            reg.enchantment("myplugin:zap", EnchantmentDefinition.builder(Text.text("Zap"))
                    .supportedItems("#minecraft:enchantable/sword").weight(5).maxLevel(3)
                    .minCost(5, 8).maxCost(55, 8).anvilCost(2).slots("mainhand").build());
            reg.addToTag("minecraft:enchantment", "minecraft:in_enchanting_table", "myplugin:zap");
            reg.addToTag("minecraft:painting_variant", "minecraft:placeable", "myplugin:sunset");
            // 타입이 없는 레지스트리나 effects 가 있는 마법부여는 데이터 팩 JSON 그대로
            reg.json("minecraft:banner_pattern", "myplugin:stripe", "{\"asset_id\":\"myplugin:stripe\",\"translation_key\":\"block.myplugin.stripe\"}");
        });
    }
}
```

- 등록기는 W2 바이옴·차원 타입과 같은 `GameRegistrar` 다. 키의 namespace 는 자기 플러그인 namespace 여야 한다(없으면 붙는다). 같은 id 의 데이터 팩 파일이 있으면 데이터 팩이 이긴다.
- 타입 있는 정의(`WolfVariant`·`PaintingVariant`·`EnchantmentDefinition`)는 버전 중립이다. 서버가 그 버전의 데이터 꼴로 바꾼다.
  `json(...)` 은 데이터 팩 형식이라 마인크래프트 버전에 묶인다.
- 텍스처(`wildTexture`·`assetId` 등)는 클라이언트 리소스 팩에 있어야 보인다.
- 늑대 자연 스폰은 레지스트리 순서로 바이옴이 맞는 첫 변종이다. 바닐라 변종과 바이옴이 겹치면 id 순서로 갈린다.
- `addToTag` 는 리로드마다 다시 붙는다(태그 파일의 `replace: false` 와 같다).
- 정리: 레지스트리 항목은 서버가 도는 동안 고정이라(핫 리로드도 태그 더하기만 다시 받는다) 플러그인이 꺼져도 다음 시작까지 남는다(이미 접속한 클라이언트의 번호가 바뀌면
  안 되므로). `addToTag` 몫은 다음 `/reload` 부터 빠진다.

## 3. 마법부여 끄기

```java
server().gameRegistries().disableEnchantment(this, "minecraft:mending");
```

또는 `furnace.properties`:

```properties
# 쉼표로 나눈 마법부여 id(기본 비어 있음)
enchantments.disabled=minecraft:mending,minecraft:frost_walker
```

꺼진 마법부여는 게임이 **새로 고르는 곳**에서 빠진다: 마법 부여대, 전리품의 `enchant_randomly`·`enchant_with_levels`
(낚시·상자), 마법 부여 제공자(몹·습격자 장비, `single` 포함), 주민 거래(마법이 부여된 책·장비). 이미 붙은 아이템, `/enchant`,
모루 합치기, 전리품의 `set_enchantments`(명시한 것)는 그대로다. 레지스트리에서는 빼지 않아 다시 켜면 곧바로 원래대로다.
플러그인이 끈 것은 그 플러그인이 꺼지면 풀린다. 아무것도 끄지 않았으면 난수 소비까지 바닐라와 같다.

## 4. 게임 규칙

```java
GameRule<Boolean> arena = server().gameRules().registerBoolean(this, "pvpArena", true);   // 이름: myplugin:pvpArena
GameRule<Integer> lives = server().gameRules().registerInt(this, "lives", 3, 1, 10);
if (server().gameRules().get(arena)) { ... }
```

- 이름은 `<플러그인 namespace>:<이름>`(이름은 영문·숫자·`_`). 불리언·정수만. `/gamerule myplugin:pvpArena false` 로 바꿀 수 있고
  level.dat `GameRules` 에 함께 저장된다. 클라이언트에는 명령 자동 완성에만 보인다.
- 플러그인이 꺼지면 `/gamerule` 에서 빠지지만 값은 level.dat 에 남아 다시 등록하면 그 값으로 돌아온다.
- 바닐라 규칙(`GameRule.bool("doDaylightCycle")`)도 같은 보기로 읽고 바꿀 수 있다(`/gamerule` 과 같은 효과).
- 바닐라 규칙만 있을 때 level.dat 결과는 바닐라와 같다(`:` 가 있는 키만 남겨 둔다).

## 5. 난이도 프로필

```java
server().difficulty().setProfile(this, new DifficultyProfile("myplugin:brutal", Difficulty.HARD, 1.5F, 1.25F));
```

- 클라이언트에는 `base`(4칸 중 하나)가 보이고, 바닐라의 모든 난이도 판정도 `base` 로 한다.
- `playerDamageMultiplier`: 난이도 보정이 붙는 피해(바닐라 `Player.hurt` 의 난이도 보정 대상)에 보정 뒤 곱한다.
- `localDifficultyMultiplier`: 지역 난이도(`DifficultyInstance` 의 실효 난이도 — 몹 장비·마법부여·특수 배율)에 곱한다.
- 둘 다 1 이면 바닐라와 결과가 같다(곱하지 않는다). 서버 전체에 하나다.
- `/difficulty`(또는 `setDifficulty`)는 프로필을 내리고 바닐라로 돌아간다. 건 플러그인이 꺼져도 내려간다(난이도 `base` 는 그대로).
- 프로필은 저장하지 않는다(level.dat 에는 `base` 만). 플러그인이 켜질 때마다 다시 건다. 하드코어 월드는 `base` 가 HARD 여야 한다.

## 6. 버전 중립

- 레지스트리 키·태그 id 는 문자열 리소스 위치다. 타입 있는 정의는 서버가 버전별 데이터 꼴로 바꾼다.
- 다음 마인크래프트 버전에서 데이터 주도가 된 레지스트리(예: 1.21.5 고양이·돼지 변종)는 같은 훅에 타입을 더해 받는다.
