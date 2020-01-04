package com.jc.devops.docker.type;

import java.util.ArrayList;
import java.util.List;

import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class Arg {
	
	public String source;
	public String target;
	
	public Arg(IData doc) {
		
		IDataCursor c = doc.getCursor();
		this.source = IDataUtil.getString(c, "source");
		
		if (this.source == null)
			this.source = IDataUtil.getString(c, "src");
		
		this.target = IDataUtil.getString(c, "target");
		
		if (this.target == null)
			this.target = IDataUtil.getString(c, "tgt");
		
		c.destroy();
	}

	public Arg(String src, String tgt) {
		this.source = src;
		this.target = tgt;
	}
	
	public IData toIData() {
		
		return this.toIData(false);
	}
	
	public IData toIData(boolean cleanSource) {
		
		IData d = IDataFactory.create();
		IDataCursor c = d.getCursor();
		
		if (cleanSource && this.source != null) {
			String src = this.source.replace("_", "-").replace(" ", "-");
			IDataUtil.put(c, "source", src);
		} else if (source != null){
			IDataUtil.put(c, "source", source);
		}
		
		IDataUtil.put(c, "target", target);
		
		c.destroy();
		
		return d;
	}
}