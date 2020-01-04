package com.jc.devops.docker.type;

import java.util.ArrayList;
import java.util.List;

import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class Port {
	
	public String internal;
	public String external;
	public String publicPort;
	public String description;
	public String type;
	public String serviceType;

	public Port(IData doc) {
		
		IDataCursor c = doc.getCursor();
		this.internal = IDataUtil.getString(c, "internal");
		this.external = IDataUtil.getString(c, "external");
		this.publicPort = IDataUtil.getString(c, "publicPort");
		this.description = IDataUtil.getString(c, "description");
		this.type = IDataUtil.getString(c, "type");
		this.serviceType = IDataUtil.getString(c, "serviceType");

		c.destroy();
	}
	
	public IData toIData() {
		return toIData(false);
	}
	
	public IData toIData(boolean clean) {
		
		IData d = IDataFactory.create();
		IDataCursor c = d.getCursor();
		
		IDataUtil.put(c, "internal", this.internal);
		IDataUtil.put(c, "external", this.external);
		
		if (publicPort != null && publicPort.length() > 0)
			IDataUtil.put(c, "publicPort", this.publicPort);

		if (clean && this.description != null) {
			IDataUtil.put(c, "description", this.description.replace(" ", "-").replace("_", "-"));
		} else {
			IDataUtil.put(c, "description", this.description);
		}
		
		IDataUtil.put(c, "type", this.type);

		if (serviceType != null)
			IDataUtil.put(c, "serviceType", this.serviceType);

		c.destroy();
		
		return d;
	}
}