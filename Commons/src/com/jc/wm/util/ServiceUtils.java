package com.jc.wm.util;

import java.io.File;
import java.util.Stack;
import java.util.regex.Pattern;

import com.wm.data.IData;
import com.wm.lang.flow.FlowException;
import com.wm.lang.ns.NSService;
import com.wm.util.JournalLogger;
import com.wm.app.b2b.server.AuditLogManager;
import com.wm.app.b2b.server.AuditRuntimeInfo;
import com.wm.app.b2b.server.EnvelopeData;
import com.wm.app.b2b.server.ISRuntimeException;
import com.wm.app.b2b.server.InvokeState;
import com.wm.app.b2b.server.ServerAPI;
import com.wm.app.b2b.server.Service;
import com.wm.app.b2b.server.ServiceException;
import com.wm.app.b2b.server.ServiceThread;
import com.wm.app.b2b.server.Session;
import com.wm.app.b2b.server.UnknownServiceException;
import com.wm.app.b2b.server.User;
import com.wm.app.b2b.server.UserManager;

import com.jc.wm.exception.IntegrationException;

/**
 * Provides convenience methods to be used from webMethods java services to simplify code lookup, localization etc.
 *
 * @author : John Carter (integrate@johncarter.eu)
 * @version : 1.0
 *
 */
public class ServiceUtils 
{
    /**
     * Identifies the service name in the calling service structure.
     */
    public static final String NODE_NAME = "node_nsName";
    
    /**
     * Identifier in NSService structure for package of calling service.
     */
    public static final String NODE_PKG = "node_pkg";
    
	/** 
	 * Used to identify the webMethods root context id based in runtime-attribute array
	 * returned by InvokeState. Attention this will have to be tested for each webMethods
	 * version as this is not official.
	 */
	public static final int			WM_ROOT_CONTEXT_ID_INDEX = 0;
	

	public static final String 		SERVER_ID_PROPERTY = "server.id";
	
    /**
     * Used to remove illegal characters in messages.
     */
    public static final Pattern ERRORMESSAGE_PARSER_PATTERN = Pattern.compile("\\[[a-zA-Z0-9_.:]*/[a-zA-Z0-9_.:]*\\]\\[[a-zA-Z0-9_]*/[a-zA-Z0-9_]*\\].*");
    // Match for a string like : "[MyPackage/my.package.my_rep:my_Service][Error9189/Appli_18]My localized message"
    public static final String ERRORMESSAGE_SPLIT_PATTERN = "[\\[\\]/]";
    private static final String EXITWITHEXCEPTION_SERVICE = "alu.common.flow:exitWithException";

    /**
     * Returns the absolute path to the config directory identified by the calling
     * service i.e. [WM_HOME/IntegrationServer/packages/[package]/config
     *
     * @return File path to a package config directory
     */
    public static File getConfigDirectoryForCallingPackage() {
        return ServerAPI.getPackageConfigDir(getPackageForCaller());
    }

    /**
     * Returns the absolute path to alternative config directory identified by the calling
     * service i.e. [WM_HOME/IntegrationServer/config/[package]
     *
     * @return File path to a package config directory
     */
    public static File getAlternativeDirectoryForCallingPackage() {
        return new File(ServerAPI.getServerConfigDir(), getPackageForCaller());
    }

    /**
     * Return the session id associated with the context of the calling service
     *
     * @return unique id of the session associated with the calling context
     */
    public static String getSession() {
        String sessionId = null;

        if (Service.getSession() != null) {
            try {
                sessionId = Service.getSession().getSessionID();
            } catch (Exception e) {
                throw new RuntimeException("Unable to retrieve the session ID : " + e);
            }
        }

        return sessionId;
    }

    /**
     * Return the user id associated with the context of the calling service.
     *
     * @return unique id of the user associated with the calling context
     */
    public static String getUserId() {
        String user = null;

        if (Service.getUser() != null) {
            try {
                user = Service.getUser().getName();
            } catch (Exception e) {
                throw new RuntimeException("Unable to retrieve the user ID : " + e);
            }
        }

        return user;
    }
    
    /**
     * Returns the server id on which this integration server is running. 
     * Attempts to use the java system property 'server.id' or if not found generates an
     * id based on the webMethods server and port attributes.
     * 
     * @return id of server including default port.
     */
	public static String getServerId() 
	{
		String serverId = System.getProperty(SERVER_ID_PROPERTY);
		
		if (serverId == null)
			serverId =  ServerAPI.getServerName() + ":" + System.getProperty("watt.server.port");
		
		return serverId;
	}
	
    /**
     * Return the service name associated with the context of the calling service.
     *
     * @return service name associated with the calling context
     */
    public static String getCallingService() 
    {
        String callingService = Service.getParentServiceName();

        if (callingService == null && Service.getCallingService() != null) 
        {
            try 
            {
                callingService = (String) Service.getCallingService().getValues().get(NODE_NAME);
            } 
            catch (Exception e) {
                throw new RuntimeException("Error, could not determine calling service : " + e);
            }
        }

        return callingService;
    }
    
