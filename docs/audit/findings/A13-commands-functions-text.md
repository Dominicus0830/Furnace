# A13 명령·함수·데이터 팩·텍스트 — 3차 전체 점검 조사 보고서 (2026-11-11)

- 기준: 브랜치 `claude/quirky-wright-lra834`, 커밋 `fcaa0b7c`. 코드는 고치지 않았다.
- 바닐라 근거: 1.21.1 서버 jar 의 난독화 클래스를 `javap -c -p` 로 읽었다(디컴파일러 실행은 거절돼 못 썼다). 번역 키는 jar 안 `en_us.json`(6881개)과 전수 대조했다. 바닐라 서버를 실제로 돌린 대조는 하지 못했다(Mojang 호스트 접근 불가). 그래서 "바닐라가 오류로 거절한다" 같은 판단은 코덱·바이트코드 구조에서 읽은 것이고, 각 항목에 근거 수준을 적었다.
- 재현 시험: `A13-repro/*.java.txt` 2개(시험 7개, **7개 모두 기준 커밋에서 실패**). 쓰는 법은 맨 아래.

## 요약

| 심각도 | 수 | 항목 |
|---|---|---|
| 치명 | 0 | |
| 높음 | 1 | A13-01 |
| 중간 | 2 | A13-02, A13-03 |
| 낮음 | 4 | A13-04, A13-05, A13-06, A13-07 |
| 참고 | 2 | A13-08, A13-09 |

범위의 대부분은 바닐라 바이트코드를 그대로 옮긴 충실한 포팅이다. 차이는 포팅한 사람이 "아직 지원하지 않는다"고 적어 둔 자리(아이템 술어, 파티클 인자)와, 나중에 생긴 기능(팀, 클리어러블)이 옛 근사를 그대로 둔 자리에 몰려 있다.

## 조사한 범위와 방법

1. **명령 트리**: 이미 `VanillaCommandTreeTest` 가 바닐라 데이터 생성기의 `commands.json` 과 노드 종류·실행 가능·파서·속성·자식·리다이렉트를 전부 대조한다. 다시 하지 않았다. 대신 루트 명령의 **권한 레벨**을 0~4 레벨 소스로 모두 열어 바닐라 표와 맞췄다(help·list·me·msg·tell·w·teammsg·tm·random·trigger = 0, op·deop·ban 계열·kick·whitelist·debug·setidletimeout·tick·transfer = 3, stop·save-*·jfr·perf = 4, 나머지 2). 전부 같다.
2. **명령 동작**: 콘솔(엔티티 붙임)과 플레이어 소스로 명령 약 600줄의 출력 문구·반환값·성공 여부를 돌려 바닐라와 맞췄다. `/execute`(if/unless 전 종류·store·수정자), `/scoreboard`(objectives·players·display·numberformat), `/team`, `/tag`, `/data`(get·merge·modify·remove, 문자열 부분, 배율), `/bossbar`, `/summon`, `/give`·`/clear`·`/xp`·`/effect`·`/attribute`·`/damage`·`/kill`, `/fill`·`/setblock`·`/clone`, `/time`·`/weather`·`/difficulty`·`/gamerule`, `/title`·`/tellraw`·`/say`·`/me`·`/msg`·`/teammsg`, `/locate`, `/worldborder`, `/spawnpoint`, `/gamemode` 등. 스코어보드 rendertype·displayautoupdate 처럼 메시지 없이 0 을 돌려주는 분기까지 바이트코드로 확인했고 같았다.
3. **번역 키 전수 대조**: 바닐라 `en_us.json` 의 `command(s).*`·`argument(s).*`·`permissions.*`·`parsing.*` 650개 중 Furnace 소스에 한 번도 나오지 않는 123개를 뽑아 하나씩 분류했다. 대부분은 `"…success." + 단수/복수` 처럼 조립되는 키이거나 클라이언트용 설명 문구다. 진짜 비어 있던 것은 `argument.pos.outofbounds`(A13-06)뿐이다.
4. **선택자·자동완성**: 선택자 옵션 70여 줄(중복 옵션·잘못된 값·공백·따옴표·nbt·team·scores 등)과 서버 쪽 자동완성 제안 200여 줄을 돌렸다. 서버가 제안해야 하는 곳(소리·소환 가능 엔티티·게임 규칙·점수 기준·시간 단위 등)은 같고, 클라이언트가 스스로 푸는 곳이 비어 있는 것은 M11 에서 "일부러 안 한 것"으로 정리된 그대로다.
5. **텍스트 컴포넌트**: `/bossbar add` 이름으로 번역·선택자·점수·키바인드·NBT·`extra`·배열·인자 수 부족(`TranslatableFormatException` 대체 표시)을 돌렸다. 풀기와 서식은 같다(A13-04 의 너그러움만 다름).
6. **데이터 팩 `/reload`**: 깨진 데이터 23종(레시피·전리품·발전 과제·태그·함수 태그·아이템 수정자·조건의 깨진 JSON, 모르는 종류, 순환 태그, 없는 항목)을 하나씩 넣고 `/reload` 를 돌렸다. 전부 "Reloading!" 로 끝나고 팩 목록·함수 실행이 그대로였다(서버가 죽거나 리로드가 실패하지 않음). 로그 문구 대조는 하지 못했다.
7. 명령 블록·`/function` 매크로·`/schedule`·`#load`·`#tick`·`/return` 은 이미 전용 시험(`CommandBlockTest`, `FunctionTest`, `MacroFunctionTest`, `ScheduleTest`)이 있어 새로 돌리지 않았다.

