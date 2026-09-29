package net.starfallen.client.model;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;
import net.starfallen.client.model.gen.MeteorGolemGeometry;
import net.starfallen.entity.MeteorGolemEntity;

public class MeteorGolemModel extends HierarchicalModel<MeteorGolemEntity> {
    private final ModelPart root, torso, head, rightArm, leftArm, rightLeg, leftLeg, crystals;
    private float partial;

    public MeteorGolemModel(ModelPart root) {
        this.root = root;
        this.torso = root.getChild("torso");
        this.head = torso.getChild("head");
        this.rightArm = torso.getChild("right_arm");
        this.leftArm = torso.getChild("left_arm");
        this.crystals = torso.getChild("crystals");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        return MeteorGolemGeometry.create();
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void prepareMobModel(MeteorGolemEntity entity, float limbSwing, float limbAmount, float partialTick) {
        this.partial = partialTick;
    }

    @Override
    public void setupAnim(MeteorGolemEntity golem, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        head.yRot = headYaw * Mth.DEG_TO_RAD * 0.6F;
        head.xRot = headPitch * Mth.DEG_TO_RAD * 0.5F;
        float walk = Mth.cos(limbSwing * 0.45F) * limbAmount;
        rightLeg.xRot = walk * 1.1F;
        leftLeg.xRot = -walk * 1.1F;
        rightArm.xRot = -walk * 0.9F;
        leftArm.xRot = walk * 0.9F;
        torso.zRot = Mth.cos(limbSwing * 0.45F) * limbAmount * 0.06F;
        // Idle breathing of the molten core
        torso.y += Mth.sin(age * 0.08F) * 0.4F;
        crystals.xRot = Mth.sin(age * 0.05F) * 0.03F;

        int state = golem.getState();
        float t = golem.getStateTicks() + partial;
        if (state == MeteorGolemEntity.STATE_POUND) {
            float wind = Mth.clamp(t / MeteorGolemEntity.POUND_WINDUP, 0, 1);
            float lift = -2.8F * wind;
            rightArm.xRot = lift;
            leftArm.xRot = lift;
            rightArm.zRot = 0.3F * wind;
            leftArm.zRot = -0.3F * wind;
            torso.xRot = -0.25F * wind;
        } else if (state == MeteorGolemEntity.STATE_THROW) {
            float wind = Mth.clamp(t / MeteorGolemEntity.THROW_WINDUP, 0, 1);
            rightArm.xRot = -2.6F * wind;
            rightArm.zRot = -0.2F;
            torso.yRot = -0.35F * wind;
        } else if (state == MeteorGolemEntity.STATE_EMERGE) {
            float e = Mth.clamp(t / MeteorGolemEntity.EMERGE_TIME, 0, 1);
            root.y += (1 - e) * 36F;
            rightArm.xRot = -2.0F * (1 - e) - 0.5F * Mth.sin(t * 0.6F) * (1 - e);
            leftArm.xRot = -2.0F * (1 - e) + 0.5F * Mth.sin(t * 0.6F) * (1 - e);
            head.xRot = -0.6F * (1 - e);
        }
        if (golem.attackAnim > 0) {
            float a = (golem.attackAnim - partial) / 10.0F;
            rightArm.xRot = -2.0F + 2.4F * (1 - a);
        }
    }
}
