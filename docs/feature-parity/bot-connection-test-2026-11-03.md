# 오프라인 서버 봇 접속 시험 (2026-11-03)

실제 마인크래프트 클라이언트 없이 Furnace 를 `online-mode=false` 로 띄우고, mineflayer 4.39.0 봇(프로토콜 767)으로 접속해
`docs/user-todo.md` 의 접속 시험 항목 중 봇으로 볼 수 있는 것을 돌렸다. 방법·스크립트는 `tools/bot-test/README.md`.

- 서버 jar 는 PR 브랜치마다 따로 빌드했다(#2 `project-thread-554gu6`, #3 `ylhpsc`, #4 `zzt3qo`, #5 `sxkhlg`, #7 `bbbls5`, #8 `aoqsdj`, 기존 항목은 기준 브랜치 `quirky-wright-lra834` = ac615f12).
- 월드는 평지(`level-type=minecraft:flat`), 바닐라 데이터는 `main` 브랜치의 `tools/vanilla-data/server-1.21.1/data` 를 `cache/vanilla-data-1.21.1` 에 둬서 썼다(컨테이너에서는 Mojang 다운로드가 403).
- "통과"는 아래 적은 관측이 기대와 맞았다는 뜻이다. 화면·소리·손맛은 봇이 볼 수 없으므로 그런 항목은 사용자 확인으로 남겼다.

## PR #2 lz4 지역 파일·플레이어 속성
| 항목 | 한 것 | 결과 |
|---|---|---|
| 속성 저장 | `/attribute @s minecraft:generic.max_health base set 40`, 체력 채움, `/attribute @s minecraft:generic.scale modifier add test:big 1 add_value`, 신속 II 효과. **서버 재시작 후 재접속** | 통과. 재접속 뒤 체력 40, 최대 체력 40, Scale 2(수정자 `test:big` 유지), 속도 기본값 0.1(신속 효과 수정자는 효과로만 다시 붙음) |
| lz4 쓰기·다시 읽기 | `region-file-compression=lz4` 새 월드, 블록 배치, 재시작 후 다시 접속 | 통과. `r.0.0.mca` 의 청크 28개 모두 압축 형식 4, 본문이 `LZ4Block` 로 시작. 재시작 뒤 놓은 다이아몬드 블록이 그대로 보임 |
| 바닐라 lz4 월드 열기 / Furnace lz4 월드를 바닐라로 열기 | — | **미확인**: 바닐라 서버를 띄울 수 없어 사용자 확인으로 남김 |

## PR #3 스트라이더
| 항목 | 결과 |
|---|---|
| 안장 | 통과. 어른에게 안장 우클릭 → 안장 1개 소모, `Saddle` 저장. 새끼에게는 소모되지 않음 |
| 뒤틀린 균류 먹이 | 통과. 안장 없는 어른에 균류 우클릭 → 1개 소모, 타지 않음, `InLove` 584(600 에서 감소 중) |
| 타기 | 통과. 빈손 우클릭으로 탑승 |
| 막대 가속·내구도 | 통과. 탄 채 막대 우클릭 → 손상 0→1, 가속 중 재클릭은 그대로 1. 발전 과제 "This Boat Has Legs" 달성 |
| 막대 조종(이동 방향) | **봇으로 확인 불가**: 돼지·스트라이더 조종은 클라이언트가 움직임을 계산해 보내는데 mineflayer 는 이를 흉내 내지 않는다(3초간 이동 0) |
| 내리기 | 가장자리: 통과(용암이 아닌 잔디 칸 9.5,-60,17.5 에 내림). 용암 한가운데: 스트라이더 자리(17.5,-61,17.5)에 내림 — "디딜 곳이 없으면 스트라이더 위로"와 맞는 것으로 봄 |
| 떨기 | 통과(AI 켠 개체). 땅 위 스트라이더 메타데이터 18(떨기)=true. NoAI 개체는 갱신하지 않음(바닐라도 `!isNoAi()` 조건이라고 기억 — 미대조) |
| 기수 스폰 | 통과. 용암 위 `/summon` 600회: 좀비 피글린 기수 20(3.3%, 기대 1/30), 새끼 기수 63(580 중 10.9%, 기대 1/10). 피글린은 뒤틀린 균류 막대를 들고 탄 스트라이더는 안장 있음 |
| 죽으면 안장 | 통과. 땅 위에서 처치 → 안장 + 실 2개 드롭(용암 위면 아이템이 타서 안 보임) |

## PR #4 RCON·채널·소리·거리
| 항목 | 결과 |
|---|---|
| RCON IP당 4개 | 통과. 같은 IP 5개 동시: 4개 인증, 5번째는 바로 끊김. 하나 닫은 뒤 새 연결은 인증됨 |
| 플러그인 채널 알림 | 통과. 시험 플러그인이 `probe:test` 등록 → 접속 때 `minecraft:register` 로 받음 |
| 클라이언트 채널 | 통과. 봇이 `fabric:test`·`mymod:chan` 등록 → `listeningPluginChannels()` 에 둘 다, `unregister` 뒤 하나만 |
| 클라이언트 이름 | 통과. `clientBrand()` = `vanilla`(mineflayer 가 보내는 값) |
| sound_entity | 통과(패킷 수준). 양 털 깎기 `entity.sheep.shear`(PLAYERS), 돼지 안장 `entity.pig.saddle`(NEUTRAL), 삼지창 `item.trident.throw`(PLAYERS, 삼지창 엔티티에 붙음), 염소 뿔 `item.goat_horn.sound.0` **RECORDS**(분 사람 자신은 못 받고 옆 플레이어만 받음 — 바닐라 `playSound(player, …)` 와 같음). 소리가 몹을 따라가는지는 화면 확인 필요 |
| 시야 거리 변경 | **차이 발견**. `setViewDistance(2)`: `set_chunk_cache_radius`(2)는 오지만 서버는 그 자리에서 청크를 내리지 않음(5초간 unload 0). 청크 경계를 넘어 움직여야 84개를 내림. `setViewDistance(6)` 으로 되돌려도 움직일 때까지 새 청크를 안 보냄. 바닐라는 다음 틱에 모든 플레이어의 추적 범위를 다시 잡는다. 실제 클라이언트는 반경을 받으면 스스로 먼 청크를 버리므로, **줄였다 늘리면 움직이기 전까지 빈 구멍이 보일 것**으로 예상(미확인). 원인: `ServerCore.setViewDistance` 가 `Player.viewDistance` 값만 바꾸고 추적 갱신은 청크 경계 이동 때만 일어남. 클라이언트가 렌더 거리를 바꿀 때도 같은 경로라 기준 브랜치에도 있는 문제 |
| 시뮬레이션 거리 | 패킷 통과(`simulation_distance` 3 수신). 작물·몹이 멈추는지는 미확인 |

## PR #5 플러그인 API 2단계 (SamplePlugin, 막대기)
| 항목 | 결과 |
|---|---|
| LEFT_CLICK_BLOCK | 통과. 서바이벌·크리에이티브 모두 블록 안 부서짐, 액션바 `minecraft:stone — clicks in chunk 0,0: N`, 누를 때마다 1씩 증가 |
| 청크 영속 데이터 | 통과. 7 까지 올린 뒤 재시작 → 다음 클릭 8 |
| LEFT_CLICK_AIR | 통과. 하늘 보고 휘두르면 `entities within 8: 5`, 블록·소를 보고 휘두르면 안 뜸 |
| 용기 칸 | 통과. 상자 `has 27 slots, slot 0 = minecraft:diamond x5`, 화로 `3 slots, slot 0 = minecraft:iron_ore x3`(창도 열림 — 플러그인이 취소하지 않으므로 정상) |
| 엔티티 우클릭 | 통과. 소·주민·상자 배 모두 창·탑승 없음, `poked 1 times`, 2초 뒤 `is still here after 2s`. 주민 `trades: 2`, `container: 8 slots`, 상자 배 `container: 27 slots` |
| retired | 통과. 찌르고 바로 `/kill` → `is gone` 만 |
| 엔티티 영속 데이터 | 통과. 3번 찌른 소가 재시작 뒤 `poked 4 times` |
| 엔티티 스폰 | 통과. 서쪽(yaw 90)을 보고 `/samplespawn` → 직후 좀비 Pos [-1.5, -60, 0.5](플레이어 0.5,0.5 에서 2칸 앞) |
| 막대기 없이 | 통과. 크리에이티브 즉시 파괴, 주민 거래 창(`minecraft:merchant`), 상자 배 탑승 |

## PR #6 불안정 시험
서버 코드 변경이 없어 접속 시험 대상이 아니다.

## PR #7 펫 낙하 메시지
| 경우 | 주인이 받은 메시지 |
|---|---|
| 사다리 끝에서 떨어져 죽음 | `Rex10 fell off a ladder` |
| 덩굴 | `Rex14 fell off some vines` |
| 매달린 사다리를 부숨 | `Rex18 fell while climbing` |
| 오를 것 없이 낙하 | `Rex22 fell from a high place` |
(앉힌 길들인 늑대, y 40~45 의 사다리/덩굴, 바닥 y -60.) 모두 기대와 같다.

## PR #8 특수 스포너 오버월드 전용
- 네더(평지 월드의 네더, 플레이어 y 88, 크리에이티브, 어려움, `doInsomnia`·`doPatrolSpawning` 켬)에서 `/tick sprint 100000`: 약탈자·팬텀·고양이 출현 0.
- 대조군: 같은 빌드, 노이즈 오버월드 y 150 발판에서 같은 조건 → 팬텀 2마리 출현(봇이 봄, 낮이 되어 타서 끝에는 0), 순찰대 0, 고양이 0.
- **판정 보류**: 바닐라 조건상 네더에서는 하늘이 안 보여 팬텀이 원래 안 나오고 고양이도 마을이 필요해, 이 시험으로는 수정 전후가 갈리지 않는다. 오버월드에서도 10만 틱 동안 순찰대가 안 나와(굴림 약 5회 × 20%) 순찰 쪽 대조는 불충분하다. 수정의 핵심(레벨 난수 미사용)은 PR 의 단위 시험이 본다.

## 기존 항목 (기준 브랜치)
| 항목 | 결과 |
|---|---|
| RCON | 통과. 맞는 비밀번호로 `list` 실행, 틀린 비밀번호는 인증 실패 |
| Query | 통과. basic stat: `A Minecraft Server / SMP / world / 0 / 20 / 127.0.0.1` |
| 관측 명령 | 통과. `/furnace threads`·`regions`·`profile` 출력 확인(부하 없음) |
| 워치독 | 이번 시험 동안 기동 10여 회, 새 월드 생성 포함, 기본 `max-tick-time` 에서 스스로 종료한 적 없음 |
| 난이도(op) | 통과. 비op 의 `set_difficulty` 는 무시, op 는 바뀜 |
| F3+I 질의(op) | 블록 질의는 통과(상자 NBT, 블록 엔티티 없는 칸은 빈 응답). **엔티티 질의는 응답이 안 왔다 → 고침**(아래) |
| 관전 텔레포트 | 통과. 관전 모드에서 `spectate` 패킷 → 대상 플레이어 위치(30.5,-60,30.5)로 이동 |
| 유휴 킥 | 통과. `player-idle-timeout=1`, 가만히 → 61초에 `multiplayer.disconnect.idling`. (어려움 평지에서는 슬라임에게 죽고 자동 리스폰하며 갱신돼 킥되지 않았다 — 리스폰도 "행동") |
| 리소스팩 | 통과. 필수 팩: 수락→다운로드→적용 후 입장, 거절은 구성 단계에서 바로 `multiplayer.requiredTexturePrompt.disconnect`. 수락 후 40초 동안 응답을 미뤄도 15초마다 keep-alive 가 오고 끊기지 않으며 적용 응답 뒤 입장 |

## 시험 중 찾아 고친 것 (이 브랜치)
1. **엔티티 저장 덧붙이기 실패로 엔티티가 사라짐.** 엔티티가 모두 떠나 빈 `Entities` 리스트로 저장된 청크(디스크에서 원소 종류 TAG_END 로 읽힘)에, 내려간 청크로 들어간 엔티티를 덧붙이면 `NbtIo` 가 `미지원 태그: 0` 으로 실패했다(`[SAVE] 엔티티 0,1 덧붙이기 실패`, 스트라이더 600마리 시험에서 144회). 덧붙이기를 `AnvilWorldSource.appendEntities` 로 옮기고 빈·다른 종류 리스트면 compound 리스트를 새로 만든다. 시험 `EntityAppendEmptyListTest`(수정 전 실패 확인).
2. **op 의 엔티티 NBT 질의(F3+I)에 응답이 없음.** 응답 NBT 를 `/data` 경로 연산용 꼴(`NbtTags.thaw`, 배열 → `NbtArray`)로 보내 UUID int 배열에서 `NbtWriter` 가 예외를 냈다. 엔티티·플레이어·블록 엔티티 응답 모두 `NbtTags.freeze` 로 바꿨다. 시험 `TagQueryFlowTest`(수정 전 실패 확인).

## 그 밖에 본 것 (고치지 않음)
- Furnace 는 구성 단계에서 클라이언트가 알려진 팩(`minecraft:core`)을 몰라도 레지스트리 내용을 늘 생략한다. 바닐라 클라이언트는 팩을 알아 문제없지만, 팩을 모른다고 답하는 비바닐라 클라이언트(mineflayer 등)는 그대로는 접속하지 못한다(봇은 패치로 우회).
- mineflayer 가 `declare_recipes` 패킷을 끝까지 읽지 못한다(PartialReadError). 실제 클라이언트로 접속해 왔으므로 봇 쪽 파서 문제일 가능성이 크지만 확인하지 않았다.
- 평지 월드 어려움에서 슬라임이 많이 나온다(바닐라 평지도 슬라임 청크면 나온다 — 정상으로 봄).
