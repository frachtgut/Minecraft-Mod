package net.starfallen.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.starfallen.registry.ModDamageTypes;
import net.starfallen.registry.ModSounds;

/**
 * Pressure-triggered floor trap. A faint click warns the victim, then star-steel spikes
 * erupt from the floor a moment later.
 */
public class SpikeTrapBlock extends Block {
    public static final BooleanProperty EXTENDED = BooleanProperty.create("extended");
    public static final BooleanProperty ARMED = BooleanProperty.create("armed");
    private static final VoxelShape EXTENDED_SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 16, 16));

    public SpikeTrapBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(EXTENDED, false).setValue(ARMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(EXTENDED, ARMED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return EXTENDED_SHAPE;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && entity instanceof LivingEntity && !(entity instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            if (!state.getValue(EXTENDED) && !state.getValue(ARMED)) {
                level.setBlock(pos, state.setValue(ARMED, true), 3);
                level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6F, 1.8F);
                level.scheduleTick(pos, this, 7);
            } else if (state.getValue(EXTENDED)) {
                impale(level, entity);
            }
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(ARMED) && !state.getValue(EXTENDED)) {
            level.setBlock(pos, state.setValue(EXTENDED, true).setValue(ARMED, false), 3);
            level.playSound(null, pos, ModSounds.SPIKE_TRAP.get(), SoundSource.BLOCKS, 1.0F, 0.9F + random.nextFloat() * 0.2F);
            AABB box = new AABB(pos.above()).inflate(0.05, 0.3, 0.05);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
                impale(level, e);
            }
            level.scheduleTick(pos, this, 30);
        } else if (state.getValue(EXTENDED)) {
            level.setBlock(pos, state.setValue(EXTENDED, false).setValue(ARMED, false), 3);
            level.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.4F, 1.6F);
        }
    }

    private static void impale(Level level, Entity e) {
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        DamageSource src = ModDamageTypes.source(level, ModDamageTypes.SPIKES);
        if (e.hurt(src, 6.0F) && e instanceof LivingEntity living) {
            living.setDeltaMovement(living.getDeltaMovement().add(0, 0.35, 0));
            living.hurtMarked = true;
        }
    }
}
