# V-06 `@VanillaPort` 표시 — item·recipe·loot 조각 (2026-11-16)

버전업 계획 V-06(`docs/roadmap/version-upgrade-plan-2026-11-16.md` 3.4절)의 한 조각. 동작은 바뀌지 않는다.

- 표시: `org.domi.furnace.server.VanillaPort`(`@Retention(SOURCE)`, 값은 바닐라 클래스 전체 이름, 여러 개 가능). 클래스 파일에 남지 않는다.
- 대응표 만들기: `python3 tools/vanilla-port/vanilla-port-map.py` → `build/vanilla-port-map.tsv`(Furnace 클래스·바닐라 클래스·소스 경로).
  바닐라 클래스 목록(Mojang 대응표에서 뽑음, 커밋 안 함)을 `--verify 목록.txt`(바닐라 클래스 목록) 로 주면 바닐라 이름이 실제로 있는지도 검사한다.
- 이 조각: `server/item`·`server/recipe`·`server/loot` 58개 클래스, 120건. 이름은 1.21.1 Mojang 대응표로 전부 존재를 확인했다.
- 표시하지 않은 것(바닐라 클래스와 1:1 대응이 없는 Furnace 고유 클래스·어댑터): `CodecException`, `PCodec`, `TextNbtJson`, `Val`, `ApiRecipes`,
  `RecipeContext`, `EnchantmentToggles`, `LegacyNoiseRandom`, `LootBlockState`, `LootDamageSource`, `LootEntity`, `LootJson`, `LootParseException`,
  `LootRegistries`, `LootWorld`, `NetworkComponents`.
- 다른 조각(block·world·net·command 등)은 같은 어노테이션과 스크립트를 그대로 쓴다.
