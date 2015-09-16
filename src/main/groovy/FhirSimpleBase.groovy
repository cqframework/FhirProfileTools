/*
        Copyright (C) 2014 The MITRE Corporation. All Rights Reserved.

 The program is provided "as is" without any warranty express or implied, including
 the warranty of non-infringement and the implied warranties of merchantability and
 fitness for a particular purpose.  The Copyright owner will not be liable for any
 damages suffered by you as a result of using the Program.  In no event will the
 Copyright owner be liable for any special, indirect or consequential damages or
 lost profits even if the Copyright owner has been advised of the possibility of
 their occurrence.
*/
import groovy.transform.TypeChecked
import org.apache.commons.io.IOUtils
import org.apache.commons.lang.StringUtils
import org.hl7.fhir.instance.formats.XmlParser
import org.hl7.fhir.instance.model.ElementDefinition
import org.hl7.fhir.instance.model.ElementDefinition.ElementDefinitionBindingComponent
import org.hl7.fhir.instance.model.ElementDefinition.TypeRefComponent

import org.hl7.fhir.instance.model.StructureDefinition
import org.hl7.fhir.instance.model.Resource
import org.hl7.fhir.instance.model.UriType

/**
 * Base set of common FHIR helper methods
 * Created by Jason Mathews on 2/5/2015.
 */
class FhirSimpleBase {

  protected final XmlParser xmlParser = new XmlParser(true)

  public final File publishDir

  StructureDefinition target

  static boolean devMode

  // ---------------------------------------------------------------------------------------------------------------------

  FhirSimpleBase(File publishDir) {
    this.publishDir = publishDir
  }

  @TypeChecked
  static String getResourceName(StructureDefinition profile) {
    // previously the "type" child in Profile has the base resource name but no more as of 3/2/2005 change
    /*
    if (profile.hasType()) {
      def t = profile.getType()
      printf "XX: getResource:: sys=%s djs=%s%n", t.getSystem(), t.getDisplay()
      //return t
      //return profile.getType()
    }
    <StructureDefinition xmlns="http://hl7.org/fhir">
     <id value="condition-daf-dafcondition"/>
     <type value="constraint"/>
     <abstract value="false"/>
     <base value="http://hl7.org/fhir/StructureDefinition/Condition"/>
     <snapshot>
      <element>
        <path value="Condition"/> ***
        <name value="DAFCondition"/>
        ...
    */
    List<ElementDefinition> snapshot = profile.hasSnapshot() && profile.getSnapshot().hasElement() ? profile.getSnapshot().getElement() : null
    if (snapshot) {
      // first element in snapshot should be resource name
      def eltDef = snapshot.get(0)
      if (eltDef.hasPath()) {
        def path = eltDef.getPath()
        // printf "XX: getResource:: %s%n", t
        return path
      }
    }
    List<ElementDefinition> diff = profile.hasDifferential() && profile.getDifferential().hasElement() ? profile.getDifferential().getElement() : null
    if (diff) {
      // first element in diff should be resource name otherwise the prefix before "." will be the resource name
      def eltDef = diff.get(0)
      if (eltDef.hasPath()) {
        def path = eltDef.getPath()
        int ind = path.indexOf('.')
        if (ind > 0) path = path.substring(0, ind)
        // printf "XX: getResource:: %s%n", t
        return path
      }
    }
    // println "X: getResourceName id=" + profile.getId()
    String resourceName = profile.getBase() // e.g. http://hl7.org/fhir/Profile/Observation
    if (resourceName == null) throw new IllegalStateException()
    int ind = resourceName.lastIndexOf('/')
    if (ind > 0) resourceName = resourceName.substring(ind + 1)
    return resourceName
  }

  static List<String> typeShortList(List<TypeRefComponent> list) {
    return typeShortList(list, true)
  }

