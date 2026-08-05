package com.example.masarifipro.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.masarifipro.R;
import com.example.masarifipro.models.Reminder;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReminderAdapter extends RecyclerView.Adapter<ReminderAdapter.ReminderViewHolder> {

    private List<Reminder> reminders;
    private OnReminderClickListener listener;

    public interface OnReminderClickListener {
        void onDeleteClick(Reminder reminder);
        void onStatusChange(Reminder reminder, boolean isDone);
    }

    public ReminderAdapter(List<Reminder> reminders, OnReminderClickListener listener) {
        this.reminders = reminders;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ReminderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reminder, parent, false);
        return new ReminderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReminderViewHolder holder, int position) {
        Reminder reminder = reminders.get(position);
        holder.tvTitle.setText(reminder.getTitle());
        holder.tvDescription.setText(reminder.getDescription());
        
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        holder.tvTime.setText(sdf.format(new Date(reminder.getReminderTime())));

        if (reminder.getAmount() > 0) {
            holder.tvAmount.setVisibility(View.VISIBLE);
            holder.tvAmount.setText(String.format("%.2f %s", reminder.getAmount(), reminder.getCurrencyCode()));
        } else {
            holder.tvAmount.setVisibility(View.GONE);
        }

        holder.cbDone.setOnCheckedChangeListener(null);
        holder.cbDone.setChecked(reminder.isDone());
        
        holder.cbDone.setOnCheckedChangeListener((buttonView, isChecked) -> {
            listener.onStatusChange(reminder, isChecked);
        });

        holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(reminder));

        // Visual state for expired or done reminders
        boolean isExpired = System.currentTimeMillis() > reminder.getReminderTime();
        
        if (reminder.isDone()) {
            holder.tvReminderStatus.setVisibility(View.VISIBLE);
            holder.tvReminderStatus.setText("مكتمل");
            holder.tvReminderStatus.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.teal_700));
            holder.cardReminder.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.light_gray));
            holder.tvTitle.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.gray));
        } else if (isExpired) {
            holder.tvReminderStatus.setVisibility(View.VISIBLE);
            holder.tvReminderStatus.setText("انتهى التذكير");
            holder.tvReminderStatus.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.holo_red_dark));
            holder.cardReminder.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.light_gray));
            holder.tvTitle.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.gray));
        } else {
            holder.tvReminderStatus.setVisibility(View.GONE);
            holder.cardReminder.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.white));
            holder.tvTitle.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.black));
        }
    }

    @Override
    public int getItemCount() {
        return reminders.size();
    }

    public void setReminders(List<Reminder> reminders) {
        this.reminders = reminders;
        notifyDataSetChanged();
    }

    static class ReminderViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDescription, tvTime, tvAmount, tvReminderStatus;
        CheckBox cbDone;
        ImageButton btnDelete;
        MaterialCardView cardReminder;

        public ReminderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvReminderStatus = itemView.findViewById(R.id.tvReminderStatus);
            cbDone = itemView.findViewById(R.id.cbDone);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            cardReminder = itemView.findViewById(R.id.cardReminder);
        }
    }
}
