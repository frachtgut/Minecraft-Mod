package net.starfallen.util;

import net.starfallen.entity.CometRayEntity;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Side-safe bridge to client key state. On a dedicated server these stay at their defaults;
 * the client installs real suppliers during client setup.
 */
public final class ClientInput {
    public static BooleanSupplier JUMP = () -> false;
    public static BooleanSupplier SPRINT = () -> false;
    public static Consumer<CometRayEntity> RAY_BOOST = ray -> {};

    public static void onRayBoost(CometRayEntity ray) {
        RAY_BOOST.accept(ray);
    }

    private ClientInput() {}
}
