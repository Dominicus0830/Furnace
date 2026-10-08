# Furnace vs 바닐라/Paper — 영역별 차이 및 미구현 목록 (HEAD d490984, 2026-10-07)

기준: "클라이언트·운영자·플러그인·디스크에서 관찰되는 동작은 바닐라/Paper/Spigot과 같고, 내부 구조만 최적화로 다르다."

## 0. 읽는 법과 신뢰도

- 5개 영역(네트워크 / 플레이어·게임플레이 / 월드·청크·저장 / 월드 생성 / 서버 인프라)을 각각 코드를 읽고 점검한 결과를 합친 것이다.
- 바닐라 동작은 점검자의 지식 기준이다. 바닐라 jar와 실제 클라이언트로 대조하지 않았다. 불확실한 항목은 "(확인 필요)"로 표시했다.
- 표기: **[OK]** 동일, **[DIFF]** 있으나 다름, **[MISSING]** 없음. 관찰 범위: **C** 클라이언트, **OP** 운영자, **PL** 플러그인 작성자, **D** 디스크, **I** 내부.
- 심각도: **H** 데이터 손실·크래시·행·핵심 기능 부재 / **M** 눈에 띄는 차이 / **L** 사소.
- "구현 안 됨"은 로드맵상 예정(플러그인 API는 M6)인 경우가 많다. 아래 1절은 미구현이 아니라 **지금 있는 코드의 결함**이다.

## 1. 기능 부재가 아닌 실제 결함 (우선 수정 대상)

| # | 결함 | 근거 | 영향 |
|---|---|---|---|
| 1 | 시작 실패 시 좀비 프로세스: 포트 점유로 `BindException`이 나도 비데몬 스레드(RegionThread, GlobalThread)와 Netty 이벤트 루프 때문에 JVM이 안 죽음. 커밋 1754307의 "바인드 실패 시 루프 종료" 수정은 `catch (RuntimeException)`이 체크 예외 `BindException`을 못 잡아 동작하지 않음(재현됨). ioPool/genPool은 지연 생성이라 원인이 아님 | `FurnaceMain.java:29,67`, `NetworkServer.java:65-72`, `RegionThread.java:36-37` | systemd `Restart=on-failure`가 실패를 못 봄. 리스너 없이 틱만 돎 |
| 2 | 종료 훅 `f.get()`에 타임아웃 없음 | `FurnaceMain.java:78` | region 스레드가 죽거나 멈추면 종료가 영원히 안 끝남 |
| 3 | 청크 로드 실패가 `loading`에 영구 잔류 (성공 때만 remove) | `Region.java:78-85` | IO 오류·손상 청크·생성기 예외 한 번에 그 청크가 재시작 전까지 로드 불가, region도 reap 안 됨 |
| 4 | 바닐라 청크의 컨테이너 블록 엔티티(상자·화로·통·호퍼 등) 로드 시 NPE (모든 block_entities를 chest로 취급, `Items[].Id`를 int로 읽음) | `AnvilChunkCodec.java:176-200`, `NbtCompound.java:25` | 3번과 결합해 그 청크가 영구 로드 불가 |
| 5 | 바닐라/Paper `playerdata/<uuid>.dat` 로드 시 ClassCast/NPE (Slot이 byte, Id가 문자열) | `PlayerData.java:95-98`, `ConnectionHandler.java:215` | 해당 플레이어는 접속할 때마다 연결이 끊김 |
| 6 | 손상된 playerdata가 `UncheckedIOException`으로 Netty 스레드에서 터져 그 플레이어가 잠김 (바닐라는 경고 후 새로 시작) | `PlayerData.java:68` | 접속 불가 |
| 7 | 종료 시 `Region.savePlayers`가 `fp.x()`(drain 전엔 0,0,0)와 디스크의 옛 슬롯으로 저장하고, `channelInactive` 저장과 같은 파일을 동시에 씀 | `Region.java:240-246` | 인벤토리 롤백·위치 손실·파일 손상 가능 |
| 8 | 플레이어는 접속 해제·종료 때만 저장됨. 자동저장은 청크만. 비원자적 쓰기 | `Region.java:306-309`, `PlayerData.java:47` | 크래시 시 접속 이후 전부 손실 |
| 9 | 저장된 인벤토리를 서버 `slots[]`에 로드하고 클라이언트에는 안 보냄 (container 0 내용, Set Held Item 미전송) | `ConnectionHandler.java:208-261` | 재접속 후 클라 인벤토리는 비어 보이는데 서버는 아이템을 가진 상태. 빈 손 우클릭으로 안 보이는 블록이 설치됨 |
| 10 | 청크 evict 직후 비동기 저장이 끝나기 전에 재구독되면 stale 데이터를 읽거나 재생성해 편집이 사라짐 (바닐라는 pending-unload 맵으로 방지) | `Region.java:217-224`, `World.java:129-142` | 편집 손실 경합 |
| 11 | 로그인 순서 검증 없음: 로그인 시작 없이 0x03을 보내면 `playerUuid==null`로 `onEnterPlay` 진입. NPE는 입장 시점이 아니라 다른 플레이어의 Netty 스레드(`Player.send` 람다의 `Types.writeUuid`)에서 나며 `ByteBuf`가 샌다. 고아 Player가 일괄 탭 패킷에 들어가 새로 접속하는 플레이어가 탭 패킷을 못 받고 스폰이 탭 항목 없이 이뤄진다. 해제 시 `playerdata/null.dat` 생성 | `ConnectionHandler.java:160-163,234-238`, `Region.java:452-471` | 비접속 클라이언트가 다른 플레이어에 영향 |
| 12 | 같은 이름 중복 로그인 허용 → 엔티티 2개, 같은 playerdata 파일 공유 | `ConnectionHandler.java:145-165`, `World.java:155` | 데이터 충돌 |
| 13 | `Disconnect` 패킷이 `UnsupportedOperationException`을 던지는 스텁. 모든 끊김은 맨 `ctx.close()` | `Disconnect.java:18-24` | 클라는 항상 "Connection lost". 사유 문구 없음 |
| 14 | 읽기/로그인 타임아웃 없음, `WRITE_BUFFER_WATER_MARK` 없음 | `NetworkServer.java:58-62` | 조용히 붙어 있는 소켓이 영구 점유. 느린 클라이언트로 출력 버퍼 무한 증가 |
| 15 | 레거시 핑(0xFE)에서 프레임 디코더가 영원히 대기 | `FrameDecoder.java` | 옛 핑 도구가 소켓을 계속 붙잡음 |
| 16 | 상태(핑) JSON: motd에 개행/제어문자가 있으면 JSON 깨짐 | `StatusResponse.java:13-19` | 서버 목록에 오류 표시 |
| 17 | `-ea` 없이 실행해 소유권 가드(`Owned.get`, `World.assertOwns`)가 프로덕션에서 컴파일 아웃 | `furnace-server/build.gradle.kts:28` | 스레드 소유 위반이 조용히 통과 |
| 18 | (정정: 대부분 반박) `World.tickThread`가 region별 `r.tick()`을 `catch (Throwable)`로 감싸고 인박스·지연 작업도 Throwable을 잡음. try 밖은 반복자·`isEmpty()`·`snapshots.remove`뿐. UncaughtExceptionHandler·워치독은 없음 | `RegionThread.java:96`, `World.java:104-111` | 낮은 우선순위 |
| 19 | `RegionFile` 쓰기: 섹터 수를 `& 0xFF`로 저장(255 초과 시 헤더 손상), 구멍 재사용 없음(파일이 계속 커짐), 읽기만 해도 빈 .mca 생성, 핸들 무제한 캐시(FD 고갈) | `RegionFile.java:16-90`, `RegionFileStore.java:12` | 디스크·FD 문제 |
| 20 | `FurnaceMain.regionPosForThread`가 N이 31의 배수일 때 대부분의 스레드에 닿지 못함(N=31은 30/31, N=62는 60/62 도달 불가). 종료 flush가 다른 스레드가 쓰는 `HashMap`을 건드리는 데이터 레이스가 되고, 예외 시 IO 풀 종료·`closeAll`을 건너뜀 | `FurnaceMain.java:76-78,102-108`, `RegionGrid.java:24` | 31·62코어 호스트에서 기본 설정으로 발생 |

## 2. 영역별 점검

### 2.1 네트워크 / 프로토콜

**프레이밍·핸드셰이크·상태**
- [OK] 프레임 형식, 압축 임계값(256), SetCompression→LoginSuccess 순서, TCP_NODELAY, 구현된 패킷 ID 전부 1.21.1과 일치.
- [DIFF L] 압축 패킷이 임계값 미만이어도 거부 안 함, 패킷 끝에 남은 바이트 검사 없음.
- [DIFF L] 핸드셰이크가 프로토콜 버전을 검증하지 않음. 바닐라는 `multiplayer.disconnect.incompatible` / `outdated_client`로 Login Disconnect.
- [DIFF L] next-state 3(transfer)은 조용히 닫음(바닐라: `transfers_disabled`). 상태 요청 반복·Pong 후 연결 유지 등 상태기계 엄격성 없음.
- [DIFF M, C] 상태 JSON: `online`이 항상 0, `max` 20 고정(설정 불가, 미적용), `sample` 비어 있음(바닐라는 최대 12명), favicon 없음, `enforcesSecureChat` 없음. motd 속성 없음(server-name 사용).

**로그인**
- [OK] 오프라인 UUID(`OfflinePlayer:`+이름)는 바닐라/Paper/Spigot과 동일.
- [MISSING M] 온라인 모드·암호화·Mojang 세션 확인·스킨 프로퍼티. (오프라인 서버와 동일한 외형이지만 `online-mode` 키가 없음)
- [MISSING L] Login Plugin Request/Response, 쿠키, Velocity/BungeeCord 포워딩.
- [DIFF L-M] 이름 검증이 길이 16뿐. 바닐라는 문자 33~126만 허용.
- [MISSING M] 서버 가득 참, 화이트리스트, 밴.
- [DIFF L] LoginSuccess의 strict error handling이 false(바닐라는 true로 알고 있음, 확인 필요).

