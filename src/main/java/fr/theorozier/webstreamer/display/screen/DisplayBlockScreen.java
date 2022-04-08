package fr.theorozier.webstreamer.display.screen;

import fr.theorozier.webstreamer.WebStreamerClientMod;
import fr.theorozier.webstreamer.display.DisplayBlockEntity;
import fr.theorozier.webstreamer.display.DisplayNetworking;
import fr.theorozier.webstreamer.display.source.DisplaySource;
import fr.theorozier.webstreamer.display.source.RawDisplaySource;
import fr.theorozier.webstreamer.display.source.TwitchDisplaySource;
import fr.theorozier.webstreamer.playlist.Playlist;
import fr.theorozier.webstreamer.playlist.PlaylistQuality;
import fr.theorozier.webstreamer.twitch.TwitchClient;
import fr.theorozier.webstreamer.util.AsyncProcessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ScreenTexts;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.NarratorManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class DisplayBlockScreen extends Screen {

    private static final Text CONF_TEXT = new TranslatableText("gui.webstreamer.display.conf");
    private static final Text WIDTH_TEXT = new TranslatableText("gui.webstreamer.display.width");
    private static final Text HEIGHT_TEXT = new TranslatableText("gui.webstreamer.display.height");
    private static final Text SOURCE_TYPE_TEXT = new TranslatableText("gui.webstreamer.display.sourceType");
    private static final Text SOURCE_TYPE_RAW_TEXT = new TranslatableText("gui.webstreamer.display.sourceType.raw");
    private static final Text SOURCE_TYPE_TWITCH_TEXT = new TranslatableText("gui.webstreamer.display.sourceType.twitch");
    private static final Text URL_TEXT = new TranslatableText("gui.webstreamer.display.url");
    private static final Text CHANNEL_TEXT = new TranslatableText("gui.webstreamer.display.channel");
    private static final Text MALFORMED_URL_TEXT = new TranslatableText("gui.webstreamer.display.malformedUrl");
    private static final Text NO_QUALITY_TEXT = new TranslatableText("gui.webstreamer.display.noQuality");
    private static final Text QUALITY_TEXT = new TranslatableText("gui.webstreamer.display.quality");
    private static final String AUDIO_DISTANCE_TEXT_KEY = "gui.webstreamer.display.audioDistance";
    private static final String AUDIO_VOLUME_TEXT_KEY = "gui.webstreamer.display.audioVolume";

    private static final Text ERR_NO_TOKEN_TEXT = new TranslatableText("gui.webstreamer.display.error.noToken");
    private static final Text ERR_CHANNEL_NOT_FOUND_TEXT = new TranslatableText("gui.webstreamer.display.error.channelNotFound");
    private static final Text ERR_CHANNEL_OFFLINE_TEXT = new TranslatableText("gui.webstreamer.display.error.channelOffline");
    private static final String ERR_UNKNOWN_TEXT_KEY = "gui.webstreamer.display.error.unknown";

    private final DisplayBlockEntity blockEntity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SourceScreen<?> sourceScreen;
    private SourceType sourceType;

    private int xHalf;
    private int yTop;
    private int ySourceTop;

    private TextFieldWidget displayWidthField;
    private TextFieldWidget displayHeightField;
    private CyclingButtonWidget<SourceType> sourceTypeButton;
    private AudioDistanceSliderWidget audioDistanceSlider;
    private AudioVolumeSliderWidget audioVolumeSlider;
    private ButtonWidget doneButton;
    private ButtonWidget cancelButton;

    private float displayWidth;
    private float displayHeight;
    private float displayAudioDistance;
    private float displayAudioVolume;

    public DisplayBlockScreen(DisplayBlockEntity blockEntity) {

        super(NarratorManager.EMPTY);
        this.blockEntity = blockEntity;

        DisplaySource source = blockEntity.getSource();
        if (source instanceof RawDisplaySource rawSource) {
            this.sourceType = SourceType.RAW;
            this.sourceScreen = new RawSourceScreen(new RawDisplaySource(rawSource));
        } else if (source instanceof TwitchDisplaySource twitchSource) {
            this.sourceType = SourceType.TWITCH;
            this.sourceScreen = new TwitchSourceScreen(new TwitchDisplaySource(twitchSource));
        } else {
            this.sourceType = SourceType.RAW;
            this.sourceScreen = new RawSourceScreen();
        }

        this.displayWidth = blockEntity.getWidth();
        this.displayHeight = blockEntity.getHeight();
        this.displayAudioDistance = blockEntity.getAudioDistance();
        this.displayAudioVolume = blockEntity.getAudioVolume();

    }

    private void setSourceScreen(SourceScreen<?> sourceScreen) {
        this.sourceScreen = sourceScreen;
        if (this.client != null) {
            this.init(this.client, this.width, this.height);
        }
    }

    @Override
    protected void init() {

        this.xHalf = this.width / 2;
        this.yTop = 60;
        this.ySourceTop = 130;

        String displayWidthRaw = this.displayWidthField == null ? Float.toString(this.displayWidth) : this.displayWidthField.getText();
        String displayHeightRaw = this.displayHeightField == null ? Float.toString(this.displayHeight) : this.displayHeightField.getText();

        this.displayWidthField = new TextFieldWidget(this.textRenderer, xHalf - 154, yTop + 11, 50, 18, LiteralText.EMPTY);
        this.displayWidthField.setText(displayWidthRaw);
        this.displayWidthField.setChangedListener(this::onDisplayWidthChanged);
        this.addDrawableChild(this.displayWidthField);
        this.addSelectableChild(this.displayWidthField);

        this.displayHeightField = new TextFieldWidget(this.textRenderer, xHalf - 96, yTop + 11, 50, 18, LiteralText.EMPTY);
        this.displayHeightField.setText(displayHeightRaw);
        this.displayHeightField.setChangedListener(this::onDisplayHeightChanged);
        this.addDrawableChild(this.displayHeightField);
        this.addSelectableChild(this.displayHeightField);

        this.sourceTypeButton = CyclingButtonWidget.builder(SourceType::getText)
                .values(SourceType.values())
                .build(xHalf - 38, yTop + 10, 192, 20, SOURCE_TYPE_TEXT, this::onSourceTypeChanged);

        this.sourceTypeButton.setValue(this.sourceType);
        this.addDrawableChild(this.sourceTypeButton);

        this.audioDistanceSlider = new AudioDistanceSliderWidget(xHalf - 154, yTop + 36, 150, 20, this.displayAudioDistance, 64);
        this.audioDistanceSlider.setChangedListener(dist -> this.displayAudioDistance = dist);
        this.addDrawableChild(this.audioDistanceSlider);

        this.audioVolumeSlider = new AudioVolumeSliderWidget(xHalf + 4, yTop + 36, 150, 20, this.displayAudioVolume);
        this.audioVolumeSlider.setChangedListener(volume -> this.displayAudioVolume = volume);
        this.addDrawableChild(this.audioVolumeSlider);
        
        this.doneButton = new ButtonWidget(xHalf - 4 - 150, height / 4 + 120 + 12, 150, 20, ScreenTexts.DONE, button -> {
            this.commitAndClose();
        });
        this.addDrawableChild(this.doneButton);

        this.cancelButton = new ButtonWidget(xHalf + 4, height / 4 + 120 + 12, 150, 20, ScreenTexts.CANCEL, button -> {
            this.close();
        });
        this.addDrawableChild(this.cancelButton);

        if (this.sourceScreen != null) {
            this.sourceScreen.init();
        }

    }

    private void onDisplayWidthChanged(String widthRaw) {
        try {
            this.displayWidth = Float.parseFloat(widthRaw);
        } catch (NumberFormatException e) {
            this.displayWidth = Float.NaN;
        }
    }

    private void onDisplayHeightChanged(String heightRaw) {
        try {
            this.displayHeight = Float.parseFloat(heightRaw);
        } catch (NumberFormatException e) {
            this.displayHeight = Float.NaN;
        }
    }

    private void onSourceTypeChanged(CyclingButtonWidget<SourceType> button, SourceType type) {
        if (type != this.sourceType) {
            this.sourceType = type;
            this.setSourceScreen(switch (type) {
                case RAW -> new RawSourceScreen();
                case TWITCH -> new TwitchSourceScreen();
            });
        }
    }

    private void commitAndClose() {
        if (Float.isFinite(this.displayWidth) && Float.isFinite(this.displayHeight)) {
            this.blockEntity.setSize(this.displayWidth, this.displayHeight);
            this.blockEntity.setAudioConfig(this.displayAudioDistance, this.displayAudioVolume);
            if (this.sourceScreen != null) {
                this.blockEntity.setSource(this.sourceScreen.source);
            }
            DisplayNetworking.sendDisplayUpdate(this.blockEntity);
        }
        this.close();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        drawCenteredText(matrices, this.textRenderer, CONF_TEXT, xHalf, 20, 0xFFFFFF);
        drawTextWithShadow(matrices, this.textRenderer, WIDTH_TEXT, xHalf - 154, yTop + 1, 0xA0A0A0);
        drawTextWithShadow(matrices, this.textRenderer, HEIGHT_TEXT, xHalf - 96, yTop + 1, 0xA0A0A0);
        if (this.sourceScreen != null) {
            this.sourceScreen.render(matrices, mouseX, mouseY, delta);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        super.tick();
        this.displayWidthField.tick();
        this.displayHeightField.tick();
        this.doneButton.active = Float.isFinite(this.displayWidth) && Float.isFinite(this.displayHeight);
        if (this.sourceScreen != null) {
            this.sourceScreen.tick();
            if (this.doneButton.active && !this.sourceScreen.valid()) {
                this.doneButton.active = false;
            }
        }
    }

    private enum SourceType {

        RAW(SOURCE_TYPE_RAW_TEXT),
        TWITCH(SOURCE_TYPE_TWITCH_TEXT);

        private final Text text;
        SourceType(Text text) {
            this.text = text;
        }

        public Text getText() {
            return text;
        }

    }

    /**
     * A basic source screen.
     */
    private abstract static class SourceScreen<S extends DisplaySource> implements Drawable {

        protected final S source;

        SourceScreen(S source) {
            this.source = source;
        }

        abstract boolean valid();
        abstract void init();
        abstract void tick();

    }

    /**
     * Screen for raw sources.
     */
    private class RawSourceScreen extends SourceScreen<RawDisplaySource> {

        private TextFieldWidget urlField;

        private final AsyncProcessor<String, URI, IllegalArgumentException> asyncUrl = new AsyncProcessor<>(URI::create);

        RawSourceScreen(RawDisplaySource source) {
            super(source);
        }

        RawSourceScreen() {
            this(new RawDisplaySource());
        }

        @Override
        public boolean valid() {
            return this.asyncUrl.idle();
        }

        @Override
        public void init() {

            boolean first = (this.urlField == null);

            this.urlField = new TextFieldWidget(textRenderer, xHalf - 154, ySourceTop + 10, 308, 20, this.urlField, LiteralText.EMPTY);
            this.urlField.setMaxLength(32000);
            this.urlField.setChangedListener(this::onUrlChanged);
            addSelectableChild(this.urlField);
            setInitialFocus(this.urlField);
            addDrawableChild(this.urlField);

            if (first) {
                this.urlField.setText(Objects.toString(this.source.getUri(), ""));
            }

        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            drawTextWithShadow(matrices, textRenderer, URL_TEXT, xHalf - 154, ySourceTop, 0xA0A0A0);
            if (this.source.getUri() == null) {
                drawCenteredText(matrices, textRenderer, MALFORMED_URL_TEXT, xHalf, ySourceTop + 50, 0xFF6052);
            }
        }

        @Override
        public void tick() {
            this.urlField.tick();
            this.asyncUrl.fetch(executor, this.source::setUri, exc -> this.source.setUri(null));
        }

        private void onUrlChanged(String rawUrl) {
            this.asyncUrl.push(rawUrl);
        }

    }

    private class TwitchSourceScreen extends SourceScreen<TwitchDisplaySource> {

        private TextFieldWidget channelField;
        private QualitySliderWidget qualitySlider;
        
        private PlaylistQuality firstQuality;

        private final AsyncProcessor<String, Playlist, TwitchClient.PlaylistException> asyncPlaylist;
        private Playlist playlist;
        private Text playlistError;

        TwitchSourceScreen(TwitchDisplaySource source) {
            super(source);
            this.firstQuality = source.getQuality();
            this.asyncPlaylist = new AsyncProcessor<>(WebStreamerClientMod.TWITCH_CLIENT::requestPlaylist);
        }

        TwitchSourceScreen() {
            this(new TwitchDisplaySource());
        }

        @Override
        public boolean valid() {
            return this.asyncPlaylist.idle();
        }

        @Override
        public void init() {

            boolean first = (this.channelField == null);

            this.channelField = new TextFieldWidget(textRenderer, xHalf - 154, ySourceTop + 10, 308, 20, this.channelField, LiteralText.EMPTY);
            this.channelField.setMaxLength(64);
            this.channelField.setChangedListener(this::onChannelChanged);
            addSelectableChild(this.channelField);
            setInitialFocus(this.channelField);
            addDrawableChild(this.channelField);
    
            this.qualitySlider = new QualitySliderWidget(xHalf - 154, ySourceTop + 50, 308, 20, this.qualitySlider);
            this.qualitySlider.setChangedListener(this::onQualityChanged);
            this.updateQualitySlider();
            addSelectableChild(this.qualitySlider);
            addDrawableChild(this.qualitySlider);

            if (first) {
                this.channelField.setText(Objects.toString(this.source.getChannel(), ""));
            }

        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            drawTextWithShadow(matrices, textRenderer, CHANNEL_TEXT, xHalf - 154, ySourceTop, 0xA0A0A0);
            if (this.playlistError == null) {
                drawTextWithShadow(matrices, textRenderer, QUALITY_TEXT, xHalf - 154, ySourceTop + 40, 0xA0A0A0);
            } else {
                drawCenteredText(matrices, textRenderer, this.playlistError, xHalf, ySourceTop + 50, 0xFF6052);
            }
        }

        @Override
        public void tick() {

            this.channelField.tick();

            this.asyncPlaylist.fetch(executor, pl -> {
                this.playlist = pl;
                this.qualitySlider.setQualities(pl.getQualities());
                if (this.firstQuality != null) {
                    this.qualitySlider.setQuality(this.firstQuality);
                    this.firstQuality = null;
                }
                this.playlistError = null;
                this.updateQualitySlider();
            }, exc -> {
                this.playlist = null;
                this.qualitySlider.setQualities(null);
                this.playlistError = switch (exc.getExceptionType()) {
                    case UNKNOWN -> new TranslatableText(ERR_UNKNOWN_TEXT_KEY, "");
                    case NO_TOKEN -> ERR_NO_TOKEN_TEXT;
                    case CHANNEL_NOT_FOUND -> ERR_CHANNEL_NOT_FOUND_TEXT;
                    case CHANNEL_OFFLINE -> ERR_CHANNEL_OFFLINE_TEXT;
                };
                this.updateQualitySlider();
            });

        }

        private void onChannelChanged(String channel) {
            this.asyncPlaylist.push(channel);
        }

        private void onQualityChanged(PlaylistQuality quality) {
            if (quality == null) {
                this.source.clearChannelQuality();
            } else if (this.playlist != null) {
                this.source.setChannelQuality(this.playlist.getChannel(), quality);
            }
        }

        private void updateQualitySlider() {
            this.qualitySlider.visible = (this.playlistError == null);
        }

    }

    private static class QualitySliderWidget extends SliderWidget {

        private int qualityIndex = -1;
        private List<PlaylistQuality> qualities;
        private Consumer<PlaylistQuality> changedListener;

        public QualitySliderWidget(int x, int y, int width, int height, QualitySliderWidget previousSlider) {
            super(x, y, width, height, LiteralText.EMPTY, 0.0);
            if (previousSlider != null) {
                this.setQualities(previousSlider.qualities);
                this.qualityIndex = previousSlider.qualityIndex;
                this.value = (double) this.qualityIndex  / (double) (this.qualities.size() - 1);
                this.updateMessage();
            } else {
                this.setQualities(null);
            }
        }
    
        public void setQualities(List<PlaylistQuality> qualities) {
            this.qualities = qualities;
            this.applyValue();
            this.updateMessage();
        }
        
        public void setQuality(PlaylistQuality quality) {
            for (int i = 0; i < this.qualities.size(); i++) {
                if (this.qualities.get(i).name().equals(quality.name())) {
                    this.qualityIndex = i;
                    this.value = (double) this.qualityIndex  / (double) (this.qualities.size() - 1);
                    this.updateMessage();
                    if (this.changedListener != null) {
                        this.changedListener.accept(this.qualities.get(i));
                    }
                    return;
                }
            }
        }

        public void setChangedListener(Consumer<PlaylistQuality> changedListener) {
            this.changedListener = changedListener;
        }

        @Override
        protected void updateMessage() {
            if (this.qualityIndex < 0) {
                this.setMessage(NO_QUALITY_TEXT);
            } else {
                this.setMessage(new LiteralText(this.qualities.get(this.qualityIndex).name()));
            }
        }

        @Override
        protected void applyValue() {
            if (this.qualities == null || this.qualities.isEmpty()) {
                this.value = 0.0;
                this.qualityIndex = -1;
                this.active = false;
                if (this.changedListener != null) {
                    this.changedListener.accept(null);
                }
            } else {
                this.qualityIndex = (int) Math.round(this.value * (this.qualities.size() - 1));
                this.value = (double) this.qualityIndex  / (double) (this.qualities.size() - 1);
                this.active = true;
                if (this.changedListener != null) {
                    this.changedListener.accept(this.qualities.get(this.qualityIndex));
                }
            }
        }

    }
    
    private static class AudioDistanceSliderWidget extends SliderWidget {
        
        private final float maxDistance;
        private Consumer<Float> changedListener;
        
        public AudioDistanceSliderWidget(int x, int y, int width, int height, float distance, float maxDistance) {
            super(x, y, width, height, LiteralText.EMPTY, distance / maxDistance);
            this.maxDistance = maxDistance;
            this.updateMessage();
        }
        
        public void setChangedListener(Consumer<Float> changedListener) {
            this.changedListener = changedListener;
        }
        
        private float getDistance() {
            return (float) (this.value * this.maxDistance);
        }
        
        @Override
        protected void updateMessage() {
            this.setMessage(new TranslatableText(AUDIO_DISTANCE_TEXT_KEY).append(": ").append(Integer.toString((int) this.getDistance())));
        }
        
        @Override
        protected void applyValue() {
            this.changedListener.accept(this.getDistance());
        }
        
    }
    
    private static class AudioVolumeSliderWidget extends SliderWidget {
        
        private Consumer<Float> changedListener;
    
        public AudioVolumeSliderWidget(int x, int y, int width, int height, float value) {
            super(x, y, width, height, LiteralText.EMPTY, value);
            this.updateMessage();
        }
    
        public void setChangedListener(Consumer<Float> changedListener) {
            this.changedListener = changedListener;
        }
    
        @Override
        protected void updateMessage() {
            Text text = (this.value == this.getYImage(false)) ? ScreenTexts.OFF : new LiteralText((int)(this.value * 100.0) + "%");
            this.setMessage(new TranslatableText(AUDIO_VOLUME_TEXT_KEY).append(": ").append(text));
        }
    
        @Override
        protected void applyValue() {
            this.changedListener.accept((float) this.value);
        }
        
    }

}
