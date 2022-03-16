package com.jc.devops.docker;

import java.util.HashMap;
import java.util.Map;

import com.jc.devops.docker.type.Deployment;
import com.jc.devops.docker.type.Deployment.WakerDelegate;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.wm.app.b2b.server.ServerAPI;
import com.wm.app.b2b.server.ServiceException;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataUtil;

public class DockerComposeImpl {
	
	public String name;
	public Deployment[] services;
	private Map<String, DockerClient> _dockerClients = new HashMap<String, DockerClient>();

	public DockerComposeImpl(String dockerHost, String dockerCert, IData doc, String stageName, String dirForPropertiesFiles) {
		
		IDataCursor c = doc.getCursor();
		if (IDataUtil.getString(c, "name") != null)
		name = IDataUtil.getString(c, "name").toLowerCase().replace(" ", "-");
		
		IData[] svcs = IDataUtil.getIDataArray(c, "services");
		c.destroy();

		if (stageName != null && stageName.length() > 0 && !stageName.equalsIgnoreCase("null"))
			name = stageName.replace(" ", "-").toLowerCase() + "_" + name;
						
		if (svcs != null) {
			this.services = new Deployment[svcs.length];
		
			for (int i=0; i < svcs.length; i++) {
				this.services[i] = new Deployment(dockerHost, dockerCert, svcs[i], dirForPropertiesFiles);
			}	
		} else {
			WebSocketContainerLogger.log("You haven't defined any services!");
		}
	}
	
	public void run(String buildno, String containers, String environment) throws ServiceException {
		
		int count = 0;
		
		for (int i = this.services.length-1; i >= 0;  i--) {
			
			WebSocketContainerLogger.log("Starting up service '" + this.services[i].name + "'");

			try {
				
				if (this.services[i].dependsOn != null) {
					
					Deployment dependsOnService = dependsOnDeployment(this.services[i].dependsOn);
					
					if (dependsOnService != null) {
						
						final Deployment svc = this.services[i];
						
						WakerDelegate waker = (deployment) -> {
							try {
								
								WebSocketContainerLogger.log("Can now start service '" + svc.name + "' dependent service is now ready: " + svc.dependsOn);

								this._run(svc, buildno, containers, environment);
							} catch (ServiceException e) {
								WebSocketContainerLogger.log("Failed to start service '" + svc.name + "' due to error: " + e.getMessage());

							} catch (DockerCertificateException e) {
								WebSocketContainerLogger.log("Failed to start service '" + svc.name + "', couldn't connect to docker host: " + e.getMessage());
							}
						};
						
						WebSocketContainerLogger.log("Waiting to start service '" + svc.name + "' dependent on: " + svc.dependsOn);

						dependsOnService.waitForWhenReady(getClientForService(svc), waker, 180000);
						
					} else {
						WebSocketContainerLogger.log("Cannot start service '" + this.services[i].name + "', dependent on non existant service " + this.services[i].dependsOn);
					}
				} else {
					this._run(this.services[i], buildno, containers, environment);
				}
				
				count += 1;
			} catch (ServiceException e) {
			
				WebSocketContainerLogger.log("Failed to start service '" + this.services[i].name + "' due to error: " + e.getMessage());

				ServerAPI.logError(e);
			} catch (DockerCertificateException e) {
				
				WebSocketContainerLogger.log("Failed to start service '" + this.services[i].name + "', couldn't connect to docker host: " + e.getMessage());

				ServerAPI.logError(e);
			}
		}
		
		if (count == 0) {
			WebSocketContainerLogger.log("Failed to start any services, woops");

			throw new ServiceException("Failed to start any services, all containtainers failed");
		}
	}
		
	private void _run(Deployment service, String buildno, String containers, String environment) throws ServiceException, DockerCertificateException {
		
		service.run(getClientForService(service), this.name, buildno, containers, environment);
	}
	
	private DockerClient getClientForService(Deployment service) throws DockerCertificateException {
		
		DockerClient client = _dockerClients.get(service.hostName != null ? service.hostName : "default");

		if (client == null) {
			client = service.getDockerClient();
			_dockerClients.put(service.hostName != null ? service.hostName : "default", client);
		}
		
		return client;
	}
	
	public void stop() throws DockerException, InterruptedException, DockerCertificateException {
		
		Deployment lastService = null;
		DockerClient dockerClient = null;//new DefaultDockerClient("unix:///var/run/docker.sock");
		
		for (int i = 0; i < this.services.length;  i++) {
			
			if (lastService != null && isNotSameHost(lastService, this.services[i])) {
				
				System.out.println("Removing network " + lastService.hostName + " / " + this.services[i].hostName);
				
				dockerClient.removeNetwork(name);
			}
			
			WebSocketContainerLogger.log("Stopping Service '" + this.services[i].name + "'");

			dockerClient = this.services[i].stop();
			lastService = this.services[i];
		}	
		
		if (lastService != null)
			dockerClient.removeNetwork(name);
	}
	
	private Deployment dependsOnDeployment(String name) {
		
		Deployment found = null;
		
		for (int i = 0; i < this.services.length; i++) {
			if (this.services[i].name.equals(name)) {
				found = this.services[i];
				break;
			}
		}
		
		return found;
	}
	
	private boolean isNotSameHost(Deployment svc1, Deployment svc2) {
		
		String host1 = "local";
		String host2 = "local";
		
		if (svc1 != null && svc1.hostName != null)
			host1 = svc1.hostName;
		
		if (svc2 != null && svc2.hostName != null)
			host2 = svc2.hostName;
		
		return !host1.equals(host2);
	}
}