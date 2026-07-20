package com.cartoonite.app;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cartoonite.app.model.Models.Season;
import com.cartoonite.app.model.Models.TitleDetail;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

/** Header del dettaglio (poster, meta, trama, selettore stagione) come singolo item. */
public class DetailHeaderAdapter extends RecyclerView.Adapter<DetailHeaderAdapter.VH> {

    public interface OnSeasonSelected {
        void onSeason(int index);
    }

    private TitleDetail detail;
    private final OnSeasonSelected seasonListener;

    public DetailHeaderAdapter(OnSeasonSelected listener) {
        this.seasonListener = listener;
    }

    public void setDetail(TitleDetail d) {
        boolean hadItem = this.detail != null;
        this.detail = d;
        if (hadItem) notifyItemChanged(0);
        else notifyItemInserted(0);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_detail_header, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        if (detail == null) return;

        h.title.setText(detail.title);

        if (detail.image != null && !detail.image.isEmpty()) {
            Picasso.get().load(detail.image).fit().centerCrop().into(h.poster);
        }

        List<String> meta = new ArrayList<>();
        if (detail.year != null) meta.add(detail.year);
        if (detail.country != null) meta.add(detail.country);
        if (detail.status != null) meta.add(detail.status);
        int eps = detail.episodeCount();
        if (eps > 0) meta.add(eps + " episodi");
        else if (detail.totalEpisodes != null) meta.add(detail.totalEpisodes + " episodi");
        h.meta.setText(TextUtils.join("  •  ", meta));

        if (detail.description != null && !detail.description.isEmpty()) {
            h.plot.setText(detail.description);
            h.plot.setVisibility(View.VISIBLE);
        } else {
            h.plot.setVisibility(View.GONE);
        }

        if (detail.seasons.size() > 1) {
            List<String> names = new ArrayList<>();
            for (Season s : detail.seasons) names.add(s.name + " (" + s.episodes.size() + ")");
            ArrayAdapter<String> sa = new ArrayAdapter<>(h.spinner.getContext(),
                    android.R.layout.simple_spinner_item, names);
            sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            h.spinner.setAdapter(sa);
            h.spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    seasonListener.onSeason(pos);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
            h.spinner.setVisibility(View.VISIBLE);
        } else {
            h.spinner.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return detail != null ? 1 : 0;
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView poster;
        TextView title, meta, plot;
        Spinner spinner;

        VH(View v) {
            super(v);
            poster = v.findViewById(R.id.poster);
            title = v.findViewById(R.id.detailTitle);
            meta = v.findViewById(R.id.detailMeta);
            plot = v.findViewById(R.id.detailPlot);
            spinner = v.findViewById(R.id.seasonSpinner);
        }
    }
}