**구성(Configuration) 단계**
- [OK] Known Packs(`minecraft:core 1.21.1`) 선언. 레지스트리 10종 전송(순서는 바닐라와 다르나 무해).
- [MISSING H, C] **Update Tags(0x0D)가 전혀 안 나감.** 블록/유체/아이템/엔티티 태그가 비어 클라이언트의 수영·사다리·태그 기반 로직이 깨질 가능성이 큼(실제 클라이언트로 확인 필요).
- [MISSING M] `minecraft:enchantment` 레지스트리(바닐라 42개). 크리에이티브 마법 부여 책 탭과 마법 부여 아이템 디코딩에 영향.
- [DIFF L] `damage_type` 47개(바닐라 48, `mace_smash` 누락 추정).
- [MISSING L-M] `minecraft:brand`(Paper는 `Paper`), Feature Flags(0x0C), Server Links, 리소스팩, 쿠키, Transfer, 구성 단계 KeepAlive.
- [DIFF L] 클라이언트의 Known Packs 응답을 읽지 않고 항상 `hasData=false`로 전송.

**PLAY 클라이언트바운드** — 122개 중 구현 24개: 0x01 SpawnEntity(플레이어 타입 128), 0x05 BlockChangedAck, 0x09 BlockUpdate, 0x0C/0x0D 청크 배치, 0x12 ContainerClose, 0x13 ContainerSetContent, 0x21 UnloadChunk, 0x22 GameEvent, 0x26 KeepAlive, 0x27 LevelChunkWithLight, 0x2B Login, 0x2E~0x30 MoveEntity, 0x33 OpenScreen, 0x3D/0x3E PlayerInfo, 0x40 SyncPlayerPosition, 0x42 RemoveEntities, 0x48 SetHeadRotation, 0x54 SetCenterChunk, 0x56 SetDefaultSpawn, 0x70 TeleportEntity. 0x1D Disconnect는 던지는 스텁.
- [MISSING H] 채팅/명령: System Chat 0x6C, Player Chat 0x39, Disguised Chat 0x1E, Commands 0x11, Command Suggestions 0x10 등. 입장·퇴장 메시지도 없음.
- [MISSING M] Update Time 0x64(클라 시계가 접속 때 0부터 시작, 플레이어마다 다름).
- [MISSING M] 접속 시 상태: Change Difficulty, Player Abilities, Set Held Item, Update Recipes, Update Recipe Book, Initialize World Border, Server Data, Set Health/Food, Set Experience, Update Attributes, Set Entity Metadata.
- [MISSING M] 엔티티 메타데이터·장비·애니메이션·이벤트: 다른 플레이어의 웅크리기/달리기/자세, 스킨 2번째 레이어(클라가 보낸 skin-parts 바이트를 읽지 않음), 손에 든 아이템, 팔 휘두르기가 안 보임.
- [MISSING L-M] World Event, Sound, Particle, Block Action(상자 뚜껑), Block Entity Data.
- [MISSING L-M] Update Light 0x2A, Update Section Blocks 0x49(여러 블록 변경 묶음 전송).
- [DIFF L-M] `LoginPlay`: `isFlat=true`(수평선·안개 높이 차이), `hashedSeed=0`(바이옴 블렌딩 지터 차이), `maxPlayers=20` 고정, 차원 목록이 overworld 1개, 게임모드 creative 고정, simulationDistance = viewDistance.
- [DIFF L] `Types.writeAngle`이 truncate(바닐라는 floor). 음수 각도가 최대 1.4° 어긋남.
- [DIFF L] 이동 델타를 `round((new-last)*4096)`로 전송(바닐라는 `encode(new)-encode(last)`), 주기적(400틱) 전체 텔레포트 재동기화 없음.
- [DIFF L] 추적 범위가 설정값(`entity-view-distance`) 기반(바닐라는 타입별 범위, Paper 기본 플레이어 48블록).

**PLAY 서버바운드** — 58개 중 처리 12개: 0x00, 0x0A(뷰 거리만), 0x18, 0x1A/0x1B/0x1C, 0x24(status 0만), 0x2F, 0x32, 0x38, 0x0E(상자 PICKUP만), 0x0F. 나머지는 로그 없이 무시.
- 미처리 항목과 바닐라 동작: 0x01 블록 엔티티 조회 / 0x02 난이도 변경 / 0x03·0x06·0x07 채팅·세션 / 0x04·0x05 명령 실행 / 0x08 청크 배치 수신 확인(클라 속도 기반 전송 조절; Furnace는 고정 `chunks-per-tick`) / 0x09 리스폰·통계 / 0x0B 명령 자동완성 / 0x0D·0x10 컨테이너 버튼 / 0x12 클라 brand 기록 / 0x13 디버그 샘플 / 0x14 책 편집 / 0x15 엔티티 조회 / 0x16 엔티티 상호작용(공격 포함) / 0x1D 지상 상태만 / 0x1E·0x1F·0x26 탈것·입력 / 0x20 아이템 선택(크리에이티브 중클릭) / 0x21 Ping(play) / 0x22·0x28·0x29 레시피 / 0x23 플레이어 능력(비행) / 0x25 플레이어 명령(웅크리기·달리기·침대·겉날개) / 0x2A~0x2E 이름 변경·리소스팩 응답·발전 과제·거래·신호기 / 0x30·0x31·0x33·0x34 명령 블록·구조 블록 / 0x35 표지판 편집 / 0x36 팔 휘두르기 / 0x37 관전 텔레포트 / 0x39 아이템 사용(먹기·활·양동이).
- [DIFF L] 컨테이너 id를 VarInt로 읽음(바닐라는 unsigned byte, 1~100에서는 동일).
- [DIFF L] 한 패킷 이동 한도 100블록(바닐라 "moved too quickly" 약 10블록). NaN/범위 밖은 보정 텔레포트(바닐라는 `invalid_player_movement`로 disconnect).
- [DIFF L-M] KeepAlive 타임아웃은 사유 없이 `close()`. 잘못된 id 응답은 무시(바닐라는 `Timed out`).
- [DIFF L] 접속 순서가 바닐라와 다름(Furnace는 SyncPosition을 두 번 보냄).
- [OK] 탭 목록 구조(액션 0x3D, 일괄 전송, 600틱 핑 갱신, 스폰 전 선행). 게임모드는 전부 1(creative). `INITIALIZE_CHAT` 생략, Tab Header/Footer 없음.

### 2.2 플레이어 / 엔티티 / 게임플레이

요약: 현재 Furnace는 **크리에이티브 전용 플레이어 존재 서버**다. 있는 것은 로그인·청크 스트리밍·탭 목록·다른 플레이어 스폰/이동/머리 회전·크리에이티브 블록 파괴/설치·상자 1종 GUI·위치/인벤토리 저장뿐이다.

**이동·상태**
- [DIFF L-M, I] 이동 검증: 충돌·물리 모델이 없어 10배 빠른 이동과 noclip을 수락(바닐라: "moved too quickly" 100/틱², "moved wrongly" 오차² 0.0625, ±3e7/±2e7 클램프).
- [MISSING M, C] 웅크리기·달리기·수영·겉날개·자세(Player Command 0x25, Player Input 0x26). 웅크리기로 설치/컨테이너 건너뛰기도 없음(상자에 블록을 든 채 Shift-클릭해도 상자가 열림).
- [MISSING M, C] 엔티티 메타데이터(스킨 레이어, 손 방향, 자세, 체력, 불). `SpawnEntity`는 플레이어 타입 128 고정.
- [MISSING L, C] Move-Player 상태 전용(0x1D): onGround만 바뀐 경우 전파 안 됨. Player Abilities(0x23, 비행 토글) 무시.

**게임모드·체력**
- [DIFF H, C] 게임모드가 전원 creative 고정(`LoginPlay.java:39`, `PlayerInfoUpdate.DEFAULT_GAME_MODE`). 바닐라 기본은 survival이고 플레이어별 저장. 없는 것: `/gamemode`, Game Event 3, 능력 패킷, 어드벤처/관전 규칙, `SetCreativeSlot` 게이트.
- [MISSING H, C] 체력·허기·포화·피해·낙하 피해·공허 사망(y<-128)·사망 화면·리스폰(Client Status 0x09). 공허에서 영원히 떨어짐. Update Health/Respawn 미전송.
- [MISSING L, C] 경험치(크리에이티브에선 숨김이라 현재는 안 보임).
- [DIFF L-M, C] `SetDefaultSpawnPosition`에 월드 스폰이 아니라 플레이어의 로그인/저장 위치를 보냄(나침반이 마지막 로그아웃 위치를 가리킴). 침대/리스폰 앵커 스폰, 신규 플레이어 스폰 반경 랜덤화(바닐라 10), 사망 위치 없음.
- [MISSING L-M, C] 스폰 보호(기본 16), op/권한 개념.

