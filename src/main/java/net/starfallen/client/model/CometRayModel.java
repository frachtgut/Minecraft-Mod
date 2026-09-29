package net.starfallen.client.model;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;
import net.starfallen.client.model.gen.CometRayGeometry;
import net.starfallen.entity.CometRayEntity;

public class CometRayModel extends HierarchicalModel<CometRayEntity> {
    private final ModelPart root, body, wingL, wingLTip, wingR, wingRTip, tail, tailMid, tailTip, saddle;
    private float partial;

    public CometRayModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.wingL = body.getChild("wing_l");
        this.wingLTip = wingL.getChild("wing_l_tip");
        this.wingR = body.getChild("wing_r");
        this.wingRTip = wingR.getChild("wing_r_tip");
        this.tail = body.getChild("tail");
        this.tailMid = tail.getChild("tail_mid");
        this.tailTip = tailMid.getChild("tail_tip");
        this.saddle = body.getChild("saddle");
    }

    public static LayerDefinition createBodyLayer() {
        return CometRayGeometry.create();
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void prepareMobModel(CometRayEntity entity, float limbSwing, float limbAmount, float partialTick) {
        this.partial = partialTick;
    }

    @Override
    public void setupAnim(CometRayEntity ray, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float flap = Mth.lerp(partial, ray.prevFlap, ray.flap);
        float amp = ray.isBoosting() ? 0.25F : 0.5F;
        float w = Mth.sin(flap) * amp;
        wingL.zRot = -w;
        wingR.zRot = w;
        wingLTip.zRot = -Mth.sin(flap - 0.9F) * amp * 0.9F;
        wingRTip.zRot = Mth.sin(flap - 0.9F) * amp * 0.9F;
        body.y += Mth.cos(flap) * 0.8F;
        body.xRot = ray.getXRot() * Mth.DEG_TO_RAD * 0.6F;
        tail.yRot = Mth.sin(age * 0.1F) * 0.2F;
        tail.xRot = Mth.sin(flap - 1.5F) * 0.12F;
        tailMid.yRot = Mth.sin(age * 0.1F - 0.8F) * 0.3F;
        tailTip.yRot = Mth.sin(age * 0.1F - 1.6F) * 0.3F;
        saddle.visible = ray.isTame();
    }
}
