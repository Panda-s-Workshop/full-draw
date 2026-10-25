package com.panda.fulldraw.entity;

import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * The arrows stuck in a living creature, oldest first: one per arrow vanilla counts in it, with the chance each comes
 * back when the creature dies. One its shooter didn't pay for holds its place with nothing to give back, so the
 * oldest really go first as vanilla's count falls. Kept in memory only, as vanilla's own count is.
 */
public interface StuckArrows {
    /** One arrow, as its pickup item (empty when no one paid for it), and its head's recovery at the default chance. */
    record Stuck(ItemStack arrow, double recovery) {}

    void fulldraw$stick(Stuck arrow);

    List<Stuck> fulldraw$stuck();
}