**인벤토리**
- [OK, 범위 좁음] `slots[46]`(메뉴 번호), Set Held Item 검증(0~8), 핫바/크리에이티브 슬롯 기록.
- [DIFF H, C] 저장된 인벤토리를 클라에 안 보냄(1절 9번).
- [DIFF M] Set Creative Slot(0x32)이 검증 없이 수락됨. 바닐라는 크리에이티브 모드, 슬롯 1~45, 개수 ≤99, 유효 아이템을 요구하고 슬롯 -1은 아이템 드롭.
- [MISSING H, C] 클릭 종류: PICKUP(상자 안)만 처리. QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT(드래그), PICKUP_ALL(더블클릭) 무시, 재동기화 없음. container 0(크리에이티브 탭, 2×2 제작칸) 클릭 무시. `changedSlots`를 그대로 믿고 `stateId`를 무시. Shift-클릭은 되는 것처럼 보이다가 재열람 때 되돌아감.
- [DIFF M, C] 컴포넌트가 있는 아이템(물약·이름 붙은·마법 부여)이 Container Click에 있으면 패킷 전체를 폐기(재동기화 없음).
- [DIFF M, C] 커서 아이템이 컨테이너를 닫을 때 반환/드롭되지 않음. 다음에 상자를 열 때 stale 값이 다시 전송됨.
- [MISSING M, C] 버리기/교체: Q/F(dig status 3/4/6)는 ack만. 아이템 엔티티가 없고 서버 인벤토리가 줄지/교체되지 않아 클라 예측과 어긋남.
- [MISSING H(생존)/L(현재), C] 제작·레시피·레시피북·제작대 (Update Recipes, Place Recipe 0x22 없음).
- [DIFF L-M, C] 왼손(슬롯 45)은 저장만 되고 설치는 주 손만. 갑옷은 저장만 되고 장비로 방송되지 않음.
- [DIFF L, D] 저장 형식이 독자 형식(`Slot` int, `Id` int, 컴포넌트 raw). 바닐라 `.dat`는 빈 인벤토리로 로드됨(실제로는 5번 결함으로 크래시).

**컨테이너**
- [OK, 범위 좁음] 상자 1종: OpenScreen 9×3, id 1~100 순환, 사거리 확인 후 닫기, 파괴 시 강제 닫기, 내용 저장.
- [MISSING H, C] 큰 상자(합쳐짐·54칸 메뉴): 설치가 항상 `facing=north,type=single`. 상자가 플레이어를 향하지도 않음.
- [MISSING M, C] 뚜껑 애니메이션·소리(Block Event 0x08), 제목이 번역키(`container.chest`)가 아닌 평문, 위가 막힌 상자 검사, 다른 보는 사람과의 편집 동기화, 파괴 시 내용물 드롭.
- [MISSING H, C] 다른 모든 컨테이너/상호작용 블록: 화로·용광로·훈연기, 제작대, 모루, 마법 부여대, 주민 거래, 엔더 상자, 함정 상자, 통, 호퍼, 발사기/공급기, 양조기, 셜커, 베틀, 숫돌, 대장장이 작업대, 신호기, 독서대, 말 인벤토리, 문/버튼/레버/침대/노트 블록 사용. 상자 특수 처리는 이름 `minecraft:chest` 정확 일치뿐.
- [MISSING M, I] 다른 region의 플레이어가 연 상자는 강제 닫기 대상이 아님(`Region.java:105-106`).

**블록 파괴**
- [DIFF H, C] dig status 0만 동작(즉시 파괴로 처리). status 1~6은 ack만. 없는 것: 모드·도구 제한, 스폰 보호, 경도/도구/파괴 시간 검증, 드롭·전리품, 파괴 소리·입자(Level Event 2001), 이웃/지지 업데이트(문·침대 반쪽, 키 큰 식물, 횃불, 중력 블록, 유체 유입), 상자 쌍 처리.
- [DIFF L, I] 사거리 `REACH_SQ=64`(눈 높이 기준). 바닐라는 4.5(크리에이티브 5.0)+AABB 1.0이고 hit vector도 검사.
- [OK] 미로드 청크는 생성하지 않음. 범위 밖 요청은 `resendBlock`으로 되돌리고 ack.

**블록 설치**
- [DIFF H, C] 아이템→블록 매핑이 이름 일치 → 기본 상태뿐. 방향·반쪽·축·타입·수중·부착·멀티파트 상태가 없음. 계단·통나무·화로·리피터·관측기·표지판·횃불이 항상 기본 방향. 문·침대·키 큰 꽃은 한쪽만. 반 블록은 항상 아래. 횃불/표지판은 벽 변형 없이 위로. 이름이 다른 아이템(레드스톤, 씨앗, 감자·당근·열매, 실, 코코아 콩)은 설치 불가. 양동이·부싯돌은 무동작, Use Item(0x39) 미처리.
- [DIFF M, C] 설치 위치가 항상 `clicked + 면 오프셋`. 바닐라는 대상이 교체 가능(풀, 눈 층)이면 그 블록 자체를 교체. 클라 예측과 한 칸 어긋남.
- [DIFF M, C] 교체 가능 집합이 하드코딩 11개 이름. 바닐라는 replaceable 태그 + `canBeReplaced`(해초, 눈 층, 불, 발광 지의류, 광원, 매달린 뿌리, 같은 블록 병합: 반 블록·양초·눈·피클).
- [DIFF L-M, C] 충돌 검사가 0.6×1.8 상자 vs 모든 블록을 완전한 정육면체로 취급. 웅크리기/수영/겉날개 높이 무시, 발 위치의 비충돌 블록(횃불·꽃·레일·버튼)도 거부, 같은 region의 플레이어만 검사, 다른 엔티티 무시.
- [MISSING M] `canSurvive`/지지 검사(공중 횃불·식물 생김), 월드 보더 검사.
- [MISSING L-M] 왼손 설치(hand=1), 웅크리기 상호작용.
- [OK] 높이 제한, 사거리·로드 게이트, 항상 ack 전송.
- [OK, 이번 수정] 이미 채워진 칸 설치 거부, 교체 시 블록 엔티티 정리.

**블록 업데이트·물리·틱**
- [MISSING H, C] 이웃 업데이트, 예약 틱, 유체 틱, 랜덤 틱 모두 없음. `Region.tick`은 플레이어·청크·가시성·저장만 처리. 없는 것: 모래·자갈 낙하, 물·용암 흐름, 불, 레드스톤, 문, 침대, 식물 떨어짐, 잎 소멸, 잔디 번짐, 작물 성장, 얼음 녹음, 눈, TNT, 피스톤. 월드젠이 만든 물·용암은 정지 상태.
- [MISSING M, C] 수중 상태(waterlogging), 빛(1절 및 2.3의 E1).
- [DIFF L] 하이트맵 3종 중 2종만 전송(MOTION_BLOCKING_NO_LEAVES 없음).

**블록 엔티티**
- [DIFF M] 상자만 존재. 화로·표지판·침대·현수막·머리·스포너·주크박스·호퍼 등 없음. 클라이언트가 블록 업데이트 시 빈 자리표시자 생성.
- [DIFF L, I] 청크 패킷의 상자 태그가 독자 `Items{Slot,Id,Count}`(바닐라는 빈 업데이트 태그). 클라가 파싱하지 못하고 오류를 로그할 수 있음. 저장 id는 `furnace:chest`.

**엔티티·아이템·몹**
- [MISSING H, C] 아이템 엔티티·드롭·줍기, 몹·동물·주민·투사체·경험치 구슬·갑옷 거치대·그림, 몹 스폰(월드젠 시점 포함). `EntityView`와 `Player`가 플레이어 전용.
- [MISSING H, C] 공격·엔티티 상호작용(Interact 0x16), 팔 휘두르기(0x36), 아이템 사용(0x39). 다른 플레이어의 휘두름·피해·넉백이 안 보임.
- [MISSING M, C] 다른 플레이어의 장비(Set Equipment)·애니메이션.
- [MISSING M] 입장·퇴장 채팅 메시지와 퇴장 애니메이션.

**월드 시계·날씨·보더**
- [MISSING H, C] Set Time 미전송. 시간 경과·수면 스킵·달 위상 없음. (클라가 시계를 자체적으로 돌리는지는 실제 확인 필요)
- [MISSING M, C] 날씨(비·천둥 Game Event, 날씨 주기) 없음. 항상 맑음.
- [MISSING L] 월드 보더: Initialize Border 미전송(클라 기본 ±29,999,984가 바닐라 기본과 같아 사실상 일치). 보더 로직·피해·경고 없음.

**채팅·명령·효과**
- [MISSING H, C] 채팅(0x06), 명령(0x04/0x05) 무시. 시스템/플레이어 채팅 미전송. 명령 트리·제안·op 없음. `/gamemode`, `/time`, `/tp`, `/give` 모두 불가.
- [MISSING M, C] 소리·입자·레벨 이벤트·블록 이벤트: 다른 플레이어의 걷기·설치·파괴 소리가 무음.
- [MISSING L-M, C] 발전 과제(L 화면 비어 있음, 토스트 없음), 통계.
- [MISSING L, C] 점수판, 보스바, 타이틀/액션바, 포션 효과, 속성, 스킨 프로퍼티(오프라인 모드 Paper와 동일).

**일치하는 부분**: 오프라인 UUID, Position long 인코딩(부호 확장 포함), 컨테이너 id 순환 1~100, 20 TPS region 틱, 뷰 거리가 클라 요청으로 클램프됨, 청크 뒤에 블록 변경이 가도록 하는 구독 순서(`World.Subscription`), 블록 편집 ack/거부 흐름.

### 2.3 월드 / 청크 / 저장

