package com.jc.devops.docker;

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

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.BuildParam;
import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.HostConfig.Bind;
import com.spotify.docker.client.messages.HostConfig.Builder;
import com.spotify.docker.client.messages.NetworkConfig;
import com.spotify.docker.client.messages.NetworkCreation;
import com.spotify.docker.client.messages.PortBinding;
import com.spotify.docker.client.messages.ProgressMessage;
import com.spotify.docker.client.messages.Volume;
import com.google.common.collect.ImmutableList;

import com.wm.app.b2b.server.ServerAPI;
import com.wm.app.b2b.server.ServiceException;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataUtil;

public class DockerComposeImpl {
	
	public String name;
	public Service[] services;
	
	public DockerComposeImpl(String dockerHost, String dockerCert, IData doc, String stageName) {
		
		IDataCursor c = doc.getCursor();
		name = IDataUtil.getString(c, "name").replace(" ", "-");
		IData[] svcs = IDataUtil.getIDataArray(c, "services");
		c.destroy();

		if (stageName != null && stageName.length() > 0 && !stageName.equalsIgnoreCase("null"))
			name = stageName.replace(" ", "-").toLowerCase() + "_" + name;
						
		if (svcs != null) {
			this.services = new Service[svcs.length];
		
			for (int i=0; i < svcs.length; i++) {
				this.services[i] = new Service(dockerHost, dockerCert, svcs[i]);
			}	
		} else {
			WebSocketContainerLogger.log("You haven't defined any services!");
		}
	}
	
	public void run(String buildno, String containers) throws ServiceException {
		
		Service lastService = null;
		DockerClient dockerClient = null;
		
		int count = 0;
		
		for (int i = 0; i < this.services.length;  i++) {
			
			WebSocketContainerLogger.log("Starting up service '" + this.services[i].serviceName + "'");

			try {
				if (lastService == null || lastService.hostName != this.services[i].hostName) {
					dockerClient = this.services[i].run(this.name, buildno, containers, i != this.services.length-1);
				} else {
					this.services[i].run(dockerClient, this.name, buildno, containers, i != this.services.length-1);
				}
				
				lastService = this.services[i];
				count += 1;
			} catch (ServiceException e) {
			
				WebSocketContainerLogger.log("Failed to start service '" + this.services[i].serviceName + "' due to error: " + e.getMessage());

				ServerAPI.logError(e);
			} catch (DockerCertificateException e) {
				
				WebSocketContainerLogger.log("Couldn't connect to docker host: " + e.getMessage());

				ServerAPI.logError(e);
			}
		}
		
		if (count == 0) {
			WebSocketContainerLogger.log("Failed to start any services, woops");

			throw new ServiceException("Failed to start any services, all containtainers failed");
		}
	}
	
	public void stop() throws DockerException, InterruptedException, DockerCertificateException {
		
		Service lastService = null;
		DockerClient dockerClient = null;//new DefaultDockerClient("unix:///var/run/docker.sock");
		
		for (int i = 0; i < this.services.length;  i++) {
			
			if (lastService != null && isNotSameHost(lastService, this.services[i])) {
				
				System.out.println("Removing network " + lastService.hostName + " / " + this.services[i].hostName);
				
				dockerClient.removeNetwork(name);
			}
			
			WebSocketContainerLogger.log("Stopping Service '" + this.services[i].serviceName + "'");

			dockerClient = this.services[i].stop();
			lastService = this.services[i];
		}	
		
		if (lastService != null)
			dockerClient.removeNetwork(name);
	}
	
	private boolean isNotSameHost(Service svc1, Service svc2) {
		
		String host1 = "local";
		String host2 = "local";
		
		if (svc1 != null && svc1.hostName != null)
			host1 = svc1.hostName;
		
		if (svc2 != null && svc2.hostName != null)
			host2 = svc2.hostName;
		
		return !host1.equals(host2);
	}

	private static class Service {
		
