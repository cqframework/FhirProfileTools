// $Id:  QuickHtmlGenerator.groovy,v 1.12 2015/07/28 14:30:40 mathews Exp $
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
import org.hl7.fhir.instance.model.Bundle
import org.hl7.fhir.instance.model.ElementDefinition
import org.hl7.fhir.instance.model.ElementDefinition.TypeRefComponent
import org.hl7.fhir.instance.model.ElementDefinition.ElementDefinitionBindingComponent
import org.hl7.fhir.instance.model.StructureDefinition
import org.hl7.fhir.instance.model.UriType
import org.hl7.fhir.instance.model.ValueSet

import java.text.SimpleDateFormat
import java.util.regex.Matcher

/**
 * Created by Jason Mathews on 2/13/2015.
 *
 * Changes:
 *  3/06/15 remove Reference() prefix to type lists
 *  3/11/15 Split must support and non-must support fields in separate panes on class-level page
 *  3/13/15 add jquery with tab panels for separate must support and other lists
 *  3/16/15 Suppress implicit fields on the resource structure (e.g. meta, contained, etc.)
 *          Add binding name + strength w/links in FHIR spec
 *  3/18/15 Sort element names for must support list so extensions are in sorted order
 *          Add is Modifier flag on class-level pages
 *  3/19/15 Add favicon.ico to web pages
 *  3/27/15 Auto-add referenced base resources if no QICore profile for it exists
 *  3/28/15 Add All Fields tab
 *  3/29/15 Add font-awesome vector symbols for flags
 *  3/30/15 Moved cardinality in new middle column in class summary table
 *  3/31/15 Make HTML output xhtml.
 *  5/11/15 Add check to ignore extension component; e.g. DiagnosticReport.extension.valueReference
 *  5/12/15 Add Strike-out to element name + cardinality if 0..0
 *  5/21/15 Refactor StructureDefinition extension defs in new ExtensionDef class
 *  6/12/15 Break sub-elements into separate class-level pages
 *  6/12/15 Suppress printing sub-elements (dotted notation) on top-level class pages (e.g. Encounter.hospitalization.admitSource)
 *  6/24/15 Remove cardinality column from table and add CQL types
 *  6/25/15 Handle type code=BackboneElement, add specialCaseElements mappings
 *  7/09/15 Use value set names in link anchor text and description for mouse-over tool tip
 *  7/14/15 Fix urls to valuesets to reflect new URL naming scheme in FHIR
 *  7/24/15 cqf valuesets are now published in "cqf" sub-folder (likewise for daf)
 *  7/31/15 Handle complex types as its own class. Treat same as resources
 *          with detailed class-level pages linked by type to other pages.
 *
 */
class QuickHtmlGenerator extends FhirSimpleBase {

  /**
   * base URL where FHIR spec is published;
   * required to reference valuesets in element bindings
   */
  static final String baseUrl = 'http://hl7-fhir.github.io/'
  //static final String baseUrl = 'http://hl7.org/fhir/2015May/' // DSTU2 ballot snapshot

  static final String outDir = "html"

  final String shortTitle = 'QUICK'
  final String overviewTitle = 'QUICK Data Model'

  //static final String baseUrl = '../../'
  //static final String outDir = "../build/publish/qicore"
  //static final String outDir = "../public/target/quick"

  static final String dateGenLabel

  static final String TARGET_RESOURCE = ''
  final List<String> classes = new ArrayList<>()
  final Map<String, StructureDefinition> profiles = new TreeMap<>()

  // keep track of QICore profiles by class name to detect references to resources that have no associated QICore profile
  final Set<String> qicoreProfiles = new HashSet<>()

  final Map<String, StructureDefinition> resources = new HashMap<>() // cache for base resources

  final Set<String> typeCodes = new TreeSet<>()
  final Set<String> typeCheck = new TreeSet<>()
  final Map<String, ValueSet> valuesets = new HashMap<>()

  final Set<IndexInfo> index = new TreeSet<>()

  final Map<String, String> uriToClassName = new HashMap<>()

  static final String keyLegend =
          '''\n<p style="margin: 10px">Key: <i class="fa fa-check fa-fw"></i>&nbsp;=&nbsp;Must support, <i
 class="fa fa-star fa-fw"></i>&nbsp;=&nbsp;QICore-defined extension, <i class="fa fa-exclamation fa-fw"></i>&nbsp;=&nbsp;Is-Modifier</p>'''

  static final Set<String> implicitElements = new HashSet<>()
  static final Set<String> primTypes = new HashSet<>()

  static final Set<String> complexTypes = new HashSet<>()
  // structure to target class name mappings for logical model
  static final Map classNames = [
          'Adverseevent'              : 'AdverseEvent',
          'Allergyintolerance'        : 'AllergyIntolerance',
          'Bodysite'                  : 'BodySite',
          'Communicationrequest'      : 'CommunicationRequest',
          'Deviceuserequest'          : 'DeviceUseRequest',
          'Deviceusestatement'        : 'DeviceUseStatement',
          'Diagnosticorder'           : 'DiagnosticOrder',
          'Diagnosticreport'          : 'DiagnosticReport',
          'Familyhistory'             : 'FamilyHistory',
          'Familymemberhistory'       : 'FamilyMemberHistory',
          'Imagingstudy'              : 'ImagingStudy',
          'Immunizationrec'           : 'ImmunizationRecommendation',
          'Immunizationrecommendation': 'ImmunizationRecommendation',
          'Medicationadministration'  : 'MedicationAdministration',
          'Medicationdispense'        : 'MedicationDispense',
          'Medicationprescription'    : 'MedicationPrescription',
          'Medicationstatement'       : 'MedicationStatement',
          'Procedurerequest'          : 'ProcedureRequest',
          'Referralrequest'           : 'ReferralRequest',
          'Relatedperson'             : 'RelatedPerson'
  ]

  static final Map<String, String> specialCaseElements = [
          // special cases of elements that don't have children explicitly defined in the path
          'DiagnosticOrder.item.event' : 'DiagnosticOrder.event'
  ]

  // mapping of fhir types to CQL types
  static final Map cqlTypeMap = [
          'boolean'            : 'Boolean',
          'Coding'             : 'Code',
          'CodeableConcept'    : 'Concept',
          'date'               : 'DateTime',
          'dateTime'           : 'DateTime',
          'decimal'            : 'Decimal',
          'instant'            : 'DateTime',
          'integer'            : 'Integer',
          'string'             : 'String',
          'Period'             : 'Interval',
          'Range'              : 'Interval',
          'time'               : 'Time',
          //'Quantity'           : 'Quantity',
          'uri'                : 'Uri',
  ]

  static {

    SimpleDateFormat df = new SimpleDateFormat('EEE, MMM dd, yyyy h:mma')
    dateGenLabel = "Generated on ${df.format(System.currentTimeMillis())}"

    // ignore resourceName.text, resourceName.text, etc.
    implicitElements.addAll([
            'id', 'meta', 'implicitRules', 'language', 'text', 'contained',
            'extension.url', 'extension.value[x]',

            'birthDate.value', // if type is Date, ContactPoint, etc. then want to suppress the subcomponents
            'birthDate.verification',
            'telecom.preferred',
            'telecom.system',
            'telecom.use',
            'telecom.value',

            'animal.id', 'animal.species', 'animal.breed', 'animal.genderStatus' // human only
    ])

    // e.g. http://hl7-fhir.github.io/datatypes.html#CodeableConcept
    primTypes.addAll([
            //'Address',
            //'Age',
            //'Attachment',
            'base64Binary',
            'boolean',
            'code',
            'Coding',
            'CodeableConcept',
            //'ContactPoint',
            'date',
            'dateTime',
            'decimal',
            //'Duration',
            //'HumanName',
            //'Identifier',
            'instant',
            'integer',
            'unsignedInt',
            'positiveInt',
            'oid',
            'Period',
            //'Quantity',
            'Range',
            //'Ratio',
            //'SampledData',
            'string',
            'time',
            //'Timing',
            'uri',
    ])

    complexTypes.addAll([
            'Address',
            'Age',
            'Annotation',
            'Attachment',
            'ContactPoint',
            'Duration',
            'HumanName',
            'Identifier',
            'Money',
            'Quantity',
            'Ratio',
            'SampledData',
            'Timing',
    ])

  }

  QuickHtmlGenerator(File publishDir) {
    super(publishDir)
  }

  @TypeChecked
  void setup() {
    //if (devMode) println "DEBUG: dev-mode enabled"

    File docsDir = new File("$outDir/pages")
    if (!docsDir.exists() && !docsDir.mkdirs()) {
      throw new IOException("failed to create docs folder structure")
    }
  }

  @TypeChecked
  void generateHtml() {
    createDetailPages()
    createAllClassesFrame()
    createOverviewSummary()
    copyIndexTemplate()
    createIndexPage()

    //println()
    //println typeCodes // [id]
    println()
    println typeCheck
    // [Address, Age, Annotation, Attachment, ContactPoint, Duration, HumanName, Identifier, Ratio, SampledData, Timing, code, id, oid, positiveInt, unsignedInt]
    System.exit(0)
  }

  @TypeChecked
  void loadExtensions() {
    // pre-load extensions?
    // use bundle = extension-definitions.xml
    def file = new File(publishDir, 'extension-definitions.xml')
    if (!file.exists()) {
      println "ERROR: extension defs not available"
      return
    }
    println "INFO: loadExtensions"
    def is = new FileInputStream(file)
    try {
      Bundle extBundle = (Bundle) xmlParser.parse(is)
      // add resources to extensionDefs
      extBundle.getEntry().each {
        def r = it.getResource()
        if (r instanceof StructureDefinition) {
          StructureDefinition extDef = (StructureDefinition) r
          // println extDef.getUrl()
          ExtensionDef.putDefinition(extDef.getUrl(), extDef)
        }
      }
      // println exts.getClass().getName()
    } finally {
      IOUtils.closeQuietly(is)
    }
  }

  @TypeChecked
  void process(File f) {
    // if (!(f.getName() =~ /.*qicore-.*profile.xml$/)) return

    StructureDefinition profile = (StructureDefinition) parseResource(f)
    String id = profile.getId()

    println()
    println "-" * 40
    println id
    final String className = getClassName(id)
    if (profiles.containsKey(className)) {
      // possibly profile id changed and publish folder has both new and old versions
      println "ERROR: duplicate profile class: $className"
      return
    }
    //? classes.add(className) // use profiles.keySet()
    profiles.put(className, profile)
    if (profile.hasUrl()) uriToClassName.put(profile.getUrl(), className)
    //? http://hl7.org/fhir/StructureDefinition/patient-qicore-qicore-patient,
    //? http://hl7.org/fhir/StructureDefinition/patient-qicore-patient
    
	qicoreProfiles.add(className) // keep track of QICore profiles	
	//qicoreProfiles.add(getResourceName(profile)) // keep track of QICore profiled resources    
  }

// ---------------------------------------------------------
// create detail pages
// ---------------------------------------------------------
  @TypeChecked
  void createDetailPages() {

    println "=" * 40
    println "profiles: " + uriToClassName.keySet().join("\n\t")
    /*
    uriToClassName.keySet().each {
      int ind = it.indexOf("qicore")
      if (ind > 0 && it.indexOf("qicore", ind+6) == -1) println "X: $it"
    }
    */
    // println uriToClassName
    // [http://hl7.org/fhir/StructureDefinition/organization-qicore-organization:Organization,
    // http://hl7.org/fhir/StructureDefinition/encounter-qicore-encounter:Encounter,

    // TODO - prescan all profiles and all element types. if reference classes in differential not present (e.g. RelatedPerson)
    // e.g. AdverseEvent.cause.item ref=DiagnosticStudy
    //? if (!devMode)  // disabled for now
      createOtherReferences()

    try {

      profiles.each { String className, StructureDefinition profile ->
        generateHtml(className, profile)
      }

    } catch(Exception e) {
      println "-" * 60
      e.printStackTrace(System.out)
    }

    println "codeList: $typeCodes" // debug
  }

