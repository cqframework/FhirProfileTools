File sourceDir = FhirUtils.getSourceDir()
def pat = FhirUtils.getProfilePattern()
FhirProfileValidator checker = new FhirProfileValidator(pat, FhirUtils.getRules())
checker.setup()

/*
// simple scan of single directory
checker.skipGoodProfiles = false
checker.checkDirectory(new File(sourceDir, 'observation'))
checker.tearDown()
*/

try {
  sourceDir.listFiles().each { file ->
    if (file.isDirectory()) {
      checker.checkDirectory(file)
    }
  }
} finally {
  checker.tearDown()
}
