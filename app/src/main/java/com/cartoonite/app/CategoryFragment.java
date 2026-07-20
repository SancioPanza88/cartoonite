package com.cartoonite.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cartoonite.app.api.ToonItaliaClient;
import com.cartoonite.app.model.Models.Title;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CategoryFragment extends Fragment {

    private static final String ARG_KEY = "category_key";

    private String categoryKey;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TitlesAdapter adapter;
    private ProgressBar progress;
    private TextView errorView;
    private RecyclerView recycler;
    private List<Title> allTitles = new ArrayList<>();
    private final Set<String> coversRequested = new HashSet<>();
    private boolean coverLoadRunning = false;

    public static CategoryFragment newInstance(String categoryKey) {
        CategoryFragment f = new CategoryFragment();
        Bundle b = new Bundle();
        b.putString(ARG_KEY, categoryKey);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        categoryKey = getArguments() != null ? getArguments().getString(ARG_KEY) : "anime_ita";
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_category, container, false);
        progress = v.findViewById(R.id.progress);
        errorView = v.findViewById(R.id.errorView);
        recycler = v.findViewById(R.id.recycler);

        adapter = new TitlesAdapter(title -> {
            Intent i = new Intent(requireContext(), DetailActivity.class);
            i.putExtra(DetailActivity.EXTRA_TITLE, title);
            startActivity(i);
        });

        GridLayoutManager glm = new GridLayoutManager(requireContext(), 3);
        recycler.setLayoutManager(glm);
        recycler.setAdapter(adapter);

        // carica le copertine mancanti per le card visibili durante lo scroll
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                requestVisibleCovers();
            }
        });

        loadTitles();
        return v;
    }

    private void loadTitles() {
        progress.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                List<Title> titles = ToonItaliaClient.getCategoryTitles(categoryKey);
                handler.post(() -> {
                    allTitles = titles;
                    adapter.setItems(titles);
                    progress.setVisibility(View.GONE);
                    requestVisibleCovers();
                });
            } catch (Exception e) {
                handler.post(() -> {
                    progress.setVisibility(View.GONE);
                    errorView.setVisibility(View.VISIBLE);
                    errorView.setText(getString(R.string.error_loading) + "\n" + e.getMessage());
                    errorView.setOnClickListener(v -> loadTitles());
                });
            }
        });
    }

    /** Carica in background le copertine dei titoli visibili che non ce l'hanno. */
    private void requestVisibleCovers() {
        if (adapter == null || recycler == null || coverLoadRunning) return;
        GridLayoutManager glm = (GridLayoutManager) recycler.getLayoutManager();
        if (glm == null) return;

        int first = glm.findFirstVisibleItemPosition();
        int last = glm.findLastVisibleItemPosition();
        if (first < 0) return;

        List<Title> visible = adapter.getItems();
        List<String> missing = new ArrayList<>();
        for (int i = Math.max(0, first - 3); i <= Math.min(visible.size() - 1, last + 6); i++) {
            Title t = visible.get(i);
            if ((t.imageUrl == null || t.imageUrl.isEmpty()) && coversRequested.add(t.id)) {
                missing.add(t.id);
            }
        }
        if (missing.isEmpty()) return;

        coverLoadRunning = true;
        executor.execute(() -> {
            try {
                Map<String, String> covers = ToonItaliaClient.loadMissingCovers(missing);
                handler.post(() -> {
                    coverLoadRunning = false;
                    if (adapter != null) adapter.updateCovers(covers);
                });
            } catch (Exception e) {
                handler.post(() -> coverLoadRunning = false);
            }
        });
    }

    public void filter(String query) {
        if (adapter == null) return;
        if (query == null || query.trim().isEmpty()) {
            adapter.setItems(allTitles);
            return;
        }
        String q = query.trim().toLowerCase();
        List<Title> filtered = new ArrayList<>();
        for (Title t : allTitles) {
            if (t.title.toLowerCase().contains(q)) filtered.add(t);
        }
        adapter.setItems(filtered);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