  /**
   * if resources refer to other resources that don't have associated QICore profiles
   * then recursively add the resource profiles as classes.
   * e.g. Resource, Media, Questionnaire, VisionPrescription, etc.
   */
  @TypeChecked
  private void createOtherReferences() {
    println "Load Resources"
    // target URI for domain resource
    final String targetUri = 'http://hl7.org/fhir/StructureDefinition/'

    List<String> profileNames = new ArrayList<>(profiles.keySet())
    // todo: note we're not recursively checking ref types for the new resources that are added
    // which may refer to yet unknown resources; e.g., Order is direct Ref in Appointment.order
    for (int i = 0; i < profileNames.size(); i++) {
      final String className = profileNames.get(i)
      def profile = profiles.get(className)
      // println "== $className ==" // debug
      //Map<String, StructureDefinition> profileMap = new TreeMap<>(profiles)
      //profileMap.each { String className, StructureDefinition profile ->
      profile.getSnapshot().getElement().each { elt ->
        if (!elt.hasType()) return
        elt.getType().each { type ->
          String code = type.getCode()
          // println "X: type code=$code"
          if (code == 'Reference') {
            if (type.hasProfile()) {
              final String uri = getProfile(type) // TODO: type changed from String to List<UriType> (fix)
              if (uri.startsWith(targetUri) && !uri.contains('qicore-') && !uriToClassName.containsKey(uri)) {
                // uri = http://hl7.org/fhir/StructureDefinition/DeviceMetric
                // resourceName = DeviceMetric (class name)
                def resourceName = uri.substring(targetUri.length())
                if (checkResource(resourceName, uri, elt)) {
                  profileNames.add(resourceName) // recursively add new resource refs as find them
                }
              }
            }
          } // Reference?
          else if (complexTypes.contains(code) && checkResource(code, null, elt)) {
            // e.g. Address
            profileNames.add(code) // recursively add new resource refs as find them
          } // complex type?
        } // each type
      } // each elemet
    } // each profile
  }


  @TypeChecked
  boolean checkResource(String resourceName, String uri, ElementDefinition elt) {
    if (profiles.containsKey(resourceName) /* || resourceName == 'Resource' */) {
      // println "X: dup $resourceName"
      return
    }
    def baseRes = resources.get(resourceName)
    if (baseRes == null) {
      File file = new File(publishDir, resourceName.toLowerCase(Locale.ROOT) + ".profile.xml")
      if (file.exists()) {
        try {
          if (uri) println "X: load resource $uri"
          else println "X: load resource $resourceName"
          //if (resourceName == 'Resource') println elt.getPath()//debug
          baseRes = (StructureDefinition) parseResource(file)
          resources.put(resourceName, baseRes)
        } catch (Exception e) {
          println "ERROR: failed to parse: " + file.getName()
        }
      } else println "WARN: resource $resourceName does not exist"
    }

    if (baseRes != null) {
      //final String resClassName = getClassName(baseRes.getId())
      //if (resourceName != resClassName) println "XX: $resourceName // $resClassName"
      if (!profiles.containsKey(resourceName)) {
        if (elt) println "X: add class $resourceName ref=" + elt.getPath()
        else println "X: add class $resourceName"
        //? classes.add(resourceName) // suppress non-qicore resources from TOC class list
        profiles.put(resourceName, baseRes)
        if (!uri) uri = baseRes.getUrl()
        uriToClassName.put(uri, resourceName)
        return true
      }
    }

    return false
  } // checkResource

// ---------------------------------------------------------
// create detailed class-level pages
// ---------------------------------------------------------
  @TypeChecked
  void generateHtml(String className, StructureDefinition profile) {

    final String resourceName = getResourceName(profile)

    // if (className != resourceName) println "X: class=$className res=$resourceName" // class=AdverseEvent res=Basic

    // if (resourceName != 'Encounter') return // only one

    println "\nclass: $className"

    /*
    //debug
    // dump elements that should have bindings but do not
    if (qicoreProfiles.contains(className)) {
      profile.getDifferential().getElement().each { elt ->
        if (!elt.hasBinding() && elt.hasType()) {
          // boolean isCode
          for (TypeRefComponent type : elt.getType()) {
            String code = type.getCode()?.toLowerCase()
            if (code && code.startsWith("cod")) {
              // coding, code, CodeableConcept, etc.
              printf "AC: %s %s%n", elt.getPath(), type.getCode()
              break
            }
          }
        }
//      else if (elt.hasBinding() && elt.getPath().endsWith("[x]")) {
//        def binding = elt.getBinding()
//        if (binding.hasValueSetReference()) binding = binding.getValueSetReference().getReference()
//        else if(binding.hasValueSetUriType()) binding = binding.getValueSetUriType().getValueAsString()
//        printf "ZZ: %-38s %s%n", elt.getPath(),  binding
//      }
      }
    }
    //debug
    */

    final String resourceNamePrefix = resourceName + "."
    Set<String> pathSet = new HashSet<>()
    Set<ElementDefinitionHolder> elts = new TreeSet<>(new ElementDefinitionHolderComparator())
    def snapList = profile.getSnapshot().getElement()

    snapList.each { elt ->

      if (!elt.hasPath()) return

      if (elt.hasSlicing()) {
        // TODO: add to slicing list
        println "skip slice: " + elt.getPath()
        return
      }

      String path = elt.getPath()

      //debug
      // dump [x] elements that have bindings
      /*
      if (elt.hasBinding() && elt.getPath().endsWith("[x]")) {
        def binding = elt.getBinding()
        if (binding.hasValueSetReference()) binding = binding.getValueSetReference().getReference()
        else if(binding.hasValueSetUriType()) binding = binding.getValueSetUriType().getValueAsString()
        else binding = "??" + binding.getStrength().getDisplay()
        printf "ZZ: %-38s %s%n", elt.getPath(),  binding // .replace("/ValueSet/","/vs/")
      }
      */

      //debug
      // dump required bindings that have no reference valueset - see gforge issue #8115
      /*
      if (elt.hasBinding()) {
        def binding = elt.getBinding()
        if (binding.hasValueSetReference()) binding = binding.getValueSetReference().getReference()
        else if(binding.hasValueSetUriType()) binding = binding.getValueSetUriType().getValueAsString()
        else if(!binding.hasValueSet()) {
          binding = "??" + binding.getStrength().getDisplay()
          // printf "ZZ: %-48s %s%n", elt.getPath(), binding // .replace("/ValueSet/","/vs/")
        }
        printf "ZZ: %-48s %s%n", elt.getPath(), binding // .replace("/ValueSet/","/vs/")
      }
      */

      if (elt.hasMax() && elt.getMax() == '0') {
        // skip elements that are removed from the resource; e.g. Patient.animal
        // not part of the class object model
        println "skip: $path"
        return
      }

      if (resourceName == path) return // skip resource def

      List<TypeRefComponent> types = elt.getType()
      if (resourceName == 'Resource') {
        if (path.endsWith(".meta")) return // skip
      } else {
        if (path.endsWith('.id') && types && 'id' == types.get(0).getCode()) {
          // ignore internal identifier
          return
        }

        // skip special fields in snapshot (e.g. id, meta, implicitRules, etc.)
        // these are inherited from Resource
        if (path.length() > resourceName.length() + 1 &&
                (path.endsWith('.quantity.comparator') ||
                        implicitElements.contains(path.substring(resourceName.length() + 1)))) {
          // println "x: ignore $path"
          return
        }
      }

      println "\n path : " + path // debug

      /*
      // lsit all code-typed elements
      if (qicoreProfiles.contains(className) && elt.hasType()) {
        for (TypeRefComponent type : types) {
          String code = type.getCode()?.toLowerCase()
          if (code == "code") {
            printf "CC: %s %s%s%n", elt.getPath(), type.getCode(), elt.hasBinding() ? " [Y]" : ""
            break
          }
        }
      }
      */

      //debug
      // dump all elements (including extensions) that have bindings
	  /*
      if (elt.hasBinding() && elt.hasType()) {
        def binding = elt.getBinding()
        def bindingRef
        if (binding.hasValueSetReference()) bindingRef = binding.getValueSetReference().getReference()
        else if (binding.hasValueSetUriType()) bindingRef = binding.getValueSetUriType().getValueAsString()
        else bindingRef = "??"
        printf "BB: %-42s %s (%s)%n", elt.getPath(), bindingRef, binding.getStrength().getDisplay()
      }
      //debug
      // dump codable elements that should have bindings but do not - review to add appropriate bindings
      else*/ if (qicoreProfiles.contains(className) && !elt.hasBinding() && elt.hasType()) {
        for (TypeRefComponent type : types) {
          String code = type.getCode()?.toLowerCase()
          if (code && code.startsWith("cod")) {
            // Coding, code, CodeableConcept, etc.
            printf "AC: %s %s%n", elt.getPath(), type.getCode()
            break
          }
        }
      }
      //debug

      if (types.size() > 1 && !path.endsWith("[x]") && types.get(0).getCode() != 'Reference')
        println "WARN: multi-type not ending with [x]: $path"

      String pathName = path
      final boolean isExtension = types && 'Extension' == types.get(0)?.getCode()
      final boolean mustSupport = elt.getMustSupport()

      if (isExtension) {
        if (!elt.hasName() && !types.get(0).hasProfile()) {
          // if not must support then don't care about names for extensions for now
          if (/*!path.endsWith('.modifierExtension') &&*/ mustSupport) {
            println "WARN: extension with no name or profile skipped: $path"
          }
          return
        }
        if (!path.contains('.')) {
          println "INFO: ignore ext: $path"
          return
        }

        if (elt.hasName()) {
          String name = elt.getName()
          if (name.startsWith(resourceNamePrefix)) name = name.substring(resourceNamePrefix.length())
          pathName = name
          // path / name
          // Procedure.extension / reasonNotPerformed
          // Patient.extension.extension / citizenship.period
          // FamilyHistory.relation.condition.extension / relation.condition.abatement
          if ((path != resourceName + '.extension' && path != resourceName + '.modifierExtension') || name.contains('.')) {
            def pathNormal = path
            if (pathNormal.startsWith(resourceNamePrefix)) pathNormal = pathNormal.substring(resourceNamePrefix.length())
            // path (with resource name prefix stripped) / name
            // extension / reasonNotPerformed
            // extension.extension / citizenship.period
            // relation.condition.extension / relation.condition.abatement
            String[] pathParts = pathNormal.split('\\.')
            String[] nameParts = name.split('\\.')
            if (pathParts.length != nameParts.length) {
              println "Z:ERROR: $resourceName: name mismatch to path\nZ:$pathNormal\nZ: $name\nZ:--" // bad '.' count
              // path : Basic.modifierExtension
            } else {
              for (int i = 0; i < pathParts.length; i++) {
                def s = pathParts[i]
                if (s != 'extension' && s != nameParts[i]) {
                  println "Z:ERROR: $resourceName: name mismatch to path at $i\nZ:$pathNormal\nZ: $name\nZ:--"
                  // sub-path mismatch
                  break
                }
              }
            }
          }
          // else println "X: extension: $path // $pathName" // e.g. CommunicationRequest.extension // name=reasonRejected

          //path = name
          /*
          int ind = name.lastIndexOf('.')
          // for extensions pathName is the unique identifier for that extension
          // path=Patient.extension.extension name=nationality.period => Patient.extension.extension.nationality.period
          if (ind >= 0) {
            if (name.startsWith(resourceName))
              name = name.substring(resourceName.length() + 1)
            pathName += "." + name
          } else {
            // println "INFO: check ext name: $path / $name"
            pathName += '.' + name
          }
          */
        } //else println "X: extension: no name: $path"

        // dump extensions with invariants
        //debug
        // if (elt.hasConstraint()) println "XI: $pathName" // none ??
        //debug

      } // isExtension?
      else if (path.contains('.extension.')) {
        // extension sub-component
        //  DiagnosticReport.extension.valueReference type=Reference
        //  Encounter.extension.extension.valueReference type=Reference
        //  Encounter.extension.extension.url type=uri
        //if (path.endsWith('.valueReference')) {
        println "X: ignore extension component: $path"
        return
        //}
        //println "X: extension in name: $path [non-ext]"
      }

      if (!pathSet.add(pathName)) {
        // duplicate element: e.g. ProcedureRequest.priority
        if ((isExtension || path.contains('.extension.')) && !elt.hasName()) {
          // not errors: e.g., Patient.extension.value[x], Patient.extension.id
          // println "INFO-ERR: skip ext $pathName"
          // ignore ??
        } else println "ERROR: duplicate element: $pathName"
        return
      }

      if (!pathName.startsWith(resourceNamePrefix)) {
        // many extensions don't have resource prefix in name; e.g. birthPlace rather than Patient.birthPlace
        if (!isExtension) println "RR: no resource prefix: $pathName"
        pathName = resourceNamePrefix + pathName
      }
      // Procedure: [ bodySite, bodySite.site[x], device, device.action, device.manipulated,
      //              performer, performer.person, performer.role, relatedItem, relatedItem.target, relatedItem.type ]
      if (!elts.add(new ElementDefinitionHolder(elt, pathName))) {
        println "ERROR: duplicate element: $pathName"
      }
    } // each element

    List<ElementDefinitionHolder> eltList = new ArrayList<>(elts)
    ElementDefinitionHolder edh = new ElementDefinitionHolder(snapList.get(0))
    processElementsIntoTree(edh, 0, eltList)

    if (edh.getChildren().isEmpty()) println "ERROR: empty list for " + edh.path
    else generateClassHtml(profile, resourceName, className, edh)

  } // generateHtml

// ---------------------------------------------------------

