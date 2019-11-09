package com.jc.ehcache.wm;
import java.util.StringTokenizer;

import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataUtil;

import net.sf.ehcache.Element;
import net.sf.ehcache.search.attribute.AttributeExtractorException;

public class IDataAttributeExtractor implements net.sf.ehcache.search.attribute.AttributeExtractor
{
	private static final long serialVersionUID = -2882184802835308048L;
	private java.util.Properties _props;
	
	public IDataAttributeExtractor(java.util.Properties props)
	{
		_props = props;
	}
	
	@Override
	public Object attributeFor(Element element, String attributeName) throws AttributeExtractorException {
						
		return getValueForArg((IData) element.getObjectValue(), new StringTokenizer(attributeName, "."));			
	}
	
	public Object getValueForArg(IData doc, StringTokenizer tokenizer)
	{
		IDataCursor c = doc.getCursor();
		Object value = IDataUtil.get(c, tokenizer.nextToken());
		c.destroy();
		
		if (tokenizer.hasMoreTokens() && value instanceof IData)
			return getValueForArg((IData) value, tokenizer);
		else
			return value;
	}
}
