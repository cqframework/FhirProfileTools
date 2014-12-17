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
import nl.fountain.xelem.excel.Row
import nl.fountain.xelem.excel.Workbook
import nl.fountain.xelem.excel.Worksheet
import nl.fountain.xelem.lex.ExcelReader

import java.util.regex.Pattern

/**
 * Base FHIR Profile Scanner that scans directory(ies) for resource spreadsheets
 * used in FHIR SVN repository then for each spreadsheet it scans the profiles
 * that match the provided profile pattern so an author can scan/validate a
 * particular set of profiles (e.g. DAF, CQF, etc.).
 *
 * @author Jason Mathews, MITRE Corporation
 */
@TypeChecked
class FhirProfileScanner {

  // profile spreadsheet structure column labels:
  //  Profile Name, Discriminator=2, Slice Description, Element, Aliases, Card., Inv., Type,
  //  Must Support, Binding, Value, Pattern, Example, Max Length, Short Label, Definition,
  //  Requirements, Comments, To Do, RIM Mapping, v2 Mapping, Display Hint, Committee Notes
  static final String LABEL_ELEMENT = 'Element'
  static final String LABEL_CARD = 'Card.'
  static final String LABEL_TYPE = 'Type'
  static final String LABEL_MUST_SUPPORT = 'Must Support'
  static final String LABEL_BINDING = 'Binding'
  static final String LABEL_VALUE = 'Value'
  static final String LABEL_DEFINITION = 'Definition'
  static final String LABEL_IS_MODIFIER = 'Is Modifier' // not in profile-spreadsheet (only used in resource spreadsheet)

  final ExcelReader reader = new ExcelReader()
  private final Pattern profilePattern

  String resourceName, lastResource
  Profile profile, lastProfile
  int profileCount

  FhirProfileScanner(Pattern profilePattern) {
    this.profilePattern = profilePattern
  }

