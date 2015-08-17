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
import nl.fountain.xelem.excel.Cell
import nl.fountain.xelem.excel.Worksheet
import org.apache.commons.io.IOUtils

import java.util.regex.Pattern

/**
 * FHIR Profile Validator generates validation report
 *
 * @author Jason Mathews, MITRE Corporation
 * Created 12/4/2014.
 */
class FhirProfileValidator extends FhirProfileScanner {

  /**
   * Flag to skip output for "good" profiles to make output report more compact
   * only showing those with errors and warnings.
   */
  boolean skipGoodProfiles = true

  static final String TABLE_DEF = '<table style="border:1px solid black; border-collapse:collapse; margin-left:30px;"><tr><th>Element<th>Flags<th>Card.<th>Type'
  static final String baseUrl = 'http://hl7-fhir.github.io/'

  // allow specific warnings on per profile basis to be ignored
  final Set<String> ignoreWarnings = new HashSet<>()

  // profile specific exceptions that override cardinality in base resource
  final Map<String, String> exceptions = new TreeMap<>()

  File outputDir
  PrintWriter out
  int errCount, warnCount, infoCount
  final Map<String, Integer> defaultIdx, defaultIdx2
  final List<String> infoList = new ArrayList<>()

  FhirProfileValidator(Pattern profilePattern, File ruleFile) {
    super(profilePattern)

    if (ruleFile && ruleFile.exists()) {
      def props = new Properties()
      def is = new FileInputStream(ruleFile)
      try {
        props.load(is)
        props.entrySet().each { def entry ->
          String key = entry.getKey()
          if (key.endsWith('.card')) {
            exceptions.put(key.substring(0, key.length()-5), entry.getValue())
          } else if (key.endsWith('.ignoreWarn')) {
            ignoreWarnings.add(key.substring(0, key.length()-11) + ":" + entry.getValue())
          }
          // TODO: support other exceptions (e.g. type override)
        }
      } finally {
        IOUtils.closeQuietly(is)
      }
    }

    // set of 24 columns in default profile worksheet template
    // column mapping: [ 'Profile Name':'1', 'Discriminator':'2', 'Slice Description':'3', 'Element':'4', 'Aliases':'5', 'Card.':'6', ... ]
    def defaultColumns = [ 'Profile Name','Discriminator','Slice Description','Element','Aliases',
                           'Card.','Inv.','Type','Must Support','Binding','Value','Pattern','Example',
                           'Max Length','Short Label','Definition','Requirements','Comments','To Do',
                           'RIM Mapping','v2 Mapping','??? Mapping','Display Hint','Committee Notes']

    defaultIdx = new TreeMap<String,Integer>()
    defaultColumns.eachWithIndex { key, idx ->
      defaultIdx.put(key, idx + 1)
    }

    // alternative set of column mappings with QUICK Mapping in 22th column with 25 columns
    // e.g. [ ..., 'v2 Mapping':'21', 'QUICK Mapping':'22', '??? Mapping':'23', 'Display Hint':'24', 'Committee Notes':'25' ]
    defaultColumns.add(21, 'QUICK Mapping')
    defaultIdx2 = new TreeMap<String,Integer>()
    defaultColumns.eachWithIndex { key, idx ->
      defaultIdx2.put(key, idx + 1)
    }
  }

  void info(String msg) {
    println "INFO: $msg resource=$resourceName"
    infoList.add(msg)
  }

