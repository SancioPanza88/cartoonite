package com.cartoonite.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cartoonite.app.api.ToonItaliaClient;
import com.cartoonite.app.model.Models.Season;
import com.cartoonite.app.model.Models.Title;
import com.cartoonite.app.model.Models.TitleDetail;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DetailActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "extra_title";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ProgressBar progress;
    private TextView errorView;
    private RecyclerView recycler;

    private DetailHeaderAdapter headerAdapter;
    private EpisodesAdapter episodesAdapter;
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

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.detailToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title.title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        progress = findViewById(R.id.detailProgress);
        errorView = findViewById(R.id.detailError);
        recycler = findViewById(R.id.detailRecycler);

        headerAdapter = new DetailHeaderAdapter(this::showSeason);
        episodesAdapter = new EpisodesAdapter(player -> {
            Intent i = new Intent(this, PlayerActivity.class);
            i.putExtra(PlayerActivity.EXTRA_URL, player.url);
            i.putExtra(PlayerActivity.EXTRA_NAME, player.name);
            startActivity(i);
        });

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(new ConcatAdapter(headerAdapter, episodesAdapter));

        loadDetail(title);
    }

    private void loadDetail(Title title) {
        progress.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                TitleDetail d = ToonItaliaClient.getDetail(title);
                handler.post(() -> showDetail(d));
            } catch (Exception e) {
                handler.post(() -> {
                    progress.setVisibility(View.GONE);
                    errorView.setVisibility(View.VISIBLE);
                    errorView.setText(getString(R.string.error_loading)
                            + "\n" + e.getMessage());
                    errorView.setOnClickListener(v -> loadDetail(title));
                });
            }
        });
    }

    private void showDetail(TitleDetail d) {
        this.detail = d;
        progress.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);

        headerAdapter.setDetail(d);
        if (!d.seasons.isEmpty()) showSeason(0);
    }

    private void showSeason(int index) {
        if (detail == null || index >= detail.seasons.size()) return;
        Season s = detail.seasons.get(index);
        episodesAdapter.setItems(s.episodes);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
