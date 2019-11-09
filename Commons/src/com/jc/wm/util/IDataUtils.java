package com.jc.wm.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.jc.wm.internal.XPathExpression;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;
import com.wm.util.coder.IDataXMLCoder;
import com.wm.util.coder.InvalidDatatypeException;

/**
 * Set of useful static methods for handling webMethods IData records.
 *
 * @author : John Carter (integrate@johncarter.eu)
 * @version : 1.0
 *
 */
public class IDataUtils
{
	/**
	 * Convenience method to convert a webMethods document into an byte array via an IDataXMLCoder instance
	 * 
	 * @param doc the webMethods document to convert
	 * @return byte array produced by IDataXMLCoder
	 * @throws IOException
	 */
	public static byte[] convertToBytes(IData doc) throws IOException
	{
		IDataXMLCoder coder = new IDataXMLCoder();
		return coder.encodeToBytes(doc);
	}
	
	/**
	 * Convenience method to convert a byte array produced by a IDataXMLCoder instance to an IData doc
	 * 
	 * @param src byte array formatted by a IDataXMLCoder or the method {@link #convertToBytes(IData)}
	 * @return converted webMethods document
	 * @throws IOException 
	 */
	public static IData convertBytesToIData(byte[] src) throws IOException
	{
		IDataXMLCoder coder = new IDataXMLCoder();
		return coder.decodeFromBytes(src);
	}
	
	 /**
     * Extracts all strings found in the IData record and returns them in a map. Sub records are also scanned.
     * Repeating key/value pairs overwrite previous pairs.
     *
     * @param record The IData record to convert to a map
     * @return Map A map of all key/value pairs in the IData structure without those pairs indicated by excludeList
     */
	public static Map<String, String> convertIDataStringsToMap(IData record)
	{
		return convertIDataStringsToMap(record, null);
	}
	
    /**
     * Extracts all strings found in the IData record and returns them in a map. Sub records are also scanned.
     * Repeating key/value pairs overwrite previous pairs.
     *
     * @param record The IData record to convert to a map
     * @param excludeList The names of keys to ignore, ie don't put in map
     * @return Map A map of all key/value pairs in the IData structure without those pairs indicated by excludeList
     */
    public static Map<String, String> convertIDataStringsToMap(IData record, String[] excludeList)
    {
        if (record == null)
            return null;
        
        IDataCursor cursor = record.getCursor();
        Map<String, String> stringMap = new HashMap<String, String>();
        
        while (cursor.next())
        {
            String key = cursor.getKey();
            Object value = cursor.getValue();
            
            if (value instanceof String && (excludeList == null || !isStringInArray(excludeList, key)))
                stringMap.put(key, (String) value);
            else if (value instanceof IData)
                stringMap.putAll(convertIDataStringsToMap((IData) value, excludeList));
        }
        
        cursor.destroy();
        
        return stringMap;
    }
    
    /**
     * Converts the given map into an IData record structure. This method is useful for passing parameters from Java to
     * webMethods services.
     *
     * @param map The Map to convert to a webMethods IData record
     * @return IData A webMethods IData record of all key/value pairs in the map without those pairs indicated by excludeList
     */
    public static IData[] convertMapToIDataListWithKeyValuePair(Map<String, Object> map)
    {
        int i = 0;
     
        if (map == null)
            return null;
        
        IData[] outIDataArray = new IData[map.size()];
        
        for (Iterator<String> it=map.keySet().iterator(); it.hasNext();)
        {
            String key = it.next();
            Object value = map.get(key);
            
            IData keyValPair = IDataFactory.create();
            IDataCursor c = keyValPair.getCursor();
            IDataUtil.put(c, "key", key);
            IDataUtil.put(c, "value", value);
            c.destroy();
            
            outIDataArray[i++] = keyValPair;
        }
        
        return outIDataArray;
    }
    
    /**
     * Converts the given map into an IData record structure. This method is useful for passing parameters from Java to
     * webMethods services.
     *
     * @param map The Map to convert to a webMethods IData record
     * @param excludeList The names of keys to ignore, ie don't put in map
     * @return IData A webMethods IData record of all key/value pairs in the map without those pairs indicated by excludeList
     */
    public static IData convertMapToIData(Map<String, Object> map, String[] excludeList)
    {
        if (map == null)
            return null;
        
        IData newRecord = IDataFactory.create();
        IDataCursor cursor = newRecord.getCursor();
        
        for (Iterator<String> it=map.keySet().iterator(); it.hasNext();)
        {
            String key = it.next();
            
            if (!isStringInArray(excludeList, key))
                cursor.insertAfter(key, map.get(key));
        }
        
        cursor.destroy();
        
        return newRecord;
    }
    
