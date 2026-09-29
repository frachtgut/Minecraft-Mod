package net.starfallen.client.model;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;
import net.starfallen.client.model.gen.VoidStalkerGeometry;
import net.starfallen.entity.VoidStalkerEntity;

/** The stalker's own animation clock stops while it is observed, freezing it mid-pose. */
public class VoidStalkerModel extends HierarchicalModel<VoidStalkerEntity> {
    private final ModelPart root, torso, head, rightArm, leftArm, rightLeg, leftLeg;
    private float partial;

    public VoidStalkerModel(ModelPart root) {
        this.root = root;
        this.torso = root.getChild("torso");
        this.head = torso.getChild("head");
        this.rightArm = torso.getChild("right_arm");
        this.leftArm = torso.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        return VoidStalkerGeometry.create();
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void prepareMobModel(VoidStalkerEntity entity, float limbSwing, float limbAmount, float partialTick) {
        this.partial = partialTick;
    }

    @Override
    public void setupAnim(VoidStalkerEntity s, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        float clock = s.getAnimClock(partial);
        // Hunched, predatory posture
        torso.xRot = 0.32F;
        head.xRot = -0.25F + headPitch * Mth.DEG_TO_RAD * 0.7F;
        head.yRot = headYaw * Mth.DEG_TO_RAD;
        head.zRot = Mth.sin(clock * 0.07F) * 0.18F;
        float walk = Mth.cos(limbSwing * 0.55F) * limbAmount;
        rightLeg.xRot = walk * 1.2F;
        leftLeg.xRot = -walk * 1.2F;
        rightArm.xRot = -0.35F - walk * 0.6F + Mth.sin(clock * 0.09F) * 0.06F;
        leftArm.xRot = -0.35F + walk * 0.6F + Mth.sin(clock * 0.09F + 1.3F) * 0.06F;
        rightArm.zRot = 0.12F + Mth.sin(clock * 0.13F) * 0.04F;
        leftArm.zRot = -0.12F - Mth.sin(clock * 0.11F) * 0.04F;
        if (attackTime > 0) {
            float a = Mth.sin(attackTime * Mth.PI);
            rightArm.xRot = -2.2F * a - 0.3F;
            leftArm.xRot = -1.8F * a - 0.3F;
            head.xRot -= 0.4F * a;
        }
        // Occasional unsettling twitch
        if (!s.isObserved() && Mth.sin(clock * 0.31F) > 0.97F) {
            head.zRot += 0.5F;
        }
    }
}
