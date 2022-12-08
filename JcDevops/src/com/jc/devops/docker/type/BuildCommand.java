package com.jc.devops.docker.type;

import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;


public class BuildCommand {

	public enum CommandType {
		entrypoint,
		healthcheck,
		env,
		copy,
		add,
		file,
		run
	}
		
	public CommandType commandType;
	public String fileType;
	public String source;
	public String target;
	
	public BuildCommand(CommandType type, String fileType, String source, String target) {
		
		this.commandType = type;
		this.fileType = fileType;
		this.source = source;
		this.target = target;
	}
	
	public BuildCommand(IData data) {
		
		IDataCursor c = data.getCursor();
		
		String cType = IDataUtil.getString(c, "commandType");
		
		if (cType.equalsIgnoreCase("env"))
			commandType = CommandType.env;
		else if (cType.equalsIgnoreCase("run"))
			commandType = CommandType.run;
		else if (cType.equalsIgnoreCase("file"))
			commandType = CommandType.file;
		else if (cType.equalsIgnoreCase("copy"))
			commandType = CommandType.copy;
		else if (cType.equalsIgnoreCase("add"))
			commandType = CommandType.add;
		else if (cType.equalsIgnoreCase("entrypoint"))
			commandType = CommandType.entrypoint;
		else if (cType.equalsIgnoreCase("healthcheck"))
			commandType = CommandType.healthcheck;
		else
			throw new RuntimeException("Invalid command type " + cType);
		
		if (IDataUtil.getString(c, "fileType") != null)
			fileType = IDataUtil.getString(c, "fileType").toLowerCase();
		
		if (IDataUtil.getString(c, "source") != null)
			source = IDataUtil.getString(c, "source");
		
		if (IDataUtil.getString(c, "target") != null)
			target = IDataUtil.getString(c, "target");

		c.destroy();
	}
	
	public IData toIData() {
		
		IData d = IDataFactory.create();
		IDataCursor c = d.getCursor();
		
		if (commandType != null)
			IDataUtil.put(c, "commandType", commandType.toString());
		else 
			IDataUtil.put(c, "commandType", CommandType.file.toString());

		IDataUtil.put(c, "fileType", fileType);
		IDataUtil.put(c, "source", source);
		IDataUtil.put(c, "target", target);

		c.destroy();
		
		return d;
	}
}