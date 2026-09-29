package net.starfallen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.starfallen.Starfallen;
import net.starfallen.client.model.SFModelLayers;
import net.starfallen.client.model.TetherHookModel;
import net.starfallen.entity.projectile.TetherHookEntity;
import net.starfallen.item.StarTetherItem;
import org.joml.Matrix4f;

/** The hook plus a shimmering ribbon of starlight back to the owner's hand. */
public class TetherHookRenderer extends EntityRenderer<TetherHookEntity> {
    private static final ResourceLocation TEXTURE = Starfallen.id("textures/entity/tether_hook.png");
    private final TetherHookModel model;

    public TetherHookRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new TetherHookModel(ctx.bakeLayer(SFModelLayers.TETHER_HOOK));
    }

    @Override
    public boolean shouldRender(TetherHookEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(TetherHookEntity hook, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partial, hook.yRotO, hook.getYRot()) - 180));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partial, hook.xRotO, hook.getXRot())));
        pose.scale(-1, -1, 1);
        model.renderToBuffer(pose, buffers.getBuffer(model.renderType(TEXTURE)), RenderUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        pose.popPose();

        if (hook.getOwner() instanceof Player player) {
            Vec3 hand = handPosition(player, partial);
            Vec3 me = hook.getPosition(partial);
            Vec3 d = hand.subtract(me);
            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
            Matrix4f m = pose.last().pose();
            int segs = 24;
            float wobble = hook.isAnchored() ? 0.0F : 0.15F;
            float time = hook.tickCount + partial;
            for (int k = 0; k < 2; k++) {
                for (int i = 0; i < segs; i++) {
                    float t0 = i / (float) segs, t1 = (i + 1) / (float) segs;
                    Vec3 p0 = point(d, t0, wobble, time);
                    Vec3 p1 = point(d, t1, wobble, time);
                    float w = 0.045F;
                    float ox = k == 0 ? w : 0, oy = k == 0 ? 0 : w;
                    int a0 = 200, a1 = 200;
                    vc.vertex(m, (float) p0.x - ox, (float) p0.y - oy, (float) p0.z).color(120, 240, 255, a0).endVertex();
                    vc.vertex(m, (float) p0.x + ox, (float) p0.y + oy, (float) p0.z).color(120, 240, 255, a0).endVertex();
                    vc.vertex(m, (float) p1.x + ox, (float) p1.y + oy, (float) p1.z).color(120, 240, 255, a1).endVertex();
                    vc.vertex(m, (float) p1.x - ox, (float) p1.y - oy, (float) p1.z).color(120, 240, 255, a1).endVertex();
                }
            }
        }
        super.render(hook, yaw, partial, pose, buffers, light);
    }

    private static Vec3 point(Vec3 d, float t, float wobble, float time) {
        float sag = wobble * Mth.sin(t * Mth.PI);
        return new Vec3(d.x * t + Mth.sin(time * 0.7F + t * 9) * sag * 0.3, d.y * t - sag, d.z * t + Mth.cos(time * 0.7F + t * 9) * sag * 0.3);
    }

    private Vec3 handPosition(Player player, float partial) {
        int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        if (!(player.getMainHandItem().getItem() instanceof StarTetherItem)) side = -side;
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {
            double fovScale = 960.0 / mc.options.fov().get();
            Vec3 v = entityRenderDispatcher.camera.getNearPlane().getPointOnPlane(side * 0.525F, -0.1F).scale(fovScale);
            return player.getEyePosition(partial).add(v).subtract(0, 0.1, 0);
        }
        float body = Mth.lerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        double sin = Mth.sin(body), cos = Mth.cos(body);
        Vec3 base = player.getPosition(partial);
        return new Vec3(base.x - cos * side * 0.35 - sin * 0.8, base.y + player.getEyeHeight() - 0.45, base.z - sin * side * 0.35 + cos * 0.8);
    }

    @Override
    public ResourceLocation getTextureLocation(TetherHookEntity entity) {
        return TEXTURE;
    }
}
