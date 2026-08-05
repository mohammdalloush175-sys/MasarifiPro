package com.example.masarifipro.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.masarifipro.R;
import com.example.masarifipro.models.SharedTrip;
import com.example.masarifipro.utils.NumberFormatter;
import com.google.firebase.auth.FirebaseAuth;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SharedTripAdapter extends RecyclerView.Adapter<SharedTripAdapter.ViewHolder> {

    private List<SharedTrip> trips = new ArrayList<>();
    private OnItemClickListener listener;
    private OnDeleteLeaveClickListener deleteLeaveListener;
    private String currentUid;

    public interface OnItemClickListener {
        void onItemClick(SharedTrip trip);
    }

    public interface OnDeleteLeaveClickListener {
        void onDeleteLeaveClick(SharedTrip trip);
    }

    public SharedTripAdapter() {
        this.currentUid = FirebaseAuth.getInstance().getUid();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnDeleteLeaveClickListener(OnDeleteLeaveClickListener listener) {
        this.deleteLeaveListener = listener;
    }

    public void setCurrentUid(String currentUid) {
        this.currentUid = currentUid;
        notifyDataSetChanged();
    }

    public void setTrips(List<SharedTrip> trips) {
        this.trips = trips;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shared_trip, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SharedTrip trip = trips.get(position);
        Context context = holder.itemView.getContext();
        holder.tvTripName.setText(trip.getName());
        holder.tvInviteCode.setText(context.getString(R.string.se_invite_code_format, trip.getInviteCode()));
        holder.tvCurrency.setText(trip.getCurrencyCode());
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
        holder.tvDate.setText(context.getString(R.string.se_creation_date_format, sdf.format(new Date(trip.getCreatedAt()))));

        if (currentUid != null && currentUid.equals(trip.getOwnerUid())) {
            holder.btnDeleteLeave.setImageResource(android.R.drawable.ic_menu_delete);
        } else {
            holder.btnDeleteLeave.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        }

        // Handle Sync Status
        if (trip.getSyncStatus() != null && !trip.getSyncStatus().equals("SYNCED") && !"guest_user".equals(trip.getOwnerUid())) {
            holder.ivSyncStatus.setVisibility(View.VISIBLE);
            if (trip.getSyncStatus().equals("FAILED")) {
                holder.ivSyncStatus.setImageResource(android.R.drawable.stat_notify_error);
                holder.ivSyncStatus.setColorFilter(0xFFF44336); // Red
            } else {
                holder.ivSyncStatus.setImageResource(android.R.drawable.stat_notify_sync);
                holder.ivSyncStatus.setColorFilter(0xFFFF9800); // Orange
            }
        } else {
            holder.ivSyncStatus.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(trip);
            }
        });

        holder.btnDeleteLeave.setOnClickListener(v -> {
            if (deleteLeaveListener != null) {
                deleteLeaveListener.onDeleteLeaveClick(trip);
            }
        });
    }

    @Override
    public int getItemCount() {
        return trips.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTripName, tvInviteCode, tvCurrency, tvMemberCount, tvTotalExpenses, tvDate;
        ImageButton btnDeleteLeave;
        ImageView ivSyncStatus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTripName = itemView.findViewById(R.id.tvTripName);
            tvInviteCode = itemView.findViewById(R.id.tvInviteCode);
            tvCurrency = itemView.findViewById(R.id.tvCurrency);
            tvMemberCount = itemView.findViewById(R.id.tvMemberCount);
            tvTotalExpenses = itemView.findViewById(R.id.tvTotalExpenses);
            tvDate = itemView.findViewById(R.id.tvDate);
            btnDeleteLeave = itemView.findViewById(R.id.btnDeleteLeave);
            ivSyncStatus = itemView.findViewById(R.id.ivSyncStatus);
        }
    }
}
