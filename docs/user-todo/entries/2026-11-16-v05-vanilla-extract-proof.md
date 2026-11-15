# V-05 데이터 추출 도구 — 노트북 증명 2건 (2026-11-16)

PR 은 **"클라우드 부분 완료, 노트북 증명 대기"** 상태다. 클라우드에서는 Mojang 호스트(piston-meta·piston-data)가 막혀 바닐라 jar·공식 대응표를 받을 수 없어 아래 둘을 못 했다. **1번 증명 전에는 병합하지 않는다**(수용 조건).
모듈 설명은 `tools/vanilla-extract/README.md`.

준비: 노트북(Dminicus_Laptop)에서 이 PR 브랜치를 받는다. Java 21, python(PATH), 인터넷이 필요하다. 이전 도구가 들어 있던 커밋 = `802ccb4515b92d955b24db9b11850248166012d2`.

| # | 해야 할 일 | 합격 기준 | 어디에 적나 |
|---|---|---|---|
| 1 | **1.21.1 결과가 이전 도구와 같은지** (한 명령): `./gradlew -PskipOracle=true :vanilla-extract:run -Pmc=1.21.1 -Pmode=proof -PlegacyRef=802ccb4515b92d955b24db9b11850248166012d2` | 끝의 "도구별 결과"가 전부 `성공`이고, "비교"의 `이전 도구` 줄과 `커밋(합격)` 줄이 전부 `같음`(`.gz` 는 푼 내용)이며 종료 코드 0. 포팅 점검의 `다름` 은 하나씩 판단해 이름 추측 오류 0. 아래 "알려진 차이" 는 예외 | PR 댓글에 출력 전체 |
| 1b | **shape-vectors 전↔후**(고정 시드): `./gradlew -PskipOracle=true :vanilla-extract:run -Pmc=1.21.1 -Pmode=proof -PlegacyRef=802ccb4515b92d955b24db9b11850248166012d2 -Ptools=shape-vectors -PtoolArgs="shape-vectors=oak_stairs,water;shape-vectors=air,stone,oak_stairs,water,glass;shape-vectors=20;shape-vectors=1234"` | `shape-vectors` 가 컴파일되고 `이전 도구` 줄이 `같음` | 같은 댓글 |
| 2 | **1.21.2 시험 실행**: `./gradlew -PskipOracle=true :vanilla-extract:run -Pmc=1.21.2 -Pmode=extract` (결과는 커밋 안 함) | 대응표로 이름이 풀리고 도구가 도는지 — 끝의 "도구별 결과" 표(`성공`/`컴파일 실패`/`실행 실패`)가 U-02 재료. 한 도구가 깨져도 나머지는 돈다. 컴파일 오류 전체는 `~/.cache/furnace-vanilla-extract/1.21.2/exporter-compile-<도구>.log` | PR 댓글에 도구별 표 |

proof 한 번이 하는 일(코드가 합격 판정까지 한다): ① 옛 도구를 `legacyRef` 에서 꺼내 같은 jar 에 돌려 "전" 생성 ② 새 도구 **전부**(기본 선택 밖인 shape-vectors 도 컴파일만) 도구별로 컴파일하고 선택된 것을 실행 ③ 옛 Java 도구가 있던 것은 전↔후, 옛 Java 도구가 없던 **파이썬 6개**(`tags`·`builtin-registries`·`block-tags`·`item-components`·`block-definitions`·`damage-types`)는 같은 도구가 만든 커밋 파일과 견줘 합격 판정 ④ 포팅 점검. 파이썬 6개만 다시 확인하려면 `-Pmode=verify -Ptools=tags,builtin-registries,block-tags,item-components,block-definitions,damage-types` (전부 `같음` 이어야 한다).

준비: 노트북(Dminicus_Laptop)에서 이 PR 브랜치를 받는다. Java 21, python(PATH, `py`/`python3`/`python`), 인터넷이 필요하다. 이전 도구가 들어 있던 커밋 = `802ccb4515b92d955b24db9b11850248166012d2`. `javap` 가 PATH 에 있어야 한다(옛 소리 종류·프로토콜 도구용 — JDK 의 bin 을 자동으로 붙인다).

## 1번이 처음에는 어긋날 것으로 예상되는 곳

도구 소스(`tools/vanilla-extract/src/main/resources/exporters/furnacetools/`)는 Mojang 이름을 **대응표 없이 기억으로 옮겨 쓴 것**이라 컴파일이 안 되거나 이름을 잘못 짚은 곳이 있을 수 있다. 그래서 `proof` 모드에 **포팅 점검**을 넣었다 — 옛 도구 바이트코드를 대응표로 풀어 "옛 코드가 실제로 부르던 Mojang 이름"을 그대로 보여 주므로, 새 도구의 `다름` 목록을 보고 이름을 고치면 된다(추측 필요 없음). 고치고 `-Pmode=port-check` 로 0 이 될 때까지 반복한 뒤 `proof` 를 한 번 돈다(`port-check` 도 준비 단계와 옛 도구 컴파일을 하므로 첫 캐시 이후에만 빠르다). 컴파일이 깨진 도구가 있어도 나머지는 계속 돌고 점검 목록에 `(이 도구는 컴파일 실패)` 로 표시된다.

