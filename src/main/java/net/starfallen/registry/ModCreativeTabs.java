package net.starfallen.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.starfallen.Starfallen;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Starfallen.MODID);

    public static final RegistryObject<CreativeModeTab> STARFALLEN = TABS.register("starfallen", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.starfallen"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> ModItems.STAR_FRAGMENT.get().getDefaultInstance())
            .displayItems((params, output) -> {
                for (var obj : ModItems.TAB_ORDER) {
                    output.accept(obj.get());
                }
            })
            .build());

    private ModCreativeTabs() {}
}
