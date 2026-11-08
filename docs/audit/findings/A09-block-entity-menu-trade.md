# A09 블록 엔티티·메뉴·거래 — 2차 전체 점검 조사 보고서 (2026-11-08)

- 기준: 브랜치 `claude/quirky-wright-lra834`, 커밋 `3942d11d`(조사는 `eab009d9` 에서 시작, 이후 문서 변경뿐). 코드는 고치지 않았다.
- 바닐라 근거: 1.21.1 서버 jar 를 디컴파일한 난독화 소스(Vineflower)와 이름 대응 스크립트로 맞춰 읽었다. 거래 표는 등록 코드에서 직접 뽑아 비교했다.
- 재현 시험: `A09-repro/*.java.txt` 3개(시험 11개 중 **8개 실패**: 차이 재현, 3개는 같음 확인용 대조군이라 통과). 쓰는 법: 확장자 `.txt` 를 떼고 `A09BeaconBeamReproTest`·`A09BeaconBeamBehaviorReproTest` 는 `furnace-server/src/test/java/org/domi/furnace/server/audit/`, `A09EnchantReproTest` 는 `.../server/menu/` 에 넣는다. A09-04 는 고양이 엔티티를 놓는 시험 틀이 없어 코드 대조만 했다.

## 요약

| 심각도 | 수 | 항목 |
|---|---|---|
| 치명 | 0 | |
| 높음 | 0 | |
| 중간 | 1 | A09-01 |
| 낮음 | 2 | A09-02, A09-04 |
| 참고 | 4 | A09-03, A09-05, A09-06, A09-07 |

범위의 대부분은 바닐라와 같은 충실한 포팅이다. 차이는 포팅한 사람이 `TODO` 로 근사해 둔 자리에 몰려 있다. 가장 큰 것은 A09-01(신호기 빛줄기를 막는 블록 151종이 바닐라와 다름)이다.

## 조사한 범위

- 블록 엔티티: 화로 계열(`FurnaceBlockEntity`·`FuelRegistry`), 양조기, 호퍼, 신호기, 금고(`Vault*`), 제작기(`CrafterBlockEntity`), 모닥불, 상자 열림 집계(`OpenersBlockEntity`), 셜커 상자 `canOpen`, `BlockEntityContainer`.
- 메뉴: 화로, 모루(`AnvilMenu`·`ItemCombinerMenu`), 숫돌, 베틀, 석재절단기, 작업대, 인벤토리 제작칸, 마법 부여대, 상인(`MerchantMenu`).
- 거래: `MerchantOffer`, `VillagerTrades` 의 직업별 거래 표 전부, `Villager` 의 재입고·특별 가격·`notifyTrade`.
- `ConnectionHandler` 의 `BlockAccess` 구현(모루 사용·마법 부여 힘·경험치 지급)과 상자 열기 경로(4769~4791행).

## 조사하지 못한 범위 (이유)

- 선반/조각 책장·렉터른·종·주크박스·벌집·스포너·시련 스포너: 시간 안에 줄 단위 대조까지 가지 못했다. 코드 안 `TODO` 와 모닥불만 읽었다(모닥불은 같음).
- 스미딩 틀·지도 제작대·신호기 메뉴: 구조(칸 번호·`quickMoveStack`)만 훑었고 줄 단위 대조는 못 했다.
- 복제 경로는 A04, 플러그인 메뉴 API 는 A14, 레드스톤은 A07 소관이라 제외했다. PR #85(M3)·#169(칸)·#124(M18)에서 이미 검토된 항목은 다시 올리지 않았다.
- 바닐라 서버를 실제로 돌린 대조는 하지 못했다(Mojang 호스트 접근 불가). 디컴파일 소스 읽기와 Furnace 시험으로 대신했다.

## 같음 확인 (다시 볼 필요 없는 것)

