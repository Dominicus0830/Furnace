// 바닐라 AdvancementVisibilityEvaluator — 자신·하위에 달성이 있거나, 자신·부모·조부모 규칙 중 처음 정해진 것이 SHOW 면 보인다.
package org.domi.furnace.server.advancement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

final class VisibilityEvaluator {
    private VisibilityEvaluator() {}

    private enum Rule { SHOW, HIDE, NEUTRAL }

    private static Rule rule(AdvancementNode n, boolean done) {
        DisplayInfo d = n.advancement().display();
        if (d == null) return Rule.HIDE;
        if (done) return Rule.SHOW;
        return d.hidden ? Rule.HIDE : Rule.NEUTRAL;
    }

    private static boolean shouldShow(List<Rule> stack) {
        for (int i = 0; i <= 2; i++) {
            Rule r = stack.get(stack.size() - 1 - i);
            if (r == Rule.SHOW) return true;
            if (r == Rule.HIDE) return false;
        }
        return false;
    }

    private static boolean eval(AdvancementNode node, List<Rule> stack, Predicate<AdvancementNode> isDone, BiConsumer<AdvancementNode, Boolean> out) {
        boolean done = isDone.test(node);
        Rule r = rule(node, done);
        boolean anyVisible = done;
        stack.add(r);
        for (AdvancementNode child : node.children()) anyVisible |= eval(child, stack, isDone, out);
        boolean visible = anyVisible || shouldShow(stack);
        stack.remove(stack.size() - 1);
        out.accept(node, visible);   // 후위 순회: 자식이 부모보다 먼저
        return anyVisible;
    }

    static void evaluate(AdvancementNode node, Predicate<AdvancementNode> isDone, BiConsumer<AdvancementNode, Boolean> out) {
        List<Rule> stack = new ArrayList<>(List.of(Rule.NEUTRAL, Rule.NEUTRAL, Rule.NEUTRAL));
        eval(node.root(), stack, isDone, out);
    }
}
