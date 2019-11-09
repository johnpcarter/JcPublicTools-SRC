
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
						
		IDataCursor c = ((IData) element.getObjectValue()).getCursor();
		Object value = IDataUtil.get(c, attributeName);
		c.destroy();
				
		System.out.println("===== prop '" + attributeName + "' == '" + value + "'");

		return value;
	}
}
