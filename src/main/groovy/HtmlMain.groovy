File sourceDir = FhirUtils.getSourceDir()
if (!sourceDir) throw new IllegalArgumentException("fhirSourceDir property is required")

def pat = FhirUtils.getProfilePattern()
FhirProfileHtmlGenerator checker = new FhirProfileHtmlGenerator(pat)
// checker.includeMustSupportOnly = false // false includes all fields
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