  void generateClassHtml(StructureDefinition profile, String resourceName, String className,
                         ElementDefinitionHolder edh) {

    final String id = profile.getId()

    def writer = new FileWriter("$outDir/pages/${className}.html")
    // Need ISO-8859-1 encoding because some descriptions contain non-UTF8 characters
    writer.println('<?xml version="1.0" encoding="ISO-8859-1"?>')
    writer.println('<!DOCTYPE HTML>')
    def html = new groovy.xml.MarkupBuilder(writer)
    html.html(xmlns: "http://www.w3.org/1999/xhtml", "xml:lang": "en", lang: "en") {
      // html.html {
      head {
        title(className)
        meta('http-equiv': "X-UA-Compatible", content: "IE=9")
        link(rel: 'icon', type: 'image/ico', href: '../resources/favicon.ico')
        link(rel: 'stylesheet', type: 'text/css', href: '../stylesheet.css', title: 'Style')
        // link(rel: 'stylesheet', type: 'text/css', href: '../jquery/jquery-ui.css')
        //link(rel: 'shortcut icon', href: '../resources/favicon.png')
        link(rel: "stylesheet", type: "text/css", href: "../font-awesome/css/font-awesome.min.css")
      }
      body {

        createDetailNavbar(html, className)

        mkp.yieldUnescaped('\n<!-- ======== START OF CLASS DATA ======== -->')
        div(class: 'header') {
          //String name = getClassName(id)
          h2(title: "Class $className", class: 'title') {
            mkp.yield(className)
          }
          /*
          h2(title: "Class $id", class: 'title') {
            String href = String.format('%s%s.html', baseUrl, id.toLowerCase()) //FilenameUtils.getBaseName(f.getName()).toLowerCase())
            a(href:href, "Class $id")
          }
          */
        }

        div(class: 'contentContainer') {
          /*
          if (id.contains("qicore-")) {
            ul(class: 'inheritance') {
              li {
                mkp.yield('FHIR Implementation:')
                a(href: "$baseUrl${id}.html", id) // http://hl7-fhir.github.io/allergyintolerance.html
                br()
              } // li


              // show if this is not a FHIR base resource
//              if ('resource' != profile.getType())
//                li {
//                  mkp.yield('FHIR Parent resource:')
//                  // e.g., resourceName AllergyIntolerance
//                  // http://hl7-fhir.github.io/allergyintolerance.html
//                  a(href: "$baseUrl${resourceName.toLowerCase(Locale.ROOT)}.html", resourceName)
//                } // li
            } // ul
          }
          */

          // add profile-level description
          if (profile.description)
            div(class: 'description') {
              ul(class: 'blockList') {
                li(class: 'blockList', style: 'font-size:115%;') {

                  String desc = ''
                  if (resourceName != 'Basic') {
                    desc = profile.getSnapshot().getElement().get(0).getShort()?.trim()
                    if (desc) {
                      mkp.yield(desc)
                      p()
                    }
                  }

                  //if (resources.containsKey(resourceName)) {
                  //mkp.yieldUnescaped('<p><b>This is a non-QICore profiled resource</b></p>')
                  //}

                  if (resourceName == 'Resource') {
                    dl {
                      dt('Direct Known Subclasses:')
                      dd {
                        profiles.keySet().eachWithIndex { String name, int idx ->
                          if (name != 'Resource') {
                            if (idx) mkp.yield(', ')
                            a(href: "${name}.html", name)
                          }
                        }
                      } // dd
                    } //dl
                  } else {
                    // don't show description for Resource since it's bogus: Base StructureDefinition for Resource Resource
                    String baseDesc = profile.getDescription()?.trim()
                    if (baseDesc && (!desc || baseDesc.replaceAll('\\.\\s*', '') != desc.replaceAll('\\.\\s*', ''))) {
                      // strip punctuation when comparing so following two descriptions are not both printed
                      // short desc:  this is an element
                      // description: this is an element.
                      mkp.yield(baseDesc)
                    }
                  }
                }
              }
            } // div

          div(class: 'summary') {
            ul(class: 'blockList') {
              li(class: 'blockList') {
                ul(class: 'blockList') {
                  li(class: 'blockList') {
                    mkp.yieldUnescaped('\n<!-- =========== FIELD SUMMARY =========== -->')
                    a(name: 'field_summary') {
                      mkp.yieldUnescaped('<!--   -->')
                    }

                    mkp.yieldUnescaped(keyLegend)
                    dumpFields(html, profile, resourceName, className, edh)
                    mkp.yieldUnescaped(keyLegend)

                  } // li
                } // ul
              } // li
            }// ul
          } // div class=summary

        } // div class=contentContainer

        createBottomNavbar(html)

      } // body
    } // html
  }  // generateClassHtml

  @TypeChecked
  static String normalizeClassName(String name) {
    StringBuilder sb = new StringBuilder()
    name.split("\\.").each{ String s ->
      if (sb.length() != 0) sb.append('.')
      sb.append(StringUtils.capitalize(s))
    }
    return sb.toString()
  }

// ---------------------------------------------------------

