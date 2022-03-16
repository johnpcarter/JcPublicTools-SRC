package com.jc.wm.introspection;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wm.app.b2b.client.ServiceException;
import com.wm.app.b2b.server.ServerAPI;
import com.wm.data.IData;

public class PackageIntrospector {

	private static Map<String, PackageIntrospector> _sources =  new HashMap<String, PackageIntrospector>();
	
	private  Map<String, PackageInfo> _packages = new HashMap<String, PackageInfo>();

	
	public static boolean haveInstance(String name) {
		
		return _sources.get(name) !=  null;
	}

	public static PackageIntrospector defaultInstance(String name) {
		return _sources.get(name);
	}
	
	public static PackageIntrospector defaultInstance(String name, String baseDir, Map<String, String> repositories, boolean reload) {
		
		PackageIntrospector p = _sources.get(name == null ? "default" : name);
		
		if (p == null || reload) {
			p = new PackageIntrospector(baseDir, "^(?!Wm).*", repositories);
			_sources.put(name, p);
		}
		
		return p;
	}
	
	public PackageIntrospector(String baseDir, String excludePattern, Map<String, String> repositories) {
		
		this.scan(baseDir, repositories, excludePattern);
	}
	
	public String[] packages() {
	
		return this._packages.keySet().toArray(new String[this._packages.size()]);
	}
	
	public IData[] packageDetails() {
		
		List<IData> out = new ArrayList<IData>();
		
		this._packages.keySet().forEach((k) -> {
			
			out.add(this._packages.get(k).toIData(false, true, null));
		});
		
		return out.toArray(new IData[out.size()]);
	}
	
	public String[] servicesForPackage(String packageName) {
		
		PackageInfo p = this._packages.get(packageName);
		ArrayList<String> services = new ArrayList<String>();
		
		if (p != null) {
			p.getServices().forEach((s) -> {
				services.add(s.getName());
			});
		}
		
		return services.toArray(new String[services.size()]);
	}
	
	public String[] apiSummary() {
		
		List<String> apis = new ArrayList<String>();
		
		this._packages.values().forEach((p) -> {
			
			p.getAPIs().forEach((a) -> {
				apis.add(a.getName());
			});
		});
		
		return apis.toArray(new String[apis.size()]);
	}
	
	public APIInfo[] apis() {
		
		List<APIInfo> apis = new ArrayList<APIInfo>();
		
		this._packages.values().forEach((p) -> {
			
			p.getAPIs().forEach((a) -> {
				apis.add(a);
			});
		});
		
		return apis.toArray(new APIInfo[apis.size()]);
	}
	
	public String[] apiReferencesForPackage(String packageName) {
		
		PackageInfo p = this._packages.get(packageName);
		ArrayList<String> apis = new ArrayList<String>();
		
		if (p != null) {
			p.getAPIs().forEach((s) -> {
				apis.add(s.getName());
			});
		}
		
		return apis.toArray(new String[apis.size()]);
	}
	
	public IData[] apiDetailsForPackage(String packageName) {
		
		PackageInfo p = this._packages.get(packageName);
		ArrayList<IData> apis = new ArrayList<IData>();
		
		if (p != null) {
			p.getAPIs().forEach((s) -> {
				apis.add(s.toIData());
			});
		}
		
		return apis.toArray(new IData[apis.size()]);
	}

	public IData[] allPackages() {
	
		List<IData> l = new ArrayList<IData>();
		
		this._packages.values().forEach((p) -> {
			l.add(p.toIData(true, false, null));
		});
		
		return l.toArray(new IData[l.size()]);
	}
	
	public PackageInfo packageInfo(String packageName) {
	
		return this._packages.get(packageName);
	}
	
	public List<IData> dependencies(String[] packageNames) {
	
		List<IData> results =  new ArrayList<IData>();
		List<PackageInfo> exclude =  new ArrayList<PackageInfo>();
		
		for (int i = 0; i < packageNames.length; i++) {
			exclude.add(_packages.get(packageNames[i]));
		}
		
		for (int i = 0; i < packageNames.length; i++) {
			
			PackageInfo p = _packages.get(packageNames[i]);
			
			if (p != null) {
				List<PackageInfo> r = p.getDependencies(exclude);
					
				if (r != null) {
					r.forEach((rp) -> {
						results.add(rp.toIData(false, true, null));
					});
						
					exclude.addAll(r);
				}
			}
		}
		
		return results;
	}
	
	private void scan(String rootDir, Map<String, String> repositories, String excludePattern) {
	
		if (repositories == null || repositories.size() == 0) {
			repositories = new HashMap<String, String>();
			repositories.put("default", "-");
		}
		
		for(String repo : repositories.keySet()) {
			
			String[] packages = new String[1];

			File baseDir = new File(rootDir);
			
			if (repositories.get(repo).equals("-")) {
				
				// no repo, directory is the package dir
				
					packages = baseDir.list();
				
			} else if (!repositories.get(repo).equals(".")) {
				
				// repo based sub-directory, so we need to add repo into path as well the path into the packages folder indicated for repo
				
					baseDir = new File(new File(baseDir, repo), repositories.get(repo));
				
					packages = baseDir.list();
			} else { 
				
				// directory is a single package
				
				packages[0] = repo;
			}
			
			this.scanRepoDir(repo, baseDir, packages, excludePattern);
		}
		
		// now index each package
		
		this._packages.values().forEach((p) -> {
			p.index(_packages);
		});
	}
	
	private void scanRepoDir(String repo, File rootDir, String[] packages, String excludePattern) {
		
		if (packages == null)
			return;
		
		for (int i = 0; i < packages.length; i++) {
			
			try {
				if (!packages[i].equals("Default") && (excludePattern == null || packages[i].matches(excludePattern)))
					
					if (new File(rootDir, packages[i]).isDirectory())
						_packages.put(packages[i], new PackageInfo(repo, packages[i], rootDir.getAbsolutePath()));
				
			} catch (ServiceException e) {
				ServerAPI.logError(new ServiceException("Cannot introspect package " + packages[i] + " due to exception: " + e.getLocalizedMessage()));
			}
		}
	}
}
