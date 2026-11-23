# 패킷 코덱 추출기 (T-252, 2026-11-23)

연구 `docs/roadmap/faster-shim-and-version-up-research-2026-11-23.md` 5.3 [3위] 의 구현이다. 목표는 버전을 올릴 때 **패킷 필드 모양(와이어)을 사람이 바닐라에서 옮겨 적지 않고 기계로 뽑는 것**과, 그 결과가 우리가 쓰는 손 스키마·서버 코덱과 같은 바이트를 내는지 자동으로 대조하는 것이다.

## 1. 한 줄 요약
바닐라 서버 jar(Mojang 이름)의 패킷 클래스 코덱을 바이트코드로 따라가 와이어 모양을 뽑는 도구(`vanilla-extract -Pmode=packet-layout`)를 만들고, 1.21.1 결과를 커밋하는 중립 잠금 파일(`protocol-767.layout`)로 두어 CI 가 손 스키마·서버 코덱과 대조한다. 서버 실행 코드는 바뀌지 않는다.

## 2. 무엇이 만들어졌나
| 조각 | 위치 | 하는 일 |
|---|---|---|
| 추출기 | `tools/vanilla-extract/.../PacketLayout.java`, `PacketLayoutCommand.java` | 패킷 클래스의 `STREAM_CODEC` 정의를 기호 실행해 와이어 토큰 목록과 필드(게터) 이름을 뽑는다 |
| 전체 표(캐시) | `~/.cache/furnace-vanilla-extract/<판>/packet-layout.tsv` | Mojang 클래스 이름이 들어 있다 — 커밋 금지 |
| 중립 잠금(커밋) | `furnace-api/src/main/resources/org/domi/furnace/api/packet/schema/protocol-767.layout` | 상태·방향·와이어 이름·방식·토큰. Mojang 클래스·상수 이름은 없다(안쪽 값 코덱은 이름의 해시 앞 8자리 `ref#a1b2c3d4`) |
| 읽기 | `furnace-api/.../PacketLayoutLock.java` | 잠금을 읽는 작은 클래스(시험이 쓴다) |
| 스키마 대조 시험 | `furnace-api/.../PacketLayoutLockTest.java` | 손 스키마와 잠금이 같은 와이어를 말하는지(CI, 바닐라 jar 불필요) |
| 바이트 대조 시험 | `furnace-server/.../plugin/packet/PacketLayoutBytesTest.java` | 잠금대로 무작위 본문을 써서 서버 코덱(`ProtocolTable`: 범용 + 손 코덱)이 같은 바이트로 읽고 되쓰는지 |
| 추출기 시험 | `tools/vanilla-extract/.../PacketLayoutTest.java` | 바닐라 없이 우리가 쓴 작은 뼈대 클래스로 추출 방식·결정성·이름 숨김을 확인 |
| CI | `.github/workflows/ci.yml` vanilla-gates | 바닐라 jar 로 잠금을 다시 뽑아 커밋된 것과 같은지(`-PlayoutVerify=true`) |
| 판 차이 | `tools/compat/version-diff.py` | 두 판의 와이어 모양 차이(바뀜·추가·삭제·못 푼 토큰)를 보고서에 넣는다 |

## 3. 어떻게 뽑나
두 가지 정의 방식을 읽는다(연구 2.3 의 표와 같은 분류).
- `StreamCodec.composite(코덱, 게터, …, 생성자)` — 인자 목록이 곧 필드 순서·코덱.
- `Packet.codec(쓰기 메서드, 읽기 생성자)` — 쓰기 메서드 몸통의 버퍼 호출 순서.

정적 초기화와 쓰기 메서드를 작은 기호 실행기로 훑는다. 값 스택에 식(트리)을 쌓고 버퍼에 닿는 호출을 사건으로 모은다. 목록·선택·맵 도우미(`writeCollection`·`writeOptional`·`writeMap`)에 넘긴 람다는 몸통까지 따라간다. 분기·점프가 나오면 그 패킷은 **손으로 남는 것**으로 표시하고 이유를 적는다(추측해서 이어 붙이지 않는다). 모르는 호출은 `?` 토큰으로 남아 보고서에 드러난다 — 새 판에서 어휘를 더해야 한다는 신호다.

`ByteBufCodecs.X` 같은 잎 코덱은 표에 없어도 그 익명 클래스의 `encode` 몸통에서 와이어를 읽는다(1.21.2 의 `CONTAINER_ID` 가 이렇게 풀린다).

법적 경계: 몸통의 명령·상수는 어디에도 저장하지 않는다. 결과에 남는 것은 와이어 토큰과 필드·게터 이름뿐이다. 바닐라 jar·Mojang 이름이 든 전체 표는 캐시에만 있다.

