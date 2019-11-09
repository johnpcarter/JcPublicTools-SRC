package com.jc.wm.junit.def.wm;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.jc.wm.junit.def.Payload;
import com.jc.wm.junit.runtime.wm.ServiceInvoker.InvalidServiceException;
import com.wm.app.b2b.server.ServerAPI;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataUtil;
import com.wm.util.Values;

public class MockedService implements Payload<IData> {

	public static final String				ID = "id";
	public static final String				DESCRIPTION = "description";
	public static final String				ACTIVE = "active";
	public static final String				ASSOCIATED_SUITES = "suites";
	public static final String				MOCK_SERVICE = "mockService";
	public static final String				MOCK_SVR = "serverAlias";
	public static final String				MATCH_ORIGIN = "match";
	public static final String				MATCH_CONTENT_TYPE = "matchContentType";
	public static final String				MATCH_SVC = "matchService";
	public static final String				MATCH_LOC = "matchLocation";
	
	public String description;
	
	private IData _data;

	private PayloadOrigin _origin;
	private PayloadContentType _contentType;
	private String _serviceName;
	
	private Exception _e;
	
	private List<String> _associatedSuites;
	
	private String _id;
	private String _packageName;
	private boolean _active;
	private String _baseDirForFileLoc;
	private String _fileLocForMock;
	private String _serviceForMock;
	private String _serverAliasForMock;
	
	private PayloadOrigin _matchOrigin;
	private PayloadContentType _matchContentType;
	private String _matchEndPoint;
	private IData _matchPipeline;
	private Exception _matchException;
	
	private Exception _error;

	public MockedService(String packageName, String id, PayloadOrigin origin, PayloadContentType contentType, String serviceName, String description) {
		
		_id = id;
		_packageName = packageName;
		_origin = origin;
		_contentType = contentType;
		_serviceName = serviceName;
		
		this.description = description;
	}

	public MockedService(String packageName, String id, PayloadOrigin origin, PayloadContentType contentType, String serviceName, String description, IData matchPipeline, String mockFileLoc) throws InvalidPayloadException {
		
		this(packageName, id ,origin, contentType, serviceName, description);
		
		setFileSource(contentType, mockFileLoc);
	}
	
	public MockedService(String packageName, String id, PayloadOrigin origin, PayloadContentType contentType, String serviceName, String description, IData matchPipeline, String serviceForMock, String serverAliasForMock) throws InvalidPayloadException, InvalidServiceException {
		
		this(packageName, id, origin, contentType, serviceName, description);
	
		setServiceSource(contentType, serviceForMock, serverAliasForMock);
	}
	
	public MockedService(String packageName, Values def) {
		
		_id = def.getString(ID);
		_packageName = packageName;
		description = def.getString(DESCRIPTION);

		_origin = PayloadOrigin.valueOf(def.getString(IDataPayloadSource.TEST_RQRP_TYPE));
		
		if (def.getString(IDataPayloadSource.TEST_RQRP_CONTENT) != null)
			_contentType = PayloadContentType.valueOf(def.getString(IDataPayloadSource.TEST_RQRP_CONTENT));
		
		_serviceName = def.getString(IDataPayloadSource.TEST_RQRP_SVC);
		
		String[] suitesArray = def.getStringArray(ASSOCIATED_SUITES);
		
		if (suitesArray != null)
		{
			_associatedSuites = new ArrayList<String>();
			for (int i = 0; i < suitesArray.length; i++)
				_associatedSuites.add(suitesArray[i]);
		}
		
		_active = def.getBoolean(ACTIVE);
		_baseDirForFileLoc = new File(ServerAPI.getPackageConfigDir(_packageName).getParentFile(), "resources").getAbsolutePath();

		if (_origin == PayloadOrigin.file) {
			
			_fileLocForMock = def.getString(IDataPayloadSource.TEST_RQRP_LOC);

		} else {
			
			_serviceForMock = def.getString(MOCK_SERVICE);
			_serverAliasForMock = def.getString(MOCK_SVR);
		}
		
		String matchType = def.getString(MATCH_ORIGIN);
		
		if (matchType != null) {
			
			_matchOrigin = PayloadOrigin.valueOf(matchType);
			
			if (_matchOrigin == PayloadOrigin.file) {
				
				if (def.getString(MATCH_CONTENT_TYPE) != null) 
					_matchContentType = PayloadContentType.valueOf(def.getString(MATCH_CONTENT_TYPE));
				else
					_matchContentType = PayloadContentType.idata;
				
				_matchEndPoint = def.getString(MATCH_LOC);
				
			} else {
				
				_matchEndPoint = def.getString(MATCH_SVC);
			}
			
		}
	}
	
	public void setMatchCriteria(PayloadOrigin origin, PayloadContentType contentType, String endPoint) throws InvalidPayloadException, InvalidServiceException {
		
		_matchOrigin = origin;
		_matchContentType = contentType;
		_matchEndPoint = endPoint;
		
		_matchPipeline = null;
		
		_getMatchedPipeline();
	}
	
	@Override
	public IData getData() {
	
		if (_data == null) {
			
			if (_origin == PayloadOrigin.file) {
				
				try {
					_data = new PayloadFileSource(_baseDirForFileLoc, _contentType, _fileLocForMock).getData();
					
				} catch (InvalidPayloadException e) {
					_error = e;
				}
			} else {
				
				try {
					_data = new PayloadServiceSource(null, _contentType,  _serviceForMock, _serverAliasForMock).getData();
					
				} catch (Exception e) {
					_error = e;
					ServerAPI.logError(e);
				}
			}
		}
		
		return _data;
	}
	
	public Exception getLoadException() {
	
		return _error;
	}
	
