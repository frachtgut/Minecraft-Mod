package net.starfallen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.starfallen.Starfallen;
import net.starfallen.client.model.AstralMimicModel;
import net.starfallen.client.model.SFModelLayers;
import net.starfallen.entity.AstralMimicEntity;

/** Renders the mimic with the real chest texture - the perfect disguise - plus its hidden innards. */
public class AstralMimicRenderer extends LivingEntityRenderer<AstralMimicEntity, AstralMimicModel> {
    private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace("textures/entity/chest/normal.png");
    private static final ResourceLocation INNARDS = Starfallen.id("textures/entity/astral_mimic.png");
    private static final ResourceLocation INNARDS_GLOW = Starfallen.id("textures/entity/astral_mimic_glow.png");

    public AstralMimicRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new AstralMimicModel(ctx.bakeLayer(SFModelLayers.MIMIC_CHEST), ctx.bakeLayer(SFModelLayers.MIMIC_INNARDS)), 0.0F);
        addLayer(new RenderLayer<>(this) {
            @Override
            public void render(PoseStack pose, MultiBufferSource buffers, int light, AstralMimicEntity mimic, float limbSwing, float limbAmount,
                               float partial, float age, float headYaw, float headPitch) {
                AstralMimicModel model = getParentModel();
                if (!model.innardsVisible()) return;
                model.renderInnards(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(INNARDS)), light,
                        LivingEntityRenderer.getOverlayCoords(mimic, 0.0F));
                model.renderInnards(pose, buffers.getBuffer(RenderType.eyes(INNARDS_GLOW)), 0xF00000, OverlayTexture.NO_OVERLAY);
            }
        });
    }

    @Override
    public void render(AstralMimicEntity mimic, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        this.shadowRadius = mimic.isAwake() ? 0.5F : 0.0F;
        getModel().animate(mimic, partial, mimic.tickCount + partial);
        super.render(mimic, yaw, partial, pose, buffers, light);
    }

    @Override
    protected boolean shouldShowName(AstralMimicEntity mimic) {
        return mimic.hasCustomName() && super.shouldShowName(mimic);
    }

    @Override
    public ResourceLocation getTextureLocation(AstralMimicEntity entity) {
        return CHEST;
    }
}
