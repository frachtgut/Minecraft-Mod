package net.starfallen.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.starfallen.entity.boss.AstraeonEntity;
import net.starfallen.registry.ModSounds;
import org.jetbrains.annotations.Nullable;

/** Plays Astraeon's battle theme while the boss is near, silencing the regular music. */
public final class BossMusic {
    @Nullable private static Track current;

    private BossMusic() {}

    public static void tick(Minecraft mc, @Nullable AstraeonEntity boss) {
        boolean want = boss != null && boss.getState() != AstraeonEntity.DYING;
        if (want) {
            mc.getMusicManager().stopPlaying();
            if (current == null || current.isStopped() || !mc.getSoundManager().isActive(current)) {
                current = new Track();
                mc.getSoundManager().play(current);
            }
            current.fadeIn();
        } else if (current != null) {
            current.fadeOut();
            if (current.isStopped()) current = null;
        }
    }

    public static void stop() {
        if (current != null) {
            Minecraft.getInstance().getSoundManager().stop(current);
            current = null;
        }
    }

    private static final class Track extends AbstractTickableSoundInstance {
        private float target = 1.0F;

        Track() {
            super(ModSounds.BOSS_MUSIC.get(), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
        }

        void fadeIn() {
            target = 1.0F;
        }

        void fadeOut() {
            target = 0.0F;
        }

        @Override
        public void tick() {
            volume += (target - volume) * 0.05F;
            if (target == 0.0F && volume < 0.02F) stop();
        }
    }
}
