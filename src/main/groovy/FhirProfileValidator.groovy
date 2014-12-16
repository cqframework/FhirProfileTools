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

  // profile specific exceptions that override cardinality in base resource
  final Map<String, String> exceptions = new TreeMap<>()

  File outputDir
  PrintWriter out
  int errCount, warnCount
  Map<String, Integer> defaultIdx

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
          }
          // TODO: support other exceptions (e.g. type override)
        }
      } finally {
        IOUtils.closeQuietly(is)
      }
    }
  }

  /**
   * Process resource profile worksheet
   *
   * @param mapping   Mapping of base resource elements to their definition
   * @param worksheet Active worksheet for this profile
   * @param index  Map of all column names to column index
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
    Integer bindIdx = index.get(LABEL_BINDING) // optional
    Integer valueIdx = index.get(LABEL_VALUE) // optional
    List<String> warnings = new ArrayList<>()
    List<String> elements = new ArrayList<>()
    // iterate rows of structure worksheet in profile
    // skip header 1) and resource-level (2) rows starting at row #3
    main: for (int i=3; i < 50; i++) {
      // if (!worksheet.hasRowAt(i)) println "no row at $i"
      Cell cell1 = worksheet.getCellAt(i, eltIdx)
      String rawEltName = cell1.getData$()
      if ('' == rawEltName) break // element name should not be blank: this means reached the end so stop processing
      String eltName = rawEltName
      if (eltName.endsWith('.extension')) continue // ignore extensions
      if (eltName.startsWith('!')) {
        eltName = eltName.substring(1) // strip ignore marker
        if (mapping.containsKey(eltName) || mapping.containsKey(rawEltName)) {
          // profile is ignoring an element that is present in base resource which is allowed
          elements.add(eltName)
          rows++
        } else println "XX: ignoreable element not in base resource: $rawEltName"
        continue
        /*
        if (!mapping.containsKey(eltName)) {
          println "\t$rawEltName: does not appear in base resource list"
          continue
        }
        */
      } //else if (eltName.endsWith('.identifier')) warn "identifier not masked: $eltName"
      Details details = mapping.get(eltName)
      if (details == null && !rawEltName.startsWith('!')) {
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
        // TODO: doesn't handle the special [x] variable typed elements;
        // e.g. Condition.onset[x] : dateTime|Age 0.. => Condition.onsetAge, Condition.onsetDateTime, etc.
        // profile can specify the expanded names (e.g. Condition.onsetAge)
      }
      rows++
      elements.add(eltName)

      Cell cell2 = worksheet.getCellAt(i, cardIdx)
      String cardinality = cell2.getData$() ?: ''
      String mustSupport = 'Y' == worksheet.getCellAt(i, mustIdx).getData$() ? 'Y' : '' // ?: ''
      Set flags =  new TreeSet()
      if (mustSupport == 'Y') flags.add('S')
      if (bindIdx && worksheet.getCellAt(i, bindIdx).getData$()?.trim()) flags.add('V') // binding/valueset
      if (valueIdx && worksheet.getCellAt(i, valueIdx).getData$()?.trim()) flags.add('F') // fixed value
      //String flags =  mustSupport == 'Y' ? 'S' : ''
      String type = worksheet.getCellAt(i, typeIdx).getData$()?.trim() ?: ''
      // println "flags: " + flags.join(', ')
      if (details == null) {
        if (count++ == 0) {
          printHeader()
          out.println(TABLE_DEF)
        }
        errors++
        //println eltName + " NF"
        //out.printf('<tr><td>%s<td>NF', eltName)
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
      if ((baseType || type) && !checkType(baseType, type)) {
        String name = eltName
        if (flags) name = String.format("%s (flags: %s)", eltName, flags.join(', '))
        printf '\ttypeDiff: %s\t%s%n\t\tbase=%s%n\t\ttype=%s%n', name, cardinality, baseType, type
        // type += "/" + details.type
        typeDiff = true
        if (type) errors++ // if type not defined then not an error but intentional omission
      }

      if (baseCard && baseCard != cardinality) {
        String name = id + "." + eltName
        //out.println "<tr><td>"+name
        if (count++ == 0) {
          //printResource()
          printHeader()
          out.println(TABLE_DEF)
        }
        def exp = exceptions.get(name)
        if (exp == cardinality) {
          // cardinality is correct exception - green color
          char cardLow = cardinality.charAt(0)
          if (cardLow != baseCard.charAt(0))
            cardinality = '<B class="big">' + cardLow + "</B>" + cardinality.substring(1)
          else cardinality = String.valueOf(cardLow) + '<B class="big">'  + cardinality.substring(1) + "</B>"
          out.printf('<tr><td>%s<td>%s<td class="overrideCard">%s<br>%s (*)', eltName, flags.join(', '), baseCard, cardinality)
        } else {
          errors++
          if (cardinality) {
            if (exp != null) println "XX: mismatch cqf exception $exp $cardinality"
            printf '\t%s\t%s\t%s%n', eltName, baseCard, cardinality
            char cardLow = cardinality.charAt(0)
            if (cardLow != baseCard.charAt(0))
              cardinality = '<B class="big">' + cardLow + "</B>" + cardinality.substring(1)
            else cardinality = String.valueOf(cardLow) + '<B class="big">' + cardinality.substring(1) + "</B>"
            out.printf('<tr><td>%s<td>%s<td class="error">%s<br>%s', eltName, flags.join(', '), baseCard, cardinality)
          } else {
            out.printf('<tr><td>%s<td>%s<td class="error">%s', eltName, flags.join(', '), baseCard)
          }
        }
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
        String classType = typeDiff && type ? 'error' : 'info'
        if (truncated) {
          out.printf('''<td%s>
<span class="dropt">%s
<span style="width:500px;">%s<BR>%s</span>
</span>%n''', typeDiff ? " class='$classType'" : '', typeDef,
                  reformatType(origBaseType), reformatType(origType))
        } else {
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
          classType = 'error'
          typeDetail = "$baseType<br>$type"
        } else {
          classType = 'info'
          // element type in profile is unspecified
          typeDetail = baseType
        }
        if (truncated) {
          out.printf('''<tr><td>%s<td>%s<td>%s<td class="%s">
<span class="dropt">%s
<span style="width:500px;">%s<BR>%s</span>
</span>%n''', eltName, flags.join(', '), cardinality,
              classType, typeDetail, origBaseType, origType)
        } else {
          out.printf('<tr><td>%s<td>%s<td>%s<td class="%s">%s%n',
            eltName, flags.join(', '), cardinality, classType, typeDetail)
        }
      } // type diff
    } // for each element

    if (count != 0) out.println('</table>')

    if(warnings) {
      warnings.each{ warn(it) }
    }

    if (rows < mapping.size()) {
      StringBuilder sb = new StringBuilder()
      sb.append("check element list: rows=").append(rows)
          .append(" mapping=").append(mapping.size())
      Set<String> set = new TreeSet<>()
      mapping.keySet().each { String name ->
        if (name.startsWith("!")) name = name.substring(1)
        set.add(name)
      }
      set.removeAll(elements)
      sb.append('<blockquote>').append('unmapped elements: ').append(set)
          .append('</blockquote>')
      warn(sb.toString())
    }
    // println "\t" + worksheetName
    //Row row = worksheet.getRowAt(2)
    //println row.getCellAt(3)
    //println "\tok"
    if (errors == 0) {
      if (count == 0) {
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
      if (count != 0) out.println '<br>'
      out.println('<div style="width:180px; background-color: #00ff00"><B>no differences: OK</b></div>')
    } else {
      errCount += errors
    }
  }

 /**
   * Returns true if base type same or compatible with profiled element type
   * @param baseType
   * @param type
   * @return true if equivalent and/or compatible and false if not
   */
  @TypeChecked
  static boolean checkType(String baseType, String type) {
    if (baseType == type) return true
    if (baseType == 'Reference(Any)') return true // profile can restrict type as required
    if (!type) return false

    if (type.contains('|')) {
      // type contains a list
      def baseTypes = new HashSet<>() // Arrays.asList(baseType.split('|'))
      baseType.split('\\|').each{ String s ->
        baseTypes.add(getBaseType(s))
      }
      def profTypes = new HashSet<>()
      type.split('\\|').each{ String s ->
        profTypes.add(getBaseType(s))
      }
      boolean val = baseTypes.equals(profTypes)
      println "X: list eq=$val" // debug
      println "1=" + baseType
      println "2=" + type
      println "1=" + baseTypes
      println "2=" + profTypes
      return val
      //return baseTypes.equals(profTypes)
    } else if (baseType.contains('(') || type.contains('{')) {
      // allow short-hand e.g.; base type = Reference(Specimen), type = Specimen ??
      // type contains profile target reference; e.g. Reference(Patient){http://hl7.org/fhir/Profile/patient-daf-dafpatient}
      baseType = getBaseType(baseType)
      type = getBaseType(type)
      boolean val = baseType == type
      println "X: profile spec $val" // debug
      println "1="+ baseType
      println "2="+type
      println "--"
      return val
      //return baseType == type
    } else {

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
   * Normalize type definitions
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
    println "profile: " + profile.id

    if (index.size() != 24) {
      printf "\tindex[%d]: %s%n", index.size(), index
    } else if (defaultIdx == null) {
      println "\tindex: set default column labels"
      defaultIdx = index
    } else if (!defaultIdx.equals(index)) {
      warn("different column labels: $index")
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
    td.info {
      line-height: 150%;
      background-color: #00ff00;
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
    </head><body>''')
  }

  @TypeChecked
  void tearDown() {
    printf('%nErrors: %d Warnings: %d%n', errCount, warnCount)
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
  void printResource() {
    if (resourceName && resourceName != lastResource) {
      println()
      println resourceName
      out.printf('\n<hr>\n<h1>%s&nbsp;<a href="%s%s.html"><img src="images/external_link_icon.gif"></a></h1>',
              resourceName, baseUrl, resourceName.toLowerCase())
      lastResource = resourceName
    }
  }

  @TypeChecked
  void printHeader() {
    printResource()
    if (profile && profile != lastProfile) {
      String name = (profile.id ?: profile.name)
      // println "profile: $name"
      out.println("<h2>profile: " + name + "</h2>")
      lastProfile = profile
    }
  }

  void warn(String msg) {
    printHeader()
    logMsg('WARN', 'ffff00', msg)
    warnCount++
  }

  void error(String msg) {
    printHeader()
    logMsg('ERROR', 'ff0000', msg)
    errCount++
  }

  void logMsg(String level, String color, String msg) {
    out.printf('<br><div style="width:400px; background-color: #%s"><B>%s: %s</b></div>%n', color, level, msg)
  }

} // FhirProfileValidator
