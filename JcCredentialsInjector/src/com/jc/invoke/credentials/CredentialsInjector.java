package com.jc.invoke.credentials;

import java.lang.reflect.Field;
import java.util.ArrayList;

import java.util.Iterator;
import java.util.List;

import com.wm.app.b2b.server.BaseService;
import com.wm.app.b2b.server.ServerAPI;
import com.wm.app.b2b.server.ServiceException;
import com.wm.app.b2b.server.invoke.InvokeChainProcessor;
import com.wm.app.b2b.server.invoke.InvokeManager;
import com.wm.app.b2b.server.invoke.ServiceStatus;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;
import com.wm.util.ServerException;

public class CredentialsInjector implements InvokeChainProcessor {
	
    public static CredentialsInjector    defaultInstance;
    
    private ConnectionManager _mgr;
    private boolean _throwError = false;
    
	public static void register(String[] connections, CredentialsProvider provider, boolean throwError) {
    	
    	InvokeManager.getDefault().registerProcessor(new CredentialsInjector(connections, provider, throwError));
    }
    
    public static void unregister() {
    	InvokeManager.getDefault().unregisterProcessor(defaultInstance);
    }
    
    public CredentialsInjector(String[] connections, CredentialsProvider provider, boolean throwError) {
    	
        System.out.println("Instantiating com.jc.invoke.credentials.CredentialsInjector");
        defaultInstance = this;
        
        _throwError = throwError;
        
       _mgr = new ConnectionManager(connections, provider);
    }
    
    public IData[] status() {
    	
    	List<IData> connections = new ArrayList<IData>();
    	
    	for (String s : _mgr.connections()) {
    		Credentials c = _mgr.get(s);
    		
    		IData stat = IDataFactory.create();
    		IDataCursor cursor = stat.getCursor();
    		IDataUtil.put(cursor, "Connection", c.id);
    		IDataUtil.put(cursor, "Object", c.altId);
    		IDataUtil.put(cursor, "username", c.userName);
    		IDataUtil.put(cursor, "password", c.password == null ? "none" : "****");
    		IDataUtil.put(cursor, "status", c.lastError == null ? (c.isBusy() ? "updating" : c.password != null ? "active" : "ready") : "failed");
    		
    		if (c.lastError != null) {
    			IDataUtil.put(cursor, "failure", c.lastError.getMessage());
    		}
    		
    		cursor.destroy();
    		
    		connections.add(stat);
    	}
    	
    	return connections.toArray(new IData[connections.size()]);
    }
    
    public void clearErrorForCredentials(String connectionAlais) {
    
    	Credentials c = _mgr.get(connectionAlais);
    	
    	if (c != null) {
    		c.lastError = null;
    	}
    }
    
    
    public void process(@SuppressWarnings("rawtypes") Iterator chain, BaseService baseService, IData pipeline, ServiceStatus status) throws ServiceException {
        
    	final String connName = getConnectionName(baseService);
    	
        if (connName != null) {
                
        	//System.out.println("START - adapter service for " + connName);
        	
        	_mgr.get(connName).onAvailable( (c) -> 
            	{
            		//System.out.println("AVAILABLE - adapter connection available for " + connName);
            		
            		InvokeChainProcessor processor = null;
            		
					try {
						if (chain.hasNext()) {
							processor = (InvokeChainProcessor) chain.next();
							processor.process(chain, baseService, pipeline, status);
						}
						
					} catch(Exception error) {
													
						if ((error.getMessage().contains("Access denied") 
								|| error.getMessage().contains("Unable to get a connection to resource") || error.getMessage().contains("Resource not available")) && c.lastError == null && _mgr.isManaged(c)) {
							
							// got here in case where new connection could not be established because password or user has changed
							// need to intercept and fix it with password 
							
				        	//System.out.println("ERROR - access denied");

							if (_mgr.update(c)) { // detect error, so as not to try again!!
								
								// try again!
								
								//System.out.println("ERROR - connection update success");
								
								try {
									processor.process(chain, baseService, pipeline, status);
									
								} catch (ServerException e) {
									throw new RuntimeException(e);
								}
							} else {
								
								//System.out.println("ERROR - connection update failed");

								if (_throwError) {
									throw new RuntimeException(c.lastError);
								} else {
									ServerAPI.logError(c.lastError);
								}
								
								throw new RuntimeException(error);
							}
							
						} else {
							throw new RuntimeException(error);
						}						
					}
				}
            );
        	
        	//System.out.println("END - adapter service");

        } else if (chain.hasNext()) {
        	try {
				((InvokeChainProcessor)chain.next()).process(chain, baseService, pipeline, status);
			} catch (ServerException e) {
				e.printStackTrace();
				throw new ServiceException(e);
			}
        }
    }
    
    protected String getConnectionName(BaseService baseService) {
    	
    	
    	String connName = null;
    	
    	if (baseService.getClass().getSimpleName().equals("AdapterServiceNode")) {
    		
    		try {
    			Field f = baseService.getClass().getDeclaredField("_defaultConnectionName");
    			f.setAccessible(true);
    			connName = (String) f.get(baseService);
			
    		} catch (NoSuchFieldException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (SecurityException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (IllegalArgumentException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (IllegalAccessException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}
    	
    	return connName;
    }
    
    protected String getServiceName(BaseService baseService) {
        return baseService.getNSName().getFullName();
    }
}
