# T-204 V5 데스크탑 FAWE 실측 — 1회차 (2026-11-17)

기준 브랜치 c22ae39d(#474·#477 포함)로 만든 Furnace 와 Paper(133)에 FAWE 2.16.2 를 올려 `tools/compat/fawe-paper/scenario_t204.py` 를 같은 기준 월드에서 차례로 돌렸다.

## 결과

- **Paper**: 모든 구간 예외 없음. FAWE 응답 `Operation completed` — F1~F4 `//fixblocks` 1216, F5 덩굴·F6 켈프·F7 산호 `//fixblocks` 도 정상, N1·N2 `//perf` 켠 `//set` 정상. 설계 문서가 예상한 "F5~F7 에서 Paper 프록시 null 필드 NPE"는 **나오지 않았다**(가정이 틀림 — 아래).
- **Furnace**: `//fixblocks` 4번 모두, `//perf neighbors on`·`update on` 상태의 `//set` 2번 모두 `UnsupportedNmsCall: husk construction - PaperweightFaweMutableBlockPlaceContext -> BlockPlaceContext`(FAWE 응답 `Please report this error`). 그래서 울타리·레드스톤 가루·판유리가 안 이어졌고(F1·F3·F4), N1 울타리·N2 물은 놓이지 않았다. anvil-diff 블록 칸 차이 202(전부 이 실패의 결과), F2 계단·F5~F7 은 차이 없음(둘 다 결과적으로 바꾸지 않음 — Paper 는 이미 맞는 상태).
- 편집 시간은 둘 다 settle 3 s 가 대부분이라 비교 의미 없음.

## 원인과 고침

- 플러그인 jar 클래스는 NMS 변환기(`NmsLinker`)를 거쳐 정의된다. NMS shim 상속은 등급 C 라 **껍데기**(생성자는 UnsupportedNmsCall, 인스턴스 메서드 지움)로 정의된다. FAWE `PaperweightPlacementStateProcessor` 생성자가 가변 문맥(`BlockPlaceContext` 상속)을 만들다 실패했다. 레벨 프록시(`ServerLevel` 상속, Unsafe 로 만듦)도 껍데기라 재정의한 읽기가 지워져 있었다.
- 3단계 시험(`NmsPlacementTest`)은 FAWE 꼴 사본을 시험 코드에 두어 변환기를 거치지 않았다 — 이 길을 못 봤다(교훈: 플러그인 꼴 시험 클래스는 변환기로 싣는다).
- 고침: `@NmsInheritable` 표시가 붙은 shim(`UseOnContext`·`BlockPlaceContext`·`ServerLevel`)은 상속을 그대로 정의한다. shim 에 없는 상위 생성자(바닐라 서술자 — FAWE 프록시의 15인자)를 부르는 생성자만 그 자리에서 NoSuchMethodError 꼴 `UnsupportedNmsLink`. 시작 보고서도 이 상속을 등급 C 로 세지 않는다. 시험: `NmsLinkerTest.inheritableShimParent…`, `NmsPlacementTest.pluginSubclassesThroughTheLinker`(변환기로 실은 문맥·프록시 = 직접 정의한 사본, 블록 8 × 면 6 × 방향 3 — 표시를 빼면 실패).
- 시나리오: 같은 좌표를 다시 찍으면 FAWE 가 `Position already set.` 로 답해 OK 정규식에 넣었다(1회차 F5 에서 두 서버 모두 멈춤).

## Paper 에서 F5~F7 이 NPE 가 아닌 까닭(가정 정정)

FAWE `PlacementStateProcessor` 는 덩굴·켈프·산호처럼 놓기 계산이 레벨 난수·예약 틱을 쓰는 블록을 그대로 둘 수도 있다 — Paper 결과에서 F5~F7 블록이 바뀌지 않았다(Furnace 와 블록 차이 없음). 재실측에서 Furnace 가 이 구간에서 UnsupportedNmsCall 을 내는지, FAWE 가 그것을 어떻게 다루는지 다시 본다.
