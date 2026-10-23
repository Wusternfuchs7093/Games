package com.wusternfuchs.cities.block;

import com.mojang.serialization.MapCodec;
import com.wusternfuchs.cities.network.CitiesNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/** A notice board: right-clicking it opens the job board of the settlement it stands in. */
public class JobBoardBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<JobBoardBlock> CODEC = simpleCodec(JobBoardBlock::new);

    public JobBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            CitiesNetwork.openBoard(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
