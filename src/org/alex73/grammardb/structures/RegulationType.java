
package org.alex73.grammardb.structures;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for RegulationType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="RegulationType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="add"/>
 *     &lt;enumeration value="mistake"/>
 *     &lt;enumeration value="fantasy"/>
 *     &lt;enumeration value="undesirable"/>
 *     &lt;enumeration value="limited"/>
 *     &lt;enumeration value="rare"/>
 *     &lt;enumeration value="rare_branch"/>
 *     &lt;enumeration value="obscenism"/>
 *     &lt;enumeration value="invective"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "RegulationType")
@XmlEnum
public enum RegulationType {


    /**
     * Якія няма ў слоўніках, але мусяць быць
     * 
     */
    @XmlEnumValue("add")
    ADD("add"),

    /**
     * Памылковыя  ў асобных слоўніках: калашы+на
     * 
     */
    @XmlEnumValue("mistake")
    MISTAKE("mistake"),

    /**
     * Няісныя словы: трудава+ны
     * 
     */
    @XmlEnumValue("fantasy")
    FANTASY("fantasy"),

    /**
     * Якія не варта выкарыстоўваць: першынству+ючы
     * 
     */
    @XmlEnumValue("undesirable")
    UNDESIRABLE("undesirable"),

    /**
     * Якія варта рэгуляваць: пушкіні+ст
     * 
     */
    @XmlEnumValue("limited")
    LIMITED("limited"),

    /**
     * Рэдкія: вісо+н
     * 
     */
    @XmlEnumValue("rare")
    RARE("rare"),

    /**
     * Рэдкая галіновая тэрміналогія
     * 
     */
    @XmlEnumValue("rare_branch")
    RARE_BRANCH("rare_branch"),

    /**
     * Мацюкі(абсцэнізмы)
     * 
     */
    @XmlEnumValue("obscenism")
    OBSCENISM("obscenism"),

    /**
     * Лаянкі(інвектывы)
     * 
     */
    @XmlEnumValue("invective")
    INVECTIVE("invective");
    private final String value;

    RegulationType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static RegulationType fromValue(String v) {
        for (RegulationType c: RegulationType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
