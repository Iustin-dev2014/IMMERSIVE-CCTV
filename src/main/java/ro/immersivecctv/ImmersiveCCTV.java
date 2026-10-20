package ro.immersivecctv;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ro.immersivecctv.block.CameraBlock;
import ro.immersivecctv.block.MonitorBlock;
import ro.immersivecctv.block.XvrBlock;
import ro.immersivecctv.block.PowerOutletBlock;
import ro.immersivecctv.block.PtzCameraBlock;
import ro.immersivecctv.block.RouterBlock;
import ro.immersivecctv.block.InternetBlock;
import ro.immersivecctv.blockentity.CameraBlockEntity;
import ro.immersivecctv.blockentity.MonitorBlockEntity;
import ro.immersivecctv.blockentity.XvrBlockEntity;
import ro.immersivecctv.blockentity.RouterBlockEntity;
import ro.immersivecctv.blockentity.InternetBlockEntity;
import ro.immersivecctv.item.CctvCableItem;
import ro.immersivecctv.item.AcChargerItem;
import ro.immersivecctv.item.PowerCableItem;
import ro.immersivecctv.item.CctvServiceTool;
import ro.immersivecctv.item.NetworkCableItem;
import ro.immersivecctv.item.FiberOpticCableItem;
import ro.immersivecctv.item.CloudAccessRemoteItem;

@Mod(ImmersiveCCTV.MODID)
public final class ImmersiveCCTV {
    public static final String MODID = "immersivecctv";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Block> XVR = BLOCKS.register("xvr", () -> new XvrBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> MONITOR = BLOCKS.register("monitor", () -> new MonitorBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.5F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> CAMERA = BLOCKS.register("camera", () -> new CameraBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> PTZ_CAMERA = BLOCKS.register("ptz_camera", () -> new PtzCameraBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.2F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> POWER_OUTLET = BLOCKS.register("power_outlet", () -> new PowerOutletBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.2F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> ROUTER = BLOCKS.register("router", () -> new RouterBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.2F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> INTERNET_BLOCK = BLOCKS.register("internet_block", () -> new InternetBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F).sound(SoundType.METAL).noOcclusion()));

    public static final RegistryObject<Item> HDMI_CABLE = ITEMS.register("hdmi_cable",
            () -> new CctvCableItem(CctvCableItem.Kind.HDMI, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> POE_CABLE = ITEMS.register("poe_cat6_cable",
            () -> new CctvCableItem(CctvCableItem.Kind.POE, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> AC_CHARGER = ITEMS.register("ac_charger",
            () -> new AcChargerItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> POWER_CABLE = ITEMS.register("power_cable",
            () -> new PowerCableItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CCTV_SERVICE_TOOL = ITEMS.register("cctv_service_tool",
            () -> new CctvServiceTool(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ETHERNET_CABLE = ITEMS.register("ethernet_cable",
            () -> new NetworkCableItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FIBRE_OPTIC_CABLE = ITEMS.register("fibre_optic_cable",
            () -> new FiberOpticCableItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CLOUD_ACCESS_REMOTE = ITEMS.register("cloud_access_remote",
            () -> new CloudAccessRemoteItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> XVR_ITEM = ITEMS.register("xvr", () -> new BlockItem(XVR.get(), new Item.Properties()));
    public static final RegistryObject<Item> MONITOR_ITEM = ITEMS.register("monitor", () -> new BlockItem(MONITOR.get(), new Item.Properties()));
    public static final RegistryObject<Item> CAMERA_ITEM = ITEMS.register("camera", () -> new BlockItem(CAMERA.get(), new Item.Properties()));
    public static final RegistryObject<Item> POWER_OUTLET_ITEM = ITEMS.register("power_outlet", () -> new BlockItem(POWER_OUTLET.get(), new Item.Properties()));
    public static final RegistryObject<Item> PTZ_CAMERA_ITEM = ITEMS.register("ptz_camera", () -> new BlockItem(PTZ_CAMERA.get(), new Item.Properties()));
    public static final RegistryObject<Item> ROUTER_ITEM = ITEMS.register("router", () -> new BlockItem(ROUTER.get(), new Item.Properties()));
    public static final RegistryObject<Item> INTERNET_BLOCK_ITEM = ITEMS.register("internet_block", () -> new BlockItem(INTERNET_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<XvrBlockEntity>> XVR_BE = BLOCK_ENTITIES.register("xvr",
            () -> BlockEntityType.Builder.of(XvrBlockEntity::new, XVR.get()).build(null));
    public static final RegistryObject<BlockEntityType<MonitorBlockEntity>> MONITOR_BE = BLOCK_ENTITIES.register("monitor",
            () -> BlockEntityType.Builder.of(MonitorBlockEntity::new, MONITOR.get()).build(null));
    public static final RegistryObject<BlockEntityType<CameraBlockEntity>> CAMERA_BE = BLOCK_ENTITIES.register("camera",
            () -> BlockEntityType.Builder.of(CameraBlockEntity::new, CAMERA.get(), PTZ_CAMERA.get()).build(null));
    public static final RegistryObject<BlockEntityType<RouterBlockEntity>> ROUTER_BE = BLOCK_ENTITIES.register("router",
            () -> BlockEntityType.Builder.of(RouterBlockEntity::new, ROUTER.get()).build(null));
    public static final RegistryObject<BlockEntityType<InternetBlockEntity>> INTERNET_BE = BLOCK_ENTITIES.register("internet_block",
            () -> BlockEntityType.Builder.of(InternetBlockEntity::new, INTERNET_BLOCK.get()).build(null));

    public static final RegistryObject<CreativeModeTab> CCTV_TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.literal("Immersive CCTV"))
            .icon(() -> new ItemStack(XVR_ITEM.get()))
            .displayItems((parameters, output) -> {
                output.accept(XVR_ITEM.get());
                output.accept(MONITOR_ITEM.get());
                output.accept(CAMERA_ITEM.get());
                output.accept(PTZ_CAMERA_ITEM.get());
                output.accept(POWER_OUTLET_ITEM.get());
                output.accept(ROUTER_ITEM.get());
                output.accept(INTERNET_BLOCK_ITEM.get());
                output.accept(HDMI_CABLE.get());
                output.accept(POE_CABLE.get());
                output.accept(AC_CHARGER.get());
                output.accept(POWER_CABLE.get());
                output.accept(CCTV_SERVICE_TOOL.get());
                output.accept(ETHERNET_CABLE.get());
                output.accept(FIBRE_OPTIC_CABLE.get());
                output.accept(CLOUD_ACCESS_REMOTE.get());
            }).build());

    public ImmersiveCCTV() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TABS.register(bus);
        CctvSounds.register(bus);
        CctvNetwork.register();
    }
}
