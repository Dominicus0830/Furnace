# LegacyRandomSource.nextDouble 정밀도 (2026-11-12)

- 버그: `block/LegacyRandomSource.nextDouble()` 가 `long * 1.110223E-16F` 였다. Java 승격 규칙상 long×float 는 float 곱셈이라 값이 24비트로 깎이고(바닐라 값과 100% 다름) 최댓값이 1.0 이 되어 [0,1) 계약이 깨졌다. nextGaussian 도 nextDouble 을 쓰므로 같이 틀렸다. (PR #280 검토 R5)
- 바닐라 `BitRandomSource.nextDouble` 은 `(double) l * 1.1102230246251565E-16`(= 2^-53) 이다. 디컴파일러가 이 double 상수를 `F` 접미사로 잘못 표기하므로, 옮길 때 접미사를 믿지 말고 바이트코드 `ldc2_w` 를 기준으로 한다(`XoroshiroRandomSource` 주석과 같은 함정).
- 수정: 상수를 double 로, long 을 double 로 먼저 바꿔 곱한다. 다른 구현(`carver/LegacyRandom`, `XoroshiroWorldgenRandom`, `XoroshiroRandomSource`)은 이미 double 상수였다.
- 시험: `block/LegacyRandomSourceVanillaTest` — JDK `java.util.Random`(같은 LCG, 같은 식)과 nextInt·nextLong·nextBoolean·nextFloat·nextDouble 열을 10개 씨앗×수십만 번 비트 비교, nextGaussian 은 바닐라 MarsagliaPolar 를 옮긴 기준과 비교, 거의 최댓값 상태에서 1.0 미만임을 확인.
- 가정: 이 환경은 Mojang 호스트에 닿지 못해 바닐라 jar 를 직접 돌린 대조는 못 했다. 기준은 바닐라 소스(검토 보고서가 대조한 BitRandomSource)와 알고리즘이 같은 JDK Random 이다.
- 영향: LegacyRandomSource 를 쓰는 모든 곳(엔티티·레벨·인챈트 메뉴·자연 스폰 등)의 nextDouble·nextGaussian 소비 값이 이제 바닐라와 같다. 같은 시드의 결정적 장면 금 파일(identitykit)이 있으면 바뀔 수 있다.