  /**
   * Scan source directory for FHIR Excel XML-formatted Workbook
   * with filename ending in -spreadsheet.xml with a prefix matches the directory name
   * that should match top-level resource and skip profiles based on core resources.
   *
   * @param dir top-level directory presumably for a FHIR resource
   */
  void checkDirectory(File dir) {
    // println dir.getName()

    File file = new File(dir, dir.getName() + '-spreadsheet.xml')
    if (!file.exists()) {
      // println "X: target not found: $file"
      return
    }

    Workbook xlWorkbook = reader.getWorkbook(file.getAbsolutePath())
    Worksheet worksheet = xlWorkbook.getWorksheet('Packages')
    if (worksheet == null) return // no profiles

    //Row row = worksheet.getRowAt(1)
    //def col = worksheet.getColumnAt(3)
    //println col.si
    List<Profile> profiles = new ArrayList<>()
    // scan each row for profiles w/filename on col #3
    // stop when reach an empty row.
    // NOTE: if any resource has more than 50 profiles then need to increase hard-limit
    // as of Dec-2014 max # profiles for any resource in FHIR svn repo is 17 for Observation.
    for (int i = 2; i < 50; i++) {
      if (!worksheet.hasRowAt(i)) break // stop at empty row
      Row row = worksheet.getRowAt(i)
      def cell = row.getCellAt(3) // source (filename)
      String val = cell.getData$()
      if ('' == val && '' == row.getCellAt(1).getData$()) {
        // stop at empty row
        // println "X:blank row = $i"
        break
      }
      if (val =~ profilePattern) {
        String name = row.getCellAt(1).getData$()
        // println "\tprofile name: $name"
        if (name.startsWith("!")) // check profile name
          println "WARN: profile disabled: $name"
        // String id = row.getCellAt(10).getData().toString()
        // cqfOut.printf("<li><a href='http://hl7-fhir.github.io/%s-%s.html'>%s</a>", id, id, id)
        profiles.add(new Profile(name, val.trim()))
        //println "\t" + val
      } // else println "XX:  no match: $val"
    }
    //println profiles
    if (!profiles) return // no cqf profiles

    worksheet = xlWorkbook.getWorksheet("Data Elements")
    if (worksheet == null) {
      println 'ERROR: data elements worksheet not found in spreadsheet'
      return
    }

    //println "\nfile: " + file.getName()
    resourceName = file.getName().replaceFirst(/-spreadsheet.xml$/, "")
    profile = null

    //def row = worksheet.getRowAt(1) // debug
    //println "rows=" + row.size()
    //printf "%s,%s%n", row.getCellAt(1).getData(), row.getCellAt(3).getData()

    // column index
    // 1=Element,2=Aliases,3=Card.,4=Inv.,5=Type,6=Is Modifier,7=Summary,8=Binding,9=Example,
    // 10=Default Value,11=Missing Meaning,12=Regex,13=Short Label,14=Definition,15=Requirements,...

    String val = worksheet.getCellAt(1, 1).getData$()
    if (LABEL_ELEMENT != val) {
      error("non-Element column at 1-1: $val")
      return
    }
    val = worksheet.getCellAt(1, 3).getData$()
    if (LABEL_CARD != val) {
      error("non-Cardinality column at 1-3: $val")
      return
    }
    val = worksheet.getCellAt(1, 5).getData$()
    int typeIdx = 5
    if (LABEL_TYPE == val) {
      val = worksheet.getCellAt(2, typeIdx).getData$()
      if ('DomainResource' !=  val && 'Resource' != val) {
        warn("expected Resouce as type: $val")
      }
    } else {
      // TODO: if these assumptions fail then need to locate the Type column
      warn("non-Type column at 1-5: $val")
      typeIdx = 0
    }

    val = worksheet.getCellAt(1, 6).getData$()
    int isModIdx = LABEL_IS_MODIFIER == val ? 6 : 0
    // println 'isModIdx='+isModIdx

    val = worksheet.getCellAt(1, 14).getData$()
    int defIdx = LABEL_DEFINITION == val ? 14 : 0

    val = worksheet.getCellAt(2, 1).getData$() // resource name
    if(val) {
      // use actual Resource name rather than filename
      if (val.startsWith("!")) warn("resource: $val disabled") // debug
      resourceName = val
    }

    // base resource-level mapping: name -> Detail{ cardinality + type } e.g. Condition.subject => card=1..1, type=Patient
    Map<String, Details> mapping = new LinkedHashMap<>()
    // skip header and resource-level rows
    String type, description
    for (int i = 3; i < 100; i++) {
      Cell cell1 = worksheet.getCellAt(i, 1) // Element name (e.g. Encounter.location)
      val = cell1.getData$()
      if (val == null || val == '') {
        // println "\tbreak at $i rows"
        break
      }
      String card = worksheet.getCellAt(i, 3).getData$() // Cardinality
      if (typeIdx > 0) {
        type = worksheet.getCellAt(i, typeIdx).getData$().trim() // Type
      } else type = ''
      if (defIdx > 0) {
        description = worksheet.getCellAt(i, defIdx).getData$().trim() // Definition
      } else description = ''
      boolean isModifier = false
      if (isModIdx > 0) {
        def value = worksheet.getCellAt(i, isModIdx).getData$()
        if (value) {
          isModifier = value == '1' || value.equalsIgnoreCase('Y')
          // if (isModifier) println "\t$val [isModifier]"
        }
      }
      mapping.put(val, new Details(card, type, description, isModifier))
    }

    //printProfile()
    //println "\t" + mapping // debug
    // out.println "elements: " + mapping.keySet() + "<P>"

    profileGroupStart(profiles)
    // now scan each profile against the base resource
    profiles.each {
      // println "\n\tprofile: " + it
      profile = it
      checkProfile(mapping, new File(dir, it.sourceFile))
    }
    profileGroupEnd(profiles)
  } // checkDirectory


  void checkProfile(Map<String, Details> mapping, File file) {
    Workbook xlWorkbook = reader.getWorkbook(file.getAbsolutePath())

    // 1. start with Metadata worksheet
    Worksheet worksheet = xlWorkbook.getWorksheet('Metadata')
    if (worksheet == null) {
      error('Metadata not found')
      return
    }

    String worksheetName = null
    // skip over header row
    for (int i = 2; i < 14; i++) {
      // find rows with id and published.structure labels in first column
      Cell cell = worksheet.getCellAt(i, 1)
      String data = cell.getData$()
      if ('id' == data) {
        profile.id = worksheet.getCellAt(i, 2).getData$() // Metadata column 2 = profile id
        //out.println id
      } else if ('published.structure' == data) {
        worksheetName = worksheet.getCellAt(i, 2).getData$()
        break
      } else if ('description' == data) {
        profile.description = worksheet.getCellAt(i, 2).getData$().trim()
        //elements must be supported by CQF rules and measure engines
        // following check: CQF-profile specific
      }
    }

    if (!worksheetName) {
      error("missing published.structure")
      return
    }

    profile.worksheetName = worksheetName

    if (!profile.id) {
      error("missing id")
      return
    }

    // 2. use structure worksheet in profile
    worksheet = xlWorkbook.getWorksheet(worksheetName)
    if (worksheet == null) {
      error(worksheetName + " worksheet not found")
      return
    }

    //int eltIdx = 4, cardIdx = 6, typeIdx = 7, mustIdx = 8
    Map<String, Integer> index = new TreeMap<>()
    try {
      Row row = worksheet.getRowAt(1)
      // iterate over columns in row 1 of structure worksheet
      for (int i = 1; i < 50; i++) {
        // structure worksheet cannot have empty column name so stop when find empty cell
        String val = row.getCellAt(i).getData$()
        if (val == null || val.isEmpty()) break
        index.put(val, i)
      }
      checkIndex(index)
      // column index: [??? Mapping:22, Aliases:5, Binding:10, Card.:6, Comments:18, Committee Notes:24, ... ]
      // template worksheet has 24 columns in Structure work??
      // following 4 columns are required, others are optional
      // getIndex() throws IllegalArgumentException if column label not found
      verifyIndex(index, LABEL_ELEMENT, 4)
      verifyIndex(index, LABEL_CARD, 6)
      verifyIndex(index, LABEL_TYPE, 8)
      verifyIndex(index, LABEL_MUST_SUPPORT, 9)
    } catch (Exception e) {
      error(e.toString())
      return
    }

    profileCount++
    processProfile(mapping, worksheet, index)
  } // checkProfile


