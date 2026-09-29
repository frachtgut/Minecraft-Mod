package net.starfallen.client.model;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;
import net.starfallen.client.model.gen.StarseerGeometry;
import net.starfallen.entity.StarseerEntity;

public class StarseerModel extends HierarchicalModel<StarseerEntity> {
    private final ModelPart root, head, halo, body, skirt, rightArm, leftArm;

    public StarseerModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.halo = head.getChild("halo");
        this.body = root.getChild("body");
        this.skirt = root.getChild("skirt");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
    }

    public static LayerDefinition createBodyLayer() {
        return StarseerGeometry.create();
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(StarseerEntity seer, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        head.yRot = headYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        halo.zRot = age * 0.04F;
        float walk = Mth.cos(limbSwing * 0.6F) * limbAmount;
        skirt.xRot = walk * 0.08F;
        skirt.zRot = Mth.sin(age * 0.05F) * 0.02F;
        rightArm.xRot = walk * 0.5F;
        leftArm.xRot = -walk * 0.5F;
        // Robes sway, the seer hovers a little when casting
        if (seer.getSpell() != StarseerEntity.SPELL_NONE) {
            float c = Mth.sin(age * 0.6F) * 0.1F;
            rightArm.xRot = -2.6F + c;
            leftArm.xRot = -2.6F - c;
            rightArm.zRot = -0.35F;
            leftArm.zRot = 0.35F;
            root.y -= 2.0F + Mth.sin(age * 0.2F);
            halo.zRot = age * 0.3F;
        } else {
            rightArm.zRot = 0.08F + Mth.sin(age * 0.07F) * 0.04F;
            leftArm.zRot = -0.08F - Mth.sin(age * 0.07F) * 0.04F;
        }
        body.xRot = 0.03F;
    }
}