  @TypeChecked
  static List<String> typeShortList(List<TypeRefComponent> list, boolean useRefPrefix) {
    if (!list) return Collections.emptyList()
    StringBuilder sb = new StringBuilder()
    def strList = new ArrayList<String>()
    // Reference(Practitioner | RelatedPerson)
    int refCount = 0
    list.each { type ->
    //for(Iterator<TypeRefComponent>it = list.iterator(); it.hasNext(); ) {
      //def type = it.next()
      if (type.hasCode() && type.getCode() == 'Reference' && type.hasProfile()) {
        if (sb.length() == 0) { // first
          if(useRefPrefix) sb.append('Reference(')
        } else sb.append(" | ")
        def profile = getProfile(type)
        // was http://hl7.org/fhir/Profile/
        if (profile.startsWith('http://hl7.org/fhir/StructureDefinition/'))
          profile = profile.substring(profile.lastIndexOf('/') + 1)
        sb.append(profile)
        refCount++
      }
    }
    if (sb.length() != 0) {
      if(useRefPrefix) sb.append(')')
      strList.add(sb.toString())
      sb.setLength(0)
      if (refCount == list.size()) return strList // all types are refs
    }
    list.each { type ->
      if (type.hasCode()) {
        String code = type.getCode()
        if (code != 'Reference') {
          if (code == '*') code = 'Any'
          strList.add(code)
        }
      } else if (type.hasProfile()) {
        def profile = getProfile(type) // .getProfile()
        if (profile.startsWith('http://hl7.org/fhir/StructureDefinition/'))
          profile = profile.substring(profile.lastIndexOf('/') + 1)
        strList.add(profile)
      }
    }
    return strList
  }

  @TypeChecked
  static String getProfile(TypeRefComponent type) {
    // NOTE: return type in getProfile() changed after May2015 from String to List<UriType> (fix)
    for (UriType item : type.getProfile()) {
      String val = item.getValueAsString()
      if (StringUtils.isNotBlank(val)) return val
    }
    return ''
  }

  @TypeChecked
  static String getTypeCode(List<TypeRefComponent> list, boolean useRefPrefix) {
    List<String> typeShortList = typeShortList(list, useRefPrefix);
    return typeShortList.isEmpty() ? '' : typeShortList.size() < 1 ? typeShortList.get(0) : typeShortList.join(' | ')
  }

  /**
   * Find target element in collection by path and name if extension or path-only if non-extension element.
   *
   * @param elementDefinitions
   * @param target
   * @return
   */
  @TypeChecked
  static ElementDefinition getElementByName(List<ElementDefinition> elementDefinitions, ElementDefinition target) {
    /*
    <element>
      <path value="DiagnosticOrder.identifier"/>
      <slicing>
      <discriminator value="identifier.label"/>
      <ordered value="true"/>
      <rules value="closed"/>
      </slicing>
    </element>

    <element>
      <path value="DiagnosticOrder.identifier"/>
      <name value="USLabDOPlacerID"/>
      <synonym value="Placer ID"/>
      <min value="1"/>
      <max value="1"/>
      <type>
      <code value="Identifier"/>
      </type>
      <mustSupport value="true"/>
    </element>

    <element>
        <path value="Patient.extension"/> ***
        <name value="race"/>              ***
        <type>
            <code value="Extension"/>
            <profile value="http://hl7.org/fhir/ExtensionDefinition/us-core-race"/>
        </type>
    </element>
    */
      final String path = target.hasPath() ? target.getPath() : ''
      final String name = target.hasName() ? target.getName() : ''
      return getElementByName(elementDefinitions, path, name)
  }

  @TypeChecked
  static ElementDefinition getElementByName(List<ElementDefinition> elementDefs, String path, String name) {
    if (name == null) name = ''
    for (Iterator<ElementDefinition> it = elementDefs.iterator(); it.hasNext();) {
      ElementDefinition elt = it.next()
      // find by path and name
      if (elt.hasPath() && elt.getPath() == path) {
        if (elt.hasName()) {
          if (!name.isEmpty() && elt.getName() == name) return elt
        } else {
          if (name.isEmpty()) return elt
        }
      }
    }
    return null // not found
  }

