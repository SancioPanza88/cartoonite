package com.cartoonite.app;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cartoonite.app.model.Models.Title;

import java.util.ArrayList;
import java.util.List;

/** Adapter della griglia titoli: card testuali stile Netflix. */
public class TitlesAdapter extends RecyclerView.Adapter<TitlesAdapter.VH> {

    public interface OnTitleClick {
        void onClick(Title title);
    }

    private static final int[] GRADIENTS = {
            0xFF8E0E00, 0xFF1F1C18, // rosso scuro
            0xFF0F2027, 0xFF203A43, // blu notte
            0xFF42275A, 0xFF734B6D, // viola
            0xFF134E5E, 0xFF71B280, // verde
            0xFF3A1C71, 0xFFD76D77, // rosa/viola
            0xFF141E30, 0xFF243B55, // blu acciaio
            0xFF870000, 0xFF190A05, // bordeaux
            0xFF1A2980, 0xFF26D0CE, // azzurro
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

        h.itemView.setOnClickListener(v -> listener.onClick(t));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, initial;
        View card;

        VH(View v) {
            super(v);
            title = v.findViewById(R.id.titleText);
            initial = v.findViewById(R.id.initialText);
            card = v.findViewById(R.id.cardContainer);
        }
    }
}