  private void verifyIndex(Map<String, Integer> map, String key, int defaultValue) {
    Integer idx = map.get(key)
    if (idx == null) throw new IllegalArgumentException("Require $key column at $defaultValue")
    // if (idx != defaultValue) warn("expected $key at $defaultValue but was $idx")
  }

  /**
   * Process resource profile worksheet
   *
   * @param mapping   Mapping of base resource elements to their definition
   * @param worksheet Active worksheet for this profile
   * @param indexMap  Map of all column names to column index
   */
  void processProfile(Map<String, Details> mapping, Worksheet worksheet,
                    Map<String, Integer> indexMap) {
    // override in subclasses
    printHeader()
    //String name = profile.name
    //if (name.startsWith('!')) name = name.substring(1)
    // if (name != profile.id) error("mismatch name=$name id=${profile.id}")
  }

  void printResource() {
    if (resourceName && resourceName != lastResource) {
      println()
      println resourceName
      lastResource = resourceName
    }
  }

  void printHeader() {
    printResource()
    if (profile && profile != lastProfile) {
      println profile.id
      lastProfile = profile
    }
  }

  /**
   * Called at startup before any profiles are processed
   */
  void setup() {
    // implement in subclasses
  }

  /**
   * Called at finish when all profiles are processed
   */
  void tearDown() {
    // implement in subclasses
  }

  /**
   * Called when structure worksheet first row is processed
   * but before checking required Element, Type, etc. columns
   * @param profile
   * @param index Index map
   */
  void checkIndex(Map<String, Integer> index) {
    // implement in subclasses
  }

  /**
   * Called when scanner starts a new resource and is about to process
   * each associate profile.
   * @param profiles
   */
  void profileGroupStart(List<Profile> profiles) {
    // implement in subclasses
  }

  /**
   * Called when scanner ends a new resource and all associated profiles.
   * @param profiles
   */
  void profileGroupEnd(List<Profile> profiles) {
    // implement in subclasses
  }

  void warn(String msg) {
    printHeader()
    println "WARN: $msg"
  }

  void error(String msg) {
    printHeader()
    println "ERROR: $msg"
  }

  @TypeChecked
  static class Profile {

    /**
     * Name of profile/package as defined in resource packages worksheet (e.g. cqf-condition).
     * Name can have '!' prefix to indicate the profile is disabled.
     */
    final String name

    /**
     * source of profile/package as defined in resource packages worksheet (e.g. condition-cqf-profile-spreadsheet.xml)
     */
    final String sourceFile

    /**
     * id as defined in resource profile spreadsheet Metadata worksheet for id value
     * which should be same as the name defined in the base resource profile.
     */
    String id

    /**
     * published.structure as defined in resource profile spreadsheet Metadata worksheet.
     *
     * This name is used for website in published profile page;
     * Example:
     *     id=cqf-medicationadministration
     *     worksheetName=cqf-ma-notgiven
     *     url=http://hl7-fhir.github.io/cqf-medicationadministration-notgiven-cqf-ma-notgiven.html
     */
    String worksheetName

    /**
     * description as defined in resource profile spreadsheet Metadata worksheet
     */
    String description

    Profile(String name, String sourceFile) {
      this.name = name
      this.id = name.startsWith('!') ? name : name.substring(1)
      this.sourceFile = sourceFile
    }

    boolean isDisabled() {
      return name.startsWith('!')
    }

    String toString() { return sourceFile }
  }

  /**
   * Type details for a FHIR element
   */
  @TypeChecked
  static class Details {
    final String card, type, description
    final boolean isModifier

    Details(String card, String type, String description, boolean isModifier) {
      this.card = card
      this.type = type ?: ''
      this.description = description
      this.isModifier = isModifier
    }
    String toString() {
      return 'card=' + card + ', type=' + type
    }
  }

} // FhirProfileScanner