# Bukkit 호환 계층의 authlib 다리 (N-lib-2) — 2026-11-11

설명: `docs/feature-parity/compat-nms-nlib-loader-2026-11-11.md` 의 "N-lib-2" 절.

## 알림 (바뀐 것)

- **Bukkit 플러그인이 하나라도 있으면 authlib 묶음을 늘 받는다.** 호환 계층의 `CraftPlayer.getProfile()`·`ServerPlayer.getGameProfile()` 이 authlib `GameProfile` 을 서명에 쓰기 때문이다. 플러그인이 `CraftPlayer` 를 반사로 한 번만 훑어도(`getMethod("getHandle")` 같은 흔한 모양) 그 클래스의 모든 서명이 풀리므로, 그 플러그인이 authlib 을 직접 쓰지 않아도 있어야 한다. Paper 는 authlib 을 늘 싣고 있다.
- **받는 양(한 번만, `plugins/.libraries/` 에 남는다):** authlib 6.0.54(113 KiB, Mojang 저장소) + 의존 Guava 32.1.2·failureaccess(2.9 MiB) + commons-lang3 3.14.0(643 KiB) + commons-io 2.15.1(489 KiB), 모두 4.1 MiB. 판과 sha256 은 바닐라 1.21.1 `libraries.list` 와 같다. Guava·commons 는 많은 플러그인이 이미 써서 대개 받아져 있다.
- **`plugins.compat.libraries.mojang=false` 이거나 받지 못하면** 시작 때 ERROR 한 줄("호환 계층 자신도 [authlib] 를 기대")을 남긴다. 그래도 Bukkit 플러그인은 켜진다. 다만 `getProfile()` 을 부르거나 `CraftPlayer` 를 반사로 훑는 플러그인은 그때 `NoClassDefFoundError` 가 난다.

## 결정할 것

없음. (Mojang 저장소 접근을 끄고 싶은 서버는 위 설정으로 끌 수 있다.)
