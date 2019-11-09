package com.jc.util.filter;

import java.io.File;
import java.io.FilenameFilter;

public class XmlFileNameFilter implements FilenameFilter
{
    public boolean accept(File dir, String name)
    {
        return name.endsWith(".xml");
    }

}