  @TypeChecked
  static ElementDefinition getExtensionByName(List<ElementDefinition> elementDefs, String name) {
    for (Iterator<ElementDefinition> it = elementDefs.iterator(); it.hasNext();) {
      ElementDefinition elt = it.next()
      if (elt.hasName() && elt.getName() == name) return elt
    }
    return null // not found
  }

  @TypeChecked
  static ElementDefinition getExtensionValueByName(List<ElementDefinition> elementDefs, String name) {
    for (Iterator<ElementDefinition> it = elementDefs.iterator(); it.hasNext();) {
      ElementDefinition elt = it.next()
      if (elt.hasName() && elt.getName() == name) {
        final def path = elt.getPath()        // <path value="Extension.extension"
        final def target = path + ".value[x]" // <path value="Extension.extension.value[x]"
        //final def sliceElt = elt
        while(it.hasNext()) {
          elt = it.next()
          def eltPath = elt.getPath()
          if (eltPath == target) return elt
          if (eltPath == path) break // next slice no value[x] element found
        }
        // Extension.extension.value[x] for that extension slice
        // return elt
        break
      }
    }
    return null // not found
  }

  @TypeChecked
  static Set<String> getTypeSet(ElementDefinition elementDef) {
    /*
    For these values: set = { "uri", "Reference(Patient)" }

    <type>
        <code value="uri"/>
    </type>

    <type>
        <code value="Reference"/>
        <profile value="http://hl7.org/fhir/Profile/Patient"/>
    </type>
    */
    if (!elementDef.hasType()) return Collections.emptySet()
    //return getTypeSet(elementDef.getType())
    def set = new TreeSet<String>()
    elementDef.getType().each {
      // http://hl7-fhir.github.io/elementdefinition.html#ElementDefinition
      // code 1..1 (required)
      if (it.hasCode()) {
        String code = it.getCode()
        if (code == 'Reference') {
          // Reference(http://hl7.org/fhir/Profile/Practitioner)
          if (it.hasProfile()) {
            String profile = getProfile(it) // TODO: type changed from String to List<UriType> (fix)
            if (profile.startsWith('http://hl7.org/fhir/StructureDefinition/'))
              profile = profile.substring(profile.lastIndexOf('/') + 1)
            code = String.format('%s(%s)', code, profile)
          }
        }
        set.add(code)
      } // NOTE: code is required in Type element so should never have case where code is not present
    }
    return set
  }

  @TypeChecked
  static String getReference(ElementDefinitionBindingComponent binding) {
    String value = null
    if (binding && binding.hasValueSet()) {
      def ref
      def valueSet = binding.getValueSet()
      try {
        if (valueSet instanceof org.hl7.fhir.instance.model.Reference) {
          ref = binding.getValueSetReference()
          if (ref && ref.hasReference()) {
            //println "XX: string ref: " + ref.getClass().getName()
            value = ref.getReference()
            //printf "X: binding: %-22s %-20s %s%n", elt.getPath(), binding.getName(), ref
          }
        } else if (valueSet instanceof UriType) {
          UriType uriType = binding.getValueSetUriType()
          if (uriType && uriType.hasValue()) value = uriType.getValue()
        } // else println "X: binding w/other valueset type: $valueSet"
      } catch (Exception e) {
        def s = e.getMessage()
        if (!s) {
          def cause = e.getCause()
          if (cause) s = cause.getMessage()
          if (!s) s = e.toString()
        }
        // e.printStackTrace(System.out)
        // println "WARN: uritype=" + binding.getValueSetUriType()
        println "WARN: bad ref: $s hasValueset=" + binding.hasValueSet() // + " " + binding.getValueSet()
      }
    }
    return value
  }

