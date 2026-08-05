package com.example.masarifipro.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.masarifipro.R;

import java.util.List;

public class CategoryDropdownAdapter extends ArrayAdapter<String> {

    public CategoryDropdownAdapter(@NonNull Context context, @NonNull List<String> objects) {
        super(context, 0, objects);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_category_dropdown, parent, false);
        }

        String category = getItem(position);
        TextView tvName = convertView.findViewById(R.id.tvCategoryName);
        View separator = convertView.findViewById(R.id.separator);

        if (tvName != null) {
            tvName.setText(category);
        }

        if (separator != null) {
            // Hide separator for the last item
            if (position == getCount() - 1) {
                separator.setVisibility(View.GONE);
            } else {
                separator.setVisibility(View.VISIBLE);
            }
        }

        return convertView;
    }
}
