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
import java.util.List;
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

        recycler.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        recycler.setAdapter(adapter);

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