**디스크 형식(Anvil)**
- [DIFF M, D] 리전 파일: 레이아웃(8KB 헤더, 4KB 섹터, `r.X.Z.mca`)은 맞음. 쓰기는 항상 zlib(타입 2), 읽기는 타입 1·2만 — 바닐라의 타입 3(무압축)·4(LZ4)와 1MB 초과용 외부 `.mcc` 미지원. `region-file-compression=lz4` 월드나 큰 청크는 "미지원 압축타입"으로 실패.
- [DIFF H, D] 청크 NBT에 쓰는 필드가 `DataVersion`, `xPos/yPos/zPos`, `Status=full`, `sections[Y, block_states]`, `Heightmaps.MOTION_BLOCKING`, `block_entities`뿐. 안 쓰는 것: `biomes`, `SkyLight/BlockLight`, `isLightOn`, `LastUpdate`, `InhabitedTime`, `structures`, `block_ticks/fluid_ticks`, `PostProcessing`, 다른 하이트맵. (하이트맵·빛은 바닐라가 로드 시 재계산하므로 치명적이지 않음. `InhabitedTime` 리셋은 지역 난이도를, 구조물 데이터 누락은 `/locate`와 구조물 내 스폰을 깨뜨림)
- [OK] block_states 팔레트·비트 패킹(`max(4, ceillog2)`, long 비걸침, 단일 항목은 `data` 생략). 읽기도 바닐라와 같음. (파일 머리 주석 "비-default state만"은 틀림)
- [DIFF H, C/D] 바이옴을 쓰지 않고 읽을 때 무시해 전 칸을 plains로 채움(`AnvilChunkCodec.java:165`). 바닐라 월드는 바이옴 색·강수·몹 스폰을 잃고, Furnace가 편집한 청크도 재시작 후 생성 바이옴을 잃음.
- [DIFF L, I] 로드 시 하이트맵 NBT를 무시하고 WORLD_SURFACE만 재계산.
- [MISSING H, D] 알 수 없는 데이터 보존 없음: 바닐라 청크를 편집·저장하면 구조물·틱·후처리·`InhabitedTime`이 사라짐.
- [DIFF M, C] `Status`를 읽을 때 확인 안 함(생성 중간 단계 청크가 full로 로드되어 구멍/기능 누락). `DataVersion` 확인·데이터 픽서 없음. 1.18 이전 `Level` 래퍼 청크는 `getList("sections")`가 null이라 예외.
- [DIFF M, D, 설계상] 편집된 청크만 저장. 생성만 된 청크는 dirty를 지우고 저장 안 함. 바닐라는 생성된 모든 청크를 저장. Furnace 지형이 바닐라와 약 0.94% 블록 차이가 있어 바닐라로 같은 폴더를 열면 이음새가 보임.
- [MISSING, D] 엔티티·POI 파일(`entities/`, `poi/`) 없음. 구조물 시작 정보 미저장.
- [DIFF M, D] 청크·플레이어 쓰기가 원자적이지 않음(임시 파일+rename, `.dat_old` 없음).

**level.dat·월드 폴더**
- [MISSING H, D] `level.dat`을 읽지도 쓰지도 않음. 시드·스폰·시간·게임룰이 `furnace.properties`에서 옴. 시드 기본값 0(바닐라: 빈 값이면 무작위). 스폰은 매 시작 시드로 재계산(`/setworldspawn`·바닐라 `SpawnX/Y/Z` 무시; 감사에서 3개 시드 중 1개가 Y 105 vs 112로 불일치). 없는 필드: 버전 정보, `Time`, `DayTime`, 비/천둥, 보더, `GameRules`, 난이도, 하드코어, `WorldGenSettings`, `DataPacks`. 바닐라 월드를 다른 시드 설정으로 열면 잘못된 시드로 새 지형 생성.
- [MISSING M, D] `session.lock` 없음(두 인스턴스가 한 폴더를 열면 .mca 손상).
- [MISSING L, D] `stats/`, `advancements/`, `data/`, `datapacks/` 안 씀(기존 파일은 무시).
- [MISSING H, C/D] 차원: 오버월드만. `DIM-1`, `DIM1`, 포털, 차원 이동 없음. `LoginPlay`가 차원 1개만 알림.

**playerdata**
- [DIFF H, D] 쓰는 필드가 `Pos`, `Rotation`, `OnGround`, 독자 `Inventory`뿐. 없는 것: `DataVersion`, `UUID`, `Health`, 허기, 경험치, `abilities`, `playerGameType`, `Dimension`, `Spawn*`, `EnderItems`, `SelectedItemSlot`, 효과, 속성. 인벤토리 슬롯 번호가 프로토콜 번호(0~45)라 바닐라(0~35, 100~103, -106)와 다름.
- 바닐라 파일 로드 크래시, 저장 시점·경합 문제는 1절 5~8번.

**접속 시 월드 정보 패킷**
- [MISSING H, C] UpdateTime, [MISSING M, C] 날씨, [MISSING L, C] 보더·난이도(클라가 Normal로 표시, 하드코어 false 고정)·게임룰(`reducedDebugInfo`, `doLimitedCrafting` 고정).
- [DIFF L-M, C] `isFlat=true`, `hashedSeed=0`, 뷰/시뮬 거리가 둘 다 플레이어별 유효 뷰 거리, `SetChunkCacheRadius` 미전송(로그인 후 클라가 렌더 거리를 올려도 클램프된 채), 상태 핑 `online:0`.
- [DIFF M, C] `SetDefaultSpawnPosition`이 플레이어 자신의 로그인 위치(`(int)` 절삭)이고 각도 항상 0.
- [DIFF L, C] 신규 플레이어 스폰 반경 랜덤화 없음.

**빛·하이트맵**
- [DIFF H, C] 빛이 상수: 26개 섹션 모두 skylight 15(0xFF), block light 0. 동굴·지붕 아래도 환하고 횃불이 무효. 빛 엔진 없음. 청크마다 53KB 상수 데이터. 편집 후 재계산도 잘못된 기준에서 클라이언트가 수행.
- [DIFF L-M] 패킷의 MOTION_BLOCKING은 이름 기반 근사(눈 바이옴에서 97.6% 열 불일치: 얇은 눈·가루눈). 생성/Anvil 로드는 `cave_air`를 표면으로 세고 편집은 세지 않아 표면 배열이 생성 경로에 따라 다름. `nonAirCount`는 `cave_air`/`void_air`를 공기로 안 세어 과대 계상(무해).
- [OK] 와이어 팔레트 형식(단일값 bits=0, 블록 indirect 4~8·direct, 바이옴 indirect 1~3·direct), YZX 인덱스 순서, 블록 엔티티 인코딩.

**청크 스트리밍**
- [OK] nearest-first 순서, SetCenterChunk, UnloadChunk, 배치 Start/Finished(실제 전송 수), 접속 시 동기 배치, 청크 뒤에 블록 변경을 흘려주는 구독 방식(순진한 순서보다 나음).
- [DIFF L, C] 클라 `desiredChunksPerTick` 무시하고 틱당 `chunks-per-tick`(기본 16, 약 320청크/초) 고정 전송. `ChunkBatchReceived`(0x08) 미처리.
- [DIFF M, C] 대기 청크가 512를 넘으면 새 중심 청크만 알리고 청크를 큐잉하지도 언로드하지도 않음(`Region.java:333-337`). 이후 플레이어가 멈추면 다음 중심 변경까지 새 링을 못 받음. 기본 뷰 거리 5(121청크)에서는 발생하지 않고 큰 거리에서 발생.
- [MISSING L-M, I] 청크 티켓·강제 로드·스폰 청크 상주 없음(구독자 없으면 100틱 후 evict). 플레이어가 없으면 아무것도 틱하지 않음.

**저장·설정**
- [OK] 자동저장 기본 6000틱, 종료 훅이 dirty 청크를 region 스레드별로 flush하고 IO 풀 대기 후 리전 파일 닫음. 크래시 시 최대 5분 편집 손실(바닐라와 동일 수준).
- [DIFF M, OP] 설정 파일이 `server.properties`가 아닌 `furnace.properties`. 기본 `view-distance` 5(바닐라 10), 최소 2(바닐라 3), 최대 32는 동일. `spawn-chunk-radius`는 바닐라 게임룰과 의미가 다름(접속 시 동기 반경만).

### 2.4 월드 생성

**현황 요약**: 지형 뼈대(노이즈·대수층·동굴·표면 규칙·53 바이옴 다중 노이즈 샘플러)는 거의 비트 단위로 일치. 그 위는 부분 구현이다. 오버월드 배치 기능 151개 중 35개가 Noop을 건드리고, 구조물은 34개 중 1개(사막 피라미드)만 있고, 네더·엔드·빛·몹·바이옴 영속화가 없고, 대형 바이옴·증폭·평지는 연결되어 있지 않다.

**검증 수준**
- 오라클 모듈(`worldgen-oracle/`)은 기능(feature) 단계에서 실제 서버와 어긋나 권위가 없다(`FeaturePipelineParityTest.java:286-291`). 2026-08-17/18부터 실제 서버 `.mca` 픽스처로 대체.
- [DONE-VERIFIED] 노이즈 기본 요소, NoiseWorldSourceParityTest(3청크, 범주 수준), SurfaceParityTest(9), AquiferParityTest(10), BiomeSamplerParityTest(6), CarverParityTest(16, 일부 허용치 있음), FeatureOrderParityTest(11단계 전부, 단계 9는 93개), StructurePlacementParityTest(129×129청크, 시드 1234, desert_pyramid·shipwreck만).
- [허용치 있음] FeatureSpillParityTest(0~3408까지 허용), StructureFeatureParityTest(700~1850), ChunkSweepParityTest(옵트인).
- 독립 실서버 감사: 기능 제거 지형 0.0038% 차이, 전체 생성 하드 불일치 0.76%(범위 0.28~1.37%, 바닐라 자체 실행 간 노이즈 0.05~0.61%), 완전히 동일한 청크는 3.9%뿐. 사막·악지·늪·맹그로브·깊은 바다는 표본에 없음. 네더·엔드·엔티티·전리품·유체 틱 미포함.
- 오라클 검증 전혀 없음: 하이트맵, 스폰, 저장/로드 왕복, 빛, desert_pyramid 외 구조물.