    /**
     * Extracts all strings found in the IData record and returns them in an array. Sub records are also scanned.
     * Each key/value pair is separated using '=', ie 'key=value'.
     *
     * @param record The IData record to convert to a String array
     * @param excludeList The names of keys to ignore, ie don't put in map
     * @return String[]	An array of all key/value pairs in the IData structure without those pairs indicated by excludeList
     */
    public static String[] convertIDataStringsToListArray(IData record, String[] excludeList)
    {
        if (record != null)
            return (String[]) convertIDataStringsToList(record, excludeList, false).toArray(new String[0]);
        else
            return null;
    }
    
    /**
     * Extracts all string value found in the IData record and returns them in an array. Sub records are also scanned.
     * Only the values are returned, the key names are lost
     *
     * @param record The IData record to convert to a String array
     * @param excludeList The names of keys to ignore, ie don't put in map
     * @return String[]	An array of all values in the IData structure not including those indicated by excludeList
     */
    public static String[] convertIDataValuesToListArray(IData record, String[] excludeList)
    {
        if (record != null)
            return (String[]) convertIDataStringsToList(record, excludeList, true).toArray(new String[1]);
        else
            return null;
    }
    
    /**
     * Extracts all strings found in the String array  and returns them in an IData record.
     * The keys are determined either by a key in indicated in the String or created dynamically.
     * The former for key/value pair is separated using '=', ie 'key=value'.
     * The latter will simply assume the String is only the value and the key will take the form 'parameterN'
     * where n is the index into the array.
     *
     * @param list The String array to convert to an IData record
     * @param excludeList The names of keys to ignore, ie don't put in map
     * @return IData The IData record of all key/value pairs in the String array without those pairs indicated by excludeList
     */
    public static IData convertStringArrayToIData(String[] list, String[] excludeList)
    {
        if (list == null)
            return null;
        
        IData newRecord = IDataFactory.create();
        IDataCursor cursor = newRecord.getCursor();
        
        for (int i=0;i<list.length; i++)
        {
            if (list[i] != null)
            {
                int separator = -1;
                String key = null;
                String value = null;
                
                if ((separator=list[i].indexOf("=")) != -1)
                {
                    key = list[i].substring(0, separator);
                    value = list[i].substring(separator+1);
                }
                else
                {
                    key = "parameter" + i;
                    value = list[i];
                }
                
                if (!isStringInArray(excludeList, key))
                    cursor.insertAfter(key, value);
            }
        } // end FOR
        
        cursor.destroy();
        
        return newRecord;
    }

    private static List<String> convertIDataStringsToList(IData record, String[] excludeList, boolean valuesOnly)
    {
        if (record == null)
            return null;

        IDataCursor cursor = record.getCursor();
        List<String> stringList = new ArrayList<String>();

        while (cursor.next())
        {
            String key = cursor.getKey();
            Object value = cursor.getValue();

            if (value instanceof String && !isStringInArray(excludeList, key))
            {
                if (valuesOnly)
                    stringList.add((String) value);
                else
                    stringList.add(key + "=" + value);
            }
            else if (value instanceof IData)
            {
                stringList.addAll(convertIDataStringsToList((IData) value, excludeList, valuesOnly));
            }
        }

        cursor.destroy();

        return stringList;
    }

    private static boolean isStringInArray(String[] list, String key)
    {
        if (list == null || key == null)
            return false;
        
        boolean found = false;
        int i = 0;
        
        while (!found && i < list.length)
        {
            if (list[i] != null & list[i].equals(key))
                found = true;
            
            i += 1;
        }
        
        return found;
    }

    /**
     * Check if value is not provided
     *
     * @param value Value to check
     * @return  true if value is not provided
     */
    public static final boolean isNotProvided(String value) 
    {
        return value == null || value.trim().length() == 0;
    }
    
    /**
     * Check if value is provided
     *
     * @param value Value to check
     * @return  true if value is provided
     */
    public static final boolean isProvided(String value) 
    {
        return value != null && value.trim().length() > 0;
    }
    /**
     * Check if IData is provided
     *
     * @param value IData to check
     * @return  true if IData is provided
     */
    public static final boolean isProvided(IData value) 
    {
        return value != null;
    }
    
    public static Object getObjectUsingNameSpace(String xpath, IData doc)
    {
        XPathExpression xpe = new XPathExpression(xpath);
        
        try
        {
            return xpe.getObject(doc);
        }
        catch (Exception e)
        {
            return null;
        }
    }
    
    /**
     * return the specified attribute in the given IData object
     * @param key The id of the attribute to retrieved (the first instance found will be returned).
     * @param doc The document to be searched.
     * @param recursive if true then any sub-levels will be searched also
     * @return the object found or null if none found
     */
    public static Object findObjectInIData(String key, IData doc, boolean recursive)
    {
    	Object v = null;

    	if (doc != null)
    	{
    		IDataCursor c = doc.getCursor();
    		v = IDataUtil.get(c, key);

    // check sub-layer

    		if (v == null && recursive)
    		{
    			c.first();

    			while (v == null && c.hasMoreData())
    			{
    				Object a = c.getValue();

    				if (a instanceof IData) // scan sub-element
    				{
    					v = findObjectInIData(key, (IData) a, true);
    				}
    				else if (a instanceof IData[]) // scan sub-element list
    				{
    					int i = 0;
    					while (i<((IData[]) a).length && v == null)
    						v = findObjectInIData(key, ((IData[]) a)[i++], true);
    				}
    				
    				c.next();
    			}
    		}
    	}

    	return v;
    }

