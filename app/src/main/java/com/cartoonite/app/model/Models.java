package com.cartoonite.app.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Modelli dati dell'app. */
public class Models {

    public static class Title implements Serializable {
        public String id;
        public String title;
        public String url;
        public String category;
        public String imageUrl;

        public Title(String id, String title, String url, String category) {
            this.id = id;
            this.title = title;
            this.url = url;
            this.category = category;
        }
    }

    public static class Player implements Serializable {
        public String name;
        public String url;

        public Player(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    public static class Episode implements Serializable {
        public int number;
        public String title;
        public List<Player> players = new ArrayList<>();

        public Episode(int number, String title) {
            this.number = number;
            this.title = title;
        }
    }

    public static class Season implements Serializable {
        public String name;
        public List<Episode> episodes = new ArrayList<>();

        public Season(String name) {
            this.name = name;
        }
    }

    public static class TitleDetail implements Serializable {
        public String id;
        public String title;
        public String url;
        public String category;
        public String image;
        public String description;
        public String originalTitle;
        public String status;
        public String totalEpisodes;
        public String country;
        public String year;
        public List<Season> seasons = new ArrayList<>();

        public int episodeCount() {
            int n = 0;
            for (Season s : seasons) n += s.episodes.size();
            return n;
        }
    }
}