  /**
   * Process resource profile worksheet
   *
   * @param mapping   Mapping of base resource elements to their definition
   * @param worksheet Active worksheet for this profile
   * @param index  Map of all column names to column index in active worksheet
   */
  @TypeChecked
  void processProfile(Map<String, Details> mapping, Worksheet worksheet,
                    Map<String, Integer> index) {

    final String id = profile.id
    //String profileName = profile.name
    //if (profileName.startsWith('!')) profileName = profileName.substring(1)
    //if (profileName != profile.id) error("mismatch name=$profileName id=$id")

    int errors = 0, count = 0
    int rows = 0
    int eltIdx =  index.get(LABEL_ELEMENT)
    int cardIdx = index.get(LABEL_CARD)
    int typeIdx = index.get(LABEL_TYPE)
    int mustIdx = index.get(LABEL_MUST_SUPPORT)
    //Integer shortIdx = index.get(LABEL_SHORT_LABEL) // optional
    Integer bindIdx = index.get(LABEL_BINDING) // optional
    Integer valueIdx = index.get(LABEL_VALUE) // optional
    Integer nameIdx = index.get(LABEL_PROFILE_NAME) // optional
    List<String> warnings = new ArrayList<>()
    Set<String> elements = new HashSet<>()
    // iterate rows of structure worksheet in profile
    // skip header 1) and resource-level (2) rows starting at row #3
    main: for (int i=3; i < 50; i++) {
      // if (!worksheet.hasRowAt(i)) println "no row at $i"
      Cell cell1 = worksheet.getCellAt(i, eltIdx)
      final String rawEltName = cell1.getData$()
      if ('' == rawEltName) break // element name should not be blank: this means reached the end so stop processing
      String eltName = rawEltName
      if (eltName.endsWith('.extension') || eltName.endsWith('.modifierExtension')) {
        if (eltName.startsWith('!')) continue // ignore skipped extensions
        if (!nameIdx) continue // ignore extensions without names
        def name = worksheet.getCellAt(i, nameIdx).getData$()
        if (!name) continue // ignore extensions without names
        eltName = name
      } else if (eltName.startsWith('!')) {
        eltName = eltName.substring(1) // strip ignore marker

        if (mapping.containsKey(eltName) || mapping.containsKey(rawEltName)) {
          // profile is ignoring an element that is present in base resource which is allowed
          elements.add(eltName)
          rows++
        } else {
          String baseName = eltName
          // check if referencing subelement; e.g. !Practitioner.address.country
          // keep stripping off last component until base name is found in base resource
          // or only resource base name is left.
          while(true) {
            int ind = baseName.lastIndexOf('.')
            if (ind <= resourceName.length()) break
            // strip last component from name (e.g. Observation.name.coding => Observation.name)
            baseName = eltName.substring(0, ind)
            if (mapping.containsKey(baseName)) {
              // println "X: skip subcomponent definition: $rawEltName $baseName" // debug
              elements.add(baseName)
              continue main
            }
          }
          println "XX: ignoreable element not in base resource: $rawEltName"
        }
        continue
        /*
        if (!mapping.containsKey(eltName)) {
          println "\t$rawEltName: does not appear in base resource list"
          continue
        }
        */
      } //else if (eltName.endsWith('.identifier')) warn "identifier not masked: $eltName"

      String nameClass = ''
      Details details = mapping.get(eltName)
      // NOTE: special [x] variable typed elements are auto-expanded to mapping table.
      // e.g. Condition.onset[x] : dateTime | Age 0.. => Condition.onsetAge, Condition.onsetDateTime, etc.
      // Profiles can specify the expanded names (e.g. Condition.onsetAge) in differential view.
      if (rawEltName.endsWith('.extension') || rawEltName.endsWith('.modifierExtension')) {
        // use generic extension details based on structuredefinition.profile.xml definition
        if (details != null) {
          nameClass = ' class="warn"'
          warnings.add("conflict with extension name and element path: " + eltName)
        }
        details = new Details('0..*', 'Extension', '', '', false)
      } else if (details == null && !rawEltName.startsWith('!')) {
        if (rawEltName.contains('.extension.')) {
          // constrain extension sub-component; e.g. Encounter.extension.valueReference or Patient.extension.uri
          continue
        }
        details = mapping.get("!" + eltName)
        if (details != null) {
          // found element name in base resource
          warnings.add("profile adding ignorable element: " + eltName)
        } else {
          // handle case where profile is referring to a component of a structure
          // e.g. Patient.identifier.system where Patient.identifier is of type Identifier
          // Observation.name <= Observation.name.coding.display
          String baseName = eltName
          // keep stripping off last component until base name is found in base resource
          // or only resource base name is left.
          while(true) {
            int ind = baseName.lastIndexOf('.')
            if (ind <= resourceName.length()) break
            // strip last component from name (e.g. Observation.name.coding => Observation.name)
            baseName = eltName.substring(0, ind)
            if (mapping.containsKey(baseName)) {
              // println "X: skip subcomponent definition: $eltName" // debug
              continue main
            }
          }
        }
      }
      rows++
      elements.add(eltName)

      String val = worksheet.getCellAt(i, cardIdx).getData$() ?: ''
      // issue tracker #4041 discusses change to publishing tools for spreadsheets allowing "!" prefix
      // in type and cardinality to annotate the type/cardinality but treat as blank to use base resource value.
      // See http://gforge.hl7.org/gf/project/fhir/tracker/?action=TrackerItemEdit&tracker_item_id=4041
      final String cardinality = val.startsWith('!') ? '' : val
      val = worksheet.getCellAt(i, mustIdx).getData$() // Y, y, Yes, N, No, or empty
      final boolean mustSupport = isYesValue(val)

      // Conformance: 2.11.0.11 Must Support
      // If creating a profile based on another profile,
      // Must Support can be changed from false to true, but *CANNOT* be changed from true to false.
      // https://www.hl7.org/implement/standards/FHIR-Develop/profiling.html
      // http://hl7-fhir.github.io/profiling.html
      // All core FHIR resources have default mustSupport=false so no need to check this for profiles based on core resources.

      Set<String> flags = new TreeSet<>()
      if (mustSupport) flags.add('S')
      if (bindIdx) {
        String binding = worksheet.getCellAt(i, bindIdx).getData$().trim()
        if (binding) {
          flags.add('V')

          // TODO validate binding compared to base resource
          // http://hl7-fhir.github.io/terminologies.html#strength
          //  1.23.0.2.1 Required
          //  When an element is bound to a required value set, derived profiles may state rules on which codes can be used,
          //  but cannot define new or additional codes for these elements.

          /*
          // if have binding then should specify short value
          if (shortIdx) {
            val = worksheet.getCellAt(i, shortIdx).getData$().trim()
            if (val.isEmpty()) { // && details?.parent == null) {
              // REVIEW: if element is expanded [x] type (e.g. valueString, etc.) then ignore short value warning
              infoList.add('element has binding and may specify a short value: ' + eltName)
            }
          }
          */
        }
      }
      if (valueIdx) {
        String value = worksheet.getCellAt(i, valueIdx).getData$()?.trim()
        if (value) {
          // possibly copy/pasted short label into fixed value field
          // if (value =~ /[|,]/) println "X: possible bad fixed value: $val"
          flags.add('F')
          // TODO validate fixed value
          // println "X: ${profile.name} $eltName value $value"
        }
      }

      String type = worksheet.getCellAt(i, typeIdx).getData$()?.trim() ?: ''
      // NOTE: type value in profile spreadsheet starting with '!' prefix annotates a type for comment only
      // and defers to the type in base resource. Treated same as having an empty/blank value.
      // See http://gforge.hl7.org/gf/project/fhir/tracker/?action=TrackerItemEdit&tracker_item_id=4041
      if (type.startsWith('!')) type = ''
      // println "flags: " + flags.join(', ')
      if (details == null) {
        // no base definition
        /*
        if (rawEltName.startsWith('!') && rawEltName.endsWith('.extension')) {
          println "ERR: ignore ext: $rawEltName [$eltName]"
          continue
        }
        */
        if (count++ == 0) {
          printHeader()
          out.println(TABLE_DEF)
        }
        errors++
        //println eltName + " NF"
        //out.printf('<tr><td>%s<td>NF', eltName)
        // if (rawEltName.endsWith('.extension')) println "ERR: ext: no details $rawEltName [$eltName] " + (details == null)
        String typeDef = type
        if (type.length() > 33) {
          typeDef = String.format('''<span class="dropt">%s
<span style="width:500px;">%s</span>
</span>''', type.substring(0,30) + '...', reformatType(type))
        }
        out.printf('<tr><td bgcolor="#FF0000">%s<td>%s<td>%s<td>%s%n',
                eltName, flags.join(', '), cardinality, typeDef)
        // diff++
        continue
      }

      // base resource element cardinality
      final String baseCard = details.card

      //if (cell2 == null) {
      //println "\tERROR: no cell at $i-5"
      //continue
      //}

      boolean typeDiff = false
      String baseType = details.type // never null but can be empty ('') if not defined
      if ((baseType || type) && !checkType(eltName, baseType, type, warnings)) {
        String name = eltName
        if (flags) name = String.format("%s (flags: %s)", eltName, flags.join(', '))
        printf '\ttypeDiff: %s\t%s%n\t\tbase=%s%n\t\ttype=%s%n', name, cardinality, baseType, type
        // type += "/" + details.type
        typeDiff = true
        // Element type implied in domain resource so valid to have baseType omitted
        // otherwise an error
        if (type && !(baseType.isEmpty() && type == 'Element' || baseType == 'Extension')) {
          errors++ // if type not defined then not an error but intentional omission
        }
      }

      // check cardinality

      if (baseCard && baseCard != cardinality) {
        String name = id + "." + eltName
        //out.println "<tr><td>"+name
        if (count++ == 0) {
          printHeader()
          out.println(TABLE_DEF)
        }
        String cardPrint = null
        String expectedCard = exceptions.get(name)
        if (cardinality == expectedCard) {
          printf '\t%s\t%s\t%s (expected)%n', eltName, baseCard, cardinality
          int ind = cardinality.indexOf('..')
          if (ind > 0) {
            // cardinality is correct exception - green color
            String cardLow = cardinality.substring(0, ind)
            if (cardLow != baseCard.substring(0, ind))
              cardPrint = '<B class="big">' + cardLow + '</B>' + cardinality.substring(ind)
            else cardPrint = String.valueOf(cardLow) + '..<B class="big">' + cardinality.substring(ind+2) + '</B>'
            out.printf('<tr><td%s>%s<td>%s<td class="overrideCard">%s<br><span title="expected cardinality">%s (*)</span>',
                    nameClass, eltName, flags.join(', '), baseCard, cardPrint)
          }
          // otherwise expected cardinality is invalid - handle in next stage of validation
        }
        if (cardPrint == null) {
          if (cardinality) {
            if (expectedCard) warnings.add("mismatch expected cardinality: expected $expectedCard but was $cardinality".toString())
            printf '\t%s\t%s\t%s%n', eltName, baseCard, cardinality
            String classType = null
            cardPrint = cardinality
            if (baseCard == '0..1' && (cardinality == '1..1' || cardinality == '0..0')) {
              // making optional element required is not an error or warning (i.e., 0..1 => 1..1 is allowed)
              // and ruling out an optional element by setting max card = 0 is also allowed (i.e., 0..1 => 0..0)
              // Source: http://fhirblog.com/2014/03/26/fhir-profiles-an-overview/
              classType = 'info'
            } else if (baseCard == '1..1' && cardinality == '1..*') {
              // 2.11.0.3 Limitations of Use
              // Profiles cannot break the rules established in the base specification (e.g. if the element
              // cardinality is 1..1 in the base specification, a profile cannot say it is 0..1, or 1..*).
              // Source: https://www.hl7.org/implement/standards/FHIR-Develop/profiling.html#2.11.0.3
              classType = 'error'
            } else if (baseCard.startsWith('1..') && cardinality.startsWith('0..')) {
              classType = 'error'
              // if element is required (i.e., min=1) then cannot make it optional.
              // Source: http://fhirblog.com/2014/03/26/fhir-profiles-an-overview/
              // FHIR spec (section Limitations of Use)
              //  http://www.hl7.org/implement/standards/fhir/profiling.html#2.13.0.4
              //  http://www.hl7.org/fhir/2015May/profiling.html#2.14.0.4
              //  http://www.hl7.org/implement/standards/fhir/2015Jan/profiling.html#2.11.0.3
              //  https://www.hl7.org/implement/standards/FHIR-Develop/profiling.html#2.11.0.3
            } else if (baseCard.endsWith('..1') && cardinality.endsWith('..*')) {
              classType = 'error'
              // if max=1 is specified in base then can't it make it many.
              // Source: http://fhirblog.com/2014/03/26/fhir-profiles-an-overview/
            }
            try {
              def card = new Cardinality(cardinality)
              if (classType == null) {
                if (card.min >= 0 && card.min <= card.max) {
                  // In a profile, you can specify any combination of cardinalities, as long as min <= max, and min >= 0.
                  // Source: http://wiki.hl7.org/index.php?title=FHIR_conformance_and_cardinality
                  classType = 'info'
                  // TODO: check other cardinality combinations here
                } else {
                  classType = 'error'
                }
              }
              // following just to highlight the parts of the cardinality in BOLD that differ from the base cardinality
              // skip highlighting for extensions since extension itself defines what is expected
              if (!type.startsWith('Extension')) {
                try {
                  def base = new Cardinality(baseCard)
                  if (card.min != base.min)
                    cardPrint = String.format('<B class="big">%d</B>..', card.min)
                  else
                    cardPrint = String.format('%d..', card.min)
                  if (card.maxVal != base.maxVal)
                    cardPrint += String.format('<B class="big">%s</B>', card.maxVal)
                  else
                    cardPrint += card.maxVal
                } catch (IllegalArgumentException e) {
                  // failed to parse base cardinality
                  cardPrint = cardinality
                }
              }
            } catch (IllegalArgumentException e) {
              classType = 'error'
              println "X: bad card: $cardinality: $e"
            }
            if (classType == 'error') errors++
            if (baseCard == '0..*' && type.startsWith('Extension') && classType == 'info') {
              // Extension card is by default 0..* so no need to show base card if card is specified for given extension
              // e.g. type = Extension{#cause#item} or Extension{http://hl7.org/fhir/StructureDefinition/communication-reasonNotPerformed
              out.printf('<tr><td%s>%s<td>%s<td>%s', nameClass, eltName, flags.join(', '), cardPrint)
            } else {
              out.printf('<tr><td%s>%s<td>%s<td class="%s">%s<br>%s', nameClass, eltName, flags.join(', '), classType, baseCard, cardPrint)
              if (classType == 'info') infoCount++
            }
          } else {
            // otherwise cardinality not specified in profile -- use base resource definition by default
            out.printf('<tr><td%s>%s<td>%s<td>%s', nameClass, eltName, flags.join(', '), baseCard)
          }
        }

        // output type field

        boolean truncated = false
        String origBaseType = baseType, origType = type
        if (baseType.length() > 33) {
          baseType = baseType.substring(0, 30) + "..."
          truncated = true
        }
        String typeDef = baseType
        if (type && typeDiff) {
          if (type.length() > 33) {
            type = type.substring(0, 30) + "..."
            truncated = true
          }
          typeDef += '<br>' + type
        }
        String classType
        if (baseType.isEmpty() && type == 'Element') {
          // Element type implied in domain resource so valid to have this omitted
          classType = 'empty' // => green cell
        }
        else {
          if (typeDiff && type) {
            // if base type is extension then profile can override the extension type which we're not checking
            classType = origBaseType == 'Extension' ? 'info' : 'error'
            if (classType=='info') infoCount++
          } else classType = type || baseType.isEmpty() ? '' : 'empty'
        }

        if (classType == 'empty' && !truncated) {
            typeDef = "<span title='type unspecified in profile'>$typeDef</span>"
        }
        //if (eltName == 'DiagnosticReport.performer') printf "X: class=%s dif=%b type=%s [%s]%n", classType, typeDiff, baseType, type // debug
        if (truncated) {
          String typeDetail = reformatType(origType)
          if (classType == 'empty') typeDetail += '<BR>Type is unspecified in profile defaulting to base type.'
          out.printf('''<td%s>
<span class="dropt">%s
<span style="width:500px;">%s<BR>%s</span>
</span>%n''', typeDiff ? " class='$classType'" : '', typeDef,
                  reformatType(origBaseType), typeDetail)
        } else {
          //typeDef += "/class=" + classType //debug
          out.printf('<td%s>%s%n', typeDiff ? " class='$classType'" : '', typeDef)
        }
      } else if (typeDiff) {
        if (count++ == 0) {
          printHeader()
          out.println(TABLE_DEF)
        }
        boolean truncated = false
        String origBaseType = reformatType(baseType)
        String origType = reformatType(type)
        if (baseType.length() > 33) {
          baseType = baseType.substring(0,30) + "..."
          truncated = true
        }
        String classType, typeDetail
        if (type) {
          if (type.length() > 33) {
            type = type.substring(0,30) + "..."
            truncated = true
          }
          if (baseType.isEmpty() && type == 'Element') {
            // Element type implied in domain resource so valid to have this omitted
            classType = 'empty' // => green cell
          }
          else classType = 'error'
          typeDetail = "$baseType<br>$type"
        } else if (baseType.isEmpty()) {
          classType = ''
          typeDetail = ''
        } else {
          // element type in profile is unspecified and uses base value
          classType = 'empty' // green cell
          typeDetail = baseType
        }
        if (classType == 'info') infoCount++
        if (truncated) {
          String origTypeDetail = origType
          if (classType == 'empty') {
            origTypeDetail += String.format('<BR>Type is unspecified in %s.',
                    baseType.isEmpty() ? 'base resource' : 'profile defaulting to base type' )
          }
          out.printf('''<tr><td%s>%s<td>%s<td>%s<td class="%s">
<span class="dropt">%s<span style="width:500px;">%s<BR>%s</span></span>%n''',
              nameClass, eltName, flags.join(', '), cardinality,
              classType, typeDetail, origBaseType, origTypeDetail)
        } else {
          if(classType == 'empty') typeDetail = baseType.isEmpty() ? "<span title='type unspecified in base'>$type</span>"
                  : "<span title='type unspecified in profile'>$baseType</span>"
          //typeDetail += "/class=" + classType //debug
          out.printf('<tr><td%s>%s<td>%s<td>%s<td class="%s">%s%n',
            nameClass, eltName, flags.join(', '), cardinality, classType, typeDetail)
        }
      } // type diff
    } // for each element (main)

    if (count != 0) out.println('</table>')

    if(warnings) {
      warnings.each{ warn(it) }
    }
    if (!infoList.isEmpty()) {
      println "XX: dump INFO"
      infoList.each {
        printHeader()
        logMsg('INFO', '', it)
      }
    }

    /*
    // this is not really a warning epecially if target profile only
    // adds one or two extensions so skip it.
    if (rows != mapping.size()) {
      Set<String> set = new TreeSet<>()
      mapping.each { String name, Details value ->
        // don't add common elements (e.g. Condition.text)
        // don't add expanded [x] types (parent != null)
        if (!value.common && value.parent == null) {
          if (name.startsWith("!")) name = name.substring(1)
          set.add(name)
        }
      }
      set.removeAll(elements)
      if (!set.isEmpty()) {
        StringBuilder sb = new StringBuilder()
        sb.append('check element list: rows=').append(rows)
                .append(" mapping=").append(mapping.size())
        sb.append('<blockquote>').append('unmapped elements: ').append(set)
                .append('</blockquote>')
        warn(sb.toString())
      }
    }
    */

    // println "\t" + worksheetName
    //Row row = worksheet.getRowAt(2)
    //println row.getCellAt(3)
    //println "\tok"
    if (errors == 0) {
      if (count == 0 && infoList.isEmpty()) {
        if (skipGoodProfiles) {
          if (resourceName && resourceName != lastResource) {
            println()
            println resourceName
          }
          println id
          println '\tno differences: OK'
          return
        }
        printHeader()
      }
      println '\tno differences: OK'
      if (count != 0 || !infoList.isEmpty()) out.println '<br>'
      out.println('<div style="width:180px; background-color: #00ff00"><B>no differences: OK</b></div>')
    } else {
      errCount += errors
    }
  }

