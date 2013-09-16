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

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the org.everit.osgi.dev.maven.jaxb.dist.definition package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {

    private final static QName _DistributionPackage_QNAME = new QName("http://everit.org/eosgi/dist/definition/1.0.0", "distributionPackage");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: org.everit.osgi.dev.maven.jaxb.dist.definition
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link DistributionPackage }
     * 
     */
    public DistributionPackage createDistributionPackage() {
        return new DistributionPackage();
    }

    /**
     * Create an instance of {@link Command }
     * 
     */
    public Command createCommand() {
        return new Command();
    }

    /**
     * Create an instance of {@link Artifacts }
     * 
     */
    public Artifacts createArtifacts() {
        return new Artifacts();
    }

    /**
     * Create an instance of {@link Artifact }
     * 
     */
    public Artifact createArtifact() {
        return new Artifact();
    }

    /**
     * Create an instance of {@link Launchers }
     * 
     */
    public Launchers createLaunchers() {
        return new Launchers();
    }

    /**
     * Create an instance of {@link Parseables }
     * 
     */
    public Parseables createParseables() {
        return new Parseables();
    }

    /**
     * Create an instance of {@link Launcher }
     * 
     */
    public Launcher createLauncher() {
        return new Launcher();
    }

    /**
     * Create an instance of {@link Parseable }
     * 
     */
    public Parseable createParseable() {
        return new Parseable();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link DistributionPackage }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://everit.org/eosgi/dist/definition/1.0.0", name = "distributionPackage")
    public JAXBElement<DistributionPackage> createDistributionPackage(DistributionPackage value) {
        return new JAXBElement<DistributionPackage>(_DistributionPackage_QNAME, DistributionPackage.class, null, value);
    }

}
