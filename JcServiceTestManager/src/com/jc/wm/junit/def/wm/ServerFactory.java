package com.jc.wm.junit.def.wm;

import java.io.File;

import com.jc.wm.junit.def.Server;
import com.wm.util.Values;
import com.wm.util.ValuesRegistry;

public class ServerFactory {

	private static ServerFactory _default;
	
	private ValuesRegistry _servers;
	
	public static ServerFactory defaultInstance() {
		
		if (_default == null)
			_default = new ServerFactory();
		
		return _default;
	}
	
	private ServerFactory() {
		
		this.load();
	}
	
	public String[] getAvailableAliases() {
		
		return _servers.getValueKeys();
	}
	
	public Server getServerForAlias(String alias) {
		
		if (alias != null) {
			
			return new Server() {
				
				String _alias;
				Values _wmserver;
				
				public Server init(String alias, Values server) {
					_alias = alias;
					_wmserver = server;
					
					return this;
				}
				
				@Override
				public String getAlias() {
					
					return _alias;
				}
				
				@Override
				public String getHost() {
					
					return _wmserver.getString("host");
				}
				
				@Override
				public String getPort() {
					
					return _wmserver.getString("port");
				}
				
				@Override
				public String getUser() {
					
					return _wmserver.getString("user");
				}
				
				@Override
				public String getPassword() {
					
					return _wmserver.getString("password");
				}
				
				public String getSsl() {
					
					return _wmserver.getString("ssl");
				}
			}.init(alias, getServer(alias));
			
		} else {
			
			return null;
		}
	}
	
	private void load() {
		
		File remoteServersFile = new File(com.wm.app.b2b.server.Server.getConfDir(), "remote.cnf");
		
		_servers = new ValuesRegistry(remoteServersFile);
	}
	
	private Values getServer(String alias) {
		
		return (Values) _servers.get(alias);
	}
	
	private String[] getValueKeys()
    {
        return (new String[] {
            "alias", "host", "port", "user", "handle", "ssl", "keyStoreAlias", "keyAlias", "acl", "keepalive", 
            "timeout", "retryServer"
        });
    }
}
