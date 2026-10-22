package com.immersivecctv;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;
import java.util.Objects;

public class XvrBlockEntity extends BlockEntity {
    public static final int CHANNELS = 16;
    public static final int BOOT_TICKS = 200;
    public static final long HDD_MB = 1_000_000L;

    public static final int A_SHUTDOWN = 1;
    public static final int A_REBOOT = 2;
    public static final int A_LAYOUT = 3;
    public static final int A_PAGE = 4;
    public static final int A_OSD_NAME = 5;
    public static final int A_OSD_TIME = 6;
    public static final int A_REC_IND = 7;
    public static final int A_DATE_FMT = 8;
    public static final int A_BRIGHT = 9;
    public static final int A_TIME_SRC = 10;
    public static final int A_TZ = 11;
    public static final int A_CH_ENABLE = 12;
    public static final int A_CH_MIRROR = 13;
    public static final int A_CH_REC = 14;
    public static final int A_CH_NAME = 15;
    public static final int A_REC_ALL = 16;
    public static final int A_OVERWRITE = 17;
    public static final int A_FORMAT = 18;
    public static final int A_POE = 19;
    public static final int A_PIN_SET = 20;
    public static final int A_DEVNAME = 21;
    public static final int A_RESET = 22;

    private long powerOnTime = -1L;
    private BlockPos monitorPos = null;
    private final BlockPos[] cameras = new BlockPos[CHANNELS];
    private final long[] cameraTimes = new long[CHANNELS];

    private String deviceName = "XVR-16CH";
    private String pin = "";
    private int layout = 4;
    private int page = 0;
    private boolean osdName = true;
    private boolean osdTime = true;
    private boolean recInd = true;
    private int dateFmt = 0;
    private int brightness = 100;
    private boolean timeReal = true;
    private int tzOffset = 0;
    private final boolean[] chEnabled = new boolean[CHANNELS];
    private final boolean[] chMirror = new boolean[CHANNELS];
    private final boolean[] chRec = new boolean[CHANNELS];
    private final String[] chName = new String[CHANNELS];
    private boolean recAll = true;
    private boolean overwrite = true;
    private boolean poe = true;
    private long hddUsedMb = 0L;

