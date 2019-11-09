package com.jc.wm.junit.def.wm;

import com.jc.wm.junit.def.Server;
import com.jc.wm.junit.runtime.wm.ServiceTestRunner;
import com.jc.wm.junit.runtime.wm.ServiceInvoker;
import com.jc.wm.junit.runtime.wm.ServiceInvoker.InvalidServiceException;
import com.wm.data.IData;
import com.wm.util.Values;

public class PayloadServiceSource extends IDataPayloadSource {
		
	public String service;
	public Server server;
	
	private ServiceInvoker _runner;
	
	public PayloadServiceSource(String suiteId, Values def) throws InvalidServiceException {
		
		super(PayloadOrigin.service);
		
		if (def.getString(IDataPayloadSource.TEST_RQRP_CONTENT) != null)
			_contentType = PayloadContentType.valueOf(def.getString(IDataPayloadSource.TEST_RQRP_CONTENT));
		
		this.service = def.getString(ServiceTestCase.TEST_SVC);
		String alias = def.getString(ServiceTestCase.TEST_SVR);
		String user = def.getString(ServiceTestCase.TEST_USER);
		
		if (alias != null)
			server = ServerFactory.defaultInstance().getServerForAlias(alias);
		
		if (server != null)
			_runner = ServiceTestRunner.serviceTestRunnerForRemoteServer(suiteId, service, server);
		else
			_runner = ServiceTestRunner.serviceTestRunnerForLocalUser(suiteId, service, user);
	}
	
	public PayloadServiceSource(String suiteId, PayloadContentType type, String service, String serverAlias) throws InvalidServiceException {
		
		super(PayloadOrigin.service);
		
		_contentType = type;
		
		_runner = new ServiceInvoker(service, ServerFactory.defaultInstance().getServerForAlias(serverAlias), null);
	}
	
	@Override
	public Values toValues() {
		
		Values v = new Values();
		
		v.setValue(IDataPayloadSource.TEST_RQRP_TYPE, _origin.toString());
		
		if (_contentType != null)
			v.setValue(IDataPayloadSource.TEST_RQRP_CONTENT, _contentType.toString());
		
		v.setValue(IDataPayloadSource.TEST_RQRP_SVC, service);
		
		if (server != null)
			v.setValue(ServiceTestCase.TEST_SVR, server.getAlias());
		
		return v;
	}
	
	@Override
	public IData getData() throws InvalidPayloadException {
		
		try {
			return _runner.run();
		} catch (Exception e) {
			throw new InvalidPayloadException(e);
		}
	}

	@Override
	public String getSrcInfo() {
		
		return _runner.toString();
	}
} 
