# Furnace 텍스트 API

Furnace 는 Kyori Adventure 를 쓰지 않고 자체 텍스트 타입 `org.domi.furnace.api.text.Text` 를 쓴다(바닐라 1.21.1 JSON/NBT 와 바이트까지 같다).
이 문서는 그 위에 얹은 도구 모음을 설명한다. 전부 `furnace-api` 안에 있고 외부 의존성이 없다. Paper 플러그인은 나중에 별도 어댑터로 지원한다.

## 도구 한눈에 보기

| 하고 싶은 일 | 쓰는 것 | 패키지 |
|---|---|---|
| MiniMessage 문법 문자열 → Text (그라디언트·플레이스홀더 포함) | `TextMarkup` | `text.markup` |
| 자주 쓰는 메시지를 한 번만 해석하고 슬롯만 채우기 | `TextTemplate` | `text.markup` |
| `&a`/`§a` 레거시 ↔ Text | `LegacyTextSerializer` | `text.serializer` |
| Text → 색 없는 글자 | `PlainTextSerializer` | `text.serializer` |
| Text → 콘솔 ANSI 색 | `AnsiTextSerializer` | `text.serializer` |
| 바닐라 JSON ↔ Text | `JsonTextSerializer` | `text.serializer` |
| 글자 찾아 바꾸기 / 트리 줄이기 / 목록 잇기 / 평탄화 | `Text.replaceText`, `compact`, `Text.join`, `flatten` | `text` |
| 플러그인 번역 키를 플레이어 언어로 | `TranslationStore`, `GlobalRenderer` | `text.translation` |

## 1. 마크업 (`TextMarkup`)

```java
TextMarkup mm = TextMarkup.markup();                       // 기본 인스턴스(표준 태그, 관대한 모드)
Text t = mm.deserialize("<red>안녕 <bold><player></bold>!", Placeholder.unparsed("player", name));
String back = mm.serialize(t);                              // 다시 마크업으로(왕복 가능)
```

- `deserialize(String, TagResolver...)`: 인자로 준 해석기(플레이스홀더·사용자 태그)가 표준 태그보다 먼저 쓰인다.
- `serialize(Text)`: 글자와 글자별 스타일이 같은 마크업을 만든다. 맨 끝의 닫는 태그는 생략한다(`builder().closeAllTags(true)` 로 전부 닫기).
- `escapeTags(String)`: 입력의 `<` 를 전부 이스케이프해 글자로 보이게 한다. `deserialize(escapeTags(s)).plainText()` 는 항상 `s`.
- `stripTags(String)`: 아는 태그를 지우고 글자만 남긴다.
- 옵션(`TextMarkup.builder()`): `strict(true)`, `tags(resolver)`, `editTags(b -> ...)`, `preProcessor`, `postProcessor`, `cache(n)`, `closeAllTags`.
- 이스케이프: `\<` 는 글자 `<`, `\\` 는 글자 `\`. 그 밖의 `\` 는 그대로 남는다. 따옴표 인자 안에서는 `\'`(또는 `\"`)와 `\\`.
- 인자: `:` 로 구분, `'...'`/`"..."` 로 감싸면 `:` `>` 를 넣을 수 있다. `https://` 의 `:` 는 인자 구분자로 보지 않는다.

### 지원하는 태그 (MiniMessage 4.17 호환)

| 태그 | 설명 |
|---|---|
| `<red>` `<dark_gray>` `<grey>` … | 16색 이름(대소문자 무시, `grey`/`dark_grey` 별칭) |
| `<#ff00ff>` `<color:#ff00ff>` `<c:...>` `<colour:...>` | 16진 색 또는 `color:이름` |
| `<bold>` `<b>`, `<italic>` `<i>` `<em>`, `<underlined>` `<u>`, `<strikethrough>` `<st>`, `<obfuscated>` `<obf>` | 꾸밈 |
| `<!bold>`, `<bold:false>` | 꾸밈 끄기(명시적 `false` — 기본 기울임인 로어 줄에서 필요) |
| `<reset>` | 열린 태그를 모두 닫고 스타일 초기화 |
| `<click:동작:값>` | `open_url` `open_file` `run_command` `suggest_command` `change_page` `copy_to_clipboard`. 값에 `:` 가 있어도 된다 |
| `<hover:show_text:'<red>x'>` | 호버 텍스트(인자는 마크업으로 다시 해석) |
| `<hover:show_item:id[:개수[:'컴포넌트 SNBT']]>` | 아이템 호버(`minecraft:` 생략 가능) |
| `<hover:show_entity:종류:uuid[:'이름']>` | 엔티티 호버 |
| `<key:key.jump>` | 키 바인딩 |
| `<lang:키:인자1:인자2>` `<tr>` `<translate>` | 번역 텍스트(인자는 마크업) |
| `<lang_or:키:대체문장:인자…>` `<tr_or>` `<translate_or>` | 대체 문장이 있는 번역 텍스트 |
| `<insert:글자>` `<insertion:글자>` | shift+클릭 삽입 |
| `<font:minecraft:uniform>` `<font:uniform>` | 글꼴 |
| `<newline>` `<br>` (`<newline:3>`) | 줄바꿈 |
| `<selector:@a[...][:구분자]>` `<sel>` | 선택자 |
| `<score:이름:목표>` | 점수 |
| `<nbt:block\|entity\|storage:대상:경로[:구분자][:interpret]>` `<data>` | NBT |
| `<gradient[:색1:색2:...][:phase]>` | 글자별 그라디언트. phase -1.0~1.0 |
| `<rainbow[:!][:phase]>` | 무지개(`!` 는 거꾸로, phase 는 정수) |
| `<transition:색1:색2:...:phase>` | phase(0~1) 위치의 색 하나로 칠함 |
| `<pride[:깃발][:phase]>` | 깃발 줄무늬(pride, progress, trans, bi, pan, nb, lesbian, ace, agender, demisexual, genderqueer, genderfluid, intersex, aromantic, polysexual, omnisexual) |
| `<pre>…</pre>` | 안쪽은 해석하지 않음 |

