# 이식 다리 — NMS shim 객체와 서버 객체를 잇는 공통 틀 (설계, 2026-11-27)

> 지시(코디네이터 11-27): 아이템 영역(메뉴)·블록 영역(블록·블록 엔티티)·네트워크 영역(채팅 서명·명령) 스레드가 모두 같은 데서 막혔다.
> T-263 G4 "서버 이식 짝" 목록(`furnace/plans/g4-port-pairs-2026-11-27/`, 2,614 짝)의 서버 메서드는 서버 안 형식(`MenuPlayer`·서버 `ItemStack`·`BlockLevel`·`Pos`·
> 서버 `MessageSignature` …)을 받는데, 호환 계층은 어댑터 경계(`docs/ops/adapter-boundary.md`)상 `furnace-api` 만 부를 수 있다.
> 이 문서는 **"shim 객체 → 서버 객체 찾기 + 형식 바꾸기 + 소유 스레드 검사 + 서버 메서드 부르기"** 를 한 가지 틀로 만드는 설계다. 첫 사용처는 메뉴다.
> 원칙: 결과는 바닐라·Paper 와 같다(메뉴 동기화 패킷 순서·stateId 포함), 바닐라 통째 이식 금지, 커밋 기준선 Mojang 이름 금지(지문만), "임시로 하고 나중에 고치기" 금지,
> 서버를 리플렉션으로 부르는 범용 다리 금지(T-263 설계 3.2·3.6 — 다리는 형식 있는 코드).

## 0. 결론

| | 무엇 | 어떻게 |
|---|---|---|
| 1 | **이식 줄(`.ports`)** 한 줄 = "NMS 멤버 X 는 서버 메서드 Y 가 한다" | 영역 스레드가 G4 짝을 읽어 확인한 뒤 줄로 적는다. 줄 하나에서 **세 조각이 생성**된다: `furnace-api` 의 다리 인터페이스 메서드(중립 형식), 서버의 구현(소유 검사 → 형식 바꾸기 → 서버 메서드, 타입 검사되는 보통 Java), 호환 계층의 `@LinkTarget` 정적 메서드 + 연결 줄(T-263 길로 껍데기·합친 멤버에 들어감). 리플렉션·`invokedynamic` 없음 |
| 2 | **짝(peer)** — shim 객체가 서버 객체를 가리키는 하나의 규칙 | 서버 객체는 경계 너머로 `Object` 표(token)로만 간다. 객체 종류(kind)마다 "찾기·만들기·거울" 규칙을 손으로 **한 번** 쓴다: 상태·블록·자리·레벨·플레이어·메뉴·칸·컨테이너·블록 엔티티·아이템 스택·난수·값 객체. 지금 흩어진 짝(`BlockEntity.furnaceHandle`, 메뉴 `NmsMenus.Live`, 상태 번호, 플레이어 handle, `PlacementBridge` 레벨)을 이 규칙으로 모은다 |
| 3 | **형식 바꾸기 네 가지** | V1 값(복사 — 레코드·불변 값), V2 짝 객체(표를 들고 위임 — 상태 있는 클래스), V3 값+되쓰기(살아 있는 보기 — 바닐라가 참조로 고치는 스택·칸), V4 콜백 짝(플러그인이 구현·상속한 객체를 서버가 부를 때). 줄마다 인자·반환이 어느 것인지는 kind 가 정하고 줄에 적지 않는다 |
| 4 | **소유 검사는 서버 쪽 생성 몸통의 첫 호출** | 검사 종류 `pure`·`any`·`menu`·`pos`·`entity`·`detached`. 주인 스레드면 바로, 틱 스레드가 아니면 주인에게 넘기고 기다림, 다른 region·전역 틱 스레드면 `ThreadOwnershipException`(지금 `NmsOwner`·`NmsEntityOwner` 규칙을 서버 쪽 한 곳 `PortGuards` 로 옮김). 빌드 시험이 첫 호출을 본다 |
| 5 | **플러그인 상속(`@NmsInheritable`)** | 서버 쪽에 생성된 **갈고리 하위 클래스**(`AnvilMenu$Port extends AnvilMenu`)가 플러그인이 재정의한 메서드만 호환 계층으로 되돌려 부른다. 플러그인의 `super.m()` 은 갈고리를 건너뛰는 `port$super$m` 으로 간다 — 바닐라의 가상 호출과 같은 순서 |
| 6 | **api.internal 다리 추가 절차** | 허용 목록(③)에 **접두 한 줄**(`org/domi/furnace/server/port/`)만 더하고, 앞으로 다리를 더하는 일은 이식 줄 추가다. 손 코드(kind·변환기·검사)는 처음 한 번 독립 검토, 이식 줄은 PR 묶음 검토(정답기로 증명된 읽기 줄은 검토 생략 — T-263 F1 과 같은 기준) |
| 7 | 성능 | 다리 호출 = 정적 final 필드의 단형 인터페이스 호출 1번 + 서버 쪽 캐스트. 상태 번호만 오가는 `pure` 줄은 할당 0. 목표(JMH, 틀 PR 에 숫자): `pure` 직접 호출 대비 +5ns 안, 레벨·자리 줄 +50ns 안, 메뉴 줄 +100ns 안(잠금 포함) |

- 도미니코 결정이 필요한 항목은 **없다**(동작은 바닐라·Paper 와 같고 기법만 다르다). 코디네이터 확인 거리: 13장 PR 순서와 담당, 12장 검토 규칙(묶음 검토 크기).
- 의존: 메뉴의 `ServerPlayer.containerMenu = 메뉴`(AnvilGUI 꼴)는 필드 리다이렉트 PR C(`docs/roadmap/shell-refs-and-field-redirect-2026-11-27.md`)가 있어야 끝까지 간다. 그 앞에서는 지금처럼 정직한 덫이다(8.6).

## 1. 지금 구조 — 왜 막히나

