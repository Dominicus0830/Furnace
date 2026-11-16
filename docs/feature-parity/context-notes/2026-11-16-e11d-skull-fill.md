## 2026-11-16 이름만 있는 머리의 스킨 채우기 — 서버 쪽(T-141 E11d)

원문 기준: 바닐라 1.21.1 `PlayerHeadItem.verifyComponentsAfterLoad`, `ResolvableProfile`(`isResolved`·`resolve`), `SkullBlockEntity`(`setOwner`·`updateOwnerProfile`·`fetchGameProfile`),
`ItemStack` 생성자·`applyComponents` 의 `verifyComponentsAfterLoad` 호출(디컴파일 대신 `javap -c` 바이트코드로 대조). E11c-1(호환 계층 `SkullProfileFill`)의 후속이다.

### 바닐라가 하는 일
- **언제:** 스택이 만들어질 때(`ItemStack` 생성자 = NBT 읽기·네트워크 읽기(크리에이티브 칸 패킷 포함)·`copy`, 그리고 `applyComponents` = `/give` 의 `ItemInput.createItemStack`) 머리의 `profile` 이 **확인 안 됨**이면 시작한다.
- **확인 안 됨(`isResolved` 아님):** `속성이 없고 && (UUID 가 있음 != 이름이 있음)` — 즉 이름만 있거나 UUID 만 있고 속성이 없는 것. **이름과 UUID 가 둘 다 있으면(`setOwner(name)` 의 UUID 0 포함) 확인된 것**이라 안 묻는다.
- **무엇을:** UUID 가 있으면 세션 서버(`fetchProfile(uuid, true)`, 서명된 속성)로. 없으면 이름: 올바른 플레이어 이름만, 사용자 캐시(`GameProfileCache.getAsync`, 온라인 모드가 아니면 못 찾은 이름은 오프라인 프로필) → 찾은 UUID 로 세션 서버 → 세션이 못 주면 이름 조회 결과(`.or(() -> 이름 조회 결과)`).
  끝내 못 찾으면 이름 경로는 `(UUID 0, 이름)`, UUID 경로는 `(그 UUID, 이름 또는 "")` — 둘 다 이름·UUID 가 모두 있으니 **한 번 시도하면 확인된 것이 되어 다시 시작되지 않는다.**
- **캐시:** 이름·UUID 각각 guava `LoadingCache`(마지막 접근 뒤 10분, 256개), 실패한 Future 도 그대로 둔다. 이름 조회와 세션 조회는 `Util.backgroundExecutor`, 결과는 **주 스레드에서** 그 스택에 쓴다(`CHECKED_MAIN_THREAD_EXECUTOR`).
- **머리 블록:** `SkullBlockEntity.loadAdditional`·`applyImplicitComponents` 가 `setOwner` → `updateOwnerProfile` 로 같은 확인을 하고, 끝나면 `owner = 확인된 값; setChanged()`. 클라이언트에 따로 보내지 않는다.

