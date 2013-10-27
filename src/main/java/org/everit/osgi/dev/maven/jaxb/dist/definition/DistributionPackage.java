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
import javax.xml.bind.annotation.XmlType;

/**
 * <p>
 * Java class for DistributionPackage complex type.
 * 
 * <p>
 * The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DistributionPackage">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence minOccurs="0">
 *         &lt;element name="artifacts" type="{http://everit.org/eosgi/dist/definition/1.0.0}Artifacts" minOccurs="0"/>
 *         &lt;element name="parseables" type="{http://everit.org/eosgi/dist/definition/1.0.0}Parseables" minOccurs="0"/>
 *         &lt;element name="launchers" type="{http://everit.org/eosgi/dist/definition/1.0.0}Launchers" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DistributionPackage", propOrder = { "artifacts", "parseables", "launchers" })
public class DistributionPackage {

    protected Artifacts artifacts;
    protected Launchers launchers;
    protected Parseables parseables;

    /**
     * Gets the value of the artifacts property.
     * 
     * @return possible object is {@link Artifacts }
     * 
     */
    public Artifacts getArtifacts() {
        return artifacts;
    }

    /**
     * Gets the value of the launchers property.
     * 
     * @return possible object is {@link Launchers }
     * 
     */
    public Launchers getLaunchers() {
        return launchers;
    }

    /**
     * Gets the value of the parseables property.
     * 
     * @return possible object is {@link Parseables }
     * 
     */
    public Parseables getParseables() {
        return parseables;
    }

    /**
     * Sets the value of the artifacts property.
     * 
     * @param value
     *            allowed object is {@link Artifacts }
     * 
     */
    public void setArtifacts(final Artifacts value) {
        artifacts = value;
    }

    /**
     * Sets the value of the launchers property.
     * 
     * @param value
     *            allowed object is {@link Launchers }
     * 
     */
    public void setLaunchers(final Launchers value) {
        launchers = value;
    }

    /**
     * Sets the value of the parseables property.
     * 
     * @param value
     *            allowed object is {@link Parseables }
     * 
     */
    public void setParseables(final Parseables value) {
        parseables = value;
    }

}