- G4 짝의 서버 메서드 서술자: 메뉴 `quickMoveStack(MenuPlayer, int) → server ItemStack`, 블록 `randomTick(int 상태, BlockLevel, Pos, RandomSource)`, 채팅 `MessageSignatureCache#pack(server MessageSignature) → int`. 호환 계층은 이 형식을 **이름으로 쓸 수 없다**(① `AdapterDirectionStaticTest`). 그래서 지금은 영역마다 `ChunkInternals` 에 좁은 메서드를 손으로 하나씩 더해 왔다(1,437줄, 기본 메서드 수백 개). 2,614 짝을 이 방식으로 하면 한 짝에 세 곳(api 인터페이스·서버 구현·호환 다리)을 손으로 써야 하고 경계 검토가 짝마다 필요하다.
- 짝(shim ↔ 서버 객체)이 영역마다 따로 있다: 블록 엔티티 shim 의 `furnaceHandle`(Object), 메뉴 보기 `NmsMenus.Live`(플레이어 handle + 창 번호), 블록 상태 shim 의 상태 번호, 엔티티 shim 의 api handle, `PlacementBridge` 의 "서버 월드면 주인 region 레벨, 아니면 플러그인 레벨을 읽는 `ReadLevel`". 규칙이 같은데 코드가 다섯 벌이다.
- 메뉴는 지금 **보기만** 있다. 서버가 연 창은 `NmsMenus.View`(일반 `AbstractContainerMenu` 하위 — `player.containerMenu instanceof ChestMenu` 가 Paper 와 달리 거짓), 플러그인이 만든 창(`new AnvilMenu(...)` 하위 클래스)은 서버가 모른다. 아이템 영역 G4 짝의 절반 넘게(`quickMoveStack`·`slotsChanged`·`stillValid`·`Slot#safeTake` …)가 이 다리 없이는 이을 곳이 없다.
- 서버 `ItemStack` 은 불변, NMS `ItemStack` 은 가변이다. 바닐라 코드는 칸의 스택 객체를 그 자리에서 고친다. 지금 "살아 있는 보기"(`ItemStack.liveMenuSlot`·`liveContainerSlot` — 고치면 CAS 로 칸에 되씀)가 이 차이를 메우는 유일한 선례다.

## 2. 틀 한 장

```
                  커밋하는 것                              빌드가 만드는 것 (build/generated, 커밋 안 함)
 furnace-compat-paper/src/shimspec/ports-1.21.1/<영역>.ports
            │  (이식 줄)
            ├── :furnace-api:generatePorts      → api.internal.port.<Area>Port        인터페이스(중립 형식만, ② 검사 대상)
            ├── :furnace-server:generatePorts   → server.port.gen.<Area>PortImpl     구현: PortGuards.x(…) → 바꾸기 → 서버 메서드
            │                                     server.port.gen.<서버클래스>$Port   갈고리 하위 클래스(hook 줄이 있는 클래스만)
            └── :furnace-compat-paper:generatePorts → compat.nms.port.gen.<Area>Ports  @LinkTarget(check="port") 정적 메서드
                                                    build/generated/ports.links      연결 줄 → FullShells·ShellMerge 가 연결 몸통으로

 손으로 한 번 쓰는 것(kind 마다, 독립 검토):
   서버  org.domi.furnace.server.port.{PortGuards, PortKinds, PortConvert}      (허용 목록 접두 한 줄)
   호환  org.domi.furnace.compat.nms.port.{Peers, PortValues, PortCallbacks}
   api   org.domi.furnace.api.internal.port.{PortBridges, PortLevel, PortRandom, …콜백 인터페이스}
```

호출 길(예: 플러그인이 `anvilMenu.quickMoveStack(player, 3)`):

```
껍데기/합친 멤버 AnvilMenu#quickMoveStack(Player,int)            (연결 몸통 — T-263 생성)
 └ ItemPorts.AnvilMenu$quickMoveStack(AnvilMenu self, Player p, int i)   (호환 생성, @LinkTarget(check="port"))
     tok = Peers.menu(self); pt = Peers.player(p)                 (짝 찾기 — 필드 읽기)
     r = ItemPorts.B.anvilMenu$quickMoveStack(tok, pt, i)         (단형 인터페이스 호출, B 는 static final)
 └ ItemPortImpl.anvilMenu$quickMoveStack(Object tok, Object pt, int i)   (서버 생성)
     PortGuards.menu(tok, "AnvilMenu#quickMoveStack", () -> {     (소유 검사 첫 호출 — 잠금/주인 스레드)
        AnvilMenu m = (AnvilMenu) tok; MenuPlayer mp = PortKinds.menuPlayer(pt);
        return PortConvert.item(m instanceof AnvilMenu$Port h ? h.port$super$quickMoveStack(mp, i) : m.quickMoveStack(mp, i)); })
 └ 호환: r(api ItemStack) → NMS ItemStack (V1 값, 새 객체)
```

## 3. 짝(peer)

### 3.1 규칙 세 가지

1. **표(token)**: 서버 객체는 경계 너머로 `Object` 로만 간다. 호환 계층은 그 형식을 이름으로 쓰지 않고(① 그대로), 서버 쪽 생성 몸통만 캐스트한다. 캐스트가 실패하면(그 shim 의 짝이 그 서버 클래스가 아님) `UnsupportedNmsCall`(어느 줄·어느 서버 클래스였는지 문구에) — 조용히 다른 값을 내지 않는다.
2. **두 방향**
   - **거울(mirror)** — 서버가 만든 객체가 플러그인에게 처음 보일 때(`player.containerMenu` 읽기, `level.getBlockEntity`, 채팅 이벤트의 메시지 …) 그 서버 클래스에 맞는 **바른 NMS shim 클래스**의 객체를 `ShimCtor.carrying(token)` 생성자로 만든다(바닐라 생성자를 돌리지 않는다). 서버 클래스 → NMS 클래스 대응은 이식 명세의 `class` 머리줄이 정한다(5장) — `@VanillaPort` 는 소스 전용이라 실행 때 읽지 않는다.
   - **짝 만들기(peer)** — 플러그인이 바닐라 생성자로 shim 을 만들면(`new AnvilMenu(id, inv, access)`, `new SimpleContainer(9)`, `MessageSignatureCache.createDefault()`) 그 생성자가 이식 줄(`<init>` 또는 정적 팩토리)로 **서버 객체를 함께 만들고** 표를 든다.
3. **동일성**: 같은 서버 객체는 늘 같은 shim 객체로 보인다(Paper 에서 `player.containerMenu == player.containerMenu`, 플러그인이 `==` 로 자기 메뉴를 찾는 꼴이 흔하다). 거울을 만들 수 있는 서버 클래스는 `volatile Object portMirror` 한 칸을 갖고(메뉴는 지금 `pluginOwner` 옆), 짝 만들기는 만든 shim 을 그 칸에 넣는다. 거울 만들기는 그 칸의 CAS — 두 스레드가 동시에 만들어도 하나만 남는다. 값 객체(V1)는 동일성을 약속하지 않는다(바닐라도 레코드·불변 값은 새로 만든다 — 같은 객체를 돌려주는 바닐라 멤버만 줄에 `same` 을 달아 짝 객체 길로 보낸다).

### 3.2 kind 표

