# 호환: Tag·탭 완성 후속 결정 (2026-11-14)

Bukkit `Tag`·`AsyncTabCompleteEvent`·`PlayerCommandSendEvent` 는 구현했다. 아래는 같은 방향의 남은 호환 공백이고, 따로 TASK 로 열지 정해 주세요.

1. **동기 `TabCompleteEvent` 와 Paper `MaterialTags`·`EntityTags`, `Registry.FLUID`·`Registry.GAME_EVENT` 를 같은 호환 묶음으로 열지.** 모두 플러그인 클래스 목록에서 빠져 있어 쓰는 플러그인은 그 처리기·필드에서 멈춘다. `TabCompleteEvent` 는 Paper 가 플러그인 명령 자동완성 때(CraftBukkit `BukkitCommandWrapper.getSuggestions`)와 콘솔에서 부른다. 추천: 한 TASK 로 연다(작다).
2. **`AsyncTabCompleteEvent.Completion`(Adventure 설명 글 후보) 지원 방법.** 호환 계층에 Adventure 가 없어 지금은 없다(부르면 NoSuchMethodError). Adventure 를 쓰는 플러그인이 많아지면 서버 jar 에 Adventure 를 넣는 쪽(사용자 결정 5 의 "받아오기"를 바꾸는 일)과 맞물린다. 추천: 지금은 그대로 두고 실제 플러그인이 `completions()` 를 부를 때 다시 연다.
