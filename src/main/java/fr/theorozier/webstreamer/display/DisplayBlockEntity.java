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

    private float width = 1;
    private float height = 1;
    private Source source;

    private float widthOffset = 0f;
    private float heightOffset = 0f;

    public DisplayBlockEntity(BlockPos pos, BlockState state) {
        super(WebStreamerMod.DISPLAY_BLOCK_ENTITY, pos, state);
        this.setSourceUrl("https://video-weaver.mrs02.hls.ttvnw.net/v1/playlist/Cv4ESK-A7H1zrtNT-HFq6dy5RYZIgcQ9MqGExrE0njyAZXFCND7SGTUfC48obcKwwqxWnroBeNNrhgIFsHjm6GjmSq-PBl0Rvx9xpnxL3ojHc8P6CKChqw0WNBy46vgoUDKXedTLS7InqtYKTLEhp_FJl0rw5hqs831cQvbGGIVgr0PcPcbwX6GXqJ7EDIgFls15Nj4GZeYmJXjznq-OoEPd1n5pyMu1t_1m09N3SCBDkFBw7J18cqpbv71ofGq1E6D3GS2ExEXoIpUdzYHBaPfPBEE2CZHQ2VChvqEFM9TlVq3bYK9P903pjrqhbXEI2XhfWqFJ5WidLcQPv_rGyZWB29NB6_lXVStYh-H75f-bSfNS0G7RR_Wv8z-5KA9o-92-UIGQo_HDpWbSRe93H5UCKFdYvuJ4mkSZpHLp1VKlkXPto1kYc6eCd5S7de_CtD8C5JKzQAll-yyy_umX3hYJt5TFkAOx6cIHssXE-Kj7WOnJt9ixwb13sSANHEBnHue87A2QddSfdAqqYFWWIQSc45vGM4f0OqjcQ_vT4ydrDQk6c6QhoMq23tC62WxGzDIwkQAiW9S8xx-ygymOZmU9PE9DEQHhLOwsnTzsav7N6XUPAFAXlB1TYABh3sc6Gi338qkrGYY26XNwWsN5Fh_G46GGRSVpJJs5tHGLIgqT0KADJeqJk9bw6Eu-Vy-omPTUVUO4c9KFvFYu7WkpdKAbwbK-PhNo7IgsXwB65XM94xOkagBAyTPFCQZR0KqOTutITH_K3juMkLw9QCgTmMBV61tsVKGNV61TMOs5NPEBJWPSCGuJmqWLcLy3A9qXnlW5JLU1vhyGdEqet7SVSzAaDBThENgp4HEx29o50CABKglldS13ZXN0LTIwrAM.m3u8");
        this.setWidth(16f / 4);
        this.setHeight(9f / 4);
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

    public void setWidth(float width) {
        this.width = width;
        this.computeWidthOffset();
    }

    public void setHeight(float height) {
        this.height = height;
        this.computeHeightOffset();
    }

    private void computeWidthOffset() {
        this.widthOffset = (this.width - 1) / -2f;
    }

    private void computeHeightOffset() {
        this.heightOffset = (this.height - 1) / -2f;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public float getWidthOffset() {
        return widthOffset;
    }

    public float getHeightOffset() {
        return heightOffset;
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
