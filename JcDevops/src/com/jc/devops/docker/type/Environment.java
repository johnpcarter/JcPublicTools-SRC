package com.jc.devops.docker.type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.PortBinding;
import com.spotify.docker.client.messages.Volume;
import com.spotify.docker.client.messages.HostConfig.Bind;
import com.spotify.docker.client.messages.HostConfig.Builder;
import com.spotify.docker.client.shaded.com.google.common.collect.ImmutableList;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class Environment {

	public String name;
	public Port[] ports;
	public Arg[] env;
	public Arg[] volumes;

	public Environment(IData doc) {

		IDataCursor c = doc.getCursor();
		this.name = IDataUtil.getString(c, "name");
		this.ports = DockerContainer.convertIDataArrayToPorts(IDataUtil.getIDataArray(c, "ports"));
		this.env = DockerContainer.convertIDataArrayToArgs(IDataUtil.getIDataArray(c, "env"));
		this.volumes = DockerContainer.convertIDataArrayToArgs(IDataUtil.getIDataArray(c, "volumes"));
		c.destroy();
	}
	
	public IData toIData() {
		
		IData out = IDataFactory.create();
		IDataCursor c = out.getCursor();
		
		IDataUtil.put(c, "name", this.name);
		
		List<IData> portsOut = new ArrayList<IData>();
		
		if (this.ports != null) {
			for (Port p : this.ports) {
				portsOut.add(p.toIData(true));
			}
		}
		
		IDataUtil.put(c, "ports",  portsOut.toArray(new IData[portsOut.size()]));

		List<IData> envOut = new ArrayList<IData>();
		
		if (this.env != null) {
			for (Arg v : this.env) {
				envOut.add(v.toIData());
			}
		}
		
		IDataUtil.put(c, "env",  envOut.toArray(new IData[envOut.size()]));
		
		List<IData> volumesOut = new ArrayList<IData>();
		
		if (this.volumes != null) {
			for (Arg v : this.volumes) {
				volumesOut.add(v.toIData(true));
			}
		}
		
		IDataUtil.put(c, "volumes",  volumesOut.toArray(new IData[volumesOut.size()]));
		
		return out;
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
	
	List<String> setupEnvList() {
		
		ArrayList<String> out = new ArrayList<>();
		
		if (this.env == null)
			return out;
		
		for (int i = 0; i < this.env.length; i++) {
		
			out.add(this.env[i].source + "=" + this.env[i].target);
		}
		
		return out;
	}

	Set<String> exposedPorts() {
	
		Set<String> out = new HashSet<String>();
		
		for (int i = 0; i < this.ports.length; i++) {
			  
			out.add(this.ports[i].internal);
		}
		
		return out;
	}
	
	Map<String, List<PortBinding>> setUpPointBindings() {
		
		final Map<String, List<PortBinding>> portBindings = new HashMap<>();
		
		for (int i = 0; i < this.ports.length; i++) {
			  
			if (this.ports[i].external != null)
				this.createPortBinding(portBindings, Integer.parseInt(this.ports[i].internal), Integer.parseInt(this.ports[i].external)); 
		}

		return portBindings;
	}
	
	void createPortBinding(Map<String, List<PortBinding>> portBindings, int from, int to) {
	    
	    
		List<PortBinding> hostPorts = new ArrayList<>();
	    
		hostPorts.add(PortBinding.of("0.0.0.0", to));
	    portBindings.put(String.valueOf(from), hostPorts);
	}
	
	void setupVolumeMounts(DockerClient dockerClient, String owner, String composeName, Builder build) throws DockerException, InterruptedException {
		
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
}