| kind | NMS 쪽 | 경계를 건너는 중립 형식 | 서버 쪽 | 짝 자리 / 찾기 비용 |
|---|---|---|---|---|
| `state` | `BlockState` | `int` 상태 번호 | `int` | shim 필드 읽기 |
| `block` | `Block`(종류 객체) | `int` 블록 번호 | `BlockBehaviors.ofBlock(n)` | 필드 읽기 + 배열 |
| `fluid` | `FluidState` | `int` | `int` | 필드 |
| `pos` | `BlockPos`(가변 `MutableBlockPos` 포함) | `int x, y, z` 세 칸 | `Pos`(서버 쪽에서 새로 — 탈출 분석 대상) | 부를 때 값 읽기(가변 자리라도 그 순간 값 — 바닐라와 같은 결과) |
| `dir` 등 열거형 | `Direction`·`Rotation`·`Mirror` … | `int` 서수(서버 번호 체계로 표 변환) | `int` | 표 |
| `level` | `Level`·`ServerLevel`·`LevelAccessor`·`LevelReader`·`BlockGetter`·`WorldGenLevel` | `World`(api) 또는 플러그인 레벨 콜백 `PortLevel` | 그 칸의 주인 region `BlockLevel`, 아니면 읽기 프록시 | 9장 |
| `player` | `Player`·`ServerPlayer` | api `Player` handle | 서버 `Player`, 메뉴 쪽은 그 연결의 `MenuPlayer` | handle 필드 |
| `entity` | 엔티티 shim | api `Entity` handle | 서버 엔티티 | 영역 2·3 이 쓸 때 더함(이 설계는 자리만) |
| `menu` | `AbstractContainerMenu` 와 하위 | `Object` 표 | 서버 `AbstractContainerMenu` 하위 | shim 필드 / 서버 `portMirror` |
| `slot` | `Slot` 과 하위 | `Object` 표 | 서버 `Slot` | 〃 |
| `container` | `Container`·`SimpleContainer`·`CraftingContainer` … | `Object` 표 또는 콜백 `PortContainer` | 서버 `Container` | 〃, 플러그인 구현은 V4 |
| `data` | `DataSlot`·`ContainerData` | `Object` 표 | 서버 `DataSlot` | 〃 |
| `blockentity` | `BlockEntity` 와 하위 | `Object` 표(지금 `furnaceHandle`) | 서버 블록 엔티티 | 〃, 지워진 것은 지금 `furnaceRebindIfRemoved` 규칙 |
| `item` | `ItemStack` | api `ItemStack`(값) | 서버 `ItemStack` | 4장 V1/V3 |
| `random` | `RandomSource` | 레벨·엔티티 난수면 그 짝의 표, 아니면 콜백 `PortRandom` | 서버 `RandomSource` | 4.4 |
| `text` | `Component` | api `Text` | api `Text`(서버도 씀) | 기존 변환 |
| `value:<이름>` | 그 밖 값 객체(채팅 서명 등) | 4.3 | 4.3 | — |

- kind 는 손으로 쓰는 작은 코드다(서버 `PortKinds`·`PortConvert`, 호환 `Peers`·`PortValues`). kind 하나를 더하는 PR = 양쪽 변환 + 소유 규칙 + 왕복 시험, **독립 검토**. 지금 있는 변환(`ItemStack.fromFurnace`/`furnaceValue`, `ApiConvert.item`, 블록 상태 번호, `PlacementBridge.ReadLevel`)은 kind 의 몸통으로 옮긴다(새로 쓰지 않는다).
- 지금 흩어진 짝(1장)은 해당 kind 를 처음 쓰는 PR 에서 그 kind 로 옮긴다. 옮긴 뒤 옛 길(`ChunkInternals` 의 좁은 메서드)이 더 쓰이지 않으면 그 PR 에서 지운다(두 벌을 남기지 않는다).

## 4. 형식 바꾸기

### 4.1 중립 형식 집합 (경계를 건너는 것은 이것뿐)

원시형, `String`, `UUID`, `byte[]`·`int[]`·`long[]`, `java.time.Instant`, `Optional`/`List` (요소도 중립), api 값 형식(`org.domi.furnace.api.item.ItemStack`·`text.Text`·`nbt.NbtCompound`·`world.World`·`entity.*` handle …), `Object` 표, 그리고 `api.internal.port` 의 콜백 인터페이스(`PortLevel`·`PortRandom`·`PortContainer`·`PortHooks.*`)와 값 레코드. 생성기가 인터페이스 서술자를 이 집합으로 검사한다(밖이면 빌드 멈춤).

### 4.2 네 가지 바꾸기

| 이름 | 쓰는 곳 | 규칙 |
|---|---|---|
| **V1 값** | 레코드·불변 값(`MessageSignature`·`SignedMessageLink`·`PlayerChatMessage`·`CraftingInput`·`Vec3` …), 계산 결과 스택(`quickMoveStack` 반환) | 성분별로 중립 형식으로 복사. 레코드 shim 은 성분 필드 말고 다른 필드를 둘 수 없으니(Paper 와 같은 `getRecordComponents`) 늘 V1 이다. 성분이 모두 중립으로 바뀌는 레코드는 생성기가 **변환을 자동으로** 만든다(`value` 머리줄, 5장) |
| **V2 짝 객체** | 숨은 상태가 있는 클래스(`MessageSignatureCache`·`LastSeenMessagesValidator`·`SignedMessageChain$Decoder`·메뉴·컨테이너·블록 엔티티) | shim 은 표만 든다. 모든 멤버가 이식 줄로 간다. 상태를 두 벌 두지 않는다 |
| **V3 값+되쓰기** | 바닐라가 참조로 받아 그 자리에서 고치는 가변 값: 칸의 `ItemStack`, `StringReader` 커서, `MutableBlockPos` 를 고치는 멤버 | 들어갈 때 값, 나올 때 **같은 NMS 객체에 되쓴다**. 줄 인자에 `inout` 을 단다(바닐라 몸통이 그 인자를 고치는 멤버만 — 줄을 쓰는 스레드가 javap 로 확인). 칸에서 나온 스택은 지금의 살아 있는 보기(CAS 되쓰기) 규칙 그대로 |
| **V4 콜백 짝** | 플러그인이 **구현**한 인터페이스 객체(자기 `Container`, FAWE 레벨 프록시)와 **상속**한 클래스(7장)를 서버가 불러야 할 때 | 서버에 생성 어댑터(서버 인터페이스를 구현하거나 갈고리 하위 클래스)를 두고 호출을 `api.internal.port` 콜백으로 돌려보낸다. 호환 쪽 콜백 구현이 서버 인자를 NMS 형식(거울·값)으로 바꿔 플러그인 객체의 가상 메서드를 부른다 |

### 4.3 값 객체 규칙(네트워크·명령 영역)

