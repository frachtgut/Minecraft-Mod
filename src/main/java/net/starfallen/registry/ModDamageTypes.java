package net.starfallen.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.starfallen.Starfallen;
import org.jetbrains.annotations.Nullable;

/** Data-driven damage types (see data/starfallen/damage_type). */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> SPIKES = key("spikes");
    public static final ResourceKey<DamageType> STARFIRE = key("starfire");
    public static final ResourceKey<DamageType> METEOR = key("meteor");
    public static final ResourceKey<DamageType> STAR_BOLT = key("star_bolt");
    public static final ResourceKey<DamageType> VOID_REND = key("void_rend");
    public static final ResourceKey<DamageType> ASTRAL_BEAM = key("astral_beam");
    public static final ResourceKey<DamageType> SHOCKWAVE = key("shockwave");

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Starfallen.id(name));
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> key) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key));
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> key, @Nullable Entity direct, @Nullable Entity causing) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key), direct, causing);
    }

    private ModDamageTypes() {}
}
