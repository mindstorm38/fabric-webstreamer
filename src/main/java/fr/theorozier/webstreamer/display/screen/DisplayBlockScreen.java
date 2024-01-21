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
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.net.URISyntaxException;
import java.util.function.Consumer;
import java.util.concurrent.*;
import java.util.List;
import java.net.URI;

/**
 * <p>This screen is the GUI that the player can use to configure a display block 
 * entity.</p>
 */
@Environment(EnvType.CLIENT)
public class DisplayBlockScreen extends Screen {

    private static final Text CONF_TEXT = Text.translatable("gui.webstreamer.display.conf");
    private static final Text WIDTH_TEXT = Text.translatable("gui.webstreamer.display.width");
    private static final Text HEIGHT_TEXT = Text.translatable("gui.webstreamer.display.height");
    private static final Text SOURCE_TYPE_TEXT = Text.translatable("gui.webstreamer.display.sourceType");
    private static final Text SOURCE_TYPE_RAW_TEXT = Text.translatable("gui.webstreamer.display.sourceType.raw");
    private static final Text SOURCE_TYPE_TWITCH_TEXT = Text.translatable("gui.webstreamer.display.sourceType.twitch");
    private static final Text URL_TEXT = Text.translatable("gui.webstreamer.display.url");
    private static final Text CHANNEL_TEXT = Text.translatable("gui.webstreamer.display.channel");
    private static final Text NO_QUALITY_TEXT = Text.translatable("gui.webstreamer.display.noQuality");
    private static final Text QUALITY_TEXT = Text.translatable("gui.webstreamer.display.quality");
    private static final String AUDIO_DISTANCE_TEXT_KEY = "gui.webstreamer.display.audioDistance";
    private static final String AUDIO_VOLUME_TEXT_KEY = "gui.webstreamer.display.audioVolume";

    private static final Text ERR_PENDING = Text.translatable("gui.webstreamer.display.error.pending");
    private static final Text ERR_INVALID_SIZE = Text.translatable("gui.webstreamer.display.error.invalidSize");
    private static final Text ERR_TWITCH = Text.translatable("gui.webstreamer.display.error.twitch");
    private static final Text ERR_NO_TOKEN_TEXT = Text.translatable("gui.webstreamer.display.error.noToken");
    private static final Text ERR_CHANNEL_NOT_FOUND_TEXT = Text.translatable("gui.webstreamer.display.error.channelNotFound");
    private static final Text ERR_CHANNEL_OFFLINE_TEXT = Text.translatable("gui.webstreamer.display.error.channelOffline");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AsyncProcessor<String, Playlist, TwitchClient.PlaylistException> asyncPlaylist = new AsyncProcessor<>(WebStreamerClientMod.TWITCH_CLIENT::requestPlaylist, false);

    /** The block entity this screen is opened on. The following fields are temporaries to save later. */
    private final DisplayBlockEntity display;

    private TextFieldWidget widthField, heightField;
    private AudioDistanceSliderWidget audioDistanceSlider;
    private AudioVolumeSliderWidget audioVolumeSlider;
    private CyclingButtonWidget<SourceType> sourceTypeButton;
    private TextWidget errorText;

    private TextFieldWidget rawUriField;

    private TextFieldWidget twitchChannelField;
    private QualitySliderWidget twitchQualitySlider;
    private TextWidget twitchQualityText;
    private Playlist twitchPlaylist;
    private TwitchClient.PlaylistException twitchPlaylistExc;

    private boolean dirty;
    private ButtonWidget doneButton;

    public DisplayBlockScreen(DisplayBlockEntity display) {
        super(ScreenTexts.EMPTY);
        this.display = display;
    }

