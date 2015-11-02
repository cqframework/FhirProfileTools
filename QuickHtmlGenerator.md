# QUICK HTML Generator

The QUICK model generator creates custom Javadoc-like documentation for QICore FHIR profiles
using `runQuickHtml` task.

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
This is the directory of the entire published FHIR specification (aka FHIR website)
which includes all FHIR profiles, extensions, valuesets, QICore implementation guide, etc.
This directory includes a "extension-definitions.xml" file, and qicore + DAF subdirectories,
as well as profiles for all core resources in the top-level folder.

The published FHIR specification can be obtained from either 1) running publish tool from
FHIR spec sources via fhir-svn repo, or 2) downloading published spec package.
* [FHIR DSTU2 package (September 2015) version 1.0.1 - official version](http://hl7.org/fhir/DSTU2/fhir-spec.zip)