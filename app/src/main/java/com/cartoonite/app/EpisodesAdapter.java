package com.cartoonite.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cartoonite.app.model.Models.Episode;
import com.cartoonite.app.model.Models.Player;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

public class EpisodesAdapter extends RecyclerView.Adapter<EpisodesAdapter.VH> {

    public interface OnPlayerClick {
        void onClick(Player player);
    }

    private final List<Episode> items = new ArrayList<>();
    private final OnPlayerClick listener;

    public EpisodesAdapter(OnPlayerClick listener) {
        this.listener = listener;
    }

    public void setItems(List<Episode> episodes) {
        items.clear();
        items.addAll(episodes);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_episode, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Episode ep = items.get(position);
        h.number.setText(ep.label != null ? ep.label : String.valueOf(ep.number));
        h.title.setText(ep.title);

        h.players.removeAllViews();
        if (ep.players.isEmpty()) {
            TextView none = new TextView(h.itemView.getContext());
            none.setText(R.string.no_links);
            none.setTextColor(0xFF888888);
            none.setTextSize(12f);
            h.players.addView(none);
        } else {
            for (Player p : ep.players) {
                Chip chip = new Chip(h.itemView.getContext());
                chip.setText("▶ " + p.name);
                chip.setTextColor(0xFFFFFFFF);
                chip.setChipBackgroundColorResource(R.color.accent);
                chip.setOnClickListener(v -> listener.onClick(p));
                h.players.addView(chip);
            }
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView number, title;
        ChipGroup players;

        VH(View v) {
            super(v);
            number = v.findViewById(R.id.epNumber);
            title = v.findViewById(R.id.epTitle);
            players = v.findViewById(R.id.epPlayers);
        }
    }
}
