package com.jc.devops.docker;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.spotify.docker.client.messages.Container;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class ContainerWrapper {

	private Container _container;
	
	public ContainerWrapper(Container container) {
		
		_container = container;
	}
	
	public IData toIData() {
		
		IData doc = IDataFactory.create();
		IDataCursor c = doc.getCursor();
		
		IDataUtil.put(c, "id", _container.id());
		IDataUtil.put(c, "image", _container.image());
		IDataUtil.put(c, "createdDate", formatDate(new Date(_container.created()*1000)));
		IDataUtil.put(c, "Created", "" + _container.created()*1000);
		IDataUtil.put(c, "status",_container.status());
		IDataUtil.put(c, "state", _container.state());
		
		List<IData> ports = new ArrayList<IData>();
		
		_container.ports().forEach((p) -> {
			if (p.publicPort() !=  null)
                ports.add(makePortDoc(p.privatePort(), p.publicPort(), p.type()));
		});
					
		IDataUtil.put(c, "ports", ports.toArray(new IData[ports.size()]));
		
		c.destroy();
		
		return doc;
	}
	
	private IData makePortDoc(Integer privatePort, Integer publicPort, String type) {
		
		IData doc = IDataFactory.create();
		IDataCursor c = doc.getCursor();
		IDataUtil.put(c, "internal", "" + privatePort);
		IDataUtil.put(c, "external", "" + publicPort);
		IDataUtil.put(c, "type", "" + type);

		c.destroy();
		
		return doc;
	}

	private static String formatDate(Date date) {
		
		return new SimpleDateFormat("dd MMM yy - HH:mm").format(date);
	}
}
