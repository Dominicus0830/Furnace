# 구조물 템플릿 데이터 픽서 (Q8) — 2026-11-07

옛 판(DataVersion)으로 저장된 구조물 `.nbt` 를 1.21.1(3955) 꼴로 올린다. 바닐라 `StructureTemplateManager.readStructure` 는 모든 source(월드 `generated/`, 데이터 팩, 내장)에서 `DataFixTypes.STRUCTURE` 로 전체 데이터 픽서를 돌린다. Furnace 는 그중 **구조물 템플릿 안에 실제로 나오는 자리**(팔레트의 블록 상태, `blocks[].nbt` 블록 엔티티, `entities[].nbt` 엔티티와 그 안의 아이템·탑승자·스포너 자료)에 닿는 고침만 바닐라 1.21.1 `DataFixers.addFixers` 순서대로 옮겼다(`furnace-server/.../datafix/`).

- 코드: `StructureDataFixer`(입구·순회), `FixTable`(단계 표), `BlockStateFixes`·`BlockEntityFixes`·`EntityFixes`·`ItemStackFixes`·`MobEffectFixes`·`AttributeIds`.
- 기대값 출처: 1.21.1 서버 jar 의 DataFix 클래스를 디컴파일해 하나씩 따라갔다(바닐라 DFU 라이브러리는 컨테이너에서 받을 수 없어 실행 대조는 못 했다). 시험 `StructureDataFixerTest`(16), `TemplateManagerDataPackTest`(데이터 팩·generated 경로).
- 내장 템플릿 1180개는 모두 DataVersion 3955 라 픽서가 아무것도 바꾸지 않는다(확인함).

## 지원 범위

| DataVersion | 처리 |
|---|---|
| 3955 | 그대로(고칠 것 없음) |
| 1452 ~ 3954 (1.13 평탄화 직후 ~ 1.21) | 아래 "옮긴 고침" 표를 판 순서대로 적용 후 3955 로 |
| 1451 이하, DataVersion 없음 | **경고 후 그대로 읽기**. 1.12 이하의 평탄화(`BlockStateData` 숫자·옛 이름 표 약 1700줄, 1451 하위 버전 고침 7개)는 옮기지 않았다. 바닐라는 DataVersion 이 없으면 500 으로 보고 평탄화까지 돌린다 — 이 점이 다르다 |
| 3956 이상(이 서버보다 새 판) | **경고 후 그대로 읽기**(바닐라도 새 판은 고치지 않는다 — 경고만 Furnace 가 더 남긴다) |

경고는 구조물마다 한 번(저장소가 결과를 캐시한다): `Structure <id> (<source>) has DataVersion <v> older than 1452; no data fixer for it, read as is` / `... newer than this server (3955); read as is`.

## 옮긴 고침

