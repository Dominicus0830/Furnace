// 서버 난이도와 난이도 프로필 — Furnace.server().difficulty(). 아무 스레드에서 불러도 된다(바뀐 난이도는 접속자에게 곧바로 보낸다).
//  - setDifficulty 는 /difficulty 와 같다(프로필은 그 난이도의 바닐라 프로필로 돌아간다). 하드코어 월드는 늘 HARD.
//  - setProfile: 플러그인이 정한 프로필. 클라에는 base 가 보인다. 그 플러그인이 꺼지면 같은 base 의 바닐라 프로필로 돌아간다.
//  - 프로필은 저장하지 않는다(level.dat 에는 base 난이도만 남는다) — 플러그인이 켜질 때마다 다시 건다.
package org.domi.furnace.api.world;

import org.domi.furnace.api.plugin.FurnacePlugin;

public interface DifficultySettings {

    /** 지금 난이도(클라가 보는 4칸). */
    Difficulty difficulty();

    /** /difficulty 와 같다. 프로필은 바닐라로 돌아간다. */
    void setDifficulty(Difficulty difficulty);

    /** 지금 프로필(플러그인이 건 것이 없으면 vanilla(difficulty())). */
    DifficultyProfile profile();

    /** 프로필을 건다(owner 소유). 하드코어 월드에서 base 가 HARD 가 아니면 IllegalStateException. */
    void setProfile(FurnacePlugin owner, DifficultyProfile profile);

    /** 플러그인 프로필을 내리고 지금 난이도의 바닐라 프로필로 돌아간다. */
    void resetProfile();
}
