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

	private File _homeDirForFiles = new File("./packages/JcDevopsConsole/resources/files");
	
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
	
	public Deployment(IData doc, String dirForFiles) {
		
		_homeDirForFiles = new File(dirForFiles);
		
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
	
	public static boolean updateContainerReferences(Deployment[] deployments, Build[] builds, String appPrefix, String buildDir, String environment, String defaultNamespace) throws FileNotFoundException, IOException {
		
		boolean buildRequired = false;

		Map<String, Map<String, String>> dnames = new HashMap<String, Map<String, String>>();

		for (Deployment d : deployments) {
			
			Map<String, String> inner = new HashMap<String, String>();
			
			for (DockerContainer c : d.containers) {
				inner.put(c.hostname != null ? c.hostname : c.name, replacement(appPrefix, d, defaultNamespace));
			}
			
			dnames.put(d.name, inner);
		}
		
		for (Deployment d : deployments) {
			if (d.updateContainerReferences(environment, buildDir, deployments, builds, dnames, defaultNamespace)) {
				buildRequired = true;
			}
		}
		
		return buildRequired;
	}
	
	/* 
	 Updates all end-point references to containers (used in docker-compose) to instead reference k8s services 
	 */
	protected boolean updateContainerReferences(String environment, String buildDir, Deployment[] deployments, Build[] builds, Map<String, Map<String, String>> newNames, String defaultNamespace) throws FileNotFoundException, IOException {
			
		boolean buildRequired = false;
		
		System.out.println("======= checking deployment " + this.name);
		
		// loop over containers in this deployment, swapping out their references to containers that are in other deployments
		// i.e. must reference their service name in k8s NOT host
		
		for (DockerContainer c: this.containers) {
							
			System.out.println("======= checking container " + c.name);
			
			Environment env = c.getEnvironment(environment);
			
			if (c.active) {
					
				File buildDirForContainer = new File(buildDir, c.name);

				// check for any 
				for (String dname : newNames.keySet()) {
					
					Map<String, String> containerRefs = newNames.get(dname);
					
					for (String containerName : containerRefs.keySet()) {
										
						// check env vars

						if (env.env != null) {
							for (Arg a : env.env) {
								if (a.target != null && a.target.contains(containerName) && !a.source.equals("mcgw_downloads_apis")) {
								
									System.out.println("Got a match, will replace " + containerName + " with " + containerRefs.get(containerName));
									
									a.target = swap(a.target, containerName, dname, containerRefs.get(containerName), defaultNamespace);
								}
							}
						}
					}
				}
				
				// check runtime build params

				boolean propsFileProcessed = false;
						
				if (c.build != null) {
					for (BuildCommand b : c.build.buildCommands.values()) {
													
						if ((b.commandType == CommandType.file || b.commandType == CommandType.copy || b.commandType == CommandType.add) && b.fileType != null && 
								(b.fileType.equals("properties") || (b.fileType.equals("resource") && (b.source.endsWith(".txt") || b.source.endsWith(".yml") || b.source.endsWith(".json") || b.source.endsWith(".conf") || b.source.endsWith(".cnf"))))) {
							
							File sourceDir = buildDirForContainer;
							File tgtDir = buildDirForContainer;
							String source = b.source;

							if (b.fileType.equals("resource")) {
								// properties files has already been copied in below, but not other files
								
								sourceDir = new File(_homeDirForFiles, "other");
								
								if (source.indexOf("/") != -1) {
									// have a sub-directory, need to recreate here
									
									tgtDir = new File(tgtDir, new File(source).getParent());
									source = new File(source).getName();
								}
							} else {
								tgtDir = new File(tgtDir, "resources");
								source += ".properties";
							}
							
							updateTextFileForReferences(c.name, sourceDir, tgtDir, source, newNames, defaultNamespace);
							propsFileProcessed = true;
						}
					}
				}
				
				// Do we have any config/properties file in the underlying image that needs updating ?
				
				if (!propsFileProcessed && builds != null) {
					
					for (Build b: builds) {
						
						// find build for container
													
						if (c.matchBuild(b)) {
							
							for (String k : b.buildCommands.keySet()) {
								
								BuildCommand bc = b.buildCommands.get(k);
							
								if ((bc.commandType == CommandType.file || bc.commandType == CommandType.add || bc.commandType == CommandType.copy) && bc.fileType != null && bc.fileType.equals("properties")) {
								
									boolean didChange = false;
									if (environment == null) {
									
										// only check generic props file
										
										if (!bc.source.startsWith("_")) {
											didChange = addUpdatedPropertiesFileToBuild(c, bc, buildDirForContainer, newNames, defaultNamespace) || buildRequired;
											propsFileProcessed = true;
										}
									} else if (bc.source.startsWith("_" + environment.toLowerCase() + "_")) {
									// only check file specific to this environment or fall back on generic if not set
										didChange = addUpdatedPropertiesFileToBuild(c, bc, buildDirForContainer, newNames, defaultNamespace) || buildRequired;
										propsFileProcessed = true;
									}
									
									if (didChange)
										buildRequired = true;
								}
							}
							
							break;
						}
					}
				}
				
				// label build with source image, and update container image (to image) to reflect target environment
				
				if (c.build != null) {
					
					c.build.sourceImage = new Image(c.image);

					if (buildRequired) {
						if (c.build.targetImage != null)
							c.image = c.build.targetImage.tag;
						else
							c.image = c.build.sourceImage.tag;
					} 	
				}
			}
		}
	
		return buildRequired;
	}
	
	private boolean addUpdatedPropertiesFileToBuild(DockerContainer c, BuildCommand bc, File buildDir,  Map<String, Map<String, String>> newNames, String defaultNamespace) throws FileNotFoundException, IOException {
		
		Build build = null;
		
		if (c.build == null) {
			build = new Build(new Build.Image(c.image), new Build.Image((c.image.endsWith(".d") || c.image.endsWith(".d.k8s"))? c.image : c.image + ".d"));
		} else {
			build = c.build;
		}
		
		BuildCommand cmd = build.buildCommands.get(bc.source);
		
		if (cmd == null) {
			cmd = new BuildCommand(CommandType.add, "properties", bc.source, bc.target);
		}
				
		if (updateTextFileForReferences(c.name, new File(_homeDirForFiles, "properties"), new File(buildDir, "resources"), bc.source + ".properties", newNames, defaultNamespace)) {
			
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
	
	private static String replacement(String appPrefix, Deployment deployment, String defaultNamespace) {
		
		// find service type for port 
			
		String serviceType = "clusterip";
			
		String txt = serviceType + "-" + deployment.name.toLowerCase().replace(" ", "-").replace("_", "-");
		
		if (appPrefix != null) {
			txt = appPrefix + txt;
		}
		
		if (deployment.version != null && deployment.version.length() > 0) {
			txt += "-" + deployment.version;
		}
			
		if (deployment.namespace != null && deployment.namespace.length() > 0) {
			txt += "." + deployment.namespace;
		} else if (defaultNamespace != null) {
			txt += "." + defaultNamespace;
		}
			
		return txt;
	}

	private boolean updateTextFileForReferences(String containerName, File srcDir, File outDir, String fileName,  Map<String, Map<String, String>> newNames, String defaultNamespace) throws FileNotFoundException, IOException {
				
		boolean didUpdate = false;
		//File outDir = buildDir.endsWith("/resources") ? new File(buildDir) : new File(new File(buildDir, ref), "resources");
		File outFile = new File(outDir, fileName);

		File inFile;
		boolean dontDelete = false;
				
		if (!outFile.exists()) {
			
			// read from source
			
			outDir.mkdirs();
			inFile = new File(srcDir, fileName);
			
			if (!inFile.exists()) {
				// if the file doesn't exist, probably because it has the container name as a prefix, try that
				
				inFile = new File(srcDir, containerName.toLowerCase() + "-" + fileName);
			}
		} else {
			
			// already started rebuild, work from already started
						
			dontDelete = true;
			inFile = outFile;
			outFile = new File(outDir, fileName + ".copy");
		}
			
		System.out.println("======= checking file " + fileName);

		try (BufferedReader rdr = new BufferedReader(new FileReader(inFile));
			 BufferedWriter wrt = new BufferedWriter(new FileWriter(outFile))) {
			
			String line = null;
			while((line=rdr.readLine()) != null) {
							
				for (String dname : newNames.keySet()) {
					Map<String, String> refs = newNames.get(dname);
					
					for (String ref : refs.keySet()) {
				
						if (line.equals(ref) || line.equals("="+ref) || line.contains("/"+ref) || line.contains(ref+":") 
								|| line.contains("'"+ref+"'") || line.contains("\""+ref+"\"")) {
							
							line = swap(line, ref, dname, refs.get(ref), defaultNamespace);
														
							didUpdate = true;
						}
					}
				}
				
				wrt.write(line);
				wrt.newLine();
			}
		}
		
		if (outFile.getName().endsWith(".copy")) {
			if (didUpdate) {				
				System.out.println("======= saving changes for " + fileName);

				Files.move(FileSystems.getDefault().getPath(outFile.getAbsolutePath()), FileSystems.getDefault().getPath(inFile.getAbsolutePath()), StandardCopyOption.REPLACE_EXISTING);
			} else { 
				Files.delete(FileSystems.getDefault().getPath(outFile.getAbsolutePath()));
			}
		} else {
				// worked on original
			if (!didUpdate && !outDir.equals(srcDir) && !dontDelete) {
					
				// no change made					
				Files.delete(FileSystems.getDefault().getPath(outFile.getAbsolutePath()));
				
				if (outDir.list().length == 0) {
					outDir.delete();
				}
			}
		}
		
		return didUpdate;
	}
	
	private String swap(String line, String oldRef, String deployment, String newRef, String defaultNamespace) {
		
		if (this.name.equals(deployment) && this.hasContainer(oldRef)) {
				
			// container is hosted in the same deployment, thus same virtual host in k8s
				
			newRef = "localhost";
		} else if (newRef.endsWith("." + this.namespace) || (this.namespace == null && newRef.endsWith(defaultNamespace))) {
			
			// reference refers to a namespace, we can remove it as it is the same as the current deployment
			
			newRef = newRef.substring(0, newRef.lastIndexOf("."));
		}
		
		System.out.println("======= replacing ref " + oldRef + " for ref " + newRef);

		return line.replace(oldRef, newRef);
	}
}