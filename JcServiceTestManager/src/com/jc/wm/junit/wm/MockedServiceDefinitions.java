package com.jc.wm.junit.wm;

import com.wm.data.IData;

public interface MockedServiceDefinitions {

	public IData[] getAllMockedServices();
	
	public IData[] getAllMockedServicesForService(String serviceName);
	
	public IData[] getAllMockedServicesForSuite(String suiteId);
}
