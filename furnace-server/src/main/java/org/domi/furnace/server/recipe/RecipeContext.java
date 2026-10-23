// 레시피 해석·인코딩에 필요한 이름 → 숫자 id 조회(아이템 레지스트리·아이템 태그).
package org.domi.furnace.server.recipe;

import org.domi.furnace.server.data.ResourceId;

public interface RecipeContext {
    /** 아이템 숫자 id(없으면 null). */
    Integer itemId(ResourceId item);

    /** 아이템 태그의 숫자 id 목록(없으면 null). */
    int[] itemTag(ResourceId tag);

    /** 레시피 직렬화기 숫자 id(바닐라 RECIPE_SERIALIZER 레지스트리 순서). */
    int serializerId(String type);
}
