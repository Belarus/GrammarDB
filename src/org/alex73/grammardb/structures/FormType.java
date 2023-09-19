
package org.alex73.grammardb.structures;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for FormType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="FormType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="nonstandard"/>
 *     &lt;enumeration value="potential"/>
 *     &lt;enumeration value="numeral"/>
 *     &lt;enumeration value="short"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "FormType")
@XmlEnum
public enum FormType {


    /**
     * Нестандартная
     * 
     */
    @XmlEnumValue("nonstandard")
    NONSTANDARD("nonstandard"),

    /**
     * Патэнцыйная
     * 
     */
    @XmlEnumValue("potential")
    POTENTIAL("potential"),

    /**
     * з ліч. 2, 3, 4
     * 
     */
    @XmlEnumValue("numeral")
    NUMERAL("numeral"),

    /**
     * кароткая форма (у прыметніках)
     * 
     */
    @XmlEnumValue("short")
    SHORT("short");
    private final String value;

    FormType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static FormType fromValue(String v) {
        for (FormType c: FormType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