  void createBottomNavbar(html) {
    html.mkp.yieldUnescaped("""
<!-- ======= START OF BOTTOM NAVBAR ====== -->
<div class="bottomNav">
<span class="legalCopy"><small><font size="-1">
        &copy; HL7.org 2011+. QUICK ${dateGenLabel}.
        </font>
</small></span>
</div>
<!-- ======== END OF BOTTOM NAVBAR ======= -->
""")
  }


// ---------------------------------------------------------
// generate field just for detailed class-level pages
// ---------------------------------------------------------
  void dumpFields(html, StructureDefinition profile, String resourceName, String className,
                  ElementDefinitionHolder edh) {

    List<ElementDefinitionHolder> elts = edh.getChildren()

    //String tabName = 'All'

    // <th class="colFirst" scope="col">
    //? <caption><span>Fields</span><span class="tabEnd">&nbsp;</span></caption>
    html.mkp.yieldUnescaped('''\n<table class="overviewSummary" border="0" cellpadding="3" cellspacing="0"
 summary="Field Summary table, listing fields, and an explanation">
<tr>
<th class="colFirst" scope="col"><i class="fa fa-fw"></i><i class="fa fa-fw"></i><i class="fa fa-fw"></i>&nbsp;Field</th>
<th class="colLast" scope="col">Type and Description
<span style="float: right"><a title="Legend for this format" href="../help.html"><img alt="doco" border="0" src="../resources/help16.png"/></a></span></th>
</tr>
''')
    // <th class="colLast" scope="col" style="border-right-width: 0px">Card.</th>
    int count = 0

    if (elts.isEmpty()) {
      html.tr(class: ('altColor')) {
        td(class: 'colFirst', "No fields")
        td(class: "colLast", '')
        mkp.yieldUnescaped('</table>')
      }
      return
    }

    def diffList = profile.getDifferential().getElement() // debug

    //? def diffMap = listToMap(resourceName, profile.getDifferential().getElement())
    // def snapshot = profile.getSnapshot().getElement()
    elts.each { child ->

      final ElementDefinition elt = child.self

      // TODO: if Encounter.collection.quantity type=Quantity
      // then want to skip subcomponents; e.g. Encounter.collection.quantity.comparator

      String path = elt.getPath()
      //if (snapElt == null) println "WARN: elt not found in snapshot: $path"
      // boolean mustSupport = getMustSupport(snapElt, elt)
      // boolean mustSupport = elt.getMustSupport()

      //boolean mustSupport = getMustSupport(elt, diffMap.get(atrName))
      //if(includeMustSupportOnly && !mustSupport) return

      //String pathName = path
      boolean isExtension = false
      if (elt.hasType() && 'Extension' == elt.getType().get(0)?.getCode())
        isExtension = true
      else if (path.endsWith(".extension") || path.endsWith('.modifierExtension')) {
        println "WARN: element has .extension suffix but not extension type???"
      }

      if (isExtension) {
        if (elt.hasName()) {
          String name = elt.getName()
          /*
          Basic.extension.extension / cause.certainty
          Basic.extension.extension / cause.item
          Encounter.extension.extension / Encounter.relatedCondition.condition
          Encounter.extension.extension / Encounter.relatedCondition.conditionRole
          Goal.extension.extension  / Goal.target.detail
          Goal.extension.extension  / Goal.target.measure
          Patient.extension.extension / citizenship.code
          Patient.extension.extension / citizenship.period
          Patient.extension.extension / clinicalTrial.NCT
          Patient.extension.extension / clinicalTrial.period
          Patient.extension.extension / clinicalTrial.reason
          Patient.extension.extension / nationality.code
          Patient.extension.extension / nationality.period
          */
          // if (name.contains('.')) println "INFO: check ext name: $path / $name"
          path = name

          /*
            int ind = name.lastIndexOf('.')
            if (ind > 0) {
              pathName += name.substring(ind)
            } else {
              println "INFO: check ext name: $path / $name"
              pathName += "." + name
            }
          */
        }
      } // isExtension?

      /*
        <element>
          <path value="Condition.extension"/>
          <name value="concernStatus"/>
          <type>
            <code value="Extension"/>
            <profile value="http://hl7.org/fhir/ExtensionDefinition/us-core-concernStatus"/>
          </type>
        </element>

        => Condition.concernStatus
        */

      //if (name.startsWith(resourceName)) name = name.substring(resourceName.length()+1)

      //if (!name.startsWith(resourceName))
      // path = path.substring(0,ind+1) + name

      // strip resourcename prefix
      // Patient.birthPlace -> birthPlace
      // if (atrName.startsWith(resourceName)) atrName = atrName.substring(resourceName.length()+1)
      // println "EXT $path"
      // include all defined extensions in differential: mustSupport or not
      // else if(includeMustSupportOnly && !mustSupport) return

      //if (mustSupport || isExtension) {

      //}
      //else if () return

      String desc
      if (elt.hasDefinition()) desc = elt.getDefinition()
      else if (elt.hasShort()) desc = elt.getShort()
      else desc = null

      boolean modifier = elt.getIsModifier()
      boolean qiCoreExt = isExtension && getElementByName(diffList, elt) != null
      //boolean addXSuffix = true

      // if (modifier && !elt.getMustSupport() && !resources.containsKey(resourceName)) println "MOD: " + elt.getPath() //debug

      String extTypeCode
      List<TypeRefComponent> extType
      ElementDefinitionBindingComponent binding
      ElementDefinition extElt

      //def type = elt.hasType() ? elt.getType() : snapElt && snapElt.hasType() ? snapElt.getType() : null
      List<TypeRefComponent> type = elt.hasType() ? elt.getType() : null
      if (type == null) println "P0: no type"
      else if (resourceName == TARGET_RESOURCE) println "P0: type=" + typeShortList(type) //debug
      if (isExtension && type && type.get(0).hasProfile()) {
        final String extProfile = getProfile(type.get(0))
        ExtensionDef extProfileDef = createExtensionDef(elt, extProfile) //new ExtensionDef(elt, extProfile)

        final String extProfileEltName = extProfileDef.extProfileEltName
        final StructureDefinition extDef = extProfileDef.getStructure()

        if (extDef == null) {
          if (/*extDef == null &&*/ resourceName == TARGET_RESOURCE) println "P5: $path [no ext def] $extProfile"
          //debug
        } else {
          //? extElt = extProfileDef.getElement()
          // check if sub-element
          if (extProfileEltName) {
            def rootElt = getExtensionByName(extDef.getSnapshot().getElement(), extProfileEltName)
            if (rootElt) {
              //? if (extElt.getMustSupport() && !elt.getMustSupport()) println "X: check MustSupport ext=true elt=false" // debug
              if (rootElt.hasDefinition()) {
                String value = rootElt.getDefinition().trim()
                if (value) desc = value
              }
              //debug
              if (rootElt.hasMin() || rootElt.hasMax())
                printf "XX: subelt card: [%d,%s] ext card: [%d %s] %s%n",
                        elt.hasMin() ? elt.getMin() : -1, elt.getMax(),
                        rootElt.hasMin() ? rootElt.getMin() : -1, rootElt.getMax(),
                        elt.getName()
              //debug
            }
          } else {
            // root-level extension element
            if (extDef.hasDescription()) {
              String value = extDef.getDescription().trim()
              if (value) desc = value
            }

            if (extDef.hasDisplay() && !elt.hasName()) {
              String display = extDef.getDisplay()
              // path = elt.getPath()
              // if (elt.hasName()) printf 'X: ext display=%s path=%s[%s]%n', display, path, elt.getName() // debug
              /*
            // strip off .extension suffix ?
            int ind = path.lastIndexOf('.')
            if (ind > 0) path = path.substring(0,ind)
            / path = path + "." + display
            println "X -> $path"
            */
              path = display
              // use display name ??
            }
          }

          // TODO use display name for path
          if (extDef.hasSnapshot() || extDef.hasDifferential()) {
            if (resourceName == TARGET_RESOURCE) println "P1: $path"//debug
            // extract type from extension definition
            // snapshot path=Extension.valueCodeableConcept
            // differential path=
            /*
            id=communication-qicore-communication
            <snapshot>
              <element>
              <path value="Extension.valueCodeableConcept"/> no value[x] in snapshot ??
              <short value="Value of extension"/>
              <definition value="Value of extension - may be a resource or one of a constrained set of the data types (see Extensibility in the spec for list)."/>
              <min value="0"/>
              <max value="1"/>
              <type>
                <code value="CodeableConcept"/>
              </type>
              </element>
            </snapshot>
            <differential>
            <element>
              <path value="Extension.value[x]"/>
              <type>
              <code value="CodeableConcept"/>
              </type>
              </element>
            </differential>
            */

            extElt = extProfileDef.getElement()

            if (extElt != null) {
              if (resourceName == TARGET_RESOURCE) println "P2: $path got extElt"//debug
              //? if (extElt.getMustSupport() && !elt.getMustSupport()) println "X: check MustSupport ext=true elt=false" // debug
              if (extElt.hasBinding()) {
                println "P5: extension has Binding $path" // debug
                binding = extElt.getBinding()
                /*
                if (elt.hasBinding() && !binding.hasName()) {
                  def oldbinding = elt.getBinding()
                  if (oldbinding.hasName()) printf "XX: no name in ext binding %s %s%n", elt.getPath(), oldbinding.getName()
                  else printf "XX: no name in binding %s%n", elt.getPath()
                }
                */
              }
              if (extElt.hasType()) {
                extType = extElt.getType()
                extTypeCode = getTypeCode(extType, false)

                //debug
                // dump codeable extensions that have no bindings
                if(!extElt.hasBinding() && !elt.hasBinding()) {
                  for(TypeRefComponent t : extType) {
                    if (t.getCode().toLowerCase().startsWith("cod")) { // Code, CodeableConcept, etc.
                      String extName = elt.getName()
                      int ind = extName.lastIndexOf('.')
                      if (ind > 0) extName = extName.substring(ind+1)
                      def code = t.getCode()
                      printf "CC: no binding on ext: %s\t%-29s %s%n", code == 'CodeableConcept' ? 'Concept' : code, elt.getPath().replaceAll('.extension$',''), extName // debug
                      break
                    }
                  }
                }
                //debug

                /*
                //printf "CC: %-10s p=%s eltn=%s binding=%b%n", extTypeCode, path, extProfileEltName, extElt.hasBinding() // debug
                if ((extTypeCode == 'Code' || extTypeCode == 'CodeableConcept') && !extElt.hasBinding()) {
                  String extName = elt.getName()
                  int ind = extName.lastIndexOf('.')
                  if (ind > 0) extName = extName.substring(ind+1)
                  printf "CC: no binding on ext: %s\t%-29s %s%n", extTypeCode == 'CodeableConcept' ? 'Concept' : extTypeCode, elt.getPath().replaceAll('.extension$',''), extName // debug
                }
                */

                /*
                 NOTE: isModifier on first element in extension def snapshot not the value[x] element
                 QICore issue # 89. Gforge issue # 5988
                == extension-adverseevent-qicore-didnotoccur.xml ==
                <id value="adverseevent-qicore-didNotOccur"/>
                <snapshot>
                  <element>
                    <path value="Extension"/>
                    <short value="Indicates if the adverse event was absent"/>
                    <definition value="When true, the resource implies that the adverse event did NOT occur during the stated time period. If true, the severity should not be specified, and the certainty value will be ignored."/>
                    <min value="1"/>
                    <max value="1"/>
                    <type>
                      <code value="Extension"/>
                    </type>
                    <isModifier value="true"/>
                  </element>

                == adverseevent-qicore-qicore-adverseevent.profile.xml ==

                    <element>
                      <path value="Other.extension"/>
                      <name value="AdverseEvent-didNotOccur"/>
                      <short value="Extension"/>
                      <definition value="An Extension"/>
                      <min value="1"/>
                      <max value="1"/>
                      <type>
                        <code value="Extension"/>
                        <profile value="http://hl7.org/fhir/StructureDefinition/adverseevent-qicore-didNotOccur"/>
                      </type>
                      <mustSupport value="true"/>
                    </element>

                   REVIEW: why is the extension ref in profile not defined with isModifier=true ??
                   bug ??
                 */
                //if (resourceName == TARGET_RESOURCE) printf "PC: 1 [%d,%s] [%d,%s]%n", elt.hasMin() ? elt.getMin() : -1, elt.getMax(), extElt.hasMin() ? extElt.getMin() : -1, extElt.getMax()//debug
                if (extElt.getIsModifier()) {
                  // extension-adverseevent-qicore-didnotoccur.xml
                  // TODO: this might be error for sub extension element
                  modifier = true
                  println "AB: extType is modifier $path"
                } else if (!extProfileEltName) {
                  // e.g. AllergyIntolerance.reasonRefuted, Communication.reasonNotPerformed
                  // if sub extension element then may need special check
                  def first = extDef.getSnapshot().getElement().get(0)
                  if (first.isModifier) {
                    modifier = true
                    //println "AB: extType is modifier $path"
                  }
                  // debug
                  if (elt.getMin() != first.getMin() || elt.getMax() != first.getMax())
                    printf 'PC: 2 [%d,%s] [%d,%s] %s%n', elt.hasMin() ? elt.getMin() : -1, elt.getMax(), first.hasMin() ? first.getMin() : -1, first.getMax(), elt.getName()
                  // debug
                }
                if (resourceName == TARGET_RESOURCE) println "P3: $path code=$extTypeCode isMod=" + modifier

                // goal-qicore-goal.target type = "*"
                //if (resourceName == "Goal") printf "EXT: path=%s types=%s%n", path, getTypeCodeList(extType)
                //if (extType == "*") extTypeCode = "Any"
              }
            } else if (resourceName == TARGET_RESOURCE) println "P4: $path [noExt] $extProfile" //debug
          } else if (resourceName == TARGET_RESOURCE) println "P5: $path [no snapshot] $extProfile" //debug
        }
      } // extension

      // output HTML for each element or extension

      /*
      String cardinality
      if (elt.hasMin() && elt.hasMax()) {
        cardinality = String.format("%d..%s", elt.getMin(), elt.getMax())
      } else cardinality = '' // if (snapElt && snapElt.hasMin() && snapElt.hasMax()) {
      */

      html.tr(class: (count++ % 2 == 0 ? 'altColor' : 'rowColor')) {
        td(class: 'colFirst') {
          String name = path
          if (name.startsWith(resourceName)) name = name.substring(resourceName.length() + 1)
          if (resourceName == 'Basic') {
            // e.g. AdverseEvent profile has names with AdverseEvent- prefix in names
            def first = profile.getSnapshot().getElement()?.get(0).getName()
            if (first) {
              // first name QICore-AdverseEvent but prefix=AdverseEvent-
              if (first.startsWith('QICore-')) first = first.substring(7)
              if (name.startsWith(first)) name = name.substring(first.length() + 1)
            }
          }

          // check if some extension elements with multiple non-reference types names don't have [x] suffix -- reported as QICore bug #154
          if (extType && extType.size() > 1 && !elt.getName().endsWith("[x]") && extType.get(0).getCode() != 'Reference') {
            printf "INFO: elt path=%s name=%s %s%n", elt.getPath(), elt.getName(), extTypeCode
            // name=FamilyMemberHistory.condition.abatement [date | Age | boolean]
            // name=Goal.target.detail [Quantity | Range | CodeableConcept]
            // name=Specimen.treatment.treatmentTime [Period | Duration]
            // TODO: add [x] to name
            name += "[x]"
          }

          StringBuilder sb = new StringBuilder()
          if (elt.getMustSupport()) sb.append('<i class="fa fa-check fa-fw" title="Must Support"></i>')
          else sb.append('<i class="fa fa-fw"></i>')
          if (qiCoreExt) sb.append('<i class="fa fa-star fa-fw" title="QICore Extension"></i>')
          else sb.append('<i class="fa fa-fw"></i>')
          if (modifier) sb.append('<i class="fa fa-exclamation fa-fw" title="Is-Modifier"></i>')
          else sb.append('<i class="fa fa-fw"></i>')
          sb.append('&nbsp;')
          mkp.yieldUnescaped(sb.toString())

          sb.setLength(0)
          String escapedName = escapeName(name)
          //? sb.append(String.format('<a name="%s">%s</a>', escapedName, name))

          // don't add to index for resources with no associated QICore profile (E.g. EpisodeOfCare)
          if (!resources.containsKey(className)) {
            String indexName = name
            int ind = indexName.lastIndexOf('.')
            if (ind > 0) indexName = indexName.substring(ind + 1)
            index.add(new IndexInfo(indexName, className + ".html#" + escapedName,
                    //String.format("%s in class <a href='pages/%s.html'>%s</a>", isExtension ? "Extension" : "Field", className, className)))
                    String.format("Field in class <a href='pages/%s.html'>%s</a>", className, className)))
          }
          strong {
            //mkp.yieldUnescaped(sb.toString())
            a(name: escapedName, name)
          } // strong

        } // td

        /*
        td(class: 'colMid') {
          if (cardinality) {
            if (cardinality == '0..0')
              html.span(style: 'text-decoration:line-through') {
                html.code(cardinality)
              }
            else html.code(cardinality)
          }
        } // td
        */

        // List<TypeRefComponent> baseType
        td(class: 'colLast') {
          // if (!mustSupport && !isExtension) mkp.yieldUnescaped('<strike>')
          //? String typeCode
          if (resourceName == TARGET_RESOURCE) println "X: elt=$path"
          if (isExtension && extType /* && extTypeCode != '*'*/) {
            //typeCode = extTypeCode
            // baseType = type
            type = extType
            //? if (elt.
          }/* else if (type) {
          // } else if (elt.hasType()) {
            //Reference(Practitioner)<br>Reference(RelatedPerson)
            typeCode = getTypeCode2(type, false)
            println "P6: check type count=" + type.size() + " " + typeCode // debug
          //} else if (snapElt && snapElt.hasType()) {
            //type = snapElt.getType()
           // typeCode = getTypeCode(type)
            //println "P6: check snap type count="+type.size() + " " + typeCode // debug
          }
          */
          else if (!type) println "X: no type for $path" // debug

          if (elt.hasBinding()) {
            // binding on element in profile overrides binding on extension definition for extensions
            binding = elt.getBinding()
          }

          /*
          //---------------------------------------------------------------------
          //debug
          if (binding) {
            String name = path
            if (elt.hasName()) {
              name = elt.getName()
              if (name.startsWith('QICore-'))  name = name.substring(7)
              //int ind = name.lastIndexOf('.')
              //if (ind > 0) name = path + name.substring(ind)
              //else name = path + "." + name
              if (!name.startsWith(resourceName)) name = resourceName  + "." + name
            }
            def diffElt = getElementByName(diffList, elt)
            def bindName = binding.getName()
            def strength = binding.getStrength()
            if (!isExtension) {
              // check base resource for binding
              def baseRes = resources.get(resourceName)
              if (baseRes == null) {
                def file = new File(dir, resourceName.toLowerCase() + ".profile.xml")
                if (file.exists()) {
                  try {
                    baseRes = (StructureDefinition) parseResource(file)
                    resources.put(resourceName, baseRes)
                    //println "bindingx: got resource $resourceName"
                  } catch (IOException e) {
                  }
                }
              }
              if (baseRes) {
                def baseElt = getElementByName(baseRes.getDifferential().getElement(), elt)
                //if (baseElt == null) {
                  //baseElt = getElementByName(baseRes.getSnapshot().getElement(), elt)
                  //if (baseElt) println "bindingx: found in base snapshot"
                //}
                def baseBinding = baseElt && baseElt.hasBinding() ? baseElt.getBinding() : null
                if (baseBinding) {
                  def baseName = baseBinding.getName()
                  if (baseName != bindName) bindName += "/$baseName"
                  def baseStrength = baseBinding.getStrength()
                  bindName = String.format('%s (%s%s)', bindName, baseStrength, baseStrength != strength ? "*" : '')
                }
              }
            }
            if (isExtension) bindName += " [ext]"
            printf "X: bindingx %s %-10s %-35s %s%n", diffElt && diffElt.hasBinding() ? "1Q" : "2C", strength, name, bindName
          } // binding?
          //debug
          //---------------------------------------------------------------------
          */

          // if (!mustSupport && !isExtension) mkp.yieldUnescaped('</strike>')
          dumpTypeDesc(html, resourceName, child, extElt, isExtension, type, binding, desc )
        } // td

      } // tr
    } // for each

    // printf "%s %s%n", resourceName, pathSet // debug

    html.mkp.yieldUnescaped('</table>')
  }

