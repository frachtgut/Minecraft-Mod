package net.starfallen.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;
import net.starfallen.client.model.gen.AstralMimicInnardsGeometry;
import net.starfallen.entity.AstralMimicEntity;

/**
 * Two models driven by the same animation: the "chest" (vanilla chest UV layout, rendered with
 * the real chest texture so the disguise is perfect) and the "innards" (teeth, tongue, eyes and
 * legs) that only show up once the mimic wakes.
 */
public class AstralMimicModel extends EntityModel<AstralMimicEntity> {
    private final ModelPart root, base, lid;
    private final ModelPart innards, baseIn, lidIn, tongue;
    private final ModelPart[] legs = new ModelPart[4];

    public AstralMimicModel(ModelPart chestRoot, ModelPart innardsRoot) {
        this.root = chestRoot;
        this.base = chestRoot.getChild("base");
        this.lid = chestRoot.getChild("lid");
        this.innards = innardsRoot;
        this.baseIn = innardsRoot.getChild("base_in");
        this.lidIn = innardsRoot.getChild("lid_in");
        this.tongue = baseIn.getChild("tongue");
        for (int i = 0; i < 4; i++) legs[i] = innardsRoot.getChild("leg" + i);
    }

    /** Exactly the vanilla chest geometry and UVs. */
    public static LayerDefinition createChestLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 19).addBox(-7.0F, -10.0F, -7.0F, 14.0F, 10.0F, 14.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        PartDefinition lid = root.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -5.0F, -14.0F, 14.0F, 5.0F, 14.0F),
                PartPose.offset(0.0F, 14.0F, 7.0F));
        lid.addOrReplaceChild("lock", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -2.0F, -15.0F, 2.0F, 4.0F, 1.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition createInnardsLayer() {
        return AstralMimicInnardsGeometry.create();
    }

    @Override
    public void setupAnim(AstralMimicEntity mimic, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
    }

    /** Applies the lid/leg animation for the given partial tick. */
    public void animate(AstralMimicEntity mimic, float partial, float age) {
        root.getAllParts().forEach(ModelPart::resetPose);
        innards.getAllParts().forEach(ModelPart::resetPose);
        float open = Mth.lerp(partial, mimic.prevLidOpen, mimic.lidOpen);
        float angle = -open * 1.25F;
        lid.xRot = angle;
        lidIn.xRot = angle;
        boolean awake = mimic.isAwake();
        float bob = awake ? Mth.abs(Mth.sin(age * 0.35F)) * 1.2F : 0;
        base.y -= bob;
        lid.y -= bob;
        baseIn.y -= bob;
        lidIn.y -= bob;
        tongue.xRot = -0.2F - open * 0.5F + Mth.sin(age * 0.5F) * 0.15F;
        tongue.visible = awake;
        for (int i = 0; i < 4; i++) {
            legs[i].visible = awake;
            legs[i].xRot = Mth.sin(age * 0.6F + i * 1.7F) * 0.5F;
        }
        innards.visible = open > 0.02F || awake;
    }

    public boolean innardsVisible() {
        return innards.visible;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {
        root.render(pose, buffer, light, overlay, r, g, b, a);
    }

    public void renderInnards(PoseStack pose, VertexConsumer buffer, int light, int overlay) {
        innards.render(pose, buffer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
    }
}
