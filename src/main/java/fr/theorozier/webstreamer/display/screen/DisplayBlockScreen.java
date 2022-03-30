package fr.theorozier.webstreamer.display.screen;

import fr.theorozier.webstreamer.display.DisplayBlockEntity;
import fr.theorozier.webstreamer.display.source.DisplaySource;
import fr.theorozier.webstreamer.display.source.RawDisplaySource;
import fr.theorozier.webstreamer.display.source.TwitchDisplaySource;
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

import java.net.URL;
import java.util.List;
import java.util.concurrent.*;

public class DisplayBlockScreen extends Screen {

    private static final Text DISPLAY_TEXT = new TranslatableText("webstreamer.display.display");
    private static final Text SOURCE_TYPE_TEXT = new TranslatableText("webstreamer.display.sourceType");
    private static final Text SOURCE_TYPE_RAW_TEXT = new TranslatableText("webstreamer.display.sourceType.raw");
    private static final Text SOURCE_TYPE_TWITCH_TEXT = new TranslatableText("webstreamer.display.sourceType.twitch");
    private static final Text URL_TEXT = new TranslatableText("webstreamer.display.url");
    private static final Text CHANNEL_TEXT = new TranslatableText("webstreamer.display.channel");
    private static final Text MALFORMED_URL_TEXT = new TranslatableText("webstreamer.display.malformedUrl");
    private static final Text NO_QUALITY_TEXT = new TranslatableText("webstreamer.display.noQuality");
    private static final Text QUALITY_TEXT = new TranslatableText("webstreamer.display.quality");

    private static final Text ERR_NO_TOKEN_TEXT = new TranslatableText("webstreamer.display.error.noToken");
    private static final Text ERR_CHANNEL_NOT_FOUND_TEXT = new TranslatableText("webstreamer.display.error.channelNotFound");
    private static final Text ERR_CHANNEL_OFFLINE_TEXT = new TranslatableText("webstreamer.display.error.channelOffline");
    private static final String ERR_UNKNOWN_TEXT_KEY = "webstreamer.display.error.unknown";

    private final DisplayBlockEntity blockEntity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SourceScreen sourceScreen;
    private SourceType sourceType;

    private CyclingButtonWidget<SourceType> sourceTypeButton;
    private ButtonWidget doneButton;
    private ButtonWidget cancelButton;

    public DisplayBlockScreen(DisplayBlockEntity blockEntity) {

        super(NarratorManager.EMPTY);
        this.blockEntity = blockEntity;

        DisplaySource source = blockEntity.getDisplaySource();
        if (source instanceof RawDisplaySource rawSource) {
            this.sourceType = SourceType.RAW;
            this.sourceScreen = new RawSourceScreen(rawSource);
        } else if (source instanceof TwitchDisplaySource twitchSource) {
            this.sourceType = SourceType.TWITCH;
            this.sourceScreen = new TwitchSourceScreen(twitchSource);
        } else {
            this.sourceType = SourceType.RAW;
            this.sourceScreen = new RawSourceScreen();
        }

    }

    private void setSourceScreen(SourceScreen sourceScreen) {
        this.sourceScreen = sourceScreen;
        if (this.client != null) {
            this.init(this.client, this.width, this.height);
        }
    }

