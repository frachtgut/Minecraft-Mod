package net.starfallen.worldgen.structure;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Weighted random block palette. */
public final class Palette {
    private final List<BlockState> states = new ArrayList<>();
    private final List<Integer> weights = new ArrayList<>();
    private int total;

    public static Palette of(BlockState state) {
        return new Palette().add(state, 1);
    }

    public Palette add(BlockState state, int weight) {
        states.add(state);
        weights.add(weight);
        total += weight;
        return this;
    }

    public BlockState pick(RandomSource random) {
        if (states.size() == 1) return states.get(0);
        int r = random.nextInt(total);
        for (int i = 0; i < states.size(); i++) {
            r -= weights.get(i);
            if (r < 0) return states.get(i);
        }
        return states.get(0);
    }

    public BlockState first() {
        return states.get(0);
    }
}
