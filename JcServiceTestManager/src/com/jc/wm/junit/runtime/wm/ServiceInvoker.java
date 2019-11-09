package com.jc.wm.junit.runtime.wm;

import com.jc.wm.junit.def.Payload;
import com.jc.wm.junit.def.Server;
import com.jc.wm.junit.def.wm.IDataPayloadSource;
import com.wm.app.b2b.client.Context;
import com.wm.app.b2b.client.ServiceException;
import com.wm.app.b2b.server.InvokeState;
import com.wm.app.b2b.server.Service;
import com.wm.app.b2b.server.Session;
import com.wm.app.b2b.server.User;
import com.wm.app.b2b.server.UserManager;
import com.wm.data.IData;
import com.wm.data.IDataFactory;
import com.wm.util.Values;

public class ServiceInvoker {

	/** 
	 * Used to identify the webMethods root context id based in runtime-attribute array
	 * returned by InvokeState. Attention this will have to be tested for each webMethods
	 * version as this is not official.
	 */
	public static final int			WM_ROOT_CONTEXT_ID_INDEX = 0;

	protected String _service;
	protected Server _server;
	protected String _user;
	
	public ServiceInvoker(String service, Server server, String userId) throws InvalidServiceException {
		
		_service = service;
		_server = server;
		_user = userId;
		
		if (_service == null || _service.indexOf(":") == -1)
			throw new InvalidServiceException(_service);
	}
	
	public IData run() throws Exception {
		return run(null);
	}
	public IData run(IData request) throws Exception {
		
		int idx = _service.indexOf(":");
		String ns = _service.substring(0, idx);
		String svc = _service.substring(idx+1);
		
		IData pipeline = request != null ? request : IDataFactory.create();
		
		if (_server != null)
		{
			return invokeRemoteService(ns, svc, pipeline);
		}
		else
		{
			// record root context id against suite id, required for mocking interface
				
			return invokeLocalService(ns, svc, pipeline, null, null);
		}
	}
	
	private IData invokeRemoteService(String ns, String svc, IData inPipeline) throws ServiceException, InvalidConnectionException
    {
        // Connect to server - edit for alternate server
        String  host = _server.getHost() + ":" + _server.getPort();
        Context context = new Context();

		String user = _server.getUser() != null ? _server.getUser() : "Administrator";
		String password = _server.getPassword() != null ? _server.getPassword() : "manage";
		
        // To use SSL:
        //
        // context.setSecure(true);

        // Optionally send authentication certificates
        //
        // String  cert    = "c:\\myCerts\\cert.der";
        // String  privKey = "c:\\myCerts\\privkey.der";
        // String  cacert  = "c:\\myCerts\\cacert.der";
        // context.setSSLCertificates(cert, privKey, cacert);

        try {
            context.connect(host, user, password);
        } catch (ServiceException e) {
            throw new InvalidConnectionException(e);
        }
        
         IData outputDocument = null;
         
         try {
        	 	outputDocument = context.invoke(ns, svc, inPipeline);
         } finally {
 	        context.disconnect();
         }
           
        return outputDocument;
    }
	
	private IData invokeLocalService(String ns, String svc, IData inputPipeline, String rootContextId, Session session) throws Exception
    {
 // Bug in wm, means that if we try to invoke services outside of service-pool we get a null pointer
 // exception. Following if fixes the problem by ensuring we have session and user assigned.

	 		if (InvokeState.getCurrentState() == null || InvokeState.getCurrentState().getSession() == null)
	 		{ 
	 			if (session != null)
	 			{
	 				InvokeState.setCurrentSession(session);
	 				InvokeState.setCurrentUser(session.getUser());	 
	 			}
	 			else
	 			{ 
	 				setInvokeStateFor(_user != null ? _user : "Administrator");
	 			}
	 		}
		 
	 		if (rootContextId != null) // allows async integrations to be linked via an optional root context id / activation id
	 			setRootContextIdForThread(rootContextId);
		 
	 		return Service.doInvoke(ns, svc, inputPipeline);
    } 
	
	protected static void setInvokeStateFor(String userId)
    {
    		setInvokeStateFor(UserManager.getUser(userId));	
    }
    
    protected static void setInvokeStateFor(User user)
    {
    		Session session = Service.getSession();
    	
    		if (session == null)
    			session = new Session("esb");
    	
    		InvokeState state = InvokeState.getCurrentState();
    		if (state == null)
    		{
    			state = new InvokeState();
    			InvokeState.setCurrentState(state);
    		}
    	
    		state.setSession(session);
    		InvokeState.setCurrentUser(user);
    }
    
    private static void setRootContextIdForThread(String activationId)
    {
    		InvokeState is = InvokeState.getCurrentState();
 			 
 		if (is != null)
 		{
     		String[] args = null;

 			if (is.getAuditRuntime() != null)
 			{
 				args = is.getAuditRuntime().getContextStack();
 				
 				if (args.length <= WM_ROOT_CONTEXT_ID_INDEX)
 					args = new String[WM_ROOT_CONTEXT_ID_INDEX+1];
 				
				args[WM_ROOT_CONTEXT_ID_INDEX] = activationId;
         		InvokeState.getCurrentState().getAuditRuntime().setContextStack(args);
 			}
 		}
    }
    
    public class InvalidServiceException extends Exception {
    	
		private static final long serialVersionUID = 7452349573186298413L;

			public InvalidServiceException(String name) {
			
				super("Service name is " + name == null ? "null" : ("invalid: " + name));
    		}
    }
    
    public class InvalidConnectionException extends Exception {

		private static final long serialVersionUID = -6404707231416448947L;

			public InvalidConnectionException(Exception e) {
				super(e);
			}
    }
}
