# 이벤트 취소 계약 (취소하면 상태는 그대로, 화면 되돌리기는 코어가)

대상: `furnace-api` 의 `Cancellable` 이벤트 전부. 서버 쪽 도우미는 `furnace-server/.../plugin/ClientResync.java`.
배경: Paper·Spigot 에서는 이벤트를 취소해도 클라이언트가 먼저 보여 준 결과(블록·아이템 개수·날기)가 남아 "고스트 블록·아이템"이 생기고,
플러그인이 `updateInventory()` 같은 되돌리기를 직접 불러야 한다(`docs/feature-parity/paper-spigot-dev-complaints-2026-11-06.md` §1.2, 항목 2). Furnace 는 이를 코어 책임으로 못 박는다.

## 1. 규칙

1. **취소하면 그 동작이 일으킬 상태 변경은 하나도 일어나지 않는다.** 블록·아이템·체력·허기·엔티티 생성·내구도·쿨다운이 모두 그대로다.
   발화 지점은 상태를 바꾸기 직전 한 곳이고, 이미 바뀐 것을 나중에 되돌리는 방식은 쓰지 않는다. 한 동작이 여러 단계(활: 탄약 뽑기 → 발사)로 이뤄지면 취소 시 앞 단계도 되돌린다.
2. **클라이언트가 먼저 보여 줬을 수 있는 것은 코어가 서버 값으로 다시 보낸다.** 플러그인은 아무것도 하지 않아도 된다(`updateInventory` 같은 호출이 필요 없다).
3. 이벤트는 **구독자가 있을 때만** 만들고 스레드를 옮긴다(`has()`/`hasSubscribers`). 되돌리기 패킷은 **취소됐을 때만** 나가므로 구독자가 없거나 취소하지 않으면 비용이 없다.
4. 새로 취소할 수 있는 이벤트를 넣을 때는 아래 표에 행을 더하고(종류·되돌리는 것), 종류에 맞는 시험을 쓴다. `CancelContractTest` 가 표·시험·문서가 서로 맞는지 검사해 빠지면 CI 가 실패한다.

## 2. 종류

| 종류 | 뜻 | 코어가 할 일 | 시험 |
|---|---|---|---|
| `CLIENT_PREDICTED` | 클라이언트가 서버 답을 기다리지 않고 결과를 먼저 보여 준다(설치한 블록, 줄어든 아이템, 날기 상태, 움직인 위치) | 취소 시 예측한 칸·블록·능력·위치를 서버 값으로 다시 보낸다 | 진짜 연결 핸들러에 패킷을 넣는 끝-끝 시험(`CancelResyncE2ETest`): 서버 상태 그대로 + 되돌리기 패킷이 나간다 |
| `CLIENT_INPUT` | 클라이언트 입력 그 자체(웅크림·달리기 키) — 클라이언트가 직접 정하므로 되돌려 보낼 방법이 없다 | 서버 상태만 그대로 둔다(Paper 도 같다) | 서버 상태 확인 |
| `SERVER_DRIVEN` | 서버가 시작한 변화(물 흐름, 자라기, 화로 굽기, 폭발, 텔레포트 등)나 클라이언트가 답을 기다리는 동작(창 열기, 침대, 손 바꾸기) | 상태를 바꾸지 않으면 끝. 보낼 것 없음 | 상태가 그대로인지 확인 |

## 3. 되돌리는 도구 (`ClientResync`)

| 메서드 | 보내는 것 | 쓰는 곳 |
|---|---|---|
| `slot(player, 창칸)` / `hand` / `hands` | 플레이어 인벤토리 칸 하나(창 -2 패킷, Inventory 번호로 바꿔서) | 우클릭 취소, 설치 취소, 먹기 취소, 던지기·놓기 취소 |
| `inventory(player)` | 열린 창(없으면 인벤토리 창) 전체 + 커서 | 창 클릭 취소, 드롭 취소, 활·쇠뇌·삼지창 취소 |
| `heldSlot(player)` | 선택한 단축 칸 | 단축 칸 변경 취소 |
| `abilities(player)` | 날기 등 능력 | 비행 전환 취소 |
| `block(player, level, pos)` | 블록 상태 + **갱신 패킷이 있는 블록 엔티티**(표지판 글·배너·머리·침대 색 등) | 부수기·왼쪽 클릭·우클릭 취소 |
| `returnToSlot(player, 창칸, stack)` | (패킷 없음) 취소된 드롭을 든 칸으로 | 드롭 취소(CraftBukkit 처럼 든 칸 먼저, 안 되면 첫 빈 칸) |

블록은 이 밖에 클라이언트의 블록 예측을 `block_changed_ack`(순번)가 통째로 되돌리므로, ack 를 빠뜨리지 않는 것이 첫째다(모든 우클릭·부수기 경로에서 취소 여부와 무관하게 보낸다).
창 -2 패킷의 칸 번호는 **Inventory 번호**(0~8 단축 칸, 9~35 주, 36~39 발·다리·가슴·머리, 40 보조 손)이지 창 칸 번호가 아니다. 이전에는 창 칸 번호(36+선택, 45)를 그대로 보내 엉뚱한 칸(갑옷 칸)을 덮을 수 있었다.

