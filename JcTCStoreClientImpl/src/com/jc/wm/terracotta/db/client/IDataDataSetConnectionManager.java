package com.jc.wm.terracotta.db.client;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.wm.app.b2b.server.ServiceException;
import com.wm.data.IData;

/**
 * Manages a pool of DatasetConnections {@link IDataDatasetConnectionImpl} objects, which in turn allow complex webMethods documents to be stored
 * via the Terracotta DB TCStore API.
 * 
 * Documents are flattened in order to be compatible the TCStore Cell structure i.e. no lists nor hierarchical elements are allowed.
 * 
 * @author John Carter (john.carter@softwareag.com)
 *
 */
public class IDataDataSetConnectionManager {
	
	public static final String DEFAULT_TCDB_URI = "terracotta://tcdb:9410";
	public static final String DEFAULT_OFH_RESOURCE_NAME = "main";
	public static final String DEFAULT_OFR_DISK_NAME = "data";
	
	private URI _connectUri;
	
	private Map<String, DatasetManagerPool> _mgrs = new HashMap<String, DatasetManagerPool>();
	
	/**
	 * Create a new Pool manager for the given Terracotta DB URI
	 * 
	 * @param uri format of terracotta://<server1>:<port>,<server2>:<port>
	 * 
	 * @throws ServiceException thrown if URI syntax is invalid
	 */
	public IDataDataSetConnectionManager(String uri) throws ServiceException {
			
		try {
			_connectUri = new URI(uri);
		} catch(Exception e) {
			try {
				_connectUri = new URI(DEFAULT_TCDB_URI);
			} catch (URISyntaxException e1) {
				throw new ServiceException(e1);
			}
		}
	}
	
	@Override
	public void finalize() {
		
		_mgrs.forEach((id, val) -> {
			val.clearPool();
		});
	}
	
	/**
	 * Established a new pool of connections for the storage and retrieval of webMethods documents.
	 * 
	 * @param documentType The document type to be stored.
	 * 
	 * @param offHeapResourceName name as defined in tc-config.xml file at 'ohr:resource name', default is 'main'
	 * @param offHeapDiskResourceName name of disk resource in tc-config.xml file at 'data:directory', default is 'data'
	 * @param minConnections number of connections to be established on startup
	 * @param maxConnections maximum number of connections to be allowed, after which demands will be blocked until connection becomes available.
	 * 
	 * @throws ServiceException
	 */
	public void establishDataSetPool(String documentType, String offHeapResourceName, String offHeapDiskResourceName, int minConnections, int maxConnections) throws ServiceException {
		
		String ofhName = offHeapResourceName != null ? offHeapResourceName : DEFAULT_OFH_RESOURCE_NAME;
		String ofrName = offHeapDiskResourceName != null ? offHeapDiskResourceName : DEFAULT_OFR_DISK_NAME;
		
		_mgrs.put(documentType, new DatasetManagerPool(documentType, _connectUri, ofhName, ofrName, minConnections, maxConnections));
	}
	
	/**
	 * Returns a connection to allow a single CRUD operation to be called, once called is automatically released back to the pool
	 * thus a new call to this method will be required for further operations.
	 * 
	 * @param documentType Required because each document type has its own connection pool.
	 * @param maxConnectionWait 	maxConnectionWait number of milliseconds to wait for new connection.
	 * @return Optional<DatasetConnection> available connection or empty if no connection became available before timeout
	 * 
	 * @throws ServiceException thrown if no pool for given document type has been configured via {@link #establishDataSetPool(String, String, String, int, int)}
	 */
	public Optional<IDataDatasetConnectionImpl> getConnection(String documentType, long maxConnectionWait) throws ServiceException {
		
		DatasetManagerPool pool = _mgrs.get(documentType);
		
		if (pool != null)
		{
			return pool.getConnection(maxConnectionWait);
		}
		else
		{
			throw new ServiceException("Please establish connection pool for document type '" + documentType + "' via com.jc.wm.terracotta.db.client.establishDataSetPool() before calling this method please!");
		}
	}