  static boolean isYesValue(String s) {
    if (s != null) {
      s = s.trim()
      if (!s.isEmpty() && s.toUpperCase().startsWith('Y')) {
        // Y, y, Yes, yes, etc. is true
        return true
      }
      // N, n, no, No, blank, etc. is false
    }
    return false
  }

 /**
   * Returns true if base type same or compatible with profiled element type
   * @param baseType
   * @param type
   * @return true if equivalent and/or compatible and false if not
   */
  @TypeChecked
  static boolean checkType(String eltName, String baseType, String type, List<String> warnings) {
    if (baseType == type) return true
    if (baseType == 'Reference(Any)') return true // profile can restrict type as required
    if (!type) return false

    if (type.contains('|')) {
      // type contains a list
      def baseTypes = new HashSet<>()
      baseType.split('\\|').each{ String s ->
        baseTypes.add(getBaseType(s))
      }
      def profTypes = new HashSet<>()
      type.split('\\|').each{ String s ->
        profTypes.add(getBaseType(s))
      }
      boolean val = baseTypes.equals(profTypes)
      if (!val) {
        int size = profTypes.size()
        profTypes.removeAll(baseTypes)
        if (profTypes.isEmpty())
          printf "\tERROR: type list mismatch: size base=%d size profile=%s: %s%n", baseTypes.size(), size, eltName
        else
          println "\tERROR: list mismatch in $eltName: types in profile not in base: $profTypes"
      }
      return val
    } else if (baseType.contains('(') || type.contains('{')) {
      if (baseType.startsWith('Reference(') && !type.startsWith('Reference(')) {
        //warnings.add("$eltName: type does not have Reference() prefix: base type=$baseType profile type=$type".toString())
        return false
      }
      // allow short-hand; e.g. base type = Reference(Specimen), type = Specimen ??
      // type contains profile target reference; e.g. Reference(Patient){http://hl7.org/fhir/Profile/patient-daf-dafpatient}
      baseType = getBaseType(baseType)
      type = getBaseType(type)
      return baseType == type
    }

    // check profile in type {}
    // [condition-core] Condition.subject type = Reference(Patient)
    // [condition-daf]  Condition.subject type = Reference(Patient){http://hl7.org/fhir/Profile/patient-daf-dafpatient}

    // Reference(Observation|Condition)
    // Reference(Observation){http://hl7.org/fhir/Profile/observation-daf-results-dafresultobservationcode}
    //  |Reference(Observation){http://hl7.org/fhir/Profile/observation-daf-results-dafresultobsquantity}
    //  |Reference(Observation){http://hl7.org/fhir/Profile/observation-daf-results-dafresultobsratio}
    //  |Reference(Observation){http://hl7.org/fhir/Profile/observation-daf-results-dafresultobsother}

    // Reference(Any)
    // Reference(Patient){http://hl7.org/fhir/Profile/patient-daf-dafpatient}
    //   |Reference(AllergyIntolerance){http://hl7.org/fhir/Profile/allergyintolerance-daf-dafallergyintolerance}
    //   |Reference(Condition){http://hl7.org/fhir/Profile/condition-daf-dafcondition}
    //   |Reference(Encounter){http://hl7.org/fhir/Profile/encounter-daf-dafencounter}
    //   |Reference(Observation){http://hl7.org/fhir/Profile/observation-daf-vitalsigns-dafvitalsigns}
    //   |Reference(Procedure){http://hl7.org/fhir/Profile/procedure-daf-dafprocedure}
    //   |Reference(MedicationStatement){http://hl7.org/fhir/Profile/medicationstatement-daf-dafmedicationstatement}
    //   |Reference(MedicationAdministration){http://hl7.org/fhir/Profile/medicationadministration-daf-dafmedicationadministration}
    //    ...
    return false
   }

