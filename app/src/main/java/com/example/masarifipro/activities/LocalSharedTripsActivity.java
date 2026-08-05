package com.example.masarifipro.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.masarifipro.R;
import com.example.masarifipro.utils.NumberFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LocalSharedTripsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "local_shared_trips";
    private static final String KEY_GROUPS = "groups_json";

    private View layoutGroupList, layoutGroupDetails;
    private RecyclerView rvGroups, rvExpenses;
    private TextView tvEmptyState, tvDetailGroupName, tvTotalExpenses, tvSharePerMember, tvMembersList;
    private FloatingActionButton fabAddGroup;
    private Toolbar toolbar;

    private List<JSONObject> groupsList = new ArrayList<>();
    private JSONObject selectedGroup = null;
    private ExpenseAdapter expenseAdapter;
    private GroupAdapter groupAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_shared_trips);

        initViews();
        loadGroups();
        showGroupList();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        layoutGroupList = findViewById(R.id.layoutGroupList);
        layoutGroupDetails = findViewById(R.id.layoutGroupDetails);
        rvGroups = findViewById(R.id.rvGroups);
        rvExpenses = findViewById(R.id.rvExpenses);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        tvDetailGroupName = findViewById(R.id.tvDetailGroupName);
        tvTotalExpenses = findViewById(R.id.tvTotalExpenses);
        tvSharePerMember = findViewById(R.id.tvSharePerMember);
        tvMembersList = findViewById(R.id.tvMembersList);
        fabAddGroup = findViewById(R.id.fabAddGroup);

        rvGroups.setLayoutManager(new LinearLayoutManager(this));
        groupAdapter = new GroupAdapter();
        rvGroups.setAdapter(groupAdapter);

        rvExpenses.setLayoutManager(new LinearLayoutManager(this));
        expenseAdapter = new ExpenseAdapter();
        rvExpenses.setAdapter(expenseAdapter);

        fabAddGroup.setOnClickListener(v -> showCreateGroupDialog());

        findViewById(R.id.btnAddMember).setOnClickListener(v -> showAddMemberDialog());
        findViewById(R.id.btnAddExpense).setOnClickListener(v -> showAddExpenseDialog());
        findViewById(R.id.btnSettlement).setOnClickListener(v -> calculateSettlement());
        findViewById(R.id.btnDeleteGroup).setOnClickListener(v -> confirmDeleteGroup());
    }

    @Override
    public void onBackPressed() {
        if (layoutGroupDetails.getVisibility() == View.VISIBLE) {
            showGroupList();
        } else {
            super.onBackPressed();
        }
    }

    private void showGroupList() {
        layoutGroupList.setVisibility(View.VISIBLE);
        layoutGroupDetails.setVisibility(View.GONE);
        fabAddGroup.setVisibility(View.VISIBLE);
        toolbar.setTitle("المصاريف المشتركة المحلية");
        selectedGroup = null;
        updateEmptyState();
    }

    private void showGroupDetails(JSONObject group) {
        selectedGroup = group;
        layoutGroupList.setVisibility(View.GONE);
        layoutGroupDetails.setVisibility(View.VISIBLE);
        fabAddGroup.setVisibility(View.GONE);
        try {
            String name = group.getString("name");
            toolbar.setTitle(name);
            tvDetailGroupName.setText(name);
            refreshGroupUI();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void refreshGroupUI() {
        if (selectedGroup == null) return;
        try {
            String currency = selectedGroup.getString("currencyCode");
            JSONArray members = selectedGroup.getJSONArray("members");
            JSONArray expenses = selectedGroup.getJSONArray("expenses");

            double total = 0;
            List<JSONObject> expenseList = new ArrayList<>();
            for (int i = 0; i < expenses.length(); i++) {
                JSONObject ex = expenses.getJSONObject(i);
                total += ex.getDouble("amount");
                expenseList.add(ex);
            }

            expenseAdapter.setExpenses(expenseList, currency);

            int memberCount = members.length();
            double share = memberCount > 0 ? total / memberCount : 0;

            tvTotalExpenses.setText(NumberFormatter.formatAmount(this, total, currency));
            tvSharePerMember.setText(NumberFormatter.formatAmount(this, share, currency));

            StringBuilder membersStr = new StringBuilder();
            for (int i = 0; i < members.length(); i++) {
                if (i > 0) membersStr.append("، ");
                membersStr.append(members.getJSONObject(i).getString("name"));
            }
            tvMembersList.setText(membersStr.length() > 0 ? membersStr.toString() : "لا يوجد أعضاء بعد");

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void updateEmptyState() {
        tvEmptyState.setVisibility(groupsList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void loadGroups() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_GROUPS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            groupsList.clear();
            for (int i = 0; i < arr.length(); i++) {
                groupsList.add(arr.getJSONObject(i));
            }
            groupAdapter.notifyDataSetChanged();
            updateEmptyState();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void saveGroups() {
        JSONArray arr = new JSONArray();
        for (JSONObject obj : groupsList) {
            arr.put(obj);
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_GROUPS, arr.toString())
                .apply();
    }

    private void showCreateGroupDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_create_trip, null);
        EditText etName = view.findViewById(R.id.etTripName);
        Spinner spinnerCurrency = view.findViewById(R.id.spinnerCurrency);
        view.findViewById(R.id.btnCreate).setVisibility(View.GONE);

        String[] currencies = {"USD", "SYP", "SAR", "AED", "EUR", "KWD", "LBP"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, currencies);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCurrency.setAdapter(adapter);

        new AlertDialog.Builder(this)
                .setTitle("إنشاء مجموعة جديدة")
                .setView(view)
                .setPositiveButton("إنشاء", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String currency = spinnerCurrency.getSelectedItem().toString();

                    if (name.isEmpty()) {
                        Toast.makeText(this, "الاسم مطلوب", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    createGroup(name, currency);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void createGroup(String name, String currency) {
        try {
            JSONObject group = new JSONObject();
            group.put("id", UUID.randomUUID().toString());
            group.put("name", name);
            group.put("currencyCode", currency);
            group.put("createdAt", System.currentTimeMillis());
            group.put("members", new JSONArray());
            group.put("expenses", new JSONArray());

            groupsList.add(0, group);
            saveGroups();
            groupAdapter.notifyDataSetChanged();
            updateEmptyState();
            showGroupDetails(group);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showAddMemberDialog() {
        EditText etName = new EditText(this);
        etName.setHint("اسم الشخص");
        
        new AlertDialog.Builder(this)
                .setTitle("إضافة شخص")
                .setView(etName)
                .setPositiveButton("إضافة", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) return;
                    addMember(name);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void addMember(String name) {
        try {
            JSONArray members = selectedGroup.getJSONArray("members");
            JSONObject member = new JSONObject();
            member.put("id", UUID.randomUUID().toString());
            member.put("name", name);
            members.put(member);

            saveGroups();
            refreshGroupUI();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showAddExpenseDialog() {
        try {
            JSONArray members = selectedGroup.getJSONArray("members");
            if (members.length() == 0) {
                Toast.makeText(this, "أضف شخصاً أولاً", Toast.LENGTH_SHORT).show();
                return;
            }

            View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_expense, null);
            EditText etTitle = view.findViewById(R.id.etExpenseTitle);
            EditText etAmount = view.findViewById(R.id.etExpenseAmount);
            EditText etNote = view.findViewById(R.id.etExpenseNote);
            Spinner spinnerPayer = view.findViewById(R.id.spinnerPayer);

            List<String> memberNames = new ArrayList<>();
            for (int i = 0; i < members.length(); i++) {
                memberNames.add(members.getJSONObject(i).getString("name"));
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, memberNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerPayer.setAdapter(adapter);

            new AlertDialog.Builder(this)
                    .setTitle("إضافة مصروف")
                    .setView(view)
                    .setPositiveButton("إضافة", (dialog, which) -> {
                        String title = etTitle.getText().toString().trim();
                        String amountStr = etAmount.getText().toString().trim();
                        int payerIndex = spinnerPayer.getSelectedItemPosition();

                        if (title.isEmpty() || amountStr.isEmpty()) {
                            Toast.makeText(this, "يرجى ملء الحقول المطلوبة", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        double amount;
                        try {
                            amount = Double.parseDouble(amountStr);
                        } catch (NumberFormatException e) {
                            return;
                        }
                        if (amount <= 0) return;

                        addExpense(title, amount, payerIndex, etNote.getText().toString().trim());
                    })
                    .setNegativeButton("إلغاء", null)
                    .show();

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void addExpense(String title, double amount, int payerIndex, String note) {
        try {
            JSONArray members = selectedGroup.getJSONArray("members");
            JSONObject payer = members.getJSONObject(payerIndex);

            JSONObject expense = new JSONObject();
            expense.put("id", UUID.randomUUID().toString());
            expense.put("title", title);
            expense.put("amount", amount);
            expense.put("paidByMemberId", payer.getString("id"));
            expense.put("paidByName", payer.getString("name"));
            expense.put("note", note);
            expense.put("createdAt", System.currentTimeMillis());

            selectedGroup.getJSONArray("expenses").put(expense);
            saveGroups();
            refreshGroupUI();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void calculateSettlement() {
        try {
            JSONArray membersArr = selectedGroup.getJSONArray("members");
            JSONArray expensesArr = selectedGroup.getJSONArray("expenses");
            String currency = selectedGroup.getString("currencyCode");

            if (membersArr.length() < 2) {
                Toast.makeText(this, "تحتاج لعضوين على الأقل للحساب", Toast.LENGTH_SHORT).show();
                return;
            }

            double totalExpenses = 0;
            Map<String, Double> paidMap = new HashMap<>();
            Map<String, String> nameMap = new HashMap<>();

            for (int i = 0; i < membersArr.length(); i++) {
                JSONObject m = membersArr.getJSONObject(i);
                paidMap.put(m.getString("id"), 0.0);
                nameMap.put(m.getString("id"), m.getString("name"));
            }

            for (int i = 0; i < expensesArr.length(); i++) {
                JSONObject ex = expensesArr.getJSONObject(i);
                double amt = ex.getDouble("amount");
                totalExpenses += amt;
                String payerId = ex.getString("paidByMemberId");
                paidMap.put(payerId, paidMap.get(payerId) + amt);
            }

            double share = totalExpenses / membersArr.length();
            List<MemberNet> netList = new ArrayList<>();

            for (String id : paidMap.keySet()) {
                netList.add(new MemberNet(id, nameMap.get(id), paidMap.get(id) - share));
            }

            List<String> results = new ArrayList<>();
            List<MemberNet> debtors = new ArrayList<>();
            List<MemberNet> creditors = new ArrayList<>();

            for (MemberNet mn : netList) {
                if (mn.net < -0.01) debtors.add(mn);
                else if (mn.net > 0.01) creditors.add(mn);
            }

            int d = 0, c = 0;
            while (d < debtors.size() && c < creditors.size()) {
                MemberNet debtor = debtors.get(d);
                MemberNet creditor = creditors.get(c);

                double amount = Math.min(-debtor.net, creditor.net);
                results.add(debtor.name + " يدفع " + NumberFormatter.formatAmount(this, amount, currency) + " إلى " + creditor.name);

                debtor.net += amount;
                creditor.net -= amount;

                if (Math.abs(debtor.net) < 0.01) d++;
                if (Math.abs(creditor.net) < 0.01) c++;
            }

            if (results.isEmpty()) {
                results.add("لا توجد مبالغ مستحقة، الجميع دفع حصته بالتساوي.");
            }

            StringBuilder sb = new StringBuilder();
            for (String s : results) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("• ").append(s);
            }

            new AlertDialog.Builder(this)
                    .setTitle("من يدفع لمن؟")
                    .setMessage(sb.toString())
                    .setPositiveButton("حسناً", null)
                    .show();

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private static class MemberNet {
        String id;
        String name;
        double net;
        MemberNet(String id, String name, double net) {
            this.id = id; this.name = name; this.net = net;
        }
    }

    private void confirmDeleteGroup() {
        new AlertDialog.Builder(this)
                .setTitle("حذف المجموعة")
                .setMessage("سيتم حذف هذه المجموعة المحلية من هذا الجهاز فقط.")
                .setPositiveButton("حذف", (dialog, which) -> deleteCurrentGroup())
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void deleteCurrentGroup() {
        groupsList.remove(selectedGroup);
        saveGroups();
        showGroupList();
        groupAdapter.notifyDataSetChanged();
    }

    private class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            try {
                JSONObject group = groupsList.get(position);
                holder.text1.setText(group.getString("name"));
                String currency = group.getString("currencyCode");
                JSONArray members = group.getJSONArray("members");
                holder.text2.setText("الأعضاء: " + members.length() + " | العملة: " + currency);
                holder.itemView.setOnClickListener(v -> showGroupDetails(group));
            } catch (JSONException e) { e.printStackTrace(); }
        }

        @Override
        public int getItemCount() { return groupsList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ViewHolder(View v) {
                super(v);
                text1 = v.findViewById(android.R.id.text1);
                text2 = v.findViewById(android.R.id.text2);
            }
        }
    }

    private class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.ViewHolder> {
        private List<JSONObject> expenses = new ArrayList<>();
        private String currency;

        void setExpenses(List<JSONObject> list, String curr) {
            this.expenses = list;
            this.currency = curr;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            try {
                JSONObject ex = expenses.get(position);
                holder.text1.setText(ex.getString("title") + " (" + NumberFormatter.formatAmount(LocalSharedTripsActivity.this, ex.getDouble("amount"), currency) + ")");
                holder.text2.setText("دفع بواسطة: " + ex.getString("paidByName"));
            } catch (JSONException e) { e.printStackTrace(); }
        }

        @Override
        public int getItemCount() { return expenses.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ViewHolder(View v) {
                super(v);
                text1 = v.findViewById(android.R.id.text1);
                text2 = v.findViewById(android.R.id.text2);
            }
        }
    }
}
