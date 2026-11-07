# 명령 점검 M11 (2026-11-07)

범위: `/execute`, 자동완성, 선택자, 이전 명령·선택자 점검에서 남긴 부분. 방법: 오프라인 서버 + mineflayer 봇(`tools/bot-test/round15/*.js`)으로 명령 수백 개를 돌려 바닐라 동작·번역 키와 대조하고, 어긋난 곳은 바닐라 바이트코드(`javap`)로 확인.

## 고친 것
- **텍스트 안의 `{"selector"}`·`{"score"}`·`{"nbt"}`**: 일반 엔티티(region 소유)를 못 읽어 빈 글이 되던 것을 바닐라 `ComponentUtils.updateForEntity` 대로 풀어 쓴다(`ComponentUtils.resolve`, future). 선택자 이름은 회색 ", " 로 잇고, 점수는 `@선택자`·`*`·점수판 숫자 형식, NBT 는 엔티티·블록·저장소와 `interpret`·구분자를 지원. `/tellraw`·`/title`·`/say`·`/me`·`/msg`·`/teammsg`·`/bossbar add|set name` 이 모두 이 길을 쓴다.
- **자동완성**: 보스바 id(`/bossbar`·`execute store bossbar`), 소리 이름(`minecraft:available_sounds` 이름 달린 제공자), 속성 수정자 id, `/tag remove`(일반 엔티티 태그 포함).
- **오류 메시지**: 모르는 효과는 `argument.resource.not_found`("Can't find element ... of type 'minecraft:mob_effect'"), 잘못된 JSON 글은 바닐라처럼 `Invalid chat component: <Gson 메시지>`, JsonTextSerializer 의 한글 오류 문구를 영어로.

## 일부러 안 한 것 (바닐라 클라이언트가 직접 풀어서 서버 제안이 안 쓰임)
선택자·슬롯·블록·아이템 자동완성의 서버 쪽 제안은 콘솔·비바닐라 클라이언트에서만 보인다. 표지판 글의 선택자·점수 해석(`SignBlockEntity` TODO)은 후속.

## 봇·시험 산물 (서버 차이 아님)
실수 `0` vs `0.0` 표시, 긴 번역 인자가 봇에서 빈 칸, 봇 언어 표에 없는 키, `execute in the_nether run tp @s` 가 봇 위치를 옮겨 뒤 실행을 오염.

## 참고
`NbtPathArgument` 는 `"["` 처럼 끝나는 경로에서 `StringIndexOutOfBounds` 를 던진다(바닐라 StringReader.peek 도 같다). 바닐라 호환이라 그대로 뒀다.
