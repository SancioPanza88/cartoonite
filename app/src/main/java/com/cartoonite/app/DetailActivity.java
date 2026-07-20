package com.cartoonite.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cartoonite.app.api.ToonItaliaClient;
import com.cartoonite.app.model.Models.Episode;
import com.cartoonite.app.model.Models.Season;
import com.cartoonite.app.model.Models.Title;
import com.cartoonite.app.model.Models.TitleDetail;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DetailActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "extra_title";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ImageView poster;
    private TextView titleView, metaView, plotView, errorView;
    private ProgressBar progress;
    private Spinner seasonSpinner;
    private RecyclerView episodesRecycler;
    private EpisodesAdapter episodesAdapter;
    private View contentLayout;

    private TitleDetail detail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        Title title = (Title) getIntent().getSerializableExtra(EXTRA_TITLE);
        if (title == null) {
            finish();
            return;
        }

        poster = findViewById(R.id.poster);
        titleView = findViewById(R.id.detailTitle);
        metaView = findViewById(R.id.detailMeta);
        plotView = findViewById(R.id.detailPlot);
        errorView = findViewById(R.id.detailError);
        progress = findViewById(R.id.detailProgress);
        seasonSpinner = findViewById(R.id.seasonSpinner);
        episodesRecycler = findViewById(R.id.episodesRecycler);
        contentLayout = findViewById(R.id.detailContent);

        titleView.setText(title.title);

        episodesAdapter = new EpisodesAdapter(player -> {
            Intent i = new Intent(this, PlayerActivity.class);
            i.putExtra(PlayerActivity.EXTRA_URL, player.url);
            i.putExtra(PlayerActivity.EXTRA_NAME, player.name);
            startActivity(i);
        });
        episodesRecycler.setLayoutManager(new LinearLayoutManager(this));
        episodesRecycler.setAdapter(episodesAdapter);

        loadDetail(title);
    }

    private void loadDetail(Title title) {
        progress.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                TitleDetail d = ToonItaliaClient.getDetail(title);
                handler.post(() -> showDetail(d));
            } catch (Exception e) {
                handler.post(() -> {
                    progress.setVisibility(View.GONE);
                    errorView.setVisibility(View.VISIBLE);
                    errorView.setText(getString(R.string.error_loading)
                            + "\n" + e.getMessage() + "\n\n(Tocca per riprovare)");
                    errorView.setOnClickListener(v -> loadDetail(title));
                });
            }
        });
    }

    private void showDetail(TitleDetail d) {
        this.detail = d;
        progress.setVisibility(View.GONE);
        contentLayout.setVisibility(View.VISIBLE);

        if (d.image != null && !d.image.isEmpty()) {
            String img = d.image.replace("http://", "https://");
            Picasso.get().load(img).fit().centerCrop().into(poster);
        }

        List<String> meta = new ArrayList<>();
        if (d.year != null) meta.add(d.year);
        if (d.country != null) meta.add(d.country);
        if (d.status != null) meta.add(d.status);
        int eps = d.episodeCount();
        if (eps > 0) meta.add(eps + " episodi");
        else if (d.totalEpisodes != null) meta.add(d.totalEpisodes + " episodi");
        metaView.setText(android.text.TextUtils.join("  •  ", meta));

        if (d.description != null && !d.description.isEmpty()) {
            plotView.setText(d.description);
            plotView.setVisibility(View.VISIBLE);
        }

        // Stagioni
        if (d.seasons.size() > 1) {
            List<String> names = new ArrayList<>();
            for (Season s : d.seasons) names.add(s.name + " (" + s.episodes.size() + ")");
            ArrayAdapter<String> sa = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, names);
            sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            seasonSpinner.setAdapter(sa);
            seasonSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    showSeason(pos);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
            seasonSpinner.setVisibility(View.VISIBLE);
            showSeason(0);
        } else if (d.seasons.size() == 1) {
            showSeason(0);
        }
    }

    private void showSeason(int index) {
        if (detail == null || index >= detail.seasons.size()) return;
        Season s = detail.seasons.get(index);
        episodesAdapter.setItems(s.episodes);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
