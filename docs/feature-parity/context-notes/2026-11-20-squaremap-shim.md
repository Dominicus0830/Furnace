# 2026-11-20 squaremap shim 5번 묶음 — 함정 기록

- 감사 도구는 클래스가 통째로 없으면 그 클래스의 멤버를 가린다. 클래스를 채우고 나면 가려져 있던 멤버(예: `BiomeSpecialEffects` 의 색 접근자, `ImposterProtoChunk#getWrapped`)를 바이트코드(javap)로 따로 확인해야 한다.
- squaremap 은 `Biome` 의 첫 비정적 필드(바닐라 `climateSettings`)의 값에서 두 번째 float 접근자를 리플렉션으로 불러 강수량을 읽는다. `getDeclaredMethods` 순서는 JVM 마다 달라 처음 시험에서 온도가 나왔다 → 들고 있는 값의 하위 클래스가 float 접근자를 둘만 선언하고 둘 다 강수량으로 답하게 했다.
- 접속자(`ServerPlayer`)의 `LivingEntity#hasPotionEffect` 는 상태 손잡이가 없어 `UnsupportedFeature` 다 — 투명 여부는 서버 다리 `isPlayerInvisible` 로(효과 표는 잠겨 있어 아무 스레드).
- 시험 서버의 지도 저장소(`MapStore.put`)는 정적이라 시험 끝에 번호를 지우지 않는다(번호 4242 사용).
- compat 에 adventure·brigadier 컴파일 경로가 없어 대역 source set(`src/adventure`·`src/brigadier`)을 더했다. 시험은 adventure 만 진짜(Maven Central 4.17.0)를 쓴다.