  /**
   * Normalize type definitions. Order of types in lists does not matter but names are case-sensitive.
   * @param s type
   * @return normalized type
   */
  @TypeChecked
  static String getBaseType(String s) {
    // Reference(Observation){http://hl7.org/fhir/Profile/observation-daf-results-dafresultobservationcode}
    // step 1
    // => Observation{http://hl7.org/fhir/Profile/observation-daf-results-dafresultobservationcode}
    // step 2
    // => Observation
    s = s.replace('Reference(','').replace(')', '').trim()
    int ind = s.indexOf('{')
    if (ind > 0 && s.endsWith('}')) {
      //int end = s.indexOf('}', ind)
      //if (end > 0)
      s = s.substring(0, ind)
    }
    return s
  }

/**
   * Called when structure worksheet first row is processed
   * but before checking required Element, Type, etc. columns
   * @param profile
   * @param index Index map
   */
  @TypeChecked
  void checkIndex(Map<String, Integer> index) {
    // FHIR template worksheet has 24 columns in Structure worksheet
    printf "%nprofile: %s [%s]%n", profile.id, profile.worksheetName
    infoList.clear() // reset info messages
    if (!defaultIdx.equals(index) && !defaultIdx2.equals(index)) {
      def keys = index.keySet()
      if(defaultIdx.keySet().equals(keys) || defaultIdx2.keySet().equals(keys)) {
        warn('different column label ordering')
        def reverseIdx = index.sort{ a, b -> a.value <=> b.value }
        printf "\tindex[%d]: %s%n", index.size(), reverseIdx
      } else {
        def difference = new TreeMap(index)
        // remove default column labels from the list to show the new column names
        defaultIdx2.keySet().each{ String key ->
          if (index.get(key) != null) difference.remove(key)
        }
        info("different column labels: $difference")
      }
    } else println "\tindex: column labels okay"
  }

