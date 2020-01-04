package com.jc.devops.docker;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class DefaultImageChecker {
	
	private Map<String, String>_default = new HashMap<String, String>();
	private String _version;
	
	public DefaultImageChecker(String version) {
		
		this._version = version;
		this._default.put("store/softwareag/webmethods-microservicesruntime:" + version, "msr");
		this._default.put("store/softwareag/apigateway-trial:" + version, "apigw");
		this._default.put("store/softwareag/microgateway-trial:" + version, "apimg");
		this._default.put("store/softwareag/universalmessaging-server:" + version, "um");
		this._default.put("store/softwareag/terracotta-server:" + version, "tcs");
	}
	
	public String check(String tag) {
	
		return this._default.remove(tag);
	}
	
	public List<IData> remainer(List<IData> images) {
		
		List<IData> docs = new ArrayList<IData>();
		
		Iterator<String> keys = this._default.keySet().iterator();
		
		while(keys.hasNext()) {
			String key = keys.next();
			docs.add(makeImageDoc(key, this._version, this._default.get(key)));
		}
		
		Iterator<IData> rimgs = images.iterator();
		
		while (rimgs.hasNext()) {
			docs.add(rimgs.next());
		}
		
		return docs;
	}
	
	private IData makeImageDoc(String repo, String version, String type) {
		
		IData doc = IDataFactory.create();
		IDataCursor c = doc.getCursor();
					
		if (repo.indexOf(":") != -1)
			repo = repo.substring(0, repo.indexOf(":"));
				
		if (repo.indexOf("/") != -1)
			IDataUtil.put(c, "_name", repo.substring(repo.lastIndexOf("/")));
		
		IDataUtil.put(c, "_repository", repo);
		IDataUtil.put(c, "_tag", repo + ":" + version);
		IDataUtil.put(c, "_version", version);

		IDataUtil.put(c, "author", "Software AG");
		IDataUtil.put(c, "type", type);
		IDataUtil.put(c, "description", "Software AG Image on Docker Hub");
		IDataUtil.put(c, "Created",  new Date().getTime());
		IDataUtil.put(c, "createdDate",  formatDate(new Date()));
		c.destroy();
		
		return doc;
	}
	
	private static String formatDate(Date date) {
		
		return new SimpleDateFormat("dd MMM yy - HH:mm").format(date);
	}
}