| 판 | 바닐라 고침 | 대상 |
|---|---|---|
| 1456 | EntityItemFrameDirectionFix | 액자 Facing |
| 1458 | Entity/Item/BlockEntityCustomNameToComponentFix | CustomName·display.Name 문자열 → JSON 글(명령 블록·명령 블록 수레 제외) |
| 1460 | EntityPaintingMotiveFix | 그림 Motive 소문자·이름공간 |
| 1474 | ColorlessShulkerEntityFix, 보라 셜커 상자 이름 | 셜커 Color 10→16, 블록·아이템 |
| 1475~1515 | 이름 바꾸기(흐르는 물·산호·복어·해초·대구/연어·프리즈머린·켈프·수박·나무껍질·스포너·산호 부채) + 1488 명령 블록 이름 | 블록·아이템·엔티티 id |
| 1494 | ItemStackEnchantmentNamesFix | ench 숫자 → Enchantments 이름 |
| 1510 | EntityTheRenameningFix(+ `bred_` 접두) | 엔티티 id |
| 1800·1802 | 염료·표지판·돌 반 블록 이름 | 블록·아이템 |
| 1803 | ItemLoreFix | Lore 문자열 → JSON |
| 1904·1917 | EntityCatSplitFix, CatTypeFix | 오실롯 → 고양이(바닐라처럼 CatType 0 은 그대로) |
| 1914 | WeaponSmithChestLootTableFix | 상자 전리품 표 |
| 1928·2509 | 파괴수·좀비 피글린 이름 | 엔티티 id·스폰 알 |
| 1948·1953 | OminousBannerRenameFix(아이템·블록 엔티티) | illager_banner → ominous_banner |
| 2209·2508·2528·2680·2690·2691·2696·2700·2717·3692 | 벌집·균류·영혼 횃불/랜턴·흙길·구리 두 번·심층암·동굴 덩굴·진달래·풀→짧은 풀 | 블록·아이템 |
| 2503 | WallPropertyFix | 담장 true/false → low/none |
| 2514 | UUID 고침 중 **능력치 수정자 UUID·머리 주인 Id·전달체 target_uuid** | 엔티티·아이템·블록 엔티티 |
| 2518 | JigsawPropertiesFix, JigsawRotationFix | 직소 블록 엔티티 이름·속성 facing → orientation |
| 2523·3814 | AttributesRenameFix | 능력치 이름(아이템·엔티티) |
| 2531 | RedstoneWireConnectionsFix | 레드스톤 가루 side |
| 2679 | CauldronRenameFix | 가마솥 level → water_cauldron |
| 2831 | UntaggedSpawnerFix | 스포너(블록·수레) SpawnData/SpawnPotentials |
| 3086·3087·3097 | 고양이·개구리 variant, british_shorthair | 엔티티 |
| 3090 | EntityPaintingFieldsRenameFix | Motive·Facing → variant·facing |
| 3097 | FilteredSignsFix, FilteredBooksFix | 걸러진 글 지우기 |
| 3438·3447 | 수상한 모래 → brushable_block(id·필드), 도자기 조각 이름 두 번 | 블록 엔티티·아이템 |
| 3439·3564 | BlockEntitySignDoubleSidedEditableTextFix, DropInvalidSignDataFix | 표지판·매다는 표지판 앞뒤 글 |
| 3448 | DecoratedPotFieldRenameFix | shards → sherds |
| 3568 | MobEffectIdFix | 신호기 효과, 엔티티 ActiveEffects·화살·효과 구름·버섯소 스튜, 물약·스튜 아이템 |
| 3800·3803 | 거북 인갑, sweeping → sweeping_edge | 아이템 |
| 3813 | BlockPosFormatAndRenamesFix | 벌·엔드 수정·떠돌이 상인·습격자·끈의 좌표, 벌집 꽃·엔드 관문 출구, 나침반 |
| 3818 | BeehiveFieldRenameFix | 벌집 bees |
| 3818_1 | BannerPatternFormatFix | 깃발 무늬 이름·색 |
| 3818_5 | **ItemStackComponentizationFix (전부)** | Count→count, tag → 컴포넌트(내구도·이름·설명·마법 부여·숨김 깃발·잠금·전리품·담긴 아이템·깃발·벌·물약·책·지도·불꽃놀이·머리·양동이 몹·나침반·모험 모드 검사·능력치…), 남은 tag 는 custom_data |
| (훅) | V705 아이템 훅 | tag 시대 아이템의 BlockEntityTag·EntityTag 에 id 넣기(위 컴포넌트화가 담긴 아이템을 찾는 데 필요) |
| 3820 | PlayerHeadBlockProfileFix | 머리 블록 SkullOwner → profile |
| 3825 | ItemStackCustomNameToOverrideComponentFix, BannerEntityCustomNameToOverrideComponentFix | 불길한 깃발·탐험 지도 이름 → item_name |
| 3833 | RemoveEmptyItemInBrushableBlockFix | 빈 아이템 지우기 |
| 3945 | AttributeIdFix, JukeboxTicksSinceSongStartedFix | 능력치 수정자 id, 주크박스 |

그 밖에 바닐라 STRUCTURE 스키마로 한 번 읽고 쓰면 엔티티·블록 엔티티·아이템 `id` 에 이름공간이 붙는다 — 같게 붙인다(최신 판 파일은 건드리지 않음).

## 옮기지 않은 것(구조물에 나올 수는 있지만 드묾)

| 판 | 고침 | 결과 |
|---|---|---|
| ≤1451 | 평탄화 전체 | 위 "지원 범위" — 경고 후 그대로 |
| 2514 | EntityUUIDFix 의 나머지(OwnerUUID·Leash UUID·Angry·Target 등) | 1.16 이전 판의 길들인 동물 주인 등이 옛 문자열로 남는다. 배치 때 엔티티 자신의 UUID 는 바닐라도 새로 정한다 |
| 1918·1955·2516·1963·3828·2505 | 주민 직업 자료·레벨·소문·두뇌 기억·빈 거래 | 1.14 이전 주민이 기본 직업으로 |
| 2511·2702·3685·3938·3818_2 | 투사체 주인·줍기·저장 아이템·무기·물약 화살 | 날아가던 화살 자료 |
| 2529·2533·2535·3093·3683·3808·3809·3812·3818_6·3322·3818_4 | 스트라이더 중력·주민 추적 거리·셜커 회전·염소 뿔 상태·TNT 블록 상태·말 갑옷 자리·짐 말 칸 번호·늑대 체력·효과 구름 물약·효과 지속 계산 자료·입자 | 해당 엔티티 필드가 옛 꼴 |
| 3094·3820 | 염소 뿔 id, 자석석 나침반 스냅숏 컴포넌트 | 아이템 |
| 3825 | TrialSpawnerConfigFix | 1.20.3~4 실험 시련 스포너 |
| — | 팔레트 밖 블록 이름(떨어지는 블록 BlockState·엔더맨 손 블록·수레 DisplayState·아이템 CanDestroy/BlockStateTag 안 이름) | 이름 바꾸기가 그 자리엔 안 닿는다 |
| — | 스키마 훅이 정하는 엔티티별 아이템 자리 중 이름으로 못 잡은 것 | 순회는 `Items`·`HandItems`·`ArmorItems`·`Inventory`·`Item`·`item`·`SaddleItem`·`ArmorItem`·`DecorItem`·`body_armor_item`·`FireworksItem`·`Trident`·`weapon`·`Offers.Recipes[].buy/buyB/sell`·`RecordItem`·`Book`·`Passengers`·스포너·벌을 본다 |

필요해지면 위 표에서 골라 `FixTable` 에 단계 하나를 더하면 된다(같은 판 안에서는 바닐라 addFixer 순서를 지킬 것).