  @TypeChecked
  void setup() {
    if (outputDir && !outputDir.exists() && !outputDir.mkdirs()) {
        throw new IOException("failed to create output directory: $outputDir")
    }
    File file = outputDir ? new File(outputDir, "out.html") : new File("out.html")
    if (file.length()) {
      File backup = outputDir ? new File(outputDir, "out.bak.html") : new File("out.bak.html")
      if (backup.exists()) backup.delete()
      file.renameTo(backup)
    }
    out = new PrintWriter(new FileWriter(file))

    out.println('''<html><head>
    <style>
    table, th, td {
        border: 1px solid black;
        border-collapse: collapse;
    }
    th, td {
        padding: 5px;
    }
    td.overrideCard {
      line-height: 150%;
      background-color: #00ff00;
    }
    td.error {
      line-height: 150%;
      background-color: red;
    }
    td.warn {
      line-height: 150%;
      background-color: yellow;
    }
    td.empty  {
      line-height: 150%;
      background-color: #00ff00;
    }
    td.info {
      line-height: 150%;
      background-color: #3366FF;
    }
    b.big {
      font-size: 110%;
    }
    span.dropt { border-bottom: thin dotted }
    span.dropt:hover {text-decoration: none; background: #ffffff; z-index: 6; }
    span.dropt span {position: absolute; left: -9999px;
      margin: 20px 0 0 0px; padding: 3px 3px 3px 3px;
      border-style:solid; border-color:black; border-width:1px; z-index: 6;}
    span.dropt:hover span {left: 2%; background: #ffffff;}
    span.dropt span {position: absolute; left: -9999px;
      margin: 4px 0 0 0px; padding: 3px 3px 3px 3px;
      border-style:solid; border-color:black; border-width:1px;}
    span.dropt:hover span {margin: 20px 0 0 70px; background: #ffffff; z-index:6;}
    </style>
    </head>
    <body>
    <h1>FHIR Profile Validation Conformance Report</h1>''')
  }

