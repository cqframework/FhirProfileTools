File sourceDir = FhirUtils.getSourceDir()
def pat = FhirUtils.getProfilePattern()
FhirProfileHtmlGenerator checker = new FhirProfileHtmlGenerator(pat)
checker.includeMustSupportOnly = false // include all fields
checker.setup()
try {
  sourceDir.listFiles().each { file ->
	  if (file.isDirectory()) {
		checker.checkDirectory(file)
	  }
	}
} finally {
  checker.tearDown()
}
