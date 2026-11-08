# Audience 연출 확장 (K4)

`Audience` 는 메시지·액션바·타이틀·보스바·사이드바에 이어 소리를 다룬다. 받을 수 없는 대상(콘솔 등)은 조용히 무시한다.

```java
player.sendActionBar(Text.text("체력 회복"));                       // 기존
player.showTitle(Title.title(Text.text("승리"), Text.text("5점"), Title.Times.DEFAULT));  // 기존
player.playSound(Sound.sound("entity.player.levelup", Sound.Source.PLAYER, 1f, 1.2f));    // 내 자리에서
player.playSound(sound, x, y, z);                                  // 월드의 한 지점에서(그 사람에게만)
player.stopSound(Sound.Source.MUSIC, null);                        // 분류별 / 소리별 / stopAllSounds()
Audience.audience(players).playSound(sound);                       // 묶음도 같다
```

- `Sound.key` 는 `minecraft:` 이 생략 가능한 리소스 id(`Sound.isValidKey`). 크기 ≥ 0, 높이 0~2(바닐라 `/playsound` 와 같다 — 0.5 밑은 클라이언트가 0.5 로 올린다). 소리 변형 씨앗은 서버가 무작위로 정한다.
- `stopSound` 의 이름도 같은 규칙이다 — 리소스 id 가 아니면 `IllegalArgumentException`(바닐라 클라이언트는 그런 이름을 읽다 실패하면 접속을 끊는다).
- 서버 구현: `PlayerHandle` 이 `SoundPacket`(0x68)·`StopSound`(0x6A)를 보낸다. 거리 판정은 클라이언트가 크기에 따라 한다.

## 열린 메뉴 제목 바꾸기

`Menu.setTitle(Text)`. 열려 있는 화면(보는 사람 전부)에는 같은 창 번호로 `open_screen` 을 다시 보내 제목을 갈아 끼우고 칸·커서를 다시 보낸다.
닫혀 있으면 값만 바뀌어 다음에 열 때 반영된다. 같은 제목이면 아무것도 보내지 않는다. 한 `Menu` 를 여러 명이 열었으면 모두 바뀐다.
어느 스레드에서 불러도 화면은 마지막 제목으로 맞는다(여는 도중에 바뀌어도, 두 스레드가 함께 바꿔도).

한 사람 창만 바꾸려면 `player.retitleMenu(Text)` — Paper `InventoryView#setTitle` 과 같다: `Menu` 제목·다른 보는 사람은 그대로이고, 창을 닫으면 사라져 다시 열면 `Menu` 제목이다.
`openMenu` 직후(아직 여는 중)에 불러도 된다. 바꾼 동안에는 `Menu#setTitle` 보다 이 값이 이긴다. `player.menuTitle(menu)` 는 그 사람 화면의 제목(열고 있지 않으면 null).
Bukkit 어댑터의 `InventoryView#setTitle`·`getTitle` 이 이것을 쓴다(null 제목은 Paper 처럼 `IllegalArgumentException`).
