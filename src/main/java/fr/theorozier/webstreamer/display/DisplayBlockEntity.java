package fr.theorozier.webstreamer.display;

import fr.theorozier.webstreamer.WebStreamerMod;
import fr.theorozier.webstreamer.display.source.DisplaySource;
import fr.theorozier.webstreamer.source.Source;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.net.MalformedURLException;
import java.net.URL;

public class DisplayBlockEntity extends BlockEntity {

    private DisplaySource displaySource;

    private float width = 1;
    private float height = 1;

    private Source source;

    private float widthOffset = 0f;
    private float heightOffset = 0f;

    public DisplayBlockEntity(BlockPos pos, BlockState state) {
        super(WebStreamerMod.DISPLAY_BLOCK_ENTITY, pos, state);
        this.setSourceUrl("https://video-weaver.mrs02.hls.ttvnw.net/v1/playlist/CoAF7BLZZQvxaWbP0FkXkOtSgtUjntOkGe4MekDt05VxPb-DDrX2Vp9rajxf84Unb-FsY4Kz5ofnTbzfhBeaOCMVcdfINW7xjFYpH37OUHgx8i5wXtar7Mi3ofMloewjojz4C85MdZ-VkU9FEfnFX2Uin1Ogwe2_hezu-w_iAYQCvo8V3FttZEtGNaz4fMegLkHHLzx8-lNOtbH_bhGYDmDWqquqCk4EGjowkiHH6wbbgdJR5E93Dd10pO_m6YbXc03IF8bdRaWqCivH_NCQfzqF0CyH3GaSfnYldVZPveFoidaPibpPrA0QSHgrxMvlxtcjFoFLje_Wo-HHUZCW0HjSD5phYMpUiCiljp8A6aN6Bu3hfeQB78eBpj7EPaVBBHuFGSYgak0RAcdAbHKDDMvZGpI_bamS2r3ifxhopHbUGWVMtKvQmwFQ0hL4RqrR3Pqc1EBjDF0OpJRBMUbv93Jt1aUT2K9pkGp6zKUY3S-qlBaH0XEZdkZaO7l5bebDClLktLaLwKXqu1KaGUMKKoawAV711gDIR6klpzmNltNi4_L_kk2VmEXeikKRxsUwnwT9PqKwcUhKPA0cZVJNdm0EAu-ne3VWlb0IDiti5h7sOKhw9akfIWhc4tirP2JZ-ufS7jPd7J4AMj-MGJWTQeWQkU9oSA_DQ-pspBdZD_1wcnnHAeLoQ7ccVGe5l4T-36jO5KkFW-FI0HOBoXIy1PzHXGivkJJoduyFaWOO9Sm9IzwcZQuz1cRbY40zoMWVSKDabSuqByeflMooNmSXZBrTKETFvIjp6FaDhthI18nTSF4dbszp-Tbu8HjkV6tD_F6ZiaDGmwpBf1UFqu6QnIJ4gBoMGKDr2a9bqsbq-8rRIAEqCWV1LXdlc3QtMjCuAw.m3u8");
        this.setWidth(16f / 4);
        this.setHeight(9f / 4);
    }

    public DisplaySource getDisplaySource() {
        return displaySource;
    }

    public void setDisplaySource(DisplaySource source) {
        this.displaySource = source;
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
