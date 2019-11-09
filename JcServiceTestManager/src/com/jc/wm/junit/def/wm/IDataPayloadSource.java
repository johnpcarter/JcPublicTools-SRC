package com.jc.wm.junit.def.wm;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;
import com.wm.util.coder.IDataJSONCoder;
import com.wm.util.coder.IDataXMLCoder;

import com.jc.wm.junit.def.Payload;

public abstract class IDataPayloadSource implements Payload<IData> {

	public static final String		TEST_RQRP_TYPE = "origin";
	public static final String		TEST_RQRP_CONTENT = "contentType";
	public static final String		TEST_RQRP_LOC = "location";
	public static final String		TEST_RQRP_SVC = "service";
	public static final String 		TEST_RQRP_ERR = "error";

	protected PayloadOrigin _origin;
	protected PayloadContentType _contentType;

	protected byte[] _bytes;
	protected Exception _error;
	
	protected List<ComparisonError> _comparisonErrors;
	
	public IDataPayloadSource() {
		
	}
	
	public IDataPayloadSource(PayloadOrigin origin) {
		
		_origin = origin;
	}
	
	@Override
	public PayloadOrigin getSrcOrigin() {
		
		return _origin;
	}

	@Override
	public PayloadContentType getContentType() {
		
		return _contentType;
	}

	@Override
	public boolean equals(Object obj) {
		
		if (obj instanceof Payload<?>) {
			
			try {
				IData response = this.getData();
				IData match = (IData) ((Payload<?>) obj).getData();
				
				return _comparePipelines("", match, response, false, null);// response.equals(match);
				
			} catch (InvalidPayloadException e) {
				
				return false;
			}
			
		} else {
			return false;
		}
	}
	
	public  boolean comparePipelines(IData left, IData right, boolean exactMatch) {
		
		_comparisonErrors = new ArrayList<ComparisonError>();
		
		return _comparePipelines(null, left, right, exactMatch, _comparisonErrors);
	}
	
	public List<ComparisonError> getComparisonErrors() {
		
		return _comparisonErrors;
	}

	private static boolean _comparePipelines(String namespace, IData left, IData right, boolean exactMatch, List<ComparisonError> collectErrors) {
		
		boolean match = true;
		
		Map<String, Object> l = convertToMap(left);
		Map<String, Object> r = convertToMap(right);
		
		if (exactMatch) {
			if (collectErrors == null && l.size() != r.size()) {
				
				return false;
				
			} else if (collectErrors != null && r.size() > l.size()) {
				
				// record right side fields that shouldn't be there
				
				collectExtraRightSideValues(l.keySet(), r.keySet(), r, collectErrors);
				
				match = false;
			}
		}
		
		if (l.size() == r.size()) {
		
			Iterator<String> keys = l.keySet().iterator();
			
			while(keys.hasNext()) {
				
				String k = keys.next();
				
				Object lobject = l.get(k);
				Object robject = r.get(k);
				
				String childNameSpace = null;
				
				if (namespace != null)
					childNameSpace = namespace + "." + k;
				else
					childNameSpace = k;
				
				if (!_match(childNameSpace, lobject, robject, exactMatch, collectErrors))
				{
					match = false;
					break;
				}
			};
		} else {
			match = false;
		}
		
		return match;
	}
	
	private static boolean _match(String namespace, Object left, Object right, boolean exactMatch, List<ComparisonError> collectErrors) {
						
		if (right == null) {
			
			if (collectErrors != null)
				collectErrors.add(new ComparisonErrorImpl(namespace, left, right, ErrorType.valueMissing));
			
			return false;
			
		} else if (left instanceof Object[]) {
			
			// got an array
			
			if (right instanceof Object[]) {
				
				Object[] leftArray = (Object[]) left;
				Object[] rightArray = (Object[]) right;

				boolean match = true;
				
				for (int l = 0; l < leftArray.length; l++) {
					
					for (int r = 0; r < rightArray.length; r++) {
						
						if (!_match(namespace + "[" + r + "]", leftArray[l], rightArray[r], exactMatch, collectErrors))
						{
							if (collectErrors == null)
								return false;
							else
								match = false;
						}
					}
				}
				
				return match;
				
			} else {
				
				// mismatch
				
				if (collectErrors != null)
					collectErrors.add(new ComparisonErrorImpl(namespace, left.getClass().getSimpleName(), left.getClass().getSimpleName(), ErrorType.typeMismatch));
				
				return false;
			}
		} else {
			
			// got an instance
			
			if (left instanceof IData) {
				
				if (right instanceof IData) {
					
					// recursive
					
					return _comparePipelines(namespace, (IData) left, (IData) right, exactMatch, collectErrors);
				} else {
					
					// type mismatch
					
					if (collectErrors != null)
						collectErrors.add(new ComparisonErrorImpl(namespace, left.getClass().getSimpleName(), left.getClass().getSimpleName(), ErrorType.typeMismatch));
						
					return false;
				}
			} else {
				
				// simple type
				
				if (left.equals(right)) {
					
					// bingo bongo
					
					return true;
				} else {
					
					if (collectErrors != null)
						collectErrors.add(new ComparisonErrorImpl(namespace, left, right, ErrorType.notequal));
					
					return false;
				}
			}
		}				
	}
	