- 기본은 V1. 판정: 바닐라 클래스가 `record` 이거나 모든 인스턴스 필드가 `final` 이고 바깥에서 그 객체를 고치는 멤버가 없으면 V1, 아니면 V2. 판정은 생성기가 서명표로 한다(줄에 적지 않는다).
- V1 성분 바꾸기는 kind 표(3.2)를 따른다. `byte[]` 성분(서명 256바이트)은 복사한다 — 바닐라 `MessageSignature` 는 배열을 그대로 들지만(복사 없음) 바닐라 밖에서 그 배열을 고치는 길이 없으므로 결과가 같다(가정 — 줄을 쓰는 스레드가 javap 로 `bytes()` 의 호출처를 확인).
- 성분에 중립으로 못 바꾸는 서버 형식이 있으면(예: 서버 `SignedMessageBody` 를 성분으로 든 레코드) 그 성분 kind 를 먼저 더한다(재귀). 둘 다 못 하면 그 줄은 쓰지 않는다(던지는 몸통 유지).
- 서버와 호환 계층이 **같은 라이브러리 형식**을 따로 들고 있는 경우(서버 `command.brigadier.StringReader` ↔ 호환 `com.mojang.brigadier.StringReader`): 같은 이름이어도 다른 클래스다. `StringReader` 는 V3(문자열 + 커서 `inout`), `ParseResults` 처럼 트리를 든 것은 V2(서버 쪽 파싱 결과를 표로) — 명령 영역이 kind 로 더한다.
- `ByteBuf`(netty): 서버와 호환 계층이 같은 netty 를 실으므로 중립 형식에 넣지 않고 **같은 객체**를 넘긴다(가정 — 서버 fatJar 와 호환 묶음의 netty 가 한 클래스 로더에서 오는지 틀 PR 에서 시험으로 확인. 다르면 `byte[]` V3).

### 4.4 난수

바닐라는 넘긴 `RandomSource` 객체를 그대로 쓰고 호출 순서가 결과를 정한다(난수 순서까지 이식 규칙). 그래서 복사하지 않는다.
- 레벨·엔티티의 난수(`level.random`, `entity.getRandom()`)는 그 짝의 서버 난수 객체 표를 넘긴다 — 같은 객체를 서버와 플러그인이 함께 돌린다(바닐라와 같다).
- 플러그인이 만든 난수(`RandomSource.create(seed)`)는 V4: 서버 쪽 어댑터가 부를 때마다 `PortRandom` 콜백으로 그 NMS 객체를 부른다. 플러그인이 같은 시드로 만든 바닐라 난수와 같은 수열(`LegacyRandomSource` shim 이 바닐라 알고리즘 — 이미 있음).

## 5. 이식 명세 (`.ports`)

`furnace-compat-paper/src/shimspec/ports-1.21.1/<영역 번호>-<이름>.ports` — 영역 나눔은 `fill-areas.txt`(영역 PR 끼리 충돌하지 않게). 문법은 명세·연결 파일과 같은 들여쓰기 꼴.

```
#furnace-ports 1 1.21.1 area=4
# 머리줄: NMS 클래스 = 서버 클래스(org.domi.furnace.server. 뒤) kind [hook] [value]
class net.minecraft.world.inventory.AnvilMenu = menu.AnvilMenu  kind=menu hook
  <init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V -> new(int, inventory, access)  check=detached
  createResult()V                                  -> createResult            check=menu  hook
  quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack; -> quickMoveStack  check=menu hook
  calculateIncreasedRepairCost(I)I                 -> static calculateIncreasedRepairCost  check=pure
class net.minecraft.world.inventory.Slot = menu.Slot  kind=slot hook
  safeTake(IILnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/item/ItemStack; -> safeTake  check=menu
class net.minecraft.network.chat.MessageSignatureCache = chat.MessageSignatureCache  kind=value:v2
  pack(Lnet/minecraft/network/chat/MessageSignature;)I -> pack  check=any
class net.minecraft.network.chat.MessageSignature = chat.MessageSignature  kind=value
```

- 왼쪽 = NMS 멤버(서술자 늘 적음 — 연결 파일과 같다). 오른쪽 = 서버 메서드 이름(서술자는 생성기가 서버 클래스에서 kind 대응으로 찾는다; 겹치기가 애매하면 `name(서버 서술자)` 로 적는다). `static` 은 서버 정적 메서드, `new(…)` 는 서버 생성자(인자 바꾸기 kind 를 적는다 — 서버 생성자는 NMS 와 인자 모양이 달라 자동으로 못 맞춘다).
- **이름 규칙**: 평문은 Paper 서명표의 공개·보호 이름뿐(T-263 D1·3.5 와 같다). private·패키지 멤버는 지문 `#<10자리>`. 서버 쪽 이름은 Furnace 자신의 코드라 평문.
- `check=` 은 6장. `hook` 은 7장. 반환·인자 바꾸기는 kind 가 정하고, 줄에는 예외(`inout`, `same`)만 적는다.
- **생성물**(세 모듈이 같은 명세를 읽는다. 생성기 코드는 `furnace-api` 의 `portgen` 소스셋 하나 — 서버·호환은 그 출력을 빌드 의존으로 쓴다):
  1. `furnace-api`: `api.internal.port.<Area>Port` 인터페이스. 메서드 이름은 **서버 쪽 이름**에서 만든다(`anvilMenu$quickMoveStack`) — ② 검사(어댑터 이름 금지)를 그대로 통과한다(NMS 패키지 이름이 상수 풀에 들어가지 않음). 서술자는 4.1 의 중립 형식만.
  2. `furnace-server`: `server.port.gen.<Area>PortImpl implements <Area>Port` — 첫 호출 `PortGuards.<check>`, 인자 kind 바꾸기, 서버 메서드 직접 호출(javac 가 서버 서술자를 검사한다 — 서버 메서드가 바뀌면 컴파일이 멈춘다), 반환 바꾸기. 그리고 `hook` 클래스의 갈고리 하위 클래스.
  3. `furnace-compat-paper`: `compat.nms.port.gen.<Area>Ports` 의 `@LinkTarget(check="port")` 정적 메서드(받는 쪽 짝 찾기 → 다리 → 반환 바꾸기)와 `build/generated/shimspec/ports.links`. `FullShells`·`ShellMerge` 는 커밋된 `links-<판>/*.links` 와 함께 이 파일을 읽어 연결 몸통을 만든다(T-263 3.5 의 생성·빌드 검사를 그대로 받는다: 낡은 줄·대상 없음·`body=` 지문 바뀜).
- **손 shim 멤버**: 손 shim(지금 `AbstractContainerMenu`·`InventoryMenu`·`Slot` 류)에 이미 몸통이 있는 멤버는 `ShellMerge` 가 덮어쓰지 않는다(T-263 "낡은 줄" 규칙). 그런 멤버는 손 몸통이 생성된 `<Area>Ports.<멤버>` 를 부르게 고친다 — 연결 줄 대신 손 호출(빌드 검사: 이식 줄이 있는데 손 몸통이 그 정적 메서드를 부르지 않으면 "손 몸통이 이식 줄을 안 씀" 경고 → 13장 P2 에서 오류로).
- `check=port` 의 뜻: 대상이 서버 쪽에서 스스로 소유 검사를 한다(T-263 `api` 와 같은 종류 — 연결 몸통에 호환 쪽 소유 검사를 넣지 않는다). 대신 빌드 시험 `PortImplOwnerFirstTest` 가 서버 생성 몸통의 첫 호출이 `PortGuards.*` 인지 바이트코드로 본다.

