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
		this._default.put("store/softwareag/webmethods-microservicesruntime", "Micro Service Runtime");
		this._default.put("store/softwareag/apigateway-trial", "API Gateway");
		this._default.put("store/softwareag/microgateway-trial", "API Micro Gateway");
		this._default.put("store/softwareag/universalmessaging-server", "Universal Messaging");
		this._default.put("store/softwareag/terracotta-server", "Terracotta Server");
	}
	
	public void check(String tag) {
		
		if (tag.indexOf(":") != -1)
			tag = tag.substring(0, tag.indexOf(":"));
		
		this._default.remove(tag);
	}
	
	public List<IData> remainer(List<IData> images) {
		
		List<IData> docs = new ArrayList<IData>();
		
		Iterator<String> keys = this._default.keySet().iterator();
		
		while(keys.hasNext()) {
			String key = keys.next();
			docs.add(makeImageDoc(key,this._version, this._default.get(key)));
		}
		
		Iterator<IData> rimgs = images.iterator();
		
		while (rimgs.hasNext()) {
			docs.add(rimgs.next());
		}
		
		return docs;
	}
	
	private IData makeImageDoc(String repo, String version, String description) {
		
		IData doc = IDataFactory.create();
		IDataCursor c = doc.getCursor();
					
		IDataUtil.put(c, "_repository", repo);
		IDataUtil.put(c, "_tag", repo + ":" + version);
		IDataUtil.put(c, "_version", version);

		IDataUtil.put(c, "author", "Software AG");
		IDataUtil.put(c, "name", description);
		IDataUtil.put(c, "description", description);
		IDataUtil.put(c, "Created",  new Date().getTime());
		IDataUtil.put(c, "createdDate",  formatDate(new Date()));
		c.destroy();
		
		return doc;
	}
	
	private static String formatDate(Date date) {
		
		return new SimpleDateFormat("dd MMM yy - HH:mm").format(date);
	}
}
