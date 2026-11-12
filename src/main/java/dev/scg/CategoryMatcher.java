package dev.scg;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Decides whether a golem holding {@code held} may drop it into a given container. */
public final class CategoryMatcher {

    public enum Mode {
        /** Same item and same components (stricter than vanilla). */
        EXACT,
        /** Same item type. This is what vanilla does. */
        ITEM,
        /** Same item type, or any two items in the same category. */
        CATEGORY
    }

    private final Mode mode;
    private final boolean allowEmptyContainers;
    private final CategoryRegistry registry;

    public CategoryMatcher(Mode mode, boolean allowEmptyContainers, CategoryRegistry registry) {
        this.mode = mode;
        this.allowEmptyContainers = allowEmptyContainers;
        this.registry = registry;
    }

    public Mode mode() {
        return mode;
    }

    public boolean allowEmptyContainers() {
        return allowEmptyContainers;
    }

    public CategoryRegistry registry() {
        return registry;
    }

    public boolean canDeposit(Container container, ItemStack held) {
        if (container.isEmpty()) {
            return allowEmptyContainers;
        }
        if (held.isEmpty()) {
            return false;
        }
        String heldCategory = mode == Mode.CATEGORY ? registry.categoryOf(held.getItem()) : null;
        for (ItemStack inside : container) {
            if (inside.isEmpty()) {
                continue;
            }
            switch (mode) {
                case EXACT -> {
                    if (ItemStack.isSameItemSameComponents(inside, held)) {
                        return true;
                    }
                }
                case ITEM -> {
                    if (ItemStack.isSameItem(inside, held)) {
                        return true;
                    }
                }
                case CATEGORY -> {
                    if (ItemStack.isSameItem(inside, held)) {
                        return true;
                    }
                    if (heldCategory != null) {
                        Item other = inside.getItem();
                        if (heldCategory.equals(registry.categoryOf(other))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
