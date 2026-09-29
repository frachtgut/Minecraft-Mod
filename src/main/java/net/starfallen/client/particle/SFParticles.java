package net.starfallen.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** All Starfallen particle implementations. */
public final class SFParticles {
    private SFParticles() {}

    private static final int FULL_BRIGHT = 0xF000F0;

    /** Twinkling four-point star (gold / cyan / violet variants via tint). */
    public static class Spark extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final float baseSize;
        private final float twinkleSpeed;

        Spark(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, float r, float g, float b) {
            super(level, x, y, z, vx, vy, vz);
            this.sprites = sprites;
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.rCol = r;
            this.gCol = g;
            this.bCol = b;
            this.lifetime = 16 + random.nextInt(18);
            this.baseSize = 0.06F + random.nextFloat() * 0.09F;
            this.quadSize = baseSize;
            this.twinkleSpeed = 0.3F + random.nextFloat() * 0.5F;
            this.friction = 0.92F;
            this.gravity = 0.0F;
            this.hasPhysics = false;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            setSpriteFromAge(sprites);
            float life = age / (float) lifetime;
            this.alpha = 1.0F - life * life;
            this.quadSize = baseSize * (0.75F + 0.35F * Mth.sin(age * twinkleSpeed)) * (1.0F - life * 0.5F);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float partial) {
            return FULL_BRIGHT;
        }
    }

    public record SparkProvider(SpriteSet sprites, float r, float g, float b) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new Spark(level, x, y, z, vx, vy, vz, sprites, r, g, b);
        }
    }

    /** Glowing ember that cools from white-yellow to deep orange as it rises. */
    public static class Ember extends TextureSheetParticle {
        Ember(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, vx, vy, vz);
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.lifetime = 20 + random.nextInt(30);
            this.quadSize = 0.05F + random.nextFloat() * 0.08F;
            this.friction = 0.94F;
            this.gravity = -0.01F;
            this.hasPhysics = false;
            pickSprite(sprites);
            this.rCol = 1.0F;
            this.gCol = 0.95F;
            this.bCol = 0.7F;
        }

        @Override
        public void tick() {
            super.tick();
            float life = age / (float) lifetime;
            this.gCol = 0.95F - 0.6F * life;
            this.bCol = 0.7F - 0.65F * Math.min(1, life * 1.5F);
            this.alpha = 1.0F - life;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float partial) {
            return FULL_BRIGHT;
        }
    }

    public record EmberProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new Ember(level, x, y, z, vx, vy, vz, sprites);
        }
    }

    /** Big, slow meteor smoke that glows orange when fresh and darkens into soot. */
    public static class Smoke extends TextureSheetParticle {
        private final SpriteSet sprites;

        Smoke(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, vx, vy, vz);
            this.sprites = sprites;
            this.xd = vx + (random.nextDouble() - 0.5) * 0.02;
            this.yd = vy;
            this.zd = vz + (random.nextDouble() - 0.5) * 0.02;
            this.lifetime = 40 + random.nextInt(40);
            this.quadSize = 0.35F + random.nextFloat() * 0.35F;
            this.friction = 0.96F;
            this.gravity = -0.004F;
            this.hasPhysics = false;
            this.roll = random.nextFloat() * 6.28F;
            this.oRoll = roll;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            setSpriteFromAge(sprites);
            float life = age / (float) lifetime;
            this.oRoll = roll;
            this.roll += 0.02F;
            this.quadSize *= 1.012F;
            float heat = Math.max(0, 1.0F - life * 4.0F);
            this.rCol = 0.22F + 0.78F * heat;
            this.gCol = 0.2F + 0.4F * heat;
            this.bCol = 0.24F + 0.05F * heat;
            this.alpha = 0.85F * (1.0F - life);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float partial) {
            return age < lifetime / 4 ? FULL_BRIGHT : super.getLightColor(partial);
        }
    }

    public record SmokeProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new Smoke(level, x, y, z, vx, vy, vz, sprites);
        }
    }

    /** A flat ring lying on the ground, expanding and fading out. */
    public static class Ring extends TextureSheetParticle {
        private final float maxSize;

        Ring(ClientLevel level, double x, double y, double z, SpriteSet sprites, float r, float g, float b, float maxSize, int life) {
            super(level, x, y, z, 0, 0, 0);
            this.xd = 0;
            this.yd = 0;
            this.zd = 0;
            this.lifetime = life;
            this.maxSize = maxSize;
            this.quadSize = 0.3F;
            this.hasPhysics = false;
            this.gravity = 0;
            this.rCol = r;
            this.gCol = g;
            this.bCol = b;
            pickSprite(sprites);
        }

        @Override
        public float getQuadSize(float partial) {
            float t = (age + partial) / lifetime;
            return 0.3F + maxSize * (1.0F - (1.0F - t) * (1.0F - t));
        }

        @Override
        public void tick() {
            super.tick();
            float t = age / (float) lifetime;
            this.alpha = 1.0F - t;
        }

        @Override
        public void render(VertexConsumer buffer, Camera camera, float partial) {
            Vec3 cam = camera.getPosition();
            float px = (float) (Mth.lerp(partial, xo, x) - cam.x());
            float py = (float) (Mth.lerp(partial, yo, y) - cam.y());
            float pz = (float) (Mth.lerp(partial, zo, z) - cam.z());
            float s = getQuadSize(partial);
            Vector3f[] v = {new Vector3f(-s, 0, -s), new Vector3f(-s, 0, s), new Vector3f(s, 0, s), new Vector3f(s, 0, -s)};
            float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
            int light = FULL_BRIGHT;
            // Top face
            buffer.vertex(v[0].x + px, v[0].y + py, v[0].z + pz).uv(u1, v1).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
            buffer.vertex(v[1].x + px, v[1].y + py, v[1].z + pz).uv(u1, v0).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
            buffer.vertex(v[2].x + px, v[2].y + py, v[2].z + pz).uv(u0, v0).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
            buffer.vertex(v[3].x + px, v[3].y + py, v[3].z + pz).uv(u0, v1).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
            // Bottom face (visible from below)
            buffer.vertex(v[3].x + px, v[3].y + py, v[3].z + pz).uv(u0, v1).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
            buffer.vertex(v[2].x + px, v[2].y + py, v[2].z + pz).uv(u0, v0).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
            buffer.vertex(v[1].x + px, v[1].y + py, v[1].z + pz).uv(u1, v0).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
            buffer.vertex(v[0].x + px, v[0].y + py, v[0].z + pz).uv(u1, v1).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    public record RingProvider(SpriteSet sprites, float r, float g, float b, float size, int life) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new Ring(level, x, y, z, sprites, r, g, b, size, life);
        }
    }

    /** A brief, huge, blinding flash. */
    public static class Flash extends TextureSheetParticle {
        Flash(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0);
            this.lifetime = 8;
            this.hasPhysics = false;
            this.gravity = 0;
            this.xd = 0;
            this.yd = 0;
            this.zd = 0;
            pickSprite(sprites);
        }

        @Override
        public float getQuadSize(float partial) {
            float t = (age + partial) / lifetime;
            return 2.5F + 6.0F * Mth.sin(t * (float) Math.PI);
        }

        @Override
        public void tick() {
            super.tick();
            this.alpha = 1.0F - age / (float) lifetime;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float partial) {
            return FULL_BRIGHT;
        }
    }

    public record FlashProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new Flash(level, x, y, z, sprites);
        }
    }

    /** A drifting arcane glyph. */
    public static class Rune extends TextureSheetParticle {
        Rune(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
            super(level, x, y, z, vx, vy, vz);
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.lifetime = 30 + random.nextInt(20);
            this.quadSize = 0.1F + random.nextFloat() * 0.06F;
            this.hasPhysics = false;
            this.gravity = 0;
            this.friction = 0.96F;
            this.rCol = 0.55F + random.nextFloat() * 0.2F;
            this.gCol = 0.85F;
            this.bCol = 1.0F;
            pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            float t = age / (float) lifetime;
            this.alpha = t < 0.2F ? t * 5 : 1.0F - (t - 0.2F) / 0.8F;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float partial) {
            return FULL_BRIGHT;
        }
    }

    public record RuneProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new Rune(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
