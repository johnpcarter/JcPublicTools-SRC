package com.jc.invoke.credentials;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class ConnectionManager {

    private HashMap<String, Credentials> _connections;
    private CredentialsProvider _provider;
    
    private List<String> _managedConnections;
    
    public ConnectionManager(String[] managedConnections, CredentialsProvider p) {
		
    	_connections = new HashMap<String, Credentials>();
    	
    	if (managedConnections != null && managedConnections.length > 0) {
    		_managedConnections = new ArrayList<String>(managedConnections.length);
    	
    		for (String c : managedConnections) {
    			_managedConnections.add(c);
    			Credentials creds = new Credentials(c);
    			creds.altId = p.providerIdForId(c);
    			_connections.put(c, creds);
    		}
    	}
    	
    	_provider = p;
	}
    
    public Set<String> connections() {
    	return _connections.keySet();
    }

    public synchronized Credentials get(String connectionName) {
    	
    	Credentials c = _connections.get(connectionName);
    	
    	if (c == null) {
    		c = new Credentials(connectionName);
    		_connections.put(connectionName, c);
    	}
    	    	
    	return c;
    }

    public boolean update(Credentials c) {
    	    	    	
    	synchronized (this) {
    		if (_connections.get(c.id) == null) {
        		_connections.put(c.id, c);
        	}
		}
    	    
    	
    	return c.updateCredentials(_provider);
    }
    
    public boolean isManaged(Credentials c) {
    	return (_managedConnections == null || _managedConnections.size() == 0 || _managedConnections.contains(c.id));
    }
}
