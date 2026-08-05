package com.example.masarifipro.adapters;

import android.app.AlertDialog;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.masarifipro.R;
import com.example.masarifipro.models.Transaction;
import com.example.masarifipro.utils.NumberFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private List<Transaction> transactions = new ArrayList<>();
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());

    private OnTransactionDeleteListener deleteListener;
    private OnItemClickListener itemClickListener;

    public interface OnTransactionDeleteListener {
        void onDelete(Transaction transaction);
    }

    public interface OnItemClickListener {
        void onItemClick(Transaction transaction);
    }

    public void setOnTransactionDeleteListener(OnTransactionDeleteListener listener) {
        this.deleteListener = listener;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    public void setTransactions(List<Transaction> transactions) {
        this.transactions = transactions != null ? transactions : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        Transaction transaction = transactions.get(position);

        holder.tvCategoryName.setText(
                transaction.getCategoryName() == null || transaction.getCategoryName().trim().isEmpty()
                        ? "عام"
                        : transaction.getCategoryName()
        );

        holder.tvDescription.setText(
                transaction.getDescription() == null ? "" : transaction.getDescription()
        );

        holder.tvDate.setText(sdf.format(transaction.getDate()));

        boolean isIncome = "income".equalsIgnoreCase(transaction.getType());
        String amountPrefix = isIncome ? "+ " : "- ";
        int color = isIncome ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336");

        String formattedAmount = NumberFormatter.format(
                transaction.getAmount(),
                transaction.getCurrencyCode()
        );

        holder.tvAmount.setText(amountPrefix + formattedAmount);
        holder.tvAmount.setTextColor(color);
        holder.viewTypeIndicator.setBackgroundColor(color);

        if (transaction.isBalanceSnapshotEnabled()
                && transaction.getBalanceBefore() != null
                && transaction.getBalanceAfter() != null) {
            String before = NumberFormatter.formatAmount(
                    holder.itemView.getContext(),
                    transaction.getBalanceBefore(),
                    transaction.getCurrencyCode());
            String after = NumberFormatter.formatAmount(
                    holder.itemView.getContext(),
                    transaction.getBalanceAfter(),
                    transaction.getCurrencyCode());
            holder.tvBalanceSnapshot.setText(holder.itemView.getContext().getString(
                    R.string.transaction_balance_snapshot, before, after));
            holder.tvBalanceSnapshot.setVisibility(View.VISIBLE);
        } else {
            holder.tvBalanceSnapshot.setText("");
            holder.tvBalanceSnapshot.setVisibility(View.GONE);
        }

        // Update Sync Status
        updateSyncStatusUI(holder.tvSyncStatus, transaction.getSyncStatus());

        holder.btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(v.getContext())
                    .setTitle("حذف العملية")
                    .setMessage("هل تريد حذف هذه العملية؟")
                    .setPositiveButton("نعم", (dialog, which) -> {
                        if (deleteListener != null) {
                            deleteListener.onDelete(transaction);
                        }
                    })
                    .setNegativeButton("إلغاء", null)
                    .show();
        });

        holder.itemView.setOnClickListener(v -> {
            if (itemClickListener != null) {
                itemClickListener.onItemClick(transaction);
            }
        });
    }

    private void updateSyncStatusUI(TextView textView, String status) {
        if (status == null) status = Transaction.STATUS_PENDING;
        
        switch (status) {
            case Transaction.STATUS_SYNCED:
                textView.setText("✓ تمت المزامنة");
                textView.setTextColor(Color.parseColor("#4CAF50"));
                break;
            case Transaction.STATUS_SYNCING:
                textView.setText("⏳ جاري المزامنة...");
                textView.setTextColor(Color.parseColor("#757575"));
                break;
            case Transaction.STATUS_PENDING:
                textView.setText("⏳ بانتظار المزامنة");
                textView.setTextColor(Color.parseColor("#FF9800"));
                break;
            case Transaction.STATUS_FAILED:
                textView.setText("❌ فشل المزامنة");
                textView.setTextColor(Color.parseColor("#F44336"));
                break;
            default:
                textView.setText("");
                break;
        }
    }

    @Override
    public int getItemCount() {
        return transactions == null ? 0 : transactions.size();
    }

    static class TransactionViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategoryName, tvDescription, tvAmount, tvDate, tvSyncStatus, tvBalanceSnapshot;
        View viewTypeIndicator;
        ImageButton btnDelete;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategoryName = itemView.findViewById(R.id.tvCategoryName);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvSyncStatus = itemView.findViewById(R.id.tvSyncStatus);
            tvBalanceSnapshot = itemView.findViewById(R.id.tvBalanceSnapshot);
            viewTypeIndicator = itemView.findViewById(R.id.viewTypeIndicator);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}