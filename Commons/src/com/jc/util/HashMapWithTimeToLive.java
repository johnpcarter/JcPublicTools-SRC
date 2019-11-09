package com.jc.util;

import java.util.*;

/**
 * Subclass of the HashMap interface to limit the size of the map and implement 
 * a cache to determine how the size should be constrained. The clean up algorithm is
 * based on LAFO (Last Accessed, First Out).
 * 
 * Associated a time-stamp with each element that is then used by a cleanup
 * thread to determine what elements to remove. The cleanup thread is triggered
 * when the size of map reaches a certain threshold (LOAD_FACTOR).
 * 
 * The thread them removes those elements that have oldest time-stamp 
 * (i.e. those that have not been accessed for the longest time). The number of
 * elements to remove is determined by the clean thresh-hold.
 *
 * @author John Carter
 * @version 2.0
 */
public class HashMapWithTimeToLive<S, T> extends HashMap<S, T>
{
	private static final long serialVersionUID = -8089432729776223799L;

	private static final int DEFAULT_MAX_ENTRIES = 1000;
    
    private static final float CLEAN_THRESHHOLD = .8f;
    
    private static final float LOAD_FACTOR = .75f;
    
    private static int CLEAN_THREAD_INTERVAL = 1800000;
    
    private CleanupThread _cleanupThread = null;
    
    private static int _cleanupThreadInterval = 0;
    
    private int _maxEntries = -1;
    
    private Map<Object, Date> _touchDates = null;
    
    private ObjectRemovedListener<T>	_removeListener;
    
    public HashMapWithTimeToLive()
    {
        this(DEFAULT_MAX_ENTRIES, CLEAN_THRESHHOLD);
    }
    
    public HashMapWithTimeToLive(int maxEntries, float cleanFactorThreshHold)
    {
        super(maxEntries, LOAD_FACTOR);
        
        resize(maxEntries, cleanFactorThreshHold);
    }
    
    /**
     * Registers the given listener which is called when objects are flushed
     * from the cache.
     * 
     * @param removalListener listener to be called when objects are autumatically removed
     */
    public void setRemovalListener(ObjectRemovedListener<T>	removalListener)
    {
        _removeListener = removalListener;
    }
    
    /**
     * Resizes the cache to determine how many elements to hold in memory
     * 
     * @param maxEntries
     * @param cleanFactorThreshHold
     */
    protected void resize(int maxEntries, float cleanFactorThreshHold)
    {
        _maxEntries = maxEntries;
        _touchDates = new HashMap<Object, Date>(maxEntries, LOAD_FACTOR);
        
        if (_cleanupThread != null)
            _cleanupThread.requestStop();
        
        startCleanupThread();
        _cleanupThread.setCleanThreshold(cleanFactorThreshHold);
    }
    
    public int getMaxCacheSize()
    {
        return _maxEntries;
    }
    
    @Override
    public T put(S key, T object)
    {
        if (_maxEntries > 0 && _touchDates.size() >= _maxEntries)
            removeOldestRecords();
        
        touchTimerForKey(key);
        
        return super.put(key, object);
    }
    
    @Override
    public synchronized void putAll(Map<? extends S,? extends T> t)
    {
        for (Iterator<? extends S> it = t.keySet().iterator(); it.hasNext();)
            put(it.next(), t.get(it.next()));
    }
    
    @Override
    public T get(Object key)
    {
        touchTimerForKey(key);
        return super.get(key);
    }
    
    @Override
    public T remove(Object obj)
    {
        synchronized (_touchDates)
        {
            _touchDates.remove(obj);
            return super.remove(obj);
        }
    }
    
    @Override
    public void clear()
    {
        synchronized (_touchDates)
        {
            _touchDates.clear();
            super.clear();
        }
    }
    
    protected void finalize()
    {
        if (_cleanupThread != null)
            stopCleanupThread();
    }
    
    @Override
    public String toString()
    {
        String outString = null;
        
        synchronized (_touchDates)
        {
            for (Iterator<Object> it = _touchDates.keySet().iterator(); it.hasNext();)
            {
                String key = (String) it.next();
                if (outString != null)
                    outString = outString + ";" + key + "=" + _touchDates.get(key);
                else
                    outString = key + "=" + _touchDates.get(key);
            }
        }
        
        return outString;
    }
    
    @SuppressWarnings("unchecked")
	@Override
    public Collection<T> values()
    {
        if (_touchDates != null && _touchDates.size() > 0)
        {
            List<T> outArray = new ArrayList<T>(_touchDates.size());
            List sortedKeyList = getTouchDateKeysSortedByTouchDate();
            T obj = null;
            
            for (Iterator<String> it = sortedKeyList.iterator(); it.hasNext();)
            {
                if ((obj = get(it.next())) != null)
                    outArray.add(obj);
            }
            
            return outArray;
        }
        else
        {
            return new ArrayList<T>();
        }
    }
    
