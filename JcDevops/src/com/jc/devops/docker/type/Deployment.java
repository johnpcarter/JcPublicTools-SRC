package com.jc.devops.docker.type;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jc.devops.docker.DockerConnectionUtil;
import com.jc.devops.docker.ImageRegistry;
import com.jc.devops.docker.WebSocketContainerLogger;
import com.jc.devops.docker.type.Build.Image;
import com.jc.devops.docker.type.BuildCommand.CommandType;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.NetworkConfig;
import com.wm.app.b2b.server.ServerAPI;
import com.wm.app.b2b.server.ServiceException;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class Deployment {
	
	public String name;
	public String appName;
	public String namespace;
	public String description;
	public String replicas;
	public String appSelector;
	public String hostName;
	public String type;
	public String serviceType;
	public String dependsOn;
	public String version;
	
	public boolean requiresVersioning;
	
	public API[] apis;
	public DockerContainer[] containers;

	private String _defaultDockerHost;
	private String _defaultDockerCert;
	
	List<DockerContainer> _runningContainers;

	private List<WakerDelegate> _wakers = new ArrayList<WakerDelegate>();
	private Runnable _runner;

	private String _homeDirForPropertiesFiles = "./packages/JcDevopsConsole/resources/files/properties";
	
	public interface WakerDelegate {
		public void deploymentsIsReader(Deployment deployment);
	}
	
	public Deployment(String dockerHost, String dockerCert, IData doc, String dirForPropertiesFiles) {
		
		this(doc, dirForPropertiesFiles);
		
		this._defaultDockerHost = dockerHost;
		this._defaultDockerCert =  dockerCert;
		
		if (this.hostName != null && !this.hostName.startsWith("localhost"))
			this._defaultDockerHost = this.hostName;
	}
	
	public Deployment(IData doc, String dirForPropertiesFiles) {
		
		_homeDirForPropertiesFiles = dirForPropertiesFiles;
		
		IDataCursor c = doc.getCursor();
		this.name = IDataUtil.getString(c, "name");
		this.appName = IDataUtil.getString(c, "appName");
		this.namespace = IDataUtil.getString(c, "namespace");
		this.description = IDataUtil.getString(c, "description");
		this.replicas = IDataUtil.getString(c, "replicas");
		this.appSelector = IDataUtil.getString(c, "appSelector");
		this.hostName = IDataUtil.getString(c, "hostName");
		this.type = IDataUtil.getString(c, "type");
		this.serviceType = IDataUtil.getString(c, "serviceType");
		this.dependsOn = IDataUtil.getString(c, "dependsOn");

		this.requiresVersioning = IDataUtil.getString(c, "requiresVersioning") != null && IDataUtil.getString(c, "requiresVersioning").toLowerCase().equals("true");
		
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
			this.containers = new DockerContainer[containerDocs.length];
			for (int i = 0; i < containerDocs.length; i++) {
				this.containers[i] = new DockerContainer(containerDocs[i]);
			}
		}
		
		this.version = IDataUtil.getString(c, "version");
		
		if (this.requiresVersioning && this.version == null && this.appName != null && this.appName.indexOf("-") != -1) {
			// look for version in app name
			
			int index = this.appName.indexOf("-");
			String v = this.appName.substring(index+1);
			
			if (ImageRegistry.isVersion(v.replace("-", "."))) {
				this.version = v;
			}
		}
	}
	
	public IData toIData() {
		
		IData d = IDataFactory.create();
		IDataCursor c = d.getCursor();
		
		IDataUtil.put(c, "name", this.name);
		IDataUtil.put(c, "description", this.description);
		IDataUtil.put(c, "appName", this.appName);
		IDataUtil.put(c, "namespace", this.namespace);
		IDataUtil.put(c, "replicas", this.replicas);
		IDataUtil.put(c, "appSelector", this.appSelector);
		IDataUtil.put(c, "hostName", this.appName);
		
		IDataUtil.put(c, "type", this.type);
		IDataUtil.put(c, "serviceType", this.serviceType);
		IDataUtil.put(c, "dependsOn", this.dependsOn);

		IDataUtil.put(c, "requiresVersioning", "" + requiresVersioning);
		
		IDataUtil.put(c, "version", this.version);
		
		List<IData> apisOut = new ArrayList<IData>();
		
		if (this.apis != null) {
			for (API api : this.apis) {
				apisOut.add(api.toIData());
			}
		}
		
		IDataUtil.put(c, "apis", apisOut.toArray(new IData[apisOut.size()]));
		
		List<IData> containersOut = new ArrayList<IData>();
		
		for (DockerContainer container : this.containers) {
			containersOut.add(container.toIData());
		}
		
		IDataUtil.put(c, "containers", containersOut.toArray(new IData[containersOut.size()]));

		c.destroy();
		
		return d;
	}
	
	public boolean hasContainer(String containerName) {
	
		boolean match = false;
		
		for (DockerContainer c : this.containers) {
			
			if (c.name.equals(containerName)) {
				match = true;
				break;
			}
		}
		
		return match;
	}
	
	/* 
	 Updates all end-point references to containers (used in docker-compose) to instead reference k8s services 
	 */
	public boolean updateContainerReferences(String appPrefix, String buildDir, Deployment[] deployments, Build[] builds, String environment) throws FileNotFoundException, IOException {
			
		boolean buildRequired = false;
				
		Map<String, Deployment> names = new HashMap<String, Deployment>();
		
		// build a table of all container references and their deployments names across entire project
		
		for (Deployment deployment : deployments) {
						
			for (DockerContainer c : deployment.containers) {
				
				if (c.name != null) {
					names.put(c.name, deployment);
				}
			}
		}
		
		// loop over containers in this deployment, swapping out their references to containers that are in other deployments
		// i.e. must reference their service name in k8s NOT host
		
		for (DockerContainer c: this.containers) {
							
			Environment env = c.getEnvironment(environment);
			
			if (c.active) {
						
				// check for any 
				for (String containerName : names.keySet()) {
					
					// check env vars

					if (env.env != null) {
						for (Arg a : env.env) {
							if (a.target != null && a.target.contains(containerName) && !a.source.equals("mcgw_downloads_apis")) {
								
								System.out.println("Got a match, will replace with " + names.get(containerName).name);
								a.target = a.target.replace(containerName, replacement(containerName, appPrefix, names.get(containerName), c, a, environment));
							}
						}
					}
					
					// check runtime build params

					boolean propsFileProcessed = false;
							
					if (c.build != null) {
						for (BuildCommand b : c.build.buildCommands.values()) {
														
							if (b.commandType == CommandType.file && b.fileType != null && b.fileType.equals("properties")) {
								
								updatePropertyFileForReferences(buildDir, buildDir, b.source, containerName, replacement(containerName, appPrefix, names.get(containerName), c, null, environment), names.get(containerName).version);
								propsFileProcessed = true;
							}
						}
					}
					
					// Do we have a properties file in the underlying image that needs updating ?
					
					if (!propsFileProcessed && builds != null) {
						
						for (Build b: builds) {
							
							// find build for container
														
							if (c.name.toLowerCase().equals(b.name.toLowerCase())) {
								
								for (String k : b.buildCommands.keySet()) {
									
									BuildCommand bc = b.buildCommands.get(k);
								
									if (bc.commandType == CommandType.file && bc.fileType != null && bc.fileType.equals("properties")) {
									
										if (environment == null) {
										
											// only check generic props file
											
											if (!bc.source.startsWith("_")) {
												buildRequired = addUpdatedPropertiesFileToBuild(c, bc, buildDir, appPrefix, containerName, names, environment) || buildRequired;
												propsFileProcessed = true;
											}
										} else if (bc.source.startsWith("_" + environment.toLowerCase() + "_")) {
										// only check file specific to this environment or fall back on generic if not set
											buildRequired = addUpdatedPropertiesFileToBuild(c, bc, buildDir, appPrefix, containerName, names, environment) || buildRequired;
											propsFileProcessed = true;
										}
									}
								}
								
								break;
							}
						}
					}
				}
				
				// label build with source image, and update container image (to image) to reflect target environment
				
				if (c.build != null) {
					
					c.build.sourceImage = new Image(c.image);

					if (buildRequired) {
						c.image = c.build.targetImage.tag;
					} 	
				}
			}
		}
	
		
		return buildRequired;
	}
	
	private boolean addUpdatedPropertiesFileToBuild(DockerContainer c, BuildCommand bc, String buildDir, String appPrefix, String containerName, Map<String, Deployment> names, String environment) throws FileNotFoundException, IOException {
		
		Build build = null;
		
		if (c.build == null) {
			build = new Build(new Build.Image(c.image), new Build.Image((c.image.endsWith(".d") || c.image.endsWith(".d.k8s"))? c.image : c.image + ".d"));
		} else {
			build = c.build;
		}
		
		BuildCommand cmd = build.buildCommands.get(bc.source);
		
		if (cmd == null) {
			cmd = new BuildCommand(CommandType.file, "properties", bc.source, bc.target);
		}
				
		if (updatePropertyFileForReferences(_homeDirForPropertiesFiles, buildDir, bc.source, containerName, replacement(containerName, appPrefix, names.get(containerName), c, null, environment), names.get(containerName).version)) {
			
			// only record it, if we did make a change
			
			build.buildCommands.put(bc.source, cmd);
			
			if (c.build == null) {
				c.build = build;
			}
			return true;
		} else {
			return false;
		}
	}
	
	private String replacement(String containerNameToReplace, String appPrefix, Deployment deployment, DockerContainer c, Arg arg, String environment) {
		
		if (deployment.name.equals(this.name) && this.hasContainer(containerNameToReplace)) {
			//container is referenced in same host, so we can simply replace it with localhost

			return "localhost";
		} else {
			
			// container is in different deployment, so we need to replace with the name of the service
			// which will be <prefix>-<serviceType>-<deployment-name>.<namespace>
			
			// find service type for port 
			
			String serviceType = "clusterip";
			
			/*for (DockerContainer dc : deployment.containers) {
				
				Environment env = dc.getEnvironment(environment);
				
				if (arg != null && dc.name.equals(containerNameToReplace)) {
					for (Port p : env.ports) {
						if (arg.target.contains(":" + p.internal)) {
							serviceType = p.serviceType.toLowerCase();
							break;
						}
					}
						
					/*if (serviceType == null) { 
						if (env.ports.length > 0) {
							serviceType = env.ports[0].serviceType.toLowerCase();
						} else {
							serviceType = "clusterip";
						}
					} else if (serviceType.equals("ingress")) {
						// use internal service, never external
						serviceType = "clusterip";
					}*
					
						
					break;
				}
			}*/
			
			String txt = serviceType + "-" + deployment.name.toLowerCase().replace(" ", "-");
		
			if (appPrefix != null)
				txt = appPrefix + txt;
			
			if (deployment.version != null) {
				txt += "-" + deployment.version;
			}
			
			if (deployment.namespace != null) {
				txt += "." + deployment.namespace;
			}
			
			return txt;
		}
	}
	
	public DockerClient getDockerClient() throws DockerCertificateException {
		
		return DockerConnectionUtil.createDockerClient(this._defaultDockerHost, this._defaultDockerCert);
	}
	
	public DockerClient run(DockerClient dockerClient, String composeName, String buildno, String containers, String environment) throws ServiceException {

		int count = 0;
		
		try {
			NetworkConfig networkConfig = NetworkConfig.builder()
                    .checkDuplicate(true)
                    .attachable(true)
                    .name(composeName)
                    .build();
			
			dockerClient.createNetwork(networkConfig);
		} catch (Exception e) {
					
			// assume reason for failure is that network already exists;
		}
								
		_runningContainers = new  ArrayList<DockerContainer>();
		
		if (containers != null)
			containers = containers.replace("_", "-");
		
		for (int i = 0; i < this.containers.length;  i++) {
		
			try {
				
				if (containers == null || containers.indexOf(this.containers[i].name) != -1) {
					
					if (containers != null || this.containers[i].active) {
						
						WebSocketContainerLogger.log("Starting Container '" + this.containers[i].name + "' in " + composeName);
						
						this.containers[i].createContainer(dockerClient, buildno, composeName, environment);

						if (this.containers[i].id != null) {					
							dockerClient.connectToNetwork(this.containers[i].id, composeName);
							dockerClient.startContainer(this.containers[i].id);
						}
					
						_runningContainers.add(this.containers[i]);
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

			throw new ServiceException("Failed to start any containers for service " + this.name);
		}
		
		return dockerClient;
	}
		
	public synchronized void waitForWhenReady(DockerClient dockerClient, WakerDelegate waker, long maxWait) {
		
		if (this._runningContainers == null) {
			// Damn, This means no containers were run as part of the current deployment, assume already running as part of a previous launch
			return;
		}
		
		if (this._checkStatus(dockerClient)) {
			waker.deploymentsIsReader(this);
		} else {
			this._wakers.add(waker);
			
			if (this._runner == null) {
				this._runner = new Runnable() {
					
					@Override
					public void run() {
						Deployment.this._waitForHealthy(dockerClient, maxWait);
					}
				};
				
				new Thread(this._runner).start();
			}
		}
	}
	
	public DockerClient stop() throws InterruptedException, DockerCertificateException, DockerException {
		
		DockerClient dockerClient = DockerConnectionUtil.createDockerClient(this._defaultDockerHost, this._defaultDockerCert);
		
		System.out.println("stopping service " + this.name);
		
		this._stop(dockerClient);

		return dockerClient;
	}
	
	private void _stop(DockerClient dockerClient) throws DockerException, InterruptedException {
				
		for (int i = 0; i < this.containers.length;  i++) {
			WebSocketContainerLogger.log("Stopping container " + this.containers[i].name);

			this.containers[i].stop(dockerClient);
		}
	}
	
	private synchronized boolean _waitForHealthy(DockerClient dockerClient, long timeout) {
		
		boolean done = false;
		int total = 0;
		
		while(!(done=_checkStatus(dockerClient)) && total < timeout) {
			
			System.out.println("Not up yet, waiting");
			
			try {
				this.wait(5000);
			} catch (InterruptedException e) {
				ServerAPI.logError(e);
			}
			
			total += 5000;
		}
		
		if (done) {
			
			WebSocketContainerLogger.log("All containers for service " + this.name + " have started");

			this._wakers.forEach((w) -> {
				w.deploymentsIsReader(this);
			});
		} else {
			WebSocketContainerLogger.log("Warning, containers for service " + this.name + " have not started within in: " + timeout);
		}
		
		return done;
	}
	
	private boolean _checkStatus(DockerClient dockerClient) {
		
		boolean done = false;
		
		try {
			List<com.spotify.docker.client.messages.Container> out = dockerClient.listContainers();
			
			int count = 0;
			
			for (int z = 0; z < this._runningContainers.size(); z++) {
				
				DockerContainer c = this._runningContainers.get(z);
				
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
			
			return count == this._runningContainers.size();
		
		} catch (DockerException | InterruptedException e) {
			
			ServerAPI.logError(e);
		} 
		
		return done;
	}
	
	private boolean updatePropertyFileForReferences(String srcDir, String buildDir, String fileName, String ref, String newRef, String suffix) throws FileNotFoundException, IOException {
				
		boolean didUpdate = false;
		File outDir = buildDir.endsWith("/resources") ? new File(buildDir) : new File(buildDir, "resources");		
		File outFile = new File(outDir, fileName + ".properties");

		File inFile;
		boolean dontDelete = false;
				
		if (!outFile.exists()) {
			
			// read from source
			
			outDir.mkdirs();
			inFile = new File(srcDir, fileName + ".properties");			
		} else {
			
			// already started rebuild, work from already started
						
			dontDelete = true;
			inFile = outFile;
			outFile = new File(outDir, fileName + ".properties.copy");
		}
		
		try (BufferedReader rdr = new BufferedReader(new FileReader(inFile));
			 BufferedWriter wrt = new BufferedWriter(new FileWriter(outFile))) {
			
			String line = null;
			while((line=rdr.readLine()) != null) {
								
				if (line.contains("=")) {
					int index = line.indexOf("=");
					String key = line.substring(0, index);
					String value = line.substring(index+1);
					
					if (value.contains(ref)) {
						line = key + "=" + value.replace(ref, newRef);
						
						if (suffix != null && !newRef.endsWith(suffix)) {
							line += "-" + suffix;
						}
						didUpdate = true;
					} else if (suffix != null && value.length() > 0 && newRef.startsWith(value) && !value.contains(suffix)) {
						// properties file has already includes new label, but does'n include the suffix yet
						
						if (newRef.endsWith(suffix)) {
							line = key + "=" + value.replace(value, newRef);
						} else {
							line = key + "=" + value.replace(value, newRef + "-" + suffix);
						}
						didUpdate = true;
					}
				}
				
				wrt.write(line);
				wrt.newLine();
			}
		}
		
		if (outFile.getName().endsWith(".copy")) {
			if (didUpdate) {				
				Files.move(FileSystems.getDefault().getPath(outFile.getAbsolutePath()), FileSystems.getDefault().getPath(inFile.getAbsolutePath()), StandardCopyOption.REPLACE_EXISTING);
			} else { 
				Files.delete(FileSystems.getDefault().getPath(outFile.getAbsolutePath()));
			}
		} else {
				// worked on original
			if (!didUpdate && !buildDir.equals(srcDir) && !dontDelete) {
					
				// no change made					
				Files.delete(FileSystems.getDefault().getPath(outFile.getAbsolutePath()));
			}
		}
		
		return didUpdate;
	}
}