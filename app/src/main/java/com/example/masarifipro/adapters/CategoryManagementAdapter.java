package com.example.masarifipro.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.masarifipro.R;
import com.example.masarifipro.models.Category;

import java.util.ArrayList;
import java.util.List;

public class CategoryManagementAdapter extends RecyclerView.Adapter<CategoryManagementAdapter.CategoryViewHolder> {

    private final Context context;
    private List<Category> categories = new ArrayList<>();
    private final OnCategoryActionListener listener;

    public interface OnCategoryActionListener {
        void onEdit(Category category);
        void onDelete(Category category);
    }

    public CategoryManagementAdapter(Context context, OnCategoryActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setCategories(List<Category> categories) {
        this.categories = new ArrayList<>(categories);
        notifyDataSetChanged();
    }

    public void updateData(List<Category> newCategories) {
        this.categories.clear();
        this.categories.addAll(newCategories);
        notifyDataSetChanged();
    }

    public List<Category> getCategories() {
        return categories;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_category_management, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = categories.get(position);
        holder.tvName.setText(category.getName());
        
        String typeStr = "income".equalsIgnoreCase(category.getType()) ? 
                context.getString(R.string.cm_income) : context.getString(R.string.cm_expense);
        holder.tvType.setText(typeStr);
        holder.tvCurrency.setText(category.getCurrencyCode());

        holder.btnEdit.setOnClickListener(v -> listener.onEdit(category));
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(category));
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvType, tvCurrency;
        ImageButton btnEdit, btnDelete;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvCategoryName);
            tvType = itemView.findViewById(R.id.tvCategoryType);
            tvCurrency = itemView.findViewById(R.id.tvCategoryCurrency);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
