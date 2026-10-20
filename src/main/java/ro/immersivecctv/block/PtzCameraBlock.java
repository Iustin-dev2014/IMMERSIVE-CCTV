package ro.immersivecctv.block;

/** PTZ camera variant; it shares the physical PoE link and block entity but exposes pan/tilt controls. */
public final class PtzCameraBlock extends CameraBlock {
    public PtzCameraBlock(Properties properties) { super(properties); }
}