- 화로: 연료 표, 요리 시간·경험치 누적(`recipesUsed`), 꺼짐 시 진행 되감기(×2), 켜짐 상태 갱신.
- 모루: 비용 계산(수리·합치기·이름 바꾸기·인챈트 병합·`repairItemCountCost`), 최대 비용 40 과 "너무 비쌈", 사용 후 깨질 확률 0.12 와 상태 변화, 이름 변경 빈 문자열 처리.
- 숫돌, 석재절단기, 작업대(`CraftingMenu`), 인벤토리 제작칸, 베틀 칸 규칙·무늬 선택·6겹 제한은 같다.
- 상인 메뉴·`MerchantOffer`: 가격 계산(수요·평판·`specialPriceDiff`·최소 1), 사용 횟수, 경험치 지급, 재입고(하루 최대 2회, 재입고 시 수요 갱신).
- 거래 표 전부(직업·등급별 후보, 선택 수, 가격, 경험치, 사용 한도)가 바닐라 등록 코드와 같다.
- 양조기(재료 표, 연료 20칸, 진행 400틱), 호퍼(빨아들이기·내보내기 순서·쿨다운 8틱·이동 시 칸 규칙), 금고(상태기계·쿨다운·플레이어별 보상 기록), 제작기(비활성 칸·제작 쿨다운 6틱)도 같다.
- 모닥불(`placeFood`·`cookTick`·`cooldownTick`·저장 필드)과 `BlockEntityContainer` 의 칸 접근도 같다.

## 항목

