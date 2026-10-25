package com.panda.fulldraw.shot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * What a player's arrow remembers about the shot that fired it. Saved with the arrow, never
 * synced: the client learns the outcome from the hit feedback.
 *
 * @param perfect     released inside the Perfect window
 * @param origin      where the arrow left the bow, for the range rules
 * @param weaponMult  the weapon's damage multiplier
 * @param gravityMult the weapon's gravity multiplier (a flatter arc below 1)
 * @param spread      the inaccuracy it was fired with (vanilla units)
 * @param weapon      the weapon's item id
 * @param precisionBonus added to a weak spot's multiplier when the arrow strikes one (the bow's Deadeye)
 */
public record ShotRecord(boolean perfect, Vec3 origin, double weaponMult, double gravityMult, double spread, Identifier weapon,
                         double precisionBonus) {
    public static final Codec<ShotRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("perfect").forGetter(ShotRecord::perfect),
            Vec3.CODEC.fieldOf("origin").forGetter(ShotRecord::origin),
            Codec.DOUBLE.fieldOf("weapon_mult").forGetter(ShotRecord::weaponMult),
            Codec.DOUBLE.fieldOf("gravity_mult").forGetter(ShotRecord::gravityMult),
            Codec.DOUBLE.fieldOf("spread").forGetter(ShotRecord::spread),
            Identifier.CODEC.fieldOf("weapon").forGetter(ShotRecord::weapon),
            Codec.DOUBLE.optionalFieldOf("precision_bonus", 0.0).forGetter(ShotRecord::precisionBonus)
    ).apply(i, ShotRecord::new));

    /** A shot from a weapon without Deadeye. */
    public ShotRecord(boolean perfect, Vec3 origin, double weaponMult, double gravityMult, double spread, Identifier weapon) {
        this(perfect, origin, weaponMult, gravityMult, spread, weapon, 0.0);
    }
}
