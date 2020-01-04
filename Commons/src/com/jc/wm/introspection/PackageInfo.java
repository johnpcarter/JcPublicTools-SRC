package com.jc.wm.introspection;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.jc.wm.introspection.APIInfo.Method;
import com.jc.wm.introspection.APIInfo.Resource;
import com.wm.app.b2b.client.ServiceException;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;
import com.wm.util.coder.XMLCoder;


public class PackageInfo {
		
	public enum TestStatus {
		none,
		success,
		failed,
		todo,
		running
	}
	
	private String _name;
	private String _description;
	private String _version;
	private String _startup;
	private String _shutdown;
	private String _build;
	private boolean _isSystem;
	private TestStatus _testStatus;
	
	private Map<String, ServiceInfo> _services = new HashMap<String, ServiceInfo>();
	private Map<String, APIInfo> _apis = new HashMap<String, APIInfo>();
 
	public PackageInfo(String name) {
		this._name = name;
	}
		
	public PackageInfo(String name, String baseDir) throws ServiceException {
		
		this._name = name;
		this.recordServices(baseDir);
							
		if (new File(new File(new File(baseDir, name), "resources"), "test-suite.json").exists()) {
			this._testStatus = TestStatus.todo;
			
			if (new File(new File(new File(new File(baseDir, name), "resources"), "results"), "okay.txt").exists()) 
				this._testStatus = TestStatus.success;
			else if (new File(new File(new File(new File(baseDir, name), "resources"), "results"), "failed.txt").exists()) 
				this._testStatus = TestStatus.failed;
			else if (new File(new File(new File(new File(baseDir, name), "resources"), "results"), "running.txt").exists()) 
				this._testStatus = TestStatus.running;
			
		} else {
			this._testStatus = TestStatus.none;
		}
		
		try {
			byte[] bytes = Files.readAllBytes(FileSystems.getDefault().getPath(baseDir, name,"manifest.v3"));
			
			 XMLCoder coder = new XMLCoder();
	         IData doc = coder.decodeFromBytes(bytes);
	        
	         if (doc !=  null) {
	        	 IDataCursor c = doc.getCursor();
		     
		         this._version = IDataUtil.getString(c, "version");
		         this._startup = IDataUtil.getString(c, "startup");
		         this._shutdown = IDataUtil.getString(c, "shutdown");
		         this._build = IDataUtil.getString(c, "build");
		         this._description = IDataUtil.getString(c, "description");
		         this._isSystem = IDataUtil.getString(c, "system_package").equals("yes") ? true : false;

		         c.destroy();
	         } else {
	        	 System.out.println("manifest is empty for :" + name);
	         }
		} catch (IOException e) {
        	 System.out.println("manifest is invalid: " + e.getLocalizedMessage());
		}
	}

	@Override
	public boolean equals(Object obj) {
		
		if (obj instanceof PackageInfo) {
			return ((PackageInfo) obj)._name != null ? ((PackageInfo) obj)._name.equals(this._name) : this._name == null;
		} else {
			return false;
		}
	}
	
	public String getName() {
		return _name;
	}
	
	public boolean isValid() {
		return _services.size() > 0;
	}
	
	public boolean hasTestCases() {
		return _testStatus != TestStatus.none;
	}
	
	public TestStatus testCases() {
		return _testStatus;
	}
	
	public Collection<APIInfo> getAPIs() {
		return _apis.values();
	}
	
	public Collection<ServiceInfo> getServices() {
		return _services.values();
	}
	
	public ServiceInfo getService(String name) {
		
		return _services.get(name);
	}
	