## 6. 소유 검사 (`PortGuards`, 서버 쪽 한 곳)

| check | 대상 | 규칙 |
|---|---|---|
| `pure` | 상태·번호만 받는 계산(`calculateIncreasedRepairCost`, 상태 표 읽기) | 검사 없음. 인자에 `level`·`menu`·`blockentity`·`entity` kind 가 있으면 빌드 오류(순수가 아님) |
| `any` | 짝 객체 하나만 만지고 그 객체가 스스로 잠그는 것(V2 값 객체 — 채팅 캐시는 그 연결의 잠금) | 서버 객체의 잠금 안에서. 잠금 없는 서버 클래스면 빌드 오류 |
| `menu` | 메뉴·칸·컨테이너·데이터 칸 | 그 메뉴 주인 플레이어의 `menuLock()` 을 잡고(재진입 — 서버 클릭 처리 안에서 부른 플러그인 코드는 이미 잡고 있다) 돈다. 블록에 매인 창(`containerBlockPos() != null`)은 그 칸의 주인 스레드여야 한다: 주인이면 바로, 틱 스레드가 아니면 주인 region 에 넘기고 기다림(`NmsOwner` 의 10초·두 번 다시 보내기 규칙), 다른 region·전역 틱 스레드는 `ThreadOwnershipException`. 주인 없는 메뉴(플레이어 없이 만든 것)는 메뉴 객체 자신의 잠금 |
| `pos` | 레벨+자리를 받는 블록 동작 | 첫 `pos` 인자 칸의 주인 스레드(위와 같은 세 갈래). 레벨이 플러그인 레벨(V4)이면 검사 없이 그 객체를 부르는 스레드에서 — 읽기만(9.2) |
| `entity` | 엔티티 짝 | `NmsEntityOwner` 규칙을 서버 쪽으로 옮긴 것(영역 2·3 이 처음 쓸 때) |
| `detached` | 아직 월드·창에 걸리지 않은 짝을 만들고 고치는 것(생성자, 열기 전 메뉴) | 짝이 붙기 전이면 통과, 붙은 뒤면 그 kind 의 규칙으로 다시 판정 |

- 이 표는 지금 `NmsOwner`(블록 칸 주인)·`NmsEntityOwner`·연결 처리기의 메뉴 잠금 규칙을 **바꾸지 않고 한 곳으로 옮긴** 것이다. 옮긴 뒤 호환 쪽 `NmsOwner` 는 아직 이식 줄로 옮기지 않은 손 다리만 쓰고, 다 옮기면 지운다.
- **잠금 순서**(교착 방지): `menuLock` → 블록 region 차례. 서버 연결 처리기(`runOnBlockRegion`)도 region 스레드에서 `menuLock` 을 잡는다 — 같은 순서다. 메뉴 검사 안에서 다른 region 을 **기다리는** 길은 틱 스레드에서 막혀 있으므로(예외) 두 잠금을 서로 기다리는 일이 없다. 틀 PR 에 두 스레드가 같은 메뉴를 동시에 부르는 시험을 둔다.
- 검사 문구(`what`)는 NMS 이름(`"AnvilMenu#quickMoveStack"`) — 생성 몸통의 문자열 상수로 들어가는데, 이것은 서버 jar 의 상수 풀에 NMS 이름이 들어간다는 뜻이다. ②(api 모듈)·`CompatIsolationStaticTest`(서버→호환 클래스 참조) 어느 쪽에도 걸리지 않지만 **패키지 없는 단순 이름만** 쓴다(`net/minecraft/` 글자가 서버 상수 풀에 생기지 않게 — 그 검사가 글자로 보므로).

## 7. 플러그인 상속 — 갈고리 하위 클래스

### 7.1 무엇이 필요한가

바닐라에서 플러그인 하위 클래스의 재정의는 바닐라 코드 안의 가상 호출로도 불린다. AnvilGUI 의 `AnvilContainer extends AnvilMenu` 는 `createResult()` 를 재정의하고, 바닐라 `ItemCombinerMenu.slotsChanged` → `createResult()` 가 그 재정의를 부른다. `removed(Player)`·`clearContainer` 를 빈 몸통으로 재정의해 닫을 때 아이템을 돌려주지 않게 한다. 서버가 메뉴 로직을 돌리므로 **서버 쪽 가상 호출이 플러그인 재정의에 닿아야** 같은 결과가 난다.

### 7.2 어떻게

- 명세 `hook` 이 붙은 클래스마다 서버에 `server.port.gen.<서버클래스>$Port extends <서버클래스>` 를 생성한다. `hook` 이 붙은 멤버마다:
  ```java
  @Override public void createResult() {
      if ((overrides & BIT_createResult) != 0) hooks.createResult(mirror); else super.createResult();
  }
  public void port$super$createResult() { super.createResult(); }
  ```
  `overrides` 는 짝을 만들 때 호환 계층이 정한 비트 집합(플러그인 클래스가 그 멤버를 **선언**했는가 — 호환 계층이 플러그인 `Class` 를 한 번 훑어 `ClassValue` 에 둔다). 재정의하지 않은 멤버는 비트가 꺼져 있어 경계를 건너지 않는다(성능·순서 모두 바닐라 그대로).
- `hooks` 는 `api.internal.port.PortHooks.<Area>` 콜백(생성 인터페이스, 중립 형식). 호환 쪽 생성 구현이 서버 인자를 NMS 형식으로 바꿔(거울·값) 플러그인 객체의 그 메서드를 **가상 호출**한다.
- 플러그인의 `super.createResult()` → shim `AnvilMenu#createResult` 연결 몸통 → 이식 줄 → 서버 생성 구현이 `m instanceof AnvilMenu$Port h ? h.port$super$createResult() : m.createResult()` — 갈고리를 다시 타지 않아 무한 되부름이 없다. 순서는 바닐라 `invokespecial` 과 같다.
- 생성기 검사(빌드 멈춤): 서버 클래스가 `final` 이거나 `hook` 멤버가 `final`/`private` 이면 오류. 바닐라에서 `final` 인 멤버에 `hook` 을 달면 오류(바닐라도 재정의 못 함). `@NmsInheritable` 이 없는 shim 클래스에 `hook` 클래스 줄을 달면 오류(상속을 허락하지 않은 클래스).
- **생성자**: 플러그인 하위 클래스의 `super(id, inv, access)` → shim 생성자(이식 줄 `new(…)`) → 서버 `AnvilMenu$Port` 생성. 짝 만들기 중 서버 생성자 안에서 가상 호출이 일어나면(바닐라 생성자가 `addSlot` 을 부름) 그 시점엔 플러그인 객체 초기화 전이다 — 바닐라와 같은 상황이고(자바 생성자 규칙), 갈고리도 같은 순서로 부른다. `overrides` 는 서버 생성자 **인자**로 넘겨(필드 대입보다 먼저 정해짐) 생성 중 가상 호출도 바닐라처럼 재정의에 닿는다.
- **필드**: AnvilGUI 는 `this.checkReachable = false`, `this.cost.set(0)`, `this.itemName` 을 쓴다. 필드는 필드 리다이렉트(PR C) 명세 `=>` 줄로 짝의 서버 값에 잇는다 — 이 설계는 `=>` 대상으로 이식 줄의 생성 접근자를 쓸 수 있게 한다(`checkReachable => port` — 대상이 이식 줄 `@LinkTarget` 이면 같은 생성 몸통). Paper 전용 필드(`checkReachable`)는 서버 메뉴에 같은 뜻의 값이 있어야 한다 — 없으면 메뉴 PR 이 서버에 더한다(Paper 동작: `stillValid` 를 건너뜀).
- 같은 틀이 플러그인 **구현** 인터페이스(V4)에도 쓰인다: 인터페이스는 모든 멤버가 `hook` 인 생성 어댑터(`server.port.gen.Container$Callback implements Container`).

