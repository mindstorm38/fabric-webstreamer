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
        this.setSourceUrl("https://video-weaver.mrs02.hls.ttvnw.net/v1/playlist/CoMFGdsL05q12Rvq4rjYqR7pBHPL3as4xKyU3K6HzyrEWbGwqMAbdi5VwTBqqip2DvFK6-gnRMgn_eBYaw9sscP7CzkhPtLYfyvsMWrrMw3_kzf4_NGB4hDsROqNmntQYKFcxBW6sfcBD_sY2cRnyf-EqkFF3fZrVzj65WZ6zRmrYK_e6Q4o1VI_vXwxErGHqX8VAGPAg1QZ33idyAc1NckoPX5IvhiK2PltyMw8jEfBuA_eonrg0J0274YHWuIZRyLUyzQObwSoasYIH589f2zJjM106S7WkP3RGkTJgr4m0uo7y_Ercgd144lemXwcDm_8AAj93N-nkqcyUBQJLAxX3qt3aB7EdVjsAaUYLmTkkrFjUvjlmRfvo_CoWNSPmaXvxHff6C4SfsGzDuLHGOpIk-1f59IBmfyGtogf_RCIsqUym7PmJq16bmc4K6LBOf85zRYiUBmmcmyoPKKZWZF0ae91VN3ASpOpNijq8GF-f4HrQNOR7lG9dViDmFbSR29IhsWGpvN_SgKIXdoSaZcO3Bil8_loXLsMJMRrYqQ1BARDZshmMpxMNpbTZsZTwhRy-PZ_9xYErs_j6iXeN9CdazKgbYERYHzLoxOogMK-Ma_ecfrDRu1plh2C4Ox_kvr1L07_lD6aQ9MkIxD-53W8yxDzRdz4OLXF2vlqyxKtr2Cp4nmvd1ohFwlcsIGpAiHlVGis-0Y_kPymbV0UHdS8GnJ03tl06dhHyqGhSXl99owjzzKWmbaiUXJcCCIoYQmC-2d7A_0b6eeezvCL_8bVewZcx9WoXMBu9ID1l6hOHPgGIYMqnSybYj9ulB2uck8TXh_xExDgZSPhAc4c4CjRoGl13BoM4-fUKy9i2iYWe_ttIAEqCWV1LXdlc3QtMjCsAw.m3u8");
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
