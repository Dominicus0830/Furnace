# Bukkit 호환 계층과 라이브러리를 한 로더에 (N-lib-1) — 2026-11-11

설명: `docs/feature-parity/compat-nms-nlib-loader-2026-11-11.md`. 검토: `furnace/reviews/nlib-loader-review.md`.

## 알림 (바뀐 것)

- **호환 모듈은 이제 서버 jar 안에 jar 째로 실린다.** Bukkit 플러그인이 있을 때 `plugins/.libraries/compat/` 에 풀어서, Paper 용 라이브러리(authlib·Adventure 등)와 같은 로더에 올린다. Bukkit 플러그인이 없으면 풀지도 올리지도 않는다.
- **`plugins.compat.libraries=needed` 의 뜻이 넓어졌다.** 예전 뜻은 "그 플러그인이 쓰는 묶음만"이었다. 이제는 "호환 계층 자신이 서명에 쓰는 묶음 + 그 플러그인이 쓰는 묶음"이다. 호환 계층이 쓰는 묶음은 호환 jar 를 훑어 자동으로 정한다. 지금은 비어 있다. 다음 단계(authlib 다리)에서 authlib 가 들어가고, 그 뒤 DFU 가 들어간다.

## 결정할 것

- **Adventure 를 받지 못하면 어떻게 할까?** 나중에 Paper API 의 `Player`·`CommandSender` 가 Adventure `Audience` 를 상속하게 되면 Adventure 는 Bukkit 호환 전체의 필수 의존이 된다. 그때 Adventure 를 받지 못하면 Bukkit 플러그인이 전부 켜지지 않는다. 이런 경우는 셋이다: 인터넷 없는 첫 실행, `plugins.compat.libraries=off`, Maven Central 차단.
  - 추천: **지금처럼 필요할 때 내려받기를 유지한다**(결정 4·5 그대로). 받지 못하면 시작할 때 한 줄로 "Adventure 를 준비하지 못해 Bukkit 호환을 끈다, 캐시 폴더에 직접 넣는 방법은 이렇다"를 알린다.
  - 다른 길: Adventure 만 서버 jar 에 함께 싣는다(약 1 MB). 오프라인에서도 되지만, "Bukkit 플러그인이 있을 때만 받는다"는 결정 4 의 예외가 된다.
  - 답이 없으면 추천대로 간다. 이 결정은 Audience 계층을 넣을 때(호환 API 쪽 묶음) 실제로 쓰인다.
