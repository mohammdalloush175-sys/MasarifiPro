package com.example.masarifipro.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.masarifipro.models.SharedTrip;
import com.example.masarifipro.models.SharedTripExpense;
import com.example.masarifipro.models.SharedTripMember;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class LocalSharedTripManager {
    private static final String PREF_NAME = "guest_shared_trips";
    private static final String KEY_TRIP_IDS = "trip_ids";

    public static List<SharedTrip> getAllTrips(Context context) {
        List<SharedTrip> trips = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String idsStr = prefs.getString(KEY_TRIP_IDS, "");
        if (idsStr == null || idsStr.isEmpty()) return trips;

        String[] ids = idsStr.split(",");
        for (String id : ids) {
            SharedTrip trip = getTrip(context, id);
            if (trip != null) {
                trips.add(trip);
            }
        }
        return trips;
    }

    public static SharedTrip getTrip(Context context, String tripId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString("trip_data_" + tripId, null);
        if (json != null) {
            try {
                return parseTrip(new JSONObject(json));
            } catch (JSONException e) { e.printStackTrace(); }
        }
        return null;
    }

    public static void saveTrip(Context context, SharedTrip trip) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String idsStr = prefs.getString(KEY_TRIP_IDS, "");
        if (idsStr == null || !idsStr.contains(trip.getTripId())) {
            if (idsStr != null && !idsStr.isEmpty()) idsStr += ",";
            else idsStr = "";
            idsStr += trip.getTripId();
            prefs.edit().putString(KEY_TRIP_IDS, idsStr).apply();
        }
        try {
            prefs.edit().putString("trip_data_" + trip.getTripId(), tripToJSONObject(trip).toString()).apply();
        } catch (JSONException e) { e.printStackTrace(); }
    }

    public static void deleteTrip(Context context, String tripId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String idsStr = prefs.getString(KEY_TRIP_IDS, "");
        if (idsStr == null || idsStr.isEmpty()) return;
        
        String[] ids = idsStr.split(",");
        StringBuilder newIds = new StringBuilder();
        for (String id : ids) {
            if (!id.equals(tripId)) {
                if (newIds.length() > 0) newIds.append(",");
                newIds.append(id);
            }
        }
        prefs.edit()
                .putString(KEY_TRIP_IDS, newIds.toString())
                .remove("trip_data_" + tripId)
                .remove("trip_members_" + tripId)
                .remove("trip_expenses_" + tripId)
                .apply();
    }

    public static List<SharedTripMember> getMembers(Context context, String tripId) {
        List<SharedTripMember> members = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString("trip_members_" + tripId, null);
        if (json == null) return members;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                members.add(parseMember(arr.getJSONObject(i)));
            }
        } catch (JSONException e) { e.printStackTrace(); }
        return members;
    }

    public static void saveMembers(Context context, String tripId, List<SharedTripMember> members) {
        JSONArray arr = new JSONArray();
        for (SharedTripMember m : members) {
            arr.put(memberToJSONObject(m));
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString("trip_members_" + tripId, arr.toString()).apply();
    }

    public static List<SharedTripExpense> getExpenses(Context context, String tripId) {
        List<SharedTripExpense> expenses = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString("trip_expenses_" + tripId, null);
        if (json == null) return expenses;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                expenses.add(parseExpense(arr.getJSONObject(i)));
            }
        } catch (JSONException e) { e.printStackTrace(); }
        return expenses;
    }

    public static void saveExpenses(Context context, String tripId, List<SharedTripExpense> expenses) {
        JSONArray arr = new JSONArray();
        for (SharedTripExpense e : expenses) {
            arr.put(expenseToJSONObject(e));
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString("trip_expenses_" + tripId, arr.toString()).apply();
    }

    private static SharedTrip parseTrip(JSONObject obj) throws JSONException {
        SharedTrip trip = new SharedTrip();
        trip.setTripId(obj.getString("tripId"));
        trip.setName(obj.getString("name"));
        trip.setInviteCode(obj.getString("inviteCode"));
        trip.setOwnerUid(obj.getString("ownerUid"));
        trip.setOwnerName(obj.getString("ownerName"));
        trip.setCurrencyCode(obj.getString("currencyCode"));
        trip.setCreatedAt(obj.getLong("createdAt"));
        trip.setUpdatedAt(obj.getLong("updatedAt"));
        JSONArray members = obj.optJSONArray("memberUids");
        List<String> memberUids = new ArrayList<>();
        if (members != null) {
            for (int i = 0; i < members.length(); i++) memberUids.add(members.getString(i));
        }
        trip.setMemberUids(memberUids);
        return trip;
    }

    private static JSONObject tripToJSONObject(SharedTrip trip) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("tripId", trip.getTripId());
        obj.put("name", trip.getName());
        obj.put("inviteCode", trip.getInviteCode());
        obj.put("ownerUid", trip.getOwnerUid());
        obj.put("ownerName", trip.getOwnerName());
        obj.put("currencyCode", trip.getCurrencyCode());
        obj.put("createdAt", trip.getCreatedAt());
        obj.put("updatedAt", trip.getUpdatedAt());
        JSONArray members = new JSONArray();
        for (String uid : trip.getMemberUids()) members.put(uid);
        obj.put("memberUids", members);
        return obj;
    }

    private static SharedTripMember parseMember(JSONObject obj) throws JSONException {
        SharedTripMember m = new SharedTripMember();
        m.setUid(obj.getString("uid"));
        m.setName(obj.getString("name"));
        m.setEmail(obj.optString("email", ""));
        m.setJoinedAt(obj.getLong("joinedAt"));
        m.setOffline(obj.optBoolean("isOffline", false));
        m.setAddedByUid(obj.optString("addedByUid", ""));
        return m;
    }

    private static JSONObject memberToJSONObject(SharedTripMember m) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("uid", m.getUid());
            obj.put("name", m.getName());
            obj.put("email", m.getEmail());
            obj.put("joinedAt", m.getJoinedAt());
            obj.put("isOffline", m.isOffline());
            obj.put("addedByUid", m.getAddedByUid());
        } catch (JSONException e) { e.printStackTrace(); }
        return obj;
    }

    private static SharedTripExpense parseExpense(JSONObject obj) throws JSONException {
        SharedTripExpense e = new SharedTripExpense();
        e.setExpenseId(obj.getString("expenseId"));
        e.setTitle(obj.getString("title"));
        e.setAmount(obj.getDouble("amount"));
        e.setCurrencyCode(obj.getString("currencyCode"));
        e.setPaidByUid(obj.getString("paidByUid"));
        e.setPaidByName(obj.getString("paidByName"));
        e.setCreatedAt(obj.getLong("createdAt"));
        e.setNote(obj.optString("note", ""));
        return e;
    }

    private static JSONObject expenseToJSONObject(SharedTripExpense e) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("expenseId", e.getExpenseId());
            obj.put("title", e.getTitle());
            obj.put("amount", e.getAmount());
            obj.put("currencyCode", e.getCurrencyCode());
            obj.put("paidByUid", e.getPaidByUid());
            obj.put("paidByName", e.getPaidByName());
            obj.put("createdAt", e.getCreatedAt());
            obj.put("note", e.getNote());
        } catch (JSONException ex) { ex.printStackTrace(); }
        return obj;
    }
}