**지형 기반**
- [DONE-VERIFIED] 노이즈·밀도 함수·대수층. 다중 노이즈 바이옴 소스는 6청크로 검증(53 바이옴, 7593 파라미터 점, OverworldBiomeBuilder만). 잔차 0.032% quart 열이 이웃 바이옴으로 뒤집힘(원인 미상). 가설: 정확한 적합도 동률에서 `Climate.RTree`의 첫 잎을 쓰는 바닐라와 달리 가장 낮은 리스트 인덱스를 씀. R-tree(자식 6개) 포팅을 시도해볼 만함.
- [STUB M, 미래] `beardifier`가 상수 0.0으로 파싱됨. 현재는 맞지만 마을·고대 도시·전초기지·요새가 생기면 지형 적응이 막힘.
- [DONE-VERIFIED + 잔차 L] 광맥(구리/철). 결정적 잔차 남음(화강암·구리·응회암·철, 3,202칸 일부).
- [MISSING H] **Bandlands(악지 점토 띠) 표면 규칙**: `SurfaceRule.Bandlands`가 null로 평가됨(`SurfaceSystem.java:279`). 점토 띠 생성·`clay_bands_offset`·`getBand` 없음. 악지·우거진 악지·침식된 악지가 테라코타 줄무늬를 잃음. 감사에서 이 바이옴은 표본에 없었고 문서에도 없는 내용.
- [MISSING M-H] 침식된 악지 기둥 확장, 얼어붙은 바다 빙산 표면 확장(`badlands_pillar`, `iceberg_pillar`, `iceberg_surface` 코드 없음).
- [DIFF L-M] `temperature()`가 기본 바이옴 온도만 씀(높이 보정·얼어붙은 바다 노이즈 보정 없음 → 얼음 영향). `steep`은 자기 높이와 청크 안 4이웃의 절대 차이(>4)를 비교하는데, 바닐라는 z±1, x±1을 방향성 있게 비교하는 것으로 약 80% 확신해 "근사"보다 큰 차이일 수 있음.
- [DONE-VERIFIED] 오버월드 동굴·추가 지하 동굴·협곡. 대체 가능 블록 목록이 이름 집합 하드코딩(태그 아님, L). 네더 동굴·협곡 [MISSING].

**배치 기능**
- 파이프라인: 5×5 pristine 격자, 9개 원점 row-major 장식, 3×3 이웃 바이옴 집합, 바닐라 FeatureSorter 위상 정렬, 기능별 재시드, 블렌드된 `getBiome` 필터. 구조 참조(STRUCTURE_REFERENCES)는 저장하지 않고 위치 함수로 매번 재계산(반경 8).
- 구현된 configured_feature 19종: ore, scattered_ore, spring_feature, disk, random_patch, flower, simple_block, seagrass, kelp, freeze_top_layer, geode, tree, random_selector, random_boolean_selector, simple_random_selector, underwater_magma, multiface_growth(glow_lichen만), forest_rock, vines. 나무 부품: 줄기 9/9, 잎 11/11, 맹그로브 뿌리, 장식 6/6, 크기 2종. 상태 제공자 5종.
- [STUB/MISSING] Noop(오버월드 관련): coral_tree/claw/mushroom, sea_pickle, root_system, huge_red/brown_mushroom, vegetation_patch·waterlogged_vegetation_patch, sculk_patch, iceberg, fossil, ice_spike, monster_room, lake, desert_well, blue_ice, bamboo, block_column(사탕수수·선인장·동굴 덩굴), large_dripstone·dripstone_cluster, `pointed_dripstone`(허용 목록에 없이 `simple_random_selector`로 Noop 낙착), `no_bonemeal_flower`(인라인 자식으로만 등장, 허용 목록에 없음). 네더/엔드 전용: chorus_plant, nether_forest_vegetation, huge_fungus, twisting/weeping_vines, netherrack_replace_blobs, block_pile, basalt_*, delta_feature, glowstone_blob, end_*, bonus_chest, void_start_platform.
- 단계별(오버월드 배치 기능 151개): 1 LAKES 2개 전부 Noop / 2 LOCAL_MODS 5개 중 빙산 2·큰 점적석 Noop / 3 UNDERGROUND_STRUCT 4개 전부 Noop(화석·몬스터 방) / 4 SURFACE_STRUCT 4개 중 청빙·사막 우물·얼음 가시 Noop, ice_patch 사실상 죽음 / 5 STRONGHOLDS 요새 없음 / 6 UNDERGROUND_ORES 34개 중 33개 구현, disk_grass Noop / 7 UNDERGROUND_DECOR 5개 중 4개 Noop(점적석 클러스터, 뾰족 점적석, 스컬크 패치, sculk_vein; ore_infested는 구현) / 8 FLUID_SPRINGS 3개 구현 / 9 VEGETAL_DECOR 93개 중 19개가 Noop 접촉 / 10 TOP_LAYER 근사. Noop 접촉 합계 36(`disk_grass` 포함; 제외하면 35, 기존 감사의 34는 sculk_vein·disk_grass 등을 센 방식 차이로 보임. 타입 기준 감사 테스트 시각은 28). monster_room은 53개 바이옴, lake_lava는 52개.
- [DIFF H, 문서에 없음] **배치 수정자가 조용히 버려짐**: `parsePlacement`가 알 수 없는 타입을 무시(`BiomeFeatureData.java:1257-1259`). `random_offset`·`environment_scan`이 오버월드 배치 기능 9개에 있음. `ice_patch`(얼음 가시)는 y-1 오프셋을 잃어 `matching_blocks snow_block` 필터가 공기 칸을 검사하게 되어 사실상 배치 안 됨. `disk_grass`는 별도 원인(위 참고)으로 Noop. `spore_blossom`과 `trees_grove`(소나무/가문비 on snow)는 `environment_scan`·`random_offset` 손실. `fixed_placement`·`count_on_every_layer`도 없음(오버월드 데이터엔 미사용). 지원되는 수정자 12종: count, rarity_filter, in_square, height_range, biome, heightmap, surface_water_depth_filter, noise_threshold_count, noise_based_count, block_predicate_filter, carving_mask, surface_relative_threshold_filter. (`random_offset` 9개, `environment_scan` 7개, 합집합 10개; `trees_grove` 자체엔 없고 내부 `pine_on_snow`/`spruce_on_snow`에 `environment_scan`만 있음. `disk_grass`는 오프셋 손실이 아니라 `parseDisk`가 `matching_blocks`가 아닌 규칙 타입을 통째로 Noop 처리해서 죽음)
- [DIFF M] `SpringFeature`(`SpringFeature.java:34-44`)가 위쪽(항상 유효)까지 세고 `rocks >= rockCount`를 씀. 바닐라는 서·동·북·남·아래 5면을 `== 4`(물 샘)로 검사한 것으로 약 90% 확신하며(jar 대조 필요), Furnace는 3면만 유효해도 배치함. `== AIR`라 `cave_air`를 놓치고 유체 틱 예약이 없음.
- [DIFF M] `clamped` IntProvider의 `count`가 상수 1이 됨(`BiomeFeatureData.java:1294-1297`): forest_flowers(바닐라 0~1), flower_forest_flowers(바닐라 0~3)에서 개수 자체가 틀리고 `nextInt` 소비도 달라 RNG가 어긋남. 오버월드에서 해당하는 것은 이 2개뿐.
- [DIFF M] 알 수 없는 블록 태그가 stone/deepslate로 조용히 대체(`tagStates` 하드코딩). 블록 술어 `solid`, `replaceable`, `has_sturdy_face` 미지원.
- [DIFF M] 중첩 Noop이 조용함(`random_patch`의 비단순 내부 기능, `simple_block`의 null 제공자, glow_lichen 외 multiface 등). 감사 테스트는 루트 타입만 확인. 선택기 안의 Noop은 같은 기능 스트림의 이후 반복에서 RNG 소비도 누락시킴.
- [DIFF M] `FreezeTopLayerFeature`가 거친 포팅(기본 온도만, 높이 보정·빛·`canSurvive` 없음, 풀/흙 밑 `snowy=true` 미설정, 블렌드 아닌 원시 quart 바이옴).
- [STUB] 하이트맵: 이름 기반 근사. MOTION_BLOCKING과 MOTION_BLOCKING_NO_LEAVES 둘 다 `getSurfaceY+1`.
- [STUB] simple-block 생존: 흙 태그 집합 + 버섯 특수 처리(하늘 개방으로 빛을 근사).
- [DONE-UNVERIFIED] 나무: 나무 위치가 가장 큰 알려진 격차(하드 불일치의 46%, 원인 미상). 실서버 허용치: 메가 정글 1566, 맹그로브 3408, 메가 가문비 987.

**구조물**
- 구현: desert_pyramid만(2개 실서버 인스턴스에서 검증). 상자에 전리품 없음, 의심스러운 모래 미배치(1블록 차이), 바이옴 유효성이 `minecraft:desert`로 하드코딩, 구조 feature 인덱스 `DESERT_PYRAMID_STRUCTURE_INDEX = 1`은 정렬된 오라클 레지스트리에서 가져왔고 실서버 순서와 같다는 보장 없음.
- 난파선: 배치만 있고 `applyStructureStarts`가 desert_pyramid 외 모든 ID를 건너뜀(`NoiseWorldSource.java:413`).
- 배치 코드: `random_spread`만 [DONE-VERIFIED]. structure_set 20개 중 2개만 있음. `concentric_rings`(요새)는 `Unsupported placement type` 예외(요새 structure_set 파일이 없어 지금은 잠재적), `exclusion_zone`·`locate_offset` 없음.
- [MISSING] 나머지 33개 구조물 전부: 광산, 요새(stronghold), 네더 요새, 해저 신전, 삼림 대저택, 고대 도시, 시련의 방, 폐허 포털, 묻힌 보물, 마을, 전초기지, 이글루, 정글 사원, 늪 오두막 등. NBT 템플릿·템플릿 풀(`.nbt` 리소스) 자체가 없음.
- 구조물 시작·참조 미저장(`/locate` 불가, 336회 `applyStructureStarts`를 `generateFull`마다 캐시 없이 호출).

