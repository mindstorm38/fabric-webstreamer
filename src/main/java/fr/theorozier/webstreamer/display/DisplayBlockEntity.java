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
        this.setSourceUrl("https://video-weaver.mrs02.hls.ttvnw.net/v1/playlist/CooFiD4-jgLBQVK_iTqGJmyyKWcM0dfKND7WOUZihkKTmf0bvpe9KCG-KG1D1LZjawqGJmI8Tyme-LQaFvJOfj4ODjo2M4tpKCoetA0192Ta_Fh3gVS99R1R3qIMbCDw8A_sEUClPuXMbpoFR5b1qtQO0IMNau_5y_I-KHJYdMHba8bkWoU6CiESQu68wJkeukJIePZ2mmU5YKUSifJK5fo9Of_GIntjHPnYKsXj7R-Civeri7JZBL1-RQ4cu4X7AW7PYmYDVGXRB9f3FmAv1Ilpg86lrWzAvDwck-NwVtukykN0nKWvhBBMDWgpwt9ESWXiYckB8-XLSz2kReIn_cuWS86VbbaIj9MvV_gKLmULRwZrhikskDbUBpeRMZ7kZvNBDyg8IEGowvVqW9ofYEhIHX9MZP60n-xMqso7KHap4cDDSMsDvxCyF73eLfvqgqLqQNm8yO3OAxyKvlpTZhYFltf8vcXSxY5CZ9l940TnqJ8P6zRronr1Om786ucBf-phE_KKLqN2tw76DbdLY_oUvBqrANcZXkoZvlcH3TRT65r41CqOSC2CzMZ7Xmt8P2mRyfdvwBhjRO1OaXfmCcdZEmONO3kDyGQLJjbJuuIUjqzxhqTJABUuGIZ7axiW3zk17SoOMQodIKHcWpljS9CgblVHRNG8rqHVXc9jbDLYBvyJM9-KyxXYY8NToE6LlYrQj7l1Rek9DlU6CWQncBZsggfYv1kHPNSgwb4X3MttdGOjXrxEeuHP0E8hn89k7w2sIUhtT7eI-vQx2c7GQWs-M6ap6lr-ubjBq8CZJuGhuHaPsfPuvYGvUluvoR2BHUMGxMfgRlw13O-OWlXE5ek5Ex_45B92XyUFkAUaDGqoye3VEBGmUv1y0iABKglldS13ZXN0LTIwrQM.m3u8");
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