    public XvrBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.XVR_BE.get(), pos, state);
        Arrays.fill(cameraTimes, -1L);
        resetSettings();
    }

    private void resetSettings() {
        deviceName = "XVR-16CH";
        pin = "";
        layout = 4;
        page = 0;
        osdName = true;
        osdTime = true;
        recInd = true;
        dateFmt = 0;
        brightness = 100;
        timeReal = true;
        tzOffset = 0;
        Arrays.fill(chEnabled, true);
        Arrays.fill(chMirror, false);
        Arrays.fill(chRec, true);
        Arrays.fill(chName, "");
        recAll = true;
        overwrite = true;
        poe = true;
    }

    // ------------------------------------------------------------------ getters

    public boolean isPowered() {
        return powerOnTime >= 0L;
    }

    public long getPowerOnTime() {
        return powerOnTime;
    }

    public boolean isBooted(long gameTime) {
        return powerOnTime >= 0L && gameTime - powerOnTime >= BOOT_TICKS;
    }

    public BlockPos getMonitorPos() {
        return monitorPos;
    }

    public BlockPos getCamera(int ch) {
        return cameras[ch];
    }

    public long getCameraTime(int ch) {
        return cameraTimes[ch];
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getPin() {
        return pin;
    }

    public int getLayout() {
        return layout;
    }

    public int getPage() {
        return page;
    }

    public boolean isOsdName() {
        return osdName;
    }

    public boolean isOsdTime() {
        return osdTime;
    }

    public boolean isRecInd() {
        return recInd;
    }

    public int getDateFmt() {
        return dateFmt;
    }

    public int getBrightness() {
        return brightness;
    }

    public boolean isTimeReal() {
        return timeReal;
    }

    public int getTzOffset() {
        return tzOffset;
    }

    public boolean isChannelEnabled(int ch) {
        return chEnabled[ch];
    }

    public boolean isChannelMirror(int ch) {
        return chMirror[ch];
    }

    public boolean isChannelRec(int ch) {
        return chRec[ch];
    }

    public String getChannelName(int ch) {
        return chName[ch].isEmpty() ? "CAM " + (ch + 1) : chName[ch];
    }

    public String getRawChannelName(int ch) {
        return chName[ch];
    }

    public boolean isRecAll() {
        return recAll;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public boolean isPoe() {
        return poe;
    }

    public long getHddUsedMb() {
        return hddUsedMb;
    }

    public boolean validCamera(int ch) {
        return level != null && cameras[ch] != null
                && level.getBlockState(cameras[ch]).getBlock() instanceof CameraBlock;
    }

    /** Camera cable plugged in, camera exists, XVR powered and PoE on. */
    public boolean isOnline(int ch) {
        return isPowered() && poe && validCamera(ch);
    }

    public int onlineCount() {
        int n = 0;
        for (int i = 0; i < CHANNELS; i++) {
            if (isOnline(i)) {
                n++;
            }
        }
        return n;
    }

    public boolean isNetworkUp() {
        return onlineCount() > 0;
    }

    public int recordingChannels() {
        int n = 0;
        for (int i = 0; i < CHANNELS; i++) {
            if (chRec[i] && chEnabled[i] && isOnline(i)) {
                n++;
            }
        }
        return n;
    }

    public boolean isRecordingActive(long gameTime) {
        return isBooted(gameTime) && recAll && (overwrite || hddUsedMb < HDD_MB) && recordingChannels() > 0;
    }

    // ------------------------------------------------------------------ server logic

    public static void serverTick(Level level, BlockPos pos, BlockState state, XvrBlockEntity be) {
        long t = level.getGameTime();
        if (t % 20L != 0L || !be.isPowered()) {
            return;
        }
        if (be.isRecordingActive(t)) {
            be.hddUsedMb = Math.min(HDD_MB, be.hddUsedMb + 30L * be.recordingChannels());
            be.setChanged();
            if (t % 100L == 0L) {
                be.syncToClient();
            }
        }
    }

    public void togglePower() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean on = !isPowered();
        powerOnTime = on ? level.getGameTime() : -1L;
        level.setBlock(worldPosition, getBlockState().setValue(XvrBlock.POWERED, on), 3);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS,
                0.7F, on ? 1.3F : 0.8F);
        syncToClient();
    }

    public void setMonitorPos(BlockPos pos) {
        this.monitorPos = pos;
        syncToClient();
    }

    /**
     * Plugs a camera into LAN port 'port'.
     * Returns 0 on success, -1 if the port is busy, -2 if the camera is already connected.
     */
    public int connectCamera(int port, BlockPos cam, long time) {
        if (level != null) {
            for (int i = 0; i < CHANNELS; i++) {
                if (cameras[i] != null && level.isLoaded(cameras[i])
                        && !(level.getBlockState(cameras[i]).getBlock() instanceof CameraBlock)) {
                    cameras[i] = null;
                }
            }
        }
        for (int i = 0; i < CHANNELS; i++) {
            if (cam.equals(cameras[i])) {
                return -2;
            }
        }
        if (cameras[port] != null) {
            return -1;
        }
        cameras[port] = cam.immutable();
        cameraTimes[port] = time;
        syncToClient();
        return 0;
    }

    public boolean disconnectLan(int port) {
        if (cameras[port] == null) {
            return false;
        }
        cameras[port] = null;
        cameraTimes[port] = -1L;
        syncToClient();
        return true;
    }

    public boolean disconnectHdmi() {
        if (monitorPos == null) {
            return false;
        }
        if (level != null && level.getBlockEntity(monitorPos) instanceof MonitorBlockEntity mon
                && worldPosition.equals(mon.getXvrPos())) {
            mon.setXvrPos(null);
        }
        monitorPos = null;
        syncToClient();
        return true;
    }

    private static boolean isProtected(int action) {
        switch (action) {
            case A_SHUTDOWN:
            case A_REBOOT:
            case A_FORMAT:
            case A_PIN_SET:
            case A_RESET:
            case A_POE:
            case A_REC_ALL:
            case A_OVERWRITE:
            case A_DEVNAME:
            case A_CH_REC:
                return true;
            default:
                return false;
        }
    }

    private static String clean(String s, int max) {
        String r = s == null ? "" : s.replaceAll("[\\r\\n\\t]", " ").trim();
        return r.length() > max ? r.substring(0, max) : r;
    }

    public void applyAction(ServerPlayer player, int action, int arg, String text, String givenPin) {
        if (level == null) {
            return;
        }
        if (isProtected(action) && !pin.isEmpty() && !pin.equals(givenPin)) {
            player.displayClientMessage(Component.literal("XVR: wrong PIN."), true);
            return;
        }
        boolean ch = arg >= 0 && arg < CHANNELS;
        switch (action) {
            case A_SHUTDOWN:
                if (isPowered()) {
                    togglePower();
                }
                return;
            case A_REBOOT:
                if (isPowered()) {
                    powerOnTime = level.getGameTime();
                    level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.7F, 1.0F);
                }
                break;
            case A_LAYOUT:
                if (arg == 1 || arg == 4 || arg == 9 || arg == 16) {
                    layout = arg;
                    page = 0;
                }
                break;
            case A_PAGE: {
                int pages = (CHANNELS + layout - 1) / layout;
                page = ((page + arg) % pages + pages) % pages;
                break;
            }
            case A_OSD_NAME:
                osdName = !osdName;
                break;
            case A_OSD_TIME:
                osdTime = !osdTime;
                break;
            case A_REC_IND:
                recInd = !recInd;
                break;
            case A_DATE_FMT:
                dateFmt = (dateFmt + 1) % 3;
                break;
            case A_BRIGHT:
                brightness = Math.max(30, Math.min(100, brightness + arg));
                break;
            case A_TIME_SRC:
                timeReal = !timeReal;
                break;
            case A_TZ:
                tzOffset = Math.max(-12, Math.min(14, tzOffset + arg));
                break;
            case A_CH_ENABLE:
                if (ch) {
                    chEnabled[arg] = !chEnabled[arg];
                }
                break;
            case A_CH_MIRROR:
                if (ch) {
                    chMirror[arg] = !chMirror[arg];
                }
                break;
            case A_CH_REC:
                if (ch) {
                    chRec[arg] = !chRec[arg];
                }
                break;
            case A_CH_NAME:
                if (ch) {
                    chName[arg] = clean(text, 20);
                }
                break;
            case A_REC_ALL:
                recAll = !recAll;
                break;
            case A_OVERWRITE:
                overwrite = !overwrite;
                break;
            case A_FORMAT:
                hddUsedMb = 0L;
                break;
            case A_POE:
                poe = !poe;
                break;
            case A_PIN_SET: {
                String p = text == null ? "" : text.replaceAll("[^0-9]", "");
                pin = p.length() > 8 ? p.substring(0, 8) : p;
                break;
            }
            case A_DEVNAME: {
                String n = clean(text, 16);
                deviceName = n.isEmpty() ? "XVR-16CH" : n;
                break;
            }
            case A_RESET:
                resetSettings();
                break;
            default:
                return;
        }
        syncToClient();
    }

    /** Hash of everything the settings screen shows (not the HDD usage). */
    public int settingsHash() {
        int h = Objects.hash(isPowered(), deviceName, pin, layout, page, osdName, osdTime, recInd, dateFmt,
                brightness, timeReal, tzOffset, recAll, overwrite, poe, monitorPos);
        h = 31 * h + Arrays.hashCode(chEnabled);
        h = 31 * h + Arrays.hashCode(chMirror);
        h = 31 * h + Arrays.hashCode(chRec);
        h = 31 * h + Arrays.hashCode(chName);
        for (int i = 0; i < CHANNELS; i++) {
            h = 31 * h + Objects.hashCode(cameras[i]);
        }
        return h;
    }

    private void syncToClient() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("PowerOn", powerOnTime);
        if (monitorPos != null) {
            tag.putLong("Monitor", monitorPos.asLong());
        }
        for (int i = 0; i < CHANNELS; i++) {
            if (cameras[i] != null) {
                tag.putLong("Cam" + i, cameras[i].asLong());
                tag.putLong("CamT" + i, cameraTimes[i]);
            }
            tag.putBoolean("En" + i, chEnabled[i]);
            tag.putBoolean("Mi" + i, chMirror[i]);
            tag.putBoolean("Rc" + i, chRec[i]);
            tag.putString("Nm" + i, chName[i]);
        }
        tag.putString("DevName", deviceName);
        tag.putString("Pin", pin);
        tag.putInt("Layout", layout);
        tag.putInt("Page", page);
        tag.putBoolean("OsdName", osdName);
        tag.putBoolean("OsdTime", osdTime);
        tag.putBoolean("RecInd", recInd);
        tag.putInt("DateFmt", dateFmt);
        tag.putInt("Bright", brightness);
        tag.putBoolean("TimeReal", timeReal);
        tag.putInt("Tz", tzOffset);
        tag.putBoolean("RecAll", recAll);
        tag.putBoolean("Overwrite", overwrite);
        tag.putBoolean("Poe", poe);
        tag.putLong("Hdd", hddUsedMb);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        powerOnTime = tag.contains("PowerOn") ? tag.getLong("PowerOn") : -1L;
        monitorPos = tag.contains("Monitor") ? BlockPos.of(tag.getLong("Monitor")) : null;
        for (int i = 0; i < CHANNELS; i++) {
            if (tag.contains("Cam" + i)) {
                cameras[i] = BlockPos.of(tag.getLong("Cam" + i));
                cameraTimes[i] = tag.getLong("CamT" + i);
            } else {
                cameras[i] = null;
                cameraTimes[i] = -1L;
            }
            chEnabled[i] = !tag.contains("En" + i) || tag.getBoolean("En" + i);
            chMirror[i] = tag.getBoolean("Mi" + i);
            chRec[i] = !tag.contains("Rc" + i) || tag.getBoolean("Rc" + i);
            chName[i] = tag.getString("Nm" + i);
        }
        deviceName = tag.contains("DevName") ? tag.getString("DevName") : "XVR-16CH";
        pin = tag.getString("Pin");
        layout = tag.contains("Layout") ? tag.getInt("Layout") : 4;
        page = tag.getInt("Page");
        osdName = !tag.contains("OsdName") || tag.getBoolean("OsdName");
        osdTime = !tag.contains("OsdTime") || tag.getBoolean("OsdTime");
        recInd = !tag.contains("RecInd") || tag.getBoolean("RecInd");                  dateFmt = tag.getInt("DateFmt");
        brightness = tag.contains("Bright") ? tag.getInt("Bright") : 100;
        timeReal = !tag.contains("TimeReal") || tag.getBoolean("TimeReal");
        tzOffset = tag.getInt("Tz");
        recAll = !tag.contains("RecAll") || tag.getBoolean("RecAll");
        overwrite = !tag.contains("Overwrite") || tag.getBoolean("Overwrite");
        poe = !tag.contains("Poe") || tag.getBoolean("Poe");
        hddUsedMb = tag.getLong("Hdd");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        double minX = worldPosition.getX();
        double minY = worldPosition.getY();
        double minZ = worldPosition.getZ();
        double maxX = minX + 1;
        double maxY = minY + 1;
        double maxZ = minZ + 1;
        if (monitorPos != null) {
            minX = Math.min(minX, monitorPos.getX());
            minY = Math.min(minY, monitorPos.getY());
            minZ = Math.min(minZ, monitorPos.getZ());
            maxX = Math.max(maxX, monitorPos.getX() + 1);
            maxY = Math.max(maxY, monitorPos.getY() + 1);
            maxZ = Math.max(maxZ, monitorPos.getZ() + 1);
        }
        for (BlockPos c : cameras) {
            if (c != null) {
                minX = Math.min(minX, c.getX());
                minY = Math.min(minY, c.getY());
                minZ = Math.min(minZ, c.getZ());
                maxX = Math.max(maxX, c.getX() + 1);
                maxY = Math.max(maxY, c.getY() + 1);
                maxZ = Math.max(maxZ, c.getZ() + 1);
            }
        }
        return new AABB(minX - 1, minY - 3, minZ - 1, maxX + 1, maxY + 2, maxZ + 1);
    }
}
