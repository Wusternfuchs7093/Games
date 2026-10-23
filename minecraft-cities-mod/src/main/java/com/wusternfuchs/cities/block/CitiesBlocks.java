package com.wusternfuchs.cities.block;

import com.wusternfuchs.cities.CitiesMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Workstations of the town's own professions and the job board. */
public final class CitiesBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, CitiesMod.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, CitiesMod.MODID);

    /** Where scholars work: the town's research comes from here. */
    public static final DeferredHolder<Block, Block> SCHOLAR_DESK = BLOCKS.register("scholar_desk",
            () -> new Block(wood()));
    /** Where builders work: each builder speeds up construction. */
    public static final DeferredHolder<Block, Block> BUILDER_BENCH = BLOCKS.register("builder_bench",
            () -> new Block(wood()));
    /** Where artisans make the culture's craft goods. */
    public static final DeferredHolder<Block, Block> CRAFT_BENCH = BLOCKS.register("craft_bench",
            () -> new Block(wood()));
    /** Right-click to see the town's jobs. */
    public static final DeferredHolder<Block, JobBoardBlock> JOB_BOARD = BLOCKS.register("job_board",
            () -> new JobBoardBlock(wood()));

    public static final DeferredHolder<Item, BlockItem> SCHOLAR_DESK_ITEM = ITEMS.register("scholar_desk",
            () -> new BlockItem(SCHOLAR_DESK.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> BUILDER_BENCH_ITEM = ITEMS.register("builder_bench",
            () -> new BlockItem(BUILDER_BENCH.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> CRAFT_BENCH_ITEM = ITEMS.register("craft_bench",
            () -> new BlockItem(CRAFT_BENCH.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> JOB_BOARD_ITEM = ITEMS.register("job_board",
            () -> new BlockItem(JOB_BOARD.get(), new Item.Properties()));

    private CitiesBlocks() {
    }

    private static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD)
                .ignitedByLava();
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(CitiesBlocks::onCreativeTabs);
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(SCHOLAR_DESK_ITEM.get());
            event.accept(BUILDER_BENCH_ITEM.get());
            event.accept(CRAFT_BENCH_ITEM.get());
            event.accept(JOB_BOARD_ITEM.get());
        }
    }
}
