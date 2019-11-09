package com.jc.wm.junit.runtime.wm;


import com.jc.wm.junit.def.Payload;
import com.jc.wm.junit.def.Server;
import com.jc.wm.junit.def.TestRunner;
import com.jc.wm.junit.def.wm.IDataPayloadSource;

import com.wm.data.IData;
import com.wm.util.Values;

public class ServiceTestRunner extends ServiceInvoker implements TestRunner<IData, IData> {
	
		private String _suiteId;
		
		public ServiceTestRunner(String suiteId, String service) throws InvalidServiceException {
			super(service, null, null);
			
			_suiteId = suiteId;
		}
		
		public static ServiceTestRunner serviceTestRunnerForLocalUser(String suiteId, String service, String user) throws InvalidServiceException {
			
			ServiceTestRunner r = new ServiceTestRunner(suiteId, service);
			r._user = user;
			
			return r;
		}
		
		public static ServiceTestRunner serviceTestRunnerForRemoteServer(String suiteId, String service, Server server) throws InvalidServiceException {
			
			ServiceTestRunner r = new ServiceTestRunner(suiteId, service);
			r._server = server;
			
			return r;
		}
		
		@Override
		public String toString() {
			
			if (_server != null) {
				
				if (_user != null)
					return _service + "(" + _server.getUser() + ")" + "@" + _server.getHost() + ":" + _server.getPort();
				else
					return _service + "@" + _server.getHost() + ":" + _server.getPort();
			} else {
				return _service;
			}
		}
		
		public String getService()  {
			
			return _service;
		}

		public Payload<IData> runTest(Payload<IData> request) {
			
			if (_suiteId != null)
				InvokeInterceptor.recordRunningSuite(_suiteId);
		
			try {
				return createResponse(super.run(request.getData()));
			} catch (Exception e) {
				return createErrorResponse(e);
			}
		}
		
		protected Payload<IData> createResponse(IData data) {
			
			return new ServiceResponseImpl(data);
		}
		
		protected Payload<IData> createErrorResponse(Exception e) {
			
			return new ServiceResponseImpl(e);
		}
		
	    private class ServiceResponseImpl extends IDataPayloadSource {

	    		private IData 				_raw;
	    			    		
	    		public ServiceResponseImpl(Exception e) {
	    			
	    			super(PayloadOrigin.error);
	    			
	    			_contentType = PayloadContentType.empty;
	    			_error = e;
	    		}
	    		
	    		public ServiceResponseImpl(IData response) {
	    			
	    			super(PayloadOrigin.service);
	    			_contentType = PayloadContentType.idata;
	    			
	    			_raw = response;
	    		}
	    		
	    		@Override
	    		public boolean equals(Object obj) {
	    			
	    			if (obj instanceof Payload<?>) {
	    				
	    				try {
	    					IData response = this.getData();
	    					IData match = (IData) ((Payload<?>) obj).getData();
	    						    					
	    					return comparePipelines(match, response, false);
	    					
	    				} catch (InvalidPayloadException e) {
	    					
	    					_error = e;
	    					
	    					return false;
	    				}
	    				
	    			} else {
	    				
	    				if (obj != null)
	    					_error = new Exception("Object is not a valid pipeline: " + obj.getClass().getSimpleName());
	    				else 
	    					_error = new Exception("Object to compare with is null");
	    				
	    				return false;
	    			}
	    		}

			@Override
			public String getSrcInfo() {
				return ServiceTestRunner.this.getService();
			}
			
			@Override
			public Values toValues() {
				return null;
			}

			@Override
			public IData getData() {
				
				return _raw;
			}
			
			@Override
			public Exception getError() {
				
				return _error;
			}
	    }
}
