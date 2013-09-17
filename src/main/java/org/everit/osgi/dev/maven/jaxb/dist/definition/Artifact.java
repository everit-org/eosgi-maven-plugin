package org.everit.osgi.dev.maven.jaxb.dist.definition;

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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

/**
 * <p>
 * Java class for Artifact complex type.
 * 
 * <p>
 * The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="Artifact">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="groupId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="artifactId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="version" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="type" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="classifier" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *       &lt;/sequence>
 *       &lt;attribute name="targetFolder" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="targetFile" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="start" type="{http://www.w3.org/2001/XMLSchema}boolean" default="true" />
 *       &lt;attribute name="startLevel" type="{http://www.w3.org/2001/XMLSchema}int" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Artifact", propOrder = {
        "groupId",
        "artifactId",
        "version",
        "type",
        "classifier"
})
public class Artifact {

    @XmlElement(required = true)
    protected String artifactId;
    protected String classifier;
    @XmlElement(required = true)
    protected String groupId;
    @XmlAttribute(name = "start")
    protected Boolean start;
    @XmlAttribute(name = "startLevel")
    protected Integer startLevel;
    @XmlAttribute(name = "targetFile")
    protected String targetFile;
    @XmlAttribute(name = "targetFolder")
    protected String targetFolder;
    protected String type;
    @XmlElement(required = true)
    protected String version;

    /**
     * Gets the value of the artifactId property.
     * 
     * @return possible object is {@link String }
     * 
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * Gets the value of the classifier property.
     * 
     * @return possible object is {@link String }
     * 
     */
    public String getClassifier() {
        return classifier;
    }

    /**
     * Gets the value of the groupId property.
     * 
     * @return possible object is {@link String }
     * 
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * Gets the value of the startLevel property.
     * 
     * @return possible object is {@link Integer }
     * 
     */
    public Integer getStartLevel() {
        return startLevel;
    }

    /**
     * Gets the value of the targetFile property.
     * 
     * @return possible object is {@link String }
     * 
     */
    public String getTargetFile() {
        return targetFile;
    }

    /**
     * Gets the value of the targetFolder property.
     * 
     * @return possible object is {@link String }
     * 
     */
    public String getTargetFolder() {
        return targetFolder;
    }

    /**
     * Gets the value of the type property.
     * 
     * @return possible object is {@link String }
     * 
     */
    public String getType() {
        return type;
    }

    /**
     * Gets the value of the version property.
     * 
     * @return possible object is {@link String }
     * 
     */
    public String getVersion() {
        return version;
    }

    /**
     * Gets the value of the start property.
     * 
     * @return possible object is {@link Boolean }
     * 
     */
    public boolean isStart() {
        if (start == null) {
            return true;
        } else {
            return start;
        }
    }

    /**
     * Sets the value of the artifactId property.
     * 
     * @param value
     *            allowed object is {@link String }
     * 
     */
    public void setArtifactId(final String value) {
        artifactId = value;
    }

    /**
     * Sets the value of the classifier property.
     * 
     * @param value
     *            allowed object is {@link String }
     * 
     */
    public void setClassifier(final String value) {
        classifier = value;
    }

    /**
     * Sets the value of the groupId property.
     * 
     * @param value
     *            allowed object is {@link String }
     * 
     */
    public void setGroupId(final String value) {
        groupId = value;
    }

    /**
     * Sets the value of the start property.
     * 
     * @param value
     *            allowed object is {@link Boolean }
     * 
     */
    public void setStart(final Boolean value) {
        start = value;
    }

    /**
     * Sets the value of the startLevel property.
     * 
     * @param value
     *            allowed object is {@link Integer }
     * 
     */
    public void setStartLevel(final Integer value) {
        startLevel = value;
    }

    /**
     * Sets the value of the targetFile property.
     * 
     * @param value
     *            allowed object is {@link String }
     * 
     */
    public void setTargetFile(final String value) {
        targetFile = value;
    }

    /**
     * Sets the value of the targetFolder property.
     * 
     * @param value
     *            allowed object is {@link String }
     * 
     */
    public void setTargetFolder(final String value) {
        targetFolder = value;
    }

    /**
     * Sets the value of the type property.
     * 
     * @param value
     *            allowed object is {@link String }
     * 
     */
    public void setType(final String value) {
        type = value;
    }

    /**
     * Sets the value of the version property.
     * 
     * @param value
     *            allowed object is {@link String }
     * 
     */
    public void setVersion(final String value) {
        version = value;
    }

}