    @Override
    protected void init() {

        int xHalf = this.width / 2;
        int yTop = 60;

        if (this.height < 270) {
            yTop = 35;
        }

        TextWidget confText = new TextWidget(this.width, 0, CONF_TEXT, this.textRenderer);
        confText.setPosition(0, 20);
        confText.setTextColor(0xFFFFFF);
        this.addDrawableChild(confText);

        TextWidget widthText = new TextWidget(WIDTH_TEXT, this.textRenderer);
        widthText.setPosition(xHalf - 154, yTop + 1);
        widthText.setTextColor(0xA0A0A0);
        widthText.alignLeft();
        this.addDrawableChild(widthText);

        TextWidget heightText = new TextWidget(HEIGHT_TEXT, this.textRenderer);
        heightText.setPosition(xHalf - 96, yTop + 1);
        heightText.setTextColor(0xA0A0A0);
        heightText.alignLeft();
        this.addDrawableChild(heightText);

        String widthVal = widthField == null ? Float.toString(this.display.getWidth()) : widthField.getText();
        widthField = new TextFieldWidget(this.textRenderer, xHalf - 154, yTop + 11, 50, 18, Text.empty());
        widthField.setText(widthVal);
        widthField.setChangedListener(val -> this.dirty = true);
        this.addDrawableChild(widthField);

        String heightVal = heightField == null ? Float.toString(this.display.getHeight()) : heightField.getText();
        heightField = new TextFieldWidget(this.textRenderer, xHalf - 96, yTop + 11, 50, 18, Text.empty());
        heightField.setText(heightVal);
        heightField.setChangedListener(val -> this.dirty = true);
        this.addDrawableChild(heightField);

        DisplaySource source = this.display.getSource();
        SourceType sourceType = SourceType.RAW;
        if (sourceTypeButton != null) {
            sourceType = sourceTypeButton.getValue();
        } else if (source instanceof TwitchDisplaySource) {
            sourceType = SourceType.TWITCH;
        }

        sourceTypeButton = CyclingButtonWidget.builder(SourceType::getText)
                .values(SourceType.values())
                .build(xHalf - 38, yTop + 10, 192, 20, SOURCE_TYPE_TEXT, (widget, val) -> {
                    // When cycling, we reset the UI to adapt for the new source type.
                    if (this.client != null) {
                        this.init(this.client, this.width, this.height);
                    }
                });
        sourceTypeButton.setValue(sourceType);
        this.addDrawableChild(sourceTypeButton);

        float audioDistanceVal = audioDistanceSlider == null ? this.display.getAudioDistance() : audioDistanceSlider.getDistance();
        audioDistanceSlider = new AudioDistanceSliderWidget(xHalf - 154, yTop + 36, 150, 20, audioDistanceVal, 64);
        audioDistanceSlider.setChangedListener(val -> this.dirty = true);
        this.addDrawableChild(audioDistanceSlider);

        float audioVolumeVal = audioVolumeSlider == null ? this.display.getAudioVolume() : audioVolumeSlider.getVolume();
        audioVolumeSlider = new AudioVolumeSliderWidget(xHalf + 4, yTop + 36, 150, 20, audioVolumeVal);
        audioVolumeSlider.setChangedListener(val -> this.dirty = true);
        this.addDrawableChild(audioVolumeSlider);

        int ySourceTop = yTop + 70;
        int ySourceBottom = ySourceTop;

        if (sourceType == SourceType.RAW) {

            TextWidget uriText = new TextWidget(URL_TEXT, this.textRenderer);
            uriText.setPosition(xHalf - 154, ySourceTop);
            uriText.setTextColor(0xA0A0A0);
            uriText.alignLeft();
            this.addDrawableChild(uriText);

            String rawUriVal = "";
            if (rawUriField != null) {
                rawUriVal = rawUriField.getText();
            } else if (source instanceof RawDisplaySource rawSource) {
                if (rawSource.getUri() != null) {
                    rawUriVal = rawSource.getUri().toString();
                }
            }
            rawUriField = new TextFieldWidget(this.textRenderer, xHalf - 154, ySourceTop + 10, 308, 20, Text.empty());
            rawUriField.setMaxLength(32000);
            rawUriField.setText(rawUriVal);
            rawUriField.setChangedListener(val -> this.dirty = true);
            this.addDrawableChild(rawUriField);

            ySourceBottom += 10 + 40;

        } else if (sourceType == SourceType.TWITCH) {

            TextWidget channelText = new TextWidget(CHANNEL_TEXT, this.textRenderer);
            channelText.setPosition(xHalf - 154, ySourceTop);
            channelText.setTextColor(0xA0A0A0);
            channelText.alignLeft();
            this.addDrawableChild(channelText);

            String twitchChannelVal = "";
            if (twitchChannelField != null) {
                twitchChannelVal = twitchChannelField.getText();
            } else if (source instanceof TwitchDisplaySource twitchSource) {
                twitchChannelVal = twitchSource.getChannel();
                this.asyncPlaylist.push(twitchChannelVal);
            } else {
                this.asyncPlaylist.push("");
            }

            twitchChannelField = new TextFieldWidget(this.textRenderer, xHalf - 154, ySourceTop + 10, 308, 20, Text.empty());
            twitchChannelField.setMaxLength(64);
            twitchChannelField.setText(twitchChannelVal);
            twitchChannelField.setChangedListener(val -> {
                this.asyncPlaylist.push(val);
                this.dirty = true;
            });
            this.addDrawableChild(this.twitchChannelField);

            twitchQualityText = new TextWidget(QUALITY_TEXT, this.textRenderer);
            twitchQualityText.setPosition(xHalf - 154, ySourceTop + 40);
            twitchQualityText.setTextColor(0xA0A0A0);
            twitchQualityText.alignLeft();
            this.addDrawableChild(twitchQualityText);

            twitchQualitySlider = new QualitySliderWidget(xHalf - 154, ySourceTop + 50, 308, 20, twitchQualitySlider);
            twitchQualitySlider.setChangedListener(val -> this.dirty = true);
            this.addDrawableChild(twitchQualitySlider);

            ySourceBottom += 50 + 40;

        }

        errorText = new TextWidget(Text.empty(), this.textRenderer);
        errorText.setDimensionsAndPosition(this.width, 0, 0, ySourceBottom);
        errorText.setTextColor(0xFF6052);
        errorText.visible = false;
        this.addDrawableChild(errorText);

        int yButtonTop = Math.min(Math.max(height / 4 + 120 + 12, ySourceBottom + 20), this.height - 25);

        doneButton = ButtonWidget.builder(ScreenTexts.DONE, button -> this.commitAndClose())
                .dimensions(xHalf - 4 - 150, yButtonTop, 150, 20)
                .build();
        doneButton.active = false;
        this.addDrawableChild(doneButton);

        ButtonWidget cancelButton = ButtonWidget.builder(ScreenTexts.CANCEL, button -> this.cancelAndClose())
                .dimensions(xHalf + 4, yButtonTop, 150, 20)
                .build();
        this.addDrawableChild(cancelButton);

        this.dirty = true;

    }

