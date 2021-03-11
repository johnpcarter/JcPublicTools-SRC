package com.jc.devops.docker.type;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.jc.devops.docker.type.BuildCommand.CommandType;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.DockerClient.BuildParam;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ProgressMessage;
import com.wm.app.b2b.server.ServiceException;

import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class Build {
	
	public String name;
	public String context;
	public String dockerFile;
	public Map<String, BuildCommand> buildCommands;
	public Image sourceImage;
	public Image targetImage;
		
	public String imageId;
	
	public Build(Image sourceImage, Image targetImage) {
	
		this.buildCommands = new HashMap<String, BuildCommand>();
		this.sourceImage = sourceImage;
		this.targetImage = targetImage;
	}
	
	public Build(IData doc) {
		
		IDataCursor c = doc.getCursor();
		this.name = IDataUtil.getString(c, "name");
		this.context = IDataUtil.getString(c, "context");
		this.dockerFile = IDataUtil.getString(c, "dockerFile");
		IData[] commands = IDataUtil.getIDataArray(c, "buildCommands");
		
		if (IDataUtil.getIData(c, "sourceImage") != null) {
			this.sourceImage = new Image(IDataUtil.getIData(c, "sourceImage"));
		}
		
		if (IDataUtil.getIData(c, "targetImage") != null) {
			this.targetImage = new Image(IDataUtil.getIData(c, "targetImage"));
		}

		c.destroy();
		
		buildCommands = new HashMap<String, BuildCommand>();
		
		if (commands != null) {
			for (int i = 0; i < commands.length; i++) {
				BuildCommand cmd = new BuildCommand(commands[i]);
				this.buildCommands.put(cmd.source, cmd);
			}
		}
	}
	
	public IData toIData() {
		
		IData d = IDataFactory.create();
		IDataCursor c = d.getCursor();
		
		IDataUtil.put(c, "name", name);
		IDataUtil.put(c, "context", context);
		
		if (this.sourceImage != null)
			IDataUtil.put(c, "fromImage", this.sourceImage.tag);
		
		IDataUtil.put(c, "dockerFile", dockerFile);

		List<IData> out = new ArrayList<IData>();
		
		for (BuildCommand b : this.buildCommands.values()) {
			out.add(b.toIData());
		}
		
		IDataUtil.put(c, "buildCommands", out.toArray(new IData[out.size()]));
		
		c.destroy();
		
		return d;
	}

	protected String run(DockerClient dockerClient, String image, String buildno, String tempName) throws ServiceException  {

		File buildDir = new File("./packages/JcDevops/builds/" +  buildno);
		
		if (this.context != null)
			buildDir =  new File(this.context);
		else if (buildCommands.get("context") != null)
			buildDir = new File(this.buildCommands.get("context").source);
		
		if (!buildDir.isDirectory())
			throw new ServiceException("BuildDir must be a directory: " + buildDir.getAbsoluteFile());
		
		if (!new File(buildDir, "Dockerfile").exists())
			throw new ServiceException("No Dockerfile in build directory: " + buildDir.getAbsoluteFile());
						
		final AtomicReference<String> imageIdFromMessage = new AtomicReference<>();
		
		String returnedImageId = null;
		
		// setup args
		
		String buildargs = "{";
		Iterator<String> it = this.buildCommands.keySet().iterator();
		
		if (image != null)
			buildargs += "\"SRC\":\"" + image + "\"";

		while (it.hasNext()) {
			 
			String k = it.next();
			BuildCommand cmd = buildCommands.get(k);
			
			if (cmd.commandType != null && cmd.commandType == CommandType.env) 
				buildargs += "\"" + k + "\":\"" + cmd.target + "\"";
			 
			if (it.hasNext())
				buildargs += ",";
		 };
		 
		 if (buildargs.endsWith(","))
				 buildargs = buildargs.substring(0, buildargs.length()-2);
		 
		 buildargs += "}";
		 			    
		try {
			
			System.out.println("build args are: " + buildargs);
				
			BuildParam buildParam = BuildParam.create("buildargs", URLEncoder.encode(buildargs, "UTF-8"));

			System.out.println("building:" + tempName + ":latest at " + FileSystems.getDefault().getPath(buildDir.getAbsolutePath()));
			
			returnedImageId = dockerClient.build(FileSystems.getDefault().getPath(buildDir.getAbsolutePath()), tempName, new ProgressHandler() {
			      @Override
			      public void progress(ProgressMessage message) throws DockerException {
			    	  
			    	  System.out.println("************* here: " + message.error());
			    	  
			        Build.this.imageId = message.buildImageId();
			        if (imageId != null) {
			          imageIdFromMessage.set(imageId);
			        }
			      }
			    }, buildParam);
					
		} catch (IOException | DockerException | InterruptedException e) {
			//e.printStackTrace();
			
			throw new ServiceException("Docker build failed due to " + e);
		}
		
		return returnedImageId;
	}
	
	public BuildCommand fileForType(CommandType type, String fileType) {
		
		BuildCommand c = null;
		for (BuildCommand b : this.buildCommands.values()) {
			if (b.commandType == type && b.fileType.equals(fileType)) {
				c = b;
				break;
			}
		}
		
		return c;
	}
	
	static public class Image {
		
		public String repository;
		public String name;
		public String version;
		public String tag;
		
		Image(String tag) {
			this.tag = tag;
		}
		
		Image(IData doc) {
			
			IDataCursor c = doc.getCursor();
			this.version = IDataUtil.getString(c, "_version");
			this.name = IDataUtil.getString(c, "_name");
			this.repository = IDataUtil.getString(c, "_repository");
			this.tag = IDataUtil.getString(c, "_tag");
			c.destroy();
		}
		
		public boolean matches(String tag, boolean anyVersion) {
			
			if (anyVersion) {
				String compare = "";
				
				if (this.repository != null)
					compare = this.repository + ":";
				
				compare += name;
				
				return tag.startsWith(compare);
			} else {
				return this.tag.equals(tag);
			}
		}
	}
}