모르는 태그(`<foo>`)는 글자로 남는다. 인자가 잘못된 태그(`<color:nope>`)는 `MarkupParseException`(원문 위치 포함).
글자별 색 태그는 코드포인트 단위로 칠하고, 같은 색이 이어지면 한 마디로 합친다. 이미 색이 정해진 안쪽 글자는 건드리지 않고 위치만 흘려보낸다.

### 닫는 규칙

- 관대한 모드(기본): `</red>` 는 가장 가까운 `<red>` 를 닫고 그 안에 열린 태그도 같이 닫는다. 끝까지 안 닫힌 태그는 자동으로 닫는다. 짝 없는 `</red>` 는 버린다(모르는 태그면 글자로 남김).
- `strict(true)`: 닫히지 않은 태그, 짝 없는 닫기, 순서가 어긋난 닫기는 모두 `MarkupParseException`. `<newline>`, `<key>` 같은 자기 닫는 태그는 닫지 않아도 된다.

### 플레이스홀더와 포매터

```java
Placeholder.parsed("name", "<gold>값")       // 값을 마크업으로 다시 해석(태그 인자 안에서도 동작). 신뢰할 수 없는 입력에는 쓰지 말 것
Placeholder.unparsed("name", "값")          // 글자 그대로(안전)
Placeholder.component("name", text)         // 완성된 Text
Placeholder.styling("name", Style...)       // <name>내용</name> 에 스타일 입히기
Formatter.number("n", 1234.5)               // <n>  <n:'#.##'>  <n:'ko-KR'>  <n:'de-DE':'#,##0.00'>
Formatter.date("d", LocalDate.now())        // <d:'yyyy-MM-dd'>
Formatter.choice("c", count)                // <c:'0#없음|1#하나|1<{0}개'>   ({0} = 숫자)
Formatter.booleanChoice("b", flag)          // <b:'<green>켜짐':'<red>꺼짐'>
Formatter.joining("j", texts)               // <j:', ':' 그리고 '>
```

사용자 태그는 함수형 인터페이스로 만든다:

```java
TagResolver shout = TagResolver.resolver("shout", (args, ctx) -> Tag.styling(s -> s.color(NamedTextColor.RED).decorate(TextDecoration.BOLD)));
TagResolver greet = TagResolver.resolver("greet", (args, ctx) -> Tag.selfClosingInserting(Text.text("안녕 " + args.popOr("이름").value())));
```

`Tag` 종류: `inserting`(텍스트 끼우기) · `styling`(자식에 스타일) · `preProcessParsed`(원문 치환) · `modifying`(자식 전체를 받아 변형, 그라디언트 방식) · `reset`.

### Furnace 전용: `TextTemplate` (성능)

자주 보내는 메시지는 한 번만 해석해 두고 슬롯만 채운다. 채울 때는 다시 해석하지 않고, 슬롯이 없는 부분 트리는 그대로 재사용한다.

```java
TextTemplate join = TextMarkup.markup().template("<yellow><player></yellow> 님이 <b><world></b> 에 입장", "player", "world");
Text msg = join.fill("player", playerName, "world", Text.text("네더", NamedTextColor.RED));   // 값: Text · 문자열 · 숫자 …
```

