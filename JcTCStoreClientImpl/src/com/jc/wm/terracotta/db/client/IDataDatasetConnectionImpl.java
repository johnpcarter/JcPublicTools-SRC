package com.jc.wm.terracotta.db.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.terracottatech.store.Cell;
import com.terracottatech.store.Dataset;
import com.terracottatech.store.DatasetWriterReader;
import com.terracottatech.store.Record;
import com.terracottatech.store.StoreException;
import com.terracottatech.store.Type;
import com.terracottatech.store.UpdateOperation;
import com.terracottatech.store.UpdateOperation.CellUpdateOperation;
import com.terracottatech.store.configuration.DatasetConfiguration;
import com.terracottatech.store.definition.CellDefinition;
import com.terracottatech.store.manager.DatasetManager;
import com.wm.app.b2b.server.ServerAPI;
import com.wm.app.b2b.server.ServiceException;
import com.wm.data.IData;

/**
 * Wraps a Dataset connection to the Terracotta DB to allows complex webMethods document types to be persisted by flattening structure.
 * Cells cannot contain cells, nor lists of any kinds, which means that the document structure has to be flattened in order 
 * to support complex document types e.g.
 * 
 * Order: IData
 *   - Header: IData
 *   	- id: String
 *   	- customerRefs: String[]
 *   
 * becomes
 * 
 * Header.id: 12345
 * Header.customerRefs[0]: C001
 * Header.customerRefs[1]: C002
 * 
 * The advantage over a serialized or String conversion being that the content of the document is searchable.
 * 
 * @author John Carter (john.carter@softwareag.com)
 *
 */
public class IDataDatasetConnectionImpl implements IDataDatasetConnection {

	private URI _connectUri;
	private String _ofhName;
	private String _ofrName;
	private String _documentType;
	private boolean _isBusy;
	private boolean _closed;
	
	private DatasetManager _dsm;
			
	/**
	 * Defines a new connection to given the terracotta db for the given offHeap resource and disk resource
	 *  
	 * @param tcdbURI - format of terracotta://<server1>:<port>,<server2>:<port>
	 * @param offHeapResourceName name as defined in tc-config.xml file at 'ohr:resource name', default is 'main'
	 * @param offHeapDiskResourceName name of disk resource in tc-config.xml file at 'data:directory', default is 'data'
	 * 
	 */
	protected IDataDatasetConnectionImpl(URI tcdbURI, String offHeapResourceName, String offHeapDiskResourceName) {
		
		_connectUri = tcdbURI;
		_ofhName = offHeapResourceName;
		_ofrName = offHeapDiskResourceName;
	}
	
	@Override
	public void finalize() {
		
		if (!_closed)
		{
			ServerAPI.logError(new ServiceException("com.jc.wm.terracotta.db.clientDatasetConnection closed on finalize, should have been closed explicitly beforehand, risk of resource leakage"));
			_dsm.close();
		}

	}
	
	/**
	 * Establishes a single connection to the terracotta DB for a data set representing the given document type
	 * using the params given on object instantiation.
	 * 
	 * @param _documentType name of document type to be used to identify the dataset.
	 * @return self, ensure streaming syntax can be used if wished
	 * 
	 * @throws ServiceException if connection cannot be established or offheap parameters are invalid
	 */
	public IDataDatasetConnectionImpl connect(String documentType) throws ServiceException {
		
		try {
			
			DatasetManager datasetManager = DatasetManager.clustered(_connectUri).build();
			  
			DatasetConfiguration dataSetConfig = datasetManager.datasetConfiguration().offheap(_ofhName).disk(_ofrName).build();  
			datasetManager.newDataset(documentType, Type.STRING, dataSetConfig);
			
		// cache the manager for later
			
			_documentType = documentType;
			_dsm = datasetManager;
		}
		catch (StoreException e) {
			
			throw new ServiceException(e);
		}
		
		return this;
	}
	
	/**
	 * Closes the underling connection to the DB, ensure that this object is disposed after use. Cannot be reused
	 */
	public void close() {
	
		_dsm.close();
		_closed = true;
	}
	
	/**
	 * Identifies whether the connection is in use
	 * 
	 * @return Returns true if this connection is use
	 */
	public boolean isBusy() {
		
		return _isBusy;
	}
	
	/**
	 * flattens and stores the given webMethods document with the given key as reference
	 * 
	 * @param key Unique key, used to later fetch the document.
	 * @param doc The webMethods document to be stored.
	 * @return true if inserted, false if exists already and update performed
	 * 
	 * @throws ServiceException Underlying TCStore connection error
	 */
	public synchronized boolean upsertIData(String key, IData doc) throws ServiceException {
			
		try {
			_isBusy = true;
			
			DatasetWriterReader<String> writerReader = _dsm.getDataset(_documentType, Type.STRING).writerReader();
			
			Record<String> record = new IDataRecord(key, doc);
			
			if (writerReader.add(key, record))
			{
				return true; // inserted
			}
			else
			{
				// already added, update
					
				//UpdateOperation<Long> aggregrate = _update(record);
				//writerReader.update(key, UpdateOperation.allOf(aggregrate));
					
				_update(record); // no reference to dataset is required, works by magin according to documentation.
				
				return false;
			}
		}
		catch(StoreException e)
		{
			throw new ServiceException(e);
		}
		finally
		{
			_isBusy = false;
		}
	}
	
	/**
	 * Retrieves the given webMethods document type for the given key
	 * 
	 * @param key Key previously used in ({@link #upsertIData(String, IData)} method
	 * @return Optional webMethods document instance
	 * 
	 * @throws ServiceException Underlying TCStore connection error
	 */
	public synchronized Optional<IData> getIData(String key) throws ServiceException {
				
		try {
			_isBusy = true;

			DatasetWriterReader<String> writerReader = _dsm.getDataset(_documentType, Type.STRING).writerReader();
			
			Optional<Record<String>> optionalRecord;
			
			if ((optionalRecord = writerReader.get(key)).isPresent())
			{
				return Optional.of(IDataRecord.recordToIData(optionalRecord.get())); 
			}
			else
			{
				return Optional.empty();
			}
		}
		catch(StoreException e)
		{
			throw new ServiceException(e);
		}
		finally
		{
			_isBusy = false;
		}
	}
	
	/**
	 * Delete the webMethods document from the underling dataset using the same key as used to store ({@link #upsertIData(String, IData)})
	 * @param key key used in {@link #upsertIData(String, IData)} method.
	 * @return true if found and deleted, false otherwise
	 * 
	 * @throws ServiceException Underlying TCStore connection error
	 */
	public synchronized boolean delete(String key) throws ServiceException {
		
		try {
			_isBusy = true;
						
			return _dsm.getDataset(_documentType, Type.STRING).writerReader().delete(key);
		}
		catch(StoreException e)
		{
			throw new ServiceException(e);
		}
		finally
		{
			_isBusy = false;
		}
	}
	
	private UpdateOperation<Long> _update(Record<String> record)
	{
		List<CellUpdateOperation<?, ?>> ops = new ArrayList<CellUpdateOperation<?, ?>>();
		
		record.forEach(cell -> {
			
			CellDefinition<?> d = cell.definition();
			ops.add(UpdateOperation.write(d.name(), d.value()));
		});
		
		//UpdateOperation<Long> aggregate = UpdateOperation.allOf(ops.toArray(new CellUpdateOperation<?, ?>[ops.size()])); 
		return UpdateOperation.install((Cell<String>[]) record.toArray());
	}
}
