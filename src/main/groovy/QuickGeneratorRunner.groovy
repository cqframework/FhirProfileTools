// $Id: FhirHtmlGenerator.groovy,v 1.8 2015/05/21 09:59:42 mathews Exp $

// location of published FHIR specification (aka FHIR website)
File publishDir = FhirUtils.getPublishDir()
if (!publishDir) throw new IllegalArgumentException("fhirPublishDir property is required")

println "source: " + publishDir

File qicoreDir = new File(publishDir.getParentFile(), 'guides/qicore2/output')
println "qi-core source: " + qicoreDir
if (!qicoreDir.isDirectory()) throw new IllegalArgumentException("QICORE profile directory not found")

def gen = new QuickHtmlGenerator(publishDir)
// gen.devMode = true // disable for publishing and formal delivery
gen.setup()
gen.loadExtensions()

qicoreDir.eachFileMatch(~/StructureDefinition-qicore-.*.xml$/, { file ->
  println "processing: " + file
  gen.process(file)
})

gen.generateHtml()