		public String serviceName;
		public String appName;
		public String replicas;
		public String appSelector;
		public String hostName;
		public API[] apis;
		public Container[] containers;
					
		private String _defaultDockerHost;
		private String _defaultDockerCert;
		
		public Service(String dockerHost, String dockerCert, IData doc) {
			
			this._defaultDockerHost = dockerHost;
			this._defaultDockerCert =  dockerCert;
			
			IDataCursor c = doc.getCursor();
			this.serviceName = IDataUtil.getString(c, "name");
			this.appName = IDataUtil.getString(c, "appName");
			this.replicas = IDataUtil.getString(c, "replicas");
			this.appSelector = IDataUtil.getString(c, "appSelector");
			this.hostName = IDataUtil.getString(c, "hostName");
	
			IData[] apiDocs = IDataUtil.getIDataArray(c, "apis");
			IData[] containerDocs = IDataUtil.getIDataArray(c, "containers");
			c.destroy();
			
			if (apiDocs != null) {
				
				this.apis = new API[apiDocs.length];
				for (int i = 0; i < apiDocs.length; i++) {
					this.apis[i] = new  API(apiDocs[i]);
				}
			}
			
			if (containerDocs != null) {
				this.containers = new Container[containerDocs.length];
				for (int i = 0; i < containerDocs.length; i++) {
					this.containers[i] = new  Container(containerDocs[i]);
				}
			}
			
			if (this.hostName != null && !this.hostName.startsWith("localhost"))
				this._defaultDockerHost = this.hostName;
		}
		
		public DockerClient run(String composeName, String buildno, String containers, boolean waitForHealthyContainers) throws ServiceException, DockerCertificateException {
			
			return run(DockerConnectionUtil.createDockerClient(this._defaultDockerHost, this._defaultDockerCert), composeName, buildno, containers, waitForHealthyContainers);
		}
		
		public DockerClient run(DockerClient dockerClient, String composeName, String buildno, String containers, boolean waitForHealthyContainers) throws ServiceException {
	
			int count = 0;
			
			try {
				NetworkConfig networkConfig = NetworkConfig.builder()
	                    .checkDuplicate(true)
	                    .attachable(true)
	                    .name(composeName)
	                    .build();
				
				NetworkCreation network = dockerClient.createNetwork(networkConfig);
			} catch (Exception e) {
						
				// assume reason for failure is that network already exists;
			}
									
			List<Container> runningContainers = new  ArrayList<Container>();
			
			if (containers != null)
				containers = containers.replace("_", "-");
			
			for (int i = 0; i < this.containers.length;  i++) {
			
				try {
					
					if (containers == null || containers.indexOf(this.containers[i].name) != -1) {
						
						if (containers != null || this.containers[i].active) {
							
							WebSocketContainerLogger.log("Starting Container '" + this.containers[i].name + "'");
							
							this.containers[i].createContainer(dockerClient, buildno, composeName);
	
							if (this.containers[i].id != null) {					
								dockerClient.connectToNetwork(this.containers[i].id, composeName);
								dockerClient.startContainer(this.containers[i].id);
							}
						
							runningContainers.add(this.containers[i]);
							count += 1;
						} else {
							WebSocketContainerLogger.log("Ignoring deactivated Container " + this.containers[i].name + ", did you mean to not start it ?");
	
						}
					
					} else {
						WebSocketContainerLogger.log("container not found in list:" + this.containers[i].name + " of " + containers);
	
						count += 1;
					}
				} catch(Exception e) {
					WebSocketContainerLogger.log("Failed to start container " + this.containers[i].name + " due to error:" + e.getMessage());
	
					e.printStackTrace();
					ServerAPI.logError(e);
				}
			}
			
			if (count == 0) {
	
				throw new ServiceException("Failed to start any containers for service " + this.serviceName);
			}
			
			// now wait for all to be running or healthy
			
			if (waitForHealthyContainers) {
				
				WebSocketContainerLogger.log("Waiting for containers to finish starting... ");
	
				waitForHealthy(dockerClient, runningContainers, 120000);
			}
			
			return dockerClient;
		}
		