	public Exception getMatchException() {
		return _matchException;
	}
	
	public void setSource(MockedService m) throws com.jc.wm.junit.def.Payload.InvalidPayloadException, InvalidServiceException {
		
		_active = m._active;
		_e = m._e;
		_associatedSuites = m._associatedSuites;

		_origin = m._origin;
		
		if (_origin == PayloadOrigin.file)
			setFileSource(m._contentType, m._fileLocForMock);
		else
			setServiceSource(m._contentType, m._serviceForMock, m._serverAliasForMock);
	}
	
	public void setFileSource(PayloadContentType contentType, String fileLoc) throws InvalidPayloadException {
		
		_origin = PayloadOrigin.file;
		_contentType = contentType;
		
		_fileLocForMock = fileLoc;
		
		String configLoc = new File(ServerAPI.getPackageConfigDir(_packageName).getParentFile(), "resources").getAbsolutePath();
				
		_data = new PayloadFileSource(configLoc, _contentType, _fileLocForMock).getData();
	}
	
	public void setServiceSource(PayloadContentType contentType, String serviceName, String serverAlias) throws InvalidPayloadException, InvalidServiceException {
		
		_origin = PayloadOrigin.file;
		_contentType = contentType;
		
		_serviceForMock = serviceName;
		_serverAliasForMock = serverAlias;
		_data = new PayloadServiceSource(null, _contentType, _serviceForMock, _serverAliasForMock).getData();
	}
	
	public String getId() {
		return _id;
	}
	
	public String packageName() {
		return _packageName;
	}
	
	public boolean isActive() {
		
		return _active;
	}
	
	public void setIsActive(boolean active) {
		
		_active = active;
	}
	
	public void addSuite(String suiteId) {
		
		if (_associatedSuites == null)
			_associatedSuites = new ArrayList<String>();
		
		_associatedSuites.add(suiteId);
	}
	
	public void removeSuite(String suiteId) {
		
		_associatedSuites.remove(suiteId);
		
		if (_associatedSuites.size() == 0)
			_associatedSuites = null;
	}
	
	public Values toValues() {
		
		Values v = new Values();
		
		v.setValue(ID, _id);
		v.setValue(IDataPayloadSource.TEST_RQRP_TYPE, _origin.toString());
		
		if (_contentType != null)
			v.setValue(IDataPayloadSource.TEST_RQRP_CONTENT, _contentType.toString());
		
		v.setValue(IDataPayloadSource.TEST_RQRP_SVC, _serviceName);
		v.setValue(ACTIVE, "" + _active);
		
		if (_associatedSuites != null)
			v.setValue(ASSOCIATED_SUITES, _associatedSuites.toArray(new String[_associatedSuites.size()]));
		
		if (_origin == PayloadOrigin.file) {
			
			v.setValue(IDataPayloadSource.TEST_RQRP_LOC, _fileLocForMock);

		} else {
			
			v.setValue(MOCK_SERVICE, _serviceForMock);
			v.setValue(MOCK_SVR, _serverAliasForMock);
		}

		if (_matchOrigin != null) {
			
			if (_matchOrigin == PayloadOrigin.file) {
				
				v.setValue(MATCH_ORIGIN, PayloadOrigin.file.toString());
				v.setValue(MATCH_CONTENT_TYPE, _matchContentType.toString());
				v.setValue(MATCH_LOC, _matchEndPoint);
				
			} else {
				
				v.setValue(MATCH_ORIGIN, PayloadOrigin.service.toString());
				v.setValue(MATCH_SVC, _matchEndPoint);
			}
		}
		
		return v;
	}
	
	@Override
	public PayloadOrigin getSrcOrigin() {
		
		return _origin;
	}

	@Override
	public PayloadContentType getContentType() {
		
		return _contentType;
	}

	@Override
	public String getSrcInfo() {
		
		return _serviceName;
	}

	public boolean matchesSuite(String id) {
		
		return this._active && (_associatedSuites == null || _associatedSuites.contains(id));
	}
	
	public boolean matchesPipeline(IData pipeline) {
		
		if (this._matchPipeline != null || this.getMatchedPipeline()) {
			
			boolean match = true;

			if (this._matchPipeline != null && pipeline != null) {
				
				// check top level keys only
				
				IDataCursor pc = pipeline.getCursor();
				
				IDataCursor c = _matchPipeline.getCursor();
				c.first();
				
				do {
					String key = c.getKey();
					Object v = c.getValue();
					
					if (!IDataUtil.get(pc, key).equals(v))
					{
						match = false;
						break;
					}
					
				} while (c.next());
				
				pc.destroy();
			}
			
			return match;
			
		} else {
			
			// unable to fetch criteria, don't allow match
			
			return false;
		}
	}
	
	private boolean getMatchedPipeline() {
		
		boolean found = true;
		
		try {
			_getMatchedPipeline();
		} catch (Exception e) {
			_matchException = e;
			found = false;
			ServerAPI.logError(e);
		}
			
		return found;
	}
		
	private void _getMatchedPipeline() throws InvalidPayloadException, InvalidServiceException {
		
		if (this._matchOrigin != null) {
			
			if (this._matchPipeline == null) {
				
				if (this._matchOrigin == PayloadOrigin.file) {
					
					this._matchPipeline = new PayloadFileSource(_baseDirForFileLoc, this._matchContentType, this._matchEndPoint).getData();
					
				} else {
					
					this._matchPipeline = new PayloadServiceSource(null, this._matchContentType, this._matchEndPoint, null).getData();
				}
			}
		}
	}

	@Override
	public Exception getError() {
		
		return _e;
	}

	@Override
	public List<ComparisonError> getComparisonErrors() {
		// TODO Auto-generated method stub
		return null;
	}
}
