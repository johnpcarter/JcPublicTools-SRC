package com.jc.invoke.credentials;

import java.util.function.Consumer;

import com.wm.app.b2b.server.Service;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class Credentials {

    public String id;
    public String altId;
    public String userName;
    public String password;
    
    public Exception lastError;
    
    private boolean _isBusy;
    
    public Credentials(String id) {
    	this.id = id;
    }

    public boolean isBusy() {
    	return _isBusy;
    }
    
	public synchronized void onAvailable(Consumer<Credentials> func) {
		func.accept(this);
	}
	
	public synchronized boolean updateCredentials(CredentialsProvider provider) {
		
		_isBusy = true;
				
    	//System.out.println("CREDS - Updating credentials for " + id);
    	
		this.lastError = provider.updateCredentials(this.id, (userName, password) -> {
			
			//System.out.println("CREDS - Got credentials for " + id);
			
			updateCredentials(userName, password);
		});
		
    	//System.out.println("CREDS - Updating credentials success " + this.lastError == null);

		_isBusy = false;

		return this.lastError == null;
	}
	
	public synchronized void updateCredentials(String userName, String password) {
		
		IData input = IDataFactory.create();
		IDataCursor inputCursor = input.getCursor();
		IDataUtil.put(inputCursor, "connectionAlias", id);
		IDataUtil.put(inputCursor, "userName", userName);
		IDataUtil.put(inputCursor, "password", password);
		inputCursor.destroy();

		try {
			Service.doInvoke("wm.cyberark.pub.art", "updateCredentials", input);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}