    public static IData restoreDocFile(String directory, String fileName) throws InvalidDatatypeException, IOException
    {
    	InputStream in = new FileInputStream(new File(directory, fileName));
    	
    	try
    	{
    		return new IDataXMLCoder().decode(in);
    	}
    	finally
    	{
    		in.close();
    	}
    }
    
    public static void writeDocToFile(String directory, String fileName, IData doc) throws InvalidDatatypeException, IOException
    {
    	OutputStream out = new FileOutputStream(new File(directory, fileName));
    	
    	try
    	{
    		new IDataXMLCoder().encode(out, doc);
    	}
    	finally
    	{
    		out.close();
    	}
    }
    
    public static void merge(IData src, IData tgt)
    {
        if (src == null || tgt == null)
            return;
        
        IDataCursor srcCursor = src.getCursor();
        IDataCursor tgtCursor = tgt.getCursor();
        
        while (tgtCursor.next())
        {
            String key = tgtCursor.getKey();
            Object val = tgtCursor.getValue();
            
            if (!(val instanceof IData))
            {
                if (!(val instanceof IData[]))  // simple object are overwritten from right to left
                {
                    IDataUtil.put(srcCursor, key, val);
                }
                else    // arrays of IData are merged (equal size) or left is added to right (right is bigger or smaller)
                {
                    IData[] srcList = IDataUtil.getIDataArray(srcCursor, key);
                    
                    if (srcList == null)    
                    {
                        IDataUtil.put(srcCursor, key, val); // No source, so simple insert right hand side
                    }
                    else
                    {
                        IData[] newList = mergeIDataArrays(srcList, (IData[]) val);
                        
                        if (newList != null)
                            IDataUtil.put(srcCursor, key, newList);
                    }
                }
            }
            else    // IData merge
            {
                IData srcDocToUpdate = IDataUtil.getIData(srcCursor, key);
                
                if (srcDocToUpdate == null)
                    IDataUtil.put(srcCursor, key, tgt);     // No src, so simply insert right hand-size
                else
                    merge(srcDocToUpdate, (IData) val); // recursive merge
            }
        }
        
        srcCursor.destroy();
        tgtCursor.destroy();
    }
    
    private static IData[] mergeIDataArrays(IData[] srcList, IData[] tgtList)
    {
        boolean resized = false;
        int srcArrayOffset = 0;
                   
        if (srcList.length != tgtList.length) // src is not the right so resize it
        {
            IData[] newSrcList = null;
                       
            if (srcList.length <= tgtList.length)       // tgtlist is bigger, so assume  merge + add
            {
                newSrcList = new IData[tgtList.length];
                srcArrayOffset = 0;
            }
            else    // tgt has smaller list, so assume it wants to add elements to existing list
            {
                newSrcList = new IData[srcList.length + tgtList.length];
                srcArrayOffset = srcList.length;
            }
                  
            for (int i=0;i<newSrcList.length;i++)
                newSrcList[i] = srcList[i];
                      
            srcList = newSrcList;
            resized = true;
        }

// Update list
        
        for (int i = 0; i<tgtList.length; i++)
        {
            IData srcListElement = srcList[srcArrayOffset+i];
                       
            if (srcListElement == null)
                srcList[srcArrayOffset+i] = tgtList[i];
            else
                merge(srcListElement, tgtList[i]);
        }   
       
// Had to replace original list, so return it so that can be used to replace original
        
        if (resized)
            return srcList;
        else
            return null;
    }
    
    public static void dumpIDataToOutput(IData doc, OutputStream out, String prefix)
    {
        IDataCursor c = doc.getCursor();
        
        while (c.next())
        {
            String key = c.getKey();
            Object value = c.getValue();
            
            if (value instanceof IData)
            {
                System.out.println(prefix + "   >" + key);
                System.out.println(prefix + "   >============");
                dumpIDataToOutput((IData) value, out, prefix + "    >");
                System.out.println(prefix + "   <============");
            }
            else if (value instanceof IData[])
            {
                System.out.println(prefix + "   >" + key);
                System.out.println(prefix + "   >============");
                for (IData line : (IData[]) value)
                    dumpIDataToOutput(line, out, prefix + " >");
                System.out.println(prefix + "   <");
            }
            else
            {
                System.out.println(prefix + " " + key + "=" + value);
            }
        }
        
        c.destroy();
    }
}
