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
import com.example.masarifipro.models.SyncOperation;

import java.util.ArrayList;
import java.util.List;

public class SyncOperationAdapter extends RecyclerView.Adapter<SyncOperationAdapter.ViewHolder> {

    private List<SyncOperation> operations = new ArrayList<>();
    private OnOperationActionListener actionListener;

    public interface OnOperationActionListener {
        void onRetry(SyncOperation operation);
        void onDelete(SyncOperation operation);
    }

    public void setActionListener(OnOperationActionListener listener) {
        this.actionListener = listener;
    }

    public void setOperations(List<SyncOperation> operations) {
        this.operations = operations != null ? operations : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sync_operation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SyncOperation op = operations.get(position);
        holder.tvOpType.setText(op.getOperationType());
        holder.tvCollection.setText(op.getCollectionName() + " (" + op.getDocumentId() + ")");
        
        String statusText;
        int color;
        switch (op.getStatus()) {
            case SyncOperation.STATUS_SYNCING:
                statusText = "🔄 Syncing...";
                color = Color.parseColor("#757575");
                break;
            case SyncOperation.STATUS_FAILED:
                statusText = "❌ Failed (Retries: " + op.getRetryCount() + ")";
                color = Color.parseColor("#F44336");
                break;
            case SyncOperation.STATUS_PENDING:
            default:
                statusText = "⏳ Pending";
                color = Color.parseColor("#FF9800");
                break;
        }
        
        holder.tvStatus.setText(statusText);
        holder.tvStatus.setTextColor(color);

        holder.btnRetry.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onRetry(op);
        });
        
        holder.btnDelete.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onDelete(op);
        });
    }

    @Override
    public int getItemCount() {
        return operations.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvOpType, tvCollection, tvStatus;
        ImageButton btnRetry, btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOpType = itemView.findViewById(R.id.tvOpType);
            tvCollection = itemView.findViewById(R.id.tvCollection);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            btnRetry = itemView.findViewById(R.id.btnRetry);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
