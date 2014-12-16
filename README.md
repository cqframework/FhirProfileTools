FhirProfileTools
================

Collection of tools for authors of Fast Healthcare Interoperability Resources (FHIR)
profiles to automate validation and supplemental documentation generation.
Initial set of tools are for profiles in Excel XML Spreadsheet 2003 format,
which is typically used to define resources and associated profiles.

Anybody creating profiles off the continuous integration branch of fhir-svn repo
has dealt with changes in the base resources and need to reflect those changes
back in the profiles. Identifying what changes were made and what potential
conflicts exist between the base resources and profiles can be daunting.

The fhir-profile-tools provides a collection of tools that can scan
for all or subset of profiles for all or specific resources then generate
validation reports, html documentation, and perform other automated tasks.
It provides the basic infrastructure to scan all top-level directories
in FHIR source folder, open resource spreadsheet XML files, extract the
list of associated profiles then process those profile that match the
target pattern.

The FhirProfileValidator can identify the following situations:
1. mismatch in cardinality between base resource and profile for correponding elements
   If the cardinality mismatch is itentional then the exception must be added to the
   rules.dat file so it will not be flagged as an error.
2. mismatch in type definition between base resource and profile for correponding elements
   Sometimes the type if base profile changes (E.g. dateTime -> date) and in most cases
   want the profile to match the same type.
3. if profile misses any elements that are defined in the base resource which could have
   possibly been added or renamed after the profile was created.
   
# How to build

Project uses [Gradle](http://www.gradle.org) as a build tool. Since gradle is awesome
you actually don't have to install gradle, you just need a working JDK installation.

To compile and run all the tests:

    gradlew test

The first time you run this it will download and install gradle. Downloaded files (including the Gradle
distribution itself) will be stored in the Gradle user home directory (typically "<user_home>/.gradle").
Subsequent runs will be much faster.

If you already have Gradle installed then you can substiute the command gradle in place of gradlew as
listed above.

    gradle test
  
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