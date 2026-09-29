package net.starfallen.registry;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.starfallen.Starfallen;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Starfallen.MODID);

    /** Golden four-pointed twinkle. */
    public static final RegistryObject<SimpleParticleType> STAR_SPARK = reg("star_spark");
    /** Cyan astral twinkle. */
    public static final RegistryObject<SimpleParticleType> ASTRAL_SPARK = reg("astral_spark");
    /** Violet void mote. */
    public static final RegistryObject<SimpleParticleType> VOID_SPARK = reg("void_spark");
    /** Glowing meteor ember. */
    public static final RegistryObject<SimpleParticleType> EMBER = reg("ember");
    /** Heavy dark smoke used by meteor trails. */
    public static final RegistryObject<SimpleParticleType> METEOR_SMOKE = reg("meteor_smoke");
    /** Flat expanding ground ring. */
    public static final RegistryObject<SimpleParticleType> SHOCKWAVE = reg("shockwave");
    /** Flat expanding violet ring. */
    public static final RegistryObject<SimpleParticleType> VOID_SHOCKWAVE = reg("void_shockwave");
    /** Big bright flash. */
    public static final RegistryObject<SimpleParticleType> NOVA_FLASH = reg("nova_flash");
    /** Rising arcane glyph. */
    public static final RegistryObject<SimpleParticleType> RUNE = reg("rune");

    private static RegistryObject<SimpleParticleType> reg(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(true));
    }

    private ModParticles() {}
}
