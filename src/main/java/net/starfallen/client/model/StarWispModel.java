package net.starfallen.client.model;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;
import net.starfallen.client.model.gen.StarWispGeometry;
import net.starfallen.entity.StarWispEntity;

public class StarWispModel extends HierarchicalModel<StarWispEntity> {
    private final ModelPart root, body, halo, orbit, tail, tail2, tail3;

    public StarWispModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.halo = root.getChild("halo");
        this.orbit = root.getChild("orbit");
        this.tail = root.getChild("tail");
        this.tail2 = tail.getChild("tail2");
        this.tail3 = tail2.getChild("tail3");
    }

    public static LayerDefinition createBodyLayer() {
        return StarWispGeometry.create();
    }

    private float tr = 1, tg = 1, tb = 1;

    public void tint(float r, float g, float b) {
        this.tr = r;
        this.tg = g;
        this.tb = b;
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, int light, int overlay,
                               float r, float g, float b, float a) {
        root.render(pose, buffer, light, overlay, r * tr, g * tg, b * tb, a);
    }

    @Override
    public void setupAnim(StarWispEntity entity, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float bob = Mth.sin(age * 0.12F) * 1.2F;
        body.y += bob;
        halo.y += bob;
        orbit.y += bob;
        tail.y += bob;
        body.yRot = headYaw * Mth.DEG_TO_RAD;
        body.xRot = headPitch * Mth.DEG_TO_RAD;
        float pulse = 1.0F + Mth.sin(age * 0.25F) * 0.08F;
        halo.xScale = halo.yScale = halo.zScale = pulse;
        orbit.yRot = age * 0.12F;
        orbit.xRot = Mth.sin(age * 0.05F) * 0.3F;
        tail.yRot = Mth.sin(age * 0.2F) * 0.35F;
        tail.xRot = 0.2F + Mth.cos(age * 0.17F) * 0.15F;
        tail2.yRot = Mth.sin(age * 0.2F - 0.8F) * 0.4F;
        tail3.yRot = Mth.sin(age * 0.2F - 1.6F) * 0.5F;
    }
}
