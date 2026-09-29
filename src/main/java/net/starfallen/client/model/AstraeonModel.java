package net.starfallen.client.model;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;
import net.starfallen.client.model.gen.AstraeonGeometry;
import net.starfallen.entity.boss.AstraeonEntity;

/**
 * Astraeon: a burning star-core caged in obsidian plates, a crowned mask with three eyes, two
 * vast floating hands, orbiting rings and crystal shards. Each attack has its own pose.
 */
public class AstraeonModel extends HierarchicalModel<AstraeonEntity> {
    private final ModelPart root, core, shell, mask, leftHand, rightHand, ringA, ringB, shards, streamers;
    private final ModelPart[] plates;
    private final ModelPart[] shardParts = new ModelPart[6];
    private final ModelPart[] streamerParts = new ModelPart[3];
    private float partial;

    public AstraeonModel(ModelPart root) {
        this.root = root;
        this.core = root.getChild("core");
        this.shell = root.getChild("shell");
        this.mask = root.getChild("mask");
        this.leftHand = root.getChild("left_hand");
        this.rightHand = root.getChild("right_hand");
        this.ringA = root.getChild("ring_a");
        this.ringB = root.getChild("ring_b");
        this.shards = root.getChild("shards");
        this.streamers = root.getChild("streamers");
        this.plates = new ModelPart[]{shell.getChild("plate_front"), shell.getChild("plate_back"), shell.getChild("plate_left"),
                shell.getChild("plate_right"), shell.getChild("plate_top"), shell.getChild("plate_bottom")};
        for (int i = 0; i < 6; i++) shardParts[i] = shards.getChild("shard" + i);
        for (int i = 0; i < 3; i++) streamerParts[i] = streamers.getChild("streamer" + i);
    }

