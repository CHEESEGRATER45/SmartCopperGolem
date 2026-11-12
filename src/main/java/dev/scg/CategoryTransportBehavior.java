package dev.scg;

import net.minecraft.world.Container;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers;
import net.minecraft.world.level.Level;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The vanilla copper golem item-transport behaviour with one change: when the golem reaches a
 * container carrying an item, "does this container accept my item?" is answered by
 * {@link CategoryMatcher} instead of vanilla's same-item test.
 *
 * Everything else (finding targets, pathfinding, queueing, animations, sounds, chest lids, cooldowns)
 * is inherited unchanged. Only the two protected methods that contain the match decision are
 * overridden, and they copy vanilla's logic step by step.
 */
public final class CategoryTransportBehavior extends TransportItemsBetweenContainers {

    private final Supplier<CategoryMatcher> matcher;

    private CategoryTransportBehavior(TibcAccess.Params p, Supplier<CategoryMatcher> matcher) {
        super(p.speed(), p.source(), p.destination(), p.horizontal(), p.vertical(),
                p.interactions(), p.onStartTravelling(), p.shouldQueue());
        this.matcher = matcher;
    }

    /** Throws if the server's TransportItemsBetweenContainers doesn't have the members we rely on. */
    public static void verify() {
        TibcAccess.verify();
    }

    /** A copy of {@code vanilla} (same speed, search ranges and animations) that uses category matching. */
    public static CategoryTransportBehavior from(TransportItemsBetweenContainers vanilla, Supplier<CategoryMatcher> matcher) {
        return new CategoryTransportBehavior(TibcAccess.Params.read(vanilla), matcher);
    }

    /** A plain vanilla copy of {@code behavior}, used to undo the patch. */
    public static TransportItemsBetweenContainers toVanilla(TransportItemsBetweenContainers behavior) {
        return TibcAccess.Params.read(behavior).toVanilla();
    }

    // Vanilla: if close enough to queue and another mob is using the target -> queue;
    // else if inside interaction range -> start the interaction; else keep walking.
    @Override
    protected void onTravelToTarget(TransportItemTarget target, Level level, PathfinderMob mob) {
        if (TibcAccess.within(this, 3.0, target, level, mob) && TibcAccess.anotherMobInteracting(this, target, level)) {
            TibcAccess.startQueuing(this, mob);
        } else if (TibcAccess.within(this, TibcAccess.interactionRange(mob), target, level, mob)) {
            startInteraction(target, mob);
        } else {
            TibcAccess.walkTowardsTarget(this, mob);
        }
    }

    // Vanilla: while standing at the target, count ticks and play the interaction animation;
    // after TARGET_INTERACTION_TIME ticks do the actual pick up / put down.
    @Override
    protected void onReachedTarget(TransportItemTarget target, Level level, PathfinderMob mob) {
        if (!TibcAccess.within(this, 2.0, target, level, mob)) {
            TibcAccess.startTravelling(this, mob);
            return;
        }
        TibcAccess.setTicks(this, TibcAccess.ticks(this) + 1);
        TibcAccess.onTargetInteraction(this, target, mob);
        if (TibcAccess.ticks(this) >= TARGET_INTERACTION_TIME) {
            resolve(mob, target.container(),
                    (m, c) -> TibcAccess.pickUpItems(this, m, c),
                    (m, c) -> stopTargetingCurrentTarget(mob),
                    (m, c) -> TibcAccess.putDownItem(this, m, c),
                    (m, c) -> stopTargetingCurrentTarget(mob));
            TibcAccess.startTravelling(this, mob);
        }
    }

    // Vanilla startOnReachedTargetInteraction: pick which animation/interaction applies.
    private void startInteraction(TransportItemTarget target, PathfinderMob mob) {
        resolve(mob, target.container(),
                (m, c) -> TibcAccess.setInteractionState(this, ContainerInteractionState.PICKUP_ITEM),
                (m, c) -> TibcAccess.setInteractionState(this, ContainerInteractionState.PICKUP_NO_ITEM),
                (m, c) -> TibcAccess.setInteractionState(this, ContainerInteractionState.PLACE_ITEM),
                (m, c) -> TibcAccess.setInteractionState(this, ContainerInteractionState.PLACE_NO_ITEM));
        TibcAccess.setTransportingState(this, TransportItemState.INTERACTING);
    }

    // Vanilla doReachedTargetInteraction, with the "leaving" test swapped for ours.
    private void resolve(PathfinderMob mob, Container container,
                         BiConsumer<PathfinderMob, Container> pickUp,
                         BiConsumer<PathfinderMob, Container> pickUpNothing,
                         BiConsumer<PathfinderMob, Container> putDown,
                         BiConsumer<PathfinderMob, Container> putDownNothing) {
        if (mob.getMainHandItem().isEmpty()) {
            // Not carrying anything: this is a pick-up visit.
            if (!container.isEmpty()) {
                pickUp.accept(mob, container);
            } else {
                pickUpNothing.accept(mob, container);
            }
        } else if (matcher.get().canDeposit(container, mob.getMainHandItem())) {
            putDown.accept(mob, container);
        } else {
            putDownNothing.accept(mob, container);
        }
    }
}
