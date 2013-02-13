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
 * <p>Java class for Launcher complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="Launcher">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="startCommand" type="{http://everit.org/eosgi/dist/definition/1.0.0}Command"/>
 *         &lt;element name="killCommand" type="{http://everit.org/eosgi/dist/definition/1.0.0}Command"/>
 *       &lt;/sequence>
 *       &lt;attribute name="os">
 *         &lt;simpleType>
 *           &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *             &lt;enumeration value="windows"/>
 *             &lt;enumeration value="linux"/>
 *             &lt;enumeration value="mac"/>
 *             &lt;enumeration value="sunos"/>
 *           &lt;/restriction>
 *         &lt;/simpleType>
 *       &lt;/attribute>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Launcher", propOrder = {
    "startCommand",
    "killCommand"
})
public class Launcher {

    @XmlElement(required = true)
    protected Command startCommand;
    @XmlElement(required = true)
    protected Command killCommand;
    @XmlAttribute(name = "os")
    protected String os;

    /**
     * Gets the value of the startCommand property.
     * 
     * @return
     *     possible object is
     *     {@link Command }
     *     
     */
    public Command getStartCommand() {
        return startCommand;
    }

    /**
     * Sets the value of the startCommand property.
     * 
     * @param value
     *     allowed object is
     *     {@link Command }
     *     
     */
    public void setStartCommand(Command value) {
        this.startCommand = value;
    }

    /**
     * Gets the value of the killCommand property.
     * 
     * @return
     *     possible object is
     *     {@link Command }
     *     
     */
    public Command getKillCommand() {
        return killCommand;
    }

    /**
     * Sets the value of the killCommand property.
     * 
     * @param value
     *     allowed object is
     *     {@link Command }
     *     
     */
    public void setKillCommand(Command value) {
        this.killCommand = value;
    }

    /**
     * Gets the value of the os property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getOs() {
        return os;
    }

    /**
     * Sets the value of the os property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setOs(String value) {
        this.os = value;
    }

}