## 조사하지 못한 범위 (이유)

- 데이터 팩 로드 오류 **로그 문구**와 `pack.mcmeta` 변형(overlays·filter·supported_formats) 전체 대조: 코드(`PackMetadata`)는 읽었고 구조가 바닐라와 같지만 실행 대조는 못 했다.
- 발전 과제·레시피·전리품 명령의 **데이터 의존 출력**(`/advancement grant everything`, `/recipe give *`, `/loot` 표 내용): 시험 서버가 바닐라 데이터 팩을 싣지 않아 A12 와 함께 봐야 한다.
- 명령 블록의 틱 순서·체인·조건부·지연 칸: `CommandBlockTest` 로 갈음했고 바닐라 `CommandBlock.tick` 과 줄 단위 대조는 하지 않았다.
- `execute if loaded` 의 판정(바닐라는 엔티티 틱 단계 청크): 플레이어 없는 시험 서버에서 거짓으로 나오는 것은 바닐라와 같다고 가정했다(바이트코드 대조 안 함).
- `/spreadplayers` 의 실제 이동(가짜 플레이어로는 "unexpected error"가 나서 `place()` 단위로만 봤다).

## 같음 확인 (다시 볼 필요 없는 것)

- 권한 레벨 표, 루트 명령 집합(`publish` 제외), 오류 문구(`Unknown or incomplete command…`, `Incorrect argument…`, 위치 표시 `<--[HERE]` 와 `...` 자르기), `Expected end of options`·`Option 'x' isn't applicable here`·`Limit must be at least 1`·`Distance cannot be negative` 등 선택자 오류.
- `/data` 의 `Invalid list index`, `Expected object, got: …`(SNBT 쉼표만), 배율 출력 `%.2f`, `Mth.floor` 포화, 문자열 부분 `Invalid substring indices`, 항목 추가 형식 불일치 시 "Nothing changed".
- `/give` 최대 6400, `/clear` 개수·테스트 모드, `/xp` 반환값(레벨·점수), `/effect` 더 약한 효과 거절, `/time` 반환값(`% 24000`), `/weather thunder` 의 -1 반환, `/gamerule` 반환값(불린 1/0), `/difficulty` 0.
- `/scoreboard players add` 음수 거절, `operation` 0 나누기, `trigger enable`, `display name|numberformat`, 목표 이름 길이 제한 없음.
- `/team` 옵션·`modify` 메시지(`Nothing changed. …` 문구까지), `/tag` 한도, `/summon` 중복 UUID·`Invalid position for summon`, `/damage` 무적 시간 중 거절, `/attribute` 모디파이어.
- 텍스트: 인자 수 부족 번역은 형식 문자열 그대로 보임, `fallback`, `%%`·`%1$s`, 선택자 쉼표 이음, 빈 선택자의 빈 글.
- `/reload`·`/datapack` 은 바닐라 `ReloadCommand`·`DataPackCommand` 와 같은 순서·검사다.