- 슬롯 값: `Text`(그대로), 그 밖의 값(문자열로 바꿔 글자 그대로), `TextTemplate.parsed("<b>마크업")`(해석), 스타일 슬롯에는 `Style`.
- 스타일 슬롯: `TextTemplate.builder(mm).styleSlot("hl").slot("n").compile("<hl>강조 <n></hl>")`.
- 호버 텍스트·번역 인자 안의 슬롯도 채워진다. 슬롯이 없으면 `isStatic()` 이고 `fill` 은 항상 같은 인스턴스를 돌려준다.
- 한계: 그라디언트/무지개 안의 슬롯은 한 칸으로 세어 색을 입힌다(채운 값의 글자 수는 반영되지 않는다).
- 값이 없는 슬롯을 채우면 `IllegalArgumentException`.
- 플레이스홀더 없는 마크업은 `TextMarkup.builder().cache(1024)` 로 결과를 LRU 캐시할 수 있다.

참고(로컬 측정, JIT 워밍업 후): 그라디언트·호버가 든 한 줄 메시지 해석 약 2.8µs, 템플릿 채우기 약 0.2µs.

## 2. 레거시 (`LegacyTextSerializer`)

```java
LegacyTextSerializer amp = LegacyTextSerializer.ampersand();   // & 코드, 16진은 &#RRGGBB 로 씀
LegacyTextSerializer sec = LegacyTextSerializer.section();     // § 코드, 16진은 §x§R§R§G§G§B§B 로 씀
Text t = amp.deserialize("&aHello &lWorld &#ff00ffhex");
String s = sec.serialize(t);
```

- 읽기는 두 16진 형식(`&#RRGGBB`, `&x&R&R&G&G&B&B`)을 모두 받는다. 색 코드는 앞선 꾸밈을 지우고, `r` 은 전부 초기화한다. 알 수 없는 코드는 글자로 남는다.
- 쓰기: 꾸밈이 사라지거나 색이 없어지면 `r` 로 초기화한 뒤 필요한 코드를 다시 쓴다. 이벤트·글꼴 같은 스타일은 버린다.
- 옵션: `character(c)`, `hexCharacter(c)`, `hexColors()`(끄면 쓸 때 가장 가까운 16색으로 줄임), `useUnusualXRepeatedCharacterHexFormat()`, `extractUrls()`/`extractUrls(Style)`/`extractUrls(Pattern, Style)`(읽을 때 주소를 `open_url` 클릭으로), `locale(l)`.

## 3. 평문 (`PlainTextSerializer`)

`PlainTextSerializer.plain().serialize(t)` 는 `t.plainText()` 와 같다. 옵션: `translatables(TRANSLATE | KEY | KEY_WITH_ARGS)`, `locale`, `keybinds(TRANSLATE | KEY | BRACKETED)`, `dynamicContent(EMPTY | DESCRIBE)`.

## 4. ANSI (`AnsiTextSerializer`)

콘솔 출력용. `AnsiTextSerializer.trueColor()`, `ansi256()`, `ansi16()`, `detected()`(환경변수 `NO_COLOR`/`COLORTERM`/`TERM` 로 추정), `builder().colorMode(NONE)`.
굵게 `1`, 기울임 `3`, 밑줄 `4`, 취소선 `9`, 난독화 `5`(깜박임). 스타일이 바뀌는 곳에만 코드를 쓰고 끝에 `ESC[0m` 으로 닫는다(`resetAtEnd(false)` 로 끔).
이름 있는 16색은 모드와 상관없이 터미널 팔레트 코드(30-37, 90-97)로 쓴다(`exactNamedColors(true)` 면 정확한 RGB).

## 5. JSON (`JsonTextSerializer`)

`JsonTextSerializer.json().deserialize(jsonString)`: 문자열, 배열(첫 원소 + 나머지는 형제), 객체(text/translate/keybind/score/selector/nbt + 모든 스타일 필드)를 읽는다.
예전 형식도 너그럽게 받는다: `hoverEvent.value`, SNBT 문자열 `value`(show_item/show_entity), UUID 정수 배열, 문자열 불리언.
`serialize(t)` 는 `t.toJson()` 그대로다. 바닐라 정답 파일의 모든 사례가 JSON 왕복에서 같은 문자열을 만든다.

## 6. 트리 도구

```java
text.replaceText(b -> b.match("id=(\\d+)").replacement(m -> Text.text("#" + m.group(1), NamedTextColor.GOLD)).times(2));
text.replaceText(TextReplacement.literal("{player}", nameText));   // 정규식 없이 빠르게
text.compact();                                                    // 같은 모습, 더 작은 트리
Text.join(JoinConfiguration.builder().separator(Text.text(", ")).lastSeparator(Text.text(" 그리고 ")).build(), items);
Text.textOfChildren(a, b, c);
text.forEachSegment((node, effectiveStyle) -> ...);                // 렌더러용: 마디 + 상속 반영 스타일
Iterator<Texts.Segment> it = text.flatten();
```