    public static LayerDefinition createBodyLayer() {
        return AstraeonGeometry.create();
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void prepareMobModel(AstraeonEntity entity, float limbSwing, float limbAmount, float partialTick) {
        this.partial = partialTick;
    }

    @Override
    public void setupAnim(AstraeonEntity boss, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        int state = boss.getState();
        float t = boss.stateTicks + partial;
        boolean enraged = boss.isEnraged();

        // Idle life
        float breathe = Mth.sin(age * 0.07F);
        core.xScale = core.yScale = core.zScale = 1.0F + breathe * 0.05F + (enraged ? 0.12F : 0);
        core.yRot = age * 0.02F;
        mask.xRot = headPitch * Mth.DEG_TO_RAD * 0.5F;
        mask.yRot = headYaw * Mth.DEG_TO_RAD * 0.3F;
        mask.y += breathe * 0.4F;
        ringA.yRot = age * (enraged ? 0.09F : 0.05F);
        ringB.yRot = -age * (enraged ? 0.06F : 0.035F);
        ringB.zRot += Mth.sin(age * 0.02F) * 0.2F;
        shards.yRot = age * 0.03F;
        for (int i = 0; i < 6; i++) shardParts[i].y += Mth.sin(age * 0.1F + i) * 1.2F;
        for (int i = 0; i < 3; i++) {
            streamerParts[i].xRot = Mth.sin(age * 0.08F + i) * 0.25F + 0.1F;
            streamerParts[i].zRot = Mth.cos(age * 0.06F + i * 2) * 0.15F;
        }
        float hover = Mth.sin(age * 0.09F) * 1.5F;
        leftHand.y += hover;
        rightHand.y -= hover;
        leftHand.zRot = -0.15F + Mth.sin(age * 0.05F) * 0.05F;
        rightHand.zRot = 0.15F - Mth.sin(age * 0.05F) * 0.05F;

        // The shell shatters in phase two
        for (ModelPart p : plates) p.visible = !enraged;
        if (state == AstraeonEntity.PHASE_SHIFT && t > 20 && t < 40) {
            float burst = (t - 20) / 20.0F;
            for (ModelPart p : plates) {
                p.visible = true;
                p.x *= 1 + burst * 2;
                p.y *= 1 + burst * 2;
                p.z *= 1 + burst * 2;
            }
            plates[0].z -= burst * 10;
            plates[1].z += burst * 10;
            plates[2].x += burst * 10;
            plates[3].x -= burst * 10;
            plates[4].y -= burst * 10;
            plates[5].y += burst * 10;
        }

        switch (state) {
            case AstraeonEntity.INTRO -> {
                float rise = Mth.clamp((t - 60) / 70.0F, 0, 1);
                leftHand.x += (1 - rise) * 4;
                rightHand.x -= (1 - rise) * 4;
                if (t > AstraeonEntity.ROAR_AT - 10) roar(Mth.clamp((t - AstraeonEntity.ROAR_AT + 10) / 15.0F, 0, 1));
            }
            case AstraeonEntity.VOLLEY -> {
                float up = Mth.clamp(t / 10.0F, 0, 1);
                leftHand.y -= 8 * up;
                rightHand.y -= 8 * up;
                leftHand.x += 3 * up;
                rightHand.x -= 3 * up;
                leftHand.xRot = -1.2F * up;
                rightHand.xRot = -1.2F * up;
            }
            case AstraeonEntity.SLAM -> {
                float raise = Mth.clamp(t / 20.0F, 0, 1);
                float strike = t > 30 ? Mth.clamp((t - 30) / 8.0F, 0, 1) : 0;
                leftHand.y -= 10 * raise - 16 * strike;
                rightHand.y -= 10 * raise - 16 * strike;
                leftHand.x -= 5 * raise;
                rightHand.x += 5 * raise;
                leftHand.xRot = 0.8F * strike;
                rightHand.xRot = 0.8F * strike;
                if (t > 41 && t < 76) {
                    // Stunned: slumped and flickering
                    mask.xRot += 0.35F;
                    core.xScale = core.yScale = core.zScale = 0.9F + Mth.sin(age * 1.5F) * 0.05F;
                }
            }
            case AstraeonEntity.METEOR_RAIN, AstraeonEntity.PHASE_SHIFT -> roar(Mth.clamp(t / 12.0F, 0, 1));
            case AstraeonEntity.CHARGE -> {
                float wind = Mth.clamp(t / 18.0F, 0, 1);
                leftHand.z += 8 * wind;
                rightHand.z += 8 * wind;
                mask.xRot -= 0.3F * wind;
            }
            case AstraeonEntity.VOID_BEAM -> {
                float charge = Mth.clamp(t / AstraeonEntity.BEAM_CHARGE, 0, 1);
                leftHand.x += 5 * charge;
                rightHand.x -= 5 * charge;
                leftHand.zRot -= 0.6F * charge;
                rightHand.zRot += 0.6F * charge;
                core.xScale = core.yScale = core.zScale = 1.0F + 0.3F * charge + (boss.isBeamFiring() ? Mth.sin(age * 2) * 0.05F : 0);
            }
            case AstraeonEntity.SINGULARITY -> {
                float c = Mth.clamp(t / 10.0F, 0, 1);
                leftHand.z -= 6 * c;
                rightHand.z -= 6 * c;
                leftHand.x -= 4 * c;
                rightHand.x += 4 * c;
            }
            case AstraeonEntity.NOVA -> {
                float c = Mth.clamp(t / 80.0F, 0, 1);
                leftHand.x += 7 * c;
                rightHand.x -= 7 * c;
                leftHand.y -= 6 * c;
                rightHand.y -= 6 * c;
                core.xScale = core.yScale = core.zScale = 1.0F + 0.7F * c;
                ringA.xScale = ringA.zScale = 1.0F + 0.4F * c;
                ringB.xScale = ringB.zScale = 1.0F + 0.4F * c;
            }
            case AstraeonEntity.DYING -> {
                float d = Mth.clamp(t / AstraeonEntity.DEATH_TIME, 0, 1);
                float shake = Mth.sin(age * 2.5F) * 0.12F * d;
                root.xRot = shake;
                root.zRot = -shake;
                leftHand.y += 12 * d;
                rightHand.y += 12 * d;
                mask.xRot += 0.6F * d;
                core.xScale = core.yScale = core.zScale = 1.0F + 0.8F * d;
            }
            default -> {}
        }
    }

    private void roar(float f) {
        mask.xRot -= 0.5F * f;
        leftHand.x += 6 * f;
        rightHand.x -= 6 * f;
        leftHand.y -= 5 * f;
        rightHand.y -= 5 * f;
        leftHand.zRot -= 0.8F * f;
        rightHand.zRot += 0.8F * f;
    }
}