	private static void collectExtraRightSideValues(Collection<String> l, Collection<String> r, Map<String, Object> rv, List<ComparisonError> collectErrors) {
		
		findExclusive(r, l).forEach(n -> {
			
			collectErrors.add(new ComparisonErrorImpl(n, rv.get(n), ErrorType.unspecifiedValue));
		});
	}
	
	private static Set<String> findExclusive(Collection<String> l, Collection<String> r) {

	    Set<String> result = new HashSet<>();
	    
	    for (String el: l) {
	      if (!r.contains(el)) {
	        result.add(el);
	      }
	    }
	    
	    return result;
	}
	
	private static Map<String, Object> convertToMap(IData data) {
		
		Map<String, Object> map = new HashMap<String, Object>();
		
		IDataCursor c = data.getCursor();
		c.first();
		
		do
		{
			String key = c.getKey();
			Object object = c.getValue();
			
			if (key != null)
				map.put(key, object);
			
		} while (c.next());
		
		c.destroy();
		return map;
	}
	
	@Override
	public IData getData() throws InvalidPayloadException {
		
		IData obj = null;
		
		if (_contentType == PayloadContentType.json) {
	        
			IDataJSONCoder jsonCoder = new IDataJSONCoder();
			try {
				obj = jsonCoder.decode(new ByteArrayInputStream(_bytes));
			} catch (Exception e) {
				throw new InvalidPayloadException(e);
			}
			
		} else if (_contentType == PayloadContentType.idata) {
			
			IDataXMLCoder coder = new IDataXMLCoder();
			try {
				obj = coder.decodeFromBytes(_bytes);
			} catch (Exception e) {
				throw new InvalidPayloadException(e);
			}
			
		} else if (_contentType == PayloadContentType.string) {
			
			obj = IDataFactory.create();
			IDataCursor c = obj.getCursor();
			IDataUtil.put(c, "$data", new String(_bytes));
			c.destroy();
			
		} else if (_contentType == PayloadContentType.xml) {
			
			obj = IDataFactory.create();
			IDataCursor c = obj.getCursor();
			IDataUtil.put(c, "$node", obj);
			c.destroy();
			
		} else {
			
			throw new RuntimeException("Invalid conent, cannot '" + _contentType  + "' convert to pipeline");
		}
		
		return obj;
	}

	@Override
	public Exception getError() {
		
		return _error;
	}
	
	public static class ComparisonErrorImpl implements ComparisonError {
		
		public String _namespace;
		public Object _leftValue;
		public Object _rightValue;
		
		public ErrorType _type;
		
		private ComparisonErrorImpl(String namespace, Object leftValue, Object rightValue, ErrorType type) {
			
			this._type = type;
			this._namespace = namespace;
			this._leftValue = leftValue;
			this._rightValue = rightValue;
		}
		
		private ComparisonErrorImpl(String namespace, Object leftValue, ErrorType type) {
			
			this._type = type;
			this._namespace = namespace;
			this._leftValue = leftValue;
		}

		@Override
		public String getNamespace() {
			
			return _namespace;
		}

		@Override
		public Object getLeftValue() {
			
			return _leftValue;
		}

		@Override
		public Object getRightValue() {
			
			return _rightValue;
		}

		@Override
		public ErrorType getErrorType() {
			
			return _type;
		}
	}
}