package net.starfallen.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.starfallen.Starfallen;
import org.jetbrains.annotations.NotNull;

public final class ModLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Starfallen.MODID);

    public static final RegistryObject<Codec<AddTableModifier>> ADD_TABLE = SERIALIZERS.register("add_table", () -> AddTableModifier.CODEC);

    /** Rolls an extra loot table and appends its results (used to seed vanilla chests with Starfallen loot). */
    public static class AddTableModifier extends LootModifier {
        public static final Codec<AddTableModifier> CODEC = RecordCodecBuilder.create(inst -> codecStart(inst)
                .and(ResourceLocation.CODEC.fieldOf("table").forGetter(m -> m.table))
                .apply(inst, AddTableModifier::new));

        private final ResourceLocation table;

        public AddTableModifier(LootItemCondition[] conditions, ResourceLocation table) {
            super(conditions);
            this.table = table;
        }

        @Override
        @SuppressWarnings("deprecation")
        protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
            LootTable extra = context.getResolver().getLootTable(table);
            extra.getRandomItemsRaw(context, LootTable.createStackSplitter(context.getLevel(), generatedLoot::add));
            return generatedLoot;
        }

        @Override
        public Codec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }
    }

    private ModLootModifiers() {}
}