		public DockerClient stop() throws InterruptedException, DockerCertificateException, DockerException {
			
			DockerClient dockerClient = DockerConnectionUtil.createDockerClient(this._defaultDockerHost, this._defaultDockerCert);
			
			System.out.println("stopping service " + this.serviceName);
			
			this._stop(dockerClient);
	
			return dockerClient;
		}
		
		private void _stop(DockerClient dockerClient) throws DockerException, InterruptedException {
					
			for (int i = 0; i < this.containers.length;  i++) {
				WebSocketContainerLogger.log("Stopping container " + this.containers[i].name);
	
				this.containers[i].stop(dockerClient);
			}
		}
		
		private synchronized boolean waitForHealthy(DockerClient dockerClient, List<Container> containers, long timeout) {
			
			boolean done = checkStatus(dockerClient, containers, timeout);
			
			if (!done)
				try {
					this.wait(timeout);
				} catch (InterruptedException e) {
					ServerAPI.logError(e);
				}
			
			WebSocketContainerLogger.log("All containers for service " + this.serviceName + " have started");
			return done;
		}
		
		private synchronized boolean checkStatus(DockerClient dockerClient, List<Container> containers, long maxWait) {
			
			boolean done = _checkStatus(dockerClient, containers);
				
			if (!done) {
										
				runCheckInBackground(dockerClient, containers);
									
				System.out.println("Wait for service containers to  start: " + this.serviceName);
	
				try {
					wait(maxWait);
					
					System.out.println("Service containers started successfully: " + this.serviceName);
	
				} catch (InterruptedException e) {
					ServerAPI.logError(e);
				}
			}
			
			return done;
		}
		
		private boolean _checkStatus(DockerClient dockerClient, List<Container> containers) {
			
			boolean done = false;
			
			try {
				List<com.spotify.docker.client.messages.Container> out = dockerClient.listContainers();
				
				int count = 0;
				
				for (int z = 0; z < containers.size(); z++) {
					
					Container c = containers.get(z);
					
					System.out.println("checking if container is healthy " + c.image);
					
					for (int i = 0; i < out.size(); i++) {
						
						if (c.image.equals(out.get(i).image())) {
							// found match
							
							WebSocketContainerLogger.log("Found running docker with status " + out.get(i).status());
	
							
							if (out.get(i).status().contains("(")) {
								// check if healthy
								
								if (out.get(i).status().contains("healthy"))
									count += 1;
							} else {
								// no health check, just make sure it's up
								
								if (out.get(i).status().contains("Up"))
									count += 1;
							}
							
							break;
						}
					}
				}
				
				return count == containers.size();
			
			} catch (DockerException | InterruptedException e) {
				
				ServerAPI.logError(e);
			} 
			
			return done;
		}
		
		private synchronized void runCheckInBackground(DockerClient dockerClient, List<Container> containers) {
			
			new Thread(new Runnable() {
				
				@Override
				public void run() {
					
					boolean done = false;
					int max = 0;
					
					WebSocketContainerLogger.log("Waiting for service layer '" + Service.this.serviceName + "' to be ready....");
	
					while (!done && max <= 12) {
													
						try {
							Thread.sleep(10000);
						} catch (InterruptedException e) {
							ServerAPI.logError(e);
						}
						
						WebSocketContainerLogger.log("Checking container health..");
						
						if (_checkStatus(dockerClient, containers)) {
							done = true;
							notifyAll();
						}
						
						max += 1;
					}		
				}
			}).start();
		}
	}
	
	private static class Container {
		
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
		
