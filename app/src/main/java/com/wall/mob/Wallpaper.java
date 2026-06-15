package com.wall.mob;

import android.os.Parcel;
import android.os.Parcelable;
import java.io.Serializable;

public class Wallpaper implements Parcelable, Serializable {
    private String id;
    private String imageUrl;
    private String thumbnailUrl;
    private String title;
    private String category;
    private String source;
    private String photographer;
    private boolean isPremium;
    private long addedAt;

    public long getAddedAt() { return addedAt; }
    public void setAddedAt(long addedAt) { this.addedAt = addedAt; }

    public Wallpaper(String id, String imageUrl, String thumbnailUrl, String title, String category, String source, String photographer, boolean isPremium) {
        this.id = id;
        this.imageUrl = imageUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.title = title;
        this.category = category;
        this.source = source;
        this.photographer = photographer;
        this.isPremium = isPremium;
    }

    public Wallpaper(String id, String imageUrl, String title, String category, String source, String photographer, boolean isPremium) {
        this(id, imageUrl, null, title, category, source, photographer, isPremium);
    }

    public Wallpaper(String id, String imageUrl, String title, String category, String source, boolean isPremium) {
        this(id, imageUrl, title, category, source, null, isPremium);
    }

    public Wallpaper(String id, String imageUrl, String thumbnailUrl, String title, String category, String source, String photographer) {
        this(id, imageUrl, thumbnailUrl, title, category, source, photographer, false);
    }

    public Wallpaper(String id, String imageUrl, String title, String category, String source, String photographer) {
        this(id, imageUrl, title, category, source, photographer, false);
    }

    public Wallpaper(String id, String imageUrl, String title, String category, String source) {
        this(id, imageUrl, title, category, source, false);
    }

    public Wallpaper() {
        // Required for Firebase
    }

    protected Wallpaper(Parcel in) {
        id = in.readString();
        imageUrl = in.readString();
        thumbnailUrl = in.readString();
        title = in.readString();
        category = in.readString();
        source = in.readString();
        photographer = in.readString();
        isPremium = in.readByte() != 0;
        addedAt = in.readLong(); // ← added
    }

    public static final Creator<Wallpaper> CREATOR = new Creator<Wallpaper>() {
        @Override
        public Wallpaper createFromParcel(Parcel in) {
            return new Wallpaper(in);
        }

        @Override
        public Wallpaper[] newArray(int size) {
            return new Wallpaper[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(imageUrl);
        dest.writeString(thumbnailUrl);
        dest.writeString(title);
        dest.writeString(category);
        dest.writeString(source);
        dest.writeString(photographer);
        dest.writeByte((byte) (isPremium ? 1 : 0));
        dest.writeLong(addedAt); // ← added
    }

    public String getId() { return id; }
    public String getImageUrl() { return imageUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getSource() { return source; }
    public String getPhotographer() { return photographer; }
    public boolean isPremium() { return isPremium; }

    public void setId(String id) { this.id = id; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public void setTitle(String title) { this.title = title; }
    public void setCategory(String category) { this.category = category; }
    public void setSource(String source) { this.source = source; }
    public void setPhotographer(String photographer) { this.photographer = photographer; }
    public void setPremium(boolean isPremium) { this.isPremium = isPremium; }

    @Override
    public String toString() {
        return "Wallpaper{" +
                "id='" + id + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", thumbnailUrl='" + thumbnailUrl + '\'' +
                ", title='" + title + '\'' +
                ", category='" + category + '\'' +
                ", source='" + source + '\'' +
                ", photographer='" + photographer + '\'' +
                ", isPremium=" + isPremium +
                ", addedAt=" + addedAt +
                '}';
    }
}
