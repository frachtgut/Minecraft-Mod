package net.starfallen.registry;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.starfallen.Starfallen;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Starfallen.MODID);

    /** Dazed by starlight: heavily slowed and unable to attack at full strength. */
    public static final RegistryObject<MobEffect> STARSTRUCK = EFFECTS.register("starstruck", () -> new StarstruckEffect()
            .addAttributeModifier(Attributes.MOVEMENT_SPEED, "7f1f8e55-5b5a-4d8f-9d6c-2a8b8b1f1a01", -0.45D, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .addAttributeModifier(Attributes.ATTACK_SPEED, "7f1f8e55-5b5a-4d8f-9d6c-2a8b8b1f1a02", -0.3D, AttributeModifier.Operation.MULTIPLY_TOTAL));

    public static class StarstruckEffect extends MobEffect {
        public StarstruckEffect() {
            super(MobEffectCategory.HARMFUL, 0xFFE38A);
        }
    }

    private ModEffects() {}
}
