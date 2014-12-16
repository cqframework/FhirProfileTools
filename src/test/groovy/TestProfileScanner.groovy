/**
 * Created by mathews on 12/15/2014.
 */
class TestProfileScanner extends GroovyTestCase {

  void testValidator() {
    File sourceDir = FhirUtils.getSourceDir()
    def pat = FhirUtils.getProfilePattern()

    FhirProfileValidator checker = new FhirProfileValidator(pat, FhirUtils.getRules())
    assert !checker.exceptions.isEmpty()

    checker.outputDir = new File('build')
    checker.setup()
    // simple scan of single directory
    checker.skipGoodProfiles = false
    checker.checkDirectory(new File(sourceDir, 'observation'))
    checker.tearDown()

    assert 2 == checker.profileCount
    assert 'Observation' == checker.resourceName
    assert 'observation-cqf-absent-profile-spreadsheet.xml' == checker.profile.sourceFile

    assert 2 == checker.errCount
    assert 0 == checker.warnCount
    assert !checker.defaultIdx.isEmpty()

    assert new File('build/out.html').exists()
  }
  
}