	public IData toIData(boolean includeServices, boolean includeRequires, List<PackageInfo> exclude) {
		
		IData[] apisDocArray = new IData[_apis.size()];
		String[] servicesDocArray = new String[_services.size()];
		
		int i = 0;
		Iterator<APIInfo> apisIterator = this._apis.values().iterator();
		while(apisIterator.hasNext()) {
			apisDocArray[i++] = apisIterator.next().toIData();
		}
		
		i = 0;
		Iterator<ServiceInfo> servicesIterator = this._services.values().iterator();
		while(servicesIterator.hasNext()) {
			servicesDocArray[i++] = servicesIterator.next().getName();
		}
		
		IData d = IDataFactory.create();
		IDataCursor c = d.getCursor();
		IDataUtil.put(c, "name", this._name);
		IDataUtil.put(c, "version", this._version);
		IDataUtil.put(c, "build", this._build);
		IDataUtil.put(c, "description", this._description);
		IDataUtil.put(c, "system", this._isSystem);
		IDataUtil.put(c, "startup", this._startup);
		IDataUtil.put(c, "shutdown", this._shutdown);
		IDataUtil.put(c, "testStatus", "" + this._testStatus);
		
		IDataUtil.put(c, "apis", apisDocArray);
		
		if (includeServices)
			IDataUtil.put(c, "services", servicesDocArray);

		if (includeRequires) {
			List<PackageInfo> depends = this.getDependencies();
			IData[] requiresDocArray = new IData[depends.size()];
			Iterator<PackageInfo> dependsIterator = depends.iterator();
			
			i = 0 ;
			while(dependsIterator.hasNext()) {
				PackageInfo next = dependsIterator.next();
				if (exclude == null || !exclude.contains(next))
					requiresDocArray[i++] = next.toIData(false, false, exclude);
			}
			
			IDataUtil.put(c, "requires", requiresDocArray);
		}
		
		c.destroy();
		
		return d;
	}

	public List<PackageInfo> getDependencies() {
		
		return getDependencies(new ArrayList<PackageInfo>());
	}
	
	public List<PackageInfo> getDependencies(List<PackageInfo> excludePackages) {
		
		ArrayList<PackageInfo> pckgs = new ArrayList<PackageInfo>();
		
		this._getDependencies(pckgs, excludePackages);
		
		return pckgs;
	}
	
	protected void _getDependencies(ArrayList<PackageInfo> pckgs, List<PackageInfo> excludePackages) {
		
		_services.keySet().forEach((k) -> {
			
			ServiceInfo svc = _services.get(k);
			
			svc.getDependencies(pckgs, excludePackages);
		});
	}
	
	public void index(Map<String, PackageInfo> packages) {
		
		_services.keySet().forEach((k) -> {
			
			ServiceInfo svc = _services.get(k);
			
			if (!svc.isIndexed())
				svc.index(packages);
		});
	}
	
	private void recordServices(String baseDir) throws ServiceException {
		
		File dir = new File(new File(baseDir, this._name), "ns");
		
		List<String> files = listf(dir, null);
		
		if (files != null) {
								
			for (int i = 0; i < files.size(); i++) {
			
				String file = files.get(i);
			
				if (file.endsWith("node.ndf")) {
					processNodeFile(baseDir, file);
				} else if (file.endsWith("flow.xml")) {
					processFlowService(baseDir, file);
				} else if (file.endsWith("java.frag")) {
					processOther(baseDir, file);
				} else {
					//processOther(baseDir, file);
				}
			}
		}
	}
	
	private void processFlowService(String baseDir, String serviceFile) throws ServiceException {
		
		ServiceInfo svc = new ServiceInfo(this._name, convertFileToServiceName(baseDir, serviceFile));
					 
		String[] lines = findMatchingLinesInFile(serviceFile, "<INVOKE ");
			 								     
		for (int i=0; i< lines.length; i++) {
				 
				 String line = lines[i];
				 						 
				 int o = line.indexOf("SERVICE=\"");
				 line = line.substring(o+9);
				 line = line.substring(0, line.indexOf(("\"")));
				 
				 svc.depends.add(new ServiceInfo(line));						 
		}
			 
		_services.put(svc.getName(), svc);
	}
	
	private void processNodeFile(String baseDir, String nodeFile) throws ServiceException {
		
		//System.out.println("Processing node file " + nodeFile);

		String[] lines = findMatchingLinesInFile(nodeFile, ">restv2Descriptor</");

		if (lines.length > 0) {
				 
			String[] titles = findMatchingLinesInFile(nodeFile, "\"title\">");
			String[] versions = findMatchingLinesInFile(nodeFile, "\"appVersion\">");
			String[] basePath = findMatchingLinesInFile(nodeFile, "\"basePath\">");
			String[] resourcePaths = findMatchingLinesInFile(nodeFile, "<value name=\"nsName\">");
			
			APIInfo api = new APIInfo(this._name, stripXMLTags(titles[0]), stripXMLTags(basePath[0]), stripXMLTags(versions[0]), processResourcePath(resourcePaths));
				 
			_apis.put(api.getName(), api);
		}
	}
	