## 4. 이벤트별 표

`되돌리는 것` 은 취소 시 코어가 다시 보내는 것, `상태` 는 취소해도 바뀌지 않는 것이다. 시험 이름은 `CancelContractTest` 표에 있다.

| 이벤트 | 종류 | 되돌리는 것 | 취소하면 바뀌지 않는 것 |
|---|---|---|---|
| `BlockBreakEvent` | `CLIENT_PREDICTED` | 부수려던 블록 + 그 블록 엔티티 | 블록, 드롭, 도구 내구도 |
| `BlockPlaceEvent` | `CLIENT_PREDICTED` | 두 손 칸, 놓으려던 칸·눌린 칸 | 블록, 손 아이템 개수 |
| `BlockIgniteEvent` | `CLIENT_PREDICTED` | 불 자리 블록(부싯돌) | 불. 예외: 부싯돌 내구도는 닳는다(CraftBukkit 과 같다) |
| `BlockBurnEvent` | `SERVER_DRIVEN` | - | 불에 타는 블록 |
| `BlockExplodeEvent` | `SERVER_DRIVEN` | - | 블록, 불, 폭발 패킷 |
| `BlockFromToEvent` | `SERVER_DRIVEN` | - | 유체 흐름 |
| `BlockGrowEvent` | `SERVER_DRIVEN` | - | 작물 나이 등 |
| `BlockPhysicsEvent` | `SERVER_DRIVEN` | - | 이웃·모양 갱신 |
| `BlockPistonEvent` | `SERVER_DRIVEN` | - | 피스톤 움직임, 밀리는 블록 |
| `LeavesDecayEvent` | `SERVER_DRIVEN` | - | 잎 |
| `EntitySpawnEvent` | `CLIENT_PREDICTED` | 플레이어가 아이템으로 만든 경우(던지기·배·수레·거치대·액자·불꽃·수정·엔더의 눈·활·쇠뇌·삼지창·낚싯대): 손 칸 또는 전체 인벤토리 | 엔티티, 아이템 소모(활·쇠뇌는 뽑은 탄약과 내구도, 쇠뇌는 장전 상태), 낚시찌 연결 |
| `EntityBreedEvent` | `SERVER_DRIVEN` | - | 새끼, 사랑 상태 |
| `EntityDamageEvent` | `SERVER_DRIVEN` | - | 체력, 피격 효과 |
| `EntityExplodeEvent` | `SERVER_DRIVEN` | - | 블록, 불, 폭발 패킷 |
| `EntityTameEvent` | `SERVER_DRIVEN` | - | 길들임 |
| `EntityTargetEvent` | `SERVER_DRIVEN` | - | 공격 대상 |
| `ProjectileHitEvent` | `SERVER_DRIVEN` | - | 적중(계속 난다) |
| `InventoryClickEvent` | `CLIENT_PREDICTED` | 열린 창 전체 + 커서(`CraftItemEvent` 포함) | 모든 칸, 커서 |
| `InventoryOpenEvent` | `SERVER_DRIVEN` | - | 창이 열리지 않는다(클라이언트는 열기 패킷을 기다림) |
| `InventoryMoveItemEvent` | `SERVER_DRIVEN` | - | 깔때기 이동 |
| `FurnaceBurnEvent` | `SERVER_DRIVEN` | - | 연료, 불 |
| `FurnaceSmeltEvent` | `SERVER_DRIVEN` | - | 재료, 결과 |
| `PlayerInteractEvent` | `CLIENT_PREDICTED` | 허공 우클릭: 그 손 칸. 블록 우클릭: 손 칸 + 눌린 블록·면 너머 블록(+ 블록 엔티티). 왼쪽 클릭 블록: 그 블록(+ 블록 엔티티) | 모든 상호작용 |
| `PlayerInteractEntityEvent` | `CLIENT_PREDICTED` | 그 손 칸 | 모든 상호작용 |
| `PlayerDropItemEvent` | `CLIENT_PREDICTED` | 전체 인벤토리 + 커서. 아이템은 든 칸으로 돌아온다 | 아이템 엔티티 없음, 개수 |
| `PlayerItemHeldEvent` | `CLIENT_PREDICTED` | 선택한 단축 칸 | 선택 칸 |
| `PlayerItemConsumeEvent` | `CLIENT_PREDICTED` | 먹던 칸 | 허기, 효과, 아이템 개수 |
| `PlayerToggleFlightEvent` | `CLIENT_PREDICTED` | 능력 패킷 | 비행 상태 |
| `PlayerMoveEvent` | `CLIENT_PREDICTED` | 보정 텔레포트(이전 위치) | 서버 위치 |
| `PlayerToggleSneakEvent` | `CLIENT_INPUT` | 없음(되돌릴 방법이 없다) | 서버의 웅크림 상태 |
| `PlayerToggleSprintEvent` | `CLIENT_INPUT` | 없음(되돌릴 방법이 없다) | 서버의 달리기 상태 |
| `PlayerSwapHandItemsEvent` | `SERVER_DRIVEN` | - | 두 손(클라이언트는 손 바꾸기를 먼저 보이지 않는다) |
| `PlayerPickupItemEvent` | `SERVER_DRIVEN` | - | 바닥 아이템 |
| `PlayerTeleportEvent` | `SERVER_DRIVEN` | - | 위치(위치 패킷도 안 나간다) |
| `PlayerGameModeChangeEvent` | `SERVER_DRIVEN` | - | 게임 모드 |
| `PlayerBedEnterEvent` | `SERVER_DRIVEN` | - | 잠자기, 침대 점유. (리스폰 지점은 바닐라처럼 이벤트 앞 단계에서 이미 정해진다) |
| `PlayerKickEvent` | `SERVER_DRIVEN` | - | 연결 |
| `AsyncChatEvent` | `SERVER_DRIVEN` | - | 채팅 방송(스팸 감시는 그대로 센다) |
| `PlayerCommandPreprocessEvent` | `SERVER_DRIVEN` | - | 명령 실행 |
| `BlockFadeEvent` | `SERVER_DRIVEN` | - | 사라지려던 블록(산호·얼음·불 등) |
| `BlockFormEvent` | `SERVER_DRIVEN` | - | 생기려던 블록(흑요석·눈·번지는 버섯 — `BlockSpreadEvent` 포함) |
| `BrewEvent` | `SERVER_DRIVEN` | - | 병, 재료 |
| `BrewingStandFuelEvent` | `SERVER_DRIVEN` | - | 블레이즈 가루, 연료 |
| `PrepareItemEnchantEvent` | `SERVER_DRIVEN` | - | 제안 세 칸이 모두 비고, 재료는 그대로 |
| `EnchantItemEvent` | `SERVER_DRIVEN` | - | 아이템, 청금석, 경험 레벨, 마법 부여 시드 |
| `EntityMountEvent` | `SERVER_DRIVEN` | - | 타는 상태(탈 수 없다) |
| `EntityDismountEvent` | `SERVER_DRIVEN` | - | 타는 상태(자발적 하차만 취소 가능, 죽음·강제 하차는 취소할 수 없다) |
| `VehicleEnterEvent` | `SERVER_DRIVEN` | - | 타는 상태 |
| `VehicleExitEvent` | `SERVER_DRIVEN` | - | 타는 상태 |
| `EntityPortalEvent` | `SERVER_DRIVEN` | - | 엔티티 위치·차원 |
| `PlayerPortalEvent` | `SERVER_DRIVEN` | - | 플레이어 위치·차원(리스폰 패킷이 나가지 않는다) |
| `PortalCreateEvent` | `SERVER_DRIVEN` | - | 지옥문 틀 안이 비어 있다 |
| `PlayerEditBookEvent` | `SERVER_DRIVEN` | - | 책 내용·서명 |
| `PlayerFishEvent` | `SERVER_DRIVEN` | - | 낚시찌 연결·낚싯대 내구도 |
| `ServerCommandEvent` | `SERVER_DRIVEN` | - | 콘솔 명령 실행 |

