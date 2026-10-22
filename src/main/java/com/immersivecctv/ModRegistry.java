package com.immersivecctv;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRegistry {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ImmersiveCCTV.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ImmersiveCCTV.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ImmersiveCCTV.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ImmersiveCCTV.MODID);

    public static final RegistryObject<Block> XVR = BLOCKS.register("xvr",
            () -> new XvrBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(1.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(s -> s.getValue(XvrBlock.POWERED) ? 4 : 0)));

    public static final RegistryObject<Block> MONITOR = BLOCKS.register("monitor",
            () -> new MonitorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(1.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final RegistryObject<Block> CAMERA = BLOCKS.register("camera",
            () -> new CameraBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .strength(1.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final RegistryObject<Item> XVR_ITEM = ITEMS.register("xvr",
            () -> new BlockItem(XVR.get(), new Item.Properties()));
    public static final RegistryObject<Item> MONITOR_ITEM = ITEMS.register("monitor",
            () -> new BlockItem(MONITOR.get(), new Item.Properties()));
    public static final RegistryObject<Item> CAMERA_ITEM = ITEMS.register("camera",
            () -> new BlockItem(CAMERA.get(), new Item.Properties()));
    public static final RegistryObject<Item> HDMI_CABLE = ITEMS.register("hdmi_cable",
            () -> new CableItem(new Item.Properties().stacksTo(16), CableItem.Kind.HDMI));
    public static final RegistryObject<Item> ETHERNET_CABLE = ITEMS.register("ethernet_cable",
            () -> new CableItem(new Item.Properties().stacksTo(16), CableItem.Kind.ETHERNET));

    public static final RegistryObject<BlockEntityType<XvrBlockEntity>> XVR_BE =
            BLOCK_ENTITIES.register("xvr",
                    () -> BlockEntityType.Builder.of(XvrBlockEntity::new, XVR.get()).build(null));
    public static final RegistryObject<BlockEntityType<MonitorBlockEntity>> MONITOR_BE =
            BLOCK_ENTITIES.register("monitor",
                    () -> BlockEntityType.Builder.of(MonitorBlockEntity::new, MONITOR.get()).build(null));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("cctv",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.immersivecctv"))
                    .icon(() -> new ItemStack(XVR_ITEM.get()))
                    .displayItems((params, out) -> {
                        out.accept(XVR_ITEM.get());
                        out.accept(MONITOR_ITEM.get());
                        out.accept(CAMERA_ITEM.get());
                        out.accept(HDMI_CABLE.get());
                        out.accept(ETHERNET_CABLE.get());
                    })
                    .build());
}
