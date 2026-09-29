package net.starfallen.client.model;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;
import net.starfallen.client.model.gen.NebulaJellyGeometry;
import net.starfallen.entity.NebulaJellyEntity;

public class NebulaJellyModel extends HierarchicalModel<NebulaJellyEntity> {
    private final ModelPart root, bell, core;
    private final ModelPart[] tentacles = new ModelPart[8];
    private final ModelPart[] segs = new ModelPart[8];
    private final ModelPart[] tips = new ModelPart[8];
    private final ModelPart arm0, arm1;
    private float partial;

    public NebulaJellyModel(ModelPart root) {
        this.root = root;
        this.bell = root.getChild("bell");
        this.core = bell.getChild("core");
        for (int i = 0; i < 8; i++) {
            tentacles[i] = bell.getChild("tentacle" + i);
            segs[i] = tentacles[i].getChild("seg");
            tips[i] = segs[i].getChild("tip");
        }
        this.arm0 = bell.getChild("arm0");
        this.arm1 = bell.getChild("arm1");
    }

    public static LayerDefinition createBodyLayer() {
        return NebulaJellyGeometry.create();
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void prepareMobModel(NebulaJellyEntity entity, float limbSwing, float limbAmount, float partialTick) {
        this.partial = partialTick;
    }

    @Override
    public void setupAnim(NebulaJellyEntity e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        // Pulse: the bell squeezes and the tentacles trail behind the thrust
        float p = e.pulseTimer > 0 ? (e.pulseTimer - partial) / 20.0F : 0.0F;
        float squeeze = Mth.sin(p * Mth.PI);
        bell.xScale = bell.zScale = 1.0F - 0.18F * squeeze + 0.03F * Mth.sin(age * 0.1F);
        bell.yScale = 1.0F + 0.15F * squeeze;
        bell.y += Mth.sin(age * 0.06F) * 0.6F;
        core.xScale = core.yScale = core.zScale = 1.0F + 0.1F * Mth.sin(age * 0.2F) + 0.3F * (e.zapTimer > 0 ? 1 : 0);
        for (int i = 0; i < 8; i++) {
            float phase = i * 0.8F;
            float sway = Mth.sin(age * 0.09F + phase) * 0.18F;
            float angle = i * Mth.PI / 4;
            tentacles[i].xRot = Mth.sin(angle) * (0.15F + squeeze * 0.35F) + sway;
            tentacles[i].zRot = -Mth.cos(angle) * (0.15F + squeeze * 0.35F) + sway * 0.5F;
            segs[i].xRot = Mth.sin(age * 0.11F + phase + 1) * 0.25F;
            tips[i].xRot = Mth.sin(age * 0.13F + phase + 2) * 0.35F;
            if (e.zapTimer > 0) tips[i].zRot = Mth.sin(age * 3 + phase) * 0.3F;
        }
        arm0.zRot = Mth.sin(age * 0.07F) * 0.25F;
        arm1.xRot = Mth.sin(age * 0.07F + 1.5F) * 0.25F;
    }
}
