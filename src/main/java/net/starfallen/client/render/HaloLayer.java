package net.starfallen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.starfallen.Starfallen;
import net.starfallen.client.model.SFModelLayers;
import net.starfallen.client.model.gen.HaloGeometry;
import net.starfallen.item.HaloItem;

/** Renders the Halo of the Fallen Star as a slowly turning crown of light above the wearer's head. */
public class HaloLayer<T extends Player, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation TEXTURE = Starfallen.id("textures/entity/halo.png");
    private final ModelPart halo;

    public HaloLayer(RenderLayerParent<T, M> parent, EntityModelSet models) {
        super(parent);
        this.halo = models.bakeLayer(SFModelLayers.HALO).getChild("halo");
    }

    public static LayerDefinition createHaloLayer() {
        return HaloGeometry.create();
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T player, float limbSwing, float limbAmount,
                       float partial, float age, float headYaw, float headPitch) {
        if (!(player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof HaloItem) || player.isInvisible()) return;
        pose.pushPose();
        getParentModel().head.translateAndRotate(pose);
        pose.translate(0, -0.72 + Mth.sin(age * 0.08F) * 0.025F, 0);
        pose.mulPose(Axis.XP.rotationDegrees(-12));
        pose.mulPose(Axis.YP.rotationDegrees(age * 2.0F));
        float pulse = 0.8F + 0.2F * Mth.sin(age * 0.15F);
        halo.render(pose, buffers.getBuffer(RenderType.eyes(TEXTURE)), RenderUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pulse, pulse, pulse, 1.0F);
        pose.popPose();
    }
}