    /**
     * Return the package name associated with the context of the calling service.
     *
     * @return package name associated with the calling context
     */
    public static String getPackageForCaller() 
    {
        return getPackageForCaller(false);
    }
    
    public static String getPackageForCaller(boolean ifNotExistGetCurrent) 
    {
    	String packageName = null;

        try {
            NSService caller = Service.getCallingService();
            if (caller == null && ifNotExistGetCurrent) {
            	packageName = Service.getPackageName();
            } else {
            	packageName = caller.getPackage().getName();
            }
            
           // if (caller == null || caller.getValues() == null || (packageName = (String) caller.getValues().get(NODE_PKG)) == null) {
           //     packageName = Service.getPackageName(null);
           // }
        } catch (Exception e) {
            throw new RuntimeException("Cannot determine package name");
        }

        return packageName;
    }

    /**
     * Return the context id's associated with the context of the calling service
     * in a list where 0 is the contextId, 1 is the parentContextId and 2 is the
     * rootContextId.
     *
     * @return array of execution IDs of a service into a String[]
     */
    public static String[] getContextIDsForService() 
    {
        String[] contextIDs = {null, null, null};

        try {
            InvokeState currentInvokeState = InvokeState.getCurrentState();
            Stack<?> servicesStack = currentInvokeState.getCallStack();
            String contextIDStack[] = currentInvokeState.getAuditRuntime().getContextStack();

            String contextId = null;
            String parentContextId = null;
            String rootContextId = null;

            int contextId_index = contextIDStack.length - 1;

            if (servicesStack.get(servicesStack.size() - 1).toString().equals(EXITWITHEXCEPTION_SERVICE) && contextId_index > 0) {
                contextId_index = contextId_index - 1;
            }

            contextId = contextIDStack[contextId_index];
            if (contextId_index > 0) {
                parentContextId = contextIDStack[contextId_index - 1];
            }
            rootContextId = contextIDStack[0];

            contextIDs[0] = contextId;
            contextIDs[1] = parentContextId;
            contextIDs[2] = rootContextId;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return contextIDs;
    }

    /**
     * Invoke IS service
     * 
     * @param inputPipeline             Input pipeline
     * @param service                       Service name with full name-space including ':'
     * @param ignoreErrors              ignore Errors if true
     * @return true if service completed successfully (only applicable if ignoreErrors = true)
     * @throws UnknownServiceException  Name space doesn't exist
     * @throws ServiceException         Service calling exception
     */
    public static boolean invokeService(IData inputPipeline, String service, boolean ignoreErrors) throws UnknownServiceException, ServiceException 
    {
    	return invokeService(inputPipeline, service, null, ignoreErrors);
    }
    
    /**
     * Invoke IS service
     *
     * @param inputPipeline Input pipeline
     * @param service Service name with full name-space including ':'
     * @param rootContextId forces the service to adopt this root context id as opposed to it's own 
     * @param ignoreErrors ignore Errors if true
     * @return true if service completed successfully (only applicable if ignoreErrors = true)
     * @throws UnknownServiceException Name space doesn't exist
     * @throws ServiceException Service calling exception
     */
    public static boolean invokeService(IData inputPipeline, String service, String rootContextId, boolean ignoreErrors) throws UnknownServiceException, ServiceException 
    {
    	if (service == null || !service.contains(":"))
    		throw new IntegrationException(0, "Invalid service : " + service);
    	
    	String ns = service.substring(0, service.indexOf(":"));
     	String svc = service.substring(service.indexOf(":")+1);
     	
     	return invokeService(inputPipeline, ns, svc, rootContextId, ignoreErrors);
    }
    
    public static boolean invokeService(IData inputPipeline, String ns, String svc, String rootContextId, boolean ignoreErrors) throws UnknownServiceException, ServiceException 
    {
    	return invokeService(inputPipeline, ns, svc, rootContextId, null, ignoreErrors);
    }
    
    protected static boolean invokeService(IData inputPipeline, String ns, String svc, String rootContextId, Session session, boolean ignoreErrors) throws UnknownServiceException, ServiceException 
    {
    	 try 
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
    				 setInvokeStateFor("Administrator");
    			 }
    		 }
    		 
    		 if (rootContextId != null) // allows async integrations to be linked via an optional root context id / activation id
    			 setRootContextIdForThread(rootContextId);
    		 
    		 Service.doInvoke(ns, svc, inputPipeline);

