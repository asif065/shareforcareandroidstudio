package com.asif.shareforcare.app;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import android.app.Application;
import android.text.TextUtils;

public class AppController extends Application{
	
	public static final String TAG = AppController.class.getSimpleName();
	
	private RequestQueue queue;
	
	private static AppController mInstance;

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
		mInstance = this;
	}
	
	public static synchronized AppController getInstance(){
		return mInstance;
	}
	
	public RequestQueue getQueue(){
		if(queue == null){
			queue  = Volley.newRequestQueue(getApplicationContext());
		}
		return queue;
	}
	
	public <T> void addToQueue(Request<T> req){
		req.setTag(TAG);
		getQueue().add(req);
	}
	
	public <T> void addToQueue(Request<T> req, String tag){
		req.setTag(TextUtils.isEmpty(tag) ? TAG : tag);
		getQueue().add(req);
	}
	
	public void cancelPendingRequest(Object tag){
		if(queue != null){
			queue.cancelAll(tag);
		}
	}
	
}