- `replaceText`: `match(Pattern|regex)` / `matchLiteral`, `replacement(Text | String | Function<MatchResult, Text>)`, `once()`, `times(n)`, `condition((match, 찾은 수, 바꾼 수) -> REPLACE | SKIP | STOP)`. 호버 텍스트·번역 인자·구분자까지 따라간다. 마디 경계를 가로지르는 일치는 찾지 않는다. 바뀐 게 없으면 같은 인스턴스.
- `compact()`: 빈 마디 제거, 스타일 없는 글자 마디 흡수, 부모와 같은 중복 스타일 제거, 자식 하나뿐인 빈 그릇 합치기, 이웃한 같은 스타일 합치기. 글자 순서와 글자별 실제 스타일은 바뀌지 않는다(`false` 와 미정은 구분한다). 여러 번 해도 결과가 같다.
- `JoinConfiguration`: `separator`, `lastSeparator`, `lastSeparatorIfSerial`, `prefix`, `suffix`, `convertor`. 프리셋 `commas(bool)`, `newlines()`, `spaces()`, `arrayLike()`, `noSeparators()`.

## 7. 번역 저장소 (`text.translation`)

플러그인이 정의한 키를 언어별로 보관하고, 플레이어 언어에 맞춰 미리 렌더링한다. 바닐라 키는 번역 텍스트로 남겨 클라이언트가 번역한다.

```java
TranslationStore store = TranslationStore.create(Locale.US)
        .register(Locale.US, "myplugin.welcome", "Welcome, %s!")
        .register(Locale.KOREAN, "myplugin.welcome", "어서 와, %s!")
        .register(Locale.US, "myplugin.items", "{0} items", TranslationStore.Format.MESSAGE_FORMAT);
store.registerGlobally();                                          // Translators(서버 plainText 등) + GlobalRenderer 에 등록

Text out = GlobalRenderer.render(Text.translatable("myplugin.welcome", playerName), player.locale());
```

- 형식: 바닐라(`%s`, `%1$s`, `%%`)와 `MessageFormat` 자리표시자(`{0}`, `''`, `'...'`). 후자는 등록할 때 바닐라 형식으로 바뀐다(`{0,number}` 의 형식 지정자는 무시).
- 로케일 찾기 순서: 정확한 로케일 → 같은 언어 → 기본 로케일(→ 그 언어).
- `GlobalRenderer`: 모르는 키는 그대로(바꿀 게 없으면 같은 인스턴스를 돌려줘 인코딩 캐시 유지), 아는 키는 인자(Text 는 스타일 유지)를 끼워 넣은 완성 텍스트로. 형식이 틀린 패턴은 바닐라처럼 패턴 그대로 보인다. 호버·인자·구분자 안도 렌더링한다.

## MiniMessage/Adventure 와 다른 점

- 그라디언트 `phase`, 무지개 색 공식, `pride` 깃발 색은 MiniMessage 4.17 의 알고리즘을 기억으로 재현한 것이다. 선형 보간·HSV 순서(무지개 6글자: `#ff0000 #ffff00 #00ff00 #00ffff #0000ff #ff00ff`)는 같지만, 경계 값(음수 phase, 깃발 줄무늬 색)에서 한 단계 어긋날 수 있다.
- 무지개 HSV→RGB 는 각 채널에 255 를 곱해 내림한다. 이름 있는 16색과 값이 같으면 `NamedTextColor` 로 정규화된다.
- `<lang>`, `<key>`, `<selector>`, `<score>`, `<nbt>`, `<newline>` 은 자식을 받지 않는 자기 닫는 태그다.
- 자기 닫는 `/>` 는 태그 이름 바로 뒤(`<br/>`)에서만 인식한다(`<click:open_url:https://x.com/>` 의 `/` 가 URL 로 남도록).
- `escapeTags` 는 알려진 태그만이 아니라 모든 `<` 를 이스케이프한다(결과를 해석하면 항상 원문이 나온다).
- `Formatter.choice` 는 ChoiceFormat 이 문구 안의 `#` `<` 를 예약 문자로 보므로 태그를 넣으려면 따옴표로 감싸야 한다.
- 레거시: 색 코드는 꾸밈을 `NOT_SET` 으로 되돌리고(명시적 `false` 가 아니다), 16색 줄이기는 RGB 거리를 쓴다(Adventure 는 HSV 거리).
- `GlobalRenderer`/`TranslationStore` 는 Adventure 의 `GlobalTranslator`/`TranslationStore` 와 이름만 비슷하고 API 는 다르다.
