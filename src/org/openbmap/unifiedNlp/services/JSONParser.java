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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class JSONParser {
    static final String TAG = JSONParser.class.getSimpleName();

	static InputStream is = null;
	static JSONObject jObj = null;
	static String json = "";
	// constructor
	public JSONParser() {
	}
	
	/**
	 * Sends a http post request
	 * @param url
	 * @param params 
	 * @return
	 */
	public JSONObject getJSONFromUrl(String url, JSONObject params) {
		// Making HTTP request
		try {
			
			DefaultHttpClient httpClient = new DefaultHttpClient();
			HttpPost httpPost = new HttpPost(url);
			 //passes the results to a string builder/entity
		    StringEntity se = new StringEntity(params.toString());

		    //sets the post request as the resulting string
		    httpPost.setEntity(se);
		    //sets a request header so the page receving the request
		    //will know what to do with it
		    httpPost.setHeader("Accept", "application/json");
		    httpPost.setHeader("Content-type", "application/json");
		    httpPost.setHeader("User-Agent", "Openbmap NLP/0.1");
			HttpResponse httpResponse = httpClient.execute(httpPost);
			HttpEntity httpEntity = httpResponse.getEntity();
			is = httpEntity.getContent();

            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "iso-8859-1"), 8);
                StringBuilder sb = new StringBuilder();
                String line = null;
                while ((line = reader.readLine()) != null) {
                    sb.append(line + "n");
                }
                is.close();
                json = sb.toString();
            } catch (Exception e) {
                Log.e(TAG, "Error converting result " + e.toString());
            }

            try {
                httpEntity.consumeContent();
            } catch (IOException e) {
                Log.e(TAG, "Error closing http entity");
            }

		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// try parse the string to a JSON object
		try {
			jObj = new JSONObject(json);
		} catch (JSONException e) {
			Log.e("JSON Parser", "Error parsing data " + e.toString());
		}
		// return JSON String
		return jObj;
	}
}