컴파일 오류가 나면 먼저 볼 곳(이름이 가장 불확실한 곳): ① `ExportBlockProps` — `block.properties()`, `Properties` 필드(`isRandomlyTicking`·`requiresCorrectToolForDrops`·`emissiveRendering`·`hasPostProcess`·`offsetFunction`), `getLootTable().location()`, `SoundEvent.getLocation()` ② `ExportProtocolIds` — `ProtocolInfo.Unbound.listPackets` 가 1.21.1 에 있는지(없으면 `details()` 경유), `PacketType.flow()/id()`, `*PacketTypes`·`*Protocols` 클래스명 ③ `ExportItemBlocks` — 비공개 필드 `attachmentDirection`, `wallBlock` ④ 정답기들 — `Strategy.getConfiguration` 결과 `var`, `Heightmap.setRawData`, `LevelChunkSection` 개수 필드 3개, `IdMap` 구현 메서드.

알려진 차이:
- **`block-attrs` 의 destroy 열(0부터 7번째 열)**: 옛 `ExportBlockAttrs` 는 난독화 `st.f(BlockGetter, BlockPos)` 를, 옛 `ExportBlockHardness` 는 `block.o().h(BlockGetter, BlockPos)` 를 불렀다. 서술자가 같은 두 메서드는 난독화 이름이 같을 수 없으니 둘 중 하나는 `getDestroySpeed` 가 아니다(경도 파일에 `bedrock -1.0` 이 있어 `h` 쪽이 `getDestroySpeed` 로 보임). 새 도구는 둘 다 `getDestroySpeed` 라 포팅 점검이 이 줄을 `다름` 으로 보이고, 전↔후도 destroy 열에서 다를 수 있다. **열 이름대로의 의미(새 도구)가 맞는 쪽이며 이름 오류가 아니다.** 확인: 포팅 점검의 `ExportBlockAttrs` 가 다르다면 `- 이전에만` 줄이 `getShadeBrightness`(또는 다른 이름)인지 본다. 전↔후가 `다름` 이면 `python -I tools/vanilla-extract/scripts/compare-without-column.py tools/vanilla-extract/build/out/1.21.1-legacy/furnace-server/src/main/resources/blocks/blockattrs_1.21.1.txt tools/vanilla-extract/build/out/1.21.1/furnace-server/src/main/resources/blocks/blockattrs_1.21.1.txt 7` 가 "다른 열도 다른 줄 0개" 면 합격으로 본다(그때는 옛 도구가 destroy 열에 경도가 아닌 값을 써 왔다는 사실을 PR 댓글에 적는다). 서버가 읽는 커밋 파일은 어차피 이 도구 출력이 아니다(아래).
- `ExportBlockSoundTypes`·`ExportProtocolIds` 는 이전에 서버 바이트코드를 읽는 파이썬이었고 지금은 서버 클래스를 직접 부르는 Java 라서 포팅 점검 대상이 아니다. 전후 결과 비교만 본다(소리 종류 번호 순서는 달라 소리 내용으로 견준다).
- 이전 `ExportBlockAttrs.java` 는 커밋된 `blockattrs_1.21.1.txt` 와 형식이 다르다(그 파일은 옛 버전 도구로 만들어졌다). 합격 판정은 전↔후이고 커밋과의 차이는 참고로만 본다 — 커밋된 파일을 새로 쓸 때는 따로 정한다.
- 결과 머리말의 도구 경로는 옛 위치(`tools/vanilla-data/…`)를 그대로 적는다(커밋된 리소스와 글자까지 같게).

## 클라우드에서 이미 확인한 것

- 단위 시험 `:vanilla-extract:test` 24개 통과: 대응표 읽기(같은 난독화 이름의 필드·메서드를 서술자로 구분), 이름 풀기가 만든 jar 를 실제로 로드·실행(하위 클래스 이름으로 부른 부모 멤버, 람다 invokedynamic, 서명 파일 제거), 준비 단계 전체(가짜 번들러 → 이름 풀기 → 데이터 생성기 실행 → 캐시 재사용), 포팅 점검, 검증기(.gz·소리 내용 비교).
- 파이썬 도구 4개(`block-tags`·`tags`·`builtin-registries`·`damage-types`)는 저장소의 `tools/vanilla-data/server-1.21.1/data` 와 커밋된 `builtin-1.21.1.txt` 로 만든 입력으로 돌려 **커밋된 리소스와 바이트 동일**함을 확인했다. 나머지 둘(`block-definitions`·`item-components`)은 데이터 생성기 reports 가 필요해 노트북에서 본다.
