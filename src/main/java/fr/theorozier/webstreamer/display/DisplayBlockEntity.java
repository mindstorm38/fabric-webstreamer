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
        this.setSourceUrl("https://video-weaver.mrs02.hls.ttvnw.net/v1/playlist/CqsFaP0A5R5vzXeEKG6t8s5d-aaiNVkACA9LgImV0lYTHjncGkoTbIi6Nc0rbKKelb9noLyIra1b15e4dCItDZ1tevkK6ExWxaKbrGs4BMte4glmk8ex-STYzT6S0lqfVvzozeAz6eHTI2o9m-mZXBAqWeCXYVxHfhKBCIAi_h9_kj3O0OFpjDcgvbqhq6YG6Tfn4iPhSd3ED1-bhCAWSrg13291zceCR-DENxm2L8jSNnOxrQ8sDTnWTA32CBouqim-PkwtPz_Pt6RaN9P8k7xG3H7AvWA4DER63ibJvQOWw87I1akH3sOj0-TMBtF978U_JkT-Ky-fQE0B3Glt9R5DjewiqJ8Mm5gaLYw4nhAvglaQUsG4Y07h_XSWYEnD30JZSt6rbhfxYtZsWew0klXjldHTsI1_sn1TYMFSnsXAwLCcqXt6wjpxkX_BbwPo2muXdMewAsuXYU2NjDuX-VZwHp3KPuopRfW1R1IRepdMB9uaT-xEodTckwKYUxDnuzwOQTWDYhjIR3DORL2njENYMGP8Th9BU3EW2mqW9Ogul1PVFvCVP23o9J1XOsVAqL5Vd32i9AvLRYQ5Rq8LSLcXs2UwQ0v2igCGsc6rs7OgBD2DI4TsawNMFDHXOcsMMT_-GjGdT7LDhJCS4GJQRSaJIRMOnNqWLzv4FtxwU5OpQAuRs7EhTttRFBcvLn6fT7L8_OV9d6dULyST5vGXmYuyi0_4hLbUQW-2Az1UfRqcjZmGw6Ykj6BCklA_oMfoA_QnZ1VQpR3RDX_7a1o-9_Xlphq0ppdg6T4xbFTHWLji18OqAz_LseuwSFcUOuO9Oi8as71n7vNZXyvX3iC1kvDJuRzTobwBOxz0pH0Twq09fVSjwGpUXGr1i7dhaZmAM5YP1zoFLak80PQ6JtsaDMSb8fsh6sk8jAl_FiABKglldS13ZXN0LTIwqwM.m3u8");
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
