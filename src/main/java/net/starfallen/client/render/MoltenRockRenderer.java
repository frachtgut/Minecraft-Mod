package net.starfallen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.starfallen.Starfallen;
import net.starfallen.client.model.MeteorModel;
import net.starfallen.client.model.SFModelLayers;
import net.starfallen.entity.projectile.MoltenRockEntity;

public class MoltenRockRenderer extends EntityRenderer<MoltenRockEntity> {
    private static final ResourceLocation TEXTURE = Starfallen.id("textures/entity/molten_rock.png");
    private static final ResourceLocation GLOW = Starfallen.id("textures/entity/molten_rock_glow.png");
    private static final ResourceLocation FLARE = Starfallen.id("textures/entity/flare.png");
    private final MeteorModel model;

    public MoltenRockRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new MeteorModel(ctx.bakeLayer(SFModelLayers.MOLTEN_ROCK));
    }

    @Override
    public void render(MoltenRockEntity rock, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = rock.tickCount + partial;
        pose.pushPose();
        pose.translate(0, 0.4, 0);
        pose.pushPose();
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        RenderUtil.billboard(pose, buffers.getBuffer(RenderType.eyes(FLARE)), 0.9F, 1.0F, 0.5F, 0.15F, 1.0F);
        pose.popPose();
        pose.mulPose(Axis.XP.rotationDegrees(age * 20));
        pose.mulPose(Axis.YP.rotationDegrees(age * 13));
        pose.scale(-1, -1, 1);
        model.renderToBuffer(pose, buffers.getBuffer(model.renderType(TEXTURE)), RenderUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), RenderUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        pose.popPose();
        super.render(rock, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MoltenRockEntity entity) {
        return TEXTURE;
    }
}
