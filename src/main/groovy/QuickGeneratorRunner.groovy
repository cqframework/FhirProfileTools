// $Id: FhirHtmlGenerator.groovy,v 1.8 2015/05/21 09:59:42 mathews Exp $

// location of published FHIR specification (aka FHIR website)
File dir = new File("../build/publish")

println "source: " + dir
def gen = new QuickHtmlGenerator(dir)
gen.devMode = true // disable for publishing and formal delivery
gen.setup()
gen.loadExtensions()

new File(dir, 'qicore').eachFileMatch(~/.*profile.xml$/, { file ->
  gen.process(file)
})

gen.generateHtml()