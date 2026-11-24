# Component 계열 실제 구현 (영역 7, 2026-11-24)

바닐라(Paper 1.21.1) 바이트코드의 규칙을 따라 새로 썼다(소스 복사 아님). 시험 `NmsComponentVisitTest`.

- `FormattedText`(+`ContentConsumer`·`StyledContentConsumer`·`EMPTY`·`STOP_ITERATION`·`of`·`composite`), `ComponentContents`, `PlainTextContents`(+`LiteralContents`·`EMPTY`), `TranslatableContents`(+`TranslatableFormatException`), `KeybindContents`, `FormattedCharSequence`·`FormattedCharSink` 추가.
- `Component`: `FormattedText`·`Iterable<Component>` 구현, `getContents`·`visit`×2·`toFlatList`×2·`contains`·`stream`·`iterator`·`getString(int)`·`tryCollapseToString`·`getVisualOrderText` 바닐라 규칙대로.
- 번역 조각 나누기는 바닐라 `decompose` 와 같다: `%s`·`%n$s`·`%%`, 인자가 글이면 그 글이 그 자리에(스타일 유지), 형식 오류(`%d`·남은 `%`·범위 밖 인자)는 틀 통째로 한 조각.
- `MutableComponent`: Furnace `Content` 가 값의 원본이고 `getContents()` 는 그것의 바닐라 내용 객체를 처음 부를 때 만들어 같은 객체를 돌려준다. `create(ComponentContents)`·패키지 생성자 추가.

## 아직 없는 것(없는 멤버 — 호출 위치에서 실패, 가짜로 채우지 않음)
- 점수·선택자·NBT 내용(`ScoreContents` 등)의 `getContents()` — `UnsupportedNmsCall`. 방문은 바닐라처럼 빈 결과.
- `ComponentContents.type()`·코덱, `Component.selector`·`score`·`nbt`·`translatableEscape`·`translationArg`, `FormattedCharSequence.backward`·`decorateOutput`.
- brigadier `Message` 구현: user-todo `2026-11-24-component-brigadier-message.md`.
- 오른쪽에서 왼쪽 글 재배열(bidi)은 서버 기본 Language 도 하지 않아 하지 않는다.

## 함정
- 호환 계층 클래스는 brigadier 없이도 올라가야 한다(`CompatLinkTest`). 상위 타입을 brigadier 로 두면 `Component` 가 못 올라간다.
- 바닐라 `getString` 은 방문 기반이다. 전에 쓰던 Furnace `plainText` 와 같은 결과(en_us)임을 시험으로 확인.
