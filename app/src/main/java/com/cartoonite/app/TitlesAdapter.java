package com.cartoonite.app;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cartoonite.app.model.Models.Title;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Adapter griglia titoli: copertina via Picasso, fallback card con iniziale. */
public class TitlesAdapter extends RecyclerView.Adapter<TitlesAdapter.VH> {

    public interface OnTitleClick {
        void onClick(Title title);
    }

    private static final int[] GRADIENTS = {
            0xFF8E0E00, 0xFF1F1C18,
            0xFF0F2027, 0xFF203A43,
            0xFF42275A, 0xFF734B6D,
            0xFF134E5E, 0xFF71B280,
            0xFF3A1C71, 0xFFD76D77,
            0xFF141E30, 0xFF243B55,
            0xFF870000, 0xFF190A05,
            0xFF1A2980, 0xFF26D0CE,
    };

    private final List<Title> items = new ArrayList<>();
    private final OnTitleClick listener;

    public TitlesAdapter(OnTitleClick listener) {
        this.listener = listener;
    }

    public void setItems(List<Title> titles) {
        items.clear();
        items.addAll(titles);
        notifyDataSetChanged();
    }

    /** Aggiorna le copertine caricate dopo (slug -> url). */
    public void updateCovers(Map<String, String> covers) {
        for (int i = 0; i < items.size(); i++) {
            Title t = items.get(i);
            if (t.imageUrl == null || t.imageUrl.isEmpty()) {
                String url = covers.get(t.id);
                if (url != null && !url.isEmpty()) {
                    t.imageUrl = url;
                    notifyItemChanged(i);
                }
            }
        }
    }

    public List<Title> getItems() {
        return items;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_title, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Title t = items.get(position);
        h.title.setText(t.title);
        h.initial.setText(t.title.isEmpty() ? "?" : t.title.substring(0, 1).toUpperCase());

        int i1 = Math.abs(t.id.hashCode()) % (GRADIENTS.length / 2) * 2;
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{GRADIENTS[i1], GRADIENTS[i1 + 1]});
        gd.setCornerRadius(12f);
        h.card.setBackground(gd);

        if (t.imageUrl != null && !t.imageUrl.isEmpty()) {
            h.poster.setVisibility(View.VISIBLE);
            Picasso.get()
                    .load(t.imageUrl)
                    .fit()
                    .centerCrop()
                    .into(h.poster);
        } else {
            h.poster.setVisibility(View.GONE);
            Picasso.get().cancelRequest(h.poster);
            h.poster.setImageDrawable(null);
        }

        h.itemView.setOnClickListener(v -> listener.onClick(t));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, initial;
        ImageView poster;
        View card;

        VH(View v) {
            super(v);
            title = v.findViewById(R.id.titleText);
            initial = v.findViewById(R.id.initialText);
            poster = v.findViewById(R.id.posterImage);
            card = v.findViewById(R.id.cardContainer);
        }
    }
}