    		 return true;
         } 
    	 catch (UnknownServiceException e) // manage invalid name-space
         {
             throw e;
         }  
    	 catch (ServiceException e) 
         {
             if (ignoreErrors)
                 return false;
             else
                 throw e;
         } 
    	 catch(ISRuntimeException e)
    	 {
    		 throw e;
    	 }
    	 catch(RuntimeException e)
    	 {
    		 throw new ISRuntimeException(e);	// make sure adapter based runtime exceptions are propagated without wrapping
    	 }
         catch (Exception e) 
         {
             throw new ServiceException(e);	// some other type of service occurred
         }
    }    
    
    /**
     * Invokes the services in a separate thread, but still blocks until execution has completed. This method is used
     * where you want to manage the sub-service as a separate task distinct from the caller as if it is a top-level
     * service. Useful if you want to take advantage of attributes such as 'delay until service success' for the publish
     * service, but flag success during the execution of your own service.
     * 
     * @param inputPipeline the pipeline to be passed to the service
     * @param service the service name with the complete name-space included
     * @param ignoreErrors if true, no flow exception are escalated
     * @return true if service completed successfully (only applicable if ignoreErrors = true)
     * @throws UnknownServiceException
     * @throws ServiceException
     */
    public static boolean invokeServiceInSeparateThread(IData inputPipeline, String service, boolean ignoreErrors) throws UnknownServiceException, ServiceException
    {
    	return invokeServiceInSeparateThread(inputPipeline,  service, null, ignoreErrors, false);
    }
    
    public static boolean invokeServiceInSeparateThread(IData inputPipeline, String service, String activationId, boolean ignoreErrors, boolean isAsync) throws UnknownServiceException, ServiceException
    {
    	if (service == null || !service.contains(":"))
    		throw new IntegrationException(0, "Invalid service : " + service);
    	
    	String ns = service.substring(0, service.indexOf(":"));
		String svc = service.substring(service.indexOf(":")+1);
		
		if (isAsync)
		{
			invokeServiceInSeparateThread(inputPipeline, ns, svc,  0, activationId);
			return true;
		}
		else
		{
			return invokeServiceInSeparateThread(inputPipeline, ns, svc, ignoreErrors, activationId);
		}
    }
    
    /**
     * Invokes the services in a separate thread, but still blocks until execution has completed. This method is used
     * where you want to manage the sub-service as a separate task distinct from the caller as if it is a top-level
     * service. Useful if you want to take advantage of attributes such as 'delay until service success' for the publish
     * service, but flag success during the execution of your own service.
     * 
     * @param inputPipeline the pipeline to be passed to the service
     * @param ns Service name-space
     * @param service Service name
     * @param ignoreErrors if true, no flow exception are escalated
     * @param rootContextId forces the service to adopt this root context id as opposed to it's own
     * @return true if service completed successfully (only applicable if ignoreErrors = true)
     * @throws UnknownServiceException
     * @throws ServiceException
     */
    public static boolean invokeServiceInSeparateThread(IData inputPipeline, String ns, String service, boolean ignoreErrors, String rootContextId) throws UnknownServiceException, ServiceException 
    { 
    	try 
	    {
    		invokeServiceInSeparateThread(inputPipeline, ns, service, 0, rootContextId).getIData();
    		
    		return true;
	    } 
    	catch (UnknownServiceException e) // manage invalid name-space
	    {
    		throw e;
	    } catch (FlowException e) // general error in sub-service, not directly mapping related
	    {
	        throw new ServiceException(e);
	    } 
		catch (ServiceException e) 
		{
	        if (ignoreErrors) 
	            return false;
	        else 
	            throw e;
	    } 
	    catch (Exception e) 
	    {
	        throw new ServiceException(e);	// some other type of service occurred
	    }	
    }
    
    /**
     * Invokes the given service in a separated thread obtained from the integration server thread pool.
     * 
     * @param inputPipeline the pipeline to be passed to the service
     * @param ns Service name-space
     * @param service Service name
     * @param maxWait delay to wait for an available thread from the central pool
     * @param rootContextId forces the service to adopt this root context id as opposed to it's own
     * @return the thread responsible for executing the service.
     */
    public static ServiceThread invokeServiceInSeparateThread(IData inputPipeline, String ns, String service, long maxWait, String rootContextId)
    {
    	setInvokeStateFor("Administrator");	// Assign a new InvokeContext to make sure it is completely separate from current
		
		 if (rootContextId != null) // allows async integrations to be linked via an optional root context id / activation id
			 setRootContextIdForThread(rootContextId);
		 
    	return Service.doThreadInvoke(ns, service, inputPipeline, maxWait);
    }
    
    public static boolean isPackageAvailable(String packageName)
    {
    	String[] enabledPackages = ServerAPI.getEnabledPackages();
    	
    	boolean found = false;
    	int i = 0;
    	while (!found && i<enabledPackages.length)
    		found = enabledPackages[i++].equals(packageName);
    	
    	return found;	
    }
    
    public static final void debugLog(int lvl, String function, String message) throws ServiceException
    {          
    		if (function == null)
    			JournalLogger.log(3, 90, lvl, message);
    		else
    			JournalLogger.log(4, 90, lvl, function, message);
     }
    
    /**
     * Logs either a Successful or Failed audit event for the calling service.
     * @param success true and Monitor will show successful completion of the service, false will show failed
     */
    public static void logServiceFailure(String reason, IData pipeline)
    {
        String externalID = null;
    		InvokeState state = InvokeState.getCurrentState();
    	
    		EnvelopeData env_data = state.getEnvelopeData();
    	
        if(env_data != null && !EnvelopeData.isISGenerated(externalID))
            externalID = env_data.getCurrentActivationID();
                
    		AuditLogManager.auditLog(getCallingService(), false, pipeline, AuditLogManager.STATUS_FAILED, reason, 0, externalID, new AuditRuntimeInfo());
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
}
