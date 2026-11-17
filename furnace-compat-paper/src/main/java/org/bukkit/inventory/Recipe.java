package org.bukkit.inventory;

/** 레시피. Furnace 는 레시피 본문(재료 모양)을 알리지 않아 이벤트가 주는 레시피는 키({@link org.bukkit.Keyed})와 결과만 안다. */
public interface Recipe {

    ItemStack getResult();
}
