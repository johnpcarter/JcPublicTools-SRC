package com.jc.wm.terracotta.db.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.function.Function;

import com.terracottatech.store.Cell;
import com.terracottatech.store.Record;
import com.terracottatech.store.UpdateOperation.CellUpdateOperation;
import com.terracottatech.store.definition.CellDefinition;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class IDataRecord implements Record<String> {

	private String _key;
	private List<Cell<?>> _cells = new ArrayList<Cell<?>>();
	
	public IDataRecord(String key, IData doc) {
		
		_key = key;
		convertIDataToCells(null, doc);
	}
	
	public IData toIData(Record<String> record) {
		
		IData doc = IDataFactory.create();
		
		convertCellsToIData(this, doc);
		
		return doc;
	}
	
	public static IData recordToIData(Record<String> record) {
	
		IData doc = IDataFactory.create();
		
		convertCellsToIData(record, doc);
		
		return doc;
	}
	
	@Override
	public int size() {
		return _cells.size();
	}

	@Override
	public boolean isEmpty() {
		
		return _cells.size() == 0;
	}

	@Override
	public boolean contains(Object o) {
		
		return _cells.contains(o);
	}

	@Override
	public Iterator<Cell<?>> iterator() {
		
		return _cells.iterator();
	}

	@Override
	public Object[] toArray() {
		
		return _cells.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		
		return _cells.toArray(a);
	}

	@Override
	public boolean add(Cell<?> e) {
		
		return _cells.add(e);
	}

	@Override
	public boolean remove(Object o) {
		
		return _cells.remove(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		
		return _cells.containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends Cell<?>> c) {
		
		return _cells.addAll(c);
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		
		return _cells.removeAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {

		return _cells.retainAll(c);
	}

	@Override
	public void clear() {
		
		_cells.clear();
	}

	@Override
	public String getKey() {
		
		return _key;
	}

	public CellUpdateOperation<String, Object> toCellUpdateOperation() {
		
		return new CellUpdateOperation<String, Object>() {
			
			private int _seq = 0;
			
			@SuppressWarnings("unchecked")
			@Override
			public CellDefinition<Object> definition() {
				
				return (CellDefinition<Object>) _cells.get(_seq).definition();
			}
			
			@Override
			public Function<Record<?>, Optional<Cell<Object>>> cell() {
				
				return new Function<Record<?>,Optional<Cell<Object>>>() {

					@SuppressWarnings("unchecked")
					@Override
					public Optional<Cell<Object>> apply(Record<?> t) {
						
						return Optional.ofNullable((Cell<Object>) _cells.get(_seq++));
					}
				};
			}
		};
	}
	
	private void convertIDataToCells(String prefix, IData doc) {
			
		IDataCursor c = doc.getCursor();
		
		c.first();
		
		do
		{
			String key = c.getKey();
			Object obj = c.getValue();
			
			if (prefix != null)
				key = prefix + "." + key;
			
			if (obj instanceof IData)
			{
				convertIDataToCells(key, (IData) obj);
			}
			else if (obj instanceof IData[])
			{
				for (int i=0; i < ((IData[]) obj).length; i++) {
					convertIDataToCells(key + "[" + i + "]", ((IData[]) obj)[i]);
				}
			}
			else if (obj instanceof Object[])
			{
				for (int i=0; i < ((Object[]) obj).length; i++) {

					convertObjectToCell(key + "[" + i + "]", ((Object[]) obj)[i]);
				}
			}	
			else
			{
				// raw value
				
				convertObjectToCell(key, (IData) obj);
			}
			
		}
		while(c.next());
		
		c.destroy();
	}
	
	private void convertObjectToCell(String key, Object obj)
	{
		_cells.add(Cell.cell(key, obj));
	}
	
	@SuppressWarnings("unchecked")
	private static void convertCellsToIData(Record<String> record, IData root)
	{
		Iterator<Cell<?>> it = record.iterator();
		
		while (it.hasNext())
		{
			Cell<?> cell = it.next();
			
			String key = cell.definition().name();
			Object value = cell.value();
			
			Result result = _getDocForKeyInRoot(key, root);
			IDataCursor c = result.doc.getCursor();
			
			if (result.shortKey.contains("["))
			{
				int idx = result.shortKey.indexOf("[");
				//int index = Integer.parseInt(result.shortKey.substring(idx+1, result.shortKey.indexOf("]")));
				result.shortKey = result.shortKey.substring(idx);
				
				Object array = IDataUtil.get(c, result.shortKey);
				
				if (array == null)
				{
					array = new ArrayList<Object>();
					IDataUtil.put(c, result.shortKey , array);
				}
				
				((ArrayList<Object>) array).add(value);
			}
			else
			{
				IDataUtil.put(c, result.shortKey, value);
			}
			
			c.destroy();
		}
		
		convertAllArrayListsToStaticArrays(root);		
	}
	
	@SuppressWarnings("unchecked")
	private static Result _getDocForKeyInRoot(String key, IData rootDoc)
	{
		Result result = new Result();
		
		if (key.contains("."))
		{
			IData doc = rootDoc;
			StringTokenizer tk = new StringTokenizer(key, ".");
			String lastKey = null;
			
			while (tk.hasMoreTokens())
			{
				String shortKey = tk.nextToken();
				
				if (tk.hasMoreTokens())
				{
					lastKey = shortKey;
					IDataCursor c = doc.getCursor();

					if (shortKey.contains("["))
					{
						int idx = shortKey.indexOf("[");
						
						int index = Integer.parseInt(shortKey.substring(idx+1, shortKey.indexOf("]")));
						shortKey = shortKey.substring(idx);
						
						Object objArray = IDataUtil.get(c, shortKey);
						
						if (objArray == null)
						{
							objArray = new ArrayList<IData>();
							doc = IDataFactory.create();
							((ArrayList<IData>) objArray).add(doc);
							
							IDataUtil.put(c, shortKey, objArray);
						}
						else
						{
							List<IData> lst = (List<IData>) objArray;
							
							if (lst.size() > index)
								doc = lst.get(index);
							else
								doc = lst.get(lst.size()-1);
						}
					}
					else
					{
						IData obj = IDataUtil.getIData(c, shortKey);
						
						if (obj == null)
						{
							obj = IDataFactory.create();
							IDataUtil.put(c, shortKey, obj);
						}
							
						doc = obj;
					}
					
					c.destroy();
				}
			}
			
			result.doc = doc;
			result.shortKey = lastKey;
		}
		else
		{
			result.doc = rootDoc;
			result.shortKey = key;
		}
		
		
		return result;
	}
	
	private static void convertAllArrayListsToStaticArrays(IData doc)
	{
		IDataCursor c = doc.getCursor();
		c.first();
		
		do {
			String key = c.getKey();
			Object v = c.getValue();
			
			if (v instanceof List)
			{
				if (((List<Object>) v).get(0) instanceof IData)
				{
					for (int i = 0; i < ((List<Object>) v).size(); i++)
						convertAllArrayListsToStaticArrays(((List<IData>) v).get(i));
				}
				
				IDataUtil.put(c, key, ((List<Object>) v).toArray(_type((List<Object>) v)));
			}
			else if (v instanceof IData)
			{
				convertAllArrayListsToStaticArrays((IData) v);
			}
		}
		while (c.next());
		
		c.destroy();
	}
	
	private static Object[] _type(List<Object> lst)
	{
		if (lst.get(0) instanceof IData)
			return new IData[lst.size()];
		else if (lst.get(0) instanceof Long)
			return new Long[lst.size()];
		else if (lst.get(0) instanceof Integer)
			return new Integer[lst.size()];
		else if (lst.get(0) instanceof Character)
			return new Character[lst.size()];
		else if (lst.get(0) instanceof Byte)
			return new Byte[lst.size()];
		else if (lst.get(0) instanceof Boolean)
			return new Boolean[lst.size()];
		else
			return new String[lst.size()];
	}
	
	private static class Result {
		public String shortKey;
		public IData doc;
	}
}
