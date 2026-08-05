package com.example.masarifipro.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.masarifipro.R;
import com.example.masarifipro.models.SharedAccount;

import java.util.ArrayList;
import java.util.List;

public class SharedAccountAdapter extends RecyclerView.Adapter<SharedAccountAdapter.ViewHolder> {

    private List<SharedAccount> accounts = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(SharedAccount account);
    }

    public SharedAccountAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setAccounts(List<SharedAccount> accounts) {
        this.accounts = accounts;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shared_account, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SharedAccount account = accounts.get(position);
        holder.tvAccountName.setText(account.getName());
        holder.tvInviteCode.setText("كود الدعوة: " + account.getInviteCode());
        holder.tvRole.setText(account.getRole());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(account);
            }
        });
    }

    @Override
    public int getItemCount() {
        return accounts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAccountName, tvInviteCode, tvRole;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAccountName = itemView.findViewById(R.id.tvAccountName);
            tvInviteCode = itemView.findViewById(R.id.tvInviteCode);
            tvRole = itemView.findViewById(R.id.tvRole);
        }
    }
}
