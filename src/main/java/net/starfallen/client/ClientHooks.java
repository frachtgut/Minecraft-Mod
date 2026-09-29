package net.starfallen.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundSource;
import net.starfallen.entity.projectile.MeteorEntity;
import net.starfallen.registry.ModSounds;

import java.util.ArrayList;
import java.util.List;

/** Client-only helpers invoked from common code through DistExecutor. */
public final class ClientHooks {
    private ClientHooks() {}

    /** Opens the Starseer's Journal: an illustrated guide stored in the language file. */
    public static void openJournal() {
        List<FormattedText> pages = new ArrayList<>();
        Language lang = Language.getInstance();
        for (int i = 0; i < 64; i++) {
            String key = "journal.starfallen.page." + i;
            if (!lang.has(key)) break;
            pages.add(Component.translatable(key));
        }
        if (pages.isEmpty()) pages.add(Component.literal("..."));
        Minecraft.getInstance().setScreen(new BookViewScreen(new BookViewScreen.BookAccess() {
            @Override
            public int getPageCount() {
                return pages.size();
            }

            @Override
            public FormattedText getPageRaw(int index) {
                return pages.get(index);
            }
        }));
    }

    /** The long roaring whoosh that follows a meteor across the sky. */
    public static void meteorSound(MeteorEntity meteor) {
        Minecraft mc = Minecraft.getInstance();
        float vol = meteor.getKind() == MeteorEntity.Kind.EVENT ? 6.0F : 2.5F;
        var random = meteor.level().getRandom();
        mc.getSoundManager().play(new EntityBoundSoundInstance(ModSounds.METEOR_FALL.get(), SoundSource.HOSTILE, vol,
                0.85F + random.nextFloat() * 0.3F, meteor, random.nextLong()));
    }
}
