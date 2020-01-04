package com.jc.devops.docker.type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jc.devops.docker.ImageRegistry;
import com.jc.devops.docker.WebSocketContainerLogger;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.PortBinding;
import com.spotify.docker.client.messages.Volume;
import com.spotify.docker.client.messages.HostConfig.Bind;
import com.spotify.docker.client.messages.HostConfig.Builder;
import com.spotify.docker.client.shaded.com.google.common.collect.ImmutableList;
import com.wm.app.b2b.server.ServiceException;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class DockerContainer {
	
	public String name;
	public String image;
	public String hostname;
	public boolean active;
	
	public Build build;
	public Port[] ports;
	public Arg[] env;
	public Arg[] volumes;
	public String[] depends;
	
	public String id;
	public String imageId;
	
	public IData readinessProbe;
	public IData livenessProbe;
	
	public DockerContainer(IData doc) {
		
		IDataCursor c = doc.getCursor();
		this.name = IDataUtil.getString(c, "name");
		this.image = IDataUtil.getString(c, "image");
		this.hostname = IDataUtil.getString(c, "hostname");
		this.active = IDataUtil.getString(c, "active") != null && IDataUtil.getString(c, "active").equalsIgnoreCase("true");

		this.depends = IDataUtil.getStringArray(c, "depends");

		if (IDataUtil.getIData(c, "build") != null)
			this.build = new Build(IDataUtil.getIData(c, "build"));
		
		this.ports = DockerContainer.convertIDataArrayToPorts(IDataUtil.getIDataArray(c, "ports"));
		this.env = DockerContainer.convertIDataArrayToArgs(IDataUtil.getIDataArray(c, "env"));
		this.volumes = DockerContainer.convertIDataArrayToArgs(IDataUtil.getIDataArray(c, "volumes"));

		readinessProbe = IDataUtil.getIData(c, "readinessProbe");
		livenessProbe = IDataUtil.getIData(c, "livenessProbe");

		c.destroy();
		
		if (hostname == null)
			hostname = this.name;
	}
	
	public IData toIData() {
		
		IData d = IDataFactory.create();
		IDataCursor c = d.getCursor();
		
		IDataUtil.put(c, "name", name);
		IDataUtil.put(c, "image", image);
		
		if (build != null)
			IDataUtil.put(c, "build", build.toIData());

		IDataUtil.put(c, "hostname", hostname);
		IDataUtil.put(c, "active", "" + active);
		IDataUtil.put(c, "id",  id);
		IDataUtil.put(c, "imageId",  imageId);
		IDataUtil.put(c, "depends",  depends);

		IDataUtil.put(c, "readinessProbe", this.readinessProbe);
		IDataUtil.put(c, "livenessProbe", this.livenessProbe);

		
		List<IData> portsOut = new ArrayList<IData>();
		
		if (this.ports != null) {
			for (Port p : this.ports) {
				portsOut.add(p.toIData(true));
			}
		}
		
		IDataUtil.put(c, "ports",  portsOut.toArray(new IData[portsOut.size()]));

		List<IData> volumesOut = new ArrayList<IData>();
		
		if (this.volumes != null) {
			for (Arg v : this.volumes) {
				volumesOut.add(v.toIData(true));
			}
		}
		
		IDataUtil.put(c, "volumes",  volumesOut.toArray(new IData[volumesOut.size()]));
		
		List<IData> envOut = new ArrayList<IData>();
		
		if (this.env != null) {
			for (Arg v : this.env) {
				envOut.add(v.toIData());
			}
		}
		
		IDataUtil.put(c, "env",  envOut.toArray(new IData[envOut.size()]));
		c.destroy();
		
		return d;
	}
	
	public void addEnvVariable(String key, String value) {
		
		Arg newElement = new Arg(key, value);

		Arg[] envArray = new Arg[this.env.length+1];
		
		for (int i = 0; i < this.env.length; i++) {
			envArray[i] = env[i];
		}
		
		envArray[this.env.length] = newElement;
		
		this.env = envArray;
	}

	protected void createContainer(DockerClient dockerClient, String buildno, String composeName) throws DockerException, InterruptedException, ServiceException {
		
		String imageName = this.image;
		
		if (this.build != null) {
			
			// need to build image first
			
			//imageName = buildno + "-" + (this.image != null ? this.image : name);
			
			//this.imageId = this.build.run(dockerClient, this.image, buildno, imageName);
		} 
			
		this._create(dockerClient, imageName, composeName);
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
	
	private String _create(DockerClient dockerClient, String imageName, String composeName) throws DockerException, InterruptedException {
					
		if (!new ImageRegistry(dockerClient).haveImage(imageName)) {
			
			if (ImageRegistry.defaultRemoteRegistry() != null) {
				WebSocketContainerLogger.log("Pulling image '" + imageName + "' from remote repository");
				ImageRegistry.defaultRemoteRegistry().pull(imageName);
			} else {
				WebSocketContainerLogger.log("Image not found, but cannot pull from remote repository as you have not provided credentials");
			}	
		} 
		
		List<String> envList = setupEnvList();

		Map<String, List<PortBinding>> portBindings = setUpPointBindings();
		final Builder hostConfigBuilder = HostConfig.builder().portBindings(portBindings);
							
		setupVolumeMounts(dockerClient, this.name, composeName, hostConfigBuilder);

		final HostConfig hostConfig = hostConfigBuilder.build();
		
		WebSocketContainerLogger.log("Creating container with hostname " + this.hostname + " for " + this.name);
		
		final ContainerConfig containerConfig = ContainerConfig.builder()
		    .hostConfig(hostConfig)
		    .hostname(this.hostname)
		    .image(imageName)
		    .exposedPorts(this.exposedPorts())
		    .env(envList).build();

		ContainerCreation creation = dockerClient.createContainer(containerConfig, this.name);
	
		this.id = creation.id();
		
		return id;
	}
	
	private void  setupVolumeMounts(DockerClient dockerClient, String owner, String composeName, Builder build) throws DockerException, InterruptedException {
		
		if (this.volumes == null)
			return;
		
		if (owner == null || owner.equals(""))
			owner = "def";
		
		ImmutableList<Volume> volumeList = dockerClient.listVolumes().volumes();
							
		for (int i = 0; i < this.volumes.length; i++) {
			
			if (this.volumes[i].source.startsWith("/")) {
				
				// local file based mount
				
				build.appendBinds(this.volumes[i].source + ":" + this.volumes[i].target);
				
			} else {
				
				String id = owner + "_" + this.volumes[i].source;
				
				if (composeName !=  null && !composeName.equals(""))
					id = composeName + "_" + id;
				
				Volume v = this.volumeForName(volumeList, id);
				
				if (v == null) {
				
					Volume toCreate = Volume.builder()
							.name(id)
							.mountpoint(this.volumes[i].target)
							.build();
					
					v = dockerClient.createVolume(toCreate);
				}
				
				build.appendBinds(Bind.from(v).to(this.volumes[i].target).build());
			}
		}
	}
	
	private Volume volumeForName(ImmutableList<Volume> volumeList, String name) {
		
		Volume found = null;
		
		for (int i = 0; i < volumeList.size(); i++) {
			
			if (volumeList.get(i).name().equals(name)) {
				found = volumeList.get(i);
				break;
			}
		}
		
		return found;
	}
	
	private List<String> setupEnvList() {
		
		ArrayList<String> out = new ArrayList<>();
		
		if (this.env == null)
			return out;
		
		for (int i = 0; i < this.env.length; i++) {
		
			out.add(this.env[i].source + "=" + this.env[i].target);
		}
		
		return out;
	}

	private Set<String> exposedPorts() {
	
		Set<String> out = new HashSet<String>();
		
		for (int i = 0; i < this.ports.length; i++) {
			  
			out.add(this.ports[i].internal);
		}
		
		return out;
	}
	
	private Map<String, List<PortBinding>> setUpPointBindings() {
		
		final Map<String, List<PortBinding>> portBindings = new HashMap<>();
		
		for (int i = 0; i < this.ports.length; i++) {
			  
			if (this.ports[i].external != null)
				this.createPortBinding(portBindings, Integer.parseInt(this.ports[i].internal), Integer.parseInt(this.ports[i].external)); 
		}

		return portBindings;
	}
	
	private void createPortBinding(Map<String, List<PortBinding>> portBindings, int from, int to) {
	    
	    
		List<PortBinding> hostPorts = new ArrayList<>();
	    
		hostPorts.add(PortBinding.of("0.0.0.0", to));
	    portBindings.put(String.valueOf(from), hostPorts);
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