  /**
   * Check if element name refers to a subcomponent (E.g. Observation.referenceRange.high.code)
   * @param resourceName resource name
   * @param name element name
   * @param keys
   * @return true if name refers to a subcomponent of an element already present
   */
  @TypeChecked
  static boolean isSubcomponent(String resourceName, String name, Set<String> keys) {
    String baseName = name
    // ` if referencing subelement; e.g. !Practitioner.address.country
    // keep stripping off last component until base name is found in base resource
    // or only resource base name is left.
    while (true) {
      int ind = baseName.lastIndexOf('.')
      if (ind <= resourceName.length()) break
      // strip last component from name (e.g. Observation.name.coding => Observation.name)
      baseName = baseName.substring(0, ind)
      if (keys.contains(baseName))
        return true
    }
    if (name.endsWith('.label')) println "XX: failed lookup: $name $resourceName\n$keys"
    return false
  }

/**
   * Construct Resource instance from XML representation.
   *
   * @param file   the file to be opened for reading.
   * @return Resource
   * @throws  IOException  if an I/O error occurs.
   */
  @TypeChecked
  Resource parseResource(File file) throws IOException {
    InputStream is = null
    try {
      is = new FileInputStream(file)
      return xmlParser.parse(is)
    } catch(IOException e) {
      throw e
    } catch(Exception e) {
      throw new IOException(e)
    } finally {
      if (is != null) IOUtils.closeQuietly(is)
    }
  }

  @TypeChecked
  String getXml(Resource resource) {
    return xmlParser.composeString(resource)
  }

  @TypeChecked
  ExtensionDef createExtensionDef(ElementDefinition elt, String extProfile) {
    // NOTE: some reason calling  new ExtensionDef constructor directly in subclass fails runtime
    // but works via this short-cut method...
    return new ExtensionDef(elt, extProfile)
  }

  @TypeChecked
  class ExtensionDef {

    final StructureDefinition extDef
    ElementDefinition extElt
    String extProfileUri, extProfileEltName

    /**
     * Extension defs loaded from "extension-definitions.xml" bundle file and/or other extension-xxx.xml files as needed.
     * Maps URI to the structureDefinition instance; e.g. http://hl7.org/fhir/StructureDefinition/patient-clinicalTrial-period
     */
    static final Map<String, StructureDefinition> extensionDefs = new HashMap<>()

    ExtensionDef(ElementDefinition elt, String extProfile) {
      extProfileUri = extProfile
      extProfileEltName = ''
      int ind = extProfile.indexOf('#')
      final String path = elt.getPath()
      if (ind > 0) {
        // http://hl7.org/fhir/StructureDefinition/adverseevent-qicore-cause#item
        println "XX: trim ext $extProfile in $path"
        extProfileUri = extProfile.substring(0, ind)
        extProfileEltName = extProfile.substring(ind + 1) // e.g. item
      }
      def extDef = extensionDefs.get(extProfileUri)
      if (extDef == null) {
        // extension-procedure-approachBodySite.xml
        // profile=http://hl7.org/fhir/StructureDefinition/procedure-approachBodySite
        def urlpath = extProfile
        ind = urlpath.lastIndexOf('/')
        if (ind > 0) {
          // check profile extension
          urlpath = urlpath.substring(ind + 1).toLowerCase()
          ind = urlpath.indexOf('#')
          if (ind > 0) {
            // e.g. extension-adverseevent-qicore-cause#item.xml
            println "XX: trim ext2 $urlpath"
            urlpath = urlpath.substring(0, ind)
          }
          def file = new File(publishDir, "extension-${urlpath}.xml")
          if (file.exists()) {
            println "INFO: load ext " + file
            extDef = loadExtension(file, extProfile)
          } else {
            println "WARN: ext " + file + " not found: path=$path"
          }
        }
      }
      this.extDef = extDef
    }

    StructureDefinition getStructure() {
      return extDef
    }

