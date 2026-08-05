package com.example.masarifipro.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.masarifipro.R;
import com.example.masarifipro.models.Debt;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DebtAdapter extends RecyclerView.Adapter<DebtAdapter.DebtViewHolder> {

    private List<Debt> debts = new ArrayList<>();
    private OnDebtActionListener listener;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());

    public interface OnDebtActionListener {
        void onEdit(Debt debt);
        void onDelete(Debt debt);
        void onMarkPaid(Debt debt);
        void onPartialPayment(Debt debt);
    }

    public void setOnDebtActionListener(OnDebtActionListener listener) {
        this.listener = listener;
    }

    public void setDebts(List<Debt> debts) {
        this.debts = debts;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DebtViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_debt, parent, false);
        return new DebtViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DebtViewHolder holder, int position) {
        Debt debt = debts.get(position);
        holder.bind(debt);
    }

    @Override
    public int getItemCount() {
        return debts.size();
    }

    class DebtViewHolder extends RecyclerView.ViewHolder {
        View indicatorType;
        TextView tvPersonName, tvStatusBadge, tvDebtType, tvAmount, tvPaidAmount, tvRemainingAmount, tvDueDate, tvNote;
        ImageButton btnMarkPaid, btnPartialPayment, btnEdit, btnDelete;
        View layoutPaid;

        public DebtViewHolder(@NonNull View itemView) {
            super(itemView);
            indicatorType = itemView.findViewById(R.id.indicatorType);
            tvPersonName = itemView.findViewById(R.id.tvPersonName);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
            tvDebtType = itemView.findViewById(R.id.tvDebtType);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvPaidAmount = itemView.findViewById(R.id.tvPaidAmount);
            tvRemainingAmount = itemView.findViewById(R.id.tvRemainingAmount);
            tvDueDate = itemView.findViewById(R.id.tvDueDate);
            tvNote = itemView.findViewById(R.id.tvNote);
            btnMarkPaid = itemView.findViewById(R.id.btnMarkPaid);
            btnPartialPayment = itemView.findViewById(R.id.btnPartialPayment);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            layoutPaid = itemView.findViewById(R.id.layoutPaid);
        }

        public void bind(Debt debt) {
            tvPersonName.setText(debt.getPersonName());
            tvAmount.setText(String.format(Locale.getDefault(), "%.2f %s", debt.getAmount(), debt.getCurrencyCode()));
            tvPaidAmount.setText(String.format(Locale.getDefault(), "%.2f %s", debt.getPaidAmount(), debt.getCurrencyCode()));
            tvRemainingAmount.setText(String.format(Locale.getDefault(), "%.2f %s", debt.getRemainingAmount(), debt.getCurrencyCode()));
            tvDueDate.setText("موعد الاستحقاق: " + dateFormat.format(new Date(debt.getDueDate())));

            if (debt.getNote() != null && !debt.getNote().isEmpty()) {
                tvNote.setVisibility(View.VISIBLE);
                tvNote.setText(debt.getNote());
            } else {
                tvNote.setVisibility(View.GONE);
            }

            boolean isOwedToMe = "owed_to_me".equals(debt.getType());
            tvDebtType.setText(isOwedToMe ? "لي عنده" : "عليّ له");
            int typeColor = isOwedToMe ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336");
            tvDebtType.setTextColor(typeColor);
            indicatorType.setBackgroundColor(typeColor);

            // Overdue logic
            boolean isOverdue = debt.getDueDate() < System.currentTimeMillis() && !"paid".equals(debt.getStatus());
            
            String statusText = "";
            int statusColor = Color.GRAY;
            switch (debt.getStatus()) {
                case "unpaid":
                    statusText = "غير مدفوع";
                    statusColor = Color.parseColor("#F44336");
                    break;
                case "partial":
                    statusText = "مدفوع جزئياً";
                    statusColor = Color.parseColor("#FF9800");
                    break;
                case "paid":
                    statusText = "مدفوع";
                    statusColor = Color.parseColor("#4CAF50");
                    break;
            }
            
            if (isOverdue) {
                statusText = "متأخر";
                statusColor = Color.parseColor("#B71C1C");
                indicatorType.setBackgroundColor(Color.parseColor("#B71C1C"));
            }

            tvStatusBadge.setText(statusText);
            tvStatusBadge.setTextColor(statusColor);

            btnMarkPaid.setVisibility("paid".equals(debt.getStatus()) ? View.GONE : View.VISIBLE);
            btnPartialPayment.setVisibility("paid".equals(debt.getStatus()) ? View.GONE : View.VISIBLE);

            btnEdit.setOnClickListener(v -> { if (listener != null) listener.onEdit(debt); });
            btnDelete.setOnClickListener(v -> { if (listener != null) listener.onDelete(debt); });
            btnMarkPaid.setOnClickListener(v -> { if (listener != null) listener.onMarkPaid(debt); });
            btnPartialPayment.setOnClickListener(v -> { if (listener != null) listener.onPartialPayment(debt); });
        }
    }
}
