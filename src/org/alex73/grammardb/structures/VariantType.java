
package org.alex73.grammardb.structures;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VariantType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VariantType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="nonstandard"/>
 *     &lt;enumeration value="potential"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "VariantType")
@XmlEnum
public enum VariantType {


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
    POTENTIAL("potential");
    private final String value;

    VariantType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VariantType fromValue(String v) {
        for (VariantType c: VariantType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
