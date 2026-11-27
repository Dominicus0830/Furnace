# 영역 1 블록 — 손 shim 명세 이전 1묶음 (2026-11-24)

- **속성 열거형 19개**(`state/properties` 의 AttachFace·BambooLeaves·BedPart·BellAttachType·ChestType·ComparatorMode·DoorHingeSide·DoubleBlockHalf·DripstoneThickness·Half·NoteBlockInstrument·PistonType·RailShape·RedstoneSide·SculkSensorPhase·SlabType·StairsShape·Tilt·WallSide)를 `block.spec` + 조각 `src/shims/java` 로 옮겼다. 상수는 명세에 `HALF = ("top")` 꼴 생성자 인자(enums.spec 방식)로 적고, 생성자·이름 필드·접근자는 옛 손 shim 그대로 조각에 둔다(바닐라 모양 유지 — PR 612 검토). 옮기기 전·후 클래스를 리플렉션으로 견줘(`toString`·`name`·서수·`getSerializedName`·선언 메서드·인터페이스) 19개 모두 같았다.
- **블록 엔티티 종류 계층**: `tools/compat/gen-nms-block-entities.py` 가 이제 조각 + `block-entity.spec` 을 낸다(머리는 잠금 파일). 옮긴 것 21개(Banner·Beacon·Bed·Beehive·Bell·BrushableBlock·SculkSensor·CalibratedSculkSensor·Campfire·Comparator·Conduit·DaylightDetector·EnchantingTable·EnderChest·TheEndPortal·TheEndGateway·PistonMoving·SculkCatalyst·SculkShrieker·Skull·Spawner·TrialSpawner·Vault 중 생성 방식으로 컴파일되는 것). javap 대조: 달라진 것은 잠금 파일의 바닐라 `implements` 가 붙은 곳(Nameable·MenuProvider·LidBlockEntity·Spawner·StateAccessor·VibrationSystem 등)뿐이다 — 바닐라와 맞춘 차이.
- **PR 601·608 뒤 갱신**: Container 가족·Sign·HangingSign·Lectern 도 조각으로 옮겼다(CommandSource 추상 메서드는 생성기가 던지는 몸통으로 처리). CommandBlockEntity.Mode·StructureBlockEntity.UpdateType 은 명세 `Outer$Name`(상수 전부)로 적고 바깥을 조각으로 뒀다. 직소는 생성기 스레드가 옮겨 이 스크립트는 건드리지 않는다(`SKIP`). 블록 엔티티 이전 완료.
- 서명 관문 기준선의 낡은 줄 117개(블록 엔티티 type-missing 등 — 바닐라 `implements` 가 붙어 같아짐)를 지웠다.
- `NmsBlockEntityClasses` 는 `newBlockEntity` 가 손으로 더해진 뒤로 생성기와 어긋나 있었다 — 생성기가 더는 이 파일을 쓰지 않는다.
- 아직 손으로 남은 블록 영역: Block·Blocks·BlockBehaviour·BlockState·StateHolder·StateDefinition·Property 계열·BlockStateProperties·BlockEntity·BlockEntityType·BlockInWorld·StructureMode·VaultState(상수 몸통)·TrialSpawnerState. 다음 묶음 대상.

## 2묶음 (핵심 클래스)
- Blocks·Block·BlockInWorld·StateDefinition·StateHolder·Property·IntegerProperty·EnumProperty·BooleanProperty·DirectionProperty·BlockStateProperties·BlockEntity·BlockEntityType 을 몸통 그대로 조각으로(`block-core.spec` 은 class 줄만, 멤버 줄은 아직 없다). 머리가 잠금 파일에서 와서 `Blocks`·`BlockEntityType` 이 바닐라처럼 final 이 아니게 됐고 `BlockEntityType<T extends BlockEntity>` 가 됐다(javap 대조에서 달라진 유일한 곳, 기준선 낡은 줄 2개 삭제). 나머지는 `javap -p` 같음.
- TrialSpawnerState 는 속성 열거형과 같은 방식(생성자 인자 명세)으로 옮겼다.
- **못 옮긴 것**: BlockState·BlockBehaviour — `BlockBehaviour$BlockStateBase` 가 Paper 내부 `ca/spottedleaf` 인터페이스를 구현하는 제네릭 클래스라 생성기가 "signature filtering is not supported" 로 멈춘다(생성기 쪽 후속). VaultState(상수 몸통 `{}`)·StructureMode(패킷 생성물)도 그대로.
- 멤버 줄(`@hand`/`@fast` 와 검사 종류)을 핵심 클래스에 적는 일은 값 채우기·연결 단계에서 한다.