    /**
     * Show that the current configuration is valid and the "done" button can be pressed.
     */
    private void showValid() {
        this.doneButton.active = true;
        this.errorText.visible = false;
    }

    /**
     * Show that the current configuration contains an error and the "done" button cannot be presed.
     * @param message The message for the error.
     */
    private void showError(Text message) {
        this.doneButton.active = false;
        this.errorText.setMessage(message);
        this.errorText.visible = true;
    }

    /**
     * Internal function to refresh the state of the "done" button, to activate it only if inputs are valid.
     * @param commit Set to true in order to commit these changes to the display block entity and send an update.
     * @return True if the configuration is valid and, if requested, has been committed.
     */
    private boolean refresh(boolean commit) {

        float width, height;

        try {
            width = Float.parseFloat(this.widthField.getText());
            height = Float.parseFloat(this.heightField.getText());
        } catch (NumberFormatException e) {
            this.showError(ERR_INVALID_SIZE);
            return false;
        }

        URI rawUri = null;
        String twitchChannel = null;
        String twitchQuality = null;

        SourceType sourceType = this.sourceTypeButton.getValue();

        if (sourceType == SourceType.RAW) {

            String rawUriVal = this.rawUriField.getText();
            if (!rawUriVal.isEmpty()) {
                try {
                    rawUri = new URI(rawUriVal);
                } catch (URISyntaxException e) {
                    this.showError(Text.literal(e.getMessage()));
                    return false;
                }
            }

        } else if (sourceType == SourceType.TWITCH) {

            if (this.asyncPlaylist.requested() || !this.asyncPlaylist.idle()) {
                this.showError(ERR_PENDING);
                return false;
            } else if (this.twitchPlaylistExc != null) {
                this.showError(switch (this.twitchPlaylistExc.getExceptionType()) {
                    case UNKNOWN -> ERR_TWITCH;
                    case NO_TOKEN -> ERR_NO_TOKEN_TEXT;
                    case CHANNEL_NOT_FOUND -> ERR_CHANNEL_NOT_FOUND_TEXT;
                    case CHANNEL_OFFLINE -> ERR_CHANNEL_OFFLINE_TEXT;
                });
                return false;
            } else if (this.twitchPlaylist == null) {
                this.showError(Text.empty());
                return false;
            }

            this.twitchQualitySlider.visible = true;
            this.twitchQualityText.visible = true;

            PlaylistQuality twitchQualityRaw = this.twitchQualitySlider.getQuality();
            if (twitchQualityRaw == null) {
                throw new IllegalStateException("twitch quality should be present if playlist is present");
            }

            twitchChannel = this.twitchPlaylist.getChannel();
            twitchQuality = twitchQualityRaw.name();

        }

        this.showValid();

        if (commit) {

            this.display.setSize(width, height);

            float audioDistance = this.audioDistanceSlider.getDistance();
            float audioVolume = this.audioVolumeSlider.getVolume();
            this.display.setAudioConfig(audioDistance, audioVolume);

            if (sourceType == SourceType.RAW) {
                this.display.setSource(new RawDisplaySource(rawUri));
            } else if (sourceType == SourceType.TWITCH) {
                this.display.setSource(new TwitchDisplaySource(twitchChannel, twitchQuality));
            }

            DisplayNetworking.sendDisplayUpdate(this.display);

        }

        return true;

    }