    @Override
    protected void init() {

        this.sourceTypeButton = CyclingButtonWidget.<SourceType>builder(type -> switch (type) {
            case TWITCH -> SOURCE_TYPE_TWITCH_TEXT;
            default -> SOURCE_TYPE_RAW_TEXT;
        }).values(SourceType.values()).build(this.width / 2 - 4 - 150, this.height / 4 + 120 - 16, 308, 20, SOURCE_TYPE_TEXT, this::onSourceTypeChanged);
        this.sourceTypeButton.setValue(this.sourceType);

        this.doneButton = new ButtonWidget(this.width / 2 - 4 - 150, this.height / 4 + 120 + 12, 150, 20, ScreenTexts.DONE, button -> {
            // this.commitAndClose();
        });

        this.cancelButton = new ButtonWidget(this.width / 2 + 4, this.height / 4 + 120 + 12, 150, 20, ScreenTexts.CANCEL, button -> {
            this.close();
        });

        this.addDrawableChild(this.sourceTypeButton);
        this.addDrawableChild(this.doneButton);
        this.addDrawableChild(this.cancelButton);

        if (this.sourceScreen != null) {
            this.sourceScreen.init();
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

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        drawCenteredText(matrices, this.textRenderer, DISPLAY_TEXT, this.width / 2, 20, 0xFFFFFF);
        if (this.sourceScreen != null) {
            this.sourceScreen.render(matrices, mouseX, mouseY, delta);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.sourceScreen != null) {
            this.sourceScreen.tick();
            this.doneButton.active = this.sourceScreen.valid();
        }
    }

    private enum SourceType {
        RAW,
        TWITCH
    }

    /**
     * A basic source screen.
     */
    private interface SourceScreen extends Drawable {
        DisplaySource source();
        boolean valid();
        void init();
        void tick();
    }

    /**
     * Screen for raw sources.
     */
    private class RawSourceScreen implements SourceScreen {

        private final RawDisplaySource source;

        private Future<URL> urlFuture;
        private String urlRaw;

        RawSourceScreen(RawDisplaySource source) {
            this.source = source;
        }

        RawSourceScreen() {
            this(new RawDisplaySource());
        }

        @Override
        public DisplaySource source() {
            return this.source;
        }

        @Override
        public boolean valid() {
            return this.urlFuture == null;
        }

        @Override
        public void init() {
            TextFieldWidget urlTextField = new TextFieldWidget(textRenderer, width / 2 - 150, 50, 300, 20, URL_TEXT);
            urlTextField.setMaxLength(32000);
            urlTextField.setChangedListener(this::onUrlChanged);
            addSelectableChild(urlTextField);
            setInitialFocus(urlTextField);
            addDrawableChild(urlTextField);
        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            drawTextWithShadow(matrices, textRenderer, URL_TEXT, width / 2 - 150, 40, 0xA0A0A0);
            if (this.source.getUrl() == null) {
                drawCenteredText(matrices, textRenderer, MALFORMED_URL_TEXT, width / 2, 80, 0xFF6052);
            }
        }

        @Override
        public void tick() {

            if (this.urlFuture != null && this.urlFuture.isDone()) {
                try {
                    this.source.setUrl(this.urlFuture.get());
                } catch (InterruptedException ignored) {
                    // Ignore to try again
                } catch (ExecutionException | CancellationException e) {
                    this.source.setUrl(null);
                }
                this.urlFuture = null;
            }

            if (this.urlRaw != null && this.urlFuture == null) {
                String urlRaw = this.urlRaw;
                this.urlRaw = null;
                this.urlFuture = executor.submit(() -> new URL(urlRaw));
            }

        }

        private void onUrlChanged(String rawUrl) {
            this.urlRaw = rawUrl;
        }

    }

    private class TwitchSourceScreen implements SourceScreen {

        private final TwitchDisplaySource source;

        private QualitySliderWidget qualitySlider;

        private Future<TwitchDisplaySource.Playlist> playlistFuture;
        private String playlistChannel;
        private Text playlistError;

        TwitchSourceScreen(TwitchDisplaySource source) {
            this.source = source;
        }

        TwitchSourceScreen() {
            this(new TwitchDisplaySource());
        }

        @Override
        public DisplaySource source() {
            return this.source;
        }

        @Override
        public boolean valid() {
            return this.playlistFuture == null;
        }

        @Override
        public void init() {

            TextFieldWidget channelTextField = new TextFieldWidget(textRenderer, width / 2 - 150, 50, 300, 20, CHANNEL_TEXT);
            channelTextField.setMaxLength(32000);
            channelTextField.setChangedListener(this::onChannelChanged);
            addSelectableChild(channelTextField);
            setInitialFocus(channelTextField);
            addDrawableChild(channelTextField);

            this.qualitySlider = new QualitySliderWidget(width / 2 - 150, 110, 300, 20);
            addSelectableChild(this.qualitySlider);
            addDrawableChild(this.qualitySlider);

        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            drawTextWithShadow(matrices, textRenderer, CHANNEL_TEXT, width / 2 - 150, 40, 0xA0A0A0);
            drawTextWithShadow(matrices, textRenderer, QUALITY_TEXT, width / 2 - 150, 100, 0xA0A0A0);
            if (this.playlistError != null) {
                drawCenteredText(matrices, textRenderer, this.playlistError, width / 2, 80, 0xFF6052);
            }
        }

        @Override
        public void tick() {

            if (this.playlistFuture != null && this.playlistFuture.isDone()) {
                this.playlistError = null;
                try {
                    this.source.setPlaylist(this.playlistFuture.get());
                    this.qualitySlider.setQualities(this.source.getPlaylist().getQualities());
                } catch (InterruptedException ignored) {
                    // Ignore to try again
                } catch (ExecutionException | CancellationException e) {
                    this.source.setPlaylist(null);
                    this.qualitySlider.setQualities(null);
                    Throwable cause = e.getCause();
                    if (cause != null) {
                        cause.printStackTrace();
                        if (cause instanceof TwitchDisplaySource.PlaylistException ple) {
                            this.playlistError = switch (ple.getExceptionType()) {
                                case UNKNOWN -> new TranslatableText(ERR_UNKNOWN_TEXT_KEY, "");
                                case NO_TOKEN -> ERR_NO_TOKEN_TEXT;
                                case CHANNEL_NOT_FOUND -> ERR_CHANNEL_NOT_FOUND_TEXT;
                                case CHANNEL_OFFLINE -> ERR_CHANNEL_OFFLINE_TEXT;
                            };
                        } else {
                            this.playlistError = new TranslatableText(ERR_UNKNOWN_TEXT_KEY, cause.getMessage());
                        }
                    }
                }
                this.playlistFuture = null;
            }

            if (this.playlistFuture == null && this.playlistChannel != null) {
                this.playlistFuture = TwitchDisplaySource.requestPlaylist(executor, this.playlistChannel);
                this.playlistChannel = null;
            }

        }

        private void onChannelChanged(String channel) {
            this.playlistChannel = channel;
        }

    }

    private static class QualitySliderWidget extends SliderWidget {

        private List<TwitchDisplaySource.PlaylistQuality> qualities;
        private int qualityIndex = -1;

        public QualitySliderWidget(int x, int y, int width, int height) {
            super(x, y, width, height, LiteralText.EMPTY, 0.0);
            this.setQualities(null);
        }

        public void setQualities(List<TwitchDisplaySource.PlaylistQuality> qualities) {
            this.qualities = qualities;
            this.applyValue();
            this.updateMessage();
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
            } else {
                this.qualityIndex = (int) Math.round(this.value * (this.qualities.size() - 1));
                this.value = (double) this.qualityIndex  / (double) (this.qualities.size() - 1);
                this.active = true;
            }
        }

    }

}
