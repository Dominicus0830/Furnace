// 서버 목표 → API 이름표. 바닐라 목표는 종류 이름에서(MeleeAttackGoal → minecraft:melee_attack, 몹 안쪽 클래스 SpiderAttackGoal → minecraft:spider_attack,
// 이름 없는 클래스는 그 부모 이름). 플러그인 목표는 플러그인이 준 이름표.
package org.domi.furnace.server.plugin;

import java.util.Locale;
import org.domi.furnace.api.entity.ai.GoalKey;
import org.domi.furnace.server.entity.ai.Goal;

final class GoalKeys {

    private GoalKeys() {}

    private static final ClassValue<GoalKey> BY_CLASS = new ClassValue<>() {
        @Override
        protected GoalKey computeValue(Class<?> c) {
            Class<?> k = c;
            while (k.getSimpleName().isEmpty() && k.getSuperclass() != null) k = k.getSuperclass();
            return GoalKey.minecraft(snake(k.getSimpleName()));
        }
    };

    static GoalKey of(Goal g) { return g instanceof PluginGoal pg ? pg.key() : BY_CLASS.get(g.getClass()); }

    /** MeleeAttackGoal → melee_attack. 끝의 Goal 을 떼고 대문자 앞에 _ . */
    static String snake(String simple) {
        String s = simple.endsWith("Goal") && simple.length() > 4 ? simple.substring(0, simple.length() - 4) : simple;
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (i > 0 && (Character.isLowerCase(s.charAt(i - 1)) || i + 1 < s.length() && Character.isLowerCase(s.charAt(i + 1)) && Character.isUpperCase(s.charAt(i - 1)))) b.append('_');
                b.append(Character.toLowerCase(ch));
            } else if (Character.isLetterOrDigit(ch)) {
                b.append(Character.toLowerCase(ch));
            } else {
                b.append('_');
            }
        }
        return b.toString().toLowerCase(Locale.ROOT);
    }
}
