/*
	Radiobeacon - Openbmap wifi and cell logger
    Copyright (C) 2013  wish7

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.openbmap.unifiedNlp;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.ExpandableListView;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openbmap.unifiedNlp.utils.CatalogDownload;
import org.openbmap.unifiedNlp.utils.ICatalogsListAdapterListener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


public class DialogPreferenceCatalogs extends DialogPreference implements ICatalogsListAdapterListener {

    private static final String TAG = DialogPreferenceCatalogs.class.getSimpleName();

    private static final String LIST_DOWNLOADS_URL = Preferences.SERVER_BASE + "/downloads/catalog_downloads.json";
    private DialogPreferenceCatalogsListAdapter mAdapter;
    private final Context mContext;
    private SparseArray<DialogPreferenceCatalogsGroup> groups;
    private List<CatalogDownload> mOnlineResults;

    private ProgressDialog checkDialog;

    public DialogPreferenceCatalogs(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        setDialogLayoutResource(R.layout.dialogpreference_catalogs);
    }

    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        groups = new SparseArray<>();
        ExpandableListView listView = (ExpandableListView) v.findViewById(R.id.list);
        mAdapter = new DialogPreferenceCatalogsListAdapter(getContext(), groups, this);
        listView.setAdapter(mAdapter);

        if (checkDialog == null || !checkDialog.isShowing()) {
            checkDialog = new ProgressDialog(getContext());
        }
        // retrieve online Catalogs
        GetAvailableCatalogsTask data = new GetAvailableCatalogsTask();
        data.execute();
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (checkDialog != null && checkDialog.isShowing()) {
            checkDialog.dismiss();
        }
        checkDialog = null;
    }

    /**
     * Creates list of online maps
     */
    public void populateListView() {
        DialogPreferenceCatalogsGroup group = null;
        String name;
        int j = 0;
        for (int i = 0; i < mOnlineResults.size(); i++) {
            if (i==0) {
                name = (mOnlineResults.get(i).getRegion() != null) ? mOnlineResults.get(i).getRegion() : "Unsorted";
                group = new DialogPreferenceCatalogsGroup(name);
                Log.d(TAG, "Added group " + name);
            } else if (!mOnlineResults.get(i).getRegion().equals(mOnlineResults.get(i - 1).getRegion())) {
                name = (mOnlineResults.get(i).getRegion() != null) ? mOnlineResults.get(i).getRegion() : "Unsorted";
                Log.d(TAG, "Added group " + name);
                groups.append(groups.size(), group);
                group = new DialogPreferenceCatalogsGroup(name);
            }
            group.children.add(mOnlineResults.get(i));
        }
        groups.append(j, group);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onItemClicked(CatalogDownload catalog) {
        ((ICatalogChooser) getContext()).catalogSelected(catalog.getRelativeUrl());
        getDialog().dismiss();
    }

    private class GetAvailableCatalogsTask extends AsyncTask<String, Void, List<CatalogDownload>> {

        @Override
        protected void onPreExecute() {
            checkDialog.setTitle(mContext.getString(R.string.prefs_check_server));
            checkDialog.setMessage(mContext.getString(R.string.please_stay_patient));
            checkDialog.setCancelable(false);
            checkDialog.setIndeterminate(true);
            checkDialog.show();
        }

        @Override
        protected List<CatalogDownload> doInBackground(String... params) {
            List<CatalogDownload> result = new ArrayList<>();

            DefaultHttpClient httpclient = new DefaultHttpClient(new BasicHttpParams());
            HttpGet httpGet = new HttpGet(LIST_DOWNLOADS_URL);
            httpGet.setHeader("Content-type", "application/json");

            InputStream inputStream = null;
            try {
                HttpResponse response = httpclient.execute(httpGet);
                HttpEntity entity = response.getEntity();

                inputStream = entity.getContent();
                // json is UTF-8 by default
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
                StringBuilder sb = new StringBuilder();

                String line;
                while ((line = reader.readLine()) != null)
                {
                    sb.append(line + "\n");
                }

                JSONObject jObject = new JSONObject(sb.toString());
                JSONArray arr = jObject.getJSONArray("downloads");
                for (int i = 0; i < arr.length(); i++) {
                    result.add(jsonToDownload(arr.getJSONObject(i)));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing server reply:" + e.getMessage());
            } finally {
                try {
                    if(inputStream != null) {
                        inputStream.close();
                    }
                } catch (Exception squish){
                    return null;
                }
            }
            return result;
        }

        @Override
        protected void onPostExecute(List<CatalogDownload> result) {
            super.onPostExecute(result);

            if (checkDialog != null && checkDialog.isShowing()) {
                checkDialog.dismiss();
            }

            mOnlineResults = result;
            populateListView();
        }

        /**
         * Converts server json in a CatalogDownload record
         * @param obj server reply
         * @return parsed server reply
         * @throws JSONException
         */
        private CatalogDownload jsonToDownload(JSONObject obj) throws JSONException {
            String updated = obj.getString("last_updated");
            String title = obj.getString("title");
            String region = obj.getString("region");
            String url = obj.getString("url");
            String id = obj.getString("id");
            return new CatalogDownload(title, region, url, id, updated);
        }
    }
}