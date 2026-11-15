# 데이터 팩 태그가 게임 동작을 구동 (2026-11-16)

출처: 사용자 확인 항목 #6(`docs/user-todo/entries/2026-11-12-small-vanilla-mismatches.md` 결정 1, 도미니코 11-15 승인 "추천대로").

## 바닐라
`BlockState.is(TagKey)`·`EntityType.is`·`ItemStack.is`·`DamageType`/`GameEvent` 홀더 `is(tag)` 는 모두 레지스트리에 걸린 **데이터 팩 태그**를 읽는다(`TagLoader`가 `/reload` 마다 새로 걸음). 그래서 데이터 팩이 `minecraft:climbable`·`prevent_mob_spawning_inside`·`wither_immune` 등을 고치면 게임 규칙이 따른다.

## Furnace 가 하던 것
- `B.isTag`·`BlockTags`(월드젠·몹·블록 규칙): jar 의 바닐라 표(`worldgen/1.21.1/block_tags.txt`)만 읽음.
- `ItemTags`·`EntityTypeTags`·`EntityType.is`·`DamageType`·`GameEvent`: jar 의 `registries/tags-1.21.1.txt` 만 읽음.
- 클라이언트로 가는 Update Tags 와 플러그인 API(`Bukkit.getTags`·`GameRegistries`)만 데이터 팩 표를 읽음 → "화면에는 고쳐 보이는데 동작은 안 바뀐" 상태.

## 고침
- 공용 입구 `ItemContext.gameplayTags()` = 데이터 팩 관리자가 건 표(데이터 팩·플러그인 `TagAddition` 포함), 안 걸렸으면(시험 서버) jar 의 바닐라 표. `/reload` 마다 `DataPackManager.apply` 가 `installTags` 로 새 표 객체를 걸고, 각 호출처의 캐시는 **표 객체가 바뀌었는지(`!=`)** 로 스스로 새로 만든다(비우기 호출 불필요 — O05 C4 의 "캐시를 비워야 한다"가 이 방식으로 해결).
- 바뀐 곳: `B.isTag`(블록 번호 `boolean[]` 캐시), `BlockTags.blocksOf`(이름 목록 캐시, 월드젠·`BlockStateArgument`·`ItemUse` 가 씀), `ItemTags`, `EntityTypeTags`, `EntityType.is`, `DamageType.isInTag/keyHasTag`, `GameEvent.is`, `PlayerProbe`(climbable·fences·walls·fence_gates 집합), `BlockShapes.of`(태그에서 만든 표; 표가 바뀌면 새로 만든다), `GameRegistriesImpl`(같은 입구로 통일).
- 블록 태그의 숫자 id = 블록 레지스트리 순서 = `Blocks` 블록 번호(시험 `BlockTagRegistryIdentityTest` 가 보증). jar 의 `block_tags.txt` 가 `tags-1.21.1.txt` 와 같은 내용임도 같은 시험이 보증한다.

## 동작 규칙(바닐라와 같게)
- 데이터 팩 표에 없는 태그 = 빈 태그(필수 참조가 빠져 로드 안 된 태그와 같음). `replace:true` 빈 목록이면 원래 블록도 빠진다.
- 바닐라에 없는 새 태그(`custom:rope` 등)도 `B.isTag`·`/setblock #…`·`BlockStateArgument` 에서 쓸 수 있다. 데이터 팩 표가 없는 시험에서만 jar 표에 없는 이름이 `IllegalArgumentException`(오타 방어).

## 알려진 한계
- 이미 만들어진 월드젠 구조물 엔진·특징 지형 캐시는 만들 때의 태그를 쓴다. 태그를 바꾸고 `/reload` 한 뒤 **새로 생성되는** 구조물·지형부터 반영되어, 리로드 전에 만든 것과 섞일 수 있다. 바닐라가 `/reload` 뒤 월드젠 태그 조건을 어떻게 보는지는 미확인(바닐라 소스 없음) — 월드젠만 시작 때 표로 고정하는 방안은 별도 TASK 후보.
- `Painting.create` 등 이미 `ItemContext.tagEntries` 를 쓰던 곳은 그대로.