	private Resource[] processResourcePath(String[] resourcePaths) {
		
		Map<String, Resource> resources = new HashMap<String, Resource>();
		
		for (int i= 0; i < resourcePaths.length; i++) {
			
			Resource r = null;
			String rp = stripXMLTags(resourcePaths[i]);
			
			if (rp.endsWith("_GET") || rp.endsWith("_POST") || rp.endsWith("_PUT") || rp.endsWith("_DELETE") || rp.endsWith("_PATCH")) {
				
				int split = rp.lastIndexOf("_");
				String resourceName = rp.substring(0, split);
				String resourceMethod = rp.substring(split+1);
				
				if ((r = resources.get(resourceName)) == null) {
					r = new Resource();
					r.resource = resourceName;
					r.methods = new Method[1];
					r.methods[0] = Method.valueOf(resourceMethod);
					
					resources.put(resourceName, r);
				} else {
					Method[] copy = new Method[r.methods.length+1];
					
					for (int z = 0; z < r.methods.length; z++) {
						copy[z] = r.methods[z];
					}
					
					copy[r.methods.length] = Method.valueOf(resourceMethod);
				}
			}	
		}
		
		return resources.values().toArray(new Resource[resources.size()]);
	}
	
	private void processOther(String baseDir, String file) throws ServiceException {
				
		ServiceInfo svc = new ServiceInfo(this._name, convertFileToServiceName(baseDir, file));
		_services.put(svc.getName(), svc);
	}

	private static List<String> listf(File directory, String filter) {
		
	    List<String> resultList = new ArrayList<String>();

	    // get all the files from a directory
	    File[] fList = directory.listFiles();
	    
	    if (fList != null) {
	    	for (File file : fList) {
	    	
	    		if (file.isFile() && (filter == null || file.getName().matches(filter))) {
	    				        			
	    			resultList.add(file.getAbsolutePath());
	    	
	    		} else if (file.isDirectory()) {
	    			resultList.addAll(listf(file.getAbsoluteFile(), filter));
	    		}
	    	}
	    
	    	return resultList;
	    } else {	        	
	    	return null;
	    }
	}

	private static String[] findMatchingLinesInFile(String filePath, String str) throws ServiceException {
		
		ArrayList<String> outArray = new ArrayList<String>();
				
		try {
			BufferedReader br = new BufferedReader(new FileReader(filePath));
	        try {
	        	String line = null;
	        	
	            while((line = br.readLine()) != null)
	            {
	            	if (line.contains(str)) {
	            		outArray.add(line);
	            	}	
	            }
	            br.close();
	        } catch (IOException e) {
	            throw new ServiceException(e);
	        }
	    } catch (FileNotFoundException e) {
	        throw new ServiceException(e);
	    }
		
		return outArray.toArray(new String[outArray.size()]);
	}

	private static String stripXMLTags(String tag) {
		
		int start = tag.indexOf(">");
		int end = tag.lastIndexOf("<");
		
		return tag.substring(start+1, end);
	}
	
	private static String convertFileToServiceName(String baseDir, String file) {
		
		if (file.indexOf(baseDir) != -1) {
			file = file.substring(file.indexOf(baseDir)+baseDir.length());
		}
		
		int i = file.indexOf("ns");
		
		if (i != -1) {
			
			file = file.substring(i+3);
			
			int l = file.lastIndexOf(".");
			
			if (l != -1) {

				file = file.substring(0, l);
				
				file = file.replace("/", ".");

				l = file.lastIndexOf(".");
				
				if (l != -1) {
					
					file = file.substring(0, l);
					
					l = file.lastIndexOf(".");
					
					if (l != -1)
						file = file.substring(0, l) + ":" + file.substring(l+1);
				}
			}
			
			return file;
		} else {
			return file;
		}
	}
}