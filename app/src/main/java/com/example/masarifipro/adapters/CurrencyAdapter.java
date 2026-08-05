package com.example.masarifipro.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.masarifipro.R;
import com.example.masarifipro.models.CurrencyAccount;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

public class CurrencyAdapter extends RecyclerView.Adapter<CurrencyAdapter.ViewHolder> {

    private List<CurrencyAccount> currencies = new ArrayList<>();
    private final OnCurrencyChangeListener listener;

    public interface OnCurrencyChangeListener {
        void onCurrencyChanged(CurrencyAccount currency);
    }

    public CurrencyAdapter(OnCurrencyChangeListener listener) {
        this.listener = listener;
    }

    public void setCurrencies(List<CurrencyAccount> currencies) {
        this.currencies = currencies;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_currency_management, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CurrencyAccount currency = currencies.get(position);
        holder.tvSymbol.setText(currency.getCurrencySymbol());
        holder.tvName.setText(currency.getCurrencyName());
        holder.tvCode.setText(currency.getCurrencyCode());
        holder.switchActive.setChecked(currency.isActive());

        holder.switchActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            currency.setActive(isChecked);
            listener.onCurrencyChanged(currency);
        });
    }

    @Override
    public int getItemCount() {
        return currencies.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSymbol, tvName, tvCode;
        SwitchMaterial switchActive;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSymbol = itemView.findViewById(R.id.tvCurrencySymbol);
            tvName = itemView.findViewById(R.id.tvCurrencyName);
            tvCode = itemView.findViewById(R.id.tvCurrencyCode);
            switchActive = itemView.findViewById(R.id.switchActive);
        }
    }
}
