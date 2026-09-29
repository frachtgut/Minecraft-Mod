package net.starfallen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/** Renders an additive, full-bright "glow" texture over the model (eyes, cracks, runes, cores). */
public class GlowLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    public interface Tint<T> {
        float[] color(T entity, float partial);
    }

    private final Function<T, ResourceLocation> texture;
    private final Tint<T> tint;

    public GlowLayer(RenderLayerParent<T, M> parent, ResourceLocation texture) {
        this(parent, e -> texture, (e, p) -> null);
    }

    public GlowLayer(RenderLayerParent<T, M> parent, Function<T, ResourceLocation> texture, Tint<T> tint) {
        super(parent);
        this.texture = texture;
        this.tint = tint;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbAmount,
                       float partial, float age, float headYaw, float headPitch) {
        if (entity.isInvisible()) return;
        float[] c = tint.color(entity, partial);
        float r = 1, g = 1, b = 1;
        if (c != null) {
            r = c[0];
            g = c[1];
            b = c[2];
        }
        getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(texture.apply(entity))), 0xF00000,
                OverlayTexture.NO_OVERLAY, r, g, b, 1.0F);
    }
}
