/*
	Radiobeacon - Openbmap Unified Network Location Provider
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
package org.openbmap.unifiedNlp.services;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.openbmap.unifiedNlp.BuildConfig;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class JSONParser {
    private static final String TAG = JSONParser.class.getSimpleName();
    private static final String USER_AGENT = "Openbmap NLP/" + BuildConfig.VERSION_NAME;

    private static JSONObject jObj = null;

    public JSONParser(Context context) {

    }

    /**
     * Sends a http JSON post request
     *
     * @param endpoint JSON endpoint
     * @param params JSON parameters
     * @return server reply
     */
    public JSONObject getJSONFromUrl(String endpoint, JSONObject params) {
        Log.v(TAG, "Online query " + params.toString());
        try {
            URL url = new URL(endpoint);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept","application/json");
            con.setRequestProperty("User-Agent", USER_AGENT);
            con.setDoOutput(true);
            con.setDoInput(true);
            con.connect();

            OutputStream os = con.getOutputStream();
            os.write(params.toString().getBytes("UTF-8"));
            os.close();

            try {
                if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    StringBuilder sb = new StringBuilder();
                    String inputLine;
                    BufferedInputStream is = new BufferedInputStream(con.getInputStream());
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    while ((inputLine = br.readLine()) != null) {
                        sb.append(inputLine);
                    }
                    jObj = new JSONObject(sb.toString());
                } else if (con.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                    // do nothing - server reply for missing cell/wifi data
                    jObj = null;
                }
                else {
                    Log.i(TAG, "No results, code " + con.getResponseCode());
                    jObj = null;
                }
                con.disconnect();
            } catch (JSONException e) {
                Log.e("JSON Parser", "Error parsing data " + e.toString());
            }
            return jObj;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }

        return null;
      }
}