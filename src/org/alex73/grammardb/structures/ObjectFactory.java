
package org.alex73.grammardb.structures;

import javax.xml.bind.annotation.XmlRegistry;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the org.alex73.grammardb.structures package. 
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


    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: org.alex73.grammardb.structures
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link Variant }
     * 
     */
    public Variant createVariant() {
        return new Variant();
    }

    /**
     * Create an instance of {@link Slounik }
     * 
     */
    public Slounik createSlounik() {
        return new Slounik();
    }

    /**
     * Create an instance of {@link Form }
     * 
     */
    public Form createForm() {
        return new Form();
    }

    /**
     * Create an instance of {@link Fan }
     * 
     */
    public Fan createFan() {
        return new Fan();
    }

    /**
     * Create an instance of {@link Paradigm }
     * 
     */
    public Paradigm createParadigm() {
        return new Paradigm();
    }

    /**
     * Create an instance of {@link Wordlist }
     * 
     */
    public Wordlist createWordlist() {
        return new Wordlist();
    }

}