| ID | 심각도 | 종류 | 근거 | 재현 | 제안 |
|---|---|---|---|---|---|
| A09-01 | 중간 | 바닐라 차이 | `BeaconBlockEntity` 약 86행: 빛줄기 차단 판정이 `B.lightBlockedAbove(state)`(위로 빛을 완전히 막는지) 근사다(`TODO(빛)`). 바닐라는 `getLightBlock(state) >= 15` 이고 기반암만 예외다. 모든 블록 상태로 대조하니 **151종**이 다르다. 슬랩·계단·카펫(이끼 카펫 포함)·눈 한 겹·밭·흙길·마법 부여대·엔드 차원문 틀·피스톤 3종(머리 포함)·리피터·비교기·햇빛 감지기·렉터른·석재절단기·퇴비통·스컬크 감지기·교정 감지기·스컬크 비명 감지기은 바닐라에서 빛줄기를 막지 않는데 Furnace 에서는 막는다(150종). 반대로 **틴티드 유리**는 바닐라에서 `lightBlock` 15 라 빛줄기를 끊는데 Furnace 는 통과시킨다(1종). 신호기 위에 슬랩·카펫 등을 올리면 효과가 사라지는 것은 사용자가 바로 알아챈다. | `A09BeaconBeamReproTest`(데이터 대조, 실패), `A09BeaconBeamBehaviorReproTest`(신호기 위에 슬랩·계단·카펫·눈·틴티드 유리를 놓고 속도 효과 확인: 슬랩·계단·카펫·눈·틴티드 유리 5개 실패, 열린 하늘·돌 2개 통과) | `LightTables.lightBlock(state) >= 15 && !bedrock` 으로 바꾼다(`LightTables` 가 이미 `blocks/blocklight_1.21.1.txt` 에서 같은 값을 읽는다). `B.lightBlockedAbove` 는 다른 쓰임이 없으면 지운다. |
| A09-02 | 낮음 | 바닐라 차이 | `EnchantmentMenu.clickMenuButton`: 청금석을 `if (!player.isCreative() \|\| !lapis.isEmpty())` 로 뺀다. 바닐라는 `lapis.consume(cost, player)` 로 창작 모드(`hasInfiniteMaterials`)면 줄이지 않는다. 창작 모드 플레이어가 청금석을 올려 두고 마법을 부여하면 Furnace 는 청금석이 줄어든다. 첫 `isCreative()` 검사로 청금석 없이 하는 것은 맞게 허용한다. | `A09EnchantReproTest.creativePlayerKeepsLapis`(실패: 10 기대, 9), 생존 대조군 `survivalConsumesLapis`(통과) | 청금석 감소를 `!player.isCreative()` 일 때만 한다(창작이면 `enchantSlots.set(1, …)` 를 건너뜀). |
| A09-03 | 참고 | 바닐라 차이 | `EnchantmentMenu.getEnchantmentList`: `random.setSeed((long) enchantmentSeed.get() + slot)` 은 long 덧셈이다. 바닐라는 `(long)(seed + slot)` 으로 **int 덧셈 후** 변환하므로 `Integer.MAX_VALUE` 근처 시드에서 부호가 갈린다. 시드는 무작위 int 라 실제로 걸릴 확률은 약 2^-30 이다. | `A09EnchantReproTest.enchantListSeedWrapsLikeIntAddition`(경계 시드로 실패, 16쌍 중 12쌍 차이) | 괄호를 옮겨 `(long) (enchantmentSeed.get() + slot)` 로 쓴다. |
| A09-04 | 낮음 | 바닐라 차이 | `ChestBlock.isChestBlockedAt` 주석은 "위에 앉은 고양이가 있으면 막힘"이라 쓰고 `TODO(엔티티)` 를 달았지만 레드스톤 전도체 검사(`isBlockedChestByBlock`)만 한다. 바닐라 `isChestBlockedAt` 은 `isBlockedChestByBlock \|\| isCatSittingOnChest`(상자 위 칸 AABB 안에 앉은 자세인 `Cat` 이 있는지)다. `Cat` 엔티티와 앉은 자세(`setInSittingPose`)는 이미 있다. 영향: 길들인 고양이를 앉힌 상자를 Furnace 는 열 수 있고(바닐라는 못 엶), 에터 상자(`EnderChestBlock` 29행), 더블 상자 짝 검사(`ConnectionHandler` 4788~4791행), 비교기 출력(`getAnalogOutputSignal`)이 모두 이 함수를 쓴다. 고양이로 상자를 잠그는 장치가 안 먹는다. | 없음(고양이를 놓는 시험 틀이 없어 코드 대조) | `BlockLevel` 에 "AABB 안의 앉은 고양이 유무" 조회를 더하고 `isChestBlockedAt` 이 `isBlockedChestByBlock \|\| isCatSittingOnChest` 를 보게 한다(검사 상자는 `(x, y+1, z)~(x+1, y+2, z+1)`). region 소유 규칙상 같은 region 밖 엔티티는 경계 처리가 필요하다. |
| A09-05 | 참고 | 바닐라 차이(알려진 가정) | `ShulkerBoxBlock.canOpen` 은 뚜껑 앞 충돌을 **블록만** 검사한다(주석에 가정으로 적혀 있음). 바닐라 `Level.noCollision` 은 충돌 가능한 엔티티(보트·셜커 등)와 월드 보더도 본다. 그래서 뚜껑 앞을 보트·셜커가 막고 있어도 Furnace 는 열린다. 또 `ShulkerBoxBlockEntity` 머리 주석의 `TODO(블록): canOpen` 은 이미 구현돼 낡은 주석이다. | 없음 | `ChunkLevel.noCollision` 에 엔티티 충돌을 더할 때 함께 맞춘다. 낡은 `TODO` 는 지운다. |
| A09-06 | 참고 | 바닐라 차이(알려진 근사) | `OpenersBlockEntity` 42행: `container_open` 게임 이벤트의 source 가 `null`(연 플레이어가 아님, `TODO(엔티티)`). 이벤트 원인이 필요한 곳(플러그인 원인 사슬 등)에서 연 플레이어를 알 수 없다. 관측 가능한 바닐라 차이는 확인하지 못했다. 닫을 때(55행)도 `null` 이다. | 없음 | 열린 플레이어를 `BlockActor` 로 넘기는 경로가 생기면 `openers` 가 마지막으로 연 쪽을 source 로 쓴다. |
| A09-07 | 참고 | 바닐라 차이(영향 없음) | `StonecutterMenu.quickMoveStack` 결과 칸: 바닐라는 `stack.getItem().onCraftedBy(...)` 를 부르지만 `TODO(아이템 제작 훅)` 만 있다. 석재절단기 결과물에는 이 훅을 쓰는 아이템(지도 등)이 없어 관측 차이는 없다. `item_crafted` 통계는 `Slot.onTake` 쪽에서 센다(작업대와 같은 경로). | 없음 | 훅 호출을 넣거나, 영향 없음을 주석으로 적고 `TODO` 를 닫는다. |

## 메모

- 위 `TODO` 가 달린 근사 6곳은 모두 "엔티티·빛 같은 다른 계층이 아직 없을 때" 쓴 임시 자리다. 이후 계층이 생겼는데 `TODO` 가 남은 경우가 A09-01(빛)과 A09-04(고양이)이므로 다른 영역에도 같은 모양의 낡은 `TODO` 가 있는지 `grep -rn "TODO(" ` 로 훑어 볼 만하다.
- 결정이 필요한 항목은 없다. 도미니코 확인 문서(`docs/user-todo/entries/`)는 만들지 않았다.