  @TypeChecked
  void tearDown() {
    if (profileCount == 0) println "no matching profiles found"
    if (infoCount != 0) printf('%nErrors: %d Warnings: %d Info: %d%n', errCount, warnCount, infoCount)
    else printf('%nErrors: %d Warnings: %d%n', errCount, warnCount)
    out.println("<HR><P>Total errors: $errCount")
    if (warnCount) out.println(" warnings: $warnCount")
    out.println("<BR>Generated on " + new Date())
    out.println('</body></html>')
    out.close()
  }

  static String reformatType(String s) {
      s.replace('|Reference', '|<BR>Reference')
  }

  @TypeChecked
  void printResourceName() {
    super.printResourceName()
      out.printf('\n<hr>\n<h2>%s&nbsp;<a href="%s%s.html"><img src="images/external_link_icon.gif"></a></h2>',
              resourceName, baseUrl, resourceName.toLowerCase())
  }

  @TypeChecked
  void printHeader() {
    printResource()
    if (profile && profile != lastProfile) {
      String name
      if (profile.multiWorksheets && profile.id) {
        // use full name if profile spreadsheet has multiple structures
        name = String.format('%s-%s', profile.id, profile.worksheetName.toLowerCase())
      } else name = profile.id ?: profile.name

      // println "profile: $name"
      //out.println("<h3>profile: " + name + "</h3>")
      out.printf('<h3>profile: %s', name)
      if (profile.id && profile.worksheetName) {
        out.printf('&nbsp;<a href="%s%s-%s.html"><img src="images/external_link_icon.gif"></a>',
                baseUrl, profile.id, profile.worksheetName.toLowerCase())
      }
      out.println('</h3>')
      lastProfile = profile
    }
  }

