package com.example.masarifipro.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.masarifipro.R;
import com.example.masarifipro.models.SharedTripExpense;
import com.example.masarifipro.utils.NumberFormatter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SharedTripExpenseAdapter extends RecyclerView.Adapter<SharedTripExpenseAdapter.ViewHolder> {

    private List<SharedTripExpense> expenses = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault());

    public void setExpenses(List<SharedTripExpense> expenses) {
        this.expenses = expenses;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shared_expense, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SharedTripExpense expense = expenses.get(position);
        Context context = holder.itemView.getContext();
        holder.tvExpenseTitle.setText(expense.getTitle());
        holder.tvPaidBy.setText(context.getString(R.string.se_paid_by_format, expense.getPaidByName()));
        holder.tvExpenseAmount.setText(NumberFormatter.formatAmount(context, expense.getAmount(), expense.getCurrencyCode()));
        
        holder.tvExpenseDate.setText(dateFormat.format(new Date(expense.getCreatedAt())));

        if (expense.getNote() != null && !expense.getNote().trim().isEmpty()) {
            holder.tvExpenseNote.setVisibility(View.VISIBLE);
            holder.divider.setVisibility(View.VISIBLE);
            holder.tvExpenseNote.setText(context.getString(R.string.se_note_format, expense.getNote()));
        } else {
            holder.tvExpenseNote.setVisibility(View.GONE);
            holder.divider.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return expenses.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvExpenseTitle, tvPaidBy, tvExpenseAmount, tvExpenseNote, tvExpenseDate;
        View divider;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvExpenseTitle = itemView.findViewById(R.id.tvExpenseTitle);
            tvPaidBy = itemView.findViewById(R.id.tvPaidBy);
            tvExpenseAmount = itemView.findViewById(R.id.tvExpenseAmount);
            tvExpenseNote = itemView.findViewById(R.id.tvExpenseNote);
            tvExpenseDate = itemView.findViewById(R.id.tvExpenseDate);
            divider = itemView.findViewById(R.id.divider);
        }
    }
}
