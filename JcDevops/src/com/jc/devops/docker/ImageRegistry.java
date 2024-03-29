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
	
	public static String WM_CURRENT_VERSION = "10.11";
	
	private DockerClient _client;
	
	private RegistryAuth _registry;
	
	private static ImageRegistry _default;
	
	public static void setDefaultRegistry(DockerClient client, String registryEmailAddress, String registryUser, String registryPassword) {
		
		_default = new ImageRegistry(client, registryEmailAddress, registryUser, registryPassword);
	}
	
	public static ImageRegistry defaultRemoteRegistry() {
		return _default;
	}
	
	public ImageRegistry(DockerClient client, String registryUser, String registryPassword) {
		
		this._client = client;
		
		this._registry = RegistryAuth.builder()
				  .username(registryUser)
				  .password(registryPassword)
				  .build();
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
			
			Image img = images.next();
					
			if (img.repoTags() != null) {
				for (String foundTag : img.repoTags()) {
					if (foundTag.equals(tag)) {
						found = true;
						break;
					}
				}
			}
		}
		
		return found;
	}
	
	public List<IData> images(String filter, boolean includeSagDefaultImages) throws ServiceException {
		
		List<IData> images = new ArrayList<IData>();
		
		try {
			
			List<Image> imgs = _client.listImages();
			
			DefaultImageChecker standard = new DefaultImageChecker(WM_CURRENT_VERSION);
			final List<IData> rimages = new ArrayList<IData>();
		
			imgs.forEach((i) -> {
						
				if (i.repoTags() != null) {
					
					if (filter == null || (i.repoTags() != null && matches(i.repoTags().get(0), filter))) {
						
						for (String t : i.repoTags()) {
							
							if (!t.endsWith(".d") && !t.contains("<none>"))
								rimages.add(makeImageDoc(i, t, standard.check(t)));
						}					
					}
				}
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
			return makeImageDoc(image, tag, new DefaultImageChecker(WM_CURRENT_VERSION).check(tag));
		else
			return null;
	}

	@Override
	public synchronized void progress(ProgressMessage message) throws DockerException {
								
		this.notify();
	}
	
	public static boolean isVersion(String v) {
		
		return v.equals("latest") || v.equals("lts") || v.matches("[0-9]")
				|| v.matches("^(v|V|)([0-9]{1,4}(\\.[0-9a-z]{1,6}){1,5})");
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
	
	private static IData makeImageDoc(Image image, String tag, String type) {
		
		IData doc = IDataFactory.create();
		IDataCursor c = doc.getCursor();
		
		IDataUtil.put(c, "id", image.id());
		
		IDataUtil.put(c, "_repository", tag);
		IDataUtil.put(c, "_tag", tag);
		
		if (image.labels() != null && image.labels().size() >  0) {
			
			if (image.labels().get("DESCRIPTION") != null)
				IDataUtil.put(c, "description", image.labels().get("DESCRIPTION"));
			
			if (image.labels().get("MAINTAINER") != null)
				IDataUtil.put(c, "author", image.labels().get("MAINTAINER"));
			
			if (image.labels().get("COMMENT") != null)
				IDataUtil.put(c, "comments",  image.labels().get("COMMENT"));
			
			if (image.labels().get("TYPE") != null)
				IDataUtil.put(c, "type", image.labels().get("TYPE"));
			else
				IDataUtil.put(c, "type", type);
			
			if (image.labels().get("CUSTOM") != null)
				IDataUtil.put(c, "isCustom", image.labels().get("CUSTOM"));
			
			if (image.labels().get("BUILD-TEMPLATE") != null)
				IDataUtil.put(c, "buildTemplate", image.labels().get("BUILD-TEMPLATE"));
			
			if (image.labels().get("SAG") != null)
				IDataUtil.put(c, "isSagImage", image.labels().get("SAG"));
			
			if (image.labels().get("PRIMARY_PORT") != null)
				IDataUtil.put(c, "primaryPort", image.labels().get("PRIMARY_PORT"));
			
			if (image.labels().get("STATUS") != null)
				IDataUtil.put(c, "status", image.labels().get("STATUS"));
			
			if (image.labels().get("TEST-STATUS") != null)
				IDataUtil.put(c, "testStatus", image.labels().get("TEST-STATUS"));
		} else {
			IDataUtil.put(c, "type", type);
		}
		
		//System.out.println("** TAG ** is " + tag);
		
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
					int s = before.lastIndexOf("/");
					IDataUtil.put(c, "_repository", before.substring(0, s));
					IDataUtil.put(c, "_name", before.substring(s+1));
				} else {
					// there is no repo name
					int s = tag.lastIndexOf(":");
					IDataUtil.put(c, "_repository", tag.substring(0, s));
				}
			} else {
				// name is in tag part!!

				IDataUtil.put(c, "_repository", before);

				int s = after.lastIndexOf("-");

				if (s != -1) {
										
					if (isVersion(after.substring(s+1))) {
						IDataUtil.put(c, "_name", after.substring(0, s));
						IDataUtil.put(c, "_version", after.substring(s+1));
					} else if (isVersion(after.substring(0, s))) {
						
						// this probably implies that the name is in the repo
						
						IDataUtil.put(c,  "_version", after.substring(0, s));
						
						if (before.indexOf("/") != -1) {
							IDataUtil.put(c, "_name", before.substring(before.lastIndexOf("/")+1) + after.substring(s));
						} else {
							IDataUtil.put(c, "_name", before + after.substring(s));
						}
					} else {
						IDataUtil.put(c, "_name", after);
					}
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
	
	private boolean matches(String value, String filter) {
		
		if (filter.endsWith("*")) {
			if (filter.length() == 1) {
				return true; // only have wildcard, so assume wants all
			} else {
				return value.startsWith(filter.substring(0, filter.length()-1));
			}
		} else {
			
			// absolute match, but remove versioning from tag 
			String rest = value.length() > filter.length() ? value.substring(value.indexOf(filter)+filter.length()+1) : null;
			
			if (rest != null && isVersion(rest)) {
				return value.startsWith(filter);
			} else {
				return value.equals(filter);
			}
		}
	}
}