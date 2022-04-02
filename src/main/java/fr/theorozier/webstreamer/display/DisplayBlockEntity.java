package fr.theorozier.webstreamer.display;

import fr.theorozier.webstreamer.WebStreamerClientMod;
import fr.theorozier.webstreamer.WebStreamerMod;
import fr.theorozier.webstreamer.display.client.DisplayUrl;
import fr.theorozier.webstreamer.display.source.DisplaySource;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.net.URL;

public class DisplayBlockEntity extends BlockEntity {
    
    private DisplaySource source;
    
    @Environment(EnvType.CLIENT)
    private DisplayUrl url;

    private float width = 1;
    private float height = 1;

    private float widthOffset = 0f;
    private float heightOffset = 0f;

    public DisplayBlockEntity(BlockPos pos, BlockState state) {
        super(WebStreamerMod.DISPLAY_BLOCK_ENTITY, pos, state);
    }
    
    @Environment(EnvType.CLIENT)
    public void setSource(DisplaySource source) {
        this.source = source;
        this.url = null;
        if (source != null) {
            URL urlRaw = source.getUrl();
            if (urlRaw != null) {
                this.url = new DisplayUrl(urlRaw, WebStreamerClientMod.DISPLAY_URLS.allocUrl(urlRaw));
            }
        }
    }
    
    @Environment(EnvType.CLIENT)
    public DisplaySource getSource() {
        return source;
    }
    
    @Environment(EnvType.CLIENT)
    public DisplayUrl getUrl() {
        return url;
    }

    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
        this.computeOffsets();
    }

    private void computeOffsets() {
        this.widthOffset = (this.width - 1) / -2f;
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
        if (this.source != null) {
            this.source.writeNbt(nbt);
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (this.source != null) {
            this.source.readNbt(nbt);
        }
    }

}
