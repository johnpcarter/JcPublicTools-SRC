package com.jc.devops.docker;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Image;
import com.spotify.docker.client.messages.ProgressMessage;
import com.spotify.docker.client.messages.RegistryAuth;
import com.wm.app.b2b.server.ServiceException;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class ImageRegistry implements ProgressHandler {
	
	private DockerClient _client;
	
	private RegistryAuth _registry;
	
	private static ImageRegistry _default;
	
	public static void setDefaultRegistry(DockerClient client, String registryEmailAddress, String registryUser, String registryPassword) {
		
		_default = new ImageRegistry(client, registryEmailAddress, registryUser, registryPassword);
	}
	
	public static ImageRegistry defaultRemoteRegistry() {
		return _default;
	}
	
	public ImageRegistry(DockerClient client, String registryEmailAddress, String registryUser, String registryPassword) {
		
		this._client = client;
		
		this._registry = RegistryAuth.builder()
				  .email(registryEmailAddress)
				  .username(registryUser)
				  .password(registryPassword)
				  .build();
	}
	
	public ImageRegistry(DockerClient client) {
		
		this._client = client;
	}
	
	public boolean haveImage(String tag) throws DockerException, InterruptedException {
		
		boolean found = false;

		Iterator<Image> images = _client.listImages().iterator();
			
		while (!found && images.hasNext()) {
				
				found = images.next().repoTags().get(0).equals(tag);
		}
		
		return found;
	}
	
	public List<IData> images(String filter, boolean includeSagDefaultImages) throws ServiceException {
		
		List<IData> images = new ArrayList<IData>();
		
		try {
			
			List<Image> imgs = _client.listImages();
			
			DefaultImageChecker standard = new DefaultImageChecker("10.5");
			final List<IData> rimages = new ArrayList<IData>();
		
			imgs.forEach((i) -> {
						
				standard.check(i.repoTags().get(0));
				
				if (filter == null || (i.repoTags() != null && i.repoTags().get(0).startsWith(filter)))
					rimages.add(makeImageDoc(i));
			});
			
			if (includeSagDefaultImages)
				images = standard.remainer(rimages);
			else 
				images = rimages;
			
		} catch (InterruptedException | DockerException e) {
			throw new ServiceException(e);
		}
		
		return images;
	}
	
	public void push(String tag) throws DockerException, InterruptedException {
		
		if (this._registry != null)
			_client.push(tag, this._registry);
		else
			_client.push(tag);
	}
	
	public synchronized IData pull(String tag) throws DockerException, InterruptedException {
					
		if (this._registry != null)
			_client.pull(tag, this._registry);//, this);
		else
			_client.pull(tag);//, this);
		
		Image image = getImageForTag(tag);
		
		if (image != null)
			return makeImageDoc(image);
		else
			return null;
	}

	@Override
	public synchronized void progress(ProgressMessage message) throws DockerException {
								
		this.notify();
	}
	
	private static boolean isVersion(String v) {
		
		return v.equals("latest") || v.matches("\\d{1,3}") || v.matches("^(v|V|)\\d{1,3}\\.\\d{1,3}(?:\\.\\d{1,6})?$") || v.matches("^(v|V|)\\d{1,3}\\_\\d{1,3}(?:\\_\\d{1,6})?$");
	}
	
	private Image getImageForTag(String tag) throws DockerException, InterruptedException {
		
		List<Image> imgs = this._client.listImages();
		Image image = null;
		
		for (int i = 0; i <  imgs.size(); i++) {
			
			if ((imgs.get(i).repoTags() != null && imgs.get(i).repoTags().size() > 0 && imgs.get(i).repoTags().get(0).equals(tag))) {
				
				image = imgs.get(i);
			}
		}
		
		return image;
	}
	
	private static IData makeImageDoc(Image image) {
		
		IData doc = IDataFactory.create();
		IDataCursor c = doc.getCursor();
		
		IDataUtil.put(c, "id", image.id());
		
		String tag = null;
		if (image.repoTags() != null && image.repoTags().size() > 0) {
			tag = image.repoTags().get(0);
			IDataUtil.put(c, "_repository", tag);
			IDataUtil.put(c, "_tag", tag);
		}
		
		if (image.labels() != null && image.labels().size() >  0) {
			
			if (image.labels().get("DESCRIPTION") != null)
				IDataUtil.put(c, "description", image.labels().get("DESCRIPTION"));
			
			if (image.labels().get("MAINTAINER") != null)
				IDataUtil.put(c, "author", image.labels().get("MAINTAINER"));
			
			if (image.labels().get("COMMENT") != null)
				IDataUtil.put(c, "comments",  image.labels().get("COMMENT"));
			
			if (image.labels().get("TYPE") != null)
				IDataUtil.put(c, "type", image.labels().get("TYPE"));
			
			if (image.labels().get("CUSTOM") != null)
				IDataUtil.put(c, "isCustom", image.labels().get("CUSTOM"));
			
			if (image.labels().get("BUILD-TEMPLATE") != null)
				IDataUtil.put(c, "buildTemplate", image.labels().get("BUILD-TEMPLATE"));
			
			if (image.labels().get("SAG") != null)
				IDataUtil.put(c, "isSagImage", image.labels().get("SAG"));
			
			if (image.labels().get("STATUS") != null)
				IDataUtil.put(c, "status", image.labels().get("STATUS"));
			
			if (image.labels().get("TEST-STATUS") != null)
				IDataUtil.put(c, "testStatus", image.labels().get("TEST-STATUS"));
		}
		
		if (tag != null && tag.indexOf(":") != -1) {

			int split = tag.indexOf(":");
			String before = tag.substring(0, split);
			String after = tag.substring(split+1);
								
			if (isVersion(after)) {
				// value version

				IDataUtil.put(c, "_name", before);
				IDataUtil.put(c, "_version", after);
				
				// perhaps the repo is in the name part (check if we have a slash)

				if (tag.indexOf("/") != -1) {
					int s = tag.lastIndexOf("/");
					IDataUtil.put(c, "_repository", tag.substring(0, s));
					IDataUtil.put(c, "_name", tag.substring(s+1));
				}
			} else {
				// name is in tag part!!

				IDataUtil.put(c, "_repository", before);

				int s = after.lastIndexOf("-");

				if (s != -1 && isVersion(after.substring(s+1))) {
					IDataUtil.put(c, "_name", after.substring(0, s));
					IDataUtil.put(c, "_version", after.substring(s+1));
				} else {
					IDataUtil.put(c, "_name", after);
				}
			}
		}
		
		IDataUtil.put(c, "sizeGb", "" + Math.round(image.size() / 1000000));
		IDataUtil.put(c, "Created", "" + image.created());
		IDataUtil.put(c, "createdDate", formatDate(new Date(Long.parseLong(image.created())*1000)));

		c.destroy();
		
		return doc;
	}
	
	private static String formatDate(Date date) {
		
		return new SimpleDateFormat("dd MMM yy - HH:mm").format(date);
	}
}