		public Container(IData doc) {
			
			IDataCursor c = doc.getCursor();
			this.name = IDataUtil.getString(c, "name");
			this.image = IDataUtil.getString(c, "image");
			this.hostname = IDataUtil.getString(c, "hostname");
			this.active = IDataUtil.getString(c, "active") != null && IDataUtil.getString(c, "active").equalsIgnoreCase("true");
	
			this.depends = IDataUtil.getStringArray(c, "depends");
	
			if (IDataUtil.getIData(c, "build") != null)
				this.build = new Build(IDataUtil.getIData(c, "build"));
			
			this.ports = convertIDataArrayToPorts(IDataUtil.getIDataArray(c, "ports"));
			this.env = convertIDataArrayToArgs(IDataUtil.getIDataArray(c, "env"));
			this.volumes = convertIDataArrayToArgs(IDataUtil.getIDataArray(c, "volumes"));
	
			c.destroy();
			
			if (hostname == null)
				hostname = this.name;
		}
		
		protected void createContainer(DockerClient dockerClient, String buildno, String composeName) throws DockerException, InterruptedException, ServiceException {
			
			String imageName = this.image;
			
			if (this.build != null) {
				
				// need to build image first
				
				imageName = buildno + "-" + (this.image != null ? this.image : name);
				
				this.imageId = this.build.run(dockerClient, this.image, buildno, imageName);
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
			
			Map<String, List<PortBinding>> portBindings = setUpPointBindings();
			List<String> envList = setupEnvList();
	
			final Builder hostConfigBuilder = HostConfig.builder().portBindings(portBindings);
								
			setupVolumeMounts(dockerClient, this.name, composeName, hostConfigBuilder);
	
			final HostConfig hostConfig = hostConfigBuilder.build();
			
			WebSocketContainerLogger.log("Creating container with hostname " + this.hostname);
			
			final ContainerConfig containerConfig = ContainerConfig.builder()
			    .hostConfig(hostConfig)
			    .hostname(this.hostname)
			    .image(imageName)
			    .exposedPorts(toStringList(this.ports))
			    .env(envList).build();
	
			ContainerCreation creation = dockerClient.createContainer(containerConfig, this.name);
		
			this.id = creation.id();
			ContainerInfo info = dockerClient.inspectContainer(id);												
							
			return this.id;
		}
		
		private void  setupVolumeMounts(DockerClient dockerClient, String owner, String composeName, Builder build) throws DockerException, InterruptedException {
			
			if (this.volumes == null)
				return;
			
			if (owner == null || owner.equals(""))
				owner = "def";
			
			ImmutableList<Volume> volumeList = dockerClient.listVolumes().volumes();
								
			for (int i = 0; i < this.volumes.length; i++) {
				
				if (this.volumes[i].src.startsWith("/")) {
					
					// local file based mount
					
					build.appendBinds(this.volumes[i].src + ":" + this.volumes[i].tgt);
					
				} else {
					
					String id = owner + "_" + this.volumes[i].src;
					
					if (composeName !=  null && !composeName.equals(""))
						id = composeName + "_" + id;
					
					Volume v = this.volumeForName(volumeList, id);
					
					if (v == null) {
					
						Volume toCreate = Volume.builder()
								.name(id)
								.mountpoint(this.volumes[i].tgt)
								.build();
						
						v = dockerClient.createVolume(toCreate);
					}
					
					build.appendBinds(Bind.from(v).to(this.volumes[i].tgt).build());
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
			
				out.add(this.env[i].src + "=" + this.env[i].tgt);
			}
			
			return out;
		}
		
		private Map<String, List<PortBinding>> setUpPointBindings() {
			
			final Map<String, List<PortBinding>> portBindings = new HashMap<>();
			
			for (int i = 0; i < this.ports.length; i++) {
				
			    List<PortBinding> hostPorts = new ArrayList<>();
			    hostPorts.add(PortBinding.of("0.0.0.0", this.ports[i].external));
			    portBindings.put(this.ports[i].internal, hostPorts);
			}
	
			return portBindings;
		}
		
		public String[] toStringList(Port[] args) {
			
			String out[] = new String[args.length];
			
			for (int i = 0; i < args.length; i++) {
				out[i] = args[i].external;
			}
			return out;
		}
	}
	
	private static class Port {
		public String internal;
		public String external;
		public String publicPort;
		
		public Port(IData doc) {
			
			IDataCursor c = doc.getCursor();
			this.internal = IDataUtil.getString(c, "internal");
			this.external = IDataUtil.getString(c, "external");
			this.publicPort = IDataUtil.getString(c, "publicPort");
			c.destroy();
		}
	}
	
	private static class Build {
		
		public String dockerfile;
		public String context;
		public Map<String, String> args;
		
		public String imageId;
		
		Build(IData doc) {
			
			IDataCursor c = doc.getCursor();
			this.dockerfile = IDataUtil.getString(c, "dockerfile");
			this.context = IDataUtil.getString(c, "context");
			IData[] args = IDataUtil.getIDataArray(c, "args");
			c.destroy();
			
			this.args = new HashMap<String, String>();
			
			if (args != null) {
				for (int i = 0; i < args.length; i++) {
					c = args[i].getCursor();
					this.args.put(IDataUtil.getString(c, "src"), IDataUtil.getString(c, "tgt"));
	
					c.destroy();
				}
			}
		}
		
		protected String run(DockerClient dockerClient, String image, String buildno, String tempName) throws ServiceException  {
	
			File buildDir = new File("./packages/JcDevops/builds/" +  buildno);
			
			if (this.context != null)
				buildDir =  new File(this.context);
			else if (this.args.get("context") != null)
				buildDir = new File(this.args.get("context"));
			
			if (!buildDir.isDirectory())
				throw new ServiceException("BuildDir must be a directory: " + buildDir.getAbsoluteFile());
			
			if (!new File(buildDir, "Dockerfile").exists())
				throw new ServiceException("No Dockerfile in build directory: " + buildDir.getAbsoluteFile());
							
			final AtomicReference<String> imageIdFromMessage = new AtomicReference<>();
			
			String returnedImageId = null;
			
			// setup args
			
			if (image != null)
				this.args.put("SRC", image);
			
			String buildargs = "{";
			Iterator<String> it = this.args.keySet().iterator();
			 
			 while (it.hasNext()) {
				 
				 String k = it.next();
				 buildargs += "\"" + k + "\":\"" + this.args.get(k) + "\"";
				 
				 if (it.hasNext())
					 buildargs += ",";
			 };
			 
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
	}
	
	private static class Arg {
		
		public String src;
		public String tgt;
		
		Arg(IData doc) {
			
			IDataCursor c = doc.getCursor();
			this.src = IDataUtil.getString(c, "src");
			this.tgt = IDataUtil.getString(c, "tgt");
			c.destroy();
		}
	}
	
	private static Arg[] convertIDataArrayToArgs(IData[] docList) {
		
		if (docList == null)
			return null;
		
		Arg[] args = new Arg[docList.length];
		
		for (int i=0; i < docList.length; i++) {
			args[i] = new Arg(docList[i]);
		}
		
		return args;
	}
	
	private static Port[] convertIDataArrayToPorts(IData[] docList) {
		
		if (docList == null)
			return null;
		
		Port[] ports = new Port[docList.length];
		
		for (int i=0; i < docList.length; i++) {
			ports[i] = new Port(docList[i]);
		}
		
		return ports;
	}
	
	private static class API {
		
		public String name;
		public String packageName;
		public String swaggerEndPoint;
		public String endPoint;
		
		API(IData doc) {
			
			IDataCursor c = doc.getCursor();
			this.name = IDataUtil.getString(c, "name");
			this.packageName = IDataUtil.getString(c, "packageName");
			this.swaggerEndPoint = IDataUtil.getString(c, "swaggerEndPoint");
			this.endPoint = IDataUtil.getString(c, "endPoint");
	
			c.destroy();
		}
	}
}