## 8. 메뉴 적용 (첫 사용처)

### 8.1 서버가 연 창의 거울

- `player.containerMenu`·`inventoryMenu` 읽기(지금 `ServerPlayer.furnaceRefreshMenus`)가 서버 메뉴의 `portMirror` 를 돌려준다. 없으면 그 서버 클래스에 맞는 NMS 클래스(`ChestMenu`·`AnvilMenu` …, 명세 머리줄)의 거울을 만든다 — `instanceof ChestMenu` 가 Paper 와 같아진다. 지금의 `NmsMenus.View` 는 지운다.
- 거울의 `slots`(공개 final 필드)는 서버 메뉴 칸마다 `Slot` 거울(짝 = 서버 `Slot`)로 채운다. 서버 메뉴는 생성 뒤 칸 목록을 바꾸지 않는다(바닐라와 같음 — 서버 `addSlot` 은 생성자에서만, 빌드 시험으로 확인). `containerId` 는 표에서 읽어 생성자에서 정한다.
- 칸의 `getItem` 은 V3: 살아 있는 보기(지금 `liveMenuSlot` 규칙을 이식 줄 구현으로 옮김). 다른 점: 지금은 창의 컨테이너 칸(상자·제작 결과)을 "마지막으로 알린 값의 사본, 고치면 던짐"으로 두었는데, 이제 `menu` 검사가 주인 스레드(또는 넘겨 기다림)를 보장하므로 **그 칸도 바로 되쓴다**(Paper 와 같다). 다른 region 틱 스레드면 예외(교착 방지 — 지금 규칙과 같은 기준).
- `getCarried`·`setCarried`·`getStateId`·`incrementStateId`·`broadcastChanges`·`sendAllDataToRemote`·`quickMoveStack`·`clicked`·`stillValid`·`removed`·`slotsChanged` 는 모두 이식 줄(지금 `UnsupportedNmsCall` 로 막은 것들이 열린다).

### 8.2 플러그인이 만든 창

1. `new AnvilContainer(...)`(→ `AnvilMenu` 생성자) → 이식 줄 `new(int, inventory, access)` → 서버 `AnvilMenu$Port`(`MenuPlayer` = 그 인벤토리 주인의 연결 메뉴 플레이어, `BlockAccess` = `access` kind: `ContainerLevelAccess.create(level, pos)` 는 월드+자리, `NULL` 은 없음). 서버 메뉴의 `portMirror` = 그 플러그인 객체.
2. AnvilGUI 꼴 열기:
   - `serverPlayer.nextContainerCounter()` → 이식 줄(서버 `Player.nextContainerCounter`).
   - `connection.send(new ClientboundOpenScreenPacket(id, MenuType.ANVIL, title))` → 지금 패킷 다리 그대로.
   - `serverPlayer.containerMenu = container` → **필드 리다이렉트(PR C)** `containerMenu => PortMenus.setContainerMenu` : `menu` 검사 안에서 서버 `p.setContainerMenu(짝)` 만 한다. Paper 도 필드 대입일 뿐이다(닫기·이벤트·패킷 없음) — 같은 뜻.
   - `serverPlayer.initMenu(container)` → 이식 줄: 서버 연결 처리기가 자기 창에 하는 것과 같은 `addSlotListener(menus)`·`setSynchronizer(menus)`(첫 `container_set_content` 를 보낸다 — Paper `initMenu` 와 같은 순서). 서버 쪽 손 함수 `PortMenus.initMenu(player, menu)` 가 `ConnectionHandler` 의 메뉴 플레이어를 꺼내는 좁은 접근자를 쓴다.
   - 닫기 `CraftEventFactory.handleInventoryCloseEvent` + `doCloseContainer()` → 이식 줄(서버 `doCloseContainer` 가 이미 Paper 순서).
3. 그 뒤 클라이언트 클릭은 서버가 **그 짝 메뉴로** 처리한다(`handleContainerClick` 은 `p.containerMenu()` 만 본다 — 바뀔 것 없음). 서버 `doClick` → `slotsChanged` → `createResult()` 가상 호출 → `AnvilMenu$Port` 갈고리 → AnvilGUI 의 `createResult` 재정의. 동기화 패킷(`container_set_slot`·`container_set_data`)과 `stateId` 는 서버 메뉴가 바닐라 이식 그대로 내므로 Paper 와 같은 순서다.
4. `getBukkitView()`: 짝 메뉴의 Bukkit 보기 = 서버가 자기 창에 주는 보기(이미 있음)와 같은 길. 클릭 이벤트(`InventoryClickEvent`)의 보기도 그것.
5. `PUTFIELD containerMenu` 가 PR C 전에 오면 지금처럼 `UnsupportedNmsLink.Field` 덫(정직한 실패). 메뉴 PR(P2)은 PR C 병합 뒤에 이 줄을 켠다 — 그 전 단계도 완결(덫).

### 8.3 시험(P2)

- 거울: 상자·모루·인벤토리 창을 열고 `containerMenu instanceof` 가 Paper 클래스와 같다, 같은 창은 같은 객체, 칸 되쓰기가 클라이언트에 `container_set_slot` 한 번.
- AnvilGUI 꼴 플러그인(시험 플러그인, ASM 없이 Java 로 — `furnace-plugin-test` 에 NMS 상속 표본): 열기 → 왼쪽 칸에 넣기 → `createResult` 재정의가 결과 칸을 채움 → 결과 클릭 → 닫기 때 `removed` 재정의(빈 몸통)로 아이템이 돌아오지 않음. 패킷 순서·`stateId` 를 Paper 1.21.1 관측표(러너 장비)와 대조 — 관측표는 그 PR 에서 장비로 만든다.
- 동시성: 다른 region 틱 스레드에서 블록 매인 메뉴 호출 → 예외, 작업 스레드 → 넘겨 기다림, 클릭 처리 안 재진입.

