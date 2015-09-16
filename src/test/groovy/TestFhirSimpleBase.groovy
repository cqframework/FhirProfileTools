import org.hl7.fhir.instance.model.StructureDefinition
import org.junit.Test

/**
 * Created by mathews on 9/14/2015.
 */
class TestFhirSimpleBase extends FhirSimpleBase {

  static File publishDir = new File('src/test/resources/publish')

  TestFhirSimpleBase() {
    super(publishDir)
  }

  @Test
  void testExtension() {
    //println "== testExtension =="
    def profile = (StructureDefinition)parseResource(new File(publishDir, 'qicore-referralrequest.profile.xml'))
    assert profile != null

    def elt = getElementByName(profile.getDifferential().getElement(), 'ReferralRequest.requester', null)
    assert elt && elt.getMustSupport()
    // [Reference(qicore-practitioner | qicore-organization | qicore-patient
    def shortList = typeShortList(elt.getType())
    assert shortList.size() == 1 && shortList.get(0).startsWith('Reference(')
    shortList = typeShortList(elt.getType(), false)
	assert shortList.size() == 1 && shortList.get(0) == 'qicore-practitioner | qicore-organization | qicore-patient'

    elt = getExtensionByName(profile.getDifferential().getElement(), 'ReferralRequest.refusalReason')
    assert elt != null
    // println "path=" + elt.getPath()
    def type = elt.hasType() ? elt.getType().get(0) : null
    assert type && type.getCode() == 'Extension'
    String extProfile = getProfile(type)
    // println "profile=$extProfile"
    def extProfileDef = createExtensionDef(elt, extProfile)
    assert extProfileDef != null, "profile=$extProfile"
    //assert extProfileDef.getStructure() != null
    def extElt = extProfileDef.getElement()
    assert extElt != null, "profile=$extProfile"
    // println extElt.getPath()
  }

  @Test
  void testProfileScan() {
    //println "== testScan =="
    publishDir.eachFileMatch(~/.*\.profile.xml$/, { file ->
      def resource = parseResource(file)
      assert resource instanceof StructureDefinition
      def profile = (StructureDefinition)resource
      def resName = getResourceName(profile)
      //println resName
      if (profile.hasConstrainedType()) {
        // println "ConstrainedType=" + profile.getConstrainedType()
        assert resName == profile.getConstrainedType()
      }
      profile.getDifferential().getElement().each{ elt ->
        // check elements
        if (elt.hasBinding()) {
          def binding = elt.getBinding()
          def ref = getReference(binding)
          assert ref != null, "path=" + elt.getPath() + ": no binding ref"
        }
      }
    })
  }

}
