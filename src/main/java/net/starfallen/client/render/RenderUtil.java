package net.starfallen.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Small helpers for billboards and flat quads. */
public final class RenderUtil {
    public static final int FULL_BRIGHT = 0xF000F0;

    private RenderUtil() {}

    /** A camera-facing square of half-size {@code s} (call after applying the camera rotation). */
    public static void billboard(PoseStack pose, VertexConsumer vc, float s, float r, float g, float b, float a) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        vertex(vc, m, n, -s, -s, 0, 0, 1, r, g, b, a);
        vertex(vc, m, n, s, -s, 0, 1, 1, r, g, b, a);
        vertex(vc, m, n, s, s, 0, 1, 0, r, g, b, a);
        vertex(vc, m, n, -s, s, 0, 0, 0, r, g, b, a);
    }

    /** A horizontal square on the XZ plane, both faces. */
    public static void flatQuad(PoseStack pose, VertexConsumer vc, float s, float r, float g, float b, float a) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        vertex(vc, m, n, -s, 0, -s, 0, 0, r, g, b, a);
        vertex(vc, m, n, -s, 0, s, 0, 1, r, g, b, a);
        vertex(vc, m, n, s, 0, s, 1, 1, r, g, b, a);
        vertex(vc, m, n, s, 0, -s, 1, 0, r, g, b, a);
        vertex(vc, m, n, s, 0, -s, 1, 0, r, g, b, a);
        vertex(vc, m, n, s, 0, s, 1, 1, r, g, b, a);
        vertex(vc, m, n, -s, 0, s, 0, 1, r, g, b, a);
        vertex(vc, m, n, -s, 0, -s, 0, 0, r, g, b, a);
    }

    /**
     * Emits one full-bright vertex. The colour is premultiplied by alpha because the glow render types blend
     * additively (ONE, ONE) and would otherwise ignore the alpha fade.
     */
    public static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                              float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).color(r * a, g * a, b * a, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT)
                .normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }
}