## 5. 알려진 한계

- **웅크림·달리기**: 클라이언트 입력이라 서버가 화면을 되돌릴 수 없다. 취소하면 클라이언트는 웅크린 채 보이고 서버는 서 있다고 안다. Paper 도 같다.
- **부싯돌·불 붙이기**: 취소해도 내구도 1이 닳는다(CraftBukkit 동작 그대로). 규칙 1의 유일한 의도적 예외다.
- **블록 엔티티**: 갱신 패킷이 있는 블록 엔티티(표지판·배너·머리·침대·시련 생성기 등)만 다시 보낸다. 나머지(상자 내용 등)는 열 때 따로 보낸다.
- **활·쇠뇌 여러 발**: 한 발이라도 나가면 정상 처리하고, 전부 취소됐을 때만 탄약·장전을 되돌린다.
- 클라이언트 모드가 서버 몰래 상태를 바꾸는 경우(예: 모드가 만든 칸 변화)는 계약 밖이다.

## 6. 시험

- `CancelResyncE2ETest`: 진짜 `ConnectionHandler` + 플러그인으로 클라이언트 패킷을 넣고, 나간 패킷과 서버 상태를 본다. 되돌리기 코드를 빼면 11개가 실패함을 확인했다.
- `CancelContractTest`: 취소 가능 이벤트가 표에 모두 있고, 표의 시험이 실제로 있으며, 이 문서 표와 종류가 같은지 검사한다.
- 서버 주도 이벤트는 기존 `PluginApi*Test`, `FurnaceBlockEntityTest`, `PlayerSleepingTest` 가 맡는다.
- 실제 클라이언트로만 볼 수 있는 화면(고스트 블록·아이템이 정말 사라지는지)은 `docs/user-todo.md` 의 "플러그인 취소 화면" 항목.