  /**
   * Dump type and description for element
   * @param html
   * @param resourceName
   * @param elt
   * @param type
   * @param binding
   * @param desc
   * @param modifier
   */
  void dumpTypeDesc(html, String resourceName, ElementDefinitionHolder edh,
                    ElementDefinition extElt, boolean isExtension,
                    List<TypeRefComponent> type, ElementDefinitionBindingComponent binding,
                    String desc) {
    StringBuilder sb = new StringBuilder()

    //boolean checkElementType = false

    final ElementDefinition elt = edh.self

    if (type) {
      //println "check type types="+type.size()
      type.each {
        if (!it.hasCode()) return
        String code = it.getCode()
        // println "X: type code=$code"
        if (code == 'Reference' && it.hasProfile()) {
          String profileUrl = getProfile(it)
          //if (!it.hasProfile()) println "X: check: no profile in type???"
          boolean hasLink = false
          String link = null
          def typeClassName = uriToClassName.get(profileUrl)
          if (typeClassName) {
            code = typeClassName
            hasLink = true
          } else if (profileUrl) {

            if (resourceName == TARGET_RESOURCE) println "X: profileUrl=$profileUrl" // debug
            if (profileUrl.contains("qicore-")) println "ERROR: bad profile ref $profileUrl in " + elt.getPath()

            // ??? getClassName(profileUrl)
            // examples:
            // http://hl7.org/fhir/StructureDefinition/Any
            // http://hl7.org/fhir/StructureDefinition/patient-qicore-qicore-patient
            // http://hl7.org/fhir/StructureDefinition/specimen-volumeFuzzy"
            // etc.
            int ind = profileUrl.lastIndexOf('/')
            if (ind > 0) {
              code = profileUrl.substring(ind + 1)
              println "X: type class=$code"
              // reference to domaon resource where qicore profile exists
              // e.g. http://hl7.org/fhir/StructureDefinition/Medication in Other.extension
              if (profiles.containsKey(code)) {
                // printf "CC: %s %s name=%s%n", profileUrl, elt.getPath(), elt.getName()
                hasLink = true
              } // else ???
              else if (code != 'Resource') {
                //if (resourceName == TARGET_RESOURCE) println "X: name ($code) not found in profiles: tc=$typeClassName"
                // e.g. Group, Appointment, Media, etc. resource references with no associated QICore profile
                /*
                if (coreResources.contains(code)) {
                  println "INFO: name ($code) not found in profiles: use FHIR resource page"
                  def resFile = new File(dir, code.toLowerCase() + ".html")
                  if (resFile.exists()) {
                    link = baseUrl + "/" + resFile.getName()
                    hasLink = true
                  } else println "ERROR: no res file: $code " + resFile.getName()
                }
                else
                */
                // TODO resource XXX
                def baseRef = code.toLowerCase(Locale.ROOT) + '.html'
                if (resources.containsKey(code)) {
                  println "INFO: name ($code) is base resource in " + elt.getPath()
                  //?external links disabled
                  //hasLink = true
                  //link = baseUrl + baseRef
                } else {
                  File file = new File(publishDir, baseRef)
                  if (file.exists()) {
                    // these should have been picked up in pre-scan of resource types
                    println "INFO: name ($code) is direct ref in " + elt.getPath()
                    //?external links disabled
                    //?hasLink = true
                    //?link = baseUrl + baseRef
                  } else println "WARN: name ($code) not found in profiles"
                }
              }
            } else {
              code = profileUrl
              println "CC: check $code"
            }
          }
          if (sb.length() != 0) sb.append(' | ')
          if (hasLink) {
            // if (code == 'Condition') printf "C: %s Condition%n", elt.getPath() //debug
            if (link) sb.append("<a href='${link}'>${code}</a>")
            else sb.append("<a href='${code}.html'>${code}</a>")
          } else {
            sb.append(code)
          }
        } else {
          if (sb.length() != 0) sb.append(' | ')
          // Encounter.hospitalization.dischargeDiagnosis  type=Resource
          // if (code == 'Any') println "XX: any="+edh.path
          if (code == 'Element' || code == 'BackboneElement' || code == '*') {
            //BackboneElement Encounter.hospitalization
            //BackboneElement Encounter.location
            //BackboneElement Encounter.participant
            //BackboneElement Encounter.statusHistory
            if (edh.getChildren().isEmpty()) {
              typeCheck.add(code) //debug
              sb.append(code)
              // TODO: not handled right Basic.extension name=AdverseEvent-cause [Extension/code=*]
              println "check: $code " + edh.path
            } else {
              // TODO: new sub-class
              println "X: Element subclass for " + edh.path
              // Basic.cause                            [Extension/code=*]
              // Encounter.relatedCondition             [code=Element]
              // Goal.target                            [code=Element]
              // Patient.extension name=citizenship     [Extension/code=*]
              // Patient.extension name=clinicalTrial   [Extension/code=*]
              // Patient.extension name=nationality     [Extension/code=*]
              String name = edh.path
              int ind = name.lastIndexOf('.')
              if (ind > 0) name = name.substring(ind+1)
              name = StringUtils.capitalize(name)
              sb.append("<a href='todo.html'>$name</a> (TBD)")
              // TODO: create sub-class for this element
            }
          }
          else if (primTypes.contains(code)) {
            String cqlType = cqlTypeMap.get(code)
            sb.append(cqlType ?: code)
            if (!cqlType) typeCheck.add(code)
            //sb.append("<a href=\"" + code + ".html\">" + code + "</a>")
            // http://hl7-fhir.github.io/datatypes.html#string
            // sb.append("<a href='${baseUrl}datatypes.html#${code}'>${code}</a>")
            /*
            if ('Coding' == code && binding == null) {
              String name = elt.getPath()
              if (elt.hasName()) {
                name = elt.getName()
                if (!name.startsWith(resourceName)) name = resourceName + "." + name
              }
              printf "XX: need binding?: %s%n", name
            }
            */
          //} //else if (code == 'BackboneElement') {
            //checkElementType = true
            //if (elt.hasMax() && elt.getMax() == '*') println "listtype BackboneElement " + edh.path
          }
          else if (complexTypes.contains(code)) {
            // add link to complex type class
            sb.append("<a href='${code}.html'>${code}</a>")
          } else {
            typeCheck.add(code)
            /*
            if ('*' == code) {
              code = 'Element' // appears to be Element in all cases; e.g. Patient.extension.citizenship
              println "XX: type=* for " + elt.getPath() + " name=" + elt.getName() //debug

              // sb.append('Element') // any Reference or any Element ??
            }
            */
            //else {
            /*
            File file = new File(publishDir, code.toLowerCase(Locale.ROOT) + ".html") // e.g. element.html
            if (file.exists()) {
              //?external links disabled
              //sb.append("<a href='${baseUrl}${file.getName()}'>${code}</a>")
              sb.append(code)
              println "22: href: $code in" + edh.path // BackboneElement
            } else {
            */
            // println "33: $code " + edh.path
            if (typeCodes.add(code)) println "X: added new type: $code in " + elt.getPath() //debug
            sb.append(code)
            // }
            //}
          }
        }
      } // each type

      if (sb.length() == 0) {
        println "XX: check unk type " + elt.getPath()
        // checkElementType = true
      } else {
        html.code {
          boolean listType = elt.hasMax() && elt.getMax() == '*'
          if (listType) {
            sb.insert(0, 'List&lt;')
            //sb.append('&gt;')
          }
          def types = sb.toString()
          if (listType || types.contains('<')) {
            if (listType) {
              if (types.endsWith(" (TBD)")) types = types.substring(0,types.length()-6) + "&gt; (TBD)"
              else types = types + ">"
            }
            mkp.yieldUnescaped(types)
          } else
            mkp.yield(types)
        }
        sb.setLength(0) // reset buffer
      }

      // only extensions should have a defined type (i.e. Extension) and have children in the edh instance
      // if (/*!isExtension &&*/ !edh.getChildren().isEmpty()) printf "11: non-Element element with children: %s%s [%s]%n", isExtension ? "*" : '', edh.path, type.get(0).getCode()
      /*
      Extension elements with children:
        Encounter.relatedCondition [Element]
        Goal.target [Element]
        Patient.citizenship [*]
        Patient.clinicalTrial [*]
        Patient.nationality [*]

      non-extension elements with children:
        Location.address [Address]
        Organization.address [Address]
        Patient.address [Address]
        Patient.birthDate [date]
        Patient.telecom [ContactPoint]

       */
      // Location.address, Organization.address, Patient.address, Patient.birthDate, Patient.telecom
      // extensions: Practitioner.practitionerRole.specialty [CodeableConcept], Specimen.collection.quantity [Quantity]

    } // type?
    else  {
      // if (!resources.containsKey(resourceName)) println "XX: check no type " + elt.getPath() // e.g. Encounter.hospitalization
      // no type -> assume Element??
      // if (edh.getChildren().isEmpty()) println "11: empty type no children: " + edh.path // e.g. Patient.animal
      if (edh.getChildren().isEmpty() && !specialCaseElements.containsKey(edh.path)) {
        html.mkp.yieldUnescaped("Element") // ??
        println "WARN: check no type " + edh.path
        /*
        DiagnosticOrder.item.event ???
        Observation.component.referenceRange
        Questionnaire.group.group
        Questionnaire.group.question.group
        QuestionnaireAnswers.group.group
        QuestionnaireAnswers.group.question.group
        ValueSet.compose.exclude
        ValueSet.compose.include.concept.designation
        ValueSet.define.concept.concept
        ValueSet.expansion.contains.contains
         */
      } else {
        // if (checkElementType) println "check type for " + edh.path
        sb.setLength(0)
        boolean listType = elt.hasMax() && elt.getMax() == '*'
        if (listType) sb.append('List&lt;')
        //def altname = specialCaseElements.get(edh.path)
        // ALT: DiagnosticOrder.item.event DiagnosticOrder.Event -> DiagnosticOrder.Event
        //String name = specialCaseElements.get(edh.path) ?: edh.path
        String name = edh.path
        // if (altname) printf "ALT: %s %s -> %s%n", edh.path, altname, name
        int ind = name.lastIndexOf('.')
        if (ind > 0) name = name.substring(ind+1)
        name = StringUtils.capitalize(name)
        // TODO: use link to generated anonymous structure element as sub-class
        sb.append("<a href='todo.html'>").append(name).append("</a>")
        if (listType) sb.append('&gt;')
        sb.append(" (TBD)")
        html.mkp.yieldUnescaped(sb.toString())
        sb.setLength(0)
        //html.mkp.yieldUnescaped("<B>" + StringUtils.capitalize(name) + " (TBD)</B>")
        // TODO: create sub-class for this element
      }
      // html.mkp.yieldUnescaped("<a href='${baseUrl}element.html'>Element</a>")
      // Encounter.location, Encounter.participant, Encounter.statusHistory, FamilyHistory.relation, FamilyHistory.relation.condition
    }

    //StringBuilder sb = new StringBuilder()
    if (binding) {
      if (binding.hasValueSet()) {
        def valueSet = binding.getValueSet()

        def ref
        try {
          if (valueSet instanceof org.hl7.fhir.instance.model.Reference) {
            ref = binding.getValueSetReference()
            if (ref && ref.hasReference()) {
              //println "XX: string ref: " + ref.getClass().getName()
              ref = ref.getReference()
              // NOTE: hasBinding method removed as of July 2015
              //if (binding.hasName())
                //printf "X: binding: %-22s %-20s %s%n", elt.getPath(), binding.getName(), ref
              //else
                printf "X: binding: %-27s %s%n", elt.getPath(), ref//.replace("/ValueSet/","/vs/") // debug
            }
          } else if (valueSet instanceof UriType) {
            ref = binding.getValueSetUriType()
            if (ref) ref = ref.getValue()
          } else println "X: binding w/other valueset type: $valueSet"
        } catch (Exception e) {
          def s = e.getMessage()
          if (!s) {
            def cause = e.getCause()
            if (cause) s = cause.getMessage()
            if (!s) s = e.toString()
          }
          // e.printStackTrace(System.out)
          // println "WARN: uritype=" + binding.getValueSetUriType()
          println "WARN: bad ref: $s " + binding.hasValueSet() // + " " + binding.getValueSet()
        }
        if (ref && ref instanceof String) {
          String href, bindingName = '', bindingDef
          if (binding.hasDescription()) bindingDef = StringUtils.trimToNull(binding.getDescription())
          // NOTE: new v3 URL as of 13-Jul-2015 http://hl7.org/fhir/ValueSet/v3-ActReason
          if (ref =~ /^http:\/\/hl7.org\/fhir\/ValueSet\//) {
          //if (ref =~ /^http:\/\/hl7.org\/fhir\/(v[23]\/)?vs\//) { .// July-13 Valueset URLs changed /vs/ => /Valueset/
            // also http://hl7-fhir.github.io/v3/vs/ServiceDeliveryLocationRoleType/index.html
            href = ref.substring(ref.lastIndexOf('/') + 1)
            /*
            X: other binding name: href=daf-problem               name=QICoreProblemCode
            X: other binding name: href=daf-encounter-type        name=DAFEncounterType
            X: other binding name: href=daf-encounter-reason      name=QICoreEncounterReasonValueset
            X: other binding name: href=daf-race                  name=QICoreRace
            X: other binding name: href=daf-ethnicity             name=QICoreEthnicity
            X: other binding name: href=daf-procedure-type        name=qicore-procedure-type

            ref=http://hl7.org/fhir/vs/daf-race
            target=valueset-daf-race.html

            name=QICoreImportance
            ref=http://hl7.org/fhir/vs/qicore-importance
            target=valueset-qicore-importance.html
            */

            File file = new File(publishDir, 'valueset-' + href + ".html")
            if (file.exists()) {
              //if (href.startsWith('daf-') || href.contains('qicore-')) {
              href = baseUrl + file.getName()
              // sb.append(String.format('Binding: <a href="%s%s">%s</a>',
              // baseUrl, file.getName(), binding.getName()))
            } else {

              if (file.exists()) {
                //if (href.startsWith('daf-') || href.contains('qicore-')) {
                println "X: DIRECT URI $href"
                href = baseUrl + file.getName()
                //sb.append(String.format('Binding: <a href="%s%s">%s</a>',
                //baseUrl, file.getName(), binding.getName()))
              } else {
                String baseName
                if (file.getName().contains("daf-")) {
                  baseName = "daf/" + file.getName()
                } else if (file.getName().contains("uslab-")) {
                  baseName = "uslab/" + file.getName()
                } else {
                  baseName = "cqf/" + file.getName()
                }
                file = new File(publishDir, baseName)
                if (file.exists()) {
                  href = baseUrl + baseName
                  println "X: DIRECT URI $href"
                }
              }

              if (!file.exists()) {
                file = new File(publishDir, href + ".html")
                // printf "X: other binding name: href=%-25s name=%s%n", file.getName(), binding.getName() // debug
                // NOTE: new v3 URL as of 13-Jul-2015 http://hl7.org/fhir/ValueSet/v3-ActReason
                // which is mapped to folder: publish/v3/ActReason/ { index.html, v3-ActReason.xml }
                // printf "V: href=%s ref=%s%n", href, ref//debug
                //def name = ref.substring('http://hl7.org/fhir/'.length())
                if (href.startsWith('v2-') || href.startsWith('v3-')) {
                  // other binding v3/vs/ActReason => \v3\ActReason\index.html
                  def basePart = href.substring(3) + "/index.html"
                  File baseDir = new File(publishDir, href.substring(0,2))
                  file = new File(baseDir, basePart)
                  if (file.exists()) {
                    //href=v3-ActReason base=ActReason/index.html
                    //other good binding1 ..\build\publish\v3\ActReason\index.html AllergyIntolerance.extension http://hl7-fhir.github.io/ActReasonX/ActReason/index.html
                    println "V: href=$href base=$basePart"
                    href = baseUrl + href.substring(0,2) + '/' +  basePart
                    printf "V: other good binding1 %s %s %s%nV:%n", file.getPath(), elt.getPath(), href
                  } else {
                    // alternate path
                    // http://hl7.org/fhir/ValueSet/v3-FamilyMember => build\publish\v3\vs\FamilyMember\{ index.html, v3-FamilyMember.xml }
                    // http://hl7.org/fhir/ValueSet/v3-ServiceDeliveryLocationRoleType => build\publish\v3\vs\ServiceDeliveryLocationRoleType\{ index.html, v3-ServiceDeliveryLocationRoleType.xml}
                    basePart = 'vs/' + basePart
                    file = new File(baseDir, basePart)
                    if (file.exists()) {
                      //?? http://hl7-fhir.github.io/FamilyMember/vs/FamilyMember/index.html
                      println "V: $href"
                      href = baseUrl + href.substring(0,2) + '/' + basePart
                      printf "V: other good binding2 %s %s %s%nV:%n", basePart, elt.getPath(), href
                    } else {
                      //println "XX: other binding name=$name href=$href file=$file " + elt.getPath()
                      printf "V: other bad binding2 ref=%s %s %s%n", ref, file, elt.getPath()
                      href = null
                    }
                  }
                } else href = null
                if (href == null) println "X: other binding2 ref=$ref file=$file " + elt.getPath()
              }
            }

            ValueSet vs = valuesets.get(ref)
            if (vs == null && file.exists()) {
              //URI uri = new URI(ref)
              //printf "VS: %s %s %s%n", uri.getPath(), uri.getQuery(), uri.getFragment()
              String baseName = ref
              int ind = baseName.lastIndexOf('/')
              if (ind > 0) baseName = baseName.substring(ind+1)
              // String baseName = FilenameUtils.getBaseName(file.getName())
              File parentDir = file.getParentFile()
              File vsFile = new File(parentDir, baseName + ".xml")
              /*
                C1 http://hl7.org/fhir/vs/procedure-status     publish/procedure-status.xml
                C2 http://hl7.org/fhir/vs/food-type            publish/valueset-food-type.xml
                C3 http://hl7.org/fhir/v3/vs/ActReason         publish/v3/ActReason/v3-ActReason.xml
                C4 http://hl7.org/fhir/v2/vs/0487              publish/v2/0487\v2-0487.xml
               */
              if (!vsFile.exists() && ref =~ /^http:\/\/hl7.org\/fhir\/(v[23])\/vs\//) {
                // println "VS: ref $ref" // debug
                vsFile = new File(parentDir, Matcher.getLastMatcher().group(1) + "-" + baseName + ".xml")
                // println "VS: check $vsFile" // debug
              }
              if (!vsFile.exists()) {
                vsFile = new File(parentDir, "valueset-" + baseName + ".xml")
                // if (!vsFile.exists()) vsFile = new File(parentDir, "cqf/" + vsFile.getName())
                // e.g. http://hl7.org/fhir/ValueSet/qicore-priority
                // println "VS: check $vsFile " + vsFile.exists() // debug case #2 count=91
              } // else println "VS: exists: $vsFile" // #=55 case #1
              if (vsFile.exists()) {
                // println "VS: ok: $vsFile" // debug cnt=120
                def is
                try {
                  is = new FileInputStream(vsFile)
                  vs = (ValueSet)xmlParser.parse(is)
                  valuesets.put(ref, vs)
                  //if (vs.hasName()) {
                  // bindingName = StringUtils.trimToNull(vs.getName())
                    // println "VS: " +
                  //}
                } catch(Exception e) {
                  // ignore
                  println "WARN: " + e.getMessage()
                } finally {
                    IOUtils.closeQuietly(is)
                }
              } // else println "VS: not found $vsFile base=$baseName"
            }
            if (vs) {
              bindingName = StringUtils.trimToNull(vs.getName())
              if (!bindingName) println "VS: no name $ref"
              if (!bindingDef) bindingDef = StringUtils.trimToNull(vs.getDescription())
            } else println "WARN: no valueset found: ref=$ref file=$file"
            // href=other-resource-type       name=OtherResourceType
            //if (ref.contains("daf-") || ref.contains("qicore-"))
            // e.g. binding name=AdministrativeGender
            // ref=http://hl7.org/fhir/vs/administrative-gender
            // target=http://hl7-fhir.github.io/administrative-gender.html
          } else if (ref.startsWith('http://') || ref.startsWith('https://')) {
            printf 'X: other binding direct name: href=%-25s%n', ref // .replace("/ValueSet/","/vs/") //  name=%s, binding.getName() // debug
            // https://www.gmdnagency.org/Info.aspx?pageid=1091
            // https://rtmms.nist.gov/rtmms/index.htm#!hrosetta
            // https://rtmms.nist.gov/rtmms/index.htm#!units
            // http://tools.ietf.org/html/bcp47
            href = ref
            // println "X: DIRECT URI $href"
            // sb.append(String.format('Binding: <a href="%s%s">%s</a>',
            // baseUrl, file.getName(), binding.getName()))
          }
          if (href) {
            sb.append('Binding: <a')
            if (bindingDef) sb.append(String.format(' title="%s"', htmlEscape(bindingDef)))
            sb.append(String.format(' href="%s">%s</a>', href, bindingName ? htmlEscape(bindingName) : 'value set'))
          } //else printf 'X: other binding3 name: href=%-25s name=%s%n', ref, binding.getName() // debug
        } //else printf 'X: other binding4 name: href=%-25s name=%s%n', ref, binding.getName() // debug
      }

      if (sb.length() == 0) {
        //? sb.append('Binding: ').append(binding.getName()) // default (no URL)
        printf 'X: other binding path=%-38s strength=%s [no url]%n', elt.getPath(), binding.getStrength() // debug
        // no reference defined in binding
        //  CarePlan.category     [strength=REQUIRED]
        //  Group.code            [strength=REQUIRED]
        //  RiskAssessment.method [strength=REQUIRED]
      }

      //a(href:baseUrl + binding.getName().toLowerCase()+".html", binding.getName())
      def strength = binding.getStrength()
      if (strength) {
        sb.append(String.format(' (%s)', strength.getDisplay()))
        //sb.append(String.format('(<a href="%s#%s">%s</a>)',
                //"${baseUrl}terminologies.html", strength.toCode(), strength.getDisplay()))
      }
    } // binding?

    // TODO: custom attribute for QICore label

    if (desc || sb.length() != 0) {
      html.blockquote {
        div(class: 'block') {
          // check if profile overrides the definition
          // TODO Value, Binding, etc.
          if (desc) {
            mkp.yield(desc)
          }
          if (sb.length() != 0) {
            if (desc) {
              // wrap other text with <P>..</P>
              sb.insert(0, '\n<p>')
              sb.append('</p>')
            }
            mkp.yieldUnescaped(sb.toString())
          }
        } // div
      } // blockquote
    }
  } // end dumpTypeDesc

// ---------------------------------------------------------
// create index-files page
// ---------------------------------------------------------
  void createIndexPage() {
    def writer = new FileWriter("$outDir/index-files.html")

    // sample interface detail page
    // http://docs.oracle.com/javase/7/docs/api/java/awt/Transparency.html
    writer.println('<!DOCTYPE HTML>') // that what FHIR web site does to its HTML pages
    //writer.println('''<!DOCTYPE HTML [
    //<!ENTITY copy "&#169;">
    //<!ENTITY nbsp "&#160;">\n]>''')
    def html = new groovy.xml.MarkupBuilder(writer)
    html.html(xmlns: "http://www.w3.org/1999/xhtml", "xml:lang": "en", lang: "en") {
      head {
        title("A-Z Index ($shortTitle)")
        link(rel: 'stylesheet', type: 'text/css', href: 'stylesheet.css', title: 'Style')
      }
      body {

        mkp.yieldUnescaped('\n<!-- ========= START OF TOP NAVBAR ======= -->')
        div(class: 'topNav') {
          a(name: 'navbar_top') {
            mkp.yieldUnescaped('\n<!--   -->\n')
          }
          ul(class: 'navList', title: 'Navigation') {
            li {
              a(href: 'overview-summary.html', 'Overview')
            } // li
            li('Class')
            li(class: "navBarCell1Rev") {
              mkp.yield('Index')
            } // li
          } // ul
          div(class: 'aboutLanguage', shortTitle)
        } // div class=topNav

        div(class: 'subNav') {
          ul(class: 'navList') {
            li {
              a(target: '_top', href: 'index.html?index-files.html', 'FRAMES')
              mkp.yieldUnescaped('&nbsp;&nbsp;')
              a(target: '_top', href: 'index-files.html', 'NO FRAMES')
            }
          }
          div {
            mkp.yieldUnescaped('<!--   -->')
          }
        } // div class=subNav
        mkp.yieldUnescaped('\n<!-- ========= END OF TOP NAVBAR ========= -->')

        div(class: 'contentContainer') {
          final Set<Character> keyset = new HashSet<>()
          index.each {
            keyset.add(Character.toUpperCase(it.name.charAt(0)))
          }

          // A-Z short-cut at top
          for (Character c in 'A'..'Z') {
            if (c != 'A')
              mkp.yieldUnescaped('&nbsp;')
            if (keyset.contains(c))
              a(href: "#$c", c)
            else
              mkp.yield(c)
          }

          char lastRef = ' '
          index.each {
            String key = it.name
            String target = it.href
            //p(class:"strong") {
            char ref = Character.toUpperCase(key.charAt(0))
            if (ref != lastRef) {
              h2(class: 'title') {
                a(name: ref, ref)
              }
              lastRef = ref
            }
            a(class: 'strong', href: "pages/$target", key)
            if (it.desc) {
              mkp.yieldUnescaped(" - ${it.desc}")
            }
            br()
            //} // span
          } // each

          br()
          p()

          // A-Z short-cut at bottom
          for (Character c in 'A'..'Z') {
            if (c != 'A')
              mkp.yieldUnescaped('&nbsp;')
            if (keyset.contains(c))
              a(href: "#$c", c)
            else
              mkp.yield(c)
          }
        } // div class=contentContainer

        mkp.yieldUnescaped('\n<!-- ======= START OF BOTTOM NAVBAR ====== -->')
        div(class: 'bottomNav') {
          a(name: 'navbar_bottom') {
            mkp.yieldUnescaped('\n<!--   -->\n')
          }
          ul(class: 'navList', title: 'Navigation') {
            li {
              a(href: 'overview-summary.html', 'Overview')
            } // li
            li('Class')
            li(class: 'navBarCell1Rev') {
              mkp.yield('Index')
            } // li
          } // ul
          div(class: 'aboutLanguage', shortTitle)
        } // div class=topNav

        div(class: 'subNav') {
          ul(class: 'navList') {
            li {
              a(target: '_top', href: 'index.html?index-files.html', 'FRAMES')
              mkp.yieldUnescaped('&nbsp;&nbsp;')
              a(target: '_top', href: 'index-files.html', 'NO FRAMES')
            }
          }
          div {
            // script getElementById('allclasses_navbar_top')...
            mkp.yieldUnescaped('<!--   -->')
          }
        } // div class=subNav
        mkp.yieldUnescaped('<!-- ======== END OF BOTTOM NAVBAR ======= -->')
        //mkp.yieldUnescaped('<dd>&nbsp;</dd>')
      }//body
    }//html
    writer.close()
  }

// ---------------------------------------------------------
// create allclasses frame page
// ---------------------------------------------------------
  void createAllClassesFrame() {
    def writer = new FileWriter("$outDir/allclasses-frame.html")

    // sample interface detail page
    // http://docs.oracle.com/javase/7/docs/api/java/awt/Transparency.html
    // writer.println('<?xml version="1.0" encoding="ISO-8859-1"?>')
    writer.println('<!DOCTYPE HTML>')
    def html = new groovy.xml.MarkupBuilder(writer)
    html.html(xmlns: "http://www.w3.org/1999/xhtml", "xml:lang": "en", lang: "en") {
      head {
        base(target: 'right')
        //title('QUICK Data Model')
        link(rel: 'stylesheet', type: 'text/css', href: 'stylesheet.css', title: 'Style')
      }
      body {
        h1(title: 'QUICK Data Model', class: 'bar') {
          strong('QUICK Data Model')
        }
        div(class: 'indexContainer') {
          def otherClasses = new ArrayList<String>()
          h2(title: 'Classes', 'Classes')
          ul(title: "Classes") {
            // h1(class: "bar", 'All Classes')
            //Collections.sort(classes)
            //classes.each { className ->
            profiles.keySet().each { className ->
              if (complexTypes.contains(className))
                otherClasses.add(className)
              else
              li {
                // String className = getClassName(id)
                a(href: "pages/${className}.html", className)
              }
            }
          }
          if (otherClasses) {
            h2(title: 'Classes', 'Complex Type Classes')
            ul(title: "Classes") {
              otherClasses.each { className ->
                li {
                  // String className = getClassName(id)
                  a(href: "pages/${className}.html", className)
                }
              }
            }
          }
        }
      }
    }
  }

// ---------------------------------------------------------
// create overview-summary page
// ---------------------------------------------------------
  void createOverviewSummary() {
    def writer = new FileWriter("$outDir/overview-summary.html")
    writer.println('<!DOCTYPE HTML>') // that what FHIR web site does to its HTML pages
    /*
    writer.println('''<!DOCTYPE HTML [
  <!ENTITY copy "&#169;">
  <!ENTITY nbsp "&#160;">\n]>''')
  */
    def html = new groovy.xml.MarkupBuilder(writer)
    html.html(xmlns: "http://www.w3.org/1999/xhtml", "xml:lang": "en", lang: "en") {
      // see http://docs.oracle.com/javase/7/docs/api/java/lang/package-summary.html
      head {
        //base(target:'left')
        title("Overview (${overviewTitle})")
        link(rel: 'stylesheet', type: 'text/css', href: 'stylesheet.css', title: 'Style')
      }
      body {

        mkp.yieldUnescaped('\n<!-- ========= START OF TOP NAVBAR ======= -->')
        div(class: 'topNav') {
          a(name: 'navbar_top') {
            mkp.yieldUnescaped('\n<!--   -->\n')
          }
          ul(class: 'navList', title: 'Navigation') {
            li(class: 'navBarCell1Rev', 'Overview')
            li('Class')
            li {
              a(href: 'index-files.html', 'Index')
            } // li
          } // ul
          div(class: 'aboutLanguage', shortTitle)
        } // div class=topNav

        div(class: 'subNav') {
          ul(class: 'navList') {
            li {
              a(target: '_top', href: 'index.html?overview-summary.html', 'FRAMES')
              mkp.yieldUnescaped('&nbsp;&nbsp;')
              a(target: '_top', href: 'overview-summary.html', 'NO FRAMES')
            }
          }
          div {
            // script getElementById('allclasses_navbar_top')...
            mkp.yieldUnescaped('<!--   -->')
          }
        } // div class=subNav
        mkp.yieldUnescaped('<!-- ========= END OF TOP NAVBAR ========= -->')

        String description = shortTitle

        div(class: 'header') {
          h1(title: 'Package', class: 'title', overviewTitle)
          div(class: 'docSummary') {
            div(class: 'block', description)
            //SimpleDateFormat df = new SimpleDateFormat('EEE, MMM dd, yyyy h:mma')
            //div(class: 'block', "Generated on ${df.format(System.currentTimeMillis())}")
          }
        }

        div(class: 'contentContainer') {
          ul(class: 'blockList') {
            li(class: 'blockList') {
              mkp.yieldUnescaped('''
<table class="packageSummary" border="0" cellpadding="3" cellspacing="0" summary="Class Summary table, listing classes, and an explanation">
<caption><span>Class Summary</span><span class="tabEnd">&nbsp;</span></caption>
<thead>
<tr>
<th class="colFirst" scope="col">Class</th>
<th class="colLast" scope="col">Description</th>
</tr>
</thead>
<tbody>''')
              int idx = 0
              profiles.each { String className, StructureDefinition profile ->
                if (complexTypes.contains(className)) return // skip complex types
                if (resources.containsKey(className)) {
                  println "X: other resource: $className"
                  // return  // skip non-qicore profiled resources on overview
                }
                String outName = className
                tr(class: idx++ % 2 ? 'rowColor' : 'altColor') {
                  td(class: 'colFirst') {
                    a(href: "pages/${className}.html", getClassName(outName))
                  } // td
                  td(class: 'colLast') {
                    //def profile = profiles.get(className)
                    if (profile) {
                      def desc = profile.getSnapshot().getElement().get(0).getShort()
                      if (!desc || getResourceName(profile) == 'Basic') desc = profile.getDescription()
                      //def desc = profile.getSnapshot().getElement().get(0).getDefinition()
                      // def desc = profile.getDescription()
                      if (desc) {
                        div(class: 'block') {
                          mkp.yieldUnescaped(desc)
                        } // div
                      }
                    }
                  } //td
                } // tr
              } // each class
              mkp.yieldUnescaped('</tbody></table>')
            } // li
          } // ul
        } // div

        createBottomNavbar(html)

      } // body
    } // html
    writer.close()
  } // createOverviewSummary

// ---------------------------------------------------------
// create top navpage for detailed pages
// ---------------------------------------------------------
  private void createDetailNavbar(html, String name) {
    // create top navbar HTML for pages/xxx.html
    html.mkp.yieldUnescaped('\n<!-- ========= START OF TOP NAVBAR ======= -->')
    html.div(class: 'topNav') {
      a(name: 'navbar_top') {
        mkp.yieldUnescaped('\n<!--   -->\n')
      }
      ul(class: 'navList', title: 'Navigation') {
        li {
          a(href: '../overview-summary.html', 'Overview')
        } // li
        li(class: 'navBarCell1Rev', 'Class')
        li {
          a(href: '../index-files.html', 'Index')
        } // li
      } // ul
      div(class: 'aboutLanguage', shortTitle)
    } // div class=topNav

    html.div(class: 'subNav') {
      //name = escapeName(name) // e.g. for RIM with <'s in names
      ul(class: 'navList') {
        li {
          //String nameOut = name
          a(target: '_top', href: "../index.html?pages/${name}.html", 'FRAMES')
          mkp.yieldUnescaped('&nbsp;&nbsp;')
          a(target: '_top', href: "${name}.html", 'NO FRAMES')
        }
      }
      div {
        mkp.yieldUnescaped('<!--   -->')
      }
    } // subNav
    html.mkp.yieldUnescaped('\n<!-- ========= END OF TOP NAVBAR ========= -->')
  } // createDetailNavbar()


