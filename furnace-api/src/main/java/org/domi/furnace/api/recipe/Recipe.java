// 레시피 값(불리언 구조 그대로의 불변 기록). 키는 레지스트리 쪽에서 붙인다({@link RecipeRegistry}) — 같은 레시피를 다른 키로 올려도 된다.
// 아이템은 레지스트리 키 문자열이라 서버 내부 번호에 묶이지 않는다. 결과 아이템에는 컴포넌트를 실을 수 있다.
package org.domi.furnace.api.recipe;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.domi.furnace.api.item.ItemStack;

public sealed interface Recipe {

    /** 결과 아이템. 특수 레시피(갑옷 염색 …)는 결과가 입력에 따라 달라 EMPTY. */
    ItemStack result();

    /** 레시피 북 묶음 이름(같으면 한 칸에 모인다). */
    String group();

    /** 바닐라 레시피 직렬화기 이름(minecraft:crafting_shaped …). */
    String type();

    /**
     * 모양 있는 제작대 레시피. pattern 은 1~3줄, 한 줄 1~3글자, 줄 길이가 같다. 공백은 빈 칸이고 그 밖의 글자는 ingredients 에 있어야 한다
     * (쓰지 않는 글자가 있으면 오류). 서버가 앞뒤 빈 줄·열을 잘라 쓴다. 좌우 뒤집은 모양도 맞는다(바닐라 규칙).
     */
    record Shaped(String group, RecipeCategory category, List<String> pattern, Map<Character, RecipeChoice> ingredients, ItemStack result,
                  boolean showNotification) implements Recipe {
        public Shaped {
            group = group == null ? "" : group;
            Objects.requireNonNull(category, "category");
            pattern = List.copyOf(pattern);
            ingredients = Map.copyOf(ingredients);
            requireResult(result);
        }

        public Shaped(List<String> pattern, Map<Character, RecipeChoice> ingredients, ItemStack result) {
            this("", RecipeCategory.MISC, pattern, ingredients, result, true);
        }

        public String type() { return "minecraft:crafting_shaped"; }
    }

    /** 모양 없는 제작대 레시피(재료 1~9개, 같은 재료를 여러 번 적으면 그만큼 필요하다). */
    record Shapeless(String group, RecipeCategory category, List<RecipeChoice> ingredients, ItemStack result) implements Recipe {
        public Shapeless {
            group = group == null ? "" : group;
            Objects.requireNonNull(category, "category");
            ingredients = List.copyOf(ingredients);
            requireResult(result);
        }

        public Shapeless(List<RecipeChoice> ingredients, ItemStack result) { this("", RecipeCategory.MISC, ingredients, result); }

        public String type() { return "minecraft:crafting_shapeless"; }
    }

    enum CookingKind {
        SMELTING("minecraft:smelting", 200), BLASTING("minecraft:blasting", 100), SMOKING("minecraft:smoking", 100),
        CAMPFIRE("minecraft:campfire_cooking", 600);

        private final String type;
        private final int defaultTime;

        CookingKind(String type, int defaultTime) {
            this.type = type;
            this.defaultTime = defaultTime;
        }

        public String type() { return type; }

        /** 바닐라 기본 굽는 시간(틱). */
        public int defaultCookingTime() { return defaultTime; }
    }

    /** 화로·용광로·훈연기·모닥불. experience 는 한 번 구울 때 주는 경험치, cookingTime 은 틱. */
    record Cooking(CookingKind kind, String group, RecipeCategory category, RecipeChoice ingredient, ItemStack result, float experience,
                   int cookingTime) implements Recipe {
        public Cooking {
            Objects.requireNonNull(kind, "kind");
            group = group == null ? "" : group;
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(ingredient, "ingredient");
            requireResult(result);
            if (!(experience >= 0f)) throw new IllegalArgumentException("experience 는 0 이상: " + experience);
            if (cookingTime < 1) throw new IllegalArgumentException("cookingTime 은 1틱 이상: " + cookingTime);
        }

        public Cooking(CookingKind kind, RecipeChoice ingredient, ItemStack result, float experience) {
            this(kind, "", RecipeCategory.MISC, ingredient, result, experience, kind.defaultCookingTime());
        }

        public String type() { return kind.type(); }
    }

    /** 석재 절단기. */
    record Stonecutting(String group, RecipeChoice ingredient, ItemStack result) implements Recipe {
        public Stonecutting {
            group = group == null ? "" : group;
            Objects.requireNonNull(ingredient, "ingredient");
            requireResult(result);
        }

        public Stonecutting(RecipeChoice ingredient, ItemStack result) { this("", ingredient, result); }

        public String type() { return "minecraft:stonecutting"; }
    }

    /** 대장장이 작업대 변환(네더라이트 업그레이드 꼴). 결과에 밑감의 컴포넌트가 덮어 씌워진다. */
    record SmithingTransform(RecipeChoice template, RecipeChoice base, RecipeChoice addition, ItemStack result) implements Recipe {
        public SmithingTransform {
            Objects.requireNonNull(template, "template");
            Objects.requireNonNull(base, "base");
            Objects.requireNonNull(addition, "addition");
            requireResult(result);
        }

        public String group() { return ""; }

        public String type() { return "minecraft:smithing_transform"; }
    }

    /**
     * 읽기 전용: 서버가 값으로 옮기지 못하는 레시피(갑옷 염색 같은 특수 제작, 대장장이 장식). type 만 알려 주고 등록에는 쓸 수 없다.
     */
    record Special(String type) implements Recipe {
        public Special {
            Objects.requireNonNull(type, "type");
        }

        public ItemStack result() { return ItemStack.EMPTY; }

        public String group() { return ""; }
    }

    private static void requireResult(ItemStack result) {
        if (result == null || result.isEmpty()) throw new IllegalArgumentException("결과 아이템이 비었다");
    }
}