    /**
     * Internal function to commit the current values to the display block and then close the window. Nothing is
     * committed if any value is invalid.
     */
    private void commitAndClose() {
        if (this.refresh(true)) {
            this.close();
        }
    }

    /**
     * Internal function to cancel configuration and close the screen.
     */
    private void cancelAndClose() {
        this.close();
    }

    @Override
    public void tick() {

        super.tick();

        SourceType sourceType = this.sourceTypeButton.getValue();

        if (sourceType == SourceType.TWITCH) {
            this.asyncPlaylist.fetch(this.executor, pl -> {
                boolean wasSet = this.twitchQualitySlider.getQuality() != null;
                this.twitchPlaylist = pl;
                this.twitchPlaylistExc = null;
                this.twitchQualitySlider.setQualities(pl.getQualities());
                // If the slider was new and the current source is a twitch one, set its quality.
                if (!wasSet && this.display.getSource() instanceof TwitchDisplaySource twitchSource) {
                    this.twitchQualitySlider.setQuality(twitchSource.getQuality());
                }
                this.dirty = true;
            }, exc -> {
                this.twitchPlaylist = null;
                this.twitchPlaylistExc = exc;
                this.twitchQualitySlider.setQualities(null);
                this.dirty = true;
            });
        } else {
            this.asyncPlaylist.fetch(this.executor, pl -> {}, exc -> {});
        }

        if (this.dirty) {
            this.refresh(false);
            this.dirty = false;
        }

    }