  @TypeChecked
  void copyIndexTemplate() {
    String body = new File('template/index.html').getText().replaceFirst('%TITLE%', shortTitle)
            .replaceFirst('</title>', '</title>\n<link rel="icon" type="image/ico" href="resources/favicon.ico"/>')
    //'</TITLE>\n<link rel="icon" type="image/png" href="resources/favicon.png"/>')

    new File("$outDir/index.html").setText(body)
    //copyFile('template/stylesheet.css', 'html/stylesheet.css')
    //File dir = new File('html/resources')
  }

  // -------------------------------------------------------------------------------------------------
  // helper methods
  // -------------------------------------------------------------------------------------------------

  /*
  static boolean getMustSupport(ElementDefinition elt, ElementDefinition diffElt) {
    if (elt && elt.hasMustSupport() && elt.getMustSupport()) return true
    if (diffElt && diffElt.hasMustSupport() && diffElt.getMustSupport()) {
      println "\tWARN: snapshot mustSupport=false but diff=true " + (elt ? elt.getPath() : '')
      return true
    }
    return false
  }
  */

  @TypeChecked
  private static int processElementsIntoTree(ElementDefinitionHolder edh, int i, List<ElementDefinitionHolder> list) {
    String path = edh.path
    final String prefix = path + ".";
    while (i < list.size() && list.get(i).path.startsWith(prefix)) {
      ElementDefinitionHolder child = list.get(i)
      edh.getChildren().add(child);
      i = processElementsIntoTree(child, i+1, list);
    }
    return i;
  }

