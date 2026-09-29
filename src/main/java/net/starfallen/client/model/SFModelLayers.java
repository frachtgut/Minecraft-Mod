package net.starfallen.client.model;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.starfallen.Starfallen;

public final class SFModelLayers {
    public static final ModelLayerLocation STAR_WISP = layer("star_wisp");
    public static final ModelLayerLocation METEOR_GOLEM = layer("meteor_golem");
    public static final ModelLayerLocation VOID_STALKER = layer("void_stalker");
    public static final ModelLayerLocation NEBULA_JELLY = layer("nebula_jelly");
    public static final ModelLayerLocation STARSEER = layer("starseer");
    public static final ModelLayerLocation MIMIC_CHEST = layer("astral_mimic_chest");
    public static final ModelLayerLocation MIMIC_INNARDS = layer("astral_mimic_innards");
    public static final ModelLayerLocation COMET_RAY = layer("comet_ray");
    public static final ModelLayerLocation ASTRAEON = layer("astraeon");
    public static final ModelLayerLocation METEOR = layer("meteor");
    public static final ModelLayerLocation MOLTEN_ROCK = layer("molten_rock");
    public static final ModelLayerLocation TETHER_HOOK = layer("tether_hook");
    public static final ModelLayerLocation SINGULARITY = layer("singularity");
    public static final ModelLayerLocation SENTINEL_EYE = layer("sentinel_eye");
    public static final ModelLayerLocation HALO = layer("halo");

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(Starfallen.id(name), "main");
    }

    private SFModelLayers() {}
}
