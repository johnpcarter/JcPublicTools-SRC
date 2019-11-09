package com.jc.wm.junit.def.wm;

import com.wm.data.IData;
import com.wm.util.Values;

import java.util.List;

import com.jc.wm.junit.def.Payload;
import com.jc.wm.junit.def.TestCase;
import com.jc.wm.junit.def.Payload.InvalidPayloadException;
import com.jc.wm.junit.def.Payload.PayloadContentType;
import com.jc.wm.junit.def.Payload.PayloadOrigin;
import com.jc.wm.junit.def.Server;
import com.jc.wm.junit.runtime.wm.ServiceInvoker.InvalidServiceException;
import com.jc.wm.junit.runtime.wm.ServiceTestRunner;

public class ServiceTestCase extends TestCase<IData, IData> {

	public static final String		ACTIVE = "active";
	public static final String		ID = "id";
	public static final String		DESCRIPTION = "description";
	public static final String		TEST_SVC = "service";
	
	public static final String		TEST_SVR = "serverAlias";
	public static final String		TEST_USER = "user";
	public static final String		TEST_REQUEST = "requestTemplate";
	public static final String		TEST_RESPONSE = "responseTemplate";
	
	
	public String user;
	public Server server;
	
	private ServiceTestCase(String suiteId, String id, String description) {
		
		super(suiteId, id, description);
	}

	public ServiceTestCase(String suiteId, String configLoc, Values def) throws InvalidPayloadException, InvalidServiceException {
		
		this.isActive = def.getBoolean(ACTIVE);

		this.id = def.getString(ID);
		this.endPoint = def.getString(TEST_SVC);
		this.description = def.getString(DESCRIPTION);

		String serverAlias = def.getString(TEST_SVR);
		
		if (serverAlias != null)
			server = ServerFactory.defaultInstance().getServerForAlias(serverAlias);
		
		user = def.getString(TEST_USER);
		
		Values request = def.getValues(TEST_REQUEST);
		Values response = def.getValues(TEST_RESPONSE);
		
		PayloadOrigin rqType = PayloadOrigin.valueOf(request.getString(IDataPayloadSource.TEST_RQRP_TYPE));
		PayloadOrigin rsType = PayloadOrigin.valueOf(response.getString(IDataPayloadSource.TEST_RQRP_TYPE));

		this._requestTemplate = makeTemplateFromValues(rqType, configLoc, request);
		this._responseTemplate = makeTemplateFromValues(rsType, configLoc, response);
		
		if (server == null)
			_runner = ServiceTestRunner.serviceTestRunnerForLocalUser(suiteId, endPoint, serverAlias);
		else
			_runner = ServiceTestRunner.serviceTestRunnerForRemoteServer(suiteId, endPoint, server);
	}
	
	public static ServiceTestCase testCaseForLocalService(String suiteId, String id, String service, String description, String user) throws InvalidServiceException {
		
		ServiceTestCase s = new ServiceTestCase(suiteId, id, description);
	
		s._runner = ServiceTestRunner.serviceTestRunnerForLocalUser(suiteId, service, user);
		
		return s;
	}
	
	public static ServiceTestCase testCaseForRemoteService(String suiteId, String id,  String service, String description, String serverAlias) throws InvalidServiceException {
	
		return testCaseForRemoteService(suiteId, id, service, description, ServerFactory.defaultInstance().getServerForAlias(serverAlias));
	}
	
	public static ServiceTestCase testCaseForRemoteService(String suiteId, String id,  String service, String description, Server server) throws InvalidServiceException {
		
		ServiceTestCase s = new ServiceTestCase(suiteId, id, description);
		
		s._runner = ServiceTestRunner.serviceTestRunnerForRemoteServer(suiteId, service, server);
		
		return s;
	}
	
	public Values toValues(boolean includeResults) {
		
		Values out = new Values();
		out.put(ID, this.id);
		out.put(DESCRIPTION, this.description);
		out.put(TEST_SVC, this.endPoint);
		
		out.put(ACTIVE, "" + this.isActive);
		
		if (this.server != null)
			out.put(TEST_SVR, this.server.getAlias());
		
		out.put(TEST_USER, this.user);
		
		out.put(TEST_REQUEST, this._requestTemplate.toValues());
		out.put(TEST_RESPONSE, this._responseTemplate.toValues());

		return out;
	}

	@Override
	public void addRequestTemplate(String configLoc, PayloadOrigin srcOriginType, PayloadContentType srcContentType, String location, String server) throws InvalidPayloadException {
		
		try {
			this._requestTemplate = makeTemplate(configLoc, srcOriginType, srcContentType, location, server);
		} catch (InvalidServiceException e) {
			throw new InvalidPayloadException(e);
		}
	}

	@Override
	public void addResponseTemplate(String configLoc, PayloadOrigin srcOriginType, PayloadContentType srcContentType, String location, String server) throws  InvalidPayloadException {

		try {
			this._responseTemplate = makeTemplate(configLoc, srcOriginType, srcContentType, location, server);
		} catch (InvalidServiceException e) {
			throw new InvalidPayloadException(e);
		}
	}
	
	private Payload<IData> makeTemplate(String configLoc, PayloadOrigin origin, PayloadContentType contentType, String location, String server) throws InvalidPayloadException, InvalidServiceException {
		
		if (origin == PayloadOrigin.file) {
			return new PayloadFileSource(configLoc, contentType, location);
		
		} else if (origin == PayloadOrigin.service) {
			
			return new PayloadServiceSource(suiteId, contentType, location, server);
						
		} else if (origin == PayloadOrigin.error) {
			return new ServicePayloadErrorSource(location);
		} else {
			throw new InvalidPayloadException("Cannot process template of type " + origin.toString());
		}
	}

	private Payload<IData> makeTemplateFromValues(PayloadOrigin origin, String configLoc, Values def) throws InvalidPayloadException, InvalidServiceException {
		
		if (origin == PayloadOrigin.file)
			return new PayloadFileSource(configLoc, def);
		else if (origin == PayloadOrigin.service)
			return new PayloadServiceSource(suiteId, def);
		else if (origin == PayloadOrigin.error)
			return new ServicePayloadErrorSource(def);
		else
			throw new InvalidPayloadException("Cannot process template of type " + origin.toString());
	}
	
	private class ServicePayloadErrorSource implements Payload<IData> {

		private Exception _exception;
		
		public ServicePayloadErrorSource(Values def) {
		
			_exception = new Exception(def.getString(IDataPayloadSource.TEST_RQRP_ERR));
		}
		
		public ServicePayloadErrorSource(String errorMessage) {
			
			_exception = new Exception(errorMessage);
		}
		
		@Override
		public PayloadOrigin getSrcOrigin() {
			
			return PayloadOrigin.error;
		}

		@Override
		public PayloadContentType getContentType() {
			
			return PayloadContentType.empty;
		}

		@Override
		public String getSrcInfo() {
			
			return _exception.getMessage();
		}
		
		@Override
		public Values toValues() {
			
			Values v = new Values();
			
			v.setValue(IDataPayloadSource.TEST_RQRP_TYPE, PayloadOrigin.error);
			v.setValue(IDataPayloadSource.TEST_RQRP_CONTENT, PayloadContentType.empty);
			v.setValue(IDataPayloadSource.TEST_RQRP_ERR, _exception.getMessage());
			
			return v;
		}

		@Override
		public IData getData() throws InvalidPayloadException {
			
			return null;
		}

		@Override
		public Exception getError() {
			
			return _exception;
		}

		@Override
		public List<ComparisonError> getComparisonErrors() {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
}