## 항목

| ID | 심각도 | 종류 | 근거 | 재현 | 제안 |
|---|---|---|---|---|---|
| A13-01 | **높음** | 미구현 | `ItemPredicateArgument.subPredicate` 는 `count`·`damage` 만 구현하고 나머지 하위 술어(레지스트리 14종 중 구현한 `count`·`damage` 를 뺀 **12종**: `enchantments`, `stored_enchantments`, `potion_contents`, `custom_data`, `container`, `bundle_contents`, `firework_explosion`, `fireworks`, `writable_book_content`, `written_book_content`, `attribute_modifiers`, `trim`, `jukebox_playable`)을 `Malformed '…' predicate: 'Not supported yet'` 로 거절한다. 바닐라 1.21.1 은 `item[custom_data~{a:1}]`, `item[enchantments~[{enchantments:"minecraft:sharpness",levels:{min:3}}]]` 형식을 받는다(`ItemSubPredicates` 레지스트리 14종, `custom_data` 코덱은 `TagParser.withAlternative(문자열, 복합 태그)` 라 SNBT 복합 태그를 그대로 받음 — 바이트코드 `cu`·`ck`·`dk`·`uz.j` 로 확인). 이 술어는 `/clear`·`/execute if items` 에서 쓰이고, **데이터 팩 함수 한 줄에 쓰이면 그 줄이 파싱에 실패해 함수 전체가 로드되지 않는다**(`FunctionTest` 가 못 파싱한 함수는 빠짐을 확인). 1.20.5 이후 데이터 팩이 NBT 검사를 `custom_data~` 로 바꾼 터라 영향 범위가 크다. **같은 뿌리가 `loot/ItemPredicate.sub` 에도 있다**: 모르는 하위 술어는 경고 후 "항상 거짓"이라 발전 과제(`inventory_changed` 의 `custom_data`·`container` 조건)와 전리품 조건(`match_tool`)이 조용히 안 걸린다(A12 경계 — 같이 고칠 것). | `A13ReproTest.itemPredicateCustomDataPartialMatch`, `…EnchantmentsPartialMatch`(실패: `execute if items … custom_data~{a:1}` 이 0, 메시지 `Not supported yet`) | 하위 술어 코덱을 한 곳(`item/` 아래)에 만들어 명령 인자와 `loot/ItemPredicate` 가 함께 쓰게 한다. 우선순위는 `custom_data`(부분 NBT 비교 `NbtUtils.compareNbt`)·`enchantments`·`stored_enchantments`·`container`·`potion_contents`. 모르는 종류를 "항상 거짓"으로 두지 말고 로드 오류로 올린다. |
| A13-02 | 중간 | 바닐라 차이(복제) | `CloneCommand.tryClear` 가 "주크박스·독서대는 아직 빠졌다"(200행 주석). 바닐라 `clone … move` 는 원본 칸을 공기로 바꾸기 전 `Clearable.tryClear` 로 담긴 것을 비워 아이템이 떨어지지 않게 한다. Furnace 는 주크박스 `RecordItem` 을 비우지 않고 공기로 바꿔 **음반이 아이템으로 떨어지는데, 목적지 주크박스에도 `RecordItem` 이 복사돼 음반이 둘이 된다**(`data get block` 으로 목적지 음반 확인). 독서대(`Book`)도 같은 구조로 보이나 시험 칸이 로드되지 않아 못 봤다(가정). 상자 등 `Container` 는 `tryClear` 가 이미 있어 영향 없음. | `A13ReproTest.cloneMoveJukeboxDoesNotDropRecord`(실패: 떨어진 아이템 엔티티 1개 기대 0) | 주크박스·독서대 블록 엔티티에 `clearContent()` 를 두고 `tryClear` 가 부르게 한다. A04(복제) 목록에도 올릴 것. |
| A13-03 | 중간 | 바닐라 차이 | `SpreadPlayersCommand.place`·`teamCount`: "팀 시스템이 없어 모든 대상의 팀이 null" 이라 `respectTeams=true` 면 **모든 대상을 한 팀으로 묶어 같은 위치에 놓는다**. 팀(`/team`·`Scoreboard.getPlayersTeam`)은 이미 구현돼 있다. 바닐라 `setPlayerPositions`/`getNumberOfTeams` 는 플레이어면 소속 팀, 아니면 null 로 그룹을 지어 팀마다 위치 하나를 쓴다(팀 없는 대상은 한 그룹). 그래서 서로 다른 팀 두 명이 있으면 바닐라는 위치 둘을 만들어 흩뿌리는데 Furnace 는 하나만 만들어 둘을 같은 칸에 둔다. 기존 시험 `respectTeamsPutsEveryoneAtTheSamePosition` 이 이 옛 동작을 굳혀 두고 있다(고칠 때 함께 바꿀 것). | `A13SpreadTeamsReproTest.differentTeamsGetDifferentPositions`(실패: 두 팀 대상의 x 가 같음) | `place`·`teamCount` 가 `Scoreboard` 에서 대상의 팀을 읽게 하고 `Rider` 가 플레이어일 때만 팀을 본다. 기존 시험을 팀별 위치 기대로 고친다. |
| A13-04 | 낮음 | 바닐라 차이 | `JsonTextSerializer`(furnace-api)가 잘못된 값을 오류 대신 **조용히 무시**한다: 모르는 색(`"color":"bogus"`), 불린이 아닌 스타일 값(`"bold":"maybe"`), 모르는 `clickEvent.action`, `value` 없는 클릭 이벤트가 모두 통과한다(`style()` 이 `instanceof`·null 검사로 건너뜀). 바닐라 1.21.1 의 `Style` 코덱은 `optionalFieldOf`(엄격), 색은 `STRING.comapFlatMap(parseColor)`(오류 `Invalid color name`), 클릭은 `Action` 열거형 코덱+`value` 필수라 모두 `Invalid chat component: …` 로 거절된다(바이트코드 `xw$b`·`xy` 로 구조 확인, 실서버 대조는 못 함 → 이 항목은 "가정"). 또 JSON 숫자 `bold:1` 은 바닐라 `JsonOps.getBooleanValue` 가 참으로 받는데 Furnace 는 무시하고, 반대로 문자열 `"true"` 는 바닐라가 거절하는데 Furnace 는 받는다(옛 형식 호환을 의도한 너그러움). 영향은 "틀린 입력이 오류 없이 서식만 빠짐"이라 작다. 플러그인·옛 데이터 호환을 위해 일부러 너그럽게 한 것이면 문서에 알려진 차이로 적는 쪽이 맞다. | `A13ReproTest.invalidTextJsonIsRejected`(실패: 예외 없음) | 결정 필요(user-todo): 명령·데이터 팩 입력(`ComponentArgument`, 데이터 팩 JSON)은 엄격하게, 플러그인 API 의 `deserialize` 는 너그럽게 두는 이중 모드. |
| A13-05 | 낮음 | 바닐라 차이 | `EntitySelectorParser` `type` 옵션: 바닐라는 `type=!x` 를 한 번 쓴 뒤 양성 `type=y`(또는 `type=#태그`)를 쓰면 `isTypeLimitedInversely && !invert` 로 `Option 'type' isn't applicable here` 오류를 낸다(바이트코드 `hk` 의 type 핸들러 `hj.F()`/`hj.D()`). Furnace 는 반전 쓴 기록(`typeLimitedInversely`)이 없어 오류 없이 받고 결과가 빈 집합이 된다(`@e[type=!pig,type=cow]` → 소 하나를 골라야 하는데 "Cow has no tags", 즉 정상 성공으로 처리). 양성 뒤 양성·양성 뒤 음성은 오류가 이미 같다. | `A13ReproTest.selectorTypeAfterInvertedTypeIsRejected`(실패: 오류 없음) | 파서에 `typeLimitedInversely` 를 두고 핸들러 첫머리에 같은 검사를 더한다(태그 분기 포함). |
| A13-06 | 낮음 | 바닐라 차이 | `BlockPosArgument.getSpawnablePos` 가 월드 밖일 때 `argument.pos.outofworld`("That position is out of this world!")를 던진다. 바닐라(`gp.c`)는 `argument.pos.outofbounds`("That position is outside the allowed boundaries.")다. 이 인자를 쓰는 바닐라 명령은 `/setworldspawn`·`/spawnpoint` 둘뿐(바이트코드 `aok`·`aoj` 확인)이다. 판정 경계(`isInSpawnableBounds`: y ±2천만, 수평 ±3천만)는 같다. | `A13ReproTest.spawnableBoundsErrorKey`(실패: "out of this world") | 예외 키를 `argument.pos.outofbounds` 로 바꾼다. |
| A13-07 | 낮음 | 미구현(알려진) | `ParticleArgument`: `item` 파티클의 비어 있지 않은 컴포넌트(`particle item stick[custom_model_data=1]`), `vibration` 의 `entity` 위치 소스를 "not supported" 오류로 거절한다(8행 주석에 명시). 바닐라 1.21.1 은 둘 다 받는다. 사용 빈도가 낮다. | 없음(코드 대조) | 컴포넌트 파서(`ItemArgument`)를 재사용해 컴포넌트를 읽는다. entity 소스는 UUID 조회가 region 왕복이라 함께 설계. |
| A13-08 | 참고 | 바닐라 차이(실행 못 함) | `DataPackManager.reload` 는 `repository.setSelected(sel)` 을 **읽기 전에** 하고, 읽기가 예외로 끝나면 되돌리지 않는다. 바닐라 `reloadResources` 는 읽기가 끝난 뒤(`thenAcceptAsync`) 선택을 바꾸므로 실패하면 옛 선택이 그대로다. 현재 로더가 깨진 데이터를 항목별로 삼켜 이 경로가 실제로 나온 적은 없다(깨진 데이터 23종 시험에서 예외 0). 팩 zip 을 열 수 없는 `UncheckedIOException` 정도가 남은 경로다. | 없음 | `setSelected` 를 성공 뒤로 옮기거나 실패 때 이전 선택으로 되돌린다. |
| A13-09 | 참고 | 가정 | `ItemCommands` 의 `Enchantment.getFullname` 은 번역 키를 `enchantment.<ns>.<name>` 으로 조립한다("가정: 데이터 팩 description 이 바닐라 기본 번역 키", 300행). 바닐라 1.21 은 데이터 팩 인챈트의 `description` 텍스트를 쓰므로 데이터 팩이 `description` 을 다른 키·리터럴로 정의하면 이름이 달라진다. 바닐라 인챈트만 쓰는 서버에서는 차이가 없다. | 없음 | `DynamicRegistries.definitions("minecraft:enchantment")` 의 `description` 을 읽는다. |

