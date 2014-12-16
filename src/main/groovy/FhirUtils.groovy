import java.util.regex.Pattern

/**
 * @author Jason Mathews, MITRE Corporation
 * Created on 12/13/2014.
 */
class FhirUtils {

  /**
   * source directory of FHIR-svn repo build/source folder from which the publish tools
   * build the FHIR web site from or equivalent where each sub-folder has a spreadsheet.xml file
   * defining a core resource.
   */
  private final static File sourceDir

  /**
   * profile rules and explicit exceptions
   */
  private final static File rules

  /**
   * ProfilePattern used to include only those profiles
   * of interest in processing and skip all others.
   */
  private final static Pattern profilePattern

  static {
    Properties props = new Properties()
    InputStream is = FhirUtils.class.getClassLoader()?.getResourceAsStream('config.dat')
    if(is) {
      try {
        props.load(is)
      } finally {
        is.close()
      }
    } else {
      File file = new File('config.dat')
      if (file.exists()) {
        def r = new FileReader('config.dat')
        try {
          props.load(r)
        } finally {
          r.close()
        }
      }
    }
    String val = props.getProperty('fhirSourceDir')
    if (!val) throw new IllegalArgumentException("fhirSourceDir property is required")
    sourceDir = new File(val)
    if (!sourceDir.isDirectory()) throw new IllegalArgumentException("fhirSourceDir must be a directory")

    val = props.getProperty('profilePattern', '.*profile-spreadsheet.xml$')
    // if (!val) // throw new IllegalArgumentException("profilePattern property is required")
    profilePattern = Pattern.compile(val)

    val = props.getProperty('profileRules')
    if (val) {
      rules = new File(val)
      if (!rules.exists()) System.err.println "rules file not found: $val"
    }
  }

  private FhirUtils() {
    // no public constructor
  }

  static File getSourceDir() {
    return sourceDir
  }

  static File getRules() {
    return rules
  }

  static Pattern getProfilePattern() {
    return profilePattern
  }
}
