# 엔티티 영역 shim 구멍 채우기 (2026-11-23)

T-232(엔티티 사슬)·#556(Axiom 액자)·#558(구조물 액자 방향) 뒤를 잇는다. 기록: [compat-shim-entity-chain-t232-2026-11-23.md](compat-shim-entity-chain-t232-2026-11-23.md).

## 1. 다시 잰 구멍 (Spigot 이름 포함)

- 도구: `:furnace-compat-paper:nmsShimAudit` + 데스크탑 재측정 때의 `-PspigotDir` 손질(공유 폴더 `audit-spigotDir.patch`, 커밋 안 함).
- 대응표: Mojang `server.txt`(paperweight 캐시의 server_mappings.txt)와 Spigot 클래스 대응 `bukkit-1.21.1-cl.csrg`(공유 폴더, 커밋 금지).
- 표본: 인기 jar 100개. 아래 숫자는 1.21.1 이 아닌 vexbot(26.3 용)만 부르는 심볼을 뺀 값이다.

| 기준 | 지원/전체 | 비율 |
|---|--:|--:|
| 기준 브랜치 c2bc631c(#558 병합본) | 3428/4067 | 84.3% |
| 이 묶음(1묶음) 뒤 | 3469/4070 | 85.2% |

엔티티 영역(`net/minecraft/world/entity/**` 소유) 구멍은 196 → 163 으로 줄었다. 플러그인별 내역은 다음과 같다.

| 플러그인 | 전 | 뒤 | 성격 |
|---|--:|--:|---|
| FancyNpcs | 119 | 103 | 월드 밖 NPC 엔티티를 만들어 값 설정 뒤 엔티티 데이터 패킷으로 보낸다. 몹별 setter(고양이·여우·판다 …)와 `getEntityData` 가 대부분 |
| LagFixer | 54 | 39 | 몹 AI(목표 선택기·길찾기·번식 목표)와 수레·배 생성자 |
| InvSee++ | 11 | 11 | 플레이어·인벤토리(다른 스레드 영역 — 건드리지 않음). 일부는 1.21.6+ 이름(ValueInput·NameAndId) |
| GSit·AxGraves·ImageFrame·DecentHolograms·WorldEdit | 18 | 14 | 생성자(갑옷 거치대·셜커·영역 효과 구름), AttributeMap, EntityDimensions 등 |

## 2. 1묶음 — 엔티티 공통 설정·종류 상수·장비 칸

- **Entity 공통 설정:** 바닐라 `setPos`(두 겹)·`setRot`·`setYRot`·`setXRot`·`teleportTo(x, y, z)`·`setInvisible`·`setGlowingTag`·`setSharedFlagOnFire`·`setSilent`·`setOnGround`·`setCustomName`·`setCustomNameVisible`·`setTicksFrozen`·`getTicksRequiredToFreeze`(140).
  - 월드의 엔티티는 주인 스레드에서 서버 엔티티의 같은 메서드를 부른다(`ChunkInternals.EntityEdit`, 다른 쓰기와 같은 스레드 규칙).
  - 아직 월드에 없는 엔티티(`EntityType#create`)는 조작을 기록했다가 넣을 때 서버가 차례로 재생한다(`EntityOp`). 같은 기록 하나가 두 길을 다 맡는다(`SetPos`·`SetRot`·`SetFlag`·`SetCustomName`·`SetTicksFrozen`).
  - 월드 밖에서도 게터가 바닐라처럼 바뀐 값을 준다(자리·각도, 공유 플래그 불 0·투명 5·발광 6).
  - 유한하지 않은 각도는 바닐라처럼 기록을 남기고 버린다.
  - 접속한 플레이어는 지금처럼 UnsupportedNmsCall(클라이언트에 알리는 길이 따로 있다 — Bukkit API).
  - 잠금 파일이 바닐라 final 을 묶는다: `setPos(Vec3)`·`setGlowingTag` 는 final, 나머지는 아니다. `NmsLinkerTest` 의 바닐라 final 목록에 둘을 더했다.
- **EntityType 상수 130개:** 종류 표(`kind-classes-1.21.1.txt` E 줄 129개) + `PLAYER`. 형식 인자는 바닐라 상수와 같다(Paper 1.21.1 javap 로 이름 130개 일치 확인). `NmsEntitySettersTest` 가 표·형식 인자·`byString` 과 같은 객체인지 본다.
- **EquipmentSlot:** 패킷 생성기의 빈 열거형을 명세로 옮겼다(Rotation 과 같은 절차: 명세 → `shimLock` → 컴파일). 칸마다 종류·번호·필터 번호·이름은 Paper 1.21.1 정적 초기화(javap -c)와 같고, `byName` 의 오류 문구도 같다. 서명 관문 기준선의 `type-missing EquipmentSlot` 줄을 지웠다.
- **LivingEntity#getOffhandItem:** `getMainHandItem` 과 같은 범위(손 칸).

## 3. 남은 것 (다음 묶음 후보)

- FancyNpcs: `getEntityData`(SynchedEntityData)와 몹별 setter. NPC 를 월드에 넣지 않고 패킷으로만 보내는 길이라 엔티티 데이터 값을 패킷 모양으로 내주는 설계가 먼저다(설계 묶음).
- LagFixer: `GoalSelector`·`Goal`·`PathNavigation`·`TargetingConditions`·번식 목표. 몹 AI 상속·조작이라 가짜 플레이어 다음 설계(결정 확정 "몹 상속은 가짜 플레이어 다음 설계만")를 따른다.
- 생성자 `(EntityType, Level)`·`(Level, x, y, z)`(수레·배·셜커·갑옷 거치대·영역 효과 구름): 생성기 전용 생성자 말고 바닐라 생성자로 월드 밖 엔티티를 만드는 길.
- 필드 직접 읽기·쓰기(`xo`·`yo`·`zo`·`yRotO`, LivingEntity `hurtTime` 등)는 shim 이 값을 들고 있지 않아 못 맞춘다(호출 가로채기가 안 된다).
- `getEyeHeight`: 월드 밖 엔티티의 종류별 눈높이 표가 호환 계층에 없다.
