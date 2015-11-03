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
  println "\nsource=" + sourceDir.getAbsolutePath()
  String guide = FhirUtils.getGuide()
  if (guide)
    checker.checkGuide(sourceDir, new File(sourceDir, "../guides/$guide/${guide}.xml"))
  else {
    sourceDir.listFiles().each { file ->
      if (file.isDirectory()) {
        checker.checkDirectory(file)
      }
    }
  }

} finally {
  checker.tearDown()
}