	private class DatasetManagerPool {
		
		private String _documentType;

		private URI _connectUri;
		private String _ofhName;
		private String _ofrName;
		private int _min;
		private int _max;
		
		private List<IDataDatasetConnectionImpl> _conns = new ArrayList<IDataDatasetConnectionImpl>();
		
		public DatasetManagerPool(String documentType, URI connectUri, String ofhName, String ofrName, int minConnections, int maxConnections) throws ServiceException {
			
			_documentType = documentType;
			
			_connectUri = connectUri;
			_ofhName = ofhName;
			_ofrName = ofrName;
			_min = minConnections;
			_max = maxConnections;
			
			populatePool();
		}
		
		public void clearPool()
		{
			_conns.forEach((c) -> {
				c.close();
			});
			
			_conns.clear();
		}
		
		public synchronized Optional<IDataDatasetConnectionImpl> getConnection(long maxWait) throws ServiceException {
			
			IDataDatasetConnectionImpl conn = _conns.stream().filter(c -> !c.isBusy()).findFirst().get();
			
			if (conn == null && _conns.size() < _max)
			{
				// create new connection
				
				conn = new IDataDatasetConnectionWrapper(_connectUri, _ofhName, _ofrName).connect(_documentType);
				
				_conns.add(conn);
			}
			else if (maxWait > 0)
			{
				try {
					this.wait(maxWait); // wait for call from connectionFreed below
					
					return getConnection(-1);
					
				} catch (InterruptedException e) {
					throw new ServiceException("No avalable connections threads");
				}
			}

			if (conn != null)
				((IDataDatasetConnectionWrapper) conn).isAvailable = true;
			
			return Optional.of(conn);
		}
		
		public synchronized void notifyConnectionReleased(IDataDatasetConnectionImpl conn) {
			
			this.notify();
		}
		
		private void populatePool() throws ServiceException {
			clearPool();
			
			for (int i = 0; i < _min; i++)
				_conns.add(new IDataDatasetConnectionWrapper(_connectUri, _ofhName, _ofrName).connect(_documentType));
		}
		
		protected class IDataDatasetConnectionWrapper extends IDataDatasetConnectionImpl {

			public boolean isAvailable;
			
			protected IDataDatasetConnectionWrapper(URI tcdbURI, String offHeapResourceName, String offHeapDiskResourceName) {
				
				super(tcdbURI, offHeapResourceName, offHeapDiskResourceName);
			}
			
			@Override
			public synchronized boolean upsertIData(String key, IData doc) throws ServiceException {
				
				if (!isAvailable)
					throw new ServiceException("Connection has been released back to pool, please use com.jc.wm.terracotta.db.client.getConnnection() beforehand");
				
				try {
					return super.upsertIData(key, doc);
				} finally {
					isAvailable = false;
					DatasetManagerPool.this.notifyConnectionReleased(this);
				}
			}
			
			@Override
			public synchronized Optional<IData> getIData(String key) throws ServiceException {
				
				if (!isAvailable)
					throw new ServiceException("Connection has been released back to pool, please use com.jc.wm.terracotta.db.client.getConnnection() beforehand");
				
				try {
					return super.getIData(key);
				} finally {
					isAvailable = false;
					DatasetManagerPool.this.notifyConnectionReleased(this);
				}
			}
			
			@Override
			public synchronized boolean delete(String key) throws ServiceException {
				
				if (!isAvailable)
					throw new ServiceException("Connection has been released back to pool, please use com.jc.wm.terracotta.db.client.getConnnection() beforehand");
				
				try {
					return super.delete(key);
				} finally {
					isAvailable = false;
					DatasetManagerPool.this.notifyConnectionReleased(this);
				}
			}
		}
	}
}