    /**
     * Returns the key of the last object added to the map
     * 
     * @return
     */
    public Object getLastInKey()
    {
        List<Object> sortedKeys = getTouchDateKeysSortedByTouchDate();
        
        if (sortedKeys != null && sortedKeys.size() > 0)
            return sortedKeys.get(0);
        else
            return null;
    }
    
    /**
     * Returns the key of the oldest object added to the map
     * and the first candidate to be removed in the case of cleanpu.
     * 
     * @return
     */
    public Object getFirstInKey()
    {
        List<Object> sortedKeys = getTouchDateKeysSortedByTouchDate();
        
        if (sortedKeys != null && sortedKeys.size() > 0)
            return sortedKeys.get(sortedKeys.size()-1);
        else
            return null;
    }
    
    private void touchTimerForKey(Object key)
    {
        synchronized (_touchDates)
        {
            _touchDates.put(key, new Date());
        }
    }
    
    private synchronized void removeOldestRecords()
    {
        _cleanupThread.cleanoutOldestRecords();
    }
    
    public static void setCleanupThreadInterval(int interval)
    {
        if (interval > 0)
            _cleanupThreadInterval = interval;
    }
    
    public static int getCleanupThreadInterval()
    {
        return _cleanupThreadInterval;
    }
    
    public void setCleanThreshold(float cleanThreshold)
    {
        _cleanupThread.setCleanThreshold(cleanThreshold);
    }
    
    protected void removeFromCacheOnly(Object obj)
    {
        T oldObj = super.remove(obj);
        _touchDates.remove(obj);
        
        if (_removeListener != null && oldObj != null)
        	_removeListener.onObjectRemoval(oldObj);
    }
    
    private void startCleanupThread()
    {
        _cleanupThread = new CleanupThread(CLEAN_THRESHHOLD);
        _cleanupThread.start();
    }
    
    private void stopCleanupThread()
    {
        _cleanupThread.requestStop();
    }
    
    private List<Object> getTouchDateKeysSortedByTouchDate()
    {
        List<Map.Entry<Object,Date>> sortedList = null;
        List<Object> sortedKeys = new ArrayList<Object>(_touchDates.size());
        
        synchronized (_touchDates)
        {
            sortedList = new ArrayList<Map.Entry<Object,Date>>(_touchDates.entrySet());
            Collections.sort(sortedList, new ComparatorBasedOnDateValue());
        }
        
        for (Map.Entry<Object, Date> e : sortedList)
            sortedKeys.add(e.getKey());
        
        return sortedKeys;
    }
    
    private class CleanupThread extends Thread
    {
        private float _cleanThreshhold = 0.8f;
        
        private boolean _requestStop = false;
        
        public CleanupThread(float cleanThreshold)
        {
            super("com.jc.recordpersistance.HashMapWithTimeToLive$CleanupThread");
            _cleanThreshhold = cleanThreshold;
        }
        
        public void setCleanThreshold(float cleanThreshhold)
        {
            _cleanThreshhold = cleanThreshhold;
        }
        
        @Override
        public void run()
        {
            if (_cleanupThreadInterval == 0)
                _cleanupThreadInterval = CLEAN_THREAD_INTERVAL;
            
            while (!_requestStop)
            {
                if (_touchDates != null && _touchDates.size() >= (_maxEntries * LOAD_FACTOR))
                {
                    cleanoutOldestRecords();
                }
                
                try
                {
                    sleep(_cleanupThreadInterval);
                }
                catch (InterruptedException e)
                {
                    _requestStop = true;
                }
            }
        }
        
        public void requestStop()
        {
            _requestStop = true;
        }
        
        private void cleanoutOldestRecords()
        {
            synchronized (_touchDates)
            {
                int size = _touchDates.size();
                List<?> sortedKeyList = getTouchDateKeysSortedByTouchDate();
                
                Iterator<?> it = sortedKeyList.iterator();
                int minimumToRemove = new Float(_maxEntries * (1.0 - _cleanThreshhold)).intValue();
                int numRemoved = 0;
                
                if (size > _maxEntries)
                    minimumToRemove += (_maxEntries - size);
                
                while (it.hasNext() && numRemoved <= minimumToRemove)
                {
                    String key = (String) it.next();
                    _touchDates.remove(key);
                    HashMapWithTimeToLive.this.removeFromCacheOnly(key);  // ensure we only remove cache item and not child stuff
                            
                    numRemoved += 1;
                } // End FOR
            }
        }
    } // End inner class : CleanupThread
    
    private class ComparatorBasedOnDateValue implements Comparator<Map.Entry<Object,Date>>
    {
        public int compare(Map.Entry<Object,Date> d1, Map.Entry<Object,Date> d2)
        {
            return (d1.getValue().compareTo(d2.getValue()));           // Ascending
        }
    }
    
    /**
     * listener is called when objects are removed from the cache.
     *
     * @param <T>
     */
    public interface ObjectRemovedListener<T>
    {
    	public void onObjectRemoval(T object);
    }
}

