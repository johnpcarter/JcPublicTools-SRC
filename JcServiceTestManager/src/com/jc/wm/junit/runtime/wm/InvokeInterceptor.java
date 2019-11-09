package com.jc.wm.junit.runtime.wm;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.wm.app.b2b.server.InvokeState;
import com.wm.app.b2b.server.ServerAPI;
import com.jc.wm.junit.wm.MockedServicesManager;
import com.jc.wm.util.ServiceUtils;
import com.wm.app.b2b.server.BaseService;
import com.wm.app.b2b.server.invoke.InvokeChainProcessor;
import com.wm.app.b2b.server.invoke.ServiceStatus;
import com.wm.data.IData;
import com.wm.data.IDataUtil;
import com.wm.util.ServerException;

public class InvokeInterceptor implements InvokeChainProcessor {

	public static Map<String, String> _runningSuites = new HashMap<String, String>();
	public static boolean _isActive;
	
	public static void activate(boolean isActive) {
		
		_isActive = isActive;
	}
	
	@Override
	public void process(Iterator chain, BaseService baseService, IData pipeline, ServiceStatus status) throws ServerException {
		
		IData mockedPipeline = null;
		
		if (_isActive)
			ServiceUtils.debugLog(0, "JcTestSuite", "Intercepting invoke for '" + getServiceName(baseService) + "'");

		if (_isActive && (mockedPipeline=isMocked(getServiceName(baseService), pipeline)) != null) {
			
			// don't call service, instead return mocked pipeline
		
			ServiceUtils.debugLog(0, "JcTestSuite", "mocking service '" + getServiceName(baseService) +"'");
			
			IDataUtil.merge(mockedPipeline, pipeline);
			
			status.setReturnValue(pipeline);
			
		} else {
			
			// continue as normal
			
            if(chain.hasNext())
                ((InvokeChainProcessor) chain.next()).process(chain, baseService, pipeline, status);
        }
	}

	public static void recordRunningSuite(String suiteId) {
		
		_runningSuites.put(getRootContextID(), suiteId);
	}
	
	private IData isMocked(String serviceName, IData pipeline) {
		
		String id = getSuiteId(pipeline);
		
		return MockedServicesManager.defaultInstance().mockedPipelineForSuiteAndService(id, serviceName, pipeline);
	}
	
	private String getServiceName(BaseService baseService) {
	
		return baseService.getNSName().getFullName();
	}
	
	private String getSuiteId(IData pipeline) {
		
		String id = getRootContextID();
		
		if (id != null)
			return _runningSuites.get(id);
		else
			return null;
	}
	
	private static String getRootContextID() 
    {
        String root = null;

        try {
            String contextIDStack[] = InvokeState.getCurrentState().getAuditRuntime().getContextStack();
           
            if (contextIDStack.length > 0) {
            		root = contextIDStack[0];
            }
            
        } catch (Exception e) {
            // do now't
        }

        return root;
    }
}
