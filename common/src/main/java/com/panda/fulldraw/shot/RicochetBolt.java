package com.panda.fulldraw.shot;

/** Implemented by every arrow through {@code AbstractArrowMixin}: the bounces a Ricochet crossbow gave its bolt. */
public interface RicochetBolt {
    int fulldraw$ricochets();

    void fulldraw$setRicochets(int bounces);
}