### Furnace 구현 (`item/ProfileFill`)
서버 `ItemStack` 은 불변 값이라 "그 스택 객체에 쓴다"가 없다. E11c-1 과 같은 방식으로 **스택이 놓이는 칸의 쓰기 지점**이 확인을 시작하고, 끝나면 "그 칸이 아직 같은 확인 안 된 프로필의 머리일 때만" 채운 프로필로 바꾼다(`replaceIfStillTheSame`).
- 확인기(`ProfileFill.Service`)는 `ServerCore` 가 만든다: 사용자 캐시(`GameProfileCache.get`)·`SessionService.fetchProfile`. 이름·UUID 캐시와 만료·크기는 바닐라와 같다. 조회 풀은 데몬 스레드 4개(실패·막힘은 DEBUG 로만).
- 쓰기 지점:
  - **플레이어 인벤토리**(`PlayerInventory.set/add/replace/load`): `/give`, 접속 때 불러오기, 크리에이티브 칸 패킷, 줍기. 갱신은 모든 칸에 적용(`updateSlots`, 객체 동일성 비교-교환)이라 확인되는 동안 칸 사이로 옮겨지거나 합쳐져도 채워진다. 클라이언트에는 다음 틱 창 동기화가 보낸다.
  - **떨어진 아이템**(`ItemEntity.setItem`): 생성자·NBT 읽기·`/summon`. 갱신은 그 엔티티를 가진 region 스레드에서(`ChunkLevel.runAtEntityOwner`, 도착한 region 이 엔티티를 안 가졌으면 최대 20번 다시), 그새 사라졌으면 버린다.
  - **상자류 블록 엔티티**(`ContainerBlockEntity.onAddedToLevel`/`startProfileFill`, `BlockEntityContainer.set`): 청크와 함께 불러올 때(`ChunkLevel.registerBlockEntities`), 놓인 뒤 NBT 를 읽을 때(`loadWithComponentsInLevel`·`loadCustomDataInto`), 메뉴·깔때기가 칸을 쓸 때. 갱신은 그 블록의 region 스레드에서, 블록 엔티티가 아직 있고 칸이 아직 같은 프로필일 때만, 바뀌면 `setChanged`.
  - **머리 블록 엔티티**(`SkullBlockEntity.onAddedToLevel`): 위와 같은 때 + 머리 아이템을 놓을 때(`BlockPlacement.updateBlockEntityComponents`). 소유자가 그새 바뀌었으면 건드리지 않고, 끝나면 `setChanged`(클라이언트 전송 없음 — 바닐라와 같다).
- 새 블록 엔티티 훅 `BlockEntity.onAddedToLevel(BlockLevel)`: 레벨이 있어야 시작할 수 있는 일을 위한 자리(기본 없음).

### Menu#update 의 fn (플러그인 API)
`Menu#update` 가 `fn` 을 칸 잠금(보관함 메뉴는 전역 클릭 잠금 포함) 안에서 불러 느린 `fn`·`fn` 안의 `Menu` 접근이 클릭 처리와 다른 스레드를 막을 수 있었다.
이제 `fn` 은 **어떤 잠금도 쥐지 않고** 부르고, 쓰기는 "읽은 객체가 아직 그대로일 때만"(객체 동일성) 잠금 안에서 한다. 그새 바뀌었으면 버리고 다시 읽어 `fn` 을 다시 부른다(`fn` 은 여러 번 불릴 수 있다고 javadoc 에 적었다 — `Inventory#update` 와 같다).

### Paper 와 같은 점·다른 점
- 호환 계층의 `SkullProfileFill`(E11c-1, 플러그인이 만든 머리·UUID 0→null 보정)은 그대로다. 서버 쪽은 바닐라 `isResolved` 규칙(UUID 0 + 이름은 확인됨)을 따르므로 두 경로가 같은 칸을 놓고 겹쳐도 서로 덮지 않는다(각자 "아직 같은 프로필일 때만").
- 바닐라는 스택 **객체**가 따라가는 대로 채우지만 Furnace 는 **쓰기 지점**에 묶인다. 알려진 차이:
  - 훅이 없는 곳에서 만들어져 훅이 없는 곳에 머무는 머리(몹 손·방어구, 액자, 갑옷 거치대, 말 가방·보트 상자·수레 상자, 보물 지도·거래 아이템, `ContainerBlockEntity` 가 아닌 보관 칸)는 채워지지 않는다. 이 곳들의 읽기 경로(`ItemStackNbt.read` 사용처)는 한 곳씩 훅을 달아야 한다 → 결정 항목 1.
  - 확인 중에 머리를 훅 없는 곳으로 옮기면 그 사본은 안 채워진다(플레이어 인벤토리 ↔ 상자는 양쪽에 훅이 있어 같은 확인에 다시 붙는다).
  - 바닐라는 `copy()` 마다 새 확인을 시작하지만 Furnace 는 같은 확인(캐시)을 쓴다 — 결과는 같다.
