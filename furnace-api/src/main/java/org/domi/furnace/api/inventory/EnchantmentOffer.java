// 마법 부여대가 보여 주는 제안 하나: 단서로 보이는 마법 부여와 그 레벨, 그리고 비용(레벨). enchantment 가 null 이면 단서 없이 비용만 보인다.
package org.domi.furnace.api.inventory;

public record EnchantmentOffer(String enchantment, int level, int cost) {}