    /**
     * Internal enumeration for the cycling button between the different menu mode depending on source type.
     */
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

//    /**
//     * A basic source screen.
//     */
//    private abstract static class SourceScreen<S extends DisplaySource> {
//
//        protected final S source;
//
//        SourceScreen(S source) {
//            this.source = source;
//        }
//
//        abstract boolean valid();
//        abstract void init();
//        abstract void tick();
//
//    }
//
//    /**
//     * Screen for raw sources.
//     */
//    private class RawSourceScreen extends SourceScreen<RawDisplaySource> {
//
//        private TextWidget urlText;
//        private TextWidget malformedUrlText;
//        private TextFieldWidget urlField;
//
//        private final AsyncProcessor<String, URI, IllegalArgumentException> asyncUri = new AsyncProcessor<>(URI::create);
//
//        RawSourceScreen(RawDisplaySource source) {
//            super(source);
//        }
//
//        RawSourceScreen() {
//            this(new RawDisplaySource());
//        }
//
//        @Override
//        public boolean valid() {
//            return this.asyncUri.idle();
//        }
//
//        @Override
//        public void init() {
//
//            boolean first = (this.urlField == null);
//
//            this.urlText = new TextWidget(xHalf - 154, ySourceTop, URL_TEXT, textRenderer);
//            this.urlText.setTextColor(0xA0A0A0);
//            addDrawableChild(this.urlText);
//
//            this.malformedUrlText = new TextWidget(xHalf, ySourceTop + 50, MALFORMED_URL_TEXT, textRenderer);
//            this.malformedUrlText.setTextColor(0xFF6052);
//            addDrawableChild(this.malformedUrlText);
//
//            this.urlField = new TextFieldWidget(textRenderer, xHalf - 154, ySourceTop + 10, 308, 20, this.urlField, Text.empty());
//            this.urlField.setMaxLength(32000);
//            this.urlField.setChangedListener(this::onUrlChanged);
//            setInitialFocus(this.urlField);
//            addDrawableChild(this.urlField);
//
//            if (first) {
//                this.urlField.setText(Objects.toString(this.source.getUri(), ""));
//            }
//
//        }
//
//        @Override
//        public void tick() {
//            this.asyncUri.fetch(executor, this.source::setUri, exc -> this.source.setUri(null));
//            this.urlText.visible = (this.source.getUri() != null);
//        }
//
//        private void onUrlChanged(String rawUrl) {
//            this.asyncUri.push(rawUrl);
//        }
//
//    }
//
//    private class TwitchSourceScreen extends SourceScreen<TwitchDisplaySource> {
//
//        private TextFieldWidget channelField;
//        private QualitySliderWidget qualitySlider;
//
//        private String firstQuality;
//
//        private final AsyncProcessor<String, Playlist, TwitchClient.PlaylistException> asyncPlaylist;
//        private Playlist playlist;
//        private Text playlistError;
//
//        TwitchSourceScreen(TwitchDisplaySource source) {
//            super(source);
//            this.firstQuality = source.getQuality();
//            this.asyncPlaylist = new AsyncProcessor<>(WebStreamerClientMod.TWITCH_CLIENT::requestPlaylist);
//        }
//
//        TwitchSourceScreen() {
//            this(new TwitchDisplaySource());
//        }
//
//        @Override
//        public boolean valid() {
//            return this.asyncPlaylist.idle();
//        }
//
//        @Override
//        public void init() {
//
//            boolean first = (this.channelField == null);
//
//            this.channelField = new TextFieldWidget(textRenderer, xHalf - 154, ySourceTop + 10, 308, 20, this.channelField, Text.empty());
//            this.channelField.setMaxLength(64);
//            this.channelField.setChangedListener(this::onChannelChanged);
//            addSelectableChild(this.channelField);
//            setInitialFocus(this.channelField);
//            addDrawableChild(this.channelField);
//
//            this.qualitySlider = new QualitySliderWidget(xHalf - 154, ySourceTop + 50, 308, 20, this.qualitySlider);
//            this.qualitySlider.setChangedListener(this::onQualityChanged);
//            this.updateQualitySlider();
//            addSelectableChild(this.qualitySlider);
//            addDrawableChild(this.qualitySlider);
//
//            if (first) {
//                this.channelField.setText(Objects.toString(this.source.getChannel(), ""));
//            }
//
//        }
//
//        @Override
//        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
//            drawTextWithShadow(matrices, textRenderer, CHANNEL_TEXT, xHalf - 154, ySourceTop, 0xA0A0A0);
//            if (this.playlistError == null) {
//                drawTextWithShadow(matrices, textRenderer, QUALITY_TEXT, xHalf - 154, ySourceTop + 40, 0xA0A0A0);
//            } else {
//                drawCenteredText(matrices, textRenderer, this.playlistError, xHalf, ySourceTop + 50, 0xFF6052);
//            }
//        }
//
//        @Override
//        public void tick() {
//
//            this.channelField.tick();
//
//            this.asyncPlaylist.fetch(executor, pl -> {
//                this.playlist = pl;
//                this.qualitySlider.setQualities(pl.getQualities());
//                if (this.firstQuality != null) {
//                    this.qualitySlider.setQuality(this.firstQuality);
//                    this.firstQuality = null;
//                }
//                this.playlistError = null;
//                this.updateQualitySlider();
//            }, exc -> {
//                this.playlist = null;
//                this.qualitySlider.setQualities(null);
//                this.playlistError = switch (exc.getExceptionType()) {
//                    case UNKNOWN -> Text.translatable(ERR_UNKNOWN_TEXT_KEY, "");
//                    case NO_TOKEN -> ERR_NO_TOKEN_TEXT;
//                    case CHANNEL_NOT_FOUND -> ERR_CHANNEL_NOT_FOUND_TEXT;
//                    case CHANNEL_OFFLINE -> ERR_CHANNEL_OFFLINE_TEXT;
//                };
//                this.updateQualitySlider();
//            });
//
//        }
//
//        private void onChannelChanged(String channel) {
//            this.asyncPlaylist.push(channel);
//        }
//
//        private void onQualityChanged(PlaylistQuality quality) {
//            if (quality == null) {
//                this.source.clearChannelQuality();
//            } else if (this.playlist != null) {
//                this.source.setChannelQuality(this.playlist.getChannel(), quality.name());
//            }
//        }
//
//        private void updateQualitySlider() {
//            this.qualitySlider.visible = (this.playlistError == null);
//        }
//
//    }