**차원·월드 유형·시드**
- [MISSING H] 네더·엔드: 로그인 핸드셰이크가 `minecraft:overworld` 하드코딩, `NoiseRouter`·`NoiseWorldSource`도 오버월드 하드코딩, `end_islands`는 `UnsupportedOperationException`, 네더/엔드 noise_settings·바이옴 소스 없음.
- [MISSING] 대형 바이옴·증폭: DF·노이즈 JSON은 있으나 선택기가 없고 `noise_settings`는 `overworld.json` 하나뿐. 평지(superflat): `SuperflatWorldSource`는 있지만 main 어디서도 참조하지 않음. 단일 바이옴·디버그·`generator-settings`·`level-type`·`generate-structures` 없음.
- 시드: 숫자 아니면 `String.hashCode()`로 바닐라와 같음. 빈 값은 0(바닐라는 무작위). level.dat이 없어 시드가 저장되지 않으므로 기존 월드에서 시드를 바꾸면 저장된 청크 옆에 비정합 지형이 생김.
- [DONE-UNVERIFIED] 스폰: 기후 검색 포팅은 충실. 높이는 `generate()` 결과(동굴·기능 없음)로 샘플링해 감사 기준("장식 전")보다 엄격. 3개 시드 중 1개 불일치.
- [MISSING] 빛, 월드젠 시점 몹, 전리품 테이블, 스포너, 유체 틱 예약(샘).

**데이터 출처 위험(M)**: `worldgen/1.21.1` 리소스에 1.21.4 전용 데이터가 섞여 있음(`pale_garden.json`, `pale_oak*`, `pale_moss*`, `*creaking*` 기능, `blocks/1.21.4.txt`, 코드 주석의 "oracle's 1.21.4 jar"). 오라클은 1.21.1 dev bundle로 고정. `biome_parameters.json`은 수리됨(커밋 2a9e4ff). 나머지 디렉터리(biome, placed_feature, configured_feature, noise, density_function, noise_settings)는 1.21.1 서버 jar와 대조된 적이 없음. 나무·식생 드리프트의 후보 원인.

**성능**: 바이옴 집합 재계산(원점당 9청크 `NoiseChunk`+`sampleChunk`, `generateFull`당 81회, pristine 격자에 이미 `biomeIds`가 있음 → 가장 큰 저비용 개선), 구조물 시작 링 336회 비캐시 호출, 꺼진 추적 플래그에서도 `RandomTrace.log`가 문자열을 즉시 연결(12곳), `PlacedFeature.placeAt` 스트림 다용. 캐시(pristine LRU 128, 표면 높이 LRU 256)는 유한. 시간 측정 수치는 문서에 없음.

### 2.5 서버 인프라 / 관리 / 플러그인 API

**설정**
- [DIFF M, OP] 파일이 `furnace.properties`(자동 생성 안 됨, 없으면 `Config.DEFAULT`, gitignore됨, `args` 무시, 리로드 없음). 지원 키 15개: 바닐라 유사(이름 다름) `server-name`(motd), `port`(server-port), `compression-threshold`(network-compression-threshold); 같은 이름 `view-distance`, `level-name`, `level-seed`; Furnace 전용 `tick-nanos`, `entity-view-distance`, `region-size`, `region-threads`, `io-threads`, `autosave-ticks`, `chunk-eviction-grace-ticks`, `spawn-chunk-radius`, `chunks-per-tick`.
- [MISSING M, OP] `server-ip`(와일드카드 바인드), `max-players`(20 하드코딩·미적용), `online-mode`, `white-list`, `enforce-whitelist`, `op-permission-level`, `gamemode`, `force-gamemode`, `difficulty`, `hardcore`, `pvp`, `spawn-protection`, `simulation-distance`, `level-type`, `generator-settings`, `generate-structures`, `allow-nether`, `spawn-monsters/animals/npcs`, `enable-command-block`, `rate-limit`, `enable-query`, `enable-rcon`, `resource-pack*`, `max-tick-time`, `player-idle-timeout`, `sync-chunk-writes`, `region-file-compression`, `use-native-transport`(NIO 전용), `enable-status`, `hide-online-players`.
- [DIFF L] 기본값: `view-distance` 5·최소 2(바닐라 10, 3~32), `level-seed` 기본 0·빈 값도 0(바닐라는 무작위).
- [DIFF L] `Properties.load(InputStream)`이 ISO-8859-1로 읽어 한글 `server-name`은 `\u` 이스케이프 필요.
- [OK] 값 검증이 키 이름과 함께 fail-fast, `view-distance` 32 초과는 WARN과 함께 클램프, 숫자가 아닌 시드는 `String.hashCode()`.

**콘솔·로그·수명주기**
- [MISSING M, OP] stdin 콘솔 없음(탭 완성 포함). 정지는 Ctrl+C/SIGTERM뿐.
- [DIFF OP] 로깅: logback 콘솔 전용(`%d{HH:mm:ss.SSS} [%thread] %-5level %logger{20} - %msg`), `latest.log`·gz 로테이션·색상 없음, 모든 메시지가 한국어, 월드젠 밖 로그 문장이 약 11개뿐이라 누가 접속했는지 로그에서 알 수 없음. `exceptionCaught`가 연결 리셋마다 WARN.
- [DIFF M, OP] TPS: 5초 하트비트가 region 스레드 0번만 읽음(`FurnaceSchedulerImpl.java:52`), 1초 단위 틱 수. MSPT·최악 스레드 값·"따라가지 못함" 경고·`/tps` 없음. 측정값은 20.0.
- [OK] 종료: 유휴 서버에서 SIGTERM이 51ms에 종료(exit 143).
- [DIFF M] 종료·킥 시 "서버 종료" 메시지 없음(`Disconnect` 스텁, 1절 13번). 킥 API 없음.
- [DIFF L] Netty `shutdownGracefully()`를 await하지 않음(`NetworkServer.java:80-81`).
- [DIFF M, OP] JVM: `-Xmx6G`만(`-Xms`·GC·pretouch 없음), `-ea` 없음(1절 17번), 시작 스크립트·Dockerfile·fat jar·README 없음(Gradle `installDist` 스크립트뿐), Java toolchain 고정 없음, 부팅 약 3.2초.

**영속·관리 파일**
- [MISSING M, D] `level.dat`, `session.lock`, `ops.json`, `whitelist.json`, `banned-players.json`, `banned-ips.json`, `usercache.json`, `eula.txt` 없음. 월드 디렉터리가 작업 디렉터리 기준 `<level-name>`(Gradle `run`에서는 `furnace-server/world`). Paper로 열면 새 월드로 취급됨.
- [DIFF M, I/D] 인벤토리가 `Player`가 아닌 `ConnectionHandler.slots`에 있고 `channelInactive`에서 글로벌 스레드로 저장(1절 7~8번).
- [DIFF] 월드 디렉터리 내 `.mca` 관련은 1절 19번.

**명령·권한·접근 제어**
- [MISSING H(공개 서버), OP] Brigadier·명령/채팅 처리·Commands 패킷·자동완성 없음. stop, op, deop, whitelist, ban, kick, say, list, tp, gamemode, time, weather, give 전부 없음. 권한 레벨 없음. 모든 클라이언트가 creative이고 인벤토리는 클라이언트 권한(`ConnectionHandler.java:417`).
- [MISSING M] 로그인 게이트: 밴·화이트리스트·max-players·이름 검증·중복 로그인 처리 없음.
- [DIFF M] 파이프라인이 `FrameDecoder`, `FrameEncoder`, 핸들러뿐. 읽기/로그인 타임아웃(바닐라 30초) 없음, IP별 스로틀 없음, KeepAlive는 play에서만 시작(첫 전송 15초 후, 미응답 시 다음 주기에 종료되므로 실효 약 30초로 바닐라와 같음. 점검자 사이에 15초/30초로 표현이 갈렸으나 코드상 15초 주기 × 2회 미응답이 맞음).
- [DIFF M] `Player.send`가 패킷마다 `eventLoop.execute`+`writeAndFlush`(flush 합치기·`WriteBufferWaterMark` 없음). 청크 전송은 클라 배치 피드백 무시(바닐라는 적응형).
- [MISSING] RCON, Query, 메트릭(bStats·JMX), 워치독·크래시 리포트.

**플러그인 시스템 / `furnace-api`**
- 현재 API: `FurnaceScheduler`(`global()`, `region(RegionPos)`), `TaskScheduler`(`execute`, `submit`, `executeDelayed`, 모두 `CompletableFuture` 반환), `GlobalScheduler`, `RegionPos` record, `Owned`, `ThreadOwner`.
- [MISSING PL] Plugin 인터페이스·수명주기, 이벤트, 명령, 설정, 로거, Server/World/Player/Block API, `plugin.yml`, 로더(`ServiceLoader`/`URLClassLoader` 없음), 클래스로더 격리, `plugins/` 디렉터리. `furnace-plugin-test/SamplePlugin`은 아무도 로드하지 않는 일반 클래스. Bukkit/Paper 플러그인 호환 없음(설계 문서 §3: "Bukkit code not used"). 플러그인 API는 로드맵상 M6(`docs/superpowers/specs/2026-06-06-furnace-multithreaded-mc-server-design.md`).
- [DIFF PL] Folia 대비: 플러그인별 작업 소유·취소 가능 핸들 없음(`CompletableFuture.cancel`은 대기 중인 작업을 멈추지 못함, `RegionThread.java:188-192`), 반복 작업·`EntityScheduler`(설계 §4.4 예정)·비동기 스케줄러 없음, 위치 기반 지정 없음(`RegionGrid`가 서버 내부용이고 `region-size`가 API에 노출되지 않아 블록→`RegionPos` 변환 불가), 작업 예외가 future 속으로 사라짐(Paper는 로그), close 후 제출 시 region은 future가 영원히 pending·global은 `RejectedExecutionException`, `GlobalThread.executeDelayed`가 `tick-nanos`와 무관하게 `delayTicks*50`ms(전역 틱이 없어 "0이면 다음 틱" 계약 미준수, `TaskScheduler.java:16-18`).

