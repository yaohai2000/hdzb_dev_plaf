package com.bhz.invoker.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

public class ServiceHttpInvoker {

	public static String get(String url) throws ClientProtocolException, IOException {
		CloseableHttpClient client = HttpClients.createDefault();
		HttpGet get = new HttpGet(url);
		CloseableHttpResponse resp = null;
		try {
			resp = client.execute(get);
			HttpEntity r = resp.getEntity();
			return EntityUtils.toString(r);

		} finally {
			if (resp != null) {
				try {
					resp.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			client.close();
		}
	}

	public static String post(String serviceId, ContentType type, String content,String url) throws ClientProtocolException, IOException {
		CloseableHttpClient client = HttpClients.createDefault();
		HttpPost httpPost = new HttpPost(url);
		httpPost.setHeader("sid",serviceId);
		httpPost.setHeader("method","POST");
		switch(type){
			case FORM: httpPost.setHeader("mediatype","");
			break;
			case JSON: httpPost.setHeader("mediatype","json");
			break;
			case XML: httpPost.setHeader("mediatype","xml");
			break;
			default: httpPost.setHeader("mediatype","");
		}
		List<NameValuePair> paramList = new ArrayList<NameValuePair>();
		paramList.add(new BasicNameValuePair("attachcontent", content));
		HttpEntity entity = new UrlEncodedFormEntity(paramList, "UTF-8");
		httpPost.setEntity(entity);
		String responseBody = client.execute(httpPost, new ResponseHandler<String>() {

			@Override
			public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
				int status = response.getStatusLine().getStatusCode();
				if (status >= 200 && status < 300) {
					HttpEntity entity = response.getEntity();
					return entity != null ? EntityUtils.toString(entity, "utf-8") : null;
				} else {
					throw new ClientProtocolException("Unexpected response status: " + status);
				}
			}

		});

		return responseBody;
	}

	public static <T> String postObject(String sid, T object, String url) throws ClientProtocolException, IOException {
		String attachcontent = JacksonUtil.bean2Json(object);
		return post(sid, ContentType.JSON,attachcontent,url);
	}

	public static String post(String sid, Map<String,String> params,String url) throws ClientProtocolException, IOException {
		String attachcontent = JacksonUtil.bean2Json(params);
		return post(sid, ContentType.JSON,attachcontent,url);
	}

}
