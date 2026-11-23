# 호환 shim: 인벤토리·창(메뉴) 묶음과 감사 도구 Spigot 이름 입력 (2026-11-23)

범위: 여러 플러그인이 같이 부르는 우선순위 구멍(`ServerPlayer.getInventory`, `Inventory`, `AbstractContainerMenu`, `ServerPlayer.containerMenu`, `Container`, `Slot`, `MenuType`, `InventoryMenu`)과, 감사 도구(`nmsShimAudit`)가 Spigot 이름 jar 를 재는 입력(`-PspigotDir`).
이 문서는 바닐라 원문을 옮기지 않았다. 모습(이름·서명)만 서명표와 맞췄고 동작은 Furnace 의 `Player.menuLock()`·창 틀(`org.domi.furnace.server.menu`)에 연결했다.

## 1. 감사 도구 `-PspigotDir=<폴더>`

- 폴더에 있어야 하는 파일: `server.txt`(바닐라 서버 이름표, 필수), `bukkit-1.21.1-cl.csrg`(또는 `bukkit-cl.csrg`, 클래스 이름 대응), 멤버 대응 `bukkit-1.21.1-members.csrg`(선택, 없으면 멤버는 Mojang 이름 그대로 본다).
- 멤버는 Mojang 이름이고 클래스만 Spigot 이름인 jar(예: 실제로 흔한 꼴)를 재려면 `bukkit-1.21.1-cl.csrg` 만 있으면 된다. `package.srg` 는 1.21.1 에 없다.
- 대응표 파일은 Spigot 저작물이라 **저장소에 커밋하지 않는다.** 경로만 입력한다(공유 폴더 `/mnt/project-files/furnace/spigot-map/` 에 있다).
- 예: `./gradlew -PskipOracle=true :furnace-compat-paper:nmsShimAudit -PspigotDir=/경로/sp`
- 코드: `NmsShimAudit.loadSpigot`(없으면 null → Spigot 이름 jar 는 예전처럼 "건너뜀"), `furnace-compat-paper/build.gradle.kts` 의 `furnace.audit.spigotDir` 시스템 속성.

## 2. 인벤토리·창 shim 설계

### 2.1 연결(다리)
- `ChunkInternals`(furnace-api)에 기본이 던지는 메서드를 더했다: `setWindowSlotNow`, `addToInventoryNow`(남은 것 반환), `menuTokenNow`, `menuLookNow`(창 보기 `MenuLook`), `menuSlotNow`, `replaceMenuSlotNow`(비교 후 바꾸기). 구현은 `ChunkInternalsImpl`, 모두 `Player.menuLock()`(= 플레이어 인벤토리 모니터)으로 감싼다.
- 창 칸 번호(0 결과, 1~4 제작, 5~8 갑옷 머리~발, 9~35 본칸, 36~44 단축, 45 보조손)와 바닐라 `Inventory` 번호(0~8 단축, 9~35 본칸, 36~39 발~머리, 40 보조손)는 `Inventory` shim 이 서로 바꿔 준다.
- `net.minecraft.world.entity.player.Inventory`: 접속 중 플레이어는 라이브(읽기·쓰기가 다리를 거쳐 서버 인벤토리를 바로 건드림), 접속 안 한 가짜 플레이어는 자체 저장소. `items`·`armor`·`offhand` 는 바닐라처럼 `NonNullList` 로 보이되 라이브 보기. `selected` 는 공개 필드라 가로챌 수 없어 `getInventory()`·메서드 호출 때 맞춘다.
- `AbstractContainerMenu`·`InventoryMenu`·`Slot`·`MenuType`·`Container`·`Nameable`: 모습은 서명표와 같다. `ServerPlayer.containerMenu`·`inventoryMenu` 는 공개 필드라 가로챌 수 없어 `CraftPlayer.getHandle()` 때 `furnaceRefreshMenus()` 가 창 신분표(`menuTokenNow`)를 비교해 새로 만든다.

### 2.2 스레드 규칙
- 플레이어 칸(본칸·단축·갑옷·보조손)은 라이브이고 쓸 수 있다(비교 후 바꾸기, 실패하면 다음 읽기에서 최신값).
- 상자 같은 **다른 구역이 가진 컨테이너의 칸**은 마지막으로 보낸 사본(`remoteSlot`)을 읽고, 쓰면 `UnsupportedNmsCall`(Paper/Folia 에서 다른 구역 컨테이너를 만지는 것과 같은 방향의 거절).
- 닫힌 창의 칸을 뒤늦게 읽으면 `UnsupportedNmsCall`.

