package com.cartoonite.app;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryPagerAdapter extends FragmentStateAdapter {

    private final List<String> keys;
    private final Map<Integer, CategoryFragment> fragments = new HashMap<>();

    public CategoryPagerAdapter(@NonNull FragmentActivity fa, List<String> keys) {
        super(fa);
        this.keys = keys;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        CategoryFragment f = CategoryFragment.newInstance(keys.get(position));
        fragments.put(position, f);
        return f;
    }

    @Override
    public int getItemCount() {
        return keys.size();
    }

    public List<CategoryFragment> getFragments() {
        return new ArrayList<>(fragments.values());
    }
}