## 9. 블록·블록 엔티티 적용

### 9.1 핫 경로

- 상태 속성 읽기(`getValue`·`isAir`·빛·모양 표)는 지금처럼 호환 쪽 표(`BlockStateInternals.Table`)에서 읽는다 — 이식 다리를 타지 않는다. 다리는 **동작**(randomTick·neighborChanged·updateShape·useItemOn·canSurvive …)용이다.
- `pure` 줄(상태 번호만): 호환 스텁 → 단형 인터페이스 → 서버 정적 표. 할당 0.
- 레벨 줄: `level` kind 가 그 칸의 주인 region `BlockLevel` 을 고른다. 고르기 비용을 줄이려고 서버 `PortKinds.level` 은 **스레드의 마지막 region 하나**를 region 스레드 객체 필드에 두고(ThreadLocal 아님), 그 region 이 그 청크를 아직 갖는지 확인만 한다(region 나뉨·합침은 확인에서 걸린다). 목표 +50ns 안, JMH 로 틀 PR 에 숫자.

### 9.2 플러그인 레벨

`PlacementBridge` 의 규칙을 kind 로 일반화한다: 서버 자신의 월드이고 이 스레드가 그 칸 주인이면 서버 region 레벨, 서버 월드인데 주인이 아니면 `pos` 검사 규칙(넘겨 기다림 / 예외), **플러그인 레벨**(FAWE 프록시처럼 서버 월드가 아닌 `Level` 하위 객체)이면 `PortLevel` 콜백(지금 `PlacementLevel` 을 넓힌 것 — 상태·유체·블록 엔티티·높이·경계 읽기)으로 그 객체의 가상 메서드를 읽는 서버 `ReadLevel`. 읽기 밖 요청은 지금처럼 `UnsupportedNmsCall`. `PlacementBridge` 는 이 kind 로 옮기고 지운다(허용 목록 줄 하나 준다).

### 9.3 블록 엔티티

- 짝 = 지금 `furnaceHandle`(서버 블록 엔티티 객체). `blockentity` kind 가 이를 표로 쓰고, 지워진 블록 엔티티면 지금의 `furnaceRebindIfRemoved` 규칙으로 다시 찾는다(자리 + 월드 → 서버 블록 엔티티, `pos` 검사 안에서).
- 월드 밖 블록 엔티티(`new ChestBlockEntity(pos, state)` 후 월드에 넣기 전)는 지금 NBT(`furnaceDetachedData`)로 든다 — `detached` 검사 줄은 그 NBT 를 서버 쪽 떨어진 블록 엔티티(`BlockStateInternals.defaultBlockEntityNbt` 와 같은 길)로 만들어 부르고 결과 NBT 를 되쓴다(V3). 월드에 붙으면 짝으로 바뀐다(지금 `furnaceAttach`).

## 10. 네트워크·명령 적용

- 채팅 서명 계열: `MessageSignature`·`SignedMessageLink`·`SignedMessageBody`·`PlayerChatMessage`(레코드) = V1, `MessageSignatureCache`·`LastSeenMessagesValidator`·`SignedMessageChain$Decoder`·`RemoteChatSession`(상태) = V2. 연결에 매인 것(그 플레이어의 캐시·검증기)은 거울 — 서버 연결이 든 객체의 표. `check=any`(그 객체 잠금) 또는 연결 차례가 필요한 것은 그 kind 가 연결 처리기에 넘긴다(네트워크 영역이 kind 를 더할 때 정함).
- `FriendlyByteBuf#readUUID(ByteBuf)` 처럼 정적·`ByteBuf` 만 오가는 줄은 `pure` 로 바로(4.3 의 netty 가정이 맞을 때).
- 명령: `StringReader` V3, `ParseResults` V2(서버 파싱 결과 표). 브리가디어 트리(`CommandDispatcher`)는 지금 호환 쪽 따로 있으므로 이 틀의 대상이 아니다(명령 영역 노트).

## 11. 성능

- 다리 호출: 호환 스텁의 `static final <Area>Port B`(서버가 기동 때 `PortBridges.install` 로 넣은 단일 구현을 클래스 초기화 때 읽음) → `invokeinterface` 단형 → JIT 인라인. 서버 구현은 상태 없는 싱글턴이라 시험이 서버를 여러 번 띄워도 같은 객체다(서버마다 다른 것은 표가 가리키는 객체 쪽).
- 할당: `pure`·`state`·`block`·`pos`(서버 `Pos` 는 탈출 분석으로 사라지는 꼴 — JMH 로 확인)·`menu` 줄은 할당 0 목표. `item` 줄은 값 바꾸기(NMS → api → 서버) 두 번 할당 — 메뉴 클릭·상호작용 빈도라 무시할 만하다(JMH 로 숫자만 싣는다). 아이템을 많이 오가는 줄이 핫 경로로 잡히면 그때 `item` kind 에 서버 스택 표 캐시를 더하는 것은 **kind 하나의 내부 변경**이라 줄은 그대로다.
- 리플렉션 없음. 플러그인 클래스 재정의 훑기(7.2)는 클래스마다 한 번(`ClassValue`).
- JMH(`src/bench`, 틀 PR): 직접 서버 호출 / `pure` 줄 / `pos` 줄(주인 스레드) / `menu` 줄(잠금 비경쟁) / 갈고리 켜진 멤버.

## 12. api.internal 다리를 더하는 절차

- **허용 목록(③)**: `org/domi/furnace/server/port/` 접두 한 줄을 이 설계의 검토로 더한다(이유: "이식 다리 — 생성 구현과 kind 손 코드"). 서버의 다른 클래스는 계속 `api.internal` 을 못 쓴다. `AdapterBoundaryStaticTest` 는 클래스 단위 표라 접두 줄을 받게 한 줄 고친다(줄어들기 규칙은 그대로 — 접두 안에 쓰는 클래스가 하나도 없으면 "지우라").
- 옛 손 다리(`ChunkInternalsImpl` 의 메뉴·`PlacementBridge`)는 이식 줄로 옮긴 만큼 지운다. 새 일에 `ChunkInternals` 에 좁은 메서드를 더하지 않는다 — 이식 줄로 한다(문서 `adapter-boundary.md` 에 이 규칙 추가).
- **무엇을 누가 검토하나**

  | 바꾸는 것 | 검토 |
  |---|---|
  | 생성기·`PortGuards`·kind(`PortKinds`·`PortConvert`·`Peers`·`PortValues`)·콜백 인터페이스 | 독립 검토(손 코드 — 처음 한 번, 고칠 때마다) |
  | 이식 줄, `check=pure` 또는 읽기 kind 이고 정답기(O1~O3)가 같음 + 멤버별 구분력 통과 | 검토 없음(T-263 F1 과 같은 기준, `proof=` 를 줄에 적음) |
  | 그 밖의 이식 줄(쓰기·`menu`·`pos`·`hook`) | **PR 묶음 검토**: 한 PR 에 줄 여럿, 검토자는 줄마다 "서버 메서드가 바닐라 멤버와 같은 일을 하는가"를 서버 몸통·바닐라 javap 로 본다. 검토 스레드 재사용(영역별 검토 스레드). 300줄 이하 조건부 승인 뒤 보강만이면 재검토 생략(기존 시간 단축 규칙) |
  | `new(…)` 생성자 줄 | 묶음 검토 + 그 클래스 짝 만들기 시험 하나 |

