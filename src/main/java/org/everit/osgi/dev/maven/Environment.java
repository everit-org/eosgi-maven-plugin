package org.everit.osgi.dev.maven;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Copyright (c) 2011, Everit Kft.
 *
 * All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */

/**
 * The OSGI environment that is specified in the pom.xml for the plugin. *
 */
public class Environment {

	/**
	 * The id that will be used to identify this configuration in system
	 * property of the framework.
	 * 
	 * @parameter
	 */
	private String id;

	/**
	 * The name of the osgi framework. Currently equinox and felix is supported.
	 * Default is equinox.
	 * 
	 * @parameter
	 */
	private String framework;

	/**
	 * The JVM options that will be applied during starting the OSGI Container.
	 * 
	 * @parameter
	 */
	private List<String> vmOptions;

	/**
	 * The timeout in milliseconds after the Tests should stop for sure. Default
	 * value is five minutes.
	 * 
	 * @parameter
	 */
	private long timeout = 300000;

	/**
	 * System properties that will be added to the JVM of started OSGI
	 * container.
	 * 
	 * @parameter
	 */
	private Map<String, String> systemProperties = new HashMap<String, String>();

	public String getFramework() {
		return framework;
	}

	public String getId() {
		return id;
	}

	public long getTimeout() {
		return timeout;
	}
	
	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	public void setSystemProperties(Map<String, String> systemProperties) {
		this.systemProperties = systemProperties;
	}

	public Map<String, String> getSystemProperties() {
		return systemProperties;
	}

	public List<String> getVmOptions() {
		return vmOptions;
	}

	public void setFramework(final String framework) {
		this.framework = framework;
	}

	public void setId(final String id) {
		this.id = id;
	}

	public void setVmOptions(final List<String> vmOptions) {
		this.vmOptions = vmOptions;
	}

}
