package fr.theorozier.webstreamer.source;

public class Source {

    private final int id;
    private final String url;

    public Source(int id, String url) {

        this.id = id;
        this.url = url;

    }

    public int getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

}
