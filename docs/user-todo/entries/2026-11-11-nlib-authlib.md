# Bukkit 호환 계층의 authlib 다리 (N-lib-2) — 2026-11-11

설명: `docs/feature-parity/compat-nms-nlib-loader-2026-11-11.md` 의 "N-lib-2" 절.

## 알림 (바뀐 것)

- **Bukkit 플러그인이 하나라도 있으면 authlib 묶음을 늘 받는다.** 호환 계층의 `CraftPlayer.getProfile()`·`ServerPlayer.getGameProfile()` 이 authlib `GameProfile` 을 서명에 쓰기 때문이다. 플러그인이 `CraftPlayer` 를 반사로 한 번만 훑어도(`getMethod("getHandle")` 같은 흔한 모양) 그 클래스의 모든 서명이 풀리므로, 그 플러그인이 authlib 을 직접 쓰지 않아도 있어야 한다. Paper 는 authlib 을 늘 싣고 있다.
- **받는 양(한 번만, `plugins/.libraries/` 에 남는다):** authlib 6.0.54(113 KiB, Mojang 저장소) + 의존 Guava 32.1.2·failureaccess(2.9 MiB) + commons-lang3 3.14.0(643 KiB) + commons-io 2.15.1(489 KiB), 모두 4.1 MiB. 판과 sha256 은 바닐라 1.21.1 `libraries.list` 와 같다. Guava·commons 는 많은 플러그인이 이미 써서 대개 받아져 있다.
- **`plugins.compat.libraries.mojang=false` 이거나 받지 못하면** 시작 때 ERROR 한 줄("호환 계층 자신도 [authlib] 를 기대")을 남긴다. 그래도 Bukkit 플러그인은 켜진다. 다만 `getProfile()` 을 부르거나 `CraftPlayer` 를 반사로 훑는 플러그인은 그때 `NoClassDefFoundError` 가 난다.
- **이것은 퇴행이다(독립 검토 R1에서 직접 재현).** Mojang 저장소에 닿지 못하는 서버에서는 `CraftPlayer.getMethod("getHandle")`·`getDeclaredMethod("getHandle")`·`Player.getDeclaredField("gameProfile")` 같은 흔한 반사가 PR #229 까지는 되다가 이제 실패한다. authlib jar 를 `plugins/.libraries/com/mojang/authlib/6.0.54/` 에 직접 두면 다시 된다.

## 결정할 것

- **Mojang 저장소(libraries.minecraft.net)에 닿지 못하는 서버를 어떻게 할까?**
  - 추천: **지금처럼 내려받기를 유지한다.** 받지 못하면 시작 때 ERROR 한 줄로 알리고, 위 폴더에 authlib jar 를 직접 넣는 방법을 안내 문서에 둔다. 대부분의 서버는 그 호스트에 닿는다(Paper 사용자도 처음 실행 때 같은 곳에서 받는다).
  - 다른 길 1: authlib(113 KiB)만 서버 jar 에 함께 싣는다. 오프라인에서도 되지만 Mojang 라이브러리를 Furnace 배포물에 넣는 것이 되어 배포 허용 여부를 따로 정해야 한다(절대 규칙 7 과 같은 줄기).
  - 다른 길 2: 받는 저장소를 하나 더 둔다(예: 다른 공개 미러). 출처와 sha256 은 그대로 바닐라 `libraries.list` 로 맞춘다. 어느 미러를 믿을지 정해야 한다.
  - 답이 없으면 추천대로 간다.
