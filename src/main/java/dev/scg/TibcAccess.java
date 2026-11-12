package dev.scg;

import net.minecraft.world.Container;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers.ContainerInteractionState;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers.OnTargetReachedInteraction;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers.TransportItemState;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers.TransportItemTarget;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Reflective access to the private parts of the vanilla TransportItemsBetweenContainers behaviour.
 * Every member is resolved once, when this class loads. If the server's version of the class doesn't
 * have one of them, loading fails and the plugin disables itself instead of breaking golems.
 */
final class TibcAccess {

    private static final Class<TransportItemsBetweenContainers> C = TransportItemsBetweenContainers.class;

    private static final Field TICKS = field("ticksSinceReachingTarget");

    private static final Field F_SPEED = field("speedModifier");
    private static final Field F_HORIZONTAL = field("horizontalSearchDistance");
    private static final Field F_VERTICAL = field("verticalSearchDistance");
    private static final Field F_SOURCE = field("sourceBlockType");
    private static final Field F_DESTINATION = field("destinationBlockType");
    private static final Field F_SHOULD_QUEUE = field("shouldQueueForTarget");
    private static final Field F_ON_START = field("onStartTravelling");
    private static final Field F_INTERACTIONS = field("onTargetInteractionActions");

    private static final Method WITHIN = method("isWithinTargetDistance",
            double.class, TransportItemTarget.class, Level.class, PathfinderMob.class, Vec3.class);
    private static final Method CENTER = method("getCenterPos", PathfinderMob.class);
    private static final Method ANOTHER = method("isAnotherMobInteractingWithTarget",
            TransportItemTarget.class, Level.class);
    private static final Method QUEUE = method("startQueuing", PathfinderMob.class);
    private static final Method RANGE = method("getInteractionRange", PathfinderMob.class);
    private static final Method WALK = method("walkTowardsTarget", PathfinderMob.class);
    private static final Method START_TRAVEL = method("onStartTravelling", PathfinderMob.class);
    private static final Method ON_INTERACT = method("onTargetInteraction",
            TransportItemTarget.class, PathfinderMob.class);
    private static final Method SET_STATE = method("setTransportingState", TransportItemState.class);
    private static final Method SET_INTERACTION = method("setInteractionState", ContainerInteractionState.class);
    private static final Method PICK_UP = method("pickUpItems", PathfinderMob.class, Container.class);
    private static final Method PUT_DOWN = method("putDownItem", PathfinderMob.class, Container.class);

    private TibcAccess() {
    }

    /** Forces the class to load, which resolves (and so checks) every member above. */
    static void verify() {
    }

    /** Constructor arguments of a TransportItemsBetweenContainers instance. */
    record Params(float speed,
                  Predicate<BlockState> source,
                  Predicate<BlockState> destination,
                  int horizontal,
                  int vertical,
                  Map<ContainerInteractionState, OnTargetReachedInteraction> interactions,
                  Consumer<PathfinderMob> onStartTravelling,
                  Predicate<TransportItemTarget> shouldQueue) {

        @SuppressWarnings("unchecked")
        static Params read(TransportItemsBetweenContainers b) {
            try {
                return new Params(
                        F_SPEED.getFloat(b),
                        (Predicate<BlockState>) F_SOURCE.get(b),
                        (Predicate<BlockState>) F_DESTINATION.get(b),
                        F_HORIZONTAL.getInt(b),
                        F_VERTICAL.getInt(b),
                        (Map<ContainerInteractionState, OnTargetReachedInteraction>) F_INTERACTIONS.get(b),
                        (Consumer<PathfinderMob>) F_ON_START.get(b),
                        (Predicate<TransportItemTarget>) F_SHOULD_QUEUE.get(b));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        TransportItemsBetweenContainers toVanilla() {
            return new TransportItemsBetweenContainers(speed, source, destination, horizontal, vertical,
                    interactions, onStartTravelling, shouldQueue);
        }
    }

    static int ticks(TransportItemsBetweenContainers b) {
        try {
            return TICKS.getInt(b);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static void setTicks(TransportItemsBetweenContainers b, int value) {
        try {
            TICKS.setInt(b, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean within(TransportItemsBetweenContainers b, double range, TransportItemTarget t, Level l, PathfinderMob m) {
        return (boolean) call(WITHIN, b, range, t, l, m, center(b, m));
    }

    static Vec3 center(TransportItemsBetweenContainers b, PathfinderMob m) {
        return (Vec3) call(CENTER, b, m);
    }

    static boolean anotherMobInteracting(TransportItemsBetweenContainers b, TransportItemTarget t, Level l) {
        return (boolean) call(ANOTHER, b, t, l);
    }

    static void startQueuing(TransportItemsBetweenContainers b, PathfinderMob m) {
        call(QUEUE, b, m);
    }

    static double interactionRange(PathfinderMob m) {
        return (double) call(RANGE, null, m);
    }

    static void walkTowardsTarget(TransportItemsBetweenContainers b, PathfinderMob m) {
        call(WALK, b, m);
    }

    static void startTravelling(TransportItemsBetweenContainers b, PathfinderMob m) {
        call(START_TRAVEL, b, m);
    }

    static void onTargetInteraction(TransportItemsBetweenContainers b, TransportItemTarget t, PathfinderMob m) {
        call(ON_INTERACT, b, t, m);
    }

    static void setTransportingState(TransportItemsBetweenContainers b, TransportItemState s) {
        call(SET_STATE, b, s);
    }

    static void setInteractionState(TransportItemsBetweenContainers b, ContainerInteractionState s) {
        call(SET_INTERACTION, b, s);
    }

    static void pickUpItems(TransportItemsBetweenContainers b, PathfinderMob m, Container c) {
        call(PICK_UP, b, m, c);
    }

    static void putDownItem(TransportItemsBetweenContainers b, PathfinderMob m, Container c) {
        call(PUT_DOWN, b, m, c);
    }

    private static Object call(Method method, Object receiver, Object... args) {
        try {
            return method.invoke(receiver, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field field(String name) {
        try {
            Field f = C.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("TransportItemsBetweenContainers has no field '" + name + "'", e);
        }
    }

    private static Method method(String name, Class<?>... params) {
        try {
            Method m = C.getDeclaredMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("TransportItemsBetweenContainers has no method '" + name + "'", e);
        }
    }
}
