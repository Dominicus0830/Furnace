// 레시피 레지스트리 — Furnace.recipes(). 데이터 팩 레시피와 플러그인 레시피를 한 표로 본다.
// 제작대·2×2 제작·제작기(크래프터)·화로류·석재 절단기·대장장이 작업대가 모두 같은 표를 읽는다 — 등록하면 그 칸들에서 바로 맞는다.
//
// 키는 "네임스페이스:이름" 소문자. 플러그인 레시피는 등록한 플러그인이 꺼지면 함께 내려가고, /reload 뒤에도 남는다.
// 바닐라(데이터 팩) 레시피도 remove 할 수 있다 — 그 제거는 /reload 뒤에도 유지된다(restore 로 되돌린다).
// 등록·제거는 어느 스레드에서 불러도 된다. 접속자에게는 바뀐 레시피 목록을 곧바로 다시 보낸다.
package org.domi.furnace.api.recipe;

import java.util.Collection;
import java.util.Optional;
import org.domi.furnace.api.plugin.FurnacePlugin;

public interface RecipeRegistry {

    /**
     * 레시피를 올린다. 같은 키가 이미 있으면 IllegalArgumentException(바꾸려면 먼저 remove). 알 수 없는 아이템·태그, 틀린 모양은
     * IllegalArgumentException(서버 상태는 그대로). {@link Recipe.Special} 은 올릴 수 없다.
     * owner 가 꺼지면 이 레시피도 내려간다.
     */
    void register(FurnacePlugin owner, String key, Recipe recipe);

    /**
     * 키의 레시피를 내린다(플러그인·데이터 팩 모두). 있었으면 true. 데이터 팩 레시피를 내리면 /reload 뒤에도 내려간 채로 둔다.
     * 접속자의 레시피 북에서도 잊게 한다.
     */
    boolean remove(String key);

    /** remove 로 내렸던 데이터 팩 레시피를 되돌린다(/reload 를 거치지 않고). 내린 적이 없으면 false. */
    boolean restore(String key);

    /** 키의 레시피(없으면 empty). 데이터 팩의 특수 레시피는 {@link Recipe.Special}. */
    Optional<Recipe> get(String key);

    /** 지금 올라 있는 모든 키(데이터 팩 레시피 뒤에 플러그인 레시피 순). */
    Collection<String> keys();

    /** 이 플러그인이 올린 키. */
    Collection<String> keys(FurnacePlugin owner);

    /** 키가 플러그인 레시피인가(데이터 팩 레시피는 false). */
    boolean isPluginRecipe(String key);
}
