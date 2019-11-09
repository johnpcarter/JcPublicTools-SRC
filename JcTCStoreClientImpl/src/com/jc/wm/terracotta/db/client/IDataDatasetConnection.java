package com.jc.wm.terracotta.db.client;

import java.util.Optional;

import com.wm.app.b2b.server.ServiceException;
import com.wm.data.IData;

/**
 * Represents a Dataset connection for storage/retrieving webMethods documents to the Terracotta DB. 
 * Requirement is to support complex documents types as cells cannot contain other cells, nor lists of any kinds. 
 * 
 * The implementation should impose a single dataset for a given document type. This will make
 * queries easier to implement by ensuring only a single document type is associated with the dataset.
 * 
 * @author John Carter (john.carter@softwareag.com)
 *
 */
public interface IDataDatasetConnection {

	/**
	 * Establishes a single connection to the terracotta DB for a data set representing the given document type
	 * using the params given on object instantiation.
	 * 
	 * @param _documentType name of document type to be used to identify the dataset.
	 * @return self, ensure streaming syntax can be used if wished
	 * 
	 * @throws ServiceException if connection cannot be established or offheap parameters are invalid
	 */
	public IDataDatasetConnectionImpl connect(String documentType) throws ServiceException;
	
	/**
	 * Closes the underling connection to the DB, ensure that this object is disposed after use. Cannot be reused
	 */
	 public void close();
	 
	 /**
	 * Identifies whether the connection is in use
	 * 
	 * @return Returns true if this connection is use
     */
	public boolean isBusy();
	
	/**
	 * flattens and stores the given webMethods document with the given key as reference
	 * 
	 * @param key Unique key, used to later fetch the document.
	 * @param doc The webMethods document to be stored.
	 * @return true if inserted, false if exists already and update performed
	 * 
	 * @throws ServiceException Underlying TCStore connection error
	 */
	 public boolean upsertIData(String key, IData doc) throws ServiceException;
	 
	 /**
	 * Retrieves the given webMethods document type for the given key
	 * 
	 * @param key Key previously used in ({@link #upsertIData(String, IData)} method
     * @return Optional webMethods document instance
     * 
     * @throws ServiceException Underlying TCStore connection error
	 */
	public Optional<IData> getIData(String key) throws ServiceException;
	
	/**
	 * Delete the webMethods document from the underling dataset using the same key as used to store ({@link #upsertIData(String, IData)})
	 * @param key key used in {@link #upsertIData(String, IData)} method.
	 * @return true if found and deleted, false otherwise
	 * 
	 * @throws ServiceException Underlying TCStore connection error
	 */
	public boolean delete(String key) throws ServiceException;
}
