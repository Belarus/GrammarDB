
package org.alex73.grammardb.structures;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="Note" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{}Slounik" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{}Form" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="Morph" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{}Fan" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *       &lt;attribute name="id" type="{}latin_char" />
 *       &lt;attribute name="lemma" use="required" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="tag" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="slouniki" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="pravapis" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="type" type="{}VariantType" />
 *       &lt;attribute name="rules" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="regulation" type="{}RegulationType" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "note",
    "slounik",
    "form",
    "morph",
    "fan"
})
@XmlRootElement(name = "Variant")
public class Variant {

    @XmlElement(name = "Note")
    protected List<String> note;
    @XmlElement(name = "Slounik")
    protected List<Slounik> slounik;
    @XmlElement(name = "Form")
    protected List<Form> form;
    @XmlElement(name = "Morph")
    protected List<String> morph;
    @XmlElement(name = "Fan")
    protected List<Fan> fan;
    @XmlAttribute(name = "id")
    protected String id;
    @XmlAttribute(name = "lemma", required = true)
    protected String lemma;
    @XmlAttribute(name = "tag")
    protected String tag;
    @XmlAttribute(name = "slouniki")
    protected String slouniki;
    @XmlAttribute(name = "pravapis")
    protected String pravapis;
    @XmlAttribute(name = "type")
    protected VariantType type;
    @XmlAttribute(name = "rules")
    protected String rules;
    @XmlAttribute(name = "regulation")
    protected RegulationType regulation;

    /**
     * Gets the value of the note property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the note property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getNote().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getNote() {
        if (note == null) {
            note = new ArrayList<String>();
        }
        return this.note;
    }

    /**
     * Gets the value of the slounik property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the slounik property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getSlounik().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Slounik }
     * 
     * 
     */
    public List<Slounik> getSlounik() {
        if (slounik == null) {
            slounik = new ArrayList<Slounik>();
        }
        return this.slounik;
    }

    /**
     * Gets the value of the form property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the form property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getForm().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Form }
     * 
     * 
     */
    public List<Form> getForm() {
        if (form == null) {
            form = new ArrayList<Form>();
        }
        return this.form;
    }

    /**
     * Gets the value of the morph property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the morph property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getMorph().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getMorph() {
        if (morph == null) {
            morph = new ArrayList<String>();
        }
        return this.morph;
    }

    /**
     * Gets the value of the fan property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the fan property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getFan().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Fan }
     * 
     * 
     */
    public List<Fan> getFan() {
        if (fan == null) {
            fan = new ArrayList<Fan>();
        }
        return this.fan;
    }

    /**
     * Gets the value of the id property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the value of the id property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setId(String value) {
        this.id = value;
    }

    /**
     * Gets the value of the lemma property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getLemma() {
        return lemma;
    }

    /**
     * Sets the value of the lemma property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setLemma(String value) {
        this.lemma = value;
    }

    /**
     * Gets the value of the tag property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTag() {
        return tag;
    }

    /**
     * Sets the value of the tag property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTag(String value) {
        this.tag = value;
    }

    /**
     * Gets the value of the slouniki property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSlouniki() {
        return slouniki;
    }

    /**
     * Sets the value of the slouniki property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSlouniki(String value) {
        this.slouniki = value;
    }

    /**
     * Gets the value of the pravapis property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPravapis() {
        return pravapis;
    }

    /**
     * Sets the value of the pravapis property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPravapis(String value) {
        this.pravapis = value;
    }

    /**
     * Gets the value of the type property.
     * 
     * @return
     *     possible object is
     *     {@link VariantType }
     *     
     */
    public VariantType getType() {
        return type;
    }

    /**
     * Sets the value of the type property.
     * 
     * @param value
     *     allowed object is
     *     {@link VariantType }
     *     
     */
    public void setType(VariantType value) {
        this.type = value;
    }

    /**
     * Gets the value of the rules property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRules() {
        return rules;
    }

    /**
     * Sets the value of the rules property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRules(String value) {
        this.rules = value;
    }

    /**
     * Gets the value of the regulation property.
     * 
     * @return
     *     possible object is
     *     {@link RegulationType }
     *     
     */
    public RegulationType getRegulation() {
        return regulation;
    }

    /**
     * Sets the value of the regulation property.
     * 
     * @param value
     *     allowed object is
     *     {@link RegulationType }
     *     
     */
    public void setRegulation(RegulationType value) {
        this.regulation = value;
    }

}
