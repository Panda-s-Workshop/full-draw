package com.panda.fulldraw.shot;

import org.jetbrains.annotations.Nullable;

/** Implemented by every arrow through {@code AbstractArrowMixin}: the shot record a player's arrow carries. */
public interface ShotHolder {
    @Nullable ShotRecord fulldraw$shot();

    void fulldraw$setShot(@Nullable ShotRecord shot);

    /** On a client, which has no shot record: the arrow's gravity multiplier, as the server sent it. */
    void fulldraw$setClientGravity(float mult);
}
