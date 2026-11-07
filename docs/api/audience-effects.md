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

- `Sound.key` 는 `minecraft:` 이 생략 가능한 리소스 id. 크기 ≥ 0, 높이 0.5~2. 소리 변형 씨앗은 서버가 무작위로 정한다.
- 서버 구현: `PlayerHandle` 이 `SoundPacket`(0x68)·`StopSound`(0x6A)를 보낸다. 거리 판정은 클라이언트가 크기에 따라 한다.

## 열린 메뉴 제목 바꾸기

`Menu.setTitle(Text)`. 열려 있는 화면(보는 사람 전부)에는 같은 창 번호로 `open_screen` 을 다시 보내 제목을 갈아 끼우고 칸·커서를 다시 보낸다.
닫혀 있으면 값만 바뀌어 다음에 열 때 반영된다. 같은 제목이면 아무것도 보내지 않는다. 한 `Menu` 를 여러 명이 열었으면 모두 바뀐다.
