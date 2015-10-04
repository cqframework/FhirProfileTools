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

    // find element by its path in snapshot
    def elt = getElementByName(profile.getSnapshot().getElement(), 'ReferralRequest.status', null)
    assert elt != null && elt.getPath() == 'ReferralRequest.status'
    def typeCode = getTypeCode(elt.getType(), false)
    assert typeCode == 'code'

    // find element by its path in differential
    elt = getElementByName(profile.getDifferential().getElement(), 'ReferralRequest.requester', null)
    assert elt != null && elt.getMustSupport()
    // [Reference(qicore-practitioner | qicore-organization | qicore-patient
    def shortList = typeShortList(elt.getType())
    assert shortList.size() == 1 && shortList.get(0).startsWith('Reference(')
    shortList = typeShortList(elt.getType(), false)
    assert shortList.size() == 1 && shortList.get(0) == 'qicore-practitioner | qicore-organization | qicore-patient'

    def typeSet = getTypeSet(elt)
    //[Reference(qicore-organization), Reference(qicore-patient), Reference(qicore-practitioner)]
    assert typeSet.size() == 3

    elt = getElementByName(profile.getSnapshot().getElement(), elt)
    assert elt != null && elt.getPath() == 'ReferralRequest.requester'

    // find extension by its name in differential
    elt = getExtensionByName(profile.getDifferential().getElement(), 'ReferralRequest.refusalReason')
    assert elt != null && elt.getPath() == 'ReferralRequest.extension'

    // println "path=" + elt.getPath()
    def type = elt.hasType() ? elt.getType().get(0) : null
    assert type && type.getCode() == 'Extension'
    assert getReference(elt.getBinding()) == 'http://hl7.org/fhir/ValueSet/v3-ActReason'
    String extProfile = getProfile(type)
    // println "profile=$extProfile"
    def extProfileDef = createExtensionDef(elt, extProfile)
    assert extProfileDef != null, "profile=$extProfile"
    assert extProfileDef.getStructure() != null
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
        // if binding is present then a reference valueset must exist
        if (elt.hasBinding()) {
          def binding = elt.getBinding()
          def ref = getReference(binding)
          assert ref != null, "path=" + elt.getPath() + ": no binding ref"
        }
      }
    })
  }
}