## 수정 묶음 제안

1. **아이템 하위 술어 코덱 + 명령·발전·전리품 공용화**(A13-01, A12 경계). 모델 Opus high 권장(공용 코덱 설계, 부분 NBT 비교). 가장 먼저 — 데이터 팩 함수 로드 실패가 걸려 있다.
2. **Clearable 보강**(A13-02): 주크박스·독서대 `clearContent`. Sonnet medium.
3. **작은 명령 정합 묶음**(A13-03, 05, 06, 07, 08, 09): 서로 파일이 달라 한 PR 에 담을 수 있다. 기존 시험 하나(`respectTeamsPutsEveryoneAtTheSamePosition`)를 새 기대로 고친다. Sonnet medium.
4. **텍스트 JSON 엄격 모드**(A13-04): 결정(user-todo) 뒤. Sonnet high.

## 재현 시험 쓰는 법

`A13-repro/A13ReproTest.java.txt` 는 확장자 `.txt` 를 떼고 `furnace-server/src/test/java/org/domi/furnace/server/audit/` 에, `A13SpreadTeamsReproTest.java.txt` 는 `…/server/command/builtin/` 에 넣는다. 시험은 콘솔(엔티티 붙임)·가짜 플레이어(`ConcurrentEmbeddedChannel`)로 명령을 돌린다. 기준 커밋에서 7개 모두 실패한다(`itemPredicate*` 2, `cloneMoveJukebox…`, `differentTeamsGetDifferentPositions`, `invalidTextJsonIsRejected`, `selectorTypeAfterInvertedTypeIsRejected`, `spawnableBoundsErrorKey` 1개씩 — `A13ReproTest` 메서드 6개 + `A13SpreadTeamsReproTest` 1개).
