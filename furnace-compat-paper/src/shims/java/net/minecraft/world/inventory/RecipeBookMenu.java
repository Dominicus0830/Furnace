package net.minecraft.world.inventory;

/**
 * 레시피 책이 붙은 창의 상위(Mojang 이름 shim). 추상 메서드는 명세(containers.spec)가 바닐라와 같이 선언한다. 창 상태는 {@link AbstractContainerMenu} 가 든다.
 */
class RecipeBookMenu {

    public RecipeBookMenu(MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }
}
