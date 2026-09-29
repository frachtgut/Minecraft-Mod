package net.starfallen.registry;

import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.starfallen.Starfallen;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Starfallen.MODID);

    // Meteors & events
    public static final RegistryObject<SoundEvent> METEOR_FALL = reg("meteor_fall");
    public static final RegistryObject<SoundEvent> METEOR_IMPACT = reg("meteor_impact");
    public static final RegistryObject<SoundEvent> STARFALL_BEGIN = reg("starfall_begin");
    public static final RegistryObject<SoundEvent> STAR_CHIME = reg("star_chime");
    // Star Wisp
    public static final RegistryObject<SoundEvent> WISP_AMBIENT = reg("wisp_ambient");
    public static final RegistryObject<SoundEvent> WISP_HURT = reg("wisp_hurt");
    public static final RegistryObject<SoundEvent> WISP_DEATH = reg("wisp_death");
    // Meteor Golem
    public static final RegistryObject<SoundEvent> GOLEM_AMBIENT = reg("golem_ambient");
    public static final RegistryObject<SoundEvent> GOLEM_HURT = reg("golem_hurt");
    public static final RegistryObject<SoundEvent> GOLEM_DEATH = reg("golem_death");
    public static final RegistryObject<SoundEvent> GOLEM_SLAM = reg("golem_slam");
    // Void Stalker
    public static final RegistryObject<SoundEvent> STALKER_AMBIENT = reg("stalker_ambient");
    public static final RegistryObject<SoundEvent> STALKER_SCREAM = reg("stalker_scream");
    public static final RegistryObject<SoundEvent> STALKER_HURT = reg("stalker_hurt");
    public static final RegistryObject<SoundEvent> STALKER_DEATH = reg("stalker_death");
    // Nebula Jelly
    public static final RegistryObject<SoundEvent> JELLY_AMBIENT = reg("jelly_ambient");
    public static final RegistryObject<SoundEvent> JELLY_ZAP = reg("jelly_zap");
    // Starseer
    public static final RegistryObject<SoundEvent> STARSEER_AMBIENT = reg("starseer_ambient");
    public static final RegistryObject<SoundEvent> STARSEER_CAST = reg("starseer_cast");
    public static final RegistryObject<SoundEvent> STARSEER_HURT = reg("starseer_hurt");
    public static final RegistryObject<SoundEvent> STARSEER_DEATH = reg("starseer_death");
    // Mimic
    public static final RegistryObject<SoundEvent> MIMIC_REVEAL = reg("mimic_reveal");
    public static final RegistryObject<SoundEvent> MIMIC_CHOMP = reg("mimic_chomp");
    // Comet Ray
    public static final RegistryObject<SoundEvent> RAY_AMBIENT = reg("ray_ambient");
    public static final RegistryObject<SoundEvent> RAY_BOOST = reg("ray_boost");
    // Astraeon
    public static final RegistryObject<SoundEvent> ASTRAEON_ROAR = reg("astraeon_roar");
    public static final RegistryObject<SoundEvent> ASTRAEON_AMBIENT = reg("astraeon_ambient");
    public static final RegistryObject<SoundEvent> ASTRAEON_HURT = reg("astraeon_hurt");
    public static final RegistryObject<SoundEvent> ASTRAEON_DEATH = reg("astraeon_death");
    public static final RegistryObject<SoundEvent> ASTRAEON_BEAM_CHARGE = reg("astraeon_beam_charge");
    public static final RegistryObject<SoundEvent> ASTRAEON_BEAM = reg("astraeon_beam");
    public static final RegistryObject<SoundEvent> ASTRAEON_SLAM = reg("astraeon_slam");
    public static final RegistryObject<SoundEvent> ASTRAEON_AWAKEN = reg("astraeon_awaken");
    public static final RegistryObject<SoundEvent> BOSS_MUSIC = reg("boss_music");
    // Gear
    public static final RegistryObject<SoundEvent> HAMMER_SLAM = reg("hammer_slam");
    public static final RegistryObject<SoundEvent> BLADE_DASH = reg("blade_dash");
    public static final RegistryObject<SoundEvent> RIFT_BLINK = reg("rift_blink");
    public static final RegistryObject<SoundEvent> SINGULARITY_HUM = reg("singularity_hum");
    public static final RegistryObject<SoundEvent> SINGULARITY_COLLAPSE = reg("singularity_collapse");
    public static final RegistryObject<SoundEvent> SCYTHE_THROW = reg("scythe_throw");
    public static final RegistryObject<SoundEvent> TETHER_FIRE = reg("tether_fire");
    public static final RegistryObject<SoundEvent> STAR_BOLT = reg("star_bolt");
    public static final RegistryObject<SoundEvent> TOTEM_NOVA = reg("totem_nova");
    public static final RegistryObject<SoundEvent> DOUBLE_JUMP = reg("double_jump");
    // Blocks
    public static final RegistryObject<SoundEvent> SPIKE_TRAP = reg("spike_trap");
    public static final RegistryObject<SoundEvent> FLAME_JET = reg("flame_jet");
    public static final RegistryObject<SoundEvent> SEAL_BREAK = reg("seal_break");
    public static final RegistryObject<SoundEvent> BRAZIER_IGNITE = reg("brazier_ignite");
    public static final RegistryObject<SoundEvent> TELESCOPE_GAZE = reg("telescope_gaze");
    public static final RegistryObject<SoundEvent> EGG_HATCH = reg("egg_hatch");
    public static final RegistryObject<SoundEvent> CRYSTAL_BREAK = reg("crystal_break");

    public static final ForgeSoundType STARLIT_CRYSTAL_SOUND = new ForgeSoundType(1.0F, 1.0F,
            CRYSTAL_BREAK, () -> net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_STEP,
            () -> net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_PLACE,
            () -> net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_HIT,
            () -> net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_FALL);

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Starfallen.id(name)));
    }

    private ModSounds() {}
}
