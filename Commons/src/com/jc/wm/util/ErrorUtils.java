package com.jc.wm.util;

import com.jc.util.*;
import com.jc.wm.exception.LocalisedException;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Convenience methods for converting Exception objects into webMethods documents and vice-versa.
 * 
 * @author John Carter (integrate@johncarter.eu)
 * @version 1.0
 */
public class ErrorUtils 
{
	/**
	 * Converts a webMethods IData array of error into a java map, keyed by reference
	 * 
	 * @param errors Map of webMethods formatted errors
	 * @return Map of simple errors, referenced by their error code
	 */
	public static Map<String, String> convertErrorDocToMap(IData[] errors)
	{
		Map<String, String> errorMap = new HashMap<String, String>();
	
		if ( errors != null)
		{
			for ( int i = 0; i < errors.length; i++ )
			{
				IDataCursor errorsCursor = errors[i].getCursor();
				String	reference = IDataUtil.getString(errorsCursor, Constants.ERR_REF);
				String	error = IDataUtil.getString(errorsCursor, Constants.ERR_MSG);
				errorsCursor.destroy();
	
				errorMap.put(reference, error);
			}
		}
	
		return errorMap;
	}
	
	/**
	 * Converts an error report produced by the service bus into a IData list that can be
	 * used by webMethods.
	 * 
	 * @param errorMap
	 * @return Map of errors documents formatted
	 */
	public static IData[] convertErrorMapToDoc(Map<String, String> errorMap)
	{
		IData[] errors = null;
	
		if ( errorMap != null && errorMap.size() > 0)
		{
			errors = new IData[errorMap.size()];
			int i = 0;
	
			for (Iterator<String> it = errorMap.keySet().iterator(); it.hasNext();)
			{
				String reference = it.next();
				String error = errorMap.get(reference);
	
				IData errorDoc = IDataFactory.create();
				IDataCursor errorsCursor = errorDoc.getCursor();
				IDataUtil.put(errorsCursor, Constants.ERR_REF, reference);
				IDataUtil.put(errorsCursor, Constants.ERR_MSG, error);
				errorsCursor.destroy();
	
				errors[i++] = errorDoc;
			}
		}
	
		return errors;
	}
	
	/**
	 * Creates a pipeline object containing the exception formatted using the webMethods exception event document type
	 * as its template. The result can be used directly as a pipeline output as it already contains a reference
	 * called '_lastError'.
	 * 
	 * @param e the error to be converted
	 * @return IData document complete with a '_lastError' reference
	 */
	public static IData convertExceptionToIData(Exception e)
	{
		return convertExceptionToIData(e, null);
	}
	
	/**
	 * Creates a pipeline object containing the exception formatted using the webMethods exception event document type
	 * as its template. The result can be used directly as a pipeline output as it already contains a reference
	 * called '_lastError'.
	 * 
	 * @param e the error to be converted
	 * @param pipeline Optional IData structure to be included in the error
	 * @return pipeline complete with a '_lastError' reference
	 */
	public static IData convertExceptionToIData(Exception e, IData pipeline)
	{
		if (e == null)
			return null;
		
		StringWriter dump = new StringWriter();
	    e.printStackTrace(new PrintWriter(dump));
	    
	    IData errContent = IDataFactory.create();
	    IDataCursor c = errContent.getCursor();
	    IDataUtil.put(c, "time", (new Date()).toString());
	    
	    if (e instanceof FormattedException)
	    	IDataUtil.put(c, "errorCode", "" + ((FormattedException) e).getErrorCode());
	    else if (e instanceof LocalisedException)
	    	IDataUtil.put(c, "errorCode", "" +  ((LocalisedException) e).getErrorCode());
	    
	    IDataUtil.put(c, "error", e.getMessage());
	    IDataUtil.put(c, "localizedError", e.getLocalizedMessage());
	    IDataUtil.put(c, "errorType", e.getClass().getSimpleName());
	    IDataUtil.put(c, "errorDump", dump.toString());
	    
	    if (e.getStackTrace().length > 0)
	    	IDataUtil.put(c, "service", e.getStackTrace()[0].toString());
	    
	    IDataUtil.put(c, "pipeline", pipeline);
	    c.destroy();
	    
	    IData err = IDataFactory.create();
	    c = err.getCursor();
	    IDataUtil.put(c, "lastError", errContent);
	    c.destroy();
	    
	    return err;
	}	
}
