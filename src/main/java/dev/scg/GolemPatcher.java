package dev.scg;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.schedule.Activity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Swaps the vanilla item-transport behaviour inside a golem's brain for {@link CategoryTransportBehavior}
 * (and back). Only the in-memory brain changes: nothing is saved to the golem, so removing the plugin
 * just leaves golems vanilla again after the next load.
 */
public final class GolemPatcher {

    private static final Field BEHAVIORS = behaviorsField();

    private GolemPatcher() {
    }

    public static boolean patch(CopperGolem golem, Supplier<CategoryMatcher> matcher) {
        return patch(golem.getBrain(), matcher);
    }

    public static boolean unpatch(CopperGolem golem) {
        Brain<CopperGolem> brain = golem.getBrain();
        Location where = find(brain, true);
        if (where != null && where.behavior.getStatus() != Behavior.Status.STOPPED
                && golem.level() instanceof ServerLevel level) {
            // Mid-delivery: stop the brain's running behaviours so the swap is safe. The golem just restarts its job.
            brain.stopAll(level, golem);
        }
        return unpatch(brain);
    }

    public static boolean isPatched(CopperGolem golem) {
        return find(golem.getBrain(), true) != null;
    }

    /** @return true if a vanilla behaviour was replaced. */
    static boolean patch(Brain<CopperGolem> brain, Supplier<CategoryMatcher> matcher) {
        Location where = find(brain, false);
        if (where == null) {
            return false;
        }
        TransportItemsBetweenContainers old = (TransportItemsBetweenContainers) where.behavior;
        if (old.getStatus() != Behavior.Status.STOPPED) {
            return false; // mid-delivery; the periodic sweep will get it later
        }
        replace(where, CategoryTransportBehavior.from(old, matcher));
        return true;
    }

    /** @return true if our behaviour was replaced by a vanilla one. */
    static boolean unpatch(Brain<CopperGolem> brain) {
        Location where = find(brain, true);
        if (where == null || where.behavior.getStatus() != Behavior.Status.STOPPED) {
            return false;
        }
        replace(where, CategoryTransportBehavior.toVanilla((TransportItemsBetweenContainers) where.behavior));
        return true;
    }

    private record Location(Set<BehaviorControl<? super CopperGolem>> set, BehaviorControl<? super CopperGolem> behavior) {
    }

    @SuppressWarnings("unchecked")
    private static Location find(Brain<CopperGolem> brain, boolean ours) {
        Map<Integer, Map<Activity, Set<BehaviorControl<? super CopperGolem>>>> byPriority;
        try {
            byPriority = (Map<Integer, Map<Activity, Set<BehaviorControl<? super CopperGolem>>>>) BEHAVIORS.get(brain);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        for (Map<Activity, Set<BehaviorControl<? super CopperGolem>>> byActivity : byPriority.values()) {
            for (Set<BehaviorControl<? super CopperGolem>> set : byActivity.values()) {
                for (BehaviorControl<? super CopperGolem> b : set) {
                    Class<?> type = b.getClass();
                    if (ours ? type == CategoryTransportBehavior.class : type == TransportItemsBetweenContainers.class) {
                        return new Location(set, b);
                    }
                }
            }
        }
        return null;
    }

    private static void replace(Location where, BehaviorControl<? super CopperGolem> replacement) {
        // Keep the set's iteration order the same as before.
        List<BehaviorControl<? super CopperGolem>> copy = new ArrayList<>(where.set);
        where.set.clear();
        for (BehaviorControl<? super CopperGolem> b : copy) {
            where.set.add(b == where.behavior ? replacement : b);
        }
    }

    private static Field behaviorsField() {
        try {
            Field f = Brain.class.getDeclaredField("availableBehaviorsByPriority");
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Brain has no field 'availableBehaviorsByPriority'", e);
        }
    }
}