## 4. 결과 (1.21.1, 실제로 돌려 본 숫자)
- 패킷 202개(손 스키마와 같은 수). **기계로 풀린 것 179개, 손으로 남는 것 23개**: 분기가 있는 쓰기 메서드 18(비트 묶음·조건 필드·이동 패킷 등), 커스텀 페이로드(코덱 둘·동적 분배) 2, 꾸러미 표시 패킷 2, 목록 원소 람다 안 분기(접속자 정보 갱신) 1. 연구 2.3 의 어림(171개)보다 약간 많다 — 정적 초기화 없는 내포 보조 클래스를 빼고 잎 코덱을 풀었기 때문이다.
- **손 스키마와 대조**: 둘 다 모양을 말하는 152개가 일치(스키마가 `custom` 이라 순서를 대조하지 않는 28개, 잠금이 손으로 남는 것은 제외). 어긋남 3개를 찾았다.
  - `login_disconnect`: 바닐라는 거절 사유를 **JSON 문자열**로 쓰는데 스키마는 `component`(play 단계의 NBT)로 적혀 있었다 → 스키마를 `string` 와이어로 고침. 서버의 손 코덱(`LoginDisconnect`)은 이미 JSON 문자열이었다.
  - `tag_query`: `optional<nbt>` 와 바닐라 `writeNbt`(null → 끝 태그) 는 같은 와이어 — 대조에서 같은 것으로 본다.
  - `remove_entities`: 스키마가 `IntList` 를 `custom` 와이어로 적었다(대조 불가로 이름을 못 박아 둠, `int_id_list` 는 잠금이 말한다).
  - 안쪽 값 코덱 23종(`ref#…`)은 스키마의 어느 와이어에 대응하는지가 한 곳에서만 정해져야 한다는 일관성도 본다(어긋남 없음).
- **바이트 대조**: 잠금대로 쓴 무작위 본문 25회씩을 서버 코덱이 읽고 같은 바이트로 되쓴 패킷 **103종**(범용 코덱과 손 코덱 합쳐). 레지스트리·안쪽 값 코덱이 필요한 토큰이 든 58종은 이 방식으로는 못 만들어 건너뛴다(토큰을 더하는 것이 다음 일). 잠금 토큰 폭을 일부러 바꾸면 이 시험이 잡는다(시험에 들어 있다).
- **판 차이 시험(1.21.2)**: 추출기를 1.21.2 에 그대로 돌리면 패킷 211개, 손 16개. `version-diff.py 1.21.1 1.21.2` 가 와이어 모양 변경 23·추가 12·삭제 3 을 낸다(예: 컨테이너 번호가 1바이트 → 가변 정수로 바뀐 패킷들). 1.21.2 에서 처음 나온 도우미(`writeContainerId`·`ResourceKey.streamCodec`)는 어휘에 한 줄씩 더했다 — 확인은 1.21.2 의 바이트코드로.

## 5. 성능
이 변경은 서버 실행 코드(`furnace-server/src/main`)를 바꾸지 않는다. 생성되는 것은 시험·판 차이용 표뿐이라 패킷 읽기·쓰기 핫 경로에 영향이 없다(손 코덱 그대로). 잠금에서 런타임 코덱을 **만들어 쓰는** 단계는 이 PR 의 범위 밖이며, 그 단계를 하게 되면 손 코덱 대비 처리량을 재고 느려지면 쓰지 않는다 — 측정 없이 바꾸지 않는다.

## 6. 쓰는 법
```
./gradlew -PskipOracle=true :vanilla-extract:run -Pmc=1.21.1 -Pmode=packet-layout                      캐시에 전체 표
./gradlew -PskipOracle=true :vanilla-extract:run -Pmc=1.21.1 -Pmode=packet-layout -PlayoutInstall=true 잠금 파일을 저장소에 쓴다(새 판으로 올릴 때·추출기를 고친 뒤)
./gradlew -PskipOracle=true :vanilla-extract:run -Pmc=1.21.1 -Pmode=packet-layout -PlayoutVerify=true  새로 뽑은 것이 커밋된 잠금과 같은지(CI)
python3 tools/compat/version-diff.py 1.21.1 1.21.2                                                   패킷 와이어 모양 차이는 두 판 모두 packet-layout 표가 있어야 나온다
```

## 7. 새 판으로 올릴 때 (U-04 프로토콜)
1. 새 판에 `-Pmode=packet-layout` 을 돌린다. `?` 토큰이 있으면 `PacketLayout` 의 어휘(`BUF`·`LEAF`)에 한 줄 더한다(바이트코드로 확인하고).
2. `version-diff.py` 의 "패킷 와이어 모양 변경·추가·삭제" 목록이 곧 할 일이다.
3. 손 스키마를 고치고 `PacketLayoutLockTest` 가 초록이 될 때까지 맞춘다(새 판 잠금은 `-PlayoutInstall=true`).
4. 손으로 남는 패킷(분기 있는 것)은 잠금에 일부 토큰만 있다 — 사람이 바닐라 쓰기 메서드를 읽는다.

## 8. 한계와 다음 일
- 스키마 자체(자바 필드 타입·`type` 줄)를 새 판용으로 **생성**하는 단계는 아직 아니다. 지금은 "무엇이 바뀌었나"와 "손 스키마가 맞나"를 기계가 알려 준다. 다음: 클래스 필드 서명(제네릭 포함)을 읽어 스키마 뼈대를 만들고 1.21.1 손 스키마와 비교(이 PR 의 잠금 + 위 대조가 그 정답기).
- 손 23개, 안쪽 값 코덱(ItemStack·Component·Particle 등 `ref#`) 풀기. 손 스키마가 `custom` 이라 대조하지 못하는 28개 중 잠금이 풀린 것은 서버 손 코덱(`GenericHand`)을 잠금으로 지킬 수 있다.
- 읽는 쪽(복호화) 대칭은 가정이다: 쓰기 메서드(또는 composite 인자)에서만 뽑는다. 대칭이 깨진 패킷이 있으면 서버 코덱 대조(4장 바이트 대조)가 잡는다.
- 토큰 `i8@x` 가 열거형 번호인지 비트 묶음인지는 잠금이 모른다(바이트 시험은 0 만 쓴다).
