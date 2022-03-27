package fr.theorozier.webstreamer.display;

import fr.theorozier.webstreamer.WebStreamerMod;
import fr.theorozier.webstreamer.source.Source;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.net.MalformedURLException;
import java.net.URL;

public class DisplayBlockEntity extends BlockEntity {

    private int width = 1, height = 1;
    private Source source;

    public DisplayBlockEntity(BlockPos pos, BlockState state) {
        super(WebStreamerMod.DISPLAY_BLOCK_ENTITY, pos, state);
        this.setSourceUrl("https://video-weaver.mrs02.hls.ttvnw.net/v1/playlist/CooF6w-DJQEcvg-A19oP3y15F3lnpGu7TtTXLamiSoJDjeOa6Q-g-V8jpZ40a3P_7ijdc3ytypwwDSrNx1NJ4gGSssUx1sHrRcIZNLWvEZ6XHPlvgbaxk3pVETZSpgHXtsSvx_YSkKG5zDKRGIJueZG2B9W0BjINYhfGtSk2uiKYDxPKdsKyFgt2AO59AXF9o66CxHePAgBEqzUdYmuYLgffG4BT7zwmcKGoaNyH3HDbbf2__KP768XyUL7bfsE8v9-OyfsMOGpvbi6n3-MHOHR0ZJWxANlihm9o_crODYhEA9R1uxl4eMEtoMOziOzkq1ksuxIoAs7XtgwkxfFFgH0dcUt4Fd3ngDKfVBpOainbK1A4Vh8YqyP40h5R1TiXKmJAPI_FoP9ynyenN2KbOnGFrZAlM6W1JP3GjuTRLntOgJRzIWQtj1DmQwINHmjhXbfo0njlhNJTt2egGSWu9SXQKN7XNSJw6QDj2jadTnwy_pzwmW8lC7xkkN5bW0wGOlYRGZLFuDwZfFQysT9GMnoutUKjxeUpIUIkhktdvuyKoyThWSb9MNH2li_8NPp7UvToWn7A7UGJXsa5Bnked7wCgLtnwY97D-BpmJRhOzSm1viaYP3PKG8pWE-PSaaChpzwlA2-EW5V4gWmXm5EgZrVnT0akh4BUIAzvDRoJHzg6dT8I2ipyqoAAy07nlzXVXzmUSZ4srUbE6jKnB-_la_THwMhDRQk4XRmrtjutsFPq6kKgV5Zat6vDiG8Cyy5qpJ0yipIAd_jZdvZQ14GuJxkwIj2Lu4fIf6mxNKA-sxK8QtrWB2oWcSDevAJ1YegBLY-i91Gj3oxfmW-AWtwizXe3FIHNgpDeQkNZusaDDKu_jM2IIImOAhU9iABKglldS13ZXN0LTIwqwM.m3u8");
    }

    public Source getSource() {
        return source;
    }

    public void setSourceUrl(String url) {
        try {
            this.source = new Source(0, new URL(url));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
    }

}
