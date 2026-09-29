package net.starfallen.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.RenderType;
import net.starfallen.client.model.gen.TetherHookGeometry;

public class TetherHookModel extends SimplePartModel {
    public TetherHookModel(ModelPart root) {
        super(root, RenderType::entityCutoutNoCull);
    }

    public static LayerDefinition createBodyLayer() {
        return TetherHookGeometry.create();
    }
}
