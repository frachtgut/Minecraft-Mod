package net.starfallen.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.RenderType;
import net.starfallen.client.model.gen.MeteorGeometry;
import net.starfallen.client.model.gen.MoltenRockGeometry;

public class MeteorModel extends SimplePartModel {
    public MeteorModel(ModelPart root) {
        super(root, RenderType::entityCutoutNoCull);
    }

    public static LayerDefinition createBodyLayer() {
        return MeteorGeometry.create();
    }

    public static LayerDefinition createSmallLayer() {
        return MoltenRockGeometry.create();
    }
}
