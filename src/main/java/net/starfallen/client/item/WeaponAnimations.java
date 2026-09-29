package net.starfallen.client.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Custom first- and third-person animations for the signature weapons:
 * the hammer is heaved overhead while charging, the scythe is held two-handed, and the staff
 * and gauntlet thrust forward whenever they are cast.
 */
public final class WeaponAnimations {
    private WeaponAnimations() {}

    /** Both arms raised high, hammer cocked behind the head. */
    private static final HumanoidModel.ArmPose HAMMER_RAISE = HumanoidModel.ArmPose.create("STARFALLEN_HAMMER_RAISE", true,
            (model, entity, arm) -> {
                float charge = Math.min(1.0F, entity.getTicksUsingItem() / 20.0F);
                float lift = -2.6F - 0.4F * charge;
                model.rightArm.xRot = lift;
                model.leftArm.xRot = lift;
                model.rightArm.yRot = -0.25F;
                model.leftArm.yRot = 0.25F;
                model.rightArm.zRot = 0.0F;
                model.leftArm.zRot = 0.0F;
                if (charge >= 1.0F) {
                    float jitter = Mth.sin(entity.tickCount * 2.5F) * 0.04F;
                    model.rightArm.xRot += jitter;
                    model.leftArm.xRot += jitter;
                }
            });

    /** Scythe held across the body in both hands. */
    private static final HumanoidModel.ArmPose SCYTHE_HOLD = HumanoidModel.ArmPose.create("STARFALLEN_SCYTHE_HOLD", true,
            (model, entity, arm) -> {
                model.rightArm.xRot = -0.9F + model.rightArm.xRot * 0.2F;
                model.rightArm.yRot = -0.45F;
                model.leftArm.xRot = -0.75F + model.leftArm.xRot * 0.2F;
                model.leftArm.yRot = 0.7F;
                model.leftArm.zRot = 0.1F;
            });

    /** Arm thrust forward towards the target (casting). */
    private static final HumanoidModel.ArmPose CAST = HumanoidModel.ArmPose.create("STARFALLEN_CAST", false,
            (model, entity, arm) -> {
                var part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
                part.xRot = -Mth.HALF_PI + model.head.xRot;
                part.yRot = model.head.yRot + (arm == HumanoidArm.RIGHT ? -0.1F : 0.1F);
            });

    private static boolean recentlyUsed(LivingEntity entity, ItemStack stack) {
        return entity instanceof Player p && p.getCooldowns().getCooldownPercent(stack.getItem(), 0) > 0.85F;
    }

    public static final IClientItemExtensions HAMMER = new IClientItemExtensions() {
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return entity.isUsingItem() && entity.getUseItem() == stack ? HAMMER_RAISE : null;
        }

        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partial,
                                               float equip, float swing) {
            if (!(player.isUsingItem() && player.getUseItem() == stack)) return false;
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            float charge = Math.min(1.0F, (player.getTicksUsingItem() + partial) / 20.0F);
            float ease = 1 - (1 - charge) * (1 - charge);
            pose.translate(side * 0.56F, -0.52F + ease * 0.45F, -0.72F + ease * 0.1F);
            pose.mulPose(Axis.XP.rotationDegrees(50F * ease));
            pose.mulPose(Axis.ZP.rotationDegrees(side * -12F * ease));
            if (charge >= 1.0F) {
                float j = Mth.sin((player.tickCount + partial) * 2.3F) * 0.012F;
                pose.translate(j, j * 0.5F, 0);
            }
            return true;
        }
    };

    public static final IClientItemExtensions BLADE = new IClientItemExtensions() {
        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partial,
                                               float equip, float swing) {
            if (!recentlyUsed(player, stack)) return false;
            // A flashing reverse-grip flourish right after a dash or rift strike
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            float t = player.getCooldowns().getCooldownPercent(stack.getItem(), partial);
            float f = Mth.clamp((t - 0.85F) / 0.15F, 0, 1);
            pose.translate(side * 0.56F, -0.52F - equip * 0.6F, -0.72F);
            pose.mulPose(Axis.YP.rotationDegrees(side * 70F * f));
            pose.mulPose(Axis.ZP.rotationDegrees(side * -40F * f));
            pose.mulPose(Axis.XP.rotationDegrees(-30F * f));
            return true;
        }
    };

    public static final IClientItemExtensions STAFF = new IClientItemExtensions() {
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return recentlyUsed(entity, stack) ? CAST : null;
        }

        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partial,
                                               float equip, float swing) {
            if (!recentlyUsed(player, stack)) return false;
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            float t = player.getCooldowns().getCooldownPercent(stack.getItem(), partial);
            float f = Mth.clamp((t - 0.85F) / 0.15F, 0, 1);
            pose.translate(side * 0.4F, -0.4F + f * 0.25F, -0.9F - f * 0.2F);
            pose.mulPose(Axis.XP.rotationDegrees(-25F * f));
            return true;
        }
    };

    public static final IClientItemExtensions GAUNTLET = STAFF;

    public static final IClientItemExtensions SCYTHE = new IClientItemExtensions() {
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return hand == InteractionHand.MAIN_HAND && !entity.swinging ? SCYTHE_HOLD : null;
        }
    };
}
