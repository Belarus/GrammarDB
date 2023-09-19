
package org.alex73.grammardb.structures;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ParadigmOptions.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ParadigmOptions">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="usually_plurals"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "ParadigmOptions")
@XmlEnum
public enum ParadigmOptions {


    /**
     * Звычайна у множным ліку
     * 
     */
    @XmlEnumValue("usually_plurals")
    USUALLY_PLURALS("usually_plurals");
    private final String value;

    ParadigmOptions(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ParadigmOptions fromValue(String v) {
        for (ParadigmOptions c: ParadigmOptions.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
