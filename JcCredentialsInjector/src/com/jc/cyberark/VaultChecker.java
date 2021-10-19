package com.jc.cyberark;

import java.util.Map;
import java.util.function.BiConsumer;

import com.wm.app.b2b.server.Service;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

import com.jc.invoke.credentials.CredentialsProvider;

public class VaultChecker implements CredentialsProvider {

	private String _endpoint;
	private String _appId;
	private String _safe;
	private Map<String, String> _connectionAliases;
	
	public VaultChecker(String endpoint, String appId, String safe, Map<String, String> aliases) {
		_endpoint = endpoint;
		_appId = appId;
		_safe = safe;
		_connectionAliases = aliases;
	}
	
	@Override
	public String providerIdForId(String alias) {
		return _connectionAliases.get(alias);
	}
	
	@Override
	public Exception updateCredentials(String connectionAlias, BiConsumer<String, String> func) {
		
		IData input = IDataFactory.create();
		IDataCursor inputCursor = input.getCursor();
		IDataUtil.put(inputCursor, "endpoint", _endpoint);
		IDataUtil.put(inputCursor, "AppID", _appId);
		IDataUtil.put(inputCursor, "Safe", _safe);

		IDataUtil.put(inputCursor, "Object", _connectionAliases.containsKey(connectionAlias) ? _connectionAliases.get(connectionAlias) : connectionAlias);
		inputCursor.destroy();

		try {
			IData output = Service.doInvoke("wm.cyberark.priv.api", "fetchCredentials", input);
			
			IDataCursor outputCursor = output.getCursor();
			String user = IDataUtil.getString(outputCursor, "username");
			String password = IDataUtil.getString(outputCursor, "password");
			String errorMessage = IDataUtil.getString(outputCursor, "errorMessage");
			outputCursor.destroy();

			if (user != null && password != null) {
				
				func.accept(user, password);
				
				return null;

			} else {
				
				return new RuntimeException(errorMessage);
			}
			
		} catch( Exception e) {
			
			//System.out.print("call to cyberark failed due to : " + e.getMessage());
			
			return e;
		}
			
	}

	
}