**스레딩 모델**
- 강점(I, 성능 관련): 스레드 한정 상태(region 맵은 락 없는 per-thread `HashMap`, 교차 region 가시성은 불변 스냅샷 + `ConcurrentHashMap`), 락 없는 입력(Netty→region 단일 쓰기자 volatile 발행), 비동기 청크(IO·gen 풀, 진행 중 로드 중복 제거, 완료를 소유 region 스레드로 마샬), 불변 스냅샷에서의 오프 스레드 직렬화(modCount 검증 본문 캐시), region 스레드에서 IO 분리, 드리프트를 교정하는 틱 타이머(나선 방지), 빈 region reap, 팔레트 압축 섹션, 유한 LRU 생성 캐시.
- 위험:
  1. [M] region→스레드가 정적 해시 `(31x+z) mod N`(`RegionGrid.java:390`), 재균형 없음. 뜨거운 region이 한 스레드에 몰릴 수 있음(Folia는 동적 병합/분할). 31의 배수 스레드 수에서는 인덱스가 `z`에만 의존.
  2. [M] 한 스레드의 여러 region이 순차 틱(`World.java:102-118`). 블로킹 작업이 같은 스레드의 region 전부를 막음: 자동저장·evict(6000틱마다 모든 dirty 청크에 `ChunkSnapshot.of`), 동기 생성 폴백 `Region.chunkAt`.
  3. [M] 틱마다 스캔: `evictIdleChunks`가 로드된 모든 청크를 매 틱 순회(`Region.java:210`), 가시성이 O(플레이어×후보), `syncTabList`가 플레이어당 틱마다 O(P). 수백 명까지는 괜찮지만 첫 확장 한계.
  4. [M] region 간 핸드오프가 최종 일관성: 핸드오프 갭 약 1틱, 관찰자 grace 3틱(`Region.java:30`). region 스레드 지연이 약 100ms를 넘으면 despawn→respawn 깜빡임(문서의 F7, `docs/remote-pr1-port/context-notes.md`). 블록 편집은 소유 region에서 `runOn`으로 하므로 다중 블록·다중 region 원자 작업은 미해결(설계 §4.5에서 열어둠).
  5. [M/H] 월드 전역 작업의 자리가 없음: `GlobalThread`가 틱 루프가 아니라 단일 executor라 결정적 전역 틱이 없고, 시간·날씨·게임룰·`/tick`·예약 명령·`/stop`이 각각 N개 region 스레드로의 fan-out+배리어가 필요한데 그 기반이 없음. 글로벌 스레드가 파일 IO(`PlayerData.save`)와 keepalive 타이머도 돌려 느린 디스크가 둘 다 지연.
  6. [M] 장애 격리: `perTick.run()`이 try 밖(1절 18번), 풀 과구독(region 스레드=CPU 수 + Netty NIO 워커 2×CPU + `ChunkWorkerPool` CPU + gen 풀 최대 4 + IO 풀 4 + `ForkJoinPool` CPU), gen 워커가 `generateFull(...).join()`으로 블록되어 생성 동시성이 4청크로 제한(`NoiseWorldSource.java:476`).
  7. [L/M] 메모리: 로드된 청크마다 컬럼 + 캐시된 스냅샷 + 캐시된 직렬화 본문(`ChunkColumn.java:51-54`). `-Xmx6G` 고정. `ChunkMemoryMeasurement`가 과거 OOM을 인용.

**테스트·CI·문서**
- 테스트: `furnace-server` 약 431개 + `furnace-api` 3개. 스케줄러 테스트는 빈약(`RegionThread` 3개, `GlobalThread` 3개; 던지는 틱·종료 행·시작 실패·`executeDelayed` 의미론 테스트 없음, 설정 파일 없는 부팅·로그인/해제 흐름도 패킷 수준 외 없음).
- CI 없음(`.github` 없음). Gradle 래퍼 9.5.0. `-PskipOracle` 플래그 있음.
- 루트 README·운영 문서 없음. 설정 키는 `ConfigLoader`를 읽어야만 알 수 있음. `docs/`는 한국어 설계 문서·계획·패리티 감사.

## 3. 문서(기존 docs)가 코드와 어긋나는 곳

- `checklist.md` Phase 2: 나무·호수·지오드·식생 패치가 "보류, 아키텍처 막힘, 장식은 중심 청크만"이라고 하나 거짓. `ChunkPipeline.java:20-25`가 5×5 읽기·3×3 쓰기 장식을 하고 tree·geode는 구현됨. lake·vegetation_patch만 Noop이며 구조적 이유는 없음.
- `checklist.md` Phase 2: `environment_scan`·`random_offset`을 열린 항목으로 두었는데 여전히 없고 영향이 큼(2.4의 배치 수정자 항목).
- `checklist.md` Phase 1b: "step9 seagrass_simple 격차, 테스트에서 제외"는 낡음(테스트가 이제 11단계 전부 포함, `seagrass_simple`은 바이옴 JSON에 있음).
- `checklist.md` Phase 3: "FeaturePipelineParityTest 3좌표 0 불일치"는 낡음(2026-08-17에 삭제되어 파일은 디버그 헬퍼만 남음, 오라클 featurefull 픽스처는 "디버깅 보조이지 정확도 게이트가 아님").
- `plan.md`: "기능 타입 5개·수정자 5개"는 낡음(현재 19종·수정자 12종).
- `context-notes.md`: 오라클을 나무 디버깅의 최종 권위로 서술했으나 테스트상 오라클 기능은 실서버와 다름. `furnace.properties: level-seed=123456`이라 했으나 저장소에 그런 파일이 없음(기본 0).
- `vanilla-audit-2026-10-07.md`: 대체로 정확. "34/151"은 실제 35개이며 수정자가 조용히 무시되는 4~5개가 더 있음. 미구현 목록에 화석·사막 우물·청빙·얼음 가시·sea_pickle·root_system·수중 식생 패치·sculk_vein·no_bonemeal_flower가 빠짐. Bandlands·침식 악지·빙산 표면 규칙, 수정자 누락, clamped count, 샘 편차는 언급 없음.
- 문서 전체가 월드젠 중심이고 네트워크·게임플레이 패리티를 다루는 문서는 없음.

## 4. 우선순위 제안

**A. 결함 수정 (1절, 대부분 작고 위험이 낮음)**
1. 시작 실패 시 종료 코드(try/catch+데몬 스레드), 종료 훅 타임아웃, `loading` 실패 오염 수정, 청크/플레이어 로드 방어(바닐라 파일 로드 시 예외 대신 안전 동작).
2. 인벤토리를 `Player`로 옮기고 자동저장에 포함, 원자적 쓰기·단일 쓰기자, 손상 파일 허용.
3. 접속 시 인벤토리·선택 슬롯을 클라에 전송.
4. 실제 `Disconnect`(로그인 JSON, 구성/플레이 NBT)와 모든 `close()` 경로에 사유.
5. 로그인 파이프라인: 순서 검증, 중복 로그인 킥, 이름 검증, 타임아웃, 버전 불일치 메시지.

**B. 클라이언트 패리티 (접속·표시 계층)**
1. Update Tags, brand, Feature Flags, `enchantment` 레지스트리 → 실제 클라이언트로 물·사다리 동작 확인.
2. Update Time·날씨·난이도·보더·능력·체력/허기·레시피 등 접속 시 상태 패킷.
3. 채팅·명령 트리(Commands/Brigadier)·콘솔·`stop`/`op`/`whitelist`/`ban`.
4. 상태 JSON(online/max/sample/favicon), 화이트리스트/밴/max-players.
5. 엔티티 메타데이터·장비·애니메이션(Player Command, Swing Arm).

**C. 게임플레이 코어**
1. 블록 상태 설치(방향·반쪽·큰 상자), 교체 가능 블록, 소리·입자.
2. 이웃 업데이트·예약 틱·유체·랜덤 틱: region 안에서 자기 청크를 순회하는 방식으로 먼저 시작 가능. 필요한 것은 스레드 간 비교 가능한 공유 게임 시간, 청크별 대기 틱 저장소, region 경계 이웃 업데이트 메시지, 교차 region 청크 접근 도우미. 전역 틱/배리어는 경계를 넘는 레드스톤·유체의 결정성용.
3. 게임모드·체력·리스폰, 아이템 엔티티·드롭, 몹·전투, 나머지 컨테이너와 클릭 종류.

**D. 월드·저장**
1. level.dat(시드·스폰·시간·게임룰), 바이옴·구조물·`InhabitedTime` 저장, 알 수 없는 NBT 보존, LZ4/`.mcc`, 리전 파일 개선, `session.lock`, 바닐라 playerdata 형식.
2. 빛 엔진, 네더·엔드, 월드 유형.

**E. 월드 생성**
1. 배치 수정자(`random_offset`, `environment_scan`, clamped `count`), Bandlands·빙산 표면 확장.
2. 1.21.1 데이터 출처 전수 대조(자동화).
3. R-tree 동률 가설, Noop 중첩 감사 테스트, 구조물(요새·광산부터).

**F. 플러그인 API / 운영**
1. 플러그인 수명주기·로더·이벤트·명령, 취소 가능 반복 작업·위치→region 헬퍼, 작업 예외 로깅.
2. 스레드별 MSPT·워치독·부하 분산, `server.properties` 호환 키셋, 파일 로깅과 접속/퇴장 로그, README.

## 5. 2차 검증 결과 (독립 에이전트 8개가 코드를 다시 읽고 대조)

검증 방식: 문서의 각 주장을 코드로 추적해 확정/부분/반박으로 판정. 실제 클라이언트·바닐라 jar는 없어서 "클라이언트에서 어떻게 보이는가"와 바닐라 동작 일부는 기억 기준이다.

