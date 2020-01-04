package com.jc.wm.introspection;

import com.wm.data.IData;

public interface WmResource {
		
		String getName();
		String getPackageName();
		IData toIData();
	}