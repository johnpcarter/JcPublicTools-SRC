package com.jc.devops.docker.type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jc.devops.docker.ImageRegistry;
import com.jc.devops.docker.WebSocketContainerLogger;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.PortBinding;
import com.spotify.docker.client.messages.HostConfig.Builder;
import com.wm.app.b2b.server.ServiceException;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class DockerContainer {
	
	public String name;
	public String buildRef;
	public String image;
	public String hostname;
	public boolean active;
	public String type;
		
	public String id;
	public String imageId;
	
	public IData readinessProbe;
	public IData livenessProbe;

	public String[] depends;

	public Build build;

	public Environment[] environments;
	
	public DockerContainer(IData doc) {
		
		IDataCursor c = doc.getCursor();
		this.name = IDataUtil.getString(c, "name");
		this.image = IDataUtil.getString(c, "image");
		this.buildRef = IDataUtil.getString(c, "buildRef");
		this.hostname = IDataUtil.getString(c, "hostname");
		this.active = IDataUtil.getString(c, "active") != null && IDataUtil.getString(c, "active").equalsIgnoreCase("true");
		this.type = IDataUtil.getString(c, "type");

		this.depends = IDataUtil.getStringArray(c, "depends");

		readinessProbe = IDataUtil.getIData(c, "readinessProbe");
		livenessProbe = IDataUtil.getIData(c, "livenessProbe");

		if (IDataUtil.getIData(c, "build") != null)
			this.build = new Build(IDataUtil.getIData(c, "build"));
		
		if (IDataUtil.getIDataArray(c, "environments") != null) {
			IData[] envs = IDataUtil.getIDataArray(c, "environments");
			this.environments = new Environment[envs.length];
			
			int i = 0;
			for (IData e : envs) {
				this.environments[i++] = new Environment(e);
			}
		}
		c.destroy();
		
		if (hostname == null)
			hostname = this.name;
	}
	
	public IData toIData() {
		
		IData d = IDataFactory.create();
		IDataCursor c = d.getCursor();
		
		IDataUtil.put(c, "name", this.name);
		IDataUtil.put(c, "image", this.image);
		
		if (buildRef != null) 
			IDataUtil.put(c, buildRef, buildRef);
		
		if (build != null)
			IDataUtil.put(c, "build", build.toIData());

		IDataUtil.put(c, "hostname", this.hostname);
		IDataUtil.put(c, "active", "" + this.active);
		IDataUtil.put(c, "type", type);

		IDataUtil.put(c, "id",  this.id);
		IDataUtil.put(c, "imageId",  this.imageId);
		IDataUtil.put(c, "depends", this. depends);

		IDataUtil.put(c, "readinessProbe", this.readinessProbe);
		IDataUtil.put(c, "livenessProbe", this.livenessProbe);
		
		
		
		List<IData> envOut = new ArrayList<IData>();
		
		if (this.environments != null) {
			for (Environment e : this.environments) {
				envOut.add(e.toIData());
			}
		}
		
		IDataUtil.put(c, "environments",  envOut.toArray(new IData[envOut.size()]));
		
		c.destroy();
		
		return d;
	}

	public boolean matchBuild(Build b) {
	
		if (this.buildRef != null) {
			return this.buildRef.equals(b.name);
		} else if (this.build != null) {
			return this.build.name.equals(b.name);
		} else {
			return this.name.toLowerCase().equals(b.name.toLowerCase());
		}
	}
	
	protected void createContainer(DockerClient dockerClient, String buildno, String composeName, String environmentName) throws DockerException, InterruptedException, ServiceException {
				
		this._create(dockerClient, composeName, environmentName);
	}
	
	protected void stop(DockerClient dockerClient) throws DockerException, InterruptedException {
		
		System.out.println("stopping container " + this.id);

		if (this.id != null) {
			dockerClient.stopContainer(this.id, 0);
			dockerClient.removeContainer(this.id);
		}
		
		if (this.imageId != null)
			dockerClient.removeImage(this.imageId);
	}
	
	private String _create(DockerClient dockerClient, String composeName, String environmentName) throws DockerException, InterruptedException {
					
		if (!new ImageRegistry(dockerClient).haveImage(image)) {
			
			if (ImageRegistry.defaultRemoteRegistry() != null) {
				WebSocketContainerLogger.log("Pulling image '" + image + "' from remote repository");
				ImageRegistry.defaultRemoteRegistry().pull(image);
			} else {
				WebSocketContainerLogger.log("Image not found, but cannot pull from remote repository as you have not provided credentials");
			}	
		} 
		
		Environment env = this.getEnvironment(environmentName);
		
		List<String> envList = env.setupEnvList();

		Map<String, List<PortBinding>> portBindings = env.setUpPointBindings();
		final Builder hostConfigBuilder = HostConfig.builder().portBindings(portBindings);
							
		env.setupVolumeMounts(dockerClient, this.name, composeName, hostConfigBuilder);

		final HostConfig hostConfig = hostConfigBuilder.build();
		
		WebSocketContainerLogger.log("Creating container with hostname " + this.hostname + " for " + this.name);
		
		final ContainerConfig containerConfig = ContainerConfig.builder()
		    .hostConfig(hostConfig)
		    .hostname(this.hostname)
		    .image(image)
		    .exposedPorts(env.exposedPorts())
		    .env(envList).build();

		ContainerCreation creation = dockerClient.createContainer(containerConfig, this.name);
			
		this.id = creation.id();
	
		WebSocketContainerLogger.log("Container created with id " + this.id);

		return id;
	}
	
	Environment getEnvironment(String name) {
	
		if (name == null || this.environments.length == 0) {
			return this.environments[0];
		} else {
			for (Environment e : this.environments) {
				
				if (e.name.equalsIgnoreCase(name)) {
					return e;
				}
			}
		}
		
		return this.environments[0];
	}
	
	public String[] toStringList(Port[] args) {
		
		String out[] = new String[args.length];
		
		for (int i = 0; i < args.length; i++) {
			out[i] = args[i].external;
		}
		return out;
	}
	
	static Arg[] convertIDataArrayToArgs(IData[] docList) {
		
		if (docList == null)
			return null;
		
		Arg[] args = new Arg[docList.length];
		
		for (int i=0; i < docList.length; i++) {
			args[i] = new Arg(docList[i]);
		}
		
		return args;
	}
	
	static Port[] convertIDataArrayToPorts(IData[] docList) {
		
		if (docList == null)
			return null;
		
		Port[] ports = new Port[docList.length];
		
		for (int i=0; i < docList.length; i++) {
			ports[i] = new Port(docList[i]);
		}
		
		return ports;
	}
}