- 이름 조회는 접속자·사용자 캐시 → Mojang 이름 조회(`SessionService.lookupByName`, 서버의 `GameProfileCache` 는 `ProfileLookup.NONE` 이라 캐시만 보므로 직접 부른다) → 그래도 없으면 오프라인 모드만 오프라인 프로필(온라인 모드는 없음 → `(UUID 0, 이름)`).
  바닐라는 `online-mode=false` 서버도 Mojang 에 이름을 묻는다(그래서 오프라인 서버에서도 `Notch` 머리에 스킨이 보인다) — 같게 했다. 플러그인 API 의 `profileLookups`(온라인 모드이거나 프록시 포워딩일 때만 묻는다, E10)와는 일부러 다르다: 이쪽은 바닐라 명령·저장 아이템 경로다.
  **오프라인 모드에서 Mojang 에 없는 이름**은 바닐라 `GameProfileCache.get` 처럼 이름을 **소문자로** 바꿔 오프라인 프로필(`offlineUuid(소문자)`, 소문자 이름)로 만든다(`HeadProfileBackend`). 온라인 모드는 없음 → `(UUID 0, 이름)`.
  못 찾은 이름의 오프라인 프로필은 사용자 캐시(`usercache.json`)에 넣지 않는다(바닐라는 넣는다 — 새 이름마다 파일을 쓰지 않으려는 차이, 확인 결과는 같다). 대가: 오프라인 서버는 그 이름을 10분 캐시가 끝나면 다시 Mojang 에 묻는다. 사용자 캐시에 이미 있는 이름은 `peekAndTouch` 로 접근 시각을 갱신한다.
- 조회 실패는 로그 없이(DEBUG) 넘어간다(바닐라는 로드 실패만 경고, 조회 실패는 조용하다).

### 동시 요청·재시도·실패 처리 (바닐라보다 단단하게)
- Mojang 으로 나가는 조회는 동시에 4개까지만(`HeadProfileBackend.Limiter` — 바닐라 `Util.backgroundExecutor` 가 동시 실행을 제한하는 것과 같은 취지), 나머지는 줄을 선다.
- 429·5xx·연결 실패는 750ms 간격으로 3번까지 다시 시도한다(`SessionService.fetchProfileRetrying`/`lookupByNameRetrying`). 200 이외의 4xx·204 는 "없음"으로 보고 다시 안 한다.
- 끝내 안 되면 **온라인 모드는 실패한 Future** 로 끝낸다 → 스킨 없는 `(UUID 0, 이름)` 결과가 아이템·블록에 굳지 않고 머리는 "확인 안 됨"으로 남아 다음 쓰기 때 다시 시도된다. 실패한 Future 는 캐시에 30초만 둔다(바닐라는 10분 동안 그대로 둔다 — 일시 장애가 10분 가는 것을 피하려는 차이). 오프라인 모드는 인터넷이 없을 수 있어 바닐라처럼 "없음"으로 쳐 오프라인 프로필로 떨어진다.
- 서버 정지 때 전역 `ProfileFill` 서비스를 해제한다(`ServerCore.closeProfileFill`, `FurnaceMain` 의 `CLOSE_PLUGINS` 단계).
- 떨어진 아이템의 채움은 바닐라처럼 스택을 제자리에서 바꾸는 것이라 클라이언트에 메타데이터 패킷을 따로 보내지 않는다(`entityData.setQuiet`). region 이 안 맞으면 50ms 뒤 다시(최대 100번).

### 알려진 시험 공백
- 실제 `RegionLevel`(다중 region 스레드)에서 도는 경로(`runAtEntityOwner`·`runOnOwner` 로 다른 스레드 넘기기)는 가짜 레벨로만 시험했다. 실제 접속 시험은 `docs/user-todo/entries/2026-11-16-e11d.md` 의 확인 대기 항목으로 남긴다.
