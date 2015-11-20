# QUICK HTML Generator

The QUICK model generator creates custom Javadoc-like documentation for QICore FHIR profiles
using `runQuickHtml` task. Output is created in "html" folder of the FHIR Profile Tools project folder.

## Introduction

The generated HTML documentation represents the QUICK logical data model. The QUICK data model is an initiative of the
Clinical Quality Information (CQI) and Clinical Decision Support (CDS) HL7 Work Groups. This data model is auto-generated
from the HL7 Quality Improvement Core (QICore) FHIR Profiles. The QUICK logical model hides some details of FHIR's
physical implementation, such as the difference between elements and extensions. By abstracting away some of the
implementation details, and focusing on classes and attributes, the features of the logical data model can be
seen more clearly. As such, the terms class and resource are used interchangeably.

Profiles are represented as classes, so for example, the QICore Procedure profile is simply represented as the
Procedure class. Elements and extensions are normalized and listed as fields on the class with a given type,
description, and cardinality. The fields are classified as must support and is modifier, as defined in FHIR
and the [QICore Implementation Guide](http://hl7.org/fhir/DSTU2/qicore/qicore.html).
When class elements refer to other classes, the reference type is also normalized from its formal profile name
(e.g., QICore-Encounter) to its logical class name (e.g., Encounter).

## Configuration

QUICKHtmlGenerator requires setting the **fhirPublishDir** property in the config.dat file.
More details on config.dat are in the Running section of the main [README](README.md).
The property fhirPublishDir is the directory of the entire published FHIR specification (aka FHIR website),
which includes all FHIR profiles, extensions, valuesets, QICore & DAF implementation guides, etc.
This directory includes a "extension-definitions.xml" file, and qicore + daf subdirectories,
as well as profiles for all core resources in the top-level folder.

The published FHIR specification can be obtained from either 1) running publish tool from
FHIR spec sources via fhir-svn repository and set property to "publish" folder, or 2) downloading published spec package
and set property to "site" folder (e.g. C:/fhir/dstu2/site).
* [FHIR DSTU2 package (September 2015) version 1.0.1 - official version](http://hl7.org/fhir/DSTU2/fhir-spec.zip)
 
NOTE if you download the package above, the **fhirPublishDir** property must point to the folder that contains the
qicore & daf sub-folders and not the parent folder that has "index.html" file and "site" folder.
In that case, set fhirPublishDir to absolute location of the "site" folder.

## Building

To generate QUICK HTML javadoc-like documentation for FHIR profiles as classes run `runQuickHtml` task
from the command-line in the FHIR Profile Tools project folder:

    gradlew runQuickHtml

 If gradle is installed locally then run

    gradle runQuickHtml

## Notes

There are some errors in generated profiles in the FHIR DSTU2 package from September 2015 with respect to cardinality of extensions.
The publisher tool uses the default cardinality 0..* for extensions rather than use the cardinality as defined in extensions. This has
been fixed in the current publishing tool and available in the continuous integration branch of FHIR. Issue was documented in HL7
gforge as issue # [8750](http://gforge.hl7.org/gf/project/fhir/tracker/?action=TrackerItemEdit&tracker_item_id=8750).
If use the packaged DSTU2 bundle then the errors will be present in the source profiles and carried through to the logical model documentation.

Example:

XML definition of the profile for severity extension element in Adverse Event was incorrectly listed as `0..*` rather than 0..1
as defined in the extension definition.

Source: http://hl7.org/fhir/DSTU2/qicore/qicore-adverseevent.profile.xml.html
```
<snapshot>
  ...
  <element>
      <path value="Basic.extension"/>
      <name value="severity"/>
      <short value="Extension"/>
      <definition value="An Extension"/>
      <min value="0"/>
      <max value="*"/>
      <type>
        <code value="Extension"/>
        <profile value="http://hl7.org/fhir/StructureDefinition/qicore-adverseevent-severity"/>
      </type>
```