  void warn(String msg) {
    if (ignoreWarnings.isEmpty() || profile == null || !ignoreWarnings.contains(profile.name + ":" + msg)) {
      printHeader()
      logMsg('WARN', 'ffff00', msg)
      warnCount++
    }
  }

  void error(String msg) {
    printHeader()
    logMsg('ERROR', 'ff0000', msg)
    errCount++
  }

  void logMsg(String level, String color, String msg) {
    if (color)
      out.printf('<br><div style="width:400px; background-color: #%s"><B>%s: %s</b></div>%n', color, level, msg)
    else
      out.printf('<br><div style="width:400px"><B>%s: %s</b></div>%n', level, msg)
  }

  static class Cardinality {
    final int min, max
    final String maxVal
    /**
     * Decode cardinality string into its parts
     * @param cardinality
     * @throws IllegalArgumentException if cardinality is invalid and not of the
     * form min..max where min is an integer and max is '*' or a number
     */
    Cardinality(String cardinality) {
      def m = cardinality =~ /^(\d+)\.\.(\d+|\*)$/
      if (!m) throw new IllegalArgumentException()
      min = Integer.parseInt(m.group(1))
      maxVal = m.group(2)
      max = maxVal == '*' ? Integer.MAX_VALUE : Integer.parseInt(maxVal)
    }

    String toString() {
      return min + '..' + maxVal
    }
  }

} // FhirProfileValidator
