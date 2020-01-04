package com.jc.wm.introspection;

import com.wm.app.b2b.server.ServerAPI;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class APIInfo implements WmResource {
	
	public enum Method {
		GET,
		POST,
		PUT,
		DELETE,
		PATCH
	}
	
	public static class Resource
	{
		public String resource;
	    public Method[] methods;
	}
	
	private String _name;
	private String _packageName;
	private String _basePath;
	private String _version;
	private Resource[] _resources;
	
	public APIInfo(String packageName, String name, String basePath, String version, Resource[] resources) {
		
		this._name = name;
		this._version = version;
		this._basePath =  basePath;
		this._packageName = packageName;
		this._resources = resources;
	}
	
	public String getName() {
		return _name;
	}
	
	public String getBasePath() {
		return _basePath;
	}
	
	public String getVersion() {
		return _version;
	}
	
	public String getPackageName() {
		return _packageName;
	}
	
	public String swaggerEndpoint() {
		
		return "http://" + ServerAPI.getServerName() + ":" + ServerAPI.getCurrentPort() + this._basePath + "?swagger.json";
	}
	
	public Resource[] getResources() {
		
		return _resources;
	}
	
	public IData toIData() {
		
		IData d = IDataFactory.create();
		IDataCursor c = d.getCursor();
		IDataUtil.put(c, "name", this._name);
		IDataUtil.put(c, "version", this._version);
		IDataUtil.put(c, "packageName", this._packageName);
		IDataUtil.put(c, "basePath", this._basePath);
		IDataUtil.put(c, "swaggerEndPoint", this.swaggerEndpoint());

		IData[] resIData = new IData[this._resources.length];
		
		for (int i = 0; i < this._resources.length; i++) {
			
			Resource r = this._resources[i];
			
			IData rd = IDataFactory.create();
			IDataCursor rc = rd.getCursor();
			IDataUtil.put(rc, "resource", r.resource);
			
			String[] methods = new String[r.methods.length];
			
			for (int z = 0; z < r.methods.length; z++) {
				methods[z] = r.methods[z].name();
			}
			
			IDataUtil.put(rc, "methods", methods);
			rc.destroy();
			
			resIData[i] = rd;
		};
		
		IDataUtil.put(c, "resources", resIData);

		c.destroy();
		
		return d;
	}
}