  @TypeChecked
  static String getClassName(String id) {
    // TODO: revisit if class naming changes; e.g. use snapshot.elements(0).getName() to preserve camel-case name
    //? if (!id) return id
    String name = id
    int ind = name.lastIndexOf('-qicore-')
    if (ind > 0) {
      // make adverseevent-qicore-qicore-adverseevent into AdverseEvent
      // and diagnosticorder-qicore-diagnosticorder into DiagnosticOrder
      // use name of first element in snapshot if consistent in profiles or just use lookup table?
      name = StringUtils.capitalize(name.substring(ind + 8))
    }
    return classNames.get(name) ?: name
  }

  @TypeChecked
  static String escapeName(String s) {
    // escape names for HTML <a name="xx">...
    // e.g. relation.born[x] => relation_born_x_
    return s.replaceAll('[^\\w.\\-]', '_')
  }

  @TypeChecked
  static String htmlEscape(String s) {
    // escape names for HTML
    //def v = s.replace('>','&gt;').replace('<','&lt;')
    //if (v.length() != s.length()) println "XX: replaced: $s"
    //return v
    return s.replace('>','&gt;').replace('<','&lt;')
  }

  @TypeChecked
  static class ElementDefinitionHolderComparator implements Comparator<ElementDefinitionHolder> {

    @Override
    int compare(ElementDefinitionHolder o1, ElementDefinitionHolder o2) {
      return o1.path.compareTo(o2.path)
    }

  }

  /**
   * ElementDefinitionHolder copied from ProfileUtilities class
   * Removed unused baseIndex field
   */
  @TypeChecked
  static class ElementDefinitionHolder {
    final ElementDefinition self
    final String path
    // private int baseIndex = 0;
    private List<ElementDefinitionHolder> children

    public ElementDefinitionHolder(ElementDefinition self) {
      this(self, self.getPath())
    }

    public ElementDefinitionHolder(ElementDefinition self, String path) {
      if (path == null) throw new NullPointerException()
      this.path = path;
      this.self = self;
      this.children = new ArrayList<ElementDefinitionHolder>()
    }

    public ElementDefinition getSelf() {
      return self
    }

    public List<ElementDefinitionHolder> getChildren() {
      return children
    }
  }

}
