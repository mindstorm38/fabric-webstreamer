package fr.theorozier.webstreamer.display.source;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class TwitchDisplaySource implements DisplaySource {

    private String channel;
    private URL url;

    public void setChannelAndUrl(String channel, URL url) {
        this.channel = channel;
        this.url = url;
    }

    public void clearChannelAndUrl() {
        this.channel = null;
        this.url = null;
    }

    public String getChannel() {
        return channel;
    }

    @Override
    public URL getUrl() {
        return this.url;
    }

    // Playlist //

    public record PlaylistQuality(String name, URL url) {}

    public static class Playlist {

        private final String channel;
        private final ArrayList<PlaylistQuality> qualities = new ArrayList<>();

        Playlist(String channel) {
            this.channel = channel;
        }

        public String getChannel() {
            return channel;
        }

        public List<PlaylistQuality> getQualities() {
            return qualities;
        }

    }

    public enum PlaylistExceptionType {
        UNKNOWN,
        NO_TOKEN,
        CHANNEL_NOT_FOUND,
        CHANNEL_OFFLINE,
    }

    public static class PlaylistException extends Exception {

        private final PlaylistExceptionType exceptionType;
        public PlaylistException(PlaylistExceptionType exceptionType) {
            super(exceptionType.name());
            this.exceptionType = exceptionType;
        }

        public PlaylistExceptionType getExceptionType() {
            return exceptionType;
        }

    }

    private static final Gson GSON = new GsonBuilder().create();
    private static final String TWITCH_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko";

    private static Playlist requestPlaylistInternal(String channel) throws PlaylistException, IOException, URISyntaxException, InterruptedException {

        if (channel.isEmpty()) {
            throw new PlaylistException(PlaylistExceptionType.CHANNEL_NOT_FOUND);
        }

        URI gqlUri = new URL("https://gql.twitch.tv/gql").toURI();

        HttpClient client = HttpClient.newHttpClient();

        JsonObject body = new JsonObject();
        body.addProperty("operationName", "PlaybackAccessToken");
        JsonObject extensions = new JsonObject();
        JsonObject persistedQuery = new JsonObject();
        persistedQuery.addProperty("version", 1);
        persistedQuery.addProperty("sha256Hash", "0828119ded1c13477966434e15800ff57ddacf13ba1911c129dc2200705b0712");
        extensions.add("persistedQuery", persistedQuery);
        body.add("extensions", extensions);
        JsonObject variables = new JsonObject();
        variables.addProperty("isLive", true);
        variables.addProperty("login", channel);
        variables.addProperty("isVod", false);
        variables.addProperty("vodID", "");
        variables.addProperty("playerType", "embed");
        body.add("variables", variables);

        HttpRequest tokenReq = HttpRequest.newBuilder(gqlUri)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .header("Accept", "application/json")
                .headers("Client-id", TWITCH_CLIENT_ID)
                .build();

        HttpResponse<String> tokenRes = client.send(tokenReq, HttpResponse.BodyHandlers.ofString());

        if (tokenRes.statusCode() != 200) {
            throw new PlaylistException(PlaylistExceptionType.NO_TOKEN);
        }

        JsonObject tokenResJson = GSON.fromJson(tokenRes.body(), JsonObject.class);
        JsonElement tokenRaw = tokenResJson.getAsJsonObject("data").get("streamPlaybackAccessToken");
        if (!tokenRaw.isJsonObject()) {
            throw new PlaylistException(PlaylistExceptionType.CHANNEL_NOT_FOUND);
        }

        JsonObject token = tokenRaw.getAsJsonObject();

        String tokenValue = URLEncoder.encode(token.get("value").getAsString(), StandardCharsets.UTF_8);
        String tokenSignature = token.get("signature").getAsString();
        URI urlsUri = new URL("https://usher.ttvnw.net/api/channel/hls/" + channel + ".m3u8?client_id=" + TWITCH_CLIENT_ID + "&token=" + tokenValue + "&sig=" + tokenSignature + "&allow_source=true&allow_audio_only=false").toURI();

        HttpRequest urlsReq = HttpRequest.newBuilder(urlsUri)
                .GET()
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> urlsRes = client.send(urlsReq, HttpResponse.BodyHandlers.ofString());

        if (urlsRes.statusCode() == 404) {
            if (urlsRes.body().contains("Can not find channel")) {
                throw new PlaylistException(PlaylistExceptionType.CHANNEL_NOT_FOUND);
            } else {
                throw new PlaylistException(PlaylistExceptionType.CHANNEL_OFFLINE);
            }
        } else if (urlsRes.statusCode() != 200) {
            throw new PlaylistException(PlaylistExceptionType.UNKNOWN);
        }

        String raw = urlsRes.body();
        List<String> rawLines = raw.lines().toList();

        Playlist playlist = new Playlist(channel);

        for (int i = 4; i < rawLines.size(); i += 3) {
            String line0 = rawLines.get(i);
            String line2 = rawLines.get(i - 2);
            int qualityNameStartIdx = line2.indexOf("NAME=\"");
            int qualityNameStopIdx = line2.indexOf('"', qualityNameStartIdx + 6);
            String qualityName = line2.substring(qualityNameStartIdx + 6, qualityNameStopIdx);
            playlist.qualities.add(new PlaylistQuality(qualityName, new URL(line0)));
        }

        return playlist;

    }

    public static Playlist requestPlaylist(String channel) throws PlaylistException {
        try {
            return requestPlaylistInternal(channel);
        } catch (IOException | URISyntaxException | InterruptedException e) {
            e.printStackTrace();
            throw new PlaylistException(PlaylistExceptionType.UNKNOWN);
        }
    }

}