- G4 목록을 이식 줄로 옮기는 일: `inferLinks` 의 G4 출력에 "이식 줄 초안" 칸을 더한다(서버 서술자의 kind 대응이 모두 있는 짝만, 없는 kind 는 "kind 필요: level·random" 처럼 적음). 영역 스레드는 초안을 확인해 `.ports` 로 옮긴다 — **G4 는 짐작**이므로 자동 채택은 정답기로 증명된 읽기 줄만.

## 13. 원칙 점검

| 원칙 | 어떻게 |
|---|---|
| 결과 = 바닐라·Paper | 동작은 서버 이식 코드 그대로(난수 객체 공유 4.4, 메뉴 패킷은 서버 메뉴가 냄), 플러그인 재정의는 갈고리로 바닐라 가상 호출 순서 그대로(7장). 못 바꾸는 kind 는 줄을 쓰지 않는다(던지는 몸통 유지) |
| 바닐라 통째 이식 금지 | 생성되는 것은 위임·바꾸기뿐. 동작 코드는 이미 있는 서버 이식 |
| 커밋 기준선 Mojang 이름 금지 | `.ports` 는 공개·보호 이름만 평문(연결 파일과 같은 D1), 생성물은 build/ 에만 |
| 임시 금지 | 각 PR 이 완결: 틀 PR 은 kind·검사·생성기 + 실제 줄 몇 개와 시험, 메뉴 PR 은 옛 `NmsMenus.View`·`ChunkInternals` 메뉴 메서드를 지우고 바꾼다(두 벌 없음). PR C 전의 필드 대입은 정직한 덫 |
| 스레드 소유권 | 서버 쪽 첫 호출 `PortGuards`(빌드 시험), 규칙은 지금 것을 옮긴 것 |
| 리플렉션 다리 금지 | 서버 호출은 생성된 보통 Java(javac 가 서술자를 검사) |

## 14. 위험과 가정

- (가정) 서버 메뉴는 생성 뒤 칸 목록을 바꾸지 않는다 — 틀 PR 에서 서버 `addSlot` 호출처를 시험으로 확인(생성자 밖 호출이 있으면 거울 `slots` 갱신을 그 kind 에 넣는다).
- (가정) netty `ByteBuf` 가 서버·호환 계층에서 같은 클래스다(4.3) — 틀 PR 시험.
- 플러그인 하위 클래스가 바닐라에 없는 생성자 꼴을 쓰는 경우(Paper 전용 생성자)는 지금 `@NmsInheritable` 규칙대로 그 자리에서 `UnsupportedNmsLink`.
- 콜백 짝(V4)에서 플러그인 컨테이너의 스택 객체 동일성: 서버 코드는 불변 스택을 "새로 set" 하는데 바닐라는 그 자리에서 고친다. 어댑터는 **같은 칸의 같은 아이템**이면 그 NMS 객체를 제자리에서 고치고(`furnaceSet`) `setItem` 을 부르지 않으며, 아이템이 바뀌거나 비면 `setItem` 을 부른다(가정 — 바닐라의 제자리 고침과 `setItem` 호출 횟수가 같아지는지 메뉴 PR 에서 Paper 관측표로 대조, 다르면 kind 규칙을 고친다).
- 갈고리 비트를 "플러그인이 선언했는가"로 정하므로 플러그인이 재정의를 **런타임에 바꾸는** 일(없음 — 자바 클래스는 고정)은 고려하지 않는다. 플러그인이 shim 하위의 하위를 다른 플러그인 jar 에 둔 경우도 `Class` 사슬로 훑으므로 같다.
- 서버 생성자 안 가상 호출(7.2)이 플러그인 재정의에 닿는 것은 바닐라와 같지만, 플러그인 재정의가 아직 초기화 안 된 자기 필드를 읽으면 NPE — 바닐라에서도 같다.

## 15. 구현 순서 (PR 별 독립 검토)

| PR | 내용 | 검토 | 담당 제안 |
|---|---|---|---|
| P1 틀 | `portgen`(세 생성물 + `ports.links`), `.ports` 문법·이름 검사, `PortBridges` 설치, `PortGuards`(pure·any·menu·pos·detached — `NmsOwner` 규칙 이전), kind: state·block·pos·dir·level(`PlacementBridge` 이전·삭제)·player·item·text·random, 허용 목록 접두 줄, 빌드 시험(첫 호출·중립 형식·이름), JMH. **실제 줄**: 블록 영역 `pure`·`pos` 줄 몇 개(예: `AnvilMenu#calculateIncreasedRepairCost`, `CropBlock#randomTick`, `CropBlock#hasSufficientLight`)와 서버 값 대조 시험 | 독립 검토(생성기·손 kind) | 이 스레드 (Opus high — 동시성·생성기) |
| P2 메뉴 | kind: menu·slot·container·data, 거울(`portMirror`)·짝 만들기, 갈고리 하위 클래스·`PortHooks`, `NmsMenus.View`·`ChunkInternals` 메뉴 메서드 삭제, `initMenu`·`nextContainerCounter`·`doCloseContainer` 줄, AnvilGUI 꼴 시험 플러그인, Paper 관측표 대조. `containerMenu` 필드 줄은 PR C 병합 뒤(그 전엔 덫) | 독립 검토 | 이 스레드 → 이후 줄 채우기는 아이템 영역 |
| P3 블록 엔티티·플러그인 레벨 | kind: blockentity(월드·월드 밖), `PortLevel` 콜백 넓히기 | 독립 검토 | 블록 영역(틀 PR 뒤) |
| P4 값 객체 | `value` 자동 레코드 변환, V2 값 객체 거울, netty 가정 확인 | 독립 검토 | 네트워크 영역 |
| 줄 채우기 | 영역별 `.ports` | 12장 표 | 각 영역 스레드 |

P1 이 먼저(나머지 전부의 바탕), P2·P3·P4 는 P1 뒤 병렬(파일이 다르다 — kind 파일만 겹치면 kind 별 파일로 나눈다). P2 의 필드 줄은 PR C 뒤.