### 2.3 알려진 차이(바닐라/Paper 와 다름)
| 차이 | 이유 |
|---|---|
| 상자 등 다른 구역 컨테이너 칸은 읽기 전용·마지막 동기 사본 | 구역 스레드 소유권. 쓰려면 그 구역으로 넘겨야 함 |
| 커서 아이템(`getCarried`)은 사본, `setCarried` 는 던짐 | 서버는 커서를 창 틀이 관리 |
| `Slot.x/y` 는 0 | 화면 좌표는 서버가 모름 |
| `InventoryMenu` 의 상위가 `RecipeBookMenu` 가 아님 | 제작 책 틀을 안 만듦. 제작칸 컨테이너(`getCraftSlots`)도 없음 |
| `Player.inventoryMenu` 가 final 이 아님 | 새로 고칠 때 바꿔야 함 |
| `Inventory.selected` 는 메서드 호출 때 맞춰짐 | 공개 필드 |
| `Container` 에 CraftBukkit 풍 `getContents()` 가 있음 | Bukkit 쪽에서 흔히 쓰임 |

### 2.4 일부러 안 한 것(링커 함정이 그대로: 부르면 `UnsupportedNmsLink`)
`ServerPlayer.initMenu`·`nextContainerCounter`, `AbstractContainerMenu.broadcastChanges/clicked/removed/sendAllDataToRemote`, `InventoryMenu` 의 제작칸·결과칸, `MenuType` 상수 전부(상수 일부만 있음), `Inventory.placeItemBackInInventory`·`removeItem(ItemStack)`(동일성 기준), `Container` 의 `onOpen/onClose/getViewers/getOwner`. 이유: 서버 창 틀과 이벤트 순서를 건드려야 하는 쓰기 경로라 지금 묶음의 읽기·칸 쓰기 범위를 넘는다.

## 3. 시험
- `NmsInventoryMenuTest`(5개): 칸 번호·라이브 보기, 빼기·넣기·`contains` 규칙, `selected` 동기, 열린 창 보기(generic_9x3, 칸 읽기, 다른 스레드 읽기, 닫기), 접속 안 한 플레이어 인벤토리. `NmsEquipmentReadTest`(8개 묶음 전체 통과).
- 서명 관문(`signatureGate`)은 이 클라우드에 Paper 서버 서명표가 없어 못 돌렸다. 대신 바닐라 서명표(`sig.tsv`)와 `javap` 를 대조해 서술자 차이 0을 확인했고, 남는 건 위 표의 상위 클래스 차이와 shim 이 더한 멤버(info 수준)뿐이다. **Paper 표가 있는 PC 에서 `signatureGate` 를 한 번 돌려 기준선에 의도한 차이를 적어야 한다**(user-todo 기록 참고).

## 4. 측정(표본 jar 100개, Spigot 이름 jar 포함: `-PspigotDir`)
| 시점 | 지원 | 전체 | 비율 |
|---|--:|--:|--:|
| 이 묶음 전 | 4070 | 5140 | 79.2% |
| 이 묶음 후 | 4148 | 5254 | 78.9% (인기 가중 79.6%) |

분모가 늘어난 것은 이 묶음에서 새로 보이게 된 클래스(전에는 "클래스 없음" 한 줄)가 부르는 멤버가 각각 분모에 들어왔기 때문이다. 지원 심볼은 +78. 코디네이터가 데스크탑에서 잰 76.8%(vexbot 26.3 jar 포함)와 숫자가 다른 것은 표본 jar 구성이 달라서다(이 값은 클라우드 표본 폴더 기준, 같은 폴더·같은 도구로 전후를 비교).

다음 묶음 순위(상위, 전체 표는 공유 폴더 `furnace/shim-ranking/ranking-2026-11-23-containers.md`):
1. `MobEffectInstance`(3개 플러그인) 2. `ArmorStand`·`ItemFrame`(엔티티 사슬 스레드 몫, PR 553 뒤) 3. `ResolvableProfile` 4. `AttributeMap`
5. FAWE·WorldEdit 공통: `Util`, `BlockableEventLoop`, `WorldData`·`MinecraftServer.getWorldData`, `LevelChunk.setBiome`
6. `ServerPlayer.level()` 서명, `ServerGamePacketListenerImpl.teleport(Location)`, `Biome.climateSettings`·`getWaterColor`
7. 동물 클래스(`Bee`·`Cat`·`Fox`·`Panda`·`Parrot`·`Pig`·`Rabbit`·`Sheep`·`Wolf`) — 엔티티 사슬 뒤.

## 5. 금 파일 갱신
`scene-compat-asm-prefilter`(3줄: InventoryFramework 0.12.2 의 기본·버전 접미사 두 판 변환 해시와 TOTAL). 이 jar 가 `Inventory`·창 클래스를 쓰는데 그 shim 이 생겨 변환이 "없는 클래스"에서 "있는 클래스의 없는 멤버/있는 멤버"로 바뀌었다. 장면 입력(shim 표면)이 바뀐 경우(identitykit 갱신 2)이고 클래스 수·판정 수(`classes=1042 prefilter-hit=936 changed=760`)는 그대로다. 독립 검토에서 이 3줄을 확인한다.
