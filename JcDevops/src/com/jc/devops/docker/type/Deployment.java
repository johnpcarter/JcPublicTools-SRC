package com.jc.devops.docker.type;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
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
import com.jc.devops.docker.WebSocketContainerLogger;
import com.jc.devops.docker.type.BuildCommand.CommandType;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.NetworkConfig;
import com.spotify.docker.client.messages.NetworkCreation;
import com.wm.app.b2b.server.ServerAPI;
import com.wm.app.b2b.server.ServiceException;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class Deployment {
	
	public String name;
	public String appName;
	public String description;
	public String replicas;
	public String appSelector;
	public String hostName;
	public String type;
	public String serviceType;
	public String dependsOn;
	
	public API[] apis;
	public DockerContainer[] containers;
				
	private String _defaultDockerHost;
	private String _defaultDockerCert;
	
	List<DockerContainer> _runningContainers;

	private List<WakerDelegate> _wakers = new ArrayList<WakerDelegate>();
	private Runnable _runner;

	public interface WakerDelegate {
		public void deploymentsIsReader(Deployment deployment);
	}
	
	public Deployment(String dockerHost, String dockerCert, IData doc) {
		
		this(doc);
		this._defaultDockerHost = dockerHost;
		this._defaultDockerCert =  dockerCert;
		
		if (this.hostName != null && !this.hostName.startsWith("localhost"))
			this._defaultDockerHost = this.hostName;
	}
	
	public Deployment(IData doc) {
		
		IDataCursor c = doc.getCursor();
		this.name = IDataUtil.getString(c, "name");
		this.appName = IDataUtil.getString(c, "appName");
		this.description = IDataUtil.getString(c, "description");
		this.replicas = IDataUtil.getString(c, "replicas");
		this.appSelector = IDataUtil.getString(c, "appSelector");
		this.hostName = IDataUtil.getString(c, "hostName");
		this.type = IDataUtil.getString(c, "type");
		this.serviceType = IDataUtil.getString(c, "serviceType");
		this.dependsOn = IDataUtil.getString(c, "dependsOn");

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
				this.containers[i] = new  DockerContainer(containerDocs[i]);
			}
		}
	}
	
	public IData toIData() {
		
		IData d = IDataFactory.create();
		IDataCursor c = d.getCursor();
		
		IDataUtil.put(c, "name", this.name);
		IDataUtil.put(c, "description", this.description);
		IDataUtil.put(c, "appName", this.appName);
		IDataUtil.put(c, "replicas", this.replicas);
		IDataUtil.put(c, "appSelector", this.appSelector);
		IDataUtil.put(c, "hostName", this.appName);
		
		IDataUtil.put(c, "type", this.type);
		IDataUtil.put(c, "serviceType", this.serviceType);
		IDataUtil.put(c, "dependsOn", this.dependsOn);

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
	
	public Deployment updateContainerReferences(String appPrefix, String namespace, String buildDir, Deployment[] deployments, Build[] builds) {
			
		Map<String, String> names = new HashMap<String, String>();
		
		for (Deployment deployment : deployments) {
						
			for (DockerContainer c : deployment.containers) {
				
				names.put(c.name, deployment.name);
			}
		}
		
		for (DockerContainer c: this.containers) {
				
			System.out.println("checking container: " + c.name + ", active " + c.active);
			
			if (c.active) {
								
				for (String n : names.keySet()) {
					
					System.out.println("checking for name " + n);

					// check env vars

					if (c.env != null) {
						for (Arg a : c.env) {
							if (a.target != null && a.target.contains(n)) {
								
								a.target = a.target.replace(n, replacement(appPrefix, namespace, names.get(n)));
							}
						}
					}
					
					// check runtime build params

					if (c.build != null) {
						for (BuildCommand b : c.build.buildCommands.values()) {
							
							if (b.commandType == CommandType.file && b.fileType != null && b.fileType.equals("properties")) {
								updatePropertyFileForReferences(buildDir, buildDir, b.source, n, replacement(appPrefix, namespace, names.get(n)));
							}
						}
					}
					// if provided, check properties from install time and move them to runtime
					
					if (builds != null) {
						
						String installProperties = getInstallPropertiesForContainer(builds, c);
						
						if (installProperties != null && updatePropertyFileForReferences("./packages/JcDevopsConsole/resources/files/properties", buildDir, installProperties, n, replacement(appPrefix, namespace, names.get(n)))) {
							BuildCommand b = new BuildCommand(CommandType.file, "other", "resources/" + installProperties + ".properties", "/opt/softwareag/IntegrationServer/" + installProperties + "-ks8.properties");
							c.build.buildCommands.put(b.source, b);
							
							c.addEnvVariable("SAG_IS_CONFIG_PROPERTIES", b.target);
							
							if (c.build.context == null) {
								c.build.context = ".";
							}
						}
					}
				}
			}
		}
		
		return this;
	}
	
	private String replacement(String appPrefix, String namespace, String deploymentName) {
		
		if (deploymentName.equals(this.name)) {
			//container is referenced in same host, so we can simply replace it with localhost

			return "localhost";
		} else {
			
			// container is in different deployment, so we need to replace with the name of the service
			// which will be <prefix>-clusterip-<deployment-name>.<namespace>
			
			if (appPrefix != null)
				return appPrefix + "clusterip-" + deploymentName.toLowerCase().replace(" ", "-") + "." + namespace;
			else
				return "clusterip-" + deploymentName.toLowerCase().replace(" ", "-") + "." + namespace;
		}
	}
	
	public DockerClient getDockerClient() throws DockerCertificateException {
		
		return DockerConnectionUtil.createDockerClient(this._defaultDockerHost, this._defaultDockerCert);
	}
	
	public DockerClient run(DockerClient dockerClient, String composeName, String buildno, String containers) throws ServiceException {

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
								
		_runningContainers = new  ArrayList<DockerContainer>();
		
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
	
	private String getInstallPropertiesForContainer(Build[] builds, DockerContainer c) {
	
		BuildCommand props = null;
		
		for (Build b : builds) {
			
			if (b.targetImage.matches(c.image, true)) {
				props = b.fileForType(CommandType.file, "properties");
				break;
			}
		}
		
		return props != null ? props.source : null;
	}
	
	private boolean updatePropertyFileForReferences(String srcDir, String buildDir, String fileName, String ref, String newRef) {
		
		boolean update = false;
		File dir = new File(buildDir, "resources");
		
		File inFile;
		File outFile = new File(dir, fileName + ".properties");

		if (outFile.exists()) {
			// already started rebuild, work from already started
			
			inFile = outFile;
			outFile = new File(buildDir, fileName + ".properties.copy");
		} else {
			dir.mkdirs();
			inFile = new File(srcDir, fileName + ".properties");
		}
		
		try (BufferedReader rdr = new BufferedReader(new FileReader(inFile));
			 BufferedWriter wrt = new BufferedWriter(new FileWriter(outFile))	) {
			
			String line = null;
			while((line=rdr.readLine()) != null) {
				
				if (line.contains(ref)) {
					line = line.replace(ref, newRef);
					update = true;
				}
				
				wrt.write(line);
				wrt.newLine();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			if (!update && outFile.exists()) {
				Files.delete(FileSystems.getDefault().getPath(outFile.getAbsolutePath()));
			} else {
				// if copy move, to original name
				
				if (outFile.getName().endsWith("copy"))
					Files.move(FileSystems.getDefault().getPath(outFile.getAbsolutePath()), FileSystems.getDefault().getPath(new File(buildDir, fileName + ".properties").getAbsolutePath()), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return update;
	}
}