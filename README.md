FhirProfileTools
================

The [FHIR](http://www.hl7.org/implement/standards/fhir/) (Fast Healthcare Interoperability Resources)
Specification is a standard for exchanging healthcare information electronically. 
Part of FHIR are profiles that define the structure of a resource (e.g. Condition, Patient, Procedure, etc.).
Profiles can be represented in source-form in the Excel XML Spreadsheet 2003 format (aka SpreadsheetML) or
in the published form as XML with the `http://hl7.org/fhir` namespace.

This project is a collection of tools for authors of FHIR profiles to automate
tasks such as validation, supplemental documentation generation and others.
First set of tools are for source profiles in SpreadsheetML format,
which are used in FHIR to define base resources and associated profiles. Second type of tools
are able to parse the FHIR XML Profiles, which are published as part of the specification.

Anybody creating profiles off the continuous integration branch of fhir-svn repo
has had to deal with changes in the base resources and needs to reflect those changes
back in their profiles. Identifying what changes were made and what potential
conflicts exist between the base resources and profiles can be daunting.

The FhirProfileTools project provides a collection of tools that can scan
for all or subset of profiles for all or specific resources then generate
validation reports, html documentation, and perform other automated tasks.
It provides the basic infrastructure to scan all top-level directories
in FHIR source folder, open resource spreadsheet XML files, extract the
list of associated profiles then process those profiles that match the
target pattern.

The FhirProfileValidator can identify the following situations:
 * mismatch in cardinality between base resource and profile for correponding elements.
   If the cardinality mismatch is itentional then the exception must be added to the
   rules.dat file so it will not be flagged as an error.
 * mismatch in type definition between base resource and profile for correponding elements.
   Sometimes the type of base profile changes (E.g. dateTime -> date) and in most cases
   want the profile to match the same type in base resource.
 * if profile misses any elements that are defined in the base resource which could have
   been added or renamed after the profile was created.
   
FhirSimpleBase is a base class with helper methods for parsing the published FHIR structure
definition XML files (aka profiles). It uses the FHIR Java reference API (tools.jar) that
is published along with the FHIR Specification.

Additional Documentation:
* [Customizing Validation Rules](customRules.md)
* [QUICK model generator](QuickHtmlGenerator.md)

# How to build

Project uses [Gradle](http://www.gradle.org) as a build tool. Since gradle is awesome
you actually don't have to install gradle, you just need a working JDK installation.

To compile and run all the tests:

    gradlew test

The first time you run this it will download and install gradle. Downloaded files (including the Gradle
distribution itself) will be stored in the Gradle user home directory (typically "<user_home>/.gradle").
Subsequent runs will be much faster.

If you already have Gradle installed then you can substitute the command gradle in place of gradlew as
listed above.

    gradle test

# Running

The FHIR-svn build repository with profile source files is required to run the tools.
Instructions to access the FHIR gForge Subversion project can be found at
http://gforge.hl7.org/gf/project/fhir including anonymous access. Also, a git mirror of the project
is available at https://github.com/hl7-fhir/fhir-svn.

To run on FHIR profiles you first need to create a `config.dat` file in top-level
of project that points to the location of resources and profiles. This normally
would be the build/source folder from the fhir-svn repository.

Can use `config-example.dat` as a template and copy that to config.dat then edit
the following 4 properties:
 * **fhirSourceDir** - directory of FHIR-svn repo build/source folder
   from which the publish tools build the FHIR web site from
 * **fhirPublishDir** - directory of the entire published FHIR specification (aka FHIR website)
   which includes all FHIR profiles, extensions, valuesets, QICore implementation guide, etc.
 * **profilePattern** - regular expression for file name matching used to include
   only those profiles of interest in processing and skip all others.
 * **profileRules** - profile rules and explicit exceptions (e.g. cqf-rules.dat). (Optional)
   This is optional but strongly recommended if using the FhirProfileValidator.

Note that either fhirSourceDir and/or fhirPublishDir property must be specified.
One or both is required depending on which tools are used. For example, FhirProfileValidator
uses the **fhirSourceDir** property and QuickHtmlGenerator uses published profiles located in 
**fhirPublishDir**.

See also [Customizing Validation Rules](customRules.md).

Now to run the validator with config.dat, run `validate` task in gradle:

     gradlew validate

To generate HTML javadoc-like documentation for FHIR profiles as classes run `runHtml` task:

     gradlew runHtml

The [QUICK model generator](QuickHtmlGenerator.md) creates custom java-like documentation QICore FHIR profiles
using `runQuickHtml` task:

     gradlew runQuickHtml

# Dependencies

* [Java to SpreadsheetML](http://sourceforge.net/projects/xelem/)
Java-library to read the Excel XML spreadsheet files.
* [FHIR DSTU2 Java Reference implementation API](http://hl7.org/fhir/DSTU2/org.hl7.fhir.tools.jar) ([Source](http://hl7.org/fhir/DSTU2/fhir-1.0.0-Java-0.9.zip))

# License

Copyright 2014 The MITRE Corporation

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
