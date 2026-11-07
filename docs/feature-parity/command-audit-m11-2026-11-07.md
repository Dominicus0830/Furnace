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

## 독립 검토에서 나온 것 (고침)
- 글을 풀어 쓸 때 스타일을 **덮어쓰던** 것을 바닐라 `withStyle`(= `applyTo`) 처럼 합친다 — 단일 개체 이름의 호버·팀 색, 점수 숫자 형식의 색이 남는다.
- NBT 글: 바닐라는 태그마다 `getAsString` 이다. interpret 면 문자열 태그를 **JSON 글로 읽고**, 아니면 색·따옴표 없이 값 그대로 보이며 구분자가 없으면 공백 없는 `","` 로 이은 글 하나다(`/data get` 의 색칠한 SNBT 와 다르다).
- 잘못된 JSON 글 오류: 바닐라 `ParserUtils.parseJson` 은 엄격한 Gson 이고 원인 메시지(`java.io.EOFException:` 접두 없음)를 보이며 커서를 리더가 멈춘 곳까지 옮긴다.

## 알려진 한계 (안 고침)
- 서명된 `/say`·`/me`·`/msg`·`/teammsg` 에 **일반 엔티티를 고르는 선택자**가 들어 있으면 방송이 읽기가 끝난 뒤(수 ms)로 밀린다. 같은 사람이 그 사이에 일반 채팅을 보내면 수신자 쪽 서명 사슬 순서가 어긋날 수 있다(가정: 클라이언트가 chat_validation_failed 로 끊을 수 있음, 시험 안 함). 선택자가 없으면 이전처럼 그 자리에서 끝난다. 사람별 채팅 순서 사슬은 후속 과제.
- 명령이 지연 시간 초과로 실패 처리된 뒤에도 늦게 끝난 읽기의 콜백(보스바 만들기·방송)이 돌 수 있다.
- `/tellraw`·`/title` 은 대상 하나의 풀기가 실패하면(점수 선택자가 둘 이상 등) 앞 대상에게도 보내지 않는다(바닐라는 앞 대상까지 보낸 뒤 오류).