    ElementDefinition getElement() {
      if (extElt == null && extDef != null) {
        if (extProfileEltName) {
          // check sub-element; e.g. extension.extension name=item
          // extElt = getElementByName(extDef.getSnapshot().getElement(), 'Extension.extension', extProfileSubElt)
          // Extension.extension.value[x] for that extension slice
          extElt = extDef.hasSnapshot() ? getExtensionValueByName(extDef.getSnapshot().getElement(), extProfileEltName) : null
          // what if Extension.extension.extension ?? can we search just by name
          if (extElt == null && extDef.hasDifferential()) {
            //extElt = getElementByName(extDef.getDifferential().getElement(), 'Extension.extension', extProfileSubElt)
            extElt = getExtensionValueByName(extDef.getDifferential().getElement(), extProfileEltName)
            if (extElt) println "X: extension subelt-diff $extProfileEltName" //debug
          } else println "X: extension subelt $extProfileEltName" //debug

          // if (tabName != 'All' && extElt.hasMin() || extElt.hasMax()) printf "XX: 1subelt ext card: [%d %s] %s%n", extElt.hasMin() ? extElt.getMin() : -1, extElt.getMax(), elt.getName()

        } else {

          // NOTE: as of August ~24, 2015, simple check of extension value type = Element was eliminated
          // need check of simple type vs Element type; e.g. Goal.category is simple and Goal.target is complex

          // gforge issue # 8611. Element extension no longer have an explicit Element type defined in its value.
          // now need to scan elements to detect if extension has any extenions in which case it's an Element.

          if (extDef.hasDifferential()) {
            // boolean isElementType = false
            def elts = extDef.getDifferential().getElement()
            def it = elts.iterator()
            while(it.hasNext()) {
              ElementDefinition elt = it.next()
              def path = elt.getPath()
              /*
              extension-goal-target

              <differential>
                ...
                <element>
                  <path value="Extension.extension"/>
                  <name value="detail"/>
                  <short value="The target value range to be achieved"/>
                  <min value="1"/> <max value="1"/>
                  <type>
                    <code value="Extension"/>
                  </type>
                </element>
                ...
              <element>
                <path value="Extension.value[x]"/>
                <min value="0"/> <max value="0"/>
              </element>
            </differential>
               */
              if (path == 'Extension.extension') {
                // if Extension.extension has max=0 then it's a simple type
                if (elt.hasName() && (!elt.hasMax() || elt.getMax() != "0")) {
                  //isElementType = true
                  //break
                  // work-around is create temp element and set type code=Element
                  // println "EE: " + extDef.getUrl() // debug
                  extElt = new ElementDefinition()
                  extElt.setPath('Extension.value[x]')
                  def type = extElt.addType()
                  type.setCode('Element')
                  return extElt
                }
              }
              // also such extensions have value[x] with max=0 to disallow simple values
            }

            // othewise assume it's a simple extension type (e.g. Reference, CodeableConcept, boolean, etc.)

            extElt = getElementByName(elts, 'Extension.value[x]', '')
            // examples:
            // http://hl7.org/fhir/StructureDefinition/allergyintolerance-reasonRefuted
            // http://hl7.org/fhir/StructureDefinition/procedure-approachBodySite
          }

          // if (extElt == null && extDef.hasSnapshot()) extElt = getElementByName(extDef.getSnapshot().getElement(), 'Extension.value[x]', '')

          /*
          //debug
          if (tabName != 'All') {
            if (!extElt) println "XX: ext value not found " + elt.getName()//debug
            else if (extElt.hasMin() || extElt.hasMax()) printf "XX: 2ext value card: [%d,%s] %s%n", extElt.hasMin() ? extElt.getMin() : -1, extElt.getMax(), elt.getName() //debug
          }
          //debug
          */
        }
      }
      return extElt
    }

    StructureDefinition loadExtension(File file, String uri) {
      StructureDefinition res
      try {
        println "X: load extdef $uri"
        res = (StructureDefinition) parseResource(file)
      } catch (Exception e) {
        println "ERROR: failed to parse: " + file.getName()
        // prevent from loading again if referenced
        // create empty definition
        res = new StructureDefinition()
        res.setUrl(uri)
      }
      extensionDefs.put(uri, res)
      return res
    }

    static void putDefinition(String extDefUrl, StructureDefinition extDef) {
      extensionDefs.put(extDefUrl, extDef)
    }
  }

}
