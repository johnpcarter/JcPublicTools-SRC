package com.jc.devops.docker.type;

import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class API {
	
	public String name;
	public String packageName;
	public String swaggerEndPoint;
	public String endPoint;
	
	public API(IData doc) {
		
		IDataCursor c = doc.getCursor();
		this.name = IDataUtil.getString(c, "name");
		this.packageName = IDataUtil.getString(c, "packageName");
		this.swaggerEndPoint = IDataUtil.getString(c, "swaggerEndPoint");
		this.endPoint = IDataUtil.getString(c, "endPoint");

		c.destroy();
	}
	
	public IData toIData() {
		
		IData d = IDataFactory.create();
		IDataCursor c = d.getCursor();
		
		IDataUtil.put(c, "name",  name);
		IDataUtil.put(c, "packageName",  packageName);
		IDataUtil.put(c, "swaggerEndPoint",  swaggerEndPoint);
		IDataUtil.put(c, "endPoint",  endPoint);

		c.destroy();
		return d;
	}
}