### 5.1 판정 요약
- 1절 결함 20건: 전부 실제 문제. 일부는 원인·세부가 달랐고(위 표에 반영) #18은 대부분 반박, #14·#16·#17은 부분 확정.
- 2.1 네트워크(B): 반박 없음. 클라이언트 쪽 영향(Update Tags 부재로 물·사다리 불가, `enchantment` 레지스트리 부재로 크리에이티브 탭 예외 가능성, LoginSuccess strict-error-handling)은 실제 클라이언트가 필요함.
- 2.2 게임플레이(C): 거의 전부 확정. 구조 주장("전역 틱 루프 필요")은 과장이어서 정정함(4절 C-2).
- 2.3 월드·저장(D): 거의 전부 확정, 줄 번호 정확.
- 2.4 월드 생성(E): 큰 틀 확정. 세부 수치와 원인 정정(위 인라인 반영).
- 2.5 인프라(F): 대부분 확정. 반박: "31의 배수에서 인덱스가 z에만 의존"은 N=31일 때만 맞음(`floorMod(31x+z, N)`).
- 줄 번호 오류: `RegionThread.java:163/188-192/223`(실제 `:96`, 인박스는 `:58-76`), `RegionGrid.java:390`(실제 `:24`), `build.gradle.kts:28`(실제 `:27`), `NetworkServer.java:58-62`(실제 `:53-63`), `World.java:155`(실제 `:156`).
- 수치 정정: 테스트 428개(`furnace-server`) + 3개(`furnace-api`), 월드젠 밖 로그 호출 약 20곳, 클라이언트바운드 패킷 총수는 약 124개일 수 있음(확인 불가), KeepAlive는 15초 주기 × 미응답 1회로 실효 15~30초.

### 5.2 검증 중 새로 발견된 문제

**데이터 손실·크래시**
- N1 [M-H] 채널이 spawn 완료 전에 닫히면 `world.playerOnline(p)`로 등록된 Player가 `World.online`에 영구 잔류(adopt 안 돼서 `playerOffline` 호출 경로 없음, `ConnectionHandler.java:238,273-277`, `Region.java:267`). 틱 비용이 유출 수에 비례(`syncTabList`, `broadcastLatencies`가 플레이어마다 전체 순회). 이번 탭 목록 변경이 만든 누수. 수정: `!isActive()` 분기와 `channelInactive`(adopt 전)에서 `playerOffline`.
- 컴포넌트가 있는 아이템을 커서로 집으면 `carried` 파싱(`ConnectionHandler.java:492`)이 try/catch 밖이라 예외로 연결이 끊김(문서의 "패킷 폐기"보다 나쁨).
- 청크 저장 실패는 로그만 남고 dirty가 이미 지워져 재시도 안 됨(`World.java:137-140`). 자동저장+evict 저장의 순서가 다중 스레드 풀에서 뒤집히면 오래된 스냅샷이 최신을 덮어쓸 수 있음.
- 레지스트리에 없는 블록 이름은 `stateIdOrAir`가 조용히 공기로 바꾸고 `DataVersion`을 확인하지 않아, 새 버전·모드 월드를 열고 저장하면 그 블록이 영구 삭제됨(`BlockRegistry.java:115-118`).
- `network.stop()`이 만든 `channelInactive` 저장을 종료 훅이 기다리지 않고 `scheduler.close()`해서 늦은 저장이 버려질 수 있음. 플레이어 저장이 글로벌 스레드에서 블로킹 IO를 해 글로벌 틱을 막음. 저장 실패 예외는 반환된 future를 무시해 로그 없이 삼켜짐(`ConnectionHandler.java:598`). 빠른 재접속 시 로드가 쓰는 중인 파일(잘린 gzip)을 읽어 #6과 같은 접속 실패.
- 손상 리전 파일: 헤더 길이로 `new byte[len - 1]` 할당(상한 없음), 압축 해제 크기 제한 없음, NBT 재귀 깊이 제한 없음(`NbtIo`, StackOverflowError는 `catch (Exception)`이 못 잡음). 읽기 실패는 곧 #3(`loading` 영구 잔류).
- `RegionFile` 생성자가 `readFully` 예외 시 `raf`를 새게 함.

**클라이언트 가시 오류**
- >512 대기 청크 분기(`Region.java:333-337`): 클라가 이미 버린 청크가 구독·`loadedChunks`에 남고 `ready=false`라 `broadcastBlockUpdate`가 `pending` 목록에 계속 쌓임. 플레이어가 돌아와도 `loadedChunks.add`가 false라 재전송 안 됨. 도달 조건은 뷰 거리 12 이상.
- 뷰 거리 변경(Client Information)이 청크 경계를 넘을 때까지 반영 안 됨(`updateChunks`가 중심이 같으면 반환).
- `SetDefaultSpawnPosition`이 `(int)`로 절삭해 음수 좌표에서 한 칸 어긋남.
- 설치 거부 시 `resendBlock` 없이 ack만 보냄(클라 예측은 시퀀스 reconcile에 의존).
- 상자 파괴 시 내용물이 사라짐(드롭 없음), 다른 보는 사람과 편집 동기화 없음.

**보안·DoS (네트워크로 도달 가능)**
- N3 [M-H] 틱당 이동 예산 없음. 패킷당 100블록 제한뿐이라 한 클라이언트가 한 틱에 수천 패킷으로 ±3e7 어디든 이동 가능. 매 틱 새 중심 청크로 약 (2vd+1)²를 구독하고, 대기 >512 분기가 정리를 멈춰 오래된 대기 청크를 계속 직렬화(생성)한다. 공격자 1명이 genPool(≤4)을 거의 독점하고 생성된 청크가 evict 유예 100틱 동안 상주.
- N4 [M] 수신 패킷 속도 제한 없음, region 인박스 드레인이 무제한(`RegionThread.java:115-120`): 0x24/0x38/0x0E 폭주가 해당 스레드의 모든 region 틱을 굶김. 인박스 큐도 무한 증가.
- N5 [M] 상자·인벤토리 아이템 미검증: 음수 count면 바닐라 디코더가 id/컴포넌트를 읽지 않아 이후 슬롯이 어긋나고, 알 수 없는 아이템 id는 클라이언트 디코드 오류. 공유 상자에 저장되어 다른 플레이어가 열 때 영구적으로 발생. 0x32는 프레임 나머지(최대 2MiB)를 `components`로 46슬롯 각각에 보관·저장·재전송하고 접근 때마다 복제. `numChanged` 상한 없음(바닐라 128). 수정: count 1..99, itemId 범위 검증, `components` 길이 제한/파싱, `isEmpty`가 count≤0도 비움으로 취급.
- N7 [L-M] 구성 단계 0x07을 반복하면 매번 레지스트리 약 270개와 Finish를 재전송(증폭). 상태 요청도 반복 가능. 수정: 연결당 1회.
- 중복 UUID: 한 명이 나가면 `PlayerInfoRemove(uuid)`가 남은 같은 UUID의 탭 항목까지 지움(클라 탭은 UUID 키, `listed`는 entityId 키; `Region.java:442-456`).
- N2 [M, 희귀] `chunkSubscribers`의 subscribe/unsubscribe가 원자적이지 않아(`World.java:269,274-279`) 비어 있는 맵이 제거되는 사이 `put`이 고아 맵에 들어가면 그 청크가 영구적으로 안 보이는 구멍이 생김. 수정: `compute`/`computeIfPresent`.
- 로그인 순서 검증 없음의 영향은 #11에 반영.
- 압축 코덱이 `Inflater`/`Deflater`를 `end()`하지 않고 `handlerRemoved` 없음(L, Cleaner 의존).

### 5.3 오탐으로 확인된 것(문서에 쓰지 말 것)
- 메모리 누수 점검: `ByteBuf` 누수는 발견되지 않음(`LevelChunkWithLight.write`가 플레이어별 래퍼를 1회 해제, `SimpleChannelInboundHandler`가 프레임 슬라이스 자동 해제).
- 스레드 안전: `Player.recordLatency`(단일 쓰기자+volatile), `listed/tracked/missingTicks`(region 스레드 한정), IO 전용 필드, modCount 본문/스냅샷 캐시 일관성은 문제없음.
- 구현된 모든 클라이언트바운드 패킷의 필드 순서와 서버바운드 파싱 순서는 1.21.1과 일치(이번 점검 범위에서 와이어 형식 오류는 발견되지 않음). 레지스트리는 `enchantment` 외에는 개수·이름이 일치.

## 6. 수정 진행 상황 (2026-10-08 기준, 브랜치 claude/work-continuation-u597cw)

수정 완료(각각 수정 전 실패/수정 후 통과하는 회귀 테스트 포함):
- 1절 #1(시작 실패 시 종료), #2(종료 flush 타임아웃), #3(청크 로드 실패 재시도), #4(바닐라 블록 엔티티 방어 + 바닐라 상자 읽기), #5·#6(playerdata 방어/손상 백업), #7·#8(플레이어 저장: 원자 쓰기·시퀀스·IO 풀·자동저장), #9(접속 시 인벤토리·선택 슬롯 전송), #11·#12(로그인 순서 검증·이름 검증·중복 로그인 킥), #13(Disconnect 패킷 구현), #14 일부(읽기 타임아웃 30초; WriteBufferWaterMark는 미적용), #16(상태 JSON 이스케이프), #20(스레드 직접 접근으로 종료 flush).
- 5.2의 N1(접속자 집합 누수), N2(구독 경합), N3(이동 예산 + 시야 밖 청크 정리, >512 분기 수정), N4(패킷 속도 제한 + 인박스 드레인 시간 예산), N5(아이템 검증·numChanged 상한·커서 컴포넌트 연결 끊김), N7(Known Packs 1회), 종료 시 사유 전달, 중복 UUID 킥.

남은 항목: #10(evict 직후 재구독 경합), #15(레거시 핑), #17(-ea), #19(RegionFile 개선), N6/N8/N9/N10, WriteBufferWaterMark, 바닐라 playerdata 인벤토리 변환, 상자 컴포넌트 변환, 바닐라 형식 저장, 그리고 2절의 모든 미구현 기능.
