package com.jc.wm.terracotta.db.client;

import java.util.Optional;
import java.util.StringTokenizer;

import com.wm.app.b2b.server.ServerAPI;
import com.wm.app.b2b.server.ServiceException;
import com.wm.app.b2b.server.globalvariables.GlobalVariablesException;
import com.wm.app.b2b.server.globalvariables.GlobalVariablesManager;
import com.wm.passman.PasswordManagerException;
import com.wm.util.JournalLogger;

public class DefaultIDataDataSetConnectionManager {

public static IDataDataSetConnectionManager _default;
	
	public static final String GP_TCDB_URI_KEY = "idata.terracotta.db.cluster.url";
	public static final String GP_MIN_CONNS = "idata.terracotta.db.min.connections";
	public static final String GP_MAX_CONNS = "idata.terracotta.db.max.connections";
	public static final String GP_MAX_WAIT = "idata.terracotta.db.max.connections.wait";

	public static final String GP_DOC_DATA_SETS = "idata.terracotta.db.documents";
	public static final String DEFAULT_OFH_RESOURCE_NAME = "main";
	public static final String DEFAULT_OFR_DISK_NAME = "data";
	
	public static void configureDefault() throws ServiceException {
		 
		try {
			_default = new IDataDataSetConnectionManagerWithISGlobalProperties(getGlobalProperty(GP_TCDB_URI_KEY));
		} catch (GlobalVariablesException e) {
	
			throw new ServiceException("Cannot setup default IDataDataSetConnectionManager due to missing property: " + e.getMessage());

		} catch (PasswordManagerException e) {
			
			throw new ServiceException(e);
		}
	}
	
	public static class IDataDataSetConnectionManagerWithISGlobalProperties extends IDataDataSetConnectionManager {

		private long _maxConnectionWait = 3000;
		
		public IDataDataSetConnectionManagerWithISGlobalProperties(String url) throws ServiceException {
			
			super(url);
			
			configureIDataDataSets();
		}
		
		public synchronized Optional<IDataDatasetConnectionImpl> getConnection(String documentType) throws ServiceException {
			
			return super.getConnection(documentType, _maxConnectionWait);
		}
		
		private void configureIDataDataSets() throws ServiceException {
			
			StringTokenizer tk;
			
			try {
				tk = new StringTokenizer(getGlobalProperty(GP_DOC_DATA_SETS), ",");
			} catch(Exception e)
			{
				throw new ServiceException("Please define the Document Types to be stored via global property " + GP_DOC_DATA_SETS);
			}
			
			int minConnections = getIntGlobalProperty(GP_MIN_CONNS, 3);
			int maxConnections = getIntGlobalProperty(GP_MAX_CONNS, 10);
			_maxConnectionWait = getLongGlobalProperty(GP_MAX_WAIT, 10000);

			while (tk.hasMoreTokens())
			{
				String documentType = tk.nextToken();
				String offHeapResourceName = DEFAULT_OFH_RESOURCE_NAME;
				String offHeapDiskResourceName = DEFAULT_OFR_DISK_NAME;
				
				if (documentType.contains(";"))
				{
					int idx = documentType.indexOf(";");
					offHeapResourceName = documentType.substring(idx+1);
					documentType = documentType.substring(0, idx);
					
					if (!offHeapResourceName.equals("main"))
						offHeapDiskResourceName = offHeapResourceName + "_dsk";
				}
				
				debugLog("com.jc.wm.terracotta.db.client.DefaultIDataDataSetConnectionManager", "Establising Terracotta TCStore Dataset for '" + documentType + "', with offheap resources: " + offHeapResourceName + ", " + offHeapDiskResourceName);

				try {
					establishDataSetPool(documentType, offHeapResourceName, offHeapDiskResourceName, minConnections, maxConnections);
				} catch(Exception e)
				{
					debugLog("com.jc.wm.terracotta.db.client.DefaultIDataDataSetConnectionManager", "** ERROR ** - Failed to establish Terracotta TCStore Dataset for '" + documentType + "', see error log for details");
					ServerAPI.logError(new ServiceException("Failed to establish Terracotta TCStore Dataset for '" + documentType + "', due to exception: " + e.getMessage()));
				}
			}
		}
	}
	
	private static int getIntGlobalProperty(String key, int defaultValue) {
		
		try {
			return Integer.parseInt(GlobalVariablesManager.getInstance().getGlobalVariableValue(key).getValue());
		} catch (Exception e) {
			
			debugLog("com.jc.wm.terracotta.db.client.DefaultIDataDataSetConnectionManager", "** WARNING ** - No property defined for '" + key + "', using default");
			return defaultValue;
		} 
		
	}
	
	private static long getLongGlobalProperty(String key, long defaultValue) {
		
		try {
			return Long.parseLong(GlobalVariablesManager.getInstance().getGlobalVariableValue(key).getValue());
		} catch (Exception e) {
			
			debugLog("com.jc.wm.terracotta.db.client.DefaultIDataDataSetConnectionManager", "** WARNING ** - No property defined for '" + key + "', using default");
			
			return defaultValue;
		} 
	}
	
	/*private static String getGlobalProperty(String key, String defaultValue) {
		
		try {
						
			return GlobalVariablesManager.getInstance().getGlobalVariableValue(key).getValue();
		} catch (Exception e) {
			
			debugLog("com.jc.wm.terracotta.db.client.DefaultIDataDataSetConnectionManager", "** WARNING ** - No property defined for '" + key + "', using default");
			
			return defaultValue;
		} 
	}*/

	private static String getGlobalProperty(String key) throws GlobalVariablesException, PasswordManagerException {
		
		debugLog("com.jc.wm.terracotta.db.client.DefaultIDataDataSetConnectionManager", "getting value for " + key);

		return GlobalVariablesManager.getInstance().getGlobalVariableValue(key).getValue();
	}
	
	protected final static void debugLog(String function, String message)
    {          
    		if (function == null)
    			JournalLogger.log(3, 90, 0, message);
    		else
    			JournalLogger.log(4, 90, 0, function, message);
     }
}