    /**
     * Internal class for specializing the slider widget for playlist quality.
     */
    private static class QualitySliderWidget extends SliderWidget {

        private int qualityIndex = -1;
        private List<PlaylistQuality> qualities;
        private Consumer<PlaylistQuality> changedListener;

        public QualitySliderWidget(int x, int y, int width, int height, QualitySliderWidget previousSlider) {
            super(x, y, width, height, Text.empty(), 0.0);
            if (previousSlider != null && previousSlider.qualities != null) {
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
        
        public void setQuality(String quality) {
            for (int i = 0; i < this.qualities.size(); i++) {
                if (this.qualities.get(i).name().equals(quality)) {
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

        public PlaylistQuality getQuality() {
            if (this.qualities == null) {
                return null;
            }
            try {
                return this.qualities.get(this.qualityIndex);
            } catch (IndexOutOfBoundsException e) {
                return null;
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
                this.setMessage(Text.literal(this.qualities.get(this.qualityIndex).name()));
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

    /**
     * Custom slider widget for audio distance to block entity.
     */
    private static class AudioDistanceSliderWidget extends SliderWidget {
        
        private final float maxDistance;
        private Consumer<Float> changedListener;
        
        public AudioDistanceSliderWidget(int x, int y, int width, int height, float distance, float maxDistance) {
            super(x, y, width, height, Text.empty(), distance / maxDistance);
            this.maxDistance = maxDistance;
            this.updateMessage();
        }
        
        public void setChangedListener(Consumer<Float> changedListener) {
            this.changedListener = changedListener;
        }
        
        public float getDistance() {
            return (float) (this.value * this.maxDistance);
        }
        
        @Override
        protected void updateMessage() {
            this.setMessage(Text.translatable(AUDIO_DISTANCE_TEXT_KEY).append(": ").append(Integer.toString((int) this.getDistance())));
        }
        
        @Override
        protected void applyValue() {
            this.changedListener.accept(this.getDistance());
        }
        
    }

    /**
     * Custom slider widget for audio volume from block entity.
     */
    private static class AudioVolumeSliderWidget extends SliderWidget {
        
        private Consumer<Float> changedListener;
    
        public AudioVolumeSliderWidget(int x, int y, int width, int height, float value) {
            super(x, y, width, height, Text.empty(), value);
            this.updateMessage();
        }
    
        public void setChangedListener(Consumer<Float> changedListener) {
            this.changedListener = changedListener;
        }

        public float getVolume() {
            return (float) this.value;
        }
    
        @Override
        protected void updateMessage() {
            Text text = (this.value == 0.0) ? ScreenTexts.OFF : Text.literal((int)(this.value * 100.0) + "%");
            this.setMessage(Text.translatable(AUDIO_VOLUME_TEXT_KEY).append(": ").append(text));
        }
    
        @Override
        protected void applyValue() {
            this.changedListener.accept((float) this.value);
        }
        
    }

}
