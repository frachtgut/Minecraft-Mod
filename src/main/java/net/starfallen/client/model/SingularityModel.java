package net.starfallen.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.RenderType;
import net.starfallen.client.model.gen.SingularityGeometry;

public class SingularityModel extends SimplePartModel {
    public SingularityModel(ModelPart root) {
        super(root, RenderType::entityTranslucent);
    }

    public static LayerDefinition createBodyLayer() {
        return SingularityGeometry.create();
    }
}
